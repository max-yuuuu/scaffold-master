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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 租户隔离验收（PR 2 / P0-B）。依赖 sql/verify/V9__dual_tenant_demo.sql 的双租户数据：
 * 租户 1 = platform（admin），租户 2 = acme（acmeadmin）。
 */
@SpringBootTest(
        classes = ScaffoldAdminApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class TenantIsolationApiTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
    };

    private static final String PLATFORM_TENANT = "platform";
    private static final String ACME_TENANT = "acme";

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void 每个租户的令牌只能查到本租户的账号和参数() {
        assertThat(usernames(platformToken())).contains("admin", "chensy").doesNotContain("acmeadmin");
        assertThat(usernames(acmeToken())).contains("acmeadmin").doesNotContain("admin", "chensy");

        // 字典与参数同样按租户隔离：acme_biz_line 只在租户 2 可见
        assertThat(dictTypes(platformToken())).contains("sys_user_sex").doesNotContain("acme_biz_line");
        assertThat(dictTypes(acmeToken())).contains("acme_biz_line");
        // 内置参数（sys.user.initPassword 等）会随「补齐租户资产」复制到每个租户，
        // 所以跨租户判据只能用某个租户独有的键，不能拿内置键的有无当证据
        assertThat(configKeys(platformToken())).contains("sys.user.initPassword").doesNotContain("acme.contact.email");
        assertThat(configKeys(acmeToken())).contains("acme.contact.email", "sys.user.initPassword").doesNotContain("it.probe_key");
    }

    @Test
    void 平台管理员切换租户后看到目标租户数据且权限身份不变() {
        String adminToken = platformToken();
        long acmeTenantId = tenantIdByCode(adminToken, ACME_TENANT);

        String switched = switchTenant(adminToken, acmeTenantId);
        Map<String, Object> me = dataOf(get("/api/auth/me", switched));
        assertThat(listOf(me, "roles")).contains("super_admin");
        // 切换后只拿到目标租户的能力集：业务权限还在，平台管理菜单不随身份带过来
        assertThat(listOf(me, "permissions")).contains("system:user:list").doesNotContain("platform:tenant:list");
        assertThat(me).containsEntry("tenantCode", ACME_TENANT);
        assertThat(((Number) me.get("tenantId")).longValue()).isEqualTo(acmeTenantId);
        assertThat(((Number) me.get("homeTenantId")).longValue()).isNotEqualTo(acmeTenantId);

        assertThat(usernames(switched)).contains("acmeadmin").doesNotContain("admin", "chensy");

        String backHome = switchTenant(switched, null);
        assertThat(usernames(backHome)).contains("admin").doesNotContain("acmeadmin");
    }

    @Test
    void 普通租户管理员不能切换租户也不能进入平台管理接口() {
        String acme = acmeToken();

        ResponseEntity<Map<String, Object>> denied = restTemplate.exchange(
                "/api/auth/switch-tenant",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("tenantId", 1), bearer(acme)),
                MAP_TYPE
        );
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(denied.getBody()).containsEntry("code", 403);
        assertThat(String.valueOf(denied.getBody().get("message"))).contains("只有平台管理员");

        ResponseEntity<Map<String, Object>> tenants = restTemplate.exchange(
                "/api/platform/tenants", HttpMethod.GET, new HttpEntity<>(bearer(acme)), MAP_TYPE);
        assertThat(tenants.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(tenants.getBody()).containsEntry("code", 403);
    }

    @Test
    void 切换租户只能去启用中的租户() {
        String admin = platformToken();
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/auth/switch-tenant",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("tenantId", 999999), bearer(admin)),
                MAP_TYPE
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(response.getBody().get("message"))).contains("目标租户不存在或已停用");
    }

    @Test
    void 停用租户后该租户账号无法登录并可在启用后恢复() {
        String admin = platformToken();
        long acmeTenantId = tenantIdByCode(admin, ACME_TENANT);
        try {
            assertThat(put("/api/platform/tenants/" + acmeTenantId + "/status", Map.of("status", 0), admin).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            ResponseEntity<Map<String, Object>> rejected = TestAuthClient.loginResponse(restTemplate, "acmeadmin", "admin123", ACME_TENANT);
            assertThat(rejected.getBody()).containsEntry("message", "所属租户已停用");
        } finally {
            put("/api/platform/tenants/" + acmeTenantId + "/status", Map.of("status", 1), admin);
        }
        assertThat(TestAuthClient.login(restTemplate, "acmeadmin", "admin123", ACME_TENANT)).isNotBlank();
    }

    @Test
    void 租户管理可以新增与编辑平台表租户且平台租户不可停用() {
        String admin = platformToken();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantCode", "it_probe");
        body.put("tenantName", "集成测试租户");
        body.put("contactName", "机器人");
        body.put("maxProjects", 2);
        body.put("status", 1);
        body.put("remark", "PR 2 集成测试，长期保留为停用态");

        ResponseEntity<Map<String, Object>> create = restTemplate.exchange(
                "/api/platform/tenants", HttpMethod.POST, new HttpEntity<>(body, bearer(admin)), MAP_TYPE);
        // 重复执行时编码已存在，改走编辑分支保持用例幂等
        long tenantId;
        if (create.getBody() != null && create.getBody().get("code").equals(200)) {
            tenantId = ((Number) dataOf(create.getBody()).get("id")).longValue();
        } else {
            assertThat(String.valueOf(create.getBody().get("message"))).contains("租户编码已存在");
            tenantId = tenantIdByCode(admin, "it_probe");
        }
        assertThat(put("/api/platform/tenants/" + tenantId, Map.of("tenantName", "集成测试租户(已编辑)"), admin).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(tenantNames(admin)).contains("集成测试租户(已编辑)");

        ResponseEntity<Map<String, Object>> stopPlatform = put(
                "/api/platform/tenants/" + tenantIdByCode(admin, PLATFORM_TENANT) + "/status", Map.of("status", 0), admin);
        assertThat(String.valueOf(stopPlatform.getBody().get("message"))).contains("平台租户不能停用");

        // 开通新租户时写入的内置参数属于那个租户，当前会话看不到
        // 上次运行把该租户置为停用了，先启用才能走切换流程
        put("/api/platform/tenants/" + tenantId + "/status", Map.of("status", 1), admin);
        String probeToken = switchTenant(admin, tenantId);
        assertThat(tenantConfigKeys(probeToken)).contains("project.gen_branch", "project.main_branch");
        String homeToken = switchTenant(probeToken, null);
        // 测试租户长期保留为停用态，避免验收库里堆可用租户
        put("/api/platform/tenants/" + tenantId + "/status", Map.of("status", 0), homeToken);
    }

    @Test
    void 跨租户的角色与菜单授权被拦截() {
        String admin = platformToken();
        long foreignRoleId = firstRoleId("/api/system/roles?pageNum=1&pageSize=10&keyword=tenant_admin", acmeToken());

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/system/users/1",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("nickname", "周明远", "roleIds", List.of(foreignRoleId)), bearer(admin)),
                MAP_TYPE
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(response.getBody().get("message"))).contains("不属于当前租户");

        // 回滚生效：admin 的角色仍是租户 1 的 super_admin
        assertThat(usernames(admin)).contains("admin");
    }

    @Test
    void 租户参数只在当前会话租户内读写() {
        String admin = platformToken();
        long acmeTenantId = tenantIdByCode(admin, ACME_TENANT);

        // 平台管理员切进租户 2 写参数；不切换时读不到租户 2 的数据
        String inAcme = switchTenant(admin, acmeTenantId);
        assertThat(tenantConfigKeys(inAcme)).doesNotContain("it.probe_key");
        assertThat(put("/api/platform/tenant-configs", Map.of(
                "configKey", "it.probe_key",
                "configValue", "acme-only",
                "remark", "租户 2 专属"
        ), inAcme).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tenantConfigKeys(inAcme)).contains("it.probe_key");

        String backHome = switchTenant(inAcme, null);
        assertThat(tenantConfigKeys(backHome)).doesNotContain("it.probe_key");

        String inAcmeAgain = switchTenant(backHome, acmeTenantId);
        long probeId = rowIdByConfigKey("/api/platform/tenant-configs?pageNum=1&pageSize=100", inAcmeAgain, "it.probe_key");
        assertThat(delete("/api/platform/tenant-configs/" + probeId, inAcmeAgain).getStatusCode()).isEqualTo(HttpStatus.OK);
        switchTenant(inAcmeAgain, null);
    }

    @Test
    void 关键词查询也不能绕过租户过滤() {
        // project.main_branch 两个租户各有一条：带 keyword 的 OR 组若不整体加括号，
        // 拦截器补的 "AND tenant_id = ?" 只会绑到最后一个 OR 分支上，另一个租户的行会漏出来
        String admin = platformToken();
        assertThat(tenantConfigKeys(admin, "main_branch")).hasSize(1);
        assertThat(tenantConfigKeys(admin, "project.")).hasSize(2);

        long acmeTenantId = tenantIdByCode(admin, ACME_TENANT);
        String inAcme = switchTenant(admin, acmeTenantId);
        assertThat(tenantConfigKeys(inAcme, "main_branch")).hasSize(1);
        assertThat(tenantConfigKeys(inAcme, "acme")).isEmpty();
    }

    @Test
    void 账号跨租户重名时必须带租户编码登录() {
        // 租户 1 与租户 2 都有 admin 时会提示填写租户编码；验收库当前只有租户 1 有 admin，
        // 这里改用显式编码登录两个租户，确认编码路径可用且能定位到各自账号
        assertThat(TestAuthClient.login(restTemplate, "admin", "admin123", PLATFORM_TENANT)).isNotBlank();
        assertThat(TestAuthClient.login(restTemplate, "acmeadmin", "admin123", ACME_TENANT)).isNotBlank();
        ResponseEntity<Map<String, Object>> wrongTenant = TestAuthClient.loginResponse(restTemplate, "admin", "admin123", ACME_TENANT);
        assertThat(wrongTenant.getBody()).containsEntry("message", "账号或密码错误");
    }

    private String platformToken() {
        return TestAuthClient.login(restTemplate, "admin", "admin123", PLATFORM_TENANT);
    }

    private String acmeToken() {
        return TestAuthClient.login(restTemplate, "acmeadmin", "admin123", ACME_TENANT);
    }

    private String switchTenant(String token, Long tenantId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", tenantId);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/auth/switch-tenant", HttpMethod.POST, new HttpEntity<>(body, bearer(token)), MAP_TYPE);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return String.valueOf(dataOf(response.getBody()).get("token"));
    }

    private List<String> usernames(String token) {
        Map<String, Object> page = dataOf(get("/api/system/users?pageNum=1&pageSize=100", token));
        return rowsOf(page).stream().map(row -> String.valueOf(row.get("username"))).toList();
    }

    private List<String> dictTypes(String token) {
        Map<String, Object> page = dataOf(get("/api/system/dict/types?pageNum=1&pageSize=100", token));
        return rowsOf(page).stream().map(row -> String.valueOf(row.get("dictType"))).toList();
    }

    private List<String> configKeys(String token) {
        return rowsOf(dataOf(get("/api/system/configs?pageNum=1&pageSize=100", token))).stream()
                .map(row -> String.valueOf(row.get("key")))
                .toList();
    }

    private List<String> tenantConfigKeys(String token) {
        return tenantConfigKeys(token, null);
    }

    private List<String> tenantConfigKeys(String token, String keyword) {
        String path = keyword == null
                ? "/api/platform/tenant-configs?pageNum=1&pageSize=100"
                : "/api/platform/tenant-configs?pageNum=1&pageSize=100&keyword=" + keyword;
        return rowsOf(dataOf(get(path, token))).stream()
                .map(row -> String.valueOf(row.get("configKey")))
                .toList();
    }

    private List<String> tenantNames(String token) {
        Map<String, Object> page = dataOf(get("/api/platform/tenants?pageNum=1&pageSize=100", token));
        return rowsOf(page).stream().map(row -> String.valueOf(row.get("tenantName"))).toList();
    }

    private long tenantIdByCode(String token, String tenantCode) {
        Map<String, Object> page = dataOf(get("/api/platform/tenants?pageNum=1&pageSize=100&keyword=" + tenantCode, token));
        return rowsOf(page).stream()
                .filter(row -> tenantCode.equals(String.valueOf(row.get("tenantCode"))))
                .map(row -> ((Number) row.get("id")).longValue())
                .findFirst()
                .orElseThrow(() -> new AssertionError("验收库里缺少租户编码 " + tenantCode + "，请先执行 sql/verify/V9__dual_tenant_demo.sql"));
    }

    private long firstRoleId(String path, String token) {
        List<Map<String, Object>> rows = rowsOf(dataOf(get(path, token)));
        assertThat(rows).isNotEmpty();
        return ((Number) rows.get(0).get("id")).longValue();
    }

    private long rowIdByConfigKey(String path, String token, String configKey) {
        return rowsOf(dataOf(get(path, token))).stream()
                .filter(row -> configKey.equals(String.valueOf(row.get("configKey"))))
                .map(row -> ((Number) row.get("id")).longValue())
                .findFirst()
                .orElseThrow(() -> new AssertionError("当前租户缺少参数 " + configKey));
    }

    /** 200 且业务码为 200 的 GET，返回响应信封 */
    private Map<String, Object> get(String path, String token) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                path, HttpMethod.GET, new HttpEntity<>(bearer(token)), MAP_TYPE);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
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
    private List<String> listOf(Map<String, Object> data, String key) {
        return (List<String>) data.get(key);
    }
}
