# AGENTS.md

## 通用原则

- 以下规则是默认约定而非铁律。遇到合理例外时可偏离，但需简要说明原因。
- 任何规则依赖的资源（文档、skill、表）缺失时，先告知用户，不擅自臆测或硬上。

## 项目强制开发准则

本项目是供后续项目复用的 Spring Boot + Vue 后台管理脚手架，必须按“统一底座、统一组件、统一风格、统一能力复用”的方式开发。开发入口见 `README.md`，链路定位见 `PROJECT_MAP.md`，视觉规范见 `DESIGN.md`。

### 前端准则

- 所有可复用的前端能力必须组件化，不允许在每个页面重复实现。
- 所有后台页面必须遵守同一套设计语言，包括颜色、字体、字号、间距、圆角、边框、阴影、表格密度、按钮样式、弹窗样式和表单样式。
- 页面开发优先复用 `ListPage`、`useListPage` / `useTreeListPage`、`DataTable`、`SearchForm`、`FormDialog`、`RowActions` / `RowMenuItem`、`PermissionButton`、`DictSelect`、`DeptTree`、`DeptSelect`、`UserSelect`、`PermissionTree`、`TreeSelect`、`DateInput` / `DateRangePicker`、`NumberInput` / `Textarea`、`Tabs` / `TabPane`、`Upload` / `Photo` / `ImageField`、`Descriptions` / `DescriptionsItem`，以及 `usePopover`、`dateValue`、`upload` 三个 composable，不在页面重复拼装公共列表、表单、日期、上传或浮层能力。
- 上述物料与珍珠玻璃样式住在 `packages/scaffold-ui`（包名 `@yuanda-lowcode-ai/scaffold-ui`）：宿主页面按子路径引用（`@yuanda-lowcode-ai/scaffold-ui/components/ui`、`@yuanda-lowcode-ai/scaffold-ui/components/business`、`@yuanda-lowcode-ai/scaffold-ui/composables/list`、`@yuanda-lowcode-ai/scaffold-ui/theme.css`），不再写 `@/components/...`。包内**不得** import 宿主的 `@/api`、`@/composables/auth`、`@/directives`，也不读 `import.meta.env`；需要宿主能力时扩展 `installScaffoldUi({ http, permission, dict })` 的注入契约（宿主适配在 `scaffold-vue/src/api/scaffold-providers.ts`），而不是让包去够宿主。平台外壳（`components/layout/**`、登录页、动态路由、品牌配置 `data/index.ts`）留在 `scaffold-vue`，设计器专属件同样留宿主并被 §6.4 裁掉。
- 所有列表页默认具备查询、重置、分页、导出、状态展示、行操作等通用能力；新增列表页面时不得遗漏这些能力，除非明确说明业务上不需要。
- 所有导出能力必须优先复用统一导出工具或统一表格组件，不允许每个页面临时实现一套导出逻辑。
- 页面只负责业务字段、业务动作和模块差异；通用交互、样式和布局必须沉淀到组件、样式变量或工具函数中。
- 前端 Mock Demo 阶段可以使用本地状态和 CSV 导出；真实业务阶段应按统一接口和统一导出规范接入后端。

### 后端准则

- 所有后端通用能力必须集中封装，不允许在业务 Controller 或 Service 中重复散落实现。
- 统一返回结构、统一异常处理、统一参数校验、统一分页模型、统一权限注解、统一数据权限注解、统一操作日志注解、统一防重复提交、统一字典解析、统一文件上传、统一导出能力。
- 新增业务模块时，应优先复用通用基础能力；只有业务差异逻辑允许写在业务模块内部。
- 接口字段、数据库字段等英文标识返回给前端或写入文档时，应附中文说明其含义和用途。

### 设计语言准则

- 本项目默认采用极简、高级、克制、统一的后台管理设计语言。
- 不允许单个页面自行引入明显不同的视觉风格。
- 前端使用自有 Vue 组件与珍珠玻璃主题，当前不依赖 Element Plus 或 three；动效统一复用 `composables/motion.ts` 中的 GSAP 能力，不在单页另建主题或动效体系。
- 所有新增页面在视觉上必须与已有页面保持一致，包括页面标题、筛选区、表格区、操作按钮、弹窗、抽屉、状态标签和提示反馈。

### 复用优先级

新增功能时按以下顺序决策：

1. 已有通用组件能满足时，直接复用。
2. 已有组件稍有不足时，优先扩展通用组件。
3. 多个页面会用到的新能力，先抽成组件、工具函数、指令或后端公共能力。
4. 只有单个业务场景独有的逻辑，才写在页面或业务模块内部。

## 数据库

- 建表、改表结构、写入或更新数据前，先调用 `/mysql` skill 查表结构再动手；纯读查询不强制。
- 业务表默认包含四字段：`create_by VARCHAR(64)`、`update_by VARCHAR(64)`、`create_time DATETIME`、`update_time DATETIME`。日志表、字典表、关联中间表等可例外，但需说明。
- 返回给前端的接口字段、数据库字段等英文标识，需附中文说明其含义/用途。

