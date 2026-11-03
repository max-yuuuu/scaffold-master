# 低代码平台完整方案

> 基于 scaffold-master 脚手架架构，构建多租户低代码平台。支持可视化设计（拖拽 + 配置融合）、完整 CRUD 代码生成、增量发布。

---

## 〇、仓库与基线

**2026-11-03 现状**

| 项 | 值 |
|----|-----|
| 权威仓库 | `https://github.com/max-yuuuu/lowcode-ai.git`（git remote 名 `lowcode-ai`） |
| 基线 HEAD | `ba3e0dc` on `main`，含 `feat/framework-global-sync`（前端组件重写）+ `feat/deploy-docker`（Docker 部署）+ 方案文档 |
| 上游 PR | 不再等合并；两个 PR 分支的改动已并入本地 main 后推 lowcode-ai |

**scaffold-master 的三重身份**（重要修正，不只是模板来源）

```
scaffold-master = ① 低代码平台自身的底座
                   ├─ 平台 UI 用 scaffold-vue 组件（ListPage/FormDialog/DataTable/珍珠玻璃样式）
                   └─ 平台后端用 scaffold-common/framework/system

                = ② 生成项目的运行时壳子
                   ├─ 项目初始化时克隆整套
                   └─ 页面发布只增量更新业务模块

                = ③ 上游开源仓库
                   └─ 现由 lowcode-ai 替代作为主推进；scaffold-master 保留同步通道
```

**待决策：低代码平台如何复用 scaffold-vue 组件？**

`scaffold-vue/package.json` 是 `"private": true` 单体应用（组件在 `src/components/`，靠 `@/` 别名引用），不是可发布 npm 包。三条路：

| 方案 | 优势 | 代价 |
|------|------|------|
| **A. fork scaffold-vue 整个 app** | 立即可用，最快开工 | 组件双份维护，与上游修复要手工来回同步 |
| **B. 抽 `@scaffold/ui` npm 包**（推荐长期） | 平台与生成项目都依赖同一份，改一处两边同步 | 需要重构 scaffold 本体，涉及大量 import 路径改动 |
| **C. git subtree 同步** | 折中：保留同步通道 | subtree 操作门槛高，组件局部改动易冲突 |

推荐路径：**A 起步 → 稳定后走 B**。先让平台跑起来，验证设计器需求稳定后再抽包，避免过早重构。

---

## 一、核心概念

### 1.1 层级关系

```
租户 Tenant（多租户隔离）
  └── 项目 Project（= 一套脚手架运行时实例）
        ├── 模型 Model（数据模型定义 → 对应数据库表）
        │     └── 字段 Field（字段定义 → 对应表列）
        └── 页面 Page（关联模型，可视化设计，增量发布）
              └── 版本 Version（每次发布记录快照，支持回滚）
```

### 1.2 架构定位

```
┌──────────────────────────────────────────────────────────────┐
│                低代码管理平台（独立项目）                       │
│                                                               │
│  租户管理 · 项目管理 · 可视化设计器 · 代码生成引擎 · 发布管理  │
│                                                               │
│  技术栈：Spring Boot 3.5 + Vue 3 + TypeScript + FreeMarker   │
└──────────────────────────┬───────────────────────────────────┘
                           │ 生成代码 / 发布
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                脚手架运行时（每个项目一个实例）                  │
│                                                               │
│  scaffold-common     → 公共（R / PageResult / BusinessException）│
│  scaffold-framework  → 框架（认证 / 权限 / 数据范围 / 审计）   │
│  scaffold-system     → 系统管理（用户 / 角色 / 菜单 / 字典）   │
│  scaffold-{business} → 生成的业务模块（完整 CRUD，只读）       │
│  scaffold-admin      → 启动入口                              │
│                                                               │
│  前端：Vue 3 + TypeScript + Vite + 珍珠玻璃主题              │
└──────────────────────────────────────────────────────────────┘
```

### 1.3 固定代码 vs 动态代码

