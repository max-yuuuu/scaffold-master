package com.scaffold.lowcode.tenant.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 租户新增与编辑请求（lc_tenant）。
 */
public class TenantWriteRequest {

    /** 租户编码（lc_tenant.tenant_code）：全局唯一，登录时可用于定位租户；创建后不可修改 */
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]{1,63}$", message = "字母开头，仅含字母、数字、下划线、短横线，长度 2-64")
    private String tenantCode;

    /** 租户名称（lc_tenant.tenant_name） */
    @NotBlank(message = "不能为空")
    private String tenantName;

    /** 联系人姓名（lc_tenant.contact_name） */
    private String contactName;

    /** 联系人电话（lc_tenant.contact_phone） */
    private String contactPhone;

    /** 最大项目数配额（lc_tenant.max_projects） */
    @Min(value = 1, message = "不能小于 1")
    @Max(value = 1000, message = "不能大于 1000")
    private Integer maxProjects;

    /** 到期日期（lc_tenant.expire_time，格式 yyyy-MM-dd）；为空表示不限 */
    @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "格式为 yyyy-MM-dd")
    private String expireDate;

    /** 租户状态（lc_tenant.status）：1 正常，0 停用 */
    private Integer status;

    /** 备注（lc_tenant.remark） */
    private String remark;

    public String getTenantCode() { return tenantCode; }
    public void setTenantCode(String tenantCode) { this.tenantCode = tenantCode; }
    public String getTenantName() { return tenantName; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public Integer getMaxProjects() { return maxProjects; }
    public void setMaxProjects(Integer maxProjects) { this.maxProjects = maxProjects; }
    public String getExpireDate() { return expireDate; }
    public void setExpireDate(String expireDate) { this.expireDate = expireDate; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
