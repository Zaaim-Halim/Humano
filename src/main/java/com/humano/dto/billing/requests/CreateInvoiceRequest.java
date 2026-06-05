package com.humano.dto.billing.requests;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO record for creating a new invoice.
 */
public record CreateInvoiceRequest(
    @NotNull(message = "Tenant ID is required") UUID tenantId,

    @NotNull(message = "Subscription ID is required") UUID subscriptionId,

    @NotBlank(message = "Invoice number is required")
    @Size(min = 3, max = 50, message = "Invoice number must be between 3 and 50 characters")
    String invoiceNumber,

    @NotNull(message = "Amount is required") @DecimalMin(value = "0.0", message = "Amount cannot be negative") BigDecimal amount,

    @DecimalMin(value = "0.0", message = "Tax amount cannot be negative") BigDecimal taxAmount,

    @NotNull(message = "Due date is required") Instant dueDate,

    /**
     * Optional coupon code to apply to this invoice (P4.5). When supplied, the
     * coupon is validated + redeemed (incrementing {@code timesRedeemed}); the
     * resolved discount is subtracted from {@code amount} before tax is computed.
     * Invalid / expired / exhausted coupons return HTTP 400.
     */
    @Size(max = 50, message = "Coupon code cannot exceed 50 characters") String couponCode
) {}
