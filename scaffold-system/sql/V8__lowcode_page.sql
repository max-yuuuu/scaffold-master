-- ==============================================================================================
-- V8__lowcode_page.sql — 页面表（PR 6）
--
-- 目的：建 lc_page —— 方案 §1.5 的「页面是形、模型是料」在这里落成表：模型与字段（lc_model / lc_field）
--       决定类型、约束与取数，页面决定布局、组件、顺序、动作。design_json 保存前已由
--       PageDesign 与模型对过一遍（字段编码存在、控件与类型相容、写入性允许），库里不留错形状。
-- 列口径：方案 §2.2 的 lc_page 草稿里这些列本期**不建**，因为还没有读取方：
--       route_parent / menu_id / menu_parent_id / menu_visible —— 生成的菜单固定挂顶层、由 Menu.sql 落；
--       permission_prefix —— 权限点由模型编码 + 动作推导（ComponentCatalog.permissionSuffix），
--                              存第二份就会和菜单种子里的那份打架；
--       current_version / publish_status / last_publish_time / publish_message 与 lc_page_version —— 属 PR 7 发布链路，
--                              版本历史跟发布动作一起来才有意义，现在建就是给一个没人写的列占位。
-- 唯一事实：page_type 是 design_json.pageType 的副本，只为列表筛选与展示，写入只跟着 design 走。
--       route_path / menu_icon / menu_sort 留空表示「由生成器按接口路径与默认值派生」，这里不再算第二遍。
-- 隔离：带 tenant_id，已登记进 ScaffoldTenantLineHandler.TENANT_TABLES（单元测试按 V*.sql 反向校验）。
--       页面归属项目、绑定模型，跨项目/跨租户引用在 Service 层按租户内反查拦截。
-- 反查：ref_fields 是保存时从设计里解析出来的字段编码清单（JSON 数组）。删字段与下线模型按它拦，
--       不做 JSON 全文搜索 —— 全文搜索会把「说明文字里提到 orderNo」当引用，反过来也会漏。
-- ID 段：V5 用 500/501 与按钮 5011–5014，V6 用 502 与按钮 5021–5027，本文件用 503 与按钮 5031–5033。
-- 授权：菜单只给平台租户(1) 的角色 1；普通租户用「补齐租户资产」获得（字典/菜单都走那条路）。
-- 幂等：CREATE TABLE IF NOT EXISTS + INSERT IGNORE，可重复执行。
-- ==============================================================================================

CREATE TABLE IF NOT EXISTS lc_page (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '页面 ID',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID（lc_tenant.id）',
    project_id BIGINT NOT NULL COMMENT '归属项目 ID（lc_project.id）',
    model_id BIGINT NOT NULL COMMENT '绑定的模型 ID（lc_model.id）：一个页面只有一个主模型，跨模型只经 REF 字段表达',
    page_code VARCHAR(64) NOT NULL COMMENT '页面编码（小写下划线，项目内唯一，决定生成页面的文件名，创建后不可改）',
    page_name VARCHAR(128) NOT NULL COMMENT '页面名称（生成菜单里的菜单名）',
    page_desc VARCHAR(512) DEFAULT NULL COMMENT '页面说明',
    page_type VARCHAR(32) NOT NULL DEFAULT 'list' COMMENT '页面类型：第一期只有 list（列表页含表单弹窗），值跟着 design_json.pageType 走',
    route_path VARCHAR(256) DEFAULT NULL COMMENT '菜单与路由落点；留空由生成器按接口路径派生',
    menu_icon VARCHAR(64) DEFAULT NULL COMMENT '菜单图标；留空用底座的 grid',
    menu_sort INT DEFAULT NULL COMMENT '菜单排序；留空排 1',
    design_json JSON NOT NULL COMMENT '设计器产出的完整页面配置（方案 §3.4），保存时已与模型逐字段对过',
    ref_fields JSON DEFAULT NULL COMMENT '设计引用的字段编码清单，删字段与下线模型按它反查影响面',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '页面状态：1 正常，0 停用（停用不参与发布）',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：1 删除，0 正常（页面可能被发布过，删了要留痕）',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lc_page_code (project_id, page_code),
    KEY idx_lc_page_model (model_id, deleted),
    KEY idx_lc_page_tenant (tenant_id, project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码页面（设计器产出的形）';

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, icon, path, component, permission, sort, visible, status, create_by, update_by)
VALUES
    (503, 500, '页面设计', 'menu', 'layers', '/lowcode/page', 'lowcode/pages', 'lowcode:page:list', 3, 1, 1, 'system', 'system'),
    (5031, 503, '新增页面', 'btn', '', '', '', 'lowcode:page:add', 1, 1, 1, 'system', 'system'),
    (5032, 503, '编辑页面', 'btn', '', '', '', 'lowcode:page:edit', 2, 1, 1, 'system', 'system'),
    (5033, 503, '删除页面', 'btn', '', '', '', 'lowcode:page:remove', 3, 1, 1, 'system', 'system');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 503 AND 5033;
