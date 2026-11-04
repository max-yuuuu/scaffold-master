package com.scaffold.admin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 老代码生成页（导入已有表 → 预览 → 下载 zip）的端到端验证（PR 5F）。
 *
 * <p>PR 5F 的验收点：这条链路改吃 PR 5 那套 FreeMarker 模板之后，产物必须与低代码链路**同一口径**
 * （落点是交付项目的真实路径、类型映射来自 {@code FieldType} 而不是自己另算一套），
 * 同时 zip 下载与预览两个入口的行为不回退。
 *
 * <p>所以这里不打形状断言，走真实链路：建一张真表 → 走接口导入 → 取预览 / 下载 zip →
 * 把 zip 里的 java 文件交给 javac 真编一遍 → 把生成的菜单 SQL 在真库里执行（事务里回滚）。
 */
@SpringBootTest(
        classes = ScaffoldAdminApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class GeneratorImportApiTest {

    private static final String DEMO_TABLE = "demo_product";
    private static final String SYS_FILE_TABLE = "sys_file";

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
    };

    /** 交付项目里一个模型的完整产物清单（与 {@code GeneratedPaths} 一致） */
    private static final List<String> DELIVERY_PATHS = List.of(
            "scaffold-system/scaffold-business/src/main/java/com/scaffold/business/demo/domain/DemoProduct.java",
            "scaffold-system/scaffold-business/src/main/java/com/scaffold/business/demo/domain/DemoProductWriteRequest.java",
            "scaffold-system/scaffold-business/src/main/java/com/scaffold/business/demo/domain/DemoProductRow.java",
            "scaffold-system/scaffold-business/src/main/java/com/scaffold/business/demo/mapper/DemoProductMapper.java",
            "scaffold-system/scaffold-business/src/main/java/com/scaffold/business/demo/service/DemoProductService.java",
            "scaffold-system/scaffold-business/src/main/java/com/scaffold/business/demo/service/DemoProductExtension.java",
            "scaffold-system/scaffold-business/src/main/java/com/scaffold/business/demo/controller/DemoProductController.java",
            "scaffold-vue/src/api/demo/product.ts",
            "scaffold-vue/src/views/demo/DemoProduct.vue",
            "scaffold-system/sql/pages/demo_product_table.sql",
            "scaffold-system/sql/pages/demo_product_menu.sql");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @org.junit.jupiter.api.io.TempDir
    static Path tempRoot;

    @BeforeEach
    void prepareDemoTable() {
        // 演示表由测试自己建、自己删：老链路是「导入库里已有的表」，没有真表就验不了
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + DEMO_TABLE);
        jdbcTemplate.execute("""
                CREATE TABLE demo_product (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '商品 ID',
                    product_name VARCHAR(64) NOT NULL COMMENT '商品名称',
                    price DECIMAL(18,2) DEFAULT NULL COMMENT '标价',
                    shelf_date DATE DEFAULT NULL COMMENT '上架日期',
                    status INT NOT NULL DEFAULT 1 COMMENT '状态',
                    note TEXT DEFAULT NULL COMMENT '备注',
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演示商品表'
                """);
        deleteImported(SYS_FILE_TABLE);
    }

    @AfterEach
    void dropDemoTable() {
        deleteImported(DEMO_TABLE);
        deleteImported(SYS_FILE_TABLE);
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + DEMO_TABLE);
    }

    @Test
    void generatorCanListAndImportDatabaseTables() {
        HttpHeaders headers = bearerHeaders(login("admin", "admin123"));

        ResponseEntity<Map<String, Object>> candidates = restTemplate.exchange(
                "/api/tool/generator/importable-tables?keyword=" + DEMO_TABLE,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                MAP_TYPE
        );
        assertThat(candidates.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> candidate = listDataOf(candidates.getBody()).get(0);
        assertThat(candidate).containsEntry("tableName", DEMO_TABLE);
        assertThat(candidate).containsEntry("imported", false);

        Long tableId = importTable(headers, DEMO_TABLE);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gen_table_column WHERE table_id = ? AND column_name = 'product_name'",
                Long.class, tableId
        )).isEqualTo(1);
    }

    /** PR 5F 第一条：产物落点不再是想象出来的 backend/ + frontend/ 两层，而是交付项目的真实路径 */
    @Test
    void 预览与下载的是交付形状的产物清单() throws IOException {
        HttpHeaders headers = bearerHeaders(login("admin", "admin123"));
        Long tableId = importTable(headers, DEMO_TABLE);

        ResponseEntity<Map<String, Object>> preview = restTemplate.exchange(
                "/api/tool/generator/tables/" + tableId + "/preview",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                MAP_TYPE
        );
        Map<String, Object> data = dataOf(preview.getBody());
        assertThat(data.get("activeFile")).isEqualTo("DemoProductController.java");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> previewFiles = (List<Map<String, Object>>) data.get("files");
        assertThat(previewFiles).extracting(file -> file.get("path"))
                .containsExactlyElementsOf(DELIVERY_PATHS);
        // 预览用的 name 还是文件名，页面靠它定位当前文件
        assertThat(previewFiles).allSatisfy(file -> assertThat(String.valueOf(file.get("name")))
                .isEqualTo(String.valueOf(file.get("path")).substring(String.valueOf(file.get("path")).lastIndexOf('/') + 1)));

        Map<String, String> files = unzip(download(headers, tableId));
        assertThat(files.keySet()).containsExactlyElementsOf(DELIVERY_PATHS);
        assertThat(files.get("scaffold-system/scaffold-business/src/main/java/com/scaffold/business/demo/"
                + "controller/DemoProductController.java"))
                .contains("package com.scaffold.business.demo.controller;")
                .contains("@RequestMapping(\"/api/demo/product\")")
                .contains("@RequiresPermission(\"demo:product:list\")");
        // 唯一一份 C 档：老链路的 zip 也带上扩展点骨架
        assertThat(files.get("scaffold-system/scaffold-business/src/main/java/com/scaffold/business/demo/"
                + "service/DemoProductExtension.java"))
                .contains("public class DemoProductExtension")
                .contains("本类属 <b>C 档</b>");
    }

    /** PR 5F 第二条：类型口径回到 FieldType 这一份事实源，date 不再被算成 LocalDateTime */
    @Test
    void 列类型按FieldType映射且底座公共列不进表单() throws IOException {
        HttpHeaders headers = bearerHeaders(login("admin", "admin123"));
        Long tableId = importTable(headers, DEMO_TABLE);
        Map<String, String> files = unzip(download(headers, tableId));

        String entity = files.get("scaffold-system/scaffold-business/src/main/java/com/scaffold/business/"
                + "demo/domain/DemoProduct.java");
        assertThat(entity).contains("@JsonFormat(pattern = \"yyyy-MM-dd\")\n    private LocalDate shelfDate;")
                .contains("private BigDecimal price;")
                .contains("private Integer status;")
                .contains("private String note;")
                .contains("import java.time.LocalDate;")
                .contains("import java.time.LocalDateTime;");
        // create_time 是导入进来的普通列，不能再由审计开关重复声明一遍
        assertThat(entity).doesNotContain("private String createBy;");

        String tableSql = files.get("scaffold-system/sql/pages/demo_product_table.sql");
        assertThat(tableSql).contains("product_name VARCHAR(64) NOT NULL")
                .contains("price DECIMAL(18,2) DEFAULT NULL")
                .contains("shelf_date DATE DEFAULT NULL")
                .contains("note TEXT NULL")
                // 长度与精度来自库里的真实列，不是导入时猜的
                .contains("KEY idx_status (status)");
        // 破坏性语句只能出现在注释里（注释教人怎么手工 ALTER）；语句行一条都不能有
        assertThat(statements(tableSql)).allSatisfy(statement ->
                assertThat(statement).doesNotContain("DROP").doesNotContain("TRUNCATE"));
        assertThat(statements(tableSql)).singleElement().satisfies(statement ->
                assertThat(statement).startsWith("CREATE TABLE IF NOT EXISTS demo_product"));
        // 数字状态列用等值，不写成 LIKE —— LIKE 一个 int 列既用不上索引也读不出人期望的结果
        String mapper = files.get("scaffold-system/scaffold-business/src/main/java/com/scaffold/business/"
                + "demo/mapper/DemoProductMapper.java");
        assertThat(mapper).contains("(#{status} IS NULL OR t.status = #{status})")
                .contains("t.product_name LIKE CONCAT('%', #{productName}, '%')");

        // sys_file 这种带租户列与审计列的表：审计列只读不写，租户列不给表单
        Long fileId = importTable(headers, SYS_FILE_TABLE);
        Map<String, String> sysFiles = unzip(download(headers, fileId));
        String write = sysFiles.get("scaffold-system/scaffold-business/src/main/java/com/scaffold/business/"
                + "system/domain/SysFileWriteRequest.java");
        assertThat(write).contains("private String originalName;")
                .doesNotContain("tenantId").doesNotContain("createTime").doesNotContain("updateTime");
        assertThat(sysFiles.keySet()).anySatisfy(path -> assertThat(path)
                .isEqualTo("scaffold-system/scaffold-business/src/main/java/com/scaffold/business/system/"
                        + "domain/SysFile.java"));
    }

    /** PR 5F 第三条：zip 里的 java 文件要真编得过，菜单 SQL 要在真库里跑得通 */
    @Test
    void 下载产物编得过且菜单SQL能在真库执行() throws IOException {
        HttpHeaders headers = bearerHeaders(login("admin", "admin123"));
        Long tableId = importTable(headers, DEMO_TABLE);
        Map<String, String> files = unzip(download(headers, tableId));

        List<String> sources = new ArrayList<>();
        List<String> javaFiles = files.keySet().stream().filter(path -> path.endsWith(".java")).toList();
        assertThat(javaFiles).hasSize(7);
        Path sourceRoot = Files.createDirectories(tempRoot.resolve("src"));
        Path classes = Files.createDirectories(tempRoot.resolve("classes"));
        for (String path : javaFiles) {
            Path target = sourceRoot.resolve(path);
            Files.createDirectories(target.getParent());
            Files.writeString(target, files.get(path), StandardCharsets.UTF_8);
            sources.add(target.toString());
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("这条用例需要 JDK 自带的 javac").isNotNull();
        ByteArrayOutputStream compilerErr = new ByteArrayOutputStream();
        List<String> command = new ArrayList<>(List.of("-proc:none", "-encoding", "UTF-8",
                "-d", classes.toString(), "-classpath", System.getProperty("java.class.path")));
        command.addAll(sources);
        int exit = compiler.run(null, null, compilerErr, command.toArray(String[]::new));
        assertThat(compilerErr.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(exit).as("javac 退出码").isZero();

        // 菜单种子在真库里跑一遍：生成物不会被自动执行，只能这么验它跑得通
        long menuBefore = countMenu();
        long grantBefore = countGrant();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (String statement : statements(files.get("scaffold-system/sql/pages/demo_product_menu.sql"))) {
                jdbcTemplate.execute(statement);
            }
            assertThat(countMenu()).isEqualTo(menuBefore + 4);
            assertThat(countGrant()).isEqualTo(grantBefore + 4);
            assertThat(jdbcTemplate.queryForList(
                    "SELECT permission FROM sys_menu WHERE permission LIKE 'demo:product:%'", String.class))
                    .containsExactlyInAnyOrder("demo:product:list", "demo:product:add", "demo:product:edit",
                            "demo:product:remove");
            status.setRollbackOnly();
        });
        assertThat(countMenu()).isEqualTo(menuBefore);
        assertThat(countGrant()).isEqualTo(grantBefore);
    }

    private long countMenu() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_menu", Long.class);
    }

    private long countGrant() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_role_menu", Long.class);
    }

    private Long importTable(HttpHeaders headers, String tableName) {
        ResponseEntity<Map<String, Object>> imported = restTemplate.exchange(
                "/api/tool/generator/import",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("tableNames", List.of(tableName)), headers),
                MAP_TYPE
        );
        assertThat(imported.getStatusCode()).isEqualTo(HttpStatus.OK);
        return jdbcTemplate.queryForObject("SELECT id FROM gen_table WHERE table_name = ?", Long.class, tableName);
    }

    private byte[] download(HttpHeaders headers, Long tableId) {
        ResponseEntity<byte[]> response = restTemplate.exchange(
                "/api/tool/generator/tables/" + tableId + "/download",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                byte[].class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.valueOf("application/zip"));
        return response.getBody();
    }

    /** 去掉注释行后按分号切句 —— 与人在客户端里逐段执行等价（生成物一条语句会占多行） */
    private List<String> statements(String sql) {
        String body = sql.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("--"))
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        List<String> statements = new ArrayList<>();
        for (String part : body.split(";")) {
            String text = part.trim();
            if (!text.isEmpty()) {
                statements.add(text);
            }
        }
        return statements;
    }

    private void deleteImported(String tableName) {
        List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM gen_table WHERE table_name = ?", Long.class, tableName);
        for (Long tableId : ids) {
            jdbcTemplate.update("DELETE FROM gen_table_column WHERE table_id = ?", tableId);
            jdbcTemplate.update("DELETE FROM gen_table WHERE id = ?", tableId);
        }
    }

    private String login(String username, String password) {
        return TestAuthClient.login(restTemplate, username, password);
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private Map<String, String> unzip(byte[] body) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(body), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                files.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataOf(Map<String, Object> body) {
        assertThat(body).containsEntry("code", 200);
        return (Map<String, Object>) body.get("data");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listDataOf(Map<String, Object> body) {
        assertThat(body).containsEntry("code", 200);
        return (List<Map<String, Object>>) body.get("data");
    }
}
