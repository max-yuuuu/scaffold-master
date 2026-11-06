# Changelog

## 2026-11-06（PR 8 第二刀：开发提交 PR → 负责人审核 → 确认合并，文件对比做到行内）


用户看完第一刀的页面后改了流程：「开发在模块完成后提交 PR，这时不能自动合并；项目负责人看到需要他审的 PR，点合并再合过去」，另外「文件对比不要只列文件名，要做到 GitHub 那样能看到具体差异」。两条都进了这一刀。

- **负责人 = `lc_project.owner_user_id`（一个用户，不是角色）**：需求原话是单数，而「待我审核」要有按人过滤的落点。用角色不会因多人不在岗卡住，但队列得先算当前用户的角色集合、换人靠改角色分配而不是改项目 —— 那是另一套权限语言，本期不引。**合并权限是两层**：权限点 `lowcode:merge-request:merge` 回答「这类动作允许不允许」，服务里的负责人判定回答「这个人是不是该为这条主干负责」；后者用 `countPlatformSuperAdmin`（角色码 `super_admin` **且**账号归属平台租户）复刻 `PlatformBoundary.isSuperAdmin` 的语义 —— 低代码模块没有 authMapper，而租户内自建一个同名角色不算数，否则任何租户管理员都能把自己项目的清单推进主干。
- **确认合并走 `mergeIntoTarget`，全程在一份临时 clone 里做，平台工作区一条引用都不动**：工作区的 HEAD 属于发布分支，把它切到主干再合并，下一次发布就可能把生成物提交到主干上 —— 那是把两条链路的现场搅在一起。三道拒绝（主干已前进 / 真冲突 / 非快进被远端拒）全在动手之前，`NO_FF` 固定产生合并提交，让「这次审核放行了什么」在历史里是一个有两个父亲、可指的提交。推送后回读远端确认，回读不上就抛错，绝不记成已合并。
- **合并前的复检顺序有讲究**：先判请求本身还作不作数（状态、预检结论），再判点的人有没有资格，最后才去问仓库 —— 反过来做，一个越权的点击会先把远端读一遍，等于让没资格的人触发网络动作。
- **`updateMerged` 带 `WHERE status = 0`**：两个人同时点合并时，第二个人必须发现这条已经收口，而不是把 `merged_by` 改写成自己、把两次合并说成一件事。
- **行内 diff 按文件现算**：`fileDiff(dir, base, head, path)` 用这条请求自己的 `merge_base_sha..source_sha` 算 —— 看的是「当时算出来的那一对提交之间的差异」，不是分支现在的样子。一次只拉一个文件：几百个文件的请求整份补丁能有几 MB，把「点开看一眼」变成一次下载就没法用了。前端按前缀分行着色（`@@` / `---` / `+++` 各占自己的类，否则一个文件头会被标成整行删除），颜色取底座语义色那一档（`--lime-soft`/`--leaf-text`、`--red-soft`/`--red-text`）。
- **审查页加了「待我审核」开关**（按负责人过滤，不是权限裁剪 —— 不限负责人时仍然看得到全部），详情抽屉底部是「确认合并」，不可点时 `title` 直接写清为什么（「主干已经前进了…先重算再合」/「有冲突的文件必须先由人解掉」）。二次确认文案说明白这是平台唯一会写人工主干的动作、推上去只能靠一笔反向提交回退。
- **项目表单与列表加了负责人**（复用 `UserSelect`，`value-key="id"`），导出 CSV 多一列「负责人」。后端拒一个查不到/已停用/跨租户的用户 ID —— 指错人就等于把这条项目锁死在没人能合的状态。

**过程中被工具救回或自己写错的几处**：`TenantContext` 在 `com.scaffold.common.tenant` 不在 framework（编译报错抓住）；服务里的助手叫 `abbreviate` 我写成 `abbrev`；`confirm()` 的参数是 `danger` 不是 `tone`；四处 CSS 令牌（`--surface-2`/`--green`/`--green-soft`/`--blue-muted`）**根本不存在** —— 这一页不在设计器目录下，`proto.test.ts` 那条令牌扫描覆盖不到它，未定义 `var()` 只会静默丢声明，所以逐个拿 `tokens.css` 核过才敢留。另外两次编辑把相邻代码挤出过（`workspacePath` 一行、`close()` 半个方法体），都在下一次读文件时看见并补回，记在这里免得再犯。

**测试与 e2e**：`MergeRequestApiTest` 6 → 11 例（负责人合并成功并落到主干内容 / 预检冲突被拒且主干未动 / 主干又前进被拒 / 按文件取到真实补丁且未改状态 / `onlyMine` 按负责人过滤），本机裸仓库真跑；`JGitGatewayTest` 15 → 19 例（按文件 diff、真合并推送、陈旧拒绝、冲突拒绝）；前端 `vite build` + `vue-tsc` + 43 例全绿。**如实记一条没覆盖到的**：「非负责人且非超管点合并被拒」这条负向分支只有代码与权限点，没有用例 —— 它需要一个本租户内、持有 `lowcode:merge-request:merge` 但不是项目负责人的账号，现成夹具里没有，另开一套角色+授权装配的代价这轮不付。**也没做真机推主干**：用户对真机安排选了「无偏好」，我按安全侧走本机裸仓库，往他真实仓库 `main` 的第一次合并留给他自己在界面上点。底座标记 tag 在合并后重打那一步属于「底座升级」的语义，本刀没做，已记进待办。

- **用户实测两处不顺手，都改了**：① 提交 `46cebb6` —— `projects.vue` 的 `submit()` 是逐字段手写的白名单，我加表单控件时把 `ownerUserId` 漏在 payload 外，于是「选了负责人 → 保存 → 提示成功 → 值没变」，一层错都不报；教训写进代码注释（白名单式组装请求体是「加字段必漏字段」的形状）。② 抽屉原来是「清单 → 点文件名 → 整屏换成单个 diff → 返回清单」，看下一个文件要来回三跳；按用户要求改成**风琴**：文件行就地展开自己的行内差异、一次只开一个、差异按路径缓存（收起再展开不重拉），行头用 `position: sticky` 各自钉在抽屉体顶部（下一个文件的行头滑上来把它顶掉 —— 这是 sticky 相对父块的自然行为，与 GitHub 文件区同一套，不需要 JS 算位置），「确认合并」从清单尾巴挪进 `Drawer` 的 `#footer` 常驻，滚到哪都能点。
- 样式那轮又抓出两个我凭空写的令牌：`--surface-2`/`--green`/`--green-soft`/`--blue-muted` 与 `--ease` **都不存在**（底座动效一律内联 `cubic-bezier(.32,.72,0,1)`，语义色是 `--lime-soft`/`--leaf-text`/`--red-soft`/`--red-text`）。这一页不在设计器目录下，`proto.test.ts` 那条令牌扫描覆盖不到，而未定义 `var()` 是静默丢声明 —— 所以现在是逐个拿 `tokens.css` 核过才敢留。**没做视觉验证**：这台机器拿不到可见浏览器，风琴展开的高度、sticky 行头与抽屉头部的贴合、`--surface-solid` 在深色主题下会不会透出色，都得你刷新页面看。
- **用户截图指出「吸顶那块被上面的内容遮住了」，查出两层，都在 CSS 而不是 JS**：① `.mr-files` 为了圆角写了 `overflow: hidden` —— 这一句把它自己变成了一层**不滚动的滚动容器**，行头的 sticky 就以它为参照，参照系不滚动等于行头钉不起来，滚上去就整条滑出可视区，看着正是「被遮住」。改成 `overflow: clip`：只裁圆角、不建立滚动容器，滚动参照仍是 `.drawer-body`。② 钉起来之后 Chrome 把 sticky 停在滚动容器的**内边距下沿**，`top: 0` 让行头落在抽屉体 14px 上内边距下面，差异行从这条缝里穿过 —— 改 `top: -14px` 让它贴住滚动框上沿（包里的窄屏媒体查询把这条内边距改成 12px，多出的 2px 由滚动容器自己裁掉，不留缝也不盖别的内容）。顺手把 hover 从 `background: var(--hover-bg)` 换成同色的背景图层：`--hover-bg` 是半透明的，直接替换会击穿这层不透明底，钉住时下面滑过的差异行会透上来。
  **这次不是靠肉眼**：拿物料包里 `.drawer / .drawer-head / .drawer-body` 三条真实声明（`overflow-y: auto` + `padding: 14px 22px 22px`）在浏览器里搭了一层同构夹具，滚动 200px 后量行头的 `getBoundingClientRect()` —— `overflow: hidden` 那版是 **−111px**（滑出可视区，等于没钉），`clip + top:0` 是 **+14px**（缝），`clip + top:-14px` 是 **0**（贴住上沿）。抽屉本身仍然没在真机上看过：这台机器的浏览器停在登录页，我没有你的账号密码。
- **清单行头改固定列（用户：文件名、状态、删减行数要宽度对齐）**：原来是一条 `display: flex`，尾部按内容长 —— 「要人解」只有冲突行才有，它的有无让后面的状态标签与两个数字逐行左右跳；数字位数不同（`+167` 与 `+9`）也让个位对不上。改成 `grid-template-columns: 12px minmax(0,1fr) 70px 74px 46px 46px`：文件名吃余量（超长走省略号），冲突标记、状态、增删数各占一列，数字右对齐并靠底座 `.tnum` 取等宽数位。改名行的旧路径从独立 flex 项挪进文件名那一格，免得它自己占一列把余量挤走。**量过**：同构夹具放四行（冲突+MODIFY+三位数 / ADD+一位数 / RENAME+旧路径+四位数 / DELETE），四行的列左沿读数完全一致（30 / 390 / 468 / 550 / 604），两个数字右沿锁在 596 与 650；「要人解」标签右沿 455 落在 70px 列里留 5px 余量 —— 先按 66px 配过，量出来只剩 1px，字体一宽就顶进下一列，所以抬到 70。

## 2026-11-06（PR 8 第二刀段：合并请求的算账、接口与审查页 —— 只读那一半收口）

上一段只有表与 Git 原语。这一段把它们接成人能看的东西：三个入口、一份清单、一次预检、一条陈旧判定。**没有批准也没有合并按钮** —— 确认合并要在「人确实看过清单」这件事成立之后再加，两刀混着做反而各自都难验（陈旧那条验收只取决于三个提交号对不对，与合不合根本无关）。

- **`refresh` 的链路**：fetch → 读两侧远端提交 → `mergeBase` → `diffFiles` → `checkMerge` → 落两张表。四道现场守卫拦的都是「编得过、结论是假的」那一类：工作区脏就不预检（三路合并按工作区当前状态判 dirty，脏着算出来的冲突清单不可信）；远端缺发布分支 / 缺主干 / 两条历史无共同祖先各说一句人接下来能做的事，其中**平台不替你建主干**是刻意的 —— 那等于替你决定仓库的起点。
- **清单按 `merge-base..源` 算**。按 `主干..源` 算会把主干上别人新提交的内容反向显示成「发布分支要删掉它」。用例里主干有人的改动时，清单只许出现发布分支那个新文件 —— 这条是端到端跑出来的，不是推出来的。
- **状态与预检分两列，文案只在一处**：`statusText` / `checkStatusText` 由后端 `MergeRequestState.text()` 给，接口直接带。先写红用例（`expected: "待审" but was: "null"`）再实现；让前端拿数字拼词，改一个状态词就要同时动前后端。列表返回 `PageResult`（统一分页模型），不是裸数组 —— 裸数组喂不进 `ListPage`，也破坏了底座那条分页口径。
- **同一分支对至多一条活着的请求**：重算就地覆盖，文件清单**整批替换**。逐行 upsert 会留下「上次有这次没有」的孤儿行，而那种行在页面上读起来像「这次要删掉这个文件」。请求行与清单同事务（`MergeRequestLedger` 单独成 bean，理由与 `PublishLedger` 一样：自调用不穿代理，`@Transactional` 等于没写）。
- **详情打开时重读一次远端判陈旧，列表不读**：一屏几十条各打一次 `ls-remote` 会把「看一眼列表」变成网络风暴，那条结论也就没人愿意等。陈旧只改状态与说明，那三个提交号保持原样 —— 它们记的是「这条请求算的是哪一对提交」，改了这句话就不成立。
- **审查页**复用 `ListPage` + `useListPage` + `DataTable` + `Drawer` + `PermissionButton`，没有自拼布局。筛选只留「归属项目」一条：标题是平台按分支与文件数生成的，对它做关键字过滤没有读者，**与其摆一个后端不认的输入框，不如不放**。
- **两处「新增文件要同刀补登记」都漏了，都是现场报错才抓住的**：`ScaffoldAdminApplication` 的 `@MapperScan` 要逐个列 `*.mapper` 包（注释里就写着不能写包根），漏了 → 上下文起不来 `No qualifying bean of type LcMergeRequestMapper`；新用例引用 `/api/lowcode` 要进 `ScaffoldTrimPolicy` 排除表，漏了 → 交付快照初始化当场被残留引用扫描拒绝。这两条注释里都写着，仍然各漏一次 —— 说明它们该出现在提交清单而不是注释里。
- **`vue-tsc` 又救了一次 `vite build` 没救的**：页面里 `pushToast(msg)` 少一个参数、`toastError(err)` 少了兜底文案、语气名我按别家组件库写成了 `'warn'` / `'success'`（底座只有 `ok` / `info` / `danger`）。四条都在 typecheck 报出来，构建那一步是全绿的。
- **两个错误前提被用例纠正**：`BusinessException` 映射的是 **400** 不是 500；而「先 seed main 再初始化」**不是**「无共同祖先」—— 那时平台是从已有历史 clone 出去的，gen 是 main 的后代，第一次刷新本来就该报出整个快照的 180 个文件（这是对的）。要造无祖先就得显式起一条独立历史，用例现在这么写。

**e2e 实际跑了什么**：`MergeRequestApiTest` 6 例（真 JGit + 本机裸仓库 + 真接口 + 真 IT 库）：无共同祖先、远端缺主干、清单只含源侧改动（含列表那两个数与文案）、两边改同一行报冲突且只列文件、主干前进后详情转陈旧、重算两次覆盖同一条。前端 `vite build` + `vue-tsc` + 43 例（含新的 URL 契约用例：`refresh` 必须是 POST 且只带 `projectId`）；物料包 16 例。IT 库需要应用 V10（`docker exec ... scaffold_system < V10__lowcode_merge_request.sql`，脚本幂等），已应用并核对到两张表与两条权限码。


- **文案与真机补记（同一 PR 内跟着改）**：动作按钮原叫「重算所选项目」，用户反馈「选中项目后没看到有请求」—— 那一步是**筛选**，不是发起，而发起长在了一个听起来像「已有东西才能刷新」的名字上。改成「生成合并请求」，并把「这一页没有单独的新建：算一次就是发起，再点一次是就地刷新同一条」写进页面 `desc`。顺手把这条链在**真实租户仓库**上跑了一遍（此前只有本机裸仓库的端到端）：项目 `order-serive` 一次生成 8.7 秒、`platform-generated → main` 清单 167 个文件、预检「可自动合并 / 冲突 0」、`merge_base_sha == target_sha`（主干还停在分叉点上，是首次合流的正常形状）。JGit 走 SSH 直连没被本机代理劫持 —— PR 4 那条 `directSshProxy()` 在这里兑现了。

## 2026-11-06（PR 8 第一段：合并请求的两张表与四条只读 Git 原语，顺带修掉一条藏了很久的双层 ref 名）


用户选的口径是「只读那一半：建表 + diff + 预检」，确认合并与推送留到下一刀。三段提交：`V10 建表` / `网关三条原语 + initEmpty 修复` / `fetch`。

- **V10 两张表的取舍写在建表注释里**（照 V9 的规矩）：`lc_merge_request` 记 `source_sha` / `target_sha` / `merge_base_sha` 三个提交号，`status`（待审 / 陈旧 / 已合并 / 已关闭）与 `check_status`（未预检 / 可自动合 / 有冲突）**分两列** —— 目标分支前进不改变内容是否冲突，有冲突也不表示请求过期，合成一列要么互相覆盖要么出现没人能显示的枚举值。`target_sha` 存下来而不是只存一个陈旧布尔值，因为列表要能说「主干已经前进到 abc123，这条请求比它旧」。三样故意不存：文件数与冲突数由文件表现算（同 V9 挡掉 `files_added/modified/deleted` 的理由）、unified diff 不入库（两个提交号已把范围钉死，详情页按需现算）、审批人与意见不建（这刀没有「批准」这个动作）。
- **租户登记与裁剪清单同刀补**：先只建表跑出 `ScaffoldTenantLineHandlerTest` 的红（`lc_merge_request 在 V10 里带 tenant_id，必须登记进白名单`）再补 `TENANT_TABLES`；`ScaffoldSnapshotPlannerTest` 整类 12 条挂过 `裁剪清单遗漏：交付文件 V10 仍引用 lc_project` —— 这就是 AGENTS.md 那条老规矩的形状，前端与单测对此完全无感，只有交付快照的残留扫描能看见。
- **四条只读原语**（`GitGateway` + `JGitGateway`）：`mergeBase` 走 `RevFilter.MERGE_BASE`；`diffFiles` 走 `DiffFormatter` + 改名识别，档位对齐 `change_type`（JGit 7 那一档叫 `ADD`，旧版叫 `INSERT`，写错直接编译不过），行级增删数按单文件 unified diff 现算；`checkMerge` 用 `RECURSIVE` 的 in-core merger 再配一份 `DirCache.newInCore()`，两份保险确保真实 `.git/index` 不被一次预检写脏；`fetch` 带回 `refs/remotes/origin/*` 并清掉远端已删的引用，**不动本地分支与工作站**（用例钉 `localBranchSha` 不变 + `isDirty` 为 false）。
- **修掉一条真缺陷**：`initEmpty` 把分支名套了两层（`setInitialBranch` 自己补 `refs/heads/`，调用处又传了 `HEADS_PREFIX + branch`），实际建出 `refs/heads/refs/heads/{branch}`。之所以一直没暴露：`resolve` 会宽松补前缀，本地读数、提交、推送、远端全都对（PR 4/7 真机推上去的分支名是干净的），而 `findRef` 是精确查的 —— `checkoutBranch` 那句「这条分支本地有了吗」答错，会去新建一条已经存在的分支。断言按精确名钉住并做反向验证：改回旧写法立刻红（1 failure + 2 errors）。已初始化工作区里那条双层 ref 不做迁移（工作区是平台本机 clone，不含独有内容，重新初始化即恢复），这条与理由一起写进 `PROJECT_MAP.md` 第九节。
- **两条只有踩过才知道的细节**：十六进制提交号哪怕对象不在本地对象库也能被 `resolve` 出来，不查 `ObjectReader.has` 就会炸成 JGit 的 `Missing unknown <sha>`，而「提交还没 fetch 回来」是正常状态不是错误；`ResolveMerger.getFailingPaths()` 在没有失败项时返回 **null 而不是空 map**，直接 `.entrySet()` 就 NPE。
- **图标**：菜单图标 `branch` 在注册表里原本不存在（`Icon.vue` 查不到会静默画 `file`），补进 `ICON_PATHS` 与 `MENU_ICON_OPTIONS`，并新增 `packages/scaffold-ui/src/__tests__/icons.test.ts` 钉「选择器列出的每个名字都能解析」+「路径不以绘图命令开头的算空」；反向验证过：抽掉 `branch` 路径当场红，报错原话是「静默兜底成 file：branch」。包单测 14 → 16 例。
- **桩的口径**：`StubGitGateway` 三条只读原语显式抛 `UnsupportedOperationException` 而不是返回空清单（用例走错门要当场喊，给它一份「看起来像没有变更」的空清单最坏），`fetch` 记进 `fetches` 供断言；`LocalBareGitGateway` 直接转给真 JGit。
- **e2e 实际跑了什么**：`JGitGatewayTest` 11 → 15 例（本机裸仓库真跑，新四条先跑红再实现）；`scaffold-lowcode` 全模块 147 例全绿；依赖这两个桩的集成用例 `ProjectWorkspaceApiTest` 15 + `PagePublishApiTest` 9 + `DeliveryGenerationApiTest` 5 全绿（IT 库 `DB_PORT=3316 REDIS_PORT=6380`，`JWT_SECRET` 现生成）；V10 在临时库 `scaffold_v10_rehearsal` 真跑 V1–V10 一遍 + V10 再跑一遍，两次 rc=0，核对到 2 条菜单行、2 条角色授权、2 张新表、默认值与 `UNIQUE(request_id,path)`，跑完 `DROP DATABASE`，没动正式 IT 库。
- **还没做的**（如实留着）：`MergeRequestService`、接口与前端审查页、把「diff + 预检 + 陈旧」串起来的端到端用例，以及确认合并与推送那一半。**这一段没有对外接口，交付面只有 V10 被裁掉这一点变化，无需重跑物化演练。**


## 2026-11-05（PR 8C 补刀：复制出来的那一格根本存不出去）

e2e 阶段查出来的缺陷，不是用户报的。对着后端读 `duplicate()` 的产物时发现的：副本 `props` 整份照抄，`field` 也一起抄了。

- **后端是权威判定方，它不允许**：`PageDesign.validateChildren`（`scaffold-lowcode/.../design/PageDesign.java:287`）对同一个区块里绑两遍同一个字段直接抛 `BusinessException`，`重复绑定 <code>` —— 表单类型会重复声明一个成员（交付项目 `vue-tsc` 当场挂），查询区则是一个条件出现两个框。所以「复制一个绑了字段的控件」这一步按下去之后，画布看着正常、撤销也能撤，但**这一页从此存不出去**，人要等点保存才收到一条 400。这条规则后端早有 `PageDesignTest:208-214` 钉着（「表单里同字段两遍：生成的 TS 类型重复声明一个成员」），是我这边没接住。
- **改法：副本换绑，换不出就当场拒绝。** `duplicate(key, fields)` 现在收模型字段表（唯一调用点 `LayerTree.vue` 传 `session.fields`）：副本沿用原控件类型，`field` 换到**整页还没绑过、且 `controlFor(field, inSearch)` 派生出同一种控件**的那个字段，`label` 跟着新字段的名字走，`queryType` 也跟着新字段走（把 `orderNo` 的 `LIKE` 抄到 `invoiceNo` 上，等于拿 `LIKE` 去查一个模型里配成 `=` 的列）；其余属性照抄。类型必须一起匹配是因为 `validateControlType` 也在那条链上 —— 把日期字段塞进 `TextInput` 一样被拒。换不出候选就抛错说明原因（「回模型加字段，或直接从物料拖一个新的」），不产一份存不下来的设计。
- **拒绝要发生在 `snapshot()` 之前**：`rebindFor()` 在压历史之前算完，被拒的这一次既不进树也不占撤销栈的一格。这条一开始用 `canUndo === false` 断，测出来是 `true` —— 前面 `setModel()` 自己占过一格；改成按「一次撤销撤掉的是哪一步」判：`store.undo()` 后 `modelRef()` 必须回到空串，说明撤销落在真做过的那一步上，而不是落在一个没发生的动作上。
- **顺手把 `boundFields()` 提成模块函数**：`referencedFields()` 里原来是一份就地写的 walk，现在两处共用（面板读数、复制换绑），少一份重复遍历。
- **e2e 实际跑了什么**：① 前端模型真产两份 `design_json`（换绑的副本 / 把副本 `field` 手动改回 `orderNo` 的旧行为），用一个临时 JUnit 喂给 `PageDesign.parse` + 真实 `DesignContext` —— 前者 `accepted`、后者 `rejected` 并报 `重复绑定 orderNo`，两个方向都验到，临时用例跑完即删未进提交；② `PageApiTest` 真起 Spring 上下文打接口（`DB_PORT=3316 REDIS_PORT=6380`，`JWT_SECRET` 现生成，验证码由 surefire 关掉）6 例全绿；③ `scaffold-lowcode` 全模块 142 例全绿（含 `PageDesignTest` 18 例）；④ 前端 `vite build` → `vue-tsc` → 单测 42 例（40 → 42，两条新用例先跑红：`照抄绑定复制出来的这一格根本存不下去` / `Missing expected exception`）。


## 2026-11-05（PR 8F：窄屏两栏改抽屉，交付演练抓到一处「本地全绿、交付构建挂」）

**窄屏判定收成一处。** 新增 `packages/scaffold-ui/src/composables/narrow.ts`：`NARROW_QUERY`（820px，与 `layout.css` 那条 `@media` 同一个数）、`isNarrow()`（`view.ts` 的「窄屏首次进入用 narrow 默认视图」改用它，不再自己抄一遍字符串）、`useNarrowScreen()`（响应式，`onScopeDispose` 摘监听）。设计器两条实现（1:1 原型与 `?mode=live`）在 820 以下把左右两栏从 grid 列里拿出来，改成盖在画布上的抽屉，一次只开一扇，配遮罩与 Esc 关闭，预览态不参与。实测 627px 宽：原型画布从被压成 **0px** 恢复到 **591px**，整页横向溢出 0，顶栏动作全部可达（「发布上线」在 x=611）；宽表格不再顶开页面，改在 `.pf-table-wrap` 内滚动（内容 955 vs 容器 477）。

**交付演练抓到的那件事才是这一刀的收获。** 按 AGENTS.md 的规矩把交付快照物化后真装真建：物化 180 文件 → 在快照里 `npm install`（装到 registry 上真实发布的 `@yuanda-lowcode-ai/scaffold-ui@1.0.0`）→ `vue-tsc` + `vite build`。第一趟就挂：

- 我为了「断点单一来源」把平台外壳 `AppShell.vue` 的 `window.matchMedia('(max-width: 820px)')` 改成 `import { NARROW_QUERY } from '@yuanda-lowcode-ai/scaffold-ui/composables/narrow'`。**本地两包 typecheck、40 例单测、`vite build` 全绿**（workspace 直接软链包源码），交付快照里却报 `Rollup failed to resolve import "@yuanda-lowcode-ai/scaffold-ui/composables/narrow"`。
- 根因不在代码而在交付形状：**交付项目按 npm 版本号拿物料包，包源码里新增的导出在发版之前对交付项目根本不存在**。而 `AppShell.vue` 是要进交付快照的平台外壳，设计器 `views/lowcode/**` 整体被裁剪清单剔掉 —— 所以全仓唯一会因此挂掉的就是外壳这一条。为核实这件事把已发布的包 `npm pack` 下来数了：108 个文件，没有 `narrow.ts`、没有 `unsavedGuard.ts`，`IconButton.vue` 里也没有 `hint` / `aria-pressed`。本 PR 给包加的 API 全部只被裁掉的设计器用到，交付面因此无感。
- **取舍**：外壳那条退回写死字面量并留注释说明原因，不做「顺手改成 import」这种会把交付弄挂的重构。断点手写副本现在有四份（`narrow.ts` / `AppShell.vue` / `layout.css` / `proto.css` + `DesignerTopbar.vue`），由新增的 `proto.test.ts` 一条用例钉住同一个 820 —— 反向验证过：把 `proto.css` 或 `AppShell.vue` 改成 821 立刻红。**真正该守的是「改了包内 API 的宿主文件如果要进交付，必须同步发版并抬 `scaffold.lowcode.scaffold-ui-version`」，这条写进 `PROJECT_MAP.md` 的踩坑清单。**
- 演练的可复现性与「这条闸门真的守得住」：落盘用一个临时 `TempSnapshotMaterializeTest`（`-Dscaffold.rehearsal.dir` 驱动，跑完即删，没进提交）；负向对照是把坏 import 塞回**快照副本**，`vite build` 退出 1 报上面那条 resolve 错误，还原后退出 0 —— 不是「绿色只是因为没人碰过」。

**还没验到的**：`prefers-reduced-motion` 只按 CSSOM 确认了平台 `base.css` 里那条 `*, *::before, *::after { animation-duration / transition-duration … !important }` 覆盖存在，没有真的模拟该 media feature；浅色 / 深色 / 窄屏 / 键盘可达都量过实数。

**测试与构建**：前端单测 39 → 40 例（新增断点一致性，先因路径写错 ENOENT 暴露了自己，修好后正向绿、两处反向红）、物料包 14 例、`ScaffoldSnapshotPlannerTest` 12 例；两包 typecheck + `vite build` 全绿；交付快照 `npm install` + `vue-tsc` + `vite build` 全绿。


## 2026-11-05（PR 8C：画布补复制 / 隐藏 / 锁定与落点预览，顺带修掉一处「改了但画布不动」）

**模型补三个动作，接进撤销栈。** `design.ts`：

- `duplicate(key)`：副本紧跟原件、属性照抄、整棵子树连同列 key 一起重新发号（`rekey()`），复制完选中副本。**区块不给复制** —— `locate()` 是按 `type` 找容器的，多出第二个 `SearchForm` 会让 `insert` / `move` / `columns` 全指到第一个上，画布上就多出一个改不动的幽灵区块；宁可当场报错，不产一份看着能改实际无效的节点。
- `setFlag(key, 'visible' | 'locked', value)`：这两个是**画布上的会话状态**，与内部 `key` 同级、不进 `design_json`（生成的页面里没有「设计器当前不想看这一格」这种概念）。既然不影响产物，翻动它们不该把整页标成「有未保存改动」（脏比对只看 `design_json`），但必须能撤销。关掉时整个 `delete` 不留 `false`，与 `applyProp` 的「空串就是没填」同一口径，否则撤销栈里会攒一堆空步。历史快照 `copy()` 要照抄这两个位，否则撤销一次锁定就把锁定弄丢了。
- 锁定后 `move` / `remove` / `duplicate` 当场抛错（报错文案用 `nameOf()` 报人看得懂的名字，不是 `TextInput` 这种类型码），工具条那三颗同步 `disabled`，区块挂「已锁定」标；隐藏后淡出 + 虚线边，意思是「还在，只是现在不看」。
- 视图接线：`DesignCanvas.vue` 区块工具条挂 上移 / 下移 / 显示-隐藏 / 锁定 / 删除，拖拽手柄 `:draggable="!block.locked"`；`LayerTree.vue` 行尾挂同款微标。`visible` 的缺省语义是「显示」，只有 `=== false` 才算藏着 —— 写 `!node.visible` 会把 `undefined` 当成隐藏，`toggleFlag()` 里先算当前值再取反，这个坑当时踩过一次（点了隐藏没反应）。
- **落点预览**：拖拽中的蓝槽写「松手插到第 3 个 · 工具栏」，末尾槽同理；补上 `dragleave`（且只在指针真的离开这一格时退高亮，`relatedTarget` 还在子树里就不退），否则拖过去又拖走时蓝槽一直亮在已经离开的位置。
- **`IconButton` 加 `hint` 与 `active`**：无障碍名要短（「解锁这一格」），tooltip 要说清后果（「锁定后这一格移动不了、删不掉、也拖不进东西 —— 防误操作，不是内容改动」），以前只有 `label` 一个字段只能二选一；`active` 同时驱动 `icon-btn.is-active`（点亮 `--blue` + `--blue-soft`），开关型的图标按钮不点亮就看不出按过没按过，另配 `aria-pressed`。

**修掉一处顺手暴露的老 bug（不是新代码引入的）。** `session.ts` 的 `root` / `blocks` / `selected` 三个读数返回的是**同一个引用** —— `store` 是普通可变 TS 对象，就地改完之后 `store.root.children` 还是那个数组，`computed` 重算得 `Object.is` 相等就不通知下游，于是 `tick` 明明涨了、画布一动不动。改成每次浅克隆一层，把「数据变了」翻译成「引用也变了」。`setProp` 一直有同样的问题，只是以前没人就地改过 `visible`，属性面板每次都是整值赋新，撞不上。

**测试**：`design.test.ts` 14 → 19 例，全部先跑红（`duplicate is not a function`）再实现；新增的三条钉的是「区块复制必须当场拒绝且树和撤销栈都不动」「标志位翻动不算内容改动」「锁定后 move/remove/duplicate 都抛错」。


## 2026-11-05（PR 8B 第十二刀：折叠箭头贴在悬浮带的圆角上）


用户截图指出：有子组件的行悬停后，左侧那颗展开箭头自己那圈高亮跟整行的悬浮带挤在一起，边距不对。

- **量下来是行根本没有左内距**：`.pf-tree-row` 写的是 `padding: 0 6px 0 0`，实测箭头 `x` 与行 `x` 偏移 **0px**、手柄也是 0px —— 16×16 的箭头方块直接顶在 8px 圆角悬浮带的左沿上，两圈背景叠在一起就成了截图里那道缝。原型是 `paddingLeft: 6 + depth * 12`，每行都有 6px 起手的内距。改成 `padding: 0 6px`，实测箭头与手柄都落在距行左沿 6px 处（行 x=43 → 箭头 x=49；子行 x=66 → 手柄 x=72），名称起点 91px。
- 箭头自身 hover 用 `--press-bg`（.075）而不是 `--hover-bg`（.045），这一条原本就比行带强一档、能分辨，没动；缩进仍然由 `.pf-thread` 的 `margin/padding + 左边丝线` 负责，行的 6px 是「手从哪儿起手」而不是层级宽度，两者不重复计数。
- **本轮按上一刀记下的顺序跑**：先 `vite build` 再 typecheck 与单测 —— 模板语法这一关 `vue-tsc` 拦不住，build 才拦得住。三项全绿（34 例单测）。


## 2026-11-05（PR 8B 第十一刀：树上的悬浮态与拖拽改位置，按原型的比例判定重做）

用户指出层级树的悬浮态和拖拽改位置还是不对。对照原型 `App.tsx:1187-1232` 那套 `dragover` 逐条比，我这版有四条实质差距。

- **落点位置原来是写死的**：根级行一律 `'inside'`、子级行一律 `'after'`。也就是说把一颗组件拖到「数据表格」那一行上，不管指针落在行的顶上还是底下，都会往表格里塞 —— 而表格没有 children，`treeDrop` 的 inside 分支会直接给它造出一个 `children`。改成原型同一套比例判定（`treeDropPosition()`）：`ratio < 0.32` → before，`0.32–0.68` 且**这一行真的有可放置区域**（复用 `dropZones()`，只有栅格列与插槽腔体）→ inside，其余 → after。
- **拖拽源从 14px 的手柄换成整行**：原型是行自己 `onDragStart`（锁定时 `preventDefault()`），手柄只是提示。原来只有那颗 14px 手柄能拖，鼠标偏一点就拖不起来。现在整行 `:draggable="!node.locked"`，手柄改成纯视觉件（`pointer-events: none`、锁定行不再亮起、`title` 写「按住整行拖拽排序」），行的光标跟着 `draggable` 变 grab / grabbing。
- **落点指示画进行自己体内**：原来是行的**兄弟**节点，插一条 2px 线会把后面整行往下顶，指针一挪就跳、位置永远对不上。改成 `position: absolute` 贴在这一行的上/下沿（`is-before` / `is-after`），inside 则给这一行描一圈 `1.5px dashed var(--blue)` + `--blue-soft` 底 —— 与原型 `outline: dropInside` 同一处理。
- **悬浮态两处修**：① 根行那颗「页面」标签原先包在 `.marks` 里，被 `:hover .marks { visibility: hidden }` 一起藏掉了 —— 而根行根本没有行尾操作栏，藏了就没有任何东西换入，整行悬停时右侧空一块。把标签从 `.marks` 里拿出来直接放 `.mt`，规则只影响真有操作栏的行。② 行高从 `min-height: 30px` 改成 `height: 32px`、根级缩进按原型的 `paddingLeft: 6 + depth * 12` 对齐，悬浮带子才盖得住整行。
- **`treeDrop` 补上同一道守卫**：`position === 'inside'` 时先查目标是不是真有可放置区域，没有就整个动作不做。这条不变量原先只在画布那条 `dropInsideContainer` 上有，树这条没有 —— 两个入口一条守、一条不守，等于不变量写在视图层而不是模型层。
- **测试**：`proto.test.ts` 7 → 8 例，新增「树上拖拽：before / after 换顺序，inside 只给容器，锁定的不动」，四条断言分别是拖到上半区排前面、拖到下半区排后面、往表格身上 inside 必须节点树逐字节不变且撤销栈不增、锁定组件自己拖不动但别人可以排它前面。34 例全绿，两包 typecheck、`vite build` 全绿。
- **一条要记的教训**：这一轮我改完模板后 `vue-tsc` 是绿的，`vite build` 却报 `[vite:vue] ProtoRail.vue (275:5): Invalid end tag` —— 重写模板时多留了一个 `</div>`。**类型检查过不了模板语法这一关**，之前几轮「typecheck + 单测 + build」里 build 才是真正兜住这层的那一步，不能省。
- **真机验收**（`:5173`，1600×1000 同域 iframe，页面 59，真 `DragEvent`）：整棵树顺序 `[页面标题, 数值指标, 查询表单, 数据表格]` → 拖第 0 行到第 2 行下 15% 处 → 指示线 `is-after`、`dragover` 被 preventDefault → 松手后 `[数值指标, 查询表单, 页面标题, 数据表格]`，画布顺序跟着一致；同一行上 15% → `is-before`、中 50% 落在表格上 → 仍是 `is-after` 且 `is-inside` 不出现（表格不是容器）；拖到栅格容器中间 → `is-inside` + `outline: rgb(35,109,233) dashed 1.5px`、无指示线，松手后子层列出「页面标题」、计数徽标「1」；锁定的数据表格行 `draggable=false`、手柄带 `is-off`、`dragstart` 的 `defaultPrevented === true`；树→画布与画布→树两个方向都能拖（`onRowDragStart` 同时置 `treeDragNodeId` 与 `draggingNodeId` + 预览帧，画布那条 `onCanvasDragOver` 因此也认）。**CSS `:hover` 仍无法用合成事件量**，悬浮带子与手柄显隐只验了 CSSOM 规则与「页面标签不再被藏」这一条结构改动。


## 2026-11-05（PR 8B 第十刀：页面层级树改成整行一条带，折叠箭头与键盘选中补上）

用户给了参考图：悬停那一行的拖拽手柄、组件图标、名称、锁定/显隐/删除三颗全在**同一条蓝色带子**里，根行还有一颗折叠箭头。对照下来我这版差三件事。

