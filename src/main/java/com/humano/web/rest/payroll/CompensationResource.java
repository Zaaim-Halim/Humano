package com.humano.web.rest.payroll;

import com.humano.dto.payroll.request.CompensationSearchRequest;
import com.humano.dto.payroll.request.CreateCompensationRequest;
import com.humano.dto.payroll.request.SalaryAdjustmentRequest;
import com.humano.dto.payroll.response.CompensationResponse;
import com.humano.dto.payroll.response.SalaryHistoryResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.payroll.CompensationService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
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
 * Employee compensation .
 */
@RestController
@RequestMapping("/api/payroll/compensations")
@PreAuthorize(
    "hasAnyAuthority('" +
    AuthoritiesConstants.ADMIN +
    "', '" +
    AuthoritiesConstants.PAYROLL_ADMIN +
    "', '" +
    AuthoritiesConstants.HR_MANAGER +
    "')"
)
public class CompensationResource {

    private final CompensationService compensationService;

    public CompensationResource(CompensationService compensationService) {
        this.compensationService = compensationService;
    }

    @PostMapping
    public ResponseEntity<CompensationResponse> create(@Valid @RequestBody CreateCompensationRequest request) {
        CompensationResponse result = compensationService.createCompensation(request);
        return ResponseEntity.created(URI.create("/api/payroll/compensations/" + result.id())).body(result);
    }

    @PostMapping("/adjust")
    public ResponseEntity<CompensationResponse> adjust(@Valid @RequestBody SalaryAdjustmentRequest request) {
        return ResponseEntity.ok(compensationService.adjustSalary(request));
    }

    @GetMapping("/employees/{employeeId}/history")
    public ResponseEntity<SalaryHistoryResponse> history(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(compensationService.getSalaryHistory(employeeId));
    }

    @GetMapping("/departments/{departmentId}")
    public ResponseEntity<Page<CompensationResponse>> byDepartment(@PathVariable UUID departmentId, Pageable pageable) {
        Page<CompensationResponse> page = compensationService.getCompensationsByDepartment(departmentId, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page);
    }

    @GetMapping("/departments/{departmentId}/cost")
    public ResponseEntity<Map<String, BigDecimal>> departmentCost(@PathVariable UUID departmentId, @RequestParam LocalDate asOfDate) {
        return ResponseEntity.ok(compensationService.calculateCompensationCost(departmentId, asOfDate));
    }

    @PostMapping("/search")
    public ResponseEntity<Page<CompensationResponse>> search(@Valid @RequestBody CompensationSearchRequest body, Pageable pageable) {
        Page<CompensationResponse> page = compensationService.searchCompensations(body, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page);
    }

    @PostMapping("/employees/{employeeId}/search")
    public ResponseEntity<Page<CompensationResponse>> searchByEmployee(
        @PathVariable UUID employeeId,
        @Valid @RequestBody CompensationSearchRequest body,
        Pageable pageable
    ) {
        Page<CompensationResponse> page = compensationService.searchCompensationsByEmployee(employeeId, body, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page);
    }
}
