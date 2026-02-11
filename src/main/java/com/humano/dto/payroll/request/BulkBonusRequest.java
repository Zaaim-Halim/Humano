package com.humano.dto.payroll.request;

import com.humano.domain.enumeration.payroll.BonusType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for awarding bonuses to multiple employees at once.
 * Supports both uniform and individual amounts.
 */
public record BulkBonusRequest(
    @NotEmpty(message = "At least one employee must be specified") List<UUID> employeeIds,

    @NotNull(message = "Bonus type is required") BonusType type,

    BigDecimal uniformAmount,

    List<EmployeeBonusAmount> individualAmounts,

    @NotNull(message = "Currency ID is required") UUID currencyId,

    @NotNull(message = "Award date is required") LocalDate awardDate,

    LocalDate paymentDate,

    String description
) {
    public record EmployeeBonusAmount(@NotNull UUID employeeId, @NotNull @DecimalMin("0.01") BigDecimal amount) {}
}
