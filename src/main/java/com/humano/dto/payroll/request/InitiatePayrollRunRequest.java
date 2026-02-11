package com.humano.dto.payroll.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for initiating a payroll run.
 */
public record InitiatePayrollRunRequest(
    @NotNull(message = "Payroll period ID is required") UUID periodId,

    PayrollScope scope,

    List<UUID> excludedEmployeeIds,

    boolean draftMode,

    String notes
) {
    public enum PayrollScope {
        ALL,
        UNIT,
        DEPARTMENT,
        SELECTED_EMPLOYEES,
    }

    public record ScopeDetails(PayrollScope type, UUID targetId, List<UUID> employeeIds) {}
}
