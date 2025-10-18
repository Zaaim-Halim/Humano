package com.humano.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Utility class to store and retrieve the current tenant ID in a ThreadLocal.
 * This ensures that tenant context is isolated between different request threads.
 *
 * This class is not a Spring component as it needs to be accessible from anywhere
 * without dependency injection in a multi-tenant environment.
 */
public final class TenantContextHolder {
    private static final Logger log = LoggerFactory.getLogger(TenantContextHolder.class);

    private static final ThreadLocal<UUID> CONTEXT = new ThreadLocal<>();

    // Private constructor to prevent instantiation
    private TenantContextHolder() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Set the current tenant ID for this thread.
     *
     * @param tenantId the tenant ID to set
     */
    public static void setCurrentTenantId(UUID tenantId) {
        if (tenantId == null) {
            log.warn("Attempt to set null tenant ID");
            return;
        }

        log.debug("Setting tenant context: {}", tenantId);
        CONTEXT.set(tenantId);
    }

    /**
     * Get the current tenant ID from the thread context.
     *
     * @return the current tenant ID or null if not set
     */
    public static UUID getCurrentTenantId() {
        UUID tenantId = CONTEXT.get();
        if (tenantId == null) {
            log.debug("No tenant context found");
        }
        return tenantId;
    }

    /**
     * Clear the current tenant ID from the thread.
     * This should be called after request processing is complete to prevent memory leaks.
     */
    public static void clearCurrentTenant() {
        log.debug("Clearing tenant context");
        CONTEXT.remove();
    }

    /**
     * Check if a tenant context exists for the current thread.
     *
     * @return true if a tenant context exists
     */
    public static boolean hasCurrentTenant() {
        return CONTEXT.get() != null;
    }

    /**
     * Execute the given action within the context of the specified tenant.
     * The previous tenant context is restored after execution.
     *
     * @param tenantId the tenant ID to use during execution
     * @param action the action to execute
     */
    public static void executeWithTenant(UUID tenantId, Runnable action) {
        UUID previousTenantId = getCurrentTenantId();
        try {
            setCurrentTenantId(tenantId);
            action.run();
        } finally {
            // Restore the previous tenant context or clear if there was none
            if (previousTenantId != null) {
                setCurrentTenantId(previousTenantId);
            } else {
                clearCurrentTenant();
            }
        }
    }
}
