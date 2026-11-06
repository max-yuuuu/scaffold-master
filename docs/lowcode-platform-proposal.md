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
                   │  组件与珍珠玻璃主题来自 @yuanda-lowcode-ai/scaffold-ui 包（与生成项目同一个包）
                   └─ 平台后端新增 scaffold-lowcode 模块，复用 common / framework / system

                = ② 生成项目的初始框架（裁剪快照，不含低代码平台自身）
                   ├─ 项目初始化推一份 A 档 app 壳到其仓库；组件不在 A 档里，是 npm 依赖
                   └─ 页面发布只增改 B 档业务文件，不再动 A 档

                = ③ 上游开源仓库
                   └─ 现由 lowcode-ai 替代作为主推进；scaffold-master 保留同步通道
```

**关于「fork scaffold-vue」：该口径作废（2026-11-03 二次修订）**

早期版本（`ba3e0dc` → `742eb0e`）在此处列过「A. fork 整个 app / B. 抽 `@yuanda-lowcode-ai/scaffold-ui` npm 包 / C. git subtree」三选一，并把 A 定为起步方案。**那三条路的前提是"低代码平台是独立项目"，需要跨仓库复用 scaffold-vue 的组件**；这个前提已被 §〇之二 第 1 条作废 —— 平台自身不 fork、不 subtree。

### 组件物料统一：抽 `@yuanda-lowcode-ai/scaffold-ui`（2026-11-03 三次修订，**已定为既定路线**）

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
import { installScaffoldUi } from '@yuanda-lowcode-ai/scaffold-ui'
import '@yuanda-lowcode-ai/scaffold-ui/theme.css'                       // 珍珠玻璃：tokens.css 及组件样式随包走

installScaffoldUi({ http, permission, dict })         // 三个能力由宿主提供，包内统一经 scaffoldUi() 取用
```

落地时的两处修正（PR 4B，2026-11-04）：

- `installScaffoldUi` **不收 `app`**。原本设想的 `app.provide` + 组件内 `inject` 在「宿主与包各装一份 vue」时会静默失配，而且 `app` 参数除了登记一个全仓零使用的 `v-permission` 指令之外没有别的用处 —— 指令定义随之删除，权限判定只走 `permission.has`。
- 注入契约的字段按真实调用面收敛：`http` 只有物料用到的 `baseUrl / token / get / put / errorMessage`（上传与直连下载用前两者拼地址与鉴权头，异常文案由宿主统一翻译）；`permission` = `has / pathOf / sessionVersion`；`dict` = `load(type)` 返回字典原始行，缓存与转选项仍由包内的 `useDict` 负责。**包内不再出现 `@/` 与 `import.meta.env`**（`vue-tsc` 用不带宿主 `paths` 桥的 tsconfig 校这一条，`src/__tests__/providers.test.ts` 另把 7 个物料接口路径钉死）。

包面 = **AGENTS.md 那份「必须优先复用的组件清单」全量进包**（`ListPage`/`useListPage`/`DataTable`/`SearchForm`/`FormDialog`/`RowActions`/`PermissionButton`/`DictSelect`/`DeptTree`/`UserSelect`/`PermissionTree`/`TreeSelect`/`DateInput`/`Upload`/`Descriptions` 及 `usePopover`/`dateValue`/`upload` 等 composable）。带数据的组件也进包，靠注入点拿数据 —— 否则生成模板要替每个项目重写一篇 `DictSelect`，违背统一组件原则。

样式随包走 7 个 css 文件：`tokens.css`（颜色/间距/圆角变量）与 `base/components/layout/materials/pages/instruments.css` 合并进 `theme.css` 单一入口。

**不进包**（平台与演示专属，留在 scaffold-vue）：`GeneratorPipeline`、`OrgCanvas`、`MenuStarMap`、`PermissionMatrix`、`PermissionTrace`、`DictSwatchbook`、`AskDock`、`InsightPanel`，以及设计器画布/属性面板/组件面板/合并请求 diff 视图。这条边界在抽包时一并划清，否则生成项目装着一堆用不上的组件。

**仓库与发布**（2026-11-04 定稿，覆盖此前「GitHub git tag 依赖」的写法）：包放本仓库 `packages/scaffold-ui` + npm workspaces（**不新建仓库** —— 平台与包同步演进，跨仓库来回改代价大于收益）；宿主用 workspace 依赖，本地改完即时生效。**对外发布走 npm registry**：生成项目的 `package.json` 里是普通版本号 `"@yuanda-lowcode-ai/scaffold-ui": "^1.0.0"`，`npm install` 直接从 registry 解析，不需要 GitHub 凭据，也不会把 npm 私有仓库的 token 带进客户的项目里。

发布渠道现状（2026-11-04 已打通）：npmjs 组织 `yuanda-lowcode-ai` 已建，**npm 强制 scope 等于组织名或用户名，所以包名从 `@scaffold/ui` 改为 `@yuanda-lowcode-ai/scaffold-ui`**（全仓 293 处一起改）。**`1.0.0` 已公开发布**（108 个文件、173.8 kB，源码直发不做 build），交付快照物化后 `npm install` 装到 1.0.0、`vue-tsc` + `vite build` 通过。发布 token 只落主机 `~/.npmrc`，不进数据库也不进仓库。

踩到的三个坑，都写在这儿免得下次再查：本机默认 registry 是 `registry.npmmirror.com`，发布必须显式 `--registry=https://registry.npmjs.org`；scoped 包不带 `--access public` 会按 private 走；账号开了 2FA 之后，npm 已把老的 Automation 类型并进 Granular token —— 要勾 **Bypass two-factor authentication (2FA)** 并把 **Organizations** 权限给到 `Read and write (publish and stage)`（只给包级权限发不了新包）。另外 **新组织首个包会进 npm 的自动审核暂存态**，注册表先挂一个 `0.0.0-stage` 占位版本，`1.0.0` 要等几分钟才可见；**镜像更滞后**，期间按镜像装会直接 `ETARGET: No matching version found`。所以交付项目里 `scaffold-vue/.npmrc` 把这个 scope 钉死在官方源，其余依赖仍走机器默认镜像。

版本纪律：包版本与 `lc_project.base_scaffold_sha` 各自记各自的事实，升级项目时以「bump 依赖版本号」为主、目录 diff 为辅。将来若要支持非 Vite 的消费方，得先补一层 `d.ts`/产物构建 —— 目前消费方一律是 Vite + `@vitejs/plugin-vue`，源码直发够用。

**时机是硬约束**：抽包必须**先于 PR 5（FreeMarker 模板）**。模板要决定生成的 `.vue` 里写 `@/components/...` 还是 `@yuanda-lowcode-ai/scaffold-ui`；先出模板再抽包，等于把所有历史项目的生成物再改一遍。

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
| 7 | **组件物料统一走 `@yuanda-lowcode-ai/scaffold-ui` 包** | 平台与每个生成项目都依赖同一个包（含珍珠玻璃主题），组件源码不再拷进项目。见 §〇 抽包规格与 §6.4。 |

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
| **固定代码**（A 档） | 后端 `scaffold-common` / `framework` / `system` 源码、app 壳（`main.ts`、路由与布局、登录页、工作台）、`pom.xml`、`package.json`、Dockerfile | 项目初始化 | 不随页面发布更新，人工可自由修改。**例外：前端组件与珍珠玻璃样式不在 A 档**，走 `@yuanda-lowcode-ai/scaffold-ui` npm 依赖，升级 = bump 版本号（§〇） |
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

> **实现状态（2026-11-05）**：`lc_tenant` 与 `sys_*` 的 `tenant_id` 改造已在 PR 1 落地，**权威定义以 [scaffold-system/sql/V1__init_schema.sql](../scaffold-system/sql/V1__init_schema.sql) 为准**（本文档早期草稿的 `lc_tenant` 片段缺 `deleted`、`remark` 列与 `idx_lc_tenant_status` 索引，已按实际 DDL 校正）。`lc_project` / `lc_model` / `lc_field` / `lc_page` / `lc_page_version` / `lc_publish_log` **已建表，权威定义以同目录的 `V5`–`V9` 为准**：下面的 DDL 是设计稿，实际落库与它有三处出入 —— ① 没有读写方的列一律不建（`lc_publish_log` 的 `files_added` / `files_modified` / `files_deleted` 与 `remote_url`、`lc_page` 的 `route_parent` / `menu_id` / `permission_prefix` 都留到真需要时再 ALTER）；② 版本行按草稿保留 `files_snapshot`，内容就是「路径 → 内容哈希」；③ 列类型与注释按底座口径写全（`utf8mb4`、`deleted`、审计四字段）。`lc_merge_request` / `lc_merge_request_file` 仍未建，属 PR 8。

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

> ~~**PR 5 的连带项**：`GeneratorService.javaType()` 是「导入已有表 → Java 类型」的反向映射，目前把 `date` 也算成 `LocalDateTime`。模板改造时必须与上表对齐，否则同一个 `date` 列在两条链路上得到不同答案。~~
> **已落地（2026-11-04，PR 5F）**：反向映射挪进 `FieldType.ofColumn(dataType)`，老代码生成页不再自己算第二份；为它补了 `LONG`（`bigint` 非自增列原来无类型可选）与 `FormType.code()`。同一个 `date` 列现在两条链路都是 `LocalDate`。老链路另有一条新约束：**主键、自增、可空、长度、精度一律现查 `information_schema`**（过去靠「列名叫 id」猜），库里已经没有的表直接拒绝生成而不是产出一份指向空表的代码。

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
2. **接入成本高于已经决定要做的抽包**。用 LCE 必须把 `ListPage`/`DataTable`/`DictSelect`/`RowActions` 逐个重写成符合物料协议的物料包 + 单独 simulator 资源 —— 而 §〇 定的 `@yuanda-lowcode-ai/scaffold-ui` 只需要把组件搬进 workspace 包并解 28 处 app 耦合，LCE 那份工作在此之上再加一层外来协议。
3. **设计语言冲突**。其设计器壳是 React + 自有设置器视觉体系，与 AGENTS.md「不依赖 Element Plus 或 three、自有 Vue 组件 + 珍珠玻璃、全站视觉统一」直接顶。FcDesigner 绑 Element Plus 且做不出列表页，同理排除。

**只借不装**：LCE 的《低代码引擎搭建协议规范》可作为 §3.4 `design_json` 的结构参考，零成本借用别人验证过的 schema 设计。

**范围收敛**：自研三栏设计器（流式拖拽 + 容器嵌套 + 属性面板配样式）第一期只做「列表页 + 表单弹窗」两类模板，看板/详情/流程后置；否则 §八 的 4 周不成立。

---

## 四、代码生成引擎

### 4.1 模板引擎：FreeMarker

> **清单已由代码持有（2026-11-04，PR 5G 定稿）**：模板住在 **`scaffold-lowcode`**，不在 `scaffold-generator` —— 模板定义的是平台的产物形状，老代码生成页只是另一个吃它的入口（PR 5F）。草稿里那份清单（`ServiceImpl` / `DTO` / `VO` / `Query` / `FormView` / `DetailView` / `types.ts`）已按 §10.2 开放项 2 的定稿作废，实际文件以下面这份为准。

