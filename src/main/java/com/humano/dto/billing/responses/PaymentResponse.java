package com.humano.dto.billing.responses;

import com.humano.domain.enumeration.billing.PaymentMethodType;
import com.humano.domain.enumeration.billing.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO record for returning payment information.
 */
public record PaymentResponse(
    UUID id,
    UUID invoiceId,
    String invoiceNumber,
    BigDecimal amount,
    PaymentStatus status,
    Instant paymentDate,
    PaymentMethodType methodType,
    String currencyCode,
    String externalPaymentId,
    String failureReason,
    BigDecimal refundedAmount,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
