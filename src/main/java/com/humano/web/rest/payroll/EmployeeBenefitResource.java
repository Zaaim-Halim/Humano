package com.humano.web.rest.payroll;

import com.humano.domain.enumeration.payroll.BenefitType;
import com.humano.dto.payroll.request.EnrollBenefitRequest;
import com.humano.dto.payroll.response.BenefitsSummaryResponse;
import com.humano.dto.payroll.response.EmployeeBenefitResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.payroll.EmployeeBenefitService;
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
 * Employee benefits .
 */
@RestController
@RequestMapping("/api/payroll/employee-benefits")
@RequirePermission(PermissionsConstants.MANAGE_BENEFITS)
public class EmployeeBenefitResource {

    private final EmployeeBenefitService benefitService;

    public EmployeeBenefitResource(EmployeeBenefitService benefitService) {
        this.benefitService = benefitService;
    }

    @PostMapping
    public ResponseEntity<EmployeeBenefitResponse> enroll(@Valid @RequestBody EnrollBenefitRequest request) {
        EmployeeBenefitResponse result = benefitService.enrollBenefit(request);
        return ResponseEntity.created(URI.create("/api/payroll/employee-benefits/" + result.id())).body(result);
    }

    @PostMapping("/{id}/terminate")
    public ResponseEntity<EmployeeBenefitResponse> terminate(
        @PathVariable UUID id,
        @RequestParam LocalDate terminationDate,
        @RequestParam(required = false) String reason
    ) {
        return ResponseEntity.ok(benefitService.terminateBenefit(id, terminationDate, reason));
    }

    public record UpdateCostsRequest(BigDecimal employerCost, BigDecimal employeeCost) {}

    @PutMapping("/{id}/costs")
    public ResponseEntity<EmployeeBenefitResponse> updateCosts(@PathVariable UUID id, @RequestBody UpdateCostsRequest body) {
        return ResponseEntity.ok(benefitService.updateBenefitCosts(id, body.employerCost(), body.employeeCost()));
    }

    @GetMapping("/employees/{employeeId}/summary")
    public ResponseEntity<BenefitsSummaryResponse> summary(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(benefitService.getBenefitsSummary(employeeId));
    }

    @GetMapping("/by-type/{type}")
    public ResponseEntity<Page<EmployeeBenefitResponse>> byType(@PathVariable BenefitType type, Pageable pageable) {
        Page<EmployeeBenefitResponse> page = benefitService.getActiveBenefitsByType(type, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page);
    }

    @GetMapping("/departments/{departmentId}/costs")
    public ResponseEntity<Map<String, Object>> departmentCosts(@PathVariable UUID departmentId, @RequestParam LocalDate asOfDate) {
        return ResponseEntity.ok(benefitService.calculateBenefitCosts(departmentId, asOfDate));
    }

    public record BulkEnrollRequest(
        List<UUID> employeeIds,
        BenefitType type,
        BigDecimal employerCost,
        BigDecimal employeeCost,
        UUID currencyId,
        LocalDate effectiveFrom
    ) {}

    @PostMapping("/bulk")
    public ResponseEntity<List<EmployeeBenefitResponse>> bulkEnroll(@RequestBody BulkEnrollRequest body) {
        return ResponseEntity.ok(
            benefitService.bulkEnrollBenefits(
                body.employeeIds(),
                body.type(),
                body.employerCost(),
                body.employeeCost(),
                body.currencyId(),
                body.effectiveFrom()
            )
        );
    }

    @GetMapping("/expiring")
    public ResponseEntity<List<EmployeeBenefitResponse>> expiring(@RequestParam(defaultValue = "30") int daysAhead) {
        return ResponseEntity.ok(benefitService.getBenefitsExpiringSoon(daysAhead));
    }
}
