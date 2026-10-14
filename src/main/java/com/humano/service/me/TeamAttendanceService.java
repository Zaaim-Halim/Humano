package com.humano.service.me;

import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.domain.enumeration.hr.LeaveStatus;
import com.humano.domain.hr.Attendance;
import com.humano.domain.hr.LeaveRequest;
import com.humano.dto.me.responses.TeamAttendance;
import com.humano.dto.me.responses.TeamAttendance.Presence;
import com.humano.dto.me.responses.TeamAttendance.Row;
import com.humano.dto.me.responses.TeamAttendance.Scope;
import com.humano.dto.me.responses.TimeClockDay;
import com.humano.repository.hr.AttendanceRepository;
import com.humano.repository.hr.LeaveRequestRepository;
import com.humano.repository.hr.projection.EmployeeHierarchyRow;
import com.humano.repository.shared.EmployeeRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Team attendance: one day's clock state for everyone a manager is responsible for (their whole
 * reporting line, via the materialized manager path) or, for HR, the whole company. Three
 * queries regardless of team size — people, that day's attendance with events, covering leave.
 */
@Service
public class TeamAttendanceService {

    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final TimeClockService timeClockService;

    public TeamAttendanceService(
        EmployeeRepository employeeRepository,
        AttendanceRepository attendanceRepository,
        LeaveRequestRepository leaveRequestRepository,
        TimeClockService timeClockService
    ) {
        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.timeClockService = timeClockService;
    }

    /**
     * @param viewerId      the signed-in employee
     * @param date          the day (defaults to today; future days are capped to today)
     * @param everyone      HR scope: the whole company instead of the viewer's reports
     * @param showLeaveType whether the viewer may see leave types
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public TeamAttendance forDay(UUID viewerId, LocalDate date, boolean everyone, boolean showLeaveType) {
        ZonedDateTime now = timeClockService.now();
        LocalDate today = now.toLocalDate();
        LocalDate day = date == null || date.isAfter(today) ? today : date;

        List<EmployeeHierarchyRow> people = everyone ? employeeRepository.findAllHierarchyRows() : reportsOf(viewerId);
        people = people.stream().filter(p -> p.status() == EmployeeStatus.ACTIVE && (everyone || !p.id().equals(viewerId))).toList();
        if (people.isEmpty()) {
            return new TeamAttendance(everyone ? Scope.ALL : Scope.NONE, day, List.of());
        }
        List<UUID> ids = people.stream().map(EmployeeHierarchyRow::id).toList();

        Map<UUID, Attendance> attendance = attendanceRepository
            .findByDateAndEmployeeIdIn(day, ids)
            .stream()
            .collect(Collectors.toMap(a -> a.getEmployee().getId(), Function.identity(), (a, b) -> a));
        Map<UUID, LeaveRequest> leave = leaveRequestRepository
            .findCovering(LeaveStatus.APPROVED, day, ids)
            .stream()
            .collect(Collectors.toMap(l -> l.getEmployee().getId(), Function.identity(), (a, b) -> a));
        boolean weekend = day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;

        List<Row> rows = people
            .stream()
            .map(p -> {
                Attendance a = attendance.get(p.id());
                TimeClockDay clocked = a == null ? null : timeClockService.toDay(a, now);
                LeaveRequest l = leave.get(p.id());
                Presence presence = presence(clocked, l != null, weekend, day.isBefore(today));
                return new Row(
                    p.id(),
                    p.firstName() + " " + p.lastName(),
                    p.jobTitle(),
                    p.imageUrl(),
                    p.unitName(),
                    p.managerId() == null ? null : p.managerFirstName() + " " + p.managerLastName(),
                    presence,
                    l != null && showLeaveType ? l.getLeaveType() : null,
                    clocked
                );
            })
            .sorted(Comparator.comparing((Row r) -> r.presence().ordinal()).thenComparing(Row::name))
            .toList();
        return new TeamAttendance(everyone ? Scope.ALL : Scope.REPORTS, day, rows);
    }

    private List<EmployeeHierarchyRow> reportsOf(UUID viewerId) {
        String path = employeeRepository.findById(viewerId).map(e -> e.getPath()).orElse(null);
        return path == null ? List.of() : employeeRepository.findManagerSubtree(path);
    }

    private static Presence presence(TimeClockDay clocked, boolean onLeave, boolean weekend, boolean past) {
        if (clocked != null && clocked.state() != TimeClockDay.State.NOT_STARTED) {
            return switch (clocked.state()) {
                case WORKING -> past ? Presence.MISSING_CLOCK_OUT : Presence.WORKING;
                case ON_BREAK -> past ? Presence.MISSING_CLOCK_OUT : Presence.ON_BREAK;
                default -> Presence.DONE;
            };
        }
        if (onLeave) return Presence.ON_LEAVE;
        if (weekend) return Presence.OFF;
        return past ? Presence.ABSENT : Presence.NOT_IN;
    }
}
