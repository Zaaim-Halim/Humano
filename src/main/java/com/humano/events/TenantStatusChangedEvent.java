package com.humano.events;

import com.humano.domain.enumeration.tenant.TenantStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a tenant's status changes.
 * This can trigger actions like:
 * - Enabling/disabling access
 * - Sending notifications
 * - Updating external systems
 *
 * <p>Implements {@link TenantScopedEvent}: listeners that touch tenant-scoped data
 * (e.g. flipping per-tenant feature flags on activation) must route via
 * {@code tenantSubdomain()}.
 */
public record TenantStatusChangedEvent(
    UUID tenantId,
    String tenantName,
    String tenantSubdomain,
    TenantStatus previousStatus,
    TenantStatus newStatus,
    String reason,
    Instant changedAt
)
    implements TenantScopedEvent {
    public static TenantStatusChangedEvent of(
        UUID tenantId,
        String tenantName,
        String tenantSubdomain,
        TenantStatus previousStatus,
        TenantStatus newStatus,
        String reason
    ) {
        return new TenantStatusChangedEvent(tenantId, tenantName, tenantSubdomain, previousStatus, newStatus, reason, Instant.now());
    }
}
