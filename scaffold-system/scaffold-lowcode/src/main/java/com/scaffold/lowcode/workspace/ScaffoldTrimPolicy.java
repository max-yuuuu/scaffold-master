package com.scaffold.lowcode.workspace;

import java.util.List;

/**
 * 交付项目快照的裁剪清单（方案 §6.4）：生成项目拿到的不是本仓库全量，而是「脚手架减去低代码平台自身」。
 *
 * <p>两道手段，都是代码里的显式配置，改了就能测：
 * <ol>
 *   <li>{@link #INCLUDED_ROOTS 白名单} 决定哪些根路径会进交付项目，{@link #EXCLUDED_PATHS 黑名单}
 *       与 {@link #IGNORED_NAMES} 命中即剔除；</li>
 *   <li>白名单内、但内容属于平台自身的片段（父 pom 里的平台模块声明、compose 里的工作区卷、
 *       认证入口里的平台 Mapper 扫描包等），用 {@link #PLATFORM_ONLY_TOKEN} 标记块圈起来，
 *       由 {@link PlatformOnlyStripper} 在写快照时整块删除。</li>
 * </ol>
 *
 * <p>{@code lc_tenant} 与 {@code gen_table} 的口径不同于其它元数据表：前者是认证链的租户注册表
 * （{@code AuthService} 每次登录都要查它是否停用，交付项目恒为 1 号租户），保留；
 * 后者属代码生成器，随 {@code scaffold-generator} 一起剔除。
 */
public final class ScaffoldTrimPolicy {

    /** 交付项目里的项目标记文件：非空仓库上没有它就不许初始化（方案 §6.3） */
    public static final String MARKER_FILE = ".scaffold-project.json";

    /** 平台专属片段标记：出现在 start / end 之间的行不会进交付项目 */
    public static final String PLATFORM_ONLY_TOKEN = "scaffold:platform-only";

    /** 白名单：只有这些根路径进交付项目，其余目录（docs 里的人工笔记、仓库自身的说明文件）一律不带 */
    private static final List<String> INCLUDED_ROOTS = List.of(
            ".gitignore",
            ".env.example",
            "LICENSE",
            "README.md",
            "AGENTS.md",
            "DESIGN.md",
            "docker-compose.yml",
            "deploy/",
            "docs/",
            "scaffold-system/",
            "scaffold-vue/");

