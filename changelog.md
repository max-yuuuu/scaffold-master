# Changelog

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
