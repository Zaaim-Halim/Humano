package com.humano.service.billing.dto.requests;

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

    @NotNull(message = "Due date is required") Instant dueDate
) {}
