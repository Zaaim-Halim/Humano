package com.humano.dto.payroll.response;

import com.humano.domain.enumeration.hr.LeaveType;
import com.humano.domain.payroll.LeavePayBand;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for leave type rule details.
 *
 * @param id                null for a rule from a payroll template
 * @param origin            WORKSPACE for the workspace's own rule, TEMPLATE for a payroll template's statutory rule
 * @param templateId        the template a TEMPLATE rule comes from (else null)
 * @param templateName      its display name
 * @param templateVersion   its version
 * @param note              what the statutory rule leaves out (TEMPLATE rules)
 * @param sourceTitle       the legal source of a TEMPLATE rule
 * @param sourceUrl         its address
 * @param overridesTemplate a WORKSPACE rule replacing an enabled template's rule for the same country and leave type
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
    String description,
    String origin,
    String templateId,
    String templateName,
    String templateVersion,
    String note,
    String sourceTitle,
    String sourceUrl,
    boolean overridesTemplate
) {}
