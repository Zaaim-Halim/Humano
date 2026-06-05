package com.humano.web.rest.payroll;

import com.humano.domain.enumeration.payroll.TaxType;
import com.humano.dto.payroll.request.CreateTaxWithholdingRequest;
import com.humano.dto.payroll.response.TaxWithholdingResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.payroll.TaxWithholdingService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.PaginationUtil;

/**
 * Tax withholdings (P2.5).
 */
@RestController
@RequestMapping("/api/payroll/tax-withholdings")
@PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.PAYROLL_ADMIN + "')")
public class TaxWithholdingResource {

    private final TaxWithholdingService withholdingService;

    public TaxWithholdingResource(TaxWithholdingService withholdingService) {
        this.withholdingService = withholdingService;
    }

    @PostMapping
    public ResponseEntity<TaxWithholdingResponse> create(@Valid @RequestBody CreateTaxWithholdingRequest request) {
        TaxWithholdingResponse result = withholdingService.createWithholding(request);
        return ResponseEntity.created(URI.create("/api/payroll/tax-withholdings/" + result.id())).body(result);
    }

    public record UpdateRateRequest(BigDecimal rate) {}

    @PutMapping("/{id}/rate")
    public ResponseEntity<TaxWithholdingResponse> updateRate(@PathVariable UUID id, @RequestBody UpdateRateRequest body) {
        return ResponseEntity.ok(withholdingService.updateRate(id, body.rate()));
    }

    @PostMapping("/{id}/terminate")
    public ResponseEntity<TaxWithholdingResponse> terminate(@PathVariable UUID id, @RequestParam LocalDate terminationDate) {
        return ResponseEntity.ok(withholdingService.terminateWithholding(id, terminationDate));
    }

    @GetMapping("/employees/{employeeId}/active")
    public ResponseEntity<List<TaxWithholdingResponse>> activeForEmployee(
        @PathVariable UUID employeeId,
        @RequestParam(required = false) LocalDate asOfDate
    ) {
        return ResponseEntity.ok(withholdingService.getActiveWithholdings(employeeId, asOfDate));
    }

    public record RecordWithholdingRequest(BigDecimal grossPay, BigDecimal withheldAmount) {}

    @PostMapping("/{id}/record")
    public ResponseEntity<TaxWithholdingResponse> record(@PathVariable UUID id, @RequestBody RecordWithholdingRequest body) {
        return ResponseEntity.ok(withholdingService.recordWithholding(id, body.grossPay(), body.withheldAmount()));
    }

    @PostMapping("/reset-ytd")
    public ResponseEntity<Map<String, Integer>> resetYtd(@RequestParam int year) {
        int updated = withholdingService.resetYearToDateAmounts(year);
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @GetMapping("/by-type/{type}")
    public ResponseEntity<Page<TaxWithholdingResponse>> byType(@PathVariable TaxType type, Pageable pageable) {
        Page<TaxWithholdingResponse> page = withholdingService.getWithholdingsByType(type, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page);
    }

    @GetMapping("/employees/{employeeId}/liability")
    public ResponseEntity<Map<String, BigDecimal>> liability(
        @PathVariable UUID employeeId,
        @RequestParam BigDecimal grossPay,
        @RequestParam(required = false) LocalDate asOfDate
    ) {
        return ResponseEntity.ok(withholdingService.calculateTaxLiability(employeeId, grossPay, asOfDate));
    }

    @GetMapping("/departments/{departmentId}/statistics")
    public ResponseEntity<Map<String, Object>> statistics(@PathVariable UUID departmentId) {
        return ResponseEntity.ok(withholdingService.getWithholdingStatistics(departmentId));
    }
}
