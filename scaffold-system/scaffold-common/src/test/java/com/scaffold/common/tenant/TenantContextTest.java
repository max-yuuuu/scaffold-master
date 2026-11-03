package com.scaffold.common.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void setAndGetIsThreadLocal() {
        TenantContext.set(42L);

        assertThat(TenantContext.getTenantId()).isEqualTo(42L);
    }

    @Test
    void startsEmptyAndClearResets() {
        assertThat(TenantContext.getTenantId()).isNull();

        TenantContext.set(1L);
        TenantContext.clear();

        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void childThreadDoesNotInheritParentContext() throws Exception {
        TenantContext.set(1L);
        AtomicReference<Long> seen = new AtomicReference<>();
        Thread child = new Thread(() -> seen.set(TenantContext.getTenantId()));
        child.start();
        child.join();

        assertThat(seen.get()).isNull();
        assertThat(TenantContext.getTenantId()).isEqualTo(1L);
    }

    @Test
    void exposesPlatformTenantIdConstant() {
        assertThat(TenantContext.PLATFORM_TENANT_ID).isEqualTo(1L);
    }
}
