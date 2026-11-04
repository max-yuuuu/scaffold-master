package com.scaffold.framework.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 平台/租户权限边界的规则本身：谁算超管、哪些名字是保留的。接口层的行为由
 * {@code TenantRoleCaliberApiTest} 用真实上下文验收。
 */
class PlatformBoundaryTest {

    private static final long PLATFORM = 1L;
    private static final long TENANT = 7L;

    @ParameterizedTest
    @CsvSource({
            // 当前租户, 角色码, 是否拒绝（true = 命中保留码）
            "1, super_admin, false",
            "7, super_admin, true",
            "1, tenant_admin, true",
            "7, tenant_admin, true",
            "7, tenant_member, true",
            "7, biz_admin, false",
            "7, SYS_ADMIN, false",
    })
    void 保留角色码只挡该挡的名字(long tenantId, String roleCode, boolean rejected) {
        String violation = PlatformBoundary.roleCodeViolation(tenantId, roleCode);
        if (rejected) {
            assertThat(violation).as("角色码 %s 在租户 %d 应被拒绝", roleCode, tenantId).isNotNull().contains("保留");
        } else {
            assertThat(violation).as("角色码 %s 在租户 %d 不应被保留码校验挡住", roleCode, tenantId).isNull();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/platform", "/platform/tenant", " /platform/tenant", "/platform/tenant/detail"})
    void 租户菜单不能挂到平台路径下(String path) {
        assertThat(PlatformBoundary.menuViolation(TENANT, path, "lowcode:page:list")).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"platform:tenant:list", "platform:config:edit", " platform:tenant:add"})
    void 租户菜单不能占用平台权限码(String permission) {
        assertThat(PlatformBoundary.menuViolation(TENANT, "/lowcode/page", permission)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/lowcode/page", "/platforms/keep", "/platformx", "", "platformx:page:list", "lowcode:platform:list"})
    void 业务菜单不该被边界校验误伤(String value) {
        assertThat(PlatformBoundary.menuViolation(TENANT, value, value)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 路径与权限码留空时按另一项判断(String value) {
        assertThat(PlatformBoundary.menuViolation(TENANT, value, "platform:tenant:list")).isNotNull();
        assertThat(PlatformBoundary.menuViolation(TENANT, "/platform/tenant", value)).isNotNull();
    }

    @Test
    void 平台租户自己不受菜单边界限制() {
        assertThat(PlatformBoundary.menuViolation(PLATFORM, "/platform/tenant", "platform:tenant:list")).isNull();
        assertThat(PlatformBoundary.menuViolation(null, "/platform/tenant", "platform:tenant:list")).isNull();
    }

    @Test
    void 超管身份必须同时满足角色码与账号归属() {
        assertThat(PlatformBoundary.isSuperAdmin(PLATFORM, List.of("super_admin"))).isTrue();
        assertThat(PlatformBoundary.isSuperAdmin(null, List.of("super_admin"))).isTrue();
        assertThat(PlatformBoundary.isSuperAdmin(TENANT, List.of("super_admin"))).isFalse();
        assertThat(PlatformBoundary.isSuperAdmin(PLATFORM, List.of("tenant_admin"))).isFalse();
        assertThat(PlatformBoundary.isSuperAdmin(PLATFORM, List.of())).isFalse();
        assertThat(PlatformBoundary.isSuperAdmin(PLATFORM, null)).isFalse();
    }

    @Test
    void 租户上下文缺失按平台租户处理() {
        // 与 AuthService 的账号归属口径保持一致：历史数据里 tenant_id 可能为空，默认归平台
        assertThat(PlatformBoundary.isPlatformTenant(null)).isTrue();
        assertThat(PlatformBoundary.isPlatformTenant(PLATFORM)).isTrue();
        assertThat(PlatformBoundary.isPlatformTenant(TENANT)).isFalse();
    }
}
