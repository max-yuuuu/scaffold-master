package com.scaffold.lowcode.page.controller;

import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.domain.R;
import com.scaffold.framework.audit.OperLog;
import com.scaffold.framework.security.RequiresPermission;
import com.scaffold.lowcode.page.domain.PageStatusRequest;
import com.scaffold.lowcode.page.domain.PagePublishRequest;
import com.scaffold.lowcode.page.domain.PageWriteRequest;
import com.scaffold.lowcode.page.service.LcPageService;
import com.scaffold.lowcode.publish.PagePublishService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 页面设计接口（PR 6）。读接口给设计器与列表页，写接口把「改设计」与「看设计」的权限分开，
 * 与模型与字段那两个接口同一口径。
 */
@RestController
@RequestMapping("/api/lowcode")
public class LcPageController {

    private final LcPageService pageService;
    private final PagePublishService publishService;

    public LcPageController(LcPageService pageService, PagePublishService publishService) {
        this.pageService = pageService;
        this.publishService = publishService;
    }

    @GetMapping("/pages")
    @RequiresPermission("lowcode:page:list")
    public R<PageResult<Map<String, Object>>> pages(
            PageQuery query,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long modelId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status
    ) {
        return R.success(pageService.pages(query, projectId, modelId, keyword, status));
    }

    @GetMapping("/pages/{id}")
    @RequiresPermission("lowcode:page:list")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.success(pageService.detail(id));
    }

    @PostMapping("/pages")
    @RequiresPermission("lowcode:page:add")
    @OperLog(module = "页面设计", action = "新增页面")
    public R<Map<String, Object>> create(@Valid @RequestBody PageWriteRequest request) {
        return R.success(pageService.create(request));
    }

    @PutMapping("/pages/{id}")
    @RequiresPermission("lowcode:page:edit")
    @OperLog(module = "页面设计", action = "编辑页面")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody PageWriteRequest request) {
        pageService.update(id, request);
        return R.success(null);
    }

    @PutMapping("/pages/{id}/status")
    @RequiresPermission("lowcode:page:edit")
    @OperLog(module = "页面设计", action = "启停页面")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody PageStatusRequest request) {
        pageService.updateStatus(id, request.getStatus());
        return R.success(null);
    }

    @DeleteMapping("/pages/{id}")
    @RequiresPermission("lowcode:page:remove")
    @OperLog(module = "页面设计", action = "删除页面")
    public R<Void> delete(@PathVariable Long id) {
        pageService.delete(id);
        return R.success(null);
    }

    /**
     * 发布页面：生成产物并推到项目的发布分支（方案 §5.1）。
     *
     * <p>权限单独一个 {@code lowcode:page:publish}：能编辑页面的人只是改设计，而这一笔会往租户自己的
     * 仓库推代码，跟 V7 把「同步仓库」与「编辑项目」分开是同一个理由。
     */
    @PostMapping("/pages/{id}/publish")
    @RequiresPermission("lowcode:page:publish")
    @OperLog(module = "页面设计", action = "发布页面")
    public R<Map<String, Object>> publish(@PathVariable Long id, @Valid @RequestBody PagePublishRequest request) {
        return R.success(publishService.publish(id, request.isForce(), request.getChangeDesc()));
    }
}
