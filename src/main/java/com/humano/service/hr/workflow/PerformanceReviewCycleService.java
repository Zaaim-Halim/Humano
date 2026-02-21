package com.humano.service.hr.workflow;

import com.humano.domain.enumeration.hr.ReviewCyclePhase;
import com.humano.domain.enumeration.hr.WorkflowType;
import com.humano.domain.hr.Department;
import com.humano.domain.hr.PerformanceReview;
import com.humano.domain.hr.ReviewCycle;
import com.humano.domain.hr.WorkflowInstance;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.workflow.requests.InitiateReviewCycleRequest;
import com.humano.dto.hr.workflow.requests.ManagerReviewRequest;
import com.humano.dto.hr.workflow.requests.SelfAssessmentRequest;
import com.humano.dto.hr.workflow.responses.ReviewCycleResponse;
import com.humano.repository.hr.DepartmentRepository;
import com.humano.repository.hr.PerformanceReviewRepository;
import com.humano.repository.hr.workflow.ReviewCycleRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.hr.workflow.infrastructure.DeadlineMonitorService;
import com.humano.service.hr.workflow.infrastructure.NotificationOrchestrationService;
import com.humano.service.hr.workflow.infrastructure.WorkflowStateManager;
import com.humano.web.rest.errors.BadRequestAlertException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing performance review cycle workflows.
 * Handles the complete lifecycle of review cycles including phases and tracking.
 */
