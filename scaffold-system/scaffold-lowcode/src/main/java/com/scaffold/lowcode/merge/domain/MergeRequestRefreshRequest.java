package com.scaffold.lowcode.merge.domain;

import jakarta.validation.constraints.NotNull;

/**
 * 重算合并请求的入参（PR 8 第一刀）。
 *
 * <p>只要一个项目 ID：清单与预检结论全部从仓库现算，任何「顺手带过来的期望值」都会变成
 * 「前端说是什么就算什么」，而前端那份读数可能已经是上一次刷新留下的。
 */
public class MergeRequestRefreshRequest {

    /** 项目 ID（lc_project.id）：仓库地址、发布分支与主干都按它取 */
    @NotNull(message = "必须指定项目")
    private Long projectId;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }
}
