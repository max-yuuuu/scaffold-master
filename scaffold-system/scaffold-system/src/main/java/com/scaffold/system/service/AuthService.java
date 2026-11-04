package com.scaffold.system.service;

import com.scaffold.common.exception.BusinessException;
import com.scaffold.common.tenant.TenantContext;
import com.scaffold.framework.security.JwtService;
import com.scaffold.framework.security.LoginUser;
import com.scaffold.framework.security.PlatformBoundary;
import com.scaffold.framework.security.SecurityUtils;
import com.scaffold.framework.security.TokenStore;
import com.scaffold.system.domain.AuthUserRow;
import com.scaffold.system.domain.MenuRow;
import com.scaffold.system.domain.dto.ChangePasswordRequest;
import com.scaffold.system.domain.dto.LoginRequest;
import com.scaffold.system.domain.vo.CurrentUserResponse;
import com.scaffold.system.domain.vo.LoginResponse;
import com.scaffold.system.domain.vo.RouteVO;
import com.scaffold.system.mapper.AuthMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    /** 账号无法定位到唯一租户时登录日志的归属占位租户 */
    private static final Long UNATTRIBUTED_TENANT = TenantContext.PLATFORM_TENANT_ID;
    /** 账号或租户定位失败时统一回给前端的提示，避免暴露租户是否存在 */
    private static final String INVALID_CREDENTIAL = "账号或密码错误";
    private static final String AMBIGUOUS_ACCOUNT = "该账号存在于多个租户，请填写租户编码";

    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenStore tokenStore;

    public AuthService(AuthMapper authMapper, PasswordEncoder passwordEncoder, JwtService jwtService, TokenStore tokenStore) {
        this.authMapper = authMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
    }

    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername();
        String tenantCode = normalize(request.getTenantCode());
        Long hintTenantId = tenantCode == null ? null : authMapper.selectTenantIdByCode(tenantCode);
        AuthUserRow user = resolveLoginUser(username, tenantCode, hintTenantId);
        if (user == null) {
            // 租户编码不存在或该租户下没有此账号，统一按凭证错误提示，不暴露租户是否存在
            rejectLogin(username, hintTenantId, INVALID_CREDENTIAL);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            rejectLogin(user, INVALID_CREDENTIAL);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            rejectLogin(user, "账号已停用");
        }
        if (authMapper.countActiveTenant(user.getTenantId()) == 0) {
            rejectLogin(user, "所属租户已停用");
        }
        String token = jwtService.generateToken(user.getId(), user.getUsername(), user.getTenantId());
        tokenStore.store(token, user.getUsername(), jwtService.expiresSeconds());
        authMapper.insertLoginLog(user.getTenantId(), user.getUsername(), 1, "登录成功");
        authMapper.updateLastLoginTime(user.getId());
        return new LoginResponse(token, jwtService.expiresSeconds());
    }

    /**
     * 平台管理员切换租户：重新签发一张 tenantId 指向目标租户的令牌并吊销旧令牌，
     * 身份与权限仍按管理员自己的角色解析（见 {@code AuthMapper} 上跳过租户注入的查询），
     * 业务数据查询按新租户过滤，等价于「带着平台管理员身份进入该租户查看」。
     *
     * @param tenantId 目标租户 ID；传 null 回到自己的平台租户
     */
    public LoginResponse switchTenant(Long tenantId) {
        LoginUser current = SecurityUtils.getLoginUser();
        AuthUserRow self = current.userId() == null ? null : authMapper.selectUserById(current.userId());
        if (self == null || self.getStatus() == null || self.getStatus() != 1) {
            throw new BusinessException(401, "未认证或登录已过期");
        }
        if (!PlatformBoundary.isSuperAdmin(self.getTenantId(), authMapper.selectRoleCodes(self.getId()))) {
            throw new BusinessException(403, "只有平台管理员可以切换租户");
        }
        long homeTenantId = self.getTenantId() == null ? TenantContext.PLATFORM_TENANT_ID : self.getTenantId();
        long targetTenantId = tenantId == null ? homeTenantId : tenantId;
        if (targetTenantId != homeTenantId && authMapper.countActiveTenant(targetTenantId) == 0) {
            throw new BusinessException("目标租户不存在或已停用");
        }

        String token = jwtService.generateToken(self.getId(), self.getUsername(), targetTenantId);
        tokenStore.store(token, self.getUsername(), jwtService.expiresSeconds());
        String previousToken = currentCredentials();
        if (previousToken != null) {
            tokenStore.revoke(previousToken);
        }
        String label = targetTenantId == homeTenantId
                ? "回到平台租户"
                : "切换到租户 " + authMapper.selectTenantCodeById(targetTenantId);
        authMapper.insertLoginLog(targetTenantId, self.getUsername(), 1, label);
        return new LoginResponse(token, jwtService.expiresSeconds());
    }

    public void logout() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getCredentials() instanceof String token) {
            tokenStore.revoke(token);
            LoginUser loginUser = authentication.getDetails() instanceof LoginUser user ? user : null;
            Long tenantId = loginUser != null && loginUser.tenantId() != null ? loginUser.tenantId() : UNATTRIBUTED_TENANT;
            authMapper.insertLoginLog(tenantId, String.valueOf(authentication.getPrincipal()), 1, "退出登录");
        }
        SecurityContextHolder.clearContext();
    }

    public CurrentUserResponse currentUser() {
        LoginUser session = SecurityUtils.getLoginUser();
        AuthUserRow user = currentUserRow();
        List<String> roleCodes = authMapper.selectRoleCodes(user.getId());
        Long actingTenantId = session.tenantId() == null ? user.getTenantId() : session.tenantId();
        Map<String, Object> tenant = authMapper.selectTenantBrief(actingTenantId);
        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getDeptId(),
                user.getDeptName(),
                actingTenantId,
                tenant == null ? null : String.valueOf(tenant.get("tenantCode")),
                tenant == null ? null : String.valueOf(tenant.get("tenantName")),
                user.getTenantId(),
                roleCodes,
                PlatformBoundary.isSuperAdmin(user.getTenantId(), roleCodes)
                        ? authMapper.selectAllPermissions() : authMapper.selectPermissions(user.getId())
        );
    }

    public Map<String, Object> routes() {
        AuthUserRow user = currentUserRow();
        // 超级管理员不受角色菜单分配限制，新增菜单无需再授权即可见；菜单集仍按当前租户取
        List<MenuRow> menus = PlatformBoundary.isSuperAdmin(user.getTenantId(), authMapper.selectRoleCodes(user.getId()))
                ? authMapper.selectAllRouteMenus()
                : authMapper.selectRouteMenus(user.getId());
        return Map.of("routes", buildRoutes(menus));
    }

    public void changePassword(ChangePasswordRequest request) {
        AuthUserRow user = currentUserRow();
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("旧密码不正确");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException("新密码不能与旧密码一致");
        }
        int updated = authMapper.updatePassword(user.getId(), user.getUsername(), passwordEncoder.encode(request.getNewPassword()));
        if (updated == 0) {
            throw new BusinessException("密码修改失败");
        }
    }

    /**
     * 填了租户编码就按「账号 + 租户」精确定位；没填时按账号全局检索，
     * 命中多个租户的账号视为歧义，要求补租户编码，绝不取第一条。
     */
    private AuthUserRow resolveLoginUser(String username, String tenantCode, Long hintTenantId) {
        if (tenantCode != null) {
            return hintTenantId == null ? null : authMapper.selectUserByUsernameAndTenant(username, hintTenantId);
        }
        List<AuthUserRow> candidates = authMapper.selectUsersByUsername(username);
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() > 1) {
            authMapper.insertLoginLog(UNATTRIBUTED_TENANT, username, 0, AMBIGUOUS_ACCOUNT);
            throw new BusinessException(AMBIGUOUS_ACCOUNT);
        }
        return candidates.get(0);
    }

    private void rejectLogin(String username, Long hintTenantId, String message) {
        authMapper.insertLoginLog(hintTenantId == null ? UNATTRIBUTED_TENANT : hintTenantId, username, 0, message);
        throw new BusinessException(message);
    }

    private void rejectLogin(AuthUserRow user, String message) {
        authMapper.insertLoginLog(user.getTenantId(), user.getUsername(), 0, message);
        throw new BusinessException(message);
    }

    private AuthUserRow currentUserRow() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        AuthUserRow user = loginUser.userId() == null ? null : authMapper.selectUserById(loginUser.userId());
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(401, "未认证或登录已过期");
        }
        return user;
    }

    private String currentCredentials() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getCredentials() instanceof String token ? token : null;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private List<RouteVO> buildRoutes(List<MenuRow> menus) {
        Map<Long, RouteVO> byId = new LinkedHashMap<>();
        menus.stream()
                .sorted(Comparator.comparing(MenuRow::getParentId).thenComparing(MenuRow::getSort).thenComparing(MenuRow::getId))
                .forEach(menu -> byId.put(menu.getId(), toRoute(menu)));
        List<RouteVO> roots = new ArrayList<>();
        for (RouteVO route : byId.values()) {
            RouteVO parent = byId.get(route.getParentId());
            if (parent == null) {
                roots.add(route);
            } else {
                parent.getChildren().add(route);
            }
        }
        return roots;
    }

    private RouteVO toRoute(MenuRow menu) {
        RouteVO route = new RouteVO();
        route.setId(menu.getId());
        route.setParentId(menu.getParentId());
        route.setName(menu.getMenuName());
        route.setPath(menu.getPath());
        route.setComponent(menu.getComponent());
        route.setIcon(menu.getIcon());
        route.setPermission(menu.getPermission());
        return route;
    }
}
