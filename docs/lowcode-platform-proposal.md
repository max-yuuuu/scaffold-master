# 元搭 · 低代码平台完整方案

> 元搭（Yuán Dā）是基于 scaffold-master 脚手架架构构建的多租户低代码平台。支持可视化设计（拖拽 + 配置融合）、完整 CRUD 代码生成、发布产物推送到项目 Git 仓库并在平台内合并。平台是研发期工具，不托管生成项目的运行时。后续演进 AI 无代码方向。

---

## 〇、仓库与基线

**2026-11-03 现状**

| 项 | 值 |
|----|-----|
| 权威仓库 | `https://github.com/max-yuuuu/lowcode-ai.git`（git remote 名 `lowcode-ai`） |
| 基线 HEAD | `69631e7` on `main`，含 `feat/framework-global-sync`（前端组件重写）+ `feat/deploy-docker`（Docker 部署）+ 方案文档 + PR 1 多租户地基 + 「元搭」定名 |
| 上游 PR | 不再等合并；两个 PR 分支的改动已并入本地 main 后推 lowcode-ai |

**scaffold-master 的三重身份**（不只是模板来源）

```
scaffold-master = ① 低代码平台自身（同一个 app）
                   ├─ 平台前端是 scaffold-vue 宿主：低代码视图新增到 src/views/lowcode/**，
                   │  组件与珍珠玻璃主题来自 @scaffold/ui 包（与生成项目同一个包）
                   └─ 平台后端新增 scaffold-lowcode 模块，复用 common / framework / system

                = ② 生成项目的初始框架（裁剪快照，不含低代码平台自身）
                   ├─ 项目初始化推一份 A 档 app 壳到其仓库；组件不在 A 档里，是 npm 依赖
                   └─ 页面发布只增改 B 档业务文件，不再动 A 档

                = ③ 上游开源仓库
                   └─ 现由 lowcode-ai 替代作为主推进；scaffold-master 保留同步通道
```

**关于「fork scaffold-vue」：该口径作废（2026-11-03 二次修订）**

早期版本（`ba3e0dc` → `742eb0e`）在此处列过「A. fork 整个 app / B. 抽 `@scaffold/ui` npm 包 / C. git subtree」三选一，并把 A 定为起步方案。**那三条路的前提是"低代码平台是独立项目"，需要跨仓库复用 scaffold-vue 的组件**；这个前提已被 §〇之二 第 1 条作废 —— 平台自身不 fork、不 subtree。

### 组件物料统一：抽 `@scaffold/ui`（2026-11-03 三次修订，**已定为既定路线**）

> 一度把抽包降级为"项目多到 diff 审不过来才做"，这个判断错了。生成项目用的是**同一套组件物料**，统一性只能在包里保证：拷源码进项目意味着每个项目一份副本、修复靠 diff 人肉搬。所以组件库**必须**是包，平台与生成项目都依赖它。

**规模与真实障碍**（实测于当前 main）：

| 项 | 数量 | 说明 |
|---|---|---|
| `.vue` 组件 / composable | 73 / 17 | 包体量 |
| `@/components` 引用点 | **148 处 / 64 文件** | 组件内部互引 83、页面引用 62 —— 改路径是机械活 |
| **对 app 层的 import** | **28 个文件** | 这才是真正的障碍：外部包不能调用宿主 app |
| └ `@/api` | 14 | 硬耦合：`UserSelect` 直接 `systemApi`；`PostLadder`/`DictSwatchbook`/`PasswordDialog`/`CommandPalette` 同 |
| └ `@/data` | 10 | 多数只是类型（`DeptTree` 仅 `import type { Dept }`），但 `@/data` 还装品牌常量，属 app 不属包 |
| └ `@/directives/permission` | 8 | `PermissionButton`/`RowMenuItem` 依赖 `v-perm`，指令又依赖权限数据源 |
| └ `@/utils/*` | 8 | `permissionGraph`/`download`/`clipboard` 等，多数可随包走 |

**解法：包提供注入点，不在组件里 import 宿主 API**

```ts
// 宿主 app（平台自身与每个生成项目）的 main.ts
import { installScaffoldUi } from '@scaffold/ui'
import '@scaffold/ui/theme.css'                       // 珍珠玻璃：tokens.css 及组件样式随包走

installScaffoldUi(app, { http, permission, dict })    // 三个能力由宿主提供，包内 inject 取用
```

包面 = **AGENTS.md 那份「必须优先复用的组件清单」全量进包**（`ListPage`/`useListPage`/`DataTable`/`SearchForm`/`FormDialog`/`RowActions`/`PermissionButton`/`DictSelect`/`DeptTree`/`UserSelect`/`PermissionTree`/`TreeSelect`/`DateInput`/`Upload`/`Descriptions` 及 `usePopover`/`dateValue`/`upload` 等 composable）。带数据的组件也进包，靠注入点拿数据 —— 否则生成模板要替每个项目重写一篇 `DictSelect`，违背统一组件原则。

样式随包走 7 个 css 文件：`tokens.css`（颜色/间距/圆角变量）与 `base/components/layout/materials/pages/instruments.css` 合并进 `theme.css` 单一入口。

**不进包**（平台与演示专属，留在 scaffold-vue）：`GeneratorPipeline`、`OrgCanvas`、`MenuStarMap`、`PermissionMatrix`、`PermissionTrace`、`DictSwatchbook`、`AskDock`、`InsightPanel`，以及设计器画布/属性面板/组件面板/合并请求 diff 视图。这条边界在抽包时一并划清，否则生成项目装着一堆用不上的组件。

**仓库与发布**：包放本仓库 `packages/scaffold-ui` + npm workspaces（**不新建仓库** —— 平台与包同步演进，跨仓库来回改代价大于收益）。发布走 **GitHub git tag 依赖**：`"@scaffold/ui": "github:max-yuuuu/lowcode-ai#v1.0.0"`，零 registry 运维，版本号即 tag，与 `lc_project.base_scaffold_sha` 天然对齐。凭据仍在主机 `~/.npmrc`，不进数据库。

**时机是硬约束**：抽包必须**先于 PR 5（FreeMarker 模板）**。模板要决定生成的 `.vue` 里写 `@/components/...` 还是 `@scaffold/ui`；先出模板再抽包，等于把所有历史项目的生成物再改一遍。

**红利**：A 档不再包含组件源码与样式（见 §5.2、§6.4），只剩 app 壳 —— "下发脚手架升级"从审一堆源码 diff 降为 **bump 一个版本号**。

---

## 〇之二、平台边界（2026-11-03 修订，覆盖此前口径）

这几条推翻了本文档早先的写法，实现时以本节为准：

| # | 口径 | 说明 |
|---|------|------|
| 1 | **本平台就是这个仓库** | 低代码能力直接长在 scaffold-master / lowcode-ai 仓库里，新增 Maven 模块承载（见 §4.4）。此前"平台是独立项目、`lc_*` 表落另一个仓库"的口径作废。 |
| 2 | **平台不托管项目运行时** | 平台是**研发期工具**，不是在线服务。发布 = 生成代码 + 提交 + 推送到指定 Git 仓库（§五），运行由使用者在自己环境部署。平台不自建、不编排、不复用生成项目的容器。 |
| 3 | **租户区分不靠泛域名** | `{tenant}.platform.com` 与 OSS 已归入 §7.2 远期形态。当前形态下 `lc_tenant.tenant_code` 只做展示与外部标识（与实际 DDL 的列注释一致），租户由登录态决定。 |
| 4 | **Git 凭据不进数据库** | 用主机级凭据（本机 `~/.ssh` 密钥），`lc_project` 只存 `repo_url` / 分支名。理由：token 入库会随 MySQL 卷一起被备份和导出，加密主密钥自身又在配置里，是循环依赖。将来平台要给多租户各带仓库权限时，再引入 `lc_tenant_credential`。 |
| 5 | **设计器自研，不引引擎** | 见 §3.6 的选型依据。 |
| 6 | **合并不自动，平台内做成 PR 式功能** | 发布只推到生成分支；进主干由人在平台内显式发起「合并请求」，选源分支 → 目标分支、看文件差异、确认后才合并（§5.3）。此前"发布即自动合并进 main"的口径作废。 |
| 7 | **组件物料统一走 `@scaffold/ui` 包** | 平台与每个生成项目都依赖同一个包（含珍珠玻璃主题），组件源码不再拷进项目。见 §〇 抽包规格与 §6.4。 |

---

## 一、核心概念

### 1.1 层级关系

```
租户 Tenant（多租户隔离）
  └── 项目 Project（= 租户名下的一个 Git 仓库，平台不托管其运行时）
        ├── 模型 Model（数据模型定义 → 对应数据库表）
        │     └── 字段 Field（字段定义 → 对应表列）
        └── 页面 Page（关联模型，可视化设计，增量发布）
              └── 版本 Version（每次发布记录快照，支持回滚）
```

### 1.2 架构定位

```
┌──────────────────────────────────────────────────────────────┐
│                低代码平台（= 本仓库新增模块）                    │
│                                                               │
│  租户管理 · 项目管理 · 可视化设计器 · 代码生成引擎 · 发布管理  │
│                                                               │
│  技术栈：Spring Boot 3.5 + Vue 3 + TypeScript + FreeMarker   │
└──────────────────────────┬───────────────────────────────────┘
                           │ 生成代码 → git commit → push 到项目仓库
                           ▼
┌──────────────────────────────────────────────────────────────┐
│        项目 Git 仓库（租户维护，平台不托管其运行时）             │
│                                                               │
│  scaffold-common     → 公共（R / PageResult / BusinessException）│
│  scaffold-framework  → 框架（认证 / 权限 / 数据范围 / 审计）   │
│  scaffold-system     → 系统管理（用户 / 角色 / 菜单 / 字典）   │
│  scaffold-business   → 生成的业务代码（完整 CRUD，单模块多包） │
│  scaffold-admin      → 启动入口                              │
│                                                               │
│  前端：Vue 3 + TypeScript + Vite + 珍珠玻璃主题              │
│                                                               │
│  由使用者 clone 后自行构建部署；平台不负责跑起来               │
└──────────────────────────────────────────────────────────────┘
```

### 1.3 固定代码 vs 动态代码

