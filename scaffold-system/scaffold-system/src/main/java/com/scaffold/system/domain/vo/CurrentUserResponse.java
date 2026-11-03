package com.scaffold.system.domain.vo;

import java.util.List;

/**
 * 当前登录主体信息。
 *
 * @param tenantId 当前会话租户 ID（平台管理员切换租户后为目标租户）
 * @param tenantCode 当前会话租户编码（lc_tenant.tenant_code）
 * @param tenantName 当前会话租户名称
 * @param homeTenantId 登录账号所属租户 ID；与 tenantId 不等说明正处于切换状态
 */
public record CurrentUserResponse(
        Long userId,
        String username,
        String nickname,
        Long deptId,
        String deptName,
        Long tenantId,
        String tenantCode,
        String tenantName,
        Long homeTenantId,
        List<String> roles,
        List<String> permissions
) {
}
