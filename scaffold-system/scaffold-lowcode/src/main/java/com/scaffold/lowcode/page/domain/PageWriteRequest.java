package com.scaffold.lowcode.page.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 页面新增与编辑请求（lc_page）。
 *
 * <p>{@code design} 收的是设计器产出的那份对象（方案 §3.4），不是字符串：让前端再 stringify 一遍，
 * 后端就得自己判「这里到底是串还是对象」，而这两者在库里都是同一份文本。
 *
 * <p>{@code pageCode} 与 {@code modelCode}、{@code projectId} 创建后不可改：它们决定生成物的落点
 * （页面文件名、取哪个模型的字段、进哪个仓库），改了等于把已发布页面的历史换到另一条链路上。
 */
public class PageWriteRequest {

    /** 归属项目 ID（lc_project.id），创建后不可改 */
    private Long projectId;

    /** 绑定的模型编码（lc_model.model_code）：一个页面一个主模型，创建后不可改 */
    @Pattern(regexp = "^[a-z][a-z0-9_]{1,31}$", message = "小写字母开头，仅含小写字母、数字、下划线，长度 2-32")
    private String modelCode;

    /** 页面编码（lc_page.page_code）：项目内唯一，决定生成页面的文件名 */
    @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$", message = "小写字母开头，仅含小写字母、数字、下划线，长度 2-64")
    private String pageCode;

    /** 页面名称（lc_page.page_name）：生成菜单里的菜单名 */
    @NotBlank(message = "不能为空")
    @Size(max = 64, message = "长度不能超过 64")
    private String pageName;

    /** 页面说明（lc_page.page_desc） */
    @Size(max = 256, message = "长度不能超过 256")
    private String pageDesc;

    /** 菜单与路由落点（lc_page.route_path）：留空由生成器按接口路径派生 */
    @Pattern(regexp = "^$|^/[A-Za-z0-9._~/-]{0,127}$", message = "以 / 开头，仅含字母、数字、点、下划线、短横线、斜杠")
    private String routePath;

    /** 菜单图标（lc_page.menu_icon）：留空用底座的 grid，取值见物料包的 MENU_ICON_OPTIONS */
    @Pattern(regexp = "^$|^[a-z][a-z0-9-]{0,31}$", message = "小写字母开头，仅含小写字母、数字、短横线")
    private String menuIcon;

    /** 菜单排序（lc_page.menu_sort）：留空排 1 */
    private Integer menuSort;

    /** 设计器产出的页面配置（lc_page.design_json），保存前与模型逐字段对过 */
    private JsonNode design;

    /** 页面状态（lc_page.status）：1 正常，0 停用（停用不参与发布） */
    private Integer status;

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getModelCode() { return modelCode; }
    public void setModelCode(String modelCode) { this.modelCode = modelCode; }
    public String getPageCode() { return pageCode; }
    public void setPageCode(String pageCode) { this.pageCode = pageCode; }
    public String getPageName() { return pageName; }
    public void setPageName(String pageName) { this.pageName = pageName; }
    public String getPageDesc() { return pageDesc; }
    public void setPageDesc(String pageDesc) { this.pageDesc = pageDesc; }
    public String getRoutePath() { return routePath; }
    public void setRoutePath(String routePath) { this.routePath = routePath; }
    public String getMenuIcon() { return menuIcon; }
    public void setMenuIcon(String menuIcon) { this.menuIcon = menuIcon; }
    public Integer getMenuSort() { return menuSort; }
    public void setMenuSort(Integer menuSort) { this.menuSort = menuSort; }
    public JsonNode getDesign() { return design; }
    public void setDesign(JsonNode design) { this.design = design; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
