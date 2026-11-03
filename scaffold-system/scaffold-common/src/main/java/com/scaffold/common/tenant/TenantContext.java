package com.scaffold.common.tenant;

/**
 * 当前请求的租户上下文。由 {@code scaffold-framework} 的 TenantFilter 从 JWT 主体解析后写入，
 * 请求结束时清理。业务代码禁止直接 set，只通过 SecurityUtils 或本类的 get 读取。
 *
 * <p>ThreadLocal 语义：每个请求线程独立、异步任务不继承。跨线程池场景需要显式传递
 * {@link #getTenantId()} 的结果到新线程并 {@link #set(Long)}。
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    /** 平台自身租户 ID，V2 种子数据全部归属此租户 */
    public static final long PLATFORM_TENANT_ID = 1L;

    private TenantContext() {
    }

    public static void set(Long tenantId) {
        CURRENT.set(tenantId);
    }

    /** 取当前线程的租户 ID；未登录或未初始化上下文时为 null。 */
    public static Long getTenantId() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
