package com.humano.service.hr.dto.requests;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for creating a new expense claim.
 */
public record CreateExpenseClaimRequest(
    @NotNull(message = "Employee ID is required") UUID employeeId,

    @NotNull(message = "Claim date is required") LocalDate claimDate,

    @NotNull(message = "Amount is required") @DecimalMin(value = "0.0", message = "Amount must be non-negative") BigDecimal amount,

    @Size(max = 2000, message = "Description must not exceed 2000 characters") String description
) {}
