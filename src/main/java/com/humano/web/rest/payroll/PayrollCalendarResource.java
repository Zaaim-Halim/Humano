package com.humano.web.rest.payroll;

import com.humano.dto.payroll.request.CreatePayrollCalendarRequest;
import com.humano.dto.payroll.response.PayrollCalendarResponse;
import com.humano.dto.payroll.response.PayrollPeriodResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.payroll.PayrollCalendarService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Payroll calendars (P2.5). Periods are managed via {@link PayrollPeriodResource}.
 */
@RestController
@RequestMapping("/api/payroll/calendars")
@PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.PAYROLL_ADMIN + "')")
public class PayrollCalendarResource {

    private static final Logger LOG = LoggerFactory.getLogger(PayrollCalendarResource.class);

    private final PayrollCalendarService calendarService;

    public PayrollCalendarResource(PayrollCalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @PostMapping
    public ResponseEntity<PayrollCalendarResponse> create(@Valid @RequestBody CreatePayrollCalendarRequest request) {
        LOG.debug("REST request to create PayrollCalendar: {}", request);
        PayrollCalendarResponse result = calendarService.createCalendar(request);
        return ResponseEntity.created(URI.create("/api/payroll/calendars/" + result.id())).body(result);
    }

    @GetMapping("/active")
    public ResponseEntity<List<PayrollCalendarResponse>> active() {
        return ResponseEntity.ok(calendarService.getActiveCalendars());
    }

    @PostMapping("/{id}/active")
    public ResponseEntity<PayrollCalendarResponse> setActive(@PathVariable UUID id, @RequestParam boolean active) {
        return ResponseEntity.ok(calendarService.setCalendarActive(id, active));
    }

    @GetMapping("/upcoming-periods")
    public ResponseEntity<Map<String, List<PayrollPeriodResponse>>> upcomingPeriods(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(calendarService.getUpcomingPeriodsByCalendar(days));
    }
}