```
scaffold-system/scaffold-lowcode/src/main/resources/templates/lowcode/
├── backend/                    # 七份，全部进 scaffold-business 的 {module} 子包
│   ├── Entity.java.ftl         # 实体：模型字段 + 审计四列（按开关），日期带 @JsonFormat
│   ├── WriteRequest.java.ftl   # 新增/修改入参：只装可写字段，自增主键不在内
│   ├── Row.java.ftl            # 列表行：主键 + 勾选进列表的字段（{Cls}VO 不再存在）
│   ├── Mapper.java.ftl         # 注解 SQL：count / 分页 / 导出 / 按主键取 / 增删改，软删按开关
│   ├── Service.java.ftl        # B 档单类 Service：校验 + 事务 + 逐个调用 C 档钩子
│   ├── Extension.java.ftl      # C 档骨架：七个钩子，生成一次之后平台不再碰
│   └── Controller.java.ftl     # 五个入口 + @RequiresPermission + @OperLog，返回 R / PageResult
├── frontend/
│   ├── Api.ts.ftl              # 按模型一份 src/api/{module}/{resource}.ts，行与入参类型就地导出
│   └── ListView.vue.ftl        # 列表 + 查询区 + 新增/编辑弹窗（表单内联在同一份 .vue 里）
├── sql/
│   ├── Table.sql.ftl           # CREATE TABLE IF NOT EXISTS：字段 + 逻辑删除 + 审计 + 索引
│   └── Menu.sql.ftl            # sys_menu 一个菜单 + 三个按钮，按 permission 判重、不写死 ID
└── _shared/                    # 宏：不单独成产物，被上面的模板引用
    ├── pojo.ftl                # 字段块与访问器块（实体 / 列表行 / 入参三份共用一个形状）
    ├── auditFields.ftl         # 审计四字段的声明与访问器
    ├── queryWhere.ftl          # WHERE 片段：列表 count、分页、导出三处用同一段
    └── methodParams.ftl        # 方法签名与调用处的参数排版（逐个一行）
```

11 份模板出 11 份产物，产物清单与落点见 §4.3（由 `GeneratedPaths` 持有），`ModelCodeGenerator.renderFiles()` 是唯一的出口。

三条渲染口径，改模板前先读：

1. **`TemplateRenderer` 必须锁 `DOLLAR_INTERPOLATION_SYNTAX`。** FreeMarker 默认同时认 `${...}` 与 `#{...}` 两种插值，不锁的话产物里 MyBatis 的 `#{orderNo}` 会被引擎当成自己的表达式求值，报的是「Undefined inverse variable」这类与业务无关的错。
2. **派生全部在 Java 侧算完**（`GenerationInput`）：命名（类名 / 包段 / 资源名 / 表名）、列类型的完整写法、需要的 import、列表 / 查询 / 表单 / INSERT / UPDATE 五组字段的筛选。模板里只允许取值与循环 —— 生成的代码要给项目里的人读，模板本身也要给人读，把判断堆进 `.ftl` 只会两头都读不动。
3. **同一份模型两次渲染必须逐字节一致**（`ModelCodeGeneratorTest` 与 `FrontendTemplatesTest` 各钉了一遍）。发布 diff 的可读性建立在这条上：内容不稳定，每次合并请求都是整文件重写，人就没法靠 diff 判断这次到底改了什么。

### 4.2 生成代码规范

> **下面两段是 2026-11-04 真机演练（PR 5G）里一个真实模型渲染出来的原文**，模型叫「销售订单」（`sales_order`，主键自增、金额 `decimal(18,2)`、状态挂 `sys_normal_disable` 字典、交货日期 `date`、备注 `text`），不是手写示意。改模板要同步这里；核对方式见 `DeliveryGenerationApiTest` 与 `changelog.md` 的 5G 记录。

口径清单（这几条就是「向底座看齐」的具体内容，与 §10.2 开放项 2 一致）：

| 事项 | 定稿 |
| --- | --- |
| 返回结构 | `R.success(...)`，分页 `PageResult`，入参分页用 `PageQuery` |
| 权限与日志 | `@RequiresPermission("{module}:{resource}:list")` 与 `@OperLog(module = 表说明, action = 动作)` |
| 导出 | `CsvResponse.attachment(文件名, service.export(...))`，导出与列表共用 `:list` 权限点 |
| Service | 单类，无 interface + impl；构造器注入 Mapper 与 **C 档 `{Cls}Extension`** |
| 命名 | `{Cls}` / `{Cls}WriteRequest` / `{Cls}Row`（不再有 `VO` / `DTO` / `Query` / `types.ts`） |
| 接口路径 | 单数 `/{module}/{resource}`，`resource` 用蛇形（`/api/sales_order/sales_order`） |
| 品牌与话术 | 交付项目的登录页、侧栏与浏览器标签读 `scaffold-vue/src/data/index.ts` 那一份 `APP`，初始化时按 `lc_project.project_name` 改写（交付改写表里那条 `{PROJECT_NAME}`）；生成物注释写「低代码设计器」而不是平台名，也不引用交付清单里没有的本文档章节。守卫在 `ScaffoldSnapshotPlanner`：交付产物里出现平台品牌关键字就当场拒绝初始化 |
| 前端物料 | 一律从 `@yuanda-lowcode-ai/scaffold-ui` 的子路径引，业务接口直引 `@/api/{module}/{resource}`，不经过 `@/api` 汇总出口（A 档） |
| 版式 | `ListPage` + `useListPage`（查询 / 重置 / 分页 / 导出 / 行操作都是公共能力），弹窗内联在同一个 `.vue` |
| 新增还是编辑 | 由一个独立的 `editing` ref 记着主键值，不借表单里某个字段的值猜 —— 主键可能根本不进表单（自增），也可能由人填（业务编号） |
| 数字输入精度 | 小数列的 `<NumberInput>` 要带 `:precision="标度"`（表单与查询区两端都带），这个组件不传时按 `step`（默认 1）推断小数位，金额输入 1234.5 会被静默取整成 1235 |
| 列表与导出的列 | 审计开关打开时页面多出「创建时间」一列，`selectRows` 与 CSV 都要带上它；`CsvUtils` 统一把 `LocalDateTime` / `LocalDate` 写成那两种规范串，导出文件里不出现 ISO 的 `T` |
| 自增主键回写 | `@Options(useGeneratedKeys = true, keyProperty = "{实体参数名}.{主键属性}")` —— 这个方法还收 `operator`，属性名不点名参数 MyBatis 就找不到该把自增号回写给谁 |

#### 后端 Controller 产物

```java
package com.scaffold.business.sales_order.controller;

import com.scaffold.business.sales_order.domain.SalesOrder;
import com.scaffold.business.sales_order.domain.SalesOrderRow;
import com.scaffold.business.sales_order.domain.SalesOrderWriteRequest;
import com.scaffold.business.sales_order.service.SalesOrderService;
import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.domain.R;
import com.scaffold.framework.audit.OperLog;
import com.scaffold.framework.security.RequiresPermission;
import com.scaffold.framework.web.CsvResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 销售订单主表（表 {@code sales_order}）的接口。
 *
 * <p>本类由低代码设计器按模型定义生成，属 B 档：每次发布整文件覆盖，要加接口请回到设计器，
 * 或在同包的 {@code SalesOrderExtension}（C 档）里做，别在这里塞业务分支。
 *
 * <p>五个入口固定成一套（列表 / 导出 / 新增 / 修改 / 删除），路径与权限点由模型编码派生；
 * 返回结构统一走 {@code R}，分页统一走 {@code PageResult}，与底座 {@code /api/system} 同一形状。
 * 自定义动作在设计器里选 {@code custom:{name}}，平台只出一个带 TODO 的桩，实现由人填。
 */
@RestController
@RequestMapping("/api/sales_order/sales_order")
public class SalesOrderController {

    private final SalesOrderService service;

    public SalesOrderController(SalesOrderService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission("sales_order:sales_order:list")
    public R<PageResult<SalesOrderRow>> list(
            PageQuery query,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) BigDecimal amountStart,
            @RequestParam(required = false) BigDecimal amountEnd,
            @RequestParam(required = false) Integer status
    ) {
        return R.success(service.page(query, orderNo, amountStart, amountEnd, status));
    }

    /** 单行详情：前端编辑前取整行，避免用列表行填表时把没显示的字段清空 */
    @GetMapping("/{id}")
    @RequiresPermission("sales_order:sales_order:list")
    public R<SalesOrder> detail(@PathVariable Long id) {
        return R.success(service.detail(id));
    }

    /** 导出当前筛选条件下的 CSV（上限由 Service 把住），文件名用模型中文名 */
    @GetMapping("/export")
    @RequiresPermission("sales_order:sales_order:list")
    public ResponseEntity<String> export(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) BigDecimal amountStart,
            @RequestParam(required = false) BigDecimal amountEnd,
            @RequestParam(required = false) Integer status
    ) {
        return CsvResponse.attachment("salesOrder.csv", service.export(orderNo, amountStart, amountEnd, status));
    }

    @PostMapping
    @RequiresPermission("sales_order:sales_order:add")
    @OperLog(module = "销售订单主表", action = "新增")
    public R<Long> create(@RequestBody SalesOrderWriteRequest request) {
        return R.success(service.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("sales_order:sales_order:edit")
    @OperLog(module = "销售订单主表", action = "修改")
    public R<Void> update(@PathVariable Long id, @RequestBody SalesOrderWriteRequest request) {
        service.update(id, request);
        return R.success(null);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("sales_order:sales_order:remove")
    @OperLog(module = "销售订单主表", action = "删除")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.success(null);
    }
}
```

区间查询拆成 `amountStart` / `amountEnd` 两个参数而不是 `BETWEEN`：一端为空时 `BETWEEN` 会整条失效，两个独立条件才能各留一半。日期类查询参数带 `@DateTimeFormat` 按规范串绑定，与 `R` 出去的 JSON 格式同一个口径。

#### 前端列表页产物（节选，完整形状见 `scaffold-vue/src/views/{module}/{Cls}.vue`）

