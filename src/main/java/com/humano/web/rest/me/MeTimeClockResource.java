package com.humano.web.rest.me;

import com.humano.domain.shared.User;
import com.humano.dto.me.requests.TimeClockRequest;
import com.humano.dto.me.responses.TeamAttendance;
import com.humano.dto.me.responses.TimeClockDay;
import com.humano.security.SecurityExpressions;
import com.humano.security.annotation.RequireAuthenticated;
import com.humano.security.annotation.RequireFeature;
import com.humano.service.billing.entitlement.FeatureCodes;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.me.MeService;
import com.humano.service.me.TeamAttendanceService;
import com.humano.service.me.TimeClockService;
import com.humano.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in employee's time clock — {@code /api/me/attendance}. Clock in, start / end a
 * break, clock out; the server stamps the time. Always the caller's own record.
 */
@RequireFeature(FeatureCodes.TIMESHEETS)
@RestController
@RequestMapping("/api/me/attendance")
public class MeTimeClockResource {

    private final MeService meService;
    private final TimeClockService timeClockService;
    private final TeamAttendanceService teamAttendanceService;
    private final SecurityExpressions securityExpressions;

    public MeTimeClockResource(
        MeService meService,
        TimeClockService timeClockService,
        TeamAttendanceService teamAttendanceService,
        SecurityExpressions securityExpressions
    ) {
        this.meService = meService;
        this.timeClockService = timeClockService;
        this.teamAttendanceService = teamAttendanceService;
        this.securityExpressions = securityExpressions;
    }

    /** {@code GET /api/me/attendance/today} : today's clock state and totals. */
    @GetMapping("/today")
    @RequireAuthenticated
    public TimeClockDay today() {
        return timeClockService.today(me());
    }

    /** {@code GET /api/me/attendance?from=&to=} : my clocked days in a range (max 93 days). */
    @GetMapping
    @RequireAuthenticated
    public List<TimeClockDay> range(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        if (to.isBefore(from) || from.plusDays(93).isBefore(to)) {
            throw new BadRequestAlertException("Choose a range of at most 93 days", "attendance", "invalidrange");
        }
        return timeClockService.range(me(), from, to);
    }

    @PostMapping("/clock-in")
    @RequireAuthenticated
    public TimeClockDay clockIn(@Valid @RequestBody(required = false) TimeClockRequest body) {
        return timeClockService.clockIn(me(), body != null && Boolean.TRUE.equals(body.remote()), body == null ? null : body.note());
    }

    @PostMapping("/break/start")
    @RequireAuthenticated
    public TimeClockDay startBreak(@Valid @RequestBody(required = false) TimeClockRequest body) {
        return timeClockService.startBreak(me(), body == null ? null : body.breakType(), body == null ? null : body.note());
    }

    @PostMapping("/break/end")
    @RequireAuthenticated
    public TimeClockDay endBreak() {
        return timeClockService.endBreak(me());
    }

    @PostMapping("/clock-out")
    @RequireAuthenticated
    public TimeClockDay clockOut(@Valid @RequestBody(required = false) TimeClockRequest body) {
        return timeClockService.clockOut(me(), body == null ? null : body.note());
    }

    /**
     * {@code GET /api/me/attendance/team?date=} : that day's attendance for my reporting line, or
     * for everyone with {@code VIEW_ATTENDANCE} (HR). Leave types only for people who manage leave.
     */
    @GetMapping("/team")
    @RequireAuthenticated
    public TeamAttendance team(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        boolean everyone = securityExpressions.hasAnyPermission("VIEW_ATTENDANCE", "MANAGE_ATTENDANCE");
        boolean leaveTypes = securityExpressions.hasAnyPermission("VIEW_LEAVE_REQUESTS", "MANAGE_LEAVE_REQUESTS", "APPROVE_LEAVE");
        return teamAttendanceService.forDay(me(), date, everyone, leaveTypes);
    }

    private UUID me() {
        return meService
            .getCurrentUser()
            .map(User::getId)
            .orElseThrow(() -> new EntityNotFoundException("Current user could not be found"));
    }
}
