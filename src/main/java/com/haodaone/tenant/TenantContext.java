package com.haodaone.tenant;

/**
 * Simple ThreadLocal holder for the current request's tenant/company id.
 * Set by TenantResolverFilter after authentication and used by services
 * and repositories to enforce tenant-scoped operations.
 */
public final class TenantContext {
    private static final ThreadLocal<Long> current = new ThreadLocal<>();

    public static void setCurrentTenant(Long companyId) {
        current.set(companyId);
    }

    public static Long getCurrentTenant() {
        return current.get();
    }

    public static void clear() {
        current.remove();
    }

    private TenantContext() {}
}
