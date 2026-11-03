package com.scaffold.lowcode.tenant.service;

import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.common.export.CsvUtils;
import com.scaffold.framework.security.SecurityUtils;
import com.scaffold.lowcode.tenant.domain.TenantStatusRequest;
import com.scaffold.lowcode.tenant.domain.TenantWriteRequest;
import com.scaffold.lowcode.tenant.mapper.LcTenantConfigMapper;
import com.scaffold.lowcode.tenant.mapper.LcTenantMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 租户管理（平台侧）。lc_tenant 是平台表，按主键与编码定位，不参与租户注入；
 * 停用的租户会在登录时被拒绝（{@code AuthService.login} 校验 lc_tenant.status）。
 */
@Service
public class LcTenantService {

    /** 平台租户不可停用，否则平台管理员自己会被锁在登录之外 */
    private static final long PLATFORM_TENANT_ID = 1L;
    private static final int DEFAULT_MAX_PROJECTS = 5;

    private final LcTenantMapper tenantMapper;
    private final LcTenantConfigMapper configMapper;
    private final TenantProvisionService provisionService;

    public LcTenantService(LcTenantMapper tenantMapper, LcTenantConfigMapper configMapper,
                           TenantProvisionService provisionService) {
        this.tenantMapper = tenantMapper;
        this.configMapper = configMapper;
        this.provisionService = provisionService;
    }

    /**
     * 给某个租户补齐平台模板里新增的菜单 / 字典 / 内置参数，并把业务菜单授给它的内置管理员角色。
     * 平台每次用迁移脚本新增租户可见菜单后（例如 V5 的「低代码」段），已有租户靠这一步拿到，
     * 新租户注册时自带。幂等：缺什么补什么，重复执行不产生第二份。
     */
    public Map<String, Object> syncAssets(Long tenantId) {
        if (tenantMapper.selectTenantBrief(tenantId) == null) {
            throw new BusinessException("租户不存在");
        }
        return provisionService.syncAssets(tenantId, operator());
    }

    public PageResult<Map<String, Object>> tenants(PageQuery query, String keyword, Integer status) {
        long total = tenantMapper.countTenants(trimToNull(keyword), status);
        List<Map<String, Object>> rows = total == 0
                ? List.of()
                : tenantMapper.selectTenants(trimToNull(keyword), status, query.offset(), query.normalizedPageSize());
        return PageResult.of(rows, total, query.normalizedPageNum(), query.normalizedPageSize());
    }

    public String exportTenants(String keyword, Integer status) {
        List<Map<String, Object>> rows = tenantMapper.selectTenants(trimToNull(keyword), status, 0, 5000);
        List<List<Object>> data = rows.stream()
                .map(row -> CsvUtils.row(value(row, "tenantCode"), value(row, "tenantName"), value(row, "contactName"),
                        value(row, "contactPhone"), value(row, "maxProjects"), value(row, "userCount"),
                        value(row, "expireDate"), statusText(row.get("status")), value(row, "createTime"), value(row, "remark")))
                .toList();
        return CsvUtils.csv(List.of("租户编码", "租户名称", "联系人", "联系电话", "项目配额", "账号数", "到期日期", "状态", "创建时间", "备注"), data);
    }

    @Transactional
    public Map<String, Object> create(TenantWriteRequest request) {
        String tenantCode = required(request.getTenantCode(), "租户编码不能为空");
        if (tenantMapper.countTenantCode(tenantCode, null) > 0) {
            throw new BusinessException("租户编码已存在");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tenantCode", tenantCode);
        row.put("tenantName", required(request.getTenantName(), "租户名称不能为空"));
        row.put("contactName", trimToNull(request.getContactName()));
        row.put("contactPhone", trimToNull(request.getContactPhone()));
        row.put("maxProjects", request.getMaxProjects() == null ? DEFAULT_MAX_PROJECTS : request.getMaxProjects());
        row.put("expireDate", blankToNull(request.getExpireDate()));
        row.put("status", request.getStatus() == null ? 1 : request.getStatus());
        row.put("remark", trimToNull(request.getRemark()));
        String operator = operator();
        tenantMapper.insertTenant(row, operator);
        long id = numberAsLong(row.get("id"));
        seedDefaultConfigs(id, operator);
        return Map.of("id", id);
    }

    @Transactional
    public void update(Long id, TenantWriteRequest request) {
        if (tenantMapper.selectTenantBrief(id) == null) {
            throw new BusinessException("租户不存在");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tenantName", trimToNull(request.getTenantName()));
        row.put("contactName", trimToNull(request.getContactName()));
        row.put("contactPhone", trimToNull(request.getContactPhone()));
        row.put("maxProjects", request.getMaxProjects());
        row.put("expireDate", blankToNull(request.getExpireDate()));
        row.put("status", request.getStatus());
        row.put("remark", trimToNull(request.getRemark()));
        if (tenantMapper.updateTenant(id, row, operator()) == 0) {
            throw new BusinessException("租户不存在或已删除");
        }
    }

    @Transactional
    public void updateStatus(Long id, TenantStatusRequest request) {
        int status = request.getStatus();
        if (status != 0 && status != 1) {
            throw new BusinessException("状态只能为 1 正常或 0 停用");
        }
        if (status == 0 && id == PLATFORM_TENANT_ID) {
            throw new BusinessException("平台租户不能停用");
        }
        Map<String, Object> brief = tenantMapper.selectTenantBrief(id);
        if (brief == null) {
            throw new BusinessException("租户不存在");
        }
        if (tenantMapper.updateTenantStatus(id, status, operator()) == 0) {
            throw new BusinessException("租户不存在或已删除");
        }
    }

    /**
     * 为某个租户写入生成链路的分支默认值（PR 3 建项目、PR 4 发布会读这两个键）。
     * 配置写入 lc_tenant_config：该表带 tenant_id，需按目标租户落数据，而当前会话可能还在平台租户，
     * 所以这里显式指定 tenant_id 而不是依赖拦截器注入。
     */
    /**
     * 供租户管理与自助注册共用：新租户都要有这两条分支默认值。
     * operator 由调用方传入 —— 注册是匿名请求，取不到登录用户。
     */
    public void seedDefaultConfigs(long tenantId, String operator) {
        insertConfig(tenantId, "project.main_branch", "main", "生成项目的目标分支（人工合并的落点）", operator);
        insertConfig(tenantId, "project.gen_branch", "platform-generated", "平台发布只推这条分支", operator);
    }

    private void insertConfig(long tenantId, String key, String value, String name, String operator) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tenantId", tenantId);
        row.put("configKey", key);
        row.put("configValue", value);
        row.put("configName", name);
        row.put("builtin", 1);
        row.put("remark", null);
        configMapper.insertForTenant(row, operator);
    }

    private static String statusText(Object value) {
        if (value == null) {
            return "";
        }
        return numberAsLong(value) == 1L ? "正常" : "停用";
    }

    private String operator() {
        return SecurityUtils.getUsername();
    }

    private static String required(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BusinessException(message);
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Object value(Map<String, Object> row, String key) {
        return row.get(key);
    }

    private static long numberAsLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
