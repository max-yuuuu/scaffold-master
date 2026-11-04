package com.scaffold.system.service;

import com.scaffold.framework.security.LoginUser;
import com.scaffold.framework.security.PermissionResolver;
import com.scaffold.framework.security.PlatformBoundary;
import com.scaffold.system.domain.AuthUserRow;
import com.scaffold.system.mapper.AuthMapper;
import org.springframework.stereotype.Service;

@Service
public class SystemPermissionResolver implements PermissionResolver {

    private final AuthMapper authMapper;

    public SystemPermissionResolver(AuthMapper authMapper) {
        this.authMapper = authMapper;
    }

    @Override
    public boolean hasPermission(LoginUser user, String permission) {
        if (user == null || user.userId() == null) {
            return false;
        }
        AuthUserRow account = authMapper.selectUserById(user.userId());
        if (account == null || account.getStatus() == null || account.getStatus() != 1) {
            return false;
        }
        // 角色码按账号归属判断：租户内自建的 super_admin 角色不算超管
        if (PlatformBoundary.isSuperAdmin(account.getTenantId(), authMapper.selectRoleCodes(account.getId()))) {
            return true;
        }
        return authMapper.selectPermissions(account.getId()).contains(permission);
    }
}
