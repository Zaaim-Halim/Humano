package com.humano.events;

import com.humano.domain.enumeration.billing.SubscriptionStatus;
import com.humano.domain.enumeration.tenant.TenantStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a tenant's status changes.
 * This can trigger actions like:
 * - Enabling/disabling access
 * - Sending notifications
 * - Updating external systems
 */
public record TenantStatusChangedEvent(
    UUID tenantId,
    String tenantName,
    TenantStatus previousStatus,
    TenantStatus newStatus,
    String reason,
    Instant changedAt
) {
    public static TenantStatusChangedEvent of(
        UUID tenantId,
        String tenantName,
        TenantStatus previousStatus,
        TenantStatus newStatus,
        String reason
    ) {
        return new TenantStatusChangedEvent(tenantId, tenantName, previousStatus, newStatus, reason, Instant.now());
    }
}
