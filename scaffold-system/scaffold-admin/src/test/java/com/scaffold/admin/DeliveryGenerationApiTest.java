package com.scaffold.admin;

import com.scaffold.lowcode.generate.GeneratedFile;
import com.scaffold.lowcode.generate.GeneratedFileWriter;
import com.scaffold.lowcode.generate.ModelCodeGenerator;
import com.scaffold.lowcode.workspace.ScaffoldTrimPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 一个真实模型生成后落进交付项目工作区（PR 5G 的端到端第一段）。
 *
 * <p>各段链路早有各自的用例：{@code ProjectWorkspaceApiTest} 验交付快照按裁剪清单落盘，
 * {@code GeneratedCodeCompilesTest} 验产物编得过，{@code GeneratedFileWriterTest} 验分档覆盖与跳过。
 * 这里把三段接起来，问的是单段测试看不见的那几个问题 —— 产物写进<b>真交付项目</b>之后：
 * 那个 Maven 模块还在不在构建路径上、页面 import 的接口文件是不是真实存在、菜单的 component
 * 能不能被前端路由解析到、物料包的引用找得到实物吗、版本号对不对得上。
 * 这些错都长在两条链路的接缝上，任何一侧的单测都无感知，而表现是「CI 全绿、项目起不来」。
 *
 * <p>模型与字段走公共接口读写（列表接口的出参就是生成器的入参形状），所以这条用例同时钉住
 * 「设计器里配的东西能被生成器原样吃到」。远端由 {@link StubGitGateway} 假扮，真机推送见 changelog 的 5G 演练记录。
 */
