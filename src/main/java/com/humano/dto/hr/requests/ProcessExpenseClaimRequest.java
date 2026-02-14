package com.humano.dto.hr.requests;

import com.humano.domain.enumeration.hr.ExpenseClaimStatus;
import jakarta.validation.constraints.NotNull;

/**
 * DTO record for processing (approving/rejecting) an expense claim.
 */
public record ProcessExpenseClaimRequest(@NotNull(message = "Status is required") ExpenseClaimStatus status) {}
