package com.scaffold.framework.tenant;

import com.scaffold.common.tenant.TenantContext;
import com.scaffold.framework.security.LoginUser;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TenantFilterTest {

    private final TenantFilter filter = new TenantFilter();

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void publishesTenantIdFromLoginUserAndClearsAfterChain() throws ServletException, IOException {
        authenticate(new LoginUser(1L, "alice", 42L));
        AtomicReference<Long> seenInsideChain = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seenInsideChain.set(TenantContext.getTenantId());
            }
        };

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(seenInsideChain.get()).isEqualTo(42L);
        // finally 块保证请求结束后清理，避免污染线程池复用的下一个请求
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void anonymousRequestLeavesTenantContextEmpty() throws ServletException, IOException {
        AtomicReference<Long> seenInsideChain = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seenInsideChain.set(TenantContext.getTenantId());
            }
        };

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(seenInsideChain.get()).isNull();
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void principalWithoutTenantClaimLeavesContextEmptyForLegacyTokens() throws ServletException, IOException {
        // 多租户上线前的旧令牌只带 userId + username；TenantFilter 不抛错也不设置上下文，
        // 交由业务层决定是否拒绝（如 getTenantId() 会抛 401）。
        authenticate(new LoginUser(1L, "legacy", null));

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(TenantContext.getTenantId()).isNull();
    }

    private static void authenticate(LoginUser principal) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal.username(), "token", List.of());
        authentication.setDetails(principal);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
