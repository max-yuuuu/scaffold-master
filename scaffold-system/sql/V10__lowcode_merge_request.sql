-- ==============================================================================================
-- V10__lowcode_merge_request.sql — 合并请求的表（PR 8 第一刀，方案 §5.4、PR 8 行）
--
-- 目的：把「发布分支 platform-generated → 人工主干 main」这条合并摆到平台上审。发布只推生成分支、
--       永不写主干（PR 4/7 已定），所以主干上进多少东西必须由人确认后合进去；这一刀只做**只读那一半**：
--       1) 建 lc_merge_request —— 一次请求一行，记下两个分支各自指向的提交与分叉点；
--       2) 建 lc_merge_request_file —— 一个变更文件一行，记下改动档位、增删行数与是否冲突；
--       3) 补权限码：列表读一份（lowcode:merge-request:list），重新算一次要单独一条
--          （lowcode:merge-request:sync，它要 clone/ls-remote 读远端仓库，与 V7 把
--          lowcode:project:sync 单列出来是同一个理由）；
--       4) **不做**确认合并与推送 —— 那是第二刀，验收要求「全程无 force-push」在那一刀上。
-- 状态与预检分两列：status 是这一条请求的生命周期（待审 / 陈旧 / 已合并 / 已关闭），
--       check_status 是内容能不能自动合的结论（未预检 / 可自动合并 / 有冲突）。两者是不同事实：
--       「目标分支又前进了」不改变内容是否冲突，「有冲突」也不表示这条请求过期了。合成一列的话，
--       一次刷新要么覆盖另一件事，要么状态机里出现「陈旧且有冲突」这种没人能显示的枚举值。
-- 不存的三样：
--       ① 文件数与冲突数由 lc_merge_request_file 现算 —— 与 V9 把 files_added/modified/deleted
--          三个计数挡在门外同一个理由：存了冗余就会和清单打架；
--       ② unified diff 文本不入库 —— source_sha / target_sha 已经把「哪两个提交之间」钉死了，
--          详情页按需从工作区仓库现算即可，一次大重构的 diff 存进库里既撑行数又会在下次刷新时对不上；
--       ③ 审批人 / 审批意见列不建 —— 本刀没有「批准」这个动作，没有读写方的列不进库（沿用 V5/V8 口径）。
-- 陈旧检测：target_sha 与目标分支当前提交号不一致就是陈旧。记 target_sha 而不是只记「陈旧 0/1」，
--       是因为列表要说「主干已经前进到 abc123，这条请求比它旧」，只给一个布尔值人没法判断。
-- 唯一性：不按 (project_id, source_branch, target_branch) 建唯一键 —— 一条分支对上主干会留下
--       多次历史请求（合完一次还有下一次），服务层用「同一分支对至多一条待审请求」这条规矩收敛。
-- 幂等：CREATE TABLE IF NOT EXISTS + INSERT IGNORE，可重复执行两遍。
-- 隔离：两张新表都带 tenant_id，登记进 ScaffoldTenantLineHandler.TENANT_TABLES
--       （单测按 sql/V*.sql 的 DDL 反向校验，漏登记当场红）。
-- ID 段：V5 用 500/501 与 5011–5013，V6 用 502 与 5021–5026，V8 用 503 与 5031–5033，V9 用按钮 5034；
--       本文件用页面 504 与按钮 5041。
-- ==============================================================================================

CREATE TABLE IF NOT EXISTS lc_merge_request (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '请求 ID',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID（冗余自项目，供租户拦截器统一过滤）',
    project_id BIGINT NOT NULL COMMENT '项目 ID（lc_project.id）',
    title VARCHAR(128) NOT NULL COMMENT '请求标题，平台按分支与文件数生成，人可改',
    description VARCHAR(512) DEFAULT NULL COMMENT '人填的说明',
    source_branch VARCHAR(64) NOT NULL COMMENT '源分支（项目的发布分支，lc_project.gen_branch 当时的值）',
    target_branch VARCHAR(64) NOT NULL COMMENT '目标分支（人工主干，lc_project.main_branch 当时的值）',
    source_sha VARCHAR(40) NOT NULL COMMENT '算这次变更时源分支指向的提交号',
    target_sha VARCHAR(40) NOT NULL COMMENT '算这次变更时目标分支指向的提交号：与它当前值不一致就是陈旧',
    merge_base_sha VARCHAR(40) DEFAULT NULL COMMENT '两条分支的分叉点：diff 按 merge-base..source 算，才只含源分支自己的改动',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '生命周期：0 待审 1 陈旧（目标分支已前进）2 已合并 3 已关闭（本刀只写 0 与 1）',
    check_status TINYINT NOT NULL DEFAULT 0 COMMENT '预检结论：0 未预检 1 可自动合并 2 有冲突（冲突只列文件，不自动取舍）',
    check_message VARCHAR(500) DEFAULT NULL COMMENT '预检的说明原文：冲突概要与 JGit 报错，人在列表上看的就这一行',
    check_time DATETIME DEFAULT NULL COMMENT '最近一次预检时间',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_lc_merge_request_project (tenant_id, project_id, status),
    KEY idx_lc_merge_request_branch (project_id, source_branch, target_branch)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合并请求（发布分支 → 人工主干，一次请求一行）';

CREATE TABLE IF NOT EXISTS lc_merge_request_file (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件行 ID',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID（冗余自请求，供租户拦截器统一过滤）',
    request_id BIGINT NOT NULL COMMENT '请求 ID（lc_merge_request.id）',
    path VARCHAR(512) NOT NULL COMMENT '仓库内相对路径（新路径）',
    old_path VARCHAR(512) DEFAULT NULL COMMENT '改名或移动时的原路径，未改名为空',
    change_type VARCHAR(16) NOT NULL COMMENT '档位：ADD 新增 / MODIFY 修改 / DELETE 删除 / RENAME 改名',
    is_conflict TINYINT NOT NULL DEFAULT 0 COMMENT '这一文件预检时是否冲突：1 必须人解，0 可自动合',
    additions INT NOT NULL DEFAULT 0 COMMENT '新增行数（列表上一眼要看的量，diff 文本本身不入库）',
    deletions INT NOT NULL DEFAULT 0 COMMENT '删除行数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '写入时间（行随请求重算整批替换，不单独记更新）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lc_merge_request_file (request_id, path),
    KEY idx_lc_merge_request_file_tenant (tenant_id, request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合并请求的变更文件清单（一个文件一行）';

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, icon, path, component, permission, sort, visible, status, create_by, update_by)
VALUES
    (504, 500, '合并请求', 'menu', 'branch', '/lowcode/merge-request', 'lowcode/merge-requests', 'lowcode:merge-request:list', 4, 1, 1, 'system', 'system'),
    (5041, 504, '重新计算合并请求', 'btn', '', '', '', 'lowcode:merge-request:sync', 1, 1, 1, 'system', 'system');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (504, 5041);