| 分类 | 内容 | 生成时机 | 更新策略 |
|------|------|----------|----------|
| **固定代码** | scaffold-common、scaffold-framework、系统管理模块、登录页、工作台、公共组件、样式 | 项目初始化 | 不随页面发布更新 |
| **动态代码** | 业务 Controller/Service/Mapper、业务页面 Vue、业务 API、路由菜单 | 页面发布 | 每次发布对应页面时增量更新 |

---

## 二、多租户设计

### 2.1 数据隔离策略

| 数据类别 | 隔离方式 | 理由 |
|----------|----------|------|
| **平台元数据**（租户、项目、模型、页面定义） | 独立数据库，tenant_id 字段隔离 | 数据量小，管理集中 |
| **系统管理数据**（用户、角色、菜单、字典） | 共享库，tenant_id 字段隔离 | MyBatis 拦截器自动注入 |
| **业务数据**（租户创建的业务表） | 每项目独立数据库 | 数据量不可预测，独立备份/恢复 |

### 2.2 核心表设计

```sql
-- ========== 平台库 ==========

-- 租户表
CREATE TABLE lc_tenant (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_code VARCHAR(64) NOT NULL UNIQUE COMMENT '租户编码',
    tenant_name VARCHAR(128) NOT NULL COMMENT '租户名称',
    contact_name VARCHAR(64) COMMENT '联系人',
    contact_phone VARCHAR(32) COMMENT '联系电话',
    max_projects INT DEFAULT 5 COMMENT '最大项目数',
    expire_time DATETIME COMMENT '到期时间',
    status TINYINT DEFAULT 1 COMMENT '0停用 1正常',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64),
    update_by VARCHAR(64)
) COMMENT '租户表';

-- 项目表
CREATE TABLE lc_project (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    project_code VARCHAR(64) NOT NULL COMMENT '项目编码(英文)',
    project_name VARCHAR(128) NOT NULL COMMENT '项目名称',
    project_desc VARCHAR(512) COMMENT '项目描述',
    
    -- 技术配置
    backend_module VARCHAR(64) DEFAULT 'business' COMMENT '后端业务模块名',
    frontend_prefix VARCHAR(64) DEFAULT '' COMMENT '前端路由前缀',
    api_prefix VARCHAR(64) DEFAULT '/api' COMMENT '接口前缀',
    
    -- 数据源
    db_host VARCHAR(128) COMMENT '数据库地址',
    db_port INT DEFAULT 3306 COMMENT '端口',
    db_name VARCHAR(64) COMMENT '数据库名',
    db_username VARCHAR(64) COMMENT '账号',
    db_password VARCHAR(256) COMMENT '密码(加密)',
    
    -- 部署
    deploy_mode VARCHAR(32) DEFAULT 'STANDALONE' COMMENT 'STANDALONE/DOCKER/K8S',
    deploy_host VARCHAR(128) COMMENT '部署域名',
    
    -- 状态
    init_status TINYINT DEFAULT 0 COMMENT '0未初始化 1初始化中 2已初始化',
    init_time DATETIME COMMENT '初始化时间',
    last_publish_time DATETIME COMMENT '最后发布时间',
    status TINYINT DEFAULT 1,
    
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64),
    update_by VARCHAR(64),
    UNIQUE KEY uk_tenant_code (tenant_id, project_code)
) COMMENT '项目表';

-- 模型表
CREATE TABLE lc_model (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    model_code VARCHAR(64) NOT NULL COMMENT '模型编码',
    model_name VARCHAR(128) NOT NULL COMMENT '模型名称',
    model_desc VARCHAR(512) COMMENT '描述',
    table_name VARCHAR(128) NOT NULL COMMENT '物理表名',
    class_name VARCHAR(128) NOT NULL COMMENT 'Java类名',
    module_name VARCHAR(64) NOT NULL COMMENT '模块名',
    
    -- 功能开关
    is_tree TINYINT DEFAULT 0 COMMENT '是否树表',
    enable_logic_delete TINYINT DEFAULT 1 COMMENT '逻辑删除',
    enable_data_scope TINYINT DEFAULT 0 COMMENT '数据权限',
    enable_audit_field TINYINT DEFAULT 1 COMMENT '审计字段',
    
    config JSON COMMENT '扩展配置',
    status TINYINT DEFAULT 1,
    version INT DEFAULT 1,
    
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64),
    update_by VARCHAR(64),
    UNIQUE KEY uk_project_model (project_id, model_code)
) COMMENT '模型表';

-- 字段表
CREATE TABLE lc_field (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    model_id BIGINT NOT NULL,
    field_code VARCHAR(64) NOT NULL COMMENT '字段编码',
    field_name VARCHAR(128) NOT NULL COMMENT '字段名称',
    field_desc VARCHAR(256) COMMENT '描述',
    
    -- 类型
    field_type VARCHAR(32) NOT NULL COMMENT 'STRING/INTEGER/DECIMAL/DATE/DATETIME/ENUM/FILE/REF',
    java_type VARCHAR(64) NOT NULL,
    java_field VARCHAR(64) NOT NULL,
    
    -- 数据库列
    column_name VARCHAR(128) NOT NULL,
    column_type VARCHAR(64) NOT NULL,
    column_length INT,
    column_precision INT,
    column_scale INT,
    
    -- 约束
    is_primary TINYINT DEFAULT 0,
    is_auto_increment TINYINT DEFAULT 0,
    is_nullable TINYINT DEFAULT 1,
    is_unique TINYINT DEFAULT 0,
    default_value VARCHAR(256),
    
    -- 表单
    is_list TINYINT DEFAULT 1 COMMENT '列表显示',
    is_query TINYINT DEFAULT 0 COMMENT '查询条件',
    query_type VARCHAR(16) DEFAULT '=',
    is_insert TINYINT DEFAULT 1,
    is_edit TINYINT DEFAULT 1,
    is_required TINYINT DEFAULT 0,
    form_type VARCHAR(32) DEFAULT 'INPUT',
    form_options JSON,
    
    -- 关联
    dict_type VARCHAR(64) COMMENT '字典类型',
    ref_model_id BIGINT COMMENT '关联模型',
    ref_field_id BIGINT COMMENT '关联字段',
    
    sort_order INT DEFAULT 0,
    
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_model_field (model_id, field_code)
) COMMENT '字段表';

-- 页面表
CREATE TABLE lc_page (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    model_id BIGINT COMMENT '关联模型',
    
    page_code VARCHAR(64) NOT NULL COMMENT '页面编码',
    page_name VARCHAR(128) NOT NULL COMMENT '页面名称',
    page_type VARCHAR(32) NOT NULL COMMENT 'LIST/FORM/DETAIL/TREE_LIST/DASHBOARD',
    page_desc VARCHAR(512),
    
    -- 路由
    route_path VARCHAR(256) NOT NULL,
    route_parent VARCHAR(256),
    component_path VARCHAR(256),
    
    -- 菜单
    menu_id BIGINT,
    menu_parent_id BIGINT DEFAULT 0,
    menu_icon VARCHAR(64),
    menu_sort INT DEFAULT 0,
    menu_visible TINYINT DEFAULT 1,
    
    -- 权限
    permission_prefix VARCHAR(128),
    
    -- 页面设计（拖拽 + 配置融合的 JSON）
    design_json JSON NOT NULL COMMENT '可视化设计器产出的完整页面配置',
    
    -- 发布状态
    current_version INT DEFAULT 0,
    publish_status TINYINT DEFAULT 0 COMMENT '0草稿 1已发布 2失败',
    last_publish_time DATETIME,
    publish_message VARCHAR(512),
    
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64),
    update_by VARCHAR(64),
    UNIQUE KEY uk_project_page (project_id, page_code)
) COMMENT '页面表';

-- 页面版本表
CREATE TABLE lc_page_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    page_id BIGINT NOT NULL,
    version INT NOT NULL,
    design_snapshot JSON NOT NULL COMMENT '页面配置快照',
    files_snapshot JSON COMMENT '生成文件快照',
    change_desc VARCHAR(512),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    create_by VARCHAR(64),
    UNIQUE KEY uk_page_version (page_id, version)
) COMMENT '页面版本表';

-- 发布日志表
CREATE TABLE lc_publish_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    page_id BIGINT NOT NULL,
    version INT NOT NULL,
    publish_type VARCHAR(32) NOT NULL COMMENT 'CREATE/UPDATE/DELETE/ROLLBACK',
    publish_status TINYINT NOT NULL COMMENT '0失败 1成功',
    files_generated JSON COMMENT '生成的文件列表',
    error_message TEXT,
    publish_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    publish_by VARCHAR(64),
    INDEX idx_project_page (project_id, page_id)
) COMMENT '发布记录表';
```

