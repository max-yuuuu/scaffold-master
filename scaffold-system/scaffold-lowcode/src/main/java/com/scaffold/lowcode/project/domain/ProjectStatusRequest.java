package com.scaffold.lowcode.project.domain;

import jakarta.validation.constraints.NotNull;

/**
 * 项目启停请求（lc_project.status）：1 正常，0 停用。停用后不再允许发布。
 */
public class ProjectStatusRequest {

    @NotNull(message = "不能为空")
    private Integer status;

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
