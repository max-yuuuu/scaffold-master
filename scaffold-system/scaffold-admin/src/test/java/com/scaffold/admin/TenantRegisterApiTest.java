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
 * 租户自助注册（登录页的注册表单）：提交即开通租户 + 管理员账号 + 角色 + 菜单，并直接回令牌。
 * 每个用例注册完都会按 tenant_id 清理，验收库不留下多余租户。
 */
@SpringBootTest(
        classes = ScaffoldAdminApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class TenantRegisterApiTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
    };

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 注册即开通并直接进入新租户() {
        String suffix = String.valueOf(System.nanoTime() % 1_000_000);
        String tenantCode = "it" + suffix;
        String username = "it_reg_admin_" + suffix;
        Map<String, Object> data = register(tenantCode, "集成测试注册租户", username, "Reg@12345");
        long tenantId = ((Number) data.get("tenantId")).longValue();
        try {
            String token = String.valueOf(data.get("token"));
            Map<String, Object> me = dataOf(get("/api/auth/me", token));
            assertThat(me).containsEntry("username", username);
            assertThat(me).containsEntry("tenantCode", tenantCode);
            assertThat(listOf(me, "roles")).contains("tenant_admin");
            // 注册出来的管理员只有本租户业务权限，拿不到平台管理权限码
            assertThat(listOf(me, "permissions")).contains("system:user:list").doesNotContain("platform:tenant:list");

            // 菜单树按平台模板复制过来了，不是空页面
            List<Map<String, Object>> routes = castList(get("/api/auth/routes", token).get("data"));
            assertThat(flattenPaths(routes)).contains("/dashboard", "/system/user");

            // 字典与内置参数随租户一起开通：否则状态下拉为空、新增用户取不到初始密码
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_dict_type WHERE tenant_id = ?", Long.class, tenantId))
                    .isGreaterThanOrEqualTo(3L);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_dict_data WHERE tenant_id = ?", Long.class, tenantId))
                    .isGreaterThanOrEqualTo(7L);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_config WHERE tenant_id = ?", Long.class, tenantId))
                    .isGreaterThanOrEqualTo(3L);

            // 补齐接口幂等：再同步一次不应产生第二份菜单 / 字典 / 参数
            long menusBefore = countByTenant("sys_menu", tenantId);
            Map<String, Object> again = dataOf(post("/api/platform/tenants/" + tenantId + "/assets/sync", adminToken()));
            assertThat(again).containsEntry("menuAdded", 0);
            assertThat(countByTenant("sys_menu", tenantId)).isEqualTo(menusBefore);
            assertThat(countByTenant("sys_dict_data", tenantId)).isEqualTo(7L);

            // 业务数据只看到自己
            Map<String, Object> page = dataOf(get("/api/system/users?pageNum=1&pageSize=50", token));
            assertThat(rowsOf(page)).hasSize(1);
            assertThat(rowsOf(page).get(0)).containsEntry("username", username);

            // 平台管理接口对普通租户管理员关闭
            ResponseEntity<Map<String, Object>> denied = restTemplate.exchange(
                    "/api/platform/tenants", HttpMethod.GET, new HttpEntity<>(bearer(token)), MAP_TYPE);
            assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

            // 平台侧看得到这个新租户，且账号数统计为 1
            String adminToken = TestAuthClient.login(restTemplate, "admin", "admin123", "platform");
            Map<String, Object> tenants = dataOf(get("/api/platform/tenants?pageNum=1&pageSize=100&keyword=" + tenantCode, adminToken));
            assertThat(rowsOf(tenants)).anySatisfy(row -> {
                assertThat(row).containsEntry("tenantCode", tenantCode);
                assertThat(((Number) row.get("userCount")).longValue()).isEqualTo(1L);
            });
        } finally {
            purgeTenant(tenantId);
        }
    }

    @Test
    void 注册时新租户拿不到平台管理菜单() {
        String suffix = String.valueOf(System.nanoTime() % 1_000_000);
        String tenantCode = "it" + suffix;
        Map<String, Object> data = register(tenantCode, "菜单边界校验租户", "it_reg_menu_" + suffix, "Reg@12345");
        long tenantId = ((Number) data.get("tenantId")).longValue();
        try {
            Long platformMenus = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_menu WHERE tenant_id = ? AND (path LIKE '/platform%' OR permission LIKE 'platform:%')",
                    Long.class, tenantId);
            assertThat(platformMenus).isZero();
            // 生成链路的分支默认参数随租户一起建好
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lc_tenant_config WHERE tenant_id = ?", Long.class, tenantId))
                    .isEqualTo(2L);
        } finally {
            purgeTenant(tenantId);
        }
    }

    @Test
    void 重复的租户编码与账号被拒() {
        String suffix = String.valueOf(System.nanoTime() % 1_000_000);
        String tenantCode = "it" + suffix;
        String username = "it_reg_dup_" + suffix;
        Map<String, Object> data = register(tenantCode, "重复校验租户", username, "Reg@12345");
        long tenantId = ((Number) data.get("tenantId")).longValue();
        try {
            ResponseEntity<Map<String, Object>> sameCode = registerResponse(tenantCode, "重复校验租户2", "it_reg_other_" + suffix, "Reg@12345");
            assertThat(sameCode.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(String.valueOf(sameCode.getBody().get("message"))).contains("租户编码已被使用");

            ResponseEntity<Map<String, Object>> sameUser = registerResponse("it" + suffix + "b", "重复账号租户", username, "Reg@12345");
            assertThat(sameUser.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(String.valueOf(sameUser.getBody().get("message"))).contains("管理员账号已被使用");

            // 弱密码走 Bean Validation，不进业务层
            ResponseEntity<Map<String, Object>> weak = registerResponse("it" + suffix + "c", "弱密码租户", "it_reg_weak_" + suffix, "12345678");
            assertThat(weak.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(String.valueOf(weak.getBody().get("message"))).contains("password");
        } finally {
            purgeTenant(tenantId);
        }
    }

    private Map<String, Object> register(String tenantCode, String tenantName, String username, String password) {
        ResponseEntity<Map<String, Object>> response = registerResponse(tenantCode, tenantName, username, password);
        assertThat(response.getStatusCode())
                .as("注册响应 %s", response.getBody())
                .isEqualTo(HttpStatus.OK);
        return dataOf(response.getBody());
    }

    private ResponseEntity<Map<String, Object>> registerResponse(String tenantCode, String tenantName, String username, String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantCode", tenantCode);
        body.put("tenantName", tenantName);
        body.put("adminUsername", username);
        body.put("password", password);
        body.put("captcha", "");
        body.put("captchaUuid", "");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange("/api/auth/register", HttpMethod.POST, new HttpEntity<>(body, headers), MAP_TYPE);
    }

    /** 注册产生的数据全部挂在 tenant_id 上，按租户清理即可（菜单 ID 是自增的，只能按 tenant_id 删） */
    private void purgeTenant(long tenantId) {
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM sys_role WHERE tenant_id = ?)", tenantId);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE tenant_id = ?)", tenantId);
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM sys_role WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM sys_menu WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM lc_tenant_config WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM sys_login_log WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM lc_tenant WHERE id = ?", tenantId);
    }

    private long countByTenant(String table, long tenantId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ?", Long.class, tenantId);
    }

    private String adminToken() {
        return TestAuthClient.login(restTemplate, "admin", "admin123", "platform");
    }

    private Map<String, Object> post(String path, String token) {
        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(Map.of(), headers), MAP_TYPE);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private Map<String, Object> get(String path, String token) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                path, HttpMethod.GET, new HttpEntity<>(bearer(token)), MAP_TYPE);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
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
    private List<String> listOf(Map<String, Object> data, String key) {
        return (List<String>) data.get(key);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        return value instanceof Map<?, ?> map
                ? (List<Map<String, Object>>) map.get("routes")
                : (List<Map<String, Object>>) value;
    }

    private List<String> flattenPaths(List<Map<String, Object>> routes) {
        List<String> paths = new java.util.ArrayList<>();
        collectPaths(routes, paths);
        return paths;
    }

    @SuppressWarnings("unchecked")
    private void collectPaths(List<Map<String, Object>> nodes, List<String> paths) {
        for (Map<String, Object> node : nodes) {
            Object path = node.get("path");
            if (path != null) {
                paths.add(String.valueOf(path));
            }
            Object children = node.get("children");
            if (children instanceof List<?> list) {
                collectPaths((List<Map<String, Object>>) list, paths);
            }
        }
    }
}
