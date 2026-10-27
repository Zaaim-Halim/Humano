package com.humano.web.rest.hr.workflow;

import com.humano.domain.enumeration.hr.PerformanceReviewStatus;
import com.humano.domain.enumeration.hr.ReviewCyclePhase;
import com.humano.dto.hr.requests.PerformanceReviewSearchRequest;
import com.humano.dto.hr.responses.PerformanceReviewResponse;
import com.humano.dto.hr.workflow.requests.AcknowledgeReviewRequest;
import com.humano.dto.hr.workflow.requests.AddCycleEmployeesRequest;
import com.humano.dto.hr.workflow.requests.AssignReviewerRequest;
import com.humano.dto.hr.workflow.requests.CalibrateReviewRequest;
import com.humano.dto.hr.workflow.requests.DeclineFeedbackRequest;
import com.humano.dto.hr.workflow.requests.ExcludeReviewRequest;
import com.humano.dto.hr.workflow.requests.FeedbackMeetingRequest;
import com.humano.dto.hr.workflow.requests.InitiateReviewCycleRequest;
import com.humano.dto.hr.workflow.requests.ManagerReviewRequest;
import com.humano.dto.hr.workflow.requests.MoveBackPhaseRequest;
import com.humano.dto.hr.workflow.requests.RequestFeedbackRequest;
import com.humano.dto.hr.workflow.requests.ResolveDisputeRequest;
import com.humano.dto.hr.workflow.requests.SelfAssessmentRequest;
import com.humano.dto.hr.workflow.requests.SubmitFeedbackRequest;
import com.humano.dto.hr.workflow.responses.CycleCandidate;
import com.humano.dto.hr.workflow.responses.FeedbackCandidate;
import com.humano.dto.hr.workflow.responses.RatingChangeResponse;
import com.humano.dto.hr.workflow.responses.ReviewCycleResponse;
import com.humano.dto.hr.workflow.responses.ReviewFeedbackCount;
import com.humano.dto.hr.workflow.responses.ReviewFeedbackOverview;
import com.humano.dto.hr.workflow.responses.ReviewFeedbackResponse;
import com.humano.dto.hr.workflow.responses.ReviewSignOffResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.SecurityExpressions;
import com.humano.security.SecurityUtils;
import com.humano.security.annotation.RequireAuthenticated;
import com.humano.security.annotation.RequireFeature;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.billing.entitlement.FeatureCodes;
import com.humano.service.hr.PerformanceReviewService;
import com.humano.service.hr.workflow.PerformanceReviewCycleService;
import com.humano.service.hr.workflow.ReviewFeedbackService;
import com.humano.service.hr.workflow.ReviewPdfService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.PaginationUtil;

/**
 * REST controller for managing performance review cycle workflows.
 * <p>
 * Running a cycle needs {@code CONFIGURE_PERFORMANCE_CYCLES}, reading cycles and their reviews
 * {@code VIEW_PERFORMANCE_REVIEWS}. Writing a review is relational rather than role-based: the
 * self-assessment is the signed-in employee's own, and the manager review and feedback meeting
 * belong to the review's assigned reviewer (or anyone with {@code MANAGE_PERFORMANCE_REVIEWS}).
 * Feedback requests follow the same rule, with contributors answering their own requests.
 */
@RequireFeature(FeatureCodes.PERFORMANCE)
@RestController
@RequestMapping("/api/hr/workflows/review-cycles")
public class ReviewCycleResource {

    private static final Logger LOG = LoggerFactory.getLogger(ReviewCycleResource.class);

    private final PerformanceReviewCycleService reviewCycleService;
    private final PerformanceReviewService performanceReviewService;
    private final ReviewFeedbackService feedbackService;
    private final SecurityExpressions securityExpressions;
    private final ReviewPdfService reviewPdfService;