### 2.3 多租户实现

```java
// scaffold-common 新增
public class TenantContext {
    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> PROJECT_ID = new ThreadLocal<>();
    
    public static void set(Long tenantId, Long projectId) { ... }
    public static Long getTenantId() { return TENANT_ID.get(); }
    public static Long getProjectId() { return PROJECT_ID.get(); }
    public static void clear() { TENANT_ID.remove(); PROJECT_ID.remove(); }
}

// scaffold-framework 新增
// 1. TenantFilter: JWT 解析后设置 TenantContext
// 2. TenantInterceptor: MyBatis 拦截器，自动注入 tenant_id
// 3. DynamicDataSource: AbstractRoutingDataSource，按 project_id 切换业务库
```

---

## 三、可视化设计器

### 3.1 设计器架构：拖拽 + 配置融合

```
┌─────────────────────────────────────────────────────────────────┐
│                        页面设计器                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────┐  ┌──────────────────────────┐  ┌──────────────┐ │
│  │ 组件库    │  │ 画布 (流式布局 + 嵌套)    │  │ 属性面板     │ │
│  │          │  │                          │  │              │ │
│  │ 容器类:  │  │  拖拽组件 → 流式排列      │  │ 选中组件后   │ │
│  │  查询区   │  │  嵌套容器 → 子组件放入    │  │ 显示:        │ │
│  │  表格区   │  │  调整顺序 → 自动重排      │  │  - 基础属性  │ │
│  │  表单区   │  │                          │  │  - 样式配置  │ │
│  │  详情区   │  │  ← 所见即所得预览 →       │  │  - 数据绑定  │ │
│  │          │  │                          │  │  - 事件动作  │ │
│  │ 控件类:  │  │                          │  │              │ │
│  │  文本输入 │  │                          │  │              │ │
│  │  下拉选择 │  │                          │  │              │ │
│  │  日期选择 │  │                          │  │              │ │
│  │  数字输入 │  │                          │  │              │ │
│  │  按钮    │  │                          │  │              │ │
│  │  标签    │  │                          │  │              │ │
│  └──────────┘  └──────────────────────────┘  └──────────────┘ │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  底部工具栏: [撤销] [重做] [预览] [保存草稿] [发布]          ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 交互方式

| 操作 | 说明 |
|------|------|
| **拖拽组件** | 从左侧组件库拖到画布，自动插入到流式布局中 |
| **嵌套容器** | 容器组件（查询区、表格区）可以嵌套子组件 |
| **调整顺序** | 拖拽已有组件调整位置，其他组件自动重排 |
| **选中编辑** | 点击组件，右侧属性面板显示该组件的配置项 |
| **样式配置** | 属性面板中可配置间距、颜色、字体等样式 |
| **数据绑定** | 属性面板中配置组件关联的模型字段和接口 |

### 3.3 组件体系

#### 容器组件

| 组件 | 用途 | 对应脚手架组件 |
|------|------|---------------|
| `PageContainer` | 页面根容器 | `.page` |
| `SearchForm` | 查询区容器 | `ListPage` 的 `#search` 插槽 |
| `DataTable` | 表格容器 | `DataTable` + `useListPage` |
| `FormContainer` | 表单容器 | `FormDialog` |
| `DetailContainer` | 详情容器 | `Descriptions` |
| `Toolbar` | 工具栏容器 | `ListPage` 的 `#actions` 插槽 |
| `RowActions` | 行操作容器 | `RowActions` + `RowMenuItem` |