@Service
@Transactional
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

    private final ReviewCycleRepository reviewCycleRepository;
    private final PerformanceReviewRepository performanceReviewRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final WorkflowStateManager workflowStateManager;
    private final NotificationOrchestrationService notificationService;
    private final DeadlineMonitorService deadlineMonitorService;

    public PerformanceReviewCycleService(
        ReviewCycleRepository reviewCycleRepository,
        PerformanceReviewRepository performanceReviewRepository,
        EmployeeRepository employeeRepository,
        DepartmentRepository departmentRepository,
        WorkflowStateManager workflowStateManager,
        NotificationOrchestrationService notificationService,
        DeadlineMonitorService deadlineMonitorService
    ) {
        this.reviewCycleRepository = reviewCycleRepository;
        this.performanceReviewRepository = performanceReviewRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.workflowStateManager = workflowStateManager;
        this.notificationService = notificationService;
        this.deadlineMonitorService = deadlineMonitorService;
    }

    /**
     * Initiate a new review cycle.
     */
    public ReviewCycleResponse initiateCycle(InitiateReviewCycleRequest request) {
        log.info("Initiating review cycle: {}", request.name());

        // Validate request
        validateCycleRequest(request);

        // Check for overlapping cycles
        if (reviewCycleRepository.existsOverlappingCycle(UUID.randomUUID(), request.startDate(), request.endDate())) {
            throw new BadRequestAlertException("An overlapping review cycle already exists", "reviewCycle", "overlapping");
        }

        // Check for duplicate name
        if (reviewCycleRepository.findByName(request.name()).isPresent()) {
            throw new BadRequestAlertException("A review cycle with this name already exists", "reviewCycle", "duplicatename");
        }

        // Create the review cycle entity
        ReviewCycle cycle = new ReviewCycle();
        cycle.setName(request.name());
        cycle.setDescription(request.description());
        cycle.setReviewPeriodStart(request.reviewPeriodStart());
        cycle.setReviewPeriodEnd(request.reviewPeriodEnd());
        cycle.setStartDate(request.startDate());
        cycle.setEndDate(request.endDate());
        cycle.setPhase(ReviewCyclePhase.DRAFT);
        cycle.setSelfAssessmentDeadline(request.selfAssessmentDeadline());
        cycle.setManagerReviewDeadline(request.managerReviewDeadline());
        cycle.setCalibrationDeadline(request.calibrationDeadline());
        cycle.setFeedbackDeadline(request.feedbackDeadline());
        cycle.setActive(true);

        // Handle department selection
        Set<UUID> departmentIds = new HashSet<>();
        if (request.includeAllDepartments()) {
            departmentIds = departmentRepository.findAll().stream().map(Department::getId).collect(Collectors.toSet());
        } else if (request.departmentIds() != null && !request.departmentIds().isEmpty()) {
            // Validate all department IDs exist
            for (UUID deptId : request.departmentIds()) {
                if (!departmentRepository.existsById(deptId)) {
                    throw EntityNotFoundException.create("Department", deptId);
                }
            }
            departmentIds = request.departmentIds();
        }
        cycle.setDepartmentIds(departmentIds);

        // Calculate total employees in the cycle
        int totalEmployees = countEmployeesInDepartments(departmentIds);
        cycle.setTotalEmployees(totalEmployees);
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
            totalEmployees,
            departmentIds.size()
        );

        return mapToResponse(savedCycle);
    }

    /**
     * Get review cycle status.
     */
    @Transactional(readOnly = true)
    public ReviewCycleResponse getCycleStatus(UUID cycleId) {
        log.info("Getting review cycle status: {}", cycleId);

        ReviewCycle cycle = reviewCycleRepository
            .findById(cycleId)
            .orElseThrow(() -> EntityNotFoundException.create("ReviewCycle", cycleId));

        return mapToResponse(cycle);
    }

    /**
     * Get active review cycles.
     */
    @Transactional(readOnly = true)
    public List<ReviewCycleResponse> getActiveCycles() {
        log.info("Getting active review cycles");

        return reviewCycleRepository.findByActiveTrue().stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    /**
     * Get cycles by phase.
     */
    @Transactional(readOnly = true)
    public List<ReviewCycleResponse> getCyclesByPhase(ReviewCyclePhase phase) {
        log.info("Getting review cycles by phase: {}", phase);

        return reviewCycleRepository.findByPhaseAndActiveTrue(phase).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    /**
     * Start self-assessment phase.
     */
    public ReviewCycleResponse startSelfAssessmentPhase(UUID cycleId) {
        log.info("Starting self-assessment phase for cycle: {}", cycleId);

        ReviewCycle cycle = reviewCycleRepository
            .findById(cycleId)
            .orElseThrow(() -> EntityNotFoundException.create("ReviewCycle", cycleId));

        // Validate current phase
        if (cycle.getPhase() != ReviewCyclePhase.DRAFT) {
            throw new BadRequestAlertException(
                "Can only start self-assessment from DRAFT phase. Current phase: " + cycle.getPhase(),
                "reviewCycle",
                "invalidphase"
            );
        }

        // Validate deadline is set
        if (cycle.getSelfAssessmentDeadline() == null) {
            throw new BadRequestAlertException(
                "Self-assessment deadline must be set before starting the phase",
                "reviewCycle",
                "nodeadline"
            );
        }

        // Update phase
        cycle.setPhase(ReviewCyclePhase.SELF_ASSESSMENT);
        ReviewCycle savedCycle = reviewCycleRepository.save(cycle);

        // Start and transition workflow
        if (cycle.getWorkflowInstance() != null) {
            WorkflowInstance workflow = cycle.getWorkflowInstance();
            if (workflow.getStatus().name().equals("DRAFT")) {
                workflowStateManager.startWorkflow(workflow.getId());
            }
            workflowStateManager.transitionState(workflow.getId(), STATE_SELF_ASSESSMENT, "Self-assessment phase started");

            // Register deadline for self-assessment
            Instant deadline = cycle.getSelfAssessmentDeadline().atStartOfDay(ZoneId.systemDefault()).toInstant();
            deadlineMonitorService.registerDeadline(
                workflow.getId(),
                "SELF_ASSESSMENT",
                "Self-assessment deadline for " + cycle.getName(),
                deadline,
                72, // Warning 72 hours before
                null
            );
        }

        // Send notifications to all employees in the cycle
        notifyEmployeesAboutSelfAssessment(savedCycle);

        log.info("Self-assessment phase started for cycle {}", cycleId);
        return mapToResponse(savedCycle);
    }

    /**
     * Start manager review phase.
     */
    public ReviewCycleResponse startManagerReviewPhase(UUID cycleId) {
        log.info("Starting manager review phase for cycle: {}", cycleId);

        ReviewCycle cycle = reviewCycleRepository
            .findById(cycleId)
            .orElseThrow(() -> EntityNotFoundException.create("ReviewCycle", cycleId));

        // Validate current phase
        if (cycle.getPhase() != ReviewCyclePhase.SELF_ASSESSMENT) {
            throw new BadRequestAlertException(
                "Can only start manager review from SELF_ASSESSMENT phase. Current phase: " + cycle.getPhase(),
                "reviewCycle",
                "invalidphase"
            );
        }

        // Validate deadline is set
        if (cycle.getManagerReviewDeadline() == null) {
            throw new BadRequestAlertException(
                "Manager review deadline must be set before starting the phase",
                "reviewCycle",
                "nodeadline"
            );
        }

        // Update phase
        cycle.setPhase(ReviewCyclePhase.MANAGER_REVIEW);
        ReviewCycle savedCycle = reviewCycleRepository.save(cycle);

        // Transition workflow
        if (cycle.getWorkflowInstance() != null) {
            workflowStateManager.transitionState(cycle.getWorkflowInstance().getId(), STATE_MANAGER_REVIEW, "Manager review phase started");

            // Register deadline for manager review
            Instant deadline = cycle.getManagerReviewDeadline().atStartOfDay(ZoneId.systemDefault()).toInstant();
            deadlineMonitorService.registerDeadline(
                cycle.getWorkflowInstance().getId(),
                "MANAGER_REVIEW",
                "Manager review deadline for " + cycle.getName(),
                deadline,
                72,
                null
            );
        }

        // Notify managers about pending reviews
        notifyManagersAboutReviews(savedCycle);

        log.info("Manager review phase started for cycle {}", cycleId);
        return mapToResponse(savedCycle);
    }

    /**
     * Start calibration phase.
     */
    public ReviewCycleResponse startCalibrationPhase(UUID cycleId) {
        log.info("Starting calibration phase for cycle: {}", cycleId);

        ReviewCycle cycle = reviewCycleRepository
            .findById(cycleId)
            .orElseThrow(() -> EntityNotFoundException.create("ReviewCycle", cycleId));

        // Validate current phase
        if (cycle.getPhase() != ReviewCyclePhase.MANAGER_REVIEW) {
            throw new BadRequestAlertException(
                "Can only start calibration from MANAGER_REVIEW phase. Current phase: " + cycle.getPhase(),
                "reviewCycle",
                "invalidphase"
            );
        }

        // Validate deadline is set
        if (cycle.getCalibrationDeadline() == null) {
            throw new BadRequestAlertException("Calibration deadline must be set before starting the phase", "reviewCycle", "nodeadline");
        }

        // Update phase
        cycle.setPhase(ReviewCyclePhase.CALIBRATION);
        ReviewCycle savedCycle = reviewCycleRepository.save(cycle);

        // Transition workflow
        if (cycle.getWorkflowInstance() != null) {
            workflowStateManager.transitionState(cycle.getWorkflowInstance().getId(), STATE_CALIBRATION, "Calibration phase started");

            // Register deadline for calibration
            Instant deadline = cycle.getCalibrationDeadline().atStartOfDay(ZoneId.systemDefault()).toInstant();
            deadlineMonitorService.registerDeadline(
                cycle.getWorkflowInstance().getId(),
                "CALIBRATION",
                "Calibration deadline for " + cycle.getName(),
                deadline,
                48,
                null
            );
        }

        // Notify HR and leadership about calibration session
        notifyAboutCalibration(savedCycle);

        log.info("Calibration phase started for cycle {}", cycleId);
        return mapToResponse(savedCycle);
    }

    /**
     * Start feedback delivery phase.
     */
    public ReviewCycleResponse startFeedbackDeliveryPhase(UUID cycleId) {
        log.info("Starting feedback delivery phase for cycle: {}", cycleId);

        ReviewCycle cycle = reviewCycleRepository
            .findById(cycleId)
            .orElseThrow(() -> EntityNotFoundException.create("ReviewCycle", cycleId));

        // Validate current phase
        if (cycle.getPhase() != ReviewCyclePhase.CALIBRATION) {
            throw new BadRequestAlertException(
                "Can only start feedback delivery from CALIBRATION phase. Current phase: " + cycle.getPhase(),
                "reviewCycle",
                "invalidphase"
            );
        }

        // Validate deadline is set
        if (cycle.getFeedbackDeadline() == null) {
            throw new BadRequestAlertException("Feedback deadline must be set before starting the phase", "reviewCycle", "nodeadline");
        }

        // Update phase
        cycle.setPhase(ReviewCyclePhase.FEEDBACK_DELIVERY);
        ReviewCycle savedCycle = reviewCycleRepository.save(cycle);

        // Transition workflow
        if (cycle.getWorkflowInstance() != null) {
            workflowStateManager.transitionState(
                cycle.getWorkflowInstance().getId(),
                STATE_FEEDBACK_DELIVERY,
                "Feedback delivery phase started"
            );

            // Register deadline for feedback delivery
            Instant deadline = cycle.getFeedbackDeadline().atStartOfDay(ZoneId.systemDefault()).toInstant();
            deadlineMonitorService.registerDeadline(
                cycle.getWorkflowInstance().getId(),
                "FEEDBACK_DELIVERY",
                "Feedback delivery deadline for " + cycle.getName(),
                deadline,
                48,
                null
            );
        }

        // Notify managers to schedule feedback meetings
        notifyManagersAboutFeedbackDelivery(savedCycle);

        log.info("Feedback delivery phase started for cycle {}", cycleId);
        return mapToResponse(savedCycle);
    }

    /**
     * Start goal setting phase.
     */
    public ReviewCycleResponse startGoalSettingPhase(UUID cycleId) {
        log.info("Starting goal setting phase for cycle: {}", cycleId);

        ReviewCycle cycle = reviewCycleRepository
            .findById(cycleId)
            .orElseThrow(() -> EntityNotFoundException.create("ReviewCycle", cycleId));

        // Validate current phase
        if (cycle.getPhase() != ReviewCyclePhase.FEEDBACK_DELIVERY) {
            throw new BadRequestAlertException(
                "Can only start goal setting from FEEDBACK_DELIVERY phase. Current phase: " + cycle.getPhase(),
                "reviewCycle",
                "invalidphase"
            );
        }

        // Update phase
        cycle.setPhase(ReviewCyclePhase.GOAL_SETTING);
        ReviewCycle savedCycle = reviewCycleRepository.save(cycle);

        // Transition workflow
        if (cycle.getWorkflowInstance() != null) {
            workflowStateManager.transitionState(cycle.getWorkflowInstance().getId(), STATE_GOAL_SETTING, "Goal setting phase started");
        }

        // Notify employees about goal setting
        notifyEmployeesAboutGoalSetting(savedCycle);

        log.info("Goal setting phase started for cycle {}", cycleId);
        return mapToResponse(savedCycle);
    }

    /**
     * Close a review cycle.
     */
    public ReviewCycleResponse closeCycle(UUID cycleId) {
        log.info("Closing review cycle: {}", cycleId);

        ReviewCycle cycle = reviewCycleRepository
            .findById(cycleId)
            .orElseThrow(() -> EntityNotFoundException.create("ReviewCycle", cycleId));

        // Validate current phase - can close from GOAL_SETTING or FEEDBACK_DELIVERY
        if (cycle.getPhase() != ReviewCyclePhase.GOAL_SETTING && cycle.getPhase() != ReviewCyclePhase.FEEDBACK_DELIVERY) {
            throw new BadRequestAlertException(
                "Can only close cycle from GOAL_SETTING or FEEDBACK_DELIVERY phase. Current phase: " + cycle.getPhase(),
                "reviewCycle",
                "invalidphase"
            );
        }

        // Update phase and status
        cycle.setPhase(ReviewCyclePhase.COMPLETED);
        cycle.setActive(false);
        ReviewCycle savedCycle = reviewCycleRepository.save(cycle);

        // Complete workflow
        if (cycle.getWorkflowInstance() != null) {
            workflowStateManager.completeWorkflow(cycle.getWorkflowInstance().getId(), "Review cycle completed successfully");
        }

        // Notify stakeholders about cycle completion
        notifyAboutCycleCompletion(savedCycle);

        log.info("Review cycle {} closed successfully", cycleId);
        return mapToResponse(savedCycle);
    }

    /**
     * Archive a completed review cycle.
     */
    public ReviewCycleResponse archiveCycle(UUID cycleId) {
        log.info("Archiving review cycle: {}", cycleId);

        ReviewCycle cycle = reviewCycleRepository
            .findById(cycleId)
            .orElseThrow(() -> EntityNotFoundException.create("ReviewCycle", cycleId));

        if (cycle.getPhase() != ReviewCyclePhase.COMPLETED) {
            throw new BadRequestAlertException(
                "Can only archive COMPLETED cycles. Current phase: " + cycle.getPhase(),
                "reviewCycle",
                "invalidphase"
            );
        }

        cycle.setPhase(ReviewCyclePhase.ARCHIVED);
        ReviewCycle savedCycle = reviewCycleRepository.save(cycle);

        log.info("Review cycle {} archived", cycleId);
        return mapToResponse(savedCycle);
    }

    /**
     * Submit self-assessment.
     */
    public void submitSelfAssessment(UUID reviewId, SelfAssessmentRequest request) {
        log.info("Submitting self-assessment for review: {}", reviewId);

        // Find the performance review
        PerformanceReview review = performanceReviewRepository
            .findById(reviewId)
            .orElseThrow(() -> EntityNotFoundException.create("PerformanceReview", reviewId));

        // Validate employee ID matches
        if (!review.getEmployee().getId().equals(request.employeeId())) {
            throw new BadRequestAlertException("Employee ID does not match the review", "selfAssessment", "employeemismatch");
        }

        // Build self-assessment comments
        StringBuilder comments = new StringBuilder();
        comments.append("=== SELF ASSESSMENT ===\n\n");
        comments.append("Self Rating: ").append(request.selfRating()).append("/5\n\n");

        if (request.achievements() != null && !request.achievements().isBlank()) {
            comments.append("Achievements:\n").append(request.achievements()).append("\n\n");
        }

        if (request.keyAccomplishments() != null && !request.keyAccomplishments().isEmpty()) {
            comments.append("Key Accomplishments:\n");
            for (String accomplishment : request.keyAccomplishments()) {
                comments.append("- ").append(accomplishment).append("\n");
            }
            comments.append("\n");
        }

        if (request.challenges() != null && !request.challenges().isBlank()) {
            comments.append("Challenges:\n").append(request.challenges()).append("\n\n");
        }

        if (request.developmentGoals() != null && !request.developmentGoals().isBlank()) {
            comments.append("Development Goals:\n").append(request.developmentGoals()).append("\n\n");
        }

        if (request.additionalComments() != null && !request.additionalComments().isBlank()) {
            comments.append("Additional Comments:\n").append(request.additionalComments()).append("\n");
        }

        // Update the review with self-assessment
        String existingComments = review.getComments() != null ? review.getComments() : "";
        review.setComments(existingComments + comments);
        review.setRating(request.selfRating()); // Set initial self-rating

        performanceReviewRepository.save(review);

        // Update cycle progress - find the cycle this review belongs to
        updateCycleSelfAssessmentProgress(review);

        // Notify manager that self-assessment is complete
        if (review.getEmployee().getManager() != null) {
            notificationService.notifyTaskAssignment(
                review.getEmployee().getManager().getId(),
                "Self-Assessment Submitted",
                review.getEmployee().getFirstName() +
                " " +
                review.getEmployee().getLastName() +
                " has completed their self-assessment and is ready for your review.",
                reviewId,
                "PerformanceReview"
            );
        }

        log.info("Self-assessment submitted for review {} by employee {}", reviewId, request.employeeId());
    }

    /**
     * Submit manager review.
     */
    public void submitManagerReview(UUID reviewId, ManagerReviewRequest request) {
        log.info("Submitting manager review for review: {}", reviewId);

        // Find the performance review
        PerformanceReview review = performanceReviewRepository
            .findById(reviewId)
            .orElseThrow(() -> EntityNotFoundException.create("PerformanceReview", reviewId));

        // Validate employee ID matches
        if (!review.getEmployee().getId().equals(request.employeeId())) {
            throw new BadRequestAlertException("Employee ID does not match the review", "managerReview", "employeemismatch");
        }

        // Build manager review comments
        StringBuilder comments = new StringBuilder();
        comments.append("\n\n=== MANAGER REVIEW ===\n\n");
        comments.append("Manager Rating: ").append(request.rating()).append("/5\n\n");

        if (request.strengths() != null && !request.strengths().isBlank()) {
            comments.append("Strengths:\n").append(request.strengths()).append("\n\n");
        }

        if (request.areasForImprovement() != null && !request.areasForImprovement().isBlank()) {
            comments.append("Areas for Improvement:\n").append(request.areasForImprovement()).append("\n\n");
        }

        if (request.developmentRecommendations() != null && !request.developmentRecommendations().isBlank()) {
            comments.append("Development Recommendations:\n").append(request.developmentRecommendations()).append("\n\n");
        }

        if (request.managerComments() != null && !request.managerComments().isBlank()) {
            comments.append("Manager Comments:\n").append(request.managerComments()).append("\n\n");
        }

        if (request.recommendForPromotion()) {
            comments.append("Promotion Recommendation: YES\n");
            if (request.promotionJustification() != null && !request.promotionJustification().isBlank()) {
                comments.append("Justification: ").append(request.promotionJustification()).append("\n");
            }
        }

        // Update the review with manager assessment
        String existingComments = review.getComments() != null ? review.getComments() : "";
        review.setComments(existingComments + comments);
        review.setRating(request.rating()); // Override with manager's final rating
        review.setReviewDate(LocalDate.now());

        performanceReviewRepository.save(review);

        // Update cycle progress
        updateCycleManagerReviewProgress(review);

        // Notify employee that their review is complete
        notificationService.notifyWorkflowCompleted(
            review.getEmployee().getId(),
            "Manager Review Completed",
            "Your manager has completed your performance review. Feedback will be delivered soon.",
            reviewId,
            "PerformanceReview"
        );

        log.info("Manager review submitted for review {} for employee {}", reviewId, request.employeeId());
    }

    /**
     * Record feedback meeting.
     */
    public void recordFeedbackMeeting(UUID cycleId, UUID employeeId, String notes) {
        log.info("Recording feedback meeting for cycle {} employee {}", cycleId, employeeId);

        ReviewCycle cycle = reviewCycleRepository
            .findById(cycleId)
            .orElseThrow(() -> EntityNotFoundException.create("ReviewCycle", cycleId));

        // Validate phase
        if (cycle.getPhase() != ReviewCyclePhase.FEEDBACK_DELIVERY) {
            throw new BadRequestAlertException(
                "Feedback can only be recorded during FEEDBACK_DELIVERY phase",
                "reviewCycle",
                "invalidphase"
            );
        }

        // Validate employee exists and belongs to a department in the cycle
        Employee employee = employeeRepository
            .findById(employeeId)
            .orElseThrow(() -> EntityNotFoundException.create("Employee", employeeId));

        // Validate employee is part of the cycle (if departments are specified)
        if (cycle.getDepartmentIds() != null && !cycle.getDepartmentIds().isEmpty()) {
            if (employee.getDepartment() == null || !cycle.getDepartmentIds().contains(employee.getDepartment().getId())) {
                throw new BadRequestAlertException(
                    "Employee does not belong to a department included in this review cycle",
                    "reviewCycle",
                    "employeenotincycle"
                );
            }
        }

        // Update delivered feedbacks count
        cycle.setDeliveredFeedbacks(cycle.getDeliveredFeedbacks() + 1);
        reviewCycleRepository.save(cycle);

        // Notify employee about completed feedback meeting
        notificationService.notifyWorkflowCompleted(
            employeeId,
            "Feedback Meeting Completed",
            "Your performance review feedback meeting has been completed. " + (notes != null ? "Notes: " + notes : ""),
            cycleId,
            "ReviewCycle"
        );

        log.info("Feedback meeting recorded for employee {} in cycle {}", employeeId, cycleId);
    }

    /**
     * Get cycle progress.
     */
    @Transactional(readOnly = true)
    public ReviewCycleResponse.CycleProgress getCycleProgress(UUID cycleId) {
        log.info("Getting cycle progress for: {}", cycleId);

        ReviewCycle cycle = reviewCycleRepository
            .findById(cycleId)
            .orElseThrow(() -> EntityNotFoundException.create("ReviewCycle", cycleId));

        List<ReviewCycleResponse.DepartmentProgress> departmentProgress = calculateDepartmentProgress(cycle);

        return new ReviewCycleResponse.CycleProgress(
            cycle.getTotalEmployees() != null ? cycle.getTotalEmployees() : 0,
            cycle.getCompletedSelfAssessments() != null ? cycle.getCompletedSelfAssessments() : 0,
            cycle.getCompletedManagerReviews() != null ? cycle.getCompletedManagerReviews() : 0,
            cycle.getDeliveredFeedbacks() != null ? cycle.getDeliveredFeedbacks() : 0,
            cycle.getCompletionPercentage(),
            departmentProgress
        );
    }

    /**
     * Send phase reminders.
     */
    public void sendPhaseReminders(UUID cycleId) {
        log.info("Sending phase reminders for cycle: {}", cycleId);

        ReviewCycle cycle = reviewCycleRepository
            .findById(cycleId)
            .orElseThrow(() -> EntityNotFoundException.create("ReviewCycle", cycleId));

        switch (cycle.getPhase()) {
            case SELF_ASSESSMENT -> sendSelfAssessmentReminders(cycle);
            case MANAGER_REVIEW -> sendManagerReviewReminders(cycle);
            case FEEDBACK_DELIVERY -> sendFeedbackDeliveryReminders(cycle);
            default -> log.warn("No reminders configured for phase {}", cycle.getPhase());
        }

        log.info("Phase reminders sent for cycle {}", cycleId);
    }

    /**
     * Get review cycle by ID.
     */
    @Transactional(readOnly = true)
    public ReviewCycleResponse getReviewCycle(UUID cycleId) {
        ReviewCycle cycle = reviewCycleRepository
            .findById(cycleId)
            .orElseThrow(() -> EntityNotFoundException.create("ReviewCycle", cycleId));
        return mapToResponse(cycle);
    }

    /**
     * Get all review cycles.
     */
    @Transactional(readOnly = true)
    public Page<ReviewCycleResponse> getAllReviewCycles(Pageable pageable) {
        return reviewCycleRepository.findAll(pageable).map(this::mapToResponse);
    }

    /**
     * Get current active cycle for a date.
     */
    @Transactional(readOnly = true)
    public Optional<ReviewCycleResponse> getCurrentCycle(LocalDate date) {
        return reviewCycleRepository.findCurrentActiveCycle(date).map(this::mapToResponse);
    }

    /**
     * Get cycles by department.
     */
    @Transactional(readOnly = true)
    public List<ReviewCycleResponse> getCyclesByDepartment(UUID departmentId) {
        return reviewCycleRepository.findByDepartmentId(departmentId).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    /**
     * Get cycles with approaching deadlines.
     */
    @Transactional(readOnly = true)
    public List<ReviewCycleResponse> getCyclesWithApproachingDeadlines(int daysAhead) {
        LocalDate deadline = LocalDate.now().plusDays(daysAhead);
        return reviewCycleRepository
            .findCyclesWithApproachingDeadlines(deadline)
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    // ==================== Private Helper Methods ====================

    private void validateCycleRequest(InitiateReviewCycleRequest request) {
        // Validate date ranges
        if (request.reviewPeriodEnd().isBefore(request.reviewPeriodStart())) {
            throw new BadRequestAlertException("Review period end must be after start", "reviewCycle", "invaliddaterange");
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new BadRequestAlertException("Cycle end date must be after start date", "reviewCycle", "invaliddaterange");
        }

        // Validate deadlines are within cycle dates
        if (request.selfAssessmentDeadline() != null) {
            if (
                request.selfAssessmentDeadline().isBefore(request.startDate()) ||
                request.selfAssessmentDeadline().isAfter(request.endDate())
            ) {
                throw new BadRequestAlertException("Self-assessment deadline must be within cycle dates", "reviewCycle", "invaliddeadline");
            }
        }

        if (request.managerReviewDeadline() != null && request.selfAssessmentDeadline() != null) {
            if (request.managerReviewDeadline().isBefore(request.selfAssessmentDeadline())) {
                throw new BadRequestAlertException(
                    "Manager review deadline must be after self-assessment deadline",
                    "reviewCycle",
                    "invaliddeadline"
                );
            }
        }
    }

    private int countEmployeesInDepartments(Set<UUID> departmentIds) {
        if (departmentIds == null || departmentIds.isEmpty()) {
            return (int) employeeRepository.count();
        }

        // Use specification to count employees by department
        Specification<Employee> spec = (root, query, cb) -> root.get("department").get("id").in(departmentIds);

        return (int) employeeRepository.count(spec);
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

    private void notifyEmployeesAboutSelfAssessment(ReviewCycle cycle) {
        List<Employee> employees = getEmployeesInCycle(cycle);
        for (Employee employee : employees) {
            notificationService.notifyTaskAssignment(
                employee.getId(),
                "Self-Assessment Required",
                "The " +
                cycle.getName() +
                " performance review cycle has started. " +
                "Please complete your self-assessment by " +
                cycle.getSelfAssessmentDeadline() +
                ".",
                cycle.getId(),
                "ReviewCycle"
            );
        }
        log.debug("Sent self-assessment notifications to {} employees", employees.size());
    }

    private void notifyManagersAboutReviews(ReviewCycle cycle) {
        Set<Employee> managers = new HashSet<>();
        List<Employee> employees = getEmployeesInCycle(cycle);
        for (Employee employee : employees) {
            if (employee.getManager() != null) {
                managers.add(employee.getManager());
            }
        }

        for (Employee manager : managers) {
            long directReportsCount = employees
                .stream()
                .filter(e -> e.getManager() != null && e.getManager().getId().equals(manager.getId()))
                .count();

            notificationService.notifyTaskAssignment(
                manager.getId(),
                "Manager Reviews Required",
                "The manager review phase has started for " +
                cycle.getName() +
                ". " +
                "You have " +
                directReportsCount +
                " employee(s) to review by " +
                cycle.getManagerReviewDeadline() +
                ".",
                cycle.getId(),
                "ReviewCycle"
            );
        }
        log.debug("Sent manager review notifications to {} managers", managers.size());
    }

    private void notifyAboutCalibration(ReviewCycle cycle) {
        log.debug("Calibration phase notification for cycle {}", cycle.getId());
        // Calibration typically involves HR and leadership - notifications would go to those roles
    }

    private void notifyManagersAboutFeedbackDelivery(ReviewCycle cycle) {
        Set<Employee> managers = new HashSet<>();
        List<Employee> employees = getEmployeesInCycle(cycle);
        for (Employee employee : employees) {
            if (employee.getManager() != null) {
                managers.add(employee.getManager());
            }
        }

        for (Employee manager : managers) {
            notificationService.notifyTaskAssignment(
                manager.getId(),
                "Feedback Meetings Required",
                "Please schedule feedback meetings with your team members for " +
                cycle.getName() +
                ". " +
                "Complete by " +
                cycle.getFeedbackDeadline() +
                ".",
                cycle.getId(),
                "ReviewCycle"
            );
        }
        log.debug("Sent feedback delivery notifications to {} managers", managers.size());
    }

    private void notifyEmployeesAboutGoalSetting(ReviewCycle cycle) {
        List<Employee> employees = getEmployeesInCycle(cycle);
        for (Employee employee : employees) {
            notificationService.notifyTaskAssignment(
                employee.getId(),
                "Goal Setting Phase",
                "The goal setting phase has started for " +
                cycle.getName() +
                ". " +
                "Please work with your manager to set goals for the upcoming period.",
                cycle.getId(),
                "ReviewCycle"
            );
        }
        log.debug("Sent goal setting notifications to {} employees", employees.size());
    }

    private void notifyAboutCycleCompletion(ReviewCycle cycle) {
        List<Employee> employees = getEmployeesInCycle(cycle);
        for (Employee employee : employees) {
            notificationService.notifyWorkflowCompleted(
                employee.getId(),
                "Performance Review Cycle Completed",
                "The " + cycle.getName() + " performance review cycle has been completed.",
                cycle.getId(),
                "ReviewCycle"
            );
        }
        log.debug("Sent completion notifications to {} employees", employees.size());
    }

    private List<Employee> getEmployeesInCycle(ReviewCycle cycle) {
        if (cycle.getDepartmentIds() == null || cycle.getDepartmentIds().isEmpty()) {
            return employeeRepository.findAll();
        }

        Specification<Employee> spec = (root, query, cb) -> root.get("department").get("id").in(cycle.getDepartmentIds());

        return employeeRepository.findAll(spec);
    }

    private void updateCycleSelfAssessmentProgress(PerformanceReview review) {
        // Find the active cycle for this review period
        Optional<ReviewCycle> activeCycle = reviewCycleRepository.findCurrentActiveCycle(review.getReviewDate());
        activeCycle.ifPresent(cycle -> {
            if (cycle.getPhase() == ReviewCyclePhase.SELF_ASSESSMENT) {
                cycle.setCompletedSelfAssessments(cycle.getCompletedSelfAssessments() + 1);
                reviewCycleRepository.save(cycle);
            }
        });
    }

    private void updateCycleManagerReviewProgress(PerformanceReview review) {
        Optional<ReviewCycle> activeCycle = reviewCycleRepository.findCurrentActiveCycle(review.getReviewDate());
        activeCycle.ifPresent(cycle -> {
            if (cycle.getPhase() == ReviewCyclePhase.MANAGER_REVIEW) {
                cycle.setCompletedManagerReviews(cycle.getCompletedManagerReviews() + 1);
                reviewCycleRepository.save(cycle);
            }
        });
    }

    private List<ReviewCycleResponse.DepartmentProgress> calculateDepartmentProgress(ReviewCycle cycle) {
        List<ReviewCycleResponse.DepartmentProgress> progressList = new ArrayList<>();

        for (UUID deptId : cycle.getDepartmentIds()) {
            Optional<Department> deptOpt = departmentRepository.findById(deptId);
            if (deptOpt.isPresent()) {
                Department dept = deptOpt.get();

                Specification<Employee> spec = (root, query, cb) -> cb.equal(root.get("department").get("id"), deptId);
                int totalInDept = (int) employeeRepository.count(spec);

                // Calculate completed reviews for this department (simplified)
                int completedInDept = 0; // Would need to track at department level

                int percentage = totalInDept > 0 ? (completedInDept * 100) / totalInDept : 0;

                progressList.add(
                    new ReviewCycleResponse.DepartmentProgress(deptId, dept.getName(), totalInDept, completedInDept, percentage)
                );
            }
        }

        return progressList;
    }

    private void sendSelfAssessmentReminders(ReviewCycle cycle) {
        List<Employee> employees = getEmployeesInCycle(cycle);
        Instant deadline = cycle.getSelfAssessmentDeadline().atStartOfDay(ZoneId.systemDefault()).toInstant();

        for (Employee employee : employees) {
            notificationService.notifyDeadlineApproaching(
                employee.getId(),
                "Self-Assessment Reminder",
                "Your self-assessment for " + cycle.getName() + " is due soon.",
                cycle.getId(),
                "ReviewCycle",
                deadline
            );
        }
    }

    private void sendManagerReviewReminders(ReviewCycle cycle) {
        Set<Employee> managers = new HashSet<>();
        List<Employee> employees = getEmployeesInCycle(cycle);
        for (Employee employee : employees) {
            if (employee.getManager() != null) {
                managers.add(employee.getManager());
            }
        }

        Instant deadline = cycle.getManagerReviewDeadline().atStartOfDay(ZoneId.systemDefault()).toInstant();

        for (Employee manager : managers) {
            notificationService.notifyDeadlineApproaching(
                manager.getId(),
                "Manager Review Reminder",
                "Manager reviews for " + cycle.getName() + " are due soon.",
                cycle.getId(),
                "ReviewCycle",
                deadline
            );
        }
    }

    private void sendFeedbackDeliveryReminders(ReviewCycle cycle) {
        Set<Employee> managers = new HashSet<>();
        List<Employee> employees = getEmployeesInCycle(cycle);
        for (Employee employee : employees) {
            if (employee.getManager() != null) {
                managers.add(employee.getManager());
            }
        }

        Instant deadline = cycle.getFeedbackDeadline().atStartOfDay(ZoneId.systemDefault()).toInstant();

        for (Employee manager : managers) {
            notificationService.notifyDeadlineApproaching(
                manager.getId(),
                "Feedback Delivery Reminder",
                "Feedback meetings for " + cycle.getName() + " must be completed soon.",
                cycle.getId(),
                "ReviewCycle",
                deadline
            );
        }
    }

    private ReviewCycleResponse mapToResponse(ReviewCycle cycle) {
        ReviewCycleResponse.PhaseDeadlines deadlines = new ReviewCycleResponse.PhaseDeadlines(
            cycle.getSelfAssessmentDeadline(),
            cycle.getManagerReviewDeadline(),
            cycle.getCalibrationDeadline(),
            cycle.getFeedbackDeadline()
        );

        List<ReviewCycleResponse.DepartmentProgress> departmentProgress = calculateDepartmentProgress(cycle);

        ReviewCycleResponse.CycleProgress progress = new ReviewCycleResponse.CycleProgress(
            cycle.getTotalEmployees() != null ? cycle.getTotalEmployees() : 0,
            cycle.getCompletedSelfAssessments() != null ? cycle.getCompletedSelfAssessments() : 0,
            cycle.getCompletedManagerReviews() != null ? cycle.getCompletedManagerReviews() : 0,
            cycle.getDeliveredFeedbacks() != null ? cycle.getDeliveredFeedbacks() : 0,
            cycle.getCompletionPercentage(),
            departmentProgress
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
            deadlines,
            progress,
            cycle.getDepartmentIds(),
            cycle.getActive() != null && cycle.getActive(),
            cycle.getCreatedDate(),
            cycle.getLastModifiedDate()
        );
    }
}
