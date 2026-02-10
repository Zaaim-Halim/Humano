package com.humano.service.billing.dto.responses;

import com.humano.domain.enumeration.billing.InvoiceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO record for returning invoice information.
 */
public record InvoiceResponse(
    UUID id,
    String invoiceNumber,
    UUID tenantId,
    String tenantName,
    UUID subscriptionId,
    BigDecimal amount,
    BigDecimal taxAmount,
    BigDecimal totalAmount,
    InvoiceStatus status,
    Instant issueDate,
    Instant dueDate,
    Instant paidDate,
    int paymentCount,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
