# 提案：底座搬进 `packages/scaffold-app`，与平台目录级隔离

**合并说明**：本提案已并入原 `fix-base-upgrade-closure` 那份变更，那份目录随之删除。并进来的两块各归其位：**升级后半截的界面入口**成为第 0 刀（它不是重复项，而是本重构缺的一块 —— 第③刀的整链验收要在界面上生成合并请求，没有它就只能用 curl 跑，按项目口径不算交付完成）；**交付边界的平台片段校验**归入第⑤刀，它在那儿以"底座不得引用平台"的单向依赖机械检查形式存在，比原来的标注检查更准，那三条指纹作为它的 token 来源保留。

## Why

底座与平台今天不只同仓，还是**同一个进程**：`scaffold-admin` 依赖 `scaffold-lowcode` 起在一个 JVM 里，`scaffold-vue` 的动态路由挂着 `views/lowcode/**`。两者的边界只由 `ScaffoldTrimPolicy` 的白/黑名单、不到十个文件的 `scaffold:platform-only` 标记块、以及一份**字面量 token** 的残留扫描维持。

这套边界是三格问题的根源，其中一格会真出门：

- **格一（噪音）**：`base_scaffold_sha` 是整仓 HEAD，平台自己的每次 PR 都算"底座漂移"，`commitCountBetween` 的 `RevWalk` 又没有路径过滤器，于是界面上报「落后 109 个提交」而实际可下发的可能只有几个文件。
- **格二（会静默出门）**：平台专属但不含那些 token 的改动（按租户码走的分支、给 role 1 加的授权、平台菜单 id）会原样进 A 档、原样下发给所有租户。加测试只是补丁，目录隔离才是根治。
- **格三（推了没生效）**：`deliverySubstitutions` 六处「平台怎么构建 vs 交付项目怎么构建」的改写块，是"共用一套装配"换来的持续负担；改写块命中数 ≠ 1 就当场失败，是这套设计里最脆的一类闸门。

用户明确：现有项目仓库是**自测用的，可以直接删掉重建**，所以本提案不受存量兼容约束，按最优形态重构。

**命名决定**：底座目录定为 `packages/scaffold-app`，不用 `packages/scaffold-admin` —— 后者会与内层那个 Spring Boot 启动模块 `scaffold-admin` 同名，一个名字同时表示"整个底座（后端+前端+SQL+部署）"和"其中一个可执行模块"，读代码时尺度反复要猜。内层模块名保持不变（改 artifactId 会牵动 pom、Dockerfile 与交付路径，纯亏）。交付仓库里的目录名由映射表决定，因此外层叫什么与租户无关。

**当前状态**：底座住在仓库根；交付形状 = 根目录 + 裁剪清单；底座身份 = git sha；平台与底座同一进程同一份 app 壳。

**期望状态**：底座住在 `packages/scaffold-app/`，可独立启动、零平台依赖；交付形状由**显式映射表**决定，与底座内部怎么摆无关；底座身份 = 交付快照的**内容指纹**；平台是底座的**薄宿主**，只朝底座单向依赖。

## What Changes

