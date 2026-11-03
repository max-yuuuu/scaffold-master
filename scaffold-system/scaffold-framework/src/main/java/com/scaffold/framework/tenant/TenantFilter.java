package com.scaffold.framework.tenant;

import com.scaffold.common.tenant.TenantContext;
import com.scaffold.framework.security.LoginUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 从 {@link com.scaffold.framework.security.JwtAuthenticationFilter} 已经写入的 {@link LoginUser}
 * 中读取 tenantId 并挂到 {@link TenantContext}，供 MyBatis 拦截器与业务代码消费；请求结束时清理。
 *
 * <p>未登录接口（{@code @Anonymous}、健康检查、验证码、登录本身）拿不到 tenantId，直接放行不设置上下文。
 * 后续 MyBatis 拦截器遇到 {@code TenantContext.getTenantId() == null} 时按业务规则决定放行或拒绝。
 */
@Component
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Long tenantId = currentTenantId();
            if (tenantId != null) {
                TenantContext.set(tenantId);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private static Long currentTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getDetails() instanceof LoginUser loginUser)) {
            return null;
        }
        return loginUser.tenantId();
    }
}