    public ReviewCycleResource(
        PerformanceReviewCycleService reviewCycleService,
        PerformanceReviewService performanceReviewService,
        ReviewFeedbackService feedbackService,
        SecurityExpressions securityExpressions,
        ReviewPdfService reviewPdfService
    ) {
        this.reviewCycleService = reviewCycleService;
        this.performanceReviewService = performanceReviewService;
        this.feedbackService = feedbackService;
        this.securityExpressions = securityExpressions;
        this.reviewPdfService = reviewPdfService;
    }

    /**
     * {@code POST  /} : Initiate a new review cycle.
     */
    @PostMapping
    @RequirePermission(PermissionsConstants.CONFIGURE_PERFORMANCE_CYCLES)
    public ResponseEntity<ReviewCycleResponse> initiateCycle(@Valid @RequestBody InitiateReviewCycleRequest request) {
        LOG.debug("REST request to initiate review cycle: {}", request.name());
        ReviewCycleResponse result = reviewCycleService.initiateCycle(request);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code PUT  /:cycleId} : Update a cycle still in draft.
     */
    @PutMapping("/{cycleId}")
    @RequirePermission(PermissionsConstants.CONFIGURE_PERFORMANCE_CYCLES)
    public ResponseEntity<ReviewCycleResponse> updateCycle(
        @PathVariable UUID cycleId,
        @Valid @RequestBody InitiateReviewCycleRequest request
    ) {
        LOG.debug("REST request to update review cycle: {}", cycleId);
        return ResponseEntity.ok(reviewCycleService.updateCycle(cycleId, request));
    }

