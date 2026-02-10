package com.humano.service.billing.dto.requests;

import com.humano.domain.enumeration.billing.PaymentMethodType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO record for creating a new payment.
 */
public record CreatePaymentRequest(
    @NotNull(message = "Invoice ID is required") UUID invoiceId,

    @NotNull(message = "Amount is required") @DecimalMin(value = "0.01", message = "Payment amount must be positive") BigDecimal amount,

    @NotNull(message = "Payment method is required") PaymentMethodType methodType,

    @NotNull(message = "Currency ID is required") UUID currencyId,

    @Size(max = 255, message = "External payment ID cannot exceed 255 characters") String externalPaymentId
) {}
