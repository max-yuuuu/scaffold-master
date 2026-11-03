package com.scaffold.system.service;

import com.scaffold.framework.security.LoginUser;
import com.scaffold.framework.security.PermissionResolver;
import com.scaffold.system.domain.AuthUserRow;
import com.scaffold.system.mapper.AuthMapper;
import org.springframework.stereotype.Service;

@Service
public class SystemPermissionResolver implements PermissionResolver {

    private static final String SUPER_ADMIN = "super_admin";

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
        if (authMapper.selectRoleCodes(account.getId()).contains(SUPER_ADMIN)) {
            return true;
        }
        return authMapper.selectPermissions(account.getId()).contains(permission);
    }
}
