package com.humano.dto.me.responses;

import com.humano.domain.enumeration.hr.LeaveType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A day of attendance for the people the viewer is responsible for: their whole reporting line
 * ({@code REPORTS}), or everyone for HR ({@code ALL}). {@code NONE} means the viewer has no one
 * to see, and the UI hides the view.
 */
public record TeamAttendance(Scope scope, LocalDate date, List<Row> rows) {
    public enum Scope {
        ALL,
        REPORTS,
        NONE,
    }

    /** Where someone is on {@code date}. */
    public enum Presence {
        WORKING,
        ON_BREAK,
        DONE,
        /** Clocked in on a past day but never clocked out. */
        MISSING_CLOCK_OUT,
        ON_LEAVE,
        /** Today, not clocked in (yet). */
        NOT_IN,
        /** A past working day with no clock-in and no leave. */
        ABSENT,
        /** Weekend. */
        OFF,
    }

    /**
     * One person. {@code day} is their clocked day (null when they never clocked in);
     * {@code leaveType} is set only for viewers who may see leave types.
     */
    public record Row(
        UUID employeeId,
        String name,
        String jobTitle,
        String imageUrl,
        String unitName,
        String managerName,
        Presence presence,
        LeaveType leaveType,
        TimeClockDay day
    ) {}
}