- **⓪ 升级后半截的界面入口（原第一份提案并入，最先合）**：升级结果屏那句「下一步」旁、以及底座读数里状态为「待合并」那行，各挂一颗「生成合并请求」，带该条记录的 `upgradeBranch` 调已有的 `refresh`；复用现成的清单、行内差异与确认合并界面，不放开任意分支进审核队列。今天这两处只能传 `projectId`，后端按口径回落发布分支，所以升级分支在界面上变不成待审请求 —— 10G 那趟闭环是从接口侧跑通的。这一刀与目录无关，单独一条分支当天可合。
- **① 前端壳包化**：`scaffold-vue` 成为一个 workspace 包 `@yuanda-lowcode-ai/scaffold-base-app`，新增 `src/shell/index.ts` 暴露三样东西——路由贡献、菜单与权限来源、外壳具名插槽。平台页面改由宿主显式注册（`import.meta.glob` 只扫得到壳自己的目录，这是注入而非审美的技术理由）。做完这一步**零重复即成立**。
  - **实际拆成两段做（①a 已完成）**：①a 只做「贡献点机制」——`shell/views.ts` 视图注册表（不静默覆盖、冲突留清单）、`shell/routes.ts` 壳外路由申报、`shell/router.ts` 工厂、`shell/index.ts` 装配入口、`src/platform.ts` 平台申报 + 排除表补一行；目录一个没动。  - **①b 的真实内容（读代码后修正过，别照原计划做）**：外壳里确实有一处平台专属交互 —— `components/layout/Topbar.vue` 的用户菜单里 `switchTenant`、「（已切换）」标记与「回到平台租户」那一项，写在**交付文件**里，而它不含任何被 token 扫到的平台字样。所以 ①b = 新增 `shell/slots.ts`（「用户菜单项」组件注册表，Topbar `provide` 一个 `closeUserMenu` 给贡献方），把那几行搬成平台申报，并在快照用例里断言裁后的 `Topbar.vue` 不再含 `switchTenant`。
  - **原计划里否掉的两项**：① 「收掉 `AppShell.vue:34` 那条手写 820 副本」—— 那字面量是**有意为之**（外壳进交付快照，而交付项目装的是 npm 已发布的 scaffold-ui，未发版的导出解析不到），且 `proto.test.ts:159` 已把每一处手抄断点钉成同一个数，再做是重复投资；② 「让 `scaffold-vue/package.json` 正式成为 workspace 包（`name` + `exports`）」—— 现在没有消费方，属投机泛化，并入第③刀与宿主 app 一起落。
  - 因此**「把平台页面搬到 `scaffold-platform/vue/`」也并入第③刀**：那一步同时改变 dev 与容器构建的形态（平台跑宿主 app、交付跑底座 app），单独做会留下一个「半拆分且浏览器验不了」的中间态 —— 正是最容易把开发环境弄坏的位置。① 的边界到此为止：①a 交付机制，①b 交付 Topbar 那一处真实隔离。
- **② 后端装配下沉**：把 `SecurityConfig`、过滤器顺序、`@MapperScan` 清单、菜单种子从可执行模块拆成 `scaffold-admin-core` 库。原因是 Spring Boot repackage 出的 fat jar **不能被别的 Maven 模块依赖**，不拆就只有拷贝一条路。底座与平台各留一个薄启动模块，两个启动模块各列各的 `*.mapper` 包（写成包根会把服务接口注册成 Mapper，构造注入打成二义）。
- **③ 搬目录**：`packages/scaffold-app/` 收 `backend/`（Maven 多模块）、`vue/`、`sql/`、`deploy/` + 两份 Dockerfile + `docker-compose.yml`、`README.md`/`DESIGN.md`/`AGENTS.md`；平台侧集中到 `scaffold-platform/`（`lowcode/` 含 `.ftl` 模板、`admin/`、`vue/` 薄宿主、`sql/` 含 `verify/` 与平台 IT）。`packages/scaffold-ui` 不动。
- **④ 交付路径映射表**：新增「底座内部路径 → 交付路径」的显式表（`packages/scaffold-app/backend/** → scaffold-system/**`、`vue/** → scaffold-vue/**`、`sql/** → scaffold-system/sql/**`）。从此底座内部再怎么挪都不改交付仓库的形状，`GeneratedPaths` 的档位正则与菜单 `component` 契约不受影响。
- **⑤ 底座身份换成内容指纹**：`base_scaffold_digest` = 对交付快照（路径 + 最终内容规范化后）求哈希，替代整仓 git sha 进入 `lc_project`、`lc_base_upgrade` 与 tag 名 `yuanda/platform-base/{digest12}`；升级分支名同键换成 `platform-upgrade/{digest7}`。新增 `packages/scaffold-app/VERSION` 作为**给人看的语义版本号**，与 digest 并存：版本给人读，digest 做判定。落后量的口径从"提交数"改成"差异文件数"，取不到时仍返回 `null` 而不是 0。
- **⑥ 清单退化**：删 `EXCLUDED_PATHS` 里 V4~V13 十行平台 SQL、13 行平台 IT 与两个测试夹具点名、`deliverySubstitutions` 整块、SQL 里两对 `scaffold:platform-only` 标记块（`gen_table` DDL 与「系统工具/代码生成」菜单种子改由平台 SQL 持有）。**保留** `IGNORED_NAMES`（裁剪走文件系统遍历，`.gitignore` 对它不起作用）与 schema/code/brand 三份残留扫描作为拆错的兜底。
- **⑦ SQL 边界从文件级升到表级**：`lc_tenant` 建表留在底座（`AuthService.login()` 每次查租户是否停用，这是 PR 4 偏离 1 的既有决定，别在搬迁时顺手清掉）；`lc_tenant_config`、`gen_table*`、`lc_project`/`lc_model`/`lc_field`/`lc_page`/`lc_page_version`/`lc_publish_log`/`lc_merge_request`/`lc_base_upgrade` 与 400/500 段菜单归平台。菜单 id 段位约定写进两份 SQL 文件头注释。
- **⑧ 单向依赖机械检查**：底座目录内 `import com.scaffold.lowcode.` 出现次数必须为零；底座前端不得出现 `api/platform`、`views/lowcode`，`@/` 别名只指向壳自身目录。与既有残留扫描共用同一批 token，配负向对照。
- **⑨ 项目列表「底座落后」列 + 批量提醒**：digest 让这个提醒既准又便宜。批量的是**提醒**，下发仍逐项目发起、逐项目人审 —— 冲突格是项目各自的。