@SpringBootTest(
        classes = {ScaffoldAdminApplication.class, StubGitGateway.Config.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestPropertySource(properties = {
        "scaffold.lowcode.workspace-root=${java.io.tmpdir}/scaffold-it-delivery-gen",
        "scaffold.lowcode.scaffold-source-root="
})
class DeliveryGenerationApiTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
    };

    private static final String REPO = "git@github.com:acme/delivery-gen.git";
    private static final String MODEL_CODE = "purchase_order";
    private static final String MODULE = "purchase_order";
    private static final String CLASS_NAME = "PurchaseOrder";
    private static final String PERMISSION_PREFIX = "purchase_order:purchase_order";
    private static final String BUSINESS_ROOT = "scaffold-system/scaffold-business/src/main/java/com/scaffold/business/";
    private static final String BUSINESS_DIR = BUSINESS_ROOT + MODULE + "/";
    private static final String UI_PACKAGE = "@yuanda-lowcode-ai/scaffold-ui";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StubGitGateway gateway;

    @Autowired
    private ModelCodeGenerator generator;

    @TempDir
    Path tempDir;

    private final List<Long> projectIds = new ArrayList<>();
    private final List<Path> workspaces = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        projectIds.forEach(id -> {
            jdbcTemplate.update("DELETE FROM lc_field WHERE model_id IN (SELECT id FROM lc_model WHERE project_id = ?)", id);
            jdbcTemplate.update("DELETE FROM lc_model WHERE project_id = ?", id);
            jdbcTemplate.update("DELETE FROM lc_project WHERE id = ?", id);
        });
        workspaces.forEach(DeliveryGenerationApiTest::deleteRecursively);
    }

    @Test
    void 产物落进交付工作区后仍是一个建得起来的项目() {
        Delivery delivery = deliver();

        assertThat(delivery.writes()).extracting(GeneratedFileWriter.Written::action)
                .containsOnly(GeneratedFileWriter.Action.CREATED);
        for (GeneratedFile file : delivery.files()) {
            assertThat(Files.isRegularFile(delivery.workspace().resolve(file.path())))
                    .as("交付工作区里的 " + file.path())
                    .isTrue();
        }

        // 生成的 java 落在 scaffold-business：这个空壳模块与父 pom 的聚合一旦被裁剪清单误删，
        // 产物就成了一堆不参与构建的源文件，编译要到发布之后才在机器上炸
        assertThat(Files.isRegularFile(delivery.workspace().resolve("scaffold-system/scaffold-business/pom.xml"))).isTrue();
        assertThat(read(delivery, "scaffold-system/pom.xml")).contains("<module>scaffold-business</module>");

        // 包名与目录对上才可能编译；再让 javac 按交付路径真编一遍
        assertThat(read(delivery, BUSINESS_DIR + "domain/" + CLASS_NAME + ".java"))
                .contains("package com.scaffold.business." + MODULE + ".domain;");
        compileBackend(delivery);
    }

    @Test
    void 前端产物引用的实物都在交付项目里() {
        Delivery delivery = deliver();
        String page = read(delivery, "scaffold-vue/src/views/" + MODULE + "/" + CLASS_NAME + ".vue");

        // @/api/... 必须落在真实文件上：路径差一个分隔符就是 vue-tsc 报「找不到模块」，页面整块起不来
        Matcher apiImport = Pattern.compile("from '@/api/([^']+)'").matcher(page);
        assertThat(apiImport.find()).as("页面里应有 @/api 的接口引用").isTrue();
        Path apiFile = delivery.workspace().resolve("scaffold-vue/src/api/" + apiImport.group(1) + ".ts");
        assertThat(apiFile).exists();
        // 反方向也对得上：页面 import 的类型与常量确实是接口文件导出的
        assertThat(read(delivery, "scaffold-vue/src/api/" + MODULE + "/" + MODULE + ".ts"))
                .contains("export interface " + CLASS_NAME + "Row")
                .contains("export const purchaseOrderApi");

        // 交付项目按 npm 版本号拿物料：占位符没换掉、或版本与包源码对不上（改了包忘了发版），
        // 都是真机 npm install / 运行时才炸，所以这两条在这里钉住。版本只认 ScaffoldTrimPolicy 那一份
        String expectedVersion = ScaffoldTrimPolicy.DEFAULT_SCAFFOLD_UI_VERSION;
        assertThat(read(delivery, "scaffold-vue/package.json"))
                .contains("\"" + UI_PACKAGE + "\": \"" + expectedVersion + "\"")
                .doesNotContain("{SCAFFOLD_UI_VERSION}");
        assertThat(packageJsonOfUi()).as("物料包版本与交付里钉的那个号不一致，说明改了包没发版")
                .contains("\"version\": \"" + expectedVersion.replace("^", "") + "\"");

        // 页面与接口文件从物料包引的每一个名字，都要在包的导出清单里真有其事
        for (UiImport uiImport : uiImportsOf(page + read(delivery, "scaffold-vue/src/api/" + MODULE + "/" + MODULE + ".ts"))) {
            assertThat(exportsOf(uiImport.subpath()))
                    .as(UI_PACKAGE + "/" + uiImport.subpath() + " 应导出 " + uiImport.name())
                    .contains(uiImport.name());
        }
    }

    @Test
    void 菜单的component指得到页面文件而SQL不会被自动执行() {
        Delivery delivery = deliver();
        String menuSql = read(delivery, "scaffold-system/sql/pages/" + MODULE + "_menu.sql");

        // 前端按 ../views/{component}.vue 解析（composables/auth.ts），对不上就是点开菜单一个白屏
        assertThat(menuSql).contains("'" + MODULE + "/" + CLASS_NAME + "'");
        assertThat(Files.isRegularFile(delivery.workspace().resolve("scaffold-vue/src/views/" + MODULE + "/" + CLASS_NAME + ".vue"))).isTrue();

        // 权限点与 Controller 上的注解必须逐字相同，否则按钮看得见、接口 403
        assertThat(read(delivery, BUSINESS_DIR + "controller/" + CLASS_NAME + "Controller.java"))
                .contains("@RequiresPermission(\"" + PERMISSION_PREFIX + ":list\")");
        assertThat(menuSql).contains("'" + PERMISSION_PREFIX + ":list'")
                .contains("'" + PERMISSION_PREFIX + ":add'")
                .contains("'" + PERMISSION_PREFIX + ":edit'")
                .contains("'" + PERMISSION_PREFIX + ":remove'");

        // 建表与菜单脚本落在子目录：容器初始化只认 sql/ 顶层的 V*.sql，子目录不递归 ——
        // 生成的脚本要人看过再手工执行，平台不替项目动库
        assertThat(delivery.files()).filteredOn(file -> file.path().endsWith(".sql"))
                .allSatisfy(file -> {
                    assertThat(file.path()).startsWith("scaffold-system/sql/pages/");
                    assertThat(Path.of(file.path()).getFileName().toString()).doesNotMatch("V\\d+__.*");
                });
    }

    @Test
    void 再次发布时B档覆盖C档保住人工写的内容() {
        Delivery delivery = deliver();
        Path extension = delivery.workspace().resolve(BUSINESS_DIR + "service/" + CLASS_NAME + "Extension.java");
        Path row = delivery.workspace().resolve(BUSINESS_DIR + "domain/" + CLASS_NAME + "Row.java");
        try {
            // C 档里加一个人工方法，模拟项目自己在扩展点上写的业务
            Files.writeString(extension, Files.readString(extension)
                    .replaceFirst("\\}\\s*$", "    /** 人工加的钩子实现 */\n    public boolean isLarge() { return true; }\n}\n"),
                    StandardCharsets.UTF_8);
            Files.writeString(row, "// 手改的 B 档\n", StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        List<GeneratedFileWriter.Written> again = GeneratedFileWriter.write(delivery.workspace(), delivery.files());

        assertThat(again).filteredOn(w -> w.path().endsWith(CLASS_NAME + "Extension.java"))
                .singleElement().satisfies(w -> assertThat(w.action()).isEqualTo(GeneratedFileWriter.Action.SKIPPED));
        assertThat(text(extension)).contains("isLarge");

        // B 档那句「人工不改」是硬约束：改了就会被冲掉，改动只在合并请求的 diff 里留痕
        assertThat(again).filteredOn(w -> w.path().endsWith(CLASS_NAME + "Service.java"))
                .singleElement().satisfies(w -> assertThat(w.action()).isEqualTo(GeneratedFileWriter.Action.OVERWRITTEN));
        assertThat(text(row)).as("B 档整文件覆盖：手改内容不保留").doesNotContain("手改的 B 档");
    }

    @Test
    void 自增主键不进新增表单() {
        Delivery delivery = deliver();
        String writeRequest = read(delivery, BUSINESS_DIR + "domain/" + CLASS_NAME + "WriteRequest.java");
        String page = read(delivery, "scaffold-vue/src/views/" + MODULE + "/" + CLASS_NAME + ".vue");

        // 主键在字段接口里默认三项全开（可录入 / 可编辑 / 必填），平台要把它压掉：
        // 真机上这张表单让人填主键，而且表单类型里 id 被声明两遍（一条 number | undefined、一条 number | null），
        // 交付项目 npm run build 在 vue-tsc 阶段直接 TS2717 / TS2322，前端镜像根本构建不出来
        assertThat(writeRequest).doesNotContain("private Long id;");
        assertThat(page).doesNotContain("id?: number").doesNotContain("form.id")
                .doesNotContain("  id: number | null");
        assertThat(page).contains("const editing = ref<number | null>(null)")
                .contains("editing.value = row.id");
        // 列表行还是带主键的：行操作、编辑、删除都要靠它定位
        assertThat(read(delivery, BUSINESS_DIR + "domain/" + CLASS_NAME + "Row.java")).contains("private Long id;");
    }

    /* ── 链路装配：建项目 → 初始化交付快照 → 建模 → 生成 → 落盘 ── */

    private Delivery deliver() {
        String token = TestAuthClient.login(restTemplate, "admin", "admin123", "platform");
        String projectCode = "dg" + System.nanoTime() % 100_000_000;
        Map<String, Object> project = new LinkedHashMap<>();
        project.put("projectCode", projectCode);
        project.put("projectName", "交付生成验收");
        project.put("repoUrl", REPO);
        project.put("mainBranch", "main");
        project.put("genBranch", "platform-generated");
        long projectId = number(dataOf(post("/api/lowcode/projects", project, token).getBody()).get("id"));
        projectIds.add(projectId);

        // 空远端 → 初始化把固定代码铺进工作区，走的就是 PR 4 那份裁剪清单
        gateway.remoteRefs = List.of();
        ResponseEntity<Map<String, Object>> initialize = post("/api/lowcode/projects/" + projectId + "/initialize", Map.of(), token);
        assertThat(initialize.getStatusCode()).as(messageOf(initialize)).isEqualTo(HttpStatus.OK);
        Path workspace = Path.of(System.getProperty("java.io.tmpdir"), "scaffold-it-delivery-gen", "1", projectCode);
        workspaces.add(workspace);
        assertThat(Files.isRegularFile(workspace.resolve("README.md"))).as("交付快照应已落盘").isTrue();

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("projectId", projectId);
        model.put("modelCode", MODEL_CODE);
        model.put("modelName", "采购单");
        model.put("modelDesc", "采购单主表");
        long modelId = number(dataOf(post("/api/lowcode/models", model, token).getBody()).get("id"));
        for (Map<String, Object> field : fields()) {
            dataOf(post("/api/lowcode/models/" + modelId + "/fields", field, token).getBody());
        }

        // 生成器的入参就是这两个接口的出参：设计器里配的东西要能被生成器原样吃到
        Map<String, Object> modelRow = rowsOf(dataOf(get("/api/lowcode/models?pageNum=1&pageSize=10&projectId=" + projectId, token)))
                .stream().filter(row -> MODEL_CODE.equals(String.valueOf(row.get("modelCode")))).findFirst().orElseThrow();
        List<Map<String, Object>> fieldRows = listDataOf(get("/api/lowcode/models/" + modelId + "/fields", token));

        List<GeneratedFile> files = generator.renderFiles(modelRow, fieldRows);
        return new Delivery(workspace, files, GeneratedFileWriter.write(workspace, files));
    }

    /** 一个够用的模型：主键、必填唯一文本、金额、字典状态、可空日期、长文本备注 */
    private List<Map<String, Object>> fields() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(field("id", "主键", "INTEGER", Map.of("isPrimary", 1, "isAutoIncrement", 1, "isList", 1)));
        list.add(field("orderNo", "订单编号", "STRING", Map.of("columnLength", 64, "isInsert", 1, "isEdit", 1,
                "isRequired", 1, "isUnique", 1, "isList", 1, "isQuery", 1, "queryType", "LIKE")));
        list.add(field("amount", "采购金额", "DECIMAL", Map.of("columnPrecision", 18, "columnScale", 2,
                "isInsert", 1, "isEdit", 1, "isList", 1)));
        list.add(field("status", "状态", "INTEGER", Map.of("dictType", "sys_normal_disable", "isInsert", 1,
                "isEdit", 1, "isRequired", 1, "isList", 1, "isQuery", 1, "queryType", "EQ")));
        list.add(field("deliveryDate", "交货日期", "DATE", Map.of("isInsert", 1, "isEdit", 1, "isList", 1)));
        list.add(field("remark", "备注", "TEXT", Map.of("isInsert", 1, "isEdit", 1, "formType", "TEXTAREA")));
        return list;
    }

    private Map<String, Object> field(String code, String name, String type, Map<String, Object> extras) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fieldCode", code);
        body.put("fieldName", name);
        body.put("fieldType", type);
        body.putAll(extras);
        return body;
    }

    private record Delivery(Path workspace, List<GeneratedFile> files, List<GeneratedFileWriter.Written> writes) {
    }

    /* ── 交付物核对辅助 ── */

    /** 七个 java 产物按交付路径真编一遍：模块没被聚合、包名对不上目录这类错，只有编译器会认 */
    private void compileBackend(Delivery delivery) {
        List<GeneratedFile> sources = delivery.files().stream().filter(file -> file.path().endsWith(".java")).toList();
        assertThat(sources).hasSize(7);
        List<String> paths = sources.stream().map(file -> delivery.workspace().resolve(file.path()).toString()).toList();

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("这条用例需要 JDK 自带的 javac").isNotNull();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        List<String> command = new ArrayList<>(List.of("-proc:none", "-encoding", "UTF-8",
                "-d", classesDir().toString(), "-classpath", System.getProperty("java.class.path")));
        command.addAll(paths);
        int code = compiler.run(null, null, err, command.toArray(String[]::new));

        assertThat(err.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(code).as("javac 退出码").isZero();
    }

    private Path classesDir() {
        try {
            return Files.createDirectories(tempDir.resolve("classes"));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /** 前端产物里每一处从物料包引的名字：{@code (子路径, 名字)} 一组一条 */
    private record UiImport(String subpath, String name) {
    }

    private List<UiImport> uiImportsOf(String code) {
        List<UiImport> imports = new ArrayList<>();
        // 大括号内不许换行也不许再有 brace：否则懒惰匹配会跨过下一行，把别的 import 名字算进这个包
        Matcher statement = Pattern.compile("import (?:type )?\\{([^}\\n]+)\\} from '" + Pattern.quote(UI_PACKAGE)
                + "(?:/([^'\\n]+))?'").matcher(code);
        while (statement.find()) {
            String subpath = statement.group(2) == null ? "." : statement.group(2);
            for (String part : statement.group(1).split(",")) {
                String name = part.trim().replaceFirst("^type ", "");
                if (!name.isEmpty()) {
                    imports.add(new UiImport(subpath, name));
                }
            }
        }
        assertThat(imports).as("前端产物应引到物料包").isNotEmpty();
        return imports;
    }

    /**
     * 物料包某个子路径的导出名清单。
     *
     * <p>读的是本仓库的包源码，交付项目装的是 registry 上按版本号发出去的那份 —— 两边对不上时
     * （加了组件忘了发版）这条用例是绿的、真机 {@code npm install} 才红，所以上面还要钉一次版本。
     */
    private List<String> exportsOf(String subpath) {
        Path file = uiSourceFile(subpath);
        List<String> names = new ArrayList<>();
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError(UI_PACKAGE + "/" + subpath + " 在物料包里找不到对应源文件", e);
        }
        Matcher braces = Pattern.compile("export (?:type )?\\{([^}]*)}").matcher(text);
        while (braces.find()) {
            for (String part : braces.group(1).split(",")) {
                String item = part.trim();
                if (!item.isEmpty()) {
                    // {@code export { default as Field }} 导出的名字是 as 后面那个
                    int as = item.indexOf(" as ");
                    names.add(as >= 0 ? item.substring(as + 4).trim() : item);
                }
            }
        }
        Matcher declared = Pattern.compile("export (?:declare )?(?:const|function|class|interface|type) (\\w+)")
                .matcher(text);
        while (declared.find()) {
            names.add(declared.group(1));
        }
        return names;
    }

    /** 按包 exports 的形状找源文件：{@code ./components/ui} 是目录的 index，{@code ./utils/validate} 是同名文件 */
    private Path uiSourceFile(String subpath) {
        Path root = Path.of(repoRoot(), "packages/scaffold-ui");
        Path asFile = root.resolve("src").resolve(subpath + ".ts");
        if (Files.isRegularFile(asFile)) {
            return asFile;
        }
        Path asIndex = root.resolve("src").resolve(subpath).resolve("index.ts");
        if (Files.isRegularFile(asIndex)) {
            return asIndex;
        }
        throw new AssertionError(UI_PACKAGE + "/" + subpath + " 这个子路径在物料包里不存在");
    }

    private String packageJsonOfUi() {
        try {
            return Files.readString(Path.of(repoRoot(), "packages/scaffold-ui/package.json"), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static String repoRoot() {
        // surefire 的工作目录是被测模块（scaffold-system/scaffold-admin），往上两级到仓库根
        return Path.of("").toAbsolutePath().getParent().getParent().toString();
    }

    private static String text(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError("读不到 " + file, e);
        }
    }

    private String read(Delivery delivery, String relativePath) {
        try {
            return Files.readString(delivery.workspace().resolve(relativePath), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError("交付工作区里读不到 " + relativePath, e);
        }
    }

    private static void deleteRecursively(Path dir) {
        try (var walk = Files.walk(dir)) {
            for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (Exception ignored) {
            // 工作区残留不影响断言结论，下次跑前会重建
        }
    }

    /* ── HTTP 辅助 ── */

    private static String messageOf(ResponseEntity<Map<String, Object>> response) {
        assertThat(response.getBody()).isNotNull();
        return String.valueOf(response.getBody().get("message"));
    }

    private ResponseEntity<Map<String, Object>> post(String path, Map<String, Object> body, String token) {
        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), MAP_TYPE);
    }

    private Map<String, Object> get(String path, String token) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(path, HttpMethod.GET,
                new HttpEntity<>(bearer(token)), MAP_TYPE);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private static long number(Object value) {
        return value instanceof Number num ? num.longValue() : Long.parseLong(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataOf(Map<String, Object> body) {
        assertThat(body).containsEntry("code", 200);
        return (Map<String, Object>) body.get("data");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rowsOf(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("rows");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listDataOf(Map<String, Object> body) {
        assertThat(body).containsEntry("code", 200);
        return (List<Map<String, Object>>) body.get("data");
    }
}
