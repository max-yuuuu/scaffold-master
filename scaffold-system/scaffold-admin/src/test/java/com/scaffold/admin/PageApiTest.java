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
 * 页面设计（PR 6A）：lc_page 的存取、design_json 入库前的校验、ref_fields 反查拦截。
 *
 * <p>这一层的价值全在「存进去的设计一定生成得出来」：设计器是给人自由摆的，生成器只认模型与模板，
 * 保存时不把两者对一遍，错的那份就要等到 PR 7 发布、再等到交付项目构建才暴露 —— 5G 演练里那六条
 * 缺陷全部从这个位置漏出去过。形状的细则在 {@code PageDesignTest}（单元 16 例），这里走接口这条真链路：
 * 权限码、租户隔离、编码唯一，以及删字段时说不说得清「哪几个页面还在用」。
 */
@SpringBootTest(
        classes = ScaffoldAdminApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class PageApiTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
    };

    private static final String REPO = "https://github.com/acme/page-lab.git";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 页面带着设计存进来再原样读回() {
        Fixture fx = new Fixture();
        try {
            String admin = fx.admin();
            long projectId = fx.project(admin, "pproj1");
            long modelId = fx.model(admin, projectId, "sales_order");
            fx.salesOrderFields(admin, modelId);
            long pageId = fx.page(admin, projectId, "sales_order_list", "销售订单列表");

            Map<String, Object> row = findPage(admin, projectId, "sales_order_list");
            assertThat(row).containsEntry("pageName", "销售订单列表");
            assertThat(row).containsEntry("pageType", "list");
            assertThat(row).containsEntry("modelCode", "sales_order");
            // 列表不带宽的 design_json：一屏几十行页面各带一份设计，翻页就把内存吃光了
            assertThat(row).doesNotContainKey("design");
            // 引用清单是保存时从设计里解析出来的，删字段反查只看它，不做 JSON 全文搜索
            assertThat(number(row.get("refFieldCount"))).isEqualTo(4);

            Map<String, Object> detail = dataOf(get("/api/lowcode/pages/" + pageId, admin));
            // 路由没填就是留空，由生成器按接口的 apiPath 落 —— 不在库里再派生一份
            assertThat(detail.get("routePath")).isNull();
            Map<String, Object> design = castMap(detail.get("design"));
            assertThat(design).containsEntry("version", "1.0").containsEntry("pageType", "list")
                    .containsEntry("modelRef", "sales_order");
            assertThat(castMap(design.get("layout")).get("type")).isEqualTo("PageContainer");
            assertThat(listOf(detail.get("refFields"))).containsExactly("orderNo", "amount", "deliveryDate", "remark");

            // 菜单落点填了以填的为准：设计器里改了路由与图标，生成的菜单就跟着改
            Map<String, Object> moved = pageBody(projectId, "sales_order_list", "销售订单列表");
            moved.put("routePath", "/trade/orders");
            moved.put("menuIcon", "chart");
            moved.put("menuSort", 3);
            assertThat(put("/api/lowcode/pages/" + pageId, moved, admin).getBody()).containsEntry("code", 200);
            assertThat(findPage(admin, projectId, "sales_order_list")).containsEntry("routePath", "/trade/orders");
            Map<String, Object> afterMove = dataOf(get("/api/lowcode/pages/" + pageId, admin));
            assertThat(afterMove).containsEntry("menuIcon", "chart").containsEntry("menuSort", 3);
            // 设计整份换掉，读回来的还是那一棵树
            assertThat(castMap(afterMove.get("design"))).containsEntry("version", "1.0");
        } finally {
            fx.clean();
        }
    }

    @Test
    void 设计对不上模型就存不进去且不留下半成品() {
        Fixture fx = new Fixture();
        try {
            String admin = fx.admin();
            long projectId = fx.project(admin, "pproj2");
            long modelId = fx.model(admin, projectId, "sales_order");
            fx.salesOrderFields(admin, modelId);

            // 绑了一个模型里没有的字段：属性面板的下拉本来只能从模型字段里选，手写等于跨模型复制了设计
            Map<String, Object> body = pageBody(projectId, "bad_field", "错字段页面");
            Map<String, Object> layout = castMap(castMap(body.get("design")).get("layout"));
            layout.put("children", List.of(Map.of("type", "DataTable",
                    "columns", List.of(Map.of("field", "amounts", "title", "金额")))));
            assertThat(messageOf(post("/api/lowcode/pages", body, admin)))
                    .contains("不是模型 sales_order 的字段")
                    .contains("layout.children");

            // 页面归属的模型与设计里的 modelRef 不一致
            Map<String, Object> mismatch = pageBody(projectId, "mismatch", "串了模型");
            castMap(mismatch.get("design")).put("modelRef", "order_item");
            assertThat(messageOf(post("/api/lowcode/pages", mismatch, admin))).contains("modelRef");

            // 第一期只做列表页：详情 / 看板现在没有对应模板
            Map<String, Object> detailPage = pageBody(projectId, "detail_page", "详情页");
            castMap(detailPage.get("design")).put("pageType", "detail");
            assertThat(messageOf(post("/api/lowcode/pages", detailPage, admin))).contains("第一期只支持列表页");

            // 自增主键被摆进表单（5G 的真机缺陷），接口这条链同样拦得住
            Map<String, Object> withKey = pageBody(projectId, "with_key", "主键进表单");
            Map<String, Object> withKeyLayout = castMap(castMap(withKey.get("design")).get("layout"));
            withKeyLayout.put("children", List.of(table(), Map.of("type", "FormContainer", "children", List.of(
                    Map.of("type", "NumberInput", "props", Map.of("label", "主键", "field", "id"))))));
            assertThat(messageOf(post("/api/lowcode/pages", withKey, admin))).contains("自增主键");

            // 项目必须存在，模型也必须在这个项目里
            Map<String, Object> noModel = pageBody(projectId, "no_model", "没有模型");
            noModel.put("modelCode", "not_a_model");
            assertThat(messageOf(post("/api/lowcode/pages", noModel, admin))).contains("模型不存在");

            assertThat(pagesOf(admin, projectId)).isEmpty();
        } finally {
            fx.clean();
        }
    }

    @Test
    void 页面编码与归属创建后不可改() {
        Fixture fx = new Fixture();
        try {
            String admin = fx.admin();
            long projectId = fx.project(admin, "pproj3");
            long modelId = fx.model(admin, projectId, "sales_order");
            fx.salesOrderFields(admin, modelId);
            long pageId = fx.page(admin, projectId, "sales_order_list", "销售订单列表");

            assertThat(messageOf(post("/api/lowcode/pages", pageBody(projectId, "sales_order_list", "重名页面"), admin)))
                    .contains("已存在同名页面编码");
            // 同编码在两个项目里各自存在（唯一键是 (project_id, page_code)）；字段也要各建一份，
            // 设计绑的是自己那份模型的字段编码
            long otherProject = fx.project(admin, "pproj3b");
            long otherModel = fx.model(admin, otherProject, "sales_order");
            fx.salesOrderFields(admin, otherModel);
            long twin = fx.page(admin, otherProject, "sales_order_list", "销售订单列表");
            assertThat(twin).isNotEqualTo(pageId);

            assertThat(messageOf(put("/api/lowcode/pages/" + pageId, pageBody(projectId, "sales_order_list2", "改编码"), admin)))
                    .contains("页面编码不可修改");
            Map<String, Object> rebind = pageBody(projectId, "sales_order_list", "换模型");
            rebind.put("modelCode", "order_item");
            assertThat(messageOf(put("/api/lowcode/pages/" + pageId, rebind, admin))).contains("页面归属模型不可修改");
            Map<String, Object> move = pageBody(projectId, "sales_order_list", "换项目");
            move.put("projectId", otherProject);
            assertThat(messageOf(put("/api/lowcode/pages/" + pageId, move, admin))).contains("归属项目不可修改");
        } finally {
            fx.clean();
        }
    }

    @Test
    void 删字段与下线模型要先问还在用的页面() {
        Fixture fx = new Fixture();
        try {
            String admin = fx.admin();
            long projectId = fx.project(admin, "pproj4");
            long modelId = fx.model(admin, projectId, "sales_order");
            fx.salesOrderFields(admin, modelId);
            long pageId = fx.page(admin, projectId, "sales_order_list", "销售订单列表");

            // 页面正在用 remark：删掉它就是让这份设计取不到列，所以入口就拦，并把受影响的页面说清楚
            assertThat(messageOf(delete("/api/lowcode/fields/" + fx.fieldIds.get("remark"), admin)))
                    .contains("销售订单列表")
                    .contains("remark");
            // 设计里没引用 status 这一格，删字段不该被拦
            long statusId = fx.fieldIds.get("status");
            fx.fieldIds.remove("status");
            assertThat(delete("/api/lowcode/fields/" + statusId, admin).getBody()).containsEntry("code", 200);

            // 整个模型都有页面在用，下线模型同样拦
            assertThat(messageOf(delete("/api/lowcode/models/" + modelId, admin))).contains("销售订单列表");

            // 页面删了，反查就不该再拦住删字段 —— 软删的页面不算还在用
            assertThat(delete("/api/lowcode/pages/" + pageId, admin).getBody()).containsEntry("code", 200);
            assertThat(delete("/api/lowcode/fields/" + fx.fieldIds.get("remark"), admin).getBody())
                    .containsEntry("code", 200);
        } finally {
            fx.clean();
        }
    }

    @Test
    void 页面按租户隔离且停用的不参与生成() {
        Fixture fx = new Fixture();
        try {
            String admin = fx.admin();
            long projectId = fx.project(admin, "pproj5");
            long modelId = fx.model(admin, projectId, "sales_order");
            fx.salesOrderFields(admin, modelId);
            long pageId = fx.page(admin, projectId, "sales_order_list", "销售订单列表");

            fx.syncAcmeAssets(admin);
            String acme = fx.acme();
            assertThat(messageOf(delete("/api/lowcode/pages/" + pageId, acme))).contains("页面不存在");
            assertThat(messageOf(put("/api/lowcode/pages/" + pageId, pageBody(projectId, "sales_order_list", "改名"), acme)))
                    .contains("页面不存在");
            // acme 拿平台租户的项目来建页面：查不到就是不存在，不能凭 id 写进去
            assertThat(messageOf(post("/api/lowcode/pages", pageBody(projectId, "acme_page", "越权页面"), acme)))
                    .contains("项目不存在");

            // 启停：停用的页面不进发布，但记录还在
            assertThat(put("/api/lowcode/pages/" + pageId + "/status", Map.of("status", 0), admin).getBody())
                    .containsEntry("code", 200);
            assertThat(findPage(admin, projectId, "sales_order_list")).containsEntry("status", 0);
            assertThat(messageOf(put("/api/lowcode/pages/" + pageId + "/status", Map.of("status", 7), admin)))
                    .contains("状态只能为");
        } finally {
            fx.clean();
        }
    }

    /* ── 请求构造 ── */

    /** 一份能生成出来的列表页设计：查询一条、工具栏一个新增、两列、表单四个字段 */
    private static Map<String, Object> design() {
        Map<String, Object> design = new LinkedHashMap<>();
        design.put("version", "1.0");
        design.put("pageType", "list");
        design.put("modelRef", "sales_order");
        design.put("layout", new LinkedHashMap<>(Map.of(
                "type", "PageContainer",
                "props", new LinkedHashMap<>(Map.of("title", "销售订单", "desc", "管理所有销售订单")),
                "children", List.of(
                        block("SearchForm", Map.of("columns", 3), List.of(
                                control("TextInput", "订单编号", "orderNo", "LIKE"))),
                        block("Toolbar", null, List.of(Map.of("type", "Button",
                                "props", new LinkedHashMap<>(Map.of("label", "新增", "action", "create"))))),
                        table(),
                        block("FormContainer", null, List.of(
                                control("TextInput", "订单编号", "orderNo", null),
                                control("NumberInput", "订单金额", "amount", null),
                                control("DateInput", "交货日期", "deliveryDate", null),
                                control("Textarea", "备注", "remark", null)))))));
        return design;
    }

    private static Map<String, Object> table() {
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("type", "DataTable");
        table.put("props", new LinkedHashMap<>(Map.of("pagination", true, "pageSize", 10)));
        table.put("columns", List.of(
                new LinkedHashMap<>(Map.of("field", "orderNo", "title", "订单编号")),
                new LinkedHashMap<>(Map.of("field", "amount", "title", "订单金额", "align", "right"))));
        return table;
    }

    private static Map<String, Object> block(String type, Map<String, Object> props, List<Map<String, Object>> children) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", type);
        if (props != null) {
            block.put("props", new LinkedHashMap<>(props));
        }
        block.put("children", new ArrayList<>(children));
        return block;
    }

    private static Map<String, Object> control(String type, String label, String field, String queryType) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("label", label);
        if (field != null) {
            props.put("field", field);
        }
        if (queryType != null) {
            props.put("queryType", queryType);
        }
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", type);
        node.put("props", props);
        return node;
    }

    /** 请求体里的 projectId 由调用方给：改页面时它是页面自己的项目，用例后面要断言的就是「这个字段不许换」 */
    private Map<String, Object> pageBody(long projectId, String pageCode, String pageName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", projectId);
        body.put("modelCode", "sales_order");
        body.put("pageCode", pageCode);
        body.put("pageName", pageName);
        body.put("design", design());
        return body;
    }

    /* ── 数据集 ── */

    /** 一个用例的数据集：项目、模型、字段、页面都要在结束时按外键顺序清掉 */
    private final class Fixture {
        private final List<Long> projectIds = new ArrayList<>();
        private final List<Long> modelIds = new ArrayList<>();
        private final Map<String, Long> fieldIds = new LinkedHashMap<>();
        private final List<Long> pageIds = new ArrayList<>();

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
            body.put("projectName", "页面用例项目");
            body.put("repoUrl", REPO);
            long id = number(dataOf(post("/api/lowcode/projects", body, token).getBody()).get("id"));
            projectIds.add(id);
            return id;
        }

        private long model(String token, long projectId, String modelCode) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("projectId", projectId);
            body.put("modelCode", modelCode);
            body.put("modelName", modelCode + " 模型");
            long id = number(dataOf(post("/api/lowcode/models", body, token).getBody()).get("id"));
            modelIds.add(id);
            return id;
        }

        /** 一份够用的销售订单字段：自增主键、文本、金额、状态、日期、备注 */
        private void salesOrderFields(String token, long modelId) {
            field(token, modelId, "id", new LinkedHashMap<>(Map.of("fieldName", "主键",
                    "fieldType", "INTEGER", "isPrimary", 1, "isAutoIncrement", 1, "isNullable", 0)));
            field(token, modelId, "orderNo", new LinkedHashMap<>(Map.of("fieldName", "订单编号",
                    "fieldType", "STRING", "columnLength", 64, "isQuery", 1, "queryType", "LIKE", "isRequired", 1)));
            field(token, modelId, "amount", new LinkedHashMap<>(Map.of("fieldName", "订单金额", "fieldType", "DECIMAL")));
            field(token, modelId, "status", new LinkedHashMap<>(Map.of("fieldName", "状态", "fieldType", "INTEGER")));
            field(token, modelId, "deliveryDate", new LinkedHashMap<>(Map.of("fieldName", "交货日期", "fieldType", "DATE")));
            field(token, modelId, "remark", new LinkedHashMap<>(Map.of("fieldName", "备注",
                    "fieldType", "STRING", "columnLength", 255, "formType", "TEXTAREA")));
        }

        private void field(String token, long modelId, String code, Map<String, Object> extras) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("fieldCode", code);
            body.putAll(extras);
            long id = number(dataOf(post("/api/lowcode/models/" + modelId + "/fields", body, token).getBody()).get("id"));
            fieldIds.put(code, id);
        }

        private long page(String token, long projectId, String pageCode, String pageName) {
            Map<String, Object> body = pageBody(projectId, pageCode, pageName);
            long id = number(dataOf(post("/api/lowcode/pages", body, token).getBody()).get("id"));
            pageIds.add(id);
            return id;
        }

        private void clean() {
            pageIds.forEach(id -> jdbcTemplate.update("DELETE FROM lc_page WHERE id = ?", id));
            jdbcTemplate.update("DELETE FROM lc_page WHERE project_id IN (SELECT id FROM lc_project WHERE project_code LIKE 'pproj%')");
            fieldIds.values().forEach(id -> jdbcTemplate.update("DELETE FROM lc_field WHERE id = ?", id));
            jdbcTemplate.update("DELETE FROM lc_field WHERE model_id IN (SELECT id FROM lc_model WHERE project_id IN (SELECT id FROM lc_project WHERE project_code LIKE 'pproj%'))");
            modelIds.forEach(id -> jdbcTemplate.update("DELETE FROM lc_model WHERE id = ?", id));
            jdbcTemplate.update("DELETE FROM lc_model WHERE project_id IN (SELECT id FROM lc_project WHERE project_code LIKE 'pproj%')");
            projectIds.forEach(id -> jdbcTemplate.update("DELETE FROM lc_project WHERE id = ?", id));
            jdbcTemplate.update("DELETE FROM lc_project WHERE project_code LIKE 'pproj%'");
        }
    }

    /* ── 读回与 HTTP 辅助 ── */

    private Map<String, Object> findPage(String token, long projectId, String pageCode) {
        return pagesOf(token, projectId).stream()
                .filter(row -> pageCode.equals(String.valueOf(row.get("pageCode"))))
                .findFirst()
                .orElseThrow(() -> new AssertionError("页面列表里没有 " + pageCode));
    }

    private List<Map<String, Object>> pagesOf(String token, long projectId) {
        return rowsOf(dataOf(get("/api/lowcode/pages?pageNum=1&pageSize=100&projectId=" + projectId, token)));
    }

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

    /** 只取提示语，用于断言失败分支：业务异常统一按 400 返回 */
    private String messageOf(ResponseEntity<Map<String, Object>> response) {
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        return String.valueOf(response.getBody().get("message"));
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
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<String> listOf(Object value) {
        return (List<String>) value;
    }
}
