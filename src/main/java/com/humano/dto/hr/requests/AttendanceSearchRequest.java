package com.humano.dto.hr.requests;

import com.humano.domain.enumeration.hr.AttendanceStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Request DTO for searching attendance records with multiple criteria.
 * All fields are optional and will be combined with AND logic.
 */
public record AttendanceSearchRequest(
    UUID employeeId,
    LocalDate startDate,
    LocalDate endDate,
    AttendanceStatus status,
    LocalTime checkInFrom,
    LocalTime checkInTo,
    LocalTime checkOutFrom,
    LocalTime checkOutTo,
    String createdBy,
    String lastModifiedBy,
    LocalDate createdDateFrom,
    LocalDate createdDateTo
) {}