    /**
     * {@code GET  /} : All review cycles, paginated.
     */
    @GetMapping
    @RequirePermission(PermissionsConstants.VIEW_PERFORMANCE_REVIEWS)
    public ResponseEntity<Page<ReviewCycleResponse>> getAllCycles(Pageable pageable) {
        LOG.debug("REST request to get all review cycles");
        Page<ReviewCycleResponse> page = reviewCycleService.getAllReviewCycles(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /:cycleId} : Get review cycle status, with progress per department.
     */
    @GetMapping("/{cycleId}")
    @RequirePermission(PermissionsConstants.VIEW_PERFORMANCE_REVIEWS)
    public ResponseEntity<ReviewCycleResponse> getCycleStatus(@PathVariable UUID cycleId) {
        LOG.debug("REST request to get review cycle status: {}", cycleId);
        ReviewCycleResponse result = reviewCycleService.getCycleStatus(cycleId);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET  /active} : Get all active review cycles.
     */
    @GetMapping("/active")
    @RequirePermission(PermissionsConstants.VIEW_PERFORMANCE_REVIEWS)
    public ResponseEntity<List<ReviewCycleResponse>> getActiveCycles() {
        LOG.debug("REST request to get active review cycles");
        List<ReviewCycleResponse> result = reviewCycleService.getActiveCycles();
        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET  /phase/:phase} : Get review cycles by phase.
     */
    @GetMapping("/phase/{phase}")
    @RequirePermission(PermissionsConstants.VIEW_PERFORMANCE_REVIEWS)
    public ResponseEntity<List<ReviewCycleResponse>> getCyclesByPhase(@PathVariable ReviewCyclePhase phase) {
        LOG.debug("REST request to get review cycles by phase: {}", phase);
        List<ReviewCycleResponse> result = reviewCycleService.getCyclesByPhase(phase);
        return ResponseEntity.ok(result);
    }

    // ==================== PHASE MANAGEMENT ====================

    /**
     * {@code POST  /:cycleId/phases/self-assessment/start} : Start self-assessment phase, creating the reviews.
     */
    @PostMapping("/{cycleId}/phases/self-assessment/start")
    @RequirePermission(PermissionsConstants.CONFIGURE_PERFORMANCE_CYCLES)
    public ResponseEntity<ReviewCycleResponse> startSelfAssessmentPhase(@PathVariable UUID cycleId) {
        LOG.debug("REST request to start self-assessment phase for cycle: {}", cycleId);
        ReviewCycleResponse result = reviewCycleService.startSelfAssessmentPhase(cycleId);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code POST  /:cycleId/phases/manager-review/start} : Start manager review phase.
     */
    @PostMapping("/{cycleId}/phases/manager-review/start")
    @RequirePermission(PermissionsConstants.CONFIGURE_PERFORMANCE_CYCLES)
    public ResponseEntity<ReviewCycleResponse> startManagerReviewPhase(@PathVariable UUID cycleId) {
        LOG.debug("REST request to start manager review phase for cycle: {}", cycleId);
        ReviewCycleResponse result = reviewCycleService.startManagerReviewPhase(cycleId);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code POST  /:cycleId/phases/calibration/start} : Start calibration phase.
     */
    @PostMapping("/{cycleId}/phases/calibration/start")
    @RequirePermission(PermissionsConstants.CONFIGURE_PERFORMANCE_CYCLES)
    public ResponseEntity<ReviewCycleResponse> startCalibrationPhase(@PathVariable UUID cycleId) {
        LOG.debug("REST request to start calibration phase for cycle: {}", cycleId);
        ReviewCycleResponse result = reviewCycleService.startCalibrationPhase(cycleId);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code POST  /:cycleId/phases/feedback/start} : Start feedback delivery phase.
     */
    @PostMapping("/{cycleId}/phases/feedback/start")
    @RequirePermission(PermissionsConstants.CONFIGURE_PERFORMANCE_CYCLES)
    public ResponseEntity<ReviewCycleResponse> startFeedbackDeliveryPhase(@PathVariable UUID cycleId) {
        LOG.debug("REST request to start feedback delivery phase for cycle: {}", cycleId);
        ReviewCycleResponse result = reviewCycleService.startFeedbackDeliveryPhase(cycleId);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code POST  /:cycleId/phases/goal-setting/start} : Start goal setting (optional, after feedback delivery).
     */
    @PostMapping("/{cycleId}/phases/goal-setting/start")
    @RequirePermission(PermissionsConstants.CONFIGURE_PERFORMANCE_CYCLES)
    public ResponseEntity<ReviewCycleResponse> startGoalSettingPhase(@PathVariable UUID cycleId) {
        LOG.debug("REST request to start goal setting for cycle: {}", cycleId);
        return ResponseEntity.ok(reviewCycleService.startGoalSettingPhase(cycleId));
    }

    /**
     * {@code POST  /:cycleId/close} : Close a review cycle.
     */
    @PostMapping("/{cycleId}/close")
    @RequirePermission(PermissionsConstants.CONFIGURE_PERFORMANCE_CYCLES)
    public ResponseEntity<ReviewCycleResponse> closeCycle(@PathVariable UUID cycleId) {
        LOG.debug("REST request to close review cycle: {}", cycleId);
        ReviewCycleResponse result = reviewCycleService.closeCycle(cycleId);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code POST  /:cycleId/archive} : Archive a completed review cycle.
     */
    @PostMapping("/{cycleId}/archive")
    @RequirePermission(PermissionsConstants.CONFIGURE_PERFORMANCE_CYCLES)
    public ResponseEntity<ReviewCycleResponse> archiveCycle(@PathVariable UUID cycleId) {
        LOG.debug("REST request to archive review cycle: {}", cycleId);
        return ResponseEntity.ok(reviewCycleService.archiveCycle(cycleId));
    }

    // ==================== REVIEWS ====================

    /**
     * {@code GET  /:cycleId/reviews} : The cycle's reviews, optionally of one status, paginated.
     */
    @GetMapping("/{cycleId}/reviews")
    @RequirePermission(PermissionsConstants.VIEW_PERFORMANCE_REVIEWS)
    public ResponseEntity<Page<PerformanceReviewResponse>> getCycleReviews(
        @PathVariable UUID cycleId,
        @RequestParam(required = false) PerformanceReviewStatus status,
        Pageable pageable
    ) {
        LOG.debug("REST request to get reviews of cycle {} with status {}", cycleId, status);
        Page<PerformanceReviewResponse> page = performanceReviewService.searchPerformanceReviews(
            new PerformanceReviewSearchRequest(null, null, null, null, null, null, null, null, null, null, cycleId, status),
            pageable
        );
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /my-reviews} : The signed-in employee's own reviews in running cycles.
     */
    @GetMapping("/my-reviews")
    @RequireAuthenticated
    public ResponseEntity<List<PerformanceReviewResponse>> getMyReviews() {
        return ResponseEntity.ok(reviewCycleService.getMyCycleReviews(currentEmployeeId()));
    }

    /**
     * {@code GET  /my-team-reviews} : Reviews the signed-in employee has to write, in running cycles.
     */
    @GetMapping("/my-team-reviews")
    @RequireAuthenticated
    public ResponseEntity<List<PerformanceReviewResponse>> getReviewsToWrite() {
        return ResponseEntity.ok(reviewCycleService.getReviewsToWrite(currentEmployeeId()));
    }

    /**
     * {@code GET  /reviews/:reviewId} : One cycle review — for the reviewed employee (their view), its
     * reviewer, an approver of its sign-off, or HR with {@code VIEW_PERFORMANCE_REVIEWS}.
     */
    @GetMapping("/reviews/{reviewId}")
    @RequireAuthenticated
    public ResponseEntity<PerformanceReviewResponse> getReview(@PathVariable UUID reviewId) {
        LOG.debug("REST request to get cycle review {}", reviewId);
        boolean hrView = securityExpressions.hasPermission(PermissionsConstants.VIEW_PERFORMANCE_REVIEWS);
        return ResponseEntity.ok(reviewCycleService.getReview(reviewId, currentEmployeeId(), hrView));
    }

    /**
     * {@code GET  /reviews/:reviewId/pdf} : A shared review as a PDF — always the employee's view,
     * whoever downloads it (the employee, the reviewer, an approver of its sign-off, or HR with
     * {@code VIEW_PERFORMANCE_REVIEWS}). Each download is logged.
     */
    @GetMapping("/reviews/{reviewId}/pdf")
    @RequireAuthenticated
    public ResponseEntity<byte[]> getReviewPdf(@PathVariable UUID reviewId) {
        boolean hrView = securityExpressions.hasPermission(PermissionsConstants.VIEW_PERFORMANCE_REVIEWS);
        ReviewPdfService.ReviewPdf pdf = reviewPdfService.export(reviewId, currentEmployeeId(), hrView);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(pdf.filename(), StandardCharsets.UTF_8).build().toString()
            )
            .body(pdf.content());
    }

