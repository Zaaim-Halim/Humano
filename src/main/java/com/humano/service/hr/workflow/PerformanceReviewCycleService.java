package com.humano.service.hr.workflow;

import com.humano.domain.enumeration.hr.ApprovalType;
import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.domain.enumeration.hr.FeedbackVisibility;
import com.humano.domain.enumeration.hr.PerformanceReviewStatus;
import com.humano.domain.enumeration.hr.RatingChangeSource;
import com.humano.domain.enumeration.hr.ReviewCyclePhase;
import com.humano.domain.enumeration.hr.ReviewCycleType;
import com.humano.domain.enumeration.hr.SignOffStatus;
import com.humano.domain.enumeration.hr.WorkflowStatus;
import com.humano.domain.enumeration.hr.WorkflowType;
import com.humano.domain.hr.PerformanceReview;
import com.humano.domain.hr.ReviewCycle;
import com.humano.domain.hr.ReviewRatingChange;
import com.humano.domain.hr.WorkflowDeadline;
import com.humano.domain.hr.WorkflowInstance;
import com.humano.domain.hr.WorkflowStateTransition;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.responses.PerformanceReviewResponse;
import com.humano.dto.hr.workflow.requests.AcknowledgeReviewRequest;
import com.humano.dto.hr.workflow.requests.CalibrateReviewRequest;
import com.humano.dto.hr.workflow.requests.InitiateReviewCycleRequest;
import com.humano.dto.hr.workflow.requests.ManagerReviewRequest;
import com.humano.dto.hr.workflow.requests.MoveBackPhaseRequest;
import com.humano.dto.hr.workflow.requests.ResolveDisputeRequest;
import com.humano.dto.hr.workflow.requests.SelfAssessmentRequest;
import com.humano.dto.hr.workflow.requests.SubmitApprovalRequest;
import com.humano.dto.hr.workflow.responses.CycleCandidate;
import com.humano.dto.hr.workflow.responses.RatingChangeResponse;
import com.humano.dto.hr.workflow.responses.ReviewCycleResponse;
import com.humano.dto.hr.workflow.responses.ReviewSignOffResponse;
import com.humano.repository.hr.DepartmentRepository;
import com.humano.repository.hr.PerformanceReviewRepository;
import com.humano.repository.hr.ReviewRatingChangeRepository;
import com.humano.repository.hr.projection.ReviewProgressRow;
import com.humano.repository.hr.workflow.ApprovalRequestRepository;
import com.humano.repository.hr.workflow.ReviewCycleRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.security.SecurityUtils;
import com.humano.service.audit.AuditEventService;
import com.humano.service.errors.BadRequestAlertException;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.hr.PerformanceReviewService;
import com.humano.service.hr.workflow.infrastructure.DeadlineMonitorService;
import com.humano.service.hr.workflow.infrastructure.NotificationOrchestrationService;
import com.humano.service.hr.workflow.infrastructure.WorkflowStateManager;
import com.humano.service.payroll.OrganizationSettingsService;
import jakarta.persistence.criteria.JoinType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing performance review cycle workflows.
 *
 * <h2>State machine</h2>
 *
 * <p>Two state spaces in lockstep: the typed {@code ReviewCyclePhase} on
 * {@code ReviewCycle.phase} (load-bearing — every method's guard reads it)
 * AND the string {@code currentState} on the cycle's {@code WorkflowInstance}
 * (mirrors the phase one-for-one). Each {@code start*Phase} method validates
 * its predecessor phase, advances both, closes the previous phase's deadline
 * and registers its own.
 *
 * <pre>
 *  DRAFT
 *    └─ startSelfAssessmentPhase        → SELF_ASSESSMENT   (creates one review per eligible employee)
 *         └─ startManagerReviewPhase    → MANAGER_REVIEW
 *              ├─ startCalibrationPhase → CALIBRATION        (optional)
 *              └─ startFeedbackDeliveryPhase → FEEDBACK_DELIVERY
 *                   ├─ startGoalSettingPhase → GOAL_SETTING  (optional)
 *                   └─ closeCycle          → COMPLETED       (shares every finished review)
 *                        └─ archiveCycle   → ARCHIVED
 * </pre>
 *
 * <h2>Reviews</h2>
 *
 * Each cycle owns its {@link PerformanceReview}s, one per employee:
 * {@code NOT_STARTED → SELF_SUBMITTED → MANAGER_SUBMITTED → SHARED}. The employee writes the
 * self-assessment (narrative, no rating); the assigned reviewer — the employee's manager when the
 * phase started, or whoever HR assigns — writes the assessment and rating; HR may calibrate the
 * rating with a note; the feedback meeting shares the review with the employee. Until then the
 * employee never sees the reviewer's side.
 *
 * <p>Once shared, the employee acknowledges the review — read, not necessarily agreed — with an
 * optional comment, or disputes it with a reason; HR resolves disputes, possibly changing the
 * rating. Acknowledgement locks the meeting notes. Every change to a final rating after the
 * reviewer gave it (calibration, dispute resolution) is logged ({@link ReviewRatingChange}).
 *
 * <p>A cycle may require a second-level sign-off of each written review ({@link SignOffStatus}):
 * submitting the review sends it to the reviewer's manager — or the PERFORMANCE_REVIEW approval
 * chain — through the approval inbox. A review waiting on its sign-off, or returned, can't be
 * calibrated or shared; a returned review can be revised until feedback delivery ends, and every
 * revision is signed off again. With nobody above the reviewer, the sign-off is waived.
 *
 * <h2>Types</h2>
 *
 * A REGULAR cycle reviews its departments (or the whole company); two regular cycles may not run at
 * the same time for the same people. A PROBATION cycle reviews employees not yet confirmed whose
 * probation ends within the review period, and the reviewer recommends confirming, extending or
 * not confirming them — a recommendation only, never applied to the employee. An AD_HOC cycle
 * reviews named employees. Probation and ad hoc cycles may run alongside any other.
 *
 * <p>The reviewer can also gather feedback from other colleagues while the review is being written
 * ({@link ReviewFeedbackService}); writing the review closes the requests nobody answered.
 *
 * <h2>Progress</h2>
 *
 * The cycle's counters are recounted from its reviews' statuses whenever a review changes, so
 * resubmissions never double-count.
 */
@Service
@Transactional("tenantTransactionManager")
public class PerformanceReviewCycleService {

    private static final Logger log = LoggerFactory.getLogger(PerformanceReviewCycleService.class);

    // Review cycle states
    public static final String STATE_DRAFT = "DRAFT";
    public static final String STATE_SELF_ASSESSMENT = "SELF_ASSESSMENT";
    public static final String STATE_MANAGER_REVIEW = "MANAGER_REVIEW";
    public static final String STATE_CALIBRATION = "CALIBRATION";
    public static final String STATE_FEEDBACK_DELIVERY = "FEEDBACK_DELIVERY";
    public static final String STATE_GOAL_SETTING = "GOAL_SETTING";
    public static final String STATE_COMPLETED = "COMPLETED";

    private static final String ENTITY_NAME = "reviewCycle";
    private static final int DEFAULT_MAX_CONTRIBUTORS = 8;
    /** Phases of a running cycle: reviews exist and the cycle hasn't closed. */
    private static final ReviewCyclePhase[] RUNNING = {
        ReviewCyclePhase.SELF_ASSESSMENT,
        ReviewCyclePhase.MANAGER_REVIEW,
        ReviewCyclePhase.CALIBRATION,
        ReviewCyclePhase.FEEDBACK_DELIVERY,
        ReviewCyclePhase.GOAL_SETTING,
    };
    private static final int MAX_CANDIDATES = 20;
    private static final Set<PerformanceReviewStatus> MANAGER_DONE = EnumSet.of(
        PerformanceReviewStatus.MANAGER_SUBMITTED,
        PerformanceReviewStatus.SHARED
    );

    private final ReviewCycleRepository reviewCycleRepository;
    private final PerformanceReviewRepository performanceReviewRepository;
    private final PerformanceReviewService performanceReviewService;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final WorkflowStateManager workflowStateManager;
    private final NotificationOrchestrationService notificationService;
    private final DeadlineMonitorService deadlineMonitorService;
    private final OrganizationSettingsService organizationSettingsService;
    private final ReviewFeedbackService feedbackService;
    private final ReviewRatingChangeRepository ratingChangeRepository;
    private final ApprovalWorkflowOrchestratorService approvalService;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ReviewFormService formService;
    private final PerformanceFrameworkService frameworkService;
    private final AuditEventService auditEventService;

    public PerformanceReviewCycleService(
        ReviewCycleRepository reviewCycleRepository,
        PerformanceReviewRepository performanceReviewRepository,
        PerformanceReviewService performanceReviewService,
        EmployeeRepository employeeRepository,
        DepartmentRepository departmentRepository,
        WorkflowStateManager workflowStateManager,
        NotificationOrchestrationService notificationService,
        DeadlineMonitorService deadlineMonitorService,
        OrganizationSettingsService organizationSettingsService,
        ReviewFeedbackService feedbackService,
        ReviewRatingChangeRepository ratingChangeRepository,
        ApprovalWorkflowOrchestratorService approvalService,
        ApprovalRequestRepository approvalRequestRepository,
        ReviewFormService formService,
        PerformanceFrameworkService frameworkService,
        AuditEventService auditEventService
    ) {
        this.reviewCycleRepository = reviewCycleRepository;
        this.performanceReviewRepository = performanceReviewRepository;
        this.performanceReviewService = performanceReviewService;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.workflowStateManager = workflowStateManager;
        this.notificationService = notificationService;
        this.deadlineMonitorService = deadlineMonitorService;
        this.organizationSettingsService = organizationSettingsService;
        this.feedbackService = feedbackService;
        this.ratingChangeRepository = ratingChangeRepository;
        this.approvalService = approvalService;
        this.approvalRequestRepository = approvalRequestRepository;
        this.formService = formService;
        this.frameworkService = frameworkService;
        this.auditEventService = auditEventService;
    }

    /**
     * Initiate a new review cycle.
     */
    public ReviewCycleResponse initiateCycle(InitiateReviewCycleRequest request) {
        log.info("Initiating review cycle: {}", request.name());

        validateCycleRequest(request, null);

        ReviewCycle cycle = new ReviewCycle();
        applyRequest(cycle, request);
        cycle.setPhase(ReviewCyclePhase.DRAFT);
        cycle.setActive(true);
        cycle.setCompletedSelfAssessments(0);
        cycle.setCompletedManagerReviews(0);
        cycle.setDeliveredFeedbacks(0);

        // Save the cycle first to get an ID
        ReviewCycle savedCycle = reviewCycleRepository.save(cycle);

        // Create associated workflow
        Map<String, Object> workflowContext = buildWorkflowContext(savedCycle);
        WorkflowInstance workflow = workflowStateManager.createWorkflow(
            WorkflowType.PERFORMANCE_REVIEW_CYCLE,
            savedCycle.getId(),
            "ReviewCycle",
            workflowContext,
            null
        );

        // Link workflow to cycle
        savedCycle.setWorkflowInstance(workflow);
        savedCycle = reviewCycleRepository.save(savedCycle);

        log.info(
            "Review cycle {} initiated with {} employees from {} departments",
            savedCycle.getId(),
            savedCycle.getTotalEmployees(),
            savedCycle.getDepartmentIds().isEmpty() ? "all" : savedCycle.getDepartmentIds().size()
        );

        return mapToResponse(savedCycle, true);
    }

    /**
     * Update a cycle that hasn't started yet (still in DRAFT).
     */
    public ReviewCycleResponse updateCycle(UUID cycleId, InitiateReviewCycleRequest request) {
        log.info("Updating review cycle: {}", cycleId);

        ReviewCycle cycle = findCycle(cycleId);
        requirePhase(cycle, "Only a draft cycle can be edited", ReviewCyclePhase.DRAFT);
        validateCycleRequest(request, cycleId);

        applyRequest(cycle, request);
        return mapToResponse(reviewCycleRepository.save(cycle), true);
    }

    /**
     * Get review cycle status.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public ReviewCycleResponse getCycleStatus(UUID cycleId) {
        log.info("Getting review cycle status: {}", cycleId);
        return mapToResponse(findCycle(cycleId), true);
    }

    /**
     * Get active review cycles.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<ReviewCycleResponse> getActiveCycles() {
        log.info("Getting active review cycles");

        return reviewCycleRepository.findByActiveTrue().stream().map(c -> mapToResponse(c, false)).collect(Collectors.toList());
    }

    /**
     * Get cycles by phase.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<ReviewCycleResponse> getCyclesByPhase(ReviewCyclePhase phase) {
        log.info("Getting review cycles by phase: {}", phase);

        return reviewCycleRepository
            .findByPhaseAndActiveTrue(phase)
            .stream()
            .map(c -> mapToResponse(c, false))
            .collect(Collectors.toList());
    }

    /**
     * Start self-assessment phase: creates one review per eligible employee, with their manager as
     * reviewer, and asks each employee for their self-assessment.
     */
    public ReviewCycleResponse startSelfAssessmentPhase(UUID cycleId) {
        log.info("Starting self-assessment phase for cycle: {}", cycleId);

        ReviewCycle cycle = findCycle(cycleId);
        requirePhase(cycle, "Can only start self-assessment from DRAFT phase", ReviewCyclePhase.DRAFT);
        requireDeadline(cycle.getSelfAssessmentDeadline(), "Self-assessment deadline must be set before starting the phase");

        List<PerformanceReview> created = createReviews(cycle);
        if (created.isEmpty()) {
            throw new BadRequestAlertException("No eligible employees are in this cycle's scope", ENTITY_NAME, "noemployees");
        }

        formService.snapshot(cycle);
        cycle.setPhase(ReviewCyclePhase.SELF_ASSESSMENT);
        refreshProgress(cycle);
        ReviewCycle savedCycle = reviewCycleRepository.save(cycle);

        // Start and transition workflow
        WorkflowInstance workflow = cycle.getWorkflowInstance();
        if (workflow != null) {
            if (workflow.getStatus() == WorkflowStatus.DRAFT) {
                workflowStateManager.startWorkflow(workflow.getId());
            }
            workflowStateManager.transitionState(workflow.getId(), STATE_SELF_ASSESSMENT, "Self-assessment phase started");
        }
        registerPhaseDeadline(cycle, "SELF_ASSESSMENT", "Self-assessment deadline for ", cycle.getSelfAssessmentDeadline(), 72);

        for (PerformanceReview review : created) {
            notificationService.notifyTaskAssignment(
                review.getEmployee().getId(),
                "Self-Assessment Required",
                "The " +
                cycle.getName() +
                " performance review cycle has started. Please complete your self-assessment by " +
                cycle.getSelfAssessmentDeadline() +
                ".",
                cycle.getId(),
                "ReviewCycle"
            );
        }

        log.info("Self-assessment phase started for cycle {} with {} reviews", cycleId, created.size());
        return mapToResponse(savedCycle, true);
    }

    /**
     * Start manager review phase.
     */
    public ReviewCycleResponse startManagerReviewPhase(UUID cycleId) {
        log.info("Starting manager review phase for cycle: {}", cycleId);

        ReviewCycle cycle = findCycle(cycleId);
        requirePhase(cycle, "Can only start manager review from SELF_ASSESSMENT phase", ReviewCyclePhase.SELF_ASSESSMENT);
        requireDeadline(cycle.getManagerReviewDeadline(), "Manager review deadline must be set before starting the phase");

        cycle.setPhase(ReviewCyclePhase.MANAGER_REVIEW);
        ReviewCycle savedCycle = reviewCycleRepository.save(cycle);

        transitionWorkflow(cycle, STATE_MANAGER_REVIEW, "Manager review phase started");
        registerPhaseDeadline(cycle, "MANAGER_REVIEW", "Manager review deadline for ", cycle.getManagerReviewDeadline(), 72);

        // Notify each reviewer once, with the number of reviews they have to write
        Map<Employee, Long> perReviewer = pendingByReviewer(
            performanceReviewRepository.findByCycleId(cycleId),
            r -> r.getManagerSubmittedAt() == null && r.getStatus() != PerformanceReviewStatus.EXCLUDED
        );
        perReviewer.forEach((reviewer, count) ->
            notificationService.notifyTaskAssignment(
                reviewer.getId(),
                "Manager Reviews Required",
                "The manager review phase has started for " +
                cycle.getName() +
                ". You have " +
                count +
                " employee(s) to review by " +
                cycle.getManagerReviewDeadline() +
                ".",
                cycle.getId(),
                "ReviewCycle"
            )
        );

        log.info("Manager review phase started for cycle {}; {} reviewers notified", cycleId, perReviewer.size());
        return mapToResponse(savedCycle, true);
    }

    /**
     * Start calibration phase (optional: feedback delivery can also start straight after manager review).
     */
    public ReviewCycleResponse startCalibrationPhase(UUID cycleId) {
        log.info("Starting calibration phase for cycle: {}", cycleId);

        ReviewCycle cycle = findCycle(cycleId);
        requirePhase(cycle, "Can only start calibration from MANAGER_REVIEW phase", ReviewCyclePhase.MANAGER_REVIEW);
        requireDeadline(cycle.getCalibrationDeadline(), "Calibration deadline must be set before starting the phase");

        cycle.setPhase(ReviewCyclePhase.CALIBRATION);
        ReviewCycle savedCycle = reviewCycleRepository.save(cycle);

        transitionWorkflow(cycle, STATE_CALIBRATION, "Calibration phase started");
        registerPhaseDeadline(cycle, "CALIBRATION", "Calibration deadline for ", cycle.getCalibrationDeadline(), 48);

        log.info("Calibration phase started for cycle {}", cycleId);
        return mapToResponse(savedCycle, true);
    }

    /**
     * Start feedback delivery phase, from CALIBRATION or directly from MANAGER_REVIEW.
     */
    public ReviewCycleResponse startFeedbackDeliveryPhase(UUID cycleId) {
        log.info("Starting feedback delivery phase for cycle: {}", cycleId);

        ReviewCycle cycle = findCycle(cycleId);
        requirePhase(
            cycle,
            "Can only start feedback delivery from MANAGER_REVIEW or CALIBRATION phase",
            ReviewCyclePhase.MANAGER_REVIEW,
            ReviewCyclePhase.CALIBRATION
        );
        requireDeadline(cycle.getFeedbackDeadline(), "Feedback deadline must be set before starting the phase");

        cycle.setPhase(ReviewCyclePhase.FEEDBACK_DELIVERY);
        ReviewCycle savedCycle = reviewCycleRepository.save(cycle);

        transitionWorkflow(cycle, STATE_FEEDBACK_DELIVERY, "Feedback delivery phase started");
        registerPhaseDeadline(cycle, "FEEDBACK_DELIVERY", "Feedback delivery deadline for ", cycle.getFeedbackDeadline(), 48);

        // Notify reviewers who have finished reviews to discuss
        Map<Employee, Long> perReviewer = pendingByReviewer(
            performanceReviewRepository.findByCycleIdAndStatusIn(cycleId, List.of(PerformanceReviewStatus.MANAGER_SUBMITTED)),
            PerformanceReview::isSignOffSettled
        );
        perReviewer.forEach((reviewer, count) ->
            notificationService.notifyTaskAssignment(
                reviewer.getId(),
                "Feedback Meetings Required",
                "Please hold feedback meetings with your " +
                count +
                " team member(s) for " +
                cycle.getName() +
                ". Complete by " +
                cycle.getFeedbackDeadline() +
                ".",
                cycle.getId(),
                "ReviewCycle"
            )
        );

        log.info("Feedback delivery phase started for cycle {}", cycleId);
        return mapToResponse(savedCycle, true);
    }

    /**
     * Start goal setting (optional, after feedback delivery): everyone reviewed is asked to propose
     * their goals for the next period, and their reviewers to agree them. Feedback meetings not held
     * yet can still be recorded; the cycle closes as from feedback delivery.
     */
    public ReviewCycleResponse startGoalSettingPhase(UUID cycleId) {
        log.info("Starting goal setting phase for cycle: {}", cycleId);

        ReviewCycle cycle = findCycle(cycleId);
        requirePhase(cycle, "Can only start goal setting from FEEDBACK_DELIVERY phase", ReviewCyclePhase.FEEDBACK_DELIVERY);

        cycle.setPhase(ReviewCyclePhase.GOAL_SETTING);
        ReviewCycle savedCycle = reviewCycleRepository.save(cycle);

        transitionWorkflow(cycle, STATE_GOAL_SETTING, "Goal setting phase started");
        closeOpenDeadlines(cycle);

        List<PerformanceReview> included = included(performanceReviewRepository.findByCycleId(cycleId));
        for (PerformanceReview review : included) {
            notificationService.notifyTaskAssignment(
                review.getEmployee().getId(),
                "Goal Setting Phase",
                "The goal setting phase has started for " +
                cycle.getName() +
                ". Please work with your manager to set goals for the upcoming period.",
                cycle.getId(),
                "ReviewCycle"
            );
        }
        Set<UUID> reviewers = new HashSet<>();
        for (PerformanceReview review : included) {
            if (review.getReviewer() != null && reviewers.add(review.getReviewer().getId())) {
                notificationService.notifyTaskAssignment(
                    review.getReviewer().getId(),
                    "Goal Setting Phase",
                    "Goal setting has started for " + cycle.getName() + ". Agree the goals your team proposes for the upcoming period.",
                    cycle.getId(),
                    "ReviewCycle"
                );
            }
        }

        log.info("Goal setting phase started for cycle {}", cycleId);
        return mapToResponse(savedCycle, true);
    }

    /**
     * Close a review cycle. Reviews the reviewer finished but didn't discuss yet are shared with the
     * employee; reviews never finished — or still waiting on, or returned at, their sign-off — stay
     * unshared, and their open sign-offs leave the approvers' inboxes.
     */
    public ReviewCycleResponse closeCycle(UUID cycleId) {
        log.info("Closing review cycle: {}", cycleId);

        ReviewCycle cycle = findCycle(cycleId);
        requirePhase(
            cycle,
            "Can only close cycle from GOAL_SETTING or FEEDBACK_DELIVERY phase",
            ReviewCyclePhase.GOAL_SETTING,
            ReviewCyclePhase.FEEDBACK_DELIVERY
        );

        Instant now = Instant.now();
        List<PerformanceReview> released = new ArrayList<>();
        for (PerformanceReview review : performanceReviewRepository.findByCycleIdAndStatusIn(
            cycleId,
            List.of(PerformanceReviewStatus.MANAGER_SUBMITTED)
        )) {
            if (review.isSignOffSettled()) {
                released.add(review);
            } else {
                approvalService.closePendingForEntity(review.getId(), ApprovalType.PERFORMANCE_REVIEW, "closed with the review cycle");
            }
        }
        for (PerformanceReview review : released) {
            review.setStatus(PerformanceReviewStatus.SHARED);
            review.setSharedAt(now);
        }
        performanceReviewRepository.saveAll(released);
        feedbackService.closeOpenRequests(cycle);

        cycle.setPhase(ReviewCyclePhase.COMPLETED);
        cycle.setActive(false);
        refreshProgress(cycle);
        ReviewCycle savedCycle = reviewCycleRepository.save(cycle);

        closeOpenDeadlines(cycle);
        if (cycle.getWorkflowInstance() != null) {
            workflowStateManager.completeWorkflow(cycle.getWorkflowInstance().getId(), "Review cycle completed successfully");
        }

        for (PerformanceReview review : included(performanceReviewRepository.findByCycleId(cycleId))) {
            notificationService.notifyWorkflowCompleted(
                review.getEmployee().getId(),
                "Performance Review Cycle Completed",
                "The " +
                cycle.getName() +
                " performance review cycle has been completed." +
                (review.getStatus() == PerformanceReviewStatus.SHARED ? " Your review is available in your profile." : ""),
                cycle.getId(),
                "ReviewCycle"
            );
        }

        log.info("Review cycle {} closed; {} reviews released at closing", cycleId, released.size());
        return mapToResponse(savedCycle, true);
    }

    /**
     * Archive a completed review cycle.
     */
    public ReviewCycleResponse archiveCycle(UUID cycleId) {
        log.info("Archiving review cycle: {}", cycleId);

        ReviewCycle cycle = findCycle(cycleId);
        requirePhase(cycle, "Can only archive COMPLETED cycles", ReviewCyclePhase.COMPLETED);

        cycle.setPhase(ReviewCyclePhase.ARCHIVED);
        ReviewCycle savedCycle = reviewCycleRepository.save(cycle);

        log.info("Review cycle {} archived", cycleId);
        return mapToResponse(savedCycle, true);
    }

    /**
     * Submit (or revise) the self-assessment of {@code employeeId} — the signed-in employee — in a
     * cycle. Open during SELF_ASSESSMENT, and late during MANAGER_REVIEW until the reviewer submits.
     */
    public PerformanceReviewResponse submitSelfAssessment(UUID cycleId, UUID employeeId, SelfAssessmentRequest request) {
        log.info("Submitting self-assessment for cycle {} by employee {}", cycleId, employeeId);

        PerformanceReview review = findReviewOf(cycleId, employeeId);
        requireIncluded(review);
        requirePhase(
            review.getCycle(),
            "Self-assessments can only be submitted during the self-assessment or manager review phase",
            ReviewCyclePhase.SELF_ASSESSMENT,
            ReviewCyclePhase.MANAGER_REVIEW
        );
        if (MANAGER_DONE.contains(review.getStatus())) {
            throw new BadRequestAlertException("Your reviewer has already completed this review", "selfAssessment", "managersubmitted");
        }

        formService.saveSelfAnswers(review, request.answers());
        boolean firstSubmission = review.getSelfSubmittedAt() == null;
        review.setSelfAchievements(request.achievements());
        review.setSelfChallenges(request.challenges());
        review.setSelfDevelopmentGoals(request.developmentGoals());
        review.setSelfComments(request.additionalComments());
        review.setSelfSubmittedAt(Instant.now());
        review.setStatus(PerformanceReviewStatus.SELF_SUBMITTED);
        performanceReviewRepository.save(review);

        refreshProgressAndSave(review.getCycle());

        if (firstSubmission && review.getReviewer() != null) {
            notificationService.notifyTaskAssignment(
                review.getReviewer().getId(),
                "Self-Assessment Submitted",
                fullName(review.getEmployee()) + " has completed their self-assessment and is ready for your review.",
                review.getId(),
                "PerformanceReview"
            );
        }

        log.info("Self-assessment submitted for review {}", review.getId());
        return detailed(review, true);
    }

    /**
     * Submit (or revise) the reviewer's assessment of {@code request.employeeId()} during
     * MANAGER_REVIEW — or, for a review returned at sign-off, until feedback delivery ends. In a
     * cycle requiring sign-off, each submission is sent for sign-off.
     *
     * @param actorId the signed-in user; must be the assigned reviewer unless {@code hrOverride}, and
     *                never the reviewed employee
     * @param hrOverride the caller may manage every review ({@code MANAGE_PERFORMANCE_REVIEWS})
     */
    public PerformanceReviewResponse submitManagerReview(UUID cycleId, UUID actorId, boolean hrOverride, ManagerReviewRequest request) {
        log.info("Submitting manager review for cycle {} employee {}", cycleId, request.employeeId());

        PerformanceReview review = findReviewOf(cycleId, request.employeeId());
        requireIncluded(review);
        if (review.getSignOffStatus() == SignOffStatus.RETURNED) {
            requirePhase(
                review.getCycle(),
                "A returned review can be revised until feedback delivery ends",
                ReviewCyclePhase.MANAGER_REVIEW,
                ReviewCyclePhase.CALIBRATION,
                ReviewCyclePhase.FEEDBACK_DELIVERY,
                ReviewCyclePhase.GOAL_SETTING
            );
        } else {
            requirePhase(
                review.getCycle(),
                "Manager reviews can only be submitted during the manager review phase",
                ReviewCyclePhase.MANAGER_REVIEW
            );
        }
        if (review.getReviewer() == null) {
            throw new BadRequestAlertException("Assign a reviewer to this review first", "managerReview", "noreviewer");
        }
        requireNotOwnReview(review, actorId);
        requireReviewerOrHr(review, actorId, hrOverride);
        if (review.getCycle().getType() == ReviewCycleType.PROBATION && request.probationRecommendation() == null) {
            throw new BadRequestAlertException(
                "Recommend confirming, extending or not confirming the employee",
                "managerReview",
                "probationrecommendation"
            );
        }

        formService.saveReviewerInput(review, request.competencyRatings(), request.answers(), request.goalRatings());
        boolean firstSubmission = review.getManagerSubmittedAt() == null;
        review.setManagerRating(request.rating());
        review.setRating(request.rating());
        review.setStrengths(request.strengths());
        review.setAreasForImprovement(request.areasForImprovement());
        review.setDevelopmentRecommendations(request.developmentRecommendations());
        review.setComments(request.managerComments());
        review.setRecommendForPromotion(request.recommendForPromotion());
        review.setPromotionJustification(request.recommendForPromotion() ? request.promotionJustification() : null);
        review.setPotential(request.potential());
        review.setProbationRecommendation(
            review.getCycle().getType() == ReviewCycleType.PROBATION ? request.probationRecommendation() : null
        );
        review.setManagerSubmittedAt(Instant.now());
        review.setStatus(PerformanceReviewStatus.MANAGER_SUBMITTED);
        performanceReviewRepository.save(review);
        feedbackService.closeOpenRequests(review);
        if (review.getCycle().isRequiresSignOff()) {
            requestSignOff(review);
        }

        refreshProgressAndSave(review.getCycle());

        if (firstSubmission) {
            notificationService.notifyWorkflowCompleted(
                review.getEmployee().getId(),
                "Manager Review Completed",
                "Your manager has completed your performance review. Feedback will be delivered soon.",
                review.getId(),
                "PerformanceReview"
            );
        }

        log.info("Manager review submitted for review {}", review.getId());
        return detailed(review, false);
    }

    /**
     * Record the feedback meeting for {@code employeeId}: the review is shared with the employee.
     * Calling it again on a shared review only updates the notes.
     *
     * @param actorId the signed-in user; must be the assigned reviewer unless {@code hrOverride}
     * @param hrOverride the caller may manage every review ({@code MANAGE_PERFORMANCE_REVIEWS})
     */
    public PerformanceReviewResponse recordFeedbackMeeting(UUID cycleId, UUID employeeId, UUID actorId, boolean hrOverride, String notes) {
        log.info("Recording feedback meeting for cycle {} employee {}", cycleId, employeeId);

        PerformanceReview review = findReviewOf(cycleId, employeeId);
        requirePhase(
            review.getCycle(),
            "Feedback can only be recorded during feedback delivery or goal setting",
            ReviewCyclePhase.FEEDBACK_DELIVERY,
            ReviewCyclePhase.GOAL_SETTING
        );
        requireNotOwnReview(review, actorId);
        requireReviewerOrHr(review, actorId, hrOverride);
        if (!MANAGER_DONE.contains(review.getStatus())) {
            throw new BadRequestAlertException("The manager review isn't completed yet", ENTITY_NAME, "managerreviewpending");
        }
        requireSignedOff(review);
        if (review.getAcknowledgedAt() != null) {
            throw new BadRequestAlertException(
                "The employee has acknowledged the review; the meeting notes can't change any more",
                ENTITY_NAME,
                "acknowledged"
            );
        }

        boolean firstShare = review.getStatus() != PerformanceReviewStatus.SHARED;
        review.setFeedbackNotes(notes);
        if (firstShare) {
            review.setStatus(PerformanceReviewStatus.SHARED);
            review.setSharedAt(Instant.now());
        }
        performanceReviewRepository.save(review);

        refreshProgressAndSave(review.getCycle());

        if (firstShare) {
            notificationService.notifyWorkflowCompleted(
                employeeId,
                "Feedback Meeting Completed",
                "Your performance review for " + review.getCycle().getName() + " is now available in your profile.",
                review.getId(),
                "PerformanceReview"
            );
        }

        log.info("Feedback meeting recorded for employee {} in cycle {}", employeeId, cycleId);
        return detailed(review, false);
    }

    /**
     * Calibrate a review's final rating during CALIBRATION. The reviewer's own rating is kept; a
     * changed rating needs a note. Calibrating back to the reviewer's rating clears the note.
     */
    public PerformanceReviewResponse calibrateReview(UUID cycleId, UUID reviewId, UUID actorId, CalibrateReviewRequest request) {
        log.info("Calibrating review {} in cycle {}", reviewId, cycleId);

        PerformanceReview review = findReview(cycleId, reviewId);
        requireNotOwnReview(review, actorId);
        requireIncluded(review);
        requirePhase(review.getCycle(), "Ratings can only be calibrated during the calibration phase", ReviewCyclePhase.CALIBRATION);
        if (review.getManagerSubmittedAt() == null) {
            throw new BadRequestAlertException("The manager review isn't completed yet", ENTITY_NAME, "managerreviewpending");
        }
        requireSignedOff(review);

        boolean changed = !request.rating().equals(review.getManagerRating());
        if (changed && (request.note() == null || request.note().isBlank())) {
            throw new BadRequestAlertException("Explain why the rating changes", ENTITY_NAME, "calibrationnote");
        }
        if (!request.rating().equals(review.getRating())) {
            logRatingChange(review, request.rating(), RatingChangeSource.CALIBRATION, changed ? request.note().trim() : null, actorId);
        }
        review.setRating(request.rating());
        review.setCalibrationNote(changed ? request.note().trim() : null);
        if (request.potential() != null) {
            review.setPotential(request.potential());
        }
        performanceReviewRepository.save(review);

        log.info("Review {} calibrated from {} to {}", reviewId, review.getManagerRating(), request.rating());
        return detailed(review, false);
    }

    /**
     * The reviewed employee acknowledges their shared review — once, until the cycle is archived.
     * Acknowledging records that they read it, not that they agree; with {@code disputed} they
     * disagree and the comment, then required, says why. The reviewer is told either way.
     */
    public PerformanceReviewResponse acknowledgeReview(UUID reviewId, UUID employeeId, AcknowledgeReviewRequest request) {
        log.info("Acknowledging review {} by employee {}", reviewId, employeeId);

        PerformanceReview review = findCycleReview(reviewId);
        if (!review.getEmployee().getId().equals(employeeId)) {
            throw new AccessDeniedException("Only the reviewed employee acknowledges their review");
        }
        if (review.getStatus() != PerformanceReviewStatus.SHARED) {
            throw new BadRequestAlertException("The review hasn't been shared with you yet", ENTITY_NAME, "notshared");
        }
        if (review.getCycle().getPhase() == ReviewCyclePhase.ARCHIVED) {
            throw new BadRequestAlertException("This review cycle is archived", ENTITY_NAME, "archived");
        }
        if (review.getAcknowledgedAt() != null) {
            throw new BadRequestAlertException("You already acknowledged this review", ENTITY_NAME, "acknowledged");
        }
        String comment = blankToNull(request.comment());
        if (request.disputed() && comment == null) {
            throw new BadRequestAlertException("Say what you disagree with", ENTITY_NAME, "disputereason");
        }

        review.setAcknowledgedAt(Instant.now());
        review.setEmployeeComment(comment);
        review.setDisputed(request.disputed());
        performanceReviewRepository.save(review);

        if (review.getReviewer() != null) {
            if (request.disputed()) {
                notificationService.notifyTaskAssignment(
                    review.getReviewer().getId(),
                    "Performance Review Disputed",
                    fullName(review.getEmployee()) + " disputes their review for " + review.getCycle().getName() + ". HR will follow up.",
                    review.getId(),
                    "PerformanceReview"
                );
            } else {
                notificationService.notifyTaskCompleted(
                    review.getReviewer().getId(),
                    "Performance Review Acknowledged",
                    fullName(review.getEmployee()) + " acknowledged their review for " + review.getCycle().getName() + ".",
                    review.getId(),
                    "PerformanceReview"
                );
            }
        }

        log.info("Review {} acknowledged{}", reviewId, request.disputed() ? " and disputed" : "");
        return detailed(review, true);
    }

    /**
     * HR resolves a disputed review: the outcome is shared with the employee and the reviewer, and a
     * different rating replaces the final one (logged). Neither the employee nor the reviewer can
     * resolve it — a dispute is settled by someone outside it.
     */
    public PerformanceReviewResponse resolveDispute(UUID reviewId, UUID actorId, ResolveDisputeRequest request) {
        log.info("Resolving the dispute of review {}", reviewId);

        PerformanceReview review = findCycleReview(reviewId);
        requireNotOwnReview(review, actorId);
        if (review.getReviewer() != null && review.getReviewer().getId().equals(actorId)) {
            throw new AccessDeniedException("The reviewer can't resolve a dispute of their own review");
        }
        if (!review.isDisputed()) {
            throw new BadRequestAlertException("This review isn't disputed", ENTITY_NAME, "notdisputed");
        }
        if (review.getDisputeResolvedAt() != null) {
            throw new BadRequestAlertException("This dispute is already resolved", ENTITY_NAME, "disputeresolved");
        }

        String resolution = request.resolution().trim();
        Integer previous = review.getRating();
        if (request.rating() != null && !request.rating().equals(previous)) {
            logRatingChange(review, request.rating(), RatingChangeSource.DISPUTE_RESOLUTION, resolution, actorId);
            review.setRating(request.rating());
        }
        review.setDisputeResolution(resolution);
        review.setDisputeResolvedAt(Instant.now());
        review.setDisputeResolvedBy(employeeRepository.findById(actorId).orElse(null));
        performanceReviewRepository.save(review);

        String message =
            "The dispute of the " + review.getCycle().getName() + " review of " + fullName(review.getEmployee()) + " was resolved.";
        notificationService.notifyWorkflowCompleted(
            review.getEmployee().getId(),
            "Review Dispute Resolved",
            "HR resolved your dispute of your " + review.getCycle().getName() + " review. The outcome is on the review.",
            review.getId(),
            "PerformanceReview"
        );
        if (review.getReviewer() != null) {
            notificationService.notifyTaskCompleted(
                review.getReviewer().getId(),
                "Review Dispute Resolved",
                message,
                review.getId(),
                "PerformanceReview"
            );
        }

        log.info("Dispute of review {} resolved; rating {} -> {}", reviewId, previous, review.getRating());
        return detailed(review, false);
    }

    /**
     * Changes to a review's final rating after the reviewer gave it, oldest first (HR) — never for
     * one's own review, whose calibration reasons stay with HR and management.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<RatingChangeResponse> getRatingChanges(UUID reviewId, UUID actorId) {
        requireNotOwnReview(findCycleReview(reviewId), actorId);
        return ratingChangeRepository
            .findByReviewIdOrderByChangedAtAscIdAsc(reviewId)
            .stream()
            .map(c ->
                new RatingChangeResponse(
                    c.getId(),
                    c.getFromRating(),
                    c.getToRating(),
                    c.getSource(),
                    c.getReason(),
                    c.getChangedBy() != null ? c.getChangedBy().getId() : null,
                    c.getChangedBy() != null ? fullName(c.getChangedBy()) : null,
                    c.getChangedAt()
                )
            )
            .toList();
    }

    /**
     * Assign who reviews an employee — for employees without a manager, or when the manager
     * changed. Only before the review is written.
     */
    public PerformanceReviewResponse assignReviewer(UUID cycleId, UUID reviewId, UUID actorId, UUID reviewerId) {
        log.info("Assigning reviewer {} to review {} in cycle {}", reviewerId, reviewId, cycleId);

        PerformanceReview review = findReview(cycleId, reviewId);
        requireNotOwnReview(review, actorId);
        requireIncluded(review);
        requirePhase(
            review.getCycle(),
            "Reviewers can only be changed during the self-assessment or manager review phase",
            ReviewCyclePhase.SELF_ASSESSMENT,
            ReviewCyclePhase.MANAGER_REVIEW
        );
        if (MANAGER_DONE.contains(review.getStatus())) {
            throw new BadRequestAlertException("This review has already been written", ENTITY_NAME, "managersubmitted");
        }
        if (review.getEmployee().getId().equals(reviewerId)) {
            throw new BadRequestAlertException("An employee can't review themselves", ENTITY_NAME, "selfreview");
        }
        Employee reviewer = employeeRepository
            .findById(reviewerId)
            .orElseThrow(() -> EntityNotFoundException.create("Employee", reviewerId));
        if (!EmployeeStatus.WORKING.contains(reviewer.getStatus())) {
            throw new BadRequestAlertException("The reviewer must be a current employee", ENTITY_NAME, "inactivereviewer");
        }

        Employee previousReviewer = review.getReviewer();
        review.setReviewer(reviewer);
        performanceReviewRepository.save(review);
        feedbackService.onReviewerChanged(review, previousReviewer, actorId);

        notificationService.notifyTaskAssignment(
            reviewer.getId(),
            "Performance Review Assigned",
            "You are now reviewing " +
            fullName(review.getEmployee()) +
            " in " +
            review.getCycle().getName() +
            ". Manager reviews are due by " +
            review.getCycle().getManagerReviewDeadline() +
            ".",
            review.getId(),
            "PerformanceReview"
        );

        return detailed(review, false);
    }

    // ==================== Cycle membership ====================

    /**
     * Take a review out of its running cycle, with a reason (long leave, for example). Possible
     * until the review is shared. It then stops counting towards progress, reminders and analytics;
     * its open feedback requests and a pending sign-off are closed, and the reviewer is told.
     * Nothing is deleted: {@link #restoreReview} puts it back.
     */
    public PerformanceReviewResponse excludeReview(UUID cycleId, UUID reviewId, UUID actorId, String reason) {
        log.info("Excluding review {} from cycle {}", reviewId, cycleId);

        PerformanceReview review = findReview(cycleId, reviewId);
        requireNotOwnReview(review, actorId);
        requirePhase(review.getCycle(), "Reviews can only be excluded while the cycle runs", RUNNING);
        String why = blankToNull(reason);
        if (why == null) {
            throw new BadRequestAlertException("Say why the review is excluded", ENTITY_NAME, "exclusionreason");
        }
        exclude(review, why, employeeRepository.findById(actorId).orElse(null));
        refreshProgressAndSave(review.getCycle());
        auditEventService.record(
            "PERFORMANCE_REVIEW_EXCLUDED",
            "PerformanceReview",
            review.getId().toString(),
            Map.of("cycleId", cycleId.toString(), "employeeId", review.getEmployee().getId().toString(), "reason", why)
        );
        return detailed(review, false);
    }

    /**
     * Put an excluded review back in its cycle, where it was: not started, self-assessed or written.
     * Only for someone still working, and only while the review can still be finished — one not yet
     * written goes back until manager reviews end. A written review in a cycle requiring sign-off
     * that had none settled is sent for sign-off again.
     */
    public PerformanceReviewResponse restoreReview(UUID cycleId, UUID reviewId, UUID actorId) {
        log.info("Putting review {} back in cycle {}", reviewId, cycleId);

        PerformanceReview review = findReview(cycleId, reviewId);
        ReviewCycle cycle = review.getCycle();
        requireNotOwnReview(review, actorId);
        requirePhase(cycle, "Reviews can only be put back while the cycle runs", RUNNING);
        if (review.getStatus() != PerformanceReviewStatus.EXCLUDED) {
            throw new BadRequestAlertException("This review isn't excluded", ENTITY_NAME, "notexcluded");
        }
        if (!EmployeeStatus.WORKING.contains(review.getEmployee().getStatus())) {
            throw new BadRequestAlertException(fullName(review.getEmployee()) + " no longer works here", ENTITY_NAME, "inactiveemployee");
        }
        boolean written = review.getManagerSubmittedAt() != null;
        if (!written) {
            requirePhase(
                cycle,
                "A review not yet written can only be put back until manager reviews end",
                ReviewCyclePhase.SELF_ASSESSMENT,
                ReviewCyclePhase.MANAGER_REVIEW
            );
        }

        review.setStatus(
            written
                ? PerformanceReviewStatus.MANAGER_SUBMITTED
                : review.getSelfSubmittedAt() != null ? PerformanceReviewStatus.SELF_SUBMITTED : PerformanceReviewStatus.NOT_STARTED
        );
        review.setExcludedAt(null);
        review.setExclusionReason(null);
        review.setExcludedBy(null);
        if (review.getReviewer() != null && !EmployeeStatus.WORKING.contains(review.getReviewer().getStatus())) {
            review.setReviewer(null);
        }
        performanceReviewRepository.save(review);
        if (written && cycle.isRequiresSignOff() && review.getSignOffStatus() == null && review.getReviewer() != null) {
            requestSignOff(review);
        }
        refreshProgressAndSave(cycle);

        if (review.getReviewer() != null) {
            notificationService.notifyTaskAssignment(
                review.getReviewer().getId(),
                "Performance Review Back in Cycle",
                fullName(review.getEmployee()) + "'s review is back in " + cycle.getName() + ".",
                review.getId(),
                "PerformanceReview"
            );
        }
        auditEventService.record(
            "PERFORMANCE_REVIEW_RESTORED",
            "PerformanceReview",
            review.getId().toString(),
            Map.of("cycleId", cycleId.toString(), "employeeId", review.getEmployee().getId().toString())
        );
        return detailed(review, false);
    }

    /**
     * People who can be added to a running cycle, by name (at most 20): for a regular or probation
     * cycle, those its scope covers today who aren't in it yet; for an ad hoc cycle, anyone working
     * who isn't in it yet.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<CycleCandidate> getAddableEmployees(UUID cycleId, String query) {
        ReviewCycle cycle = findCycle(cycleId);
        requirePhase(
            cycle,
            "People can only be added until manager reviews end",
            ReviewCyclePhase.SELF_ASSESSMENT,
            ReviewCyclePhase.MANAGER_REVIEW
        );
        String term = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return employeeRepository
            .findAll(addableSpec(cycle, term), PageRequest.of(0, MAX_CANDIDATES, Sort.by("firstName", "lastName")))
            .stream()
            .map(e ->
                new CycleCandidate(e.getId(), fullName(e), e.getJobTitle(), e.getDepartment() != null ? e.getDepartment().getName() : null)
            )
            .toList();
    }

    /**
     * Add people to a running cycle, until manager reviews end — those who joined its scope after it
     * opened (see {@link #getAddableEmployees}). Each gets a review like everyone else's, reviewed by
     * their manager when they have a working one, and is asked for their self-assessment.
     */
    public List<PerformanceReviewResponse> addEmployees(UUID cycleId, UUID actorId, Set<UUID> employeeIds) {
        log.info("Adding {} employee(s) to cycle {}", employeeIds.size(), cycleId);

        ReviewCycle cycle = findCycle(cycleId);
        requirePhase(
            cycle,
            "People can only be added until manager reviews end",
            ReviewCyclePhase.SELF_ASSESSMENT,
            ReviewCyclePhase.MANAGER_REVIEW
        );
        if (employeeIds.contains(actorId)) {
            throw new AccessDeniedException("You can't add yourself to a review cycle");
        }
        Set<UUID> inCycle = performanceReviewRepository.findEmployeeIdsByCycleId(cycleId);
        List<UUID> already = employeeIds.stream().filter(inCycle::contains).toList();
        if (!already.isEmpty()) {
            throw new BadRequestAlertException(
                "Already in this cycle: " + names(already) + ". Put an excluded review back instead of adding the person again",
                ENTITY_NAME,
                "alreadyincycle"
            );
        }
        Specification<Employee> requested = (root, query, cb) -> root.get("id").in(employeeIds);
        List<Employee> employees = employeeRepository.findAll(addableSpec(cycle, "").and(requested));
        if (employees.size() != employeeIds.size()) {
            Set<UUID> found = employees.stream().map(Employee::getId).collect(Collectors.toSet());
            throw new BadRequestAlertException(
                "Not eligible for this cycle: " + names(employeeIds.stream().filter(id -> !found.contains(id)).toList()),
                ENTITY_NAME,
                "noteligible"
            );
        }

        List<PerformanceReview> created = performanceReviewRepository.saveAll(employees.stream().map(e -> newReview(cycle, e)).toList());
        if (cycle.getType() == ReviewCycleType.AD_HOC) {
            cycle.getEmployeeIds().addAll(employeeIds);
        }
        refreshProgressAndSave(cycle);

        for (PerformanceReview review : created) {
            notificationService.notifyTaskAssignment(
                review.getEmployee().getId(),
                "Self-Assessment Required",
                "You have been added to the " +
                cycle.getName() +
                " performance review cycle. Please complete your self-assessment by " +
                (cycle.getPhase() == ReviewCyclePhase.SELF_ASSESSMENT
                        ? cycle.getSelfAssessmentDeadline()
                        : cycle.getManagerReviewDeadline()) +
                ".",
                cycle.getId(),
                "ReviewCycle"
            );
            if (review.getReviewer() != null) {
                notificationService.notifyTaskAssignment(
                    review.getReviewer().getId(),
                    "Performance Review Assigned",
                    "You are now reviewing " +
                    fullName(review.getEmployee()) +
                    " in " +
                    cycle.getName() +
                    ". Manager reviews are due by " +
                    cycle.getManagerReviewDeadline() +
                    ".",
                    review.getId(),
                    "PerformanceReview"
                );
            }
        }
        auditEventService.record(
            "PERFORMANCE_CYCLE_EMPLOYEES_ADDED",
            "ReviewCycle",
            cycleId.toString(),
            Map.of("employeeIds", employeeIds.stream().map(UUID::toString).sorted().toList())
        );
        log.info("Added {} review(s) to cycle {}", created.size(), cycleId);
        return created.stream().map(r -> performanceReviewService.toResponse(r, false)).toList();
    }

    /**
     * Someone left: their reviews still open in running cycles are excluded; reviews they had yet
     * to write lose their reviewer, so HR assigns another (the person running the phase is told);
     * and the feedback they were asked for is cancelled. Runs after the leaving is committed, in its
     * own transaction, so a failure here never undoes it. Nothing happens if they were reinstated.
     */
    @Transactional(value = "tenantTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void onEmployeeLeft(UUID employeeId, LocalDate lastDay) {
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null || EmployeeStatus.WORKING.contains(employee.getStatus())) {
            return;
        }
        String reason = "Left the company" + (lastDay != null ? " on " + lastDay : "");
        Map<UUID, ReviewCycle> touched = new LinkedHashMap<>();

        for (PerformanceReview review : performanceReviewRepository.findByEmployeeIdAndCycleActiveTrueOrderByReviewDateDesc(employeeId)) {
            if (review.getStatus() != PerformanceReviewStatus.SHARED && review.getStatus() != PerformanceReviewStatus.EXCLUDED) {
                exclude(review, reason, null);
                touched.put(review.getCycle().getId(), review.getCycle());
            }
        }
        Map<ReviewCycle, Integer> unassigned = new LinkedHashMap<>();
        for (PerformanceReview review : performanceReviewRepository.findByReviewerIdAndCycleActiveTrueOrderByReviewDateDesc(employeeId)) {
            if (MANAGER_DONE.contains(review.getStatus()) || review.getStatus() == PerformanceReviewStatus.EXCLUDED) {
                continue;
            }
            review.setReviewer(null);
            performanceReviewRepository.save(review);
            feedbackService.onReviewerChanged(review, employee, null);
            touched.put(review.getCycle().getId(), review.getCycle());
            unassigned.merge(review.getCycle(), 1, Integer::sum);
        }
        feedbackService.cancelOpenRequestsFrom(employee);
        touched.values().forEach(this::refreshProgressAndSave);

        unassigned.forEach((cycle, count) ->
            phaseOwner(cycle).ifPresent(owner ->
                notificationService.notifyTaskAssignment(
                    owner.getId(),
                    "Reviewer Left",
                    fullName(employee) +
                    " has left. " +
                    count +
                    " review(s) in " +
                    cycle.getName() +
                    " they had yet to write need another reviewer.",
                    cycle.getId(),
                    "ReviewCycle"
                )
            )
        );
        log.info("Employee {} left: {} running cycle(s) updated", employeeId, touched.size());
    }

    /**
     * Take a cycle back one phase — for a phase started too early. Self-assessment never goes back
     * to draft (reviews exist); manager reviews reopen only while no rating was changed in
     * calibration (a reviewer revising would overwrite it); feedback delivery goes back to the phase
     * it came from only while nothing was shared; goal setting goes back to feedback delivery. The
     * reopened phase gets a deadline from today on, and those after it stay in order. People with
     * something to do in it are reminded.
     */
    public ReviewCycleResponse moveBackPhase(UUID cycleId, MoveBackPhaseRequest request) {
        ReviewCycle cycle = findCycle(cycleId);
        ReviewCyclePhase from = cycle.getPhase();
        ReviewCyclePhase to = previousPhase(cycle);
        log.info("Moving cycle {} back from {} to {}", cycleId, from, to);

        if (
            to == ReviewCyclePhase.MANAGER_REVIEW &&
            performanceReviewRepository.findByCycleId(cycleId).stream().anyMatch(PerformanceReviewCycleService::calibrated)
        ) {
            throw new BadRequestAlertException(
                "Ratings were already changed in calibration; reopening manager reviews would let reviewers overwrite them",
                ENTITY_NAME,
                "calibrated"
            );
        }
        if (
            from == ReviewCyclePhase.FEEDBACK_DELIVERY &&
            !performanceReviewRepository.findByCycleIdAndStatusIn(cycleId, List.of(PerformanceReviewStatus.SHARED)).isEmpty()
        ) {
            throw new BadRequestAlertException(
                "Reviews were already shared with employees; feedback delivery can't be undone",
                ENTITY_NAME,
                "shared"
            );
        }
        applyMovedBackDeadlines(cycle, to, request);

        cycle.setPhase(to);
        ReviewCycle saved = reviewCycleRepository.save(cycle);
        String reason = request.reason().trim();
        transitionWorkflow(cycle, to.name(), "Moved back from " + from + ": " + reason);
        switch (to) {
            case SELF_ASSESSMENT -> registerPhaseDeadline(
                cycle,
                "SELF_ASSESSMENT",
                "Self-assessment deadline for ",
                cycle.getSelfAssessmentDeadline(),
                72
            );
            case MANAGER_REVIEW -> registerPhaseDeadline(
                cycle,
                "MANAGER_REVIEW",
                "Manager review deadline for ",
                cycle.getManagerReviewDeadline(),
                72
            );
            case CALIBRATION -> registerPhaseDeadline(
                cycle,
                "CALIBRATION",
                "Calibration deadline for ",
                cycle.getCalibrationDeadline(),
                48
            );
            case FEEDBACK_DELIVERY -> registerPhaseDeadline(
                cycle,
                "FEEDBACK_DELIVERY",
                "Feedback delivery deadline for ",
                cycle.getFeedbackDeadline(),
                48
            );
            default -> {}
        }
        switch (to) {
            case SELF_ASSESSMENT -> sendSelfAssessmentReminders(cycle);
            case MANAGER_REVIEW -> sendManagerReviewReminders(cycle);
            case FEEDBACK_DELIVERY -> sendFeedbackDeliveryReminders(cycle);
            default -> {}
        }
        auditEventService.record(
            "PERFORMANCE_CYCLE_MOVED_BACK",
            "ReviewCycle",
            cycleId.toString(),
            Map.of("from", from.name(), "to", to.name(), "reason", reason)
        );
        return mapToResponse(saved, true);
    }

    /**
     * The signed-in employee's own reviews in running cycles, leaving out any taken out of their
     * cycle; the reviewer's side stays hidden until the review is shared.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<PerformanceReviewResponse> getMyCycleReviews(UUID employeeId) {
        return included(performanceReviewRepository.findByEmployeeIdAndCycleActiveTrueOrderByReviewDateDesc(employeeId))
            .stream()
            .map(r -> performanceReviewService.toResponse(r, true))
            .toList();
    }

    /**
     * One cycle review for its page. The reviewed employee gets their view (see
     * {@link PerformanceReviewService#toResponse}); the reviewer and HR ({@code hrView}) get all of it.
     * Anyone else is refused.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public PerformanceReviewResponse getReview(UUID reviewId, UUID actorId, boolean hrView) {
        PerformanceReview review = performanceReviewRepository
            .findByIdAndCycleIsNotNull(reviewId)
            .orElseThrow(() -> EntityNotFoundException.create("PerformanceReview", reviewId));
        if (review.getEmployee().getId().equals(actorId)) {
            return detailed(review, true);
        }
        requireReviewAccess(review, actorId, hrView);
        return detailed(review, false);
    }

    /**
     * A shared cycle review exactly as its employee sees it, for its PDF — whoever downloads it (the
     * employee, the reviewer, a sign-off approver or HR with {@code hrView}), so the document never
     * holds what stays with HR and management. Refused before the review is shared.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public PerformanceReviewResponse getSharedReviewAsEmployeeSees(UUID reviewId, UUID actorId, boolean hrView) {
        PerformanceReview review = performanceReviewRepository
            .findByIdAndCycleIsNotNull(reviewId)
            .orElseThrow(() -> EntityNotFoundException.create("PerformanceReview", reviewId));
        if (!review.getEmployee().getId().equals(actorId)) {
            requireReviewAccess(review, actorId, hrView);
        }
        if (review.getStatus() != PerformanceReviewStatus.SHARED) {
            throw new BadRequestAlertException("A review can be downloaded once it is shared", ENTITY_NAME, "notshared");
        }
        return detailed(review, true);
    }

    /**
     * A review's sign-off for its page — the reviewer, its approvers and HR; never the reviewed
     * employee. Empty ({@code status} {@code null}) when the review has none.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public ReviewSignOffResponse getSignOff(UUID reviewId, UUID actorId, boolean hrView) {
        PerformanceReview review = findCycleReview(reviewId);
        if (review.getEmployee().getId().equals(actorId)) {
            throw new AccessDeniedException("The sign-off of your review is between your reviewer and their manager");
        }
        requireReviewAccess(review, actorId, hrView);
        SignOffStatus status = review.getSignOffStatus();
        if (status == null || status == SignOffStatus.WAIVED) {
            return new ReviewSignOffResponse(status, null, null, null, 0, 0, null, null, false);
        }
        return approvalRequestRepository
            .findFirstByEntityIdAndApprovalTypeOrderBySubmittedAtDesc(reviewId, ApprovalType.PERFORMANCE_REVIEW)
            .map(request -> approvalService.getApprovalStatus(request.getId()))
            .map(approval -> {
                boolean waiting = status == SignOffStatus.PENDING && approval.status() == WorkflowStatus.PENDING_APPROVAL;
                return new ReviewSignOffResponse(
                    status,
                    approval.approvalRequestId(),
                    approval.currentApproverId(),
                    approval.currentApproverName(),
                    approval.currentLevel() != null ? approval.currentLevel() : 1,
                    approval.totalLevels() != null ? approval.totalLevels() : 1,
                    approval.submittedAt(),
                    approval.dueDate(),
                    waiting && actorId != null && actorId.equals(approval.currentApproverId())
                );
            })
            .orElseGet(() -> new ReviewSignOffResponse(status, null, null, null, 0, 0, null, null, false));
    }

    /**
     * Reviews the signed-in employee has to write, in running cycles, leaving out any taken out of
     * their cycle.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<PerformanceReviewResponse> getReviewsToWrite(UUID reviewerId) {
        return included(performanceReviewRepository.findByReviewerIdAndCycleActiveTrueOrderByReviewDateDesc(reviewerId))
            .stream()
            .map(r -> performanceReviewService.toResponse(r, false))
            .toList();
    }

    /**
     * Get cycle progress.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public ReviewCycleResponse.CycleProgress getCycleProgress(UUID cycleId) {
        log.info("Getting cycle progress for: {}", cycleId);
        return progressOf(findCycle(cycleId), true);
    }

    /**
     * Send phase reminders to the people who still have something to do in the current phase.
     */
    public void sendPhaseReminders(UUID cycleId) {
        log.info("Sending phase reminders for cycle: {}", cycleId);

        ReviewCycle cycle = findCycle(cycleId);

        switch (cycle.getPhase()) {
            case SELF_ASSESSMENT -> {
                sendSelfAssessmentReminders(cycle);
                feedbackService.sendContributorReminders(cycle);
            }
            case MANAGER_REVIEW -> {
                sendManagerReviewReminders(cycle);
                feedbackService.sendContributorReminders(cycle);
            }
            case FEEDBACK_DELIVERY, GOAL_SETTING -> sendFeedbackDeliveryReminders(cycle);
            default -> throw new BadRequestAlertException(
                "There are no reminders for the " + cycle.getPhase() + " phase",
                ENTITY_NAME,
                "noreminders"
            );
        }

        log.info("Phase reminders sent for cycle {}", cycleId);
    }

    /**
     * Get review cycle by ID.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public ReviewCycleResponse getReviewCycle(UUID cycleId) {
        return mapToResponse(findCycle(cycleId), true);
    }

    /**
     * Get all review cycles.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public Page<ReviewCycleResponse> getAllReviewCycles(Pageable pageable) {
        return reviewCycleRepository.findAll(pageable).map(c -> mapToResponse(c, false));
    }

    /**
     * Get current active cycle for a date.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public Optional<ReviewCycleResponse> getCurrentCycle(LocalDate date) {
        List<ReviewCycle> running = reviewCycleRepository.findActiveOn(date);
        return running
            .stream()
            .filter(c -> c.getType() == ReviewCycleType.REGULAR)
            .findFirst()
            .or(() -> running.stream().findFirst())
            .map(c -> mapToResponse(c, false));
    }

    /**
     * Get cycles by department.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<ReviewCycleResponse> getCyclesByDepartment(UUID departmentId) {
        return reviewCycleRepository
            .findByDepartmentId(departmentId)
            .stream()
            .map(c -> mapToResponse(c, false))
            .collect(Collectors.toList());
    }

    /**
     * Get cycles with approaching deadlines.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<ReviewCycleResponse> getCyclesWithApproachingDeadlines(int daysAhead) {
        LocalDate deadline = LocalDate.now().plusDays(daysAhead);
        return reviewCycleRepository
            .findCyclesWithApproachingDeadlines(deadline)
            .stream()
            .map(c -> mapToResponse(c, false))
            .collect(Collectors.toList());
    }

    // ==================== Private Helper Methods ====================

    /** A single review, with the template part of the form. */
    private PerformanceReviewResponse detailed(PerformanceReview review, boolean forReviewedEmployee) {
        return performanceReviewService.toResponse(review, forReviewedEmployee).withForm(formService.formOf(review, forReviewedEmployee));
    }

    private ReviewCycle findCycle(UUID cycleId) {
        return reviewCycleRepository.findById(cycleId).orElseThrow(() -> EntityNotFoundException.create("ReviewCycle", cycleId));
    }

    private PerformanceReview findCycleReview(UUID reviewId) {
        return performanceReviewRepository
            .findByIdAndCycleIsNotNull(reviewId)
            .orElseThrow(() -> EntityNotFoundException.create("PerformanceReview", reviewId));
    }

    private void logRatingChange(PerformanceReview review, Integer toRating, RatingChangeSource source, String reason, UUID actorId) {
        ReviewRatingChange change = new ReviewRatingChange();
        change.setReview(review);
        change.setFromRating(review.getRating());
        change.setToRating(toRating);
        change.setSource(source);
        change.setReason(reason);
        change.setChangedBy(actorId != null ? employeeRepository.findById(actorId).orElse(null) : null);
        change.setChangedAt(Instant.now());
        ratingChangeRepository.save(change);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Sends the written review for sign-off, replacing a sign-off still open from an earlier
     * submission. The approver is the reviewer's manager, or the PERFORMANCE_REVIEW chain's first
     * step; with nobody fitting — or only the reviewer or the reviewed employee — it is waived.
     */
    private void requestSignOff(PerformanceReview review) {
        approvalService.closePendingForEntity(review.getId(), ApprovalType.PERFORMANCE_REVIEW, "revised by the reviewer");
        Employee reviewer = review.getReviewer();
        UUID employeeId = review.getEmployee().getId();
        boolean approverFits = approvalService
            .firstApprover(ApprovalType.PERFORMANCE_REVIEW, reviewer)
            .filter(a -> !a.getId().equals(reviewer.getId()) && !a.getId().equals(employeeId))
            .isPresent();

        review.setSignOffComment(null);
        review.setSignOffDecidedAt(null);
        review.setSignOffDecidedBy(null);
        if (!approverFits) {
            review.setSignOffStatus(SignOffStatus.WAIVED);
            performanceReviewRepository.save(review);
            log.info("Sign-off of review {} waived: nobody above reviewer {}", review.getId(), reviewer.getId());
            return;
        }
        review.setSignOffStatus(SignOffStatus.PENDING);
        performanceReviewRepository.save(review);
        approvalService.submitForApproval(
            new SubmitApprovalRequest(
                ApprovalType.PERFORMANCE_REVIEW,
                review.getId(),
                "PerformanceReview",
                reviewer.getId(),
                null,
                null,
                3,
                fullName(reviewer) +
                "'s review of " +
                fullName(review.getEmployee()) +
                " for " +
                review.getCycle().getName() +
                " is waiting for your sign-off."
            )
        );
    }

    private static void requireSignedOff(PerformanceReview review) {
        if (review.getSignOffStatus() == SignOffStatus.PENDING) {
            throw new BadRequestAlertException("This review is waiting for its sign-off", ENTITY_NAME, "signoffpending");
        }
        if (review.getSignOffStatus() == SignOffStatus.RETURNED) {
            throw new BadRequestAlertException("This review was returned to the reviewer at sign-off", ENTITY_NAME, "signoffreturned");
        }
    }

    /** The reviewer, an approver of the review's sign-off, or HR ({@code hrView}). */
    private void requireReviewAccess(PerformanceReview review, UUID actorId, boolean hrView) {
        boolean isReviewer = review.getReviewer() != null && review.getReviewer().getId().equals(actorId);
        if (
            !isReviewer &&
            !hrView &&
            (actorId == null ||
                !approvalRequestRepository.existsByEntityIdAndApprovalTypeAndApproverId(
                    review.getId(),
                    ApprovalType.PERFORMANCE_REVIEW,
                    actorId
                ))
        ) {
            throw new AccessDeniedException("You can't see this review");
        }
    }

    private PerformanceReview findReviewOf(UUID cycleId, UUID employeeId) {
        return performanceReviewRepository
            .findByCycleIdAndEmployeeId(cycleId, employeeId)
            .orElseThrow(() -> new EntityNotFoundException("No review for this employee in this cycle"));
    }

    private PerformanceReview findReview(UUID cycleId, UUID reviewId) {
        return performanceReviewRepository
            .findByIdAndCycleId(reviewId, cycleId)
            .orElseThrow(() -> EntityNotFoundException.create("PerformanceReview", reviewId));
    }

    private static void requirePhase(ReviewCycle cycle, String message, ReviewCyclePhase... allowed) {
        if (!Arrays.asList(allowed).contains(cycle.getPhase())) {
            throw new BadRequestAlertException(message + ". Current phase: " + cycle.getPhase(), ENTITY_NAME, "invalidphase");
        }
    }

    private static void requireDeadline(LocalDate deadline, String message) {
        if (deadline == null) {
            throw new BadRequestAlertException(message, ENTITY_NAME, "nodeadline");
        }
    }

    private static List<PerformanceReview> included(List<PerformanceReview> reviews) {
        return reviews.stream().filter(r -> r.getStatus() != PerformanceReviewStatus.EXCLUDED).toList();
    }

    private static void requireIncluded(PerformanceReview review) {
        if (review.getStatus() == PerformanceReviewStatus.EXCLUDED) {
            throw new BadRequestAlertException("This review was taken out of the cycle", ENTITY_NAME, "excluded");
        }
    }

    /** Nobody writes, shares, calibrates or reassigns their own review — HR rights included. */
    private static void requireNotOwnReview(PerformanceReview review, UUID actorId) {
        if (review.getEmployee().getId().equals(actorId)) {
            throw new AccessDeniedException("You can't act on your own review");
        }
    }

    private static void requireReviewerOrHr(PerformanceReview review, UUID actorId, boolean hrOverride) {
        boolean isReviewer = review.getReviewer() != null && review.getReviewer().getId().equals(actorId);
        if (!isReviewer && !hrOverride) {
            throw new AccessDeniedException("Only the assigned reviewer can do this");
        }
    }

    private void validateCycleRequest(InitiateReviewCycleRequest request, UUID existingCycleId) {
        // Validate date ranges
        if (request.reviewPeriodEnd().isBefore(request.reviewPeriodStart())) {
            throw new BadRequestAlertException("Review period end must be after start", ENTITY_NAME, "invaliddaterange");
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new BadRequestAlertException("Cycle end date must be after start date", ENTITY_NAME, "invaliddaterange");
        }

        // Deadlines fall within the cycle and follow the phase order
        LocalDate previous = request.startDate();
        String previousLabel = "the cycle start";
        String[] labels = { "Self-assessment", "Manager review", "Calibration", "Feedback" };
        LocalDate[] deadlines = {
            request.selfAssessmentDeadline(),
            request.managerReviewDeadline(),
            request.calibrationDeadline(),
            request.feedbackDeadline(),
        };
        for (int i = 0; i < deadlines.length; i++) {
            LocalDate deadline = deadlines[i];
            if (deadline == null) {
                continue;
            }
            if (deadline.isBefore(previous)) {
                throw new BadRequestAlertException(
                    labels[i] + " deadline can't be before " + previousLabel,
                    ENTITY_NAME,
                    "invaliddeadline"
                );
            }
            if (deadline.isAfter(request.endDate())) {
                throw new BadRequestAlertException(labels[i] + " deadline must be within cycle dates", ENTITY_NAME, "invaliddeadline");
            }
            previous = deadline;
            previousLabel = "the " + labels[i].toLowerCase(Locale.ROOT) + " deadline";
        }

        ReviewCycleType type = request.type() != null ? request.type() : ReviewCycleType.REGULAR;
        if (type == ReviewCycleType.REGULAR) {
            Set<UUID> departments = request.includeAllDepartments() || request.departmentIds() == null ? Set.of() : request.departmentIds();
            boolean overlaps = reviewCycleRepository
                .findActiveOverlapping(ReviewCycleType.REGULAR, existingCycleId, request.startDate(), request.endDate())
                .stream()
                .anyMatch(other -> sharePeople(other.getDepartmentIds(), departments));
            if (overlaps) {
                throw new BadRequestAlertException(
                    "A regular review cycle for the same people already runs at that time",
                    ENTITY_NAME,
                    "overlapping"
                );
            }
        }
        if (type == ReviewCycleType.AD_HOC) {
            if (request.employeeIds() == null || request.employeeIds().isEmpty()) {
                throw new BadRequestAlertException("Choose the employees an ad hoc cycle reviews", ENTITY_NAME, "noemployees");
            }
            for (UUID employeeId : request.employeeIds()) {
                Employee employee = employeeRepository
                    .findById(employeeId)
                    .orElseThrow(() -> EntityNotFoundException.create("Employee", employeeId));
                if (!EmployeeStatus.WORKING.contains(employee.getStatus())) {
                    throw new BadRequestAlertException(fullName(employee) + " isn't a current employee", ENTITY_NAME, "inactiveemployee");
                }
            }
        }
        reviewCycleRepository
            .findByName(request.name().trim())
            .filter(other -> !other.getId().equals(existingCycleId))
            .ifPresent(other -> {
                throw new BadRequestAlertException("A review cycle with this name already exists", ENTITY_NAME, "duplicatename");
            });

        if (type != ReviewCycleType.AD_HOC && !request.includeAllDepartments() && request.departmentIds() != null) {
            for (UUID deptId : request.departmentIds()) {
                if (!departmentRepository.existsById(deptId)) {
                    throw EntityNotFoundException.create("Department", deptId);
                }
            }
        }
    }

    /** Two department scopes share people when either is company-wide (empty) or they share a department. */
    private static boolean sharePeople(Set<UUID> a, Set<UUID> b) {
        return a == null || a.isEmpty() || b.isEmpty() || a.stream().anyMatch(b::contains);
    }

    /**
     * Copies the request onto the cycle. An empty department set means the whole company; an ad hoc
     * cycle has named employees instead.
     */
    private void applyRequest(ReviewCycle cycle, InitiateReviewCycleRequest request) {
        cycle.setName(request.name().trim());
        cycle.setDescription(request.description());
        cycle.setReviewPeriodStart(request.reviewPeriodStart());
        cycle.setReviewPeriodEnd(request.reviewPeriodEnd());
        cycle.setStartDate(request.startDate());
        cycle.setEndDate(request.endDate());
        cycle.setSelfAssessmentDeadline(request.selfAssessmentDeadline());
        cycle.setManagerReviewDeadline(request.managerReviewDeadline());
        cycle.setCalibrationDeadline(request.calibrationDeadline());
        cycle.setFeedbackDeadline(request.feedbackDeadline());
        ReviewCycleType type = request.type() != null ? request.type() : ReviewCycleType.REGULAR;
        cycle.setType(type);
        boolean adHoc = type == ReviewCycleType.AD_HOC;
        cycle.setDepartmentIds(
            adHoc || request.includeAllDepartments() || request.departmentIds() == null
                ? new HashSet<>()
                : new HashSet<>(request.departmentIds())
        );
        cycle.setEmployeeIds(adHoc ? new HashSet<>(request.employeeIds()) : new HashSet<>());
        cycle.setFeedbackVisibility(request.feedbackVisibility() != null ? request.feedbackVisibility() : FeedbackVisibility.MANAGER_ONLY);
        cycle.setAllowFeedbackNominations(request.allowFeedbackNominations() == null || request.allowFeedbackNominations());
        cycle.setMaxContributors(request.maxContributors() != null ? request.maxContributors() : DEFAULT_MAX_CONTRIBUTORS);
        cycle.setRequiresSignOff(Boolean.TRUE.equals(request.requiresSignOff()));
        cycle.setTemplate(
            request.templateId() != null ? frameworkService.templateForCycle(request.templateId(), cycle.getTemplate()) : null
        );
        cycle.setTotalEmployees(getEligibleEmployees(cycle).size());
    }

    private Map<String, Object> buildWorkflowContext(ReviewCycle cycle) {
        Map<String, Object> context = new HashMap<>();
        context.put("cycleId", cycle.getId().toString());
        context.put("cycleName", cycle.getName());
        context.put("reviewPeriodStart", cycle.getReviewPeriodStart().toString());
        context.put("reviewPeriodEnd", cycle.getReviewPeriodEnd().toString());
        context.put("totalEmployees", cycle.getTotalEmployees());
        context.put("departmentCount", cycle.getDepartmentIds().size());
        return context;
    }

    /**
     * Employees the cycle reviews: currently working, in the cycle's departments (all when none are
     * set), and hired before the review period ended. Managers and departments are fetched with
     * them, so building reviews and notifications doesn't query per employee.
     */
    /**
     * Who a cycle reviews: current employees in its departments who started by the end of the
     * period (REGULAR); those of them not yet confirmed whose probation ends within the period
     * (PROBATION); or the named employees still working (AD_HOC).
     */
    private List<Employee> getEligibleEmployees(ReviewCycle cycle) {
        return employeeRepository.findAll(eligibleSpec(cycle));
    }

    /** The query behind {@link #getEligibleEmployees}; public for the repository tests. */
    public static Specification<Employee> eligibleSpec(ReviewCycle cycle) {
        ReviewCycleType type = cycle.getType();
        Set<UUID> departmentIds = type == ReviewCycleType.AD_HOC ? Set.of() : cycle.getDepartmentIds();
        Set<UUID> employeeIds = cycle.getEmployeeIds();
        LocalDate periodStart = cycle.getReviewPeriodStart();
        LocalDate periodEnd = cycle.getReviewPeriodEnd();
        return (root, query, cb) -> {
            if (query != null && Employee.class.equals(query.getResultType())) {
                root.fetch("manager", JoinType.LEFT);
                root.fetch("department", JoinType.LEFT);
            }
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("status").in(EmployeeStatus.WORKING));
            if (departmentIds != null && !departmentIds.isEmpty()) {
                predicates.add(root.get("department").get("id").in(departmentIds));
            }
            if (type == ReviewCycleType.AD_HOC) {
                predicates.add(employeeIds == null || employeeIds.isEmpty() ? cb.disjunction() : root.get("id").in(employeeIds));
            } else if (periodEnd != null) {
                predicates.add(cb.or(cb.isNull(root.get("startDate")), cb.lessThanOrEqualTo(root.get("startDate"), periodEnd)));
            }
            if (type == ReviewCycleType.PROBATION) {
                predicates.add(cb.isNull(root.get("confirmationDate")));
                predicates.add(cb.isNotNull(root.get("probationEndDate")));
                if (periodStart != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("probationEndDate"), periodStart));
                }
                if (periodEnd != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("probationEndDate"), periodEnd));
                }
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    /**
     * Takes a review out of its cycle: refused once shared. A pending or returned sign-off is
     * closed (an approved or waived one stays); open feedback requests are cancelled; a reviewer
     * still working is told.
     */
    private void exclude(PerformanceReview review, String reason, Employee by) {
        if (review.getStatus() == PerformanceReviewStatus.SHARED) {
            throw new BadRequestAlertException("A review already shared can't be excluded", ENTITY_NAME, "shared");
        }
        if (review.getStatus() == PerformanceReviewStatus.EXCLUDED) {
            throw new BadRequestAlertException("This review is already excluded", ENTITY_NAME, "excluded");
        }
        review.setStatus(PerformanceReviewStatus.EXCLUDED);
        review.setExcludedAt(Instant.now());
        review.setExclusionReason(reason);
        review.setExcludedBy(by);
        if (review.getSignOffStatus() == SignOffStatus.PENDING || review.getSignOffStatus() == SignOffStatus.RETURNED) {
            approvalService.closePendingForEntity(review.getId(), ApprovalType.PERFORMANCE_REVIEW, "review excluded from the cycle");
            review.setSignOffStatus(null);
        }
        performanceReviewRepository.save(review);
        feedbackService.closeOpenRequests(review);

        Employee reviewer = review.getReviewer();
        if (reviewer != null && EmployeeStatus.WORKING.contains(reviewer.getStatus())) {
            notificationService.notifyTaskCompleted(
                reviewer.getId(),
                "Performance Review No Longer Needed",
                fullName(review.getEmployee()) + " is no longer part of " + review.getCycle().getName() + ", so their review isn't needed.",
                review.getId(),
                "PerformanceReview"
            );
        }
    }

    /** A new review in {@code cycle}, reviewed by the employee's manager when they have a working one. */
    private static PerformanceReview newReview(ReviewCycle cycle, Employee employee) {
        Employee manager = employee.getManager();
        PerformanceReview review = new PerformanceReview();
        review.setCycle(cycle);
        review.setEmployee(employee);
        review.setReviewer(manager != null && EmployeeStatus.WORKING.contains(manager.getStatus()) ? manager : null);
        review.setReviewDate(cycle.getReviewPeriodEnd());
        review.setStatus(PerformanceReviewStatus.NOT_STARTED);
        return review;
    }

    /**
     * Who can join a running cycle: working, not in it yet, matching {@code term}; for a regular or
     * probation cycle also within its scope ({@link #eligibleSpec}).
     */
    private Specification<Employee> addableSpec(ReviewCycle cycle, String term) {
        Specification<Employee> spec = ReviewFeedbackService.candidateSpec(
            performanceReviewRepository.findEmployeeIdsByCycleId(cycle.getId()),
            term
        );
        return cycle.getType() == ReviewCycleType.AD_HOC ? spec : spec.and(eligibleSpec(cycle));
    }

    private String names(Collection<UUID> employeeIds) {
        return employeeRepository
            .findAllById(employeeIds)
            .stream()
            .map(PerformanceReviewCycleService::fullName)
            .sorted()
            .collect(Collectors.joining(", "));
    }

    /** A review whose final rating was changed in calibration. */
    private static boolean calibrated(PerformanceReview review) {
        return review.getManagerRating() != null && review.getRating() != null && !review.getManagerRating().equals(review.getRating());
    }

    /**
     * The phase a cycle goes back to (see {@link #moveBackPhase}). Feedback delivery goes back to
     * the phase it was started from, read from the cycle's history.
     */
    private ReviewCyclePhase previousPhase(ReviewCycle cycle) {
        if (cycle.getPhase() == ReviewCyclePhase.SELF_ASSESSMENT) {
            throw new BadRequestAlertException(
                "Self-assessment can't go back to draft: the cycle's reviews already exist",
                ENTITY_NAME,
                "cannotmoveback"
            );
        }
        return reopenablePhase(cycle).orElseThrow(() ->
            new BadRequestAlertException(
                cycle.getPhase() == ReviewCyclePhase.FEEDBACK_DELIVERY
                    ? "The phase before feedback delivery isn't recorded"
                    : "A cycle can only go back while it runs. Current phase: " + cycle.getPhase(),
                ENTITY_NAME,
                "cannotmoveback"
            )
        );
    }

    /** The phase going back would reopen; empty when the cycle can't go back. */
    private Optional<ReviewCyclePhase> reopenablePhase(ReviewCycle cycle) {
        return switch (cycle.getPhase()) {
            case MANAGER_REVIEW -> Optional.of(ReviewCyclePhase.SELF_ASSESSMENT);
            case CALIBRATION -> Optional.of(ReviewCyclePhase.MANAGER_REVIEW);
            case GOAL_SETTING -> Optional.of(ReviewCyclePhase.FEEDBACK_DELIVERY);
            case FEEDBACK_DELIVERY -> phaseBeforeFeedback(cycle);
            default -> Optional.empty();
        };
    }

    /** Manager review or calibration: the phase feedback delivery was last started from. */
    private Optional<ReviewCyclePhase> phaseBeforeFeedback(ReviewCycle cycle) {
        Set<String> forward = Set.of(STATE_MANAGER_REVIEW, STATE_CALIBRATION);
        List<WorkflowStateTransition> history = cycle.getWorkflowInstance() != null
            ? workflowStateManager.getWorkflowHistory(cycle.getWorkflowInstance().getId())
            : List.of();
        for (int i = history.size() - 1; i >= 0; i--) {
            WorkflowStateTransition t = history.get(i);
            if (STATE_FEEDBACK_DELIVERY.equals(t.getToState()) && forward.contains(t.getFromState())) {
                return Optional.of(ReviewCyclePhase.valueOf(t.getFromState()));
            }
        }
        return Optional.empty();
    }

    /**
     * Sets the deadlines of the reopened phase and those after it from the request (empty keeps a
     * date). The reopened phase's deadline is today or later; each stays within the cycle and in
     * phase order.
     */
    private void applyMovedBackDeadlines(ReviewCycle cycle, ReviewCyclePhase to, MoveBackPhaseRequest request) {
        LocalDate[] dates = {
            cycle.getSelfAssessmentDeadline(),
            cycle.getManagerReviewDeadline(),
            cycle.getCalibrationDeadline(),
            cycle.getFeedbackDeadline(),
        };
        LocalDate[] requested = {
            request.selfAssessmentDeadline(),
            request.managerReviewDeadline(),
            request.calibrationDeadline(),
            request.feedbackDeadline(),
        };
        String[] labels = { "Self-assessment", "Manager review", "Calibration", "Feedback" };
        int first =
            switch (to) {
                case SELF_ASSESSMENT -> 0;
                case MANAGER_REVIEW -> 1;
                case CALIBRATION -> 2;
                default -> 3;
            };
        for (int i = first; i < dates.length; i++) {
            if (requested[i] != null) {
                dates[i] = requested[i];
            }
        }
        LocalDate today = LocalDate.now(organizationSettingsService.zone());
        if (dates[first] == null || dates[first].isBefore(today)) {
            throw new BadRequestAlertException(
                labels[first] + " deadline must be today or later to reopen the phase",
                ENTITY_NAME,
                "invaliddeadline"
            );
        }
        LocalDate previous = cycle.getStartDate();
        String previousLabel = "the cycle start";
        for (int i = 0; i < dates.length; i++) {
            if (dates[i] == null) {
                continue;
            }
            if (i >= first && dates[i].isBefore(previous)) {
                throw new BadRequestAlertException(
                    labels[i] + " deadline can't be before " + previousLabel,
                    ENTITY_NAME,
                    "invaliddeadline"
                );
            }
            if (i >= first && dates[i].isAfter(cycle.getEndDate())) {
                throw new BadRequestAlertException(labels[i] + " deadline must be within cycle dates", ENTITY_NAME, "invaliddeadline");
            }
            previous = dates[i];
            previousLabel = "the " + labels[i].toLowerCase(Locale.ROOT) + " deadline";
        }
        cycle.setSelfAssessmentDeadline(dates[0]);
        cycle.setManagerReviewDeadline(dates[1]);
        cycle.setCalibrationDeadline(dates[2]);
        cycle.setFeedbackDeadline(dates[3]);
    }

    /** Who started the cycle's current phase, when an employee did: the open deadline's assignee. */
    private Optional<Employee> phaseOwner(ReviewCycle cycle) {
        if (cycle.getWorkflowInstance() == null) {
            return Optional.empty();
        }
        return deadlineMonitorService
            .getIncompleteDeadlines(cycle.getWorkflowInstance().getId())
            .stream()
            .map(WorkflowDeadline::getAssignee)
            .filter(Objects::nonNull)
            .findFirst();
    }

    /** One review per eligible employee not yet in the cycle, reviewed by their manager when they have a working one. */
    private List<PerformanceReview> createReviews(ReviewCycle cycle) {
        Set<UUID> alreadyReviewed = performanceReviewRepository.findEmployeeIdsByCycleId(cycle.getId());
        List<PerformanceReview> reviews = new ArrayList<>();
        for (Employee employee : getEligibleEmployees(cycle)) {
            if (!alreadyReviewed.contains(employee.getId())) {
                reviews.add(newReview(cycle, employee));
            }
        }
        return performanceReviewRepository.saveAll(reviews);
    }

    private void transitionWorkflow(ReviewCycle cycle, String state, String reason) {
        if (cycle.getWorkflowInstance() != null) {
            workflowStateManager.transitionState(cycle.getWorkflowInstance().getId(), state, reason);
        }
    }

    /**
     * Closes the previous phase's deadline and registers this one at the end of the deadline day,
     * in the company's timezone. Warnings and overdue notices go to the person who started the
     * phase, when they are an employee.
     */
    private void registerPhaseDeadline(ReviewCycle cycle, String type, String description, LocalDate date, int warningHours) {
        if (cycle.getWorkflowInstance() == null) {
            return;
        }
        closeOpenDeadlines(cycle);
        UUID owner = SecurityUtils.getCurrentUserId().filter(employeeRepository::existsById).orElse(null);
        deadlineMonitorService.registerDeadline(
            cycle.getWorkflowInstance().getId(),
            type,
            description + cycle.getName(),
            endOfDay(date),
            warningHours,
            owner
        );
    }

    private void closeOpenDeadlines(ReviewCycle cycle) {
        if (cycle.getWorkflowInstance() == null) {
            return;
        }
        deadlineMonitorService
            .getIncompleteDeadlines(cycle.getWorkflowInstance().getId())
            .forEach(deadline -> deadlineMonitorService.completeDeadline(deadline.getId()));
    }

    /** The end of {@code date} in the company's timezone. */
    private Instant endOfDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay(organizationSettingsService.zone()).toInstant();
    }

    /** Per reviewer, how many of {@code reviews} match {@code pending}; reviews without a reviewer are skipped. */
    private static Map<Employee, Long> pendingByReviewer(
        List<PerformanceReview> reviews,
        java.util.function.Predicate<PerformanceReview> pending
    ) {
        return reviews
            .stream()
            .filter(r -> r.getReviewer() != null && pending.test(r))
            .collect(Collectors.groupingBy(PerformanceReview::getReviewer, LinkedHashMap::new, Collectors.counting()));
    }

    private void sendSelfAssessmentReminders(ReviewCycle cycle) {
        Instant deadline = endOfDay(cycle.getSelfAssessmentDeadline());
        List<PerformanceReview> pending = performanceReviewRepository.findByCycleIdAndStatusIn(
            cycle.getId(),
            List.of(PerformanceReviewStatus.NOT_STARTED)
        );
        for (PerformanceReview review : pending) {
            notificationService.notifyDeadlineApproaching(
                review.getEmployee().getId(),
                "Self-Assessment Reminder",
                "Your self-assessment for " + cycle.getName() + " is due by " + cycle.getSelfAssessmentDeadline() + ".",
                cycle.getId(),
                "ReviewCycle",
                deadline
            );
        }
    }

    private void sendManagerReviewReminders(ReviewCycle cycle) {
        Instant deadline = endOfDay(cycle.getManagerReviewDeadline());
        pendingByReviewer(
            performanceReviewRepository.findByCycleId(cycle.getId()),
            r -> r.getManagerSubmittedAt() == null && r.getStatus() != PerformanceReviewStatus.EXCLUDED
        ).forEach((reviewer, count) ->
            notificationService.notifyDeadlineApproaching(
                reviewer.getId(),
                "Manager Review Reminder",
                "You still have " +
                count +
                " review(s) to write for " +
                cycle.getName() +
                ", due by " +
                cycle.getManagerReviewDeadline() +
                ".",
                cycle.getId(),
                "ReviewCycle",
                deadline
            )
        );
    }

    private void sendFeedbackDeliveryReminders(ReviewCycle cycle) {
        Instant deadline = endOfDay(cycle.getFeedbackDeadline());
        pendingByReviewer(
            performanceReviewRepository.findByCycleIdAndStatusIn(cycle.getId(), List.of(PerformanceReviewStatus.MANAGER_SUBMITTED)),
            PerformanceReview::isSignOffSettled
        ).forEach((reviewer, count) ->
            notificationService.notifyDeadlineApproaching(
                reviewer.getId(),
                "Feedback Delivery Reminder",
                "You still have " +
                count +
                " feedback meeting(s) to hold for " +
                cycle.getName() +
                ", due by " +
                cycle.getFeedbackDeadline() +
                ".",
                cycle.getId(),
                "ReviewCycle",
                deadline
            )
        );
    }

    private void refreshProgressAndSave(ReviewCycle cycle) {
        refreshProgress(cycle);
        reviewCycleRepository.save(cycle);
    }

    /** Recounts the cycle's counters from its reviews. */
    private List<ReviewProgressRow> refreshProgress(ReviewCycle cycle) {
        List<ReviewProgressRow> rows = performanceReviewRepository.countProgressByDepartment(cycle.getId(), PerformanceReviewStatus.SHARED);
        cycle.setTotalEmployees((int) sum(rows, ReviewProgressRow::total));
        cycle.setCompletedSelfAssessments((int) sum(rows, ReviewProgressRow::selfSubmitted));
        cycle.setCompletedManagerReviews((int) sum(rows, ReviewProgressRow::managerSubmitted));
        cycle.setDeliveredFeedbacks((int) sum(rows, ReviewProgressRow::shared));
        return rows;
    }

    private static long sum(List<ReviewProgressRow> rows, Function<ReviewProgressRow, Long> field) {
        return rows.stream().map(field).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
    }

    /**
     * The cycle's progress from its counters; with {@code withDepartments}, also per department of
     * the reviewed employees (one grouped query; empty while the cycle is a draft).
     */
    private ReviewCycleResponse.CycleProgress progressOf(ReviewCycle cycle, boolean withDepartments) {
        List<ReviewCycleResponse.DepartmentProgress> departmentProgress = List.of();
        Integer feedbackRequested = null;
        Integer feedbackReceived = null;
        Integer acknowledged = null;
        Integer disputesOpen = null;
        Integer signOffPending = null;
        Integer signOffReturned = null;
        if (withDepartments && cycle.getPhase() != ReviewCyclePhase.DRAFT && cycle.isRequiresSignOff()) {
            signOffPending = (int) performanceReviewRepository.countByCycleIdAndSignOffStatus(cycle.getId(), SignOffStatus.PENDING);
            signOffReturned = (int) performanceReviewRepository.countByCycleIdAndSignOffStatus(cycle.getId(), SignOffStatus.RETURNED);
        }
        if (withDepartments && cycle.getPhase() != ReviewCyclePhase.DRAFT) {
            int[] feedback = feedbackService.totals(cycle.getId());
            feedbackRequested = feedback[0];
            feedbackReceived = feedback[1];
            acknowledged = (int) performanceReviewRepository.countByCycleIdAndAcknowledgedAtIsNotNull(cycle.getId());
            disputesOpen = (int) performanceReviewRepository.countByCycleIdAndDisputedTrueAndDisputeResolvedAtIsNull(cycle.getId());
            departmentProgress = performanceReviewRepository
                .countProgressByDepartment(cycle.getId(), PerformanceReviewStatus.SHARED)
                .stream()
                .map(row -> toDepartmentProgress(cycle.getPhase(), row))
                .sorted(
                    Comparator.comparing(ReviewCycleResponse.DepartmentProgress::departmentName, Comparator.nullsLast(String::compareTo))
                )
                .toList();
        }

        return new ReviewCycleResponse.CycleProgress(
            cycle.getTotalEmployees() != null ? cycle.getTotalEmployees() : 0,
            cycle.getCompletedSelfAssessments() != null ? cycle.getCompletedSelfAssessments() : 0,
            cycle.getCompletedManagerReviews() != null ? cycle.getCompletedManagerReviews() : 0,
            cycle.getDeliveredFeedbacks() != null ? cycle.getDeliveredFeedbacks() : 0,
            cycle.getCompletionPercentage(),
            departmentProgress,
            feedbackRequested,
            feedbackReceived,
            acknowledged,
            disputesOpen,
            signOffPending,
            signOffReturned
        );
    }

    /** A department's reviews done for the current phase's work. */
    private static ReviewCycleResponse.DepartmentProgress toDepartmentProgress(ReviewCyclePhase phase, ReviewProgressRow row) {
        int total = row.total() != null ? row.total().intValue() : 0;
        long done =
            switch (phase) {
                case DRAFT -> 0;
                case SELF_ASSESSMENT -> row.selfSubmitted();
                case MANAGER_REVIEW, CALIBRATION -> row.managerSubmitted();
                case FEEDBACK_DELIVERY, GOAL_SETTING, COMPLETED, ARCHIVED -> row.shared();
            };
        int completed = (int) done;
        return new ReviewCycleResponse.DepartmentProgress(
            row.departmentId(),
            row.departmentName(),
            total,
            completed,
            total > 0 ? (completed * 100) / total : 0
        );
    }

    private ReviewCycleResponse mapToResponse(ReviewCycle cycle, boolean withDepartments) {
        ReviewCycleResponse.PhaseDeadlines deadlines = new ReviewCycleResponse.PhaseDeadlines(
            cycle.getSelfAssessmentDeadline(),
            cycle.getManagerReviewDeadline(),
            cycle.getCalibrationDeadline(),
            cycle.getFeedbackDeadline()
        );

        UUID workflowId = cycle.getWorkflowInstance() != null ? cycle.getWorkflowInstance().getId() : null;

        return new ReviewCycleResponse(
            cycle.getId(),
            workflowId,
            cycle.getName(),
            cycle.getDescription(),
            cycle.getReviewPeriodStart(),
            cycle.getReviewPeriodEnd(),
            cycle.getStartDate(),
            cycle.getEndDate(),
            cycle.getPhase(),
            withDepartments ? reopenablePhase(cycle).orElse(null) : null,
            deadlines,
            progressOf(cycle, withDepartments),
            new ReviewCycleResponse.FeedbackSettings(
                cycle.getFeedbackVisibility(),
                cycle.isAllowFeedbackNominations(),
                cycle.getMaxContributors()
            ),
            cycle.isRequiresSignOff(),
            cycle.getType(),
            cycle.getDepartmentIds(),
            cycle.getEmployeeIds().isEmpty()
                ? List.of()
                : employeeRepository
                    .findAllById(cycle.getEmployeeIds())
                    .stream()
                    .map(e -> new ReviewCycleResponse.CycleEmployee(e.getId(), fullName(e)))
                    .sorted(Comparator.comparing(ReviewCycleResponse.CycleEmployee::name))
                    .toList(),
            cycle.getTemplate() != null
                ? new ReviewCycleResponse.CycleTemplate(cycle.getTemplate().getId(), cycle.getTemplate().getName())
                : null,
            cycle.getActive() != null && cycle.getActive(),
            cycle.getCreatedDate(),
            cycle.getLastModifiedDate()
        );
    }

    private static String fullName(Employee employee) {
        return employee.getFirstName() + " " + employee.getLastName();
    }
}
