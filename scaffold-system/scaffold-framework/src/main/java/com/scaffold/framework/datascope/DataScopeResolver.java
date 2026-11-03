package com.scaffold.framework.datascope;

import com.scaffold.framework.security.LoginUser;

public interface DataScopeResolver {

    /**
     * 解析登录主体的数据权限范围。按 {@link LoginUser#userId()} 而不是账号解析：
     * 多租户下 username 只在租户内唯一，平台管理员切换租户后仍需按自己的角色取范围。
     */
    DataScopeCriteria resolve(LoginUser user);
}