## 测试与验收（TDD 与 e2e）

- **先写测试再实现**：新行为先用用例描述出来、跑出失败（失败原因正是待实现的那件事），再动手实现把它转绿。不允许实现完再补一组「描述现状」的断言。
- **每个功能都要有测试**，没有对应测试的功能不算完成、不进入提交。分层按风险给：形状与规则给单元/参数化测试，跨模块的装配给集成测试。
- **功能完成必须跑 e2e**，只有类型检查或单测绿不算交付。本项目的 e2e 形态按链路选：
  - 后端接口：起真实 Spring 上下文打接口（IT 库固定 `DB_PORT=3316 REDIS_PORT=6380`，另需 `JWT_SECRET`，否则整个上下文起不来）。
  - 代码生成物：把产物落盘后真起 `javac` 编译；前端产物进宿主跑 `vue-tsc` + `vite build`。
  - SQL：在临时库里真执行并验证幂等（跑两遍），跑完即删，不动正式 IT 库。
  - 交付链改动：物化交付快照真跑 `npm install` + 构建，单测对「交付缺组件就编译不过」无感知。
- **报告要写清 e2e 实际跑了什么、结果如何**；跑不了（缺环境、无可见浏览器）就如实说明未验证，不得用单测绿冒充交付验证。

## 版本与文档

- 不主动 commit。完成一个逻辑单元后，提出建议的 commit message 供用户确认，确认后再提交；用户明确要求时可直接提交。
- 代码变更后维护项目中的 `changelog.md`（记录变更内容），该文件不存在则创建。
- 模块、接口或调用链改变时同步 `PROJECT_MAP.md` 和相关说明；保留历史日志与人工备注。

## 分支与 PR 流程

固定一条路径：**任务完成 → 基于 `dev` 的新分支提交 → 向 `dev` 开 PR**。用户指定走这条流程时，提交、推送、开 PR 视为已授权，不必逐次确认；commit message 与 PR 内容在完成后一并报告。

- **一个任务一条分支**，命名 `<类型>/<范围>`：`feat/lowcode-pr5d-frontend-templates`、`docs/lowcode-generation-caliber`、`chore/agents-branch-workflow`。开工时从最新的远端 dev 拉：`git fetch --prune <远端>` 后 `git switch -c <新分支> <远端>/dev`。
- **已合并的分支不再追加提交**。PR 合进 dev 就是收口了，往那条分支上继续推会把新工作混进已合并的 PR（本仓库真踩过：PR 合并后同分支又推两个提交，只能另开分支重搬）。
- **提交前确认基点**：`git log --oneline <远端>/dev..HEAD` 的条数必须正好是本任务要提的提交。多出来的就是基点不对 —— 前一任务的 PR 还没合，此时等它合并，或把新分支指向那个 tip 并在 PR 描述里写明前置 PR。
- **下一个任务开始前先合并**：任务之间是串行依赖的（后一个要用前一个的产物），所以下一个任务从 dev 拉分支的前提是前一个 PR 已合并；不要为了赶进度自行叠加。
- 合并方向只有 `feature → dev`，`dev → main` 只做阶段性收口，永不反向、不直接在 dev 上写。合并不强推，优先 `--ff-only`，有分叉就用合并提交。
- **共享分支（`dev` / `main`）绝不 force-push**，也不擅自删远端分支或改仓库配置（子模块指针、remote、`.gitmodules`）—— 这些一律先与用户确认。本地还没推过的分支重指基点是安全的。
- 本仓库的远端是 `lowcode-ai`（平台迭代）；`origin` / `upstream` 是脚手架上游，**不往它们推**。
- changelog 与 `PROJECT_MAP.md` 跟着所属任务一起进 PR，不让文档跨 PR 悬空；代码与文档可以拆成同 PR 的两条提交。

## 部署

- 本脚手架交付本地开发底座，并附带单主机自托管的容器部署：`docker-compose.yml`（前端 nginx + 后端 + Redis，MySQL 在 `mysql` profile 内按需启用）、两份 `Dockerfile` 与 `docs/docker-deployment.md`。域名、TLS、多副本与云端编排不在默认交付范围内，需要时另行设计并先与用户确认。
- 开展部署工作前先看 `docs/docker-deployment.md`，按其中的流程与验证方法执行；涉及服务器 / 部署环境的操作同样先读该指南，找不到或覆盖不到时先告知用户，不凭经验硬上。

## 工具调用与多智能体

- 当前工具列表可能不完整。需要某能力却没有对应工具时，先用 `tool_search` 搜索再判断是否可用。
- 仅当用户明确要求 subagent / 子智能体 / delegation / 多智能体 / 并行工作时，才创建子智能体：用 `tool_search` 搜索 `multi-agent subagent spawn_agent`，再用搜索返回的实际工具名执行。
- 未经明确要求不主动创建子智能体。“深度分析”“彻底检查”“详细研究”等表述本身不等同于授权。
