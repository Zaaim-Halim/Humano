package com.humano.dto.hr.requests;

import com.humano.domain.enumeration.hr.OvertimeType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for creating a new overtime record.
 */
public record CreateOvertimeRecordRequest(
    @NotNull(message = "Employee ID is required") UUID employeeId,

    @NotNull(message = "Date is required") LocalDate date,

    @NotNull(message = "Hours is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Hours must be greater than 0")
    BigDecimal hours,

    @NotNull(message = "Overtime type is required") OvertimeType type,

    @Size(max = 500, message = "Notes must not exceed 500 characters") String notes
) {}
