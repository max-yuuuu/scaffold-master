package com.scaffold.lowcode.project.controller;

import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.domain.R;
import com.scaffold.framework.audit.OperLog;
import com.scaffold.framework.security.RequiresPermission;
import com.scaffold.framework.web.CsvResponse;
import com.scaffold.lowcode.project.domain.ProjectStatusRequest;
import com.scaffold.lowcode.project.domain.ProjectWriteRequest;
import com.scaffold.lowcode.project.service.LcProjectService;
import com.scaffold.lowcode.workspace.BaseUpgradeService;
import com.scaffold.lowcode.workspace.ProjectRepositoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 项目管理接口。租户只能看到并操作自己名下的项目（tenant_id 条件由拦截器注入）。
 */
@RestController
@RequestMapping("/api/lowcode/projects")
public class LcProjectController {

    private final LcProjectService service;
    private final ProjectRepositoryService repositoryService;
    private final BaseUpgradeService baseUpgradeService;

    public LcProjectController(LcProjectService service, ProjectRepositoryService repositoryService,
                               BaseUpgradeService baseUpgradeService) {
        this.service = service;
        this.repositoryService = repositoryService;
        this.baseUpgradeService = baseUpgradeService;
    }

    @GetMapping
    @RequiresPermission("lowcode:project:list")
    public R<PageResult<Map<String, Object>>> projects(
            PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status
    ) {
        return R.success(service.projects(query, keyword, status));
    }

    /** 精简列表：模型、页面等后续模块选项目用 */
    @GetMapping("/options")
    @RequiresPermission("lowcode:project:list")
    public R<List<Map<String, Object>>> options() {
        return R.success(service.options());
    }

    @GetMapping("/export")
    @RequiresPermission("lowcode:project:list")
    public ResponseEntity<String> exportProjects(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status
    ) {
        return CsvResponse.attachment("projects.csv", service.exportProjects(keyword, status));
    }

    @PostMapping
    @RequiresPermission("lowcode:project:add")
    @OperLog(module = "项目管理", action = "新增项目")
    public R<Map<String, Object>> create(@Valid @RequestBody ProjectWriteRequest request) {
        return R.success(service.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("lowcode:project:edit")
    @OperLog(module = "项目管理", action = "编辑项目")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody ProjectWriteRequest request) {
        service.update(id, request);
        return R.success(null);
    }

    @PutMapping("/{id}/status")
    @RequiresPermission("lowcode:project:edit")
    @OperLog(module = "项目管理", action = "启停项目")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody ProjectStatusRequest request) {
        service.updateStatus(id, request);
        return R.success(null);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("lowcode:project:remove")
    @OperLog(module = "项目管理", action = "删除项目")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.success(null);
    }

    /** 仓库连通性检查：只读远端引用，不改项目状态（方案 §6.2 第 1 步） */
    @PostMapping("/{id}/repo/check")
    @RequiresPermission("lowcode:project:sync")
    @OperLog(module = "项目管理", action = "检查仓库连通性")
    public R<Map<String, Object>> checkRepo(@PathVariable Long id) {
        return R.success(repositoryService.check(id));
    }

    /** 首次初始化：clone（空仓库走未出生 HEAD）→ 写固定代码 → 提交 → 推发布分支 → 回读校验 */
    /**
     * 底座落后读数（§10.4）。只读：它回答的是「这个项目当初交付在哪个底座上、现在底座走到哪了」，
     * 一步都不碰仓库，所以权限沿用 {@code lowcode:project:list}。
     *
     * <p>{@code behindCommits} 为 {@code null} 是「数不出来」而不是 0 —— 界面要显示「未知」，
     * 把不知道说成没问题比不报更坏。
     */
    @GetMapping("/{id}/base-status")
    @RequiresPermission("lowcode:project:list")
    public R<Map<String, Object>> baseStatus(@PathVariable Long id) {
        return R.success(baseUpgradeService.status(id));
    }

    /**
     * 发起底座升级：按当前底座重新生成 A 档、与项目主干三方合并，把可自动合的内容推到一条
     * {@code platform-upgrade/*} 分支上。权限单独一个 {@code lowcode:project:upgrade}：
     * 这一笔会往租户自己的仓库推分支，与 V7 把「同步仓库」从「编辑项目」分出来同一个理由。
     *
     * <p>这里<b>不碰主干</b>：升级分支之后走合并请求，由人审、由人确认合入 —— 与页面发布同一道门。
     */
    @PostMapping("/{id}/base-upgrade")
    @RequiresPermission("lowcode:project:upgrade")
    @OperLog(module = "项目管理", action = "底座升级")
    public R<Map<String, Object>> baseUpgrade(@PathVariable Long id) {
        return R.success(baseUpgradeService.upgrade(id));
    }

    @PostMapping("/{id}/initialize")
    @RequiresPermission("lowcode:project:sync")
    @OperLog(module = "项目管理", action = "初始化固定代码")
    public R<Map<String, Object>> initialize(@PathVariable Long id) {
        return R.success(repositoryService.initialize(id));
    }

    /** 工作区读数：路径、是否脏、有没有未推送提交 */
    @GetMapping("/{id}/workspace")
    @RequiresPermission("lowcode:project:list")
    public R<Map<String, Object>> workspace(@PathVariable Long id) {
        return R.success(repositoryService.workspace(id));
    }

    /** 清空工作区：有未提交改动或未推送提交时拒绝执行，平台不静默丢人的代码 */
    @DeleteMapping("/{id}/workspace")
    @RequiresPermission("lowcode:project:sync")
    @OperLog(module = "项目管理", action = "重建工作区")
    public R<Map<String, Object>> resetWorkspace(@PathVariable Long id) {
        return R.success(repositoryService.resetWorkspace(id));
    }
}