    /** 黑名单：命中即剔除，优先级高于白名单；目录以 / 结尾 */
    private static final List<String> EXCLUDED_PATHS = List.of(
            // 平台业务与在线代码生成入口：交付项目里既没有菜单也没有表
            "scaffold-system/scaffold-lowcode/",
            "scaffold-system/scaffold-generator/",
            // 平台元数据建表与菜单种子（lc_project / lc_model / lc_field / lc_page / lc_tenant_config 与 400、500 段菜单）
            // 新增平台侧 SQL 时要在这一段补一行；漏了不会静默出门，ScaffoldSnapshotPlanner 的
            // 表名残留扫描会当场拒绝初始化
            "scaffold-system/sql/V4__platform_tenant.sql",
            "scaffold-system/sql/V5__lowcode_project.sql",
            "scaffold-system/sql/V6__lowcode_model.sql",
            "scaffold-system/sql/V7__lowcode_workspace.sql",
            "scaffold-system/sql/V8__lowcode_page.sql",
            "scaffold-system/sql/V9__lowcode_publish.sql",
            "scaffold-system/sql/V10__lowcode_merge_request.sql",
            "scaffold-system/sql/V11__lowcode_merge_review.sql",
            "scaffold-system/sql/V12__lowcode_page_rollback.sql",
            "scaffold-system/sql/V13__lowcode_base_upgrade.sql",
            // 双租户越权验收脚本，只在平台侧有意义
            "scaffold-system/sql/verify/",
            // 平台侧验收用例：测的是 /api/lowcode 与 /api/platform，交付项目里没有这些接口，留下就是必然失败的测试。
            // 新增引用了平台包或平台接口的用例文件时要在下面补一行，忘补不会静默出门 ——
            // ScaffoldSnapshotPlanner 的残留引用扫描会当场拒绝，但报的是初始化快照时失败，不容易联想到新文件
            "scaffold-system/scaffold-admin/src/test/java/com/scaffold/admin/TenantIsolationApiTest.java",
            "scaffold-system/scaffold-admin/src/test/java/com/scaffold/admin/TenantRegisterApiTest.java",
            "scaffold-system/scaffold-admin/src/test/java/com/scaffold/admin/TenantRoleCaliberApiTest.java",
            "scaffold-system/scaffold-admin/src/test/java/com/scaffold/admin/ProjectApiTest.java",
            "scaffold-system/scaffold-admin/src/test/java/com/scaffold/admin/ModelApiTest.java",
            "scaffold-system/scaffold-admin/src/test/java/com/scaffold/admin/PageApiTest.java",
            "scaffold-system/scaffold-admin/src/test/java/com/scaffold/admin/ProjectWorkspaceApiTest.java",
            "scaffold-system/scaffold-admin/src/test/java/com/scaffold/admin/DeliveryGenerationApiTest.java",
            "scaffold-system/scaffold-admin/src/test/java/com/scaffold/admin/PagePublishApiTest.java",
            "scaffold-system/scaffold-admin/src/test/java/com/scaffold/admin/MergeRequestApiTest.java",
            "scaffold-system/scaffold-admin/src/test/java/com/scaffold/admin/StubGitGateway.java",
            "scaffold-system/scaffold-admin/src/test/java/com/scaffold/admin/LocalBareGitGateway.java",
            // 平台前端页面与接口客户端
            "scaffold-vue/src/views/lowcode/",
            // 在线代码生成页面：交付项目里没有 scaffold-generator，留着就是一个点开报错的菜单
            "scaffold-vue/src/views/tool/",
            "scaffold-vue/src/api/platform.ts",
            // 平台对应用壳的申报（视图表与壳外路由）。它 import 的是 views/lowcode 里的页面，
            // 交付项目既没有那些页面也没有 /api/lowcode，留下就是一份指向不存在模块的申报
            "scaffold-vue/src/platform.ts",
            // 上面那份客户端的用例：断言写的是 /api/lowcode 与 /api/platform 的真实 URL，
            // 客户端被裁掉了而测试留着，交付项目就是一跑就红。PR 12 只补了源文件这一半，漏了这一半
            "scaffold-vue/src/api/__tests__/platform.test.ts",
            // 只有被剔除的代码生成页在用这个流水线组件（抽包后它留在宿主，不进包）；其余业务组件与系统页面一起保留
            "scaffold-vue/src/components/platform/GeneratorPipeline.vue",
            // 物料包源码不进交付项目：组件与主题走 @yuanda-lowcode-ai/scaffold-ui 依赖（§〇 抽包口径），交付只改写依赖版本
            "packages/",
            // 根 package.json / package-lock.json 与 .dockerignore 属平台 workspace 构建，交付项目按 npm 依赖自洽
            "package.json",
            "package-lock.json",
            ".dockerignore",
            // 方案文档与部署文档里的平台章节：交付项目不需要读它
            "docs/lowcode-platform-proposal.md");

    /** 任何层级命中这个名字就跳过：构建产物、依赖目录、平台工作区与本机垃圾文件 */
    private static final List<String> IGNORED_NAMES = List.of(
            ".git",
            ".idea",
            ".npm-cache",
            ".vite",
            "node_modules",
            "target",
            "dist",
            // 平台本地工作区根（默认 ./lowcode-workspace，容器里是卷）：里面是各项目 clone 出来的交付物，
            // 不排除的话交付快照会把上一版交付整个套进新一版里
            "lowcode-workspace",
            ".DS_Store",
            // 本机口令与解释器字节码：裁剪走文件系统遍历，.gitignore 拦不住它。
            // scaffold-admin/.env.local 里就是开发用的 JWT_SECRET，把它列进 A 档清单等于打算把口令下发给
            // 每一个租户（10G 真机跑出来过一次，那趟靠交付仓库自己的 .gitignore 才没被 git add 收进去）
            ".env.local",
            "__pycache__");

    /** 快照里不允许出现的平台代码引用（Java/TS/JSON），残留即说明裁剪清单漏项 */
    private static final List<String> FORBIDDEN_CODE_REFERENCES = List.of(
            "com.scaffold.lowcode",
            "com.scaffold.generator",
            "@/views/lowcode",
            "@/views/tool",
            // 平台自己的接口与验收用例：留在交付项目里就是点开报错的菜单和必然失败的测试
            "/api/lowcode",
            "/api/platform");

