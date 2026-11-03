package com.scaffold.lowcode.tenant.controller;

import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.domain.R;
import com.scaffold.framework.audit.OperLog;
import com.scaffold.framework.security.RequiresPermission;
import com.scaffold.framework.web.CsvResponse;
import com.scaffold.lowcode.tenant.domain.TenantStatusRequest;
import com.scaffold.lowcode.tenant.domain.TenantWriteRequest;
import com.scaffold.lowcode.tenant.service.LcTenantService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
 * 平台侧租户管理接口。仅平台管理员（super_admin）持有 platform:tenant:* 权限码。
 */
@RestController
@RequestMapping("/api/platform/tenants")
public class LcTenantController {

    private final LcTenantService service;

    public LcTenantController(LcTenantService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission("platform:tenant:list")
    public R<PageResult<Map<String, Object>>> tenants(
            PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status
    ) {
        return R.success(service.tenants(query, keyword, status));
    }

    @GetMapping("/export")
    @RequiresPermission("platform:tenant:list")
    public ResponseEntity<String> exportTenants(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status
    ) {
        return CsvResponse.attachment("tenants.csv", service.exportTenants(keyword, status));
    }

    @PostMapping
    @RequiresPermission("platform:tenant:add")
    @OperLog(module = "租户管理", action = "新增租户")
    public R<Map<String, Object>> create(@Valid @RequestBody TenantWriteRequest request) {
        return R.success(service.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("platform:tenant:edit")
    @OperLog(module = "租户管理", action = "编辑租户")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody TenantWriteRequest request) {
        service.update(id, request);
        return R.success(null);
    }

    @PutMapping("/{id}/status")
    @RequiresPermission("platform:tenant:edit")
    @OperLog(module = "租户管理", action = "启停租户")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody TenantStatusRequest request) {
        service.updateStatus(id, request);
        return R.success(null);
    }

    /**
     * 给该租户补齐平台模板里新增的菜单 / 字典 / 内置参数。
     * 平台每次用迁移脚本加了租户可见菜单后（例如 V5 的「低代码」段），已有租户靠这一步拿到；幂等，可重复执行。
     */
    @PostMapping("/{id}/assets/sync")
    @RequiresPermission("platform:tenant:edit")
    @OperLog(module = "租户管理", action = "补齐租户资产")
    public R<Map<String, Object>> syncAssets(@PathVariable Long id) {
        return R.success(service.syncAssets(id));
    }
}
