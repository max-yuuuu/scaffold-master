package com.scaffold.lowcode.generate;

import com.scaffold.common.exception.BusinessException;

import java.util.regex.Pattern;

/**
 * 生成产物的落点表与所有权分档（方案 §4.3、§5.2）。
 *
 * <p>路径与档位只在这里定义一次：模板只管内容，生成器只管按这里的规则拼路径，
 * 而 {@link #tier} 是唯一的判档口径 —— 由路径反推档位，而不是让每个模板自己声明，
 * 否则「哪个文件会被覆盖、哪个文件平台不碰」就又变成两处需要人工对齐的口径。
 *
 * <p>判档用的是**白名单式的形状匹配**：只有列出来的这几种形状是页面发布可以写的，
 * 其余一律拒绝。看着比「按前缀判断」啰嗦，但挡住的是真事故 —— 交付项目里的
 * {@code pom.xml}、{@code src/api/index.ts}、{@code src/components/**}、{@code theme.css}、Dockerfile
 * 都是 A 档，模板里拼错一段路径就会静默覆盖掉人工维护的底座文件（方案 §九「平台覆盖人工代码」一条）。
 */
public final class GeneratedPaths {

    /** 交付项目里的业务 Maven 模块：一个项目只有这一个，新增模型只加子包，不动 pom（方案 §5.2 硬约束） */
    private static final String BUSINESS_ROOT = "scaffold-system/scaffold-business/src/main/java/com/scaffold/business/";

    /** B 档后端源码：{@code business/{module}/{domain|mapper|service|controller}/{Cls}.java} */
    private static final Pattern BACKEND_JAVA = Pattern.compile(
            "^scaffold-system/scaffold-business/src/main/java/com/scaffold/business/"
                    + "([a-z][a-z0-9_]*)/(domain|mapper|service|controller)/([A-Z][A-Za-z0-9]*)\\.java$");

    /** C 档后端扩展点：{@code business/{module}/service/{Cls}Extension.java} */
    private static final Pattern BACKEND_EXTENSION = Pattern.compile(
            "^scaffold-system/scaffold-business/src/main/java/com/scaffold/business/"
                    + "([a-z][a-z0-9_]*)/service/([A-Z][A-Za-z0-9]*)Extension\\.java$");

    /** B 档前端接口：按模型一份 {@code src/api/{module}/{resource}.ts}（方案 §10.2 第 ⑤ 条） */
    private static final Pattern FRONTEND_API = Pattern.compile(
            "^scaffold-vue/src/api/([a-z][a-z0-9_]*)/([a-z][A-Za-z0-9_]*)\\.ts$");

    /** B 档前端页面：{@code src/views/{module}/{Cls}.vue}，路径要与菜单的 component 对上 */
    private static final Pattern FRONTEND_VIEW = Pattern.compile(
            "^scaffold-vue/src/views/([a-z][a-z0-9_]*)/([A-Z][A-Za-z0-9]*)\\.vue$");

    /**
     * C 档前端人写区：{@code src/views/{module}/overrides/*.vue}。
     *
     * <p>这里只是把形状认成 C 档，让分档判定完整；覆写文件怎么被发现、怎么盖到 B 档区块上
     * 仍未定稿（方案 §10.2 开放项 3），所以生成器目前不产出这类文件。
     */
    private static final Pattern FRONTEND_OVERRIDE = Pattern.compile(
            "^scaffold-vue/src/views/([a-z][a-z0-9_]*)/overrides/([A-Za-z0-9._\\-]+)\\.vue$");

    /** B 档 SQL：只增量建新表与菜单种子，永不产出 DROP（方案 §4.3） */
    private static final Pattern PAGE_SQL = Pattern.compile(
            "^scaffold-system/sql/pages/([a-z0-9_]+)_(table|menu)\\.sql$");

    private GeneratedPaths() {
    }

    /**
     * 按路径判档。
     *
     * @return B 档 {@link GeneratedFile.Tier#GENERATED} 或 C 档 {@link GeneratedFile.Tier#EXTENSION}
     * @throws BusinessException 路径不属于页面发布可写的范围（A 档或拼错的路径）
     */
    public static GeneratedFile.Tier tier(String path) {
        if (path == null || path.isBlank()) {
            throw new BusinessException("生成产物没有路径");
        }
        // C 档先判：{Cls}Extension.java 本身也落在 service/ 目录下，形状是 B 档的子集
        if (BACKEND_EXTENSION.matcher(path).matches() || FRONTEND_OVERRIDE.matcher(path).matches()) {
            return GeneratedFile.Tier.EXTENSION;
        }
        if (BACKEND_JAVA.matcher(path).matches() || FRONTEND_API.matcher(path).matches()
                || FRONTEND_VIEW.matcher(path).matches() || PAGE_SQL.matcher(path).matches()) {
            return GeneratedFile.Tier.GENERATED;
        }
        throw new BusinessException("生成器无权写这个路径：" + path
                + "。A 档（pom、app 壳、组件、样式、Dockerfile）只在项目初始化与底座升级时写入，不随页面发布更新");
    }

    public static String entity(String module, String className) {
        return BUSINESS_ROOT + module + "/domain/" + className + ".java";
    }

    public static String writeRequest(String module, String className) {
        return BUSINESS_ROOT + module + "/domain/" + className + "WriteRequest.java";
    }

    /** 列表行：{@code {Cls}Row}，不再有 {@code {Cls}VO}（方案 §10.2 第 ① 条） */
    public static String row(String module, String className) {
        return BUSINESS_ROOT + module + "/domain/" + className + "Row.java";
    }

    public static String mapper(String module, String className) {
        return BUSINESS_ROOT + module + "/mapper/" + className + "Mapper.java";
    }

    public static String service(String module, String className) {
        return BUSINESS_ROOT + module + "/service/" + className + "Service.java";
    }

    /** C 档扩展点：生成一次，之后平台不碰（方案 §5.2） */
    public static String extension(String module, String className) {
        return BUSINESS_ROOT + module + "/service/" + className + "Extension.java";
    }

    public static String controller(String module, String className) {
        return BUSINESS_ROOT + module + "/controller/" + className + "Controller.java";
    }

    public static String apiTs(String module, String resourceSlug) {
        return "scaffold-vue/src/api/" + module + "/" + resourceSlug + ".ts";
    }

    public static String listView(String module, String className) {
        return "scaffold-vue/src/views/" + module + "/" + className + ".vue";
    }

    /**
     * 建表语句。落在 {@code sql/pages/} 子目录是有意的：容器的 {@code mysql} 初始化只认
     * {@code sql/} 顶层的 {@code V*.sql}，子目录不会被自动执行 —— 生成的 SQL 要人看过再手工跑，
     * 平台不替项目动库。
     */
    public static String tableSql(String tableName) {
        return "scaffold-system/sql/pages/" + tableName + "_table.sql";
    }

    public static String menuSql(String tableName) {
        return "scaffold-system/sql/pages/" + tableName + "_menu.sql";
    }
}
