package com.scaffold.lowcode.project.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 项目新增与编辑请求（lc_project）。项目 = 租户名下的一个 Git 仓库，是模型与页面的归属容器。
 */
public class ProjectWriteRequest {

    /** 项目编码（lc_project.project_code）：租户内唯一，决定生成物的包名与目录名，创建后不可改 */
    @Pattern(regexp = "^[a-z][a-z0-9-]{1,31}$", message = "小写字母开头，仅含小写字母、数字、短横线，长度 2-32")
    private String projectCode;

    /** 项目名称（lc_project.project_name） */
    @NotBlank(message = "不能为空")
    @Size(max = 64, message = "长度不能超过 64")
    private String projectName;

    /** 项目描述（lc_project.project_desc） */
    @Size(max = 512, message = "长度不能超过 512")
    private String projectDesc;

    /** 仓库地址（lc_project.repo_url）：仅 https:// 或 git@host:path，凭据走主机 SSH 密钥 */
    @NotBlank(message = "不能为空")
    private String repoUrl;

    /** 主干分支（lc_project.main_branch）：人工合并请求的目标分支，平台永不直接写 */
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._/-]{0,63}$", message = "字母数字开头，仅含字母、数字、点、下划线、短横线、斜杠")
    private String mainBranch;

    /** 发布分支（lc_project.gen_branch）：平台发布只推这条分支 */
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._/-]{0,63}$", message = "字母数字开头，仅含字母、数字、点、下划线、短横线、斜杠")
    private String genBranch;

    /** 项目负责人（lc_project.owner_user_id → sys_user.id）：合并请求只有他或超级管理员能确认合并 */
    private Long ownerUserId;

    /** 备注（lc_project.remark） */
    @Size(max = 500, message = "长度不能超过 500")
    private String remark;

    /** 项目状态（lc_project.status）：1 正常，0 停用 */
    private Integer status;

    public String getProjectCode() { return projectCode; }
    public void setProjectCode(String projectCode) { this.projectCode = projectCode; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getProjectDesc() { return projectDesc; }
    public void setProjectDesc(String projectDesc) { this.projectDesc = projectDesc; }
    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
    public String getMainBranch() { return mainBranch; }
    public void setMainBranch(String mainBranch) { this.mainBranch = mainBranch; }
    public String getGenBranch() { return genBranch; }
    public void setGenBranch(String genBranch) { this.genBranch = genBranch; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
