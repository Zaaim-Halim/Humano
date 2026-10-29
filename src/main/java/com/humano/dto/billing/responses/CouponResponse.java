package com.humano.dto.billing.responses;

import com.humano.domain.enumeration.billing.BillingCycle;
import com.humano.domain.enumeration.billing.DiscountType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * DTO record for returning coupon information.
 */
public record CouponResponse(
    UUID id,
    String code,
    DiscountType type,
    BigDecimal discount,
    BigDecimal percentage,
    Instant startDate,
    Instant expiryDate,
    boolean active,
    Integer maxRedemptions,
    Integer timesRedeemed,
    /** The only billing cycle the coupon applies to; null for any. */
    BillingCycle billingCycle,
    /** The plans the coupon applies to; empty for any plan. */
    Set<UUID> planIds,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
