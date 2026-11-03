package com.scaffold.lowcode.tenant.service;

import com.scaffold.common.exception.BusinessException;
import com.scaffold.common.tenant.TenantContext;
import com.scaffold.framework.security.JwtService;
import com.scaffold.framework.security.TokenStore;
import com.scaffold.lowcode.tenant.domain.TenantRegisterRequest;
import com.scaffold.lowcode.tenant.mapper.LcTenantMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 租户自助注册 = 租户开通：建租户 → 复制平台租户的业务菜单树 → 建内置管理员角色并全量授权 →
 * 建管理员账号 → 直接签发登录令牌。整程一个事务，任一步失败都不留下半个租户。
 *
 * <p>注册是匿名请求，没有租户上下文，开通用的写入都显式带 tenant_id
 *     （见 {@code LcProvisionMapper}），不依赖拦截器注入。
 */
@Service
public class TenantRegistrationService {

    private static final int DEFAULT_MAX_PROJECTS = 5;
    /** 注册即开通，租户配额先给默认值；后续由平台管理员在租户管理里调整 */
    private static final String REGISTER_OPERATOR = "register";

    private final LcTenantMapper tenantMapper;
    private final TenantProvisionService provisionService;
    private final LcTenantService tenantService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenStore tokenStore;

    public TenantRegistrationService(LcTenantMapper tenantMapper, TenantProvisionService provisionService,
                                     LcTenantService tenantService, PasswordEncoder passwordEncoder,
                                     JwtService jwtService, TokenStore tokenStore) {
        this.tenantMapper = tenantMapper;
        this.provisionService = provisionService;
        this.tenantService = tenantService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
    }

    @Transactional
    public Map<String, Object> register(TenantRegisterRequest request) {
        String tenantCode = request.getTenantCode().trim();
        String tenantName = request.getTenantName().trim();
        String username = request.getAdminUsername().trim();

        if (tenantMapper.countTenantCode(tenantCode, null) > 0) {
            throw new BusinessException("租户编码已被使用，请换一个");
        }
        if (provisionService.countUsername(username) > 0) {
            throw new BusinessException("管理员账号已被使用，请换一个");
        }

        Map<String, Object> tenant = new LinkedHashMap<>();
        tenant.put("tenantCode", tenantCode);
        tenant.put("tenantName", tenantName);
        tenant.put("contactName", null);
        tenant.put("contactPhone", null);
        tenant.put("maxProjects", DEFAULT_MAX_PROJECTS);
        tenant.put("expireDate", null);
        tenant.put("status", 1);
        tenant.put("remark", "自助注册开通");
        tenantMapper.insertTenant(tenant, REGISTER_OPERATOR);
        long tenantId = numberAsLong(tenant.get("id"));
        tenantService.seedDefaultConfigs(tenantId, REGISTER_OPERATOR);

        // 菜单树、字典、内置参数与内置管理员角色一次性按平台模板开通
        Map<String, Object> assets = provisionService.syncAssets(tenantId, REGISTER_OPERATOR);
        long roleId = ((Number) assets.get("roleId")).longValue();
        long userId = provisionService.createAdminUser(tenantId, username, request.getPassword(), roleId, REGISTER_OPERATOR);
        provisionService.recordRegistration(tenantId, username);

        String token = jwtService.generateToken(userId, username, tenantId);
        tokenStore.store(token, username, jwtService.expiresSeconds());
        return Map.of(
                "token", token,
                "expiresIn", jwtService.expiresSeconds(),
                "tenantId", tenantId,
                "tenantCode", tenantCode,
                "menuCount", assets.get("menuAdded")
        );
    }

    private static long numberAsLong(Object value) {
        if (value == null) {
            throw new BusinessException("租户开通失败：未取到自增主键");
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
