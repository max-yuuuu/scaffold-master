-- ==============================================================================================
-- V6__lowcode_model.sql — 模型与字段表（PR 3b）
--
-- 目的：建 lc_model / lc_field —— 方案 §1.4 已定口径：**模型是代码生成器的输入（期望态），
--       不是"模型管理"业务功能**。生成项目里没有"模型"这个东西，只有被生成的实体、表与接口。
-- 列口径：方案 §2.2 里的 lc_model.config / lc_model.version 本期**不建** —— config 没有任何读取方，
--       版本历史属于页面侧（lc_page_version），模型自身的历史靠发布 diff 看。
--       lc_field 补齐 create_by / update_by，与 AGENTS.md 的业务表四字段约定一致。
-- 类型映射：field_type 的取值与「→ Java 类型 / 列类型 / 默认表单控件」由代码里的 FieldType 枚举
--       单点定义（com.scaffold.lowcode.model.FieldType），不在库里再存一份映射，
--       前端通过 GET /api/lowcode/models/meta 取，避免两处口径漂移。
-- 隔离：两张表都带 tenant_id，已登记进 ScaffoldTenantLineHandler.TENANT_TABLES；
--       model 归属 project、field 归属 model，跨项目/跨租户引用在 Service 层按租户内反查拦截。
-- ID 段：V5 用 500/501 与按钮 5011–5013，本文件用 502 与按钮 5021–5027。
-- 授权：菜单只给平台租户(1) 的角色 1；普通租户用「补齐租户资产」获得（字典/菜单都走那条路）。
-- 幂等：CREATE TABLE IF NOT EXISTS + INSERT IGNORE，可重复执行。
-- ==============================================================================================

