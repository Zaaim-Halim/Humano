package com.humano.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * P4.2 / P4.3 — Published when a payment moves to {@code FAILED}, whether via
 * the provider's synchronous response (CardException) or via the asynchronous
 * Stripe webhook ({@code payment_intent.payment_failed}). Async listeners can
 * react by triggering dunning state advancement (P4.4) or a "payment failed"
 * customer email (P4.3).
 *
 * <p>Implements {@link TenantScopedEvent}: dunning runs against tenant-scoped
 * subscription state, so the listener must route via {@code tenantSubdomain()}.
 */
public record PaymentFailedEvent(
    UUID paymentId,
    UUID invoiceId,
    String invoiceNumber,
    UUID tenantId,
    String tenantName,
    String tenantSubdomain,
    BigDecimal amount,
    String currency,
    String externalPaymentId,
    String failureReason,
    String providerCode,
    Instant failedAt
)
    implements TenantScopedEvent {
    public static PaymentFailedEvent of(
        UUID paymentId,
        UUID invoiceId,
        String invoiceNumber,
        UUID tenantId,
        String tenantName,
        String tenantSubdomain,
        BigDecimal amount,
        String currency,
        String externalPaymentId,
        String failureReason,
        String providerCode
    ) {
        return new PaymentFailedEvent(
            paymentId,
            invoiceId,
            invoiceNumber,
            tenantId,
            tenantName,
            tenantSubdomain,
            amount,
            currency,
            externalPaymentId,
            failureReason,
            providerCode,
            Instant.now()
        );
    }
}
