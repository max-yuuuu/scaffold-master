package com.scaffold.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.scaffold.system.domain.AuthUserRow;
import com.scaffold.system.domain.MenuRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface AuthMapper {

    /**
     * 按租户编码定位租户 ID；登录时用于把同名账号定位到唯一租户。
     * 不过滤 status：租户停用时仍要走到「所属租户已停用」的提示，而不是含糊的「账号或密码错误」。
     * lc_tenant 是平台表，本身不带 tenant_id，不参与租户注入。
     */
    @Select("""
            SELECT id
            FROM lc_tenant
            WHERE tenant_code = #{tenantCode} AND deleted = 0
            """)
    Long selectTenantIdByCode(@Param("tenantCode") String tenantCode);

    /** 当前会话租户的展示信息（编码与名称），供 /auth/me 返回给顶栏 */
    @Select("""
            SELECT tenant_code AS tenantCode, tenant_name AS tenantName
            FROM lc_tenant
            WHERE id = #{tenantId} AND deleted = 0
            """)
    Map<String, Object> selectTenantBrief(@Param("tenantId") Long tenantId);

    /** 判断租户是否处于启用状态；登录与切换租户时用于拒绝停用租户 */
    @Select("""
            SELECT COUNT(*)
            FROM lc_tenant
            WHERE id = #{tenantId} AND status = 1 AND deleted = 0
            """)
    int countActiveTenant(@Param("tenantId") Long tenantId);

    /** 切换租户的审计日志里带上租户编码，只查未删除的租户 */
    @Select("SELECT tenant_code FROM lc_tenant WHERE id = #{tenantId} AND deleted = 0")
    String selectTenantCodeById(@Param("tenantId") Long tenantId);

    /**
     * 未填租户编码时的候选账号：调用方必须处理 size &gt; 1 的歧义，不能取第一条。
     * 身份解析按账号全局检索，故跳过租户注入（登录时尚无租户上下文，切换租户后也要能解析到自己）。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT u.id, u.tenant_id, u.username, u.password, u.nickname, u.status, u.dept_id, d.dept_name
            FROM sys_user u
            LEFT JOIN sys_dept d ON d.id = u.dept_id AND d.tenant_id = u.tenant_id
            WHERE u.username = #{username} AND u.deleted = 0
            ORDER BY u.tenant_id
            """)
    List<AuthUserRow> selectUsersByUsername(@Param("username") String username);

    /** 填写租户编码时的精确定位 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT u.id, u.tenant_id, u.username, u.password, u.nickname, u.status, u.dept_id, d.dept_name
            FROM sys_user u
            LEFT JOIN sys_dept d ON d.id = u.dept_id AND d.tenant_id = u.tenant_id
            WHERE u.username = #{username} AND u.tenant_id = #{tenantId} AND u.deleted = 0
            """)
    AuthUserRow selectUserByUsernameAndTenant(@Param("username") String username, @Param("tenantId") Long tenantId);

    /** 按全局主键取账号：请求级身份解析走这条路，避免依赖只在租户内唯一的账号 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT u.id, u.tenant_id, u.username, u.password, u.nickname, u.status, u.dept_id, d.dept_name
            FROM sys_user u
            LEFT JOIN sys_dept d ON d.id = u.dept_id AND d.tenant_id = u.tenant_id
            WHERE u.id = #{userId} AND u.deleted = 0
            """)
    AuthUserRow selectUserById(@Param("userId") Long userId);

    /**
     * 角色编码按用户主键解析，跨租户跳过注入：平台管理员切换到目标租户时仍要拿到自己的 super_admin 身份。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT r.role_code
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId} AND r.status = 1 AND r.deleted = 0
            ORDER BY r.sort, r.id
            """)
    List<String> selectRoleCodes(@Param("userId") Long userId);

    /** 权限码同样按用户主键解析，与当前会话租户无关 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT DISTINCT m.permission
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id AND r.status = 1 AND r.deleted = 0
            JOIN sys_role_menu rm ON rm.role_id = ur.role_id
            JOIN sys_menu m ON m.id = rm.menu_id
            WHERE ur.user_id = #{userId}
              AND m.status = 1
              AND m.permission IS NOT NULL
              AND m.permission <> ''
            ORDER BY m.permission
            """)
    List<String> selectPermissions(@Param("userId") Long userId);

    /** 普通用户的可见菜单：按角色授权取，菜单集仍受当前会话租户约束（走租户注入） */
    @Select("""
            SELECT DISTINCT m.id, m.parent_id, m.menu_name, m.menu_type, m.icon, m.path,
                   m.component, m.permission, m.sort
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id AND r.status = 1 AND r.deleted = 0
            JOIN sys_role_menu rm ON rm.role_id = ur.role_id
            JOIN sys_menu m ON m.id = rm.menu_id
            WHERE ur.user_id = #{userId}
              AND m.status = 1
              AND m.visible = 1
              AND m.menu_type IN ('dir', 'menu')
            ORDER BY m.parent_id, m.sort, m.id
            """)
    List<MenuRow> selectRouteMenus(@Param("userId") Long userId);

    /** 超级管理员：当前租户内全部启用的权限码 */
    @Select("""
            SELECT DISTINCT permission
            FROM sys_menu
            WHERE status = 1 AND permission IS NOT NULL AND permission <> ''
            ORDER BY permission
            """)
    List<String> selectAllPermissions();

    /** 超级管理员：当前租户内全部启用且可见的目录与菜单 */
    @Select("""
            SELECT id, parent_id, menu_name, menu_type, icon, path, component, permission, sort
            FROM sys_menu
            WHERE status = 1 AND visible = 1 AND menu_type IN ('dir', 'menu')
            ORDER BY parent_id, sort, id
            """)
    List<MenuRow> selectAllRouteMenus();

    /**
     * 写登录日志。tenant_id 表示事件归属租户；账号不存在时为 1（"未归属"占位），
     * 因为登录表单不携带租户选择，无法判断该用户名应该在哪个租户下失败。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Insert("""
            INSERT INTO sys_login_log (tenant_id, username, status, msg, login_time)
            VALUES (#{tenantId}, #{username}, #{status}, #{msg}, NOW())
            """)
    void insertLoginLog(@Param("tenantId") Long tenantId,
                        @Param("username") String username,
                        @Param("status") int status,
                        @Param("msg") String msg);

    /** 登录成功后记录最近登录时间（sys_user.last_login_time），用户列表与看板据此展示 */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE sys_user SET last_login_time = NOW() WHERE id = #{userId} AND deleted = 0")
    void updateLastLoginTime(@Param("userId") Long userId);

    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE sys_user
            SET password = #{password}, update_by = #{username}, update_time = NOW()
            WHERE id = #{userId} AND deleted = 0
            """)
    int updatePassword(@Param("userId") Long userId, @Param("username") String username, @Param("password") String password);
}
