package com.scaffold.lowcode.merge.controller;

import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.domain.R;
import com.scaffold.framework.audit.OperLog;
import com.scaffold.framework.security.RequiresPermission;
import com.scaffold.lowcode.merge.domain.MergeRequestRefreshRequest;
import com.scaffold.lowcode.merge.service.MergeRequestService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 合并请求接口（PR 8 第一刀，方案 §5.4）。
 *
 * <p>这一刀只有读与算：没有「批准」也没有「合并」。权限分两条的理由与发布链路一样 ——
 * {@code lowcode:merge-request:list} 只是看库里已经算好的结论，
 * 而 {@code lowcode:merge-request:sync} 会连远端仓库（{@code fetch} 与 {@code ls-remote}），
 * 是一次真的仓库动作，不该跟「看列表」同一个码。
 */
@RestController
@RequestMapping("/api/lowcode")
public class LcMergeRequestController {

    private final MergeRequestService service;

    public LcMergeRequestController(MergeRequestService service) {
        this.service = service;
    }

    @GetMapping("/merge-requests")
    @RequiresPermission("lowcode:merge-request:list")
    public R<PageResult<Map<String, Object>>> requests(PageQuery query, @RequestParam(required = false) Long projectId) {
        return R.success(service.list(query, projectId));
    }

    /** 详情带变更文件清单（冲突的排在最前）；打开时若目标分支已前进，这一条会当场转陈旧 */
    @GetMapping("/merge-requests/{id}")
    @RequiresPermission("lowcode:merge-request:list")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.success(service.detail(id));
    }

    /** 重算一次：fetch → 分叉点 → 变更清单 → 合并试算 → 落账。全程不动主干、不推送 */
    @PostMapping("/merge-requests/refresh")
    @RequiresPermission("lowcode:merge-request:sync")
    @OperLog(module = "合并请求", action = "重新计算合并请求")
    public R<Map<String, Object>> refresh(@Valid @RequestBody MergeRequestRefreshRequest request) {
        return R.success(service.refresh(request.getProjectId()));
    }
}