## Impact

### 受影响的规范

- `spec/specs/base-upgrade/spec.md`（新，并入自原第一份提案）- 升级分支经界面进合并请求、指引与动作同源。
- `spec/specs/app-shell/spec.md`（新）- 壳的贡献点与宿主注册契约。
- `spec/specs/delivery-shape/spec.md`（新）- 交付路径映射、清单退化后的归属规则、平台片段必须显式标注（含那三条指纹）与单向依赖检查。
- `spec/specs/base-identity/spec.md`（新）- digest 身份、版本展示、落后判定与读数口径。
- 基线仍不存在，差异一律 `ADDED`；权威旧文本在 `docs/lowcode-platform-proposal.md` §5.2、§6.4、§10.4 与 `PROJECT_MAP.md` 第九节，验收后需回写。

### 受影响的代码

- 前端：`scaffold-vue/src/shell/`（新）、`main.ts`、`router/index.ts`、`components/layout/**`（开插槽）、根 `package.json` workspaces、`scaffold-platform/vue/`（薄宿主）。
- 后端：`scaffold-admin-core`（新模块）、`scaffold-platform/admin/`、`scaffold-lowcode` 移到 `scaffold-platform/lowcode/`、`ScaffoldSourceLocator`（判据与根路径）、`ScaffoldTrimPolicy`（瘦身 + 映射表）、`ScaffoldSnapshotPlanner`（digest 计算）、`PlatformBaseTag`/`BaseUpgrade`（键换成 digest）、`BaseUpgradeService.status()`（落后量口径）、`GeneratedPaths`（不受影响，映射表负责兼容）。
- 测试：`ScaffoldSnapshotPlannerTest`、`JGitGatewayTest`、`MergeRequestApiTest`、`ProjectWorkspaceApiTest`、`DeliveryGenerationApiTest`、**`ScaffoldTenantLineHandlerTest` 必须改成同时扫底座与平台两处 SQL 目录**（只扫一处则平台新表漏登记＝跨租户泄露而测试看不见）。

### 用户影响

- 租户侧交付仓库的**形状与内容不变**（这正是映射表的目的）。
- 平台开发者：改底座要在 `packages/scaffold-app/` 里改，改平台在 `scaffold-platform/`；误把平台语义写进底座会被机械检查当场拒。

### API 变更

- 非破坏性但字段改名：`baseScaffoldSha` → `baseScaffoldDigest`（`base-status`、`base-upgrade`、`lc_project`、`lc_base_upgrade` 与前端类型同步），新增 `baseVersion`（读 `VERSION`）。

### 需要迁移

