package com.humano.dto.billing.responses;

import com.humano.domain.enumeration.billing.BillingCycle;
import com.humano.domain.enumeration.billing.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One customer's add-on in the owner console's list. */
public record AddonRowResponse(
    UUID id,
    UUID tenantId,
    String tenantName,
    String subdomain,
    String planName,
    BillingCycle billingCycle,
    SubscriptionStatus subscriptionStatus,
    String featureCode,
    int quantity,
    int unitSize,
    BigDecimal unitPrice,
    BigDecimal monthlyTotal,
    Instant billedUntil,
    Instant since
) {}
