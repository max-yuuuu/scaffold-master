# 通用后台管理脚手架项目地图

> 2026-11-03 更新。用于后续业务开发和排障；“已确认”表示代码链路已核对，运行验证记录见 `changelog.md`。数据库映射已与本地 MySQL 5.7 的真实结构及三份初始化脚本核对；本次同步多租户地基 PR 1（`lc_tenant` 表、`sys_*` 加 `tenant_id`、`TenantContext` + `TenantFilter` 打通登录链路）。

## 一、项目总览

| 端 | 路径 | 技术栈 | 本地入口 | 来源 |
| --- | --- | --- | --- | --- |
| 后端 | `scaffold-system` | Java 17+、Spring Boot 3.5、MyBatis、MySQL、Redis、JWT | `8080`，接口前缀 `/api` | 已确认 |
| 管理后台 | `scaffold-vue` | Vue 3、TypeScript、Vite、Vue Router、GSAP，自有组件 | `5173`，开发代理 `/api` | 已确认 |
| 前端物料包 | `packages/scaffold-ui`（`@yuanda-lowcode-ai/scaffold-ui`） | 73 个组件 + 16 个 composable + 工具 + 珍珠玻璃主题，npm workspaces 成员 | 宿主按 `@yuanda-lowcode-ai/scaffold-ui/...` 子路径引用，主题走 `@yuanda-lowcode-ai/scaffold-ui/theme.css` | 已确认 |

- Maven 模块：`scaffold-common`、`scaffold-framework`、`scaffold-system`、`scaffold-generator`、`scaffold-lowcode`（平台专属，交付快照里整块裁掉）、`scaffold-business`（生成物唯一落点，随交付进项目）、`scaffold-admin`。
- npm workspaces：仓库根 `package.json` 声明 `packages/*` 与 `scaffold-vue`，锁文件在根（`package-lock.json`），`scaffold-vue/node_modules/@yuanda-lowcode-ai/scaffold-ui` 是指向 `packages/scaffold-ui` 的软链。开发命令一律从根跑：`npm run dev` / `build` / `test:unit`（`build` 与 `test:unit` 会分别进包和进宿主各跑一遍）。
- 包名前缀：`com.scaffold`；启动类：`scaffold-system/scaffold-admin/src/main/java/com/scaffold/admin/ScaffoldAdminApplication.java`。
- 配置：`scaffold-system/scaffold-admin/src/main/resources/application.yml`、`application-dev.yml`；前端代理：`scaffold-vue/vite.config.ts`。
- 数据库初始化：`scaffold-system/sql/V1__init_schema.sql`（15 张表，含 `lc_tenant`，`sys_*` 表带 `tenant_id`）、`V2__seed_data.sql`（首行平台租户 id=1 种子）、`V3__system_resource_menu.sql`；开发工具：`scaffold-admin/src/main/java/com/scaffold/admin/util/DbInit.java`。
- 入口规范：`README.md`、`AGENTS.md`、`DESIGN.md`；部署见 `docs/docker-deployment.md`；低代码平台方案见 `docs/lowcode-platform-proposal.md`；建筑与工作流已移除。

## 二、业务术语词典

| 业务说法 | 代码 / 表 | 字段及中文含义 | 来源 |
| --- | --- | --- | --- |
| 租户 | `lc_tenant` | `tenant_code` 租户编码；`tenant_name` 名称；`max_projects` 项目数配额；`expire_time` 到期时间；`status` 启用状态 | 已确认 |
| 后台账号 | `AuthUserRow`、`sys_user` | `tenant_id` 归属租户 ID；`username` 登录账号；`nickname` 姓名；`status` 启用状态；`deleted` 删除标记 | 已确认 |
| 角色 | `sys_role`、`sys_user_role` | `role_code` 角色编码；`builtin` 内置标记；`data_scope` 数据范围 | 已确认 |
| 菜单 / 按钮 | `MenuRow`、`sys_menu`、`sys_role_menu` | `menu_type` 为 `dir` 目录 / `menu` 页面 / `btn` 操作；`permission` 权限码 | 已确认 |
| 组织与岗位 | `sys_dept`、`sys_post`、`sys_user_post` | `parent_id` 上级部门；`ancestors` 上级链；`post_code` 岗位编码 | 已确认 |
| 字典 | `sys_dict_type`、`sys_dict_data` | `dict_type` 字典类型；`dict_label` 显示名称；`dict_value` 存储值 | 已确认 |
| 参数 | `sys_config` | `config_key` 参数键；`config_value` 参数值；`builtin` 内置标记 | 已确认 |
| 登录 / 操作审计 | `sys_login_log`、`sys_oper_log` | `login_time` 登录事件时间；`oper_time` 操作时间；`request_params` 脱敏参数 | 已确认 |
| 生成配置 | `gen_table`、`gen_table_column` | `table_name` 表名；`java_field` 属性名；`synced` 配置确认标记 | 已确认 |
| 审计字段 | 业务表公共列 | `create_by` 创建人；`update_by` 更新人；`create_time` 创建时间；`update_time` 更新时间 | 已确认 |

## 三、模块文件地图

下列后端业务路径均相对于 `scaffold-system`，宿主前端路径均相对于 `scaffold-vue/src`；物料包内文件写作 `packages/scaffold-ui/src/...`（表格里同一目录后续的 `Xxx.vue` 省略前缀，宿主引用时写成 `@yuanda-lowcode-ai/scaffold-ui/components/ui/Xxx.vue`）。