- [ ] 数据库迁移：仅需 `ALTER TABLE ... RENAME COLUMN`（或直接删自测库重建 —— 用户已授权按最优做）
- [ ] API 版本提升：无
- [x] 用户沟通：`AGENTS.md` 里「packages/scaffold-ui 的包内纪律」要升级为整个底座目录的纪律
- [x] 文档更新：`changelog.md`、`PROJECT_MAP.md`（§九 特殊机制备忘的裁剪条目 + 第十一节）、`docs/lowcode-platform-proposal.md` §5.2/§6.4/§10.4、`docs/docker-deployment.md`（`LOWCODE_SCAFFOLD_SOURCE_ROOT` 默认值与挂载路径）

## 时间线评估

中～大，分五刀串行：**⓪ 界面入口（0.5 天，单独一条分支最先合，后面每一刀的整链验收都靠它）**→ ① 壳包化（1–2 天，风险全在编译期与 `vite build`，当天可回滚）→ ② admin-core 拆分（0.5–1 天）→ ③ 搬目录 + 映射表 + 清单退化 + digest + 机械检查（1–2 天，同刀做，之后删测试仓库重建）→ ④ 落后列与批量提醒（0.5 天）。

## 风险

- **格二的泄露窗口在本提案落地前仍然开着**：原第一份提案里那道"命中平台指纹而未标注就当场拒绝"的快照校验，现在排在第⑤刀（机械检查）与第③刀（目录隔离）之后 —— 在旧结构上这条检查立不起来（`views/lowcode` 与壳同在一个 `scaffold-vue` 里，一上来就红）。如果 ⓪ 之后、③ 之前还要发几版底座，就要评估是否把那三条指纹先做成**只报不改**的告警用出来，而不是等到第⑤刀一次性收紧。缓解：⓪ 合完就停下来判一次，看这期间底座改动里有没有落进 A 档文件的平台语义。

- **包化时 glob 与 alias 的行为变化**：动态路由靠 `import.meta.glob` 扫页面，扫不到宿主贡献的目录；vite alias 与 `@/` 解析在跨包后含义会变。缓解：① 刀先只做壳与贡献点、不动目录位置，`vite build` + `vue-tsc` + 真浏览器点一次登录与菜单跳转作为验收。
- **admin-core 拆分的隐性装配**：过滤器顺序、`@MapperScan` 包清单、`PlatformBoundary` 这类判定，两处各写一遍就会漂。缓解：装配只住在 core，两个启动模块不许重复声明；`@MapperScan` 各列各的 `*.mapper` 包并有测试钉住。
- **Dockerfile 构建上下文与 compose 挂载路径**：`scaffold-vue/Dockerfile`、后端 Dockerfile 的 context 都写的是仓库根相对路径，搬迁后必须一起改，`LOWCODE_SCAFFOLD_SOURCE_ROOT` 默认值 `/var/lib/yuanda/scaffold-src` 的挂载来源也要跟着指到新根。缓解：`docs/docker-deployment.md` 的流程按新路径重跑一遍，不许只改代码不跑容器。
- **digest 的稳定性**：路径顺序、行尾、末尾换行都会改变指纹，指纹不稳就会"每次看起来都像有更新"。缓解：规范化规则（排序 + UTF-8 + 统一换行）单独一条单元用例，并断言同一 checkout 连续两次计算得到同一个 digest、改一个文件字节则必变。
- **镜像与 checkout 成对那条不变式换了表现形式**：裁剪清单在后端代码里、底座源码在 checkout 里，版本错开就是"按旧清单裁新源码"。digest 不能自动发现错配。缓解：`base-status` 同时回 `baseVersion` + digest + 后端清单版本三者，任一不一致就在界面上说出来。
- **交付内容意外变化**：清单退化时最容易多删或漏删。缓解：搬迁前后各跑一次 `ScaffoldSnapshotPlanner.plan` 对同一逻辑底座做**逐文件字节比对**，差异必须能逐条解释；再按 AGENTS.md 的交付链口径物化演练（真 `npm install` + `vite build` + `javac`），并给新机械检查做负向对照（故意往底座塞一条 `com.scaffold.lowcode` import，确认当场红）。
- 仍不做的取舍：不拆独立仓库（会把"底座侧不需要任何凭据"这条好性质换成新增一类网络与凭据故障）；不让租户依赖已发布的 shell 包（等于收走改登录页/布局/路由的能力，是产品边界不是技术选择）；不做交付侧 SQL/菜单补齐（菜单集合已定为底座内部实现、不算对外契约）。
