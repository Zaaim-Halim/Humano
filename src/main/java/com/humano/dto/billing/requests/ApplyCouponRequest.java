package com.humano.dto.billing.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A coupon code a customer enters for its own subscription. */
public record ApplyCouponRequest(@NotBlank @Size(max = 50, message = "Coupon code cannot exceed 50 characters") String code) {}
