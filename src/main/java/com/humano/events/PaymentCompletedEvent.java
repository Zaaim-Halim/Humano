package com.humano.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a payment is completed.
 * This can trigger actions like:
 * - Activating tenant/subscription
 * - Sending receipt emails
 * - Updating accounting systems
 *
 * <p>Implements {@link TenantScopedEvent}: listeners that update tenant-scoped
 * accounting/audit data must route via {@code tenantSubdomain()}.
 */
public record PaymentCompletedEvent(
    UUID paymentId,
    UUID invoiceId,
    String invoiceNumber,
    UUID tenantId,
    String tenantName,
    String tenantSubdomain,
    BigDecimal amount,
    String currency,
    String paymentMethod,
    String externalPaymentId,
    Instant completedAt
)
    implements TenantScopedEvent {
    public static PaymentCompletedEvent of(
        UUID paymentId,
        UUID invoiceId,
        String invoiceNumber,
        UUID tenantId,
        String tenantName,
        String tenantSubdomain,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        String externalPaymentId
    ) {
        return new PaymentCompletedEvent(
            paymentId,
            invoiceId,
            invoiceNumber,
            tenantId,
            tenantName,
            tenantSubdomain,
            amount,
            currency,
            paymentMethod,
            externalPaymentId,
            Instant.now()
        );
    }
}
