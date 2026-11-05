package com.scaffold.lowcode.model.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 模型表访问。lc_model 带 tenant_id 且已登记进租户拦截器白名单，条件自动注入。
 */
@Mapper
public interface LcModelMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM lc_model m
            WHERE m.deleted = 0
              AND (#{projectId} IS NULL OR m.project_id = #{projectId})
              AND (#{keyword} IS NULL OR #{keyword} = ''
                   OR m.model_code LIKE CONCAT('%', #{keyword}, '%')
                   OR m.model_name LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{status} IS NULL OR m.status = #{status})
            </script>
            """)
    long countModels(@Param("projectId") Long projectId, @Param("keyword") String keyword, @Param("status") Integer status);

    @Select("""
            <script>
            SELECT m.id,
                   m.project_id AS projectId,
                   p.project_code AS projectCode,
                   p.project_name AS projectName,
                   m.model_code AS modelCode,
                   m.model_name AS modelName,
                   m.model_desc AS modelDesc,
                   m.table_name AS tableName,
                   m.class_name AS className,
                   m.module_name AS moduleName,
                   m.is_tree AS isTree,
                   m.enable_logic_delete AS enableLogicDelete,
                   m.enable_data_scope AS enableDataScope,
                   m.enable_audit_field AS enableAuditField,
                   m.status,
                   (SELECT COUNT(*) FROM lc_field f WHERE f.model_id = m.id) AS fieldCount,
                   DATE_FORMAT(m.create_time, '%Y-%m-%d %H:%i') AS createTime,
                   DATE_FORMAT(m.update_time, '%Y-%m-%d %H:%i') AS updateTime
            FROM lc_model m
            LEFT JOIN lc_project p ON p.id = m.project_id
            WHERE m.deleted = 0
              AND (#{projectId} IS NULL OR m.project_id = #{projectId})
              AND (#{keyword} IS NULL OR #{keyword} = ''
                   OR m.model_code LIKE CONCAT('%', #{keyword}, '%')
                   OR m.model_name LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{status} IS NULL OR m.status = #{status})
            ORDER BY m.project_id, m.id
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Map<String, Object>> selectModels(@Param("projectId") Long projectId,
                                           @Param("keyword") String keyword,
                                           @Param("status") Integer status,
                                           @Param("offset") int offset,
                                           @Param("pageSize") int pageSize);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM lc_model
            WHERE project_id = #{projectId}
              AND model_code = #{modelCode}
              AND deleted = 0
              AND (#{excludeId} IS NULL OR id &lt;&gt; #{excludeId})
            </script>
            """)
    long countModelCode(@Param("projectId") Long projectId, @Param("modelCode") String modelCode, @Param("excludeId") Long excludeId);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM lc_model
            WHERE project_id = #{projectId}
              AND table_name = #{tableName}
              AND deleted = 0
              AND (#{excludeId} IS NULL OR id &lt;&gt; #{excludeId})
            </script>
            """)
    long countModelTable(@Param("projectId") Long projectId, @Param("tableName") String tableName, @Param("excludeId") Long excludeId);

    @Select("""
            SELECT id, project_id AS projectId, model_code AS modelCode, model_name AS modelName,
                   table_name AS tableName, class_name AS className, module_name AS moduleName, status
            FROM lc_model
            WHERE id = #{id} AND deleted = 0
            """)
    Map<String, Object> selectModelBrief(@Param("id") Long id);

    /**
     * 生成器要吃的模型行：{@link #selectModelBrief(Long)} 那份没有生成期要判的四个开关
     * （树表、逻辑删除、数据权限、审计字段），发布这条链路必须按它们决定产物的形状。
     */
    @Select("""
            SELECT id, project_id AS projectId, model_code AS modelCode, model_name AS modelName,
                   model_desc AS modelDesc, table_name AS tableName, class_name AS className,
                   module_name AS moduleName, is_tree AS isTree, enable_logic_delete AS enableLogicDelete,
                   enable_data_scope AS enableDataScope, enable_audit_field AS enableAuditField, status
            FROM lc_model
            WHERE id = #{id} AND deleted = 0
            """)
    Map<String, Object> selectModelForGenerate(@Param("id") Long id);

    /**
     * 按「项目 + 模型编码」取模型。
     *
     * <p>页面绑模型走编码而不是 id（方案 §1.5：页面与模型的耦合只发生在 field_code 这个符号上，
     * 而 design_json 里写的就是 {@code modelRef} 编码）；反查必须限定在同一项目内，
     * 否则同租户别的项目的同名模型会被串进来。
     */
    @Select("""
            SELECT id, project_id AS projectId, model_code AS modelCode, model_name AS modelName,
                   table_name AS tableName, class_name AS className, module_name AS moduleName, status
            FROM lc_model
            WHERE project_id = #{projectId} AND model_code = #{modelCode} AND deleted = 0
            """)
    Map<String, Object> selectModelBriefByCode(@Param("projectId") Long projectId, @Param("modelCode") String modelCode);

    /** REF 字段选目标模型用的精简列表（同项目内） */
    @Select("""
            SELECT id, model_code AS modelCode, model_name AS modelName, table_name AS tableName, class_name AS className
            FROM lc_model
            WHERE deleted = 0 AND status = 1 AND (#{projectId} IS NULL OR project_id = #{projectId})
            ORDER BY project_id, id
            """)
    List<Map<String, Object>> selectModelOptions(@Param("projectId") Long projectId);

    @Select("SELECT COUNT(*) FROM lc_model WHERE project_id = #{projectId} AND deleted = 0")
    long countModelsByProject(@Param("projectId") Long projectId);

    @Insert("""
            INSERT INTO lc_model (project_id, model_code, model_name, model_desc, table_name, class_name, module_name,
                                  is_tree, enable_logic_delete, enable_data_scope, enable_audit_field, status, create_by, update_by)
            VALUES (#{row.projectId}, #{row.modelCode}, #{row.modelName}, #{row.modelDesc}, #{row.tableName}, #{row.className}, #{row.moduleName},
                    #{row.isTree}, #{row.enableLogicDelete}, #{row.enableDataScope}, #{row.enableAuditField}, #{row.status}, #{operator}, #{operator})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insertModel(@Param("row") Map<String, Object> row, @Param("operator") String operator);

    @Update("""
            UPDATE lc_model
            SET model_name = COALESCE(#{row.modelName}, model_name),
                model_desc = COALESCE(#{row.modelDesc}, model_desc),
                table_name = COALESCE(#{row.tableName}, table_name),
                class_name = COALESCE(#{row.className}, class_name),
                module_name = COALESCE(#{row.moduleName}, module_name),
                is_tree = COALESCE(#{row.isTree}, is_tree),
                enable_logic_delete = COALESCE(#{row.enableLogicDelete}, enable_logic_delete),
                enable_data_scope = COALESCE(#{row.enableDataScope}, enable_data_scope),
                enable_audit_field = COALESCE(#{row.enableAuditField}, enable_audit_field),
                status = COALESCE(#{row.status}, status),
                update_by = #{operator}
            WHERE id = #{id} AND deleted = 0
            """)
    int updateModel(@Param("id") Long id, @Param("row") Map<String, Object> row, @Param("operator") String operator);

    @Update("UPDATE lc_model SET status = #{status}, update_by = #{operator} WHERE id = #{id} AND deleted = 0")
    int updateModelStatus(@Param("id") Long id, @Param("status") Integer status, @Param("operator") String operator);

    @Update("UPDATE lc_model SET deleted = 1, update_by = #{operator} WHERE id = #{id} AND deleted = 0")
    int softDeleteModel(@Param("id") Long id, @Param("operator") String operator);

    @Delete("DELETE FROM lc_field WHERE model_id = #{modelId}")
    int deleteFieldsOfModel(@Param("modelId") Long modelId);
}
