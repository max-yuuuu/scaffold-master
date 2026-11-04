package com.scaffold.lowcode.model.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 字段新增与编辑请求（lc_field）。列类型与 Java 类型不在请求里传 ——
 * 由 {@code FieldType} 按 fieldType + 长度/精度算出来，避免前端与后端两套口径。
 */
public class FieldWriteRequest {

    /** 字段编码（lc_field.field_code）：小驼峰，模型内唯一；页面与模型的耦合只在这个符号上 */
    @Pattern(regexp = "^[a-z][A-Za-z0-9]{1,63}$", message = "小写字母开头，驼峰式，仅含字母与数字，长度 2-64")
    private String fieldCode;

    /** 字段名称（lc_field.field_name）：表单标签与列表表头 */
    @NotBlank(message = "不能为空")
    @Size(max = 64, message = "长度不能超过 64")
    private String fieldName;

    /** 字段说明（lc_field.field_desc） */
    @Size(max = 256, message = "长度不能超过 256")
    private String fieldDesc;

    /** 字段类型（lc_field.field_type）：STRING/TEXT/INTEGER/DECIMAL/DATE/DATETIME/ENUM/FILE/REF */
    @NotBlank(message = "不能为空")
    private String fieldType;

    /** 列长度（字符串类）；空则用类型默认值 */
    private Integer columnLength;

    /** DECIMAL 精度 */
    private Integer columnPrecision;

    /** DECIMAL 小数位 */
    private Integer columnScale;

    /** 是否主键（lc_field.is_primary），一个模型最多一个 */
    private Integer isPrimary;

    /** 是否自增（lc_field.is_auto_increment），必须是主键 */
    private Integer isAutoIncrement;

    /** 是否可空（lc_field.is_nullable） */
    private Integer isNullable;

    /** 是否唯一索引（lc_field.is_unique） */
    private Integer isUnique;

    /** 列默认值（lc_field.default_value） */
    @Size(max = 256, message = "长度不能超过 256")
    private String defaultValue;

    /** 列表页是否显示 */
    private Integer isList;

    /** 是否作为查询条件 */
    private Integer isQuery;

    /** 查询方式：EQ/NE/GT/GE/LT/LE/LIKE/BETWEEN/IN，也接受 = != > 等运算符 */
    private String queryType;

    /** 新增表单是否出现 */
    private Integer isInsert;

    /** 编辑表单是否出现 */
    private Integer isEdit;

    /** 是否必填 */
    private Integer isRequired;

    /** 表单控件：INPUT/TEXTAREA/NUMBER/SELECT/RADIO/CHECKBOX/SWITCH/DATE/DATETIME/UPLOAD/REF，空则按字段类型取默认 */
    private String formType;

    /** RADIO / CHECKBOX 的固定选项；挂了字典时留空，以字典为准 */
    private List<FormOption> formOptions;

    /** 展示字典类型（sys_dict_type.dict_type），ENUM 必填 */
    @Size(max = 64, message = "长度不能超过 64")
    private String dictType;

    /** REF 指向的模型 ID（同项目同租户） */
    private Long refModelId;

    /** REF 下拉显示的目标字段 ID，留空则显示目标模型主键 */
    private Long refFieldId;

    /** 字段顺序（lc_field.sort_order） */
    private Integer sortOrder;

    /** 控件固定选项 */
    public record FormOption(String label, String value) {
    }

    public String getFieldCode() { return fieldCode; }
    public void setFieldCode(String fieldCode) { this.fieldCode = fieldCode; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getFieldDesc() { return fieldDesc; }
    public void setFieldDesc(String fieldDesc) { this.fieldDesc = fieldDesc; }
    public String getFieldType() { return fieldType; }
    public void setFieldType(String fieldType) { this.fieldType = fieldType; }
    public Integer getColumnLength() { return columnLength; }
    public void setColumnLength(Integer columnLength) { this.columnLength = columnLength; }
    public Integer getColumnPrecision() { return columnPrecision; }
    public void setColumnPrecision(Integer columnPrecision) { this.columnPrecision = columnPrecision; }
    public Integer getColumnScale() { return columnScale; }
    public void setColumnScale(Integer columnScale) { this.columnScale = columnScale; }
    public Integer getIsPrimary() { return isPrimary; }
    public void setIsPrimary(Integer isPrimary) { this.isPrimary = isPrimary; }
    public Integer getIsAutoIncrement() { return isAutoIncrement; }
    public void setIsAutoIncrement(Integer isAutoIncrement) { this.isAutoIncrement = isAutoIncrement; }
    public Integer getIsNullable() { return isNullable; }
    public void setIsNullable(Integer isNullable) { this.isNullable = isNullable; }
    public Integer getIsUnique() { return isUnique; }
    public void setIsUnique(Integer isUnique) { this.isUnique = isUnique; }
    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
    public Integer getIsList() { return isList; }
    public void setIsList(Integer isList) { this.isList = isList; }
    public Integer getIsQuery() { return isQuery; }
    public void setIsQuery(Integer isQuery) { this.isQuery = isQuery; }
    public String getQueryType() { return queryType; }
    public void setQueryType(String queryType) { this.queryType = queryType; }
    public Integer getIsInsert() { return isInsert; }
    public void setIsInsert(Integer isInsert) { this.isInsert = isInsert; }
    public Integer getIsEdit() { return isEdit; }
    public void setIsEdit(Integer isEdit) { this.isEdit = isEdit; }
    public Integer getIsRequired() { return isRequired; }
    public void setIsRequired(Integer isRequired) { this.isRequired = isRequired; }
    public String getFormType() { return formType; }
    public void setFormType(String formType) { this.formType = formType; }
    public List<FormOption> getFormOptions() { return formOptions; }
    public void setFormOptions(List<FormOption> formOptions) { this.formOptions = formOptions; }
    public String getDictType() { return dictType; }
    public void setDictType(String dictType) { this.dictType = dictType; }
    public Long getRefModelId() { return refModelId; }
    public void setRefModelId(Long refModelId) { this.refModelId = refModelId; }
    public Long getRefFieldId() { return refFieldId; }
    public void setRefFieldId(Long refFieldId) { this.refFieldId = refFieldId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
