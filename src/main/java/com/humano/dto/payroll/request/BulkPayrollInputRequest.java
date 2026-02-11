package com.humano.dto.payroll.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for creating multiple payroll inputs at once.
 */
public record BulkPayrollInputRequest(
    @NotNull(message = "Period ID is required") UUID periodId,

    @NotEmpty(message = "At least one input is required") @Valid List<PayrollInputItem> inputs,

    String source,

    boolean overwriteExisting
) {
    public record PayrollInputItem(
        @NotNull UUID employeeId,
        @NotNull UUID componentId,
        java.math.BigDecimal quantity,
        java.math.BigDecimal rate,
        java.math.BigDecimal amount,
        String metadata
    ) {}
}
