-- ==============================================================================================
-- V7__lowcode_workspace.sql — 项目工作区与仓库同步（PR 4）
--
-- 目的：1) lc_project 增加 sync_message —— 记录最近一次仓库操作（连通性检查 / 初始化 / 推送）的
--          失败原因或进度，前端直接显示，不靠人去翻后端日志；
--       2) 补「同步仓库」按钮权限码 lowcode:project:sync，与已有的增删改权限分开：
--          能编辑项目不等于能往租户仓库推代码。
-- 状态机：sync_status 由 PR 4 的 ProjectRepositoryService 推进（0 未初始化 → 1 初始化中 → 2 就绪，
--       异常态 3 工作区脏 / 4 与远端分叉 / 5 仓库不可达），V5 里已建列，本文件只补说明文案。
-- 工作区：workspace_path 存「{tenantId}/{projectCode}」相对路径，实际目录在 scaffold.lowcode.workspace-root
--       下（容器里是工作区卷），仓库凭据仍然只有主机 SSH 密钥，库里不落任何 token。
-- 幂等：列存在时跳过 ALTER；INSERT IGNORE 可重复执行。
-- ID 段：V5 用 500/501 与按钮 5011–5013，本文件用按钮 5014。
-- ==============================================================================================

SET @add_sync_message := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lc_project' AND COLUMN_NAME = 'sync_message') = 0,
    'ALTER TABLE lc_project ADD COLUMN sync_message VARCHAR(500) DEFAULT NULL COMMENT ''最近一次仓库操作的失败原因或进度说明（PR 4 工作区与推送状态）'' AFTER sync_status',
    'DO 0');
PREPARE stmt FROM @add_sync_message;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- sync_status 的取值含义在 V5 建列注释里已写全，这里只把 V5 头注里「状态推进由 PR 4 负责」兑现：
-- 无新语义，不再重复 ALTER 注释，避免升级时把已有列定义来回改。

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, icon, path, component, permission, sort, visible, status, create_by, update_by)
VALUES
    (5014, 501, '同步仓库', 'btn', '', '', '', 'lowcode:project:sync', 4, 1, 1, 'system', 'system');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id = 5014;