```vue
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { DateInput, Field, NumberInput, TextInput, Textarea, type Column } from '@yuanda-lowcode-ai/scaffold-ui/components/ui'
import { DictSelect, DictTag, FormDialog, ListPage, PermissionButton } from '@yuanda-lowcode-ai/scaffold-ui/components/business'
import { useListPage } from '@yuanda-lowcode-ai/scaffold-ui/composables/list'
import { confirm } from '@yuanda-lowcode-ai/scaffold-ui/composables/confirm'
import { pushToast, toastError } from '@yuanda-lowcode-ai/scaffold-ui/composables/toast'
import { required, validate } from '@yuanda-lowcode-ai/scaffold-ui/utils/validate'
import { salesOrderApi, type SalesOrderRow, type SalesOrderWriteRequest } from '@/api/sales_order/sales_order'

/* ============================================================
   销售订单主表管理。由低代码设计器按模型定义生成，属 B 档：每次发布整文件覆盖。
   版式固定走 ListPage + useListPage（查询 / 重置 / 分页 / 导出 / 行操作都是公共能力），
   页面只声明字段、列与动作。要加区块写到 src/views/sales_order/overrides/ 里；
   要长期人工接管这一页，就把它改名并让发布不再产出它 —— 现在它每次发布都会被整文件覆盖。
   ============================================================ */

type SalesOrderQuery = {
  orderNo: string
  amountStart: number | null
  amountEnd: number | null
  status: number | null
}

const list = useListPage<SalesOrderRow, SalesOrderQuery>({
  query: { orderNo: '', amountStart: null, amountEnd: null, status: null },
  fetch: params => salesOrderApi.list(params),
  exporter: params => salesOrderApi.export(params),
  exportName: '销售订单主表.csv',
})

const columns: Column[] = [
  { key: 'id', title: '主键', minWidth: 110, align: 'right' },
  { key: 'orderNo', title: '订单编号', minWidth: 160 },
  { key: 'amount', title: '订单金额', minWidth: 110, align: 'right' },
  { key: 'status', title: '状态', minWidth: 110, align: 'right' },
  { key: 'deliveryDate', title: '交货日期', minWidth: 160 },
  { key: 'remark', title: '备注', minWidth: 160 },
  { key: 'createTime', title: '创建时间', width: 160 },
  { key: 'actions', title: '操作', width: 112, className: 'col-actions' },
]

/** 正在编辑哪一行的主键；空着就是新增。不借表单里某个字段的值来猜 */
const editing = ref<number | null>(null)

async function openEdit(row: SalesOrderRow) {
  try {
    const detail = await salesOrderApi.detail(row.id)
    editing.value = row.id
    Object.assign(form, emptyForm(), detail)   // 编辑前先取整行：列表行只有勾选进列表的字段
    errors.value = {}
    dialog.value = true
  } catch (error) {
    toastError(error, '销售订单加载失败')
  }
}

async function submit() {
  errors.value = validate(form, { orderNo: [required('请输入订单编号')], status: [required('请选择状态')] })
  if (Object.keys(errors.value).length) return
  const payload: SalesOrderWriteRequest = { orderNo: form.orderNo.trim(), amount: form.amount, /* … */ }
  saving.value = true
  try {
    if (editing.value !== null) await salesOrderApi.update(editing.value, payload)
    else await salesOrderApi.create(payload)
    pushToast(editing.value !== null ? '销售订单已更新' : '销售订单已创建')
    dialog.value = false
    await list.load()
  } catch (error) {
    toastError(error, '销售订单保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ListPage title="销售订单主表管理" desc="本页由低代码设计器按模型定义生成；要改字段回到设计器改模型再发布。"
            table-title="销售订单列表" :list="list" :columns="columns">
    <template #actions>
      <PermissionButton perm="sales_order:sales_order:add" variant="primary" icon="plus" @click="openCreate">新增销售订单</PermissionButton>
    </template>
    <template #search>
      <Field label="订单编号"><TextInput v-model="list.query.orderNo" clearable @enter="list.search()" /></Field>
      <Field label="订单金额（起）"><NumberInput v-model="list.query.amountStart" :precision="2" /></Field>
      <Field label="订单金额（止）"><NumberInput v-model="list.query.amountEnd" :precision="2" /></Field>
      <Field label="状态"><DictSelect v-model="list.query.status" type="sys_normal_disable" all-label="全部" /></Field>
    </template>
    <template #cell-status="{ row }"><DictTag type="sys_normal_disable" :value="row.status" /></template>
    <template #cell-createTime="{ row }"><span class="tnum soft">{{ row.createTime ?? '—' }}</span></template>
    <template #cell-actions="{ row }">
      <div class="row-actions">
        <PermissionButton perm="sales_order:sales_order:edit" icon-only icon="edit" label="编辑" @click="openEdit(row)" />
        <PermissionButton perm="sales_order:sales_order:remove" icon-only icon="trash" label="删除" danger @click="remove(row)" />
      </div>
    </template>
  </ListPage>

  <FormDialog :open="dialog" :title="editing !== null ? '编辑销售订单' : '新增销售订单'" :saving="saving" @close="dialog = false" @submit="submit">
    <Field label="订单编号" required wide :error="errors.orderNo"><TextInput v-model="form.orderNo" /></Field>
    <!-- 自增主键不在这里（值由数据库给）；小数列的数字输入框带 :precision，否则金额被抹成整数 -->
    <Field label="订单金额" :error="errors.amount"><NumberInput v-model="form.amount" :precision="2" /></Field>
    <Field label="状态" required :error="errors.status"><DictSelect v-model="form.status" type="sys_normal_disable" /></Field>
    <Field label="交货日期" :error="errors.deliveryDate"><DateInput v-model="form.deliveryDate" type="date" /></Field>
    <Field label="备注" wide :error="errors.remark"><Textarea v-model="form.remark" :rows="3" /></Field>
  </FormDialog>
</template>
```

模板里的 `editing` 在 `<script setup>` 里要写 `editing.value`，在 `<template>` 里必须写 `editing` —— Vue 的 SFC 编译器只在模板上下文自动解包 ref。两处写反了单测看不出来（产物是字符串），交付项目 `npm run build` 的 `vue-tsc` 阶段会报 TS18047 / TS2551，这条是真机演练撞出来的。

### 4.3 生成文件路径规则

路径以**生成项目的真实目录结构**为准。生成项目就是脚手架的一份拷贝，顶层不是 `backend/` + `frontend/`，后端是 `scaffold-{module}` Maven 模块、前端是 `scaffold-vue` 单应用。下面每条标注所属所有权档（§5.2）。

> **PR 5E 起这张表由代码持有（2026-11-04）**：唯一事实源是 `scaffold-lowcode` 的 `GeneratedPaths` —— 路径由它拼、档位由它按路径反推，产物清单从 `ModelCodeGenerator.renderFiles()` 出来。本节只写形状与理由，具体每一格以代码为准；此前草稿里的 `{Cls}VO`、`domain/vo/`、`src/api/{m}.ts`、`views/{m}/{kebab}.vue`、`backend/`+`frontend/` 两层目录都已作废。

| 档 | 产物 | 落点（相对交付项目根） |
|---|---|---|
| B | 实体 / 入参 / 列表行 | `scaffold-system/scaffold-business/src/main/java/com/scaffold/business/{module}/domain/{Cls}.java`、`{Cls}WriteRequest.java`、`{Cls}Row.java` |
| B | Mapper / Service / Controller | 同上 `mapper/{Cls}Mapper.java`、`service/{Cls}Service.java`、`controller/{Cls}Controller.java` |
| B | 前端接口 | `scaffold-vue/src/api/{module}/{resource}.ts`（按模型一份，§10.2 第 ⑤ 条） |
| B | 前端页面 | `scaffold-vue/src/views/{module}/{Cls}.vue`（`{Cls}` 就是菜单 `component`，与路由解析一致） |
| B | 建表 / 菜单种子 | `scaffold-system/sql/pages/{table}_table.sql`、`{table}_menu.sql` —— 子目录不在 `docker-entrypoint-initdb.d` 的自动执行范围内，**只生成增量语句，禁 DROP / ALTER DROP COLUMN**，由人执行 |
| C | 后端扩展点 | `…/business/{module}/service/{Cls}Extension.java`：首次生成空骨架后即归人工所有，之后发布**存在即跳过** |
| C | 前端人写区 | `scaffold-vue/src/views/{module}/overrides/*.vue`（形状先认下，加载机制见 §10.2 开放项 3） |
| A | 其余一切 | `pom.xml`、`scaffold-admin` 装配、`src/api/index.ts`、`src/components/**`、`src/styles/**`、`main.ts`、Dockerfile、`docker-compose.yml` —— 页面发布**无权写**，`GeneratedPaths.tier()` 认不出来就抛错 |

C 档扩展点是"业务逻辑唯一允许落笔的地方"，平台只在首次生成空骨架、之后永不覆盖。前端 `overrides/` 用于人工覆写某个区块而不碰 B 档主文件（第一期只落分档表里的形状，机制待 §10.2 开放项 3 定稿）。

---

### 4.4 模块落点（新增，2026-11-03）

低代码能力直接长在本仓库，与 `scaffold-generator` 同层新增模块：

```
packages/
└── scaffold-ui          # 新增：@yuanda-lowcode-ai/scaffold-ui 组件包（73 组件 + 17 composable + theme.css）
                         #       导出 installScaffoldUi({http, permission, dict}) 注入点
                         #       npm workspaces 成员，平台与生成项目共用同一个包

scaffold-system/         # 后端 Maven 聚合
├── scaffold-common      # TenantContext（PR 1 已落）；无新增
├── scaffold-framework   # TenantFilter（PR 1 已落）；后续加租户 SQL 拦截器
├── scaffold-system      # 用户/角色/菜单/字典，已带 tenant_id
├── scaffold-generator   # 现有：表单元数据 → renderFiles() → zip 下载
├── scaffold-lowcode     # 新增：租户/项目/模型/字段/页面/发布/合并请求 业务
└── scaffold-admin       # 启动入口，聚合新模块
```

**复用的接缝（PR 5E–5F 已落地）**：生成逻辑只有一个出口 —— `ModelCodeGenerator.renderFiles(模型行, 字段行)` 返回 `List<GeneratedFile>`（公共出参类型，路径 + 内容 + 由路径反推的档位）。两条 sink 并存：`GeneratedFileZip` 打成 zip 给人手工取产物（老 `/tool/generator` 页的 `preview` / `download` 走这条），`GeneratedFileWriter` 按档位落进工作区（B 覆盖、C 存在即跳过），落盘后提交推送由 PR 7 接走。老 `GeneratorService` 不再拼字符串，它只做「读 `gen_table` / `gen_table_column` + 现查 `information_schema`」，由 `ImportedTableInput` 适配成同一份模型形状 —— 两条链路共用一套模板，才不会各自漂一套生成口径。依赖方向是 `scaffold-generator` → `scaffold-lowcode`（模板与派生层住在 lowcode），反向不依赖。

**Git 能力放哪**：`scaffold-lowcode` 内部 `publish` 包，不沉到 `scaffold-framework`——只有低代码业务用得到，放框架层会让普通后台项目背上 JGit 依赖。JGit 依赖（`org.eclipse.jgit` + `org.eclipse.jgit.ssh.apache`）只声明在 `scaffold-lowcode`。

**前端**：scaffold-vue 变成**宿主 app** —— 低代码视图进 `src/views/lowcode/**`，可复用组件与主题搬进 `packages/scaffold-ui`，两侧都通过包名引用。设计器专用组件（画布、属性面板、组件面板、diff 视图）留在宿主、**不进包**，且在 §6.4 被裁剪掉，不进任何生成项目。

**为什么包不单独建仓库**：平台与组件同步演进、跨仓库来回改的摩擦大于收益；本仓库 `packages/scaffold-ui` + npm workspaces，宿主用 workspace 依赖（本地即时生效），生成项目用发布到 npm registry 的版本号依赖（§〇「仓库与发布」）。

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

外加权限点（`permission_prefix` × 5）与 `@OperLog`。**这一类覆盖后台管理接口的绝大多数，不需要设计，只需要配置。**

> **两个开关本期不在此列（PR 5E，2026-11-04）**：树表（`is_tree` 时的 `/tree` 端点与树形页面）与数据范围（`enable_data_scope` 的 `@DataScope` 与部门过滤）**模板还没实现**，`ModelCodeGenerator.renderFiles()` 在进模板**之前**就把这两种组合抛掉，`models.vue` 上两个开关的说明同步改成「本期不生成」。不选「忽略勾选、把能生成的生成出来」：静默忽略等于交付一个「看着配置对了、实际没有」的项目，比报错更难发现。库里那两列的注释记的是列的设计意图，边界当前写在生成入口与 UI 上，比 ALTER 一次列注释更靠近读的人；要做的时候按 §5.2 加模板、把这两条拒收回即可。

