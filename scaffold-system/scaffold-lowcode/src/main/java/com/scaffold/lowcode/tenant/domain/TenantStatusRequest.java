package com.scaffold.lowcode.tenant.domain;

import jakarta.validation.constraints.NotNull;

/**
 * 租户启停请求（lc_tenant.status）：1 正常，0 停用。停用后该租户下的账号无法登录。
 */
public class TenantStatusRequest {

    @NotNull(message = "不能为空")
    private Integer status;

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