- **高亮挂错了元素**：原来 `.pf-tree-line`（里面那颗 `<button>`）才是选中面，手柄和行尾操作栏是它的**兄弟**，落在带子外面 —— 选中时只有文字背后亮一块，手柄贴在带子左沿外面、三颗操作落在右沿外面。改成 `.pf-tree-row` 整行就是那块面（`min-height: 30px`、`border-radius: var(--r-xs)`、hover 上 `--hover-bg`、选中上 `--blue-soft` + 描蓝），手柄/图标/名称/微标/操作栏全在里面，与原型 `.group/tree` 同一个结构。实测一行子元素顺序 `pf-grip → icon → nm → marks → pf-row-acts`，行高 30px，操作栏 `row.contains(acts) === true`。
- **补上折叠箭头**：原型是「有子节点 ⇒ 那颗 16×16 的箭头；没有 ⇒ 才让位给拖拽手柄」，一格只放一个左手件。我这版根行的箭头是写死的装饰（`treeExpanded` 有状态、没人读），容器行压根没有箭头。现在两者都由 `treeOpen(id)` / `toggleNode(id)` 驱动，箭头带 `aria-expanded`、旋转 90° 有过渡，收起时嵌套那一层整块不渲染。实测：空栅格容器 → 手柄、无计数、无嵌套层；往第一列放一颗日期选择 → 换成箭头、计数徽标「1」、嵌套层出现；点折叠 → 嵌套层 0、`aria-expanded=false`；再点 → 回到 1。
- **键盘到得了行**：原来只有那颗手柄 `tabindex="0"`，选中只能靠鼠标点。现在每行 `role="treeitem"` + `tabindex="0"` + `aria-selected`，Enter / 空格即选中（实测聚焦行按 Enter → 行加 `is-active`、右栏头部翻成「数据表格」），树容器补 `role="tree"`。
- **一处如实说明**：行尾那三颗仍然是鼠标/聚焦才显形（`display: none`），所以它们不在 Tab 序里，键盘用户选到行之后点不到它们 —— 这不是回归，改前也一样，而且同样的三个动作在**画布上那颗选中节点的工具条**里始终可见可点，键盘走那条能完成。要真把树里的三颗也做成可 Tab，得把它们从 `display` 换成定位叠加 + 淡入（否则会挤宽那一行），这刀没做，等需要时再说。
- **测试与验收**：本轮是纯交互层重排，没有新增可断言的业务规则，`proto.test.ts` 仍 7 例；两包 typecheck、单测 14 + 33、`vite build` 全绿。真机（`:5173`，1600×1000 同域 iframe，页面 59）另有一处教训：第一次跑数出来「空栅格容器也显示折叠箭头 + 计数 1」看着像 bug，实际是前一条脚本中途报错留下了脏状态 —— 整页 reload 后重测才是上面那组数。**CSS `:hover` 用合成 `mouseover` 量不出来**（`elementFromPoint` 那套伪状态只有真实指针能触发），所以手柄显隐与操作栏换入这两条只验证了 CSSOM 里规则存在且选择器正确（`.pf-tree-row:hover .pf-row-acts, .pf-tree-row:focus-within .pf-row-acts { display: inline-flex }` 等），选中态/折叠/键盘选中这些是真量到的。


## 2026-11-05（PR 8B 第九刀：上一条里我改坏了三处，逐条纠）

用户回看后指出：收起按钮删错了、Tab 组的内边距不统一、关页面的确认框要用平台样式。

- **1. 我上一刀把「收起面板」那颗按钮弄丢了，而且不是我删的 —— 是我把它挤没了**。`.pf-fluid` 我写的是 `display: flex; width: 100%`，而平台 `.segmented` 自带 `flex: none`：分段控件于是死占整行 234px，把同一行的收起按钮推到 x=261–287，而 `.pf-pane` 是 `overflow: hidden`、右边界 266 —— 按钮直接被裁掉了，看起来就像被我删了。改成 `flex: 1 1 0; min-width: 0`，实测按钮回到 x=227–253、距面板右边 13px（12px 内距 + 1px 描边），可见。真正该删的是**面板底部那颗「收起组件」**：整条页脚一起删掉（`Button`、`toggleAll`、`allCollapsed`、`.pf-pane-foot` 四条样式一并清），分组折叠只留分组头那颗箭头一格一格控；顺带把因此没人用的 `chevronsUp`/`chevronsDown` 两条图标注册项也删了 —— 留着就是往公共图标表里塞两个死键。
- **2. Tab 组的内边距不统一，是因为我两个面板用了两套节奏**：左栏 `.pf-pane-head` 是 `padding: 12px 12px 0` + `gap: 8px`、无分隔线，右栏是 `.pf-ins-head { 12px 12px 8px }` + `.pf-ins-tabs { 0 12px }`、Tab 组头顶 0px 直接顶在头部下面。照原型对齐（左栏 `p-3 border-b` 内 `flex gap-1.5`；右栏 `p-3.5 border-b space-y-3` 把头部行与 Tab 组包在同一块里）：左栏 `padding: 12px; gap: 6px; border-bottom: 1px`，右栏头部 `14px 14px 0`、Tab 组 `12px 14px 14px` + 自己的下分隔线。实测两组分段控件都是 36px 高（3px 内距 + 28px 按钮 + 3px），左栏 178px 宽（234 − 12×2 − 26 − 6）、右栏 282px 宽（310 − 14×2），左右内距各自对称。
- **3. 关标签页的确认框做不到与平台 UI 一致 —— 这是浏览器的规定，不是实现选择**。`beforeunload` 只允许网页**取消**这次离开，那条确认框一律由浏览器自己画，页面在这里塞不进任何自定义 DOM。所以保留 `useUnsavedGuard` 走原生拦截（实测脏了 `defaultPrevented === true`、点保存后变 `false`、再改一次又回到 `true`，不确认就关不掉，「反复点也不关」这条本来就满足）。我一度想补一条 `onBeforeRouteLeave` + 平台 `confirm()` 管站内换路由，写完去查了产品形态：设计器是 `pages.vue:117 openDesignerInNewTab()` 用 `window.open` 开出去的独立标签页，历史里只有这一条，站内没有任何入口会离开它 —— 那条钩子挂上去永远触发不了，属于死代码，删掉了，把这个限制写进 `unsavedGuard.ts` 的注释里，免得下一个人再补一遍。真要一条平台样式的确认，只能是在页面里放一颗显式「退出设计器」按钮，而那颗正是上一轮用户让我从顶栏删掉的。
- **测试**：把「物料分组默认全展开，收起与展开只由底部那一条按钮控制」这条的标题与断言文案改掉 —— 按钮已经没了，文案继续提它就是说谎；断言本身（默认全展开）仍然有效。删掉 `chevronsUp/Down` 那段注册断言。两包 typecheck、单测 14 + 33、`vite build` 全绿。
- **真机验收**（`:5173`，1600×1000 同域 iframe，页面 59）：收起按钮 rect `x=227–253`、面板右界 266 → 内缩 13px、`checkVisibility() === true`；`.pf-pane-foot` 数量为 0，`.pf-pane` 最后一个子元素是 `.pf-pane-body`；左栏 head 实测 `padding: 12px`、`gap: 6px`、`border-bottom: 1px`，右栏 head `14px 14px 0` + tabs `12px 14px 14px` 带下分隔线，两组分段控件同高 36px；`beforeunload` 三轮翻转如上。控制台除 Chrome 那条 `Blocked attempt to show a 'beforeunload' confirmation panel for a frame that never had a user gesture`（脚本驱动的 iframe 没有真人手势，是它证明监听在调 `preventDefault`）外无报错。**系统那条确认框本身在 iframe 里看不到**，需要真人在真标签页里点一次关闭才算验完最后一步。


## 2026-11-05（PR 8B 第八刀：分段控件与两条抽屉改用平台件，顶栏去掉返回，脏了拦一次）

用户又点了五处，四条是「这里自己画了一套平台已有的东西」，一条是交互取舍。

- **1. 深色主题下分段控件的激活态突兀** —— 根因是我在 `proto.css` 里手画了一版 `.pf-segmented`，激活态填 `linear-gradient(145deg, rgba(255,255,255,.93), rgba(246,251,249,.62))`：浅色下是块玻璃，深色下就是一块白斑。平台本来就有 `Segmented.vue`（`.segmented.m-groove` 凹槽底座 + `.seg-thumb.m-pill` 玻璃滑块 + GSAP `slideThumb` 位移动画 + `role="radiogroup"` 与左右键切换），材质走 `--pill-bg`，深色下自动变成 `rgba(255,255,255,.11→.05)` 的暗玻璃。把设计器里**六处**控制型分段全部换成它：左栏「组件物料 ↔ 页面层级」、右栏三段 Tab、表格档位、圆角阶梯、表面材质、视口菜单的终端设备，加上画布表格头那颗行高密度与分段容器预览；`.pf-segmented` / `.pf-seg-btn` 两套样式（全局一份 + NodeRenderer scoped 一份）整块删掉，只补一条 `.pf-fluid { display:flex; width:100% }` 铺满栏宽（滑块那颗是 `<span>`，`> button` 选择器不会误伤）。分段容器的预览页签顺带有了本地状态，不再是一颗点了没反应的假控件。
- **2. 顶栏去掉返回按钮**：设计器本来就是独占整个标签页，「返回应用页面列表」要么 `window.close()` 要么 `history.back()`，在新标签页语境里很生硬。`ProtoTopbar.vue` 删掉那颗按钮与 `back()`，`DesignerTopbar.vue` 删掉 `IconButton name="arrowLeft"` 与 `close` 这条 emit，`LiveDesigner.vue` 连带删掉 `goBack()` 与已经没人用的 `useRouter`。
- **3. 关标签页前拦一次未保存**：新增 `packages/scaffold-ui/src/composables/unsavedGuard.ts` 的 `useUnsavedGuard(dirty)` —— 挂 `beforeunload`，脏了就 `preventDefault()` 且置空 `returnValue`（Chrome 两个都要才认），作用域销毁时自动摘监听。两条设计器各接自己的脏源：原型那条是 `unsavedCount > 0`，`?mode=live` 那条是 `session.dirty`。浏览器不允许自绘这条确认，所以不仿平台样式，只保证拦得住。
- **4. 栏底那颗按钮还是不和谐**：它用的是我自造的 `.pf-mini`（24px、`--float-bg`、手写边框），而平台那颗是 `Button size="sm"`（30px、`btn-secondary` + `m-pill`）。改成平台 `Button` 并让它自己带 icon，`.pf-mini` 两份定义（ProtoRail 与 ProtoOverlays 各一份）一起删掉。
- **5. 两条抽屉的位置与样式都不对**：`.pf-drawer` 是我自己写的一层，挂在 `.pf-scrim` 里、而那层是 `display:flex` 没有对齐声明 —— 所以它其实贴在**左边**，而且整层被 `.pf-chassis` 那圈 10px 机身边框和 38px 圆角切着。换成平台 `Drawer.vue`：`Teleport to="body"` + `.overlay.drawer-overlay`（`place-items: stretch end`，贴右满高）+ `.drawer` + `overlayEnter(el, done, 'drawer')` 那套升起 + `createFocusTrap` + Escape 自管。结构代码 `:width="560"`、事件配置 `:width="420"`，头部 `title`/`sub` 传字符串（那颗 activity 图标按平台样式不再放），脚部走 `#footer` 插槽；`.pf-drawer*` 四条全局样式与 `.pf-modal-title` 的 scoped 覆写一并删。命令面板与发布弹窗仍留在遮罩那层，只把两条抽屉搬出去。
- **验收**（`:5173`，1600×1000 同域 iframe，页面 59）：深色下分段实测底座 `rgba(0,0,0,.22)`、滑块 `linear-gradient(145deg, rgba(255,255,255,.11), …)`、激活字 `rgb(238,242,241)`、非激活 `rgb(144,154,157)` —— 与参考图那块暗玻璃一致，运行时来回切主题四次，字色每次都跟着 `--ink` 走（`#eef2f1 ↔ #101418`）；浅色下底座 `rgba(225,231,229,.5)`。顶栏左侧实测只剩 `pf-brand` + `pf-crumbs`，`[aria-label="返回应用页面列表"]` 数量为 0。栏底按钮 class 实测 `btn btn-secondary btn-sm m-pill`、高 30px、带 icon，文案「收起组件」点击后四组卡片全部隐藏。合成 `beforeunload` 事件在脏状态下 `defaultPrevented === true`、点「立即保存草稿」后变 `false` 且浮条消失；Chrome 同时打出 `Blocked attempt to show a 'beforeunload' confirmation panel for a frame that never had a user gesture` —— 这条恰好证明监听真的在调 `preventDefault`，但**系统那条确认弹窗在脚本驱动的 iframe 里看不到**，需要真人在真标签页里点一次才算验完最后一步。两条抽屉实测都 `Teleport` 到 body（`.closest('.pf-chassis') === null`）、class `overlay drawer-overlay` + `drawer`、贴右满高：结构代码 560×984 落点 x=1032（视口 1600，右边留 8px 槽），事件配置 420×984 落点 x=1172；标题/副标题/两节触发时机/脚部「取消 · 保存事件配置」齐全，`.drawer-body` 是 `overflow-y: auto`，Escape 一次即关。圆角阶梯点第 4 档实测节点 `border-radius` 变 20px，画布上那颗行高密度仍是可点的平台分段（`pointer-events: auto`、激活「标准 48」）。两包 typecheck、单测 14 + 33、`vite build` 全绿；控制台除上面那条 Chrome 安全提示外无报错。
- **没写单测的一处，如实说明**：`useUnsavedGuard` 只有 10 行且直接摸 `window`，本仓的单测跑在 node 里没有 DOM，硬凑一个假 window 只是把断言写在替身上。它的实际验证是浏览器里 `defaultPrevented` 的翻转与 Chrome 那条安全提示。


## 2026-11-05（PR 8B 第七刀：深色铭牌与提示牌堆的文字其实一直是黑的）

用户报「移动组件后底部那条浮条字体不清楚」。量出来不是「不够清楚」，是**对比度 1.04:1 —— 近黑的字压在近黑的底上，基本读不出来**。

- **根因是三个从原型带过来、平台从来没定义过的 CSS 变量名**：`--plate-fg`、`--plate-muted`、`--line-hover`。`var()` 解析失败时整条声明作废，颜色落到继承值上 —— 继承到的是外壳的 `--ink: #101418`，于是所有坐在 `--plate-bg`（深色玻璃）上的东西都变成了黑底黑字。原型自己的 `index.css` 里这三条是有值的（`--plate-fg: #f2f5f4`、`--plate-muted: rgba(255,255,255,.56)`、`--line-hover: rgba(46,58,62,.18)`），我搬样式的时候把名字一起搬了，平台令牌表里却没有对应条目，也没人报错。
- **受影响四处**：`.pf-toast`（移动/复制/删除后弹的那条提示牌堆，就是用户点名的这条）、`.pf-plate`（数值指标右上角那块「工位铭牌 ND-P3-FORM-02 | 采样周期 5000ms」深色铭牌）、`.pf-code`（事件抽屉里的声明预览 JSON）、`.dz-schema`（Schema 抽屉的深色代码面）。前两处实测原本 `rgb(16,20,24)` on `rgba(21,24,26,.9)`。
- **改法**：`--plate-fg` → 平台的 `--plate-ink`、`--plate-muted` → `--plate-soft`，两条值本来就一模一样（`#f2f5f4` / `rgba(255,255,255,.56)`），不新造同义令牌；`--line-hover` 平台确实没有这一档（`.input-shell:hover` 是把 `rgba(var(--shade),.18)` 直接写在规则里的），所以在 `tokens.css` 的浅色与深色两段各补一条，7 处悬浮描边从「静默不生效」变成真会亮。`.input-shell` 那条内联值这次没动，避免顺手改掉既有页面的观感。
- **顺带抓到一条同类的第二个坑**：`.pf-plate` 里那两个次要文字 span 我用的 class 名是 `.muted`，而 `NodeRenderer.vue` 的 scoped 样式里 `.muted { color: var(--ink-muted) }` 是给表格单元格准备的「浅底次要文字」。两条特异性相同（`.pf-plate .muted` 与 `.muted[data-v-x]`），scoped 样式后注入所以赢了 —— 铭牌上的次要字被压成 `rgb(89,99,104)`，在深底上只有约 2.1:1。原型的写法是在 span 上直接给 `--plate-muted`、根本没有 `.muted` 这个名字，所以把铭牌里那两处改名 `.dim`，冲突消失、表格那处 `.muted` 保持原样。
- **测试**：`proto.test.ts` 6 → 7 例（先跑红，直接把 11 处引用列出来）。新这条不测颜色好不好看，测**「设计器 CSS 里每一个 `var(--x)` 引用的令牌是否真的存在」**：读平台 `styles/*.css` 收全部已定义令牌（断言数量 > 80，防止路径失效后用例静默空跑），再扫 `proto.css` 与设计器两个目录下的每个 `.vue`，文件内自己声明的局部变量（`--pf-rail-w` 这类）算已定义，带兜底值的 `var(--x, y)` 不在射程内。这类「写错变量名 → 整条声明作废」和 `Icon.vue` 查不到名字静默画 `file` 是同一种静默失败，类型检查与构建都不报错，只有这条能拦住。两包 typecheck、单测 14 + 33 全绿、`vite build` 全绿。
- **真机验收**（`:5173`，1600×1000 同域 iframe，页面 59）：拖「分割线」入画布后提示牌堆实测文字 `rgb(242,245,244)` on `rgba(21,24,26,.92)`，**对比度从 1.04:1 升到 13.34:1**；铭牌三段逐一复算 —— 次要字 `rgba(255,255,255,.56)` 合成后约 **5.5:1**（AA 正文线 4.5:1），主值 `#f2f5f4` **12.5:1**，青柠 `#9be35a` **8.8:1**；表格里那颗 `.mono.muted` 仍读 `rgb(89,99,104)`（它坐在浅底行上，本来就该是次要深灰，没被误伤）。**截图面仍拿不到可见浏览器面**，以上是从计算样式逐项读回、按 WCAG 相对亮度公式算的，不是像素比对。
- **这条要认账**：之前几轮我在验收里写「提示牌堆弹出『已撤销上一步画布改动』」「铭牌读出『工位铭牌 ND-P3-FORM-02 | 采样周期 5000ms』」—— 那些读的是 `innerText`，字是黑的还是白的它一样返回，所以内容对、可读性完全没验。以后这类深色面要连计算色一起量。


## 2026-11-05（PR 8B 第六刀：松手只认可放置区域，未保存浮条改用平台那条 .float-bar）

- **非法落点不再改树**（`data.ts` 新增 `dropZones()`，`state.ts` 两个 drop 入口加守卫）。此前 `.pf-node` 整颗都挂着 `dragover/drop`，`dropInsideContainer` 又用 `mapNode` 无条件往目标身上 append `children` —— 在**数据表格**身上松手会真的塞出一个 `table.children`，而表格压根没有 children 这个概念，渲染器与生成器都不认这份形状。现在只有栅格（每列一个落点，返回列下标）与插槽容器（整腔一个，返回 `undefined`）算落点，其余 kind 返回空数组，`dropInsideContainer` 在非落点上直接 `resetDrag()` 返回：不进撤销栈、不弹提示、不碰 `nodes`。
- **`dropAtTopIndex` 改成无参**，落点索引由 `activeDropIndex` 自己说话。原来调用方兜底成 `?? nodes.length`，等于把「dragover 压根没落到列表上」也画成一个末尾新组件；现在索引为 `null` 就是没有落点，松手什么也不做。
- **可放置区域终于接上了真交互**：`NodeRenderer.vue` 的 `.pf-grid-col` / `.pf-slotbox` 上早就写好了 `is-active` 样式，却没有任何 dragover 与 drop handler，也没有 `drop-target` 入口 —— 那两高亮是死代码。现在按 `dropZones()` 挂上，容器与栅格列被划过时描蓝，`zone-over/leave/drop` 三个事件逐级从嵌套的 `NodeRenderer` 冒回画布。
- **未保存浮条改用平台那件**（`ProtoDesigner.vue`）：设计器自己写了一份 `.pf-float`，把 `.float-bar` 的几何值抄了一遍却把颜色写死成 `rgba(21,24,26,.9)` / `--plate-fg` / `font-size: 12.5px`，与用户管理那条批量操作浮条（`ListPage.vue` 的 `.float-bar` + `float-bar-count` + `float-bar-sep` + `Button variant="text"`）是两套。删掉 `.pf-float` 整块，改挂平台 `.float-bar`，用 `Teleport to="body"` + `floatBarEnter/floatBarLeave` 那套升起动画，撤销/重做两颗也换成平台 `Button`（`variant="text" size="sm" icon=`）而不是自绘 28px 圆钮 —— 浮条高度由同一套控件尺寸决定，自绘那颗会把它顶高 1px。`?mode=live` 那条本来就在用 `.float-bar`，两条现在同源。
- **测试**：`proto.test.ts` 4 → 6 例（先跑红：`data.ts does not provide an export named 'dropZones'`）。一条钉「只有栅格列与插槽腔体是落点，画布上现有那四颗一个都不是」，一条钉「在非法落点松手 ⇒ 节点树逐字节不变、撤销栈不增加、拖拽态清干净；没有落点索引时松手 ⇒ 也不插」。前端单测 14 + 32 全绿，两包 typecheck 与 `vite build` 全绿。
- **真机验收**（`:5173`，1600×1000 同域 iframe，页面 59，用真 `DragEvent` 驱动）：拖「数值指标」到数据表格身上 → 可放置区域高亮 0 个、松手后顶层节点 4→5 且表格内部没有嵌套（走的是顶层落点，不是塞进表格）；点物料卡加一颗栅格容器 → 拖「输入框」划过它的第 1 列 → `.pf-grid-col.is-active` 1 个、顶层落点槽 0 个（两处不打架）、松手后该列出现组件且顶层数量不动；从物料卡直接在顶栏松手 → 节点数不变、无残留幽灵；在画布上 dragover 后不 drop 直接 dragend → 节点数不变、幽灵与高亮槽都归零。浮条实测与用户管理页那条**逐属性相同**：底 `rgba(21,24,26,.9)`、字 `rgb(242,245,244)`、圆角 999px、内距 `8px 8px 8px 18px`、字号 13px、gap 8px、描边 `rgba(255,255,255,.1)`、阴影三段一致，**高度都是 44.75px**；按钮 class 为 `btn btn-text btn-sm`（放弃改动多一个 `is-danger`），与用户页那四颗同一套。提示牌堆消失 3.6s 后浮条按 `unsavedCount` 重新升起。**截图面仍拿不到可见浏览器面**，以上全是 DOM 实测值。


## 2026-11-05（PR 8B 第五刀：物料搜索框、底部按钮、拖拽预览帧、自定义分辨率那格）

用户又点了四处细节，逐条落。

- **搜索框里那颗混进来的分组开关删掉**。原型（`designer/src/App.tsx:951`）在搜索框尾部塞了一颗按钮当「全部收起/展开分组」，没关键字时它是折叠开关、有关键字时才换成清空 × —— 一个位置两种语义，鼠标移过去才知道按下去干什么。这刀把它整颗摘掉：搜索框只剩前置放大镜 + 输入 + 有关键字才出现的清空 ×。收起/展开分组的职责挪到栏底那条按钮。
- **栏底那条按钮改名并改行为**（`ProtoRail.vue`）：不再显示「画布节点 N 个」（两个 Tab 的页脚都不显示计数，页脚改成右对齐单按钮）；按钮从「新建扩展槽 / 全部展开」改成**「收起组件」↔「展开组件」**，动作是整组折叠/展开，图标跟着翻 —— 收起态画 `chevronsUp`、展开态画 `chevronsDown`（注册表新增这两条双层箭头；单箭头只表示一层，压不住「全部」这个语义）。分组默认全展开（`groupCollapsed` 初值是空对象），这条现在有用例钉住。
- **拖拽预览帧从占位框改成组件本体**（`state.ts` 的 `dragPreview` + `ProtoCanvas.vue`）：拖物料卡或拖画布上已有组件时，落点处直接就地渲染那颗组件 —— 表格画真表格、数值指标画真读数，外面套一圈 1.5px 蓝色虚线 + `--blue-soft` 底 + `--e-accent` 徽标，`pointer-events: none`，`pfGhostPulse` 0.22s 入场。关键一条：**预览节点必须在 `startMaterialDrag` 那一刻建好、整段拖拽复用同一份**，因为 `createNodeFromMaterial` 生成随机后缀的 id 与 code —— 放进 computed 里每次 dragover 重建一颗，预览帧会一路抖到底。原来那颗「末尾占位槽」也去掉，末尾落点由 `drop-zone:tail` 那条虚线区自己高亮。
- **自定义分辨率那格的「应用」按钮变形修好**：它原先和输入框挤在同一行 flex 里、没有 `flex: none` 也没有 `white-space: nowrap`，被输入框压到换行变形。按原型改成整格布局：上分隔线 + 左「自定义宽度」标签 + 92×30 输入框（右侧 `px` 上标）+ 47×30 应用按钮，两控件同高同基线。顺手补了原型有、我这版漏的三条行为：宽度**夹在 320–2560**（越界夹回来并写回输入框，不让一个手滑的数字撑爆画布）、回车即应用、点常用断点时把值同步进自定义输入框。
- **测试**：`proto.test.ts` 3 → 4 例，新增一条同时钉「分组默认全展开」与「预览帧在拖拽起点建好、整段复用同一颗、松手即清」。前端单测 14 + 30 全绿，两包 typecheck 与 `vite build` 全绿。
- **真机验收**（`:5173`，1600×1000 同域 iframe，页面 59）：搜索框实测只剩 1 个 svg、无关键字时清空按钮不存在，输入「表格」→ 19 张卡筛到 1 张且清空按钮出现，点清空 → 回到 19 张、按钮消失；栏底实测文案「收起组件」、图标 path `M17 11l-5-5-5 5…`（chevronsUp）、页脚 `justify-content: flex-end`、无计数，点一下 → 「展开组件」+ `M7 6l5 5 5-5…`、四组 `.pf-tiles` 全部 `offsetParent === null`，再点回原状；用真 `DragEvent` 拖「数据表格」到第二个节点前 → 落点处出现 1 个 `.pf-ghost`，实测 938×517、内含真 `<table>` 4 行、outline `rgb(35,109,233) dashed 1.5px`、底 `rgba(35,109,233,.1)`、`pointer-events: none`、徽标「预览落位 · 数据表格（松手即放置）」带 `kindTable` 图标；拖到末尾 → 虚线区翻成 `pf-tail is-active` 且预览帧跟着挪到最后；`dragend` 后幽灵消失、画布仍是 4 个节点；视口菜单那格实测输入框 92×30、按钮 47×30、两者中线差 < 1px、按钮 `nowrap` 未被裁切，输 99999 点应用 → 胶囊与画布 `max-width` 都落到 2560 且菜单关闭，点「1280 px」→ 自定义输入框同步成 1280。**截图面仍拿不到可见浏览器面**，以上全是 DOM 实测值。


## 2026-11-05（PR 8B 第四刀：分栏折叠态与选中工具条按设计稿校正，图标改成语义驱动）

用户对着 `:5199` 比了一轮，报两处：① 左右两栏的折叠状态不对（并给了设计稿里收起后的样子）；② 选中组件后右上角那条工具条没 1:1，且**锁定/解锁的图标要按语义用**。量下来两处都是真 bug，不是错觉。

- **折叠态错在三处，逐条改**：① 收起后我把那一栏的 grid 列压成 `0px`，可 `column-gap: 12px` 还在 —— 画布左边永远留一条 12px 死带，看着像没塌干净。改成收起时直接把 grid 换成两列/一列（`236px 1fr 312px` → `1fr 312px` → `1fr`），栏没了就是真没了。② 「展开物料」「展开配置」两颗胶囊我原先绝对定位在 `.pf-body` 上（`top: 24px`，落在栏间距里），而设计稿里它们是**贴在画布舞台内侧**的：左胶囊 `top:10 left:12`，右胶囊和视口胶囊排在同一行 `top:10 right:12 gap:8`。搬到 `.pf-canvas` 内部并复用平台 `m-pill` 材质，实测收起后三颗胶囊 展开物料 / 桌面端自适应 / 展开配置 依次排开，与设计稿同位同宽（30px 高）。③ 文案与图标也照设计稿改回「展开物料」（我写成「展开组件物料」）、「展开配置」，图标从随手拿的 `sidebar`/`sliders` 换成 `panelLeftOpen`/`panelRightOpen`。
- **顺手修了折叠态才能暴露的一处结构问题**：视口胶囊原本挂在 `sticky` 的 `.pf-canvas-top` 里，而 `.pf-canvas` 自己就是滚动容器 —— 设计稿里胶囊是不跟着滚的。改成舞台 `overflow: hidden` + 内层 `.pf-canvas-scroll` 负责滚动，实测滚 200px 胶囊位移 0。页面级的 `paddingX/paddingY/blockGap` 也终于真的作用在滚动层与节点间距上（此前写死 14/12/10，右栏那三格改了没反应）。
- **选中工具条按设计稿重做**：几何值从 26px/`0 4px 0 10px`/gap 1 改成 **28px、`padding: 0 6px`、gap 2、top −14、right 12**，六颗按钮 22×22 圆形；名称徽标前面那颗图标从写死的 `layers` 改成**按组件类型取**（`KIND_ICON[node.kind]`，数值指标画的是四根读数柱）。禁用规则照原型补齐：锁定后上移/下移/删除三颗一起禁用、删除的 title 翻成「组件已锁定」。隐藏与锁定两颗在生效时加 `bg-white/30` 高亮，看得出当前态。
- **图标按语义用（这是用户点名的那条）**：解锁态我原先画的是 `sidebar`（一个侧栏方块）、隐藏态画的是 `ban`（一个禁止圈）—— 都跟「锁」「 visibility」没有关系。现在 `lockIcon(locked)` / `visibleIcon(visible)` 两个函数出图标：锁定画闭合锁、解锁画开锁（锁梁只扣一侧），显示画眼、隐藏画带斜遮线的眼。同一套语义同时用在画布工具条和左栏层级树的悬浮操作栏上。
- **注册表补 26 条**（`packages/scaffold-ui/src/data/icons.ts`）：`unlock`、`eyeOff`、`panelLeft/RightClose/Open`、`grip`（2×3 拖拽点，替掉随手用的 `more`），以及 20 条 `kind*` 组件类型图标。`MENU_ICON_OPTIONS` 不动 —— 这些是设计器语汇，不该出现在菜单图标选择器里。
- **左栏层级树补上设计稿里的行尾操作栏**：此前只有 `×` 删除，设计稿是「锁定 / 显隐 / 删除」三颗（22×22、圆角 6、锁定态描蓝底），且静默态那排状态微标（锁、斜线眼、子节点计数）在鼠标移入时让位给操作按钮。抽成 `ProtoTreeRowActions.vue`（根节点与子节点两处共用），锁定后拖拽手柄 `draggable=false` 并换成「已锁定，不可拖拽」。
- **两条真 bug 是靠 DOM 量出来才发现的**：① `<TreeRowActions>` 我 import 进来却按 `<RowActions>` 写模板 —— Vue 把它当成未知元素原样渲染，`vue-tsc` 与 `vite build` 全绿，页面上那颗操作栏压根不存在（`document.querySelectorAll('.pf-row-acts').length === 0`）。② 子组件 scoped 样式里写 `.pf-tree-row:hover .pf-row-acts { display: inline-flex }` 永远不命中 —— `:hover` 那一层属于父组件的 scope id。显隐规则挪进全局 `proto.css`，实测 idle `none`、键盘聚焦后 `flex`。
- **测试**：新增 `__tests__/proto.test.ts` 3 例（先跑出红：`data.ts does not provide an export named 'KIND_ICON'`）。钉的是「每种 `NodeKind` 都有专属图标且都在注册表里登记过」——`Icon.vue` 查不到名字时静默画 `file`，物料卡与工具条会整片退化成同一个灰方块，类型检查和构建都拦不住，只有这条断言能拦住；另两条钉 `lock≠unlock`、`eye≠eyeOff`、`panel*Close` 与 `panel*Open` 箭头方向相反。前端单测 14 + 29 全绿，两包 typecheck 与 `vite build` 全绿。
- **真机验收**（`:5173`，1600×1000 同域 iframe，页面 59）：三栏 grid 实测 `236px 968px 312px` → 收起左栏 `1216px 312px` → 再收起右栏 `1540px`（单列），左胶囊相对舞台 `13px/11px`、右簇 `1294px/11px`，胶囊高 30px；工具条实测 28px 高、`top −14 right 12`、`padding 0 6px`、gap 2、底 `rgb(35,109,233)`、六颗按钮均 22px、徽标图标 12px 且 path 是 `kindReadout`；点锁定 → aria 翻「解锁组件」、path 换成闭合锁、`is-on` 生效、上移/下移/删除三颗 `disabled=true`，再点回原态全部还原；点隐藏 → aria 翻「显示组件」、path 出现斜遮线、节点 opacity 落到 0.42；树里 4 行有操作栏（页面根没有，与设计稿一致），锁定后 `draggable=false`、微标出现锁、删除按钮禁用；物料 19 张卡的图标 19 种 path 全不同、0 张退化成 `file`。**截图面仍拿不到可见浏览器面**，以上全是 DOM 实测值。


## 2026-11-05（PR 8B：设计器 1:1 视觉原型，`proto/` 一层，暂不接数据）

第三次修正口径 —— 前两次我把「1:1」理解成「外壳照原型、内容按后端白名单」，用户要的是**整页照原型复刻，先不接数据**。这一刀按那个口径做，边界用 AskUserQuestion 明确确认过（选项：连模型一起 1:1 / UI 与交互 1:1 模型不动 / 先做纯视觉 1:1 暂不接数据 —— 选了第三个）。

- **落点**：新增 `views/lowcode/designer/proto/` 一层。`DesignerView.vue` 退成一个开关：默认渲染原型，带 `?mode=live` 才渲染已接数据那一版（原 `DesignerView.vue` 的内容整体改名 `LiveDesigner.vue`，一行没删）。同一条路由、同一个登录守卫，等 design_json 升到 1.1、原型那份形状能产出生成物时这个开关就该消失。**两条都活着而不是把已接数据那一版删掉**，是因为发布链路（PR 7）与生成器只认 v1.0 那份形状，砍掉就没有任何一条路能真发出页面。
- **搬进来的六件**：`data.ts`（19 项物料、20 种 kind、按 kind 分支的触发时机表、化成工站那套演示数据，逐字对应 `designerData.ts`）、`state.ts`（节点树 + 撤销栈 + 拖拽与落点 + 浮层栈 + 提示牌堆 + 事件抽屉草稿，对应 `App.tsx` 那 3557 行里的 handler 部分）、`NodeRenderer.vue`（20 种 kind 的富预览：数值指标的主角大读数 + 深色工位铭牌 + 刻度条 + 斜线慢操作区间 + kv 只读读数、数据表格的三档密度 + 吸左勾选列 + 吸右操作列 + 分类色胶囊 + 权限 chip + 状态标签 + 分页脚注、工序流向、栅格分栏、插槽容器、分段容器、基础控件）、`proto.css`（约 700 行：把原型 JSX 里内联的 Tailwind 工具类写成真实 CSS，颜色与尺寸一律走平台令牌）、`ProtoTopbar/Rail/Canvas/Inspector/Overlays/Designer`。
- **修两处 bug（都是量出来的，不是看出来的）**：① `NodeRenderer` 里 `table` 与 `allChecked` 是 setup 时算一次的普通常量 —— 点「宽松 56」撤销栈多了一步、按钮高亮也换了，行高与单元格内距一动不动。改成 `computed`。② 单元格左右内距压根没绑密度档位（原型是 `padding: 0 ${cellPx}px`），补一个 `--pf-cell-px` 变量由档位驱动，实测 紧凑 14 / 标准 16 / 宽松 18。
- **与原型不一致的地方，如实写清**：① 拖拽的 1:1 幽灵预览只在容器与栅格列的落点上给了虚线框 + 名称徽标，**没有**渲染被拖组件的完整 1:1 形态（原型是 `renderGhostPreviewBlock` 直接复用渲染器）；顶层按 `clientY` 算落点索引已经做了，插帧位置对，帧里是占位而不是组件本体。② `topology` 只搬了三张工序卡，没搬工序连线与脉冲点动画。③ 行高密度改档位后**行高本身看不出变化** —— 批次号在窄列里换行（实测两行 36px）把行高撑到 74px，压过 40/48/56 三档；这一点原型自己也是同样表现（它那张截图里 `BT-20260419–081A` 就是两行），所以不算偏离，但要说明。
- **不接数据的边界**：「保存草稿」与「发布上线」只动本地脏计数并弹提示，不打接口；Schema 抽屉输出的是这份原型形状（`pageCode` / `version 2.4.0` / `styleSystem pearl-glass` / `nodes[]`），**不是** `design_json v1.0`，这份送进后端 `PageDesign.parse` 会当场 400（version 不认、PageContainer 不在）。右栏底部常驻一句说明，不让人误以为这版能发布。
- **测试**：typecheck 两包 + 单测 14 + 26 全绿 + `vite build` 通过。`proto/` 这一层是纯视觉稿，没有可断言的业务规则，所以没为它写单测 —— 它的验收是真机点一遍（见下），而不是形状断言。
- **真机验收**（`:5173`，1600×1000 同域 iframe，页面 59）：`.pf-chassis` 在、`.sidebar` 不存在；顶栏实测 64px；三栏 `236px / 968px / 312px`；物料 19 张卡实测 66×66、分组「布局容器 / 表单输入 / 数据展示 / 基础操作」；画布 4 个节点，选中节点工具条显示「数值指标」；铭牌读出「工位铭牌 ND-P3-FORM-02 | 采样周期 5000ms」、刻度 08:00/10:00/12:00/14:28（当前柱）/16:00、kv 两项、斜线区间与「现在」线都在；筛选三格实测宽度 220/200/180；表格 4 行、权限 chip 四串、状态标签四条、分类胶囊四条、脚注「共 4 个待审批次 · 已勾选 N 项」随勾选变；密度三档实测单元格内距 14/16/18；事件 Tab → 事件抽屉（触发时机两条带 code、声明预览 JSON、取消/保存）；Schema 抽屉标题与「UTF-8 · JSON Schema v2」；发布弹窗三格「发布版本号 / 目标运行环境 / 变更说明」预填 v2.4.1-release；⌘K 四条指令；深色切换实测 `data-theme=dark`、节点底 `rgb(27,31,33)`，切回浅色正常；撤销弹出「已撤销上一步画布改动」且重做转为可用；预览态 grid `0px 1516px 0px`、工具条消失、按钮文案翻成「退出运行预览」。**截图面仍拿不到可见浏览器面**，以上全部是 DOM 实测值，不是像素比对。


## 2026-11-05（PR 8A 第二刀返工：三栏按设计稿重建，画布改成所见即所得）

上一条只搬了外壳，三栏里还是旧的 `dz-*` 示意块 —— 用户指着设计稿说「组件物料区、画布、右侧属性面板这三部分没有按设计稿重构」，是对的。这一刀把三栏全部重建。