#### 控件组件

| 组件 | 用途 | 对应脚手架组件 |
|------|------|---------------|
| `TextInput` | 文本输入 | `TextInput` |
| `NumberInput` | 数字输入 | `NumberInput` |
| `Select` | 下拉选择 | `Select` |
| `DictSelect` | 字典下拉 | `DictSelect` |
| `DeptSelect` | 部门选择 | `DeptSelect` |
| `UserSelect` | 用户选择 | `UserSelect` |
| `DateInput` | 日期选择 | `DateInput` |
| `DateRangePicker` | 日期范围 | `DateRangePicker` |
| `TreeSelect` | 树形选择 | `TreeSelect` |
| `Switch` | 开关 | `Switch` |
| `Upload` | 文件上传 | `Upload` |
| `ImageField` | 图片上传 | `ImageField` |
| `Textarea` | 多行文本 | `Textarea` |
| `Button` | 按钮 | `PermissionButton` |
| `Tag` | 标签 | `DictTag` / `StatusTag` |
| `Metric` | 读数 | `Metric` / `Odometer` |

### 3.4 设计器产出的 JSON 结构

```json
{
  "version": "1.0",
  "pageType": "list",
  "modelRef": "order_info",
  
  "layout": {
    "type": "PageContainer",
    "props": { "title": "订单列表", "desc": "管理所有订单" },
    "children": [
      {
        "type": "SearchForm",
        "props": { "columns": 3, "gap": 16 },
        "children": [
          {
            "type": "TextInput",
            "props": { "label": "订单号", "field": "orderNo", "queryType": "LIKE", "placeholder": "请输入订单号" }
          },
          {
            "type": "DictSelect",
            "props": { "label": "状态", "field": "status", "dictType": "order_status", "queryType": "=" }
          },
          {
            "type": "DateRangePicker",
            "props": { "label": "创建时间", "field": "createTime", "queryType": "BETWEEN" }
          }
        ]
      },
      {
        "type": "Toolbar",
        "props": {},
        "children": [
          { "type": "Button", "props": { "label": "新增", "permission": "order:add", "variant": "primary", "action": "create" } },
          { "type": "Button", "props": { "label": "导出", "permission": "order:export", "action": "export" } }
        ]
      },
      {
        "type": "DataTable",
        "props": { "selectable": true, "pagination": true, "pageSize": 10 },
        "columns": [
          { "field": "orderNo", "title": "订单号", "width": 150, "align": "left", "sortable": true },
          { "field": "amount", "title": "金额", "width": 100, "align": "right", "render": "money" },
          { "field": "status", "title": "状态", "width": 80, "render": "tag" },
          { "field": "createTime", "title": "创建时间", "width": 160, "render": "datetime" }
        ]
      },
      {
        "type": "RowActions",
        "props": { "priority": 2 },
        "children": [
          { "type": "Button", "props": { "label": "编辑", "permission": "order:edit", "action": "edit" } },
          { "type": "Button", "props": { "label": "详情", "permission": "order:view", "action": "view" } },
          { "type": "Button", "props": { "label": "删除", "permission": "order:delete", "action": "delete", "danger": true } }
        ]
      }
    ]
  }
}
```

