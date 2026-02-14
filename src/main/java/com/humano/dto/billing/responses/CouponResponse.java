package com.humano.dto.billing.responses;

import com.humano.domain.enumeration.billing.DiscountType;
import java.math.BigDecimal;
import java.time.Instant;
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
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
