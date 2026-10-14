package com.humano.dto.me.responses;

import com.humano.domain.enumeration.hr.AttendanceStatus;
import com.humano.domain.enumeration.hr.EventAction;
import com.humano.domain.enumeration.hr.EventType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * One day on the signed-in employee's time clock: check-in/out, breaks, and the derived state
 * and totals. {@code workedMinutes} is time between check-in and check-out (or now, while the
 * day is open) minus breaks.
 */
public record TimeClockDay(
    UUID attendanceId,
    LocalDate date,
    AttendanceStatus status,
    State state,
    LocalTime checkIn,
    LocalTime checkOut,
    long workedMinutes,
    long breakMinutes,
    List<Event> events
) {
    public enum State {
        NOT_STARTED,
        WORKING,
        ON_BREAK,
        DONE,
    }

    public record Event(EventType type, EventAction action, LocalTime time, String description) {}
}
