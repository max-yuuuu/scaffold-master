-- ==============================================================================================
-- V12__lowcode_page_rollback.sql — 页面回滚的权限点（PR 9，方案 §5.4）
--
-- 这一刀不加列也不加表：回滚要用的锚点 PR 7 就备齐了 —— lc_page_version.commit_sha 与 files_snapshot
--       记着「那一版是哪一笔提交、里面有哪些文件、各是什么哈希」，tag 记着版本号，lc_publish_log
--       记着每次动作。缺的只是「谁能把一笔还原写进发布分支」。
-- 权限码：lowcode:page:rollback 单列一条按钮。看版本历史是读（沿用 lowcode:page:list），
--       而回滚会往租户自己的仓库推提交 —— 与 V9 把 lowcode:page:publish、V7 把 lowcode:project:sync
--       单列出来是同一个理由：能改设计不等于能写仓库。
-- 为什么不是「退回上一版」的开关而是新的一版：回滚一律以新提交实现（revert 而非 reset），
--       版本号 current+1、publish_type=ROLLBACK、照样打 tag，于是这一版本身也能被回滚过去。
-- 幂等：菜单与授权用 INSERT IGNORE，可重复执行两遍。
-- ID 段：V8 用 503 与 5031–5033，V9 用按钮 5034，本文件用按钮 5035。
-- ==============================================================================================

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, icon, path, component, permission, sort, visible, status, create_by, update_by)
VALUES
    (5035, 503, '回滚页面', 'btn', '', '', '', 'lowcode:page:rollback', 5, 1, 1, 'system', 'system');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id = 5035;
