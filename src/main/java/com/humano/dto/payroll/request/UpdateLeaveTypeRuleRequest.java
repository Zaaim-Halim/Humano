package com.humano.dto.payroll.request;

import com.humano.domain.payroll.LeavePayBand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.util.List;

/**
 * Request DTO for changing a leave type rule; a null field keeps its current value.
 *
 * @param deductionPercentage  share of the daily salary not paid on days no band covers
 * @param payBands             the rule's bands, replacing the current ones; an empty list removes them
 * @param affectsTaxableSalary whether the deduction lowers taxable pay and contribution bases
 */
public record UpdateLeaveTypeRuleRequest(
    @DecimalMin(value = "0", message = "Deduction percentage must be at least 0")
    @DecimalMax(value = "100", message = "Deduction percentage cannot exceed 100")
    BigDecimal deductionPercentage,

    List<LeavePayBand> payBands,

    Boolean affectsTaxableSalary
) {}