| 后端模块 | Controller | Service | Mapper / 表 | 来源 |
| --- | --- | --- | --- | --- |
| 认证与路由 | `scaffold-system/src/main/java/com/scaffold/system/controller/AuthController.java` | 同包域 `service/AuthService.java`、`SystemPermissionResolver.java` | `mapper/AuthMapper.java`；用户、角色、菜单及关联表、登录日志、`lc_tenant` 状态校验。登录人信息由 `framework/security/SecurityUtils` 统一读取（令牌 `userId` + `tenantId` 声明经 `LoginUser` 挂在 `Authentication#getDetails()`）；`framework/tenant/TenantFilter` 在 `JwtAuthenticationFilter` 之后从 `LoginUser` 写 `TenantContext`，请求结束清理。`switchTenant` 为重签 JWT 的会话切换，身份与权限查询标 `@InterceptorIgnore(tenantLine = "true")` 按全局主键定位 | 已确认 |
| 用户、角色、组织、菜单、字典、参数 | `scaffold-system/src/main/java/com/scaffold/system/controller/SystemManagementController.java` | `service/SystemManagementService.java` | `mapper/SystemManagementMapper.java`；各 `sys_*` 管理表 | 已确认 |
| 工作台 | `scaffold-system/src/main/java/com/scaffold/system/controller/DashboardController.java` | `service/DashboardService.java` | `mapper/DashboardMapper.java`；用户、角色、两类日志 | 已确认 |
| 资源监测（只读） | `scaffold-system/src/main/java/com/scaffold/system/controller/ResourceMonitorController.java` | `service/ResourceMonitorService.java` | 无自有表：JMX 取主机与 JVM、Redis 连接取 `INFO` / `PING`、Hikari `HikariPoolMXBean` 与 MySQL 全局状态、`information_schema.tables` | 已确认 |
| 日志查询、导出与清理 | `scaffold-system/src/main/java/com/scaffold/system/controller/MonitorController.java` | `service/SystemManagementService.java` | `mapper/SystemManagementMapper.java`；两类日志 | 已确认 |
| 日志统计与当日泳道 | 同上 | `service/MonitorStatsService.java` | `mapper/MonitorStatsMapper.java`；两类日志（只读） | 已确认 |
| 代码生成（老链路，导入库里已有的表） | `scaffold-generator/src/main/java/com/scaffold/generator/controller/GeneratorController.java` | `service/GeneratorService.java`（只做「读配置 + 装配」，不再拼字符串）、`service/ImportedTableInput.java`（`gen_table` / `gen_table_column` → 模型形状） | `mapper/GeneratorMapper.java`；`information_schema`（主键、自增、可空、长度、精度**现查**）、两张生成配置表；产物来自 `lowcode/generate/ModelCodeGenerator`，zip 走 `GeneratedFileZip` | 已确认 |
| 低代码生成模板（PR 5，读模型 → 生成物） | `lowcode/generate/ModelCodeGenerator.renderFiles(项目行, 模型行, 字段行)` → `List<GeneratedFile>`；两条出口 `GeneratedFileZip.pack`（zip 下载）与 `GeneratedFileWriter.write`（落进工作区，B 覆盖 / C 存在即跳过），后者已由 PR 7 的发布链路接走（见「低代码平台 · 页面发布」一条） | `generate/TemplateRenderer.java`（FreeMarker，classpath `templates/lowcode`，锁定 dollar 插值语法）、`generate/GenerationInput.java`（唯一的派生层：命名、列类型全形式、查询条件与参数摊平、每个文件的 import 清单）、`generate/GeneratedPaths.java`（§4.3 落点表 + 按路径白名单反推 A/B/C 档位，认不出就拒写）、`generate/GeneratedFile.java`（产物 + 档位，只能由 `GeneratedFile.of(path, content)` 构造） | 模板在 `scaffold-lowcode/src/main/resources/templates/lowcode/**`，一个模型 11 份产物：`backend/` 七份（实体 / 入参 / 行 / Mapper / Service / **Extension（唯一的 C 档）** / Controller，Mapper 与 Service 含按主键取整行的详情）、`frontend/`（接口封装 + 列表页）、`sql/`（建表 + 菜单种子）、`_shared/`（auditFields / pojo / queryWhere / methodParams 四个共用宏）；输入是 `lc_project` / `lc_model` / `lc_field` 的行；PR 6B 起 `renderFiles` 可再多接一份 `PageTarget`（design_json + 页面行），没有设计时页面形状回到模型的 `is_list` / `is_query` / `is_insert` 三个勾选；`is_tree` / `enable_data_scope` 在进模板前被拒 | 已确认 |
| 健康检查 | `scaffold-admin/src/main/java/com/scaffold/admin/controller/HealthController.java` | 无独立业务服务 | 无数据库写入 | 已确认 |
| 低代码平台 · 模型与字段 | `scaffold-lowcode/src/main/java/com/scaffold/lowcode/model/controller/LcModelController.java` | `service/LcModelService.java`、`service/LcFieldService.java`；类型映射在 `model/FieldType.java`、命名派生在 `model/Naming.java` | `mapper/LcModelMapper.java`、`mapper/LcFieldMapper.java`；`lc_model` / `lc_field`（带 tenant_id，走拦截器） | 已确认 |
| 低代码平台 · 页面设计（PR 6） | `scaffold-lowcode/src/main/java/com/scaffold/lowcode/page/controller/LcPageController.java` | `page/service/LcPageService.java`（写库前把设计与模型逐格对完、解析 `ref_fields`、定死一个模型一个列表页）；形状与模型的事实源在 `lowcode/design/`：`PageDesign.parse(json, DesignContext)`、`ComponentCatalog`（第一期组件 / 动作 / 属性白名单）、`DesignNode`、`PageTarget`（生成落点：页面名 / 路由 / 图标 / 排序 + 设计） | `mapper/LcPageMapper.java`；`lc_page`（带 tenant_id，走拦截器；发布四列由 V9 补，版本与日志见下一条）。删字段与下线模型按 `ref_fields` 反查它（`LcFieldService` / `LcModelService` 经 `page/PageReferences`） | 已确认 |
| 低代码平台 · 底座升级（PR 10，§5.2） | `LcProjectController`：`GET /api/lowcode/projects/{id}/base-status`（权限沿用 `lowcode:project:list`，纯读数：`baseScaffoldSha` / `scaffoldSha` / `upToDate` / `behindCommits` / `latest` 那条升级记录）、`POST /projects/{id}/base-upgrade`（权限 `lowcode:project:upgrade`，它往租户自己的仓库推分支，所以与「编辑项目」分开；**不碰主干**） | `workspace/BaseUpgradeService.java`（三侧的取法各不同：base = 项目仓库里交付时打的 `yuanda/platform-base/{sha}` tag、ours = 项目主干、theirs = 按当前底座 checkout 现生成的快照提交，**都不需要平台保留底座历史**；动手前三道拒绝 —— 远端没主干、同名升级分支已在、base tag 取不到；一格可自动合的都没有时不建分支也不推，`status=3`）；`workspace/ScaffoldSnapshotPlanner.java`（A 档清单的唯一来源，与初始化同一把尺 —— 两边算出不同的 A 档，三方合并就变成平台自己跟自己冲突）；`workspace/MergeOutcome.java`（五档结论，只有 `CLEAN` 会被写出去）、`MergedFile.java`、`BaseUpgrade.java`（`platform-upgrade/{底座短sha}` 分支名的唯一出处）；`GitGateway` 五条新原语：`threeWayMerge(dir, base, ours, theirs, paths)`（逐文件，`MergeChunk` 回取内容 —— `getSequences()` 给的是三个输入不是结果；`RawText.writeLine` 不带行终止符。**路径并集只收 A 档**：`GeneratedPaths.isPagePublishOwned` 命中的 B/C 档形状与项目标记文件一律不进名单，否则 base/ours 里那些项目自己的业务代码会被报成「平台不再交付，删不删由人定」，等于诱导人删自己的东西 —— 10G 真机抓的）、`snapshotCommit(dir, files, message)`（`DirCache.newInCore()` + `CommitBuilder`，不动任何工作区）、`baseUpgrade(...)`（临时 clone 里编排，平台工作区一条引用都不动）、`commitCountBetween(dir, from, to)`（**数不出来返回 null 不是 0**）、`stampBaseTag(dir, repoUrl, targetBranch, tag, msg)`（**重新 clone 目标分支**才打：那笔合并提交只活在临时 clone 与远端；同名不移动）；`merge/service/MergeRequestService.stampBaseAfterMerge`（合并成功后搬 base：tag → `base_scaffold_sha` → 升级记录收成已合入；tag 失败只告警不回滚合并，查不到升级记录时不猜底座号）；前端 `scaffold-vue/src/views/lowcode/projects.vue` 的仓库抽屉：**四组 `Descriptions` 各有 `title`，动作放在它所依据的那组的 `extra` 槽里**（仓库与发布分支→初始化固定代码、工作区→清空工作区、连通性检查→检查连通性、底座与升级→发起底座升级），顶部只留状态与那句解释；每块自己管自己的加载态（`pending()` 在未读回时显示「读取中…」而不是 '—'）。升级结果屏按三档计数说话，并把冲突清单与「平台撤掉」清单逐条列出；`upToDate` 时升级按钮禁用并说明为什么（不摆注定被后端拒掉的按钮） | `V13__lowcode_base_upgrade.sql`：一次发起一条（`base_scaffold_sha`、`target_scaffold_sha`、`upgrade_branch`、逐文件结论 JSON、`status` 0 待合并 / 1 已合入 / 2 陈旧 / 3 无内容、`message` 一句人话）+ 按钮 5015 权限码 `lowcode:project:upgrade`；`lc_base_upgrade` 已登记 `ScaffoldTenantLineHandler.TENANT_TABLES`，V13 已补进 `ScaffoldTrimPolicy` 排除表，`LcBaseUpgradeMapper` 放在已被 `@MapperScan` 覆盖的 `lowcode.project.mapper`；`lc_project.base_scaffold_sha` 从此不再只在初始化时写一次 —— 合进主干后由 10F 推进，否则 base 永远停在上一轮、下一趟把已下发过的内容再下发一遍 | IT 已过（真 JGit + 本机裸仓库：`JGitGatewayTest` 43 例、`MergeRequestApiTest` 12 例含「合并后 base 搬不搬」、`ProjectWorkspaceApiTest` 16 例）。**真机 10G 已过**（项目 23 / `test-lowcode-push`）：落后 109 个提交 → 升级分支 `platform-upgrade/099fd1f` → 合并请求 #177 由负责人合进主干（`36fff869` → 合并提交 `5808aea2`）→ 新 tag 剥开后正指那笔、上一轮 tag 原样停在 `2dbf5989`、发布分支未动 → `base_scaffold_sha` 推进、读数 `upToDate=true` / `behindCommits=0`。真机抓出并已修的三条见 changelog 同日「PR 10 收尾」。剩：人工「补打底座标记 tag」的界面入口（存量项目缺 tag 时只能手工做）、`.scaffold-project.json` 里的底座号不随升级更新 |
| 低代码平台 · 页面回滚（PR 9，§5.4） | `LcPageController`：`GET /api/lowcode/pages/{id}/versions`（PageQuery 分页，倒序，权限沿用 `lowcode:page:list` —— 读库不碰仓库，不该再多一个按钮）、`GET /pages/{id}/versions/{version}`（单版产物清单，每行带 `tier` 与 `rollbackable`）、`POST /pages/{id}/rollback`（权限 `lowcode:page:rollback`，body 必带 `version`，`files` 为空即整页回滚） | `publish/PageRollbackService.java`（三道拒绝都在写之前：回滚到当前版等于什么都不做、那一版查不到、快照哈希与仓库内容对不上；取数走仓库而不是按今天的模板重跑 —— 重跑拿到的是「那一版今天应该长什么样」；C 档按 `GeneratedPaths.tier` 现场反推后剔除，不是靠调用方自觉）；`publish/PublishSiteResolver.java`（发布与回滚共用的现场守卫：项目未就绪 / 工作区脏 / 本地与远端不一致，两条链路都是「整目录暂存 + 提交 + 推送」，守卫抄两份迟早漂）；`publish/PageVersionService.java`（版本历史的唯一读路径；tag 与动作类型去 `lc_publish_log` 取而不抄进版本表）；`publish/ContentHash.java`；`workspace/GitGateway.fileContents(dir, commitRev, paths)`（只读走对象库，不 checkout、不动索引与 HEAD；缺失的路径不进结果而不是给空串 —— 空内容是合法内容，写回去就是把文件清空；二进制与非 UTF-8 当场拒） | `V12__lowcode_page_rollback.sql` 只加一条权限点（按钮 5035，父级 503），已补进 `ScaffoldTrimPolicy` 排除表；回滚产出的那一版写 `lc_page_version`（`publish_type=ROLLBACK`、版本号 `current+1`、照样打 tag，于是它本身也能被回滚过去）+ `lc_publish_log` 一条；`files_snapshot` 以当前版为底、只把还原过的路径换成目标版的哈希。本轮不新增表，`TENANT_TABLES` 白名单不动 | 真机已过（9F）：回滚产出 v3 ROLLBACK、附注 tag 剥开后正指向那笔提交、`main` 未动、v3 的父提交就是回滚前的 gen tip、干净 clone 交付仓库后 `docker compose up` 四服务 healthy 且 `SalesOrder.vue` 读到的就是 v1 的内容，交付侧新增/查询/详情/唯一约束拒绝/删除/导出全跑通。剩：合并那一步仍只有 IT 覆盖（9F 用户选不写 main）、抽屉与回滚按钮未在浏览器里点过 |
| 低代码平台 · 合并请求（PR 8，§5.4） | `LcMergeRequestController`：`GET /api/lowcode/merge-requests`（PageQuery 分页 + 可选 projectId + `onlyMine` 按负责人过滤，权限 `lowcode:merge-request:list`）、`GET /merge-requests/{id}`（详情带清单，打开时重读一次远端判陈旧）、`GET /merge-requests/{id}/file-diff?path=`（单文件行内差异，权限同 list）、`POST /merge-requests/refresh`（权限 `...:sync`：它真连远端仓库）、`POST /merge-requests/{id}/merge`（权限 `...:merge` + 服务里再判「是不是项目负责人或平台超管」） | `merge/service/MergeRequestService.java`（四道现场守卫：项目未就绪、工作区脏就不预检、远端缺发布分支、缺主干、两条历史无共同祖先 —— 各说一句人接下来能做的事，其中「平台不替你建主干」是刻意的，那等于替你决定仓库的起点）、**零差异不进队列**（11A 整链演练：`diffFiles` 为空时 `refresh` 直接拒并说「没有要合进主干的内容」，既不建新条也不把现有待审条刷成 0 文件；`mergeIntoTarget` 把 JGit 的 `ALREADY_UP_TO_DATE` 从「返回目标 sha」改成拒绝 —— 放过就会在台账记一次根本没发生的放行，升级分支走同一条路还会凭空搬走 `base_scaffold_sha`；两道闸同一句话）、`merge/service/MergeRequestLedger.java`（请求行与文件清单是一次结论的两半，同事务；同一分支对至多一条活着的请求，重算就地覆盖并**整批替换**清单 —— 逐行 upsert 会留下「上次有这次没有」的孤儿行，那在页面上读起来像「这次要删掉这个文件」）、`merge/domain/MergeRequestState.java`（status 与 check_status 两套文案的唯一来源：接口直接带 `statusText` / `checkStatusText`，让前端拿数字拼词就是两处各说各话的开始）；`workspace/GitGateway.java` 四条新原语，全部只读：`fetch(dir)`（带回 `refs/remotes/origin/*` 且**不动本地分支与工作区**——工作区是按发布分支 clone 的，不 fetch 就根本读不到主干，读到的清单会是过期的假东西）、`mergeBase(dir, one, other)`（`RevFilter.MERGE_BASE`；清单必须按 `merge-base..源` 算，按 `目标..源` 会把主干上别人的新提交反向显示成「发布分支要删它」；对象不在本地时返回 null 而不是抛 JGit 的 `Missing unknown`）、`diffFiles(dir, base, head)`（`DiffFormatter` + 改名识别，档位对齐 `change_type`；行级增删数按单文件 diff 现算，**diff 文本不入库也不返回**）、`checkMerge(dir, ours, theirs)`（`RECURSIVE` 的 in-core merger + `DirCache.newInCore()` 两份保险，真实 `.git/index` 不被一次预检写脏；冲突只报文件清单不替人取舍）；形状件 `Change.java`、`ChangedFile.java`、`MergeCheck.java` ；前端 `scaffold-vue/src/views/lowcode/merge-requests.vue`（复用 `ListPage` + `useListPage` + `DataTable` + `Drawer` + `PermissionButton`；筛选只有「归属项目」一条 —— 标题是平台按分支与文件数生成的，对它做关键字过滤没有读者，与其摆一个后端不认的输入框不如不放） | `V10__lowcode_merge_request.sql`：`lc_merge_request`（含 `source_sha` / `target_sha` / `merge_base_sha`；`status` 生命周期与 `check_status` 预检结论**分两列**，因为「目标分支又前进了」不改变内容是否冲突）与 `lc_merge_request_file`（一文件一行，`UNIQUE(request_id,path)`）；文件数/冲突数与 diff 文本都不存（同 V9 挡掉三个计数的理由）；权限码 `lowcode:merge-request:list`（页面 504）与 `lowcode:merge-request:sync`（按钮 5041，它要读远端仓库）；两张表已登记 `ScaffoldTenantLineHandler.TENANT_TABLES`，V10 与 `MergeRequestApiTest.java` 已补进 `ScaffoldTrimPolicy` 的排除表，`com.scaffold.lowcode.merge.mapper` 已补进 `ScaffoldAdminApplication` 的 `@MapperScan`（那里不能写包根，漏了就是上下文起不来 `No qualifying bean`） | 剩：「非负责人且非超管被拒」这条负向分支的用例（要一个本租户内持有 merge 权限又不是负责人的账号，现成夹具里没有）。「合并后重打底座标记 tag」与「底座升级 / 落后读数」两条已由 PR 10 接走（见上面「低代码平台 · 底座升级」一条），其中来源分支放开的是 `platform-upgrade/*` 这一类 |
| 低代码平台 · 页面发布（PR 7，§5.1） | `LcPageController` 的 `POST /api/lowcode/pages/{id}/publish`（权限码 `lowcode:page:publish`，与「编辑页面」分开） | `scaffold-lowcode/src/main/java/com/scaffold/lowcode/publish/PagePublishService.java`（读设计 → 重校验 → `renderFiles(model, fields, PageTarget)` → 落盘 → 提交 → 推送 → 回读远端 → 打 tag → 记账；四道现场守卫与 no-op 都在这里）；`PublishLedger.java`（版本行 + 日志 + 页面 + 项目四笔一个事务）；`PublishTag.java`（`v{projectCode}-{pageCode}-{version}`，只创建不移动）；`ExtensionReferenceCheck.java`（发布前 C 档引用体检，判据是本次生成的 `domain/` 成员，默认阻断、`force` 放行）；`workspace/WorkspacePaths.java`（`{tenantId}/{projectCode}` 定位规则，初始化与发布共用一份） | `publish/mapper/LcPageVersionMapper.java`、`publish/mapper/LcPublishLogMapper.java`；`V9__lowcode_publish.sql` 的 `lc_page_version`（一版一条：设计原文 + 路径→哈希 + 提交号）与 `lc_publish_log`（一次动作一条，成功 / 无变更 / 失败都留）；`lc_page` 的 `current_version` / `publish_status` / `last_publish_time` / `publish_message`；`lc_project.last_commit_sha` / `last_publish_time`。产物落点仍由 `GeneratedPaths` 定，写盘走 `GeneratedFileWriter`（B 覆盖、C 存在即跳过） | 已确认 |
| 低代码平台 · 项目 | `scaffold-lowcode/src/main/java/com/scaffold/lowcode/project/controller/LcProjectController.java` | `service/LcProjectService.java`；`scaffold-lowcode/src/main/java/com/scaffold/lowcode/repo/RepoUrlValidator.java`（仓库地址白名单） | `mapper/LcProjectMapper.java`；`lc_project`（带 tenant_id，走拦截器） | 已确认 |
| 低代码平台 · 仓库与工作区 | `LcProjectController` 的 `/{id}/repo/check`、`/{id}/initialize`、`GET|DELETE /{id}/workspace` | `scaffold-lowcode/src/main/java/com/scaffold/lowcode/workspace/ProjectRepositoryService.java`（状态机与守卫）；`GitGateway.java`（接口）+ `JGitGateway.java`（JGit 实现，SSH 走主机密钥，含 tag 创建与推送）；`PlatformBaseTag.java`（底座标记 tag `yuanda/platform-base/{底座提交号}`，底座升级三方合并的 base 侧留痕）；`ScaffoldSourceLocator.java`（定位底座 checkout） | `mapper/LcProjectMapper.java` 的 `selectProjectForSync` / `updateSyncState`；`lc_project` 的 `sync_status` / `sync_message` / `workspace_path` / `base_scaffold_sha` / `last_commit_sha`；工作区落在 `scaffold.lowcode.workspace-root` 卷内；底座标记 tag 推进**项目仓库**（只创建、永不移动） | 已确认 |
| 交付快照裁剪（发布产物的定义） | 无 Controller，初始化时由 `ProjectRepositoryService` 调用 | `workspace/ScaffoldTrimPolicy.java`（路径白/黑名单 + `scaffold:platform-only` 标记块 + `deliverySubstitutions()` 交付改写表，含把 `APP.name` / `APP.shortName` 换成 `{PROJECT_NAME}` 的品牌改写与「交付产物不许带平台品牌」的守卫）、`ScaffoldSnapshotPlanner.java`（读底座 + 残留引用校验 + 改写块缺失即失败）、`PlatformOnlyStripper.java`、`ScaffoldSnapshotWriter.java`（`write` 只写清单；非空仓库的全量初始化再调 `pruneOutsideSnapshot` 删快照外遗留）、`ProjectMarker.java`（`.scaffold-project.json`） | 输入是本仓库的 git checkout（`base_scaffold_sha` 记它的 HEAD）；输出只进工作区，不直接写库；组件与样式不在快照里，靠 `@yuanda-lowcode-ai/scaffold-ui` npm 依赖（版本由 `scaffold.lowcode.scaffold-ui-version` 写进交付 `package.json`）；品牌由 `ScaffoldSnapshotPlanner.plan(sourceRoot, version, projectName)` 按项目名改写 | 已确认 |
| 低代码平台 · 租户资产开通 | 同上 `LcTenantController` 的 `/{id}/assets/sync` | `service/TenantProvisionService.java`（菜单 / 字典 / 参数 + 两个内置角色：`tenant_admin` 全量授权、`tenant_member` 只授页面与目录节点，注册与补齐共用） | `mapper/LcProvisionMapper.java`；`sys_menu` / `sys_role` / `sys_role_menu` / `sys_dict_type` / `sys_dict_data` / `sys_config`，全部显式带 tenant_id | 已确认 |
| 低代码平台 · 租户注册开通 | `scaffold-lowcode/src/main/java/com/scaffold/lowcode/tenant/controller/TenantRegisterController.java` | `service/TenantRegistrationService.java`（建租户 → 复制菜单树 → 建 `tenant_admin` + `tenant_member` 内置角色 → 建管理员 → 回令牌，单事务） | `mapper/LcProvisionMapper.java`；`sys_menu` / `sys_role` / `sys_role_menu` / `sys_user` / `sys_user_role` / `sys_login_log`，全部显式带 `tenant_id` | 已确认 |
| 低代码平台 · 租户 | `scaffold-lowcode/src/main/java/com/scaffold/lowcode/tenant/controller/LcTenantController.java`、`LcTenantConfigController.java` | 同包域 `service/LcTenantService.java`、`LcTenantConfigService.java` | `mapper/LcTenantMapper.java`（`lc_tenant`，平台表不带 `tenant_id`）、`mapper/LcTenantConfigMapper.java`（`lc_tenant_config`，读写条件由租户拦截器注入） | 已确认 |
| 租户隔离（公共能力） | 无 Controller | `scaffold-framework/src/main/java/com/scaffold/framework/config/MybatisPlusConfig.java` | `scaffold-framework/src/main/java/com/scaffold/framework/tenant/ScaffoldTenantLineHandler.java`；白名单表自动注入 `tenant_id` | 已确认 |