| 分类 | 内容 | 生成时机 | 更新策略 |
|------|------|----------|----------|
| **固定代码**（A 档） | 后端 `scaffold-common` / `framework` / `system` 源码、app 壳（`main.ts`、路由与布局、登录页、工作台）、`pom.xml`、`package.json`、Dockerfile | 项目初始化 | 不随页面发布更新，人工可自由修改。**例外：前端组件与珍珠玻璃样式不在 A 档**，走 `@scaffold/ui` npm 依赖，升级 = bump 版本号（§〇） |
| **动态代码**（B 档） | 业务 Controller/Service/Mapper、业务页面 Vue、业务 API | 页面发布 | 每次发布整文件重新生成，推到生成分支 |
| **扩展点**（C 档） | `service/{Cls}Extension.java`、`views/{m}/overrides/*.vue` | 首次发布生成空骨架 | 之后平台不再生成，业务逻辑写这里 |

三档的完整定义与判定见 §5.2。路由与菜单不属任一档：不生成文件，靠 `sys_menu` + `import.meta.glob` 自动注册。

### 1.4 模型的定位：生成器的输入，不是业务功能

`lc_model` / `lc_field` 在平台里有 CRUD UI，很容易被误读成"模型管理"是一个业务模块。**它不是**。准确定位是**代码生成器的输入（元模型 / 期望态）**：

| | 平台侧 | 生成项目侧 |
|---|---|---|
| 模型的形态 | 数据库里的元数据行（`lc_model` + `lc_field`） | **不存在"模型"这个东西**，存在的是被生成的实体、表、接口 |
| 有无运行时语义 | 有（平台自己的业务表） | 平台不参与；模型不产生任何运行时对象 |
| 变更的含义 | 编辑期望态 | 发布 = 把期望态编译成代码推到 `gen_branch` |

由此推出**一条判据**，用来决定需求该进模型还是进扩展点：

> **「这段逻辑能不能只靠字段名 + 字段类型推导出来？」**
> 能 → 进模型（B 档生成）。例：列、查询条件、表单控件、校验、字典翻译、导出列、外键下拉。
> 不能 → 进 C 档扩展点手写。例：状态流转的业务规则、跨实体事务、调外部系统、聚合计算、审批链。

这条判据即 §4.5 接口生成边界的基础。

**期望态与实际态必然分叉**：模型改了，项目里的实体可能已被人工改过（C 档，或 B 档被违规手改）。分叉不是 bug，是这套设计的常态 —— 处理手段是 diff 审查（§5.3 合并请求），而不是"让两边保持一致"。平台不做一致性校验、不去检测项目实体是否偏离模型（做不到，也不该做）。

### 1.5 模型 ↔ 页面的交互契约

两者的耦合只发生在**字段编码（`field_code`）这一个符号**上，分三层：

```
① 设计期（平台内，页面唯一的数据依赖）
   设计器属性面板的「绑定字段」下拉
      └─ 数据来源：GET /api/lowcode/projects/{pid}/models/{modelCode}/fields
      └─ 字典下拉来自 lc_field.dict_type → sys_dict；关联下拉来自 lc_field.ref_model_id
   页面不猜字段：design_json 里出现的每个 field 值都必须是该模型的真实 field_code

② 生成期（同一份模板同时吃 design_json + lc_field）
   模型决定：实体属性、Mapper、列表列取数与类型、查询条件、表单控件类型(form_type)、
             校验(is_required)、导出列、权限点(permission_prefix + list|add|edit|remove|export)
   页面决定：布局、组件选择、区块顺序、样式、动作与显隐条件
   —— 两者在 FreeMarker 模板里合流：页面是"形"，模型是"料"

③ 运行期（在生成项目里，平台不在链路上）
   index.vue → src/api/{module}.ts → Controller → Service → Mapper → 物理表
```

基数约束（定死，否则设计器与模板会各自发散）：

- **一个页面一个主模型**（`lc_page.model_id`）；跨模型只通过 `REF` 字段的外键下拉表达，**不生成 join 查询**；
- **一个模型多个页面**：列表页 + 详情 + 弹窗表单是三个 `lc_page`，共享同一套字段与接口；
- **`DASHBOARD` / 自定义页允许 `model_id = NULL`**：只生成壳子与路由，取数全人工写在 overrides；其余 `page_type` 必须绑模型，否则字段与接口无从推导。

**反向引用检查**：删字段 / 下线模型前必须知道有没有页面在用。做法是保存 `design_json` 时解析出引用的 `field_code` 列表写入 `lc_page.ref_fields`（§2.2），删除时按 `(model_id, field_code)` 反查，命中则拒绝并列出受影响页面 —— 不做 JSON 全文搜索，更不能等发布时模板报错才发现。

---

## 二、多租户设计

### 2.1 数据隔离策略

| 数据类别 | 隔离方式 | 理由 |
|----------|----------|------|
| **平台元数据**（租户、项目、模型、页面定义） | 与系统管理**同库**，`lc_` 表前缀区分（✅ PR 1 已按此落地：`lc_tenant` 与 `sys_*` 同在 `scaffold_system`） | 数据量小、无需额外数据源配置；平台自身就是单库部署 |
| **系统管理数据**（用户、角色、菜单、字典） | 同库，`tenant_id` 字段隔离 | MyBatis 拦截器自动注入（PR 2+） |
| **业务数据**（租户创建的业务表） | 生成项目自带库；平台侧通过 `lc_project.db_*` 连接信息访问，每项目一库 | 数据量不可预测，独立备份/恢复。本期平台不托管运行时，建表 SQL 随代码推到项目仓库，由项目自己的迁移执行 |

### 2.2 核心表设计

> **实现状态（2026-11-03）**：`lc_tenant` 与 `sys_*` 的 `tenant_id` 改造已在 PR 1 落地，**权威定义以 [scaffold-system/sql/V1__init_schema.sql](../scaffold-system/sql/V1__init_schema.sql) 为准**（本文档早期草稿的 `lc_tenant` 片段缺 `deleted`、`remark` 列与 `idx_lc_tenant_status` 索引，已按实际 DDL 校正）。`lc_project` / `lc_model` / `lc_field` / `lc_page` / `lc_page_version` / `lc_publish_log` / `lc_merge_request` / `lc_merge_request_file` 尚未建表，下面的 DDL 是 PR 2 的设计稿，落库前需按 §〇之二 与 §5.3 的口径复核。

```sql
-- ========== 平台库 ==========

-- 租户表（✅ 已实现，字段与 V1__init_schema.sql 一致）
CREATE TABLE lc_tenant (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '租户 ID',
    tenant_code VARCHAR(64) NOT NULL COMMENT '租户编码，用于展示与外部标识',
    tenant_name VARCHAR(128) NOT NULL COMMENT '租户名称',
    contact_name VARCHAR(64) COMMENT '联系人姓名',
    contact_phone VARCHAR(32) COMMENT '联系人电话',
    max_projects INT NOT NULL DEFAULT 5 COMMENT '最大项目数（配额）',
    expire_time DATETIME COMMENT '到期时间，NULL 表示不限',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '租户状态：1 正常，0 停用',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：1 删除，0 正常',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64),
    update_by VARCHAR(64),
    PRIMARY KEY (id),
    UNIQUE KEY uk_lc_tenant_code (tenant_code),
    KEY idx_lc_tenant_status (status)
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
    
    -- 数据源（PR 3a 实际建表时未落：这些列到 PR 5 生成 application.yml 才有读写方，届时以 ALTER 补）
    db_host VARCHAR(128) COMMENT '数据库地址',
    db_port INT DEFAULT 3306 COMMENT '端口',
    db_name VARCHAR(64) COMMENT '数据库名',
    db_username VARCHAR(64) COMMENT '账号',
    db_password VARCHAR(256) COMMENT '密码(加密)',
    
    -- 代码仓库（租户维护，平台推送目标；凭据不落库，见 §〇之二 第 4 条）
    repo_url VARCHAR(256) NOT NULL COMMENT '仓库地址，仅 https:// 或 git@ 形式',
    main_branch VARCHAR(64) NOT NULL DEFAULT 'main' COMMENT '人工主干分支，平台合并目标',
    gen_branch VARCHAR(64) NOT NULL DEFAULT 'platform-generated' COMMENT '平台生成分支，平台自有历史',
    workspace_path VARCHAR(256) COMMENT '平台本机 clone 相对路径（持久化卷内）',
    last_commit_sha VARCHAR(40) COMMENT '平台最后一次成功推送的提交号',
    base_scaffold_sha VARCHAR(40) COMMENT '项目初始快照取自本仓库的提交号，判断落后多少、下发 A 档升级的起点',

    -- 部署（远期：仅 STANDALONE 在本期实现，DOCKER/K8S 属平台托管形态，暂不落地）
    deploy_mode VARCHAR(32) DEFAULT 'STANDALONE' COMMENT 'STANDALONE/DOCKER/K8S，本期仅 STANDALONE',
    deploy_host VARCHAR(128) COMMENT '部署域名',
    
    -- 状态（生命周期与仓库同步合并为一条状态机，避免 init_status / sync_status 两套状态互相矛盾）
    sync_status TINYINT NOT NULL DEFAULT 0 COMMENT '项目生命周期：0 未初始化 1 初始化中 2 就绪 3 工作区脏 4 与远端分叉 5 仓库不可达',
    init_time DATETIME COMMENT '固定代码初始化完成时间',
    last_sync_time DATETIME COMMENT '最近一次同步远端时间',
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
    
    -- config / version 本期不建：config 没有读取方，版本历史属于页面侧（lc_page_version），
    -- 模型自身的历史靠发布 diff 看。见 §10.3 PR 3b。
    status TINYINT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：模型下线要留痕，页面可能还在引用它的字段',
    
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
    field_type VARCHAR(32) NOT NULL COMMENT 'STRING/TEXT/INTEGER/DECIMAL/DATE/DATETIME/ENUM/FILE/REF —— TEXT 为 PR 3b 扩充：后台的备注/说明/富文本最高频，只靠 varchar 撑到几 K 会让生成的列类型与底座不一致',
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
    ref_fields JSON COMMENT '该页面引用的字段编码快照，保存设计时解析写入，供删字段/下线模型时反查拦截（见 §1.5）',
    
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
    tenant_id BIGINT NOT NULL COMMENT '租户 ID（冗余自页面，供租户拦截器统一过滤）',
    page_id BIGINT NOT NULL,
    version INT NOT NULL,
    design_snapshot JSON NOT NULL COMMENT '页面配置快照',
    files_snapshot JSON COMMENT '生成文件快照：路径 → 内容哈希',
    commit_sha VARCHAR(40) COMMENT '该版本对应的提交号，回滚定位用',
    change_desc VARCHAR(512),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    create_by VARCHAR(64),
    UNIQUE KEY uk_page_version (page_id, version),
    KEY idx_page_version_tenant (tenant_id)
) COMMENT '页面版本表';

-- 发布日志表
CREATE TABLE lc_publish_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL COMMENT '租户 ID（冗余自项目，供租户拦截器统一过滤）',
    project_id BIGINT NOT NULL,
    page_id BIGINT NOT NULL,
    version INT NOT NULL,
    publish_type VARCHAR(32) NOT NULL COMMENT 'CREATE/UPDATE/DELETE/ROLLBACK',
    publish_status TINYINT NOT NULL COMMENT '发布结果：0失败 1成功 2无变更（发布只到生成分支，不含合并动作）',
    files_generated JSON COMMENT '生成的文件列表',
    files_added INT COMMENT '新增文件数',
    files_modified INT COMMENT '修改文件数',
    files_deleted INT COMMENT '删除文件数',
    commit_sha VARCHAR(40) COMMENT '本次发布在生成分支上的提交号',
    tag_name VARCHAR(64) COMMENT '发布的版本标记名，合并与回滚的锚点',
    branch VARCHAR(64) COMMENT '目标分支名（生成分支）',
    remote_url VARCHAR(256) COMMENT '推送时的仓库地址快照',
    error_message TEXT,
    publish_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    publish_by VARCHAR(64),
    INDEX idx_project_page (project_id, page_id),
    INDEX idx_publish_log_tenant (tenant_id)
) COMMENT '发布记录表';

-- 合并请求表（平台内 PR 式功能，见 §5.3）
CREATE TABLE lc_merge_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL COMMENT '租户 ID',
    project_id BIGINT NOT NULL COMMENT '项目 ID',
    title VARCHAR(128) NOT NULL COMMENT '合并请求标题',
    description VARCHAR(512) COMMENT '变更说明',
    source_branch VARCHAR(64) NOT NULL COMMENT '来源分支，默认项目生成分支',
    target_branch VARCHAR(64) NOT NULL COMMENT '目标分支，默认项目主干',
    merge_mode VARCHAR(16) NOT NULL DEFAULT 'MERGE_COMMIT' COMMENT 'MERGE_COMMIT 创建合并提交 / FF_ONLY 仅快进',
    status VARCHAR(16) NOT NULL COMMENT 'DRAFT草稿 CHECKING检查中 MERGEABLE可合并 CONFLICT冲突 STALE目标已前进 MERGED已合并 CLOSED已关闭',
    base_sha VARCHAR(40) COMMENT '检查时两分支的共同祖先提交号',
    source_sha VARCHAR(40) COMMENT '检查时来源分支顶端提交号',
    target_sha VARCHAR(40) COMMENT '检查时目标分支顶端提交号，执行前须重取比对',
    merge_commit_sha VARCHAR(40) COMMENT '合并成功后目标分支上的合并提交号',
    files_changed INT COMMENT '变更文件数',
    check_message VARCHAR(512) COMMENT '检查结论，冲突或不可合并时说明原因',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64),
    update_by VARCHAR(64),
    KEY idx_mr_project_status (project_id, status),
    KEY idx_mr_tenant (tenant_id)
) COMMENT '合并请求表';

-- 合并请求文件表（diff 列表快照，按请求重建，不长期有效）
CREATE TABLE lc_merge_request_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL COMMENT '租户 ID',
    merge_request_id BIGINT NOT NULL COMMENT '关联合并请求',
    file_path VARCHAR(512) NOT NULL COMMENT '文件路径（来源侧新路径）',
    old_path VARCHAR(512) COMMENT '重命名前的旧路径，非重命名为空',
    change_type VARCHAR(16) NOT NULL COMMENT 'ADD新增 MODIFY修改 DELETE删除 RENAME重命名',
    ownership_tier CHAR(1) NOT NULL COMMENT '文件所有权档：A固定 B纯生成 C扩展点，见 §5.2',
    additions INT COMMENT '新增行数',
    deletions INT COMMENT '删除行数',
    is_binary TINYINT NOT NULL DEFAULT 0 COMMENT '是否二进制文件，1 时不渲染 diff 正文',
    is_conflict TINYINT NOT NULL DEFAULT 0 COMMENT '三方合并是否冲突，1 时平台不自动取舍',
    UNIQUE KEY uk_mr_file (merge_request_id, file_path)
) COMMENT '合并请求变更文件表';
```

