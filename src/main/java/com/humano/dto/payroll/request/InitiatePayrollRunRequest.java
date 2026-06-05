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

    String notes,

    /**
     * Optional reporting currency (P3.4). When set, each {@code PayrollResult} in the run
     * is calculated in the employee's native currency AND converted into this currency
     * (rate looked up at the period's {@code paymentDate}, with a most-recent-before
     * fallback bounded by {@code humano.payroll.max-exchange-rate-staleness-days}).
     * When null, the run is single-currency: native totals only, no conversion.
     */
    UUID reportingCurrencyId
) {
    public enum PayrollScope {
        ALL,
        UNIT,
        DEPARTMENT,
        SELECTED_EMPLOYEES,
    }

    public record ScopeDetails(PayrollScope type, UUID targetId, List<UUID> employeeIds) {}
}