> **主键的两条口径（PR 5G，2026-11-04，真机演练撞出来的）**：① **只支持数字主键**（`Long` / `Integer`）—— 五个入口的定位参数写的是 `@PathVariable Long id`，字符串主键的模型编得过但调不通（路径上拿到的是编号，Service 按数字查），`GenerationInput` 在进模板前就抛错说明原因，不做「生成一份看着对、跑起来错」的产物；② **自增主键不进表单也不进入参** —— 值由数据库给，进表单等于让人猜下一个 id，还会让页面的表单类型把 `id` 声明两遍（一条 `number | undefined`、一条 `number | null`），交付项目 `npm run build` 在 `vue-tsc` 阶段直接 `TS2717` / `TS2322`，前端镜像根本构建不出来。这条在两层各钉一次：`LcFieldService` 存字段时压掉自增主键的「可录入 / 可编辑 / 必填」（设计器里显示的就是真值，不留「勾了但没生效」），`GenerationInput` 组 `formFields` 时再排除一次（老链路的存量配置与库里改造前写入的行都可能还勾着）。
>
> 「新增还是编辑」因此不能靠表单里的值判断（自增主键根本不在表单里，业务编号主键新建那条也填了值），改成页面里一个独立的 `editing` ref 记着被编辑行的主键。注意它在 `<script setup>` 里要写 `editing.value`、在 `<template>` 里必须写 `editing` —— SFC 编译器只在模板上下文自动解包 ref，写反了单测看不出来（产物只是字符串），要到交付项目构建才炸。这条是 5G 第二轮构建抓到的。

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

**落地事实（PR 7，2026-11-05）**，实现是 `com.scaffold.lowcode.publish.PagePublishService`：

- **上图的「哈希对比」由 Git 本身完成**：产物落进工作区后 `commitAll` 在没有任何改动时返回 `null`，
  这就是 no-op —— 不再另算一套哈希，否则「平台认为变了而 Git 认为没变」会出现一个空提交或一个漏掉的发布。
  版本号与 `lc_page_version` 只在推送成功、tag 落到远端之后才写（**先落到仓库、再记库里的账**）；
  库里说发过 v3 而仓库里查无此提交，PR 9 的回滚就会去取不存在的内容。
- **发布现场有四条守卫**，都在动手之前拒绝并说清怎么办：项目 `sync_status` 必须是就绪（发布不替人初始化）；
  工作区目录得是真仓库；工作区必须干净（提交是整目录暂存，脏着就会把人在工作区里的临时改动一起推上去）；
  本地与远端的发布分支必须一致，不一致时按 `last_commit_sha` 分辨是谁前进的 —— 远端仍停在平台上次推上去
  那个提交就是「上次推送没成功、这次补推」，否则是别人往发布分支推过东西，要求先重建工作区（不强推、
  不自动合并、不"修好"）。配套把 `resetWorkspace` 的守卫改成同一口径，否则人往发布分支推过一次之后，
  重建会被当成「有未推送提交」而永久发不动。
- **`lc_page_version` 与 `lc_publish_log` 分工**：前者只在成功发布时有行（一个版本号一条，带当时的设计原文
  与产物哈希，是 PR 9 的取数来源），后者每次动作一条（成功 / 无变更 / 失败都留）。失败必须留痕：
  「库里说这个项目今天发布过三次而仓库里只有两个版本」只有靠日志才说得清是哪一次推送没出去。
- **踩到的一处真缺陷**：`ls-remote` 对附注 tag 返回两行，第一行是 tag 对象自己的号。原来只读第一行，
  「tag 与 `commit_sha` 对不对得上」这条验收就永远对不上，而 PR 9 按 tag 取内容也会拿一个不是提交的号。
  现在 `RemoteRef.commitSha()` 给出解引用后的提交（`JGitGatewayTest` 钉住语义，PR 7 的 IT 钉住链路）。
- 用例是 `PagePublishApiTest` 9 例，**远端用真 JGit 打在本机裸仓库上**而不是 `StubGitGateway`：
  这四条验收全是 Git 语义，桩只能证明平台自己怎么记账，证明不了仓库里真有什么。首次发布的产物
  还顺手真起 `javac` 编一遍。
- **真机那一趟（2026-11-05，对着真实 GitHub 仓库）**：把 PR 6C 期间在设计器里搭出来的那条
  `sales_order_page` 连发三次 —— 第一次远端发布分支前进、tag 指到那个提交、`main` 未被碰、库里
  版本与日志逐字对得上；第二次同样的设计是 no-op（不产生提交、不涨版本、不打第二个 tag）；第三次
  是修掉下面的缺陷之后重发。**它同时暴露了 §1.5 合流层的两处缺陷**：① 菜单权限点只收了工具栏那些动作
  （行操作并进来时写成了交集），于是页面有「编辑 / 删除」两颗按钮、Controller 有对应
  `@RequiresPermission`，而项目里的角色拿不到 `:edit` / `:remove` —— 按钮看得见、点下去 403；
  ② 画布没填标题时 `PageDesign.title()` 兜到 `modelRef`，客户页面的大标题显示成英文模型编码。
  两处都先写成失败用例再修（`DesignGenerationTest`）。这就是为什么 §10.3 要求每一刀都跑真东西：
  各段单测都只看半边，那两句断言拿错的实现同样是绿的。

### 5.2 文件所有权三档（替代原「CUSTOM-START/END 文本级合并」）

生成分支是平台唯一的产物出口，主干由人掌控。文件分档的作用不是"保证合并无冲突"，而是：

1. 界定 B 档可以整文件覆盖——因为约定人工不在主干改它；
2. 让 C 档承载手写业务逻辑且平台永不碰——合并时的 diff 才是"看得过来"的量；
3. 让危险变更（如删列 SQL）不进 B 档，只以提示形式出现在 diff 里。

| 档 | 内容 | 平台行为 | 人工约定 |
|----|------|----------|----------|
| **A 固定文件** | 后端 `scaffold-common` / `framework` / `system` 源码、前端 app 壳（`main.ts`、路由与布局、登录页、工作台）、`pom.xml`、`package.json`（含 `@yuanda-lowcode-ai/scaffold-ui` 版本号）、Dockerfile | **不随页面发布生成或覆盖**；只在人工发起的「底座升级」动作里重新生成，推独立升级分支走合并请求（见下方「A 档怎么跟着框架迭代」） | 可随意修改。**组件与样式不在 A 档** —— 它们是 `@yuanda-lowcode-ai/scaffold-ui` 依赖，升级只 bump 版本号，不进 diff |
| **B 纯生成文件** | `{Cls}.java`（实体）、`{Cls}WriteRequest.java`、`{Cls}Row.java`（列表行）、`{Cls}Mapper.java`、`{Cls}Service.java`、`{Cls}Controller.java`、`api/{module}/{resource}.ts`、`views/{module}/{Cls}.vue` | 每次发布**整文件覆盖** | 不在主干手改；要改回到模型或设计器改，再发布。人硬改了也不静默丢 —— 改动会在合并请求的 diff 里与生成结果正面撞，由人取舍 |
| **C 扩展点文件** | `service/{Cls}Extension.java`、`views/{module}/overrides/*.vue` | 首次生成**空骨架**后即视为人工资产，之后发布**存在即跳过** | 业务逻辑写这里，平台不碰。**钩子方法签名是平台契约**：改名或删方法，下次发布的 B 档调用点直接编译失败，这是刻意设计（可见失败优于静默改行为） |

> 命名口径（2026-11-04 定稿，覆盖此前写法）：本表原写 `{Cls}ServiceImplCustom.java` / `{resource}.ext.ts` / `{Cls}VO` / `{Cls}Query` / `types.ts`，那是 interface+impl 那套约定的残留。§10.2 开放项 2 已定为**生成物向底座看齐**（单类 Service、`WriteRequest`、`PageQuery` + 逐个 `@RequestParam`），所以扩展点统一叫 `{Cls}Extension`，VO/Query/types.ts 三类文件不再存在；本节与 §4.2、§4.3 一律按此口径。

**分档判定已经落成代码（PR 5E，2026-11-04）。** 上面这张表不再是"实现要照着抄的规格"，而是 `scaffold-lowcode` 里 `GeneratedPaths` 的说明文档：产物路径由它拼、档位由 `GeneratedPaths.tier(path)` 按路径**反推**，`GeneratedFile.of(path, content)` 是唯一的构造入口 —— 模板不能自己声明"我属于 C 档"。判档用白名单形状匹配（列出 B/C 允许的每一种路径形状，认不出就拒绝），不按前缀排除：`src/api/` 前缀下就住着 A 档的 `index.ts` 汇总出口。落盘那一侧（`GeneratedFileWriter`）B 档覆盖、C 档存在即跳过，并在写之前再判一次路径没越出工作区。

C 档后端的骨架是一份 `{Cls}Extension`，七个钩子：`afterRows(List<{Cls}Row>)`（列表与导出共用）、create / update / delete 各一对 `before*` + `after*`。B 档 Service 的构造器把它当依赖注入并逐个调用，所以"改名或删方法就编译失败"这条契约是真的（常驻测试 `GeneratedCodeCompilesTest` 把七个 java 文件一起交给 javac）。钩子都在 Service 的 `@Transactional` 里跑，`after*` 抛异常连带回滚 —— 骨架的类注释里写明了，别把 `after` 当提交后执行。

**"存在即跳过"的前提**：调用方给的工作区要反映项目当前内容。发布分支落后主干时，主干上人工改过的扩展类在这份工作区里不存在，骨架会被重新生成，两边在合并请求里以 `both added` 冲突暴露 —— 不静默丢代码，但 PR 7 组工作区时要把这条做对（**已兑现，见下方「落地事实」**：发布前要求本地与远端一致，重铺固定代码时放过 B/C 档形状）。

> 原方案的 `CUSTOM-START / CUSTOM-END` 标记 + 文本级 diff 合并机制**取消**：它是"平台覆盖文件"与"保留手写代码"两个诉求在同一文件上的强行调和，实现复杂且最容易静默丢代码。分档 + 分支隔离 + 人工审 diff 三重手段已覆盖该诉求。若个别场景仍需在一个文件里混排，可自行在 C 档文件内用标记，但平台不参与解析。

**A 档"固定"的硬约束：一个项目只有一个业务模块。**

新增 Maven 模块必须同时改两处 A 档文件 —— 父 `scaffold-system/pom.xml` 的 `<modules>` 与 `scaffold-admin/pom.xml` 的 `<dependencies>`（本仓库自己加 `scaffold-lowcode` 就是这样改的）。如果每个业务模块都这么来一次，"A 档不随发布更新"就破了，每次发布都会带上 pom 冲突。

所以约定：**生成项目初始化时预置单一业务模块 `scaffold-business`，所有生成的业务代码进它的子包**（`com.scaffold.business.{model}`），pom 在初始化时一次写好、此后平台永不改。`lc_project.backend_module` 默认值 `business` 正是为此；它允许改名但只影响初始化时的路径生成。前端同理：业务视图与接口只往 `src/views/{module}/**` 与 `src/api/{module}/{resource}.ts`（按模型一份，见 §10.2 第 ⑤ 条）增文件，不碰路由与注册表（靠 `sys_menu` + `import.meta.glob` 自动生效）。

这条也让合并请求的 diff 更干净：A 档不出现在 diff 里，审的就是"这次发布动了哪些业务文件"。

**A 档怎么跟着框架迭代（否则平台级演进就断在存量项目上）。**

A 档的「固定」指的是**不随页面发布自动覆盖**，不是「平台再也产不出这些文件」—— 真按后者理解，底座修个 bug、加个通用能力，存量项目就永远拿不到。所以下发的通道单独设计：

