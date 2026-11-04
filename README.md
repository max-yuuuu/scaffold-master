# 通用后台管理脚手架

用于后续后台管理系统开发的 Spring Boot + Vue 底座。包含认证、菜单与按钮权限、数据权限、组织管理、字典、参数、日志、工作台和代码生成骨架；新增项目在此基础上实现业务模块。

<!-- scaffold:platform-only:start 以下两段是元搭平台自身的定位与进度，交付项目里整段裁掉 -->
> **演进方向**：本项目正从通用脚手架演进为多租户低代码平台 **「元搭」**（Yuán Dā）。权威基线已切至 [`lowcode-ai`](https://github.com/max-yuuuu/lowcode-ai.git)（remote 名 `lowcode-ai`），上游 PR 不再等合并；完整方案见 [元搭 · 低代码平台完整方案](docs/lowcode-platform-proposal.md)。脚手架本身继续承担三重身份 —— 元搭自身（同一个 app：前端 UI + 后端 common/framework/system + 新增 `scaffold-lowcode`）、生成项目的初始框架（裁剪后的固定代码快照）、上游开源同步通道。

## 元搭演进

**层级**：租户 → 项目 → 模型 → 页面

**关键口径**（细节与表结构、API、流程图见方案文档）：

- 平台就长在本仓库：低代码能力新增 `scaffold-lowcode` 模块承载，scaffold 同时是元搭底座与生成项目的模板来源。
- **平台不托管项目运行时**：它是研发期工具。发布 = 生成代码 + 提交 + 推送到租户登记的 Git 仓库**生成分支**；进主干由人在平台内发起「合并请求」，选源/目标分支、看文件 diff、确认才合并，平台不自动合并、永不 force-push。
- 权限码前缀分两类：`platform:` 是平台运营专属（租户管理、租户参数，不下发给租户），`lowcode:` 是租户自己的开发功能（项目、模型、页面），随菜单模板复制到每个租户。平台新增租户可见菜单后，已有租户要在租户管理点「补齐资产」。
- 租户自助注册即开通：`POST /api/auth/register`（匿名 + 验证码）建租户、管理员账号、内置 `tenant_admin` 角色与该租户的菜单树，并直接回登录令牌；管理员账号要求全局唯一。注意该接口默认公开，对外暴露前先决定是否加审核开关。
- 多租户数据隔离：平台元数据与 `sys_*` 同库，`lc_` 前缀 + `tenant_id` 字段隔离；业务库随生成项目自带，平台不连（建表 SQL 推仓库由项目自己迁移）。运行时由 MyBatis-Plus `TenantLineInnerInterceptor` 按**表白名单**自动注入 `tenant_id`（PR 2 已落，新增带 `tenant_id` 的表必须同步登记，单测反向校验）；`super_admin` 看别的租户走会话内「切换租户」重签 JWT，不提供无租户条件的旁路查询。
- Git 凭据走主机级 SSH 密钥，数据库只存 `repo_url` 与分支名；Git 操作全部用 JGit，不经 shell。
- 代码生成用 FreeMarker 模板替换现有 `GeneratorService` 的 StringBuilder 拼接，生成**完整可运行 CRUD**（Controller / Service / Mapper / Vue 页面 / API / 菜单 SQL），而非当前的 7 份骨架文件。生成物按所有权分三档：A 固定（初始化后平台不碰）、B 纯生成（每次覆盖、约定人工不改）、C 扩展点（生成一次骨架后平台不碰，业务逻辑写这里）。原 `CUSTOM-START/END` 文本级合并机制取消。
- 页面设计器采用拖拽 + 配置融合：流式布局画布摆放容器与控件、支持嵌套、右侧属性面板配样式和数据绑定，产出结构化 JSON；**自研，不引 LowCodeEngine**（选型依据见方案 §3.6）。
- 增量发布：文件哈希对比只写变更文件；路由通过 `sys_menu` + `import.meta.glob` 自动注册，无需改前端代码。
- 组件物料：抽 **`@yuanda-lowcode-ai/scaffold-ui`** 包（本仓库 `packages/scaffold-ui` + npm workspaces），平台与每个生成项目共用同一个包 —— 组件源码不再拷进项目，升级从搬 diff 降为 bump 版本号。带数据的组件（`DictSelect`/`UserSelect`/`PermissionButton`）靠 `installScaffoldUi({http, permission, dict})` 注入宿主能力。**已完成**：物料迁入包、包内 21 处对 `@/api`、`@/directives`、宿主 `auth.ts` 的 import 全部改为注入，包内 `@/` 与 `import.meta.env` 归零。**待落地**：发布到 npm registry（生成项目按版本号依赖，不走 GitHub git tag），以及交付快照的裁剪清单同步。不存在 fork：平台前端就是 scaffold-vue 宿主，低代码视图进 `src/views/lowcode/**`。
- 每个生成项目只有一个业务模块 `scaffold-business`（初始化时预置进 pom），生成代码进它的子包 —— 否则每次发布都要改 A 档 pom，"固定代码不随发布变动"就破了。
- 字段类型映射只有一份：`com.scaffold.lowcode.model.FieldType` 定义类型 → Java 类型 / 列类型 / 默认控件，保存时算好落库，前端读 `/api/lowcode/models/meta`，改这张表等于改所有已生成项目未来发布的产物，所以逐条有单测钉死。
- 模型（`lc_model` / `lc_field`）是**代码生成器的输入（期望态）**，不是"模型管理"业务功能；判据：「这段逻辑能不能只靠字段名 + 类型推导出来？」能 → 进模型生成，不能 → 进 C 档扩展点手写。页面与模型的耦合只在 `field_code`。
- 接口生成边界：标准 CRUD 六端点由模型直接推导；自定义接口（状态流转、批量、聚合、跨实体）一律走扩展点不生成；**flow / 流程编排 / 状态机元数据化本期不做**。
- 版本与回滚：每次发布打 tag，支持文件级 / 页面级 / 发布级三级回滚，一律以 `revert` 新提交实现，不倒带分支历史。

**当前阶段**：P0-A 多租户地基（`lc_tenant`、`sys_*` 加 `tenant_id` 与复合唯一键、`TenantContext` / `TenantFilter` / JWT `tenantId` claim / 登录三段校验）与 **P0-B 租户隔离收口**（租户 SQL 拦截器、`super_admin` 切换租户旁路、`lc_tenant_config`、`scaffold-lowcode` 模块与租户管理 / 租户参数页面）均已完成，含**租户自助注册**：登录页可切换到注册表单，提交即开通租户（建租户 + 管理员账号 + `tenant_admin` 角色 + 复制平台业务菜单 / 字典 / 参数）并直接进入。P1（PR 3）也已完成：`lc_project` + 仓库地址协议白名单 + 项目管理页面、`lc_model` / `lc_field` + 模型与字段配置页面（字段类型 → Java / 列类型映射的唯一事实源是 `FieldType` 枚举），手工新增租户时填了管理员就走同一套开通流程。**PR 4 已完成**：JGit 接入（`GitGateway` / `JGitGateway`，凭据走主机 SSH 密钥、不入库）、仓库连通性检查、按代码里的裁剪清单产出交付快照并推送到项目的发布分支（`sync_status 0→1→2`，异常态 3/4/5 带 `sync_message`），以及 `scaffold-business` 空壳模块预置。交付产物已实测「能编译、能起、能登录，且不含 `/tool` 页面与 `lc_project` 等平台元数据表」。下一步 PR 4B —— 抽 `@yuanda-lowcode-ai/scaffold-ui` 组件包（PR 5 的硬前置）。
<!-- scaffold:platform-only:end -->

## 开发入口

- [设计规范](DESIGN.md)：珍珠玻璃主题、组件复用及交互规范。
- [开发约定](AGENTS.md)。
<!-- scaffold:platform-only:start 下列三项只在平台仓库里存在，交付项目不带 -->
- [项目地图](PROJECT_MAP.md)：按功能定位页面、接口、服务、SQL 和权限。
- [更新日志](changelog.md)：记录每次交付物的变更。
- [元搭 · 低代码平台完整方案](docs/lowcode-platform-proposal.md)：多租户层级、可视化设计器、代码生成、发布与仓库合并请求。
<!-- scaffold:platform-only:end -->
- [后端架构](docs/backend-architecture.md)、[前后端接口约定](docs/frontend-backend-integration.md)、[前端架构](docs/admin-redesign-architecture.md)。
- [Docker 部署指南](docs/docker-deployment.md)：Compose 一键编排前端 / 后端 / Redis / 可选 MySQL。

## 目录与技术栈

| 目录 | 用途 | 技术栈 |
| --- | --- | --- |
| `scaffold-system` | 后端 Maven 多模块工程 | Java 17+、Spring Boot 3.5、MyBatis、MySQL、Redis、JWT |
| `scaffold-vue` | 管理后台（宿主 app） | Vue 3、TypeScript、Vite 6、Vue Router 4、GSAP 3；外壳、路由、登录与业务页面 |
<!-- scaffold:platform-only:start 物料包源码只在平台仓库里，交付项目按 npm 依赖装 @yuanda-lowcode-ai/scaffold-ui -->
| `packages/scaffold-ui` | 前端物料包 `@yuanda-lowcode-ai/scaffold-ui` | 67 个组件 + 16 个 composable + 工具 + 珍珠玻璃主题（`theme.css`），npm workspaces 成员，宿主与生成项目共用 |
<!-- scaffold:platform-only:end -->
| `scaffold-system/sql` | 首次初始化脚本 | `V1__init_schema.sql` 建表，`V2__seed_data.sql` 初始化数据，`V3__system_resource_menu.sql` 补资源监测菜单，`V4__platform_tenant.sql` 补租户参数表与平台管理菜单（含存量库的 `sys_menu` 自增迁移）；`sql/verify/` 是双租户验收数据，**不自动导入** |
| `docs` | 当前架构说明 | 后端架构、前后端接口约定和前端组件架构 |

当前包含工作台和 11 个管理页面；建筑、工作流模块已移除。

## 本地启动

准备 JDK 17 或更新版本、Maven、Node.js 22+、MySQL 5.7+ 和 Redis；前后端依赖均使用项目现有配置。

后端读取进程环境变量，不会自动加载 `.env` 文件；可在 IDE 运行配置或启动终端中设置：

| 变量 | 中文说明 | 默认值 |
| --- | --- | --- |
| `JWT_SECRET` | JWT 签名密钥，至少 32 字节且不能全为空白 | 必填，无默认值 |
| `DB_HOST` / `DB_PORT` | MySQL 地址 / 端口 | `localhost` / `3306` |
| `DB_NAME` | 数据库名称 | `scaffold_system` |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号 / 密码 | 本地开发默认账号；按实际环境配置 |
| `DB_TIMEZONE` | 数据库连接与会话时区 | `+08:00` |
| `REDIS_HOST` / `REDIS_PORT` | Redis 地址 / 端口 | `localhost` / `6379` |
| `REDIS_PASSWORD` | Redis 密码 | 空；按实际环境配置 |
| `CAPTCHA_ENABLED` | 登录图形验证码开关；关闭时登录不再校验验证码 | `true` |
| `JSON_TIMEZONE` | JSON 序列化时区，须与 `DB_TIMEZONE` 的会话时区对齐，否则日期整体偏移 | `GMT+8` |
| `VITE_API_PROXY_TARGET` | 前端开发代理的后端地址 | `http://localhost:8080` |

可在启动后端的终端中生成本机密钥；生成结果保留在当前进程环境，不写入仓库：

```bash
export JWT_SECRET="$(openssl rand -hex 32)"
```

使用 IDE 时将密钥配置到运行环境。未配置或不足 32 字节时后端启动失败；保留同一密钥可让重启前的有效 token 继续验签，更换密钥会使旧 token 失效。

首次创建空的 `scaffold_system` 库后，按顺序导入 `sql/` 顶层的四份 `V*__*.sql`（`sql/verify/` 子目录不在其中）；mysql 客户端须使用 `--default-character-set=utf8mb4`，并通过 `--init-command="SET time_zone='+08:00'"` 设置会话时区。也可在 IDE 运行 `scaffold-admin` 中的 `com.scaffold.admin.util.DbInit`，该工具使用同一组 `DB_*` 变量并按版本顺序初始化（自动发现顶层 `V*__*.sql`，不递归子目录）；参数 `fix` 会删除整个目标库，仅在核实库名、备份且明确同意重建时使用。

```bash
# 在 scaffold-system 目录构建与启动后端
mvn -pl scaffold-admin -am package -DskipTests
java -jar scaffold-admin/target/scaffold-admin-0.1.0-SNAPSHOT.jar
```

```bash
# 前端命令一律在仓库根执行（npm workspaces）；首次使用先 npm install
npm run dev
```

浏览器访问 `http://localhost:5173`，后端本地端口为 `8080`；前端统一通过 `/api` 代理访问后端。

需要整套容器化部署（前端 nginx + 后端 + Redis + 可选 MySQL）时见 [Docker 部署指南](docs/docker-deployment.md)，本机开发仍按上面的命令启动。

本机反复启停后端时的几条硬事实写在同一份指南的「本地直接跑进程（不起容器）的几条事实」一节：重打 jar 要 `clean package`（增量构建可能不重打 fat jar）、`JWT_SECRET` 一变所有会话就 401、启动变量请固化成不入库的 `.env.local` 而不是从旧进程 `ps eww` 里抓、测试用 `3316`/`6380` 别撞业务库、改包内组件源码宿主即时生效不用重装。

种子库管理员为 `admin`，开发密码为 `admin123`；仅用于本地开发，接入实际环境前请修改密码并配置自己的数据库与 Redis 凭据。种子组织和用户为示例，电话留空，邮箱使用 `example.com`。验证码为服务端渲染的 PNG 图片，答案只存 Redis 不回传明文，按图上字符输入；`CAPTCHA_ENABLED=false` 可整关（后端集成测试即以此关闭，无需读图）。超级管理员可见全部启用且可见的已注册菜单，并拥有全部操作权限；其余种子角色默认未授予菜单，需在角色管理中分配。无可用菜单的账号登录后显示“暂未分配可用菜单”。

## 新业务模块

1. 先设计业务表与中文字段含义；默认包含 `create_by`、`update_by`、`create_time`、`update_time` 四个审计字段。
2. 复用 `scaffold-common` 的返回、分页与异常，以及 `scaffold-framework` 的权限、数据权限、操作日志能力。
3. 前端列表复用 `ListPage`、`useListPage` / `useTreeListPage`；表单复用 `FormDialog`、`Field` 和现有控件，操作复用 `PermissionButton`。
4. 在 `src/api` 接入统一 HTTP 客户端，页面按 `views/<sys_menu.component>.vue` 目录约定放置即可被 `src/composables/auth.ts` 自动解析（文件名与 `component` 值不同名时才需要在 `COMPONENT_ALIAS` 登记别名），再添加菜单和按钮权限。
5. 按角色配置菜单和数据范围，验证低权限账号的真实接口拒绝行为、查询、分页、导出及表单校验。
6. 同步项目地图、相关接口说明和更新日志。

代码生成初始配置为空，按需导入当前数据库的真实表，支持字段信息查看、配置说明保存、7 个文件预览和 ZIP 下载。输出为开发骨架：Controller、Service、Mapper 的业务 CRUD 仍需实现，前端页面需按统一组件规范补齐；下载不会自动写入本工程。

## 验证

不连接数据库和 Redis 的测试与编译：

```bash
# 在 scaffold-system 目录
mvn -pl scaffold-framework -am test
mvn -pl scaffold-admin -am package -DskipTests
```

完整后端集成测试需要配置上述环境变量并准备独立校验库：

```bash
# 在 scaffold-system 目录；建议用独立校验库，测试会写入夹具和日志
mvn -pl scaffold-admin -am test
```

```bash
# 在仓库根：test:unit 会分别进 packages/scaffold-ui 与 scaffold-vue 各跑一遍
npm run test:unit
npm run build
# 已安装 Python Playwright 与 Chromium 时，可验证真实浏览器会话与权限
python3 scaffold-vue/scripts/session-regression.py
```

浏览器回归脚本只创建并删除自己生成的测试角色和用户，不清空表（用户与角色按逻辑删除，行会留在表里、`deleted` 置 1）；管理员密码可通过 `SCAFFOLD_TEST_PASSWORD` 覆盖。脚本无法读验证码图片，因此**要求后端以 `CAPTCHA_ENABLED=false` 启动**（与 `scaffold-admin` 的 surefire 同一口径），否则登录第一步就会失败。测试与构建成功不能代替业务页面验收。

## 许可证

本项目代码采用 [MIT License](LICENSE)，允许商用、修改和分发，使用时保留版权及许可证声明。

第三方依赖遵循各自的许可证。GSAP 使用 [Standard "No Charge" License](https://gsap.com/community/standard-license/)，不属于本项目的 MIT 授权范围；使用与再分发时须遵守其条款并保留原有声明。