### 3.5 属性面板

选中画布中的组件后，右侧属性面板显示：

```
┌─────────────────────────────────────────────────────────────┐
│  属性面板: DictSelect                                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌───────────────────────────────────────────────────────┐ │
│  │  基础属性                                              │ │
│  │  ────────────────────────────────────────────────────  │ │
│  │  标签:     [状态________]                              │ │
│  │  绑定字段: [status ▼]     ← 从关联模型的字段中选择     │ │
│  │  字典类型: [order_status ▼] ← 从已定义的字典中选择      │ │
│  │  查询方式: [= ▼]                                       │ │
│  │  是否必填: [○]                                         │ │
│  │  占位提示: [请选择状态______]                           │ │
│  └───────────────────────────────────────────────────────┘ │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐ │
│  │  样式配置                                              │ │
│  │  ────────────────────────────────────────────────────  │ │
│  │  宽度:     [自动 ▼]   ← 自动/固定宽度/跨列            │ │
│  │  间距:     [默认 ▼]                                   │ │
│  └───────────────────────────────────────────────────────┘ │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐ │
│  │  高级配置                                              │ │
│  │  ────────────────────────────────────────────────────  │ │
│  │  显示条件: [始终显示 ▼]   ← 可配置条件表达式           │ │
│  │  联动动作: [+ 添加联动]   ← 值变化时触发的动作         │ │
│  └───────────────────────────────────────────────────────┘ │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 四、代码生成引擎

### 4.1 模板引擎：FreeMarker

```
scaffold-generator/src/main/resources/templates/
├── backend/
│   ├── Controller.java.ftl      # 含 @RequiresPermission + @OperLog
│   ├── Service.java.ftl         # 接口定义
│   ├── ServiceImpl.java.ftl     # 含完整 CRUD + 校验 + 事务
│   ├── Mapper.java.ftl          # 注解 SQL，含分页/软删除/导出
│   ├── Entity.java.ftl          # 含审计字段
│   ├── DTO.java.ftl             # 写入请求
│   ├── VO.java.ftl              # 列表/详情视图
│   └── Query.java.ftl           # 查询参数
├── frontend/
│   ├── api.ts.ftl               # TypeScript API 模块
│   ├── ListView.vue.ftl         # 使用 ListPage + useListPage
│   ├── FormView.vue.ftl         # 使用 FormDialog + Field
│   ├── DetailView.vue.ftl       # 使用 Descriptions
│   └── types.ts.ftl             # 类型定义
├── sql/
│   ├── table.sql.ftl            # CREATE TABLE
│   └── menu.sql.ftl             # INSERT sys_menu
└── _shared/
    ├── auditFields.ftl          # 宏：审计字段
    ├── pagination.ftl           # 宏：分页查询
    └── csvExport.ftl            # 宏：CSV 导出
