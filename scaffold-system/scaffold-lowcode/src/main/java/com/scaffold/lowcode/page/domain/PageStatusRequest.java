package com.scaffold.lowcode.page.domain;

import jakarta.validation.constraints.NotNull;

/**
 * 页面启停请求（lc_page.status）：1 正常，0 停用。停用的页面不参与发布。
 */
public class PageStatusRequest {

    @NotNull(message = "不能为空")
    private Integer status;

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
