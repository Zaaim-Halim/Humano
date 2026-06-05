package com.humano.web.rest.payroll;

import com.humano.dto.payroll.request.PayslipSearchRequest;
import com.humano.dto.payroll.response.PayslipResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.payroll.PayslipService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.PaginationUtil;

/**
 * Payslip queries. Generation entry points live on {@link PayrollRunResource}
 * (whole-run) and {@link PayrollResultResource} (single result). The PDF binary
 * endpoint {@link #downloadPdf} renders (or re-serves the cached artifact) and streams
 * it from the per-tenant storage backend (P3.5).
 * <p>
 * Authorization: this resource is intentionally <strong>not</strong> opened to
 * {@code EMPLOYEE}. None of the read methods below check caller-vs-target, so granting
 * the bare role would let any authenticated employee read a colleague's payslip by
 * iterating UUIDs (IDOR on the most sensitive data in the system). Real self-service
 * &mdash; "GET my own payslips" with a {@code caller == employeeId} check &mdash; is a future enhancement.
 * Until then keep the class-level grant narrow.
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
     * Streams the payslip PDF for {@code id}. On first call the PDF is rendered from the
     * Thymeleaf template, stored in the tenant's storage backend, and the reference
     * recorded on {@code Payslip.pdfUrl}; subsequent calls stream the cached artifact.
     *
     * <p>Returns {@code Content-Type: application/pdf} with a {@code Content-Disposition:
     * attachment; filename="{payslipNumber}.pdf"} header.
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<InputStreamResource> downloadPdf(@PathVariable UUID id) {
        PayslipService.PdfDownload download = payslipService.downloadPdf(id);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.filename() + "\"")
            .body(new InputStreamResource(download.content()));
    }
}
