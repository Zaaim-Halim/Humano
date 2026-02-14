package com.humano.dto.hr.responses;

import com.humano.domain.enumeration.hr.EventAction;
import com.humano.domain.enumeration.hr.EventType;
import java.time.LocalTime;
import java.util.UUID;

/**
 * DTO record for returning attendance event information.
 */
public record AttendanceEventResponse(UUID id, EventType eventType, LocalTime eventTime, EventAction eventAction, String description) {}
