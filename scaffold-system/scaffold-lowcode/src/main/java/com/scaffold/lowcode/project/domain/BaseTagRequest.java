package com.scaffold.lowcode.project.domain;

import jakarta.validation.constraints.Size;

/**
 * 人工补打底座标记 tag 的入参。
 *
 * <p>{@code commitSha} 可以不填：不填时平台按发布分支上的 {@code .scaffold-project.json} 自己认那一笔交付
 * （见 {@code BaseTagService}）。填了就是「我告诉你哪一笔是」，平台会核对它确实在那条分支的历史里。
 */
public class BaseTagRequest {

    /** 要打的提交号；留空表示让平台按项目标记文件定位 */
    @Size(max = 64, message = "过长")
    private String commitSha;

    public String getCommitSha() {
        return commitSha;
    }

    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }
}
