package com.humano.dto.payroll.request;

import com.humano.domain.enumeration.hr.LeaveType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for creating a leave type rule.
 */
public record CreateLeaveTypeRuleRequest(
    @NotNull(message = "Country ID is required") UUID countryId,

    @NotNull(message = "Leave type is required") LeaveType leaveType,

    @NotNull(message = "Deduction percentage is required")
    @DecimalMin(value = "0", message = "Deduction percentage must be at least 0")
    @DecimalMax(value = "100", message = "Deduction percentage cannot exceed 100")
    BigDecimal deductionPercentage,

    boolean affectsTaxableSalary
) {}