| 前端功能 | 路由 | 页面文件 | API 文件 / 后端模块 | 来源 |
| --- | --- | --- | --- | --- |
| 登录 / 租户注册（同一张卡切换表单，共用验证码与成功动画） | `/login` | `views/Login.vue` + `components/NeuralNet.vue` | `api/auth.ts` 的 `login` / `register` / `captcha`、`portal.ts` 的 `health`；文案与备案读数取自 `data/index.ts` 的 `APP.platform` / `company` / `beian` | 已确认 |
| 工作台（主次布局 + 会话首次开场 + 模块网络卡） | `/dashboard` | `views/Dashboard.vue` | `api/portal.ts`；工作台；有日志权限时加载当日泳道 | 已确认 |
| 用户（列表 / 看板） | `/system/user` | `views/system/Users.vue` | `api/system.ts`；系统管理 | 已确认 |
| 角色（列表 / 权限矩阵 / 溯源·图谱舞台） | `/system/role` | `views/system/Roles.vue` + `components/business/PermissionTrace.vue` | 同上 + `permissions/graph` | 已确认 |
| 菜单（表格 / 导航星系·图谱舞台 + 侧栏预览） | `/system/menu` | `views/system/Menus.vue` + `components/business/MenuStarMap.vue` | 同上 | 已确认 |
| 部门（组织画布 / 表格，选中路径脉冲） | `/system/dept` | `views/system/Depts.vue` | 同上 | 已确认 |
| 岗位（列表 / 阶梯·图谱舞台） | `/system/post` | `views/system/Posts.vue` + `components/business/PostLadder.vue` | 同上；阶梯成员经 `users?postId=` | 已确认 |
| 字典（表格 / 样本册） | `/system/dict` | `views/system/Dict.vue` + `components/business/DictSwatchbook.vue` | 同上 | 已确认 |
| 参数（设置面板 / 表格） | `/system/config` | `views/system/Configs.vue` | 同上 | 已确认 |
| 资源监测（读数条 + 折线 + 描述列表 + 表格，5 秒自动刷新） | `/system/resource` | `views/system/resource/index.vue` | `api/system.ts` 的 `resource.snapshot`；资源监测 | 已确认 |
| 租户管理（列表 / 新增 / 编辑 / 启停 / 进入该租户） | `/platform/tenant` | `views/lowcode/tenants.vue` | `api/platform.ts` 的 `platformApi.tenants`；切换租户走 `composables/auth.ts` 的 `switchTenant` | 已确认 |
| 租户参数（当前会话租户的键值参数） | `/platform/tenant-config` | `views/lowcode/tenant-configs.vue` | `api/platform.ts` 的 `platformApi.tenantConfigs` | 已确认 |
| 项目管理（列表 / 新增 / 编辑 / 启停 / 删除，分支列显示发布分支 → 主干） | `/lowcode/project` | `views/lowcode/projects.vue` | `api/platform.ts` 的 `platformApi.projects` | 已确认 |
| 模型管理（列表 + 右侧抽屉配字段，选类型实时预览 Java / 列类型） | `/lowcode/model` | `views/lowcode/models.vue` | `api/platform.ts` 的 `platformApi.models`；字典下拉读 `systemApi.dictTypes`（无权限时留空，保存由后端校验） | 已确认 |
| 页面设计 · 列表（有哪些页面、启停、删除、点「设计」开新标签页） | `/lowcode/page` | `views/lowcode/pages.vue` | `api/platform.ts` 的 `platformApi.pages`；菜单 503 定的就是这个路径 | PR 6C |
| 页面设计 · 设计器（独占整个浏览器标签页：无导航轨、无控制台顶栏。两条实现同一条路由） | `/lowcode/designer/:页面 ID` 默认渲染 **1:1 视觉原型**；带 `?mode=live` 渲染**已接数据那一版**（静态路由，挂在 `AppShell` 之外） | 原型：`designer/proto/ProtoDesigner.vue`（机身 + 顶栏 + 三栏 + 浮层）+ `state.ts`（节点树/撤销/拖拽与拖拽预览帧/浮层栈/提示牌堆）+ `data.ts`（19 项物料、20 种 kind 与 `KIND_ICON`/`lockIcon`/`visibleIcon`/`dropZones` 图标与落点语义表、演示数据）+ `NodeRenderer.vue` + `ProtoTreeRowActions.vue`（树行尾锁定/显隐/删除三颗，两处共用）+ `proto.css`。**分段控件、抽屉、底部浮条三样一律用平台件**（`Segmented.vue` / `Drawer.vue` / `.float-bar`），设计器不再自绘 —— 手画的那版激活态填近白渐变，深色主题下就是一块白斑。已接数据那一版：`LiveDesigner.vue` + `session.ts` + `DesignerTopbar/MaterialPanel/LayerTree/DesignCanvas/InspectorPanel/SchemaDrawer/PublishDialog/DesignerCommandDock.vue` | 两条共用同一个登录守卫与同一条路由。**原型那一版不打接口**：保存与发布只动本地计数，它输出的 `nodes[]` 形状不是 `design_json v1.0`，送进后端 `PageDesign.parse` 会当场 400；`design.ts` 那份（区块树、落点合法性、撤销栈、`design_json` 进出、`PALETTE` 与 `defaultPropsFor`）仍是唯一能产出生成物的模型，白名单与后端 `ComponentCatalog` 同一份，由 `design.test.ts` 逐项钉住。保存 `pages.update`、发布 `pages.publish`（`lowcode:page:publish`）只在 `?mode=live` 这条上通。机身与顶栏复用 `layout.css` 的 `.app-frame` / `.brand-mark` / `.crumbs` / `.cmd-trigger` 全局类。**这一段路由要套 `scaffold:platform-only` 标记**，否则交付项目会留下一条引用已剔除目录的死路。**画布落点只认 `dropZones()` 说有的那些**（栅格列、插槽腔体、顶层索引位）：在别的组件身上松手不改树、不进撤销栈；未保存浮条与批量操作浮条同源，都是 `instruments.css` 的 `.float-bar`。**设计器 CSS 只准引用 `tokens.css` 里存在的令牌**（`--line-hover` 是这一刀补进去的一档悬浮描边；原型自带的 `--plate-fg`/`--plate-muted` 在平台侧对应 `--plate-ink`/`--plate-soft`，不要照搬名字）—— 由 `proto.test.ts` 逐文件扫 `var()` 引用钉住，写错名字是静默失效：整条声明作废、深色玻璃上的文字会落到继承来的近黑 `--ink` 上。**顶栏没有返回按钮**（设计器独占整个标签页），离开这一页就是关标签页，脏了由 `useUnsavedGuard` 挂 `beforeunload` 拦一次。**窄屏（`narrow.ts` 的 820）以下左右两栏从 grid 列拿出来，改成盖在画布上的抽屉**，一次只开一扇、配遮罩与 Esc，预览态不参与；顶栏与原型层各自的手抄 `@media (max-width: 820px)` 由 `proto.test.ts` 那条断点用例钉住同一个数。**画布与层级树都有复制 / 隐藏 / 锁定**：`design.ts` 的 `duplicate(key, fields)` 只给控件（区块是按 `type` 定位的容器，复制出第二个会让 `locate()` 找错、画布上多一个改不动的幽灵区块，所以当场拒绝），**副本必须换绑字段** —— 后端 `PageDesign.validateChildren` 禁止同一区块绑两遍同一个字段（表单类型重复声明成员，交付项目 `vue-tsc` 当场挂），所以 `duplicate` 要收模型字段表，换到整页还没绑过、且 `controlFor()` 派生出同一种控件的字段，`label` 与 `queryType` 跟着新字段走，换不出就当场拒绝；`setFlag('visible' | 'locked')` 这两位与内部 `key` 同级、**不进 `design_json`、不算未保存改动、但进撤销栈**，关掉时整个 `delete` 不留 `false`；`locked` 让 `move` / `remove` / `duplicate` 全部抛错。**`session.ts` 的 `root` / `blocks` / `selected` 每次给新浅克隆引用** —— `store` 是普通可变对象，就地改完 `Object.is` 还相等，`computed` 不通知下游，表现是「数据变了画布不动」（`setProp` 一直有此问题，整值赋值撞不上而已）。落点预览：拖拽中的蓝槽写「松手插到第 N 个 · 区块名」，并有 `dragleave` 退高亮 | PR 8B–8F |
| 操作日志（横带：概览 / 趋势或泳道 / 明细，抽屉级联） | `/monitor/oper-log` | `views/monitor/OperLog.vue` | `api/system.ts`；日志与统计 | 已确认 |
| 登录日志（横带：概览 / 趋势或热力 / 异常 / 明细） | `/monitor/login-log` | `views/monitor/LoginLog.vue` | 同上 + `login-logs/heatmap` | 已确认 |
| 代码生成（三步流水线抽屉） | `/tool/generator` | `views/tool/Generator.vue` + `components/business/GeneratorPipeline.vue` | `api/portal.ts`；生成器 | 已确认 |
| 无菜单 / 无匹配页面 | `/no-access` / 通配路径 | `views/NoAccess.vue`、`views/NotFound.vue` | 路由与会话状态 | 已确认 |

