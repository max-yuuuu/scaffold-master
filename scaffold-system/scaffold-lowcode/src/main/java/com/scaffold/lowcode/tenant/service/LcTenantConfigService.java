package com.scaffold.lowcode.tenant.service;

import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.common.export.CsvUtils;
import com.scaffold.framework.security.SecurityUtils;
import com.scaffold.lowcode.tenant.domain.TenantConfigWriteRequest;
import com.scaffold.lowcode.tenant.mapper.LcTenantConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 租户参数（lc_tenant_config）：只作用于当前会话租户，跨租户改参数需要先切换到该租户。
 */
@Service
public class LcTenantConfigService {

    private final LcTenantConfigMapper mapper;

    public LcTenantConfigService(LcTenantConfigMapper mapper) {
        this.mapper = mapper;
    }

    public PageResult<Map<String, Object>> configs(PageQuery query, String keyword) {
        String key = trimToNull(keyword);
        long total = mapper.countConfigs(key);
        List<Map<String, Object>> rows = total == 0
                ? List.of()
                : mapper.selectConfigs(key, query.offset(), query.normalizedPageSize());
        return PageResult.of(rows.stream().map(LcTenantConfigService::booleanBuiltin).toList(),
                total, query.normalizedPageNum(), query.normalizedPageSize());
    }

    public String exportConfigs(String keyword) {
        List<Map<String, Object>> rows = mapper.selectConfigs(trimToNull(keyword), 0, 5000).stream()
                .map(LcTenantConfigService::booleanBuiltin).toList();
        List<List<Object>> data = rows.stream()
                .map(row -> CsvUtils.row(row.get("configKey"), row.get("configName"), row.get("configValue"),
                        Boolean.TRUE.equals(row.get("builtin")) ? "系统内置" : "自定义", row.get("remark"), row.get("updateTime")))
                .toList();
        return CsvUtils.csv(List.of("参数键", "参数名称", "参数值", "来源", "备注", "更新时间"), data);
    }

    /** TINYINT 经驱动回来可能是 0/1 也可能是 true/false，统一成 boolean 再给前端，与 sys_config 的 builtin 同形 */
    private static Map<String, Object> booleanBuiltin(Map<String, Object> row) {
        Map<String, Object> copy = new LinkedHashMap<>(row);
        Object value = copy.get("builtin");
        boolean on = value instanceof Boolean flag ? flag
                : value instanceof Number number && number.intValue() == 1;
        copy.put("builtin", on);
        return copy;
    }

    @Transactional
    public Map<String, Object> save(TenantConfigWriteRequest request) {
        String key = request.getConfigKey().trim();
        String operator = SecurityUtils.getUsername();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("configKey", key);
        row.put("configValue", request.getConfigValue() == null ? "" : request.getConfigValue().trim());
        row.put("configName", null);
        row.put("remark", trimToNull(request.getRemark()));

        if (request.getId() == null) {
            if (mapper.countConfigKey(key, null) > 0) {
                throw new BusinessException("参数键已存在");
            }
            row.put("builtin", 0);
            mapper.insert(row, operator);
            return Map.of("configKey", key);
        }
        if (mapper.countConfigKey(key, request.getId()) > 0) {
            throw new BusinessException("参数键已存在");
        }
        if (mapper.updateValue(request.getId(), row, operator) == 0) {
            throw new BusinessException("参数不存在或不属于当前租户");
        }
        return Map.of("id", request.getId());
    }

    @Transactional
    public void remove(Long id) {
        if (mapper.delete(id) == 0) {
            throw new BusinessException("参数不存在、不属于当前租户，或为内置参数");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
