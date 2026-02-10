package com.humano.domain.enumeration.billing;

public enum SubscriptionStatus {
    PENDING_PAYMENT, // Waiting for initial payment
    TRIAL, // In trial period
    ACTIVE, // Fully active subscription
    PAST_DUE, // Payment failed but still active (grace period)
    SUSPENDED, // Temporarily suspended due to non-payment
    CANCELLED, // Cancelled by user
    EXPIRED, // Subscription period ended
}
