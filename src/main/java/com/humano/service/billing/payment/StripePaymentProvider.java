package com.humano.service.billing.payment;

import com.stripe.exception.ApiException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.CardException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.SetupIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.SetupIntentCreateParams;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * P4.2 — Stripe-backed {@link PaymentProvider}.
 * <p>
 * Activated only when {@code humano.billing.stripe.secret-key} is non-empty
 * (via {@link ConditionalOnProperty}) so the app boots in dev without Stripe
 * credentials. When inactive, {@code PaymentService} falls back to its existing
 * simulate-success path.
 * <p>
 * <b>Charge model.</b> Uses the PaymentIntents API with {@code confirm=true} and
 * {@code automatic_payment_methods.enabled=true}, which is Stripe's recommended
 * single-call charge path for already-tokenised cards. {@code amount} is converted
 * from major units (BigDecimal EUR/USD) to Stripe's minor-unit long (cents).
 * Zero-decimal currencies (JPY, KRW, ...) are NOT special-cased — passing them
 * is a configuration error for now (caller should validate currency support
 * before invocation).
 * <p>
 * <b>Idempotency.</b> The caller's {@code idempotencyKey} is passed via
 * {@link RequestOptions#getIdempotencyKey()} so two retries against the same
 * key collapse to one charge server-side.
 */
@Component
@ConditionalOnProperty(name = "humano.billing.stripe.secret-key")
public class StripePaymentProvider implements PaymentProvider {

    private static final Logger LOG = LoggerFactory.getLogger(StripePaymentProvider.class);

    private static final BigDecimal MINOR_UNIT_SCALE = new BigDecimal(100);

    private final String secretKey;

    public StripePaymentProvider(@Value("${humano.billing.stripe.secret-key}") String secretKey) {
        this.secretKey = secretKey;
    }

    @PostConstruct
    void announce() {
        // Don't log the secret. Log the prefix to make accidental live-key-in-dev visible.
        String prefix = secretKey.length() > 7 ? secretKey.substring(0, 7) : "<short>";
        LOG.info("StripePaymentProvider active (key prefix '{}…') — live charges go through Stripe", prefix);
    }

    @Override
    public ChargeResult charge(BigDecimal amount, String currency, String methodToken, String idempotencyKey) {
        if (amount == null || amount.signum() <= 0) {
            throw new PaymentProviderException(PaymentProviderException.Kind.CONFIGURATION, "Amount must be positive");
        }
        long amountMinor = amount.setScale(2, RoundingMode.HALF_UP).multiply(MINOR_UNIT_SCALE).longValueExact();
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
            .setAmount(amountMinor)
            .setCurrency(currency.toLowerCase(Locale.ROOT))
            .setPaymentMethod(methodToken)
            .setConfirm(true)
            .setAutomaticPaymentMethods(
                PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                    .setEnabled(true)
                    .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                    .build()
            )
            .build();
        RequestOptions opts = RequestOptions.builder().setApiKey(secretKey).setIdempotencyKey(idempotencyKey).build();
        try {
            PaymentIntent intent = PaymentIntent.create(params, opts);
            return new ChargeResult(intent.getId(), intent.getStatus(), snapshotIntent(intent));
        } catch (StripeException e) {
            throw mapException(e, "charge failed");
        }
    }

    @Override
    public RefundResult refund(String transactionId, BigDecimal amount) {
        RefundCreateParams.Builder builder = RefundCreateParams.builder().setPaymentIntent(transactionId);
        if (amount != null) {
            long amountMinor = amount.setScale(2, RoundingMode.HALF_UP).multiply(MINOR_UNIT_SCALE).longValueExact();
            builder.setAmount(amountMinor);
        }
        RequestOptions opts = RequestOptions.builder().setApiKey(secretKey).build();
        try {
            Refund refund = Refund.create(builder.build(), opts);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("refundId", refund.getId());
            metadata.put("status", refund.getStatus());
            metadata.put("amount", refund.getAmount());
            metadata.put("currency", refund.getCurrency());
            metadata.put("paymentIntent", refund.getPaymentIntent());
            return new RefundResult(refund.getId(), refund.getStatus(), metadata);
        } catch (StripeException e) {
            throw mapException(e, "refund failed");
        }
    }

    @Override
    public SetupIntentResult createSetupIntent(String customerKey) {
        SetupIntentCreateParams.Builder builder = SetupIntentCreateParams.builder().addPaymentMethodType("card");
        if (customerKey != null && !customerKey.isBlank()) {
            builder.setCustomer(customerKey);
        }
        RequestOptions opts = RequestOptions.builder().setApiKey(secretKey).build();
        try {
            SetupIntent intent = SetupIntent.create(builder.build(), opts);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("setupIntentId", intent.getId());
            metadata.put("status", intent.getStatus());
            metadata.put("customer", intent.getCustomer());
            return new SetupIntentResult(intent.getId(), intent.getClientSecret(), metadata);
        } catch (StripeException e) {
            throw mapException(e, "createSetupIntent failed");
        }
    }

    private Map<String, Object> snapshotIntent(PaymentIntent intent) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("paymentIntentId", intent.getId());
        snapshot.put("status", intent.getStatus());
        snapshot.put("amount", intent.getAmount());
        snapshot.put("amountReceived", intent.getAmountReceived());
        snapshot.put("currency", intent.getCurrency());
        snapshot.put("paymentMethod", intent.getPaymentMethod());
        snapshot.put("latestCharge", intent.getLatestCharge());
        if (intent.getLastPaymentError() != null) {
            Map<String, Object> err = new HashMap<>();
            err.put("code", intent.getLastPaymentError().getCode());
            err.put("message", intent.getLastPaymentError().getMessage());
            err.put("declineCode", intent.getLastPaymentError().getDeclineCode());
            snapshot.put("lastPaymentError", err);
        }
        return snapshot;
    }

    private PaymentProviderException mapException(StripeException e, String context) {
        PaymentProviderException.Kind kind;
        if (e instanceof CardException) {
            kind = PaymentProviderException.Kind.DECLINED;
        } else if (e instanceof AuthenticationException) {
            kind = PaymentProviderException.Kind.AUTHENTICATION;
        } else if (e instanceof InvalidRequestException) {
            kind = PaymentProviderException.Kind.CONFIGURATION;
        } else if (e instanceof RateLimitException || e instanceof ApiException) {
            kind = PaymentProviderException.Kind.TRANSIENT;
        } else {
            kind = PaymentProviderException.Kind.UNKNOWN;
        }
        String providerCode = e.getCode();
        String message = context + ": " + (e.getMessage() != null ? e.getMessage() : kind.name());
        return new PaymentProviderException(kind, providerCode, message, e);
    }
}
