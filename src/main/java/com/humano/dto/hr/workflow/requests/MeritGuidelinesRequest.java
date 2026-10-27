package com.humano.dto.hr.workflow.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * The merit matrix: for each final rating 1–5, the suggested range of base pay increase in percent.
 */
public record MeritGuidelinesRequest(@NotNull @Size(min = 5, max = 5) List<@Valid @NotNull Band> bands) {
    public record Band(
        @Min(1) @Max(5) int rating,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal minPercent,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal maxPercent
    ) {}
}
