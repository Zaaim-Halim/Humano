package com.humano.dto.hr.requests;

import com.humano.domain.enumeration.hr.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * DTO record for creating a new attendance record.
 */
public record CreateAttendanceRequest(
    @NotNull(message = "Employee ID is required") UUID employeeId,

    @NotNull(message = "Date is required") LocalDate date,

    LocalTime checkIn,

    LocalTime checkOut,

    @NotNull(message = "Status is required") AttendanceStatus status
) {}
