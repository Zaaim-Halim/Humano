package com.humano.dto.payroll.request;

import com.humano.domain.enumeration.payroll.BonusType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for awarding a bonus to an employee.
 */
public record AwardBonusRequest(
    @NotNull(message = "Employee ID is required") UUID employeeId,

    @NotNull(message = "Bonus type is required") BonusType type,

    @NotNull(message = "Amount is required") @DecimalMin(value = "0.01", message = "Bonus amount must be positive") BigDecimal amount,

    @NotNull(message = "Currency ID is required") UUID currencyId,

    @NotNull(message = "Award date is required") LocalDate awardDate,

    LocalDate paymentDate,

    @Size(max = 500, message = "Description cannot exceed 500 characters") String description,

    boolean payImmediately
) {}
