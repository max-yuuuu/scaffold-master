-- ==============================================================================================
-- V5__lowcode_project.sql — 低代码项目表与「低代码」菜单（PR 3a）
--
-- 目的：1) 建 lc_project —— 项目 = 租户名下的一个 Git 仓库，是模型与页面的归属容器；
--       2) 新增「低代码」菜单目录与「项目管理」页面及按钮权限码（租户自己可见，
--          与只给平台运营用的「平台管理」段分开）。
-- 列口径：方案 §2.2 里的 db_*（生成 application.yml 用的连接配置）、deploy_*（远期部署形态）
--       和 backend_module / api_prefix / frontend_prefix（生成期常量）本期**不建** ——
--       现在没有任何读写方，建了就是让表单里多一排填了没用的字段。等 PR 5 生成配置文件时
--       按真实需要以 ALTER 补列。
-- 状态机：sync_status 是项目的唯一生命周期状态（方案 §2.2 已把原 init_status / sync_status 两套合并），
--       0 未初始化 → 1 初始化中 → 2 就绪，异常态 3 工作区脏 / 4 与远端分叉 / 5 仓库不可达。
--       本期只读写 0 与 2 之外的展示，状态推进由 PR 4 的仓库初始化与发布链路负责。
-- 接口：`GET/POST/PUT/DELETE /api/lowcode/projects`、`PUT /api/lowcode/projects/{id}/status`。
-- 隔离：lc_project 带 tenant_id，已登记进 ScaffoldTenantLineHandler.TENANT_TABLES，
--       读写条件由拦截器自动注入（单测按本文件的 DDL 反向校验，漏登记当场红）。
-- 仓库凭据：库里只存 repo_url 与两个分支名，不存任何 token —— 推送走主机级 SSH 密钥（§〇之二 第 4 条）。
-- ID 段：V2 用 1–301 与按钮 1011–2021，V3 用 108，V4 用 400 段；本文件使用 500 段（按钮 5011–5012）。
-- 授权：平台租户(1) 的超级管理员(1) 直接补授权；普通租户的内置管理员由「补齐菜单」接口
--       （POST /api/platform/tenants/{id}/menus/sync）按同一套模板补齐，新租户注册时自带。
-- 幂等：CREATE TABLE IF NOT EXISTS + INSERT IGNORE，可重复执行。
-- ==============================================================================================

CREATE TABLE IF NOT EXISTS lc_project (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '项目 ID',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID（lc_tenant.id）',
    project_code VARCHAR(64) NOT NULL COMMENT '项目编码（英文，租户内唯一，决定生成物的包名与目录名）',
    project_name VARCHAR(128) NOT NULL COMMENT '项目名称',
    project_desc VARCHAR(512) DEFAULT NULL COMMENT '项目描述',
    repo_url VARCHAR(256) NOT NULL COMMENT '代码仓库地址，仅 https:// 或 git@host:path 两种形式；凭据走主机 SSH 密钥，库里不存',
    main_branch VARCHAR(64) NOT NULL DEFAULT 'main' COMMENT '人工主干分支（sys 合并请求的目标分支，平台永不直接写）',
    gen_branch VARCHAR(64) NOT NULL DEFAULT 'platform-generated' COMMENT '平台发布分支（发布只推这条，永不 force-push）',
    workspace_path VARCHAR(256) DEFAULT NULL COMMENT '平台本机 clone 的相对路径（工作区卷内），由仓库初始化写入',
    base_scaffold_sha VARCHAR(40) DEFAULT NULL COMMENT '初始快照取自本仓库的提交号，用于判断项目落后底座多少',
    last_commit_sha VARCHAR(40) DEFAULT NULL COMMENT '平台最后一次成功推送的提交号',
    sync_status TINYINT NOT NULL DEFAULT 0 COMMENT '生命周期：0 未初始化 1 初始化中 2 就绪 3 工作区脏 4 与远端分叉 5 仓库不可达',
    init_time DATETIME DEFAULT NULL COMMENT '固定代码初始化完成时间',
    last_sync_time DATETIME DEFAULT NULL COMMENT '最近一次同步远端时间',
    last_publish_time DATETIME DEFAULT NULL COMMENT '最后发布时间',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '项目状态：1 正常，0 停用',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：1 删除，0 正常',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lc_project_code (tenant_id, project_code),
    KEY idx_lc_project_tenant (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码项目';

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, icon, path, component, permission, sort, visible, status, create_by, update_by)
VALUES
    (500, 0, '低代码', 'dir', 'code', '/lowcode', '', '', 6, 1, 1, 'system', 'system'),
    (501, 500, '项目管理', 'menu', 'grid', '/lowcode/project', 'lowcode/projects', 'lowcode:project:list', 1, 1, 1, 'system', 'system'),
    (5011, 501, '新增项目', 'btn', '', '', '', 'lowcode:project:add', 1, 1, 1, 'system', 'system'),
    (5012, 501, '编辑项目', 'btn', '', '', '', 'lowcode:project:edit', 2, 1, 1, 'system', 'system'),
    (5013, 501, '删除项目', 'btn', '', '', '', 'lowcode:project:remove', 3, 1, 1, 'system', 'system');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 500 AND 5013;
