package com.humano.web.rest.payroll;

import com.humano.domain.enumeration.payroll.DeductionType;
import com.humano.dto.payroll.request.CreateDeductionRequest;
import com.humano.dto.payroll.request.DeductionSearchRequest;
import com.humano.dto.payroll.response.DeductionResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.payroll.DeductionService;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.PaginationUtil;

/**
 * Deductions resource — manage employee deductions.
 */
@RestController
@RequestMapping("/api/payroll/deductions")
@RequirePermission(PermissionsConstants.MANAGE_DEDUCTIONS)
public class DeductionResource {

    private final DeductionService deductionService;

    public DeductionResource(DeductionService deductionService) {
        this.deductionService = deductionService;
    }

    @PostMapping
    public ResponseEntity<DeductionResponse> create(@Valid @RequestBody CreateDeductionRequest request) {
        DeductionResponse result = deductionService.createDeduction(request);
        return ResponseEntity.created(URI.create("/api/payroll/deductions/" + result.id())).body(result);
    }

    @PostMapping("/{id}/terminate")
    public ResponseEntity<DeductionResponse> terminate(@PathVariable UUID id, @RequestParam LocalDate terminationDate) {
        return ResponseEntity.ok(deductionService.terminateDeduction(id, terminationDate));
    }

    @GetMapping("/employees/{employeeId}/active")
    public ResponseEntity<List<DeductionResponse>> activeForEmployee(@PathVariable UUID employeeId, @RequestParam LocalDate asOfDate) {
        return ResponseEntity.ok(deductionService.getActiveDeductions(employeeId, asOfDate));
    }

    @GetMapping("/employees/{employeeId}/calculate")
    public ResponseEntity<Map<String, BigDecimal>> calculate(
        @PathVariable UUID employeeId,
        @RequestParam BigDecimal grossPay,
        @RequestParam LocalDate asOfDate
    ) {
        return ResponseEntity.ok(deductionService.calculateDeductions(employeeId, grossPay, asOfDate));
    }

    @GetMapping("/by-type/{type}")
    public ResponseEntity<Page<DeductionResponse>> byType(@PathVariable DeductionType type, Pageable pageable) {
        Page<DeductionResponse> page = deductionService.getDeductionsByType(type, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page);
    }

    public record UpdateDeductionAmountRequest(BigDecimal amount, BigDecimal percentage) {}

    @PutMapping("/{id}/amount")
    public ResponseEntity<DeductionResponse> updateAmount(@PathVariable UUID id, @RequestBody UpdateDeductionAmountRequest body) {
        return ResponseEntity.ok(deductionService.updateDeductionAmount(id, body.amount(), body.percentage()));
    }

    @GetMapping("/departments/{departmentId}/statistics")
    public ResponseEntity<Map<String, Object>> statistics(@PathVariable UUID departmentId, @RequestParam LocalDate asOfDate) {
        return ResponseEntity.ok(deductionService.getDeductionStatistics(departmentId, asOfDate));
    }

    @PostMapping("/search")
    public ResponseEntity<Page<DeductionResponse>> search(@Valid @RequestBody DeductionSearchRequest body, Pageable pageable) {
        Page<DeductionResponse> page = deductionService.searchDeductions(body, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page);
    }

    @PostMapping("/employees/{employeeId}/search")
    public ResponseEntity<Page<DeductionResponse>> searchByEmployee(
        @PathVariable UUID employeeId,
        @Valid @RequestBody DeductionSearchRequest body,
        Pageable pageable
    ) {
        Page<DeductionResponse> page = deductionService.searchDeductionsByEmployee(employeeId, body, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page);
    }
}