| 公共能力 | 文件入口 | 用途 |
| --- | --- | --- |
| 列表模板与数据状态 | `packages/scaffold-ui/src/components/business/ListPage.vue`、`packages/scaffold-ui/src/composables/list.ts` | 查询、重置、分页、导出、勾选、底部批量浮条及树列表；`altView` + `#alt` 换成画布 / 看板 / 矩阵；行操作由 `RowActions.vue` + `RowMenuItem.vue` 承载（前几个平铺图标按钮，其余收进 teleport 到 body 的「更多」菜单，横向滚动与 overflow 裁剪都盖不住） |
| 版式组件 | `packages/scaffold-ui/src/components/business/OrgCanvas.vue`、`BoardView.vue`、`PermissionMatrix.vue`、`PostLadder.vue`、`PermissionTrace.vue`、`MenuStarMap.vue`、`DictSwatchbook.vue`、`packages/scaffold-ui/src/composables/view.ts` | 组织画布、看板分列（拖拽 + 键盘移动）、角色权限矩阵、岗位阶梯、权限溯源（三层下钻）、导航星系（三层下钻）、字典样本册、按页面记忆视图。`GeneratorPipeline.vue` 不在此列：它只服务被裁掉的代码生成页，留在宿主 `scaffold-vue/src/components/platform/` |
| 图谱与问答 | `packages/scaffold-ui/src/components/charts/RelationGraph.vue`、`packages/scaffold-ui/src/composables/neural.ts`、`packages/scaffold-ui/src/components/business/InsightPanel.vue`、`AskDock.vue`、`packages/scaffold-ui/src/composables/ask.ts`、`packages/scaffold-ui/src/composables/viewport.ts`、`components/NeuralNet.vue` | 通用关系图（弹簧布局变形、脉冲、扇形触须；`variant="stage"` 为珍珠玻璃图谱舞台）、舞台 Canvas 绘制件（珍珠球、玻璃刻度表盘、连线与脉冲、背景网络、轨道、台阶光柱）、分析栏、问答坞、本地规则问答引擎、共享视口平移缩放、登录神经网络背景 |
| 仪器化组件 | `packages/scaffold-ui/src/components/ui/Metric.vue`、`Odometer.vue`、`SectionLabel.vue`、`SpecPlate.vue`、`DeltaPill.vue`、`Ruler.vue`、`HoverCard.vue`、`Descriptions.vue` + `DescriptionsItem.vue` | 显示级读数、逐位滚动、编号眉标、规格铭牌、环比、刻度尺、悬停名片、只读键值汇总（复用 `.kv-grid` 材质，配 `Panel` 的 `padded`） |
| 图表 | `packages/scaffold-ui/src/components/charts/TrendChart.vue`、`BarChart.vue`、`TickRing.vue`、`Swimlane.vue` | 自绘 SVG：形变折线与准星（稀疏数据茎叶点，`max` 可锁定纵轴上限给百分比一类有界序列用）、柱状、刻度环、时间轴泳道（默认聚焦有操作时段）；工作台数据权限为页内占比条 |
| 表单控件与引用选项 | `packages/scaffold-ui/src/components/business/FormDialog.vue`、`DictSelect.vue`、`DeptTree.vue`、`DeptSelect.vue`、`UserSelect.vue`、`packages/scaffold-ui/src/components/ui/DateInput.vue`、`DateRangePicker.vue`、`NumberInput.vue`、`Textarea.vue`、`TreeSelect.vue`、`Tabs.vue` + `TabPane.vue`、`Upload.vue` + `Photo.vue` + `ImageField.vue`、`packages/scaffold-ui/src/composables/dateValue.ts`、`popover.ts`、`upload.ts` | 统一表单、字典 / 部门 / 用户选择、日期与区间（值与显示分离、区间两端共享锚点）、数字与多行文本、树形下拉、标签页、浮层定位、文件与图片上传（走 `fetch` + 同一套 Bearer 鉴权与 `{ code, message, data }` 信封） |
| 权限 | `packages/scaffold-ui/src/components/business/PermissionButton.vue`、`RowMenuItem.vue`，宿主侧 `composables/permission.ts` | 判定入口是宿主的 `hasPermission`，由 `main.ts` 调 `installScaffoldUi({ http, permission, dict })` 注入给包；包不 import 宿主模块。原 `directives/permission.ts` 里的 `v-permission` 指令全仓零使用，已随抽包删除 |
| 外壳 | `components/layout/AppShell.vue`、`Sidebar.vue`、`Topbar.vue`、`CommandPalette.vue` | 导航、移动侧栏、滚动凝结顶栏、命令面板（最近访问 / 页面 / 用户 / 动作）与账号操作 |
| 请求与会话 | `api/http.ts`、`api/session.ts`、`composables/auth.ts`、`router/index.ts` | 响应解包、存储、401 处理、动态路由与会话清理；`download` 走 GET 查询串、`downloadForm` 走 POST + 表单参数，两者共用同一套 401 与附件失败处理；`sys_menu.component` 由 `COMPONENT_ALIAS` 显式别名 + `import.meta.glob('../views/**/*.vue')` 约定解析，新增页面不必改 `auth.ts` |
| 外观、动效与离开守卫 | `packages/scaffold-ui/src/composables/appearance.ts`、`motion.ts`、`focus.ts`、`narrow.ts`、`unsavedGuard.ts` | 主题、密度、GSAP、浮层层级与焦点；`narrow.ts` 是平台唯一窄屏断点（820px，`NARROW_QUERY` / `isNarrow()` / `useNarrowScreen()`，外壳与 CSS 的手抄副本由 `proto.test.ts` 钉住同一个数，见第九节 PR 8F 那条）；`unsavedGuard.ts` 给独占标签页的页面挂 `beforeunload`（浏览器只给这一个钩子，且强制用它自己的系统确认框，网页无权在这里画 UI） |
| 样式 | `packages/scaffold-ui/src/styles/theme.css`（单一入口）内含 `tokens.css`、`base.css`、`materials.css`、`components.css`、`instruments.css`、`layout.css`、`pages.css` | 珍珠玻璃主题随包发布，`@import` 顺序就是原来的七层加载顺序；宿主 `main.ts` 只留 `import '@yuanda-lowcode-ai/scaffold-ui/theme.css'` 一行 |

| 部署链路 | 文件入口 | 用途 |
| --- | --- | --- |
| 服务编排 | `docker-compose.yml`、`.env.example` | 前端 nginx + 后端 + Redis 常驻，MySQL 在 `mysql` profile 内按需启用；后端对 MySQL 用 `depends_on` + `required: false` 的可选依赖，启用 profile 时等库健康、外部模式自动跳过，`DB_PORT`（后端连库端口）与 `MYSQL_HOST_PORT`（内置库宿主机映射端口）分用两个变量。操作与验证记录见 `docs/docker-deployment.md` |
| 后端镜像 | `scaffold-system/Dockerfile`、`scaffold-system/.dockerignore` | `maven:3.9-eclipse-temurin-17` 构建 → `eclipse-temurin:17-jre-jammy` 运行；镜像内装 `curl` 专供健康检查，`ENTRYPOINT` 预留 `JAVA_OPTS` |
| 前端镜像 | `scaffold-vue/Dockerfile`（构建上下文改为仓库根）、根 `.dockerignore`、`scaffold-vue/nginx.conf` | `node:22-alpine` 上先拷三份清单（根、`packages/scaffold-ui`、`scaffold-vue`）跑 `npm ci`，再 `npm --workspace scaffold-vue run build`（含 `vue-tsc`）→ `nginx:1.27-alpine` 托管 `/build/scaffold-vue/dist`；`/api` 反代到 `backend:8080`，SPA 深链接回退 `index.html`，`/assets/` 长缓存。原先的 `scaffold-vue/.dockerignore` 已删，上下文换到根之后只有根 `.dockerignore` 生效 |
| 内置 MySQL 字符集 | `deploy/mysql-client.cnf` | 官方镜像首次建卷导入 SQL 时客户端默认按 latin1 解析，中文会二次编码入库；该配置把 `[client]` 固定为 utf8mb4 |

## 四、场景剧本

**登录与切换账号**：`Login.vue` → `authApi.login` → `AuthController.login` → `AuthService.login` → `AuthMapper` 校验账号、写登录日志 → `JwtService` 生成独立 token、`TokenStore` 写 Redis → `/auth/me` 取角色和权限 → `/auth/routes` 取菜单 → `router/index.ts` 注册页面；退出同步清除会话、菜单、路由和引用缓存，旧请求以会话版本判定是否仍可写回。

**新增用户**：`Users.vue` → `/system/users/options` 取引用选项 → `FormDialog` 校验 → `systemApi.users.create` → `SystemManagementController.createUser` → `SystemManagementService.createUser` → 用户表与角色、岗位关联表 → 列表重载；操作日志由公共切面记录。

**分配权限**：`Roles.vue` → 菜单树与角色菜单接口 → `PermissionTree` 勾选菜单 / 按钮、选择数据范围 → `PUT /system/roles/{id}` → 更新角色及关联表 → 重新登录加载新权限；停用或删除角色不能提供菜单与操作权限。

**泳道定位明细**：`Dashboard.vue` / `OperLog.vue` 的 `Swimlane` → `GET /monitor/oper-logs/timeline` → `MonitorStatsService.operTimeline` 按模块聚合 → 点击时间块以模块名写入操作日志关键词并滚到明细；工作台跳转通过 `pathOf('monitor/operlog/index')` 取当前账号可访问路径。

**看板改状态 / 矩阵改授权**：`BoardView` 拖到「停用」列 → 确认 → `PUT /system/users/{id}/status` → 重载；`PermissionMatrix` 本地累计改动 → 浮条「保存变更」→ 逐个角色 `PUT /system/roles/{id}`（仅带 `menuIds`）→ 重载矩阵；内置角色只读。

**字典维护**：`Dict.vue` → 类型表单 / 数据抽屉 → 类型与数据 CRUD → 两张字典表 → `invalidateDict` 更新公共下拉缓存；业务下拉仅请求启用选项接口。

**代码生成**：`Generator.vue` → 查询当前库 `information_schema` → 导入表与列信息 → 保存说明 / 确认标记 → 预览 7 个文件 → 生成并下载 ZIP；业务 CRUD 实现与代码接入仍由开发者完成。

## 五、API 快速索引

所有路径都以前缀 `/api` 开始；`{id}` 表示记录 ID，`{dictType}` 表示字典类型。