```sql
-- PR 2 新增：租户级参数。读写条件由租户拦截器注入，接口不收 tenant_id 入参
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
) COMMENT '租户级参数';
```

内置两条，PR 3 建项目与 PR 4 发布读它作为该租户下所有项目的分支默认值：`project.main_branch`（生成项目主干，人工合并的落点）与 `project.gen_branch`（平台发布只推这条分支）。

### 2.2 之二 字段类型映射（PR 3b 定，代码为准）

映射只定义一次：`com.scaffold.lowcode.model.FieldType`。库里 `java_type` / `column_type` 存的是**保存时算出来的结果**，模板生成时直接读，不再二次推导；前端通过 `GET /api/lowcode/models/meta` 取同一份表，界面上能看到「选了这个类型会生成什么」。

| field_type | Java | 列类型（默认） | 默认控件 | 约束 |
| --- | --- | --- | --- | --- |
| STRING | String | varchar(255) | INPUT | 长度可改 |
| TEXT | String | text | TEXTAREA | PR 3b 扩充 |
| INTEGER | Integer | int | NUMBER | 自增主键升级为 Long / bigint |
| DECIMAL | BigDecimal | decimal(18,2) | NUMBER | 精度与小数位可改 |
| DATE | LocalDate | date | DATE | — |
| DATETIME | LocalDateTime | datetime | DATETIME | — |
| ENUM | String | varchar(64) | SELECT | 必须挂当前租户真实存在的字典 |
| FILE | String | varchar(512) | UPLOAD | 存路径或 URL |
| REF | Long | bigint | REF | 必须指向同项目模型；指向自身合法（树表 parent_id） |

> **PR 5 的连带项**：`GeneratorService.javaType()` 是「导入已有表 → Java 类型」的反向映射，目前把 `date` 也算成 `LocalDateTime`。模板改造时必须与上表对齐，否则同一个 `date` 列在两条链路上得到不同答案。

### 2.3 多租户实现

```java
// scaffold-common 新增（✅ PR 1 已落 TenantContext）
public class TenantContext {
    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> PROJECT_ID = new ThreadLocal<>();

    public static void set(Long tenantId, Long projectId) { ... }
    public static Long getTenantId() { return TENANT_ID.get(); }
    public static Long getProjectId() { return PROJECT_ID.get(); }
    public static void clear() { TENANT_ID.remove(); PROJECT_ID.remove(); }
}

// scaffold-framework 新增（✅ PR 2 已落）
// 1. TenantFilter: JWT 解析后设置 TenantContext（PR 1）
// 2. MybatisPlusConfig: MybatisPlusInterceptor + TenantLineInnerInterceptor
// 3. ScaffoldTenantLineHandler: 决定哪些表被注入 tenant_id
```

**注入范围是白名单，不是黑名单**（`ScaffoldTenantLineHandler.TENANT_TABLES`，PR 2 定）：只有登记过的表（11 张 `sys_*` + `lc_tenant_config`）会被自动补 `tenant_id`，其余一律不碰 —— 反向写法（"全部减去少数"）会给 `information_schema`、生成器配置表和无 `tenant_id` 的关联中间表加上不存在的列。名单遗漏的后果是泄露，所以 `ScaffoldTenantLineHandlerTest` 直接解析 `sql/V*.sql` 的 DDL 做反向校验：**凡是带 `tenant_id` 列的表必须在白名单里**，新增表忘记登记当场红。匿名请求（登录、验证码、健康检查）没有租户上下文，整体跳过注入。

**`super_admin` 跨租户 = 会话内切换租户**（关闭原开放项 1）：`POST /api/auth/switch-tenant {tenantId}` 校验调用者角色与目标租户状态后，重签一张 `tenantId` 指向目标租户的 JWT 并**吊销旧令牌**，`tenantId` 为空回到自己的平台租户，切换动作写登录日志。管理员的身份、角色与数据范围仍按**本人**解析 —— 相关查询（`AuthMapper` 的账号/角色/权限、`SystemManagementMapper` 的档案与角色范围）标 `@InterceptorIgnore(tenantLine = "true")` 按全局主键定位，业务数据与菜单集仍按当前租户过滤。因此切换后能看到该租户的数据、看不到平台管理菜单，边界只有一条会话轨迹，审计清楚。

**账号不是全局身份键**：`sys_user` 唯一键是 `(tenant_id, username)`，跨租户重名是常态。登录可选 `tenantCode`（`lc_tenant.tenant_code`）：填了按「账号 + 租户」精确定位，未填且命中多个租户时报「该账号存在于多个租户，请填写租户编码」，绝不取第一条。权限与数据范围解析的入参已从 `username` 改为 `LoginUser`（按 `userId`）。

**拦截器管不到的写入靠服务层拦**：`sys_user_role` / `sys_user_post` / `sys_role_menu` / `sys_role_dept` 没有 `tenant_id`，`SystemManagementService.ownedIds()` 在授权前按当前租户反查目标主键，发现外来 ID 直接拒绝并回滚整笔事务；菜单 `parentId` 同样校验归属。`lc_tenant_config` 的读写只作用于当前会话租户（接口不收 `tenant_id` 入参），跨租户改参数必须先切换 —— 这是切换口径在 API 形状上的体现。

**一处踩坑**：`sys_menu.id` 原由应用侧 `MAX(id)+1` 分配，而 `MAX` 已被租户条件限制，两个租户会算出同一个 ID 撞全局主键；已改为数据库自增（V1 建表 + `useGeneratedKeys`，V4 带存量库 `ALTER`）。

**动态数据源本期不做**。平台不连项目业务库：模型的字段定义存在平台库 `lc_field`，建表 SQL 作为生成产物推到项目仓库、由项目自己的迁移流程执行（§5.2）。因此 `AbstractRoutingDataSource` 按 `project_id` 切库这套不进本期范围；只有在"平台要在线读项目库做数据预览/报表"时才需要，那属 §7.2 远期能力。

`lc_project.db_*` 字段保留但本期只作为**交付给项目的连接配置**（生成 `application.yml` 时写入），平台自身不建连接。这也意味着 `db_password` 存的是项目库口令——**它是给项目用的配置值，不是平台凭据**，写入生成产物时应由项目自己以环境变量或 profile 覆盖，平台产物里不嵌明文。

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

