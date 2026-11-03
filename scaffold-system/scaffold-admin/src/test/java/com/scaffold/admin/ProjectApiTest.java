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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 项目管理接口（PR 3a）。依赖 sql/verify/V9__dual_tenant_demo.sql 的第二个租户 acme 做跨租户对照。
 */
@SpringBootTest(
        classes = ScaffoldAdminApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class ProjectApiTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
    };

    private static final String REPO = "https://github.com/acme/order-service.git";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 项目从新增到软删的完整链路() {
        String admin = platformToken();
        String suffix = String.valueOf(System.nanoTime() % 1_000_000);
        String code = "itproj" + suffix;
        Map<String, Object> created = dataOf(post("/api/lowcode/projects", body(code, "订单中台", REPO, "main", "platform-generated"), admin).getBody());
        long id = ((Number) created.get("id")).longValue();
        try {
            Map<String, Object> row = findProject(admin, code);
            assertThat(row).containsEntry("projectName", "订单中台");
            assertThat(row).containsEntry("syncStatusText", "未初始化");
            assertThat(row).containsEntry("mainBranch", "main");
            assertThat(row).containsEntry("genBranch", "platform-generated");

            assertThat(put("/api/lowcode/projects/" + id, body(null, "订单中台(改名)", "git@github.com:acme/order-service.git", "trunk", "gen"), admin).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            Map<String, Object> updated = findProject(admin, code);
            assertThat(updated).containsEntry("projectName", "订单中台(改名)");
            assertThat(updated).containsEntry("repoUrl", "git@github.com:acme/order-service.git");
            assertThat(updated).containsEntry("mainBranch", "trunk");

            // options 只回精简字段，给后续模型/页面模块选项目用
            List<Map<String, Object>> options = listDataOf(get("/api/lowcode/projects/options", admin));
            assertThat(options).anySatisfy(option -> {
                assertThat(option).containsEntry("projectCode", code);
                assertThat(option).doesNotContainKey("repoUrl");
            });

            assertThat(put("/api/lowcode/projects/" + id + "/status", Map.of("status", 0), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(findProject(admin, code)).containsEntry("status", 0);

            // 停用后不再出现在 options 里
            assertThat(listDataOf(get("/api/lowcode/projects/options", admin)))
                    .noneSatisfy(option -> assertThat(option).containsEntry("projectCode", code));

            assertThat(delete("/api/lowcode/projects/" + id, admin).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(findProjectOrNull(admin, code)).isNull();
            // 软删：行还在库里，只是 deleted = 1
            assertThat(jdbcTemplate.queryForObject("SELECT deleted FROM lc_project WHERE id = ?", Integer.class, id)).isEqualTo(1);
        } finally {
            jdbcTemplate.update("DELETE FROM lc_project WHERE id = ?", id);
        }
    }

    @Test
    void 非法仓库地址与分支配置被拒() {
        String admin = platformToken();
        String suffix = String.valueOf(System.nanoTime() % 1_000_000);

        assertThat(messageOf(post("/api/lowcode/projects", body("itreject" + suffix, "非法仓库", "file:///home/git/repo.git", "main", "gen"), admin)))
                .contains("仓库地址只支持");
        assertThat(messageOf(post("/api/lowcode/projects", body("itreject" + suffix, "内嵌凭据", "https://token:x-oauth-basic@github.com/a/b.git", "main", "gen"), admin)))
                .contains("不能内嵌账号");
        assertThat(messageOf(post("/api/lowcode/projects", body("itreject" + suffix, "同分支", REPO, "main", "main"), admin)))
                .contains("主干分支与发布分支不能相同");
        assertThat(messageOf(post("/api/lowcode/projects", body(null, "缺编码", REPO, "main", "gen"), admin)))
                .contains("项目编码不能为空");
    }

    @Test
    void 项目按租户隔离且编码只在租户内唯一() {
        String admin = platformToken();
        // V5 的「低代码」菜单只进了平台租户，acme 是迁移前开通的租户，要先走「补齐资产」拿到菜单
        syncTenantAssets(admin, "acme");
        String acme = TestAuthClient.login(restTemplate, "acmeadmin", "admin123", "acme");
        String suffix = String.valueOf(System.nanoTime() % 1_000_000);
        String code = "itshared" + suffix;
        List<Long> createdIds = new java.util.ArrayList<>();
        try {
            long platformId = projectIdOf(dataOf(post("/api/lowcode/projects", body(code, "平台侧项目", REPO, "main", "gen"), admin).getBody()));
            createdIds.add(platformId);
            long acmeId = projectIdOf(dataOf(post("/api/lowcode/projects", body(code, "Acme 侧同名项目", REPO, "main", "gen"), acme).getBody()));
            createdIds.add(acmeId);
            // 同一 project_code 在两个租户各自建得出来（唯一键是 (tenant_id, project_code)）
            assertThat(acmeId).isNotEqualTo(platformId);
            assertThat(findProject(acme, code)).containsEntry("projectName", "Acme 侧同名项目");
            // 两个租户各看各的那一条
            assertThat(findProject(admin, code)).containsEntry("projectName", "平台侧项目");
            assertThat(projectNames(acme, code)).containsExactly("Acme 侧同名项目");
            assertThat(projectNames(admin, code)).containsExactly("平台侧项目");
            // 跨租户改 / 删被拒：拦截器给 UPDATE / DELETE 补了 tenant_id，影响行数为 0
            ResponseEntity<Map<String, Object>> crossUpdate = put("/api/lowcode/projects/" + platformId, body(null, "越权改名", REPO, "main", "gen"), acme);
            assertThat(String.valueOf(messageOf(crossUpdate))).contains("项目不存在");
            // 删除同样被拒：软删语句被注入租户条件，影响 0 行 → 服务层报「项目不存在或已删除」
            ResponseEntity<Map<String, Object>> crossDelete = delete("/api/lowcode/projects/" + platformId, acme);
            assertThat(crossDelete.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(messageOf(crossDelete)).contains("项目不存在");
            assertThat(findProjectOrNull(admin, code)).isNotNull();
        } finally {
            createdIds.forEach(id -> jdbcTemplate.update("DELETE FROM lc_project WHERE id = ?", id));
        }
    }

    /** 平台管理员给某个租户补齐菜单 / 字典 / 参数（幂等），返回新增条数摘要 */
    private Map<String, Object> syncTenantAssets(String adminToken, String tenantCode) {
        long tenantId = tenantIdByCode(adminToken, tenantCode);
        ResponseEntity<Map<String, Object>> response = post("/api/platform/tenants/" + tenantId + "/assets/sync", Map.of(), adminToken);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return dataOf(response.getBody());
    }

    private long tenantIdByCode(String token, String tenantCode) {
        return rowsOf(dataOf(get("/api/platform/tenants?pageNum=1&pageSize=100&keyword=" + tenantCode, token))).stream()
                .filter(row -> tenantCode.equals(String.valueOf(row.get("tenantCode"))))
                .map(row -> ((Number) row.get("id")).longValue())
                .findFirst()
                .orElseThrow(() -> new AssertionError("验收库缺少租户 " + tenantCode));
    }

    private long projectIdOf(Map<String, Object> data) {
        return ((Number) data.get("id")).longValue();
    }

    private Map<String, Object> body(String code, String name, String repoUrl, String mainBranch, String genBranch) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (code != null) {
            body.put("projectCode", code);
        }
        body.put("projectName", name);
        body.put("repoUrl", repoUrl);
        if (mainBranch != null) {
            body.put("mainBranch", mainBranch);
        }
        if (genBranch != null) {
            body.put("genBranch", genBranch);
        }
        return body;
    }

    /** 指定编码在本租户列表里的项目名称，用于证明「同名项目各自可见、互不串台」 */
    private List<String> projectNames(String token, String projectCode) {
        return rowsOf(dataOf(get("/api/lowcode/projects?pageNum=1&pageSize=100&keyword=" + projectCode, token))).stream()
                .filter(row -> projectCode.equals(String.valueOf(row.get("projectCode"))))
                .map(row -> String.valueOf(row.get("projectName")))
                .toList();
    }

    private Map<String, Object> findProject(String token, String projectCode) {
        Map<String, Object> row = findProjectOrNull(token, projectCode);
        assertThat(row).as("租户 %s 下应能查到项目 %s", token.substring(0, 8), projectCode).isNotNull();
        return row;
    }

    private Map<String, Object> findProjectOrNull(String token, String projectCode) {
        return rowsOf(dataOf(get("/api/lowcode/projects?pageNum=1&pageSize=100&keyword=" + projectCode, token))).stream()
                .filter(row -> projectCode.equals(String.valueOf(row.get("projectCode"))))
                .findFirst()
                .orElse(null);
    }

    private String platformToken() {
        return TestAuthClient.login(restTemplate, "admin", "admin123", "platform");
    }

    private String messageOf(ResponseEntity<Map<String, Object>> response) {
        assertThat(response.getBody()).isNotNull();
        return String.valueOf(response.getBody().get("message"));
    }

    private Map<String, Object> get(String path, String token) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                path, HttpMethod.GET, new HttpEntity<>(bearer(token)), MAP_TYPE);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<Map<String, Object>> post(String path, Map<String, Object> body, String token) {
        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), MAP_TYPE);
    }

    private ResponseEntity<Map<String, Object>> put(String path, Map<String, Object> body, String token) {
        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, headers), MAP_TYPE);
    }

    private ResponseEntity<Map<String, Object>> delete(String path, String token) {
        return restTemplate.exchange(path, HttpMethod.DELETE, new HttpEntity<>(bearer(token)), MAP_TYPE);
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
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
