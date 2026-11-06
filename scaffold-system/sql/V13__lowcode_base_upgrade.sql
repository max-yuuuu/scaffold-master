-- ==============================================================================================
-- V13__lowcode_base_upgrade.sql — 底座升级的结论与权限点（PR 10，方案 §5.2、§10.4）
--
-- 记什么：一次「把当前底座的 A 档下发给这个项目」的结论。逐文件的三方合并结果存成一份 JSON，
--       计数由它现算 —— 与 V9 挡掉三个计数列的同一口径（存两份会漂）。
-- 为什么非存不可：升级结论是「当时」的事实。底座 checkout 一直在往前走，事后重算拿到的是新的
--       那份快照，算不出当时哪几格冲突了；而冲突的文件不写进升级分支，合并请求的 diff 里
--       根本看不见它们 —— 不存这份清单，「这三格要人解」这句话升级完就没人能再说出来。
-- status 的读者：列表要显示这条升级还等不等合并，10F 在合并成功后要把它推到「已下发」并
--       推进 lc_project.base_scaffold_sha；没产生升级分支的那种（全无可写）单独一档，
--       它不该出现在「待合并」的队列里。
-- 权限码：lowcode:project:upgrade 单列一条按钮。它能往租户仓库推分支，与 V7 把「同步仓库」
--       从「编辑项目」里分出来是同一个理由。
-- 幂等：建表 IF NOT EXISTS，菜单与授权用 INSERT IGNORE，可重复执行两遍。
-- ID 段：V5 用 501 与 5011–5013，V7 用按钮 5014，本文件用按钮 5015。
-- ==============================================================================================

CREATE TABLE IF NOT EXISTS lc_base_upgrade (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '升级记录 ID',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID（冗余自项目，供租户拦截器统一过滤）',
    project_id BIGINT NOT NULL COMMENT '项目 ID（lc_project.id）',
    base_scaffold_sha VARCHAR(40) NOT NULL COMMENT '升级前项目记的底座提交号，也就是三方合并 base 侧那个 tag 指向的提交',
    target_scaffold_sha VARCHAR(40) NOT NULL COMMENT '这次要下发到的底座提交号（底座 checkout 当前 HEAD）',
    upgrade_branch VARCHAR(128) DEFAULT NULL COMMENT '推出去的升级分支名；一格可写的都没有时为 NULL，表示没建分支',
    files_detail JSON NOT NULL COMMENT '逐文件三方合并结论：[{path, outcome}]，冲突与「平台撤掉」的那些靠它说话',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0 待合并 / 1 已合入主干 / 2 主干又前进（陈旧）/ 3 没产生升级分支（无内容可下发）',
    message VARCHAR(512) DEFAULT NULL COMMENT '一句人话：可自动合几格、冲突几格、撤掉几格',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '发起人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '状态变化时间',
    PRIMARY KEY (id),
    KEY idx_lc_base_upgrade_project (tenant_id, project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='底座升级结论（一次发起一条）';

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, icon, path, component, permission, sort, visible, status, create_by, update_by)
VALUES
    (5015, 501, '底座升级', 'btn', '', '', '', 'lowcode:project:upgrade', 5, 1, 1, 'system', 'system');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id = 5015;
