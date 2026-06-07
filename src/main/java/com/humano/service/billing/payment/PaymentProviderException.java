package com.humano.service.billing.payment;

/**
 * Wrapper around a payment provider's failure. The typed {@link Kind} lets the
 * calling service decide between retry / surface-to-user / hard-fail without
 * leaking the provider's exception type into business code.
 */
public class PaymentProviderException extends RuntimeException {

    private final Kind kind;
    private final String providerCode;

    public PaymentProviderException(Kind kind, String providerCode, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.providerCode = providerCode;
    }

    public PaymentProviderException(Kind kind, String message) {
        super(message);
        this.kind = kind;
        this.providerCode = null;
    }

    public Kind getKind() {
        return kind;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public enum Kind {
        /** Card declined / insufficient funds / authentication required — surface to user. */
        DECLINED,
        /** Network / provider 5xx — retry candidate. */
        TRANSIENT,
        /** Bad request / configuration error — fix in code; do not retry. */
        CONFIGURATION,
        /** Authentication failure — likely a stale API key. */
        AUTHENTICATION,
        /** Unknown error — treat as transient. */
        UNKNOWN,
    }
}
