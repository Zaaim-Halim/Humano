package com.humano.dto.payroll.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for recalculating payroll for specific employees or entire run.
 */
public record RecalculatePayrollRequest(
    @NotNull(message = "Payroll run ID is required") UUID payrollRunId,

    List<UUID> employeeIds,

    boolean recalculateAll,

    List<String> componentsToRecalculate,

    String reason
) {}