    /** 快照里不允许出现的平台元数据表名（只在 .sql 里扫），交付项目的库结构不该带它们 */
    private static final List<String> FORBIDDEN_SCHEMA_REFERENCES = List.of(
            "lc_project",
            "lc_model",
            "lc_field",
            "lc_page",
            "lc_page_version",
            "lc_tenant_config",
            "lc_publish_log",
            "lc_merge_request",
            "gen_table");

    /** 裁剪清单必须能找到的底座根路径，缺一个就说明本仓库结构与清单不一致，先改清单再发布 */
    private static final List<String> REQUIRED_ROOTS = List.of(
            "scaffold-system/pom.xml",
            "scaffold-system/scaffold-common/pom.xml",
            "scaffold-system/scaffold-framework/pom.xml",
            "scaffold-system/scaffold-system/pom.xml",
            "scaffold-system/scaffold-business/pom.xml",
            "scaffold-system/scaffold-admin/pom.xml",
            "scaffold-system/sql/V1__init_schema.sql",
            "packages/scaffold-ui/package.json",
            "scaffold-vue/package.json",
            "scaffold-vue/src/main.ts");

    /** 交付改写里依赖版本的占位符，由 {@code scaffold.lowcode.scaffold-ui-version} 填入 */
    public static final String SCAFFOLD_UI_VERSION_PLACEHOLDER = "{SCAFFOLD_UI_VERSION}";

    /** 交付改写里项目名的占位符，初始化时按 {@code lc_project.project_name} 填入 */
    public static final String PROJECT_NAME_PLACEHOLDER = "{PROJECT_NAME}";

    /**
     * 交付产物里不许出现的平台自身品牌。交付的是项目自己的后台，登录页大字、侧栏品牌、浏览器标签
     * 与生成物注释都读得到这些字 —— 底座里留着自己的名字（平台界面要用），交付这一份必须改写。
     */
    private static final List<String> FORBIDDEN_BRAND_REFERENCES = List.of("元搭低代码平台", "元搭低代码");

    /**
     * 交付改写：底座这几处写的是「平台自己怎么用 npm workspaces 构建」，交付项目要换成
     * 「按 npm 依赖怎么构建」。这里是整块精确匹配的行替换 —— 不做正则、不做模板引擎，
     * 底座这几行一旦改动，{@link ScaffoldSnapshotPlanner} 会当场失败而不是交付一个装不上依赖的项目。
     *
     * @param path     相对底座根的文件路径
     * @param from     底座里的原文（连续若干行，不含结尾换行）
     * @param to       交付项目里的替换文本，可含 {@link #SCAFFOLD_UI_VERSION_PLACEHOLDER}
     *                 与 {@link #PROJECT_NAME_PLACEHOLDER}
     */
    public record DeliverySubstitution(String path, List<String> from, List<String> to) {
    }