- **左栏拆成 `MaterialPanel.vue` + `LayerTree.vue`**。物料卡按原型的几何值做：66px 见方、圆角 11、上层满宽 37px 微缩展台（19px 图标、内外同心圆角 7.5）、下层 11px 标签，hover 抬 1.5px 并把展台染成蓝底蓝图标、按下 scale .96 换 grabbing 光标。分组头 26px，左侧 6px 分类点（沙/天/玫瑰，只区分类别不表示状态），右侧计数徽标 17px 等宽 + 折叠箭头。顶部 `组件物料 ↔ 页面层级` 用平台的 `Segmented`（凹槽底座 + 玻璃滑块，与外壳导航同一件），搜索框 32px 带前置放大镜。层级树按原型画垂直丝线（`--thread`），拖拽手柄与删除只在悬浮或键盘聚焦时显形 —— **手柄本身是可聚焦的按钮**，不然键盘到不了。
- **画布改成所见即所得（`DesignCanvas.vue`）**：不再是一堆示意块，每个区块用它自己在生成物里对应的那件物料渲染 —— 查询区是真的输入行（`TextInput`/`NumberInput`/`DateInput`/`DateRangePicker`/`Select`/`DictSelect` 按控件类型分派，间距与列数读 `gap`/`columns`），表格区是真的 `.table`（勾选列与操作列吸附、列宽照设计、三行骨架占位），行操作按 `priority` 平铺其余进「更多」，表单弹窗是两列的弹窗预览。节点卡语汇照原型：圆角 20、实色面 + 内高光 + `--e1`，hover 换 `--e2`，选中描蓝、加 4px `--blue-soft` 外环并抬起 2px；选中后右上角浮出 26px 蓝色胶囊工具条（名称 + 上移/下移/删除）。落点槽高 6px、命中时涨到 10px 并内描蓝。右上角收纳式视口胶囊（桌面/平板/手机 × 自适应/常用宽度）只改画布宽度，**不进 design_json** —— 它是看的人的设置，不是这份设计的内容。内容整体 `pointer-events: none`：点画布上任何东西都是选中这个区块，而不是在一份还没接后端的设计稿里填一个填不出结果的表单。
- **右栏拆成 `InspectorPanel.vue`**：头部 32px 图标块 + 名称 + 等宽类型码 + 「页面设置」跳转 + 收起；三段 Tab 用 `Segmented`。组件属性只渲染 `PROPS` 白名单里、且当前容器允许的那几格（`queryType` 只在查询区、`required` 只在表单弹窗）；布局类（`columns`/`gap`/`pagination`/`pageSize`/`priority`）归到样式属性 Tab；按钮的权限点由动作推导后**只读展示**，不给手写输入框。小节标题复用平台 `SectionLabel`，青柠光环点用 scoped `::before` 补 —— 那是设计器的语汇，不该塞进公共组件。
- **原型里有、这刀故意没搬的四处**，每处都是「画布上能摆但生成器产不出来」那一类：① 19 种 kind 里那 11 种（栅格容器、插槽容器、分段容器、分割线、数值指标、状态标签、页面标题、按钮组、分页器、弹窗容器、复选框/开关）不进物料库，`design.test.ts` 新加一条把 `PALETTE` 与白名单逐项钉死（多一格少一格都红）；② 表格的「行高密度 紧凑40/标准48/宽松56」切换器不搬 —— 密度是 `data-density` 外观设置，看的人选的，不是这份设计的内容，放进来就是两处事实；③ 圆角阶梯 / 内边距 / 底部外边距 / 表面材质四格不搬，`ComponentCatalog` 不认；④ 事件配置 Tab 保留（三段结构照原型）但只写清「第一期不存事件，按钮行为由动作推导」，不放那套触发时机 + 动作 + JSON 预览的表单。左栏底部的「新建扩展槽」同理没搬 —— 我们没有插槽容器这个类型。
- **`defaultPropsFor` 从组件里挪进 `design.ts`** 并补两条用例：按钮默认必须带 `action`（动作是权限点的来源，空动作的按钮存进去，生成器只能猜这颗按钮要不要种菜单权限），控件默认带标签，区块不带任何东西。
- **顺手删掉一处我自己刚写出来的坏味道**：区块工具条里放了一颗 `disabled` 且没有 handler 的「撤销」按钮 —— 照原型那个位置该是复制/隐藏/锁定，而那三个动作要等 `design.ts` 补 `duplicate`/`visible`/`locked`（刀 3）。摆一颗永远点不动的按钮比少三颗按钮更糟，先删掉。
- **测试**：`design.test.ts` 12→14 例（物料库同源 + 落点默认属性），前端单测 14 + 26 全绿，typecheck 两包与 `vite build` 全绿。
- **真机验收**（`:5173` + `:8080`，页面 59，1600×1000 同域 iframe）：三栏 grid 实测 `236px / 968px / 312px`；物料 13 张卡实测 66×66、三个分组「页面区块 / 表单控件 / 操作按钮」；画布 6 个节点（page + 五个区块）按真物料渲染，表头实测出「订单编号 / 订单金额 / 状态 / 交货日期 / 备注」加勾选与操作两列；点表格区 → 工具条浮出（上移/下移/删除三颗，无障碍名齐全）、节点抬起（top 从 491 起算带 translateY）、右栏头部翻成「表格区 / DataTable」、小节是「表格 + 列」；三段 Tab 逐一切过：样式属性给「布局」两格、事件配置给说明不放假表单、组件属性给 20 格含列编辑器；切「页面层级」实测出 18 行树（页面 → 5 区块 → 子控件带字段码），5 条丝线，手柄默认 `opacity: 0` 且可聚焦。**截图仍然拿不到可见浏览器面**，以上全是 DOM 实测值。


## 2026-11-05（PR 8A 返工：设计器不套外壳，独占整个标签页）

上一条 8A 我把方向判断错了 —— 判断成「复用平台外壳，设计器只补一条工作区工具条」，因为外壳的 `Topbar.vue` 已经有面包屑、⌘K 和外观设置，再画一条顶栏像是重复。口径不对：**点「设计」开出来的那条标签页就是设计器本身**，没有左侧导航轨、没有控制台 header，UI 与原型一模一样。这一条把 8A 的外壳部分重做到那个形状，并把原型里除「所见即所得渲染」之外的浮层一次搬完。

- **路由换形状**：`/lowcode/designer/:页面 ID` 一条静态路由挂在 `AppShell` 之外（`router/index.ts` 的 `staticRoutes`，仍走同一个登录守卫、不是 public）。`pages.vue` 里那套「同路由带 `?design=页面 ID`、列表整屏换成设计器」的装配全删了 —— 深链从查询参数挪进路径，别人拿到 `/lowcode/designer/59` 不用先猜 `design` 是干什么的。取数（`pages.detail` + `models.fields`）跟着搬进 `DesignerView.vue`，打不开就留在原地说明原因，不悄悄跳回列表。
- **原型那条 64px 顶栏整个搬过来**：返回 / 品牌标 / 面包屑（项目 › 页面设计器 › 页面名 + 发布状态胶囊）/ ⌘K 入口 / 撤销重做 / 结构代码 / 深浅色 / 预览运行态 / 保存草稿 / 发布上线。**没有新写一套 CSS**：`.app-frame`（10px 深色机身 + 38px 圆角 + 两团漫射光）、`.brand-mark`、`.crumbs`、`.cmd-trigger`、`.kbd`、`.code-chip`、`.float-bar` 全是 `layout.css` / `components.css` / `instruments.css` 里的全局类，原型那条顶栏本来就是照这套令牌画的，所以复用即一致；`.dz-frame` 只把 `.app-frame` 的两列导航轨改成「顶栏 + 工作区」两行。
- **一份会话收在一处**（`designer/session.ts`）：store、tick、脏状态、浮层栈、拖拽/点选、预览、两栏收起、保存与发布全在这一份里，六个组件读同一个对象。之前用 props 往下传十几格，漏一格的表现就是「画布动了但顶栏撤销还是灰的」—— 上一刀我就真漏了 `canUndo`/`canRedo` 一格，靠给每个读数手动挂 `tick` 才兜住。
- **搬 UI 时暴露的三处缺陷**（都是先复现再修）：① **脏状态是个计数器**，撤销回到基线之后浮条还挂着「有 1 项画布改动尚未保存」；改成与库里那一份逐字比对（`JSON.stringify(store.json()) !== baseline`），并且这个 computed 一开始还没挂上 `tick`，删掉一个区块浮条根本不出来。② **`title="发布「{{ page.pageName }}」`** —— mustache 在属性位不插值，弹窗标题原样显示成模板字符串；改 `:title` 绑定。③ **落点被当场拒绝时我把报错吞了**，还顺手 `act()` 一步空操作 —— 撤销栈里多出一条什么都没改的历史、脏计数白加一格；现在报错原样弹出来，只 `refresh()` 重算读数。
- **自己引入的一处交付链回归（这刀唯一由我弄坏的）**：新路由写在 `scaffold-vue/src/router/index.ts` 里引用 `@/views/lowcode/designer/DesignerView.vue`，而 `router/index.ts` 是要进交付项目的、`views/lowcode/` 是黑名单剔除的 —— `ScaffoldSnapshotPlanner.validate` 的残留引用扫描当场拒绝：`裁剪清单遗漏：交付文件 scaffold-vue/src/router/index.ts 仍引用 @/views/lowcode`，`ScaffoldSnapshotPlannerTest` 12 例全挂。修法是给这一段套 `scaffold:platform-only:start/end` 标记块（`PlatformOnlyStripper` 写快照时整块删掉，标记必须独占整行），而不是把 `router/index.ts` 塞进黑名单 —— 交付项目要自己的路由表。**前端 typecheck、单测、`vite build` 全绿照样看不见这一条**：它只在交付快照初始化时跑。第一次跑全量后端时 `| tail` 把退出码吞成了 0，差点当成全绿过去，第二次改成重定向到文件再看 `$?` 才认出来。
- **后端补三格**：`selectPageBrief` 加 `p.current_version` / `p.publish_status` 与 `LEFT JOIN lc_project` 取 `projectName`。库里 V9 起就有这两列，只是详情查询没读 —— 不补的代价是顶栏那句「已发布 v2」要么写死要么不写，发布弹窗要说清版本号怎么来也拿不到当前号。`PageApiTest` 加三条断言钉住。
- **发布客户端这刀补上**：`POST /lowcode/pages/{id}/publish` 后端在 PR 7 就通了，前端一直没有客户端（`platformApi.pages` 只有 CRUD + detail + updateStatus）。入参只有 `force` 与 `changeDesc`，**版本号由后端按 `current_version` 算**，所以原型里那格「发布版本号」输入框砍掉；「目标运行环境」下拉也砍 —— 环境跟着项目走，切环境是换项目而不是在下拉里选一个，库里没有这个维度。引用体检命中时后端抛的是 `BusinessException`，消息以「就带 force 再来一次」结尾，弹窗按这句话才放出「我已确认，仍然发布」那颗按钮（这层字符串耦合写在注释里，后端换成结构化错误码要跟着改）。发布前如果画布是脏的，弹窗会拦住并给一颗「保存并发布」：发出去的是库里那份，不是画布上这份。
- **预览运行态**：收起两栏、画布摘掉所有 × 与落点缝，只读渲染 —— 这一条按原型是「按发布后的样子看这一页」，第一期先做到「不可编辑」，还没做到「按生成物的版式渲染」（那是刀 3 的所见即所得）。
- **测试**：`platform.test.ts` 新增那条先跑出 `api.pages.publish is not a function` 再实现转绿；前端 typecheck 两包 + 单测 14 + 24 例 + `vite build` 全绿；后端全量 **314 例全绿**（common 10 + framework 42 + generator 6 + lowcode 142 + admin 114，IT 库 3316），其中 `PageApiTest` 6 例带着新加的三条断言、`ScaffoldSnapshotPlannerTest` 12 例在补上标记块之后从全挂转全绿。
- **真机验收**（`:5173` + `:8080`，页面 59，1600×1000 同域 iframe 量）：路由 `/lowcode/designer/59` 下 `.sidebar` 与 `.topbar` 都不存在，机身 `.app-frame` 在；顶栏实测 64px、未滚动时背景透明、只留一条下边线；三栏实测 `236px / 968px / 312px`，面板底色 `#fbfcfc`；结构代码抽屉打开是真 `design_json`（`version 1.0 / pageType list / modelRef sales_order`），深色铭牌底 `rgba(21,24,26,.9)`，脚部读数与「复制结构代码」都在；⌘K 面板 35 条分「指令 / 组件物料 / 画布节点」三组，输入「查询区」过滤到 2 条、Enter 执行、面板自动收起；预览态 grid 变 `0px / 1516px / 0px`、两栏与全部 × 消失、按钮文案翻成「退出运行预览」；删一个区块 → 浮条出现（「画布上有未保存改动 / 放弃改动 / 立即保存草稿」），撤销 → 区块回来、浮条消失、撤销按钮重新禁用。**没有真的发布也没有保存**：弹窗打开后取消，库里那份设计原样。
- **没验到的一格**：本机 `:8080` 那个后端进程还是改动前的类，所以界面上顶栏胶囊显示「未归属项目 / 未发布」，而页面 59 实际已经发到 v2。那三格由 `PageApiTest` 在真接口上验过，**界面上的读数要等后端重启才能看到**；重启会动到 `:8080` 上那个进程（可能是另一个会话在用），没有擅自动它。
- **窄屏仍未处理**：627px 视口下中栏被挤到 0px（236 + 312 已经吃满），归刀 6。


## 2026-11-05（PR 8A：设计器外壳第一刀 —— 按 Figma 新稿重排三栏，顺手修掉三处既有缺陷）

`designer/` 那份 Figma Make 原型（React 19 + Tailwind v4，6846 行）跑起来逐屏点过之后定下口径：**Vue 重写、原型只当视觉与交互规格书、第一期保守到后端 `ComponentCatalog` 白名单以内**（物料 19 种 kind 里生成器产不出的那些不进面板）。这一刀只做外壳，六刀串行，后面依次是左栏、画布、右栏、浮层与发布、窄屏。

- **设计器不再自带一套顶栏**。原型那条顶栏有面包屑、⌘K、深浅色、撤销重做、保存与发布；平台外壳的 `Topbar.vue` + `CommandPalette.vue` 已经给了前三样。两条顶栏同时挂出来，人分不清哪一条说的是「这个页面」、哪一条说的是「整个控制台」，而且 `?design=页面 ID` 与列表同路由，外壳面包屑只到「页面管理」这一层 —— 所以页面名与模型编码留在设计器自己的工具条里，`DesignerToolbar.vue` 只放改这份设计要用的动作。
- **`.designer` 的 `height: 100%` 从来没生效过**：它和 `.content` 之间夹着一层 `.route-frame`，而后者只有 `min-width: 0`、高度是 auto，百分比高度在 auto 高度的父元素上算不出来。表现是整份设计稿把外层 `.content` 撑成页面滚动（实测 1000 高视口下 `.designer` 767px 而 `.content` 476px），三栏不各自滚动、画布那个滚动条永远不出现。改成 `position: absolute; inset: 0` 贴住 `.content`（它是这条链上唯一 `position: relative` 的祖先）。**这条是「画布滚动后工具条凝结」写出来才发现自己成了死代码的根因** —— 先有能滚的画布，凝结才有触发的时候。
- **打开设计器就有可撤销的一步**：`createDesigner(design)` 之后那行 `store.setModel(page.modelCode)` 是一次装配动作，却进了撤销栈。于是刚进来「撤销」就是可点的，点一下 `modelRef` 被抹回 seed 里的空串，而画布看起来什么都没变 —— 下一次保存送出去的是一份没有模型绑定的设计。新增 `bindModel(code)`（任何时候都不产生历史），`setModel` 保留成「人在设计器里换模型」那种该能撤销的动作。用例先跑出 `store.bindModel is not a function`，再实现转绿。
- **`:saving` 是一个不存在的 prop**：`PermissionButton` 的等待态属性叫 `loading`，设计器一直传的是 `:saving`。Vue 不会为未知属性报错，只把它当普通 attribute 落到 DOM 上 —— 于是保存期间按钮既没有等待态也不禁用，双击就是两次 `PUT /lowcode/pages/59`，而第二次可能带着与第一次不同的设计。
- **两栏宽度用设计器自己的令牌**：`--dz-rail-w: 236px` / `--dz-inspector-w: 312px` 定义在 scoped 块里，不写进 `packages/scaffold-ui/src/styles/tokens.css` —— 原型那份 CSS 里的 `--rail-w` 是它自己的左栏宽度，而平台的 `--rail-w` 是外壳导航轨的，改它等于连带改掉所有页面的侧栏。撤销 / 重做两枚图标补进 `data/icons.ts`（24 网格、1.6 描边，与既有 `arrowUp`、`sidebar` 同一套线性风格），原型那两枚是 16 网格，按比例搬过来。
- **收起用 `v-if` 而不是宽度归零**：归零的面板还在 Tab 序列里，键盘要连按好几次才跳过一栏看不见的东西。收起后画布两上角各留一枚展开胶囊，胶囊上是文字不是图标 —— 只留一个图标的话，人得先猜刚才收掉的是哪一栏。
- **测试**：`design.test.ts` 12→13 例；`npm run typecheck` 两包全绿、单测 14 + 23 例全绿、`vite build` 通过。
- **真机验收**（宿主 `:5173` + 后端 `:8080`，`admin` 登录，页面 59「销售订单页」）：1600 宽下三栏实测 `236px / 1072px / 312px`；收起左栏 → grid 变 `0px 1fr 312px`、`.dz-left` 从 DOM 摘掉、画布左上出现「展开组件面板」胶囊，点回原状；画布滚 160px → 工具条凝结（描边换 `--line-strong`、挂 `--e2` 阴影）；深色下工具条与面板底色 `#1b1f21`、字色 `#eef2f1`、凝结描边白 10%、胶囊底 `rgba(30,34,37,.9)` 逐项归位；撤销 / 重做在刚打开时都是禁用态。**截图这次拿不到可见浏览器面（in-app browser 未打开），以上都是 DOM 实测计算值，不是像素比对**；拖拽落点与所见即所得归刀 3。
- **还没做**：① 发布状态胶囊要后端把 `lc_page.current_version` / `publish_status` 放进 `PageRow`，与刀 5 的发布按钮一起接（`POST /lowcode/pages/{id}/publish` 的入参是 `force` + `changeDesc`，原型那格「目标运行环境」库里没有对应字段，砍；「发布版本号」由 `lc_page_version` 生成，不让手填）；② 结构代码 / 预览运行态 / 发布上线三枚按钮归刀 5；③ 原型全站只有 14 处响应式前缀、`index.css` 零 `@media`，627px 视口下中栏被挤到 55px，窄屏归刀 6。


## 2026-11-05（PR 7：页面发布链路 —— 设计到租户仓库的那一跳）

方案 §10.3 的 PR 7 是「发布链路：哈希对比 → 提交 `gen_branch` → 推送 → 打 tag → 写 `lc_publish_log`；no-op 幂等；发布前 C 档引用体检」。这一刀把 §5.1 那张图整个落成 `com.scaffold.lowcode.publish.PagePublishService`，四刀提交：远端 tag 语义、交付裁剪补漏 + 发布主链路、引用体检、文档与本条。

- **哈希对比这件事交给 Git 本身**：产物落盘后 `commitAll` 在没有改动时返回 `null`，那就是 no-op —— 不另算一套哈希。两处各算一次迟早会漂，表现要么是多出一个空提交，要么是「平台说变了而 Git 说没变」的一次假发布。重复点同一份设计：不产生提交、不涨版本、不打第二个 tag，`lc_page.publish_status=2` 说明「内容与仓库一致」。
- **先落到仓库、再记库里的账**：版本号、tag、`lc_page_version`、`lc_publish_log`、`lc_page.current_version`、`lc_project.last_publish_time` 全在推送成功并回读远端确认之后才写。反过来就会出现「库里说发过 v3、仓库里查无此提交」，而 PR 9 的回滚正是照着这句话去取内容的。tag 推不上去（远端开了 tag 保护一类）同样整版不认 —— tag 是合并与回滚的锚点，没有锚点的版本比发布失败更难查。
- **推送失败不留半成品，但也不丢弃真实存在的东西**：远端拿不到半个提交（refspec 不带 `+`，非快进被如实拒），库里落一条 `publish_status=3` 的失败日志、页面状态写成「上次失败」并说明「提交 X 留在本地工作区、重试会把它推上去」；`lc_page_version` 一行都不写。重试时内容没变（`commitAll` 返回 null），但本地领先远端 —— 这一条必须走补推而不是当成无变更放过，否则一次网络抖动就把这个页面永久卡在「库里说没发过、分支上有个没人认领的提交」。用例 `推送失败时不留下成功版本_重试把同一个提交推上去` 钉的就是这个来回。
- **四道现场守卫**：项目 `sync_status` 不是就绪就拒绝（发布不替人初始化，也不会造一个空目录让人以为初始化过了）；工作区目录得是真仓库；**工作区必须干净** —— 提交是整目录暂存，人在工作区里改了没提交的东西会被这次发布一起推进发布分支，那是把不属于他的改动挂到他的动作上；本地与远端的发布分支不一致时按 `last_commit_sha` 分辨是谁前进的：远端仍停在平台上次推上去那个提交 → 上次没推成功，这次补推；否则是别人往发布分支推过 → 拒绝并要人先重建工作区（不强推、不自动合并、不"修好"）。
- **两张表的分工**：`lc_page_version` 只在成功发布时有行（一版一条，带那一刻的设计原文与「路径 → 内容哈希」，PR 9 的取数来源）；`lc_publish_log` 每次动作一条，成功 / 无变更 / 失败都留。草稿里的 `files_added` / `files_modified` / `files_deleted` 与 `remote_url` 不建：计数由 `files_generated` 里每条的落盘动作现算，地址在 `lc_project` 上 —— 沿用 V5/V8 的口径，没有读写方的列不进库。V9 在 IT 库里连跑两遍验过幂等，菜单按钮 5034 `lowcode:page:publish`（能编辑页面不等于能往租户仓库推代码，与 V7 把 `lowcode:project:sync` 单列出来同一个理由）。
- **发布前 C 档引用体检（§5.2 第 2 条）**：从工作区读该模型的 `{Cls}Extension.java`，正则级反查 `getX()` / `setX(` / `.x`，命中「本次生成的 `domain/` 里不存在」的名字就默认阻断发布，列出 `文件:行 + 字段名 + 那一行原文`；人显式 `force` 才放行，而那些命中仍原样留在发布结果里 —— 发出去了，但他得知道自己刚才放过了什么。判据用**生成物**而不是 `lc_field`：主键与审计列是模板补到类上的、字段表里没有，照字段清单判就会把 `row.getCreateTime()` 报成错引用。`package` / `import` 的包名段在文本上长得和「对象.字段」一模一样，任何一份 java 都有这两行 —— **不排掉就是每一次发布都被自己这份清单拦下来，这一条是 IT 第一次跑就撞出来的（九个用例全被这段噪音挡住）**。§5.2 第 1 条「删字段入口就拦」本期没做：那要在工作区可能根本不存在的前提下去读扩展代码，静默跳过等于这条规则时灵时不灵。
- **顺带修掉的三处既有缺陷**：① `ls-remote` 对附注 tag 返回两行，第一行是 tag 对象自己的号，原来只读它 ——「tag 与 `commit_sha` 对得上」这条验收永远对不上，PR 9 按 tag 取内容也会拿着一个不是提交的号去找；现在 `RemoteRef.commitSha()` 给解引用后的提交。② **重铺固定代码会把人写的 C 档从发布分支删掉**：`pruneOutsideSnapshot` 原来按「快照之外即清理」删，而 A 档快照里没有业务产物 —— 人往发布分支推过一行扩展点代码之后，一次「重建工作区 + 重新初始化」就把它删了，而 C 档正是平台唯一产不出来的东西（§5.2「C 档平台永不碰」）。现在放过 `GeneratedPaths` 认得出的 B/C 档形状，这条与 §5.2 那句「PR 7 组工作区时要把这条做对」是同一件事。③ `resetWorkspace` 把「本地 ≠ 远端」一律当成有未推送提交，于是别人往发布分支推过一次之后，重建被挡住、发布也被挡住，项目死锁；现在同样按 `last_commit_sha` 分辨。④ 交付裁剪补回 `scaffold-vue/src/api/__tests__/platform.test.ts`：PR 12 给这个用例加了 `/api/lowcode/pages` 断言却没把它进排除表，**dev 的交付快照从那次合并起就会被残留扫描当场拒绝**（本地全量绿是因为这条只在初始化时跑，而那阵子没有用例走到）。
- 工作区定位规则（`{tenantId}/{projectCode}`）抽成 `WorkspacePaths`：初始化与发布两处不再各拼一遍，两处漂开的表现是「固定代码铺在 A 目录、发布写进 B 目录」这种两边都自洽而真机上什么都没有的错。
- **测试**：`PagePublishApiTest` 9 例 + `ExtensionReferenceCheckTest` 10 例 + `JGitGatewayTest` 多一条 tag 解引用。发布链路**不用 `StubGitGateway` 而用真 JGit 打在本机裸仓库上**（`LocalBareGitGateway`，按仓库地址映射到临时裸仓库）：§10.3 那四条验收全是 Git 语义，桩只能证明平台自己怎么记账、证不了仓库里真有什么。首次发布的产物落盘后真起 `javac` 编一遍 ——「设计驱动的产物」与「编得过的产物」钉在同一条链上（这也把 PR 6 那条「design_json 能喂给模板生成可运行页面」的后半截兑掉了）。后端 **312 例全绿**（common 10 + framework 42 + lowcode 140 + generator 6 + admin 114），IT 库 3316。
- **真机演练（对着真实 GitHub 仓库跑了一遍）**：库里本来就有 6C 期间在设计器里搭出来那条页面（`order-serive` / `sales_order_page`，模型 `sales_order`，5 个引用字段），用本分支的后端把它发了三次：
  - 第一次 `POST /api/lowcode/pages/59/publish` → 远端 `platform-generated` 从 `38d89f3` 前进到 `02b9e49`，tag `vorder-serive-sales_order_page-1` 推上去（附注 tag 对象 `3b7c176`，解引用行指向 `02b9e49` —— 正是 PR 7 补的那个语义），`main` 全程停在 `36c8021` 没被碰；库里 `lc_page_version` v1 + `lc_publish_log` 成功一条 + `lc_page.current_version=1` + `lc_project.last_commit_sha` 与远端逐字相同。那次提交实际只动了 5 个文件（Controller / Mapper / Service / 菜单 SQL / 列表页），另外 6 份与仓库里已有内容逐字节一致 —— 「设计驱动的 diff」本来就该是这个量。
  - 同样的设计再点一次 → `status=2`、`version` 仍是 1、`tagName` 为空、远端既没有第二个提交也没有 `tag-2`：no-op 幂等在真仓库上成立。
  - 修完下面两处缺陷后发第三次 → v2（`f53b2a3` + `tag-2`），产物里菜单权限点变成 `list/add/edit/remove` 四个、页面大标题变成「销售订单页」。
- **真机产物暴露的两处 PR 6B 合流缺陷**（都先写成红用例再修，`DesignGenerationTest` 现 7 例）：① `menuButtonsOf` 把行操作并进工具栏那份清单时写成了交集（`filter(actions::contains)`），而编辑 / 删除通常只出现在行上 —— 于是菜单只种了 `:add`，生成页面两颗按钮照渲染、Controller 上 `@RequiresPermission(:edit / :remove)` 也在，项目里的角色拿不到这两个权限点，**按钮看得见、点下去 403**；② `PageDesign.title()` 在画布没填标题时兜到 `modelRef`，客户页面的大标题显示成 `sales_order` 这样的英文编码（画布自己在 `9f20981` 已经改成兜页面名，生成物没跟着同一口径）。这两处各段单测都只看半边：`DesignGenerationTest` 原来只断言「设计里没有删除就不该有 `:remove`」，那句拿错的实现同样是绿的；页面标题则从没被断过。
- **还没做/没验的**：① 设计器 UI 正在重构，「在页面上点发布按钮」这一段没验（接口层与真仓库那一跳都是真东西）；② `lc_page_version.publish_type` 只写 `CREATE` / `UPDATE`，页面下线的 `DELETE` 与 PR 9 的 `ROLLBACK` 都还没有产出方。、


## 2026-11-05（PR 6 前两刀：页面设计入库，生成期把「形」与「料」合流）

方案 §10.3 的 PR 6 是 `lc_page` / `design_json` + 设计器第一期。这一趟先做完后端那半边（设计器 UI 与端到端演练进后续两刀），落三条提交。

- **设计只在保存时对模型，不留到发布**：新增 `com.scaffold.lowcode.design`，`PageDesign.parse(json, DesignContext)` 在写库之前把设计与 `lc_field` 逐格对完 —— 结构借方案 §3.4 的搭建协议形状，**不接 LCE 全协议**（§10.2 第 4 项落定：本项目走代码生成路线，没有生产运行时要渲染这份 JSON，引进协议等于为一套不需要的能力付全套复杂度）。18 例单元用例（`PageDesignTest`）每条都对应一类「编得过、单测绿、真机才炸」：协议版本与页面类型不认识就拒、组件白名单外（部门/用户/上传/开关/读数这类取数来源不由模型推导的控件第一期不产出）、控件必须待在允许它的容器、`field` 必须是模型真实字段且报错带路径与可用清单、同区块不重复绑定、按钮只认内置动作（`custom:` 因为前端 `overrides/` 的覆写机制还没定稿而拒绝、`view` 因为详情页模板还不存在而拒绝）、手写 `permission` 拒绝、`render` 与字段类型相容（`tag` 要挂字典、`money` 要数字）、`queryType` 与控件相容（区间只在日期与数字上成立、`LIKE` 只给文本、`DateRangePicker` 只能 `BETWEEN`）、属性只认这个组件支持的那几个（`dictType` / `precision` 明确拒绝：字典与小数位是模型侧独有事实，5G 那个金额静默取整就是两处事实打架的结果）、容器两层与节点数 200 的上限。报错统一带节点路径（`layout.children[0].children[1]`），设计器要照着它高亮画布上的方块。
- **`lc_page` 只建有人读的列**：V8 建表时把方案 §2.2 草稿里的 `route_parent` / `menu_id` / `menu_parent_id` / `menu_visible` / `permission_prefix` 与发布那一组（`current_version` / `publish_status` / `last_publish_time` / `publish_message`）连同 `lc_page_version` 一起留到 PR 7 —— 沿用 V6 那条口径：没有读写方的列不进库。权限点不进 `lc_page` 是因为它由「模型编码 + 动作」推导，存第二份就会出现「菜单里权限点齐着、页面上按钮永远灰」。`route_path` / `menu_icon` / `menu_sort` 留空表示由生成器派生，不在库里再算一遍。`ref_fields` 兑现 §1.5 的反查：删字段与下线模型现在会说清「哪几个页面还在用」（`销售订单列表（sales_order_list）`），软删的页面不算还在用；`PageApiTest` 6 例走真接口验权限码、租户隔离、编码唯一与这条反查。
- **生成期合流（§1.5 ②）**：`GenerationInput.of(model, fields, PageTarget)`。有设计时，查询区有哪些条件、表格有哪些列按什么顺序叫什么、表单收哪几格、页面上有哪几个按钮全由 design_json 说；类型、DDL、约束、权限点前缀仍由模型说 —— 页面是形、模型是料，两者在同一份模板里合流。几个不显然的口径：写入语句的列 = 设计表单 ∩ 模型允许写入（两边都收紧，谁都不能单独放开，否则表单收了模型不让写的列就是静默丢值）；主键不进列但恒进 `Row`（`openEdit` 与 `remove` 都读它）；前端必填 = 模型必填或设计标必填（只能收紧），后端注解只认模型，两边不一致时以数据库约束为准；`export` 不产出按钮（导出是 `ListPage` 表头的固定能力，再放一个是两个入口），但设计里没有 `export` 就不接 `exporter`，表头那颗按钮随之消失；菜单里种哪些权限点跟着设计里真出现的动作走，没有删除就不种 `:remove`。`DesignGenerationTest` 5 例最后一例专门钉「没有设计的那条路（zip 下载与老代码生成页）形状与 PR 5 逐字节一致」—— 合流不能把既有链路一起改掉。
- **入库前又补两条入口拦截**：有 `create` / `edit` 按钮就必须画 `FormContainer`（否则点开是一台空弹窗，页面看着能新增其实不能），画了弹窗就必须收齐「NOT NULL 又没有默认值」的可写字段（表单没收这一格，生成的 INSERT 就少这一列，新增点保存是运行期 500，而页面上看不出任何问题）。判据与建表语句的 `ddlNull` 同源，两处不同口径这条规则自己就会打架。
- **第一期定死一个模型只有一个列表页**：生成物落点是 `views/{module}/{Cls}.vue`，由模型类名派生。放两个列表页进同一模型，设计器里看着两份设计都能生成，交付项目里只剩一个文件 —— 后一次发布整文件盖掉前一次的产物，这种「看着都对」的覆盖比报错难查得多。§1.5 说的「一个模型多个页面」是列表 + 详情 + 表单三类，第一期只落了列表这一类，所以按「模型 × 列表页」唯一在新增与编辑两处都拦，报错带着已占位的编码，软删后可重建。
- 后端 **289 例**全绿（common 10 + framework 42 + lowcode 127 + generator 6 + admin 104），IT 库 3316。**未做的部分**：设计器 UI（三栏 + 流式拖拽 + 属性面板）、§10.3 那条验收「用设计器搭出一个订单列表页，产出的 JSON 喂给 PR 5 模板生成可运行页面」与交付快照物化演练 —— 这两件是 PR 6 的 6C / 6D，本 PR 只到「JSON 手填也能生成得出可运行页面」这一步。


## 2026-11-04（交付品牌跟着项目走，生成物话术里不再出现平台名）

5G 真机演练时顺手看的一眼：客户项目的登录页大字写着「元搭低代码平台」、侧栏写着「元搭低代码」、浏览器标签是「销售订单主表 · 元搭低代码平台」，登录页还在宣传「代码生成 · 导入数据表」（那个页面属 `scaffold-generator`，交付时已被裁掉）。交付的是**这个项目自己的后台**，不是平台的一个副本，所以这不算外观问题。

- **品牌只有一份**：`scaffold-vue/src/data/index.ts` 里的 `APP` —— `name` 进登录页大字与浏览器标签（`router/index.ts` 拼 `title · APP.name`），`shortName` 进侧栏。所以改写也只钉这一处：`ScaffoldTrimPolicy` 的交付改写表新增一条 `APP.name` / `APP.shortName` 两行，值用新占位符 `{PROJECT_NAME}`，由 `ScaffoldSnapshotPlanner.plan(sourceRoot, scaffoldUiVersion, projectName)` 在初始化时按 `lc_project.project_name` 填入 —— 底座那份照旧留着（平台自己的界面要显示平台名），只有交付出来那份被换掉。项目名是人在页面上打的自由文本，进 TS 单引号字面量前按 `tsLiteral` 把反斜杠和引号翻倍。项目名为空当场拒绝：交付一份没有名字的品牌比报错更难发现。
- **不吹交付里没有的功能**：登录页 `CAPABILITIES` 里那条「代码生成」同样走改写表删掉 —— `scaffold-generator` 与 `views/tool` 在裁剪清单的黑名单里，留着就是向客户宣传一个点不开的菜单。
- **生成物的话术跟着改**：11 份模板里的注释与页面文案原样写着「由元搭低代码平台按模型定义生成」，客户每天在自己的源码里读到平台名；「整页接管见方案 §5.2」更糟 —— 方案文档属平台、不进交付清单，读者按图索骥翻到一个不存在的文件。统一改成「由低代码设计器按模型定义生成」，§5.2 那句换成一句能执行的话（要长期接管就改名并让发布不再产出它）。
- **守卫**：`ScaffoldSnapshotPlanner.validate` 多扫一组品牌关键字（`元搭低代码平台` / `元搭低代码`），交付产物里出现就拒绝初始化，消息说清要么补改写、要么把它裁掉 —— 与平台代码引用残留同一套做法。用例：`ScaffoldSnapshotPlannerTest` 新增 2 例（按项目名改写、交付里不出现平台品牌与被裁功能），`ModelCodeGeneratorTest` 新增 1 例（11 份产物不含品牌关键字与方案章节引用，**这条是先跑出红的**：改实现前它列出了模板里的原话），`DeliveryGenerationApiTest` 走真初始化链路再钉一遍（工作区里的 `data/index.ts` 是项目名、`Login.vue` 没有「代码生成」、生成物不含「元搭」与「方案 §」）。
- **真机复核**：把那两条交付改写应用到还在跑的演练容器上重建前端镜像（`vue-tsc` + `vite build` 过），浏览器里 `document.title` = 「登录 · 订单中台」→ 登录后「工作台 · 订单中台」，侧栏大字 = 「订单中台」，能力条目只剩角色与按钮权限 / 数据权限 / 操作审计三条，生成页面的说明行是「本页由低代码设计器按模型定义生成；要改字段回到设计器改模型再发布。」，整页 `body.innerText` 里搜不到「元搭」。
- 后端 **263 例**全绿（common 10 + framework 42 + lowcode 106 + generator 6 + admin 99）。品牌与话术属 A 档与产物文本：**存量项目要等 PR 8 的底座升级才拿得到品牌改写**，而它们已有的生成物注释里带着平台名，那部分只能靠下次发布覆盖 B 档文件时顺带改掉（C 档扩展点是人工写的，平台不碰）。

## 2026-11-04（PR 5G：端到端真机验证，一次跑撞出六处接缝缺陷）

方案 §10.3 那条 5G 写的是「建项目 → 建模 → 生成 → 发布 → 合并 → 干净机器 `docker compose up`」。发布与合并是 PR 7 / PR 8 的范围，还没实现，所以这一步的实际形状是：**链路里已有的段全用真东西**（真实交付快照、真实模型接口、真实工作区提交、真实 GitHub 仓库的 clone、真实容器），只有「发布」那一跳由一次性脚本人肉顶上，跑完删除 —— 这趟演练的目的本来就是把接缝上的错找出来，顺便把 PR 7 要补什么钉清楚。