`modelRef` 指向模型 code，是页面与模型的唯一耦合点（§1.5）；每个控件的 `field` 必须是该模型的真实 `field_code`，属性面板的下拉数据来自模型字段接口，不允许手填。

按钮的 `action` 只接受**内置动作枚举**，其余一律走扩展点：

| 内置动作 | 生成物 |
|---|---|
| `create` / `edit` / `delete` / `view` | 打开 `FormDialog` / 调 DELETE / 跳详情；权限点自动分配 |
| `export` | 调 `/export` 端点 |
| `refresh` | 重新拉列表 |
| `custom:{name}` | **不生成逻辑**：在 C 档 `overrides/{page}.vue` 里生成一个带 `TODO` 的 handler 桩，由人填（§4.5） |

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

### 3.6 设计器引擎选型：自研（已定，2026-11-03 复核）

对比过的两个候选与其**实际维护状态**：

| 项 | 阿里 LowCodeEngine | FormCreate FcDesigner |
|---|---|---|
| 最近一次 push | 2025-03-10（约 20 个月前） | 2026-09-16 |
| npm 最新版 | `1.3.4`，2025-06-19 后无更新 | `3.5.0`，2026-10-28 |
| Star / open issues | 15.9k / 664 | 2.4k / — |
| 设计器内核 | React；npm 包只给 typings，运行时**仅支持 CDN 引入** | Vue3，但渲染器绑 Element Plus / AntDV / Vant |
| 产物 | 运行时渲染 JSON 的渲染器（可导出代码） | **只有表单**，列表页/表格/行操作/看板不在能力内 |

选**自研**的三条硬理由：

1. **它的核心资产用不上**。LCE 最大价值是「物料协议生态 + 运行时把 schema 渲染成页面」。本项目走**代码生成**路线（§四），画布只需设计态近似预览，不需要生产运行时渲染器——引进来等于为一套不需要的能力付全套复杂度。
2. **接入成本高于已经决定要做的抽包**。用 LCE 必须把 `ListPage`/`DataTable`/`DictSelect`/`RowActions` 逐个重写成符合物料协议的物料包 + 单独 simulator 资源 —— 而 §〇 定的 `@scaffold/ui` 只需要把组件搬进 workspace 包并解 28 处 app 耦合，LCE 那份工作在此之上再加一层外来协议。
3. **设计语言冲突**。其设计器壳是 React + 自有设置器视觉体系，与 AGENTS.md「不依赖 Element Plus 或 three、自有 Vue 组件 + 珍珠玻璃、全站视觉统一」直接顶。FcDesigner 绑 Element Plus 且做不出列表页，同理排除。

**只借不装**：LCE 的《低代码引擎搭建协议规范》可作为 §3.4 `design_json` 的结构参考，零成本借用别人验证过的 schema 设计。

**范围收敛**：自研三栏设计器（流式拖拽 + 容器嵌套 + 属性面板配样式）第一期只做「列表页 + 表单弹窗」两类模板，看板/详情/流程后置；否则 §八 的 4 周不成立。

---

## 四、代码生成引擎

### 4.1 模板引擎：FreeMarker

模板清单（文件名同样受 §10.2 开放项 2 的命名定稿影响，`ServiceImpl` / `Query` / `types.ts` 三项在"向底座看齐"的分支下会消失）：

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

> ⚠️ 下面两段示例是**早期草稿的设想结构**，与本仓库现有底座的实际约定不一致（`R.ok` vs `R.success`、`{Cls}DTO` vs `{Cls}WriteRequest`、interface + impl vs 单类 Service、`views/{m}/{r}/index.vue` vs `views/{m}/Users.vue`）。PR 5 动手前先按 §10.2 开放项 2 定稿，示例届时重写；照抄现在这版会生成一套与底座风格分叉的代码。

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

路径以**生成项目的真实目录结构**为准。生成项目就是脚手架的一份拷贝，顶层不是 `backend/` + `frontend/`，后端是 `scaffold-{module}` Maven 模块、前端是 `scaffold-vue` 单应用。下面每条标注所属所有权档（§5.2），分档判定就按这张表的路径前缀做：

```typescript
const fileRules = {
  // B 档：每次发布整文件覆盖，约定人工不改
  backend: {
    entity:     (m, cls) => `scaffold-${m}/src/main/java/com/scaffold/${m}/domain/${cls}.java`,
    writeRequest: (m, cls) => `scaffold-${m}/src/main/java/com/scaffold/${m}/domain/dto/${cls}WriteRequest.java`,
    vo:         (m, cls) => `scaffold-${m}/src/main/java/com/scaffold/${m}/domain/vo/${cls}VO.java`,
    mapper:     (m, cls) => `scaffold-${m}/src/main/java/com/scaffold/${m}/mapper/${cls}Mapper.java`,
    controller: (m, cls) => `scaffold-${m}/src/main/java/com/scaffold/${m}/controller/${cls}Controller.java`,
    service:    (m, cls) => `scaffold-${m}/src/main/java/com/scaffold/${m}/service/${cls}Service.java`,
  },
  frontend: {
    api:    (m) => `scaffold-vue/src/api/${m}.ts`,                    // 一个模块一个文件
    listView: (m, kebab) => `scaffold-vue/src/views/${m}/${kebab}.vue`, // 复数平铺，如 system/Users.vue
  },
  sql: {                       // B 档，但只生成增量语句，禁 DROP / ALTER DROP COLUMN
    table: (t) => `sql/pages/${t}_table.sql`,
    menu:  (t) => `sql/pages/${t}_menu.sql`,
  },
  // C 档：首次生成空骨架后即归人工所有，之后发布"存在即跳过"
  custom: {
    ext: (m, cls) => `scaffold-${m}/src/main/java/com/scaffold/${m}/service/${cls}Extension.java`,
    overrides: (m, kebab) => `scaffold-vue/src/views/${m}/overrides/${kebab}.*.vue`,
  },
  // A 档：项目初始化写入一次，平台此后不生成不覆盖
  // pom.xml、scaffold-admin 装配、scaffold-vue/src/components、src/styles、Dockerfile、docker-compose.yml
};
```

> **命名要向现有底座看齐**：`scaffold-system` 实际用的是 `{Entity}WriteRequest`（不是 `{Entity}DTO`）、`PageQuery` + 逐个 `@RequestParam`（不是 `{Entity}Query` 对象）、`R.success(...)`（不是 `R.ok(...)`）、单类 Service（不是 interface + `service/impl`）。§4.2 的示例代码是按另一套约定写的，两者不一致 —— PR 5 必须先定这一刀（见 §10.2 开放项 2），否则生成物与底座风格分叉，违反 AGENTS.md 的"统一底座"。

C 档扩展点是"业务逻辑唯一允许落笔的地方"，平台只在首次生成空骨架、之后永不覆盖。前端 `overrides/` 用于人工覆写某个区块而不碰 B 档主文件（第一期可先只做后端扩展点，前端 overrides 视 P2 完成情况决定要不要上）。

---

### 4.4 模块落点（新增，2026-11-03）

低代码能力直接长在本仓库，与 `scaffold-generator` 同层新增模块：

```
packages/
└── scaffold-ui          # 新增：@scaffold/ui 组件包（73 组件 + 17 composable + theme.css）
                         #       导出 installScaffoldUi(app, {http, permission, dict}) 注入点
                         #       npm workspaces 成员，平台与生成项目共用同一个包

scaffold-system/         # 后端 Maven 聚合
├── scaffold-common      # TenantContext（PR 1 已落）；无新增
├── scaffold-framework   # TenantFilter（PR 1 已落）；后续加租户 SQL 拦截器
├── scaffold-system      # 用户/角色/菜单/字典，已带 tenant_id
├── scaffold-generator   # 现有：表单元数据 → renderFiles() → zip 下载
├── scaffold-lowcode     # 新增：租户/项目/模型/字段/页面/发布/合并请求 业务
└── scaffold-admin       # 启动入口，聚合新模块
```

