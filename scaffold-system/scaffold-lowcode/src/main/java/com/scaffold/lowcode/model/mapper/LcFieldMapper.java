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
 * 字段表访问。lc_field 带 tenant_id 且已登记进租户拦截器白名单，条件自动注入。
 */
@Mapper
public interface LcFieldMapper {

    /** 模型的全部字段，按 sort_order 排 —— 它同时决定生成的实体属性顺序与表单/列表列顺序 */
    @Select("""
            SELECT id,
                   model_id AS modelId,
                   field_code AS fieldCode,
                   field_name AS fieldName,
                   field_desc AS fieldDesc,
                   field_type AS fieldType,
                   java_type AS javaType,
                   column_name AS columnName,
                   column_type AS columnType,
                   column_length AS columnLength,
                   column_precision AS columnPrecision,
                   column_scale AS columnScale,
                   is_primary AS isPrimary,
                   is_auto_increment AS isAutoIncrement,
                   is_nullable AS isNullable,
                   is_unique AS isUnique,
                   default_value AS defaultValue,
                   is_list AS isList,
                   is_query AS isQuery,
                   query_type AS queryType,
                   is_insert AS isInsert,
                   is_edit AS isEdit,
                   is_required AS isRequired,
                   form_type AS formType,
                   form_options AS formOptions,
                   dict_type AS dictType,
                   ref_model_id AS refModelId,
                   ref_field_id AS refFieldId,
                   sort_order AS sortOrder
            FROM lc_field
            WHERE model_id = #{modelId}
            ORDER BY sort_order, id
            """)
    List<Map<String, Object>> selectByModel(@Param("modelId") Long modelId);

    @Select("SELECT COUNT(*) FROM lc_field WHERE model_id = #{modelId}")
    long countFields(@Param("modelId") Long modelId);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM lc_field
            WHERE model_id = #{modelId}
              AND field_code = #{fieldCode}
              AND (#{excludeId} IS NULL OR id &lt;&gt; #{excludeId})
            </script>
            """)
    long countFieldCode(@Param("modelId") Long modelId, @Param("fieldCode") String fieldCode, @Param("excludeId") Long excludeId);

    /** 一个模型最多一个主键 */
    @Select("""
            <script>
            SELECT COUNT(*)
            FROM lc_field
            WHERE model_id = #{modelId}
              AND is_primary = 1
              AND (#{excludeId} IS NULL OR id &lt;&gt; #{excludeId})
            </script>
            """)
    long countPrimaryKeys(@Param("modelId") Long modelId, @Param("excludeId") Long excludeId);

    @Select("""
            SELECT id, model_id AS modelId, field_code AS fieldCode, field_name AS fieldName,
                   field_type AS fieldType, ref_model_id AS refModelId
            FROM lc_field
            WHERE id = #{id}
            """)
    Map<String, Object> selectFieldBrief(@Param("id") Long id);

    /** REF 字段「下拉显示哪一列」用的候选：目标模型的字段 */
    @Select("""
            SELECT id, field_code AS fieldCode, field_name AS fieldName, field_type AS fieldType
            FROM lc_field
            WHERE model_id = #{modelId}
            ORDER BY sort_order, id
            """)
    List<Map<String, Object>> selectFieldOptions(@Param("modelId") Long modelId);

    @Insert("""
            INSERT INTO lc_field (model_id, field_code, field_name, field_desc, field_type, java_type,
                                  column_name, column_type, column_length, column_precision, column_scale,
                                  is_primary, is_auto_increment, is_nullable, is_unique, default_value,
                                  is_list, is_query, query_type, is_insert, is_edit, is_required,
                                  form_type, form_options, dict_type, ref_model_id, ref_field_id, sort_order, create_by, update_by)
            VALUES (#{row.modelId}, #{row.fieldCode}, #{row.fieldName}, #{row.fieldDesc}, #{row.fieldType}, #{row.javaType},
                    #{row.columnName}, #{row.columnType}, #{row.columnLength}, #{row.columnPrecision}, #{row.columnScale},
                    #{row.isPrimary}, #{row.isAutoIncrement}, #{row.isNullable}, #{row.isUnique}, #{row.defaultValue},
                    #{row.isList}, #{row.isQuery}, #{row.queryType}, #{row.isInsert}, #{row.isEdit}, #{row.isRequired},
                    #{row.formType}, #{row.formOptions}, #{row.dictType}, #{row.refModelId}, #{row.refFieldId},
                    #{row.sortOrder}, #{operator}, #{operator})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insertField(@Param("row") Map<String, Object> row, @Param("operator") String operator);

    @Update("""
            UPDATE lc_field
            SET field_code = COALESCE(#{row.fieldCode}, field_code),
                field_name = COALESCE(#{row.fieldName}, field_name),
                field_desc = COALESCE(#{row.fieldDesc}, field_desc),
                field_type = COALESCE(#{row.fieldType}, field_type),
                java_type = COALESCE(#{row.javaType}, java_type),
                column_name = COALESCE(#{row.columnName}, column_name),
                column_type = COALESCE(#{row.columnType}, column_type),
                column_length = #{row.columnLength},
                column_precision = #{row.columnPrecision},
                column_scale = #{row.columnScale},
                is_primary = COALESCE(#{row.isPrimary}, is_primary),
                is_auto_increment = COALESCE(#{row.isAutoIncrement}, is_auto_increment),
                is_nullable = COALESCE(#{row.isNullable}, is_nullable),
                is_unique = COALESCE(#{row.isUnique}, is_unique),
                default_value = #{row.defaultValue},
                is_list = COALESCE(#{row.isList}, is_list),
                is_query = COALESCE(#{row.isQuery}, is_query),
                query_type = COALESCE(#{row.queryType}, query_type),
                is_insert = COALESCE(#{row.isInsert}, is_insert),
                is_edit = COALESCE(#{row.isEdit}, is_edit),
                is_required = COALESCE(#{row.isRequired}, is_required),
                form_type = COALESCE(#{row.formType}, form_type),
                form_options = #{row.formOptions},
                dict_type = #{row.dictType},
                ref_model_id = #{row.refModelId},
                ref_field_id = #{row.refFieldId},
                sort_order = COALESCE(#{row.sortOrder}, sort_order),
                update_by = #{operator}
            WHERE id = #{id}
            """)
    int updateField(@Param("id") Long id, @Param("row") Map<String, Object> row, @Param("operator") String operator);

    @Delete("DELETE FROM lc_field WHERE id = #{id}")
    int deleteField(@Param("id") Long id);

    /** 字典必须真实存在于当前租户，否则生成的页面会是一个空下拉（sys_dict_type 走拦截器注入租户条件） */
    @Select("SELECT COUNT(*) FROM sys_dict_type WHERE dict_type = #{dictType} AND status = 1")
    long countDictType(@Param("dictType") String dictType);
}