- **常驻的端到端用例**：`DeliveryGenerationApiTest`（`scaffold-admin`，5 例）。建项目与初始化走真接口，交付快照由 PR 4 那份裁剪清单落进临时工作区，模型与字段也走公共接口配（**列表接口的出参就是生成器的入参形状**，所以它同时钉住「设计器里配的东西能被生成器原样吃到」），然后 `renderFiles` → `GeneratedFileWriter.write` 落进那份真实工作区。断言全挑单段测试看不见的接缝：`scaffold-business` 的 pom 还在且被父 pom 聚合（模块被裁剪清单误删时，产物只是一堆不参与构建的源文件）、页面里 `@/api/...` 指向的文件确实存在、菜单的 `component` 能解析到 `scaffold-vue/src/views/...`、权限点在菜单与 Controller 上逐字相同、物料包的具名导出与版本号对得上、二次发布时 B 档覆盖而 C 档保住人工加的方法。`ProjectWorkspaceApiTest` 里的假远端提成公用的 `StubGitGateway`（两处都要「能 clone、能提交、能推」的桩，真 JGit 语义仍在 `JGitGatewayTest` 对着本机裸仓库验）。
- **撞出来的六处，按严重程度排**（每一处都是「编得过、单测全绿、真机才炸」）：
  1. **小数列的输入框把金额抹成整数**（数据错，且不报任何错）。`NumberInput` 不传 `precision` 时按 `step`（默认 1）推断小数位，于是 `decimal(18,2)` 的列输入 1234.5 提交上去就是 1235；查询区区间两端同样被取整，查出来的集合也不对。修在派生层：字段模型带出 `numberPrecision`（取列的标度），表单与查询区两处 `<NumberInput>` 都带上 `:precision`。库里的证据留着一行：`SO-UI-0100 = 1235.00`（修复前建的）与 `SO-DEC-1 = 1234.50`（修复后建的）。
  2. **自增主键进了新增表单**。字段接口把「可录入 / 可编辑 / 必填」默认全开，主键也不例外，于是页面要求人填主键，`WriteRequest` 还带一个改不动的 `id`；更硬的是模板的类型里另外硬编码了一行 `id?: number`，同一个名字声明两遍（一条 `number | undefined`、一条 `number | null`），交付项目 `npm run build` 在 `vue-tsc` 阶段报 TS2300 / TS2717 / TS2322，**前端镜像根本构建不出来**。三处一起改：`LcFieldService` 存自增主键时压掉那三格（设计器里显示的就是真值，不留「勾了但没生效」）、`GenerationInput` 组 `formFields` 时排除 `primary && autoIncrement`（老链路的存量配置可能还勾着）、「哪一行在编辑」改由独立的 `editing` ref 记着（自增主键不在表单里，业务编号主键新建那条也填了值，两种情况都不能拿表单的值当判据）。改这条时又踩一个小坑：`editing` 在 `<script setup>` 里要写 `editing.value`、在 `<template>` 里必须写 `editing`（SFC 编译器只在模板上下文自动解包 ref），写反了单测看不出来 —— 产物只是字符串 —— 交付项目 `vue-tsc` 报 TS18047 / TS2551，是第二轮构建抓到的。
  3. **`@Options(keyProperty = "id")` 回写不了自增号**。这个方法挂审计列时还多收一个 `operator`，MyBatis 多参数时要求属性名点出参数名（底座自己写的是 `keyProperty = "row.id"`），真机点第一次「新增」抛 `ExecutorException` → 500。这条过去被 `CrudTemplatesTest` 按模板原话钉死了 —— 模板与测试一起错，形状断言与 javac 都无感知。
  4. **列表与导出没有审计列**。页面在审计开关打开时多出「创建时间」列，`Row` 也声明了这四个字段，但 `selectRows` 只选勾选进列表的字段，CSV 也按同一份拼列 —— 真机上那一列每行都是「—」，导出文件里干脆没有这一列。补 `selectRows` 与 `CsvUtils.csv` 的审计段。
  5. **CSV 里的时间带 `T`**。`CsvUtils.cell` 直接 `String.valueOf`，同一个值在页面上是 `2026-11-04 20:46:05`、在导出文件里是 ISO 的 `2026-11-04T20:46:05`。统一在 `CsvUtils` 里收（`LocalDateTime` / `LocalDate` 按 `@JsonFormat` 那两种规范串写），新配 `CsvUtilsTest` 2 例。
  6. **`.vue` 里接口引用的路径与文件落点分家**。页面写 `@/api/{module}/{resourceName}`（驼峰）而文件是 `{resourceSlug}.ts`（蛇形），单字词夹具（`order`）看不出来，两个词以上就断。修模板用 `resourceSlug`，并把「import 的目标必须真实存在」写进上面那条 e2e。
- **两处口径当场定死**（写进方案 §4.5）：① **只支持数字主键** —— 五个入口的定位参数是 `@PathVariable Long id`，字符串主键编得过但调不通，`GenerationInput` 进模板前就抛错说明怎么办（业务编号做成唯一列 + 另加数字主键）；② **列类型的完整写法只拼一次** —— `lc_field.column_type` 存的已经是 `varchar(64)`（`FieldType` 算好的），老链路给的是 `information_schema` 的裸 `DATA_TYPE` + 长度列，`GenerationInput` 按长度再拼一次就成了 `VARCHAR(64)(64)`，人在目标库执行第一次就是语法错误；已经带括号就原样收下。
- **交付链的两处 A 档改动**（要等底座升级下发给存量项目，属 PR 8 范围）：① `scaffold-system/Dockerfile` 的 `mvn package` 加 `--mount=type=cache,target=/root/.m2` —— 干净机器上第一次构建后端镜像光下载 Maven 依赖就 14 分钟（交付项目的 Dockerfile 没有依赖层，改一行业务代码就重下一遍），加挂载后同一趟构建 **4.7 秒**；② `CsvUtils` 的时间规范串。这两处不在页面发布权限内（B/C 档），项目侧要等一次底座升级才拿得到。
- **真机验证记录**：空目录 `git clone` 交付仓库的 `platform-generated` → 写 `.env`（`WEB_PORT=8099`、`MYSQL_HOST_PORT=3327`，本机 3306/3316 都被占着）→ `docker compose --profile mysql up -d --build` 四个服务全 healthy，内置 MySQL 首次建卷自动导入 V1–V3 → 手工执行生成的 `sales_order_table.sql` 与 `sales_order_menu.sql`（客户端带 `--default-character-set=utf8mb4`，中文注释入库正常；**两遍执行结果一致**，菜单仍是 4 条、授权仍是 4 条，`SHOW CREATE TABLE` 与模板给的一致）→ 浏览器登录 → 侧栏出现「销售订单主表」→ 页面按 `ListPage` 版式渲染（查询区四个条件、八个列、导出 / 刷新 / 分页都在）→ 新增弹窗里没有主键格，填 `1234.5` 落库仍是 `1234.5` → 列表「创建时间」有值 → 修改 / 唯一列查重（400「订单编号已存在」）/ 必填校验（400「订单编号不能为空」）/ 导出 CSV（表头带「创建时间」）/ 删除后详情 400「销售订单不存在或已删除」而库里 `deleted=1` 行还在，全部走通。
- 测试规模：后端 **260 例**全绿（common 10 + framework 42 + lowcode 103 + generator 6 + admin 99，基点已含 PR 9 那趟租户权限边界）；新增或改写的用例是 `DeliveryGenerationApiTest` 5、`CsvUtilsTest` 2、`FrontendTemplatesTest` +3（自增主键、业务主键、小数精度）、`CrudTemplatesTest` +1（审计列，另把导出那条的期望改成带「创建时间」）、`SqlTemplatesTest` +1（列类型已是完整写法）、`ModelCodeGeneratorTest` +2（引用落点、非数字主键拒绝）、`ModelApiTest` +1（自增主键的写入位被压掉）。
- **没在这一步做的**：① 发布入口（`lc_publish_log`、哈希对比 no-op、C 档引用体检）仍是 PR 7；② 底座升级下发上面那两处 A 档改动是 PR 8；③ 交付项目的品牌与生成物话术还带着平台名 —— 已收在下一条（同日「交付品牌跟着项目走」）；④ 前端 `overrides/` 的加载机制仍未定（§10.2 开放项 3）。



## 2026-11-04（租户权限边界收口 + 内置只读成员角色）

起因是「新注册租户拥有全部权限」这条验收：核对下来功能已经齐（建租户 + 复制业务菜单树 + 内置管理员角色全量授权 + 建管理员 + 直接回令牌），但**全权限的 `tenant_admin` 本身就是一条通向平台的旁路**，顺手把它验了出来。

- **越权路径是先跑出红、再修的**，三条都在 `TenantRoleCaliberApiTest` 里留了钉子：
  - ① `createRole` 的角色码唯一性由租户拦截器注入条件，只在**本租户**内查 → 租户管理员可以在自己租户里建一个 `role_code = super_admin` 的角色绑给自己，而 `SystemPermissionResolver` 原先只看角色码就给全量放行。红跑出来的实证是这个人能 `GET /api/platform/tenants` 拿到**全部租户**（`lc_tenant` 是平台表，不带 `tenant_id`）。
  - ② `createMenu` / `updateMenu` 的 `permission` 与 `path` 是自由文本 → 造一条 `permission = platform:tenant:list` 的菜单授给自己的角色，同样进得去平台接口；平台侧的守卫只有 `@RequiresPermission`，没有第二道租户判定。
  - ③ `/api/auth/switch-tenant` 原先也只看角色码 → 伪超管能直接把令牌切进任意租户。
- **口径落在 `framework/security/PlatformBoundary` 一个类里**，避免四处各判一遍：保留角色码（`super_admin` 只归平台租户；`tenant_admin` / `tenant_member` 归开通流程内置，任何租户都不能手工占用）、平台菜单段（`/platform` 路径段与 `platform:` 权限码前缀，与 `LcProvisionMapper` 复制模板时的排除条件同一口径），以及 `isSuperAdmin(账号归属租户, 角色码)` —— 角色码是 `super_admin` **且账号归属平台租户**才算超管。放行判定的四处（`SystemPermissionResolver.hasPermission`、`AuthService.currentUser` / `routes` / `switchTenant`）全部换成这一条，平台管理员带着超管身份切进租户看数据的既有用法不受影响。保留码校验排在唯一性判定之前，否则租户里已存在的内置角色会把原因盖成「角色编码已存在」。
- **新租户自带两个内置角色**：`tenant_admin`（全部业务菜单，含写操作按钮）与新增的 `tenant_member`「租户成员」（只授 `menu_type <> 'btn'` 的目录与页面节点，即 `:list` 能拿、一个写按钮都拿不到；`data_scope = ALL`、`builtin = 1`）。给新租户预置只读角色的理由是注册者进门第一件事就是建用户，而用户必须挂一个角色 —— 只有一个全权限角色等于逼人把权限给满。注册开通、平台手工新增租户带管理员、「补齐租户资产」三条链路共用同一套 `TenantProvisionService`，**存量租户要在租户管理点一次「补齐租户资产」才会多出这个只读角色**（用例里专门验了这条：删掉成员角色再补齐，能原样补回来，重复补齐不翻倍）。
- **交付快照的裁剪清单跟着补一项**：`TenantRoleCaliberApiTest` 引用了 `/api/platform`，被 `ScaffoldSnapshotPlanner.validate` 当场拦下（「裁剪清单遗漏」），加进 `ScaffoldTrimPolicy` 的平台侧验收用例黑名单 —— 这条校验是有效的，不是摆设。`Roles.vue` 里内置角色那一格原来固定显示「全部权限」，现在有了只读内置角色，文案改成「内置授权」（提示改为权限由平台开通流程维护）。
- 验证：**先红后绿**。`PlatformBoundaryTest` 25 条参数化断言（保留码只挡该挡的、`/platforms/keep` 这类业务路径不误伤、超管判定要同时满足码与归属）；`TenantRoleCaliberApiTest` 8 条 e2e 在实现前跑出 5 红 + 2 错（红的原因正是待实现的那件事：伪超管读平台接口是 200、`super_admin` 角色能手工建、菜单能挂 `platform:` 码、注册出来的租户没有 `tenant_member`），实现后 8 条全绿。第 8 条是补的口径钉子：**角色是租户私有物料**，`sys_role` 在租户拦截器白名单里，超管停在平台租户时角色管理页只看得到平台自己的 6 个种子角色，看不到也分配不了各租户的 `tenant_admin` / `tenant_member`（对称地，平台那几个角色租户里也看不到）；要管就走租户管理的「进入该租户」（`/api/auth/switch-tenant`，顶栏会显示「已切换」），切进去之后能看见内置角色也能给人挂上，但拿着别租户的 roleId 在平台租户里授权仍然 400。后端全量 `mvn test` **244 例**（common 8 + framework 42 + lowcode 96 + generator 6 + admin 92）BUILD SUCCESS，含既有 `TenantRegisterApiTest` 3 例、`TenantIsolationApiTest` 11 例、`AuthApiTest` 8 例、`ScaffoldSnapshotPlannerTest` 10 例；前端 `npm run build`（`vue-tsc` + `vite build`）与 `npm run test:unit` 通过。e2e 用 IT 库（`DB_PORT=3316`、`REDIS_PORT=6380`），用例注册的租户按 `tenant_id` 全部清理，库里不残留。本次没有改前端行为，只有 `Roles.vue` 那一处文案，未在浏览器里人工点验。
- **已知边界（没在本次改）**：① `sys_role_menu` 只保证「授权对象是本租户菜单」，租户内的角色分配仍然全能 —— 只读角色给的是默认档位，租户管理员照样能新建一个更宽的角色；② 保留角色码判定在 Java 侧是大小写敏感的，与 `selectRoleCodes(...).contains(...)` 同一口径，MySQL 的 `utf8mb4` 排序规则却不区分大小写，所以库里进不了一个只在大写形式上撞名的角色，但它也不会因此获得超管身份（判定看的是 Java 侧的精确匹配）；③ 平台菜单段的排除在 SQL 侧仍写作 `path LIKE '/platform%'`，比 Java 侧的段判定宽（会连 `/platformx` 一起排除），对复制模板这条路没有实际影响，口径统一留待平台菜单表结构调整时一起做。



## 2026-11-04（PR 5F：老代码生成页改吃同一套模板，TDD 第一趟）

`scaffold-generator` 那套 StringBuilder 全删了：`gen_table` / `gen_table_column` 先由 `ImportedTableInput` 适配成模型形状，再交给与低代码链路同一个 `ModelCodeGenerator`，zip 只是换了个出口（`GeneratedFileZip`）。方案 §4.4 说的「生成逻辑不动，只换 sink」到这里才真的成立。

- **这是新规矩（`AGENTS.md`「测试与验收」）下的第一个任务，先写红再实现**：先落 4 条 e2e，跑出 3 条红 —— ① 产物路径是想象出来的 `backend/src/main/java/...` 与 `frontend/src/views/...`，不是交付项目的真实目录；② java 文件只有 4 个（缺 `Row` / `WriteRequest` / `Extension`）；③ `date` 列的实体属性是 `LocalDateTime`。第 4 条（列表与导入）本来就是绿的。然后才动实现把它们转绿。
- **类型口径回到一份事实源**（方案 §2.2 之二那条连带项落地）：删掉 `GeneratorService.javaType()` / `formType()`，改为 `FieldType.ofColumn(dataType)` 反推。为此给 `FieldType` 补了 `LONG` 一格 —— 库里非自增的 `bigint` 列（外部 ID、以分计的金额）原来没有对应类型可选：`INTEGER` 只在自增主键时升级成 Long，于是老链路只能自己另算一份，两条链路对同一个 `date` / `bigint` 列给出不同答案。`FormType` 顺带补 `code()` 与 `FieldType` / `QueryType` 同口径。
- **事实从库里现查，配置仍留在库里**：`selectDatabaseColumns` 补 `column_key` / `extra` / `is_nullable` / 长度 / 精度 / 标度。主键与自增过去靠「列名叫 id」猜，猜错就生成出定位不到记录、INSERT 顶掉 `AUTO_INCREMENT` 的代码。反过来，进不进表单、进不进列表、查询方式这些**配置**仍读 `gen_table_column`（页面上存过的优先），存的控件名是改造前的中文时退回该类型的默认控件，而不是一律降成输入框。物理表已经不在了（IT 库里就有一条这样的 `it_purchase_order` 夹具行）时 preview 直接 400 说明原因 —— 出一份指向不存在表的代码没有意义，这条由 `PortalModulesApiTest` 钉住。
- **底座公共列怎么处置**：审计四列齐且类型对得上 → `enableAuditField=1` 并且不再作为字段出现（否则实体里会出现两个 `createTime`）；只齐一部分（日志表、第三方建的表）→ 当普通列但**不给写**；有 `deleted` 整型列 → `enableLogicDelete=1`；`tenant_id` 只读且不进列表 —— 表单里给人一个挑租户的下拉，等于给一次越权的机会。
- **数字列不再 LIKE**：`FieldType.defaultQueryType()` 只给文本列模糊匹配。老链路命中列名带 status / type 就一律 `LIKE`，生成出 `t.status LIKE CONCAT('%', ?, '%')` —— 索引用不上，读出来也不是人期望的等值结果。
- **接口与命名跟着换成定稿口径**：产物落点 `scaffold-system/scaffold-business/src/main/java/com/scaffold/business/{module}/...`、`scaffold-vue/src/api/{module}/{resource}.ts`、`scaffold-vue/src/views/{module}/{Cls}.vue`、`scaffold-system/sql/pages/{表}_table.sql` 与 `_menu.sql`；接口路径单数下划线（`/api/demo/product`），权限点 `demo:product:list` 一套四个。`GeneratorPipeline.vue` 的 `FORM_LABEL` 改按枚举名取标签（存进库的值变了）。
- 依赖方向：`scaffold-generator` → `scaffold-lowcode`（模板与派生层住在 lowcode，方案 §4.4），反向不依赖，无环。本模块 pom 顺带补上 `spring-boot-starter-test`（它以前没有测试目录）。
- 验证：**先红后绿**，`GeneratorImportApiTest` 4 例（清单与导入、交付形状清单、类型口径与公共列、zip 里 7 个 java 真起 javac + 菜单 SQL 在真库里跑通并回滚，建一张 `demo_product` 现删）、`ImportedTableInputTest` 6 例（公共列判定、事实现查、配置优先级）、`FieldTypeTest` +2 例（`ofColumn` 反推表、LIKE 只给文本列）。后端 **212 例**全绿（common 8 + framework 17 + lowcode 96 + generator 6 + admin 85），前端 `vue-tsc` 通过；IT 库跑完无残留（`demo_product` 与 `gen_table` 里的测试行都清掉了）。
- **已知边界（没在本次改）**：① 老链路没存唯一索引信息，`isUnique` 一律 false，所以生成的建表语句不带 `UNIQUE KEY`、Service 不生成查重 —— 要唯一性判定就走低代码链路的模型；② 导入 `sys_file` 这类多租户表时 `tenant_id` 只是普通只读列，交付项目的租户拦截器认的是 A 档硬编码白名单，生成代码不会自动补这个列（§5.2「一个项目一个库」口径下的既有限制）；③ `gen_table_column` 里改造前导入的旧行，其 `java_type` 快照仍是旧口径 —— 生成时不读它（现查库），只有页面的映射图按存值画，重新导入一次就一致了。

## 2026-11-04（PR 5E：A/B/C 分档落成代码、C 档扩展点骨架、两条 sink）

模板链到这里收成一个出口：`ModelCodeGenerator.renderFiles(项目行, 模型行, 字段行)` → `List<GeneratedFile>`，两条 sink 吃同一份产物（方案 §4.4「生成逻辑不动，只换 sink」）。

- **档位是从路径反推的，不是模板自述的**：`GeneratedPaths` 把方案 §4.3 那张表落成代码，`GeneratedFile.of(path, content)` 是唯一构造入口，档位由 `GeneratedPaths.tier(path)` 判。理由是让「这次发布会不会覆盖这个文件」只有一个出处 —— 模板各自声明档位，就要人工在 11 份 .ftl 和分档表之间对齐，错一次就冲掉人的代码。判档用**白名单形状匹配**（正则逐条列 B/C 的形状）而不是前缀排除：`scaffold-vue/src/api/` 前缀下就住着 A 档的 `index.ts` 汇总出口，按前缀放行等于把 §10.2 第 ⑤ 条的约定写破。认不出来的路径当场抛错，消息里点名 A 档都有哪些。`{Cls}Extension.java` 也落在 `service/` 下，形状是 B 档的子集，所以 C 档先判。
- **C 档骨架 = `backend/Extension.java.ftl`**：七个钩子 —— `afterRows(List<Row>)` 一个（列表与导出共用）、create/update/delete 各 `before*` / `after*`。B 档 `Service` 的构造器把它当依赖（`OrderService(OrderMapper, OrderExtension)`）并逐个调用。这是方案 §5.2「钩子方法签名是平台契约，改名或删方法下次发布直接编译失败（可见失败优于静默改行为）」的落地：契约成立的前提是两边都生成、都进同一次发布，`GeneratedCodeCompilesTest` 现在把 7 个 java 文件一起交给 javac，就是这个保证。骨架里写明**钩子都在 Service 的 `@Transactional` 里跑**，`after*` 抛异常会连带回滚 —— 「先落库再通知外部系统」得自己处理补偿，这条不写人就当 `after` 是提交后执行。
- **两条 sink 的语义**：`GeneratedFileWriter`（git 侧，落进工作区）按档走 —— B 档整文件覆盖、C 档「存在即跳过」，返回每个产物 `CREATED` / `OVERWRITTEN` / `SKIPPED` 供发布日志汇总；落盘前再判一次路径不越出工作区（`resolve` 遇上绝对路径会直接丢掉工作区前缀，这层不看模板而看磁盘）。`GeneratedFileZip`（zip 侧，给人手工取产物）**照样带着 C 档骨架、不做跳过** —— 下载方要不要覆盖自己那份扩展点由人判断，平台在这条路上代替不了。
- **「存在即跳过」有一条前提，写在 javadoc 里而不是藏起来**：调用方给的工作区必须反映项目当前的内容。发布分支落后主干时，主干上人工改过的扩展类在这份工作区里不存在，骨架会被重新生成，两边在合并请求里以 `both added` 冲突暴露 —— 不静默丢代码，但要 PR 7 组工作区时把这条做对。
- **明确拒绝两个还没实现的模型开关**：`isTree` / `enableDataScope` 在 `renderFiles` 进模板**之前**抛错，消息指向 C 档手写或取消勾选。不选「忽略勾选、把能生成的生成出来」：那两个开关是人在设计器里明确表达的需求，静默忽略等于交付一个「看着配置对了、实际没有」的项目，比报错更难发现。库里那两列的注释还留着「生成 /tree 接口」「生成 @DataScope」的说法，本期不改表（注释记的是列的设计意图，边界当前写在生成入口与 UI 上，比 ALTER 一次 COMMENT 更靠近读的人）；`models.vue` 两个开关的 hint 改成「本期不生成：勾选后发布会被拒绝」。
- 验证：新增 `GeneratedPathsTest` 5、`ModelCodeGeneratorTest` 5、`ExtensionContractTest` 4、`GeneratedFileWriterTest` 4、`GeneratedFileZipTest` 3，共 **21 例**；`GeneratedCodeCompilesTest` 改吃 `renderFiles` 的出参（不再自己维护一份模板清单），`TemplateFixture` 里没人用的 `BACKEND_TEMPLATES` 常量删掉。`ExtensionContractTest` 那条「Service 里出现的每个 `extension.xxx(...)` 都能在骨架里找到同名同参声明」是扫渲染结果得出的，不抄签名，所以模板改一边就会红。后端全量 **202 例**（common 8 + framework 17 + lowcode 94 + admin 83）`mvn test` BUILD SUCCESS。
- **未做**：产物预览与 zip 下载还没有前端消费方（`renderFiles` 的两个调用方是 PR 5F 的老生成器与 PR 7 的发布链路），所以这一步不加平台侧的 preview 接口；前端 `overrides/` 只在分档表里认下形状（让 §5.2 的 C 档定义完整、PR 6 的 `custom:{name}` 桩有合法落点），**加载与覆写机制仍未定**（方案 §10.2 开放项 3）。

## 2026-11-04（PR 5D：前端两份模板，产物在宿主里过了 vue-tsc）

`frontend/Api.ts.ftl`（→ `src/api/{module}/{resource}.ts`）与 `frontend/ListView.vue.ftl`（→ `src/views/{module}/{Cls}.vue`）。写之前先把底座的真实写法读了一遍：`views/lowcode/models.vue` 的 ListPage 版式、`composables/list.ts` 的 `useListPage` 契约（含 `exporter` / `exportName` / `params()` 丢空值）、`utils/validate.ts` 的 `validate(form, rules)`、`DictTag` 的 `{type, value}`、`composables/auth.ts` 的 `resolveComponent` → `../views/${component}.vue`。

- **分支基点搞错又改回来了**（记下来免得再犯）：我从 `lowcode-ai/dev` 拉 5D 分支，而 dev 当时只合到 5B，5C 还在 PR #3 上没合。改到一半发现 GenerationInput 里 5C 的派生全不在，于是把 WIP 三个 .ftl 与两份新模板搬到 `feat/lowcode-pr5d-frontend-templates`（改指向 5C 分支的 tip，那条本地分支没有提交、也没推过，重指无损），在正确基点上重做 Java 侧派生。
- **接口文件按模型一份**（本项修订方案 §10.2 的第 ⑤ 条）：原来写的 `src/api/{module}.ts` 是模块级单文件，一个项目多个模型时，任何一个字段变化都要重写整文件、diff 会盖到别人的接口上；按模型分文件后一次发布只动一个文件。另一条硬约束：**不引 `@/api` 汇总出口**，那个 barrel 是 A 档，平台往里加导出行等于破「A 档不随发布更新」。
- **补了详情接口**（动到 5B 的三套模板）：编辑弹窗必须先 `GET /{id}` 取整行再填表单 —— 列表行只有勾选进列表的字段，直接拿 row 填，保存时会把没显示出来的字段当空值覆盖掉。所以 Mapper 加 `selectById`（选全部列）、Service 加 `detail`、Controller 加 `@GetMapping("/{id}")`（权限点复用 `:list`，与底座 `system:post:list` 覆盖导出同一口径）。方案 §4.x 的接口表里本来就有这条，是我在 5B 当「没人用的方法」删掉的。
- **类型形状两条**：日期在 TS 里是 `string`（后端 `@JsonFormat` 出规范串，前端物料 `dateValue` 也只认这两种，全程不用 Date 对象承载）；入参里的数字字段**一律 `| null`**，Java 侧本来就是 `Integer` / `Long` / `BigDecimal`，必填由后端 Service 校验并回中文提示，前端表单初值是空，类型上先允许 null 才不用骗编译器 —— 这条是 `vue-tsc` 逼出来的：先前写成 `number` 直接报 `Type 'null' is not assignable to type 'number'`。
- **控件映射定死**（`GenerationInput.formControl` / `queryControl` / `pageImportsOf`）：挂字典的一律 `DictSelect`，列表单元格换 `DictTag`；TEXTAREA→Textarea、NUMBER→NumberInput、DATE/DATETIME→DateInput、其余 TextInput。区间拆出来的起止两个参数**不按字典渲染**（日期区间是两个日期框，不是两个下拉）；多选（后端收 `FIND_IN_SET` 逗号串）如实给一个文本框，不假装是多选控件。**没挂字典的 SELECT、UPLOAD、REF 先落文本框并留注释**：选项来源与上传接口模板不替项目猜。页面 import 只列这个页面真会用到的组件（`pageImports`），多余的 import 读代码的人会以为页面里有那个控件。
- **JS 字面量转义**：字段说明是中文自由文本，出现单引号或反斜杠就得转义（`jsText`），否则生成的 .vue 少一个转义就是整份文件解析失败，而这种错要到前端构建才暴露。SQL 侧同理另有一条 `sqlText`。
- **验证**：`vue-tsc` 的演练是把渲染出的两份文件写到宿主 `scaffold-vue/src/api/order/order.ts` 与 `src/views/order/Order.vue` 跑 typecheck（本模块测试不引 node 工具链），一共改了四轮才过 —— Query/Form 得用 `type` 别名（interface 没有隐式索引签名，不满足 `Record<string, unknown>` 约束）、数字可空、可空文本的 `?.trim() ?? null`、对象字面量统一「每项一行 + 尾逗号」（JS 允许尾逗号，省掉换行体操）；之后 `npm run build`（含 `vite build`）也过，演练文件随即删掉。新增 `FrontendTemplatesTest` 11 例：路径来源、可空性、五个接口方法、版式骨架与权限点、字典控件、查询区控件、表单校验与 trim、编辑前取整行、无查询条件时不出查询区、引号转义、两次渲染逐字节一致。夹具 `TemplateFixture` 补 `dict()` / `area()` 两条字段形态。后端 **181 用例全绿**，前端 24 用例 + 构建全绿。

## 2026-11-04（PR 5C：建表与菜单种子模板，真机跑了两遍）

模板链的最后一段「要人拿去执行」的产物。这两份 SQL 的特殊之处是：形状断言证明不了 MySQL 收不收，所以除了单测，还在本机 IT 容器（`scaffold-it-mysql`，3316）的一个临时库里把 V1 + V2 + 生成物**各跑两遍**做了物化演练。

- **三条口径定稿**（写进方案 §5.2 的更新策略表与 `PROJECT_MAP.md`）：① **永不生成破坏性语句** —— 建表用 `CREATE TABLE IF NOT EXISTS`，只建「新表」，对已存在的表不加列不删列；模型加字段时看合并请求 diff 里多出的那一行，由人手工 `ALTER TABLE ... ADD COLUMN`。为什么不在文件里生成 ALTER：MySQL 没有 IF NOT EXISTS 的加列写法，写进去第二次执行直接报错，等于把「可重复执行」这个性质换掉了。② 菜单**不照抄底座的「固定 ID + `INSERT IGNORE`」** —— 那是 V6 知道 502 这个号，而 `sys_menu.id` 在 V4 已改成数据库自增，生成物不知道本项目用到哪个号；改成按 `permission` 判重的 `INSERT ... SELECT ... FROM DUAL WHERE NOT EXISTS`，父级用会话变量 `@{资源}_menu` 取，`tenant_id` 不写（靠列默认值 1），只授给内置管理员 `role_id = 1`，其它角色由项目侧在角色管理里勾。③ 落点 `sql/generated/{表名}.sql` 与 `sql/generated/menu/{资源名}.sql`：子目录不在 `docker-entrypoint-initdb.d` 的自动执行范围内（mysql 不递归子目录，与 `sql/verify/` 同一机制），建表由人在目标库执行一次 —— 平台不托管运行时，也不连项目的库。
- **`GenerationInput` 补的派生**：每列 `ddlType`（大写列类型）、`ddlNull`（自增主键 `NOT NULL AUTO_INCREMENT` / 必填 `NOT NULL` / 可空文本列只写 `NULL` / 其余 `DEFAULT NULL`，外加 `defaultValue`，`CURRENT_TIMESTAMP` 是唯一不加引号的写法）、`ddlComment`（单引号翻倍 + 反斜杠翻倍，说明里出现引号很常见，不转义就是建表语句被截断）、`textType`；表级 `tableIndexes`（唯一列 `UNIQUE KEY`、非 LIKE 的查询列与关联列 `KEY`；**LIKE 列不建**因为生成的是前置通配用不上，**TEXT 列不建**因为 MySQL 要求给前缀长度否则建表失败）、`tableComment`、`menuComponent`（就是 `views/{module}/{Cls}`，与前端路由 `composables/auth.ts` 的解析规则一致）、`menuButtons` / `menuPermissionList` / `resourceSlug`。
- **踩到的两个坑**：① MySQL 不允许 TEXT 列带默认值，`remark TEXT DEFAULT NULL` 直接建表失败 → 可空文本列只写 `NULL`；② 手工 `docker exec -i ... mysql < xxx.sql` 不带 `--default-character-set=utf8mb4` 时，容器内 locale 为 `C`，客户端按 latin1 解析 UTF-8 字节，中文注释与菜单名**二次编码入库**（查出来是 `C3A8C2AE…` 而不是 `E8AEA2…`）—— 这不是生成物的问题（底座自己的 V2 用同样命令导入也一样），但生成脚本永不自动执行、只能手工跑，所以这条必须写进部署文档：手工执行带该参数，与 `deploy/mysql-client.cnf` 的由来是同一个坑。
- **物化演练结果**（临时库 `lowcode_5c_rehearsal`，跑完即删，正式 IT 库 `scaffold_system` 未动）：V1 + V2 + 生成建表 + 生成菜单**各执行两遍**，全程无报错；表 12 列、6 个索引（`PRIMARY` + `uk_order_no` + 4 个 `idx_`）、表注释 `订单主表` 入库字节正确；`sys_menu` 4 行（1 页面 + 3 按钮，按钮 `parent_id` 指向页面自增出来的 2022）、`sys_role_menu` 4 行 —— **第二遍没有产生任何重复行**，幂等成立。
- 验证：`SqlTemplatesTest` 7 例（DDL 形状与列序、索引取舍、两个开关关掉后片段整块消失、生成物语句行里不含 DROP/ALTER/TRUNCATE、单引号转义、菜单按 permission 判重且不写死 ID、两次生成逐字节一致）。后端全量测试见下条命令记录。
- **未做**：生成物路径的落地与「哪些文件属于本次发布」的装配在 PR 5E（`renderFiles()` + A/B/C 分档 + 存在即跳过）；`SqlTemplatesTest` 里那句 `statements()`（滤掉注释行再判破坏性语句）说明注释文本仍带着「请加 ALTER」的指导语，这是刻意保留的 —— 读产物的人需要知道下一步该做什么。

## 2026-11-04（分支口径修正：PR 5B 重新开 PR）

PR #1 合并进 dev 之后，我把 PR 5B 的两条提交推到了同一条已合并分支 `feat/lowcode-pr5-freemarker` 上（GitHub 不会重开已合并的 PR，但提交挂在那条分支上等于混进了已收口的单元）。按分支口径搬到独立分支 `feat/lowcode-pr5b-backend-templates` 重新开 PR #2（base 仍是 `dev`），内容一字未改，提交是原来那两条。PR #1 分支上的残留提交没动 —— 删分支或强推都算改动共享状态，等用户点头。后续 5C 在新分支 `feat/lowcode-pr5c-sql-templates` 上做。

## 2026-11-04（PR 5B：后端 CRUD 五套模板，产物真编译）

5A 只搭了骨架 + 实体，这一步把 `WriteRequest` / `Row` / `Mapper` / `Service` / `Controller` 四套补齐，并给每条口径找到出处。扩展点（`{Cls}Extension` 骨架与调用点）留到 5E —— 现在加会让这一步编译不过。

- **口径来源**：先读底座自己的 CRUD 竖切（`SystemManagementController/Service/Mapper` 的岗位那五段 + `PostWriteRequest`）再定形状，落实 §10.2 已定的「生成物向底座看齐」。定下来的形状记在 `PROJECT_MAP.md` 的「生成物口径」条里：单类 Service；查询条件**逐个参数平铺**（不塞 Map、不造 Query 对象）；SQL 写成「参数为空就是不限制」的静态语句，不用 `<script>` 拼 WHERE，运算符只来自 `QueryType`；日期出入参在**生成字段上**写 `@JsonFormat`，钉成前端 `dateValue` 只认的 `yyyy-MM-dd` / `yyyy-MM-dd HH:mm:ss` 两种规范串（不改全局 Jackson —— 那是 A 档，平台不该为一个模型去动交付项目的序列化设置）；创建/更新时间交给表上的 `DEFAULT CURRENT_TIMESTAMP` / `ON UPDATE CURRENT_TIMESTAMP`；列表排序固定主键倒序，不猜「哪个字段才是排序列」。两处偏离底座并写了理由：区间查询拆 `xxxStart` / `xxxEnd`（`BETWEEN` 一端为空会整条失效），多选收逗号串走 `FIND_IN_SET`（注解 SQL 里省掉 `<foreach>`）。
- **新增守卫**：`SqlIdentifiers` —— 生成物的表名与列名**不加反引号**（加了每行 SQL 都读不动），所以 MySQL 保留字必须在建模时拦住；接在 `LcModelService.requireUsableTable`（原 `requireUniqueTable`，顺带把两条表名检查合进一个名字）与 `LcFieldService.buildRow` 的列名派生处，整词判定、大小写无关，`order_no` / `description` 不受影响，报错带着可照着改的建议。`GenerationInput` 另加三条生成前拦截：没有主键、没有任何「可录入」字段、没有任何「可编辑」字段 —— 放过去就是缺列的残缺 INSERT / UPDATE。
- **派生仍全在 Java 侧**：`GenerationInput` 新增 `queryConditions`（SQL 用的一组）与 `queryParams`（方法签名用的一组）、`insertFields` / `updateFields` / `rowFields` / `uniqueFields`、`entityVar`、`hasDateQuery`，以及**每个文件自己的 import**（`entityImports` / `rowImports` / `writeImports` / `queryParamImports`，不留无用 import）。共用片段收进 `_shared/`：`pojo.ftl`（字段块 + 访问器块，实体/行/入参三份共用）、`queryWhere.ftl`（count 与分页、导出同一段 WHERE）、`methodParams.ftl`（参数排版与 `declare`，零参数时收成一行 `name()`）。
- **三个踩到的模板坑**（都改了模板并写进 `PROJECT_MAP.md`）：① FreeMarker 兼容模式同样认 `#{...}` 是插值，于是每段 MyBatis SQL 都被引擎当表达式解析报错 —— `TemplateRenderer` 显式锁 `DOLLAR_INTERPOLATION_SYNTAX`；② `.ftl` 里「整行只有 FTL 标签」才会被剥掉，所以循环续行的缩进必须写在**带真实内容的那一行**上（`SELECT` 与 `SET` 两处一开始写歪，续行挤到行首）；③ 条件里的空值判断要写 `#{x} IS NULL`，裸写 `x` 会被 MyBatis 当列名，运行期才炸。
- **产物真编译做成常驻测试**：新增 `GeneratedCodeCompilesTest`，把渲染出的六个文件写到临时目录、用本模块测试的类路径（已含 `scaffold-common` + `scaffold-framework` + mybatis + spring，与交付项目 `scaffold-business` 那三个依赖同批）起 javac 真编一遍 —— 审计×逻辑删除四种组合全过。这个测试抓到了 `Mapper` / `Service` / `Controller` 缺 `BigDecimal` / `LocalDateTime` import 的问题，形状断言对这类错完全无感。夹具抽成 `TemplateFixture`（订单全套 + 最小模型），形状测试与编译测试吃同一份。
- 验证：后端 **163** 用例全绿（`mvn test` BUILD SUCCESS；lowcode 38→55，新增 `CrudTemplatesTest` 12 + `GeneratedCodeCompilesTest` 2 + `SqlIdentifiersTest` 3，`ModelApiTest` 加两条保留字用例）。渲染产物肉眼复核过一遍（`/tmp/gen/out`）：注释带中文与列名/列类型、`#{}` 与参数名一一对应、软删与物删两条路。
- **未落地**：模型的「树形」「数据权限」两个开关后端模板还没实现，5E 接进发布链路时要明确拒绝这两种组合，不能出一个静默忽略勾选的页面；`custom:{name}` 动作的 TODO 桩、`{Cls}Extension` 骨架与 A/B/C 分档同样留给 5E。

## 2026-11-04（底座升级的两个缺口：base 侧留痕落地 + 两条部署不变式）

用户对 §5.2「A 档固定」提出两点质疑：① 配置文件与 app 壳不随发布更新，那平台级的框架迭代怎么下发到存量项目；② 模型删了字段而扩展代码还在引用，只靠交付时编译 + 合并时 diff 不友好。方案文档先按建议改定（A 档另设人工发起的「底座升级」动作 + 三方合并 + 合并请求人审；C 档发布前做引用体检、默认阻断；命名口径收口为 `{Cls}Extension`，`VO`/`Query`/`types.ts` 作废），随后追问「具体怎么升级」，暴露出两个真缺口，这一轮把它们处理掉。