1. **动作与页面发布分开**：页面发布只碰 B/C 档；A 档变更走人工发起的「**底座升级**」动作（已落地，PR 10：三方合并 + `platform-upgrade/*` 升级分支，界面沿用合并请求那一套）。
2. **产物落独立分支**：平台按当前裁剪清单重新生成 A 档，推到 `platform-upgrade/{base_scaffold_sha}` 分支，不碰主干、不碰发布分支。
3. **三方合并而不是覆盖**：绝不静默覆盖项目私有值（数据库连接、`artifactId`、品牌文案、`.env`），也不把平台的新东西漏掉 —— 三方合并能把「人改的那几行」和「平台改的那几行」分开，真冲突交人解。已落地（PR 10）：`GitGateway.threeWayMerge(dir, base, ours, theirs, paths)` 走 JGit 的 in-core `MergeAlgorithm`，与合并请求的预检 `checkMerge` 同一套底座原语、不落盘也不碰 `.git/index`，**不新造机制**；一个文件一个结论（`MergeOutcome` 五档），只有 `CLEAN` 会被写进升级分支。一条要知道的差别：**JGit 把相邻行的两处修改也算冲突**（git 命令行会把它们和掉）—— 多报的冲突只是把「平台不替你决定」的范围放宽，方向是安全的。
4. **人审才合**：合并请求里看 diff、确认、合并；平台不自动合并、不 force-push、不批量下发（一次一个项目，由项目侧的人决定）。
5. **先看见差距再决定升不升**：`base_scaffold_sha` 与平台 HEAD 之间的提交数 + A 档文件级差异清单，做成项目详情的只读「落后多少」读数。

三方合并的三个输入分别从哪来（缺一个就退化成人肉对比）：

| 角色 | 内容 | 来源 | 要不要平台侧底座 git 历史 |
|------|------|------|--------------------------|
| **ours** | 项目 `main` 当前那批 A 档文件 | 项目仓库 | 不要 |
| **theirs** | 新版 A 档 | 平台按**底座 checkout 当前 HEAD** 跑一遍裁剪清单 + 交付改写，现生成 | 不要 |
| **base** | 当初交付那一版的 A 档 | 项目仓库里的**底座标记 tag** `yuanda/platform-base/{底座提交号}` | 不要 |

**base 侧留痕已经落地（PR 4 的初始化链路上）**：平台每交付一份完整 A 档快照，就给那个提交打一个附注 tag `yuanda/platform-base/{base_scaffold_sha}` 并推到项目仓库，初始化结果与工作区读数都会回报它。这个 tag 是**只创建、永不移动、永不删除**的 —— 移动它等于改掉某一轮升级的 base，三方合并会算出错的结果；同名 tag 已存在就说明这个底座版本交付过了，原样留着。tag 推不上去（仓库侧开了 tag 保护）不回滚初始化，只在 `sync_message` 里留告警。

于是「平台能不能读到某个历史版本的底座源码」这个原本很难的部署问题被绕开了：base 从项目仓库拿，theirs 从底座 checkout 当前 HEAD 现生成，**都不需要平台侧保留底座 git 历史**。历史只剩一个装饰性用途 —— 第 5 步那个「落后多少个提交」的计数；拿不到就只显示文件级差异清单，不影响升级本身。

两条部署不变式（不守住就会产出对不上的 base）：

- **底座 checkout 必须带 `.git`**。只拷源码树时 `base_scaffold_sha` 为空，tag 打不出来，这个项目以后就做不了三方底座升级。平台不静默容忍：初始化照做，但会把这句话写进 `sync_message`，项目抽屉的「底座标记 tag」一行也会显示未记录。
- **平台镜像与底座 checkout 必须成对更新**。裁剪清单（`ScaffoldTrimPolicy`、交付改写表）在平台代码里，底座源码在 checkout 里；两者版本错开时，交付物是按 A 版清单裁 B 版源码得到的，而 tag 记的是 B 版的提交号 —— base 就成了假话。主机上 `git pull` 了底座却没重建镜像，是最容易踩到的一种。平台侧暂未做自动核对（PR 8 可加构建期烘进镜像的 sha 与 checkout HEAD 比对），当前靠部署流程约束：**动底座就重建镜像，两件事一起做**。

组件与样式不在这条路上 —— 它们是 npm 依赖，升级 = 改 `package.json` 一行版本号，项目侧随时可以自己改（作为 ours 参与三方合并，不会被平台版本冲掉）；平台想统一推新组件版本才走底座升级。也因此 A 档升级 diff 的量比抽包前小一个数量级。

**C 档引用体检（删字段不能只等编译和 diff 来兜）。**

「模型删了字段、人写的 `{Cls}Extension` 还在引用」这件事，只靠交付项目编译 + 合并 diff 太晚也太吵：等编译失败时人已经在合并请求里了，而 diff 只会显示"生成物变了"，不会告诉他"你少了一个字段"。所以平台侧提前一层：

1. **删字段入口就拦**：与页面一样先反查影响面 —— 哪些页面引用（`lc_page.ref_fields`，PR 3b 已落）、该模型的扩展代码里有哪些符号还在用它，合成「受影响清单」要求操作者确认。字段**改名 = 删 + 建**（列名由编码派生），所以改名同样走这道检查。
2. **发布前引用体检**：从工作区/远端读该项目的 C 档文件，按模型字段派生符号（`get{Field}()` / `set{Field}(` / `.{javaField}`）做文本级反查，命中「当前模型已不存在的字段」就在发布预检里列出 `文件:行 + 字段名`，**默认阻断发布**，操作者可显式选择「我已确认，仍发布」（可能是同名局部变量或注释）。
3. **口径**：宁多报不漏报。本期只做正则级检查，不引 Java AST 解析器 —— 目标是把"删了字段还引用"这类高频事故提前 5 分钟暴露，编译器仍是最后防线，不是第一道。

**落地事实（PR 7，2026-11-05）**，实现是 `ExtensionReferenceCheck` + `PagePublishService.staleReferences`：第 2 条已进发布链路（默认阻断，`force` 显式放行，命中仍原样留在发布结果里），**第 1 条「删字段入口就拦」还没做** —— 那需要删字段时就去读工作区里的扩展代码，而工作区可能根本不存在（换过服务器、清过卷），静默跳过等于这条规则时灵时不灵；先只做发布前那一层，删字段侧仍按 `lc_page.ref_fields` 反查页面。三处判据的取舍：

- **「合法」按本次发布真正生成的 `domain/` 里声明的成员算，不按 `lc_field` 的字段清单**：主键与审计列是模板补到类上的、字段表里没有它们，照字段清单判就会把 `row.getCreateTime()` 报成错引用。
- `package` / `import` 的包名段在文本上长得和「对象.字段」一模一样，而任何一份 java 都有这两行 —— 不排掉就是每一次发布都被自己这份清单拦下（这条是 IT 第一次跑就撞出来的，九个用例全被噪音挡住）。方法调用（`.setScale(`、`.size()`）与类名限定的静态访问（`System.out`）也不算字段引用；`type.getName()` 这种分不清读取对象的命中**照报**，代价由 `force` 承担。
- 人自己在扩展点里声明的方法与字段不报（那是这个文件的私有词汇）。

**"存在即跳过"的前提已经兑现**：重铺固定代码（`pruneOutsideSnapshot`）现在会**放过 `GeneratedPaths` 认得出的 B/C 档形状**。少了这层判断，「重建工作区 + 重新初始化」会连人写的扩展点一起从发布分支删掉 —— 而 C 档正是平台唯一产不出来的东西（`PagePublishApiTest` 里那条「人往发布分支推一行代码 → 平台拒绝 → 重建 → 那一行还在」钉的就是这件事）。工作区的定位规则也抽成了 `WorkspacePaths`，初始化与发布两处不再各拼一遍 `{tenantId}/{projectCode}`。

其余更新策略：

| 文件类型 | 更新策略 | 说明 |
|----------|----------|------|
| 建表 SQL | 覆盖到项目仓库 `scaffold-system/sql/pages/{表名}_table.sql`，`CREATE TABLE IF NOT EXISTS` | **只建「新表」**：对已存在的表不加列、不删列，`DROP` / `ALTER ... DROP COLUMN` 一条都不生成。模型加过字段就看合并请求的 diff —— 多出来的那一行就是要人工补的 `ALTER TABLE ... ADD COLUMN`（MySQL 没有 IF NOT EXISTS 的加列写法，把 ALTER 写进这个文件会让第二次执行直接报错）。破坏性变更在发布结果里以提示列出，由人写迁移。这目录**不在** `docker-entrypoint-initdb.d` 的自动执行范围内（mysql 不递归子目录），由人在目标库执行一次 —— 平台不托管运行时，也不连项目的库 |
| 菜单 SQL | 覆盖到 `scaffold-system/sql/pages/{表名}_menu.sql`，按 `permission` 判重的 `INSERT ... SELECT ... WHERE NOT EXISTS` | 不能照抄底座 V6 的「固定 ID + `INSERT IGNORE`」—— 那是因为它知道 502 这个号，而 `sys_menu.id` 在 V4 已改成数据库自增，生成物不知道本项目用到哪个号。同理授权也只按已存在判重，只授给内置管理员角色（`role_id = 1`），其它角色由项目侧在角色管理里勾。页面本身靠 `sys_menu` + `import.meta.glob` 自动注册，`component` 就是 `views/{module}/{Cls}` |
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

> **实现状态（2026-11-06，PR 9 / PR #20）**：**文件级与页面级已落地** ——
> `GET /api/lowcode/pages/{id}/versions[/v]` 是版本历史的唯一读路径，`POST /pages/{id}/rollback`
> 取的是**仓库里那一笔的内容**而不是按今天的模板重跑（重跑拿到的是「那一版今天应该长什么样」），
> 落盘前先拿快照哈希与仓库内容对账；产出的是**一笔新提交、新版本号 `current+1`、照样打 tag**，
> 于是回滚出来的那一版本身也能再被回滚回去；`lc_page_version.publish_type` 从此有第三种 `ROLLBACK`。
> 只写发布分支，进主干仍走合并请求。**发布级（revert 某次发布或合并提交）没做**：现在一版只记一个页面
> 的产物，"一次动作牵动多个页面"这个粒度还没有落点，要做就得先决定它记在哪张表上。
> C 档不参与回滚这条口径没变，判据由 `GeneratedPaths.tier` 现场反推，不靠调用方自觉。

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

**初始化只建发布分支，不建主干 —— 所以空仓库起步的项目第一次合并必然被挡（11A 整链演练定稿）**：`initialize` 推的是 `{gen_branch}`；主干由人掌控，平台不替人决定仓库的起点，于是 `merge-requests/refresh` 在远端没有主干时会拒：「远端没有主干 main：合并的目标不存在…请先在仓库侧建出来」。这一步**必须写在交付流程里**，否则新租户走完「初始化 → 发布」，第一次点「生成合并请求」就卡在一句 400 上。仓库侧建一次即可（以发布分支当前那笔为起点）：

```bash
git clone <仓库> && cd <仓库>
git push origin origin/platform-generated:refs/heads/main
```

建完之后主干与发布分支同尖，此时重算是**零差异** —— 平台不为它建请求、点合并也会被拒（见 §5.3 与 §10.3 PR 11），等下一次发布推进发布分支才有真内容要合。

### 6.4 初始化快照的裁剪清单

**生成项目拿到的不是本仓库全量，而是"脚手架减去低代码平台自身"**。不做裁剪，每个生成的项目里都会躺着一套设计器、租户管理和一个能反向生成代码的工具菜单。