    private static final List<DeliverySubstitution> DELIVERY_SUBSTITUTIONS = List.of(
            // 品牌只这一份：APP.name 进登录页与浏览器标签，APP.shortName 进侧栏（composables 与路由都读它）
            new DeliverySubstitution("scaffold-vue/src/data/index.ts",
                    List.of("  name: '元搭低代码平台',",
                            "  shortName: '元搭低代码',"),
                    List.of("  name: '" + PROJECT_NAME_PLACEHOLDER + "',",
                            "  shortName: '" + PROJECT_NAME_PLACEHOLDER + "',")),
            // 交付项目里没有在线代码生成页（scaffold-generator 与 views/tool 都被裁掉），
            // 登录页继续宣传它就是向客户吹一个不存在的功能
            new DeliverySubstitution("scaffold-vue/src/views/Login.vue",
                    List.of("  { icon: 'log', title: '操作审计', desc: '登录日志与操作日志自动记录' },",
                            "  { icon: 'code', title: '代码生成', desc: '导入数据表，生成业务模块开发骨架' },"),
                    List.of("  { icon: 'log', title: '操作审计', desc: '登录日志与操作日志自动记录' },")),
            new DeliverySubstitution("scaffold-vue/package.json",
                    List.of("    \"@yuanda-lowcode-ai/scaffold-ui\": \"*\","),
                    List.of("    \"@yuanda-lowcode-ai/scaffold-ui\": \"" + SCAFFOLD_UI_VERSION_PLACEHOLDER + "\",")),
            new DeliverySubstitution("scaffold-vue/Dockerfile",
                    List.of("# 构建上下文为仓库根：npm workspaces 需要根 package.json / package-lock.json 与 packages/scaffold-ui 的物料源码",
                            "FROM node:22-alpine AS build",
                            "WORKDIR /build",
                            "# 先只拷三份清单，依赖未变时 npm ci 这一层可复用缓存",
                            "COPY package.json package-lock.json ./",
                            "COPY packages/scaffold-ui/package.json packages/scaffold-ui/",
                            "COPY scaffold-vue/package.json scaffold-vue/",
                            "RUN npm ci",
                            "COPY packages packages",
                            "COPY scaffold-vue scaffold-vue",
                            "RUN npm --workspace scaffold-vue run build"),
                    List.of("# 构建上下文为 scaffold-vue 目录：物料包 @yuanda-lowcode-ai/scaffold-ui 是普通 npm 依赖，不需要 workspace 清单",
                            "# 交付项目不带锁文件（锁跟着平台的 workspace 走），按 package.json 里的版本号 npm install",
                            "FROM node:22-alpine AS build",
                            "WORKDIR /build",
                            "COPY package.json ./",
                            "RUN npm install",
                            "COPY . .",
                            "RUN npm run build")),
            new DeliverySubstitution("scaffold-vue/Dockerfile",
                    List.of("COPY scaffold-vue/nginx.conf /etc/nginx/conf.d/default.conf",
                            "COPY --from=build /build/scaffold-vue/dist /usr/share/nginx/html"),
                    List.of("COPY nginx.conf /etc/nginx/conf.d/default.conf",
                            "COPY --from=build /build/dist /usr/share/nginx/html")),
            new DeliverySubstitution("docker-compose.yml",
                    List.of("    build:",
                            "      # 上下文是仓库根：npm workspaces 要把 packages/scaffold-ui 一起交给镜像构建",
                            "      context: .",
                            "      dockerfile: scaffold-vue/Dockerfile"),
                    List.of("    build:",
                            "      context: ./scaffold-vue")),
            new DeliverySubstitution("README.md",
                    List.of("# 前端命令一律在仓库根执行（npm workspaces）；首次使用先 npm install"),
                    List.of("# 在 scaffold-vue 目录启动前端；首次使用先 npm install")),
            new DeliverySubstitution("README.md",
                    List.of("# 在仓库根：test:unit 会分别进 packages/scaffold-ui 与 scaffold-vue 各跑一遍",
                            "npm run test:unit",
                            "npm run build",
                            "# 已安装 Python Playwright 与 Chromium 时，可验证真实浏览器会话与权限",
                            "python3 scaffold-vue/scripts/session-regression.py"),
                    List.of("# 在 scaffold-vue 目录",
                            "npm run test:unit",
                            "npm run build",
                            "# 已安装 Python Playwright 与 Chromium 时，可验证真实浏览器会话与权限",
                            "python3 scripts/session-regression.py")),
            new DeliverySubstitution("docs/docker-deployment.md",
                    List.of("| `scaffold-vue/Dockerfile` | 前端镜像：`node:22-alpine` 构建 → `nginx:1.27-alpine` 托管。平台自身的构建上下文是仓库根（npm workspaces 要把根清单与 `packages/scaffold-ui` 一起交给构建，compose 里写作 `context: .` + `dockerfile: scaffold-vue/Dockerfile`）；交付项目的上下文是 `scaffold-vue` 目录，组件与主题来自 npm 依赖 `@yuanda-lowcode-ai/scaffold-ui` |",
                            "| `.dockerignore`（仓库根）与 `scaffold-vue/.dockerignore` | 排除 `node_modules`、`dist`、`target`、`.git`、`lowcode-workspace` 等。Docker 只读「上下文根」的那一份，所以两份各管一边 |",
                            "| `package.json` / `package-lock.json`（仓库根） | 平台侧 npm workspaces 声明（`packages/*` + `scaffold-vue`）与锁文件；`@yuanda-lowcode-ai/scaffold-ui` 的物料源码在 `packages/scaffold-ui`，镜像内 `npm ci` 会把它软链进 `node_modules`。这两份清单属平台，不进交付项目 |"),
                    List.of("| `scaffold-vue/Dockerfile` | 前端镜像：`node:22-alpine` 里 `npm install` + `npm run build` → `nginx:1.27-alpine` 托管；构建上下文是 `scaffold-vue` 目录，组件与珍珠玻璃主题来自 npm 依赖 `@yuanda-lowcode-ai/scaffold-ui` |",
                            "| `scaffold-vue/.dockerignore` | 排除 `node_modules`、`dist`、`.DS_Store` |")),
            new DeliverySubstitution("docs/docker-deployment.md",
                    List.of("- **前端命令一律从仓库根跑**（`npm run dev|build|test:unit`）。宿主 `scaffold-vue` 与物料包 `packages/*` 是 npm workspaces，`node_modules` 里那个包是指向 `packages/...` 的软链：**改包内源码宿主即时生效，不用重装**；只有改了依赖清单（`package.json`）才需要再 `npm install` 一次重建软链与锁文件。"),
                    List.of("- **前端命令在 `scaffold-vue` 目录跑**（`npm run dev|build|test:unit`）。组件与珍珠玻璃主题是 npm 依赖 `@yuanda-lowcode-ai/scaffold-ui`，升级就是改 `package.json` 里的版本号再 `npm install`；本项目不是 workspace，仓库根没有清单。")));