    /**
     * {@code POST  /:cycleId/self-assessment} : Submit the signed-in employee's self-assessment.
     */
    @PostMapping("/{cycleId}/self-assessment")
    @RequireAuthenticated
    public ResponseEntity<PerformanceReviewResponse> submitSelfAssessment(
        @PathVariable UUID cycleId,
        @Valid @RequestBody SelfAssessmentRequest request
    ) {
        LOG.debug("REST request to submit self-assessment for cycle {}", cycleId);
        return ResponseEntity.ok(reviewCycleService.submitSelfAssessment(cycleId, currentEmployeeId(), request));
    }

    /**
     * {@code POST  /:cycleId/manager-review} : Submit a manager review (assigned reviewer or HR).
     */
    @PostMapping("/{cycleId}/manager-review")
    @RequireAuthenticated
    public ResponseEntity<PerformanceReviewResponse> submitManagerReview(
        @PathVariable UUID cycleId,
        @Valid @RequestBody ManagerReviewRequest request
    ) {
        LOG.debug("REST request to submit manager review for cycle {}: employee {}", cycleId, request.employeeId());
        return ResponseEntity.ok(reviewCycleService.submitManagerReview(cycleId, currentEmployeeId(), canManageReviews(), request));
    }

    /**
     * {@code POST  /:cycleId/feedback/:employeeId} : Record the feedback meeting, sharing the review with the employee.
     */
    @PostMapping("/{cycleId}/feedback/{employeeId}")
    @RequireAuthenticated
    public ResponseEntity<PerformanceReviewResponse> recordFeedbackMeeting(
        @PathVariable UUID cycleId,
        @PathVariable UUID employeeId,
        @Valid @RequestBody(required = false) FeedbackMeetingRequest request
    ) {
        LOG.debug("REST request to record feedback meeting for cycle {}: employee {}", cycleId, employeeId);
        String notes = request != null ? request.notes() : null;
        return ResponseEntity.ok(
            reviewCycleService.recordFeedbackMeeting(cycleId, employeeId, currentEmployeeId(), canManageReviews(), notes)
        );
    }

