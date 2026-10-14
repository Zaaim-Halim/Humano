package com.humano.service.me;

import com.humano.domain.enumeration.hr.AttendanceStatus;
import com.humano.domain.enumeration.hr.EventAction;
import com.humano.domain.enumeration.hr.EventType;
import com.humano.domain.hr.Attendance;
import com.humano.domain.hr.AttendanceEvent;
import com.humano.domain.shared.Employee;
import com.humano.dto.me.responses.TimeClockDay;
import com.humano.dto.me.responses.TimeClockDay.State;
import com.humano.repository.hr.AttendanceRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.BusinessRuleViolationException;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.payroll.OrganizationSettingsService;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The employee's own time clock: clock in, take and end breaks, clock out.
 *
 * <p>Times are stamped by the server in the company timezone (organization settings) — never
 * taken from the client — and recorded on the day's {@link Attendance} (check-in/out) plus an
 * {@link AttendanceEvent} per action, so HR's attendance views see exactly what was clocked.
 * Each action is only allowed from the matching state (e.g. no break before clocking in).
 */
@Service
public class TimeClockService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final OrganizationSettingsService organizationSettingsService;

    public TimeClockService(
        AttendanceRepository attendanceRepository,
        EmployeeRepository employeeRepository,
        OrganizationSettingsService organizationSettingsService
    ) {
        this.attendanceRepository = attendanceRepository;
        this.employeeRepository = employeeRepository;
        this.organizationSettingsService = organizationSettingsService;
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public TimeClockDay today(UUID employeeId) {
        ZonedDateTime now = now();
        return attendanceRepository
            .findFirstByEmployeeIdAndDate(employeeId, now.toLocalDate())
            .map(a -> toDay(a, now))
            .orElseGet(() -> new TimeClockDay(null, now.toLocalDate(), null, State.NOT_STARTED, null, null, 0, 0, List.of()));
    }

    /** Days between {@code from} and {@code to} that have an attendance record. */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<TimeClockDay> range(UUID employeeId, LocalDate from, LocalDate to) {
        ZonedDateTime now = now();
        return attendanceRepository
            .findByEmployeeIdAndDateBetweenOrderByDate(employeeId, from, to)
            .stream()
            .map(a -> toDay(a, now))
            .toList();
    }

    @Transactional("tenantTransactionManager")
    public TimeClockDay clockIn(UUID employeeId, boolean remote, String note) {
        ZonedDateTime now = now();
        Attendance day = attendanceRepository
            .findFirstByEmployeeIdAndDate(employeeId, now.toLocalDate())
            .orElseGet(() -> newDay(employeeId, now.toLocalDate()));
        if (day.getCheckIn() != null) {
            throw new BusinessRuleViolationException("timeclock.alreadyClockedIn", "You have already clocked in today.");
        }
        LocalTime time = minute(now);
        day.setCheckIn(time);
        day.setStatus(remote ? AttendanceStatus.WORK_FROM_HOME : AttendanceStatus.PRESENT);
        addEvent(day, EventType.CHECK_IN, EventAction.CHECK_IN, time, note);
        return toDay(attendanceRepository.save(day), now);
    }

    @Transactional("tenantTransactionManager")
    public TimeClockDay startBreak(UUID employeeId, EventType type, String note) {
        ZonedDateTime now = now();
        Attendance day = openDay(employeeId, now);
        if (state(day) != State.WORKING) {
            throw new BusinessRuleViolationException("timeclock.notWorking", "You can only start a break while clocked in.");
        }
        EventType breakType = type == EventType.LUNCH_BREAK || type == EventType.MEETING ? type : EventType.OTHER;
        addEvent(day, breakType, EventAction.BREAK_START, minute(now), note);
        return toDay(attendanceRepository.save(day), now);
    }

    @Transactional("tenantTransactionManager")
    public TimeClockDay endBreak(UUID employeeId) {
        ZonedDateTime now = now();
        Attendance day = openDay(employeeId, now);
        if (state(day) != State.ON_BREAK) {
            throw new BusinessRuleViolationException("timeclock.notOnBreak", "You are not on a break.");
        }
        addEvent(day, openBreak(day).getEventType(), EventAction.BREAK_END, minute(now), null);
        return toDay(attendanceRepository.save(day), now);
    }

    @Transactional("tenantTransactionManager")
    public TimeClockDay clockOut(UUID employeeId, String note) {
        ZonedDateTime now = now();
        Attendance day = openDay(employeeId, now);
        LocalTime time = minute(now);
        if (state(day) == State.ON_BREAK) {
            addEvent(day, openBreak(day).getEventType(), EventAction.BREAK_END, time, null); // close the break first
        }
        day.setCheckOut(time);
        addEvent(day, EventType.CHECK_OUT, EventAction.CHECK_OUT, time, note);
        return toDay(attendanceRepository.save(day), now);
    }

    // ------------------------------------------------------------------ helpers

    private Attendance openDay(UUID employeeId, ZonedDateTime now) {
        Attendance day = attendanceRepository
            .findFirstByEmployeeIdAndDate(employeeId, now.toLocalDate())
            .orElseThrow(() -> new BusinessRuleViolationException("timeclock.notClockedIn", "Clock in first."));
        State state = state(day);
        if (state == State.NOT_STARTED) {
            throw new BusinessRuleViolationException("timeclock.notClockedIn", "Clock in first.");
        }
        if (state == State.DONE) {
            throw new BusinessRuleViolationException("timeclock.alreadyClockedOut", "You have already clocked out today.");
        }
        return day;
    }

    private Attendance newDay(UUID employeeId, LocalDate date) {
        Employee employee = employeeRepository
            .findById(employeeId)
            .orElseThrow(() -> EntityNotFoundException.create("Employee", employeeId));
        Attendance day = new Attendance();
        day.setEmployee(employee);
        day.setDate(date);
        return day;
    }

    private void addEvent(Attendance day, EventType type, EventAction action, LocalTime time, String note) {
        AttendanceEvent event = new AttendanceEvent();
        event.setAttendance(day);
        event.setEventType(type);
        event.setEventAction(action);
        event.setEventTime(time);
        event.setDescription(note == null || note.isBlank() ? null : note.strip());
        day.getEvents().add(event);
    }

    private static List<AttendanceEvent> ordered(Attendance day) {
        return day
            .getEvents()
            .stream()
            .sorted(Comparator.comparing(AttendanceEvent::getEventTime).thenComparing(e -> e.getEventAction().ordinal()))
            .toList();
    }

    private static State state(Attendance day) {
        if (day.getCheckIn() == null) return State.NOT_STARTED;
        if (day.getCheckOut() != null) return State.DONE;
        return openBreakOrNull(day) != null ? State.ON_BREAK : State.WORKING;
    }

    private static AttendanceEvent openBreak(Attendance day) {
        return openBreakOrNull(day);
    }

    /** The last BREAK_START not followed by a BREAK_END, if any. */
    private static AttendanceEvent openBreakOrNull(Attendance day) {
        AttendanceEvent open = null;
        for (AttendanceEvent e : ordered(day)) {
            if (e.getEventAction() == EventAction.BREAK_START) open = e;
            else if (e.getEventAction() == EventAction.BREAK_END) open = null;
        }
        return open;
    }

    TimeClockDay toDay(Attendance day, ZonedDateTime now) {
        // Open days run until now (today) or until midnight (a forgotten clock-out on a past day).
        LocalTime end = day.getCheckOut() != null
            ? day.getCheckOut()
            : day.getDate().equals(now.toLocalDate()) ? minute(now) : LocalTime.MAX.truncatedTo(ChronoUnit.MINUTES);
        long breakMinutes = 0;
        LocalTime breakStart = null;
        for (AttendanceEvent e : ordered(day)) {
            if (e.getEventAction() == EventAction.BREAK_START) breakStart = e.getEventTime();
            else if (e.getEventAction() == EventAction.BREAK_END && breakStart != null) {
                breakMinutes += minutesBetween(breakStart, e.getEventTime());
                breakStart = null;
            }
        }
        if (breakStart != null) breakMinutes += minutesBetween(breakStart, end);
        long span = day.getCheckIn() == null ? 0 : minutesBetween(day.getCheckIn(), end);
        List<TimeClockDay.Event> events = ordered(day)
            .stream()
            .map(e -> new TimeClockDay.Event(e.getEventType(), e.getEventAction(), e.getEventTime(), e.getDescription()))
            .toList();
        return new TimeClockDay(
            day.getId(),
            day.getDate(),
            day.getStatus(),
            state(day),
            day.getCheckIn(),
            day.getCheckOut(),
            Math.max(0, span - breakMinutes),
            breakMinutes,
            events
        );
    }

    private static long minutesBetween(LocalTime a, LocalTime b) {
        return Math.max(0, Duration.between(a, b).toMinutes());
    }

    private static LocalTime minute(ZonedDateTime now) {
        return now.toLocalTime().truncatedTo(ChronoUnit.MINUTES);
    }

    ZonedDateTime now() {
        String tz = organizationSettingsService.get().timezone();
        ZoneId zone;
        try {
            zone = ZoneId.of(tz == null || tz.isBlank() ? "UTC" : tz);
        } catch (DateTimeException e) {
            zone = ZoneId.of("UTC");
        }
        return ZonedDateTime.now(zone);
    }
}
