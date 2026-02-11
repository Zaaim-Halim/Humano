package com.humano.dto.payroll.response;

import com.humano.domain.enumeration.payroll.Kind;
import com.humano.domain.enumeration.payroll.Measurement;
import com.humano.domain.enumeration.payroll.PayComponentCode;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for pay component details.
 */
public record PayComponentResponse(
    UUID id,
    PayComponentCode code,
    String name,
    Kind kind,
    Measurement measure,
    boolean taxable,
    boolean contributesToSocial,
    boolean percentage,
    Integer calcPhase,
    int ruleCount,
    List<PayRuleSummary> activeRules
) {
    public record PayRuleSummary(UUID id, String formula, Integer priority, boolean active) {}
}
