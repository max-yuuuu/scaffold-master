-- ==============================================================================================
-- V11__lowcode_merge_review.sql — 项目负责人与合并记账（PR 8 第二刀，方案 §5.4、PR 8 行）
--
-- 目的：把「开发提交 PR → 负责人审核 → 点合并」这条流程落到库里。第一刀只算清单，
--       这一刀要回答两件事：**谁有权改人工主干**，以及**这一条是谁在什么时候合的**。
-- 负责人建模：lc_project.owner_user_id（→ sys_user.id，一个用户而不是一个角色）。理由是需求原话是
--       「给项目添加一个负责人」，而「待我审核」这个队列要有个按人过滤的落点。用角色的话多人不在岗
--       时不会卡住，但队列得先算当前用户的角色集合，且换人靠改角色分配而不是改项目 —— 那是另一套
--       权限语言，本期不引。超级管理员不受这一列限制（底座里超管本来就持全部操作权限）。
-- 合并记账：merged_by / merged_time 两列都有读者 —— 列表与详情要写「已由谁合入」，
--       而「这条请求是否还活着」也靠 status=2 判定；审批意见与「批准」这一步没有独立动作，
--       所以不建 approve_by / approve_time（沿用 V5/V8「没有读写方的列不进库」的口径）。
-- 权限码：lowcode:merge-request:merge 单列一条按钮。能看清单（list）与能把提交推进人工主干
--       是两种信任级别，跟 V9 把 lowcode:page:publish、V7 把 lowcode:project:sync 单列出来同理由。
-- 幂等：ALTER 走「列存在即跳过」的 PREPARE 写法，菜单与授权用 INSERT IGNORE，可重复执行两遍。
-- ID 段：V9 用按钮 5034，V10 用页面 504 与按钮 5041，本文件用按钮 5042。
-- ==============================================================================================

SET @add_owner_user := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lc_project' AND COLUMN_NAME = 'owner_user_id') = 0,
    'ALTER TABLE lc_project ADD COLUMN owner_user_id BIGINT DEFAULT NULL COMMENT ''项目负责人用户 ID（sys_user.id）：合并请求只有他或超级管理员能确认合并，「待我审核」按它过滤'' AFTER gen_branch',
    'DO 0');
PREPARE stmt FROM @add_owner_user;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_merged_by := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lc_merge_request' AND COLUMN_NAME = 'merged_by') = 0,
    'ALTER TABLE lc_merge_request ADD COLUMN merged_by VARCHAR(64) DEFAULT NULL COMMENT ''确认合并的人（只有项目负责人或超级管理员能写成这一列）'' AFTER check_time',
    'DO 0');
PREPARE stmt FROM @add_merged_by;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_merged_time := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lc_merge_request' AND COLUMN_NAME = 'merged_time') = 0,
    'ALTER TABLE lc_merge_request ADD COLUMN merged_time DATETIME DEFAULT NULL COMMENT ''合入人工主干的时间；与 status=2 一起构成「这条请求已收口」'' AFTER merged_by',
    'DO 0');
PREPARE stmt FROM @add_merged_time;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, icon, path, component, permission, sort, visible, status, create_by, update_by)
VALUES
    (5042, 504, '确认合并', 'btn', '', '', '', 'lowcode:merge-request:merge', 2, 1, 1, 'system', 'system');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id = 5042;
