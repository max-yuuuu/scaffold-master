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
 * 租户权限边界与内置角色口径：
 * <ul>
 *   <li>租户内不得手工占用平台保留角色码、不得造出平台保留权限码的菜单；</li>
 *   <li>{@code super_admin} 只在账号归属平台租户时生效，存量伪超管角色不放行平台接口；</li>
 *   <li>注册即开通要自带「租户管理员（全权限）+ 租户成员（只读）」两个内置角色，新租户建普通用户不必手搓角色。</li>
 * </ul>
 * 用例注册的租户在结尾按 tenant_id 清理，验收库不残留。
 */
@SpringBootTest(
        classes = ScaffoldAdminApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class TenantRoleCaliberApiTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
    };

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 租户管理员不能占用保留角色码() {
        Fixture fixture = registerTenant("保留角色码");
        try {
            for (String code : List.of("super_admin", "tenant_admin", "tenant_member")) {
                ResponseEntity<Map<String, Object>> response = post("/api/system/roles",
                        Map.of("name", "手工角色" + code, "code", code, "dataScope", "ALL"), fixture.token());
                assertThat(response.getStatusCode())
                        .as("租户内手工创建角色码 %s 应被拒绝，实际响应 %s", code, response.getBody())
                        .isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(String.valueOf(response.getBody().get("message"))).contains("保留");
            }
            // 内置两角色各只有一份，没有被手工创建出第二份同名角色；租户内不存在手工角色
            assertThat(roleCount(fixture.tenantId(), "super_admin")).isZero();
            assertThat(roleCount(fixture.tenantId(), "tenant_admin")).isEqualTo(1L);
            assertThat(roleCount(fixture.tenantId(), "tenant_member")).isEqualTo(1L);
            assertThat(count("SELECT COUNT(*) FROM sys_role WHERE tenant_id = ? AND builtin = 0", fixture.tenantId()))
                    .isZero();
        } finally {
            purge(fixture.tenantId());
        }
    }

    @Test
    void 平台租户不被保留码校验挡住() {
        String admin = platformToken();
        // 平台租户里 super_admin 早已存在，撞名只能报「已存在」而不能报「保留」——口径别把平台自己锁死
        ResponseEntity<Map<String, Object>> reserved = post("/api/system/roles",
                Map.of("name", "平台保留码验证", "code", "super_admin", "dataScope", "ALL"), admin);
        assertThat(reserved.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String message = String.valueOf(reserved.getBody().get("message"));
        assertThat(message).doesNotContain("保留").contains("已存在");

        // 平台租户照常建普通角色
        ResponseEntity<Map<String, Object>> created = post("/api/system/roles",
                Map.of("name", "平台普通角色", "code", "it_platform_role_" + System.nanoTime() % 1_000_000,
                        "dataScope", "DEPT"), admin);
        assertThat(created.getStatusCode())
                .as("平台租户建普通角色应成功，实际响应 %s", created.getBody())
                .isEqualTo(HttpStatus.OK);
        long roleId = ((Number) dataOf(created.getBody()).get("id")).longValue();
        assertThat(delete("/api/system/roles/" + roleId, admin).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void 菜单不得使用平台保留的权限码与路径() {
        Fixture fixture = registerTenant("保留权限码");
        try {
            assertThat(post("/api/system/menus",
                    Map.of("name", "越权菜单", "type", "menu", "path", "/evil", "permission", "platform:tenant:list"),
                    fixture.token()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(post("/api/system/menus",
                    Map.of("name", "越权菜单", "type", "menu", "path", "/platform/evil", "permission", "system:user:list"),
                    fixture.token()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            // 已有菜单改权限码也不行：把本租户一条业务菜单改成平台权限码
            long menuId = firstOwnMenuId(fixture.tenantId());
            assertThat(put("/api/system/menus/" + menuId, Map.of("permission", "platform:tenant:list"), fixture.token())
                    .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            // 业务菜单照常可建，校验不能误伤
            assertThat(post("/api/system/menus",
                    Map.of("name", "自定义页面", "type", "menu", "path", "/custom/page", "permission", "custom:page:list"),
                    fixture.token()).getStatusCode()).isEqualTo(HttpStatus.OK);
        } finally {
            purge(fixture.tenantId());
        }
    }

    @Test
    void 存量伪超管角色不放行平台接口() {
        Fixture fixture = registerTenant("伪超管");
        try {
            // 直接落一条 role_code = super_admin 的角色并绑到租户管理员，模拟存量数据或绕过入口校验
            jdbcTemplate.update("""
                    INSERT INTO sys_role (tenant_id, role_name, role_code, data_scope, sort, status, builtin, create_by, update_by)
                    VALUES (?, '伪装超管', 'super_admin', 'ALL', 9, 1, 0, 'it', 'it')
                    """, fixture.tenantId());
            Long forgedRoleId = jdbcTemplate.queryForObject(
                    "SELECT id FROM sys_role WHERE tenant_id = ? AND role_code = 'super_admin' AND create_by = 'it'",
                    Long.class, fixture.tenantId());
            jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)",
                    fixture.adminUserId(), forgedRoleId);

            String token = TestAuthClient.login(restTemplate, fixture.adminUsername(), "Caliber@12345", fixture.tenantCode());
            Map<String, Object> me = dataOf(get("/api/auth/me", token));
            assertThat(listOf(me, "roles")).contains("super_admin");
            assertThat(listOf(me, "permissions")).doesNotContain("platform:tenant:list");

            assertThat(getResponse("/api/platform/tenants?pageNum=1&pageSize=10", token).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            // 业务异常统一回 400 + 业务码 403（见 GlobalExceptionHandler），这里断言两层
            ResponseEntity<Map<String, Object>> switchDenied =
                    post("/api/auth/switch-tenant", Map.of("tenantId", 1L), token);
            assertThat(switchDenied.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(switchDenied.getBody()).containsEntry("code", 403);
            assertThat(String.valueOf(switchDenied.getBody().get("message"))).contains("只有平台管理员可以切换租户");

            // 真超管（账号归属平台租户）切换租户不受影响
            String admin = platformToken();
            Map<String, Object> switched = dataOf(post("/api/auth/switch-tenant", Map.of("tenantId", fixture.tenantId()), admin).getBody());
            assertThat(switched).containsKey("token");
        } finally {
            purge(fixture.tenantId());
        }
    }

    @Test
    void 注册即开通自带只读成员角色() {
        Fixture fixture = registerTenant("只读成员");
        try {
            assertThat(roleCodes(fixture.tenantId())).contains("tenant_admin", "tenant_member");
            long memberId = roleId(fixture.tenantId(), "tenant_member");

            // 只读 = 拿到全部页面与目录（含 :list），一个写操作按钮都不给
            assertThat(grantedButtons(memberId)).isZero();
            assertThat(grantedPages(memberId)).isEqualTo(tenantPages(fixture.tenantId()));

            assertThat(roleCodes(fixture.tenantId())).containsOnly("tenant_admin", "tenant_member");
            assertThat(builtinFlags(fixture.tenantId())).containsOnly(1);
            // 只读管的是「能不能改」，数据范围仍给全部，否则成员连本租户的列表都看不清
            assertThat(jdbcTemplate.queryForObject("SELECT data_scope FROM sys_role WHERE id = ?",
                    String.class, memberId)).isEqualTo("ALL");
        } finally {
            purge(fixture.tenantId());
        }
    }

    @Test
    void 成员能看列表但改不了数据() {
        Fixture fixture = registerTenant("成员读写");
        try {
            long memberId = roleId(fixture.tenantId(), "tenant_member");
            String memberName = fixture.adminUsername() + "_member";
            assertThat(post("/api/system/users", Map.of(
                    "username", memberName,
                    "nickname", "只读成员",
                    "password", "Member@12345",
                    "roleIds", List.of(memberId)), fixture.token()).getStatusCode()).isEqualTo(HttpStatus.OK);

            String token = TestAuthClient.login(restTemplate, memberName, "Member@12345", fixture.tenantCode());
            Map<String, Object> me = dataOf(get("/api/auth/me", token));
            assertThat(listOf(me, "roles")).contains("tenant_member");
            assertThat(listOf(me, "permissions")).contains("system:user:list").doesNotContain("system:user:add");

            // 看得到本租户用户列表
            Map<String, Object> page = dataOf(get("/api/system/users?pageNum=1&pageSize=50", token));
            assertThat(rowsOf(page)).extracting(row -> row.get("username"))
                    .contains(fixture.adminUsername(), memberName);
            // 路由里页面可见
            assertThat(routePaths(get("/api/auth/routes", token))).contains("/system/user");

            // 写操作一律 403
            assertThat(post("/api/system/users", Map.of("username", memberName + "x", "nickname", "越权"), token)
                    .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(delete("/api/system/users/" + fixture.adminUserId(), token).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            // 平台接口同样进不去
            assertThat(getResponse("/api/platform/tenants?pageNum=1&pageSize=10", token).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        } finally {
            purge(fixture.tenantId());
        }
    }

    @Test
    void 内置角色不可改删且补齐能补出只读角色() {
        Fixture fixture = registerTenant("内置角色幂等");
        try {
            long memberId = roleId(fixture.tenantId(), "tenant_member");
            long adminRoleId = roleId(fixture.tenantId(), "tenant_admin");

            assertThat(put("/api/system/roles/" + memberId, Map.of("name", "改名"), fixture.token()).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(delete("/api/system/roles/" + memberId, fixture.token()).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(delete("/api/system/roles/" + adminRoleId, fixture.token()).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);

            String admin = platformToken();
            // 只读角色是随本次改动才加上的：之前开通的存量租户没有它，靠「补齐资产」补出来
            jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM sys_role WHERE id = ?", memberId);
            assertThat(roleCount(fixture.tenantId(), "tenant_member")).isZero();

            Map<String, Object> backfilled = dataOf(post("/api/platform/tenants/" + fixture.tenantId() + "/assets/sync",
                    Map.of(), admin).getBody());
            assertThat(((Number) backfilled.get("memberRoleId")).longValue()).isPositive();
            long memberAfter = roleId(fixture.tenantId(), "tenant_member");
            assertThat(grantedButtons(memberAfter)).isZero();
            assertThat(grantedPages(memberAfter)).isEqualTo(tenantPages(fixture.tenantId()));

            long menusBefore = count("SELECT COUNT(*) FROM sys_menu WHERE tenant_id = ?", fixture.tenantId());
            long memberMenusBefore = grantedPages(memberAfter);
            for (int i = 0; i < 2; i++) {
                Map<String, Object> synced = dataOf(post("/api/platform/tenants/" + fixture.tenantId() + "/assets/sync",
                        Map.of(), admin).getBody());
                assertThat(synced).containsEntry("menuAdded", 0);
            }
            // 重复补齐不会多出第二个成员角色，也不会把菜单或授权翻倍
            assertThat(count("SELECT COUNT(*) FROM sys_menu WHERE tenant_id = ?", fixture.tenantId())).isEqualTo(menusBefore);
            assertThat(roleCount(fixture.tenantId(), "tenant_member")).isEqualTo(1L);
            assertThat(grantedPages(roleId(fixture.tenantId(), "tenant_member"))).isEqualTo(memberMenusBefore);
        } finally {
            purge(fixture.tenantId());
        }
    }

    @Test
    void 内置角色是租户私有物料超管要切进去才管得到() {
        Fixture fixture = registerTenant("超管看角色");
        try {
            String admin = platformToken();
            // 平台租户里既没有 tenant_admin 也没有 tenant_member：角色表按会话租户注入条件，各租户一份
            assertThat(roleCodesFromApi(admin)).doesNotContain("tenant_admin", "tenant_member");

            // 要管某个租户的角色，走租户管理的「进入该租户」，切进去之后视图就是那个租户的
            String inTenant = switchTenant(admin, fixture.tenantId());
            assertThat(roleCodesFromApi(inTenant)).contains("tenant_admin", "tenant_member");

            long member = roleId(fixture.tenantId(), "tenant_member");
            String memberName = fixture.adminUsername() + "_sw";
            assertThat(post("/api/system/users", Map.of(
                    "username", memberName,
                    "nickname", "切进去建的用户",
                    "password", "Switch@12345",
                    "roleIds", List.of(member)), inTenant).getStatusCode()).isEqualTo(HttpStatus.OK);

            // 但仍不能拿着别租户的角色码在平台租户里给人授权（既有隔离口径）
            String backHome = switchTenant(inTenant, null);
            assertThat(post("/api/system/users", Map.of(
                    "username", memberName + "_x",
                    "nickname", "跨租户授权",
                    "roleIds", List.of(member)), backHome).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        } finally {
            purge(fixture.tenantId());
        }
    }

    /** 注册一个租户，返回它的编码、管理员账号与登录令牌 */
    private Fixture registerTenant(String tenantName) {
        String suffix = String.valueOf(System.nanoTime() % 1_000_000);
        String tenantCode = "it" + suffix;
        String username = "it_caliber_" + suffix;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantCode", tenantCode);
        body.put("tenantName", tenantName);
        body.put("adminUsername", username);
        body.put("password", "Caliber@12345");
        body.put("captcha", "");
        body.put("captchaUuid", "");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/auth/register", HttpMethod.POST, new HttpEntity<>(body, headers), MAP_TYPE);
        assertThat(response.getStatusCode())
                .as("注册租户 %s 应成功，实际响应 %s", tenantCode, response.getBody())
                .isEqualTo(HttpStatus.OK);
        Map<String, Object> data = dataOf(response.getBody());
        long tenantId = ((Number) data.get("tenantId")).longValue();
        long adminUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE tenant_id = ? AND username = ?", Long.class, tenantId, username);
        return new Fixture(tenantCode, username, tenantId, adminUserId, String.valueOf(data.get("token")));
    }

    private record Fixture(String tenantCode, String adminUsername, long tenantId, long adminUserId, String token) {
    }

    private void purge(long tenantId) {
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM sys_role WHERE tenant_id = ?)", tenantId);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE tenant_id = ?)", tenantId);
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM sys_role WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM sys_menu WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM lc_tenant_config WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM sys_login_log WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM lc_tenant WHERE id = ?", tenantId);
    }

    private List<String> roleCodes(long tenantId) {
        return jdbcTemplate.queryForList("SELECT role_code FROM sys_role WHERE tenant_id = ? AND deleted = 0",
                String.class, tenantId);
    }

    private List<Integer> builtinFlags(long tenantId) {
        return jdbcTemplate.queryForList("SELECT builtin FROM sys_role WHERE tenant_id = ? AND deleted = 0",
                Integer.class, tenantId);
    }

    private long roleId(long tenantId, String code) {
        return jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE tenant_id = ? AND role_code = ? AND deleted = 0",
                Long.class, tenantId, code);
    }

    private long grantedButtons(long roleId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_role_menu rm
                JOIN sys_menu m ON m.id = rm.menu_id
                WHERE rm.role_id = ? AND m.menu_type = 'btn'
                """, Long.class, roleId);
    }

    /** 页面与目录节点（menu_type <> 'btn'）：即「能看」的那一半 */
    private long grantedPages(long roleId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_role_menu rm
                JOIN sys_menu m ON m.id = rm.menu_id
                WHERE rm.role_id = ? AND m.menu_type <> 'btn'
                """, Long.class, roleId);
    }

    private long tenantPages(long tenantId) {
        return count("SELECT COUNT(*) FROM sys_menu WHERE tenant_id = ? AND menu_type <> 'btn'", tenantId);
    }

    private long roleCount(long tenantId, String code) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_role WHERE tenant_id = ? AND role_code = ? AND deleted = 0",
                Long.class, tenantId, code);
    }

    private long firstOwnMenuId(long tenantId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE tenant_id = ? AND menu_type = 'menu' ORDER BY id LIMIT 1",
                Long.class, tenantId);
    }

    private long count(String sql, Object arg) {
        return jdbcTemplate.queryForObject(sql, Long.class, arg);
    }

    private String platformToken() {
        return TestAuthClient.login(restTemplate, "admin", "admin123", "platform");
    }

    /** 角色列表按会话租户注入条件，这里读的是「当前这个令牌看得见哪些角色」 */
    private List<String> roleCodesFromApi(String token) {
        return rowsOf(dataOf(get("/api/system/roles?pageNum=1&pageSize=100", token))).stream()
                .map(row -> String.valueOf(row.get("code")))
                .toList();
    }

    /** 切换会话租户：重签一张令牌，旧令牌当场失效（见 AuthService.switchTenant） */
    private String switchTenant(String token, Long targetTenantId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", targetTenantId);
        return String.valueOf(dataOf(post("/api/auth/switch-tenant", body, token).getBody()).get("token"));
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

    private ResponseEntity<Map<String, Object>> getResponse(String path, String token) {
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(bearer(token)), MAP_TYPE);
    }

    private Map<String, Object> get(String path, String token) {
        ResponseEntity<Map<String, Object>> response = getResponse(path, token);
        assertThat(response.getStatusCode())
                .as("GET %s 应成功，实际响应 %s", path, response.getBody())
                .isEqualTo(HttpStatus.OK);
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
    private List<String> routePaths(Map<String, Object> body) {
        Object data = body.get("data");
        List<Map<String, Object>> routes = data instanceof List<?> list
                ? (List<Map<String, Object>>) list
                : (List<Map<String, Object>>) ((Map<String, Object>) data).get("routes");
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
