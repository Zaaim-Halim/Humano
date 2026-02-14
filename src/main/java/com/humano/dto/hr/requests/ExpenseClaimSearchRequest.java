package com.humano.dto.hr.requests;

import com.humano.domain.enumeration.hr.ExpenseClaimStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for searching expense claims with multiple criteria.
 * All fields are optional and will be combined with AND logic.
 */
public record ExpenseClaimSearchRequest(
    UUID employeeId,
    ExpenseClaimStatus status,
    LocalDate claimDateFrom,
    LocalDate claimDateTo,
    BigDecimal minAmount,
    BigDecimal maxAmount,
    String description,
    Boolean hasReceipt,
    String createdBy,
    LocalDate createdDateFrom,
    LocalDate createdDateTo
) {}