| 功能 | 方法与路径 | Controller | 来源 |
| --- | --- | --- | --- |
| 健康检查 | `GET /health` | `HealthController` | 已确认 |
| 验证码 / 登录 / 退出 | `GET /auth/captcha`（返回 `captchaEnabled` 是否启用、`uuid` 校验标识、`img` base64 PNG 图片，不回传明文答案）、`POST /auth/login`（可选 `tenantCode`：账号跨租户重名时必填）、`POST /auth/logout` | `AuthController` | 已确认 |
| 用户、路由、修改密码 | `GET /auth/me`（含 `tenantId` / `tenantCode` / `tenantName` / `homeTenantId`）、`GET /auth/routes`、`PUT /auth/password` | 同上 | 已确认 |
| 租户自助注册 | `POST /auth/register`（匿名 + 验证码；`tenantCode` / `tenantName` / `adminUsername` / `password`；成功直接回 `token`、`expiresIn`、`tenantId`、`tenantCode`、`menuCount`） | `TenantRegisterController`（`scaffold-lowcode`） | 已确认 |
| 切换租户 | `POST /auth/switch-tenant {tenantId}`（仅 `super_admin`；`tenantId` 为空回到自己的平台租户；重签 JWT 并吊销旧令牌） | `AuthController` | 已确认 |
| 租户管理 | `GET/POST /platform/tenants`（列表含 `userCount`）、`PUT /platform/tenants/{id}`、`PUT /platform/tenants/{id}/status`、`GET /platform/tenants/export` | `LcTenantController`（`platform:tenant:list/add/edit`） | 已确认 |
| 模型 | `GET/POST /lowcode/models`（`projectId` / `keyword` / `status` 过滤）、`PUT/DELETE /lowcode/models/{id}`、`PUT /lowcode/models/{id}/status`、`GET /lowcode/models/options`、`GET /lowcode/models/meta`（类型映射表） | `LcModelController`（`lowcode:model:*`） | 已确认 |
| 字段 | `GET/POST /lowcode/models/{id}/fields`、`GET /lowcode/models/{id}/field-options`、`PUT/DELETE /lowcode/fields/{id}` | 同上（读用 `lowcode:model:list`，写用 `lowcode:field:*`） | 已确认 |
| 项目管理 | `GET/POST /lowcode/projects`、`PUT/DELETE /lowcode/projects/{id}`、`PUT /lowcode/projects/{id}/status`、`GET /lowcode/projects/options`（精简列表）、`GET /lowcode/projects/export` | `LcProjectController`（`lowcode:project:*`） | 已确认 |
| 仓库连通性检查 | `POST /lowcode/projects/{id}/repo/check`（只读 `ls-remote`，返回 `reachable` / `empty` / `branches` / `mainBranchExists` / `genBranchExists` / `action`；失败也回 200，用 `reachable=false` 表达，**不改项目生命周期**） | `LcProjectController`（`lowcode:project:sync`） | 已确认 |
| 固定代码初始化 | `POST /lowcode/projects/{id}/initialize`（同步执行：按裁剪清单写工作区 → 提交 → 显式推 `refs/heads/{gen_branch}` → 回读校验；`sync_status 0→1→2`，异常写 3/4/5 与 `sync_message`） | 同上（`lowcode:project:sync`） | 已确认 |
| 工作区读数与重建 | `GET /lowcode/projects/{id}/workspace`（`exists` / `dirty` / `unpushed` / `localSha` / `remoteSha`）、`DELETE /lowcode/projects/{id}/workspace`（有未提交改动或未推送提交时拒绝） | 同上（读用 `lowcode:project:list`，删用 `lowcode:project:sync`） | 已确认 |
| 补齐租户资产 | `POST /platform/tenants/{id}/assets/sync`（按平台模板补缺少的菜单 / 字典 / 参数并授权内置管理员角色，幂等） | `LcTenantController`（`platform:tenant:edit`） | 已确认 |
| 租户参数 | `GET /platform/tenant-configs`（分页，`keyword` 命中参数键 / 名称 / 备注）、`GET /platform/tenant-configs/export`、`PUT /platform/tenant-configs`（`{id?, configKey, configValue, remark}`）、`DELETE /platform/tenant-configs/{id}` | `LcTenantConfigController`（`platform:config:list/edit`）；只作用于当前会话租户 | 已确认 |
| 工作台 | `GET /dashboard/summary`（含 `trendPrev`、`dailyOps`；`stats` 含 `menus` 菜单项、`depts` 部门数） | `DashboardController` | 已确认 |
| 权限溯源图 | `GET /system/permissions/graph` | `SystemManagementController`（`system:role:list`） | 已确认 |
| 资源监测读数 | `GET /system/resource`（`server` 主机与运行时、`cpu` / `memory` 主机占用、`jvm` 堆与 GC 与线程、`disks` 挂载点、`redis` 缓存状态、`database.pool` 连接池与 `database.mysql` 会话读数；采不到一律 `-1`） | `ResourceMonitorController`（`system:resource:list`） | 已确认 |
| 用户 | `GET/POST /system/users`（列表支持 `postId` 岗位过滤）、`PUT/DELETE /system/users/{id}` | `SystemManagementController` | 已确认 |
| 用户引用 / 导出 / 状态 / 密码 | `GET /system/users/options`、`GET /system/users/export`、`PUT /system/users/{id}/status`、`PUT /system/users/{id}/password` | 同上 | 已确认 |
| 角色 | `GET/POST /system/roles`、`PUT/DELETE /system/roles/{id}`、`GET /system/roles/{id}/menus`、`GET /system/roles/export` | 同上 | 已确认 |
| 菜单 | `GET /system/menus/tree`、`POST /system/menus`、`PUT/DELETE /system/menus/{id}`、`GET /system/menus/export` | 同上 | 已确认 |
| 部门 | `GET /system/depts/tree`、`POST /system/depts`、`PUT/DELETE /system/depts/{id}`、`GET /system/depts/export` | 同上 | 已确认 |
| 岗位 / 参数 / 字典类型 | `GET/POST /system/posts`、`/system/configs`、`/system/dict/types`；各资源支持 `PUT/DELETE /{id}` 和 `GET /export` | 同上 | 已确认 |
| 字典数据 / 业务选项 | `GET /system/dict/data/{dictType}`、`POST /system/dict/data`、`PUT/DELETE /system/dict/data/{id}`、`GET /system/dict/options/{dictType}` | 同上 | 已确认 |
| 两类日志 | `GET /monitor/oper-logs`、`/monitor/login-logs`；各支持 `GET /export`、`DELETE /{id}`、`DELETE` 清空 | `MonitorController` | 已确认 |
| 登录热力 | `GET /monitor/login-logs/heatmap?days=7|30`（`cells` 星期×小时、`anomalies` 连续失败聚合） | 同上（`monitor:loginlog:list`） | 已确认 |
| 日志统计 / 泳道 | `GET /monitor/oper-logs/stats`、`GET /monitor/oper-logs/timeline`、`GET /monitor/login-logs/stats` | 同上 | 已确认 |
| 生成器表与导入 | `GET /tool/generator/tables`、`GET /tool/generator/importable-tables`、`POST /tool/generator/import` | `GeneratorController` | 已确认 |
| 生成器配置与输出 | `PUT /tool/generator/tables/{id}`；其后追加 `GET /columns`、`GET /preview`、`POST /generate`、`GET /download` | 同上 | 已确认 |

## 六、改动影响链

| 变更 | 必查位置 |
| --- | --- |
| 新增业务页面 | 业务表 / DTO → Mapper → Service → Controller → `src/api` → `ListPage` / `FormDialog` → `views/<component>.vue` 目录约定（同名的底座页面才需要在 `COMPONENT_ALIAS` 保留别名）→ 菜单及角色授权 |
| 改权限 | `sys_role` / `sys_menu` / 关联表 → `AuthMapper`、`SystemPermissionResolver` → `@RequiresPermission` → `auth.ts`、`PermissionButton` → 低权限浏览器 / 接口回归 |
| 改数据范围 | `SystemDataScopeResolver` → `DataScopeCriteria` → 查询与导出 SQL → 用户筛选 / 分页；新增业务必须显式接入 |
| 改字段 | 初始化或后续迁移 SQL → Mapper 字段别名 → DTO 校验 / Service → API 类型 → 页面表单、表格与导出 |
| 改外观 / 公共交互 | 样式令牌或公共组件 → 代表性页面 → 浅色 / 深色 / 窄宽度 / 键盘 / 减少动效 |
| 改认证 | `JwtService`、`TokenStore`、认证过滤器 → `AuthService` → `session.ts`、`http.ts`、`auth.ts`、路由守卫 → 会话回归脚本 |
| 改生成模板 | 新链路：`GenerationInput`（派生）→ `templates/lowcode/**` → `ModelCodeGenerator.renderFiles` → `GeneratedPaths`（落点与档位）→ `GeneratedCodeCompilesTest`（按交付路径落盘真起 javac）/ `ExtensionContractTest`（B 档调用点与 C 档骨架的钩子契约）/ `GeneratedFileWriterTest`（B 覆盖、C 存在即跳过）/ **`DeliveryGenerationApiTest`（交付快照 + 生成落盘的端到端，见下条）**。老链路：`ImportedTableInput`（库里已有的表 → 模型形状）→ 同一个 `ModelCodeGenerator` → `GeneratedFileZip`，e2e 在 `GeneratorImportApiTest`（含 zip 内产物真起 javac、菜单 SQL 在真库执行后回滚）与 `ImportedTableInputTest` |

## 七、症状定位表

| 症状 | 第一现场 | 排查重点 | 来源 |
| --- | --- | --- | --- |
| 切换账号后停留登录页 / 旧菜单仍可见 | `router/index.ts`、`composables/auth.ts`、`AppShell.vue` | 会话同步清理、旧请求版本、路由初始化取消、失效跳转 | 已确认 |
| 登录成功但没有菜单 | `AuthService.routes`、`AuthMapper`、`NoAccess.vue` | 角色启用状态、菜单授权、组件是否注册 | 已确认 |
| 菜单可见但接口返回 403 | `PermissionAspect`、`SystemPermissionResolver` | 当前角色 / 按钮权限、用户状态；引用选项使用专用接口 | 已确认 |
| 部门筛选漏掉下级 / 数据范围错误 | `SystemManagementMapper`、`SystemDataScopeResolver` | `ancestors`、部门范围、分页和导出是否同口径 | 已确认 |
| 时间差 8 小时 / 连接时区异常 | `application-dev.yml`、`DbInit` | Hikari 连接属性、`DB_TIMEZONE`、MySQL 会话 `NOW()` | 已确认 |
| 页面过渡空白 / 后台浮层未关闭 | `composables/motion.ts` | 片段根页面是否有 `.route-frame` 元素根、`pageRootOf` 能否找到 `.page`、`pageEnter` / `pageLeave` 的 `done()` 兜底定时器是否放行、后台状态与减少动效终态 | 已确认 |
| 导出接口报 `getOutputStream() has already been called` | `OperationLogAspect.filterSerializableArgs` | 切面是否已剔除 `ServletResponse` / `MultipartFile` 等容器对象；序列化失败只应丢日志内容，不能带崩业务请求 | 已确认 |
| 重启后端后所有 `/api/*` 变 401 | `application.yml` 的 `scaffold.security.jwt.secret`、启动方式 | `JWT_SECRET` 与上一个进程不一致（签名校验不过就是未认证）。启动变量固化成不入库的 `.env.local` 再 `set -a; . ./.env.local`，别从 `ps eww` 抓旧进程 env —— 含空格的值会被拆坏。流程见 `docs/docker-deployment.md`「本地直接跑进程」 | 已确认 |
| 改了代码但后端行为没变 | `scaffold-admin/target/*.jar` 时间戳 | 增量 `package` 可能不重打 fat jar，必须 `clean package`；怀疑依赖没进去就 `unzip -l <jar> \| grep <关键字>` | 已确认 |
| 项目管理显示「就绪」但工作区读数「未建立」 | `ProjectRepositoryService.resetWorkspace`、`scaffold.lowcode.workspace-root` | 工作区路径是相对**进程工作目录**的，换目录启动就读到空目录。点「清空工作区」把状态清回未初始化（目录不在时只重置状态、不碰远端），再重新初始化；不要手工改 `sync_status` | 已确认 |
| 升级后的项目里组件有两份（包里的 + 本地拷贝） | `ScaffoldSnapshotWriter.pruneOutsideSnapshot` | 全量初始化必须删快照外遗留；`sync_message` 里应出现「清理快照外旧文件 N 个」，为 0 又确实跨了形态就要查清理分支 | 已确认 |
| 生成项目 `npm install` 报 `ETARGET ... @yuanda-lowcode-ai/scaffold-ui` | `scaffold-vue/.npmrc` | 国内镜像同步新包滞后；交付自带 `.npmrc` 把该 scope 钉在官方 registry，缺这个文件或被覆盖就会撞 | 已确认 |
| 日期字段整体早 8 小时 / `yyyy-MM-dd` 少一天 | `application.yml` 的 `spring.jackson.time-zone` | 序列化时区是否与连接强制的 MySQL 会话时区（`+08:00`）对齐；`DB_TIMEZONE`、Hikari 连接属性 | 已确认 |
| 读数停在 0 / 动画停在中途 | `Odometer.vue`、`motion.ts` | 后台标签页 rAF 不触发：读数用定时器兜底，GSAP 补间在切到后台时走完 | 已确认 |
| 登录成功后停在对勾圆环 | `Login.vue` | 进入外壳的导航被取消（会话过期等）时须恢复登录按钮 | 已确认 |
| 抽屉内确认框被挡 / Escape 连带关闭 | `focus.ts`、`Modal.vue`、`Drawer.vue`、`Select.vue` | 打开顺序、公共层级、最上层焦点与事件 | 已确认 |
| 预览其他文件无代码 / 菜单 SQL 不匹配 | `GeneratorService`、`Generator.vue` | 每份文件的 `code`、当前菜单字段和类型 | 已确认 |

## 八、权限标识速查

| 模块 | 权限码 | 用途 |
| --- | --- | --- |
| 用户 | `system:user:list/add/edit/remove/resetPwd` | 查询导出 / 新增 / 编辑状态 / 删除 / 重置密码 |
| 角色、菜单、部门、岗位、字典、参数 | `system:role`、`system:menu`、`system:dept`、`system:post`、`system:dict`、`system:config`，后缀 `list/add/edit/remove` | 各模块查询导出与写入操作 |
| 资源监测 | `system:resource:list` | 查询主机 / JVM / Redis / 数据库连接读数（只读，无写入权限码） |
| 日志 | `monitor:operlog:list/remove`、`monitor:loginlog:list/remove` | 查询导出 / 删除清理 |
| 代码生成 | `tool:gen:list` | 当前生成器全部接口 |
| 租户管理 | `platform:tenant:list/add/edit` | 查询导出 / 新增租户 / 编辑与启停（平台运营职责，只下发给 `super_admin`） |
| 租户参数 | `platform:config:list/edit` | 当前会话租户的键值参数读写；内置参数不可删除 |
| 项目管理 | `lowcode:project:list/add/edit/remove/sync` | 项目查询导出与工作区读数 / 新增 / 编辑与启停 / 删除 / **仓库动作**（连通性检查、初始化固定代码、清空工作区）。单独一个 `sync` 码是因为「能编辑项目」不等于「能往租户仓库推代码」 |
| 模型与字段 | `lowcode:model:list/add/edit/remove`、`lowcode:field:add/edit/remove` | 模型查询与字段读取 / 新增 / 编辑与启停 / 删除；字段的增删改单独三个码 |

