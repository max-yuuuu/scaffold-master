-- ==============================================================================================
-- V4__platform_tenant.sql — 元搭平台租户管理与租户参数（PR 2 / P0-B）
--
-- 目的：1) 菜单 ID 改为数据库自增 —— 原来应用侧按本租户 MAX(id)+1 分配，多租户下两个租户会算出
--          同一个 ID 撞全局主键（V1 已同步改，这里补存量库的迁移；重复执行是等值 no-op）；
--       2) 新增租户级参数表 lc_tenant_config，存放平台按租户区分的默认值（生成链路先用）；
--       3) 新增「平台管理」菜单目录与「租户管理」「租户参数」两个页面及其按钮权限码；
--       4) 给平台租户（id=1）灌入两条内置参数，避免新页面打开是空的。
-- 接口：`GET/POST/PUT /api/platform/tenants`、`GET/PUT/DELETE /api/platform/tenant-configs`、
--       `POST /api/auth/switch-tenant`（平台管理员切换租户，重签 JWT）。
-- 隔离：lc_tenant 无 tenant_id（平台表，按主键与编码定位）；lc_tenant_config 带 tenant_id，
--       读写条件由 TenantLineInnerInterceptor 自动注入，跨租户改参数必须先切换到该租户。
-- ID 段：V2 用 1–301 与按钮段 1011–2021，V3 用 108，本文件使用未占用的 400 段（按钮 4011–4021）。
-- 授权：只补超级管理员(1)。租户管理属平台运营职责，不下发给普通租户角色。
-- 幂等：CREATE TABLE IF NOT EXISTS + INSERT IGNORE，可重复执行。
-- ==============================================================================================

ALTER TABLE sys_menu MODIFY id BIGINT NOT NULL AUTO_INCREMENT COMMENT '菜单 ID，由数据库自增分配（租户各自 MAX+1 会撞主键）';

CREATE TABLE IF NOT EXISTS lc_tenant_config (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '参数 ID',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID（lc_tenant.id）',
    config_key VARCHAR(128) NOT NULL COMMENT '参数键，如 project.gen_branch',
    config_value VARCHAR(500) DEFAULT NULL COMMENT '参数值',
    config_name VARCHAR(128) DEFAULT NULL COMMENT '参数名称（中文说明）',
    builtin TINYINT NOT NULL DEFAULT 0 COMMENT '系统内置：1 内置不可删除，0 自定义',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lc_tenant_config (tenant_id, config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户级参数';

-- 内置参数：PR 3 建项目、PR 4 发布时读取，作为租户下所有项目的分支默认值
INSERT IGNORE INTO lc_tenant_config (tenant_id, config_key, config_value, config_name, builtin, remark, create_by, update_by)
VALUES
    (1, 'project.main_branch', 'main', '生成项目的主干分支', 1, '人工合并请求的目标分支', 'system', 'system'),
    (1, 'project.gen_branch', 'platform-generated', '平台发布分支', 1, '平台只推这条分支，永不改主干', 'system', 'system');

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, icon, path, component, permission, sort, visible, status, create_by, update_by)
VALUES
    (400, 0, '平台管理', 'dir', 'database', '/platform', '', '', 5, 1, 1, 'system', 'system'),
    (401, 400, '租户管理', 'menu', 'users', '/platform/tenant', 'lowcode/tenants', 'platform:tenant:list', 1, 1, 1, 'system', 'system'),
    (4011, 401, '新增租户', 'btn', '', '', '', 'platform:tenant:add', 1, 1, 1, 'system', 'system'),
    (4012, 401, '编辑租户', 'btn', '', '', '', 'platform:tenant:edit', 2, 1, 1, 'system', 'system'),
    (402, 400, '租户参数', 'menu', 'gear', '/platform/tenant-config', 'lowcode/tenant-configs', 'platform:config:list', 2, 1, 1, 'system', 'system'),
    (4021, 402, '保存参数', 'btn', '', '', '', 'platform:config:edit', 1, 1, 1, 'system', 'system');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 400 AND 4021;
