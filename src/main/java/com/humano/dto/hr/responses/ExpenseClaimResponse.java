package com.humano.dto.hr.responses;

import com.humano.domain.enumeration.hr.ExpenseClaimStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for returning expense claim information.
 */
public record ExpenseClaimResponse(
    UUID id,
    UUID employeeId,
    String employeeName,
    LocalDate claimDate,
    BigDecimal amount,
    String description,
    ExpenseClaimStatus status,
    String receiptUrl,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
