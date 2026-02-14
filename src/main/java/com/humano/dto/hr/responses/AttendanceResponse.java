package com.humano.dto.hr.responses;

import com.humano.domain.enumeration.hr.AttendanceStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO record for returning attendance information.
 */
public record AttendanceResponse(
    UUID id,
    UUID employeeId,
    String employeeName,
    LocalDate date,
    LocalTime checkIn,
    LocalTime checkOut,
    AttendanceStatus status,
    List<AttendanceEventResponse> events,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
