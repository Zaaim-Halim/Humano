package com.humano.dto.payroll.response;

import com.humano.domain.enumeration.hr.LeaveType;
import com.humano.domain.payroll.LeavePayBand;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for leave type rule details.
 */
public record LeaveTypeRuleResponse(
    UUID id,
    UUID countryId,
    String countryCode,
    String countryName,
    LeaveType leaveType,
    String leaveTypeName,
    BigDecimal deductionPercentage,
    List<LeavePayBand> payBands,
    boolean affectsTaxableSalary,
    String description
) {}
