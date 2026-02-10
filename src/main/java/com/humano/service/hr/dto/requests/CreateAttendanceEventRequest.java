package com.humano.service.hr.dto.requests;

import com.humano.domain.enumeration.hr.EventAction;
import com.humano.domain.enumeration.hr.EventType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.util.UUID;

/**
 * DTO record for creating a new attendance event.
 */
public record CreateAttendanceEventRequest(
    @NotNull(message = "Attendance ID is required") UUID attendanceId,

    @NotNull(message = "Event type is required") EventType eventType,

    @NotNull(message = "Event time is required") LocalTime eventTime,

    @NotNull(message = "Event action is required") EventAction eventAction,

    String description
) {}