- **缺口 1（必须现在做，事后补不回来）：三方合并的 base 侧没有留痕。** 库里只存了 `base_scaffold_sha` 一个字符串，`last_commit_sha` 又每次发布都被覆盖 —— 光有 sha 拿不到内容，而项目仓库里那个交付提交过一阵就没人认得出是哪个。没有 base 就只是两份文件的 diff，平台无法判断该覆盖谁，正是「静默覆盖项目私有配置」那个担心。修法：新增 `PlatformBaseTag`（tag 名 `yuanda/platform-base/{底座提交号}` + 附注说明），`GitGateway` 增 `tagCommit` / `pushTag`（`JGitGateway` 把分支推送与 tag 推送共用的 refspec 状态校验提成 `push()`），初始化每交付一份完整 A 档快照就给那个提交打附注 tag 并推到项目仓库。三条路径都覆盖：空仓库首次提交、非空仓库全量同步提交、以及**与远端一致没有新提交**（此时 tag 打在仓库里已有的那个提交上，发布分支优先、没有就落主干 —— 存量项目手工把交付内容合进主干后再来初始化就是这个形状）。
- **口径**：tag **只创建、永不移动、永不删除**（移动它等于改掉上一轮升级的 base，三方合并会算出错的结果），同名已存在就原样留着；tag 推不上去（仓库侧 tag 保护）**不回滚初始化**，只在 `sync_message` 与初始化结果里留告警 —— 分支已经推好了，不该因为一个 tag 让整次动作失败并删工作区；tag 推送失败抛的是普通 `BusinessException` 而不是 `WorkspaceGitException`，避免把项目状态误打成「与远端分叉」。
- **底座 checkout 读不到提交号时如实喊出来**：只拷源码树（tarball、`docker cp`）时 `headSha` 返回 null，原来静默留空。现在初始化照做，但把「该项目后续无法做三方底座升级，请把带 .git 的底座仓库挂给平台」写进 `sync_message`，工作区读数新增 `baseScaffoldSha` / `baseTag` / `baseTagExists`（三态：远端已有 / 远端缺 / 工作区不在时未核对），项目抽屉「底座标记 tag」一行按三态显示。
- **缺口 2 因此大幅缩水**：三方合并的三个输入是 ours = 项目 `main`、theirs = 按底座 checkout **当前 HEAD** 现生成、base = 项目仓库的 tag，**三者都不需要平台侧保留底座 git 历史**。历史只剩「落后多少个提交」那个计数的装饰用途，拿不到就退化为只给文件级差异清单。剩下的是两条部署不变式：底座 checkout 必须带 `.git`；**平台镜像与底座 checkout 必须成对更新** —— 裁剪清单在后端代码里、底座源码在 checkout 里，版本错开就是「按旧清单裁新源码」，而 tag 记的是新源码的提交号，base 成了假话。当前没有自动核对（PR 8 可加构建期烘进镜像的 sha 与 checkout HEAD 比对），靠部署流程约束。
- **未做但已记清**：冲突解决本期只列冲突文件，真正的解决走本地 `git fetch && git merge platform-upgrade/{sha}`，浏览器内合并编辑器不在 PR 8 范围；底座升级动作本身（生成 theirs、三方合并、推升级分支、开合并请求、合并后重打 tag）仍是 PR 8 的活。
- **验证**：`JGitGatewayTest` 8→10 例（真机裸仓库验 tag 创建/推送/同名不移动，以及 tag 推送被拒时不是 `WorkspaceGitException`）；`ProjectWorkspaceApiTest` 10→15 例（tag 钉进仓库且指向本次交付提交、同名已存在不重推不移动、内容与远端一致时打在已有提交上且不推分支、tag 推送失败仍就绪但留告警、无底座提交号时如实告警）。后端两处全绿，前端 `vue-tsc`（包 + 宿主）全绿。
- **文档**：方案 §5.2 补三方合并输入表、base 侧留痕机制与两条部署不变式；PR 4 行补记 tag、PR 8 行写明三个输入与「缺 tag 的项目被挡住」、风险表「脚手架升级下发困难」一行改为 tag 方案；`docs/docker-deployment.md` 平台专属段把「底座源码 checkout」扩成三条子项（必须带 `.git`、只读挂载所以平台不 fetch/pull、动底座就重建镜像）并新增第 4 条「项目仓库要允许推 tag」；`PROJECT_MAP.md` 仓库与工作区链路行加 `PlatformBaseTag`，事实清单加一条完整机制。
- **遗留**：在本轮之前已初始化的项目（含真机那个存量项目）没有这个 tag。补法有两条 —— 挂带 `.git` 的底座 checkout 后「清空工作区 → 重新初始化」（会走「内容与远端一致」那条新路径自动补 tag），或在项目仓库手工 `git tag yuanda/platform-base/{sha} {交付提交} && git push origin --tags`。两者都要推远端，等用户点头再做。

## 2026-11-04（PR 5A：FreeMarker 渲染骨架与实体模板）

PR 5 的第一步，只搭「渲染 + 输入派生」的骨架并用实体模板验证形状，四套后端模板与前端模板留到 5B/5D。用户对 PR 5 的口径是**可读性优先**：模板文件要适合人读，所以按「产物形态」分目录（`backend/`、`frontend/`、`sql/`、`_shared/`），文件名就是产物名（`Entity.java.ftl`），派生逻辑一律留在 Java 侧，模板只做插值与循环。

- **依赖**：`scaffold-lowcode` 加 `org.freemarker:freemarker`（版本由 Spring Boot BOM 管），模板随本模块 resources 走 —— 与 JGit 一样只落平台侧模块，交付项目不带它。
- **`TemplateRenderer`**：`Configuration.VERSION_2_3_32` + `setClassLoaderForTemplateLoading(..., "templates/lowcode")` + `setNumberFormat("computer")`（不让数字被本地化成 `1,000`）+ `RETHROW_HANDLER` + `setLogTemplateExceptions(false)`；渲染失败包成 `BusinessException("模板渲染失败：{模板名}（…）")`，模板名与根因都回给人。
- **`GenerationInput.of(project, model, fields)`**：把三张表的行派生成模板要的口径 —— `module` / `className` / `resourceName` / `tableName` / `modelName` / `modelDesc` / `permissionPrefix` / `apiPath` / `tree` / `logicDelete` / `dataScope` / `audit` / `fields` / `listFields` / `queryFields` / `formFields` / `imports`（排序后输出，保证同样输入得到同样字节）/ `primaryField`；每个字段派生 `code` / `name` / `desc` / `javaField` / `javaType` / `column` / `columnType` / `primary` / `autoIncrement` / `nullable` / `unique` / `required` / `list` / `query` / `queryOperator` / `insert` / `edit` / `formType` / `dictType` / `defaultValue`。**读的是库里已经算好的 `java_type` / `column_type`**（`FieldType` 是唯一事实源），模板侧不再二次推导；`columnType` 负责合成 `varchar(64)` / `decimal(18,2)` 这种带参数的写法，`queryOperator` 走 `QueryType.of(...).operator()`，`truth()` 同时认 `Boolean` / `Number` / `"1"`。
- **`GeneratedFile`**：`record (path, content, tier)` + `enum Tier { GENERATED, EXTENSION }`，为 5E 的 A/B/C 分档与「存在即跳过」先占好形状。
- **模板**：`_shared/auditFields.ftl` 提供 `declarations` / `accessors` 两个宏（`createBy` / `createTime` / `updateBy` / `updateTime`，被实体与后续的 Row / WriteRequest 共用）；`backend/Entity.java.ftl` 出单类 POJO —— 类注释写清「本类由元搭低代码平台按模型定义生成，属 B 档：每次发布整文件覆盖，业务逻辑写 `{Cls}Extension`（C 档）」，每个字段带中文说明与列名/列类型注释，getter/setter 按字段顺序输出，审计四字段按 `audit` 开关追加。
- **验证**：`EntityTemplateTest` 5 例 —— POJO 形状（package、字段、访问器、审计开关）、同样输入两次渲染字节一致、派生只在 Java 侧（模板里不出现类型推导）、模板名写错时报错带模板名。

## 2026-11-04（PR 4B 收尾：存量项目真机升级，顺带修掉两个交付链缺陷）

用户要求「按方案处理存量项目」。选的路径是**不删历史**：把上次真机推出去的旧形态（282 文件，只有 `platform-generated` 一条分支）先推成 `main`，再让平台对它做一次全量初始化 —— 这等于把「存量项目怎么升级到抽包后的形态」真跑了一遍。

- **顺带清掉的验证数据**：`uidemo`（绑假地址 `git@github.com:acme/ui-demo.git`、从未初始化）。删除被守卫拦了一次 —— 「项目下还有模型，请先删除模型」，按提示先删它名下的 `order_detail` 模型（连带 3 个字段）才删掉项目，守卫行为符合预期。
- **缺陷 1：全量初始化没有删除语义，升级后项目里两份物料并存。** 第一次初始化推上去后核对 diff，发现 `main → platform-generated` **一个文件都没删**（D=0），旧形态的 96 个组件/composable/工具/样式文件原样留在发布分支里，同时 `package.json` 已经依赖 npm 包 —— 抽包的意义直接抵消。根因是 `ScaffoldSnapshotWriter.write()` 只写清单、从不删，原注释还写着「平台无权清理清单之外的东西」：那句话对**主干上人工写的 C 档文件**成立，但发布分支是平台自己维护的全量产物，快照就是它应有的内容。修法：新增 `ScaffoldSnapshotWriter.pruneOutsideSnapshot(workspace, snapshot)`（删快照外的文件 + 收空目录，保留 `.git`），只在**非空仓库的全量初始化**分支里调用，删除数写进 `sync_message`；`ProjectWorkspaceApiTest` 加一例「clone 出旧形态 → 初始化后 `components/business/ListPage.vue` 与整个 `business/` 目录都不在、消息里出现『清理快照外旧文件』」。
- **缺陷 2：READY 但工作区目录不在时，项目被两头守卫卡死。** 换服务器、清过卷或手工删目录之后，状态仍是就绪 → 初始化被「已就绪不许再初始化」拒；想清空又因为按钮只在 `workspace.exists` 时出现（后端也直接抛「工作区不存在，无需重建」）而无入口。修法：后端 `resetWorkspace` 在目录缺失时不再报错，而是把状态清回未初始化（远端与已推送内容一概不动，因此不需要脏/未推送守卫），已是未初始化才继续报「无需重建」；前端 `canResetWorkspace` 改成「目录在 **或** 状态不是未初始化」，并在 `statusHint` 里给这一状态单独一句可操作的提示。测试覆盖「初始化 → 手工删目录 → 清空动作重置状态 → 再清空报无需重建 → 重新初始化成功且旧拷贝被清掉」。
- **真机结果**：`sync_message` = 「固定代码已推送到 platform-generated，并清理快照外旧文件 **109** 个」，`sync_status=2`，新提交 `ff5124d`，**`main` 全程停在 `36c8021` 没被写过**。升级 diff 变成 `146 files changed：D=107 / A=2 / M=37`（删的是组件源码拷贝与交付不该带的 `scaffold-vue/package-lock.json`，加的是 `api/scaffold-providers.ts` 与 `composables/permission.ts`），核对交付树里 `components/platform`、`views/tool`、`views/lowcode`、`scaffold-system/scaffold-lowcode`、`packages` 全为空 ✓。
- **升级后的项目能装能编译**：另起干净 clone 拉 `platform-generated`，`npm install` 装到 `@yuanda-lowcode-ai/scaffold-ui@1.0.0`（靠交付自带的 `.npmrc` 把该 scope 钉在官方源，绕开镜像滞后）、53 个包 18 秒，`npm run build`（`vue-tsc` + `vite build`）通过。
- **验证**：后端 134 用例全绿（admin 76→78，两条新用例）、前端 24 用例 + 构建全绿；后端用新 jar 重启后在浏览器里把整条链（清空工作区 → 初始化 → 读数）走完。踩到一条运维事实：用 `ps eww` 抓旧进程环境变量再 `env $vars` 重启的做法对**含空格的值不可靠**，这次重启后所有登录态失效（`/api/*` 全 401），只能重新登录 —— 以后重启后端要么用固定 env 文件，要么接受会话失效。
- **运维事实补进文档**（用户要求「把文档里的运维事实补充完整」）：`docs/docker-deployment.md` 新增「本地直接跑进程（不起容器）的几条事实」一节 —— 重打 jar 必须 `clean package`（增量构建可能不重打 fat jar）、`JWT_SECRET` 一变全部会话 401、启动变量固化成不入库的 `.env.local` 并给出加载命令（明确反对从 `ps eww` 抓旧进程 env，含空格的值会被拆坏）、`3306/6379` 是业务库而本脚手架测试与本地实例用 `3316/6380`、清 Redis 等于会话与验证码全作废、前端命令从仓库根跑且包内源码经软链即时生效不用重装。平台专属段补三条：工作区路径**相对进程工作目录**（换目录启动会「看起来工作区丢了」，正解是点清空而不是手工改 `sync_status`）、清空在目录缺失时只重置状态不碰远端、全量初始化会删快照外遗留且删除数进 `sync_message`。`PROJECT_MAP.md` 排障表加 6 行症状→第一现场（重启后全 401 / 改了代码行为没变 / 就绪但工作区未建立 / 项目里两份组件物料并存 / 生成项目 `ETARGET` 装不上物料包），`README.md` 本地启动处加指针。
  - 连带修正：新写的「npm workspaces」那句对**交付项目不成立**（它们不是 workspace、命令在 `scaffold-vue` 目录跑），所以 `deliverySubstitutions()` 又加一条 `docs/docker-deployment.md` 的整块替换，并在 `ScaffoldSnapshotPlannerTest` 里断言交付侧读到的是 `scaffold-vue` 目录版本、读不到 `npm workspaces`。后端 134 用例全绿。

## 2026-11-04（分支口径调整 + PR 4B：组件抽成 `@yuanda-lowcode-ai/scaffold-ui` 物料包、注入点解耦）

- **分支口径**：`feat/lowcode-pr3-model-field`（PR 3b/3c + PR 4 + 抽屉版式，4 个提交）快进并入 `main`，再从 `main` 拉出 `dev`；后续模块开发分支一律向 `dev` 合并 PR，`main` 只做阶段收口。远端仍未推。

按方案 §10.3 开 PR 4B（P1.5 组件抽包），这是 PR 5 的硬前置：模板要先决定生成的 `.vue` 里写 `@/components/...` 还是 `@yuanda-lowcode-ai/scaffold-ui`。

- **workspace 骨架**：仓库根新增 `package.json`（`workspaces: ["packages/*", "scaffold-vue"]`）、`package-lock.json` 从 `scaffold-vue/` 上移到根，开发命令一律从根跑（`npm run dev|build|test:unit`，`build` 先分别 `vue-tsc` 包与宿主再 `vite build`）。npm **不支持 `workspace:*` 协议**（报 `EUNSUPPORTEDPROTOCOL`），宿主写 `"@yuanda-lowcode-ai/scaffold-ui": "*"` 让 workspace 解析，装完 `node_modules/@yuanda-lowcode-ai/scaffold-ui` 是指向 `packages/scaffold-ui` 的软链。
- **物料迁入包**：67 个 `.vue`（`Icon` + `ui/**` + `business/**` + `charts/**`）、16 个 composable、8 个工具、7 层样式进 `packages/scaffold-ui`；样式合并成单一入口 `theme.css`（`@import` 顺序就是原来 `main.ts` 的七行顺序），宿主只留 `import '@yuanda-lowcode-ai/scaffold-ui/theme.css'`。`data/icons.ts`（`ICON_PATHS` / `MENU_ICON_OPTIONS`）与 `AVATAR_TINTS` 属展示物料跟着走；`NavGroup/NavLeaf/BaseRow/Menu/Dept` 抽到包内 `types.ts`，宿主 `data/index.ts` 只剩品牌 `APP` 并按原路径 `export type`，页面继续从 `@/data` 取类型。
- **边界按实际引用重判**（§6.4 那张「平台专属」表的三态落地）：进包 = `OrgCanvas` / `MenuStarMap` / `PermissionMatrix` / `PermissionTrace` / `DictSwatchbook` / `InsightPanel` / `AskDock` / `PostLadder`（部门、角色、菜单、字典、岗位五个基础页在用，剔掉交付项目编译不过）；留宿主 = 外壳 `components/layout/**`、`NeuralNet`、品牌配置、`composables/auth.ts`（登录态与动态路由是 app 的事），以及 `GeneratorPipeline` —— 移到 `scaffold-vue/src/components/platform/`，它只服务被裁剪的代码生成页。
- **注入点 `installScaffoldUi({ http, permission, dict })`**：包内 14 个文件、21 处 `@/api` / `@/directives/permission` / 宿主 `auth.ts` 引用全部改走注入。`http` 只暴露物料真正用到的 `baseUrl / token / get / put / errorMessage`；`permission` = `has / pathOf / sessionVersion`；`dict` = `load(type)` 返回字典原始行，缓存与选项转换仍由包内 `useDict` 负责。宿主侧新增 `src/api/scaffold-providers.ts` 做适配，`main.ts` 在挂载前注入。包内新增 `api.ts`（`materialApi`）承载 7 个底座固定接口，`types.ts` 收走 `PageResult` / `QueryParams` / `UserRow` / `TimelineBlock` / `LoginHeatmap` / `PermissionGraph` 六个形状，宿主 `api/http.ts`、`api/system.ts` 改为按原路径转出，全仓只有一份定义。
- **两处设计修正**：① 原设想 `installScaffoldUi(app, …)` 里注册 `v-permission`，实测**全仓零使用**（只有定义没有调用），于是指令删除、`app` 参数删除，权限状态留在宿主 `composables/permission.ts`（原 `directives/permission.ts`，目录名与内容终于一致）；② `provide/inject` 换成模块级单例注册表 `scaffoldUi()`，未安装直接抛错并指名安装入口 —— 宿主与包各装一份 vue 时 `inject` 会静默失配，单例不会。
- **交付口径定稿**（回答「`@yuanda-lowcode-ai/scaffold-ui` 放哪、生成项目从哪拿」）：包放本仓库 `packages/scaffold-ui`，**发布走 npm registry**，生成项目 `package.json` 里是普通版本号，`npm install` 不需要 GitHub 凭据；此前文档里的「GitHub git tag 依赖」作废，`max-yuuuu/lowcode-ai` 仓库也不必再建（GitHub 现在返回 Not Found）。未落地：`@scaffold` 组织归属（registry 上 `@yuanda-lowcode-ai/scaffold-ui` 尚未被占用）、发布 token 只进主机 `~/.npmrc`、`files` 白名单与「源码直发 vs 补一层构建」的取舍。**因此交付快照的裁剪清单必须跟着改**（`packages/` 不进交付、根清单与锁不进交付、`scaffold-vue/package.json` 的依赖要写发布版本），这是下一步的 PR 4B-5，本期交付链尚未接上。
- **构建链**：前端镜像的构建上下文从 `scaffold-vue` 上移到仓库根（先拷三份清单跑 `npm ci`，再 `npm --workspace scaffold-vue run build`），`docker-compose.yml` 的 `frontend.build` 相应改 `context: .` + `dockerfile`；新增根 `.dockerignore`（`node_modules` / `dist` / `target` / `.git` / `lowcode-workspace` 等），原 `scaffold-vue/.dockerignore` 已删 —— 上下文换到根之后只有根那一份生效，留着是误导。
- **一个值得记的坑**：`env.d.ts` 里 `declare module '*.vue'` 的兜底声明会让 `vue-tsc` **看不见错误的 `.vue` 路径**。搬迁脚本把宿主自己的 `@/components/NeuralNet.vue` 也改写成了 `@yuanda-lowcode-ai/scaffold-ui/components/NeuralNet.vue`，两次 typecheck 全绿，是 `vite build` 才报「Rollup failed to resolve」。改完加了一个逐说明符落点校验脚本（`@/` 与 `@yuanda-lowcode-ai/scaffold-ui/*` 全部按 exports + 扩展名解析一遍），此后靠它而不是靠 tsc 兜。
- 验证：
  - 前端：`vue-tsc`（包 + 宿主各一遍）、`vite build`、24 个用例（宿主 api 10 + 包 utils 11 + 包注入点 3）全绿。新增 `packages/scaffold-ui/src/__tests__/providers.test.ts` 钉三件事 —— 未安装就取能力必须报错、`apiUrl` / `authHeaders` 走注入的 `baseUrl` / `token`、7 个物料接口的路径与查询串逐字不变。node 跑测试不认无扩展名的相对导入，包内 5 个运行时 `from '../providers'` 补成 `'../providers.ts'`。
  - 后端：`mvn test` 全绿（common 8 / framework 17 / lowcode 29 / admin 76）。`ScaffoldSnapshotPlannerTest` 仍过 —— 它断言的是「平台的东西不进交付项目」，包搬走之后交付快照只是少了一批物料文件，**这正说明它对本期这个破坏无感知**，PR 4B-5 要补一条「交付前端的依赖可解析」断言。
  - 浏览器真机（dev 5173 + 后端 8080 + 3316/6380，`admin` 登录，控制台无报错无 Vue 警告）：工作台/侧栏正常；`/system/user` 部门树出 4 个节点（`useDepts` → `materialApi.deptTree` → 注入 `http`）、列表 3 行、`新增用户` 按钮可见（`permission.has`）、「状态」下拉出 `正常 / 停用`（`dict.load`）；`/system/dept` 新增部门弹窗里负责人下拉出 3 个用户（`materialApi.usersPage`）；`/system/menu` 星图渲染出「导航 58 项 / 系统管理 8 个页面」（`loadPermissionGraph` + `pathOf`，并且 `sessionVersion` 的 watch 改成首次取数时才注册，避免模块加载早于 `installScaffoldUi`）；`:root` 上 `--ink` `--blue` 取到珍珠玻璃令牌值，`data-theme=light` / `data-density=regular` 由包内 `appearance` 在挂载前写入。
- **交付链按 npm 依赖重接（PR 4B-5）**：物料进包之后，交付快照既没有 `packages/` 也没有根清单，交付项目的前端必然编译不过 —— 这一步把它接上。
  - 裁剪清单加**第三层手段**：`ScaffoldTrimPolicy.deliverySubstitutions()` 精确整行块替换。标记块只会删，而底座有六处写的是「平台怎么用 npm workspaces 构建」，交付项目要换成「按 npm 依赖怎么构建」，同一批行既要消失又要换内容：`scaffold-vue/package.json` 的 `"@yuanda-lowcode-ai/scaffold-ui": "*"` → 配置里的版本、`scaffold-vue/Dockerfile` 的构建段与 nginx 两行、`docker-compose.yml` 的 `frontend.build`、README 两处命令注释、部署文档的构建说明行。替换文本里 `{SCAFFOLD_UI_VERSION}` 由新配置 `scaffold.lowcode.scaffold-ui-version`（env `LOWCODE_SCAFFOLD_UI_VERSION`，默认 `^1.0.0`）填入；**改写块找不到或出现多次当场报错**，不允许推一个装不上依赖的项目出去。
  - `packages/`、根 `package.json` / `package-lock.json` / 根 `.dockerignore` 明确不进交付；`GeneratorPipeline` 的剔除路径跟着搬到 `scaffold-vue/src/components/platform/`；`REQUIRED_ROOTS` 加 `packages/scaffold-ui/package.json`（底座丢了物料包，规划阶段就失败）。
  - 交付项目的前端**没有锁文件**，镜像里从 `npm ci` 改成 `npm install`，可复现性由 `@yuanda-lowcode-ai/scaffold-ui` 版本号承担；`scaffold-vue/.dockerignore` 恢复（交付的上下文就是这个目录，平台那份在仓库根，两份各管一边）；`vite.config.ts` 加 `optimizeDeps.exclude: ['@yuanda-lowcode-ai/scaffold-ui']` —— 包发的是 `.vue`/`.ts` 源码，被 esbuild 预打包会直接失败。
  - README 里元搭定位、演进进度、平台专属链接（`PROJECT_MAP.md` / `changelog.md` / 方案文档）和 `packages/scaffold-ui` 目录行都用标记块裁掉，交付项目的 README 里读不到平台死链。
  - **演练抓到一个真实缺陷**：平台本地工作区 `lowcode-workspace/`（容器里是卷）里装着上一版**交付克隆**，而它正好落在 `scaffold-system/` 白名单子树内，于是新快照把上一版交付整个套了进去 —— 文件数从 282 莫名涨到 460 才暴露。`IGNORED_NAMES` 补 `lowcode-workspace` 与 `.vite`，并加两条断言（`accepts(...lowcode-workspace...)` 为 false、快照路径不含该目录）。
  - 包面收口：`package.json` 加 `files: ["src", "!src/**/__tests__"]`，`npm pack` 出 108 个文件、测试不进包；`peerDependencies` 补 `vue-router`（`ask.ts` 用它的类型）。
  - 交付验证（真实代码，不是模拟）：用生产同一条 `ScaffoldSnapshotPlanner.plan(root, "file:/tmp/.../scaffold-ui-1.0.0.tgz")` + `ScaffoldSnapshotWriter` 落盘 **177 个文件** → 交付 `scaffold-vue` 里 `npm install` 装到物料包 → `npm run build`（`vue-tsc` + `vite build`）通过；交付的 `docker-compose.yml` 是 `context: ./scaffold-vue`、`Dockerfile` 是 `npm install` 形态、README 里平台死链计数为 0。验证完清理临时目录。
  - 后端：`mvn test` 132 个用例全绿（common 8 / framework 17 / lowcode **31**（新增两条交付改写用例）/ admin 76）。
  - **发布渠道已打通**：npmjs 组织 `yuanda-lowcode-ai` 建好，`@yuanda-lowcode-ai/scaffold-ui@1.0.0` 以 public 发布成功（108 个文件、173.8 kB）。剩下 `LOWCODE_SCAFFOLD_UI_VERSION` 就是真值，不再需要临时指 tarball。
- **包名定为 `@yuanda-lowcode-ai/scaffold-ui`**（组织已建好，npm 强制 scope 等于组织或用户名，`@scaffold/ui` 发不到这个组织下）。全仓 293 处旧名一次改掉、覆盖 52 个文件：宿主与包的 import 说明符、`scaffold-vue/package.json` 依赖键、`vite.config.ts` 的 `optimizeDeps.exclude`、裁剪改写表两侧的文本、`application.yml` 配置注释、README / AGENTS / PROJECT_MAP / 方案 / 部署文档的叙述。`node_modules` 软链与根 lock 由 `npm install` 重建；改完宿主与包的 `vue-tsc`、`vite build`、24 个用例、planner 的交付改写用例全绿（旧名只剩 `target/`、`dist/` 与本地工作区里的上一版交付克隆，都是构建产物与忽略目录）。
- **npm 发布实测到的四个坑**（都记下来，别下次再查一轮）：
  - 本机默认 registry 是 `registry.npmmirror.com` 镜像，发布必须显式 `--registry=https://registry.npmjs.org`。
  - scoped 包不带 `--access public` 会按 private 走。
  - 账号开了 2FA：npm 已把老的 Automation 类型并进 Granular token，要勾 **Bypass two-factor authentication (2FA)**，且权限必须给到 **Organizations → `yuanda-lowcode-ai` → Read and write (publish and stage)**；只给包级权限发不了「还不存在的新包」（第一次试发布 PUT 返回 404、`npm access list packages` 返回 403 就是这个原因，不是组织没建）。
  - **新组织首个包会进 npm 自动审核暂存态**：注册表先挂一个 `0.0.0-stage` 占位版本，`1.0.0` 几分钟后才可见；**镜像更滞后**，这期间按镜像装直接 `ETARGET: No matching version found for @yuanda-lowcode-ai/scaffold-ui@^1.0.0`。我先前写「镜像会自动同步、国内装不受影响」是错的，实测打脸后修正。
- **因此交付目录多带一份 `scaffold-vue/.npmrc`**：把 `@yuanda-lowcode-ai` 这个 scope 钉在官方 registry，其余依赖仍走机器默认镜像不牺牲速度。写这份文件时我先误覆盖了原有的 `cache=.npm-cache / fund=false / audit=false` 三行，从 `git show HEAD` 里读回来才没丢配置 —— 教训是改既有配置文件前先 `git show` 看原内容，别直接整份重写。planner 加了一条断言守住「交付里必须有 `scaffold-vue/.npmrc`」。
- **发布后的端到端复验（真实链路，不是模拟）**：物化交付快照 **177 个文件**（依赖写 `^1.0.0`）→ 交付 `scaffold-vue` 里 `npm install` 装到 `@yuanda-lowcode-ai/scaffold-ui@1.0.0` → `npm run build`（`vue-tsc` + `vite build`）通过。PR 4B 的验收项里「包被外部 app 以版本号依赖消费」就此闭环 —— 消费者是真实交付项目，比另造一个 demo app 更有说服力。
- **安全提醒**：会话里出现过的那把第一张 token 仍有发布权（它报的是 EOTP，说明权限够），建议去 npmjs 撤销；当前 `~/.npmrc` 用的是新的 granular token，值没进对话、也没进仓库。

## 2026-11-04（PR 4：JGit 仓库接入、§6.4 裁剪清单代码化、空仓库首次初始化）

按方案 §10.3 开 PR 4。本单元的落点是「项目不再只是一行数据库记录」：平台能把裁剪后的固定代码推到租户仓库的发布分支，并且**交付产物是脚手架减去平台自身**。

- **`scaffold-business` 空壳模块预置**（§5.2 那条硬约束的兑现）：交付项目的业务代码只进 `com.scaffold.business.{模型}` 子包，Maven 模块不再增加。它在本仓库里就是空壳（只有 `package-info.java`），父 pom 的 `<modules>` 与 admin 的 `<dependencies>` 现在就写好 —— 这样交付快照不必再去改写 pom，A 档 pom 真的从此不动。`@MapperScan` 同时补了 `com.scaffold.business`。
- **JGit 依赖只落 `scaffold-lowcode`**（`org.eclipse.jgit` + `org.eclipse.jgit.ssh.apache`，7.7.1）。所有 Git 动作经 `GitGateway` 接口，不经 shell；SSH 走主机 `~/.ssh` 密钥与 `known_hosts`，HTTPS 不存 token，库里仍然只有 `repo_url` 与分支名。
- **`GitGateway` / `JGitGateway`**：`ls-remote`、空仓库 `init` 并把 HEAD 设成未出生的发布分支、克隆指定分支、切工作分支、add+commit（**无改动不产生空提交**，未出生分支看索引条目数、已有提交看暂存区 diff）、显式 `refs/heads/{branch}` 非强推、脏工作区与本地/远端提交号读取。失败分两类落到 `sync_status`：连不上（5 仓库不可达）与远端拒绝（4 与远端分叉），都带一句可操作的说明（主机指纹未登记 / 私钥有口令 / 分支保护）。
- **§6.4 裁剪清单成了代码里的一份配置**（`ScaffoldTrimPolicy`）：路径白名单 + 黑名单 + 任意层级的构建产物目录名，加上第二道手段 —— 文件内的 `scaffold:platform-only:start/end` 标记块，由 `PlatformOnlyStripper` 在写快照时整块删除。同一套机制通吃父 pom 的 `<module>`、admin pom 的依赖、`docker-compose.yml` 的工作区卷、`application.yml` 的平台参数段、建表脚本里的 `gen_table` 与「系统工具」菜单种子、前端 `api/index.ts` 的平台客户端，以及启动类里平台模块的 Mapper 扫描包。标记必须独占整行，写歪了当场报错而不是产出半截文件。
- **交付产物是校验过的**（`ScaffoldSnapshotPlanner`）：底座根路径缺一块就失败（清单与仓库结构不一致要先改清单）；写完的文本还要扫一遍平台残留引用（`com.scaffold.lowcode`、`com.scaffold.generator`、`/api/lowcode`、`/api/platform`，SQL 里另扫 `lc_project`/`lc_model`/`lc_field`/`lc_page`/`lc_tenant_config`/`gen_table`），命中就拒绝初始化 —— 带病的交付物不出门。
- **一处与方案 §6.4 字面不同的判断，已在方案里回写**：`lc_tenant` **保留**在交付项目的库结构里。`AuthService.login()` 每次登录都要查 `countActiveTenant`（停用租户立刻登不上），`currentUser()` 要读租户编码与名称；删掉它要么登录报错，要么给交付项目维护第二套认证代码 —— 与 §6.4「避免两套 schema、两套认证」的原意相反。剔除的是其余 `lc_*` 元数据表。同理，`OrgCanvas` / `PermissionMatrix` / `DictSwatchbook` 这些组件§6.4 列为「平台专属、不进项目」，实际是部门 / 角色 / 字典三个基础页在用，只有 `GeneratorPipeline` 真的只服务被剔除的代码生成页，因此只裁它。
- **初始化链路与状态机**（`ProjectRepositoryService`，接口在 `/api/lowcode/projects/{id}/repo/check|initialize|workspace`）：连通性检查**只读**，不改生命周期；初始化走 `0 → 1 → 2`，空仓库按 §6.3 在未出生 HEAD 上提交并用显式 refspec 建分支，非空仓库要求主干存在且认得出脚手架（`.scaffold-project.json` 或 `scaffold-system/pom.xml` + `scaffold-vue/package.json`），推完**回读 `ls-remote` 校验远端 ref 确实指向本次提交**才置就绪；内容与远端一致时不制造空提交、也不谎称推送过。固定代码（A 档）只在初始化写一次，已就绪的项目再次初始化被拒；`resetWorkspace` 在有未提交改动或未推送提交时拒绝执行，平台不静默丢人的代码。库里存成 `file://` 之类的脏值在初始化时再校一次。
- **动作是同步执行的**，不引入异步任务表：这是单机研发期工具（§7.1），放请求线程里租户上下文与失败原因都能如实回给人；`sync_status=1` + 进程内的按项目互斥已经挡住并发。部署文档里说明了 nginx 反代需要给 `/api` 放宽 `proxy_read_timeout`。
- **V7**：`lc_project` 加 `sync_message`（失败原因/进度直接显示在界面，不让人翻日志；列已存在时跳过 ALTER 保证幂等）+ 「同步仓库」按钮权限码 `lowcode:project:sync`。**能编辑项目不等于能往租户仓库推代码**，所以单独一个码。`sync_status` 的中文文案从 SQL 的 CASE 挪进 `ProjectSyncStatus`，接口与页面共用一处定义。
- **一个由启动失败暴露的坑**：`@MapperScan("com.scaffold.lowcode")` 会把包树里**所有接口**注册成 MyBatis Mapper，`GitGateway` 因此多出一个 bean 让注入变成二义、后端起不来。改成逐个列到 `*.mapper` 包。交付侧按包根扫 `com.scaffold.business` 是有意保留的例外（生成代码会不断加子包），代价写进了注释。
- **抽屉版式二次修订**（使用者反馈后两轮收敛）：① `.drawer-toolbar` 是 `justify-content: space-between` 且工具条有四个子元素，「初始化固定代码」在就绪后隐藏就把剩下的按钮拉散 —— 新增全局 `.drawer-toolbar-actions`（动作成组贴右、可换行），并把入口按生命周期给：就绪只有「检查连通性 + 清空工作区」，未初始化/上次不可达才是「检查连通性 + 初始化固定代码」，工作区没建过就不出现「清空工作区」（点了只会拿一句「工作区不存在」）；② 查真相时发现三个区块小标题**从来没渲染过** —— `SectionLabel` 只认 `title` prop、没有默认插槽，我按全站另 13 处的写法改成 `title=` 后，使用者定稿为不显示标题，于是正文去掉标题、连通性检查挪到最后，块间距统一 16px（`.desc` 是 `Descriptions` 的根类，`.kv-grid` 在它里面，第一版选择器写错没生效，是量 DOM 发现的）；③ 顶部状态条下单独一行显示「最近一次动作」，读数表格里不再重复一遍同步状态。实测两种状态间距序列一致（`10/14/16/16`）。

- **前端**：`projects.vue` 行操作加「仓库与工作区」，抽屉里三块读数 —— 仓库绑定（地址 / 两个分支 / 底座提交 / 最后推送提交 / 最近一次动作）、连通性检查（能否访问、是否空仓库、远端分支、下一步）、平台工作区（路径、是否已建、未提交改动、本地/远端提交、未推送提交）。同步状态列改成 Tag，异常态有告警图标，鼠标悬停看 `sync_message`。动作按钮统一 `lowcode:project:sync` 权限，初始化与清空都先二次确认并说明后果。
- **compose**：新增 `lowcode-workspace` / `maven-repo` / `npm-cache` 三个卷，底座 checkout 与主机 `~/.ssh` 以只读挂进后端；这些行都在标记块里，交付项目的 compose 是干净的。
- 验证：
  - 后端 **128 个测试全绿**（common 8 / framework 17 / lowcode 27 / admin 76）。新增 `ScaffoldSnapshotPlannerTest` 8 例（对**真实底座**出快照并钉死「平台的东西不进交付项目」「底座能力必须进」、pom/启动类/SQL/前端入口的裁剪结果、标记块不成对与同行成对的报错、底座缺失提示）；`JGitGatewayTest` 8 例对本机临时裸仓库跑通「空仓库未出生 HEAD → 提交 → 显式建分支推送 → 回读」、无改动不空提交、脏与未推送检测、**远端已前进时推送被拒而不是强推**、克隆后切发布分支且主干不动、不可达报错文案、分支名规则；`ProjectWorkspaceApiTest` 8 例用可控假远端跑状态机（就绪、不可达、非法地址、重复初始化、未推送提交禁止清空、状态读数、只读检查不改状态、停用项目拒绝初始化），并断言固定代码真的按清单落盘且不含 `scaffold-lowcode` / `views/tool`。
  - 交付产物**实测能编译能起能登录**：把 282 个文件的快照落到临时目录 → 离线 `mvn package` 出 jar → 前端 `npm ci` + `vite build` 通过 → 用交付侧的 `V1`–`V3` 建一个全新库 → 起在 8091 端口 → `admin` 登录成功、`/api/system/users` 返回 3 条、`/api/auth/routes` 的菜单里没有任何 `/tool` 与 `/lowcode` 路径、`/api/lowcode/projects` 返回 404；验证完删掉临时库与目录。
  - 前端 `vue-tsc` + `vite build` 通过，21 个前端用例通过；浏览器实测项目管理页出现「仓库与工作区」入口，对一个真实 GitHub 空仓库执行只读连通性检查回显「可访问 / 空仓库 / 下一步：初始化会直接创建发布分支并推入固定代码」。
