package com.scaffold.lowcode.tenant.controller;

import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.domain.R;
import com.scaffold.framework.audit.OperLog;
import com.scaffold.framework.security.RequiresPermission;
import com.scaffold.framework.web.CsvResponse;
import com.scaffold.lowcode.tenant.domain.TenantConfigWriteRequest;
import com.scaffold.lowcode.tenant.service.LcTenantConfigService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 租户参数接口：读写都限定在当前会话租户内，平台管理员要改别的租户先切换过去。
 */
@RestController
@RequestMapping("/api/platform/tenant-configs")
public class LcTenantConfigController {

    private final LcTenantConfigService service;

    public LcTenantConfigController(LcTenantConfigService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission("platform:config:list")
    public R<PageResult<Map<String, Object>>> configs(
            PageQuery query,
            @RequestParam(required = false) String keyword
    ) {
        return R.success(service.configs(query, keyword));
    }

    @GetMapping("/export")
    @RequiresPermission("platform:config:list")
    public ResponseEntity<String> exportConfigs(@RequestParam(required = false) String keyword) {
        return CsvResponse.attachment("tenant-configs.csv", service.exportConfigs(keyword));
    }

    @PutMapping
    @RequiresPermission("platform:config:edit")
    @OperLog(module = "租户参数", action = "保存参数")
    public R<Map<String, Object>> save(@Valid @RequestBody TenantConfigWriteRequest request) {
        return R.success(service.save(request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("platform:config:edit")
    @OperLog(module = "租户参数", action = "删除参数")
    public R<Void> remove(@PathVariable Long id) {
        service.remove(id);
        return R.success(null);
    }
}
