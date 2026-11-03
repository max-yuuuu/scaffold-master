package com.scaffold.lowcode.tenant.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 租户参数写入请求（lc_tenant_config）。作用于当前会话租户，不接受 tenant_id 入参。
 */
public class TenantConfigWriteRequest {

    /** 参数 ID（lc_tenant_config.id）：新增时为空，修改时必填 */
    private Long id;

    /** 参数键（lc_tenant_config.config_key），如 project.gen_branch */
    @NotBlank(message = "不能为空")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_.-]{1,127}$", message = "字母开头，仅含字母、数字、点、下划线、短横线")
    private String configKey;

    /** 参数值（lc_tenant_config.config_value） */
    private String configValue;

    /** 备注（lc_tenant_config.remark） */
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }
    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
