package com.humano.web.rest.payroll;

import com.humano.dto.payroll.request.ApprovePayrollRunRequest;
import com.humano.dto.payroll.request.InitiatePayrollRunRequest;
import com.humano.dto.payroll.request.RecalculatePayrollRequest;
import com.humano.dto.payroll.response.PayrollResultResponse;
import com.humano.dto.payroll.response.PayrollRunResponse;
import com.humano.dto.payroll.response.PayrollRunSummaryResponse;
import com.humano.dto.payroll.response.PayslipResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.payroll.PayrollProcessingService;
import com.humano.service.payroll.PayslipService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Payroll run lifecycle : DRAFT &rarr; CALCULATED &rarr; APPROVED &rarr; POSTED.
 *
 * <p>Payslip access by run + employee (P2.5 / P3.5):
 *
 * <ul>
 *   <li>{@code GET /runs/{id}/payslips/{employeeId}} returns the payslip <strong>JSON</strong>
 *       (metadata + line breakdown).</li>
 *   <li>{@code GET /runs/{id}/payslips/{employeeId}/pdf} streams the rendered PDF
 *       binary &mdash; matches the literal ROADMAP P3.5 acceptance URL. Delegates to
 *       {@link PayslipService#downloadPdf(UUID)} after resolving the payslip via
 *       {@link PayslipService#findByRunAndEmployee(UUID, UUID)}.</li>
 *   <li>The id-scoped {@code GET /api/payroll/payslips/{id}/pdf} on
 *       {@link PayslipResource} is the canonical streaming endpoint; the route above is
 *       a thin alias so the acceptance URL works.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/payroll/runs")
@PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.PAYROLL_ADMIN + "')")
public class PayrollRunResource {

    private static final Logger LOG = LoggerFactory.getLogger(PayrollRunResource.class);

    private final PayrollProcessingService processingService;
    private final PayslipService payslipService;

    public PayrollRunResource(PayrollProcessingService processingService, PayslipService payslipService) {
        this.processingService = processingService;
        this.payslipService = payslipService;
    }

    @PostMapping
    public ResponseEntity<PayrollRunResponse> initiate(@Valid @RequestBody InitiatePayrollRunRequest request) {
        LOG.debug("REST request to initiate PayrollRun: {}", request);
        PayrollRunResponse run = processingService.initiatePayrollRun(request);
        return ResponseEntity.created(URI.create("/api/payroll/runs/" + run.id())).body(run);
    }

    @PostMapping("/{id}/calculate")
    public ResponseEntity<PayrollRunResponse> calculate(@PathVariable UUID id) {
        return ResponseEntity.ok(processingService.calculatePayroll(id));
    }

    /**
     * Approves the run. Path id is authoritative; the body's {@code payrollRunId} is
     * overridden with {@code id} so they can't disagree.
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<PayrollRunResponse> approve(@PathVariable UUID id, @Valid @RequestBody ApprovePayrollRunRequest body) {
        ApprovePayrollRunRequest safe = new ApprovePayrollRunRequest(id, body.approverId(), body.approvalNotes(), body.forceApproval());
        return ResponseEntity.ok(processingService.approvePayrollRun(safe));
    }

    @PostMapping("/{id}/post")
    public ResponseEntity<PayrollRunResponse> post(@PathVariable UUID id) {
        return ResponseEntity.ok(processingService.postPayrollRun(id));
    }

    /**
     * Recalculates. Path id is authoritative (see {@link #approve}).
     */
    @PostMapping("/{id}/recalculate")
    public ResponseEntity<PayrollRunResponse> recalculate(@PathVariable UUID id, @Valid @RequestBody RecalculatePayrollRequest body) {
        RecalculatePayrollRequest safe = new RecalculatePayrollRequest(
            id,
            body.employeeIds(),
            body.recalculateAll(),
            body.componentsToRecalculate(),
            body.reason()
        );
        return ResponseEntity.ok(processingService.recalculatePayroll(safe));
    }

    @GetMapping("/{id}/summary")
    public ResponseEntity<PayrollRunSummaryResponse> summary(@PathVariable UUID id) {
        return ResponseEntity.ok(processingService.getPayrollRunSummary(id));
    }

    @GetMapping("/{id}/results")
    public ResponseEntity<List<PayrollResultResponse>> results(@PathVariable UUID id) {
        return ResponseEntity.ok(payslipService.getResultsForRun(id));
    }

    @PostMapping("/{id}/generate-payslips")
    public ResponseEntity<List<PayslipResponse>> generatePayslips(@PathVariable UUID id) {
        return ResponseEntity.ok(payslipService.generatePayslipsForRun(id));
    }

    /**
     * Returns the payslip metadata (JSON) for a given run + employee. The {@code pdfUrl}
     * field is populated once the PDF generator is implemented. To stream the actual PDF
     * bytes, see {@link PayslipResource#downloadPdf}.
     */
    @GetMapping("/{id}/payslips/{employeeId}")
    public ResponseEntity<?> payslipFor(@PathVariable UUID id, @PathVariable UUID employeeId) {
        return payslipService
            .findByRunAndEmployee(id, employeeId)
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElseGet(() -> {
                ProblemDetail pd = ProblemDetail.forStatus(404);
                pd.setTitle("Payslip not found");
                pd.setDetail("No payslip exists for run " + id + " and employee " + employeeId);
                return ResponseEntity.status(404).body(pd);
            });
    }

    /**
     * P3.5 acceptance URL: streams the rendered PDF for a (run, employee) pair. Resolves
     * the payslip the same way {@link #payslipFor} does (404 when missing) and delegates
     * to {@link PayslipService#downloadPdf}, which generates-on-first-call and serves the
     * cached artifact on subsequent calls.
     */
    @GetMapping("/{id}/payslips/{employeeId}/pdf")
    public ResponseEntity<?> payslipPdfFor(@PathVariable UUID id, @PathVariable UUID employeeId) {
        var slipOpt = payslipService.findByRunAndEmployee(id, employeeId);
        if (slipOpt.isEmpty()) {
            ProblemDetail pd = ProblemDetail.forStatus(404);
            pd.setTitle("Payslip not found");
            pd.setDetail("No payslip exists for run " + id + " and employee " + employeeId);
            return ResponseEntity.status(404).body(pd);
        }
        PayslipService.PdfDownload download = payslipService.downloadPdf(slipOpt.get().id());
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.filename() + "\"")
            .body(new InputStreamResource(download.content()));
    }
}
