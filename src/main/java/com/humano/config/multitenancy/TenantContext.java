package com.humano.config.multitenancy;

/**
 * Thread-local storage for current tenant context.
 * Used to determine which database to route operations to.
 *
 * @author Humano Team
 */
public final class TenantContext {

    /**
     * HTTP session attribute name that pins an authenticated session to a tenant subdomain.
     * Written by the login success handler; checked by {@code TenantResolutionFilter} on
     * every subsequent request so a stolen session cookie cannot be reused across tenants.
     */
    public static final String SESSION_TENANT_ATTRIBUTE = "humano.tenant";

    /** SLF4J MDC key used by {@code TenantResolutionFilter} and the async {@code TaskDecorator}. */
    public static final String MDC_TENANT_KEY = "tenant";

    /**
     * Reserved subdomain used for platform/admin operations against the master DB. Business
     * endpoints under {@code /api/**} reject this value.
     */
    public static final String MASTER = "master";

    private static final ThreadLocal<String> CURRENT_TENANT = new InheritableThreadLocal<>();

    private TenantContext() {
        // Utility class, prevent instantiation
    }

    /**
     * Sets the current tenant identifier for this thread.
     *
     * @param tenantId the tenant identifier
     */
    public static void setCurrentTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    /**
     * Gets the current tenant identifier for this thread.
     *
     * @return the tenant identifier, or null if not set
     */
    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    /**
     * Clears the current tenant context.
     * Should be called after request processing to prevent memory leaks.
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }

    /**
     * Checks if the current context is the master database context.
     *
     * @return true if in master context, false otherwise
     */
    public static boolean isMasterContext() {
        String tenant = CURRENT_TENANT.get();
        return tenant == null || MASTER.equals(tenant);
    }

    /**
     * Checks if a tenant is currently set in the context.
     *
     * @return true if a tenant is set, false otherwise
     */
    public static boolean hasTenant() {
        String tenant = CURRENT_TENANT.get();
        return tenant != null && !MASTER.equals(tenant);
    }
}
