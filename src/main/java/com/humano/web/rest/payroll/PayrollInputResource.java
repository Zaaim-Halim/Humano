package com.humano.web.rest.payroll;

import com.humano.dto.payroll.request.BulkPayrollInputRequest;
import com.humano.dto.payroll.request.CreatePayrollInputRequest;
import com.humano.dto.payroll.response.PayrollInputResponse;
import com.humano.security.annotation.RequirePayrollAdmin;
import com.humano.service.payroll.PayrollInputService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
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
 * Per-period payroll inputs &mdash; OT hours, allowances, ad-hoc overrides .
 */
@RestController
@RequestMapping("/api/payroll/inputs")
@RequirePayrollAdmin
public class PayrollInputResource {

    private final PayrollInputService inputService;

    public PayrollInputResource(PayrollInputService inputService) {
        this.inputService = inputService;
    }

    @PostMapping
    public ResponseEntity<PayrollInputResponse> create(@Valid @RequestBody CreatePayrollInputRequest request) {
        PayrollInputResponse result = inputService.createInput(request);
        return ResponseEntity.created(URI.create("/api/payroll/inputs/" + result.id())).body(result);
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<PayrollInputResponse>> createBulk(@Valid @RequestBody BulkPayrollInputRequest request) {
        return ResponseEntity.ok(inputService.createBulkInputs(request));
    }

    public record UpdateInputRequest(BigDecimal quantity, BigDecimal rate, BigDecimal amount) {}

    @PutMapping("/{id}")
    public ResponseEntity<PayrollInputResponse> update(@PathVariable UUID id, @RequestBody UpdateInputRequest body) {
        return ResponseEntity.ok(inputService.updateInput(id, body.quantity(), body.rate(), body.amount()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        inputService.deleteInput(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/employees/{employeeId}")
    public ResponseEntity<List<PayrollInputResponse>> forEmployee(@PathVariable UUID employeeId, @RequestParam UUID periodId) {
        return ResponseEntity.ok(inputService.getEmployeeInputs(employeeId, periodId));
    }

    @GetMapping("/periods/{periodId}")
    public ResponseEntity<Page<PayrollInputResponse>> forPeriod(@PathVariable UUID periodId, Pageable pageable) {
        Page<PayrollInputResponse> page = inputService.getPeriodInputs(periodId, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page);
    }

    @GetMapping("/periods/{periodId}/by-component/{componentId}")
    public ResponseEntity<List<PayrollInputResponse>> byComponent(@PathVariable UUID periodId, @PathVariable UUID componentId) {
        return ResponseEntity.ok(inputService.getInputsByComponent(periodId, componentId));
    }

    @GetMapping("/periods/{periodId}/summary")
    public ResponseEntity<Map<String, Object>> summary(@PathVariable UUID periodId) {
        return ResponseEntity.ok(inputService.getPeriodInputSummary(periodId));
    }

    @GetMapping("/periods/{periodId}/validation")
    public ResponseEntity<Map<String, Object>> validate(@PathVariable UUID periodId) {
        return ResponseEntity.ok(inputService.validatePeriodInputs(periodId));
    }
}