- **真机推送已完成**（使用者授权后对本机 → GitHub 真实仓库跑通）：`order-serive` 绑定 `git@github.com:max-yuuuu/test-lowcode-push.git`（空仓库），初始化耗时约 14 秒，推上 `refs/heads/platform-generated` 提交 `36c8021`，282 个文件；`sync_status` 走 `0→1→2`，`base_scaffold_sha` 记的是底座提交，`workspace` 读数 `dirty=false / unpushed=false / localSha==remoteSha`，`main` 分支全程没被写过。GitHub 侧核对：远端只有 `refs/heads/platform-generated` 这一条。
- **真机跑出两个「代码没错、环境或依赖不给力」的坑**（都在 `SshTransportSupportTest` 里钉住了）：
  - **系统代理劫持 git over ssh**：这台机器开着本机 HTTP 代理（`scutil --proxy` 里 `127.0.0.1:7897`），JGit 的 `DefaultProxyDataFactory` 会读 JVM/系统代理，于是 SSH 去连代理端口并被掐断，报错只有一句含糊的 `Session is being closed`（调试日志里才看得见 `remote=/127.0.0.1:7897`）。现在 `directSshProxy()` 固定返回 null 走直连 —— 与 `ssh` 命令、原生 git 的行为一致；真要代理就在 `~/.ssh/config` 写 `ProxyCommand`，那条路走 `ConnectorFactory`，不受影响。
  - **ed25519 部署密钥需要 BouncyCastle**：Apache SSHD 2.19 的 OpenSSH 私钥解析器没有 BC 就报 `Unsupported key type (ssh-ed25519)`，认证阶段表现为 `publickey: no keys to try` —— 看起来像「密钥没配好」，实际是依赖缺失（BC 既不由 Spring Boot 也不由 JGit 管理版本，父 pom 里显式钉 1.80）。另外这次还暴露一个自己的操作失误：只构建 `-pl scaffold-lowcode -am` 就重启后端，BC 根本没进 fat jar，白查一轮。
- 顺带把推送失败的信息文案改准：不再把分支名当「目标」印出来，认证类失败补一句「推送凭据只走主机级密钥，HTTPS 请改用 git@ 或配置主机 token」。


## 2026-11-04（PR 3b / 3c：模型与字段配置、类型映射单一事实源、手工新增租户的开通分支）

接 PR 3a 继续 P1：`lc_model` / `lc_field` 建表与配置页面，以及方案 §10.3 PR 3 验收项「字段类型 → Java 类型/列类型映射表有单测」。

- **`lc_model` / `lc_field`（V6）**：模型归属项目、字段归属模型，唯一键分别是 `(project_id, model_code)` 与 `(model_id, field_code)`。方案 §2.2 里的 `lc_model.config` / `version` 本期**不建** —— config 没有读取方，版本历史属于页面侧（`lc_page_version`），模型自己的历史靠发布 diff 看。`lc_model` 补 `deleted`（模型下线要留痕，页面可能还在引用它的字段），`lc_field` 补齐 create_by/update_by 与 AGENTS.md 的业务表四字段约定一致。两张表都登记进租户拦截器白名单。
- **类型映射的唯一事实源是代码里的 `FieldType` 枚举**，不在库里再存一份映射表：`STRING/TEXT/INTEGER/DECIMAL/DATE/DATETIME/ENUM/FILE/REF` 各自定 Java 类型、列类型与默认控件，保存时把算出来的 `java_type` / `column_type` 落库（模板生成时直接读，不再二次推导），前端通过 `GET /api/lowcode/models/meta` 取同一份表渲染。`FieldTypeTest` 逐条钉死这张表 —— 改它等于改所有已生成项目未来发布的产物。
- **一处对方案枚举的扩充**：`field_type` 增加 `TEXT`。§2.2 原本只有 `STRING`，而后台系统里备注 / 说明 / 富文本是最高频字段，只靠 varchar 撑到几 K 会让生成的列类型与底座不一致。已回写进 §2.2。
- **规则**：自增主键固定升级为 `bigint` / `Long`（底座主键一律 BIGINT）；`ENUM` 必须挂字典且字典要在**当前租户**真实存在（否则生成出一个空下拉）；`REF` 必须指向**同项目**的模型（指向自身合法，树表的 `parent_id` 就是这样）；一个模型最多一个主键；自增必须是主键；主键不可空且自动必填；`RADIO`/`CHECKBOX` 要么挂字典要么给固定选项；查询方式只认 `QueryType` 枚举值，认不出来回落到等于，**不让任意字符串进 SQL 片段**。列名只由字段编码派生，不给单独改列名的入口（改了列名历史数据与生成物就对不上）。
- **`Naming`**：`snake` / `pascal` / `camel` 对 `orderService`、`OrderService`、`order_service` 三种写法幂等（先按分隔符切、再按大小写边界切，`HTTPServer` → `http_server`）。第一版没切下划线导致 `pascal` 不幂等，是单测抓出来的。
- **PR 3c 手工开通租户**：`TenantWriteRequest` 增加可选 `adminUsername` / `adminPassword` —— 填了就顺带走 `TenantProvisionService` 的同一套开通流程（复制菜单 / 字典 / 参数 + 建内置 `tenant_admin` 角色 + 建管理员账号），留空只建租户记录。初始密码留空时读系统参数 `sys.user.initPassword`，与「用户管理-新增用户」同口径。这关掉了 PR 2 遗留的「手工新增租户进去是空菜单」。
- **前端**：新增 `views/lowcode/models.vue` —— 列表按项目过滤，字段配置走右侧 `Drawer`（与「字典管理」的主从形态一致，不额外开菜单路由），字段表单里选类型后实时显示 `生成结果 → Long · bigint` 这类映射预览；固定选项用每行「标签:值」的文本域与后端 `form_options` 互转。`api/platform.ts` 补 `models` 组与 `ModelMeta` 等类型。
- **项目删除加了拦截**：项目下还有模型时不许删（`LcProjectService.delete`），否则模型会指向一个不存在的项目。
- 验证：后端 100 个测试全绿（新增 `ModelApiTest` 5 例覆盖派生与不可改、类型映射与默认值、非法定义拒绝、meta 单一来源、跨租户隔离；`FieldTypeTest` 7 例；`TenantIsolationApiTest` 加手工开通 1 例，共 11 例）；`vue-tsc` + `vite build` 通过；浏览器实测新建 `uidemo` 项目 → 建 `order_detail` 模型（表名/类名自动派生）→ 抽屉里加 INTEGER 主键并勾自增，预览从 `Integer · int` 变成 `Long · bigint`，保存后字段行显示 `bigint / Long`。
- **测试夹具补了兜底清理**：`ModelApiTest` 之前只在拿到 id 后才登记删除，断言在解析响应前失败会留残留（实测漏了三个 `mproj*` 项目），现在额外按编码前缀兜一遍。
- **未做（PR 4）**：仓库连通性检查（`ls-remote`）与初始化快照推送；`sync_status` 的推进、`workspace_path` / `base_scaffold_sha` 的写入都在那里。

#### 2026-11-04（PR 3a：lc_project 建表、项目管理页面与「补齐租户资产」）

按方案 §10.3 开 PR 3（P1 上）。本单元先做项目侧：建表、后端 CRUD、仓库地址白名单、项目管理页面，以及一个 PR 2 遗留问题的收口 —— 已有租户拿不到迁移新增的租户可见菜单。

- **`lc_project`（V5）**：项目 = 租户名下的一个 Git 仓库。**没有照抄方案 §2.2 的全部列** —— `db_*`（生成 `application.yml` 用的连接配置）、`deploy_*`（远期部署形态）、`backend_module` / `api_prefix` / `frontend_prefix`（生成期常量）本期没有任何读写方，建了就是让表单多一排填了没用的字段，等 PR 5 有真实需要时以 ALTER 补。落了 `repo_url` / `main_branch` / `gen_branch` / `workspace_path` / `base_scaffold_sha` / `last_commit_sha` / `sync_status`（单一生命周期状态机，方案已把原 init_status 与 sync_status 合并）与三个时间戳。表已登记进租户拦截器白名单，DDL 反向校验单测自动守住。
- **仓库地址是安全边界**：`RepoUrlValidator`（`com.scaffold.lowcode.repo`）只放行 `https://host/path` 与 `git@host:path`，拒 `file://` / `ext::` / `ssh://` / 裸路径 / 内嵌账号口令，也拒 `http://`（方案 §九「用户输入进入 Git 操作造成注入或越权仓库」那条风险的落点 —— PR 4 的 JGit 直接拿这个值建 remote）。4 个单测覆盖放行与拒绝形态。
- **项目 CRUD**：`/api/lowcode/projects`（列表 / options / 导出 / 新增 / 编辑 / 启停 / 软删），权限码 `lowcode:project:list/add/edit/remove`。**编码租户内唯一**（唯一键 `(tenant_id, project_code)`，两个租户可以有同名项目）；**主干分支与发布分支不许相同** —— 同一条分支既被人工写又被平台推，合并请求就没有意义。`options` 只回精简字段给后续模型/页面模块选项目。
- **新菜单段 500「低代码」**（项目管理，`/lowcode/project` → `views/lowcode/projects.vue`）。与 400 段「平台管理」的区分是口径性的：**`platform:` 前缀 = 平台运营专属（不下发给租户），`lowcode:` 前缀 = 租户自己的开发功能**。注册开通复制菜单时按 `path LIKE '/platform%' OR permission LIKE 'platform:%'` 排除平台段，按语义判断不写死 ID。
- **「补齐租户资产」补上了 PR 2 的一个真实缺口**：平台用迁移脚本新增租户可见菜单后，**已有租户不会自动获得**（V5 的 500 段就只进了平台租户）。新增 `POST /api/platform/tenants/{id}/assets/sync` 与租户管理里的行操作按钮：按平台模板补齐缺少的菜单 / 字典类型 / 字典数据 / 内置参数，并把业务菜单授给该租户的内置管理员角色。菜单没有跨租户唯一键，用**身份键**判断该不该补：优先 `permission`，其次 `path`，最后 `menu_type + menu_name`；`sys_dict_data` 也没有唯一键，只能按 `(dict_type, dict_value)` 反查去重，否则补一次多一份。幂等，重复执行 `menuAdded = 0`。
- **顺带修掉注册的一个真缺陷**（自查发现，不是用户报的）：注册只复制了菜单，**字典与内置参数没带** —— 实测新租户 `sys_dict_type` 0 条、`sys_config` 0 条，表现为状态下拉全空、新增用户取不到初始密码。开通逻辑抽成 `TenantProvisionService`（菜单 / 字典 / 参数 / 内置管理员角色），注册与补齐共用。
- **前端**：`projects.vue` 按 `ListPage` 版式实现（查询 / 重置 / 分页 / 导出 / 行操作齐全），分支列用两个 Tag 加箭头表达「发布分支 → 主干」的方向，长仓库地址走 `.cell-sub` + 截断不新造样式。
- 验证：后端 91 个测试全绿（`ProjectApiTest` 3 例：新增→编辑→停用→软删全链路、非法仓库与分支被拒、跨租户同名编码各自可见且改删被拒；`RepoUrlValidatorTest` 4 例；`TenantRegisterApiTest` 加了字典/参数随开通落地与补齐幂等的断言）；前端 `vue-tsc` + `vite build` 通过；浏览器实测界面新增 `ui-demo` 项目 → 列表渲染正确 → 删除生效，租户管理点「补齐租户资产」给 `demo` 租户补到「菜单 5 项、字典 3 类 / 7 条、参数 3 条」。
- **未做（PR 3b/3c）**：`lc_model` / `lc_field` 与字段类型映射、模型与字段配置页面；平台管理员手工新增租户时的开通分支；仓库连通性检查（`ls-remote`）与初始化快照推送属 PR 4 的 JGit 范围。

#### 2026-11-03（PR 2 · P0-B：租户隔离收口 —— SQL 拦截器、切换租户旁路、租户管理与租户参数）

按方案 §10.3 的 PR 2 范围开工，把「租户只是加了列」推进到「租户是运行时真的在生效的隔离边界」。后端 80 个测试全绿（25 单测 + 55 集成，含新增 9 个租户隔离用例），前端 `vue-tsc` + `vite build` + 21 个单测通过，并在本地实机跑通登录 → 租户管理 → 进入租户 → 回到平台租户的完整链路。

**新增模块**：`scaffold-system/scaffold-lowcode`（`com.scaffold.lowcode.*`）—— 平台业务从这一个模块长出来，生成项目的初始化快照按 §6.4 剔除它。

- **租户 SQL 拦截器（`scaffold-framework`）**：`MybatisPlusConfig` 注册 `MybatisPlusInterceptor` + `TenantLineInnerInterceptor`，`ScaffoldTenantLineHandler` 决定注入范围。**采用白名单而非黑名单**：只有 `ScaffoldTenantLineHandler.TENANT_TABLES` 里登记的 12 张表（11 张 `sys_*` + `lc_tenant_config`）会被注入 `tenant_id`，其余（`lc_tenant`、四张关联中间表、`gen_table*`、`information_schema`）一律不碰 —— 反过来写会让公共查询凭空背上不存在的列。白名单遗漏是泄露风险，所以配了一条**反向校验单测**：`ScaffoldTenantLineHandlerTest.白名单覆盖建表脚本里所有带租户列的表` 直接解析 `sql/V*.sql` 的 DDL，凡是带 `tenant_id` 列的表必须在白名单里，反之不能在里面；新增表忘记登记会当场红。匿名请求（登录、验证码、健康检查）没有租户上下文，整体跳过注入。
- **`super_admin` 跨租户旁路口径落定**（关掉方案 §10.2 开放项 1）：走**会话内切换租户**，不走「不带租户条件的专用查询通道」。`POST /api/auth/switch-tenant {tenantId}` 校验调用者是 `super_admin`、目标租户启用后，重签一张 `tenantId` 指向目标租户的 JWT 并**吊销旧令牌**，`tenantId` 传空回到自己的平台租户；切换事件写 `sys_login_log`（「切换到租户 acme」/「回到平台租户」）。管理员的业务权限与数据范围仍按**自己的角色**解析，所以能看见目标租户数据却不会丢权限，平台管理菜单也不会跟着下发给租户 —— 边界干净，审计只有一条会话轨迹。
- **身份与授权解析改按用户 ID，不再按账号**：账号唯一键是 `(tenant_id, username)`，跨租户重名是常态，用账号当身份键会取错人。`PermissionResolver` / `DataScopeResolver` 的入参从 `String username` 改为 `LoginUser`，实现里走 `selectUserById` / `selectUserProfileById`；`AuthMapper` 的角色、权限、账号定位查询标 `@InterceptorIgnore(tenantLine = "true")`（都按全局主键定位，切换租户后仍要解析到自己），而 `selectAllPermissions` / `selectAllRouteMenus` 两个全表查询**保留注入**（菜单集按当前租户取）。登录支持可选 `tenantCode`（`lc_tenant.tenant_code`）：填了按「账号 + 租户」精确定位，没填且命中多个租户时报「该账号存在于多个租户，请填写租户编码」，绝不取第一条；前端登录页加了对应输入框并记住最后一次。
- **关联中间表的写入校验**（PR 2 范围内唯一拦得住的跨租户写入口）：`sys_user_role` / `sys_user_post` / `sys_role_menu` / `sys_role_dept` 没有 `tenant_id`，拦截器管不到写入。`SystemManagementService.ownedIds()` 在写入前按当前租户反查目标主键（表本身在白名单内，条件自动注入），发现外来 ID 直接报「所选角色不属于当前租户」并回滚整笔事务；菜单的 `parentId` 同样校验归属，且 `updateMenu` 补了「上级菜单不能是自身」。
- **`sys_menu.id` 改为数据库自增**：原实现是应用侧 `MAX(id)+1`，而 `MAX` 已被租户条件限制 —— 两个租户会算出同一个 ID 撞全局主键。V1 建表与 `insertMenu` 一起改（去掉显式 id、`useGeneratedKeys` 回读），V4 带存量库的 `ALTER`。
- **`lc_tenant_config` + 租户管理**：新表按 `(tenant_id, config_key)` 唯一，存平台按租户区分的默认值，先落 `project.main_branch` / `project.gen_branch` 两条内置参数（PR 3 建项目、PR 4 发布读它）。`LcTenantController`（`/api/platform/tenants`，权限码 `platform:tenant:list/add/edit`）提供列表（含账号数）、导出、新增、编辑、启停；平台租户禁止停用。`LcTenantConfigController`（`/api/platform/tenant-configs`，`platform:config:list/edit`）**只作用于当前会话租户**：接口不收 `tenant_id` 入参，读写条件全靠拦截器注入 —— 这是「跨租户必须先切换」口径在 API 形状上的体现，普通租户角色拿不到这两个权限码。
- **日志清理语义明确**：`DELETE /api/monitor/oper-logs|login-logs` 的清空现在只清当前租户（拦截器给 `DELETE` 补了条件），`MonitorLogWriteApiTest` 的断言相应改为按 `tenant_id = 1` 统计。
- **前端**：新增 `api/platform.ts`（`platformApi.tenants` / `tenantConfigs`，复用 `system.ts` 的 `crudPage`，该 helper 已导出）；`views/lowcode/tenants.vue`（列表 / 新增 / 编辑 / 启停 / 「进入该租户」，当前租户行标 Tag 且不显示进入按钮）、`views/lowcode/tenant-configs.vue`（当前租户参数，内置项不可删）；`composables/auth.ts` 新增 `switchTenant()` —— 落新令牌、清菜单与权限、`resetSessionRoutes()`，然后由调用方 `router.replace('/')` 让路由守卫按新会话重建，与刷新页面走同一条路径；顶栏用户菜单在切换态显示「租户：xxx（已切换）」与「回到平台租户」。两个新页面按 `views/<component>.vue` 自动解析规则命名（kebab-case），不需要往 `COMPONENT_ALIAS` 里加条目。
- **验收数据**：新增 `sql/verify/V9__dual_tenant_demo.sql`（租户 2 `acme` + 自己的部门 / 角色 / 菜单 / 字典 / 参数 / 账号 `acmeadmin`）。放在 `sql/verify/` 子目录里，mysql 镜像的 `docker-entrypoint-initdb.d` 不递归子目录，因此不会被默认导入，生产库也不要执行。`TenantIsolationApiTest` 9 个用例覆盖：每租户只查得到自己的账号 / 字典 / 参数、切换后可见且身份权限不变（`/auth/me` 新增 `tenantId` / `tenantCode` / `tenantName` / `homeTenantId`）、非 `super_admin` 不能切换、停用租户的账号登录被拒且启用后恢复、跨租户角色授权被拦、租户参数只在当前租户内读写、重名账号必须带租户编码。
- **顺手的复用**：导出用的 CSV 拼装从 `SystemManagementService` 私有方法提到 `scaffold-common` 的 `CsvUtils`（平台侧也要导出，按 AGENTS.md 不重复实现）；`PageQuery` 补 `offset()`，与既有 `normalizedPageNum/Size` 同一套归一化规则。
- **未交付（明确记在 §10.3 PR 3）**：租户开通流程 —— 新租户的管理员账号、角色与菜单初始化。当前「新增租户」只建租户与默认参数，进去是空菜单；验收数据里的 acme 租户是手工 SQL 灌出来的。

### 验收后修订（用户指出「租户参数页面 UI 没有按照规范」）

- **`views/lowcode/tenant-configs.vue` 重做成标准列表页**。原实现手搓了 `PageHeader + Panel + SectionLabel + DataTable`，违反 DESIGN.md「列表使用 `ListPage` 与 `useListPage`」，也缺了规范要求的查询 / 重置 / 分页 / 导出。改后与岗位管理等页面结构一致（DOM 指纹逐项对齐：页头说明、搜索区、重置/查询、表格卡标题、刷新与导出、行、分页）。
- **配套把后端接口改成分页**：`GET /api/platform/tenant-configs` 返回 `PageResult` 并支持 `keyword`（命中参数键 / 名称 / 备注），新增 `GET /api/platform/tenant-configs/export` 出 CSV；`TenantConfigRow.builtin` 由后端归一化成 boolean，与 `sys_config` 的返回形状一致。
- **顺带修掉一个租户隔离漏洞（真问题，不是风格问题）**：`LcTenantConfigMapper` 的关键词条件写成平铺 `WHERE kw IS NULL OR kw='' OR a LIKE .. OR b LIKE ..`，而拦截器补的是「原 WHERE **AND** tenant_id = ?」—— SQL 里 `AND` 优先级高于 `OR`，注入条件只绑到了最后一个 OR 分支，带关键词查询时**另一个租户的行会漏出来**（直查验收库：错误写法 3 行、加括号 1 行）。现在 OR 组整体加括号，并补了回归用例 `关键词查询也不能绕过租户过滤`（`project.main_branch` 两个租户各一条，带 keyword 必须只回 1 条）。底座既有 Mapper 的同类条件本来就是带括号写法，是我新写的模块没照做。
- **导出下发形态收敛**：CSV 的 `ResponseEntity` 组装从各 Controller 的私有 `csv()` 提到 `scaffold-framework/web/CsvResponse.attachment(filename, content)`，与 `CsvUtils` 配套，消掉 system / lowcode 三处重复。
- **表单空值口径**：`tenants.vue` 的到期日期与 `tenant-configs.vue` 的备注为空时提交 `undefined` 而不是空串（DESIGN.md 明确要求空值不写进后端）。
- 复验：后端 81 个测试全绿（新增关键词越权用例），前端 `vue-tsc` + `vite build` 通过，浏览器实测搜索 `main_branch` 只回 1 条、导出 200 `text/csv`。

## 2026-11-04（PR 2 追加：租户自助注册 —— 登录页切换注册表单，提交即开通）

用户补需求：「还需要租户注册页面，保持和登录页一致，就切换一下注册表单」。注册天然要把之前推到 PR 3 的**租户开通流程**一起做掉，否则注册完进去是空菜单。两个口径由用户选定：**注册立即开通**（不是待审核）、**新租户管理员拿全部业务菜单**（不是只给工作台）。

- **`POST /api/auth/register`（匿名 + 验证码）**：一个事务里建租户 → 复制平台租户的业务菜单树 → 建内置 `tenant_admin` 角色（`data_scope = ALL`）并全量授权 → 建管理员账号并绑角色 → 直接回登录令牌，注册成功即等于登录成功，前端不用再走一次登录。任一步失败整体回滚，不留半个租户。
- **菜单模板复制**：`LcProvisionMapper.selectTemplateMenus` 取平台租户菜单并按 `id` 升序（父级先于子级）逐条插入，用 `oldId → newId` 映射重建 `parent_id`；排除口径按语义写而不是硬编码 ID —— `NOT (path LIKE '/platform%' OR permission LIKE 'platform:%')`，所以新租户拿不到「平台管理」段。
- **注册要求管理员账号全局唯一**（比 `sys_user` 的 `(tenant_id, username)` 唯一键更严）：否则别人注册一个同名 `admin`，平台管理员下次登录就会撞上「该账号存在于多个租户，请填写租户编码」。这条是产品口径，不是数据库约束，写在 `TenantRegistrationService` 与文档里。
- **匿名写入的口径**：注册时没有租户上下文，拦截器不会注入 `tenant_id`，所以 `LcProvisionMapper` 每条语句都显式带 `tenant_id` 并标 `@InterceptorIgnore`；`LcTenantService.seedDefaultConfigs` 的 `operator` 改成入参（原来内部取 `SecurityUtils.getUsername()`，匿名调用直接抛 401 —— 这就是第一版注册测试全红的原因）。
- **前端**：登录页改为**同一张卡切换表单**（`mode = login | register`），版式、验证码、成功动画、进入逻辑全部共用；注册态四个字段（租户编码 / 租户名称 / 管理员账号 / 管理员密码）带与后端一致的 pattern 校验，底部一个 text 按钮在两种模式间切换，副标题与提交文案随模式变。`composables/auth.ts` 把「拿到令牌后的开场」抽成 `openSession()`，`login` 与 `registerTenant` 共用；注册成功顺带把租户编码写进 `sessionStorage` 预填。
- **注册也进登录日志**（`msg = 注册开通租户`），平台管理员在「登录日志」能看到租户是自己注册进来的。
- 验证：`TenantRegisterApiTest` 3 例（注册即开通并直接进入、拿不到平台管理菜单且默认参数已建、重复编码/重复账号/弱密码被拒），用例结束按 `tenant_id` 清理不残留租户；后端 84 个测试全绿，前端 `vue-tsc` + `vite build` 通过；浏览器实测注册 `demo` / `demoadmin` → 直接落到工作台，侧栏 12 个业务菜单、无平台管理，`/auth/me` 返回 `tenantCode=demo`、`roles=[tenant_admin]`、35 个权限码且不含 `platform:*`。
- **注意**：注册接口默认公开，任何能访问到前端的人都能开出租户（靠验证码 + 编码/账号唯一性挡）。单主机自用没问题，对外暴露前要先决定是否需要审核开关或白名单。

## 2026-11-03（低代码方案四次修订：模型定位为生成器输入、补模型↔页面契约、明确接口生成边界）

用户连续追问「后端模型怎么定义」「模型建完怎么和页面交互」「接口相关的生成怎么搞，不用设计 flow 流吗」，暴露方案里三处只画了表、没定语义的地方。纯文档变更，**未动代码**。

- **§1.4 新增：模型是生成器的输入（元模型 / 期望态），不是"模型管理"业务功能**。`lc_model` / `lc_field` 有 CRUD UI 容易被误读成业务模块，但它没有运行时语义 —— 在生成项目侧"模型"这个东西不存在，存在的只有被生成的实体、表、接口。给出**一条判据**贯穿全文：「这段逻辑能不能只靠字段名 + 字段类型推导出来？」能 → 进模型（B 档生成）；不能 → 进 C 档扩展点手写。
- **§1.4 明确"期望态与实际态必然分叉"是常态**：模型改了、项目实体可能已被人工改过。处理手段是 §5.3 的 diff 审查，不是让两边一致；平台不做一致性校验、不检测项目实体是否偏离模型（做不到也不该做）。
- **§1.5 新增：模型 ↔ 页面契约**。耦合只在 `field_code` 这一个符号上，分设计期（属性面板字段下拉的数据来自 `GET /api/lowcode/projects/{pid}/models/{modelCode}/fields`，页面不猜字段、不许手填）/ 生成期（同一份模板同时吃 `design_json` + `lc_field`，模型是"料"、页面是"形"）/ 运行期（`index.vue → api → Controller → Service → Mapper → 表`，平台不在链路上）。基数定死：一页面一主模型（跨模型只走 `REF` 外键下拉，**不生成 join**）、一模型多页面、`DASHBOARD` 允许不绑模型只出壳子。
- **新增反向引用检查**：`lc_page.ref_fields` 保存设计时解析出的字段引用，删字段/下线模型前按 `(model_id, field_code)` 反查拦截并列出受影响页面 —— 不做 JSON 全文搜索，也不能等发布时模板报错才发现。
- **§4.5 新增：接口生成边界**。① 标准 CRUD 六端点（list/get/add/update/delete/export）由模型直接推导，外加 5 个权限点、`@OperLog`、树表 `/tree`、数据范围 —— 不需要设计，只需配置；② 自定义接口（状态流转、批量、聚合、跨实体事务、外部调用）**一律走 C 档扩展点，不生成**，平台只生成扩展类骨架、注册路由菜单、按 `custom:{name}` 动作生成带 `TODO` 的 handler 桩；③ **flow / 流程编排本期明确排除**（从 §九 的"周期风险"升级为"范围排除"）：运行时解释执行会顶翻"平台不托管运行时"，编译成 Java 则等于再加一整个模板族 + DSL + 校验器；而 C 档手写 20 行 `updateStatus` 比发明流程 DSL 便宜一个数量级。
- **连带决定：状态流转不做元数据化**（不建 `lc_action`）。它看着比 flow 小，但入模型就要动作 DSL + 生成器分支 + UI。触发条件写进 §10.2 开放项 7：真实项目出现 3 个以上形态一致的状态机需求再上。
- **§3.4 补内置动作枚举**（`create`/`edit`/`delete`/`view`/`export`/`refresh`/`custom:{name}`），属性面板的字段绑定改为"只能从模型选、不允许手填"。
- **§九 风险新增四条**："什么都想配置"导致范围失控（用 §1.4 判据挡）、模型与项目实体分叉、删字段打断已有页面、以及原有设计器复杂度项归位到 P2 范围。§10.1 已定口径补模型定位/接口生成/页面与模型三条；§10.3 与 P3 交付物补 `custom` handler 桩与 import 走包。
- **修正残留**：§1.2 架构图 `scaffold-{business}` → `scaffold-business`（单业务模块，见 §5.2）；§1.3 C 档示例名从已废弃的 `{Cls}ServiceImplCustom` / `{resource}.ext.ts` 对齐到 §4.3 的 `service/{Cls}Extension.java` / `overrides/*.vue`。

## 2026-11-03（低代码方案三次修订：`@yuanda-lowcode-ai/scaffold-ui` 抽包定为既定路线）

用户指出「平台生成的项目用的也是同一套组件物料，所以需要复用一套组件」，据此推翻我上一轮把抽包降级为条件方案的判断。纯文档变更，**未动代码**。

- **改判**：`@yuanda-lowcode-ai/scaffold-ui` **必须抽包**，是既定路线而非备选。统一性只能在包里保证 —— 组件源码拷进项目意味着每个项目一份副本、修复靠人肉搬 diff。上一轮写的「② 用合并请求下发 A 档升级 / ③ 只在项目多到审不过来才抽包」三档表整体删除。
- **实测规模与真实障碍**（当前 main）：`@/components` 引用 **148 处 / 64 文件**（组件互引 83、页面引用 62），体量 73 个 `.vue` 组件 + 17 个 composable。改 import 是机械活，**障碍是 28 个文件直接 import app 层**：`@/api` 14（`UserSelect` 直接 `systemApi`、`PostLadder`/`DictSwatchbook`/`PasswordDialog`/`CommandPalette` 同）、`@/data` 10（多数只是类型，如 `DeptTree` 的 `import type { Dept }`，但该模块还装品牌常量）、`@/directives/permission` 8、`@/utils/*` 8。
- **解法定为注入点**：`installScaffoldUi(app, { http, permission, dict })` + `import '@yuanda-lowcode-ai/scaffold-ui/theme.css'`（7 个 css 合并为单一入口）。包面 = AGENTS.md 那份组件清单**全量进包**，带数据的组件也进（否则生成模板要替每项目重写一篇 `DictSelect`）。三个注入契约列为 PR 4B 第一刀（§10.2 开放项 6）。
- **包边界**：平台/演示专属组件不进包也不进项目 —— `GeneratorPipeline`、`OrgCanvas`、`MenuStarMap`、`PermissionMatrix`、`PermissionTrace`、`DictSwatchbook`、`AskDock`、`InsightPanel`，加设计器画布/属性面板/diff 视图。
- **仓库与发布**：包放**本仓库** `packages/scaffold-ui` + npm workspaces（不新建仓库：平台与组件同步演进，跨仓库来回改的摩擦大于收益）；发布走 **GitHub git tag 依赖**，零 registry 运维、版本号即 tag、与 `base_scaffold_sha` 天然对齐；token 走主机 `~/.npmrc`，延续"凭据不进 DB"。
- **时机是硬约束**：新增 **P1.5 / PR 4B（2.5 周）**，必须在 PR 5（FreeMarker 模板）之前 —— 模板与设计器画布都要先定组件引用方式是包名还是 `@/` 别名，晚抽包等于把设计器代码和所有历史项目生成物再改一遍。总排期 19 → **21.5 周**。
- **A 档范围随之缩小**（§1.3、§5.2、§6.4 同步）：组件与珍珠玻璃样式不再是 A 档源码，改为 `package.json` 里的依赖；"下发脚手架升级"从审一堆源码 diff 降为 bump 版本号。后端 common/framework/system 仍按源码拷进项目（Maven 侧不变）。
- **风险表新增两条**：私有 git tag 依赖导致生成项目 `npm install` 拉不到包（集成用例必须含一次干净 `npm ci`，不能只靠开发机缓存）；抽包行为回归面大（148 处引用），要求**分两步提交** —— 先搬路径建 workspace 保持行为不变、再改注入点解耦。

## 2026-11-03（低代码方案二次修订：作废 fork 组件复用口径，补生成项目裁剪清单与单一业务模块约束）

同日方案修订的续集。用户指出「scaffold-vue 就是给低代码平台用的」，据此追查发现一条更早的口径同样建立在已作废的假设上。仍是**纯文档变更，未动代码**。

- **「fork scaffold-vue 整个 app」口径作废（§〇）**：`git log -S` 追到 A/B/C 三选一那张表写于 `742eb0e`，其前提是文档初版 `ba3e0dc` 里的「低代码管理平台（**独立项目**）」—— 正是上一轮 §〇之二 第 1 条作废的那条。平台与组件同在 `scaffold-vue` 一个 app，低代码视图进 `src/views/lowcode/**` 即可，**不 fork、不 subtree、不为复用而抽包**；上一轮我只改了口径表却把「A ✅ 起步采用」留在原地，是自相矛盾，本轮清掉。
- **重述真正剩下的问题**：`scaffold-vue` 是 `private: true` 单体应用，影响的是「生成项目是组件库的一份拷贝，修复与升级不会自动流过去」。给出三档处置：① 不主动下发（本期默认）② **用已设计的合并请求下发 A 档升级**（推荐，PR 8 上线后近乎零额外成本）③ 抽 `@yuanda-lowcode-ai/scaffold-ui` npm 包。原「稳定后走 B」从既定路线降级为**条件触发**（项目多到 diff 审不过来才做）。
- **新增 §6.4 初始化快照裁剪清单**：生成项目拿到的不是本仓库全量，否则每个交付项目里都躺着一套设计器、租户管理和一个能反向生成代码的 `tool` 菜单。逐项列出剔除（`scaffold-lowcode`、`scaffold-generator` + `views/tool/**` + 菜单种子、`lc_*` 建表、设计器专用组件）/ 保留（common/framework/system、组件库、珍珠玻璃、登录页、工作台；`sys_*` 的 `tenant_id` 列与 `TenantContext` 保留、项目内租户恒为 1，避免两套 schema 与两套认证代码分叉）/ 预置（`scaffold-business` 空壳）。要求裁剪清单落成**代码里的显式白/黑名单**并配「裁剪后能编译、能起、能登录」的集成用例，进 PR 4 验收。
- **新增 A 档硬约束：一个项目只有一个业务模块（§5.2）**。核对 `scaffold-system/pom.xml` 与 `scaffold-admin/pom.xml` 后确认：新增 Maven 模块必须同时改父 pom 的 `<modules>` 与 admin 的 `<dependencies>`，而这两处是 A 档"初始化后固定"。若每个业务模块都改 pom，"固定代码不随发布更新"当场失效、每次发布都可能带 pom 冲突。故约定所有生成业务代码进 `scaffold-business` 的子包 `com.scaffold.business.{model}`，pom 初始化时一次写好；`lc_project.backend_module` 默认 `business` 正是为此（与 `lc_tenant` 那条同源的猜测这次对上了）。附带好处：A 档不进 diff，审的就是"这次发布动了哪些业务文件"。
- **`lc_project.base_scaffold_sha`**：记录项目快照取自本仓库哪个提交，是判断"该项目落后多少"与执行上述 ② 升级下发的起点。
- **§九 风险表**：「脚手架升级下发困难」的应对从"组件 npm 包发布"改为"合并请求下发 A 档升级"；新增一行「裁剪清单遗漏导致交付项目带着设计器与代码生成入口」，标为严重。
- **连带校正**：§〇 三重身份第 ② 条从"生成项目的运行时壳子"改为"生成项目的初始框架（裁剪快照，不含平台自身）"；§4.4 前端段补上不 fork/不抽包与裁剪说明；§3.6 选型理由去掉对已作废 B 方案的引用；§10.1 已定口径表同步；PR 4 范围与验收补裁剪清单；README 三重身份与组件复用两条重写。

## 2026-11-03（低代码方案修订：平台边界、发布改推仓库、新增平台内合并请求）

纯文档与口径变更，**未动代码**。方案文档此前有多处与已落地实现、与实际交付边界不一致，本次一并校正。

- **平台边界（新增 §〇之二）**：确立六条覆盖此前口径 —— ① 平台就长在本仓库（新增 `scaffold-lowcode` 模块），"独立项目、`lc_*` 表落另一仓库"的旧口径作废；② **平台不托管项目运行时**，它是研发期工具；③ 租户区分靠登录态，不靠泛域名（`tenant_code` 只做展示与外部标识，与实际 DDL 列注释一致）；④ Git 凭据走主机级、不进数据库；⑤ 设计器自研；⑥ 合并不自动，平台内做成 PR 式功能。
- **发布定义改写（§五）**：发布 = 生成 → 哈希对比 → 提交 → **只推 `gen_branch`**，不碰主干。原方案"每项目一个运行时实例 + 构建部署"隐含平台托管运行，资源与运维成本远超当前交付边界，取消。
- **新增合并请求设计（§5.3）**：对齐 GitHub PR 流程但全部在平台内执行 —— 选来源/目标分支、变更文件列表（按 A/B/C 档分组，带 `+/-` 行数）、unified diff 查看、可合并性 dry-run 预检、人工确认才合并推送。状态机 `草稿 → 检查中 → 可合并/冲突/陈旧 → 已合并/已关闭`。冲突**不在网页里编辑代码**，只列文件并回本地解决。执行顺序硬约束：合并提交与推送在同一动作内，成功后回读 `ls-remote` 才置状态。安全边界：永不 force-push、不 `pull --rebase`、不 `reset --hard`，远端与平台记录不一致只报告不"修好"。
- **文件所有权三档替代 `CUSTOM-START/END`（§5.2）**：A 固定（初始化后平台不碰）、B 纯生成（整文件覆盖，约定人工不改）、C 扩展点（生成一次骨架后平台不碰，业务逻辑写这里）。原方案的文本级 diff 合并机制取消 —— 它是"平台覆盖文件"与"保留手写代码"在同一文件上的强行调和，最容易静默丢代码。分档同时让危险变更（`DROP` / `ALTER ... DROP COLUMN`）不进生成产物，只以提示列出。
- **设计器选型定稿（新增 §3.6）**：对比 LowCodeEngine（最近 push 2025-03-10、npm `1.3.4` 2025-06 后无更新、664 open issues、设计器内核是 React 且仅支持 CDN 引入）与 FormCreate FcDesigner（活跃但只做表单、渲染器绑 Element Plus），选**自研**：代码生成路线不需要运行时渲染器，接 LCE 要把自有组件逐个重写成物料包（而平台与组件本来就在同一个 app 里，这份重写纯粹是为引擎付的税），且视觉体系与 AGENTS.md 冲突。只借其搭建协议规范作 `design_json` 结构参考。第一期只做列表页 + 表单弹窗。
- **模块落点与复用接缝（新增 §4.4）**：`GeneratorService.renderFiles()` 已返回 `Map<路径, 内容>`，`download()` 只是把它打成 zip，Git 发布是**换一条 sink**，生成逻辑不动。JGit 依赖只声明在 `scaffold-lowcode`，不沉 `scaffold-framework`（避免普通后台项目背上 JGit）。
- **Schema 校正（§2.2）**：`lc_tenant` 片段按 PR 1 实际 DDL 补齐 `deleted` / `remark` / `idx_lc_tenant_status` 并注明权威定义在 `V1__init_schema.sql`（文档草稿与实际表结构有出入）。`lc_project` 增加 `repo_url` / `main_branch` / `gen_branch` / `workspace_path` / `last_commit_sha`，并把 `init_status` 与新增的 `sync_status` 合并成单条生命周期状态机（两套状态会互相矛盾）。新增 `lc_merge_request` / `lc_merge_request_file`；`lc_page_version` / `lc_publish_log` 补 `tenant_id`（供租户拦截器统一过滤）与提交号、tag、文件计数、远端地址快照字段。`deploy_mode` 的 K8S 明确本期不实现。
- **数据源口径（§2.3）**：**动态数据源本期不做** —— 平台不连项目业务库，建表 SQL 作为产物推仓库由项目自己迁移；`lc_project.db_*` 只是交付给项目的连接配置，不是平台凭据。
- **初始化流程（§六）**：补仓库绑定与连通性检查、工作区卷 + Maven/npm 缓存卷，以及**空仓库边界**（GitHub 新建仓库无 `main`、HEAD unborn，clone 后 `add`/`commit` 会报错，需显式 `refs/heads/{gen_branch}` 创建分支，非空仓库要求存在脚手架标志文件才允许初始化）。
- **架构分层拆两节（§七）**：7.1 当前形态（单主机自托管 compose、登录态定租户、SSH 推仓库）与 7.2 远期能力（泛域名、OSS、多副本、K8S、平台托管运行、多租户各带凭据），后者明确不做、需要时先确认。原文把 SaaS 形态与单主机交付口径混在一张图里。
- **排期重算（§八）**：P0 拆成已完成的 P0-A 与待做的 P0-B；设计器 4 周 → 5 周（且限定只做两类模板）；P4「Git 集成 2 周」→ 4 周（含 diff 视图、可合并性预检、空仓库与工作区）。总计 16 周 → 19 周（一人约 4.5 个月）。
- **风险表（§九）**：删去已取消的 `CUSTOM-START/END` 应对项，补入用户输入进 Git 操作的注入风险（JGit 不经 shell + URL 协议白名单）、静默丢弃未推送发布、破坏性 DDL 下发、JGit 与原生 git 行为偏差。
- **§十 重写**：原文把已定事项（组件复用 A→B、设计器选型）重列成待决策，改为「已定口径」表 + 四项真正的开放项（`super_admin` 跨租户旁路、C 档文件约定、`design_json` 是否对齐 LCE 协议、多项目工作区并发锁）。
- **基线信息**：§〇 的 HEAD 从 `ba3e0dc` 更新到 `69631e7`；§2.1 平台元数据隔离方式从"独立数据库"校正为"与 `sys_*` 同库 + `lc_` 前缀"（与 PR 1 落地一致）。
- **README 同步**：`README.md`「元搭演进」关键口径重写，删除"平台作为独立项目""`CUSTOM-START/END` 保护"两条过期口径，补入不托管运行时、合并请求、三档所有权、JGit 与主机凭据；「当前阶段」从"P0 未开工"改为"P0-A 已完成，下一步 P0-B"。

