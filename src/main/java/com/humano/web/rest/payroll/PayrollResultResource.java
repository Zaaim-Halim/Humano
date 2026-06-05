package com.humano.web.rest.payroll;

import com.humano.dto.payroll.response.PayrollResultResponse;
import com.humano.dto.payroll.response.PayslipResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.payroll.PayslipService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Per-employee payroll result detail . Results are produced by a run; this resource
 * exposes a single result's line breakdown and a payslip-generation entry point.
 */
@RestController
@RequestMapping("/api/payroll/results")
@PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.PAYROLL_ADMIN + "')")
public class PayrollResultResource {

    private final PayslipService payslipService;

    public PayrollResultResource(PayslipService payslipService) {
        this.payslipService = payslipService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PayrollResultResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(payslipService.getResultDetails(id));
    }

    @PostMapping("/{id}/generate-payslip")
    public ResponseEntity<PayslipResponse> generatePayslip(@PathVariable UUID id) {
        return ResponseEntity.ok(payslipService.generatePayslip(id));
    }
}
