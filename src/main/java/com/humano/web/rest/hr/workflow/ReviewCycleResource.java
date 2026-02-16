package com.humano.web.rest.hr.workflow;

import com.humano.domain.enumeration.hr.ReviewCyclePhase;
import com.humano.dto.hr.workflow.requests.InitiateReviewCycleRequest;
import com.humano.dto.hr.workflow.requests.ManagerReviewRequest;
import com.humano.dto.hr.workflow.requests.SelfAssessmentRequest;
import com.humano.dto.hr.workflow.responses.ReviewCycleResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.hr.workflow.PerformanceReviewCycleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for managing performance review cycle workflows.
 */
@RestController
@RequestMapping("/api/hr/workflows/review-cycles")
public class ReviewCycleResource {

    private static final Logger LOG = LoggerFactory.getLogger(ReviewCycleResource.class);

    private final PerformanceReviewCycleService reviewCycleService;

    public ReviewCycleResource(PerformanceReviewCycleService reviewCycleService) {
        this.reviewCycleService = reviewCycleService;
    }

    /**
     * {@code POST  /} : Initiate a new review cycle.
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<ReviewCycleResponse> initiateCycle(@Valid @RequestBody InitiateReviewCycleRequest request) {
        LOG.debug("REST request to initiate review cycle: {}", request.name());
        ReviewCycleResponse result = reviewCycleService.initiateCycle(request);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET  /:cycleId} : Get review cycle status.
     */
    @GetMapping("/{cycleId}")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<ReviewCycleResponse> getCycleStatus(@PathVariable UUID cycleId) {
        LOG.debug("REST request to get review cycle status: {}", cycleId);
        ReviewCycleResponse result = reviewCycleService.getCycleStatus(cycleId);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET  /active} : Get all active review cycles.
     */
    @GetMapping("/active")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "', '" +
        AuthoritiesConstants.EMPLOYEE +
        "')"
    )
    public ResponseEntity<List<ReviewCycleResponse>> getActiveCycles() {
        LOG.debug("REST request to get active review cycles");
        List<ReviewCycleResponse> result = reviewCycleService.getActiveCycles();
        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET  /phase/:phase} : Get review cycles by phase.
     */
    @GetMapping("/phase/{phase}")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<List<ReviewCycleResponse>> getCyclesByPhase(@PathVariable ReviewCyclePhase phase) {
        LOG.debug("REST request to get review cycles by phase: {}", phase);
        List<ReviewCycleResponse> result = reviewCycleService.getCyclesByPhase(phase);
        return ResponseEntity.ok(result);
    }

    // ==================== PHASE MANAGEMENT ====================

    /**
     * {@code POST  /:cycleId/phases/self-assessment/start} : Start self-assessment phase.
     */
    @PostMapping("/{cycleId}/phases/self-assessment/start")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<ReviewCycleResponse> startSelfAssessmentPhase(@PathVariable UUID cycleId) {
        LOG.debug("REST request to start self-assessment phase for cycle: {}", cycleId);
        ReviewCycleResponse result = reviewCycleService.startSelfAssessmentPhase(cycleId);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code POST  /:cycleId/phases/manager-review/start} : Start manager review phase.
     */
    @PostMapping("/{cycleId}/phases/manager-review/start")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<ReviewCycleResponse> startManagerReviewPhase(@PathVariable UUID cycleId) {
        LOG.debug("REST request to start manager review phase for cycle: {}", cycleId);
        ReviewCycleResponse result = reviewCycleService.startManagerReviewPhase(cycleId);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code POST  /:cycleId/phases/calibration/start} : Start calibration phase.
     */
    @PostMapping("/{cycleId}/phases/calibration/start")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<ReviewCycleResponse> startCalibrationPhase(@PathVariable UUID cycleId) {
        LOG.debug("REST request to start calibration phase for cycle: {}", cycleId);
        ReviewCycleResponse result = reviewCycleService.startCalibrationPhase(cycleId);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code POST  /:cycleId/phases/feedback/start} : Start feedback delivery phase.
     */
    @PostMapping("/{cycleId}/phases/feedback/start")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<ReviewCycleResponse> startFeedbackDeliveryPhase(@PathVariable UUID cycleId) {
        LOG.debug("REST request to start feedback delivery phase for cycle: {}", cycleId);
        ReviewCycleResponse result = reviewCycleService.startFeedbackDeliveryPhase(cycleId);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code POST  /:cycleId/close} : Close a review cycle.
     */
    @PostMapping("/{cycleId}/close")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<ReviewCycleResponse> closeCycle(@PathVariable UUID cycleId) {
        LOG.debug("REST request to close review cycle: {}", cycleId);
        ReviewCycleResponse result = reviewCycleService.closeCycle(cycleId);
        return ResponseEntity.ok(result);
    }

    // ==================== REVIEW SUBMISSIONS ====================

    /**
     * {@code POST  /:cycleId/self-assessment} : Submit a self-assessment.
     */
    @PostMapping("/{cycleId}/self-assessment")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "', '" +
        AuthoritiesConstants.EMPLOYEE +
        "')"
    )
    public ResponseEntity<Void> submitSelfAssessment(@PathVariable UUID cycleId, @Valid @RequestBody SelfAssessmentRequest request) {
        LOG.debug("REST request to submit self-assessment for cycle {}: employee {}", cycleId, request.employeeId());
        reviewCycleService.submitSelfAssessment(cycleId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * {@code POST  /:cycleId/manager-review} : Submit a manager review.
     */
    @PostMapping("/{cycleId}/manager-review")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "', '" +
        AuthoritiesConstants.EMPLOYEE +
        "')"
    )
    public ResponseEntity<Void> submitManagerReview(@PathVariable UUID cycleId, @Valid @RequestBody ManagerReviewRequest request) {
        LOG.debug("REST request to submit manager review for cycle {}: employee {}", cycleId, request.employeeId());
        reviewCycleService.submitManagerReview(cycleId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * {@code POST  /:cycleId/feedback/:employeeId} : Record feedback meeting completion.
     */
    @PostMapping("/{cycleId}/feedback/{employeeId}")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "', '" +
        AuthoritiesConstants.EMPLOYEE +
        "')"
    )
    public ResponseEntity<Void> recordFeedbackMeeting(
        @PathVariable UUID cycleId,
        @PathVariable UUID employeeId,
        @RequestParam(required = false) String notes
    ) {
        LOG.debug("REST request to record feedback meeting for cycle {}: employee {}", cycleId, employeeId);
        reviewCycleService.recordFeedbackMeeting(cycleId, employeeId, notes);
        return ResponseEntity.ok().build();
    }

    // ==================== PROGRESS & REMINDERS ====================

    /**
     * {@code GET  /:cycleId/progress} : Get cycle progress.
     */
    @GetMapping("/{cycleId}/progress")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<ReviewCycleResponse.CycleProgress> getCycleProgress(@PathVariable UUID cycleId) {
        LOG.debug("REST request to get cycle progress: {}", cycleId);
        ReviewCycleResponse.CycleProgress result = reviewCycleService.getCycleProgress(cycleId);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code POST  /:cycleId/reminders} : Send phase reminders.
     */
    @PostMapping("/{cycleId}/reminders")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<Void> sendPhaseReminders(@PathVariable UUID cycleId) {
        LOG.debug("REST request to send phase reminders for cycle: {}", cycleId);
        reviewCycleService.sendPhaseReminders(cycleId);
        return ResponseEntity.ok().build();
    }
}
