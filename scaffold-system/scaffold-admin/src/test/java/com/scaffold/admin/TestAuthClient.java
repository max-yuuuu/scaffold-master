package com.scaffold.admin;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class TestAuthClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
    };

    private TestAuthClient() {
    }

    static String login(TestRestTemplate restTemplate, String username, String password) {
        return login(restTemplate, username, password, null);
    }

    /** tenantCode 租户编码（lc_tenant.tenant_code）：账号跨租户重名时必须指定 */
    static String login(TestRestTemplate restTemplate, String username, String password, String tenantCode) {
        ResponseEntity<Map<String, Object>> response = loginResponse(restTemplate, username, password, tenantCode);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = dataOf(response.getBody());
        assertThat(data).containsKeys("token", "expiresIn");
        return (String) data.get("token");
    }

    static ResponseEntity<Map<String, Object>> loginResponse(TestRestTemplate restTemplate, String username, String password) {
        return loginResponse(restTemplate, username, password, null);
    }

    static ResponseEntity<Map<String, Object>> loginResponse(TestRestTemplate restTemplate, String username, String password, String tenantCode) {
        Map<String, Object> captcha = dataOf(restTemplate.exchange(
                "/api/auth/captcha",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                MAP_TYPE
        ).getBody());

        // 验证码接口只回图片，明文答案留在 Redis；测试通过关闭验证码开关绕过人机校验
        boolean captchaEnabled = Boolean.parseBoolean(String.valueOf(captcha.get("captchaEnabled")));
        assertThat(captchaEnabled).as("测试用例应运行在 scaffold.security.captcha.enabled=false 下").isFalse();

        return restTemplate.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(loginBody(username, password, String.valueOf(captcha.getOrDefault("uuid", "")), tenantCode)),
                MAP_TYPE
        );
    }

    /** captchaUuid 验证码 UUID；验证码开关关闭时 captcha 可为空串 */
    static Map<String, Object> loginBody(String username, String password, String captchaUuid, String tenantCode) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);
        body.put("captcha", "");
        body.put("captchaUuid", captchaUuid);
        if (tenantCode != null) {
            body.put("tenantCode", tenantCode);
        }
        return body;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> dataOf(Map<String, Object> body) {
        assertThat(body).containsEntry("code", 200);
        return (Map<String, Object>) body.get("data");
    }
}
