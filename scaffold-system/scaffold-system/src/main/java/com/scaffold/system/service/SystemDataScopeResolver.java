package com.scaffold.system.service;

import com.scaffold.framework.datascope.DataScopeCriteria;
import com.scaffold.framework.datascope.DataScopeResolver;
import com.scaffold.framework.security.LoginUser;
import com.scaffold.system.mapper.SystemManagementMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SystemDataScopeResolver implements DataScopeResolver {

    private final SystemManagementMapper mapper;

    public SystemDataScopeResolver(SystemManagementMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public DataScopeCriteria resolve(LoginUser user) {
        if (user == null || user.userId() == null) {
            return DataScopeCriteria.selfOnly(null);
        }
        Map<String, Object> profile = mapper.selectUserProfileById(user.userId());
        if (profile == null || profile.get("id") == null) {
            return DataScopeCriteria.selfOnly(null);
        }
        Long userId = numberAsLong(profile.get("id"));
        Long deptId = numberAsLong(profile.get("deptId"));
        List<Map<String, Object>> roles = mapper.selectActiveRoleScopes(userId);
        if (roles.stream().anyMatch(role -> "ALL".equals(role.get("dataScope")))) {
            return DataScopeCriteria.all();
        }
        List<Long> deptIds = new ArrayList<>();
        for (Map<String, Object> role : roles) {
            String scope = String.valueOf(role.get("dataScope"));
            Long roleId = numberAsLong(role.get("id"));
            if ("DEPT".equals(scope) && deptId != null) {
                deptIds.add(deptId);
            } else if ("DEPT_AND_CHILD".equals(scope) && deptId != null) {
                deptIds.addAll(mapper.selectDeptAndChildIds(deptId));
            } else if ("CUSTOM".equals(scope) && roleId != null) {
                deptIds.addAll(mapper.selectRoleDeptIds(roleId));
            }
        }
        return DataScopeCriteria.limited(userId, deptIds);
    }

    private Long numberAsLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }
}