    /**
     * {@code PUT  /:cycleId/reviews/:reviewId/calibration} : Calibrate a review's final rating.
     */
    @PutMapping("/{cycleId}/reviews/{reviewId}/calibration")
    @RequirePermission(PermissionsConstants.MANAGE_CALIBRATION)
    public ResponseEntity<PerformanceReviewResponse> calibrateReview(
        @PathVariable UUID cycleId,
        @PathVariable UUID reviewId,
        @Valid @RequestBody CalibrateReviewRequest request
    ) {
        LOG.debug("REST request to calibrate review {} in cycle {}", reviewId, cycleId);
        return ResponseEntity.ok(reviewCycleService.calibrateReview(cycleId, reviewId, currentEmployeeId(), request));
    }

    /**
     * {@code POST  /reviews/:reviewId/acknowledgement} : The reviewed employee acknowledges their
     * shared review, or disputes it.
     */
    @PostMapping("/reviews/{reviewId}/acknowledgement")
    @RequireAuthenticated
    public ResponseEntity<PerformanceReviewResponse> acknowledgeReview(
        @PathVariable UUID reviewId,
        @Valid @RequestBody AcknowledgeReviewRequest request
    ) {
        LOG.debug("REST request to acknowledge review {} (disputed: {})", reviewId, request.disputed());
        return ResponseEntity.ok(reviewCycleService.acknowledgeReview(reviewId, currentEmployeeId(), request));
    }

    /**
     * {@code POST  /reviews/:reviewId/dispute-resolution} : HR resolves a disputed review.
     */
    @PostMapping("/reviews/{reviewId}/dispute-resolution")
    @RequirePermission(PermissionsConstants.MANAGE_PERFORMANCE_REVIEWS)
    public ResponseEntity<PerformanceReviewResponse> resolveDispute(
        @PathVariable UUID reviewId,
        @Valid @RequestBody ResolveDisputeRequest request
    ) {
        LOG.debug("REST request to resolve the dispute of review {}", reviewId);
        return ResponseEntity.ok(reviewCycleService.resolveDispute(reviewId, currentEmployeeId(), request));
    }

    /**
     * {@code GET  /reviews/:reviewId/sign-off} : The review's second-level sign-off — for its
     * reviewer, its approvers and HR, never the reviewed employee.
     */
    @GetMapping("/reviews/{reviewId}/sign-off")
    @RequireAuthenticated
    public ResponseEntity<ReviewSignOffResponse> getSignOff(@PathVariable UUID reviewId) {
        boolean hrView = securityExpressions.hasPermission(PermissionsConstants.VIEW_PERFORMANCE_REVIEWS);
        return ResponseEntity.ok(reviewCycleService.getSignOff(reviewId, currentEmployeeId(), hrView));
    }

    /**
     * {@code GET  /reviews/:reviewId/rating-changes} : Changes to the final rating after the reviewer gave it.
     */
    @GetMapping("/reviews/{reviewId}/rating-changes")
    @RequirePermission(PermissionsConstants.VIEW_PERFORMANCE_REVIEWS)
    public ResponseEntity<List<RatingChangeResponse>> getRatingChanges(@PathVariable UUID reviewId) {
        return ResponseEntity.ok(reviewCycleService.getRatingChanges(reviewId, currentEmployeeId()));
    }

