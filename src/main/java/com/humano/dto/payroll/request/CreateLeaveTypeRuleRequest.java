package com.humano.dto.payroll.request;

import com.humano.domain.enumeration.hr.LeaveType;
import com.humano.domain.payroll.LeavePayBand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for creating a leave type rule.
 *
 * @param deductionPercentage  share of the daily salary not paid on days no band covers
 * @param payBands             stretches of the absence deducted at their own percentage; null or empty for none
 * @param affectsTaxableSalary whether the deduction lowers taxable pay and contribution bases; null means yes
 */
public record CreateLeaveTypeRuleRequest(
    @NotNull(message = "Country ID is required") UUID countryId,

    @NotNull(message = "Leave type is required") LeaveType leaveType,

    @NotNull(message = "Deduction percentage is required")
    @DecimalMin(value = "0", message = "Deduction percentage must be at least 0")
    @DecimalMax(value = "100", message = "Deduction percentage cannot exceed 100")
    BigDecimal deductionPercentage,

    List<LeavePayBand> payBands,

    Boolean affectsTaxableSalary
) {}