CREATE TABLE IF NOT EXISTS lc_model (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模型 ID',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID（lc_tenant.id）',
    project_id BIGINT NOT NULL COMMENT '归属项目 ID（lc_project.id）',
    model_code VARCHAR(64) NOT NULL COMMENT '模型编码（英文，项目内唯一，决定表名/类名/包名的默认值）',
    model_name VARCHAR(128) NOT NULL COMMENT '模型名称（中文，用于生成页面的标题与菜单名）',
    model_desc VARCHAR(512) DEFAULT NULL COMMENT '模型描述',
    table_name VARCHAR(128) NOT NULL COMMENT '物理表名（默认由 model_code 转下划线）',
    class_name VARCHAR(128) NOT NULL COMMENT 'Java 类名（默认由 model_code 转大驼峰）',
    module_name VARCHAR(64) NOT NULL COMMENT '业务子包名（生成代码进 scaffold-business 的这个子包，见方案 §5.2 单一业务模块）',
    is_tree TINYINT NOT NULL DEFAULT 0 COMMENT '是否树表：1 时生成 /tree 接口与树表页面',
    enable_logic_delete TINYINT NOT NULL DEFAULT 1 COMMENT '逻辑删除：1 时生成 deleted 列与软删语句',
    enable_data_scope TINYINT NOT NULL DEFAULT 0 COMMENT '数据权限：1 时生成 @DataScope 注解与部门过滤',
    enable_audit_field TINYINT NOT NULL DEFAULT 1 COMMENT '审计字段：1 时生成 create_by/update_by/create_time/update_time',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '模型状态：1 正常，0 停用（停用后不再参与发布）',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：1 删除，0 正常（模型下线要留痕，页面可能还在引用它的字段）',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lc_model_code (project_id, model_code),
    KEY idx_lc_model_tenant (tenant_id, project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码模型（生成器输入）';

CREATE TABLE IF NOT EXISTS lc_field (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '字段 ID',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID（lc_tenant.id）',
    model_id BIGINT NOT NULL COMMENT '归属模型 ID（lc_model.id）',
    field_code VARCHAR(64) NOT NULL COMMENT '字段编码（小驼峰，模型内唯一；页面与模型的耦合只在这个符号上，见方案 §1.5）',
    field_name VARCHAR(128) NOT NULL COMMENT '字段名称（中文，表单标签与列表表头）',
    field_desc VARCHAR(256) DEFAULT NULL COMMENT '字段说明',
    field_type VARCHAR(32) NOT NULL COMMENT '字段类型：STRING/TEXT/INTEGER/DECIMAL/DATE/DATETIME/ENUM/FILE/REF，Java 与列类型由代码侧 FieldType 推导',
    java_type VARCHAR(64) NOT NULL COMMENT 'Java 类型（保存时按 field_type 落库，避免模板里再算一遍）',
    column_name VARCHAR(128) NOT NULL COMMENT '数据库列名（默认由 field_code 转下划线）',
    column_type VARCHAR(64) NOT NULL COMMENT '数据库列类型（默认由 field_type 推导，可带长度）',
    column_length INT DEFAULT NULL COMMENT '列长度（字符串类）',
    column_precision INT DEFAULT NULL COMMENT '小数精度（DECIMAL 用）',
    column_scale INT DEFAULT NULL COMMENT '小数位数（DECIMAL 用）',
    is_primary TINYINT NOT NULL DEFAULT 0 COMMENT '是否主键',
    is_auto_increment TINYINT NOT NULL DEFAULT 0 COMMENT '是否自增（主键自增列固定 bigint）',
    is_nullable TINYINT NOT NULL DEFAULT 1 COMMENT '是否可空',
    is_unique TINYINT NOT NULL DEFAULT 0 COMMENT '是否唯一索引',
    default_value VARCHAR(256) DEFAULT NULL COMMENT '列默认值（DDL 里的 DEFAULT）',
    is_list TINYINT NOT NULL DEFAULT 1 COMMENT '列表页是否显示',
    is_query TINYINT NOT NULL DEFAULT 0 COMMENT '是否作为查询条件',
    query_type VARCHAR(16) NOT NULL DEFAULT '=' COMMENT '查询方式：= / != / > / >= / < / <= / LIKE / BETWEEN / IN',
    is_insert TINYINT NOT NULL DEFAULT 1 COMMENT '新增表单是否出现',
    is_edit TINYINT NOT NULL DEFAULT 1 COMMENT '编辑表单是否出现（如创建时间之类只增不改）',
    is_required TINYINT NOT NULL DEFAULT 0 COMMENT '是否必填（生成校验注解）',
    form_type VARCHAR(32) NOT NULL DEFAULT 'INPUT' COMMENT '表单控件：INPUT/TEXTAREA/NUMBER/SELECT/RADIO/CHECKBOX/SWITCH/DATE/DATETIME/UPLOAD/REF',
    form_options JSON DEFAULT NULL COMMENT '控件选项（RADIO/CHECKBOX 的固定取值；挂了字典时留空，以字典为准）',
    dict_type VARCHAR(64) DEFAULT NULL COMMENT '展示字典类型（任何 field_type 都可挂，用于列表与表单的取值翻译）',
    ref_model_id BIGINT DEFAULT NULL COMMENT 'REF 字段指向的模型 ID（必须同项目同租户）',
    ref_field_id BIGINT DEFAULT NULL COMMENT 'REF 字段指向的展示字段 ID（下拉显示哪一列，留空则用目标模型的主键）',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '字段顺序（决定实体属性与表单/列表列的顺序）',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lc_field_code (model_id, field_code),
    KEY idx_lc_field_model (model_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码字段（生成器输入）';

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, icon, path, component, permission, sort, visible, status, create_by, update_by)
VALUES
    (502, 500, '模型管理', 'menu', 'database', '/lowcode/model', 'lowcode/models', 'lowcode:model:list', 2, 1, 1, 'system', 'system'),
    (5021, 502, '新增模型', 'btn', '', '', '', 'lowcode:model:add', 1, 1, 1, 'system', 'system'),
    (5022, 502, '编辑模型', 'btn', '', '', '', 'lowcode:model:edit', 2, 1, 1, 'system', 'system'),
    (5023, 502, '删除模型', 'btn', '', '', '', 'lowcode:model:remove', 3, 1, 1, 'system', 'system'),
    (5024, 502, '新增字段', 'btn', '', '', '', 'lowcode:field:add', 4, 1, 1, 'system', 'system'),
    (5025, 502, '编辑字段', 'btn', '', '', '', 'lowcode:field:edit', 5, 1, 1, 'system', 'system'),
    (5026, 502, '删除字段', 'btn', '', '', '', 'lowcode:field:remove', 6, 1, 1, 'system', 'system');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 502 AND 5026;
