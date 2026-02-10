package com.humano.service.hr.dto.requests;

import com.humano.domain.enumeration.hr.AttendanceStatus;
import java.time.LocalTime;

/**
 * DTO record for updating an existing attendance record.
 */
public record UpdateAttendanceRequest(LocalTime checkIn, LocalTime checkOut, AttendanceStatus status) {}
