package com.humano.web.rest.payroll;

import com.humano.dto.payroll.request.AwardBonusRequest;
import com.humano.dto.payroll.request.BonusSearchRequest;
import com.humano.dto.payroll.request.BulkBonusRequest;
import com.humano.dto.payroll.response.BonusResponse;
import com.humano.dto.payroll.response.BonusSummaryResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.payroll.BonusService;
import jakarta.validation.Valid;
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
 * Bonuses .
 */
@RestController
@RequestMapping("/api/payroll/bonuses")
@PreAuthorize(
    "hasAnyAuthority('" +
    AuthoritiesConstants.ADMIN +
    "', '" +
    AuthoritiesConstants.PAYROLL_ADMIN +
    "', '" +
    AuthoritiesConstants.HR_MANAGER +
    "')"
)
public class BonusResource {

    private final BonusService bonusService;

    public BonusResource(BonusService bonusService) {
        this.bonusService = bonusService;
    }

    @PostMapping
    public ResponseEntity<BonusResponse> award(@Valid @RequestBody AwardBonusRequest request) {
        BonusResponse result = bonusService.awardBonus(request);
        return ResponseEntity.created(URI.create("/api/payroll/bonuses/" + result.id())).body(result);
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<BonusResponse>> awardBulk(@Valid @RequestBody BulkBonusRequest request) {
        return ResponseEntity.ok(bonusService.awardBulkBonuses(request));
    }

    @PostMapping("/{id}/mark-paid")
    public ResponseEntity<BonusResponse> markPaid(@PathVariable UUID id, @RequestParam LocalDate paymentDate) {
        return ResponseEntity.ok(bonusService.markAsPaid(id, paymentDate));
    }

    @PostMapping("/bulk-mark-paid")
    public ResponseEntity<List<BonusResponse>> markBulkPaid(@RequestBody List<UUID> bonusIds, @RequestParam LocalDate paymentDate) {
        return ResponseEntity.ok(bonusService.markBulkAsPaid(bonusIds, paymentDate));
    }

    @GetMapping("/employees/{employeeId}/summary")
    public ResponseEntity<BonusSummaryResponse> summary(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(bonusService.getBonusSummary(employeeId));
    }

    @GetMapping("/pending")
    public ResponseEntity<Page<BonusResponse>> pending(@RequestParam LocalDate paymentDateBefore, Pageable pageable) {
        Page<BonusResponse> page = bonusService.getPendingBonuses(paymentDateBefore, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page);
    }

    @GetMapping("/departments/{departmentId}/analytics")
    public ResponseEntity<Map<String, Object>> departmentAnalytics(@PathVariable UUID departmentId, @RequestParam int year) {
        return ResponseEntity.ok(bonusService.getDepartmentBonusAnalytics(departmentId, year));
    }

    @PostMapping("/search")
    public ResponseEntity<Page<BonusResponse>> search(@Valid @RequestBody BonusSearchRequest body, Pageable pageable) {
        Page<BonusResponse> page = bonusService.searchBonuses(body, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page);
    }

    @PostMapping("/employees/{employeeId}/search")
    public ResponseEntity<Page<BonusResponse>> searchByEmployee(
        @PathVariable UUID employeeId,
        @Valid @RequestBody BonusSearchRequest body,
        Pageable pageable
    ) {
        Page<BonusResponse> page = bonusService.searchBonusesByEmployee(employeeId, body, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page);
    }
}
