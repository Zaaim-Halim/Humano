package com.humano.dto.hr.requests;

import com.humano.domain.enumeration.hr.EventAction;
import com.humano.domain.enumeration.hr.EventType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Request DTO for searching attendance events with multiple criteria.
 * All fields are optional and will be combined with AND logic.
 */
public record AttendanceEventSearchRequest(
    UUID attendanceId,
    UUID employeeId,
    EventType eventType,
    EventAction eventAction,
    LocalTime eventTimeFrom,
    LocalTime eventTimeTo,
    String description,
    String createdBy,
    String lastModifiedBy,
    LocalDate createdDateFrom,
    LocalDate createdDateTo
) {}
