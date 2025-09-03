package com.humano.domain.enumeration.billing;

/**
 * Defines how a feature's entitlement is limited.
 */
public enum FeatureLimitType {
    BOOLEAN,    // enabled/disabled
    QUANTITY,   // numeric limit (e.g. seats)
    METERED     // usage-based, aggregated externally
}