- **生成路径与命名校正（§4.1–4.3）**：核对现有底座实际结构后发现方案里的生成物约定与底座分叉 —— 实际是 `scaffold-{module}/src/main/java/com/scaffold/{module}/...`（顶层非 `backend/`）、`domain/dto/{Entity}WriteRequest`（非 `{Entity}DTO`）、无 `domain/query` 包（列表用 `PageQuery` + 逐个 `@RequestParam`）、单类 Service（无 `service/impl`）、`R.success(...)`（非 `R.ok(...)`）、前端 `src/api/{module}.ts` 单文件与 `src/views/{module}/Users.vue` 复数平铺（无 `src/types`、无 `{r}/index.vue` 目录式）。§4.3 已按实际结构重写并给每个路径标注 A/B/C 档；§4.1、§4.2 加了醒目提示，把"生成物向底座看齐 vs 反过来重构底座分层"列为 PR 5 的第一刀（§10.2 开放项 2）。
- **PR 划分（新增 §10.3）**：PR 2 租户隔离收口 → PR 3 项目/模型/字段 → PR 4 JGit 与仓库绑定/工作区/空仓库初始化 → PR 5 FreeMarker 与文件分档 → PR 6 设计器第一期 → PR 7 发布推送 → PR 8 合并请求 → PR 9 回滚与端到端。每个 PR 附验收标准与依赖，PR 5 可与 PR 4 并行。

## 2026-11-03（前端品牌常量同步「元搭」）

- **补齐定名落尾**：`scaffold-vue/src/data/index.ts` 的 `APP.name` 由 `通用后台管理系统` 改为 `元搭低代码平台`，`APP.shortName` 由 `管理后台` 改为 `元搭低代码`。定名提交只改了 README 与方案文档，这两处前端常量掉队，页面标题与侧栏 logo 文案仍显示旧名。
- **有意不动的字段**：`APP.company`（登录页页脚版权主体）与 `APP.platform` 介绍位保持"通用后台底座"口径 —— 脚手架交付给下游项目时由接入方替换，不在平台定名范围内。
- **格式修正**：该文件此前被 IDE 按分号风格重排（35 行），与仓库其余 TS 源码的无分号风格不一致；本次按 HEAD 版本还原后只重新套用两行命名，未引入格式化差异。

## 2026-11-03（低代码平台定名「元搭」）

- **命名决策**：产品定名 **元搭**（Yuán Dā）。"元"取元数据驱动 / 元能力（平台造平台）/ 元学习（P2 AI 演进预留）三层；"搭"是核心动作，一秒懂产品干什么。拼音 `YuanDa`；域名候选 `yuanda.ai`（呼应 AI 方向）与 `yuanda.cn`。英文名走拼音而非 `MetaBuild` —— "Meta" 在软件类别是 Facebook 母公司商标雷区。
- **排雷结论**：公开 web 搜索未查到精确同名产品。同族拥挤但要区分的是"X 搭"家族（阿里宜搭、腾讯微搭、奥哲氚云、简道云），语义近似是 gitee 上 `MetaLowCode 美乐低代码` 开源项目（Meta = 元），品牌不同。对外文案统一带限定「元搭 · 低代码平台」避混淆。**待人工确认**：中国商标网第 9 / 42 类精确近似查询（公开 web 查不到官方库）与域名可注状态。
- **禁用口径不变**：本仓库示例、mockup、方案、代码注释不出现"悦海出行"及变体。
- **文本更新**：README 顶部演进提示块与新段标题切到"元搭"；`docs/lowcode-platform-proposal.md` 标题改为「元搭 · 低代码平台完整方案」，副标题加"元搭（Yuán Dā）是..."与 P2 演进说明。技术描述段落仍用"低代码平台"作类别词。

## 2026-11-03（多租户地基 PR 1）

- **背景与范围**：低代码平台 P0 的第一份可验证代码。本次只做地基，跨租户 SQL 过滤（MyBatis Plus `TenantLineInnerInterceptor`）、租户管理 UI、`lc_tenant_config` 与项目/模型/页面表都放后续 PR。
- **Schema**：按项目"初始化 SQL 是新项目起点，不是旧库自动升级"的约定，直接改 V1/V2，不写 V4。`V1__init_schema.sql` 新增 `lc_tenant` 表（`tenant_code` 唯一、`status`、`deleted`、`max_projects` 配额、`expire_time`），11 张 `sys_*` 表在 `id` 之后统一加 `tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID'`；跨租户唯一约束改为 `(tenant_id, 原键)` —— `sys_user(username)`、`sys_role(role_code)`、`sys_post(post_code)`、`sys_dict_type(dict_type)`、`sys_config(config_key)` 都改成复合唯一，允许不同租户重名；tenant_id 索引补齐（含 `sys_dict_data (tenant_id, dict_type)`、`sys_oper_log/sys_login_log (tenant_id, *_time)`）。`V2__seed_data.sql` 首行插入 `lc_tenant` 平台租户 `id=1, code=platform`，其余 sys_* INSERT 靠列 DEFAULT 1 自动归属，未逐个显式声明。关联表 `sys_user_role / sys_user_post / sys_role_menu / sys_role_dept` 保持不带 tenant_id：它们通过两端主键关联的实体已具备租户归属，跨租户串数据由应用层校验兜底。
- **TenantContext（scaffold-common）**：新增 `com.scaffold.common.tenant.TenantContext`，纯 `ThreadLocal` 零依赖；`PLATFORM_TENANT_ID = 1L` 常量暴露平台租户 ID；`set / getTenantId / clear` 三方法。业务代码禁止直接 set，通过 `SecurityUtils` 或本类读取。
- **LoginUser 加 tenantId**：`record LoginUser(Long userId, String username)` 改为 `record LoginUser(Long userId, String username, Long tenantId)`。全项目仅 `JwtService.parsePrincipal` 一处构造，其他调用方走 `SecurityUtils` 访问器；旧令牌（不带 tenantId claim）解析出 `tenantId = null`，由 `SecurityUtils.getTenantId()` 在使用点抛 401，`TenantFilter` 不因 null 抛错。
- **JwtService tenantId claim**：`generateToken` 签名改为 `(Long userId, String username, Long tenantId)`，多写一个 `tenantId` claim；`parsePrincipal` 抽出 `readLong` 辅助统一读 `userId` 与 `tenantId`，缺失时为 null。`JwtServiceTest` 补 `roundTripsUserIdAndTenantIdThroughClaims` 与 `nullTenantIdClaimParsesAsNullPrincipalTenant` 两条用例，旧 4 条用例改新签名。
- **TenantFilter**：新增 `com.scaffold.framework.tenant.TenantFilter extends OncePerRequestFilter`，从 `SecurityContext` 里的 `LoginUser` 读 `tenantId`、写入 `TenantContext`、`finally` 块清理避免污染线程池下一个请求；匿名请求（无 authentication 或 details 不是 LoginUser）不设置上下文。`SecurityConfig` 用 `.addFilterAfter(tenantFilter, JwtAuthenticationFilter.class)` 注册 —— 顺序硬约束：TenantFilter 依赖 JwtAuthenticationFilter 已经写入的 `Authentication#getDetails()`。
- **SecurityUtils.getTenantId()**：与 getUserId/getUsername 同形态，取不到时抛 `BusinessException(401, "获取租户信息异常")`。
- **认证链路 tenantId 打通**：`AuthUserRow` 加 `tenantId` 字段与 getter/setter；`AuthMapper.selectUserByUsername` SQL 加上 `u.tenant_id`；新增 `AuthMapper.countActiveTenant(tenantId)` 供登录校验租户状态；`insertLoginLog` 签名改为 `(tenantId, username, status, msg)`，`sys_login_log` 落表时带 `tenant_id`。账号不存在时用 `UNATTRIBUTED_TENANT = 1L` 占位（登录表单不带租户选择，无法判定归属）。`AuthService.login` 依次校验：账号密码 → 用户状态 → 租户状态（`countActiveTenant`），签发 token 时传 `user.getTenantId()`；`AuthService.logout` 从 `LoginUser.tenantId()` 读并写日志。
- **测试**：新增 `TenantContextTest` 4 项（set/get、clear、子线程不继承、PLATFORM_TENANT_ID 常量）、`TenantFilterTest` 3 项（正常写上下文并清理、匿名请求保持 null、旧令牌 tenantId=null 不抛错），`JwtServiceTest` 由 4 项扩到 6 项。`mvn -pl scaffold-common,scaffold-framework -am test` 通过 **20 项**（common 8、framework 12，0 失败 0 跳过）；`mvn -pl scaffold-admin -am package -DskipTests` 通过（全 5 模块编译，主代码 27 + 测试代码 3 + 其他 4 组）。
- **待用户处理**：本地 `scaffold_system` 库需要 `DbInit fix` 重建（走 V1/V2/V3 全套），因为原表结构没 tenant_id 列；重建前记得业务库不是这个（本机 3306 是业务容器）。集成测试 `mvn -pl scaffold-admin -am test` 尚未跑，需要新库就位后验证。
- **未做（明确留 PR 2）**：MyBatis Plus 拦截器自动注入 `tenant_id = ?`（需要先定 `super_admin` 跨租户旁路口径）、租户管理页 UI、`lc_tenant_config` 表、`lc_project` 与模型/页面表、`COMPONENT_ALIAS` 前端菜单动态过滤。

## 2026-11-03（合并两个待上游 PR、切换权威仓库、低代码平台方案落地）

- **仓库定位切换**：`feat/framework-global-sync`（64 文件前端组件重写、+5230 行）与 `feat/deploy-docker`（Docker 部署）合并到本地 `main`；新增 remote `lowcode-ai` 指向 `https://github.com/max-yuuuu/lowcode-ai.git` 并推送。**此后以 lowcode-ai/main 为基线**，原 `origin`（max-yuuuu/scaffold-master）与 `upstream` 保留但不再作为主推进。上游两个 PR 的处理策略由"等合并"改为"不再等"。
- **合并冲突处理**：`AGENTS.md` 自动合并；`PROJECT_MAP.md` 保留 framework 侧的 `V3__system_resource_menu.sql` 引用 + 保留 deploy 侧的 `docs/docker-deployment.md` 引用；`README.md` 保留 deploy 侧的 Docker 段落 + 采用 framework 侧的新验证码文案（放弃 deploy 侧的旧"页面显示的字符"表述）；`changelog.md` 两个 `## 2026-11-01` 段落并列保留（回迁 + Docker）。
- **低代码平台方案落地**：新增 `docs/lowcode-platform-proposal.md`（792 行），综合四个 agent 从架构、多租户、代码生成、前端设计器四个维度的深度分析。核心口径：租户→项目→模型→页面 四层层级；scaffold 三重身份（平台底座 + 生成项目模板 + 上游同步通道）；FreeMarker 模板替换 `GeneratorService` 的 StringBuilder 拼接、生成完整 CRUD 而非骨架；页面设计器采用拖拽 + 配置融合（流式布局、容器嵌套、属性面板），产出结构化 JSON 由运行时引擎映射到 `ListPage` / `FormDialog` / `DataTable`；增量发布按文件哈希对比只写变更；Git 集成支持文件级 / 页面级 / 发布级三级回滚，`CUSTOM-START/END` 标记保护手写代码。
- **README 演进说明**：顶部加"演进方向"提示块，新增"低代码平台演进"段列出关键口径与当前阶段。开发入口新增方案文档链接。
- **组件复用待决策项明确**：`scaffold-vue/package.json` 是 `"private": true` 单体应用而非可发布 npm 包，平台复用其组件的三条路径（fork 整个 app / 抽 `@yuanda-lowcode-ai/scaffold-ui` 包 / git subtree）在方案中标为待决策，倾向路径为"短期 fork 起步、稳定后抽包"。

## 2026-11-01（从业务工程回迁框架通用能力）

- **背景与口径**：以下能力在实际业务工程（网约车运营平台）上先落地并验证，本库原先只有精简版本。本次只搬**与业务无关的框架层**：全局修复与健壮性、系统管理新增页、组件库补齐；不搬业务模块、厂商 SDK（阿里云 OSS / 短信 / 一键登录）与 Excel/Pdf 导出工具链，因此**没有新增任何 Maven 依赖**。
- **图形验证码不再回传明文**：`CaptchaService` 改为 java.awt 无头渲染 base64 PNG（随机底色 + 干扰线噪点 + 逐字符旋转，不引第三方验证码库），答案只存 Redis 并在 `verify` 时消费；响应字段由 `uuid + code` 改为 `captchaEnabled`（是否启用验证码）、`uuid`（校验标识）、`img`（base64 PNG 图片），`code` 明文彻底不出服务端。新增开关 `scaffold.security.captcha.enabled`（环境变量 `CAPTCHA_ENABLED`，默认开启）；`scaffold-admin` 的 surefire 以系统属性关闭验证码，`TestAuthClient` 改为断言关闭状态并提交空验证码，避免集成测试读图。新增 `CaptchaServiceTest` 三条用例覆盖「只回图片、按 Redis 答案校验并删除、关闭时不落 Redis 且直接放行」。
- **令牌携带用户 ID**：新增 `LoginUser`（用户 ID + 账号），`JwtService.parsePrincipal` 解析 `userId` 声明，`JwtAuthenticationFilter` 把它放进 `Authentication#getDetails()`；新增 `SecurityUtils` 统一读取当前登录人（`getUserId` / `getUsername` / `getLoginUser` / `isAuthenticated`），业务代码不再自行解析请求头。
- **`@Anonymous` 免鉴权白名单**：新增 `@Anonymous` 注解与 `PermitAllUrlProperties`，启动时扫描标注的 Controller / 方法，把路径变量 `{xxx}` 归一为 `*` 后加入 Spring Security 放行列表（兼容 PathPatternParser）；`SecurityConfig` 消费该列表。回迁时把原实现的 `commons-lang3` `RegExUtils` 换成 JDK 正则替换，保持零新增依赖。
- **操作日志不再拖垮业务请求**：`OperationLogAspect#params` 原先直接 `valueToTree(joinPoint.getArgs())`，导出 / 下载类接口持有 `HttpServletResponse` 时抛 `getOutputStream() has already been called`，异常处理器又想用 JSON 写回已预设为附件的响应，形成二次失败。现剔除 `ServletRequest` / `ServletResponse` / `MultipartFile` / `BindingResult` / `Model`，并把兜底从只捕 `JsonProcessingException` 扩到 `IllegalArgumentException | IllegalStateException`：参数记不住时只丢日志内容。客户端 IP 改由新增的 `framework/web/IpUtils` 按代理头优先级解析（原来是直接取 `request.getRemoteAddr()`，经 Nginx 后全记成容器内网地址）。
- **日期不再整体早 8 小时**：新增 `spring.jackson.time-zone: ${JSON_TIMEZONE:GMT+8}`。数据库连接把会话时区强制成 `+08:00`，而 Jackson 默认按 UTC 序列化 `java.util.Date`，`yyyy-MM-dd` 这类跨天字段会直接少一天；只对齐序列化时区，不加 `date-format`，避免改动既有接口的日期线格式。
- **页面过渡卡死修复**：多根页面（`ListPage` 与 `FormDialog` / `Drawer` 是兄弟节点）在 `<Transition mode="out-in">` 下没有元素根，leave 钩子不执行导致 `isLeaving` 永真、新路由渲染成注释占位，enter 钩子也从不触发使 `.reveal` 停在 `opacity: 0`（DOM 有内容但肉眼是白屏）。`AppShell.vue` 在 `<component :is>` 外补一层 `.route-frame` 元素根；`motion.ts` 新增 `pageRootOf` 把动画与 `is-entrance-ready` 落回容器内的 `.page`，`pageEnter` / `pageLeave` 加幂等 `finish()` 与兜底定时器，补间被打断也一定放行 `done()`；`ListPage` 自带 `onMounted(() => revealPage(root))` 兜住片段根场景。
- **宽表格可用**：`DataTable` 外包 `.table-shell`，用 `scrollbar-width: none` + `::-webkit-scrollbar { display: none }` 隐藏常驻横向滚动条（滚动能力不变：触控板横扫、Shift + 滚轮、触屏拖动照常），横向滚动后给外壳加 `is-hx-scrolled`；勾选列 `.col-check` 改 `position: sticky; left: 0` 与右侧操作列对称，吸附列补 `--surface-solid` 底色与 `tr:hover` / `tr.is-selected` 叠色，并加一条 `inset -1px 0 0 var(--line)` 内阴影提示左侧已吸附。`.content` 由 `overflow-x: clip` 改 `auto` 并补 `min-width: 0`（`.route-frame` 同步），内容真超宽时由内容区兜底横滚而不是被深色外框裁掉。
- **组件与交互补齐**：新增 13 个 UI 组件（`DateInput`、`DateRangePicker`、`Textarea`、`NumberInput`、`TreeSelect`、`Tabs` / `TabPane`、`Upload`、`Photo`、`ImageField`、`Descriptions` / `DescriptionsItem`）与 `RowActions` / `RowMenuItem`（行内前若干动作平铺、其余收进 teleport 到 body 的「更多」菜单，表格横滚与 overflow 裁剪都盖不住）、`popover` / `dateValue` / `upload` 三个 composable；`Panel` 增 `padded` 启用原本闲置的 `.panel-body`；`TrendChart` 增可选 `max` 锁定纵轴上限（百分比序列自适应会抬到 150% 这种读不通的刻度，不传时行为不变）；`PermissionButton` 在只给 `label` 未给 `icon` 时按文案取默认图标，避免行操作全体退化成三个点；`http` 增 `downloadForm`（导出接口以 POST + 表单参数接收查询条件）并抽出共用的 `readBlob`。业务词表（打款、轨迹详情等）与高德地图工具不随组件库带入底座。
- **新增页面免登记**：`composables/auth.ts` 的 `COMPONENT_MAP` 改为 `COMPONENT_ALIAS` + `import.meta.glob('../views/**/*.vue')` 约定解析——底座既有 11 个页面因文件名与 `sys_menu.component` 不同名保留显式别名，其余按 `views/<component>.vue` 自动解析，新增页面不再改这个文件；路由构建时解析不到加载器的条目直接跳过，避免注册出空白路由。
- **系统管理新增「资源监测」**（`/system/resource`，权限码 `system:resource:list` 查询资源实时读数）：后端 `ResourceMonitorController` + `ResourceMonitorService` 全部只读采集、不落库不建表——JMX 取主机 CPU / 物理内存 / 交换 / 磁盘挂载点与 JVM 堆、GC、线程、类加载、文件描述符，Redis 走连接自身的 `INFO` / `PING`，数据库读 HikariCP `HikariPoolMXBean` 池水位（活跃 / 空闲 / 排队线程 / 上限，**不需要任何数据库权限**）与 `SHOW GLOBAL STATUS|VARIABLES`、`information_schema.tables`（需要 PROCESS 权限，受限账号下只有这一段降级）。利用率一律回传 `-1` 表示采不到，页面显示「—」而不是 0%；macOS 上 `getCpuLoad()` 恒为 -1，改按两次采样的进程 CPU 时间差 / 墙钟差折算，整机取不到时用 1 分钟负载 / 核数折算并在页面标注「按负载折算」。前端 `views/system/resource/index.vue` 沿用非列表页骨架（`PageHeader` → `.stack` → 多个 `Panel`），5 秒自动刷新、页面切后台即暂停、卸载清理定时器；`sql/V3__system_resource_menu.sql` 在系统管理(100) 下新增页面 108，只补超级管理员(1) 的授权。
- **品牌只搬结构**：登录页左侧介绍位与页脚版权 / 备案改为读 `APP.platform`（眉标、主标语、说明句）、`APP.company`、`APP.beian`（新增 `BeianItem` 备案条目类型），960px 以下画布不再整块隐藏而是退化成一条备案页脚；`APP.copyright` 已无引用故删除。本库取值保持通用口径（不写公司名与备案号，`beian` 为空数组），重置密码默认值仍为 `Yy@123456`，`index.html` 标题不动。
- **有意不搬**：业务模块与业务页面、厂商 SDK 与对应配置键、Excel 导出与 POI 工具链、RuoYi 风格的 `scaffold-common` 全量工具类。两处即使搬过来也没有调用方，因此确认不做：`PageQuery.orderByClause` 排序白名单（当前所有 Mapper 都是静态 `ORDER BY`，没有拼接入口）、`views/system` 七个管理页（两边代码本就逐字相同，无差异可迁）。
- **验证**：后端 `mvn -pl scaffold-framework -am test` 通过 11 项（公共模块 4、`JwtServiceTest` 4、新增 `CaptchaServiceTest` 3），`mvn -pl scaffold-admin -am package -DskipTests` 打包通过；前端 `npm ci`、`npm run test:unit` 21 项通过、`npm run build`（含 `vue-tsc --noEmit`）通过，产物中可读到资源监测页与 `/system/resource` 请求。随后在同一 docker MySQL 实例上另建独立校验库（导入 V1/V2/V3，17 张表，避开业务工程在用的开发库），`mvn -pl scaffold-admin -am test` 通过 **57 项**（common 4 + framework 7 + admin 集成 46，0 失败 0 跳过）：46 项全部经由 `TestAuthClient` 登录，等于「surefire 注入验证码关闭 + 夹具断言关闭态并提交空验证码」这套新契约经真实 Spring 上下文跑通，`OperationLogAspectApiTest` 2 项同时确认切面剔除容器对象后审计仍能正常写库。浏览器实测记录：资源监测页 8 个面板与真实读数（物理内存 99.1% 判「紧张」、Hikari `active 0 / idle 10 / max 10 / awaiting 0`、MySQL 会话 20/151）随 5 秒自动刷新递进；`.route-frame` 存在且页面 `opacity: 1` / `transform: none`、`.reveal` 无残留隐藏；用户列表表格内容 1040px 而容器 732px，`scrollLeft = 240` 后外壳拿到 `is-hx-scrolled`、勾选列出现内阴影；未显式传 `icon` 的 `PermissionButton` 按文案取到 edit / trash；无效令牌访问 `GET /api/system/resource` 返回 401。页面实测还发现资源监测页脚说明引用了未迁入本底座的「基础设施 · 服务器信息」静态台账页，已删除该半句。`scaffold-vue/scripts/session-regression.py` 也已真跑（后端以 `CAPTCHA_ENABLED=false` 启动、独立校验库、Playwright + Chromium 在临时 venv 里装），**第一次跑直接把两处迁移遗漏逼了出来**：① 脚本原先依赖旧验证码契约——`captcha["code"]` 与页面 `.captcha-code` 文本，改成服务端出图后这两处都取不到值，现改为要求后端关闭验证码、等输入位从 DOM 移除再判（`captchaEnabled` 初值是 `true`，立刻查存在会把首屏瞬时渲染误读成开启）；② 管理员菜单数断言 11 → 12（新增资源监测页）。同时脚本还暴露出一个真实的数据缺陷：`V3` 照搬了业务工程「同时授予角色 2」的写法，而本底座的角色 2 默认没有任何菜单，结果 `chensy` 登录后只有资源监测一个页面、落到 `/system/resource` 而非 `/no-access`；已改为只补超级管理员(1) 的授权，其余角色由管理员在角色管理中分配。修完连跑两次均 `EXIT=0`，两条 PASS 全过；夹具用户与角色按逻辑删除，行留在表里、`deleted` 置 1。

## 2026-11-01（Docker 部署）

- **交付口径变化**：AGENTS.md 的部署段原写「当前交付范围为本地开发底座，不包含部署」，本次按用户明确要求补上单主机自托管的 Docker 部署，该段已改写为指向 `docs/docker-deployment.md`，并把边界划到「域名、TLS、多副本与云端编排不在默认交付范围内」；**只加容器编排，不改任何业务代码、不加任何 Maven / npm 依赖**。
- **编排**：`docker-compose.yml` 提供前端 nginx、后端 jar、Redis 三个常驻服务，MySQL 放进 `mysql` profile 默认不启动，避免与本机已有数据库抢端口；`deploy/mysql-client.cnf` 与 `.env.example` 配套。变量拆分口径：`DB_PORT` 是后端连库的端口，`MYSQL_HOST_PORT` 是内置 MySQL 映射到宿主机的端口，两者不复用同一个语义（否则外部模式下 `DB_HOST=host.docker.internal` 会连到错误端口）。`backend` 对 `mysql` 用 `depends_on` + `required: false` 的可选依赖：启用 profile 时等 MySQL 健康再启动，不带 profile 时自动跳过，不会反向把数据库拉起来。
- **冷启动竞态**：首版编排里后端只依赖 Redis，`up -d --profile mysql` 会让后端早于 MySQL 就绪启动，日志出现 `java.net.ConnectException: Connection refused`，且 `/api/health` 不探测数据库、仍报 healthy，前端被放行后首个业务请求返回 500（Hikari 懒初始化，随后自动恢复）。改用可选依赖后同样的启动序列里连接拒绝为 0 条。
- **镜像**：`scaffold-system/Dockerfile` 用 `maven:3.9-eclipse-temurin-17` 构建、`eclipse-temurin:17-jre-jammy` 运行，运行层另装 `curl` 专供容器健康检查，`ENTRYPOINT` 预留 `JAVA_OPTS`；`scaffold-vue/Dockerfile` 用 `node:22-alpine` 跑 `npm run build`（含 `vue-tsc` 类型检查）、`nginx:1.27-alpine` 托管。两处 `.dockerignore` 排除 `target/`、`node_modules/`、`dist/`，构建上下文不再携带本机产物。两个 Dockerfile 都不写 `# syntax=docker/dockerfile:1`：没有用到 BuildKit 专属特性，写上只会多一次跨网拉 syntax 镜像的失败点。
- **前端反代**：`scaffold-vue/nginx.conf` 把 `/api` 转发到 `backend:8080` 并带上 `X-Real-IP` / `X-Forwarded-*`（与回迁的 `IpUtils` 代理头解析口径一致）、SPA 深链接回退 `index.html`、`/assets/` 长缓存、开 gzip。生产前端继续走相对 `/api`，不需要新的环境变量。
- **踩到的两个坑**：① `COPY --from=build dist` 在本机 BuildKit 下报 `"/dist": not found`，改为绝对路径 `/build/dist` 后通过；② 内置 MySQL 首次建卷导入 SQL 时，官方镜像容器内 locale 为 `C`，客户端默认按 latin1 解析 UTF-8 字节，种子中文被二次编码存库（`sys_user.nickname` 入库为 `C3A5E28098…`，页面显示「å‘¨æ˜Žè¿œ」），挂 `deploy/mysql-client.cnf` 固定 `[client] default-character-set=utf8mb4` 后同一行入库为 `E591A8E6988EE8BF9C`，与「周明远」的 UTF-8 字节一致。
- **本机网络**：Docker 守护进程不读宿主机 shell 代理变量，基础镜像直连 Docker Hub 超时。为完成构建在 `~/.docker/daemon.json` 加了 `registry-mirrors`（daocloud、1ms.run）并在 Docker Desktop 设置里开了手工代理（`ProxyHTTPMode=manual` 才生效，只写 `HttpProxy` 无效），属于本机环境配置、未写入仓库。
- **验证**：全部用独立 project name、独立卷、错开端口（`WEB_PORT=8181/8182/8183/8186` 与默认 80、`MYSQL_HOST_PORT=3316~3322`）的临时实例跑通，**未读写本机业务库 `scaffold_system`**；结束后逐个 `down -v` 清理容器与卷，本机 `scaffold-mysql`、`dsh` 容器全程正常。后端镜像 471 MB、前端镜像 77 MB；`docker compose config` 在带 / 不带 `mysql` profile 下均通过；不带 profile 单起 `redis backend` 确认 MySQL 不被可选依赖反向拉起。接口链路实测：`/api/health` 返回 `status=UP` 且时间为 `+08:00`、`/api/auth/captcha` 返回 base64 PNG、无令牌访问列表 401、验证码开启时缺验证码登录被拒（`code=400 验证码错误或已过期`）、SPA 深链接 `/system/user`、`/tool/generator`、`/monitor/operlog` 均回退 200、导出接口返回带中文表头与中文数据的 `text/csv`。关掉验证码后完成真实登录：拿到 token、`/api/auth/me` 返回 35 条权限、`/api/auth/routes` 返回四个顶级路由、`/api/system/users` 返回 3 条种子用户。浏览器实测登录页 → 工作台 → 用户管理表格渲染出「周明远 / 平台架构部」，控制台无报错；验证码开启形态下登录页的 `img` 实测渲染出 130×44 的 PNG。字符集回归：修复前 `sys_user.nickname` 入库为 `C3A5E28098…`（双重编码），修复后为 `E591A8E6988EE8BF9C`，与「周明远」的 UTF-8 字节一致，`sys_menu` 39 行导入完整。外部库路径在依赖顺序改动后重验：`DB_HOST=host.docker.internal` + `DB_PORT` 与 `MYSQL_HOST_PORT` 同值时登录成功且 `sys_login_log` 真实落库，后端日志零条连接拒绝。验证过程中因漏设 `DB_PORT` 让一次测试请求打到了宿主机 3306，已核实业务库里既没有该测试库也没有任何新增写入，该陷阱已写入部署指南。本次为接口与页面渲染验证，未做压测与多副本编排。
- **文档**：新增 `docs/docker-deployment.md`（前置条件、两条启动路线、环境变量中文口径、运维命令、验证方法、受限网络排障、验证记录），README 开发入口与 PROJECT_MAP 模块文件地图同步登记部署链路。

## 2026-10-11（MIT 开源发布）

- 以当前代码建立独立的公开版本快照，采用 MIT License；第三方依赖保留各自许可，README 单独说明 GSAP 许可。
- 开源内容保留前后端源码、测试、初始化 SQL、锁文件、必要开发脚本及当前架构文档；排除宣传视频、旧规划、编辑器配置、依赖、构建产物、缓存、日志、备份和私密配置。
- JWT 密钥改为必填环境变量 `JWT_SECRET`，启动时校验 UTF-8 编码至少 32 字节且不能全为空白；新增无需数据库和 Redis 的密钥边界与签名验证测试。
- 初始化示例电话留空，示例邮箱改用 `example.com`；清理公开文档中的本机绝对路径和已排除目录入口，保留历史日志与人工备注。
- 发布整理仅作用于独立副本，原项目工作区及本地提交历史保留。
- 验证：独立副本 `npm ci`、21 项前端单元测试、含类型检查的前端构建、8 项后端公共模块与 JWT 单元测试、完整后端编译打包均通过；核对必要源码、文档链接和排除规则，敏感信息模式扫描未发现问题。本次未执行数据库集成测试或浏览器业务回归，未操作实际数据库。

## 2026-10-11（宣传视频 v2：45 秒卡点重制）

- 重新设计为约 45 秒的版本：开场用"普通表格 → 高光快剪"制造反差，按 120 BPM 节拍网格剪辑，每个动效都推近并跟拍，深色模式切换放在 drop 上；口播改用 `zh-CN-YunjianNeural`，并改写成短句。
- 清晰度：录屏视口改为 1440×810、DPR 3，加 `--force-device-scale-factor=3` 后 screencast 输出真实的 4320×2430 帧（不加时 headless 只输出 CSS 尺寸）。`clock.js` 把页面时钟和 CSS 动画同步放慢 10 倍再抓帧，内容帧率约 70fps。用 Pillow 亚像素裁切加 Lanczos 渲染推拉镜头，最终只做一次 H.264 CRF 14 编码。
- 声音：`music.py` 用 numpy 合成 BGM 和 8 种音效（无外部采样）；口播出现时 BGM 自动压低，两遍 loudnorm 到 -14 LUFS。
- 竖版改为深色画布：上方是段落标题，中间是 4:5 主画面，并单独设计一套镜头轨迹，不再用模糊背景铺底。
- 产出：`out/v2/` 下横版 2560×1440 和 1920×1080、竖版 1440×2560 和 1080×1920，均为 60fps、47 秒；另有 SRT 字幕、横竖封面、音频预览。v1 脚本保存在 本地历史宣传素材目录，v1 成片保留不动。
- 验证：横版 1440p 码率 12.7 Mbps；响度 -14.1 LUFS，峰值 -1.2 dBFS。在同一界面区域按 1:1 截图对比 1080p 的 v1 与 v2，v2 的文字和图形明显更清晰。抽帧核对了取景、字卡和字幕的时间与位置。

## 2026-10-11（宣传视频清晰度重制）

- 录屏由 JPEG 88 抓帧改为原生 1920×1080 PNG 抓帧，取消将 1080p 图像放大成 2880×1620 的伪高分辨率步骤；素材使用 RGB 无损编码，以毫秒时间基保留动作时序、按记录时长封口并转换为 30fps，在关闭页面前等待抓帧确认完成。
- 合成的中间片段改为 RGB 无损编码，最终只做一次 H.264、CRF 14 压缩，并统一 BT.709 色彩与有限范围；竖版主体采用轻微锐化，上下背景沿用原稿的虚化效果。取景按素材实际尺寸折算，兼容原素材。
- 增加素材与成片目录参数；本次新素材写入 `_clips_hd/`，高清横竖成片、字幕和封面使用 `_hd` 后缀，保留原口播、剪辑场景与原文件；同步录制说明及项目地图。
- 验证：9 个场景完成实际页面录制；横版 1920×1080、竖版 1080×1920，均为 30fps、2158 帧、71.94 秒，两版全片解码通过；核对关键场景的文字、取景和字幕，口播音频与字幕内容和原版一致。片段按累计帧数对齐，避免逐段时长舍入产生累计偏移。

## 2026-10-11（修复：浮层内滑块首帧错位）

- 分段选择器、侧栏选中块、命令面板高亮、代码生成步骤条共用的 `slideThumb` 在祖先处于缩放过渡（弹窗 / 命令面板从触发点展开、页面进场）时量到的是缩放后的包围盒，首次打开菜单编辑弹窗时「菜单类型」滑块偏小错位，切换一次才纠正；现按祖先当前缩放比折算回布局像素。`Segmented` 另监听各选项尺寸（字体加载完成只改选项宽度时也重新对位）。
- 表单字段内的分段选择器不再被纵向 flex 拉伸成整行空槽（`.field > .segmented` 按内容定宽）；部门画布适配视口改用布局尺寸，不受页面进场缩放影响。

## 2026-10-11（图谱舞台回归珍珠玻璃）

- 撤销暗场舞台：删除 `.stage-scope` 深色令牌，改为浅 / 深主题各一套 `--graph-*` RGB 三元组；舞台底改为与外壳一致的浅底点阵 + 环境光，深色只随外观设置切换。
- 视觉隐喻替换：中心光球改为珍珠球（珠光渐变、淡虹彩、缓慢扫描的刻度环与青柠卫星点）；程序化水母神经元改为玻璃刻度表盘，刻度着色比例为真实读数（已分配角色的用户、有持有人的角色、已授权页面）；叠加发光、粒子迸发改为实色细线、18% 光环脉冲与开场细环涟漪。
- 溯源全景：条目改为围绕表盘的整齐刻度弧与放射细线，跨类授权线只在悬停聚焦时出现，名称沿放射方向排开；条目过多时不画名称。台阶、分析栏、问答坞、工具条全部回到珍珠玻璃材质。

## 2026-10-11（设计审查与第三轮重构：暗场舞台、全站中文化、数据口径修正）

