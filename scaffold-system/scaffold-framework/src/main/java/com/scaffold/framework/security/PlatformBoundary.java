package com.scaffold.framework.security;

import com.scaffold.common.exception.BusinessException;
import com.scaffold.common.tenant.TenantContext;

import java.util.Collection;
import java.util.Set;

/**
 * 平台与租户之间的权限边界口径。
 *
 * <p>多租户下 <b>角色码与权限码都不是全局可信的</b>：<code>sys_role</code> 的角色码唯一性只在本租户内生效，
 * 菜单的权限码由「菜单管理」自由填写，两者都能被租户管理员自己造出来。所以凡是「凭一个名字就放行」的地方
 * 都必须先确认这个名字出自哪个租户 —— 这里就是这份判断的唯一出处。
 */
public final class PlatformBoundary {

    /** 平台超级管理员角色码，只有归属平台租户的账号持有才生效 */
    public static final String SUPER_ADMIN_ROLE_CODE = "super_admin";
    /** 租户内置管理员角色码，由 {@code TenantProvisionService} 开通时创建 */
    public static final String TENANT_ADMIN_ROLE_CODE = "tenant_admin";
    /** 租户内置只读成员角色码，同上 */
    public static final String TENANT_MEMBER_ROLE_CODE = "tenant_member";

    /** 平台管理段的菜单与权限码前缀，与 {@code LcProvisionMapper} 复制模板时的排除条件同一口径 */
    public static final String PLATFORM_PATH_PREFIX = "/platform";
    public static final String PLATFORM_PERMISSION_PREFIX = "platform:";

    private static final Set<String> PROVISIONED_ROLE_CODES =
            Set.of(TENANT_ADMIN_ROLE_CODE, TENANT_MEMBER_ROLE_CODE);

    private PlatformBoundary() {
    }

    /** 是否平台租户；上下文缺失（匿名请求）时按平台租户处理，与 {@code AuthService} 的归属口径一致 */
    public static boolean isPlatformTenant(Long tenantId) {
        return tenantId == null || tenantId == TenantContext.PLATFORM_TENANT_ID;
    }

    /**
     * 是否真正拥有超管身份：角色码是 super_admin <b>且账号归属平台租户</b>。
     * 租户内自建一个同名角色不算数。
     */
    public static boolean isSuperAdmin(Long accountTenantId, Collection<String> roleCodes) {
        return roleCodes != null
                && roleCodes.contains(SUPER_ADMIN_ROLE_CODE)
                && isPlatformTenant(accountTenantId);
    }

    /**
     * 手工创建角色时的保留码校验。
     *
     * @param currentTenantId 当前会话租户
     * @param roleCode        待创建的角色码
     * @return 拒绝原因；通过校验时返回 null
     */
    public static String roleCodeViolation(Long currentTenantId, String roleCode) {
        if (PROVISIONED_ROLE_CODES.contains(roleCode)) {
            return "角色编码 " + roleCode + " 为平台内置保留编码，不能手工创建";
        }
        if (SUPER_ADMIN_ROLE_CODE.equals(roleCode) && !isPlatformTenant(currentTenantId)) {
            return "角色编码 " + SUPER_ADMIN_ROLE_CODE + " 为平台保留编码，只有平台租户可以持有";
        }
        return null;
    }

    /** 校验失败直接抛出统一业务异常，写接口的调用点不用各自拼判断 */
    public static void checkRoleCode(Long currentTenantId, String roleCode) {
        String violation = roleCodeViolation(currentTenantId, roleCode);
        if (violation != null) {
            throw new BusinessException(violation);
        }
    }

    /**
     * 租户侧不能把菜单挂到平台管理段上，否则「给自己的角色授一条平台菜单」就等于拿到平台接口。
     *
     * @return 拒绝原因；通过校验时返回 null
     */
    public static String menuViolation(Long currentTenantId, String path, String permission) {
        if (isPlatformTenant(currentTenantId)) {
            return null;
        }
        if (isPlatformPath(path)) {
            return "路径 " + PLATFORM_PATH_PREFIX + " 属于平台管理段，租户菜单不能使用该前缀";
        }
        if (isPlatformPermission(permission)) {
            return "权限码 " + PLATFORM_PERMISSION_PREFIX + " 属于平台管理段，租户菜单不能使用该前缀";
        }
        return null;
    }

    /** 校验失败直接抛出统一业务异常，写接口的调用点不用各自拼判断 */
    public static void checkMenu(Long currentTenantId, String path, String permission) {
        String violation = menuViolation(currentTenantId, path, permission);
        if (violation != null) {
            throw new BusinessException(violation);
        }
    }

    /** 只认平台段本身：/platform 与 /platform/** 拦掉，/platforms 这类业务路径不能误伤 */
    private static boolean isPlatformPath(String path) {
        String trimmed = path == null ? "" : path.trim();
        return trimmed.equals(PLATFORM_PATH_PREFIX) || trimmed.startsWith(PLATFORM_PATH_PREFIX + "/");
    }

    private static boolean isPlatformPermission(String permission) {
        String trimmed = permission == null ? "" : permission.trim();
        return trimmed.startsWith(PLATFORM_PERMISSION_PREFIX);
    }
}
