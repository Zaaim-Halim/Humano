package com.humano.service.billing.payment;

import java.math.BigDecimal;
import java.util.Map;

/**
 * P4.2 — Payment provider abstraction.
 * <p>
 * Hides the chosen processor behind a small operation-shaped surface so
 * {@link com.humano.service.billing.PaymentService} can charge / refund / create
 * a setup intent without knowing about Stripe specifics. Today the only impl is
 * {@link StripePaymentProvider}; future providers (Adyen, Braintree) plug in by
 * implementing this interface and registering as a Spring bean.
 * <p>
 * <b>Idempotency.</b> Every {@link #charge} call takes a caller-supplied
 * {@code idempotencyKey}. The provider must guarantee that two calls with the
 * same key + same payload return the same transaction id and do not double-charge.
 * The Humano-side key is {@code "payment-" + Payment.id} so a Spring-Retry / hand
 * re-invocation of {@code PaymentService.retryPayment} is safe.
 * <p>
 * <b>Failure model.</b> Implementations throw {@link PaymentProviderException} for
 * any non-success outcome. Card declines, network errors, and processor outages
 * all map to the same exception type with a typed {@code Kind} so the calling
 * service can decide between retry-now / fail-now / surface-to-user.
 */
public interface PaymentProvider {
    /**
     * Authorise + capture a charge against the supplied payment method token.
     *
     * @param amount         money to charge in major units (e.g. 12.34 EUR).
     * @param currency       ISO 4217 code; the provider validates support.
     * @param methodToken    provider-issued payment-method handle (e.g. Stripe pm_xxx).
     * @param idempotencyKey caller-supplied dedupe key; same key + same body returns the same charge.
     * @return charge outcome — transaction id + status + raw provider response.
     */
    ChargeResult charge(BigDecimal amount, String currency, String methodToken, String idempotencyKey);

    /**
     * Refund a previously-completed charge.
     *
     * @param transactionId provider-side charge id returned from {@link #charge}.
     * @param amount        partial refund amount in major units; null means full refund.
     * @param currency      ISO 4217 code of the original charge; needed to convert {@code amount}
     *                      to the provider's minor units at the currency's correct scale.
     * @return refund outcome — refund id + raw provider response.
     */
    RefundResult refund(String transactionId, BigDecimal amount, String currency);

    /**
     * Create a SetupIntent so a tenant can save a card without immediately charging it.
     *
     * @param customerKey provider-side customer reference (Stripe cus_xxx) or null to create a guest intent.
     * @return setup intent — client secret to hand to the SPA + raw provider response.
     */
    SetupIntentResult createSetupIntent(String customerKey);

    /**
     * Charge outcome.
     *
     * @param transactionId   provider-side id (Stripe PaymentIntent id).
     * @param status          provider-reported status. Mapped to {@code PaymentStatus} by the caller.
     * @param providerMetadata raw provider response payload — persisted on {@code Payment.providerMetadata}.
     */
    record ChargeResult(String transactionId, String status, Map<String, Object> providerMetadata) {}

    record RefundResult(String refundId, String status, Map<String, Object> providerMetadata) {}

    record SetupIntentResult(String setupIntentId, String clientSecret, Map<String, Object> providerMetadata) {}
}