| 处置 | 内容 | 理由 |
|------|------|------|
| **剔除** | `scaffold-lowcode` 模块及 admin pom 中对应依赖 | 平台业务不该出现在交付项目里 |
| **剔除** | `scaffold-generator` 模块、`views/tool/**`、对应 `sys_menu` 种子行 | 否则项目里有一个在线代码生成入口 |
| **剔除** | `lc_*` 建表语句与租户管理菜单种子 | `lc_tenant` / `lc_project` / `lc_model` 等是平台元数据 |
| **剔除** | 设计器专用前端组件（画布、属性面板、组件面板、合并请求 diff 视图）**不进包也不进项目**；平台/演示专属组件（`GeneratorPipeline`、`OrgCanvas`、`MenuStarMap`、`PermissionMatrix`、`PermissionTrace`、`DictSwatchbook`、`AskDock`、`InsightPanel`）留在平台宿主，同样不进项目 | 普通业务后台不需要，也不回流为通用组件（§4.4、§〇） |
| **保留** | `scaffold-common` / `framework` / `system` 源码、app 壳（`main.ts`、路由与布局、登录页、工作台） | 这就是"现在这套空框架"的可运行部分 |
| **改为依赖** | 组件库与珍珠玻璃样式不再拷源码进项目，而是 `package.json` 里的 `@yuanda-lowcode-ai/scaffold-ui` + `main.ts` 里的 `installScaffoldUi` 与 `theme.css` | 生成项目与平台共用一套组件物料；升级从"搬源码 diff"降为"bump 版本号"（§〇） |
| **保留** | `sys_*` 的 `tenant_id` 列与 `TenantContext` / `TenantFilter`，生成项目里租户恒为 `1` | 避免维护两套 schema 与两套认证代码；将来项目要独立多租户时列已在，无需迁移 |
| **预置** | 单一业务模块 `scaffold-business`（空壳）+ 其 pom 装配 | 见 §5.2：平台此后不改任何 A 档 pom |

裁剪清单要作为**代码里的一份显式配置**（路径白/黑名单），不是手工拷一遍目录 —— 它是发布产物的定义，改了就要能测试。建议随 PR 4 一起落地并配一份"裁剪后项目能编译、能起、能登录"的集成用例。

**落地事实（PR 4，2026-11-04）** —— 清单写在 `ScaffoldTrimPolicy`，机制与原表有三处出入，都按代码为准：

1. 手段是**路径白/黑名单 + 文件内的 `scaffold:platform-only:start/end` 标记块**两层。第二层是因为有些平台专属内容长在必须保留的文件里：父 pom 的 `<module>`、admin pom 的依赖、`docker-compose.yml` 的工作区卷、`application.yml` 的平台参数段、`V1`/`V2` 里的 `gen_table` 与「系统工具」菜单种子、前端 `api/index.ts` 的平台客户端、启动类里平台模块的 Mapper 扫描包。标记独占整行，写完快照还会扫一遍残留引用（`com.scaffold.lowcode`、`/api/lowcode`、`lc_project` 等），漏裁就拒绝初始化而不是带病出门。
2. **`lc_tenant` 保留在交付项目的库结构里**，与原表「剔除 `lc_*` 建表语句」字面不同。理由：`AuthService.login()` 每次登录都要查租户是否停用、`currentUser()` 要读租户编码与名称，删了这张表要么登录直接报错，要么给交付项目维护第二套认证代码 —— 与原表「避免两套 schema、两套认证代码」的约定相反。剔除的是其余元数据表（`lc_project` / `lc_model` / `lc_field` / `lc_tenant_config` 与后续 `lc_page`、`lc_publish_log`、`lc_merge_request`）。交付项目里这张表恒为一行（1 号租户）。
3. **组件的归属按实际引用裁**：`OrgCanvas` / `PermissionMatrix` / `PermissionTrace` / `MenuStarMap` / `DictSwatchbook` / `InsightPanel` / `AskDock` 原表列为「平台与演示专属、不进项目」，实测它们分别被部门 / 角色 / 菜单 / 字典四个基础页在用；把它们剔掉交付项目就编译不过。本期只剔真正只服务于被删页面的 `GeneratorPipeline`。这批组件的归属应在 PR 4B 抽 `@yuanda-lowcode-ai/scaffold-ui` 时用「进包 / 留宿主 / 进项目」三态重新判定，而不是继续靠这份表。
4. **PR 4B 把三态判完了，并给裁剪加了第三层手段**（2026-11-04）。三态结果：**进包** = 上述被基础页在用的组件 + `ui`/`charts`/`Icon` + 16 个 composable + 工具 + 七层样式（合并成 `theme.css`）；**留宿主** = app 壳 `components/layout/**`、`NeuralNet.vue`、品牌 `data/index.ts`、`composables/auth.ts`（登录态与动态路由），它们照旧作为 A 档源码进项目；**不进项目也不进包** = `components/platform/GeneratorPipeline.vue`。包与根 `package.json` / `package-lock.json` / 根 `.dockerignore` 都不进交付，组件改由 `@yuanda-lowcode-ai/scaffold-ui` 依赖提供（§〇）。
   - 只做「删除」不够了：底座有六处写的是「平台怎么用 npm workspaces 构建」，交付项目要换成「按 npm 依赖怎么构建」，同一批行既要消失又要换成别的内容 —— 标记块表达不了。于是 `ScaffoldTrimPolicy.deliverySubstitutions()` 增加**精确整行块替换表**（`scaffold-vue/package.json` 的依赖行、`scaffold-vue/Dockerfile` 两段、`docker-compose.yml` 的 frontend.build、README 两处命令注释、部署文档的构建说明行），替换文本里的 `{SCAFFOLD_UI_VERSION}` 由 `scaffold.lowcode.scaffold-ui-version` 填。改写块在底座里找不到或出现多次就当场失败，宁可不出门。
   - **交付项目的前端没有锁文件**（锁跟着平台的 workspace 走），镜像里改用 `npm install`，可复现性由 `@yuanda-lowcode-ai/scaffold-ui` 的版本号承担；这一条在 `scaffold-vue/Dockerfile` 的交付形态里写明了。交付目录另带一份 `scaffold-vue/.npmrc`，把 `@yuanda-lowcode-ai` 这个 scope 钉在官方 registry —— 国内镜像同步新包滞后，按镜像装会 `ETARGET: No matching version found` 直接失败，其余依赖仍走机器默认镜像不受影响。
   - 演练暴露一个真实缺陷：平台本地工作区目录（`lowcode-workspace/`，容器里是卷）里装着上一版**交付克隆**，而它落在 `scaffold-system/` 白名单子树内，于是快照会把上一版交付整个套进新版 —— 文件数从 282 涨到 460 才被发现。已把 `lowcode-workspace` 加进 `IGNORED_NAMES`（顺带 `.vite`），并加了两条断言。修完的真实快照 **177 个文件**，用 `file:` 指向 `npm pack` 出的 tarball 装依赖 → `vue-tsc` + `vite build` 全绿，交付 `docker-compose.yml` / README / 部署文档里已读不到仓库根 workspace 与平台死链。
5. **全量初始化 = 发布分支按快照整体替换**（存量项目真机升级后补的语义，2026-11-04）。第一次把旧形态项目升级到新形态时 diff 里 **D=0**：写入只有「写」没有「删」，发布分支上同时存在「npm 包里的组件」和「上一版拷进来的组件源码」，抽包意义被抵消。现在 `ScaffoldSnapshotWriter.pruneOutsideSnapshot()` 只在**非空仓库的全量初始化**里删快照外的文件并收空目录（保留 `.git`），删除数写进 `sync_message`。边界要说清：这条只作用于平台自己维护的发布分支，**主干上人工写的 C 档文件平台照旧不碰** ——「平台无权清理清单之外的东西」那句原话只对主干成立。
   同一次演练还暴露一个状态死角：`sync_status=就绪` 而工作区目录已不在（换服务器、清过卷、手工删目录）时，初始化被「已就绪不许再初始化」拒、清空入口又因 `workspace.exists=false` 不出现（后端也抛「工作区不存在，无需重建」），项目两头卡死。现在 `resetWorkspace` 在目录缺失时只把状态清回未初始化（不动远端，因此不需要脏/未推送守卫），前端按钮条件改成「目录在 **或** 状态非未初始化」。
   真机结果：`main` 全程未被写，`platform-generated` 上得到一次干净的形态迁移 diff（`D=107 / A=2 / M=37`，清理 109 个遗留文件），干净 clone 该分支后 `npm install` 装到 `@yuanda-lowcode-ai/scaffold-ui@1.0.0`、`vue-tsc` + `vite build` 通过。**存量升级通道可用，但「谁把新版本号写进项目」仍未落地**（PR 7/8 待办：落后读数 + 依赖升级动作走合并请求）。

`lc_project.base_scaffold_sha`（§2.2）记录该项目快照取自本仓库哪个提交，用于日后判断"这个项目落后多少"，也是 §〇 方案②（用合并请求下发 A 档升级）的起点；交付项目根上的 `.scaffold-project.json` 里写同一份提交号，两边互相能核对。

