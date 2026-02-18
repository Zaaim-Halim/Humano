package com.humano.config.multitenancy;

/**
 * Thread-local storage for current tenant context.
 * Used to determine which database to route operations to.
 *
 * @author Humano Team
 */
public final class TenantContext {

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
        return tenant == null || "master".equals(tenant);
    }

    /**
     * Checks if a tenant is currently set in the context.
     *
     * @return true if a tenant is set, false otherwise
     */
    public static boolean hasTenant() {
        String tenant = CURRENT_TENANT.get();
        return tenant != null && !"master".equals(tenant);
    }
}
