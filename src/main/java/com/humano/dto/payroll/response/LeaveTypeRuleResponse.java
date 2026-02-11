package com.humano.dto.payroll.response;

import com.humano.domain.enumeration.hr.LeaveType;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response DTO for leave type rule details.
 */
public record LeaveTypeRuleResponse(
    UUID id,
    UUID countryId,
    String countryName,
    LeaveType leaveType,
    String leaveTypeName,
    BigDecimal deductionPercentage,
    boolean affectsTaxableSalary,
    String description
) {}
