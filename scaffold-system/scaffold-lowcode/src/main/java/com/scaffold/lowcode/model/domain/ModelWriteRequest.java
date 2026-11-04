package com.scaffold.lowcode.model.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 模型新增与编辑请求（lc_model）。模型是生成器的输入（方案 §1.4），
 * 表名 / 类名 / 包名留空时按模型编码派生，填了以填的为准。
 */
public class ModelWriteRequest {

    /** 归属项目 ID（lc_project.id），创建后不可改 —— 换项目等于换仓库 */
    private Long projectId;

    /** 模型编码（lc_model.model_code）：项目内唯一，决定表名与类名的默认值 */
    @Pattern(regexp = "^[a-z][a-z0-9_]{1,31}$", message = "小写字母开头，仅含小写字母、数字、下划线，长度 2-32")
    private String modelCode;

    /** 模型名称（lc_model.model_name）：生成页面的标题与菜单名 */
    @NotBlank(message = "不能为空")
    @Size(max = 64, message = "长度不能超过 64")
    private String modelName;

    /** 模型描述（lc_model.model_desc） */
    @Size(max = 256, message = "长度不能超过 256")
    private String modelDesc;

    /** 物理表名（lc_model.table_name），留空按 modelCode 派生 */
    @Pattern(regexp = "^$|^[a-z][a-z0-9_]{1,63}$", message = "小写字母开头，仅含小写字母、数字、下划线")
    private String tableName;

    /** Java 类名（lc_model.class_name），留空按 modelCode 派生 */
    @Pattern(regexp = "^$|^[A-Z][A-Za-z0-9]{1,63}$", message = "大写字母开头，仅含字母与数字")
    private String className;

    /** 业务子包名（lc_model.module_name），留空按 modelCode 派生；生成代码进 scaffold-business 的这个子包 */
    @Pattern(regexp = "^$|^[a-z][a-z0-9_]{1,31}$", message = "小写字母开头，仅含小写字母、数字、下划线")
    private String moduleName;

    /** 是否树表（lc_model.is_tree） */
    private Integer isTree;

    /** 逻辑删除（lc_model.enable_logic_delete） */
    private Integer enableLogicDelete;

    /** 数据权限（lc_model.enable_data_scope） */
    private Integer enableDataScope;

    /** 审计字段（lc_model.enable_audit_field） */
    private Integer enableAuditField;

    /** 模型状态（lc_model.status）：1 正常，0 停用 */
    private Integer status;

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getModelCode() { return modelCode; }
    public void setModelCode(String modelCode) { this.modelCode = modelCode; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getModelDesc() { return modelDesc; }
    public void setModelDesc(String modelDesc) { this.modelDesc = modelDesc; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getModuleName() { return moduleName; }
    public void setModuleName(String moduleName) { this.moduleName = moduleName; }
    public Integer getIsTree() { return isTree; }
    public void setIsTree(Integer isTree) { this.isTree = isTree; }
    public Integer getEnableLogicDelete() { return enableLogicDelete; }
    public void setEnableLogicDelete(Integer enableLogicDelete) { this.enableLogicDelete = enableLogicDelete; }
    public Integer getEnableDataScope() { return enableDataScope; }
    public void setEnableDataScope(Integer enableDataScope) { this.enableDataScope = enableDataScope; }
    public Integer getEnableAuditField() { return enableAuditField; }
    public void setEnableAuditField(Integer enableAuditField) { this.enableAuditField = enableAuditField; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
