package com.scaffold.lowcode.tenant.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 租户级参数（lc_tenant_config）访问。该表带 tenant_id，读写条件全部由租户拦截器自动注入，
 * 因此不提供 tenantId 入参：平台管理员要改某个租户的参数，先切换到该租户。
 */
@Mapper
public interface LcTenantConfigMapper {

    /**
     * 关键词命中参数键 / 名称 / 备注；tenant_id 条件由拦截器注入。
     * 注意 OR 组必须整体加括号：拦截器补的是 "原 WHERE AND tenant_id = ?"，
     * 不括起来会让命中前几个 OR 分支的行绕过租户过滤。
     */
    @Select("""
            <script>
            SELECT COUNT(*)
            FROM lc_tenant_config c
            WHERE (#{keyword} IS NULL OR #{keyword} = ''
                   OR c.config_key LIKE CONCAT('%', #{keyword}, '%')
                   OR c.config_name LIKE CONCAT('%', #{keyword}, '%')
                   OR c.remark LIKE CONCAT('%', #{keyword}, '%'))
            </script>
            """)
    long countConfigs(@Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT c.id,
                   c.config_key AS configKey,
                   c.config_value AS configValue,
                   c.config_name AS configName,
                   c.builtin,
                   c.remark,
                   DATE_FORMAT(c.update_time, '%Y-%m-%d %H:%i') AS updateTime
            FROM lc_tenant_config c
            WHERE (#{keyword} IS NULL OR #{keyword} = ''
                   OR c.config_key LIKE CONCAT('%', #{keyword}, '%')
                   OR c.config_name LIKE CONCAT('%', #{keyword}, '%')
                   OR c.remark LIKE CONCAT('%', #{keyword}, '%'))
            ORDER BY c.builtin DESC, c.config_key
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Map<String, Object>> selectConfigs(@Param("keyword") String keyword,
                                            @Param("offset") int offset,
                                            @Param("pageSize") int pageSize);

    @Select("SELECT COUNT(*) FROM lc_tenant_config WHERE config_key = #{configKey} AND (#{excludeId} IS NULL OR id <> #{excludeId})")
    long countConfigKey(@Param("configKey") String configKey, @Param("excludeId") Long excludeId);

    @Insert("""
            INSERT INTO lc_tenant_config (config_key, config_value, config_name, builtin, remark, create_by, update_by)
            VALUES (#{row.configKey}, #{row.configValue}, #{row.configName}, #{row.builtin}, #{row.remark}, #{operator}, #{operator})
            """)
    int insert(@Param("row") Map<String, Object> row, @Param("operator") String operator);

    /**
     * 开通新租户时给"另一个租户"写默认参数：当前会话还在平台租户，拦截器注入的会是平台租户，
     * 所以这条唯一地显式带 tenant_id 并跳过注入。除此之外不要新增绕过拦截器的写法。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Insert("""
            INSERT INTO lc_tenant_config (tenant_id, config_key, config_value, config_name, builtin, remark, create_by, update_by)
            VALUES (#{row.tenantId}, #{row.configKey}, #{row.configValue}, #{row.configName}, #{row.builtin}, #{row.remark}, #{operator}, #{operator})
            ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), update_by = VALUES(update_by)
            """)
    int insertForTenant(@Param("row") Map<String, Object> row, @Param("operator") String operator);

    @Update("""
            UPDATE lc_tenant_config
            SET config_value = #{row.configValue}, remark = COALESCE(#{row.remark}, remark), update_by = #{operator}
            WHERE id = #{id}
            """)
    int updateValue(@Param("id") Long id, @Param("row") Map<String, Object> row, @Param("operator") String operator);

    /** 内置参数不允许删除，避免生成项目时拿不到分支默认值 */
    @Delete("DELETE FROM lc_tenant_config WHERE id = #{id} AND builtin = 0")
    int delete(@Param("id") Long id);
}