- 超级管理员角色编码为 `super_admin`，后端操作权限直接放行，菜单按启用和显示状态读取。
- 业务字典选项仅需已登录；字典管理数据和写入仍使用对应权限。
- 用户引用选项要求 `system:user:list`；只读用户不接收角色、岗位及初始密码选项，也不接收部门联系人信息。
- 数据范围：`ALL` 全部、`DEPT_AND_CHILD` 本部门及下级、`DEPT` 本部门、`SELF` 本人、`CUSTOM` 自选部门。

## 九、特殊机制备忘

- **多租户地基（PR 1，2026-11-03）**：`TenantContext`（`scaffold-common`，纯 ThreadLocal）+ `TenantFilter`（`scaffold-framework`，`SecurityConfig` 用 `.addFilterAfter(tenantFilter, JwtAuthenticationFilter.class)` 注册）+ `LoginUser.tenantId` + JWT `tenantId` claim 已打通登录链路：`AuthService.login` 依次校验账号密码 → 用户状态 → 租户状态（`AuthMapper.countActiveTenant`），签发 token 携带 `user.getTenantId()`；`SecurityUtils.getTenantId()` 在使用点抛 401。11 张 `sys_*` 表带 `tenant_id NOT NULL DEFAULT 1`；跨租户唯一键改为 `(tenant_id, 原键)`。旧令牌（无 `tenantId` claim）解析出 `tenantId = null`，`TenantFilter` 不抛错，业务层决定是否拒绝。
- **平台推仓库的两个运行期前提**（PR 4 真机验证踩到的，`SshTransportSupportTest` 已钉住）：① `scaffold-lowcode` 必须带 `bcprov-jdk18on`（父 pom 显式钉版本，Boot 与 JGit 都不管理它）—— 没有它 Apache SSHD 解析不了 ed25519 私钥，报 `publickey: no keys to try`，看着像凭据没配；② `JGitGateway.directSshProxy()` 固定不走 HTTP/系统代理 —— 本机开着代理时 JGit 的默认代理工厂会把 `github.com:22` 指到代理端口，失败只剩 `Session is being closed`。需要代理就在 `~/.ssh/config` 写 `ProxyCommand`。凭据仍然只有主机密钥，库里不存 token。
- **交付产物由代码里的裁剪清单定义，不在文档里手抄一遍**（PR 4，方案 §6.4）：`ScaffoldTrimPolicy` 是路径白名单 + 黑名单，`PlatformOnlyStripper` 再删文件内的 `scaffold:platform-only:start/end` 标记块（标记必须独占整行）。新增平台侧模块、页面、接口客户端、建表脚本或 compose 挂载时，**三处要一起想清楚**：路径要不要进黑名单、是不是既有文件里的平台专属片段、`ScaffoldSnapshotPlanner` 的残留引用扫描能不能兜住。清单改了必须跑 `ScaffoldSnapshotPlannerTest`（它对真实底座出快照），漏裁不会静默出门 —— 初始化当场失败。**反向的那一半也要想**：裁剪走的是文件系统遍历，**底座机器上的 `.gitignore` 对它不起作用**，所以本机口令与解释器字节码（`.env.local`、`__pycache__`）必须按名字进 `IGNORED_NAMES` —— 少一条就把开发用的 `JWT_SECRET` 列进 A 档清单，而且它照样计入「可自动合 N 个文件」报给审的人，实际却因为交付仓库自己的 `.gitignore` 而根本没被 `git add` 收走（10G 真机跑出来过，那趟没推上去是运气不是闸门）。交付项目里 `lc_tenant` 与其余 `lc_*` 命运不同：**保留**，因为 `AuthService.login()` 每次都要查租户是否停用；剔除的是 `lc_project` / `lc_model` / `lc_field` / `lc_tenant_config` 等元数据表。
- **底座升级的 base 侧靠项目仓库里的 tag，不靠平台侧底座历史**（2026-11-04，方案 §5.2）：初始化每交付一份完整 A 档快照，就给那个提交打附注 tag `yuanda/platform-base/{base_scaffold_sha}` 并推到项目仓库（`PlatformBaseTag`）。三方合并的三个输入是 ours = 项目 `main`、theirs = 按底座 checkout 当前 HEAD 现生成、base = 这个 tag，**三者都不需要平台保留底座的 git 历史**；历史只用于「落后多少个提交」那个计数，拿不到就退化为只给文件级差异清单。tag **只创建、永不移动、永不删除**（移动它等于改掉上一轮升级的 base），推不上去只告警不回滚初始化。**base 侧靠「加新 tag」前进，不靠搬旧 tag**（PR 10）：升级分支被负责人合进主干后，平台按新的 `target_scaffold_sha` 打一个新的 tag 在那笔合并提交上、并把 `lc_project.base_scaffold_sha` 推到同一个号 —— 旧 tag 原样留着当那一轮的 base；漏掉这一步 base 就永远停在上一轮，下一趟会把已经下发过的内容再下发一遍，而界面上看不出异常。tag 打不上去时**不回滚合并**，只把告警写进升级记录，代价是下一次升级被「找不到底座标记 tag」拦住（拦住比拿错的 base 硬算安全），人工补打的入口还没做。连带两条部署不变式：① 底座 checkout 必须带 `.git`，否则 `base_scaffold_sha` 为空、tag 打不出来，该项目以后做不了底座升级（平台会如实写进 `sync_message` 与抽屉读数）；② 平台镜像与底座 checkout 必须成对更新 —— 裁剪清单在后端代码里、底座源码在 checkout 里，版本错开就是「按旧清单裁新源码」，而 tag 记的是新源码的提交号。
- **生成的 SQL 是「给人执行」的产物，不是迁移记录**（PR 5C）：建表落 `scaffold-system/sql/pages/{表名}_table.sql`、菜单落 `scaffold-system/sql/pages/{表名}_menu.sql`（落点表在 `GeneratedPaths`）。三条口径钉死：① **永不生成 `DROP` / `ALTER ... DROP COLUMN`**，`CREATE TABLE IF NOT EXISTS` 只建新表 —— 已存在的表不加列，模型加字段时看合并请求 diff 里多出的那一行人工 `ALTER`（把 ALTER 写进文件会让第二次执行直接报错，MySQL 没有 IF NOT EXISTS 的加列写法）；② 菜单**不能用底座的「固定 ID + INSERT IGNORE」**（那是 V6 知道 502 这个号，而 `sys_menu.id` 在 V4 已改数据库自增），改成按 `permission` 判重的 `INSERT ... SELECT ... WHERE NOT EXISTS`，父级用会话变量取，只授给 `role_id = 1`；③ 子目录不在 `docker-entrypoint-initdb.d` 的自动执行范围内（mysql 不递归），由人手工执行且**客户端必须带 `--default-character-set=utf8mb4`** —— 不带时容器 locale 为 `C`，中文注释与菜单名会被二次编码入库，与 `deploy/mysql-client.cnf` 的由来同一个坑。另外列名注释里的单引号要翻倍（`sqlText`），LIKE 查询列与 TEXT 列都不建索引（前置通配用不上、TEXT 建索引必须给前缀长度否则建表失败）。
- **前端生成物口径（PR 5D）**：版式固定 `ListPage` + `useListPage`（查询 / 重置 / 分页 / 导出 / 行操作都是物料里的公共能力，页面只声明字段、列与动作）；组件一律从 `@yuanda-lowcode-ai/scaffold-ui/components/{ui,business}`、`composables/list`、`utils/validate` 子路径引，**产物里不出现 `@/components` / `@/composables`**；接口封装写 `src/api/{module}/{resource}.ts`，从 `@/api/http` 取 `httpClient`，**不引 `@/api` 汇总出口**（A 档，平台不往里加导出行）。控件映射在 Java 侧定（`formControl`）：挂字典的一律 `DictSelect` + 单元格 `DictTag`，TEXTAREA→Textarea、NUMBER→NumberInput、DATE/DATETIME→DateInput、其余 TextInput；**没挂字典的 SELECT、UPLOAD、REF 先落文本框并留注释**（选项来源与上传接口模板不替项目猜）。类型形状两条：日期在 TS 里是 `string`（后端 `@JsonFormat` 出的规范串，前端 `dateValue` 也只认这两种）；**数字字段在入参里一律 `\| null`**（Java 侧是 Integer/Long/BigDecimal，必填由后端 Service 校验并回「XX不能为空」，前端表单初值是空，类型上先允许 null 才不用骗编译器）。编辑必须先 `GET /{id}` 取整行再填表单 —— 列表行只有勾选进列表的字段，直接拿 row 填会把没显示的字段清空，这就是 Mapper/Service/Controller 多一个详情方法的原因。验证方式：把渲染出的两份文件放进宿主 `scaffold-vue/src` 的对应路径跑 `vue-tsc`（本模块测试不引 node 工具链，演练是一次性的），过了再删掉。

- **`@MapperScan` 会把包树里所有接口注册成 Mapper**：所以低代码侧要逐个列到 `*.mapper` 包，写成包根 `com.scaffold.lowcode` 会让服务接口（如 `GitGateway`）多出一个 bean 把构造注入打成二义、后端直接起不来。业务包 `com.scaffold.business` 按包根扫是有意的例外，代价是那里的接口一律按 Mapper 注册。
- **生成物口径（PR 5B 定，模板与测试一起钉住）**：单类 Service 不做 interface+impl；查询条件逐个参数平铺（不塞 Map、不造 Query 对象）；SQL 写成「参数为空就是不限制」的**静态语句**，不用 `<script>` 拼 WHERE，运算符只来自 `QueryType`；日期出入参用 `@JsonFormat` 在生成字段上钉成前端 `dateValue` 只认的两种规范串（不改全局 Jackson，那是 A 档）；创建/更新时间交给表上的 `DEFAULT CURRENT_TIMESTAMP` / `ON UPDATE CURRENT_TIMESTAMP`；列表排序固定主键倒序，不猜「哪个字段是排序列」；区间查询拆 `xxxStart` / `xxxEnd`（`BETWEEN` 一端为空会整条失效）；多选收逗号串走 `FIND_IN_SET`（注解 SQL 里免掉 `<foreach>`）。三条必须记住的引擎事实：① `TemplateRenderer` **要锁 dollar 插值语法**，否则 MyBatis 的 `#{x}` 会被 FreeMarker 当自己的表达式吃掉；② 模板里 `.ftl` 的缩进与空行直接决定产物可读性，只在「整行只有 FTL 标签」时才指望空白被剥掉，循环里的续行必须让该行带上真实内容；③ 生成物不加反引号，所以表名/列名的 MySQL 保留字在建模时就拦（`SqlIdentifiers`，`desc` / `order` / `key` 一类）。**改模板必须跑 `GeneratedCodeCompilesTest`**：它拿测试类路径（已含 common + framework + mybatis + spring，与交付项目 `scaffold-business` 的依赖同批）真起 javac 编一遍七个文件，形状断言抓不到少逗号、少 import 这类错。

- **分档与扩展点口径（PR 5E 定）**：① 产物清单只从 `ModelCodeGenerator.renderFiles()` 出，档位由路径反推（`GeneratedPaths.tier`，白名单形状匹配：`scaffold-{…}` 之外的、以及 `src/api/index.ts` / `src/components/**` / pom / Dockerfile 这些 A 档形状一律拒写），**模板不许自述档位**；`{Cls}Extension.java` 也住在 `service/` 下、形状是 B 档的子集，所以 C 档先判。② C 档就一份 `{Cls}Extension`，七个钩子（`afterRows` + create/update/delete 各一对 `before*`/`after*`），B 档 Service 构造器注入并逐个调用 —— 钩子签名是平台契约，改了就编译失败（刻意）；钩子在 Service 的 `@Transactional` 里跑，`after*` 抛异常连带回滚。③ 两条 sink 语义不同：`GeneratedFileWriter`（工作区）B 覆盖 / C 存在即跳过，`GeneratedFileZip`（下载）**不做跳过**、照带骨架。④ 「存在即跳过」的前提是工作区反映项目当前内容，发布分支落后主干时会以 `both added` 冲突暴露而非静默丢代码 —— 这条归 PR 7 兜。⑤ 模型的 `is_tree` / `enable_data_scope` 两个开关**在进模板前抛错**（不静默忽略勾选），`models.vue` 的 hint 同步改成「本期不生成」。