```

### 4.2 生成代码规范

#### 后端 Controller 示例

```java
package com.scaffold.{module}.controller;

import com.scaffold.common.domain.PageResult;
import com.scaffold.common.domain.R;
import com.scaffold.framework.annotation.OperLog;
import com.scaffold.framework.annotation.RequiresPermission;
import com.scaffold.{module}.domain.{ClassName};
import com.scaffold.{module}.domain.dto.{ClassName}DTO;
import com.scaffold.{module}.domain.vo.{ClassName}VO;
import com.scaffold.{module}.domain.query.{ClassName}Query;
import com.scaffold.{module}.service.{ClassName}Service;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/{module}/{resource}")
@RequiredArgsConstructor
public class {ClassName}Controller {

    private final {ClassName}Service {resource}Service;

    @GetMapping
    @RequiresPermission("{module}:{resource}:list")
    public R<PageResult<{ClassName}VO>> list({ClassName}Query query) {
        return R.ok({resource}Service.selectList(query));
    }

    @GetMapping("/{id}")
    @RequiresPermission("{module}:{resource}:list")
    public R<{ClassName}VO> getById(@PathVariable Long id) {
        return R.ok({resource}Service.selectById(id));
    }

    @PostMapping
    @RequiresPermission("{module}:{resource}:add")
    @OperLog("{tableComment}新增")
    public R<Void> add(@Validated @RequestBody {ClassName}DTO dto) {
        {resource}Service.insert(dto);
        return R.ok();
    }

