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

    public LcProjectController(LcProjectService service) {
        this.service = service;
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
}
