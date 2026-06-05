package com.humano.web.rest.payroll;

import com.humano.dto.payroll.request.PayslipSearchRequest;
import com.humano.dto.payroll.response.PayslipResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.payroll.PayslipService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.PaginationUtil;

/**
 * Payslip queries (P2.5). Generation entry points live on {@link PayrollRunResource}
 * (whole-run) and {@link PayrollResultResource} (single result).
 * <p>
 * The PDF binary endpoint {@link #downloadPdf} is wired but returns 501 until P3.5 lands
 * the PDF generator; once {@code Payslip.pdfUrl} is populated, this method can stream from
 * the per-tenant storage backend without further changes to the surface.
 * <p>
 * Authorization: this resource is intentionally <strong>not</strong> opened to
 * {@code EMPLOYEE}. None of the read methods below check caller-vs-target, so granting
 * the bare role would let any authenticated employee read a colleague's payslip by
 * iterating UUIDs (IDOR on the most sensitive data in the system). Real self-service
 * &mdash; "GET my own payslips" with a {@code caller == employeeId} check &mdash; lands
 * with P6.1 (method-level permission tightening). Until then keep the class-level grant
 * narrow.
 */
@RestController
@RequestMapping("/api/payroll/payslips")
@PreAuthorize(
    "hasAnyAuthority('" +
    AuthoritiesConstants.ADMIN +
    "', '" +
    AuthoritiesConstants.PAYROLL_ADMIN +
    "', '" +
    AuthoritiesConstants.HR_MANAGER +
    "')"
)
public class PayslipResource {

    private final PayslipService payslipService;

    public PayslipResource(PayslipService payslipService) {
        this.payslipService = payslipService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PayslipResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(payslipService.getPayslip(id));
    }

    @GetMapping("/by-number/{number}")
    public ResponseEntity<PayslipResponse> byNumber(@PathVariable String number) {
        return ResponseEntity.ok(payslipService.getPayslipByNumber(number));
    }

    @GetMapping("/employees/{employeeId}")
    public ResponseEntity<Page<PayslipResponse>> forEmployee(@PathVariable UUID employeeId, Pageable pageable) {
        Page<PayslipResponse> page = payslipService.getEmployeePayslips(employeeId, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page);
    }

    @GetMapping("/employees/{employeeId}/latest")
    public ResponseEntity<PayslipResponse> latestForEmployee(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(payslipService.getLatestPayslip(employeeId));
    }

    @GetMapping("/employees/{employeeId}/ytd")
    public ResponseEntity<Map<String, Object>> ytdForEmployee(@PathVariable UUID employeeId, @RequestParam int year) {
        return ResponseEntity.ok(payslipService.getYearToDateSummary(employeeId, year));
    }

    @GetMapping("/periods/{periodId}")
    public ResponseEntity<Page<PayslipResponse>> forPeriod(@PathVariable UUID periodId, Pageable pageable) {
        Page<PayslipResponse> page = payslipService.getPayslipsForPeriod(periodId, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page);
    }

    @PostMapping("/search")
    public ResponseEntity<Page<PayslipResponse>> search(@Valid @RequestBody PayslipSearchRequest body, Pageable pageable) {
        Page<PayslipResponse> page = payslipService.searchPayslipsAdvanced(body, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * Streams the payslip PDF. Returns 501 until P3.5 wires the PDF generator and
     * populates {@code Payslip.pdfUrl}; the route exists so downstream code can be
     * written against the stable URL.
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<ProblemDetail> downloadPdf(@PathVariable UUID id) {
        PayslipResponse slip = payslipService.getPayslip(id);
        if (slip.pdfUrl() == null || slip.pdfUrl().isBlank()) {
            ProblemDetail pd = ProblemDetail.forStatus(501);
            pd.setTitle("Payslip PDF not yet generated");
            pd.setDetail("Payslip " + id + " has no PDF on file; PDF generation requires roadmap task P3.5.");
            return ResponseEntity.status(501).body(pd);
        }
        ProblemDetail pd = ProblemDetail.forStatus(501);
        pd.setTitle("PDF streaming not yet wired");
        pd.setDetail("Payslip " + id + " has a recorded pdfUrl (" + slip.pdfUrl() + ") but streaming via StorageFactory is part of P3.5.");
        return ResponseEntity.status(501).body(pd);
    }
}
