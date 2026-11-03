package com.scaffold.framework.security;

public interface PermissionResolver {

    /**
     * 判断登录主体是否拥有权限码。按 {@link LoginUser#userId()} 而不是账号解析：
     * 多租户下 username 只在租户内唯一，同名账号跨租户是常态。
     */
    boolean hasPermission(LoginUser user, String permission);
}