    /**
     * {@code PUT  /:cycleId/reviews/:reviewId/reviewer} : Assign who reviews the employee.
     */
    @PutMapping("/{cycleId}/reviews/{reviewId}/reviewer")
    @RequirePermission(PermissionsConstants.MANAGE_PERFORMANCE_REVIEWS)
    public ResponseEntity<PerformanceReviewResponse> assignReviewer(
        @PathVariable UUID cycleId,
        @PathVariable UUID reviewId,
        @Valid @RequestBody AssignReviewerRequest request
    ) {
        LOG.debug("REST request to assign reviewer {} to review {} in cycle {}", request.reviewerId(), reviewId, cycleId);
        return ResponseEntity.ok(reviewCycleService.assignReviewer(cycleId, reviewId, currentEmployeeId(), request.reviewerId()));
    }

    // ==================== ADDITIONAL FEEDBACK ====================

    /**
     * {@code GET  /:cycleId/reviews/:reviewId/feedback-requests} : A review's feedback requests, as
     * the caller may see them (the reviewed employee, the reviewer, or HR).
     */
    @GetMapping("/{cycleId}/reviews/{reviewId}/feedback-requests")
    @RequireAuthenticated
    public ResponseEntity<ReviewFeedbackOverview> getFeedbackRequests(@PathVariable UUID cycleId, @PathVariable UUID reviewId) {
        boolean hrView = securityExpressions.hasPermission(PermissionsConstants.VIEW_PERFORMANCE_REVIEWS);
        return ResponseEntity.ok(feedbackService.getForReview(cycleId, reviewId, currentEmployeeId(), hrView, canManageReviews()));
    }

    /**
     * {@code POST  /:cycleId/reviews/:reviewId/feedback-requests} : Ask someone for feedback (the
     * reviewer or HR), or nominate them (the reviewed employee).
     */
    @PostMapping("/{cycleId}/reviews/{reviewId}/feedback-requests")
    @RequireAuthenticated
    public ResponseEntity<ReviewFeedbackResponse> requestFeedback(
        @PathVariable UUID cycleId,
        @PathVariable UUID reviewId,
        @Valid @RequestBody RequestFeedbackRequest request
    ) {
        LOG.debug("REST request to ask {} for feedback on review {}", request.contributorId(), reviewId);
        return ResponseEntity.ok(feedbackService.request(cycleId, reviewId, currentEmployeeId(), canManageReviews(), request));
    }

    /**
     * {@code GET  /:cycleId/reviews/:reviewId/feedback-candidates?query=} : Colleagues who could be
     * asked for feedback on the review, by name (at most 10) — for whoever can add one now.
     */
    @GetMapping("/{cycleId}/reviews/{reviewId}/feedback-candidates")
    @RequireAuthenticated
    public ResponseEntity<List<FeedbackCandidate>> getFeedbackCandidates(
        @PathVariable UUID cycleId,
        @PathVariable UUID reviewId,
        @RequestParam(required = false) String query
    ) {
        return ResponseEntity.ok(feedbackService.candidates(cycleId, reviewId, currentEmployeeId(), canManageReviews(), query));
    }

    /**
     * {@code GET  /:cycleId/feedback-counts} : Feedback requested and received, per review of the cycle.
     */
    @GetMapping("/{cycleId}/feedback-counts")
    @RequirePermission(PermissionsConstants.VIEW_PERFORMANCE_REVIEWS)
    public ResponseEntity<List<ReviewFeedbackCount>> getFeedbackCounts(@PathVariable UUID cycleId) {
        return ResponseEntity.ok(feedbackService.countsByReview(cycleId));
    }

    /**
     * {@code GET  /my-feedback-requests} : Feedback the signed-in employee was asked for, in running cycles.
     */
    @GetMapping("/my-feedback-requests")
    @RequireAuthenticated
    public ResponseEntity<List<ReviewFeedbackResponse>> getMyFeedbackRequests() {
        return ResponseEntity.ok(feedbackService.getMyRequests(currentEmployeeId()));
    }

