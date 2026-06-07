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
 */
public record PaymentFailedEvent(
    UUID paymentId,
    UUID invoiceId,
    String invoiceNumber,
    UUID tenantId,
    String tenantName,
    BigDecimal amount,
    String currency,
    String externalPaymentId,
    String failureReason,
    String providerCode,
    Instant failedAt
) {
    public static PaymentFailedEvent of(
        UUID paymentId,
        UUID invoiceId,
        String invoiceNumber,
        UUID tenantId,
        String tenantName,
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
            amount,
            currency,
            externalPaymentId,
            failureReason,
            providerCode,
            Instant.now()
        );
    }
}
