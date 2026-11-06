package com.scaffold.lowcode.merge.domain;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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

    /**
     * 来源分支（可选，默认是项目的发布分支）。底座升级推的是 {@code platform-upgrade/*} 分支，
     * 它同样要走「看清单 → 预检 → 人确认」这条路，所以这里收一个来源分支而不是另开一套界面。
     *
     * <p>只放开这两种来源，见 {@code MergeRequestService.requireAllowedSource}：随便指一个分支
     * 就等于把「平台只交付两条链路」这条边界交给了调用方。
     */
    @Size(max = 128, message = "来源分支名不能超过 128 字")
    private String sourceBranch;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getSourceBranch() {
        return sourceBranch;
    }

    public void setSourceBranch(String sourceBranch) {
        this.sourceBranch = sourceBranch;
    }
}
