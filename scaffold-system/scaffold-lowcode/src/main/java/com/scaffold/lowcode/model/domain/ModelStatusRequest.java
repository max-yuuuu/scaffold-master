package com.scaffold.lowcode.model.domain;

import jakarta.validation.constraints.NotNull;

/**
 * 模型启停请求（lc_model.status）：1 正常，0 停用。停用中的模型不参与发布。
 */
public class ModelStatusRequest {

    @NotNull(message = "不能为空")
    private Integer status;

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
