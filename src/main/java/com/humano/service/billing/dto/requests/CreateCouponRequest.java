package com.humano.service.billing.dto.requests;

import com.humano.domain.enumeration.billing.DiscountType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO record for creating a new coupon.
 */
public record CreateCouponRequest(
    @NotBlank(message = "Coupon code is required")
    @Size(min = 3, max = 50, message = "Coupon code must be between 3 and 50 characters")
    String code,

    @NotNull(message = "Discount type is required") DiscountType type,

    @NotNull(message = "Discount amount is required")
    @DecimalMin(value = "0.0", message = "Discount cannot be negative")
    BigDecimal discount,

    @DecimalMin(value = "0.0", message = "Percentage cannot be negative")
    @DecimalMax(value = "100.0", message = "Percentage cannot exceed 100")
    BigDecimal percentage,

    @NotNull(message = "Expiry date is required") Instant expiryDate,

    Instant startDate,

    Integer maxRedemptions
) {}