- **Mapper 里带 `OR` 的关键词条件必须整体加括号**：租户拦截器补的是「原 WHERE `AND tenant_id = ?`」，而 SQL 中 `AND` 优先级高于 `OR`。写成 `WHERE #{kw} IS NULL OR #{kw} = '' OR a LIKE .. OR b LIKE ..` 时，注入的租户条件只绑到最后一个 `OR` 分支，带关键词的查询会漏出别的租户数据（`LcTenantConfigMapper` 初版就踩了这一点，已修并补越权用例）。正确写法是 `WHERE (#{kw} IS NULL OR ... OR b LIKE ..)`，底座既有 Mapper 全部是这个形态。
- **字段类型映射只有一份**：`com.scaffold.lowcode.model.FieldType` 定义「类型 → Java 类型 / 列类型 / 默认控件」，保存时把算好的 `java_type` / `column_type` 落库，模板生成直接读库值，前端读 `GET /api/lowcode/models/meta` —— 库里和前端都不另存映射表。`FieldTypeTest` 逐条钉死这张表，改它等于改所有已生成项目未来发布的产物。连带约束：自增主键固定 `bigint` / `Long`；`ENUM` 必须挂当前租户真实存在的字典；`REF` 必须指向同项目模型（指向自身合法）；一个模型最多一个主键；`RADIO`/`CHECKBOX` 要么挂字典要么给固定选项；查询方式只认 `QueryType` 枚举，认不出来回落等于，任意字符串不得进 SQL 片段。
- **`Naming` 的三种写法幂等**：`snake` / `pascal` / `camel` 先按分隔符（空格 `.` `-` `_`）切，再按大小写边界切（`HTTPServer` → `http_server`），所以 `orderService`、`OrderService`、`order_service` 得到同一组词；表名 / 类名 / 列名 / 包名都由它派生，列名不给单独改的入口。
- **接缝上的口径由真机端到端钉住（PR 5G，2026-11-04）**：`DeliveryGenerationApiTest` 把「建项目 → 初始化交付快照 → 建模配字段 → 生成 → 落盘」接成一条常驻用例，钉的是各段单测都看不见的那几处接缝 —— 生成的 java 落进交付项目后仍要 `scaffold-business` 被父 pom 聚合（裁剪清单误删模块时产物只是一堆不参与构建的源文件）、`.vue` 里 `@/api/...` 指向的文件必须真存在、菜单 `sys_menu.component` 必须能解析到 `scaffold-vue/src/views/...`、物料包的具名导出与版本号要核得上。**这一趟真机（干净目录 clone → `docker compose up`）撞出四处缺陷，形状断言与 javac 都抓不到**：① `.vue` 的接口引用用了驼峰 `resourceName` 而文件落点是蛇形 `resourceSlug`，单字词根看不出来；② `lc_field.column_type` 存的已经是 `varchar(64)`，`GenerationInput` 又按 `columnLength` 拼一遍长度，建表语句成 `VARCHAR(64)(64)`（老链路给的是裸 `varchar` + 长度列，两条链路入参形状不同）；③ 自增主键进表单 —— 字段接口把「可录入 / 可编辑 / 必填」默认成全开，而模板的类型里还硬编码着一行 `id?: number`，于是 `id` 被声明两遍（一条 `number | undefined`、一条 `number | null`），交付项目 `vue-tsc` 报 TS2717 / TS2322，前端镜像根本构建不出来；④ `@Options(keyProperty = "id")` 在多参数方法上回写不了自增号（审计列让 insert 多收一个 `operator`），MyBatis 抛 `ExecutorException`，**编得过、单测全绿，真机上点第一次「新增」才炸** —— 底座的写法是 `keyProperty = "row.id"`，模板跟着它（这条曾被 `CrudTemplatesTest` 按模板原话钉死，等于测试与模板一起错）。另外补了一条生成边界：**主键必须是数字**（`Long` / `Integer`），路径参数写的是 `@PathVariable Long id`，字符串主键当场拒绝而不是产出一份调不通的接口。
- **页面是形、模型是料，合流点在 `GenerationInput`（PR 6B 定，2026-11-05）**：有设计时「哪些格子出现、什么顺序、叫什么、怎么渲染、点哪几个按钮」由 `design_json` 说，「类型、DDL、约束、权限点前缀」仍由模型说，两者只在 `GenerationInput` 合流一次，模板里不做判断。几条不显然的口径：① 写入语句的列 = 设计表单 ∩ 模型允许写入（`is_insert` / `is_edit`），两边都收紧、谁都不能单独放开 —— 只认设计会让表单收了模型不许写的列而静默丢值，只认模型会生成出「表单里没有那格却要求填值」的接口；② 主键不进列但恒进 `Row`（`openEdit` 与 `remove` 都读它）；③ 前端必填 = 模型必填或设计标必填（只能收紧），后端校验注解只认模型，两边不一致时以数据库约束为准；④ `export` 不产出按钮（导出是 `ListPage` 表头的固定能力），但设计里没有 `export` 就不接 `exporter`，表头那颗随之消失；⑤ 菜单种哪些权限点跟着设计里真出现的动作走，没有删除动作就不种 `:remove`；⑥ 没有 `PageTarget` 的那条路（zip 下载与老代码生成页）形状必须与 PR 5 逐字节一致，`DesignGenerationTest` 末例钉它 —— 合流不能把既有链路一起改掉。第一期另有两条入口拦截与一条边界：有 `create` / `edit` 就必须画 `FormContainer`；画了弹窗就要收齐「NOT NULL 又没默认值」的可写字段（判据与建表语句 `ddlNull` 同源，两处不同口径这条规则自己会打架）；一个模型只允许一个列表页，因为页面落点 `views/{module}/{Cls}.vue` 由模型类名派生，第二个会在下次发布整文件盖掉第一个。
- **两条链路的类型口径只有一份（PR 5F 收口）**：`FieldType` 是「字段类型 → Java 类型 / 列类型 / 默认控件」的单向事实源，反向的「库里已有的列类型 → 字段类型」也在同一个枚举上（`FieldType.ofColumn`），老代码生成页不再自己算第二份。为此补了 `LONG`（`bigint` 非自增列，如外部 ID、以分计的金额）与 `FormType.code()`；`defaultQueryType()` 只给文本列 `LIKE`。同一个 `date` 列现在两条链路都得到 `LocalDate`。
- **权限码前缀是口径**：`platform:` = 平台运营专属（租户管理、租户参数），不下发给普通租户；`lowcode:` = 租户自己的开发功能（项目、模型、页面），随菜单模板复制给每个租户。菜单模板的排除条件按语义写：`path LIKE '/platform%' OR permission LIKE 'platform:%'`，不硬编码菜单 ID。
- **租户与平台的权限边界只有一个出处：`framework/security/PlatformBoundary`**。角色码与权限码在多租户下都不可信 —— `sys_role` 的唯一性只在租户内，菜单权限码由「菜单管理」自由填写，所以租户管理员原本能自建 `role_code = super_admin` 或造一条 `permission = platform:tenant:list` 的菜单来拿到平台接口。三处口径都走这个类：① 手工建角色时 `super_admin` 只归平台租户、`tenant_admin` / `tenant_member` 是开通内置码不让手工占用；② 租户侧菜单不能用 `/platform` 路径段与 `platform:` 权限码前缀（与菜单模板的排除条件同一口径）；③ `super_admin` 的放行判定要求**账号归属平台租户**（`SystemPermissionResolver.hasPermission`、`AuthService.currentUser/routes/switchTenant` 都按这条），租户内伪造同名角色码不算超管，也切不了租户。
- **角色是租户私有物料，超管的「全权限」不含跨租户**：`sys_role` 在租户拦截器白名单里，角色列表按会话租户注入条件，所以平台管理员停在平台租户时，角色管理只看得到租户 1 的种子角色，看不见也分配不了各租户的 `tenant_admin` / `tenant_member`（反向同理）。要管某个租户的角色就走租户管理的「进入该租户」（`/api/auth/switch-tenant`，顶栏显示「已切换」），切进去后的读写都落在该租户；拿着别租户的 roleId 授权会被 `ownedIds` 判成「不属于当前租户」而 400。这条由 `TenantRoleCaliberApiTest` 与 `TenantIsolationApiTest` 共同钉住。
- **平台新增租户可见菜单后要「补齐租户资产」**：迁移脚本只往平台租户插菜单，已有租户不会自动获得（`sys_menu` 是租户各一份）。用租户管理里的「补齐租户资产」或 `POST /api/platform/tenants/{id}/assets/sync`；菜单按身份键判重（`permission` → `path` → `menu_type + menu_name`），`sys_dict_data` 无唯一键只能按 `(dict_type, dict_value)` 反查去重。
- **租户注册即开通**：`POST /api/auth/register` 在一个事务里建租户 + 复制平台菜单树（排除 `path LIKE '/platform%'` 或 `permission LIKE 'platform:%'` 的段）+ 建两个内置角色（`tenant_admin` 拿全部业务菜单，`tenant_member` 只拿 `menu_type <> 'btn'` 的页面与目录节点，即「能看不能改」）+ 建管理员账号，并直接回登录令牌。给新租户预置只读角色是因为注册者进门第一件事就是建用户，而用户必须挂一个角色 —— 只留一个全权限角色等于逼人把权限给满。存量租户靠「补齐租户资产」补出这个只读角色。**注册要求管理员账号全局唯一**（严于 `sys_user` 的 `(tenant_id, username)` 唯一键），否则同名账号会让别人登录时必须多填租户编码。注册是匿名请求，开通用的写入全部显式带 `tenant_id` 并标 `@InterceptorIgnore`。
- **租户参数页必须是 `ListPage`**：DESIGN.md 规定列表统一用 `ListPage` + `useListPage`（查询 / 重置 / 分页 / 导出），所以 `/api/platform/tenant-configs` 是分页接口而不是数组接口；不要为"数据量小"另起一套 `PageHeader + DataTable` 手搓版式。
- **CSV 下发统一走 `framework/web/CsvResponse.attachment(filename, content)` + `common/export/CsvUtils`**：Controller 不再各自私有 `csv()`。
- **交付项目的品牌与话术跟着项目走（PR 5G 补）**：一份 `APP`（`scaffold-vue/src/data/index.ts`）被登录页大字、侧栏与浏览器标签（`router/index.ts` 的 `title · APP.name`）共同引用，所以交付改写只钉那一处 + 删掉登录页「代码生成」那条已被裁掉的能力；生成物注释写「低代码设计器」，不写平台名，也不引用交付清单里没有的方案章节（`ModelCodeGeneratorTest` 扫 11 份产物断言）。品牌关键字进了 `ScaffoldSnapshotPlanner.validate` 的拒绝项，跟平台代码引用残留同一套做法。
- **交付项目的镜像构建挂了 Maven 依赖缓存（PR 5G 真机测出来的）**：`scaffold-system/Dockerfile` 里 `mvn package` 那一步用 `RUN --mount=type=cache,target=/root/.m2`。构建上下文是整份源码，没有挂载时改一行业务代码就要重下几百个依赖（干净机器实测第一次约 14 分钟全在下载，挂上后同一趟 4.7 秒）。这份 Dockerfile 属 A 档，**存量项目要等一次底座升级才拿得到**。
- **CSV 导出的时间口径在 `CsvUtils` 里收（PR 5G）**：`cell()` 不直接 `String.valueOf`，`LocalDateTime` / `LocalDate` 按 `@JsonFormat` 给前端的同两种规范串写出。导出的列也要与页面对齐：审计开关打开时页面多出「创建时间」，`selectRows` 与 `CsvUtils.csv` 都得带上它，否则那一列在页面上是空的、在文件里根本没有。