    @PutMapping("/{id}")
    @RequiresPermission("{module}:{resource}:edit")
    @OperLog("{tableComment}修改")
    public R<Void> update(@PathVariable Long id, @Validated @RequestBody {ClassName}DTO dto) {
        {resource}Service.update(id, dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("{module}:{resource}:remove")
    @OperLog("{tableComment}删除")
    public R<Void> remove(@PathVariable Long id) {
        {resource}Service.deleteById(id);
        return R.ok();
    }

    @GetMapping("/export")
    @RequiresPermission("{module}:{resource}:export")
    @OperLog("{tableComment}导出")
    public void export({ClassName}Query query, HttpServletResponse response) {
        {resource}Service.export(query, response);
    }
}
```

#### 前端列表页示例

```vue
<script setup lang="ts">
import ListPage from '@/components/business/ListPage.vue'
import { useListPage } from '@/composables/list'
import { {resource}Api } from '@/api/{module}/{resource}'
import type { Column } from '@/components/ui/DataTable.vue'

const columns: Column[] = [
  // 根据模型字段定义生成
  { field: 'orderNo', title: '订单号', width: 150 },
  { field: 'amount', title: '金额', width: 100, align: 'right' },
  { field: 'status', title: '状态', width: 80, render: 'tag' },
]

const list = useListPage({
  query: { orderNo: undefined, status: undefined },
  fetch: (params) => {resource}Api.list(params),
  exporter: (params) => {resource}Api.export(params),
  exportName: '{tableComment}.csv',
})
</script>

<template>
  <ListPage title="{tableComment}管理" :list="list" :columns="columns">
    <template #search="{ query }">
      <Field label="订单号" wide>
        <TextInput v-model="query.orderNo" placeholder="请输入订单号" />
      </Field>
      <Field label="状态">
        <DictSelect dict-type="order_status" v-model="query.status" />
      </Field>
    </template>
    <template #actions>
      <PermissionButton perm="{module}:{resource}:add" @click="list.openForm()">新增</PermissionButton>
      <PermissionButton perm="{module}:{resource}:export" @click="list.export()">导出</PermissionButton>
    </template>
  </ListPage>
</template>
```

### 4.3 生成文件路径规则

```typescript
const fileRules = {
  backend: {
    controller: (module, cls) => `backend/src/main/java/com/scaffold/${module}/controller/${cls}Controller.java`,
    service: (module, cls) => `backend/src/main/java/com/scaffold/${module}/service/${cls}Service.java`,
    serviceImpl: (module, cls) => `backend/src/main/java/com/scaffold/${module}/service/impl/${cls}ServiceImpl.java`,
    mapper: (module, cls) => `backend/src/main/java/com/scaffold/${module}/mapper/${cls}Mapper.java`,
    entity: (module, cls) => `backend/src/main/java/com/scaffold/${module}/domain/${cls}.java`,
    dto: (module, cls) => `backend/src/main/java/com/scaffold/${module}/domain/dto/${cls}DTO.java`,
    vo: (module, cls) => `backend/src/main/java/com/scaffold/${module}/domain/vo/${cls}VO.java`,
  },
  frontend: {
    api: (module, resource) => `frontend/src/api/${module}/${resource}.ts`,
    types: (module, resource) => `frontend/src/types/${module}/${resource}.ts`,
    listView: (module, resource) => `frontend/src/views/${module}/${resource}/index.vue`,
    formView: (module, resource) => `frontend/src/views/${module}/${resource}/form.vue`,
    detailView: (module, resource) => `frontend/src/views/${module}/${resource}/detail.vue`,
  },
  sql: {
    table: (tableName) => `sql/pages/${tableName}_table.sql`,
    menu: (tableName) => `sql/pages/${tableName}_menu.sql`,
  }
};
```

---

## 五、增量发布机制

### 5.1 发布流程

```
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│ 设计页面  │───▶│ 生成代码  │───▶│ 哈希对比  │───▶│ 增量写入  │───▶│ 构建部署  │
└──────────┘    └──────────┘    └──────────┘    └──────────┘    └──────────┘
                                  │
                                  ▼
                           只更新变更的文件
                           跳过未变更的文件
```

### 5.2 文件更新策略

| 文件类型 | 更新策略 | 说明 |
|----------|----------|------|
| Controller/Service/Mapper | 覆盖 | 每次发布完全重新生成 |
| Vue 页面 | 覆盖 | 每次发布完全重新生成 |
| API 文件 | 覆盖 | 每次发布完全重新生成 |
| 路由配置 | 自动注册 | 菜单写入 sys_menu 后，前端路由自动生效 |
| 菜单 SQL | 增量执行 | INSERT IGNORE INTO sys_menu |
| 建表 SQL | 条件执行 | CREATE TABLE IF NOT EXISTS |
| pom.xml / package.json | 不更新 | 项目初始化后固定 |
| 公共组件/样式 | 不更新 | 框架升级时单独处理 |

### 5.3 自定义代码保护

```java
// 开发者可在生成文件中添加保护标记
// CUSTOM-START
// 自定义业务逻辑...
// CUSTOM-END

// 重新生成时，保护标记内的代码被保留
```

### 5.4 版本与回滚

支持三级回滚：
- **文件级**：回滚单个文件的某次变更
- **页面级**：回滚某个页面相关的所有文件
- **发布级**：回滚某次发布的所有变更

---

## 六、项目初始化流程

### 6.1 首次创建项目

```
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│ 创建项目  │───▶│ 克隆脚手架│───▶│ 生成配置  │───▶│ 初始化DB │───▶│ 项目就绪  │
└──────────┘    └──────────┘    └──────────┘    └──────────┘    └──────────┘
                     │                                │
                     ▼                                ▼
              ┌──────────────┐                ┌──────────────┐
              │ 复制固定代码  │                │ 执行初始化SQL │
              │ - common     │                │ - V1 建表     │
              │ - framework  │                │ - V2 种子数据  │
              │ - system     │                │ - V3 系统菜单  │
              │ - admin      │                │               │
              └──────────────┘                └──────────────┘
```

### 6.2 初始化内容

| 步骤 | 操作 | 产出 |
|------|------|------|
| 1 | 生成项目配置 | pom.xml、package.json、application.yml |
| 2 | 复制固定代码 | scaffold-common、scaffold-framework、系统模块 |
| 3 | 初始化数据库 | 执行 V1~V3 SQL，创建系统表 |
| 4 | 初始化 Git | 创建仓库，首次提交 |
| 5 | 构建项目 | Maven 构建、npm install |

---

## 七、技术架构总览

```
┌─────────────────────────────────────────────────────────────────┐
│                        接入层 (Nginx)                           │
│  域名路由: {tenant}.platform.com                                │
│  静态资源: OSS/{tenant}/{project}/dist                          │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                    低代码管理平台                                 │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ │
│  │ 租户管理 │ │ 项目管理 │ │ 模型设计 │ │ 页面设计 │ │ 发布管理 │ │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘ │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ 代码生成引擎 (FreeMarker) + 版本管理 (Git)                  ││
│  └─────────────────────────────────────────────────────────────┘│
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                    数据存储层                                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐       │
│  │ 平台库    │  │ 租户业务库│  │ Redis    │  │ Git仓库  │       │
│  │ (元数据) │  │ (动态路由)│  │ (缓存)   │  │ (代码)   │       │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘       │
└─────────────────────────────────────────────────────────────────┘
```

---

## 八、开发排期

| 阶段 | 周期 | 交付物 |
|------|------|--------|
| **P0: 多租户基础** | 3周 | TenantContext、TenantFilter、MyBatis 拦截器、tenant_id 字段改造 |
| **P1: 项目 + 模型** | 3周 | 项目管理、模型设计器、字段配置 UI |
| **P2: 可视化设计器** | 4周 | 拖拽画布 + 属性面板 + 组件库 + 实时预览 |
| **P3: 代码生成升级** | 3周 | FreeMarker 模板、完整 CRUD 生成、增量发布机制 |
| **P4: 发布与版本** | 2周 | Git 集成、版本管理、回滚机制 |
| **P5: 联调测试** | 1周 | 端到端测试、性能优化 |

**总计约 16 周（4 个月）**

---

## 九、关键风险与应对

| 风险 | 影响 | 应对措施 |
|------|------|---------|
| tenant_id 遗漏导致数据泄露 | 严重 | MyBatis TenantLineInnerInterceptor 自动注入 |
| 生成代码质量不稳定 | 影响业务 | 以 SystemManagementService 为参照，模板必须生成同等质量代码 |
| 设计器复杂度失控 | 周期延长 | 先支持列表页+表单页，流程设计器放第二期 |
| 脚手架升级下发困难 | 版本碎片化 | 前端组件 npm 包发布，后端 Maven BOM 管理 |
| 自定义代码被覆盖 | 开发者不满 | CUSTOM-START/END 标记保护机制 |

---

## 十、下一步行动

1. **确认方案**：是否同意上述架构设计？
2. **技术选型确认**：
   - 代码存储：Git 仓库 vs 数据库 BLOB？
   - 设计器引擎：自研 vs 基于阿里 LowCodeEngine？
3. **优先级确认**：先做模型设计器还是页面设计器？
4. **创建项目骨架**：搭建低代码平台的基础结构
