package com.humano.web.rest.payroll;

import com.humano.dto.payroll.request.GeneratePayrollPeriodsRequest;
import com.humano.dto.payroll.response.PayrollPeriodResponse;
import com.humano.security.annotation.RequirePayrollAdmin;
import com.humano.service.payroll.PayrollCalendarService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.PaginationUtil;

/**
 * Payroll periods . All period operations live under {@link PayrollCalendarService};
 * this resource only exposes them under a stable path for the SPA.
 */
@RestController
@RequestMapping("/api/payroll/periods")
@RequirePayrollAdmin
public class PayrollPeriodResource {

    private static final Logger LOG = LoggerFactory.getLogger(PayrollPeriodResource.class);

    private final PayrollCalendarService calendarService;

    public PayrollPeriodResource(PayrollCalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @PostMapping("/generate")
    public ResponseEntity<List<PayrollPeriodResponse>> generate(@Valid @RequestBody GeneratePayrollPeriodsRequest request) {
        LOG.debug("REST request to generate periods: {}", request);
        return ResponseEntity.ok(calendarService.generatePeriods(request));
    }

    @GetMapping("/calendars/{calendarId}")
    public ResponseEntity<Page<PayrollPeriodResponse>> list(@PathVariable UUID calendarId, Pageable pageable) {
        Page<PayrollPeriodResponse> page = calendarService.getPeriods(calendarId, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page);
    }

    @GetMapping("/calendars/{calendarId}/open")
    public ResponseEntity<List<PayrollPeriodResponse>> open(@PathVariable UUID calendarId) {
        return ResponseEntity.ok(calendarService.getOpenPeriods(calendarId));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<PayrollPeriodResponse> close(@PathVariable UUID id) {
        return ResponseEntity.ok(calendarService.closePeriod(id));
    }

    @PostMapping("/{id}/reopen")
    public ResponseEntity<PayrollPeriodResponse> reopen(@PathVariable UUID id, @RequestParam String reason) {
        return ResponseEntity.ok(calendarService.reopenPeriod(id, reason));
    }

    @PutMapping("/{id}/payment-date")
    public ResponseEntity<PayrollPeriodResponse> updatePaymentDate(@PathVariable UUID id, @RequestParam LocalDate paymentDate) {
        return ResponseEntity.ok(calendarService.updatePaymentDate(id, paymentDate));
    }
}
