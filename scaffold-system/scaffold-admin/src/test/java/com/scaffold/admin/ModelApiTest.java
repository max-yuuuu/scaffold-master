package com.scaffold.admin;

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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型与字段配置（PR 3b）。模型是生成器的输入，所以这里的用例都在钉「输入是否自洽」：
 * 派生规则、类型映射、跨项目引用、主键与字典约束。
 */
@SpringBootTest(
        classes = ScaffoldAdminApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class ModelApiTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
    };

    private static final String REPO = "https://github.com/acme/model-lab.git";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 模型按编码派生表名类名且编码不可改() {
        Fixture fx = new Fixture();
        try {
            String admin = fx.admin();
            long projectId = fx.project(admin, "mproj1");
            Map<String, Object> model = dataOf(post("/api/lowcode/models", modelBody(projectId, "order_detail", "订单明细"), admin).getBody());
            long modelId = number(model.get("id"));
            fx.modelIds.add(modelId);

            Map<String, Object> row = findModel(admin, projectId, "order_detail");
            assertThat(row).containsEntry("tableName", "order_detail");
            assertThat(row).containsEntry("className", "OrderDetail");
            assertThat(row).containsEntry("moduleName", "order_detail");
            assertThat(row).containsEntry("enableLogicDelete", 1);
            assertThat(row).containsEntry("enableAuditField", 1);
            assertThat(row).containsEntry("fieldCount", 0);

            // 编码与归属项目不可改
            assertThat(messageOf(put("/api/lowcode/models/" + modelId, modelBody(projectId, "order_items", "订单明细"), admin)))
                    .contains("模型编码不可修改");
            assertThat(messageOf(put("/api/lowcode/models/" + modelId, modelBody(projectId + 1, "order_detail", "订单明细"), admin)))
                    .contains("归属项目不可修改");
            // 同项目内编码与表名都不能重复
            assertThat(messageOf(post("/api/lowcode/models", modelBody(projectId, "order_detail", "重复编码"), admin)))
                    .contains("已存在同名模型编码");
            assertThat(messageOf(post("/api/lowcode/models", withTableName(modelBody(projectId, "other_code", "换个编码"), "order_detail"), admin)))
                    .contains("已存在同名数据表");

            // 表名原样进生成的 SQL（不加反引号），MySQL 保留字必须在建模时就拦住
            assertThat(messageOf(post("/api/lowcode/models", modelBody(projectId, "order", "订单"), admin)))
                    .contains("MySQL 保留字");

            // 项目下有模型时不许删项目
            assertThat(messageOf(delete("/api/lowcode/projects/" + projectId, admin))).contains("项目下还有模型");
        } finally {
            fx.clean();
        }
    }

    @Test
    void 字段类型映射覆盖各类型与默认值() {
        Fixture fx = new Fixture();
        try {
            String admin = fx.admin();
            long projectId = fx.project(admin, "mproj3");
            long modelId = fx.model(admin, projectId, "product");

            fx.field(admin, modelId, field("id", "主键", "INTEGER", Map.of("isPrimary", 1, "isAutoIncrement", 1)));
            fx.field(admin, modelId, field("productName", "商品名称", "STRING", Map.of("columnLength", 128, "isQuery", 1, "queryType", "LIKE")));
            fx.field(admin, modelId, field("price", "售价", "DECIMAL", Map.of("columnPrecision", 10, "columnScale", 2)));
            fx.field(admin, modelId, field("status", "状态", "INTEGER", Map.of("dictType", "sys_normal_disable")));
            fx.field(admin, modelId, field("remark", "备注", "TEXT", Map.of("formType", "TEXTAREA")));
            fx.field(admin, modelId, field("onSaleDate", "上架日期", "DATE", Map.of()));

            Map<String, Object> name = fieldRow(admin, modelId, "productName");
            assertThat(name).containsEntry("javaType", "String");
            assertThat(name).containsEntry("columnType", "varchar(128)");
            assertThat(name).containsEntry("columnName", "product_name");
            assertThat(name).containsEntry("queryType", "LIKE");
            assertThat(name).containsEntry("isQuery", 1);

            assertThat(fieldRow(admin, modelId, "price")).containsEntry("columnType", "decimal(10,2)").containsEntry("javaType", "BigDecimal");
            assertThat(fieldRow(admin, modelId, "status")).containsEntry("columnType", "int").containsEntry("dictType", "sys_normal_disable");
            assertThat(fieldRow(admin, modelId, "remark")).containsEntry("columnType", "text").containsEntry("formType", "TEXTAREA");
            assertThat(fieldRow(admin, modelId, "onSaleDate")).containsEntry("columnType", "date").containsEntry("javaType", "LocalDate");
            assertThat(fieldRow(admin, modelId, "id")).containsEntry("columnType", "bigint");

            // 顺序按 sort_order，决定生成实体属性与列表列的顺序
            List<String> order = fields(admin, modelId).stream().map(row -> String.valueOf(row.get("fieldCode"))).toList();
            assertThat(order).startsWith("id").endsWith("onSaleDate");
        } finally {
            fx.clean();
        }
    }

    @Test
    void 非法字段定义被拒() {
        Fixture fx = new Fixture();
        try {
            String admin = fx.admin();
            long projectId = fx.project(admin, "mproj4");
            long modelId = fx.model(admin, projectId, "category");
            long otherModelId = fx.model(admin, fx.project(admin, "mproj4b"), "supplier");

            assertThat(messageOf(post("/api/lowcode/models/" + modelId + "/fields", field("id", "主键", "INTEGER", Map.of("isAutoIncrement", 1)), admin)))
                    .contains("自增字段必须是主键");
            fx.field(admin, modelId, field("id", "主键", "INTEGER", Map.of("isPrimary", 1, "isAutoIncrement", 1)));
            assertThat(messageOf(post("/api/lowcode/models/" + modelId + "/fields", field("code", "编码", "STRING", Map.of("isPrimary", 1)), admin)))
                    .contains("只能有一个主键");
            assertThat(messageOf(post("/api/lowcode/models/" + modelId + "/fields", field("status", "状态", "ENUM", Map.of()), admin)))
                    .contains("字典枚举类型必须选择字典");
            assertThat(messageOf(post("/api/lowcode/models/" + modelId + "/fields", field("level", "层级", "ENUM", Map.of("dictType", "no_such_dict")), admin)))
                    .contains("字典类型在当前租户不存在");
            assertThat(messageOf(post("/api/lowcode/models/" + modelId + "/fields", field("supplier", "供应商", "REF", Map.of()), admin)))
                    .contains("关联引用必须选择目标模型");
            assertThat(messageOf(post("/api/lowcode/models/" + modelId + "/fields", field("supplier", "供应商", "REF", Map.of("refModelId", otherModelId)), admin)))
                    .contains("同一个项目");
            assertThat(messageOf(post("/api/lowcode/models/" + modelId + "/fields", field("kind", "类别", "STRING", Map.of("formType", "RADIO")), admin)))
                    .contains("要么挂字典，要么给出固定选项");
            assertThat(messageOf(post("/api/lowcode/models/" + modelId + "/fields", field("kind", "类别", "NOT_A_TYPE", Map.of()), admin)))
                    .contains("不支持的字段类型");
            // 列名同样不能是保留字：desc 派生出的列写进生成的 SQL 直接语法错
            assertThat(messageOf(post("/api/lowcode/models/" + modelId + "/fields", field("desc", "描述", "STRING", Map.of()), admin)))
                    .contains("MySQL 保留字").contains("description");

            // 同项目内的 REF 合法，且能指定展示字段
            long sameProjectModel = fx.model(admin, projectId, "goods");
            long refField = fx.field(admin, modelId, field("goods", "商品", "REF", Map.of("refModelId", sameProjectModel)));
            assertThat(fieldRow(admin, modelId, "goods")).containsEntry("columnType", "bigint").containsEntry("javaType", "Long");
            assertThat(number(fieldRow(admin, modelId, "goods").get("refModelId"))).isEqualTo(sameProjectModel);
            assertThat(refField).isPositive();
        } finally {
            fx.clean();
        }
    }

    @Test
    void 类型映射表由接口单一来源提供() {
        String admin = new Fixture().admin();
        Map<String, Object> meta = dataOf(get("/api/lowcode/models/meta", admin));
        List<Map<String, Object>> fieldTypes = castList(meta.get("fieldTypes"));
        assertThat(fieldTypes).extracting(row -> String.valueOf(row.get("code")))
                .contains("STRING", "TEXT", "INTEGER", "DECIMAL", "DATE", "DATETIME", "ENUM", "FILE", "REF");
        assertThat(fieldTypes).anySatisfy(row -> {
            assertThat(row).containsEntry("code", "DECIMAL");
            assertThat(row).containsEntry("javaType", "BigDecimal");
            assertThat(row).containsEntry("columnType", "decimal(18,2)");
            assertThat(row).containsEntry("formType", "NUMBER");
        });
        assertThat(castList(meta.get("queryTypes"))).extracting(row -> String.valueOf(row.get("code"))).contains("LIKE", "BETWEEN");
        assertThat(castList(meta.get("formTypes"))).extracting(row -> String.valueOf(row.get("code"))).contains("TEXTAREA", "UPLOAD");
    }

    @Test
    void 模型与字段按租户隔离() {
        Fixture fx = new Fixture();
        try {
            String admin = fx.admin();
            long platformProject = fx.project(admin, "mproj5");
            long platformModel = fx.model(admin, platformProject, "tenant_private");

            fx.syncAcmeAssets(admin);
            String acme = fx.acme();
            // acme 直接引用平台租户的模型：既查不到，也不能拿它当 REF 目标
            assertThat(messageOf(getRaw("/api/lowcode/models/" + platformModel + "/fields", acme))).contains("模型不存在");
            long acmeProject = fx.projectAs(acme, "mproj5");
            // 同编码的模型可以在两个租户各自存在（唯一键是 (project_id, model_code)，跨租户不冲突）
            long acmeModel = fx.modelAs(acme, acmeProject, "tenant_private");
            assertThat(acmeModel).isNotEqualTo(platformModel);
            // 把平台租户的模型当 REF 目标：跨租户查不到，直接拒绝
            assertThat(messageOf(post("/api/lowcode/models/" + acmeModel + "/fields",
                    field("ref", "引用", "REF", Map.of("refModelId", platformModel)), acme))).contains("目标模型不存在");
        } finally {
            fx.clean();
        }
    }

    /* ── 请求构造与断言辅助 ── */

    private Map<String, Object> modelBody(long projectId, String modelCode, String modelName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", projectId);
        body.put("modelCode", modelCode);
        body.put("modelName", modelName);
        return body;
    }

    private Map<String, Object> withTableName(Map<String, Object> body, String tableName) {
        body.put("tableName", tableName);
        return body;
    }

    private Map<String, Object> field(String code, String name, String type, Map<String, Object> extras) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fieldCode", code);
        body.put("fieldName", name);
        body.put("fieldType", type);
        body.putAll(extras);
        return body;
    }

    private List<Map<String, Object>> fields(String token, long modelId) {
        return listDataOf(get("/api/lowcode/models/" + modelId + "/fields", token));
    }

    private Map<String, Object> fieldRow(String token, long modelId, String fieldCode) {
        return fields(token, modelId).stream()
                .filter(row -> fieldCode.equals(String.valueOf(row.get("fieldCode"))))
                .findFirst()
                .orElseThrow(() -> new AssertionError("字段列表里没有 " + fieldCode));
    }

    private Map<String, Object> findModel(String token, long projectId, String modelCode) {
        return rowsOf(dataOf(get("/api/lowcode/models?pageNum=1&pageSize=100&projectId=" + projectId, token))).stream()
                .filter(row -> modelCode.equals(String.valueOf(row.get("modelCode"))))
                .findFirst()
                .orElseThrow(() -> new AssertionError("模型列表里没有 " + modelCode));
    }

    /** 一个用例的数据集：项目、模型、字段都要在结束时按外键顺序清掉 */
    private final class Fixture {
        private final List<Long> projectIds = new ArrayList<>();
        private final List<Long> modelIds = new ArrayList<>();
        private final List<Long> fieldIds = new ArrayList<>();

        private String admin() {
            return TestAuthClient.login(restTemplate, "admin", "admin123", "platform");
        }

        private String acme() {
            return TestAuthClient.login(restTemplate, "acmeadmin", "admin123", "acme");
        }

        private void syncAcmeAssets(String adminToken) {
            long tenantId = number(rowsOf(dataOf(get("/api/platform/tenants?pageNum=1&pageSize=100&keyword=acme", adminToken))).get(0).get("id"));
            post("/api/platform/tenants/" + tenantId + "/assets/sync", Map.of(), adminToken);
        }

        private long project(String token, String code) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("projectCode", code + System.nanoTime() % 1_000_000);
            body.put("projectName", "模型用例项目");
            body.put("repoUrl", REPO);
            long id = number(dataOf(post("/api/lowcode/projects", body, token).getBody()).get("id"));
            projectIds.add(id);
            return id;
        }

        private long projectAs(String token, String code) {
            return project(token, code);
        }

        private long model(String token, long projectId, String modelCode) {
            long id = number(dataOf(post("/api/lowcode/models", modelBody(projectId, modelCode, modelCode + " 模型"), token).getBody()).get("id"));
            modelIds.add(id);
            return id;
        }

        private long modelAs(String token, long projectId, String modelCode) {
            return model(token, projectId, modelCode);
        }

        private long field(String token, long modelId, Map<String, Object> body) {
            long id = number(dataOf(post("/api/lowcode/models/" + modelId + "/fields", body, token).getBody()).get("id"));
            fieldIds.add(id);
            return id;
        }

        private void clean() {
            // 按 id 删之外再按编码前缀兜一遍：断言在「拿到 id 之前」失败时（如响应解析出错）也不会留残留
            fieldIds.forEach(id -> jdbcTemplate.update("DELETE FROM lc_field WHERE id = ?", id));
            modelIds.forEach(id -> jdbcTemplate.update("DELETE FROM lc_model WHERE id = ?", id));
            projectIds.forEach(id -> jdbcTemplate.update("DELETE FROM lc_project WHERE id = ?", id));
            jdbcTemplate.update("DELETE FROM lc_field WHERE model_id IN (SELECT id FROM lc_model WHERE project_id IN (SELECT id FROM lc_project WHERE project_code LIKE 'mproj%'))");
            jdbcTemplate.update("DELETE FROM lc_model WHERE project_id IN (SELECT id FROM lc_project WHERE project_code LIKE 'mproj%')");
            jdbcTemplate.update("DELETE FROM lc_project WHERE project_code LIKE 'mproj%'");
        }
    }

    /* ── HTTP 辅助 ── */

    private ResponseEntity<Map<String, Object>> post(String path, Map<String, Object> body, String token) {
        return exchange(HttpMethod.POST, path, body, token);
    }

    private ResponseEntity<Map<String, Object>> put(String path, Map<String, Object> body, String token) {
        return exchange(HttpMethod.PUT, path, body, token);
    }

    private ResponseEntity<Map<String, Object>> delete(String path, String token) {
        return exchange(HttpMethod.DELETE, path, null, token);
    }

    private ResponseEntity<Map<String, Object>> exchange(HttpMethod method, String path, Object body, String token) {
        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), MAP_TYPE);
    }

    private Map<String, Object> get(String path, String token) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(bearer(token)), MAP_TYPE);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    /** 只取业务码与提示，用于断言失败分支 */
    private Map<String, Object> getRaw(String path, String token) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(bearer(token)), MAP_TYPE);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        return response.getBody();
    }

    private String messageOf(ResponseEntity<Map<String, Object>> response) {
        assertThat(response.getBody()).isNotNull();
        return String.valueOf(response.getBody().get("message"));
    }

    private String messageOf(Map<String, Object> body) {
        return String.valueOf(body.get("message"));
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