6. **交付品牌跟着项目走（PR 5G 真机演练后补，2026-11-04）**。演练时看见的客户项目登录页写着「元搭低代码平台」、侧栏写着「元搭低代码」，还宣传「代码生成」这个已被裁掉的功能 —— 交付的是项目自己的后台，不是平台的副本，所以这是缺陷不是外观。做法是给交付改写表再加两条：`scaffold-vue/src/data/index.ts` 里 `APP.name` / `APP.shortName` 两行换成 `{PROJECT_NAME}`（初始化时按 `lc_project.project_name` 填，空名拒绝交付；自由文本进 TS 字面量前转义引号与反斜杠），`views/Login.vue` 的「代码生成」那条能力直接删。底座自己那份不动，平台界面仍显示平台名。同一层守卫扩到品牌：交付产物里出现平台品牌关键字就当场拒绝初始化。**连带改了生成物话术** —— 11 份模板的注释与页面文案原样写着「由元搭低代码平台按模型定义生成」，「整页接管见方案 §5.2」引用的又是交付清单里不存在的本文档章节，统一换成「由低代码设计器按模型定义生成」与一句能执行的话；这条由 `ModelCodeGeneratorTest` 扫产物断言（改实现前它是红的，红得正好：列出的就是模板原话）。注意品牌改写属 A 档，**存量项目要等 PR 8 的底座升级才拿得到**。

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
| **P1.5：组件抽包 `@yuanda-lowcode-ai/scaffold-ui`** | 2.5 周 | `packages/scaffold-ui` + npm workspaces；组件与 composable 迁入；**解 app 层耦合**（注入点 `installScaffoldUi`）；`theme.css` 合并 7 个 css；平台专属组件划出包外；发布为 npm 包并让生成项目按版本号依赖 | **抽包与注入点已完成**（2026-11-04，PR 4B），包名 `@yuanda-lowcode-ai/scaffold-ui`，`files` 白名单已收；剩正式 `npm publish --access public` 与 demo app 消费验证，**P2/P3 的硬前置** |
| **P2：可视化设计器** | 5 周 | 三栏骨架 + 流式拖拽 + 容器嵌套 + 属性面板；**第一期只覆盖列表页 + 表单弹窗两类模板**，看板/详情/流程后置 | 待做 |
| **P3：代码生成升级** | 3 周 | FreeMarker 模板替换 `StringBuilder`、完整 CRUD 生成、A/B/C 文件分档与 C 档扩展点骨架、`renderFiles` 出参提为公共类型；**模板产物的 import 走 `@yuanda-lowcode-ai/scaffold-ui`**；自定义接口只生成 `custom:{name}` 的 TODO handler 桩（§4.5） | 待做 |
| **P4：发布 + 仓库 + 合并请求** | 4 周 | Git sink（JGit：clone/commit/push/tag）、空仓库初始化、工作区卷与构建缓存、`lc_publish_log`、合并请求（diff 列表 + unified diff + 可合并性预检 + 合并执行）、三级回滚 | 拆成 PR 4 / PR 7 / PR 8：PR 4（JGit、连通性、工作区卷、裁剪清单、空仓库初始化与真机推送）✅、PR 7（页面发布链路：提交 → 推送 → tag → `lc_publish_log`、no-op 幂等、发布前引用体检）✅ 已完成；合并请求（PR 8，PR #19）与页面回滚（PR 9，PR #20）已合入 dev，底座升级与落后读数、合并后搬 base 侧为 PR 10（分支 `feat/lowcode-pr10-base-upgrade`，向 dev 开 PR）。这一格剩下的是真机那两趟：PR 10 的「升级 → 审 → 合 → 重打 tag → 读数归零」与 §5.4 的发布级回滚 |
| **P5：联调测试** | 2 周 | 端到端（建项目 → 建模 → 设计 → 发布 → 合并 → clone 起来跑通）、租户隔离回归 | ✅ 已完成（2026-11-06，PR 11：整链从空仓库一路跑到交付容器里真跑增删改查，见 `changelog.md` 同日「PR 11」一条；租户隔离回归此前每刀都在补） |

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
| 脚手架升级下发困难 | 版本碎片化 | 组件走 `@yuanda-lowcode-ai/scaffold-ui` 包，升级 = bump 版本号；后端用 Maven BOM 管版本；`lc_project.base_scaffold_sha` 记录各项目落后多少。A 档 app 壳的差异走**独立的「底座升级」动作 + 三方合并 + 合并请求人审**下发（机制见 §5.2），不随页面发布混在一起，也不静默覆盖项目私有配置。三方合并的 base 侧靠项目仓库里的 `yuanda/platform-base/*` tag（初始化时已打，只创建不移动），平台侧因此**不必保留底座 git 历史**；代价是两条部署不变式：底座 checkout 必须带 `.git`（否则记不到提交号、tag 打不出来，平台会如实告警），平台镜像与底座 checkout 必须成对更新（否则裁剪清单与底座源码来自两个版本，tag 记的 sha 就是假话） |
| 生成项目 `npm install` 拉不到物料包 | 中 | 发布走 npm registry 后不需要 GitHub 凭据；私有包则把 `_authToken` 配在主机 `~/.npmrc`（不进 DB、不进仓库）；"裁剪后项目能编译能起能登录"的集成用例必须包含一次干净 `npm ci`，避免只在开发机凭缓存跑通 |
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
| 组件物料 | **统一走 `@yuanda-lowcode-ai/scaffold-ui` 包**（放本仓库 `packages/scaffold-ui`，npm workspaces），平台与生成项目共用；组件不再拷源码进生成项目。不存在 fork：平台前端就是 scaffold-vue 宿主 |
| 包的发布渠道 | **npm registry**（2026-11-04 定稿，取代早先的 GitHub git tag 方案）：生成项目按普通版本号依赖，构建不需要 Git 凭据；发布 token 只落主机 `~/.npmrc` |
| 包面 | AGENTS.md 那份组件清单**全量进包**，含带数据的组件（`DictSelect`/`UserSelect`/`PermissionButton`…），靠 `installScaffoldUi({http, permission, dict})` 注入宿主能力；平台/演示专属组件不进包 |
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
2. ~~**生成物命名约定跟底座还是跟模板**~~ **已定（2026-11-04，PR 5 开工第一刀）：选 (a) 生成物向底座看齐**，判据是可读性 —— 生成代码是给项目里的人读和改的，底座已经是一套一致的形状（`{Entity}WriteRequest`、`PageQuery` + 逐个 `@RequestParam`、`R.success(...)`、单类 Service、`views/{module}/{Cls}.vue`），再造一套 interface+impl / DTO+Query+types.ts 只会让一个项目里出现两种风格，而教科书分层对单人维护的项目并不更好读。连带定死五件事：① `{Cls}VO` / `{Cls}Query` / `types.ts` 三类文件不再存在，列表行用 `{Cls}Row`；② 扩展点统一 `{Cls}Extension`（`ServiceImplCustom` 那个旧名作废）；③ 生成物包名 `com.scaffold.business.{module}`，与「一个项目只有一个业务模块」一致；④ 接口路径用单数 `/{module}/{resource}`，不做英文自动变复数（底座 `/system/users` 的复数是人工历史命名，自动变格只会生成 `Categorys` 一类错名）；⑤ 前端接口文件**按模型一份** `src/api/{module}/{resource}.ts` —— 本项是 PR 5D 的修订，原写模块级单文件 `src/api/{module}.ts`，多模型时任何一个字段变化都要重写整文件、diff 会盖到别人的接口上；按模型分文件后一次发布只动一个文件，且不引 `@/api` 汇总出口（那是 A 档，平台不往里加导出行）。§4.2 的示例代码按此重写，见 PR 5G。
3. **C 档扩展点的文件约定**：~~后端一个扩展类是否够用~~ **已定（2026-11-04，PR 5E）**：后端就一个 `{Cls}Extension`，七个钩子（`afterRows` 一个 + create/update/delete 各一对 `before*`/`after*`），由 B 档 Service 构造器注入并逐个调用；自定义接口不生成端点、直接在这个类里加方法（§4.5 ②）。不再往下拆（`{Cls}QueryExtension` / `{Cls}Validator` 之类）的理由：钩子签名是平台契约，每多一套就多在下次发布时咬人一次的面积，而人能随便加自己的方法，一个类不是瓶颈。**前端 `overrides/` 仍开放**：PR 5E 只把它的形状认进分档表（§4.3、§5.2，为的是分档判定完整、PR 6 的 `custom:{name}` 桩有合法落点），**覆写怎么被发现、盖到 B 档哪个区块上、整页接管怎么退出**这三个问题还没定，定稿前生成器不产出这类文件。
4. ~~`design_json` 是否对齐 LCE 搭建协议~~ **已定（2026-11-05，PR 6A）：借结构、不借全协议**。理由：本项目走代码生成路线，没有生产运行时要渲染这份 JSON，引进整套 LCE 协议等于为一套不需要的能力付全套复杂度（组件注册表、作用域、事件总线、数据源绑定都要跟着来）。落定的形状见 §3.4 与 `com.scaffold.lowcode.design.PageDesign`：`{version, pageType, modelRef, layout}` 一棵树，区块白名单、控件-容器相容、`field` 必须是模型真实字段、按钮只认内置动作，保存时就与 `lc_field` 逐格对完，报错带节点路径（`layout.children[0].children[1]`）。协议版本号 `version` 留在这里是为了将来要加东西时能认出旧数据。
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
| **PR 4**<br>P1 下 | ✅ 已完成（2026-11-04）：JGit 依赖只落 `scaffold-lowcode`（7.7.1 + `ssh.apache`）；`ls-remote` 连通性检查（只读，不改生命周期）；工作区卷与 Maven/npm 缓存卷；**§6.4 裁剪清单代码化**（`ScaffoldTrimPolicy` 路径白/黑名单 + `scaffold:platform-only` 标记块 + 交付产物的平台残留引用扫描）+ `scaffold-business` 空壳预置；空仓库首次初始化（未出生 HEAD、显式 `refs/heads/{gen_branch}`、推完回读校验）与 `sync_status` 推进、`sync_message` 失败原因（V7）。初始化同步执行，不引入异步任务表 | 对一个真实空仓库跑通「绑定 → clone → 全量提交固定代码 → 推 `gen_branch` → `sync_status=2`」；**裁剪后的项目能编译、能起、能登录，且不含 `views/tool` 与 `lc_*` 表**；`file://` 等非法 URL 被拒；未推送提交时重建工作区被阻止。**实测补记**：交付快照 282 个文件离线 `mvn package` 通过、前端 `npm ci` + `vite build` 通过、用交付侧 V1–V3 建新库能起能登录，菜单里没有 `/tool` 与 `/lowcode`；`lc_tenant` 因登录链路每次都要查租户状态而保留（§6.4 已回写）。**真机推送已完成（2026-11-04）**：对本机 → GitHub 真实空仓库推上 282 个文件、`sync_status=2`、主干未被写；顺带修掉「系统代理劫持 git-over-ssh」与「缺 BouncyCastle 时 ed25519 部署密钥报 no keys to try」两处。**底座标记 tag（2026-11-04 补，PR 8 的前置）**：每交付一份完整 A 档快照就给那个提交打附注 tag `yuanda/platform-base/{base_scaffold_sha}` 并推到项目仓库，只创建、永不移动、永不删除；tag 推不上去只告警不回滚初始化，底座 checkout 读不到提交号时如实写进 `sync_message` 与项目抽屉读数（`JGitGatewayTest` 10 例含真机 tag 语义、`ProjectWorkspaceApiTest` 15 例） | PR 3 |
| **PR 4B**<br>P1.5 | **抽 `@yuanda-lowcode-ai/scaffold-ui`**：`packages/scaffold-ui` + npm workspaces（根 `package.json`、锁文件上移到根）；组件 + 16 composable + 工具 + 7 个 css（合并为 `theme.css`）迁入；`installScaffoldUi({http, permission, dict})` 注入点；解包内对 `@/api`、`@/directives/permission`、宿主 `auth.ts`、`import.meta.env` 的全部引用；平台专属件（`GeneratorPipeline`、外壳 `components/layout/**`、`NeuralNet`、品牌 `data/index.ts`）划在包外；前端镜像构建上下文上移到仓库根；裁剪清单补第三层手段 `deliverySubstitutions()`（精确整行块替换 + `{SCAFFOLD_UI_VERSION}`），交付项目按 npm 依赖拿物料、不再拷源码。**分两步提交**这一条实际没能成立：物料文件被就地改了两轮（先相对化、后注入点），拆不出两个各自可构建的提交，最终合成一次提交，changelog 里分步记录 | 平台自身跑通且行为无回归（`vue-tsc` + 现有测试全绿）✅；包内 `@/` 引用为 0、`import.meta` 为 0 ✅；交付快照 177 文件用 `file:` tarball 装依赖后 `vue-tsc` + `vite build` 通过 ✅；包被一个最小 demo app 以 **npm registry 版本号**依赖消费 ⬜（组织与 token 已就绪，差正式 `npm publish --access public`） | PR 3，**必须在 PR 5 之前** |
| **PR 5**<br>P3 | FreeMarker 模板引擎替换 `GeneratorService` 的 StringBuilder；A/B/C 文件分档与 C 档扩展点骨架；`renderFiles()` 出参提为公共类型，zip sink 与 git sink 并存；**模板产物 import 走 `@yuanda-lowcode-ai/scaffold-ui`** | 生成的代码能编译、`vue-tsc` 通过；产物与 `scaffold-system` 现有同类文件逐行比对达到同等质量；zip 下载行为不回退 | PR 3（可与 PR 4 并行）、**PR 4B** |
| ↳ 5A–5E | ✅ 已完成（2026-11-04，分片进度与踩坑见 `changelog.md`）：5A 渲染骨架 + 实体（`TemplateRenderer` 必须锁 `DOLLAR_INTERPOLATION_SYNTAX`，否则 MyBatis 的 `#{}` 被引擎吃掉）；5B 后端 CRUD 五套 + `SqlIdentifiers` 保留字拦截 + 生成前守卫；5C 建表与菜单种子（真机在一个临时库里各跑两遍验幂等）；5D 前端接口与列表页（产物塞进宿主跑 `vue-tsc` + `vite build` 验）；**5E 分档落成代码** —— `GeneratedPaths` 持路径表并按路径白名单反推档位、`ModelCodeGenerator.renderFiles()` 出 `List<GeneratedFile>`、`{Cls}Extension` 七钩子骨架与 B 档调用点、`GeneratedFileZip` / `GeneratedFileWriter` 两条 sink、`is_tree` / `enable_data_scope` 明确拒绝 | 常驻测试 `GeneratedCodeCompilesTest` 把 7 个 java 产物按交付路径落盘后真起 javac（审计×逻辑删除四种组合）；`ExtensionContractTest` 扫渲染结果核对钩子契约；后端 202 例全绿 | — |
| ↳ 5F | ✅ 已完成（2026-11-04）：老 `GeneratorService` 的 StringBuilder 全删，改由 `ImportedTableInput` 把 `gen_table` / `gen_table_column` 适配成模型形状后吃同一套模板；`javaType()` / `formType()` 并入 `FieldType.ofColumn`（连带补 `FieldType.LONG` 与 `FormType.code()`），`date` 两条链路都是 `LocalDate`；主键 / 自增 / 可空 / 长度 / 精度改为现查 `information_schema`（不再靠列名叫 `id` 猜），表已不存在时拒绝生成；审计四列齐且类型对才交给模板、`deleted` → 逻辑删除、`tenant_id` 只读不进列表；`LIKE` 只给文本列 | zip 下载与预览两个入口都在且**产物是交付形状**：`GeneratorImportApiTest` 4 例（含 zip 内 7 个 java 真起 javac、菜单 SQL 在真库执行后回滚）+ `ImportedTableInputTest` 6 例 + `FieldTypeTest` 反推表 2 例；后端 212 例全绿 | 5E |
| ↳ 5G | ✅ 已完成（2026-11-04）。**发布与合并属 PR 7 / PR 8，还没实现，所以这一趟是「已有各段全用真东西，人肉补上发布那一跳」**：真实交付快照 + 真实模型接口 + 真实工作区提交 + 空目录 clone 真实 GitHub 仓库 + `docker compose --profile mysql up -d --build` 四个服务全 healthy，生成的两份 SQL 手工执行两遍验幂等，浏览器里走完登录 → 菜单 → 页面 → 新增（弹窗无主键格）→ 列表 → 修改 → 唯一查重 → 必填校验 → 导出 → 逻辑删除。§4.1 的模板清单与 §4.2 的示例改成实际产物原文（示例那个模型就是演练用的「销售订单」）。顺带把两件事收进本 PR：交付品牌按 `project_name` 改写（登录页 / 侧栏 / 浏览器标签，见 §6.4 第 6 条）与生成物话术去平台名。新增常驻端到端 `DeliveryGenerationApiTest`（交付快照 + 生成落盘 + 前端实物引用 + 菜单/权限点一致 + 二次发布 B 覆盖 C 跳过，5 例） | **一趟撞出六处「编得过、单测绿、真机才炸」的接缝缺陷**：① `NumberInput` 不带 `precision` → 金额 1234.5 被静默抹成 1235（数据错且不报错）；② 自增主键进表单 + 页面类型里 `id` 声明两遍 → 交付项目 `vue-tsc` 报错，前端镜像构建不出来；③ `@Options(keyProperty = "id")` 在多参数方法上回写不了自增号 → 第一次「新增」500；④ 列表与导出不带审计列 → 页面「创建时间」一列恒空；⑤ CSV 里时间是 ISO 带 `T` 的写法，与页面两种口径；⑥ 页面 `@/api` 引用用驼峰而文件落点是蛇形（单字词夹具看不出）。顺带定死两条边界：只支持数字主键、`lc_field.column_type` 已是完整写法不再二次拼长度。另发现交付链两处 A 档要改（Dockerfile 缺 Maven 依赖缓存挂载：第一次构建 14 分钟、加挂载后 4.7 秒；`CsvUtils` 时间口径），存量项目要等 PR 8 的底座升级下发 | 5F |
| **PR 6**<br>P2 | ✅ 已完成 6A / 6B / 6C（2026-11-05，PR 11 与 PR 12 已合入 dev）：`lc_page` / `design_json` 入库 + 保存时与模型逐格校验 + `ref_fields` 反查拦截（6A）；生成期合流 `GenerationInput.of(model, fields, PageTarget)`（6B，页面是形、模型是料）；设计器第一期三栏工作台 + 流式拖拽 + 容器嵌套 + 属性面板（6C，只支持列表页 + 表单弹窗，定死一个模型只有一个列表页） | 用设计器搭出一个订单列表页，产出的 JSON 能喂给 PR 5 模板生成可运行页面 —— **JSON→生成→可编译这一段已随 PR 7 的 IT 验掉**（`PagePublishApiTest` 首次发布后对落盘产物真起 `javac`）；**「在设计器里点出来」这一半等设计器重构定稿后复跑**（设计稿未出，UI 正在返工） | PR 4、PR 4B、PR 5 |
| **PR 7**<br>P4 上 | ✅ 已完成（2026-11-05）：`PagePublishService` 发布链路 —— 读设计并重校验 → `renderFiles` → 落进工作区（B 覆盖 / C 存在即跳过）→ 提交 `gen_branch` → 推送 → 回读远端 → 打 tag `v{projectCode}-{pageCode}-{version}` → 写 `lc_page_version` + `lc_publish_log` → 推进 `lc_page` 发布四列与 `lc_project.last_publish_time`。no-op 幂等；四条现场守卫（未初始化 / 工作区脏 / 分支缺失 / 远端已前进）都在动手前拒绝；发布前 C 档引用体检默认阻断、`force` 显式放行并把命中留在结果里。顺带三处：`ls-remote` 解引用附注 tag、重铺固定代码放过 B/C 档形状、工作区定位规则收成 `WorkspacePaths` | 重复点同一份设计发布不产生空 commit、不涨版本、不打第二个 tag ✅；推送失败不留下半成品（远端拿不到半个提交、库里不记成功版本，重试把那个提交补推上去）✅；tag 与 `lc_page_version.commit_sha` 对得上 ✅；主干全程未被写 ✅；删掉字段后带着旧引用的扩展代码发布不出去（单测 10 例 + IT 一条 force 放行）✅。用例走真 JGit + 本机裸仓库（`PagePublishApiTest` 9 例），另对着真实 GitHub 仓库跑了一趟真机（连发三次：首发 / no-op / 修缺陷后重发），后端 314 例全绿。**真机那趟抓到 §1.5 合流层两处缺陷并修掉**：菜单漏种行操作的 `:edit` / `:remove`（页面按钮看得见、点了 403），以及画布没填标题时页面大标题兜到英文模型编码 | PR 6 |
| **PR 8**<br>P4 下 | ✅ 已完成（2026-11-06，PR #19 已合入 dev）：合并请求 —— `lc_merge_request` / `lc_merge_request_file`、变更文件列表、行内 unified diff、可合并性 dry-run 预检、确认合并与推送、状态机与陈旧检测、项目负责人作为「谁能合」的判据。同一格原计划承载的**底座升级**、**落后读数**与**合并成功后重打底座标记 tag** 三件按实际进度拆到 PR 10（见下表最后一行），因为它们各自的接口与表都超出了合并请求那一刀 | 从 `gen_branch` 到 `main` 走通一次带 diff 审查的合并；目标分支前进后旧请求自动转"陈旧"；冲突场景只列文件不自动取舍；全程无 force-push | PR 7 |
| **PR 9**<br>P5 | ✅ 已完成（2026-11-06，PR #20 已合入 dev）：版本历史读路径（`GET /pages/{id}/versions[/v]`）+ §5.4 那三级里的**文件级与页面级**回滚（`POST /pages/{id}/rollback`，`revert` 语义产出一笔新提交、照样打 tag，所以它自己也能被回滚过去；只写发布分支，进主干仍走合并请求）。**发布级（revert 某次发布或合并提交）没做** —— 它要的是「一次动作牵动多个页面」的粒度，而现在每一版只记一个页面的产物；C 档照旧不参与回滚。真机回滚与干净机器 `docker compose up` 的交付演练见 `changelog.md` 同日 9F 一条 | 建项目 → 建模 → 设计 → 发布 → 合并 → 在干净机器 clone 该仓库 `docker compose up` 跑通并可用 | PR 8 |
| **PR 10**<br>P4 下 | ✅ 已完成（2026-11-06，分支 `feat/lowcode-pr10-base-upgrade` 向 dev 开 PR）：**底座升级** —— A 档三方合并下发（ours = 项目 `main`、theirs = 按底座 checkout 当前 HEAD 现生成的快照提交、base = 项目仓库的 `yuanda/platform-base/*` tag）、逐文件五档结论（只有可自动合的写进升级分支）、`platform-upgrade/{底座短sha}` 分支走 PR 8 那套审查与确认合并、**落后读数**（`commitCountBetween`，数不出来说「数不出来」而不是 0）、合并成功后打新 tag 并推进 `base_scaffold_sha`；结论表 `lc_base_upgrade`（V13）与新权限点 `lowcode:project:upgrade` | 升级能把平台改的那几行与项目改的那几行分开、真冲突与「平台撤掉」只列文件不代取舍；缺 `yuanda/platform-base/*` tag 的项目被挡住并说明原因；同名升级分支已存在时不重推；合进主干后 base 侧搬到这一版（旧 tag 不动）、读数归零；全程无 force-push、不写主干除了合并请求那一道门 | PR 8 |