- 暗场舞台（已于同日撤销，见上条）：`tokens.css` 新增 `.stage-scope`（舞台底、暗玻璃、`--glow-1~5` 发光色，并在舞台内重新声明文字 / 描边 / 面板令牌，分析栏与问答坞自动换肤）；新增 `composables/neural.ts` Canvas 绘制件（光球、程序化神经元、发光丝、脉冲、迸发、真实数据背景网络、轨道、台阶光柱、地平线）；`RelationGraph` 新增 `variant="stage"`、`network` / `networkFocus` / `networkAlpha` / `orbits`、`slab` 台阶节点与 `hover` 事件、「重播 / 暂停」，删除原随机三角网背景；可点小目标不再漂移。
- 权限溯源重做为三层下钻（全景 → 模块 → 实体），背景网络为全部用户 / 角色 / 菜单真实点位与授权线；菜单星图重做为「导航星系」（三圈刻度轨道、分支扇出、角色 → 页面 → 按钮链路，侧栏预览改为占位不覆盖）；岗位阶梯重做为「能量阶梯」（对角线铺满不裁切、光柱与地平线、脊线脉冲、成员触须，删除右侧大空面板；窄屏横向滚动）。
- 全站文案中文化：`SectionLabel` 去掉英文眉标与编号，`PageHeader` / `ListPage` 去掉 `eyebrow`；工作台日期、读数标签、用户看板（最近登录 / 从未登录）、部门读数、登录页、命令面板、矩阵、泳道等英文标签改为中文；参数面板 `light / dark / auto` 显示为浅色 / 深色 / 跟随系统（取值不变）。`.eyebrow` 改为中文小标签样式。
- 工作台：删除布局重叠且与常用入口重复的「模块网络」卡；数据权限气泡改为横向占比条（删除 `BubblePack`）；删除页头与侧块重复的规格铭牌。操作日志 / 登录日志删除与概览读数重复的页头铭牌。
- 稀疏数据：`TrendChart` 非零点 ≤ 3 时改画茎叶点并标数值；`Swimlane` 默认聚焦到有操作的时段（至少 6 小时），可切全天；`DeltaPill` 新增 `fresh`「新增」态。
- 表格：操作列吸附右侧，用户表在 1440 宽下操作列不再被挤出可视区。
- 后端口径修正：登录成功写入 `sys_user.last_login_time`（最近登录时间，此前从未写入导致看板恒显示「从未登录」）；岗位 / 角色在岗人数与工作台数据权限分布排除已删除用户；`/dashboard/summary` 的 `stats[].fresh`（上一周期为 0、无法计算环比）新增。`AuthApiTest` 补最近登录时间断言。
- 验证：前端 `npm run test:unit` 21 个用例、`npm run build`（含 vue-tsc）通过；后端 `mvn -pl scaffold-admin -am test` 46 个集成测试通过；Playwright 1440 / 390 宽、浅色 / 深色、减少动态效果截图核对三个舞台与工作台、日志、用户页；舞台页无头实测满帧、最差帧约 10ms。

## 2026-10-11（神经网络升级：全模块差异化视图与全局炫技）

> 分工说明：Phase 0–6 公共底座与前六个模块由 Claude 完成；共享视口抽取、Phase 7 岗位阶梯、Phase 8 全局炫技点及文档/验证由 Devin（SWE）完成，条目分别以【Claude】/【SWE】标注。

- 【Claude】公共底座：`composables/motion.ts` 扩展 `spring`、`createFrameLoop`、`revealCascade`、`typeOut`、`bezierAt`、`firstTimeInSession`；新增 `RelationGraph`（BFS 逐层蔓延入场、贝塞尔连线脉冲、扇形触须、悬停邻接高亮、拖拽平移与缩放）、`InsightPanel`、`AskDock` 与本地规则问答引擎 `ask.ts`；`instruments.css` 补 `.rg-*`、`.insight-*`、`.heat-*` 样式；令牌新增 `--thread`、`--thread-active`、`--pulse-halo`、`--node-bg`（浅深两套）。
- 【Claude】权限溯源：`GET /api/system/permissions/graph`（权限 `system:role:list`）返回用户、角色、菜单及用户-角色、角色-菜单两组关联；角色页新增「溯源」视图（三能力岛环绕开场、选中角色扇形展开用户与菜单、`InsightPanel` 读数下钻、底部问答坞），用户页行操作带 `?trace=user:ID` 跳转聚焦。
- 【Claude】登录热力与异常：`GET /api/monitor/login-logs/heatmap?days=`（权限 `monitor:loginlog:list`）返回 7×24 `cells` 与连续失败 `anomalies`；登录日志「02 趋势」可切热力格，对角线波纹点亮、悬停十字高亮、失败占比斜线，「03 异常」卡片点按联动明细筛选。
- 【Claude】参数设置面板：参数页默认「面板」视图（可切表格），按键名前缀分组、按值推断控件行内编辑，改动累积到底部浮条显示「原值 → 新值」一次提交，失败项保留标红。
- 【Claude】菜单星图与导航预览：菜单页新增「星图」（目录 / 菜单 / 按钮三环 radial 布局，按钮显示权限码），有角色权限时叠加角色环，孤立菜单与缺权限码按钮红描边；左侧固定导航预览实时渲染当前树，隐藏 / 停用项虚化。
- 【Claude】代码生成流水线与字典样本册：生成抽屉改三步流水线（表信息 → 字段映射贝塞尔连线 → 代码逐行级联预览）；字典页新增「样本册」视图，真实 `DictTag` 平铺，点卡片 Flip 原地展开编辑（label / tone / 排序 / 默认项金标）。
- 【SWE】共享视口：抽取 `composables/viewport.ts`（`useViewportPan`），组织画布与关系图复用同一套拖拽平移、Ctrl/⌘ + 滚轮缩放、pointer capture 与 reset 逻辑，移除两处重复实现。
- 【SWE】岗位职级阶梯：用户分页、统计与导出接口新增可选 `postId` 参数（经 `sys_user_post` 关联过滤，沿用 `@DataScope` 数据权限）；新增 `PostLadder` 组件，岗位页增加「阶梯」视图——按 `sort` 排成上升台阶，`TickRing` 显示在岗人数，点卡片右侧扇形展开在岗成员触须。
- 【SWE】命令面板「问系统」：输入 `?` 切换问答模式，展示预设问题芯片，提交后打字机 + 答案卡级联，卡片芯片可跳转对应页面；`ask.ts` 新增参数意图（开关参数、登录验证码是否开启、敏感参数值），走 `system:config:list` 权限过滤与 `configs.list` 数据；参数页支持 `?keyword=` 定位。
- 【SWE】登录神经网络背景：新增 `NeuralNet.vue`（Canvas 节点 = 能力模块，连线脉冲往返、节点缓慢呼吸），置于登录页左侧深色画布底层；登录成功时中心迸发短划并收束到对勾，页面隐藏、离屏、reduced-motion 时暂停或静态呈现。
- 【SWE】工作台开场与模块网络：本会话首次进入工作台，卡片从中心弹簧飞出依次落位；新增「模块网络」小卡（用户 / 角色 / 菜单 / 部门节点 + 关联脉冲，点节点跳转对应页面）；`/dashboard/summary` 的 `stats` 新增 `menus`（菜单项总数）与 `depts`（部门数）两项。
- 【SWE】细化动效：部门画布选中节点时根 → 当前节点路径连线高亮并沿曲线走脉冲；用户看板拖拽成功落位与取消回飞改用阻尼弹簧；操作日志详情抽屉与角色授权抽屉内容接入 `revealCascade`（约 70–90ms 级联，reduced-motion 静态呈现）。
- 【SWE】验证：前端 `npm run test:unit` 21 个用例通过，`npm run build`（含 vue-tsc）通过；后端 `mvn -pl scaffold-admin -am test` 45 个集成测试通过（含用户 `postId` 岗位过滤、权限图、登录热力与新增 summary 字段断言）。浏览器实测：登录页网络呼吸与成功迸发、工作台开场弹簧入场与模块网络节点跳转、岗位阶梯台阶与成员触须（含深色）、部门画布选中路径脉冲、命令面板 `?` 问答（预设芯片 + 三类参数意图应答 + 芯片跳转）、操作日志 / 角色抽屉级联、375px 无横向溢出、reduced-motion 静态呈现，全程无控制台错误。后端测试需本地 MySQL 与 Redis，连接信息走 `DB_*` / `REDIS_*` 环境变量。

## 2026-10-11（UI 精致化：仪器化语汇与六种版式）

- 新增仪器化语汇：等宽眉标与编号、显示级读数（逐位滚动、上标单位）、深色规格铭牌、环比胶囊、刻度尺、斜线填充、悬停名片；令牌补充等宽字体、显示字号、刻度、铭牌、5 枚只表示类别的分类色与画布环境光，样式新增 `instruments.css` 一层。
- 新增自绘图表：趋势图（切换时间档时折线形变、准星读数平滑跟随、失败序列与区间斜线）、柱状图、60 刻度成功率环、气泡占比、时间轴泳道（实时「现在」线、失败与慢操作标记）。
- 工作台改为主次布局：登录脉搏主卡（总量、环比、峰值 / 均值）+ 在线与接口侧块 + 近 7 天操作量 / 成功率环 / 数据权限气泡 + 当日操作泳道（有日志权限时）+ 最近登录与常用入口。
- 操作日志、登录日志改为横带分层（概览 → 趋势 / 泳道 → 明细）；点击泳道块按模块过滤明细。部门管理新增组织架构画布（平移缩放、连线先到节点后亮、四角浮动面板）；用户管理新增看板（按部门 / 角色 / 状态分列，按状态可拖拽启停，提供键盘「移动到…」）；角色管理新增菜单 × 角色权限矩阵（十字高亮、级联勾选、底部浮条一次提交，内置角色只读）。视图偏好按页面记在本机。
- 登录页改为全幅深色画布：实时时钟刻度、服务健康与延迟、能力面板；登录卡随指针轻倾，登录成功走「收拢 → 对勾圆环 → 进入」三拍；进入外壳的导航被取消时恢复登录按钮。
- 浮层与外壳：弹窗从触发点展开、列表行生长成详情抽屉并收回原行、批量操作改为底部深色玻璃浮条、命令面板分组为最近访问 / 页面 / 用户 / 动作并以玻璃滑块指示焦点、提示改为可悬停展开的牌堆（悬停暂停计时、倒计时细环）、主按钮涟漪、内容滚动后顶栏凝结、表格中文短字段不再逐字折行。
- 后端新增只读接口：`GET /api/monitor/oper-logs/stats`、`GET /api/monitor/oper-logs/timeline`、`GET /api/monitor/login-logs/stats`（`MonitorStatsService` / `MonitorStatsMapper`）；工作台汇总新增 `trendPrev`（上一周期登录数）与 `dailyOps`（近 7 天按日操作量）。不改表结构。
- 同步 `DESIGN.md`（七层样式、令牌、版式、能量语态表）、`PROJECT_MAP.md`、两份架构 / 接口文档与 UI 方案文档。
- 验证：后端在新建的独立校验库 `scaffold_verify` 上 `scaffold-admin` 41 个集成测试通过（含新增 `MonitorStatsApiTest` 4 个及工作台字段断言）；前端 15 个单测、类型检查与构建通过；会话回归脚本两项通过。浏览器在 1440 / 375 / 320 px 与深色下检查工作台、两个日志页、部门画布、用户看板、角色矩阵、登录页，11 个页面 320 px 无横向溢出、无 Vue 警告；实测泳道、看板拖拽停用与恢复、矩阵授权保存与还原、命令面板用户搜索跳转、行展开抽屉、提示牌堆展开。测试中的临时改动均已还原。浏览器面板处于后台时 rAF 不运行，入场长帧未能实测。

## 2026-10-10（通用后台脚手架整理与验收）

- 移除建筑、工作流模块及 Flowable 依赖，后端保留公共基础、框架、系统管理、代码生成和启动模块；数据库首次初始化收敛为 `V1__init_schema.sql`、`V2__seed_data.sql`。
- 前端采用 Vue 3、GSAP 与自有珍珠玻璃组件，移除 Element Plus 和 three；统一主题、表格密度、列表模板、查询、表单、权限按钮、字典及组织选择组件，重写登录、工作台和 10 个管理页面。
- 修复切换账号时旧路由初始化、旧菜单响应、慢退出请求及旧账号 401 覆盖新会话的问题；退出同步清除路由、权限及字典/部门缓存，未分配菜单的账号显示明确落点。
- 登录 token 增加独立标识，避免同秒重新登录生成相同 token；停用或已删除角色不再授予菜单和权限。用户页面改为按用户操作权限获取必要选项，业务字典选项与管理接口权限分开。
- 超级管理员自动获取全部菜单与权限；用户按部门筛选包含下级部门；工作台 7 天与 30 天趋势包含当天；代码预览返回全部文件，生成菜单 SQL 与当前字段及手动主键结构一致。
- 数据库、Redis 连接读取环境变量；MySQL 时区参数通过连接属性传递，避免 `+08:00` 被 URL 解码为无效值，应用连接与初始化工具统一设置数据库会话时区。
- 修复页面切换空白、减少动态效果与后台标签页关闭浮层的问题；浮层按打开顺序处理层级、焦点和 Escape，下拉层级跟随所在弹窗；长内容抽屉保持底部按钮可见，正文独立滚动。
- 新建根目录 `README.md`、`PROJECT_MAP.md`，重写 `DESIGN.md`、前端说明及三份架构/接口文档，更新 `AGENTS.md`；保留历史规划和日志，移除历史文档中的实际数据库密码，忽略本地缓存、日志和私有环境文件。
- 验证：41 个后端测试通过并完成打包，15 个前端测试通过，类型检查与构建通过。真实浏览器完成会话竞态与低权限验证、字典/菜单增改删与导出、生成器导入/配置/7 文件预览/ZIP 下载；检查 11 页在 320/375px、深色模式及减少动态效果下的显示，以及主题跟随、密度持久化、菜单搜索和嵌套浮层键盘操作。生成菜单 SQL 在集成测试中实际执行并回滚。
- 验证使用独立本地校验库。按确认删除四份旧报告/需求文档，并重建 `scaffold_system`；初始化数据移除不存在的业务表生成配置，生成器从空列表开始。代码生成输出仍是开发骨架，业务 CRUD 和页面接入需在后续项目中实现。

## 2026-06-08 (侧栏动态菜单修复)

- 修复前端侧栏动态菜单跳转：后端返回的 `/system/role`、`/system/dept` 等路径型路由不再被当作 Vue Router `name` 处理，避免点击左侧菜单时报 `No match for {"name":"/system/role"}`。
- 新增导航工具单元测试，覆盖静态菜单路由名和动态菜单路径两种跳转来源。

## 2026-06-07 (最终验收修复)

- 修复后端集成测试登录夹具：新增 `TestAuthClient`，统一按真实契约先调用 `GET /api/auth/captcha` 获取 `captchaUuid` 和验证码，再提交 `POST /api/auth/login`。
- 更新认证、系统管理、监控日志、工作流、代码生成等测试中的旧登录写法，避免验证码校验拦截导致验收测试无法覆盖真实业务接口。
- 更新 `scaffold-vue/README.md`，将“纯前端 Mock、未接后端接口”的历史说明改为当前真实接口接入状态，并补充 `VITE_API_PROXY_TARGET` 代理配置说明。
- 完成最终验收验证：`mvn test` 通过 42 个后端测试，`mvn package -DskipTests` 打包成功，`npm run test:unit` 通过 18 个前端单元测试，`npm run build` 通过；本地启动后端 `18081` 和前端 `5178`，经前端 `/api` 代理完成验证码、登录、当前用户和工作台摘要真实接口冒烟；`admin-redesign` 独立原型 `npm run build` 通过。

## 2026-06-06 (Flowable 工作流闭环)

- 接入真实 Flowable 基础运行时：`scaffold-admin` 显式声明 `flowable-spring-boot-starter-process`，dev 配置开启 Flowable schema 自动更新和 schema 更新锁，并关闭异步执行器。
- 新增 `V3__flowable_workflow.sql`：扩展 `wf_model_meta.bpmn_xml`、`deployment_id`、`process_definition_id`、`version`，新增 `wf_approval_record` 审批记录表，并补充工作流业务索引。
- 重构 `scaffold-workflow`：新增 `WorkflowStatus`、`WorkflowRequests`、`BpmnTemplateService`，模型部署改为 Flowable BPMN 部署，流程定义从部署后的 `processDefinitionId` 绑定。
- 强化工作流完整性和权限：BPMN XML 强制校验可执行 `process id` 与 `modelKey` 一致；审批接口校验当前用户必须是任务处理人、候选用户或候选组成员；撤回动作仅允许流程发起人执行。
- 兼容旧已部署模型：定义列表会自动为 `deployed=1` 但缺少 `process_definition_id` 的旧模型补部署默认 BPMN，并回填 Flowable 部署 ID、流程定义 ID 和版本号。
- 新增流程实例闭环接口：`POST /api/workflow/instances/start` 发起实例、`GET /api/workflow/instances/{processInstanceId}/trace` 查询实例轨迹；待办改为按当前用户和角色候选组读取 Flowable task，审批后完成/终止流程并写入 `wf_approval_record`。
- 流程定义和待办接口改为从 BPMN XML 解析真实节点名称；流程轨迹在完成态补充“归档”结束节点，避免轨迹停留在最后一个人工审批节点。
- 保留旧演示数据兼容：没有 Flowable 历史实例的历史 `wf_business` 待办仍可展示、审批和查看静态轨迹，避免初始化种子数据失效。
- 前端流程模型页新增 BPMN XML 编辑和默认模板生成；流程定义页新增发起流程弹窗并展示真实 BPMN 节点；待办卡片和审批抽屉进度条使用后端返回的真实 BPMN 节点；审批抽屉优先按 `processInstanceId` 加载真实实例轨迹；API 类型支持字符串任务 ID。
- 工作流前端列表改为后端 keyword 查询和分页：流程模型、流程定义、我的待办、我的已办均具备查询、重置、分页、导出；待办页新增 loading 状态，避免接口加载时误显示空任务。
- 优化工作流 UI：隐藏非发起人的“撤回”动作，提交按钮增加提交中禁用，移除侧边栏固定待办角标，修复长业务编号/流程 key/审批意见可能撑破卡片或抽屉的问题。
- 待办接口和前端类型补充 `businessKey` 业务编号，待办页本地搜索同步支持业务编号，避免后端搜索命中后被前端二次过滤隐藏。
- Vite 开发代理新增 `VITE_API_PROXY_TARGET` 环境变量覆盖能力，默认仍指向 `http://localhost:8080`，便于本地端口冲突时验证当前后端实例。
- 更新 `docs/backend-architecture.md` 和 `docs/frontend-backend-integration.md`，补充 Flowable 架构状态、接口和字段中文说明。
- 完成验证：`mvn -pl scaffold-admin -am test` 通过 42 个后端测试；`npm run test:unit` 通过 18 个前端单元测试；`npm run build` 通过；浏览器在 `http://127.0.0.1:5177` 完成流程模型 BPMN 模板、流程定义发起弹窗、待办实例轨迹和审批提交冒烟，当前页面无 console error/warn，UI 烟测数据已清理。

## 2026-06-06 (性能与健壮性修复)

- 修复前端 Vite 代理端口：`vite.config.ts` 代理目标从 `localhost:18080` 改为 `localhost:8080`，与后端实际监听端口一致。
- 修复前端 `http.ts` import 顺序：将 `authSession` 的 import 语句移到模块顶部，修正不规范的 import 位置。
- 修复后端监控日志删除/清空权限标识错误：操作日志和登录日志的 DELETE 接口从错误的 `list` 权限改为正确的 `remove` 权限，防止只有查看权限的用户删除日志。
- 移除安全配置中 `/api/test/**` 的 `permitAll` 放行规则，生产环境不再暴露测试端点；将测试安全配置移入 `TestSecurityConfig` 仅在测试时生效。
- 增强全局异常处理器：新增 `HttpMessageNotReadableException`（JSON 格式错误返回 400）、`MethodArgumentTypeMismatchException`（参数类型错误返回 400）、`IllegalArgumentException`、`HttpRequestMethodNotSupportedException`（返回 405）、`NoResourceFoundException`（返回 404）和通用 `Exception`（返回 500 并记录日志）处理，防止堆栈信息泄露。
- 修复 JWT 解析异常静默丢弃问题：添加 debug 级别日志记录失败的 token 解析。
- 修复 DashboardService 登录趋势统计的潜在 NPE：对数据库返回的 `hourValue` 和 `total` 增加 null 检查。
- 为前端流程模型部署/删除、流程定义挂起/激活、待办审批、代码生成配置保存等异步操作补充 try-catch 错误处理，防止 API 异常导致页面无反馈。
- 继续清理前端残留演示交互：工作台、流程模型、流程定义、我的已办导出改为统一 CSV 下载；工作台待办“审批”跳转到真实待办页，不再伪造审批成功。
- 新增剪贴板工具 `copyText` 并接入代码生成预览弹窗，“复制当前”改为调用浏览器 Clipboard API，失败时给出明确错误提示。
- 参数配置和字典管理的“刷新缓存”类假提示改为真实重新加载列表；登录页“忘记密码”空链接改为“联系管理员重置密码”静态提示。
- 修复待办页流程进度 Vue prop 告警：新增 `normalizeFlowStep` 将后端返回的当前节点名称映射为进度索引，`FlowSteps` 支持数字和节点名称两类输入。
- 修复部门管理删除后的前端状态残留：新增 `removeDeptFromTree` 工具并接入部门删除流程，删除成功后立即从本地部门树剔除软删节点并切回父级/根部门，避免页面继续显示已删除部门。
- 修复监控日志前端关键词过滤健壮性：新增 `includesKeyword` 工具，登录日志过滤兼容空用户名/IP，操作日志过滤兼容空字段并纳入请求参数，避免 Vue update 阶段因 `null.includes` 报错。
- 修复移动端后台外壳样式：小屏侧栏强制图标化，隐藏顶栏搜索和用户姓名，收敛工作台/待办卡片、筛选框和流程进度条的固定宽度，消除移动视口内横向溢出。
- 优化字典管理行操作可见性：字典类型编辑/删除按钮在选中行、悬停和键盘聚焦时保持可操作，避免行操作完全依赖 `display:none` 的 hover 状态。
- 修复操作日志 CSV 导出契约：导出列新增“请求参数”，确保按 `request_params` 命中的日志导出后仍能看到筛选命中字段，并新增回归测试覆盖。
- 完成验证：`npm run test:unit` 通过 16 个前端单元测试，`npm run build` 通过，`mvn -pl scaffold-admin -am test` 通过 35 个后端测试；浏览器抽样验证工作台导出/待办跳转、流程模型/流程定义/我的已办导出、代码生成预览复制和待办页流程进度无当前页面 warn/error。
- 完成浏览器路由冒烟：在 `http://localhost:5175` 逐页打开工作台、系统管理、系统监控、工作流程和代码生成共 15 个主要路由，核心标题均加载成功，当前页面无 warn/error；移动 390px 视口复验工作台和我的待办，`.app-content` 无水平滚动。
- 完成真实浏览器写操作联调：在 `http://localhost:5175` 通过页面完成参数配置新增/编辑/删除、岗位管理新增/编辑/删除、流程模型新建/配置/部署/删除；浏览器控制台无来自当前前端的 warn/error，数据库核查 `sys_config`、`wf_model_meta`、`wf_business` 无测试残留，`sys_post` 无 `deleted = 0` 的有效测试残留。
- 扩展真实浏览器全功能联调：继续覆盖用户新增/编辑/重置密码/删除、角色新增/编辑/权限保存/删除、菜单新增/编辑/显示切换/删除、部门新增/编辑/删除、字典类型和字典数据新增/编辑/删除、操作日志和登录日志筛选/详情/导出/批量删除、代码生成导入表/配置保存/预览复制/下载、流程定义流程图/挂起/激活/导出、待办审批和已办导出；所有测试数据清理后无有效 `ui_smoke` 残留。
- 完成真实 API 写操作烟测：从配置解析 `scaffold_system` 数据库连接并查表结构后，通过 8080 实例完成认证、部门、岗位、角色、用户、菜单、字典、参数、流程模型、流程定义、临时待办审批、操作日志和登录日志共 12 类写操作验证，10 项测试数据清理成功，数据库无活跃 `smoke_` 残留。

## 2026-06-06

- 补齐监控日志写能力：新增操作日志/登录日志按 ID 删除和清空接口，前端批量删除、清空改为真实后端调用，并新增 `MonitorLogWriteApiTest`。
- 补齐监控日志导出：新增 `GET /api/monitor/oper-logs/export` 和 `GET /api/monitor/login-logs/export` CSV 下载接口，前端操作日志/登录日志导出按钮改为真实文件下载，并扩展 `MonitorLogWriteApiTest` 与前端 API 映射测试。
- 补齐用户管理操作接口：新增 `GET /api/system/users/export`、`PUT /api/system/users/{id}/status` 和 `PUT /api/system/users/{id}/password`，前端用户导出、批量启停、重置密码改为真实后端调用，并扩展 `SystemWriteApiTest` 与前端 API 映射测试。
- 修复角色删除校验：角色关联的用户已被软删除时，不再阻塞角色删除，避免角色管理中已无有效用户仍提示“角色已分配用户”。
- 新增统一数据权限能力：增加 `@DataScope` 注解、`DataScopeAspect` 切面、`DataScopeCriteria` 上下文和 `DataScopeResolver` 解析接口，系统模块按当前用户角色合并 `ALL`、`DEPT`、`DEPT_AND_CHILD`、`SELF`、`CUSTOM` 数据范围，并接入用户列表和用户 CSV 导出。
- 新增统一操作日志能力：增加 `@OperLog` 注解、`OperationLogAspect` 切面、`OperationLogRecorder` 记录接口和 JDBC 写入实现，覆盖系统管理、账号改密、工作流和代码生成写接口；操作日志请求参数自动脱敏 `password`、`token`、`secret`、`authorization` 等敏感字段，并支持按请求地址和请求参数检索。
- 补齐代码生成数据库表导入：新增 `GET /api/tool/generator/importable-tables` 和 `POST /api/tool/generator/import`，通过 `information_schema` 导入 `gen_table` 与 `gen_table_column`，前端导入弹窗改为真实接口，并新增 `GeneratorImportApiTest`。
- 补齐代码生成 zip 下载：新增 `GET /api/tool/generator/tables/{id}/download`，生成 Entity、Mapper、Service、Controller、前端 API、Vue 列表页和菜单 SQL 文件压缩包；前端“生成/下载全部”改为真实下载。
- 补齐流程模型写能力：新增模型创建、编辑、删除接口，前端流程模型页新增真实新建/配置/删除弹窗交互，并新增 `WorkflowModelWriteApiTest`。
- 新增当前用户修改密码接口 `PUT /api/auth/password`，校验 `oldPassword` 后加密写入 `sys_user.password`；顶部栏修改密码弹窗改为真实接口，成功后退出重新登录，并扩展 `AuthApiTest` 与前端 API 映射测试。
- 清理顶部栏和登录页残留演示交互：搜索改为真实路由跳转，应用按钮改为快捷入口，通知读取工作台真实待办，个人中心展示当前登录用户信息，登录页文案改为测试账号提示。
- 补齐前端通用 CSV 导出工具：新增 `toCsv`/`downloadCsv`，将角色、岗位、菜单、部门、字典数据、参数配置和代码生成列表的假导出提示改为真实 CSV 文件下载，并新增 CSV 转义单元测试。
- 将 `scaffold-vue/src/data/index.ts` 从历史 Mock 数据文件收敛为类型、侧边栏导航和头像色板定义，页面数据源均走 API。
- 更新 `docs/frontend-backend-integration.md`、`docs/backend-architecture.md` 和后端开发计划状态，补充新增接口字段中文说明。
- 完成验证：`npm run test:unit` 通过 9 个前端单元测试，`npm run build` 通过，`mvn -pl scaffold-admin -am test` 通过 34 个后端测试，`mvn -pl scaffold-admin -am package -DskipTests` 打包成功，并在 `http://127.0.0.1:18080` 完成登录、工作台、工作流、生成器导入候选、生成器 zip 下载、数据权限过滤、软删用户后角色删除、改密错误校验、用户停用登录拦截、用户重置密码登录、用户 CSV 导出、操作日志自动写入与敏感字段脱敏真实接口烟测。

## 2026-06-05

- 初始化 `scaffold-system` 后端 Maven 多模块工程，新增 `scaffold-common`、`scaffold-framework`、`scaffold-system`、`scaffold-generator`、`scaffold-workflow`、`scaffold-admin` 模块。
- 新增 `ScaffoldAdminApplication`、`GET /api/health` 健康检查接口、统一响应模型 `R`、分页模型、业务异常、全局异常处理和 traceId 过滤器。
- 为后端基础能力新增 JUnit 测试，覆盖统一响应、分页参数、分页返回、健康检查、traceId 和业务异常响应，并完成 Maven 测试、打包和本地 jar 启动验证。
- 新增 早期后端开发计划（历史资料），基于现有文档和 `scaffold-vue` 前端 Demo 反推 `scaffold-system` 后端工程结构、数据库、接口、模块开发顺序和验收计划。
- 新增 `scaffold_system` MySQL 数据库初始化脚本 `V1__init_schema.sql` 和 `V2__seed_system_data.sql`，创建系统用户、角色、菜单、部门、岗位、字典、参数、日志、代码生成和工作流元数据等基础表，并写入 BCrypt 密码种子数据。
- 为 `scaffold-admin` dev 环境补充 MySQL 与 Redis 配置，完成表结构、审计字段、种子数据行数和用户数据的数据库校验。
- 实现后端认证接口：`POST /api/auth/login`、`GET /api/auth/me`、`GET /api/auth/routes`、`POST /api/auth/logout`，支持 JWT、Redis token 会话、登录/登出日志和动态菜单路由。
- 新增 `TokenStore` 并让 JWT 过滤器校验 Redis 会话状态，保证登出后的旧 token 无法继续访问受保护接口。
- 扩展认证集成测试，覆盖登录成功、登录失败、未带 token、动态路由、登出和登出后 token 失效；完成 Maven 测试、打包和真实 HTTP 验证。
- 新增系统管理只读接口：用户分页、角色分页、菜单树、部门树、岗位分页、字典类型分页、字典数据、参数配置分页，以及操作日志/登录日志分页查询。
- 新增 `SystemManagementMapper`、`SystemManagementService`、`SystemManagementController` 和 `MonitorController`，通过 SQL 聚合返回前端需要的 `deptName`、`postName`、`roleName`、`userCount` 等展示字段。
- 开启 MyBatis `call-setters-on-nulls`，保证 Map 响应中 `lastLogin` 等空值字段仍保留，稳定前端接口字段契约。
- 新增 `SystemReadApiTest` 集成测试，覆盖系统管理只读接口的分页、树结构、字典、配置和日志响应，并完成 Maven 测试、打包和真实 HTTP 抽样验证。
- 将 dev 环境 `com.scaffold` 日志级别从 `debug` 调整为 `info`，减少接口运行时 SQL 调试日志刷屏。
- 优化 `admin-redesign` 事项详情交互，将全屏模糊遮罩小弹窗改为非阻断右侧详情抽屉，保留主页面可见性并补充事项摘要、处理上下文和流转记录。
- 补全 `admin-redesign` 静态页面所有可见按钮的前端交互：全局搜索、环境切换、用户菜单、顶部导航、数据刷新、台账筛选、事项详情、通过/驳回、侧栏查看全部、审计中心与审计详情。
- 为 `admin-redesign` 新增命令搜索弹层、顶部下拉菜单、详情/列表弹窗和 Toast 反馈，保证按钮点击均有可见结果。
- 新建 `admin-redesign` 独立前端原型目录，从零创建 React + Vite 静态后台管理页面，不复用现有 `scaffold-vue` 代码。
- 按用户确认的高级发散方向实现 `Editorial Control Desk` 风格后台首页，包含顶部编辑式导航、指标条、审批与权限台账、权限风险、流程积压、生成器状态和实时审计日志。
- 为 `admin-redesign` 增加基础交互：导航选中、台账筛选、行选中联动审计事项、刷新时间更新。
- 新增 `admin-redesign/design-qa.md`，记录构建、浏览器可访问性树、视觉方向和交互验证结果。
- 按用户要求删除 `admin-redesign` 前端改造项目及其全部文件，停止对应 Vite 本地服务；保留原始静态 HTML 目录和当前项目其他文件。
- 新增 `admin-redesign` 前端项目，将 本地原型目录 的静态 HTML/CSS/JS 原型转换为 Vue 3 + Vite + TypeScript 架构。
- 将静态原型样式迁入 `admin-redesign/src/styles/prototype`，保留原型视觉语言并通过 Vue 页面复用。
- 新增 `admin-redesign/src/api`、`components`、`directives`、`layouts`、`mock`、`router`、`stores`、`types`、`utils`、`views` 等规划目录。
- 将原型布局转换为 `AdminLayout.vue`，将登录、工作台、系统管理、监控、工作流和代码生成页面按业务域拆分到 `views`。
- 新增 `BaseButton`、`BaseTable`、`SearchForm`、`FormDialog`、`StatusTag`、`PermissionButton`、`DictSelect` 等统一组件。
- 新增 `docs/admin-redesign-architecture.md`，说明静态原型资源到 Vue 规划架构的转换关系和关键字段中文含义。
- 修正 `admin-redesign` 的复刻精度：将图标体系替换为原型自定义 SVG path，按原型结构重写登录页、工作台和用户管理页，并将用户 mock 数据扩展为接近原型的 56 条。
- 为满足一比一复刻要求，新增 `admin-redesign/public/exact-prototype`，完整复制 本地原型目录 静态原型资源，并将 `admin-redesign` 默认入口切换为该原型基准渲染。
- 将 `admin-redesign` 默认入口切回真实 Vue 渲染，按原 HTML 原型的 class、布局和内容结构重做登录页、后台布局、工作台、用户、角色、菜单、部门、字典、参数、日志、工作流和代码生成等页面。
- 新增 早期产品技术规划（历史资料），规划 Spring Boot + Vue 后台管理脚手架的产品定位、技术栈、架构、核心模块、数据库原则、接口原则和迭代路线。
- 新增 `docs/prd.md`，整理后台管理脚手架的用户角色、核心流程、功能需求、页面规划、非功能需求和 MVP 验收标准。
- 新增 `scaffold-vue` 前端 Mock Demo，包含 Dashboard、RBAC + 数据权限管理、Flowable 审批中心等核心页面。
- 新增 早期前端 Demo 实现计划（历史资料），记录前端 Demo 实现计划。
- 将 `scaffold-vue` 前端 Demo 的菜单、页面标题、按钮、表格、工作流和占位模块等用户可见文案统一调整为中文。
- 补齐 `scaffold-vue` 前端 Mock 页面：用户管理、菜单管理、部门管理、字典管理、代码生成、流程模型、流程定义、审计日志、系统设置。
- 为前端 Demo 增加本地点击交互：筛选、重置、新增、编辑、状态切换、角色分配、代码预览、流程部署、审批操作、日志导出和系统设置保存。
- 新增 `scaffold-vue/src/utils/exportCsv.ts` 通用 CSV 导出工具，并为所有表格页面补充导出入口。
- 在产品技术规划和 PRD 中补充通用表格能力，明确所有列表页默认支持导出当前筛选结果。
- 新增项目级 `AGENTS.md`，将统一组件复用、统一设计语言、前后端通用能力复用定义为必须遵守的开发准则。
- 在产品技术规划中补充强制开发准则，明确新增功能优先复用或扩展通用能力。
- 补齐 `admin-redesign` 岗位管理页面、导航、路由、权限标识和 mock 数据，并按静态原型修正岗位数据、表格选择列、排序图标、分页控件与标题栏布局，修正系统管理侧边栏菜单顺序不一致的问题。
- 按静态原型重做 `admin-redesign` 菜单管理页，将扁平表格改为菜单树结构，补齐展开/收起、树形缩进、目录/菜单/按钮标签、显示开关和图标操作列，并按原型菜单树数据恢复可见行顺序。
- 启动逐页逐组件视觉审计，批量生成 Vue 与静态原型截图和 DOM 摘要；按审计结果重做部门管理、操作日志与登录日志的可见结构、文案、表头、筛选区、数据和分页控件。
- 按静态原型区分流程模型与流程定义、我的待办与我的已办页面，补齐流程定义表头、数据、状态与操作按钮，以及我的已办表格、审批结果和分页结构。
- 修正我的已办页多余筛选卡，保持与静态原型一致的单表格卡片搜索结构。
- 机械导出静态原型 `data.jsx` 为 `prototypeData.ts`，让用户、角色、部门、菜单、岗位、字典、参数和代码生成列表复用原型同源数据，避免可见内容漂移。
- 将流程模型和待办/已办页面切换为 `prototypeData` 同源流程数据，修正模型、待办卡片和代码生成等页面的可见数据差异。
- 为参数配置页补齐与原型一致的分页条和每页条数选择器。
- 为 `scaffold-system` 新增后端权限注解能力：`@RequiresPermission`、权限解析器、AOP 拦截器和 403 统一异常响应，实现后端接口级权限校验。
- 补齐系统管理写接口：用户、角色、菜单、部门、岗位、字典类型、字典数据和参数配置的新增、修改、删除接口，复用统一响应、统一分页、JWT 会话和权限注解。
- 为系统管理写接口新增集成测试 `SystemWriteApiTest` 和 `SystemCatalogWriteApiTest`，覆盖低权限用户拒绝访问、用户/岗位 CRUD、角色/部门/菜单/字典/参数配置 CRUD。
- 完成 `scaffold-system` 后端全量验证：`mvn -pl scaffold-admin -am test` 通过 18 个测试，`mvn -pl scaffold-admin -am package` 打包成功，并在 18080 端口完成真实 HTTP 登录、权限拒绝、角色 CRUD 和参数配置 CRUD 冒烟。
- 为 `scaffold-vue` 新增真实后端 API 客户端、认证 API、会话存储工具和系统管理 API 封装，新增 `npm run test:api` Node 原生测试，覆盖统一响应、Bearer token、业务错误、认证接口和系统管理端点映射。
- 将 `scaffold-vue` 登录页从本地 Mock 登录改为真实后端登录、当前用户拉取和退出登录，Vite dev server 新增 `/api` 到 `localhost:18080` 的代理。
- 将 `scaffold-vue` 用户、角色、菜单、部门、岗位、字典、参数配置页面接入真实后端读写接口，并用 Playwright 真实验证用户、角色、菜单、部门、岗位、字典、参数配置 CRUD。
- 将 `scaffold-vue` 操作日志和登录日志页面接入真实后端只读分页接口，并完成浏览器访问验证。
- 新增 `docs/frontend-backend-integration.md`，记录已接入页面、接口字段中文说明和仍使用 Mock 的后续模块。
- 新增 Dashboard 后端真实接口 `GET /api/dashboard/summary`，返回统计卡、24 小时趋势、待办和最近动态。
- 将工作流代码落入 `scaffold-workflow` 模块，新增流程模型、流程定义、待办、已办、审批和审批记录接口，并补充集成测试。
- 将代码生成器代码落入 `scaffold-generator` 模块，新增生成表、字段、预览、配置保存和生成确认接口，并补充集成测试。
- 扩展 `V2__seed_system_data.sql`，补充 `wf_model_meta`、`wf_business`、`gen_table`、`gen_table_column` 示例数据，保证新库初始化后工作流和代码生成页面有真实数据。
- 新增 `scaffold-vue/src/api/portal.ts` 和 API 映射测试，将工作台、工作流、代码生成器页面从前端 Mock 切换为真实接口。
- 新增 `docs/backend-architecture.md`，说明后端 Maven 模块、包类类型、业务边界和主要字段中文含义。
- 完成验证：`npm run test:api` 通过 8 个前端 API 测试，`npm run build` 通过，`mvn -pl scaffold-admin -am test` 通过 21 个后端测试，并完成真实后端与 Vite `/api` 代理接口联调。