    /** 交付项目里物料包依赖的默认版本写法（可被配置覆盖，便于本地用 file: 依赖演练交付链） */
    public static final String DEFAULT_SCAFFOLD_UI_VERSION = "^1.0.0";

    private ScaffoldTrimPolicy() {
    }

    public static List<String> includedRoots() {
        return INCLUDED_ROOTS;
    }

    public static List<String> excludedPaths() {
        return EXCLUDED_PATHS;
    }

    public static List<String> forbiddenCodeReferences() {
        return FORBIDDEN_CODE_REFERENCES;
    }

    public static List<String> forbiddenSchemaReferences() {
        return FORBIDDEN_SCHEMA_REFERENCES;
    }

    public static List<String> forbiddenBrandReferences() {
        return FORBIDDEN_BRAND_REFERENCES;
    }

    public static List<String> requiredRoots() {
        return REQUIRED_ROOTS;
    }

    public static List<DeliverySubstitution> deliverySubstitutions() {
        return DELIVERY_SUBSTITUTIONS;
    }

    /**
     * 判断某个相对目录还要不要继续往里走：整个子树都在白名单外或被黑名单命中就剪掉，
     * 免得把 node_modules、target 之外的平台目录也遍历一遍。
     *
     * @param relativeDir 相对底座根的目录路径，用 {@code /} 分隔，不带结尾斜杠
     */
    public static boolean descends(String relativeDir) {
        if (relativeDir.isEmpty() || containsIgnoredName(relativeDir)) {
            return false;
        }
        String asDir = relativeDir + "/";
        for (String excluded : EXCLUDED_PATHS) {
            if (asDir.startsWith(excluded) || excluded.equals(relativeDir)) {
                return false;
            }
        }
        for (String included : INCLUDED_ROOTS) {
            if (asDir.startsWith(included) || included.startsWith(asDir) || included.equals(relativeDir)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断底座里的某个相对路径是否要带进交付项目。
     *
     * @param relativePath 相对底座 checkout 根的路径，用 {@code /} 分隔
     */
    public static boolean accepts(String relativePath) {
        if (relativePath.isEmpty() || containsIgnoredName(relativePath)) {
            return false;
        }
        // V1 建表脚本与 V2 种子数据由平台自身维护，交付快照走同一份，只在文件内做标记块删除
        for (String excluded : EXCLUDED_PATHS) {
            if (excluded.endsWith("/")) {
                if (relativePath.startsWith(excluded)) {
                    return false;
                }
            } else if (relativePath.equals(excluded)) {
                return false;
            }
        }
        for (String included : INCLUDED_ROOTS) {
            if (included.endsWith("/")) {
                if (relativePath.startsWith(included)) {
                    return true;
                }
            } else if (relativePath.equals(included)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIgnoredName(String relativePath) {
        for (String segment : relativePath.split("/")) {
            if (IGNORED_NAMES.contains(segment)) {
                return true;
            }
        }
        return false;
    }
}
