package com.scaffold.lowcode.tenant.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 租户开通与资产补齐用的写入。两条链路都在「没有目标租户上下文」的情况下运行
 * （注册是匿名请求，补齐是平台管理员在自已的租户里操作），
 * 所以每条语句都显式带 tenant_id 并标 {@code @InterceptorIgnore}，不依赖拦截器注入。
 */
@Mapper
public interface LcProvisionMapper {

    /** 管理员账号要求全局唯一：sys_user 唯一键是 (tenant_id, username)，重名会让登录必须多填租户编码 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT COUNT(*) FROM sys_user WHERE username = #{username} AND deleted = 0")
    long countUsername(@Param("username") String username);

    /**
     * 平台租户的业务菜单模板：按 id 升序返回，保证父级先于子级（种子数据即按此顺序写入）。
     * 排除「平台管理」段（400 段）—— 那是平台运营自己的菜单，按语义判断而不写死 ID。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT id, parent_id AS parentId, menu_name AS menuName, menu_type AS menuType,
                   icon, path, component, permission, sort, visible, status
            FROM sys_menu
            WHERE tenant_id = #{templateTenantId}
              AND NOT (path LIKE '/platform%' OR permission LIKE 'platform:%')
            ORDER BY id
            """)
    List<Map<String, Object>> selectTemplateMenus(@Param("templateTenantId") long templateTenantId);

    /** 目标租户已有菜单，用于「补齐」时判断哪些模板菜单还没复制过来 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT id, parent_id AS parentId, menu_name AS menuName, menu_type AS menuType, path, permission
            FROM sys_menu
            WHERE tenant_id = #{tenantId}
            ORDER BY id
            """)
    List<Map<String, Object>> selectTenantMenus(@Param("tenantId") long tenantId);

    @InterceptorIgnore(tenantLine = "true")
    @Insert("""
            INSERT INTO sys_menu (tenant_id, parent_id, menu_name, menu_type, icon, path, component, permission, sort, visible, status, create_by, update_by)
            VALUES (#{row.tenantId}, #{row.parentId}, #{row.menuName}, #{row.menuType}, #{row.icon}, #{row.path},
                    #{row.component}, #{row.permission}, #{row.sort}, #{row.visible}, #{row.status}, #{operator}, #{operator})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insertMenu(@Param("row") Map<String, Object> row, @Param("operator") String operator);

    /** 内置管理员角色：data_scope 固定 ALL，新租户开箱可用 */
    @InterceptorIgnore(tenantLine = "true")
    @Insert("""
            INSERT INTO sys_role (tenant_id, role_name, role_code, data_scope, sort, status, builtin, remark, create_by, update_by)
            VALUES (#{row.tenantId}, #{row.roleName}, #{row.roleCode}, 'ALL', 1, 1, 1, #{row.remark}, #{operator}, #{operator})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insertAdminRole(@Param("row") Map<String, Object> row, @Param("operator") String operator);

    /** 目标租户的内置管理员角色 ID，没有则返回 null */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT id FROM sys_role WHERE tenant_id = #{tenantId} AND role_code = #{roleCode} AND deleted = 0 LIMIT 1")
    Long selectRoleIdByCode(@Param("tenantId") long tenantId, @Param("roleCode") String roleCode);

    @InterceptorIgnore(tenantLine = "true")
    @Insert("INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (#{roleId}, #{menuId})")
    void insertRoleMenu(@Param("roleId") Long roleId, @Param("menuId") Long menuId);

    @InterceptorIgnore(tenantLine = "true")
    @Insert("""
            INSERT INTO sys_user (tenant_id, username, password, nickname, status, create_by, update_by)
            VALUES (#{row.tenantId}, #{row.username}, #{row.password}, #{row.nickname}, 1, #{operator}, #{operator})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insertAdminUser(@Param("row") Map<String, Object> row, @Param("operator") String operator);

    @InterceptorIgnore(tenantLine = "true")
    @Insert("INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    void insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    /** 注册开通也进登录日志，平台管理员在「登录日志」里能看到租户是自己注册进来的 */
    @InterceptorIgnore(tenantLine = "true")
    @Insert("""
            INSERT INTO sys_login_log (tenant_id, username, status, msg, login_time)
            VALUES (#{tenantId}, #{username}, 1, '注册开通租户', NOW())
            """)
    void insertRegisterLog(@Param("tenantId") Long tenantId, @Param("username") String username);

    /**
     * 复制平台租户的字典类型。sys_dict_type 唯一键是 (tenant_id, dict_type)，
     * INSERT IGNORE 天然幂等，重复补齐不会产生第二份。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Insert("""
            INSERT IGNORE INTO sys_dict_type (tenant_id, dict_name, dict_type, status, remark, create_by, update_by)
            SELECT #{tenantId}, dict_name, dict_type, status, remark, #{operator}, #{operator}
            FROM sys_dict_type
            WHERE tenant_id = #{templateTenantId}
            """)
    int copyDictTypes(@Param("tenantId") long tenantId, @Param("templateTenantId") long templateTenantId, @Param("operator") String operator);

    /** sys_dict_data 没有唯一键，只能按 (dict_type, dict_value) 反查去重，否则补齐一次就多一份选项 */
    @InterceptorIgnore(tenantLine = "true")
    @Insert("""
            INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, sort, status, tone, is_default, remark, create_by, update_by)
            SELECT #{tenantId}, s.dict_type, s.dict_label, s.dict_value, s.sort, s.status, s.tone, s.is_default, s.remark, #{operator}, #{operator}
            FROM sys_dict_data s
            WHERE s.tenant_id = #{templateTenantId}
              AND NOT EXISTS (
                  SELECT 1 FROM sys_dict_data t
                  WHERE t.tenant_id = #{tenantId} AND t.dict_type = s.dict_type AND t.dict_value = s.dict_value
              )
            """)
    int copyDictData(@Param("tenantId") long tenantId, @Param("templateTenantId") long templateTenantId, @Param("operator") String operator);

    /** sys_config 唯一键是 (tenant_id, config_key)，同样幂等 */
    @InterceptorIgnore(tenantLine = "true")
    @Insert("""
            INSERT IGNORE INTO sys_config (tenant_id, config_name, config_key, config_value, config_type, builtin, remark, create_by, update_by)
            SELECT #{tenantId}, config_name, config_key, config_value, config_type, builtin, remark, #{operator}, #{operator}
            FROM sys_config
            WHERE tenant_id = #{templateTenantId}
            """)
    int copyConfigs(@Param("tenantId") long tenantId, @Param("templateTenantId") long templateTenantId, @Param("operator") String operator);
}