- **租户 SQL 自动过滤（PR 2，2026-11-03）**：`MybatisPlusInterceptor` + `TenantLineInnerInterceptor` 在 `scaffold-framework` 注册，`ScaffoldTenantLineHandler.TENANT_TABLES` 是**白名单**（11 张 `sys_*` + `lc_tenant_config`）：不在名单里的表不注入 —— 包括 `lc_tenant`、四张关联中间表、`gen_table*` 与 `information_schema`，反向写法会让公共查询背上不存在的列。名单遗漏等于泄露，由 `ScaffoldTenantLineHandlerTest` 解析 `sql/V*.sql` 反向校验：凡 DDL 带 `tenant_id` 列的表必须在名单内。**新增带 `tenant_id` 的表必须同步登记**。匿名请求（登录、验证码、健康检查）无上下文，整体跳过注入。日志「清空」因此只清当前租户。
- **`super_admin` 跨租户 = 会话内切换租户（PR 2 定口径）**：`POST /api/auth/switch-tenant` 校验调用者角色与目标租户状态后重签 JWT（`tenantId` 指向目标租户）并吊销旧令牌，空 `tenantId` 回到自己的平台租户；管理员的身份、角色、数据范围仍按本人解析（相关查询标 `@InterceptorIgnore(tenantLine = "true")` 按全局主键定位），业务数据与菜单集仍按当前租户。平台管理菜单不下发给租户，所以顶栏常驻「回到平台租户」。切换动作写登录日志。
- **账号不是全局身份键（PR 2）**：唯一键是 `(tenant_id, username)`，跨租户重名是常态。登录可选 `tenantCode`，命中多个租户且未填编码时报「该账号存在于多个租户，请填写租户编码」；权限与数据范围解析改按 `LoginUser.userId()`（`PermissionResolver` / `DataScopeResolver` 入参已从 `username` 改为 `LoginUser`）。
- **关联中间表的跨租户写入靠服务层拦**：`sys_user_role` / `sys_user_post` / `sys_role_menu` / `sys_role_dept` 无 `tenant_id`，拦截器管不到写入；`SystemManagementService.ownedIds()` 在授权前按当前租户反查目标主键，外来 ID 直接拒绝并回滚整笔事务。菜单 `parentId` 同样校验归属。
- **`sys_menu.id` 由数据库自增**：应用侧 `MAX(id)+1` 被租户条件限制，多租户会撞全局主键；V1 建表、`insertMenu`（`useGeneratedKeys`）与 V4 存量 `ALTER` 一起改。
- **租户参数只作用于当前会话租户**：`/api/platform/tenant-configs` 不收 `tenant_id` 入参，条件全靠拦截器注入 —— 要改别的租户先切换过去。新建租户时为其灌默认参数是唯一例外（`insertForTenant` 显式带 `tenant_id` 并跳过注入）。
- **验收库的双租户数据不在默认初始化里**：`sql/verify/V9__dual_tenant_demo.sql` 放子目录，mysql 的 `docker-entrypoint-initdb.d` 不递归子目录；跑 `TenantIsolationApiTest` 前手工执行一次。**尚未交付**：租户开通流程（新租户的管理员账号、角色与菜单初始化），当前「新增租户」只建租户与默认参数。
- Redis token key 前缀为 `auth:token:`；退出删除当前 token，每次登录的 JWT 带独立 ID。
- JWT 签名密钥读取必填环境变量 `JWT_SECRET`，UTF-8 编码至少 32 字节且不能全为空白；缺失或长度不足时阻止启动。
- 验证码答案只存 Redis 并在 `verify` 时消费；接口只返回 base64 PNG 图片与 `uuid`，明文不回传前端（`code` 字段已删除）。开关 `scaffold.security.captcha.enabled`（环境变量 `CAPTCHA_ENABLED`）关闭时 `verify` 直接放行，`scaffold-admin` 的 surefire 以系统属性关闭，集成测试因此不需要读图。
- 免鉴权接口优先用 `@Anonymous` 标注在 Controller 类或方法上，由 `PermitAllUrlProperties` 启动扫描加入放行列表（路径变量 `{xxx}` 归一为 `*`），不要在 `SecurityConfig` 里再手写 `requestMatchers`。
- `session.ts` 统一管理 localStorage / sessionStorage；会话清理两处都清，外观设置独立保留。
- `sessionVersion` 阻止旧菜单 / 用户请求写回；旧 token 的 401 不能清除新账号会话。
- 字典、部门缓存随账号切换清除；列表以请求序号避免晚到结果覆盖新查询。
- MySQL 时区以连接属性传递并强制到会话；不修改实例全局时区。`spring.jackson.time-zone`（`JSON_TIMEZONE`，默认 `GMT+8`）必须与该会话时区一致，否则 `java.util.Date` 按 UTC 序列化会让日期整体早 8 小时。
- 资源监测全部只读：JMX + Redis `INFO` / `PING` + Hikari `HikariPoolMXBean`（无需数据库权限）+ `SHOW GLOBAL STATUS|VARIABLES`（需 PROCESS 权限，受限账号下只有这一段降级）。利用率一律 `-1` 表示采不到，页面显示「—」而非 0%；macOS 的进程 CPU 恒取不到瞬时值，按两次采样的 CPU 时间差 / 墙钟差折算。
- 初始化 SQL 是新项目起点，不是旧库的自动升级迁移；`DbInit fix` 会删除整个目标库。
- 日志表、关联表、生成字段表不强制使用全部四个业务审计字段；`sys_file` 为保留表，当前没有上传业务接口。
- `composables/ask.ts` 是本地规则问答引擎：各模块注册 `intent{patterns, run}`，按 `hasPermission` 过滤不可用意图；命令面板输入 `?` 进入同一引擎。
- 视口平移缩放集中在 `composables/viewport.ts`（`useViewportPan`）：组织画布与关系图共用；`firstTimeInSession` 控制工作台开场等“每会话一次”的编排。
- 用户按岗位过滤走 `sys_user_post` 关联条件，分页、统计、导出一致生效并沿用 `@DataScope`。
- **平台侧新增的前端入口文件如果引用了被剔除的目录，要套 `scaffold:platform-only` 标记块**（PR 8A 踩到的）：`ScaffoldTrimPolicy` 的黑名单剔掉 `scaffold-vue/src/views/lowcode/`、`src/api/platform.ts`、`src/views/tool/` 这些平台专属目录，但 `src/router/index.ts` 是要进交付项目的 —— 往里加一条指向 `@/views/lowcode/**` 的静态路由，交付项目的路由表就留下一条点进去必然「页面不存在」的死路。`ScaffoldSnapshotPlanner.validate` 的残留引用扫描会当场拒绝（`裁剪清单遗漏：交付文件 … 仍引用 …`），连带 `ScaffoldSnapshotPlannerTest` 整类挂掉。**前端 typecheck、单测、`vite build` 全绿看不见这一条**，它只在交付快照初始化时跑；跑全量后端时把命令接上 `| tail` 会把退出码吞成 0，要重定向到文件再看 `$?`。正确做法是给那一段套标记（标记必须独占整行，`PlatformOnlyStripper` 写快照时整块删），而不是把入口文件塞进黑名单。
- **JGit 的 `resolve` 宽松、`findRef` 精确，双重 ref 名能骗过前一半**（PR 8 查到的既有缺陷，已修）：`InitCommand.setInitialBranch` 自己会补 `refs/heads/`，底座 `JGitGateway.initEmpty` 又把 `HEADS_PREFIX + branch` 传进去，实际建出 `refs/heads/refs/heads/{branch}`。为什么这么久没炸：`Repository.resolve("refs/heads/{branch}")` 会宽松补前缀，所以 `localBranchSha`、提交、推送、远端读数全对（PR 4/7 真机推上去的分支名是干净的）；而 `findRef` 是精确查的，`checkoutBranch` 那句「这条分支本地有了吗」就答错，回头去新建一条已经存在的分支。两条口径：**判断引用存在要用精确名查，别用宽松 resolve 的存在性当它存在**；同理十六进制提交号即使对象不在本地对象库也能 `resolve` 出来，要判「有没有这个对象」得查 `ObjectReader.has`。**已初始化的工作区里那条双层 ref 还在**（工作区是平台本机 clone、不含独有内容，重新初始化即恢复，故不做数据迁移）。断言按精确名 + 「不存在 `refs/heads/refs/` 开头的引用`」钉在 `JGitGatewayTest`，反向验证过：改回旧写法立刻红。
- **交付里要用的宿主文件，不许 import 物料包里还没发布的导出**（PR 8F 踩到的）：交付项目按 npm 版本号拿 `@yuanda-lowcode-ai/scaffold-ui`，**包源码里新增的文件与导出，在 `npm publish` 之前对交付项目不存在**。本地是 workspace 软链，`vue-tsc`、单测、`vite build` 全绿都看不出问题；交付快照里 `vite build` 才报 `Rollup failed to resolve import "@yuanda-lowcode-ai/scaffold-ui/composables/narrow"`。当时的现场：为了「窄屏断点单一来源」把 `AppShell.vue` 的 `matchMedia` 字面量换成包里的 `NARROW_QUERY` —— 而外壳是进交付快照的，设计器 `views/lowcode/**` 被裁剪清单整体剔掉，所以全仓只有这一条会挂。两条规矩：① **进交付的宿主文件只用已发布版本里确实存在的导出**（要核对就 `npm pack @yuanda-lowcode-ai/scaffold-ui@<版本>` 数文件，别凭包源码推断）；② 改了包内 API 且交付侧要用，必须**同步发版并抬 `scaffold.lowcode.scaffold-ui-version` / `ScaffoldTrimPolicy.DEFAULT_SCAFFOLD_UI_VERSION`**，否则交付构建挂。这类「包改了、版本没动」的错位只有物化演练能抓到，`ScaffoldSnapshotPlannerTest` 与本地构建都无感。
- **想要「整屏工作台」的页面别在 `.content` 里做**（PR 8A 顺带查到的事实）：`AppShell.vue` 在路由内容外面套了一层 `.route-frame`，而它只有 `min-width: 0`、高度是 auto —— 百分比高度在它身上算不出来，页面按内容自然长高，把 `.content` 撑成整页滚动，页面内部的分区滚动随之失效。真需要整屏就走独立静态路由（如 `/lowcode/designer/:页面 ID`）自己渲染 `.stage` + `.app-frame`；只是想在普通页面里做一块固定高度的区域，才用 `position: absolute; inset: 0` 贴住 `.content`（它是这条链上唯一 `position: relative` 的祖先）。

<!-- MANUAL-NOTES-START -->
## 十、人工维护备注

后续项目的业务规则、特殊权限和人工定位说明可补充在此区域。
<!-- MANUAL-NOTES-END -->

## 十一、待核实与开发边界

| 项目 | 当前事实 | 后续处理 |
| --- | --- | --- |
| 生成业务 CRUD | 模板出一份完整的业务模块（五个端点 + 列表页 + 建表与菜单 SQL），已在干净机器上 `docker compose up` 跑通增删改查（PR 5G） | 缺的是**发布入口**：`renderFiles` 目前只有老代码生成页（zip）与测试两个调用方，模型侧「生成 → 落工作区 → 提交推送」要等 PR 7 |
| 新业务数据权限 | 当前接入用户列表与导出，其他新增业务不会自动具备数据范围 | 按业务表的部门 / 创建人字段接入公共能力 |
| 文件业务与防重复提交 | 文件表已保留，统一上传及防重复提交能力未提供完整业务闭环；`Upload` 组件与 `composables/upload.ts` 已按统一鉴权就绪，只缺后端上传接口 | 有实际需求时扩展公共能力 |
| 资源监测的历史曲线 | 只回传单次快照，近 5 分钟折线由页面自身采样累积，离开页面即丢弃；不落库、不建表 | 需要跨重启的趋势与告警时再设计留存表与采集任务 |
| 模型没有版本历史 | 模型改动只体现在下次发布的 diff 里，库里不留版本（`lc_model.version` / `config` 本期不建） | 需要「回滚某个模型定义」时再设计，别和 `lc_page_version` 混为一谈 |
| 真机推送 | 已对本机 → GitHub 真实空仓库跑通：282 个文件推上 `platform-generated`（提交 `36c8021`），`sync_status=2`、`main` 未被写、回读远端一致；过程中修掉系统代理劫持与缺 BouncyCastle 两处 | 租户各带凭据、以及 deploy key 一把只绑一个仓库的限制（现在每台主机一把账号级 key 还是每仓库一把 deploy key）需要定口径，属 `lc_tenant_credential` 那一档的事 |
| 容器里的仓库凭据 | compose 已把 `~/.ssh`、工作区卷、Maven/npm 缓存与底座 checkout 挂进后端，流程写在 `docs/docker-deployment.md` 的「低代码平台的工作区与仓库凭据」一节 | 第一次上容器部署时按那节逐步跑一遍；私钥带口令或 `known_hosts` 缺指纹都会在初始化时如实报错 |
| 交付快照的「能起能登录」 | 已实测三次：PR 4 是 282 文件（含组件源码）离线 `mvn package` + `npm ci` + 建库起服务登录；PR 4B 抽包后是 **177 文件**，前端依赖换成 npm 包 `@yuanda-lowcode-ai/scaffold-ui`，用 `npm pack` 出的 tarball 走 `file:` 依赖装成功后 `vue-tsc` + `vite build` 通过；PR 8F 是 **180 文件**，改用 **registry 上真实发布的 1.0.0** 走 `npm install` 后 `vue-tsc` + `vite build` 通过，并做过负向对照 —— 把一处「交付文件 import 包里还没发布的导出」塞回快照副本，`vite build` 当场退出 1 报 resolve 失败，说明这条演练真守得住（该缺陷见第九节 PR 8F 那条） | 这条链依赖本机 Maven/npm 缓存；CI 里要长期守住就得把「干净 `npm install`」也放进去（方案 §九 已列为风险）。交付项目的前端**没有锁文件**，可复现性由 `@yuanda-lowcode-ai/scaffold-ui` 版本号承担；**版本号能承担的前提是「交付要用的包 API 已经发过版」** —— 本地 workspace 软链让这个前提在本地永远不成立，所以改包必须配一次物化演练 |
| 租户开通流程 | 自助注册与「租户管理里手工新增（填了管理员即开通）」两条路径共用 `TenantProvisionService`，菜单模板当前是**复制平台租户的业务菜单** | 菜单模板要成为可维护的一等资产时再建 `lc_menu_template`，避免长期靠「平台租户恰好配得对」 |
| 注册接口默认公开 | 任何能访问前端的人都能自助开出租户，只靠验证码与编码/账号唯一性挡 | 对外暴露前先决定是否加审核开关或白名单 |
| 拦截器覆盖不到的写入 | 关联中间表与显式跨租户写参数（`insertForTenant`）靠服务层校验与显式条件 | 后续新增无 `tenant_id` 的关联表时，必须同步加归属校验，不能只靠拦截器 |
| 业务排序字段白名单 | `PageQuery` 未提供 `orderByClause`，当前所有 Mapper 的 `ORDER BY` 都是静态列名，没有拼接入口 | 业务模块需要按前端传入字段动态排序时，再加白名单与驼峰转下划线，勿直接拼串 |
| 历史能力边界 | 早期版本包含当前已删除的建筑与工作流模块 | 历史变更见 `changelog.md`，当前范围查本地图与 README |
