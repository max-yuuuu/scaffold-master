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
            // 平台元数据建表与菜单种子（lc_project / lc_model / lc_field / lc_tenant_config 与 400、500 段菜单）
            // 新增平台侧 SQL 时要在这一段补一行；漏了不会静默出门，ScaffoldSnapshotPlanner 的
            // 表名残留扫描会当场拒绝初始化
            "scaffold-system/sql/V4__platform_tenant.sql",
            "scaffold-system/sql/V5__lowcode_project.sql",
            "scaffold-system/sql/V6__lowcode_model.sql",
            "scaffold-system/sql/V7__lowcode_workspace.sql",
            // 双租户越权验收脚本，只在平台侧有意义
            "scaffold-system/sql/verify/",
            // 平台侧验收用例：测的是 /api/lowcode 与 /api/platform，交付项目里没有这些接口，留下就是必然失败的测试
            "scaffold-system/scaffold-admin/src/test/java/com/scaffold/admin/TenantIsolationApiTest.java",
            "scaffold-system/scaffold-admin/src/test/java/com/scaffold/admin/TenantRegisterApiTest.java",
            "scaffold-system/scaffold-admin/src/test/java/com/scaffold/admin/ProjectApiTest.java",
            "scaffold-system/scaffold-admin/src/test/java/com/scaffold/admin/ModelApiTest.java",
            "scaffold-system/scaffold-admin/src/test/java/com/scaffold/admin/ProjectWorkspaceApiTest.java",
            // 平台前端页面与接口客户端
            "scaffold-vue/src/views/lowcode/",
            // 在线代码生成页面：交付项目里没有 scaffold-generator，留着就是一个点开报错的菜单
            "scaffold-vue/src/views/tool/",
            "scaffold-vue/src/api/platform.ts",
            // 只有被剔除的代码生成页在用这个流水线组件；其余业务组件与系统页面一起保留
            "scaffold-vue/src/components/business/GeneratorPipeline.vue",
            // 方案文档与部署文档里的平台章节：交付项目不需要读它
            "docs/lowcode-platform-proposal.md");

    /** 任何层级命中这个名字就跳过：构建产物、依赖目录与本机垃圾文件 */
    private static final List<String> IGNORED_NAMES = List.of(
            ".git",
            ".idea",
            ".npm-cache",
            "node_modules",
            "target",
            "dist",
            ".DS_Store");

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
            "scaffold-vue/package.json",
            "scaffold-vue/src/main.ts");

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

    public static List<String> requiredRoots() {
        return REQUIRED_ROOTS;
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
