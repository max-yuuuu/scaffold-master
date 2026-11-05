-- ==============================================================================================
-- V9__lowcode_publish.sql — 页面发布链路的表与列（PR 7，方案 §5.1、§5.4）
--
-- 目的：1) lc_page 补齐 V8 留到这一刀的四列 —— 版本号、最近一次发布结果、发布时间与结果说明；
--       2) 建 lc_page_version —— 每次「成功发布」固化一份设计快照与产物哈希，是 PR 9 三级回滚的取数来源；
--       3) 建 lc_publish_log —— 每一次发布动作（成功、无变更、失败）都留一条，失败也要留；
--       4) 补「发布页面」按钮权限码 lowcode:page:publish：能编辑页面不等于能往租户仓库推代码，
--          这条与 V7 把 lowcode:project:sync 单列出来是同一个理由。
-- 两套记录的分工：lc_page_version 只在成功时才有行（一个版本号一条），lc_publish_log 每次动作都有一条。
--       所以「这个页面发布过哪几个版本」查前者，「这个人今天点了三次发布，第二次推送失败」查后者。
--       合在一张表里要么版本行带上失败态（回滚取数时得先过滤），要么日志行缺状态（审计看不出哪次没成）。
-- 列口径：方案 §2.2 草稿里 lc_publish_log 的 files_added / files_modified / files_deleted 与 remote_url
--       本期**不建** —— 三个计数由 files_generated 里每条的落盘动作现算，存三份冗余就会和清单打架；
--       仓库地址在 lc_project 上，一次发布推的是当时那一条，日志里没有独立读者。沿用 V5/V8 的口径：
--       没有读写方的列不进库。
-- 版本语义：current_version 只在推送成功、tag 已落到远端之后才 +1（先记账再动手会留下库里说发过而
--       仓库里查无此提交的状态）；无变更与失败都不涨，失败把原因写进 publish_message。
--       lc_publish_log.version 是「这次动作指向的版本号」：成功就是新号，无变更是已有号，失败是那次想发的号。
-- 幂等：CREATE TABLE IF NOT EXISTS + 列存在即跳过的 ALTER + INSERT IGNORE，可重复执行两遍。
-- 隔离：两张新表都带 tenant_id，已登记进 ScaffoldTenantLineHandler.TENANT_TABLES
--       （单测按 sql/V*.sql 的 DDL 反向校验，漏登记当场红）。
-- ID 段：V5 用 500/501 与 5011–5014，V6 用 502 与 5021–5027，V8 用 503 与 5031–5033，本文件用按钮 5034。
-- ==============================================================================================

SET @add_current_version := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lc_page' AND COLUMN_NAME = 'current_version') = 0,
    'ALTER TABLE lc_page ADD COLUMN current_version INT NOT NULL DEFAULT 0 COMMENT ''已成功发布的版本号：无变更与失败都不涨，tag 末尾那个数就是它'' AFTER ref_fields',
    'DO 0');
PREPARE stmt FROM @add_current_version;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_publish_status := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lc_page' AND COLUMN_NAME = 'publish_status') = 0,
    'ALTER TABLE lc_page ADD COLUMN publish_status TINYINT NOT NULL DEFAULT 0 COMMENT ''最近一次发布结果：0 未发布 1 已发布 2 内容与远端一致（无变更）3 上次发布失败'' AFTER current_version',
    'DO 0');
PREPARE stmt FROM @add_publish_status;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_last_publish_time := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lc_page' AND COLUMN_NAME = 'last_publish_time') = 0,
    'ALTER TABLE lc_page ADD COLUMN last_publish_time DATETIME DEFAULT NULL COMMENT ''最近一次发布动作的时间'' AFTER publish_status',
    'DO 0');
PREPARE stmt FROM @add_last_publish_time;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_publish_message := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lc_page' AND COLUMN_NAME = 'publish_message') = 0,
    'ALTER TABLE lc_page ADD COLUMN publish_message VARCHAR(500) DEFAULT NULL COMMENT ''最近一次发布的结果说明：失败原因或无变更的结论，页面直接显示'' AFTER last_publish_time',
    'DO 0');
PREPARE stmt FROM @add_publish_message;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS lc_page_version (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '版本行 ID',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID（冗余自页面，供租户拦截器统一过滤）',
    page_id BIGINT NOT NULL COMMENT '页面 ID（lc_page.id）',
    version INT NOT NULL COMMENT '版本号，与 tag 末尾那个数同一个',
    design_snapshot JSON NOT NULL COMMENT '发布那一刻的设计原文：design_json 之后还会被人改，回滚要按当时这份',
    files_snapshot JSON NOT NULL COMMENT '产物清单：路径 → 内容 sha256，用来回答「那次发布里这个文件是什么」',
    commit_sha VARCHAR(40) NOT NULL COMMENT '该版本在发布分支上的提交号，回滚与 diff 的定位点',
    change_desc VARCHAR(512) DEFAULT NULL COMMENT '发布说明，人填的',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '发布人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lc_page_version (page_id, version),
    KEY idx_lc_page_version_tenant (tenant_id, page_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面版本（每次成功发布固化一份快照）';

CREATE TABLE IF NOT EXISTS lc_publish_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志 ID',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID（冗余自项目，供租户拦截器统一过滤）',
    project_id BIGINT NOT NULL COMMENT '项目 ID（lc_project.id）',
    page_id BIGINT NOT NULL COMMENT '页面 ID（lc_page.id）',
    version INT NOT NULL COMMENT '这次动作指向的版本号：成功是新号，无变更是已有号，失败是那次想发的号',
    publish_type VARCHAR(32) NOT NULL COMMENT '动作类型：CREATE 首次发布 / UPDATE 再次发布（ROLLBACK 属 PR 9）',
    publish_status TINYINT NOT NULL COMMENT '结果：1 成功 2 无变更 3 失败（库里不存在 0，那是 lc_page 的未发布态）',
    files_generated JSON DEFAULT NULL COMMENT '产物清单：路径 + 档位 + 落盘动作（新建/覆盖/跳过）',
    commit_sha VARCHAR(40) DEFAULT NULL COMMENT '本次发布在发布分支上的提交号；无变更时是分支上已有那个',
    tag_name VARCHAR(64) DEFAULT NULL COMMENT '版本标记名 v{项目}-{页面}-{版本}，合并与回滚的锚点；失败与无变更时为空',
    branch VARCHAR(64) DEFAULT NULL COMMENT '目标分支（项目的发布分支）',
    error_message TEXT COMMENT '失败原因原文（含 JGit 的远端报错），成功与无变更为空',
    publish_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发布动作时间',
    publish_by VARCHAR(64) DEFAULT NULL COMMENT '发布人',
    PRIMARY KEY (id),
    KEY idx_lc_publish_log_page (project_id, page_id),
    KEY idx_lc_publish_log_tenant (tenant_id, project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发布记录（每次发布动作一条，失败也留）';

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, icon, path, component, permission, sort, visible, status, create_by, update_by)
VALUES
    (5034, 503, '发布页面', 'btn', '', '', '', 'lowcode:page:publish', 4, 1, 1, 'system', 'system');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id = 5034;
