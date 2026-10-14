package com.humano.web.rest.me;

import com.humano.domain.shared.User;
import com.humano.dto.hr.requests.EmployeeTrainingSearchRequest;
import com.humano.dto.hr.requests.PerformanceReviewSearchRequest;
import com.humano.dto.hr.responses.EmployeeTrainingResponse;
import com.humano.dto.hr.responses.PerformanceReviewResponse;
import com.humano.dto.payroll.response.PayslipResponse;
import com.humano.dto.payroll.response.SalaryHistoryResponse;
import com.humano.security.annotation.RequireAuthenticated;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.hr.PerformanceReviewService;
import com.humano.service.hr.TrainingService;
import com.humano.service.me.MeService;
import com.humano.service.payroll.CompensationService;
import com.humano.service.payroll.PayslipService;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service, read-only views of the signed-in employee's own work record: pay, payslips,
 * reviews and trainings. The HR endpoints for the same data need HR permissions (they can read
 * anyone); these take no employee id at all, so they can only ever return the caller's own data.
 */
@RestController
@RequestMapping("/api/me")
public class MeWorkResource {

    private final MeService meService;
    private final CompensationService compensationService;
    private final PayslipService payslipService;
    private final PerformanceReviewService performanceReviewService;
    private final TrainingService trainingService;

    public MeWorkResource(
        MeService meService,
        CompensationService compensationService,
        PayslipService payslipService,
        PerformanceReviewService performanceReviewService,
        TrainingService trainingService
    ) {
        this.meService = meService;
        this.compensationService = compensationService;
        this.payslipService = payslipService;
        this.performanceReviewService = performanceReviewService;
        this.trainingService = trainingService;
    }

    /** {@code GET /api/me/compensation/history} : my salary history and current pay. */
    @GetMapping("/compensation/history")
    @RequireAuthenticated
    public SalaryHistoryResponse salaryHistory() {
        return compensationService.getSalaryHistory(me());
    }

    /** {@code GET /api/me/payslips} : my payslips. */
    @GetMapping("/payslips")
    @RequireAuthenticated
    public Page<PayslipResponse> payslips(Pageable pageable) {
        return payslipService.getEmployeePayslips(me(), pageable);
    }

    /** {@code GET /api/me/payslips/{id}} : one of my payslips (another employee's is a 404). */
    @GetMapping("/payslips/{id}")
    @RequireAuthenticated
    public PayslipResponse payslip(@PathVariable UUID id) {
        return own(id);
    }

    /** {@code GET /api/me/payslips/ytd?year=} : my year-to-date totals. */
    @GetMapping("/payslips/ytd")
    @RequireAuthenticated
    public Map<String, Object> payslipYtd(@RequestParam int year) {
        return payslipService.getYearToDateSummary(me(), year);
    }

    /** {@code GET /api/me/payslips/{id}/pdf} : the PDF of one of my payslips. */
    @GetMapping("/payslips/{id}/pdf")
    @RequireAuthenticated
    public ResponseEntity<InputStreamResource> payslipPdf(@PathVariable UUID id) {
        own(id);
        PayslipService.PdfDownload download = payslipService.downloadPdf(id);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.filename() + "\"")
            .body(new InputStreamResource(download.content()));
    }

    /** {@code GET /api/me/performance-reviews} : my reviews. */
    @GetMapping("/performance-reviews")
    @RequireAuthenticated
    public Page<PerformanceReviewResponse> performanceReviews(Pageable pageable) {
        return performanceReviewService.searchPerformanceReviews(
            new PerformanceReviewSearchRequest(me(), null, null, null, null, null, null, null, null, null),
            pageable
        );
    }

    /** {@code GET /api/me/trainings} : my training enrolments. */
    @GetMapping("/trainings")
    @RequireAuthenticated
    public Page<EmployeeTrainingResponse> trainings(Pageable pageable) {
        return trainingService.searchEmployeeTrainings(
            new EmployeeTrainingSearchRequest(me(), null, null, null, null, null, null),
            pageable
        );
    }

    private PayslipResponse own(UUID payslipId) {
        PayslipResponse payslip = payslipService.getPayslip(payslipId);
        if (!me().equals(payslip.employeeId())) {
            throw EntityNotFoundException.create("Payslip", payslipId);
        }
        return payslip;
    }

    private UUID me() {
        return meService
            .getCurrentUser()
            .map(User::getId)
            .orElseThrow(() -> new EntityNotFoundException("Current user could not be found"));
    }
}
