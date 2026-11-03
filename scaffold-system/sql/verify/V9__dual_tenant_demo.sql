-- ==============================================================================================
-- V9__dual_tenant_demo.sql — 双租户验收数据（只用于隔离回归，不属于默认初始化）
--
-- 位置：放在 sql/verify/ 子目录，mysql 镜像的 docker-entrypoint-initdb.d 只导入顶层目录的
--       .sql/.sh，不会递归，所以建库时不会自动灌进来；生产库同样不要执行。
-- 用途：验证 PR 2 的租户隔离口径 —— A 租户令牌查 B 租户业务数据必须为空，
--       平台管理员要看到 B 租户数据必须先「切换到租户 B」（POST /api/auth/switch-tenant）。
-- 内容：租户 2（acme）+ 它自己的部门 / 角色 / 菜单 / 字典 / 参数 / 账号。
--       菜单按 id + 500000 复制租户 1 的业务菜单（排除 400 段平台管理菜单），parent_id 同步偏移；
--       账号 acmeadmin 复用租户 1 admin 的密码散列，口令与集成测试一致（admin123）。
-- 幂等：先删后插，可重复执行；只影响 tenant_id = 2 与 id >= 500000 的行。
-- 用法：DB_PORT=3316 的本地验收库执行
--           docker exec -i scaffold-it-mysql mysql --default-character-set=utf8mb4 -uroot -proot \
--             scaffold_system < scaffold-system/sql/verify/V9__dual_tenant_demo.sql
-- ==============================================================================================

DELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM sys_role WHERE tenant_id = 2);
DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE tenant_id = 2);
DELETE FROM sys_user_post WHERE user_id IN (SELECT id FROM sys_user WHERE tenant_id = 2);
DELETE FROM sys_user WHERE tenant_id = 2;
DELETE FROM sys_role WHERE tenant_id = 2;
DELETE FROM sys_dept WHERE tenant_id = 2;
DELETE FROM sys_menu WHERE tenant_id = 2;
DELETE FROM sys_dict_data WHERE dict_type IN (SELECT dict_type FROM sys_dict_type WHERE tenant_id = 2);
DELETE FROM sys_dict_type WHERE tenant_id = 2;
DELETE FROM sys_config WHERE tenant_id = 2;
DELETE FROM lc_tenant_config WHERE tenant_id = 2;
DELETE FROM lc_tenant WHERE id = 2;

INSERT INTO lc_tenant (id, tenant_code, tenant_name, contact_name, contact_phone, max_projects, status, remark, create_by, update_by)
VALUES (2, 'acme', 'Acme 验收租户', '验收机器人', '13800000002', 3, 1, '双租户隔离回归用，非真实客户', 'system', 'system');

INSERT INTO sys_dept (tenant_id, parent_id, ancestors, dept_name, leader, sort, status, create_by, update_by)
VALUES (2, 0, '', 'Acme 总部', '验收机器人', 1, 1, 'system', 'system');
INSERT INTO sys_dept (tenant_id, parent_id, ancestors, dept_name, leader, sort, status, create_by, update_by)
VALUES (2, LAST_INSERT_ID(), CONCAT('0,', LAST_INSERT_ID()), 'Acme 研发部', '验收机器人', 2, 1, 'system', 'system');

-- 租户 2 的菜单：复制租户 1 的业务菜单（含按钮），排除 V4 加进来的平台管理段 400/401/402/4011/4012/4021
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, icon, path, component, permission, sort, visible, status, create_by, update_by)
SELECT m.id + 500000,
       2,
       CASE WHEN m.parent_id = 0 THEN 0 ELSE m.parent_id + 500000 END,
       m.menu_name, m.menu_type, m.icon, m.path, m.component, m.permission, m.sort, m.visible, m.status, 'system', 'system'
FROM sys_menu m
WHERE m.tenant_id = 1
  AND m.id NOT IN (400, 401, 402, 4011, 4012, 4021);

INSERT INTO sys_role (tenant_id, role_name, role_code, data_scope, sort, status, builtin, remark, create_by, update_by)
VALUES (2, 'Acme 管理员', 'tenant_admin', 'ALL', 1, 1, 1, '租户 2 内置管理员，拥有本租户全部菜单', 'system', 'system');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r JOIN sys_menu m ON m.tenant_id = r.tenant_id WHERE r.tenant_id = 2 AND r.role_code = 'tenant_admin';

INSERT INTO sys_user (tenant_id, username, password, nickname, phone, email, dept_id, status, create_by, update_by)
SELECT 2, 'acmeadmin', u.password, '验收管理员', '13800000002', 'acmeadmin@example.com',
       (SELECT id FROM sys_dept WHERE tenant_id = 2 AND parent_id = 0 LIMIT 1), 1, 'system', 'system'
FROM sys_user u WHERE u.tenant_id = 1 AND u.username = 'admin';

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u JOIN sys_role r ON r.tenant_id = u.tenant_id
WHERE u.tenant_id = 2 AND u.username = 'acmeadmin' AND r.role_code = 'tenant_admin';

INSERT INTO sys_dict_type (tenant_id, dict_name, dict_type, status, remark, create_by, update_by)
VALUES (2, 'Acme 业务线', 'acme_biz_line', 1, '租户 2 独有字典', 'system', 'system');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, sort, status, tone, is_default, create_by, update_by)
VALUES (2, 'acme_biz_line', '仓储', 'warehouse', 1, 1, 'info', 1, 'system', 'system');

INSERT INTO sys_config (tenant_id, config_name, config_key, config_value, config_type, builtin, remark, create_by, update_by)
VALUES (2, 'Acme 专属参数', 'acme.contact.email', 'ops@acme.example.com', 'N', 0, '租户 2 独有参数', 'system', 'system');

INSERT INTO lc_tenant_config (tenant_id, config_key, config_value, config_name, builtin, remark, create_by, update_by)
VALUES (2, 'project.main_branch', 'main', '生成项目的主干分支', 1, '双租户验收数据', 'system', 'system'),
       (2, 'project.gen_branch', 'platform-generated', '平台发布分支', 1, '双租户验收数据', 'system', 'system');