    /**
     * {@code GET  /feedback-requests/:requestId} : A feedback request, for the contributor answering it.
     */
    @GetMapping("/feedback-requests/{requestId}")
    @RequireAuthenticated
    public ResponseEntity<ReviewFeedbackResponse> getFeedbackRequest(@PathVariable UUID requestId) {
        return ResponseEntity.ok(feedbackService.getForContributor(requestId, currentEmployeeId()));
    }

    /**
     * {@code PUT  /feedback-requests/:requestId/submit} : Give (or revise) feedback, as the contributor.
     */
    @PutMapping("/feedback-requests/{requestId}/submit")
    @RequireAuthenticated
    public ResponseEntity<ReviewFeedbackResponse> submitFeedback(
        @PathVariable UUID requestId,
        @Valid @RequestBody SubmitFeedbackRequest request
    ) {
        LOG.debug("REST request to submit feedback {}", requestId);
        return ResponseEntity.ok(feedbackService.submit(requestId, currentEmployeeId(), request));
    }

    /**
     * {@code POST  /feedback-requests/:requestId/approve} : Approve an employee's nomination (the reviewer or HR).
     */
    @PostMapping("/feedback-requests/{requestId}/approve")
    @RequireAuthenticated
    public ResponseEntity<ReviewFeedbackResponse> approveFeedbackRequest(@PathVariable UUID requestId) {
        LOG.debug("REST request to approve feedback nomination {}", requestId);
        return ResponseEntity.ok(feedbackService.approve(requestId, currentEmployeeId(), canManageReviews()));
    }

    /**
     * {@code POST  /feedback-requests/:requestId/decline} : Decline a request (the contributor) or
     * turn a nomination down (the reviewer or HR).
     */
    @PostMapping("/feedback-requests/{requestId}/decline")
    @RequireAuthenticated
    public ResponseEntity<ReviewFeedbackResponse> declineFeedbackRequest(
        @PathVariable UUID requestId,
        @Valid @RequestBody(required = false) DeclineFeedbackRequest request
    ) {
        LOG.debug("REST request to decline feedback request {}", requestId);
        String reason = request != null ? request.reason() : null;
        return ResponseEntity.ok(feedbackService.decline(requestId, currentEmployeeId(), canManageReviews(), reason));
    }

    /**
     * {@code POST  /feedback-requests/:requestId/cancel} : Cancel an unanswered request (the reviewer
     * or HR), or withdraw a nomination (the reviewed employee).
     */
    @PostMapping("/feedback-requests/{requestId}/cancel")
    @RequireAuthenticated
    public ResponseEntity<ReviewFeedbackResponse> cancelFeedbackRequest(@PathVariable UUID requestId) {
        LOG.debug("REST request to cancel feedback request {}", requestId);
        return ResponseEntity.ok(feedbackService.cancel(requestId, currentEmployeeId(), canManageReviews()));
    }

    // ==================== PROGRESS & REMINDERS ====================

    /**
     * {@code GET  /:cycleId/progress} : Get cycle progress.
     */
    @GetMapping("/{cycleId}/progress")
    @RequirePermission(PermissionsConstants.VIEW_PERFORMANCE_REVIEWS)
    public ResponseEntity<ReviewCycleResponse.CycleProgress> getCycleProgress(@PathVariable UUID cycleId) {
        LOG.debug("REST request to get cycle progress: {}", cycleId);
        ReviewCycleResponse.CycleProgress result = reviewCycleService.getCycleProgress(cycleId);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code POST  /:cycleId/reminders} : Remind the people who still have something to do in the current phase.
     */
    @PostMapping("/{cycleId}/reminders")
    @RequirePermission(PermissionsConstants.CONFIGURE_PERFORMANCE_CYCLES)
    public ResponseEntity<Void> sendPhaseReminders(@PathVariable UUID cycleId) {
        LOG.debug("REST request to send phase reminders for cycle: {}", cycleId);
        reviewCycleService.sendPhaseReminders(cycleId);
        return ResponseEntity.ok().build();
    }