| **PR 11**<br>P5 | ✅ 已完成（2026-11-06）：**端到端整链演练** —— 新建空仓库 `pr11-e2e`，一路走完 建项目 → 连通性 → 初始化（179 文件 + 底座标记 tag）→ 建模 7 字段 → 设计（列表页 design_json）→ 发布 v1/v2 → 仓库侧建主干 → 合并请求 → 合进主干（`4a75b462`）→ 干净 clone `main` + `docker compose up` 四服务 healthy → 手工执行生成 SQL → 交付应用真跑新增/唯一约束拒绝/LIKE 分页/详情/修改/导出/删除。**抓到三件事**：① 零变更请求也能被「合并」成功并记成已放行（已修：`refresh` 零差异不建、`mergeIntoTarget` 拒 `ALREADY_UP_TO_DATE`，四道拒绝）；② 交付流程缺「先建主干」这一步（补进 §6.3，含那条 `git push origin origin/platform-generated:refs/heads/main`）；③ deploy key 全局唯一 + ssh 首把通过即停，一台主机服务多个租户仓库必须改用账号级 key 或每租户机器账号（补进 `docs/docker-deployment.md`） | 一条链跑通且交付物里看得见最后一次设计改动（导出表头是 v2 那笔「回访金额（含税）」）；`MergeRequestApiTest` 14 例、后端全量 372 例 0 失败 | PR 9、PR 10 |

**PR 4 与 PR 8 是本期两处新增技术风险点**（JGit 行为差异、主机凭据与 `known_hosts`），各自都要留一次真机验证，不要只在单元测试里过。
