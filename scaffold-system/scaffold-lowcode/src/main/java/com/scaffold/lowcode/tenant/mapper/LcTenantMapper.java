package com.scaffold.lowcode.tenant.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 租户表访问。lc_tenant 是平台表，本身不带 tenant_id；
 * 列表里的用户数子查询按 t.id 相关，必须跳过租户注入，否则只会统计出当前会话租户的人数。
 */
@Mapper
public interface LcTenantMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM lc_tenant t
            WHERE t.deleted = 0
              AND (#{keyword} IS NULL OR #{keyword} = ''
                   OR t.tenant_code LIKE CONCAT('%', #{keyword}, '%')
                   OR t.tenant_name LIKE CONCAT('%', #{keyword}, '%')
                   OR t.contact_name LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{status} IS NULL OR t.status = #{status})
            </script>
            """)
    long countTenants(@Param("keyword") String keyword, @Param("status") Integer status);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            <script>
            SELECT t.id,
                   t.tenant_code AS tenantCode,
                   t.tenant_name AS tenantName,
                   t.contact_name AS contactName,
                   t.contact_phone AS contactPhone,
                   t.max_projects AS maxProjects,
                   DATE_FORMAT(t.expire_time, '%Y-%m-%d') AS expireDate,
                   t.status,
                   (SELECT COUNT(*) FROM sys_user u WHERE u.tenant_id = t.id AND u.deleted = 0) AS userCount,
                   DATE_FORMAT(t.create_time, '%Y-%m-%d %H:%i') AS createTime,
                   t.remark
            FROM lc_tenant t
            WHERE t.deleted = 0
              AND (#{keyword} IS NULL OR #{keyword} = ''
                   OR t.tenant_code LIKE CONCAT('%', #{keyword}, '%')
                   OR t.tenant_name LIKE CONCAT('%', #{keyword}, '%')
                   OR t.contact_name LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{status} IS NULL OR t.status = #{status})
            ORDER BY t.id
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Map<String, Object>> selectTenants(
            @Param("keyword") String keyword,
            @Param("status") Integer status,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize
    );

    /** 租户编码全局唯一（lc_tenant.tenant_code 上有唯一键），登录时按它定位租户 */
    @Select("""
            SELECT COUNT(*)
            FROM lc_tenant
            WHERE tenant_code = #{tenantCode}
              AND deleted = 0
              AND (#{excludeId} IS NULL OR id <> #{excludeId})
            """)
    long countTenantCode(@Param("tenantCode") String tenantCode, @Param("excludeId") Long excludeId);

    @Select("SELECT tenant_code AS tenantCode, tenant_name AS tenantName FROM lc_tenant WHERE id = #{id} AND deleted = 0")
    Map<String, Object> selectTenantBrief(@Param("id") Long id);

    @Insert("""
            INSERT INTO lc_tenant (tenant_code, tenant_name, contact_name, contact_phone, max_projects,
                                   expire_time, status, remark, create_by, update_by)
            VALUES (#{row.tenantCode}, #{row.tenantName}, #{row.contactName}, #{row.contactPhone}, #{row.maxProjects},
                    #{row.expireDate}, #{row.status}, #{row.remark}, #{operator}, #{operator})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insertTenant(@Param("row") Map<String, Object> row, @Param("operator") String operator);

    @Update("""
            UPDATE lc_tenant
            SET tenant_name = COALESCE(#{row.tenantName}, tenant_name),
                contact_name = COALESCE(#{row.contactName}, contact_name),
                contact_phone = COALESCE(#{row.contactPhone}, contact_phone),
                max_projects = COALESCE(#{row.maxProjects}, max_projects),
                expire_time = #{row.expireDate},
                status = COALESCE(#{row.status}, status),
                remark = COALESCE(#{row.remark}, remark),
                update_by = #{operator}
            WHERE id = #{id} AND deleted = 0
            """)
    int updateTenant(@Param("id") Long id, @Param("row") Map<String, Object> row, @Param("operator") String operator);

    @Update("""
            UPDATE lc_tenant
            SET status = #{status}, update_by = #{operator}
            WHERE id = #{id} AND deleted = 0
            """)
    int updateTenantStatus(@Param("id") Long id, @Param("status") Integer status, @Param("operator") String operator);
}
