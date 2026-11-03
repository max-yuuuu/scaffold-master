package com.scaffold.system.domain.dto;

/**
 * 平台管理员切换租户的请求（仅 super_admin 可用）。
 *
 * <p>tenantId 目标租户 ID（lc_tenant.id）；为空表示退出目标租户、回到自己的平台租户。
 */
public class SwitchTenantRequest {

    private Long tenantId;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
}
