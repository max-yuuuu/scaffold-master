package com.scaffold.lowcode.tenant.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 租户自助注册请求（匿名接口，登录页的注册表单）。
 *
 * <p>提交即开通：创建租户、管理员账号、内置 tenant_admin 角色与该租户的菜单树，
 * 并直接回一张登录令牌。
 */
public class TenantRegisterRequest {

    /** 租户编码（lc_tenant.tenant_code）：全局唯一，登录时可用于定位租户 */
    @NotBlank(message = "不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9-]{1,31}$", message = "小写字母开头，仅含小写字母、数字、短横线，长度 2-32")
    private String tenantCode;

    /** 租户名称（lc_tenant.tenant_name） */
    @NotBlank(message = "不能为空")
    @Size(max = 64, message = "长度不能超过 64")
    private String tenantName;

    /** 管理员账号（sys_user.username）：注册要求全局唯一，避免他人登录后需要多填租户编码 */
    @NotBlank(message = "不能为空")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{2,31}$", message = "字母开头，仅含字母、数字、下划线，长度 3-32")
    private String adminUsername;

    /** 管理员密码：至少一位字母与一位数字 */
    @NotBlank(message = "不能为空")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,32}$", message = "长度 8-32 且需同时包含字母和数字")
    private String password;

    private String captcha;

    private String captchaUuid;

    public String getTenantCode() { return tenantCode; }
    public void setTenantCode(String tenantCode) { this.tenantCode = tenantCode; }
    public String getTenantName() { return tenantName; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }
    public String getAdminUsername() { return adminUsername; }
    public void setAdminUsername(String adminUsername) { this.adminUsername = adminUsername; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getCaptcha() { return captcha; }
    public void setCaptcha(String captcha) { this.captcha = captcha; }
    public String getCaptchaUuid() { return captchaUuid; }
    public void setCaptchaUuid(String captchaUuid) { this.captchaUuid = captchaUuid; }
}