    // ==================== CYCLE MEMBERSHIP ====================

    /**
     * {@code POST  /:cycleId/reviews/:reviewId/exclusion} : Take a review out of its running cycle, with a reason.
     */
    @PostMapping("/{cycleId}/reviews/{reviewId}/exclusion")
    @RequirePermission(PermissionsConstants.CONFIGURE_PERFORMANCE_CYCLES)
    public ResponseEntity<PerformanceReviewResponse> excludeReview(
        @PathVariable UUID cycleId,
        @PathVariable UUID reviewId,
        @Valid @RequestBody ExcludeReviewRequest request
    ) {
        LOG.debug("REST request to exclude review {} from cycle {}", reviewId, cycleId);
        return ResponseEntity.ok(reviewCycleService.excludeReview(cycleId, reviewId, currentEmployeeId(), request.reason()));
    }

    /**
     * {@code DELETE  /:cycleId/reviews/:reviewId/exclusion} : Put an excluded review back in its cycle.
     */
    @DeleteMapping("/{cycleId}/reviews/{reviewId}/exclusion")
    @RequirePermission(PermissionsConstants.CONFIGURE_PERFORMANCE_CYCLES)
    public ResponseEntity<PerformanceReviewResponse> restoreReview(@PathVariable UUID cycleId, @PathVariable UUID reviewId) {
        LOG.debug("REST request to put review {} back in cycle {}", reviewId, cycleId);
        return ResponseEntity.ok(reviewCycleService.restoreReview(cycleId, reviewId, currentEmployeeId()));
    }

    /**
     * {@code GET  /:cycleId/addable-employees?query=} : People who can be added to the running cycle, by name.
     */
    @GetMapping("/{cycleId}/addable-employees")
    @RequirePermission(PermissionsConstants.CONFIGURE_PERFORMANCE_CYCLES)
    public ResponseEntity<List<CycleCandidate>> getAddableEmployees(
        @PathVariable UUID cycleId,
        @RequestParam(required = false) String query
    ) {
        return ResponseEntity.ok(reviewCycleService.getAddableEmployees(cycleId, query));
    }

    /**
     * {@code POST  /:cycleId/reviews} : Add people to the running cycle.
     */
    @PostMapping("/{cycleId}/reviews")
    @RequirePermission(PermissionsConstants.CONFIGURE_PERFORMANCE_CYCLES)
    public ResponseEntity<List<PerformanceReviewResponse>> addEmployees(
        @PathVariable UUID cycleId,
        @Valid @RequestBody AddCycleEmployeesRequest request
    ) {
        LOG.debug("REST request to add {} employee(s) to cycle {}", request.employeeIds().size(), cycleId);
        return ResponseEntity.ok(reviewCycleService.addEmployees(cycleId, currentEmployeeId(), request.employeeIds()));
    }

    /**
     * {@code POST  /:cycleId/phases/back} : Take the cycle back one phase.
     */
    @PostMapping("/{cycleId}/phases/back")
    @RequirePermission(PermissionsConstants.CONFIGURE_PERFORMANCE_CYCLES)
    public ResponseEntity<ReviewCycleResponse> moveBackPhase(@PathVariable UUID cycleId, @Valid @RequestBody MoveBackPhaseRequest request) {
        LOG.debug("REST request to move cycle {} back a phase", cycleId);
        return ResponseEntity.ok(reviewCycleService.moveBackPhase(cycleId, request));
    }

    private boolean canManageReviews() {
        return securityExpressions.hasPermission(PermissionsConstants.MANAGE_PERFORMANCE_REVIEWS);
    }

    /** The signed-in user's id, which is also their employee id. */
    private static UUID currentEmployeeId() {
        return SecurityUtils.getCurrentUserId().orElseThrow(() -> new AccessDeniedException("Not signed in as an employee"));
    }
}