**复用的接缝**：`GeneratorService.renderFiles()` 已返回 `Map<文件路径, 文件内容>`，而 `download()` 只是把这份 Map 打成 zip 字节（[scaffold-generator/.../GeneratorService.java:117](../scaffold-system/scaffold-generator/src/main/java/com/scaffold/generator/service/GeneratorService.java#L117)）。Git 发布是**换一条 sink**（同一份 Map 落盘 + 提交 + 推送），生成逻辑不动。本期把该返回值提为公共出参类型，两条 sink 并存：`zip 下载`（保留给手工取产物）与 `推送到仓库`。

**Git 能力放哪**：`scaffold-lowcode` 内部 `publish` 包，不沉到 `scaffold-framework`——只有低代码业务用得到，放框架层会让普通后台项目背上 JGit 依赖。JGit 依赖（`org.eclipse.jgit` + `org.eclipse.jgit.ssh.apache`）只声明在 `scaffold-lowcode`。

**前端**：scaffold-vue 变成**宿主 app** —— 低代码视图进 `src/views/lowcode/**`，可复用组件与主题搬进 `packages/scaffold-ui`，两侧都通过包名引用。设计器专用组件（画布、属性面板、组件面板、diff 视图）留在宿主、**不进包**，且在 §6.4 被裁剪掉，不进任何生成项目。

**为什么包不单独建仓库**：平台与组件同步演进，跨仓库来回改的摩擦大于收益；本仓库 `packages/scaffold-ui` + npm workspaces，宿主用 workspace 依赖（本地即时生效），生成项目用 git tag 依赖（发布产物）。

---

### 4.5 接口生成边界：哪些接口能生成，flow 不做

模型建完，接口从哪来？分三类，边界必须先划清，否则"要不要 flow 设计器"这个问题会随需求越滚越大。

#### ① 标准 CRUD —— 由模型直接推导，不需要任何设计

一个绑定了模型的页面，自动生成六个端点，形态完全固定：

| 端点 | 来源 | 说明 |
|---|---|---|
| `GET /api/{module}/{resource}` | 列表 | 分页 + `is_query` 字段作条件 + `query_type`（`=` / `LIKE` / `BETWEEN`） |
| `GET /api/{module}/{resource}/{id}` | 详情 | 主键 |
| `POST` / `PUT /{id}` / `DELETE /{id}` | 增改删 | 用 `is_insert` / `is_edit` / `is_required` 生成校验；删除走 `enable_logic_delete` |
| `GET /api/{module}/{resource}/export` | 导出 | `is_list` 字段作列 |

外加权限点（`permission_prefix` × 5）、`@OperLog`、树表（`is_tree` 时多一个 `/tree`）、数据范围（`enable_data_scope`）。**这一类覆盖后台管理接口的绝大多数，不需要设计，只需要配置。**

#### ② 自定义接口 —— 走 C 档扩展点，不生成

状态流转、批量操作、聚合统计、跨实体事务、调外部系统：写进 `service/{Cls}Extension.java`（后端）与 `views/{m}/overrides/*.vue`（前端）。平台只负责三件配角事：生成扩展类**骨架**、注册路由与菜单、按页面声明分配权限点。

前端要调自定义接口时，设计器的按钮动作选 `custom:{name}`，模板生成一个带 `TODO` 的 handler 桩落到 overrides，由人填实现。**平台不提供"在页面上配置接口逻辑"的能力**，这是本期的明确边界。

#### ③ flow / 流程编排 —— 本期不做（第二期）

为什么不做，三条理由：

1. **与"平台不托管运行时"冲突**。flow 有两种存在方式：运行时解释执行（那平台就成了执行引擎，直接顶翻 §〇之二 第 2 条），或编译成 Java 编排代码（那是给 codegen 再加一整个模板族 + 一套 DSL + 一套校验器，工程量高于前面全部之和）。
2. **它不是"接口生成"的问题，是业务规则表达的问题**。后台管理系统里真正需要流程的页面占比很低，而低代码平台最容易死于"什么都想配置"。§九 早就写了"流程设计器放第二期"，这里把它从"周期风险"升级为"范围排除"。
3. **已有更便宜的替代品**：C 档扩展点 + 合并请求。流程逻辑手写 20 行 Java，比发明一个流程 DSL 便宜一个数量级，而且改错了能 revert。

顺带一个被同样理由排除的候选：**状态流转要不要元数据化**（给模型加"动作"定义：源状态 → 目标状态、权限点、是否必填备注）。后台里状态变更极常见，这个提议很诱人。结论是**不加**（列为 §10.2 开放项 7）：它看着比 flow 小，但一旦入模型就要定义动作 DSL、参数校验、生成器分支与 UI，而手写 `updateStatus` 只有 20 行。等真实项目里出现 3 个以上形态一致的状态机需求，再考虑。

#### 判据回顾

| 需求 | 归处 | 依据 |
|---|---|---|
| 加一列、改查询方式、换表单控件 | 模型（B 档生成） | 靠字段名 + 类型可推导 |
| 加一个"审核通过"接口 | C 档扩展点 | 含业务规则，推导不出来 |
| 加一个审批流（多级、会签、超时） | 手写；flow 设计器属第二期 | 需要规则表达能力，不是生成问题 |

---

## 五、发布与合并机制

平台不托管项目运行时（§〇之二 第 2 条），"发布"= 生成代码 + 提交 + 推送到租户登记的仓库。合并是**另一个独立动作**，由人显式发起（§5.3）。

### 5.1 发布只做「生成 + 提交 + 推送生成分支」，不碰主干

```
┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
│ 设计页面  │─▶│ 生成文件  │─▶│ 哈希对比  │─▶│ 提交到    │─▶│ 推送远端  │
└──────────┘  └──────────┘  │ vs 工作区 │  │ 生成分支  │  └────┬─────┘
                             └─────┬────┘  └──────────┘        │
                                   │ 无差异                     ▼
                                   ▼                    主干保持不动，等人发起合并
                            no-op，不产生提交
```

- 平台**只写 `gen_branch`**，`main_branch` 始终由人掌控；
- 全部 Git 操作用 **JGit**（纯 Java，不经过 shell，避免 `repo_url` / `project_code` 等用户输入拼进命令行造成注入）；
- 发布成功打 tag `v{projectCode}-{pageCode}-{version}`，作为合并与回滚的锚点。

### 5.2 文件所有权三档（替代原「CUSTOM-START/END 文本级合并」）

生成分支是平台唯一的产物出口，主干由人掌控。文件分档的作用不是"保证合并无冲突"，而是：

1. 界定 B 档可以整文件覆盖——因为约定人工不在主干改它；
2. 让 C 档承载手写业务逻辑且平台永不碰——合并时的 diff 才是"看得过来"的量；
3. 让危险变更（如删列 SQL）不进 B 档，只以提示形式出现在 diff 里。

| 档 | 内容 | 平台行为 | 人工约定 |
|----|------|----------|----------|
| **A 固定文件** | 后端 `scaffold-common` / `framework` / `system` 源码、前端 app 壳（`main.ts`、路由与布局、登录页、工作台）、`pom.xml`、`package.json`（含 `@scaffold/ui` 版本号）、Dockerfile | 项目初始化时写入一次，此后平台**不生成不覆盖** | 可随意修改。**组件与样式不在 A 档** —— 它们是 `@scaffold/ui` 依赖，升级只 bump 版本号，不进 diff |
| **B 纯生成文件** | Entity / DTO / VO / Query / Mapper / Controller / api.ts / types.ts / 列表页 Vue | 每次发布**整文件覆盖** | 不在主干手改；要改回到模型或设计器改，再发布 |
| **C 扩展点文件** | `{Cls}ServiceImplCustom.java`、`{resource}.ext.ts`、`overrides/**` | 首次生成**空骨架**后即视为人工资产，之后发布**存在即跳过** | 业务逻辑写这里，平台不碰 |

> 原方案的 `CUSTOM-START / CUSTOM-END` 标记 + 文本级 diff 合并机制**取消**：它是"平台覆盖文件"与"保留手写代码"两个诉求在同一文件上的强行调和，实现复杂且最容易静默丢代码。分档 + 分支隔离 + 人工审 diff 三重手段已覆盖该诉求。若个别场景仍需在一个文件里混排，可自行在 C 档文件内用标记，但平台不参与解析。

**A 档"固定"的硬约束：一个项目只有一个业务模块。**

新增 Maven 模块必须同时改两处 A 档文件 —— 父 `scaffold-system/pom.xml` 的 `<modules>` 与 `scaffold-admin/pom.xml` 的 `<dependencies>`（本仓库自己加 `scaffold-lowcode` 就是这样改的）。如果每个业务模块都这么来一次，"A 档不随发布更新"就破了，每次发布都会带上 pom 冲突。

所以约定：**生成项目初始化时预置单一业务模块 `scaffold-business`，所有生成的业务代码进它的子包**（`com.scaffold.business.{model}`），pom 在初始化时一次写好、此后平台永不改。`lc_project.backend_module` 默认值 `business` 正是为此；它允许改名但只影响初始化时的路径生成。前端同理：业务视图只往 `src/views/{module}/**` 与 `src/api/{module}.ts` 增文件，不碰路由与注册表（靠 `sys_menu` + `import.meta.glob` 自动生效）。

这条也让合并请求的 diff 更干净：A 档不出现在 diff 里，审的就是"这次发布动了哪些业务文件"。

其余更新策略：

| 文件类型 | 更新策略 | 说明 |
|----------|----------|------|
| 建表 SQL | 覆盖到项目仓库 `sql/` 下，`CREATE TABLE IF NOT EXISTS` | **只生成增量语句，不生成 `DROP` / `ALTER ... DROP COLUMN`**；破坏性变更在发布结果里以提示列出，由人写迁移 |
| 菜单 SQL | 覆盖，`INSERT IGNORE INTO sys_menu` | 生成项目启动后前端路由经 `import.meta.glob` 自动注册 |
| 路由配置 | 不生成 | 走 `sys_menu` + 自动注册，避免生成文件与人工路由表冲突 |

### 5.3 合并请求：平台内的 PR 式功能

形态对齐 GitHub 的「Compare & pull request → 审 diff → Create pull request → Merge」，差别是全部动作在平台内对工作区执行、直接推远端分支，不产生 fork。

**入口与创建**

```
项目详情 → 合并请求 → [新建]
  来源分支  [platform-generated ▼]   ← 默认生成分支，可任选项目内分支
  目标分支  [main ▼]
  合并方式  ○ 创建合并提交（默认）   ○ 仅快进（目标必须无前进）
  标题/说明 [本次发布订单列表页 v3____]
                                [检查可合并性]  →  [确认合并]
```

**详情页四块内容**

| 块 | 内容 | 实现要点 |
|----|------|----------|
| 变更文件列表 | 状态标记（新增/修改/删除/重命名）+ 每文件 `+/-` 行数 + 按 A/B/C 档分组 | JGit `DiffFormatter` 的 rename detection；列表默认按"生成分支新增"优先，让人先看到平台要往里塞什么 |
| 差异查看 | 逐文件 unified diff，可切并排 | 超大文件与二进制只报统计不渲染正文；单次 diff 分页上限，超出提示"文件过多，请分批发布" |
| 检查项 | 可合并性、目标分支是否已前进、工作区是否干净、生成分支是否落后远端 | 可合并性用**不落盘的 dry-run 三方合并**（`MergeStrategy.RECURSIVE`）预演，不产生提交 |
| 合并动作 | 仅在检查通过时可用 | 见下方执行顺序 |

**状态机**

```
草稿 → 检查中 → 可合并 ──[确认]──▶ 已合并
                   │
                   ├─ 三方合并失败 → 冲突（列出冲突文件）
                   ├─ 目标分支前进 → 陈旧（要求重新检查）
                   └─ 放弃          → 已关闭
```

冲突处理口径：**平台不在网页里编辑代码**，只列出冲突文件并提示在本地 IDE 解决后推来源分支再重跑检查——与 GitHub 一致，避免把代码编辑器塞进本期范围。可选增强（低优先，可砍）：冲突场景按文件提供「全部采用来源版本 / 全部保留目标版本」的单边裁决，代价是静默丢一侧代码，因此必须逐文件二次确认，不整批套用。

**执行顺序**

1. `fetch` 远端，取目标分支最新提交；
2. 重跑全部检查（人可能停留在页面上很久，不能用创建时的缓存结论）；
3. 执行 merge，生成合并提交，提交信息带请求编号与页面版本；
4. 推送目标分支——**合并提交与推送在同一个动作里**，不允许"平台上已合并但远端没有"；
5. 回读 `ls-remote` 校验远端 ref 已指向合并提交，才置状态为"已合并"；推送成功但状态写入失败的，启动时按 `lc_merge_request` 与远端对账修正。

**安全边界**

- 永不 force-push 任何分支；仅快进模式下目标分支已前进则直接拒绝。
- 不做 `pull --rebase` 自动变基、不做 `reset --hard`；远端与平台记录不一致时只报告，不"修好"。
- `gen_branch` 是工作区缓存，**不作为唯一真相**：`design_json` 与模型定义在平台库，工作区可从「clone + 全量重生成」重建；重建前若存在未推送提交必须停下来问人，不得静默丢弃。
- JGit 的重命名检测弱于 C git，也不支持 `.gitattributes` 自定义 merge driver；diff 视图可能把重命名显示成"删除 + 新增"，属已知偏差，评审用途可接受。
- 凭据走主机级（SSH remote + 本机 `~/.ssh` 密钥），JGit 需 `org.eclipse.jgit.ssh.apache` 传输模块；私钥须无口令或经 ssh-agent，`known_hosts` 需预置目标主机指纹。
- 目标分支若设了分支保护、要求必须走外部 PR，推送会被远端拒绝；平台如实回显远端错误，不绕过保护。

### 5.4 版本与回滚

一律**以新提交实现**，不倒带分支历史（`revert` 而非 `reset`，避免破坏他人已 fetch 的历史）：

- **文件级**：取该页面上一版本 tag 的对应文件内容，生成一个只改该文件的提交；
- **页面级**：同上，覆盖该页面相关的全部 B 档文件；
- **发布级**：revert 某次发布对应的提交或合并提交。

C 档扩展点文件不参与回滚——平台不生成它，也就无权回滚它。

---

## 六、项目初始化流程

### 6.1 首次创建项目

```
┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐
│ 创建项目  │──▶│ 绑定仓库  │──▶│ clone    │──▶│ 生成配置  │──▶│ 复制固定  │──▶│ 全量提交  │
│ (填表单)  │   │ (填 URL)  │   │ 工作区   │   │  文件     │   │ 代码 A档 │   │ 推送分支  │
└──────────┘   └──────────┘   └────┬─────┘   └──────────┘   └──────────┘   └────┬─────┘
                                   │                                             │
                                   ▼                                             ▼
                          仓库为空时走「首次创建分支」                 打 tag：v{project}-init
                          JGit 拿到空工作区、无 HEAD
```

### 6.2 初始化内容

| 步骤 | 操作 | 产出 |
|------|------|------|
| 1 | 校验并绑定仓库 | `repo_url` 协议白名单校验（只放行 `https://` 与 `git@`，拒 `file://` / `ext://` / 路径式），`ls-remote` 连通性检查 |
| 2 | 克隆到工作区 | `workspace_path` 指向持久化卷内目录；卷需含 Maven 与 npm 缓存，否则每次生成全量重下依赖 |
| 3 | 生成项目配置 | `pom.xml`、`package.json`、`application.yml` |
| 4 | 复制固定代码（A 档） | scaffold-common、scaffold-framework、系统模块、组件库、样式 |
| 5 | 初始化数据库 | 执行 V1~V3 SQL 建系统表（项目自带库，`lc_project.db_*` 连接信息） |
| 6 | 首次提交并推送 | 见 6.3；`sync_status` 0 → 1 → 2。落地时这一步**同步执行**（单机研发期工具，放请求线程里租户上下文与失败原因都能如实回给人），并发用 `sync_status=1` + 进程内按项目互斥挡；仓库动作的失败原因写进新列 `lc_project.sync_message`，界面直接显示 |

### 6.3 空仓库边界

新登记的项目仓库通常是刚在 GitHub 上建的空仓库，`clone` 得到无 `main`、无 HEAD 的 unborn 状态，`git add` / `commit` 都会报错。首次初始化必须单独处理：

- 检测 `repository.getAllRefs()` 为空 → 认定为首次初始化；
- 在 unborn HEAD 上直接提交全量固定代码，`push` 时用 `refs/heads/{gen_branch}` 显式创建分支（不存在上游，不能用 `-u origin main` 式默认行为）；
- 推送成功后回读 `ls-remote` 校验远端 ref 已存在，再置 `sync_status=2`；
- 若仓库非空且已有 `main`，则要求 `main` 上存在约定的脚手架标志文件或 `.scaffold-project.json`，否则拒绝初始化（避免把平台产物推进一个不相关仓库）。

### 6.4 初始化快照的裁剪清单

**生成项目拿到的不是本仓库全量，而是"脚手架减去低代码平台自身"**。不做裁剪，每个生成的项目里都会躺着一套设计器、租户管理和一个能反向生成代码的工具菜单。

| 处置 | 内容 | 理由 |
|------|------|------|
| **剔除** | `scaffold-lowcode` 模块及 admin pom 中对应依赖 | 平台业务不该出现在交付项目里 |
| **剔除** | `scaffold-generator` 模块、`views/tool/**`、对应 `sys_menu` 种子行 | 否则项目里有一个在线代码生成入口 |
| **剔除** | `lc_*` 建表语句与租户管理菜单种子 | `lc_tenant` / `lc_project` / `lc_model` 等是平台元数据 |
| **剔除** | 设计器专用前端组件（画布、属性面板、组件面板、合并请求 diff 视图）**不进包也不进项目**；平台/演示专属组件（`GeneratorPipeline`、`OrgCanvas`、`MenuStarMap`、`PermissionMatrix`、`PermissionTrace`、`DictSwatchbook`、`AskDock`、`InsightPanel`）留在平台宿主，同样不进项目 | 普通业务后台不需要，也不回流为通用组件（§4.4、§〇） |
| **保留** | `scaffold-common` / `framework` / `system` 源码、app 壳（`main.ts`、路由与布局、登录页、工作台） | 这就是"现在这套空框架"的可运行部分 |
| **改为依赖** | 组件库与珍珠玻璃样式不再拷源码进项目，而是 `package.json` 里的 `@scaffold/ui` + `main.ts` 里的 `installScaffoldUi` 与 `theme.css` | 生成项目与平台共用一套组件物料；升级从"搬源码 diff"降为"bump 版本号"（§〇） |
| **保留** | `sys_*` 的 `tenant_id` 列与 `TenantContext` / `TenantFilter`，生成项目里租户恒为 `1` | 避免维护两套 schema 与两套认证代码；将来项目要独立多租户时列已在，无需迁移 |
| **预置** | 单一业务模块 `scaffold-business`（空壳）+ 其 pom 装配 | 见 §5.2：平台此后不改任何 A 档 pom |

裁剪清单要作为**代码里的一份显式配置**（路径白/黑名单），不是手工拷一遍目录 —— 它是发布产物的定义，改了就要能测试。建议随 PR 4 一起落地并配一份"裁剪后项目能编译、能起、能登录"的集成用例。

**落地事实（PR 4，2026-11-04）** —— 清单写在 `ScaffoldTrimPolicy`，机制与原表有三处出入，都按代码为准：

1. 手段是**路径白/黑名单 + 文件内的 `scaffold:platform-only:start/end` 标记块**两层。第二层是因为有些平台专属内容长在必须保留的文件里：父 pom 的 `<module>`、admin pom 的依赖、`docker-compose.yml` 的工作区卷、`application.yml` 的平台参数段、`V1`/`V2` 里的 `gen_table` 与「系统工具」菜单种子、前端 `api/index.ts` 的平台客户端、启动类里平台模块的 Mapper 扫描包。标记独占整行，写完快照还会扫一遍残留引用（`com.scaffold.lowcode`、`/api/lowcode`、`lc_project` 等），漏裁就拒绝初始化而不是带病出门。
2. **`lc_tenant` 保留在交付项目的库结构里**，与原表「剔除 `lc_*` 建表语句」字面不同。理由：`AuthService.login()` 每次登录都要查租户是否停用、`currentUser()` 要读租户编码与名称，删了这张表要么登录直接报错，要么给交付项目维护第二套认证代码 —— 与原表「避免两套 schema、两套认证代码」的约定相反。剔除的是其余元数据表（`lc_project` / `lc_model` / `lc_field` / `lc_tenant_config` 与后续 `lc_page`、`lc_publish_log`、`lc_merge_request`）。交付项目里这张表恒为一行（1 号租户）。
3. **组件的归属按实际引用裁**：`OrgCanvas` / `PermissionMatrix` / `PermissionTrace` / `MenuStarMap` / `DictSwatchbook` / `InsightPanel` / `AskDock` 原表列为「平台与演示专属、不进项目」，实测它们分别被部门 / 角色 / 菜单 / 字典四个基础页在用；把它们剔掉交付项目就编译不过。本期只剔真正只服务于被删页面的 `GeneratorPipeline`。这批组件的归属应在 PR 4B 抽 `@scaffold/ui` 时用「进包 / 留宿主 / 进项目」三态重新判定，而不是继续靠这份表。

`lc_project.base_scaffold_sha`（§2.2）记录该项目快照取自本仓库哪个提交，用于日后判断"这个项目落后多少"，也是 §〇 方案②（用合并请求下发 A 档升级）的起点；交付项目根上的 `.scaffold-project.json` 里写同一份提交号，两边互相能核对。

---

## 七、技术架构总览

### 7.1 当前形态（单主机自托管，本期交付）

```
┌────────────────────────────────────────────────────────────────┐
│              接入层：nginx 容器（本 compose 已有）               │
│   静态资源本地托管 + /api 反代 + SPA 回退；无泛域名、无 OSS      │
└──────────────────────────────┬─────────────────────────────────┘
                               │
┌──────────────────────────────▼─────────────────────────────────┐
│                     低代码平台（= 本仓库）                       │
│  租户管理 · 项目管理 · 模型设计 · 页面设计 · 发布 · 合并请求     │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │ 代码生成 (FreeMarker) + Git 发布 (JGit) + 工作区管理      │ │
│  └──────────────────────────────────────────────────────────┘ │
│  租户识别：登录态（JWT 的 tenantId claim），不靠子域名          │
└───────┬──────────────────────────────────┬─────────────────────┘
        │                                  │ push（SSH，主机级密钥）
┌───────▼───────────────────┐   ┌──────────▼──────────────────────┐
│ 平台库 scaffold_system     │   │  项目 Git 仓库（租户自有）       │
│  lc_* 元数据 + sys_* 系统  │   │  由使用者 clone 后自行构建部署   │
└───────────────────────────┘   └─────────────────────────────────┘
        │
┌───────▼───────────────┐   ┌─────────────────────────────────────┐
│ Redis（会话/缓存）     │   │ 工作区卷：项目 clone + Maven/npm 缓存│
└───────────────────────┘   └─────────────────────────────────────┘
```

平台定位是**研发期工具**，跑在开发机或一台自托管主机上；可用性要求等同于"我在写代码时它在"，不需要多副本、不需要 SLA。

### 7.2 远期形态（本期不做，需要时另行设计并先确认）

以下能力属于"平台对外提供 SaaS 服务"，与 AGENTS.md「域名、TLS、多副本与云端编排不在默认交付范围」直接冲突，**不在本期范围**：

| 远期能力 | 为什么现在不做 |
|----------|----------------|
| `{tenant}.platform.com` 泛域名路由 | 需泛域名解析 + 泛域名证书 + DNS 控制权限，单主机自托管形态给不了；当前用登录态定租户 |
| OSS 托管各项目前端产物 | 平台不托管运行时，项目产物由项目自己部署 |
| 多副本 + 负载均衡 | 研发期工具无此需求；且工作区是单机有状态目录，多副本要先解决工作区争用 |
| K8S 编排（`deploy_mode=K8S`） | 表字段留着，代码只实现 `STANDALONE`，避免写三套分支再只测一套 |
| 平台内托管生成项目运行 | 见 §〇之二 第 2 条；要做需先解决构建隔离与资源配额 |
| 多租户各带自己的仓库凭据 | 当前用主机级凭据；对外提供服务时需 `lc_tenant_credential` + 加密 + 权限隔离，届时单独设计 |

---

## 八、开发排期

估算口径：**一人全职开发的自然周**，含自测与文档，不含等待与返工缓冲。

| 阶段 | 周期 | 交付物 | 状态 |
|------|------|--------|------|
| **P0-A：多租户地基** | — | `lc_tenant` 建表、`sys_*` 加 `tenant_id` 与复合唯一键、`TenantContext` / `TenantFilter` / `LoginUser.tenantId` / JWT claim / 登录三段校验 | ✅ 已完成（`be2145d`） |
| **P0-B：租户隔离收口** | 2 周 | `TenantLineInnerInterceptor` 自动过滤（表白名单 + DDL 反向校验）、`super_admin` 切换租户旁路、登录 `tenantCode`、关联表写入归属校验、租户管理 UI、`lc_tenant_config` | ✅ 已完成（PR 2，2026-11-03；租户开通流程移入 P1） |
| **P1：项目 + 模型** | 3 周 | `lc_project`（含 §2.2 新增的仓库字段）、`lc_model` / `lc_field`、项目管理与模型设计 UI、仓库绑定与连通性检查、租户开通流程的**手工新增分支**（PR 2 已交付自助注册即开通，平台管理员在租户管理里手工建租户时还不建账号） | ✅ 已完成（PR 3a / 3b / 3c，2026-11-04）；连通性与初始化的 Git 部分在 PR 4 交付 |
| **P1.5：组件抽包 `@scaffold/ui`** | 2.5 周 | `packages/scaffold-ui` + npm workspaces；73 组件 + 17 composable 迁入；**解 28 处 app 层耦合**（注入点 `installScaffoldUi`）；`theme.css` 合并 7 个 css；平台专属组件划出包外；发布为 git tag 并让宿主改依赖 | 待做，**P2/P3 的硬前置** |
| **P2：可视化设计器** | 5 周 | 三栏骨架 + 流式拖拽 + 容器嵌套 + 属性面板；**第一期只覆盖列表页 + 表单弹窗两类模板**，看板/详情/流程后置 | 待做 |
| **P3：代码生成升级** | 3 周 | FreeMarker 模板替换 `StringBuilder`、完整 CRUD 生成、A/B/C 文件分档与 C 档扩展点骨架、`renderFiles` 出参提为公共类型；**模板产物的 import 走 `@scaffold/ui`**；自定义接口只生成 `custom:{name}` 的 TODO handler 桩（§4.5） | 待做 |
| **P4：发布 + 仓库 + 合并请求** | 4 周 | Git sink（JGit：clone/commit/push/tag）、空仓库初始化、工作区卷与构建缓存、`lc_publish_log`、合并请求（diff 列表 + unified diff + 可合并性预检 + 合并执行）、三级回滚 | 拆成 PR 4 / PR 8：PR 4（JGit、连通性、工作区卷、裁剪清单、空仓库初始化）已完成，只剩对真实租户仓库推一次固定代码的真机验证；tag、`lc_publish_log`、合并请求与回滚留给 PR 8 |
| **P5：联调测试** | 2 周 | 端到端（建项目 → 建模 → 设计 → 发布 → 合并 → clone 起来跑通）、租户隔离回归 | 待做 |

**总计约 21.5 周（一人约 5 个月）**

排期依赖：P1 的仓库字段与连通性检查是 P4 的前置；**P1.5 必须在 P2/P3 之前** —— 设计器画布与生成模板都要先确定组件的引用方式（包名 vs `@/` 别名），晚抽包等于把设计器代码和所有历史项目的生成物再改一遍；P3 的文件分档决定 P4 生成哪些文件、diff 才可读。P2 可与 P1 并行准备但不并行开发（设计器的数据绑定要等模型定稿）。

原 P4「2 周」不含 diff 视图与可合并性预检，按本表拆细后为 4 周；原 §5.3 的 `CUSTOM-START/END` 文本级合并机制取消，省下的部分已在 5 周内吸收。

---

## 九、关键风险与应对

| 风险 | 影响 | 应对措施 |
|------|------|---------|
| `tenant_id` 遗漏导致跨租户数据泄露 | 严重 | `TenantLineInnerInterceptor` 自动注入（P0-B）；上线前用双租户数据集跑越权用例，不靠人工检查 SQL |
| 生成代码质量不稳定 | 影响业务 | 以 `scaffold-system` 现有 Service/Controller 为参照，模板产物必须达到同等质量；CI 里对生成产物跑编译与 `vue-tsc`，不让生成物"看起来对" |
| 设计器复杂度失控 | 周期延长 | P2 只做列表页 + 表单弹窗；流程设计器、看板放第二期 |
| 低代码平台"什么都想配置" | 范围失控，做不完 | 用 §1.4 判据挡住：能从字段推导的才进模型，业务规则一律进 C 档扩展点手写；flow / 状态机 / 规则引擎明确排除（§4.5） |
| 模型与项目实体分叉 | 发布产物与预期不符 | 承认为常态（§1.4）：模型是期望态，差异靠 §5.3 合并请求的 diff 审查暴露，平台不做一致性校验、不自动"修正" |
| 删字段 / 下线模型打断已有页面 | 中 | 保存 `design_json` 时把引用的 `field_code` 写入 `lc_page.ref_fields`，删除前反查拦截并列出受影响页面 |
| 用户输入进入 Git 操作造成注入或越权仓库 | 严重 | 全链路 JGit（不经 shell）；`repo_url` 协议白名单只放行 `https://` 与 `git@`，拒 `file://` / `ext://` / 路径式；分支名走 `Repository` API 而非字符串拼接 |
| 平台覆盖人工代码 | 严重 | 平台只写 `gen_branch`，主干由人通过合并请求显式改；A/B/C 文件分档保证 C 档平台永不生成；合并前必须看 diff |
| 静默丢弃未推送的发布 | 中 | 工作区不 `reset --hard`；重建工作区前检测未推送提交并停下问人 |
| 破坏性数据库变更随发布下发 | 严重 | 建表 SQL 只生成增量语句，禁止 `DROP` / `ALTER ... DROP COLUMN`；破坏性变更以提示形式列出，由人手写迁移 |
| JGit 合并/diff 行为与原生 git 有偏差 | 轻 | 已知：重命名检测弱、不支持自定义 merge driver。合并前预检 + 冲突一律人工处理，不做单边自动取舍 |
| 脚手架升级下发困难 | 版本碎片化 | 组件走 `@scaffold/ui` 包，升级 = bump 版本号；后端用 Maven BOM 管版本；`lc_project.base_scaffold_sha` 记录各项目落后多少。A 档 app 壳的差异仍走**合并请求**下发 |
| 私有 git tag 依赖导致生成项目 `npm install` 拉不到包 | 中 | `~/.npmrc` 配 `_authToken`（主机级，不进 DB、不进仓库）；"裁剪后项目能编译能起能登录"的集成用例必须包含一次干净 `npm ci`，避免只在开发机凭缓存跑通 |
| 抽包时组件行为回归 | 中（影响面大：148 处引用） | P1.5 分两次提交：先只搬路径与建 workspace（行为不变，`vue-tsc` + 现有测试守住），再改注入点解耦 28 处 app 依赖。不要一次做完 |
| 裁剪清单遗漏，交付项目里带着设计器与代码生成入口 | 严重 | §6.4 的白/黑名单作为代码里的一份显式配置并配集成用例：裁剪后项目能编译、能起、能登录，且不含 `tool` 菜单与 `lc_*` 表 |

---

## 十、已定口径与开放项

### 10.1 已定（不再讨论，实现按此执行）

| 项 | 结论 |
|----|------|
| 平台形态 | 就是本仓库，新增 `scaffold-lowcode` 模块承载；**不托管项目运行时** |
| 发布定义 | 生成 → 提交 → 推送到租户登记的仓库**生成分支**，不碰主干 |
| 合并方式 | 平台内 PR 式功能：选源/目标分支 → 看文件 diff → 确认才合并 |
| 凭据 | 主机级 SSH 密钥，DB 只存 `repo_url` 与分支名 |
| Git 实现 | JGit 纯 Java，不经 shell |
| 组件物料 | **统一走 `@scaffold/ui` 包**（放本仓库 `packages/scaffold-ui`，npm workspaces），平台与生成项目共用；组件不再拷源码进生成项目。不存在 fork：平台前端就是 scaffold-vue 宿主 |
| 包的发布渠道 | **GitHub git tag 依赖**（`"@scaffold/ui": "github:max-yuuuu/lowcode-ai#v1.0.0"`），零 registry 运维；token 走主机 `~/.npmrc` |
| 包面 | AGENTS.md 那份组件清单**全量进包**，含带数据的组件（`DictSelect`/`UserSelect`/`PermissionButton`…），靠 `installScaffoldUi(app, {http, permission, dict})` 注入宿主能力；平台/演示专属组件不进包 |
| 设计器引擎 | 自研，第一期只做列表页 + 表单弹窗；LCE 协议规范仅作 schema 参考 |
| 部署形态 | 单主机自托管 docker-compose；泛域名 / OSS / 多副本 / K8S 属远期，见 §7.2 |
| 代码存储 | Git 仓库（不存数据库 BLOB）；平台库存 `design_json` 与版本快照 |
| 模型定位 | `lc_model` / `lc_field` 是**生成器的输入（期望态）**，不是"模型管理"业务功能；判据见 §1.4 |
| 接口生成 | 标准 CRUD 六端点由模型推导；自定义接口一律走 C 档扩展点；**flow / 流程编排 / 状态机元数据化本期不做**（§4.5） |
| 页面与模型 | 一个页面一个主模型、一个模型多个页面；耦合只在 `field_code`；`DASHBOARD` 可不绑模型（§1.5） |
| 租户隔离实现 | MyBatis-Plus `TenantLineInnerInterceptor` + **表白名单**（新增带 `tenant_id` 的表必须登记，单测按 `sql/V*.sql` 反向校验）；无 `tenant_id` 的关联中间表由服务层做归属校验（§2.3） |
| `super_admin` 跨租户 | **会话内切换租户**：重签 JWT（`tenantId` 指向目标租户）并吊销旧令牌，身份与权限仍按本人角色解析；不提供无租户条件的旁路查询（§2.3） |
| 身份键 | 账号只在租户内唯一，一律按 `userId` 解析权限与数据范围；登录可选 `tenantCode` 定位租户 |

### 10.2 真正的开放项

1. ~~`super_admin` 跨租户旁路口径~~ **已定（PR 2）**：走"会话内切换租户"，见 §10.1 与 §2.3。留下的实现细节 —— 切换态下顶栏常驻「回到平台租户」，平台管理菜单不下发给租户。
2. **生成物命名约定跟底座还是跟模板**（PR 5 的第一刀）：底座 `scaffold-system` 实际用 `{Entity}WriteRequest`、`PageQuery` + 逐个 `@RequestParam`、`R.success(...)`、单类 Service、`src/api/{module}.ts` 单文件、`views/{module}/Users.vue` 复数平铺；而本文档 §4.2 示例写的是 `{Entity}DTO` / `{Entity}Query` / `R.ok()` / interface + impl / `api/{m}/{r}.ts` / `views/{m}/{r}/index.vue`。两者必须择一：**(a) 生成物向底座看齐**（改动小，但每实体一个 Service 类与现有"一个模块一个大 Service"不同，仍需定义业务模块的粒度）或 **(b) 反过来把底座重构成分层结构**（干净，但要动已交付的 system 模块，与"固定代码不随发布变动"冲突）。定了才能写模板，也才能定 C 档扩展点放哪（`{Cls}Extension` 还是别的）。
3. **C 档扩展点的文件约定**：后端一个扩展类是否够用、前端 `overrides/` 第一期要不要做。与上条绑定。
4. **`design_json` 是否对齐 LCE 搭建协议**：对齐能白捡一份别人验证过的 schema 与文档，代价是字段冗余（本项目只需列表/表单两类）。倾向"借结构不借全协议"。
5. **多项目工作区并发**：同一台机器上两个项目同时发布是否加文件锁。单租户自己用时不会发生，平台给别人用时才会。
6. **`installScaffoldUi` 的三个契约**（PR 4B 的第一刀）：`http`（请求与统一返回结构 `R` 的解包，是否复用 `scaffold-vue/src/api/http.ts`）、`permission`（`v-perm` 需要的权限集合从哪来、何时刷新）、`dict`（`useDict` 的缓存归属 —— 包内缓存还是宿主提供）。这三个定不好，28 处解耦会改成 28 个各写各的 prop，包就白抽了。
7. **状态流转要不要元数据化**（本期结论：不要，见 §4.5）。若将来做，形态是模型下挂"动作"定义（源状态 → 目标状态、权限点、附加校验），代价是动作 DSL + 生成器分支 + 一套 UI。触发条件建议明确为：**真实项目里出现 3 个以上形态一致的状态机需求**，再上；否则一条 `updateStatus` 手写 20 行更便宜。

### 10.3 PR 划分

每个 PR 是一个可独立验证、可独立回滚的逻辑单元；顺序即依赖顺序，除注明"可并行"外不得打乱。

| PR | 范围 | 验收标准 | 依赖 |
|----|------|----------|------|
| **PR 2**<br>P0-B | `TenantLineInnerInterceptor` 自动注入；`super_admin` 切换租户旁路；登录 `tenantCode` 与按 `userId` 的身份解析；关联表写入归属校验；`lc_tenant_config`；租户管理 UI（列表/新增/编辑/启停/进入租户）；**租户自助注册**（登录页切换注册表单，提交即开通租户 + 管理员 + 菜单） | ✅ 已完成：双租户种子数据下 A 租户令牌查 B 租户记录为空、`super_admin` 切换后可见且身份权限不变、非管理员切换被拒、停用租户账号登录被拒、跨租户角色授权被拦、租户参数只在当前租户内读写（`TenantIsolationApiTest` 9 例 + 拦截器单测 5 例） | PR 1 ✅ |
| **PR 3a**<br>P1 上 | ✅ 已完成：`lc_project` 建表（`db_*` / `deploy_*` 等无读写方的列留到 PR 5 以 ALTER 补）、`RepoUrlValidator` 协议白名单、项目 CRUD 与 `ListPage` 版式的项目管理页面、500 段「低代码」菜单，以及「补齐租户资产」接口（已有租户获得迁移新增的菜单 / 字典 / 参数） | 项目从新增到软删全链路；非法仓库地址与同分支被拒；跨租户同名编码各自可见且改删被拒（`ProjectApiTest` 3 例 + `RepoUrlValidatorTest` 4 例） | PR 2 ✅ |
| **PR 3b / 3c**<br>P1 上 | ✅ 已完成：`lc_model` / `lc_field` 建表与 CRUD、`FieldType` 作为类型映射的唯一事实源（库里存算好的 `java_type` / `column_type`，前端读 `/models/meta`）、字段配置走 `ListPage` + `Drawer` 主从形态；手工新增租户时填了管理员就走同一套开通流程 | 建模型、配字段；`FieldTypeTest` 7 例钉死映射表；`ModelApiTest` 5 例覆盖派生/约束/跨租户；手工开通的租户能用该账号登录并看到菜单 | PR 3a ✅ |
| **PR 4**<br>P1 下 | ✅ 已完成（2026-11-04）：JGit 依赖只落 `scaffold-lowcode`（7.7.1 + `ssh.apache`）；`ls-remote` 连通性检查（只读，不改生命周期）；工作区卷与 Maven/npm 缓存卷；**§6.4 裁剪清单代码化**（`ScaffoldTrimPolicy` 路径白/黑名单 + `scaffold:platform-only` 标记块 + 交付产物的平台残留引用扫描）+ `scaffold-business` 空壳预置；空仓库首次初始化（未出生 HEAD、显式 `refs/heads/{gen_branch}`、推完回读校验）与 `sync_status` 推进、`sync_message` 失败原因（V7）。初始化同步执行，不引入异步任务表 | 对一个真实空仓库跑通「绑定 → clone → 全量提交固定代码 → 推 `gen_branch` → `sync_status=2`」；**裁剪后的项目能编译、能起、能登录，且不含 `views/tool` 与 `lc_*` 表**；`file://` 等非法 URL 被拒；未推送提交时重建工作区被阻止。**实测补记**：交付快照 282 个文件离线 `mvn package` 通过、前端 `npm ci` + `vite build` 通过、用交付侧 V1–V3 建新库能起能登录，菜单里没有 `/tool` 与 `/lowcode`；`lc_tenant` 因登录链路每次都要查租户状态而保留（§6.4 已回写）。**待做一次真机推送**：对真实租户仓库点「初始化固定代码」，同时验证部署密钥与 `known_hosts` | PR 3 |
| **PR 4B**<br>P1.5 | **抽 `@scaffold/ui`**：`packages/scaffold-ui` + npm workspaces；73 组件 + 17 composable + 7 个 css（合并为 `theme.css`）迁入；`installScaffoldUi(app, {http, permission, dict})` 注入点；解 28 处 app 层 import；平台/演示专属组件划出包外；打 tag 发布，宿主与文档改依赖。**分两步提交**：先搬路径建 workspace（行为不变），再改注入点解耦 | 平台自身跑通且行为无回归（`vue-tsc` + 现有测试全绿）；148 处引用中不再有组件间 `@/` 互引；包被一个最小 demo app 以 git tag 依赖消费成功 | PR 3，**必须在 PR 5 之前** |
| **PR 5**<br>P3 | FreeMarker 模板引擎替换 `GeneratorService` 的 StringBuilder；A/B/C 文件分档与 C 档扩展点骨架；`renderFiles()` 出参提为公共类型，zip sink 与 git sink 并存；**模板产物 import 走 `@scaffold/ui`** | 生成的代码能编译、`vue-tsc` 通过；产物与 `scaffold-system` 现有同类文件逐行比对达到同等质量；zip 下载行为不回退 | PR 3（可与 PR 4 并行）、**PR 4B** |
| **PR 6**<br>P2 | `lc_page` / `design_json`；设计器第一期：三栏骨架 + 流式拖拽 + 容器嵌套 + 属性面板，**只支持列表页 + 表单弹窗** | 用设计器搭出一个订单列表页，产出的 JSON 能喂给 PR 5 模板生成可运行页面 | PR 4、PR 4B、PR 5 |
| **PR 7**<br>P4 上 | 发布链路：哈希对比 → 提交 `gen_branch` → 推送 → 打 tag → 写 `lc_publish_log`；no-op 幂等 | 重复点同一版本发布不产生空 commit；推送失败不留下半成品提交；tag 与 `lc_page_version.commit_sha` 对得上 | PR 6 |
| **PR 8**<br>P4 下 | 合并请求：`lc_merge_request` / `lc_merge_request_file`、变更文件列表、unified diff、可合并性 dry-run 预检、确认合并与推送、状态机与陈旧检测 | 从 `gen_branch` 到 `main` 走通一次带 diff 审查的合并；目标分支前进后旧请求自动转"陈旧"；冲突场景只列文件不自动取舍；全程无 force-push | PR 7 |
| **PR 9**<br>P5 | 三级回滚（`revert` 新提交）；端到端联调；租户隔离回归 | 建项目 → 建模 → 设计 → 发布 → 合并 → 在干净机器 clone 该仓库 `docker compose up` 跑通并可用 | PR 8 |

**PR 4 与 PR 8 是本期两处新增技术风险点**（JGit 行为差异、主机凭据与 `known_hosts`），各自都要留一次真机验证，不要只在单元测试里过。
