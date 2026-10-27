package com.humano.service.hr.workflow;

import com.humano.domain.enumeration.hr.ImprovementPlanOutcome;
import com.humano.domain.enumeration.hr.ImprovementPlanStatus;
import com.humano.domain.enumeration.hr.PerformanceReviewStatus;
import com.humano.domain.hr.ImprovementPlan;
import com.humano.domain.hr.ImprovementPlanCheckIn;
import com.humano.domain.hr.ImprovementPlanObjective;
import com.humano.domain.hr.PerformanceReview;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.workflow.requests.AcknowledgeImprovementPlanRequest;
import com.humano.dto.hr.workflow.requests.CloseImprovementPlanRequest;
import com.humano.dto.hr.workflow.requests.ExtendImprovementPlanRequest;
import com.humano.dto.hr.workflow.requests.ImprovementPlanCheckInRequest;
import com.humano.dto.hr.workflow.requests.ImprovementPlanRequest;
import com.humano.dto.hr.workflow.responses.ImprovementPlanResponse;
import com.humano.dto.hr.workflow.responses.ReviewImprovementPlanStatus;
import com.humano.repository.hr.ImprovementPlanCheckInRepository;
import com.humano.repository.hr.ImprovementPlanRepository;
import com.humano.repository.hr.PerformanceReviewRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.BadRequestAlertException;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.hr.workflow.infrastructure.NotificationOrchestrationService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Performance improvement plans.
 *
 * <p>A plan is started — never automatically — by the employee's direct manager or HR with
 * {@code MANAGE_PERFORMANCE_REVIEWS}, from a shared cycle review whose final rating is 2 or lower;
 * an employee has at most one open plan. It runs for 30 to 180 days and may be extended once, by up to
 * 90 days, with a reason.
 *
 * <p>Flow: a <b>draft</b> (private to the manager side, editable, deletable) is <b>shared</b> with the
 * employee, who <b>acknowledges</b> receiving it — optionally commenting, which never blocks the plan.
 * The manager side records <b>check-ins</b> (append-only) and finally the <b>outcome</b>: successful,
 * unsuccessful (only once the end date is reached) or cancelled. The outcome is a record only: it
 * never changes the employee's status or starts an offboarding.
 *
 * <p>Who sees a plan is narrower than for reviews: the employee (once shared), their direct manager,
 * whoever runs it and HR with {@code MANAGE_PERFORMANCE_REVIEWS} — not cycle reviewers, colleagues
 * giving feedback or sign-off approvers. Nobody runs their own plan.
 */
@Service
@Transactional("tenantTransactionManager")
public class ImprovementPlanService {

    private static final Logger log = LoggerFactory.getLogger(ImprovementPlanService.class);
    private static final String ENTITY_NAME = "improvementPlan";
    private static final String ENTITY_TYPE = "ImprovementPlan";

    static final int MAX_RATING = 2;
    static final int MIN_DAYS = 30;
    static final int MAX_DAYS = 180;
    static final int MAX_EXTENSION_DAYS = 90;
    private static final Set<ImprovementPlanStatus> OPEN = EnumSet.of(ImprovementPlanStatus.DRAFT, ImprovementPlanStatus.ACTIVE);

    /** The signed-in user and whether they manage performance reviews (HR). */
    public record Actor(UUID id, boolean hrManage) {}

    private final ImprovementPlanRepository planRepository;
    private final ImprovementPlanCheckInRepository checkInRepository;
    private final PerformanceReviewRepository reviewRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationOrchestrationService notificationService;

    public ImprovementPlanService(
        ImprovementPlanRepository planRepository,
        ImprovementPlanCheckInRepository checkInRepository,
        PerformanceReviewRepository reviewRepository,
        EmployeeRepository employeeRepository,
        NotificationOrchestrationService notificationService
    ) {
        this.planRepository = planRepository;
        this.checkInRepository = checkInRepository;
        this.reviewRepository = reviewRepository;
        this.employeeRepository = employeeRepository;
        this.notificationService = notificationService;
    }

    // ---------------------------------------------------------------- reads

    /** The signed-in employee's plans once shared with them, newest first. */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<ImprovementPlanResponse> mine(Actor actor) {
        return planRepository
            .findByEmployeeIdAndStatusInOrderByCreatedDateDesc(
                actor.id(),
                EnumSet.of(ImprovementPlanStatus.ACTIVE, ImprovementPlanStatus.CLOSED)
            )
            .stream()
            .map(p -> toResponse(p, actor, false))
            .toList();
    }

    /** The plans the signed-in user runs: their direct reports' and the ones they started; every plan for HR. */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<ImprovementPlanResponse> managed(Actor actor) {
        List<ImprovementPlan> plans = actor.hrManage()
            ? planRepository.findAllByOrderByCreatedDateDesc()
            : planRepository.findManagedBy(actor.id());
        return plans.stream().filter(p -> !isEmployee(p, actor)).map(p -> toResponse(p, actor, false)).toList();
    }

    /** One plan with its objectives and check-ins. */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public ImprovementPlanResponse get(UUID planId, Actor actor) {
        ImprovementPlan plan = findPlan(planId);
        requireRead(plan, actor);
        return toResponse(plan, actor, true);
    }

    /** Whether the signed-in user may start a plan from a review, and the latest plan started from it. */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public ReviewImprovementPlanStatus forReview(UUID reviewId, Actor actor) {
        PerformanceReview review = findReview(reviewId);
        ImprovementPlan latest = planRepository
            .findByReviewIdOrderByCreatedDateDesc(reviewId)
            .stream()
            .filter(p -> canRead(p, actor))
            .findFirst()
            .orElse(null);
        return new ReviewImprovementPlanStatus(
            startProblem(review, actor) == null,
            latest != null ? latest.getId() : null,
            latest != null ? latest.getStatus() : null
        );
    }

    // ---------------------------------------------------------------- changes

    /** A new plan, as a draft, from a shared review rated 2 or lower. */
    public ImprovementPlanResponse start(UUID reviewId, ImprovementPlanRequest request, Actor actor) {
        PerformanceReview review = findReview(reviewId);
        String problem = startProblem(review, actor);
        if (problem != null) {
            if (problem.startsWith("!")) throw new AccessDeniedException(problem.substring(1));
            throw new BadRequestAlertException(problem, ENTITY_NAME, "cannotstart");
        }
        validate(request);

        ImprovementPlan plan = new ImprovementPlan();
        plan.setEmployee(review.getEmployee());
        plan.setReview(review);
        plan.setOwner(employeeRepository.findById(actor.id()).orElse(null));
        plan.setStatus(ImprovementPlanStatus.DRAFT);
        apply(plan, request);
        plan = planRepository.save(plan);
        log.info("Improvement plan {} drafted for employee {} from review {}", plan.getId(), review.getEmployee().getId(), reviewId);
        return toResponse(plan, actor, true);
    }

    /** Change a draft. */
    public ImprovementPlanResponse update(UUID planId, ImprovementPlanRequest request, Actor actor) {
        ImprovementPlan plan = findPlan(planId);
        requireManage(plan, actor);
        requireStatus(plan, ImprovementPlanStatus.DRAFT, "Only a draft can be changed; record a check-in or extend the plan instead");
        validate(request);
        apply(plan, request);
        return toResponse(planRepository.save(plan), actor, true);
    }

    /** Delete a draft never shared with the employee. */
    public void delete(UUID planId, Actor actor) {
        ImprovementPlan plan = findPlan(planId);
        requireManage(plan, actor);
        requireStatus(plan, ImprovementPlanStatus.DRAFT, "A shared plan is closed with an outcome, not deleted");
        planRepository.delete(plan);
        log.info("Improvement plan draft {} deleted", planId);
    }

    /** Share the draft with the employee; it runs from now until its end date. */
    public ImprovementPlanResponse activate(UUID planId, Actor actor) {
        ImprovementPlan plan = findPlan(planId);
        requireManage(plan, actor);
        requireStatus(plan, ImprovementPlanStatus.DRAFT, "This plan was already shared");
        if (plan.getObjectives().isEmpty()) {
            throw new BadRequestAlertException("Add at least one objective", ENTITY_NAME, "noobjectives");
        }
        plan.setStatus(ImprovementPlanStatus.ACTIVE);
        plan.setActivatedAt(Instant.now());
        plan = planRepository.save(plan);
        notificationService.notifyTaskAssignment(
            plan.getEmployee().getId(),
            "Improvement Plan",
            "An improvement plan was shared with you. Please read it and confirm you received it.",
            plan.getId(),
            ENTITY_TYPE
        );
        log.info("Improvement plan {} shared with employee {}", planId, plan.getEmployee().getId());
        return toResponse(plan, actor, true);
    }

    /** The employee confirms they received the plan — not that they agree — with an optional comment. */
    public ImprovementPlanResponse acknowledge(UUID planId, AcknowledgeImprovementPlanRequest request, Actor actor) {
        ImprovementPlan plan = findPlan(planId);
        if (!isEmployee(plan, actor)) {
            throw new AccessDeniedException("Only the employee acknowledges their plan");
        }
        requireStatus(plan, ImprovementPlanStatus.ACTIVE, "Only a running plan is acknowledged");
        if (plan.getAcknowledgedAt() != null) {
            throw new BadRequestAlertException("You already acknowledged this plan", ENTITY_NAME, "acknowledged");
        }
        plan.setAcknowledgedAt(Instant.now());
        plan.setEmployeeComment(blankToNull(request != null ? request.comment() : null));
        plan = planRepository.save(plan);
        Employee runner = plan.getOwner() != null ? plan.getOwner() : plan.getEmployee().getManager();
        if (runner != null) {
            notificationService.notifyTaskCompleted(
                runner.getId(),
                "Improvement Plan Acknowledged",
                fullName(plan.getEmployee()) +
                " acknowledged their improvement plan" +
                (plan.getEmployeeComment() != null ? ", with a comment" : ""),
                plan.getId(),
                ENTITY_TYPE
            );
        }
        return toResponse(plan, actor, true);
    }

    /** Record a progress review; the employee sees it. */
    public ImprovementPlanResponse checkIn(UUID planId, ImprovementPlanCheckInRequest request, Actor actor) {
        ImprovementPlan plan = findPlan(planId);
        requireManage(plan, actor);
        requireStatus(plan, ImprovementPlanStatus.ACTIVE, "Check-ins are recorded on a running plan");
        checkInRepository.save(
            new ImprovementPlanCheckIn(
                plan,
                employeeRepository.findById(actor.id()).orElse(null),
                request.progress(),
                request.note().trim(),
                Instant.now()
            )
        );
        notificationService.notifyTaskCompleted(
            plan.getEmployee().getId(),
            "Improvement Plan Check-in",
            "A check-in was recorded on your improvement plan.",
            plan.getId(),
            ENTITY_TYPE
        );
        return toResponse(plan, actor, true);
    }

    /** More time, once: a later end date, at most 90 days after the current one, with the reason. */
    public ImprovementPlanResponse extend(UUID planId, ExtendImprovementPlanRequest request, Actor actor) {
        ImprovementPlan plan = findPlan(planId);
        requireManage(plan, actor);
        requireStatus(plan, ImprovementPlanStatus.ACTIVE, "Only a running plan is extended");
        if (plan.getOriginalEndDate() != null) {
            throw new BadRequestAlertException("A plan is extended once; close it with an outcome instead", ENTITY_NAME, "extended");
        }
        LocalDate newEnd = request.endDate();
        if (!newEnd.isAfter(plan.getEndDate())) {
            throw new BadRequestAlertException("The new end date must be after the current one", ENTITY_NAME, "enddate");
        }
        if (newEnd.isAfter(plan.getEndDate().plusDays(MAX_EXTENSION_DAYS))) {
            throw new BadRequestAlertException(
                "A plan is extended by at most " + MAX_EXTENSION_DAYS + " days",
                ENTITY_NAME,
                "extensiontoolong"
            );
        }
        plan.setOriginalEndDate(plan.getEndDate());
        plan.setEndDate(newEnd);
        plan.setExtensionReason(request.reason().trim());
        plan = planRepository.save(plan);
        notificationService.notifyTaskCompleted(
            plan.getEmployee().getId(),
            "Improvement Plan Extended",
            "Your improvement plan now runs until " + newEnd + ". " + plan.getExtensionReason(),
            plan.getId(),
            ENTITY_TYPE
        );
        return toResponse(plan, actor, true);
    }

    /**
     * End the plan with its outcome. Unsuccessful only once the end date is reached; successful or
     * cancelled at any time. Only recorded — the employee's status never changes here.
     */
    public ImprovementPlanResponse close(UUID planId, CloseImprovementPlanRequest request, Actor actor) {
        ImprovementPlan plan = findPlan(planId);
        requireManage(plan, actor);
        requireStatus(plan, ImprovementPlanStatus.ACTIVE, "Only a running plan is closed; delete a draft instead");
        if (request.outcome() == ImprovementPlanOutcome.UNSUCCESSFUL && LocalDate.now().isBefore(plan.getEndDate())) {
            throw new BadRequestAlertException(
                "An unsuccessful outcome is recorded once the plan's end date is reached",
                ENTITY_NAME,
                "tooearly"
            );
        }
        plan.setStatus(ImprovementPlanStatus.CLOSED);
        plan.setOutcome(request.outcome());
        plan.setOutcomeNote(request.note().trim());
        plan.setClosedAt(Instant.now());
        plan.setClosedBy(employeeRepository.findById(actor.id()).orElse(null));
        plan = planRepository.save(plan);
        notificationService.notifyWorkflowCompleted(
            plan.getEmployee().getId(),
            "Improvement Plan Closed",
            "Your improvement plan was closed. Open it to read the outcome.",
            plan.getId(),
            ENTITY_TYPE
        );
        log.info("Improvement plan {} closed: {}", planId, request.outcome());
        return toResponse(plan, actor, true);
    }

    // ---------------------------------------------------------------- rules

    private static boolean isEmployee(ImprovementPlan plan, Actor actor) {
        return plan.getEmployee().getId().equals(actor.id());
    }

    private static boolean isDirectManager(Employee employee, Actor actor) {
        return employee.getManager() != null && actor.id().equals(employee.getManager().getId());
    }

    /** The manager side: the direct manager, whoever runs the plan, or HR — never the employee. */
    private static boolean canManage(ImprovementPlan plan, Actor actor) {
        if (isEmployee(plan, actor)) return false;
        boolean runs = plan.getOwner() != null && actor.id().equals(plan.getOwner().getId());
        return runs || isDirectManager(plan.getEmployee(), actor) || actor.hrManage();
    }

    private static boolean canRead(ImprovementPlan plan, Actor actor) {
        return canManage(plan, actor) || (isEmployee(plan, actor) && plan.getStatus() != ImprovementPlanStatus.DRAFT);
    }

    private static void requireManage(ImprovementPlan plan, Actor actor) {
        if (!canManage(plan, actor)) {
            throw new AccessDeniedException("Only the employee's manager or HR runs this plan");
        }
    }

    private static void requireRead(ImprovementPlan plan, Actor actor) {
        if (!canRead(plan, actor)) {
            throw new AccessDeniedException("You can't see this improvement plan");
        }
    }

    private static void requireStatus(ImprovementPlan plan, ImprovementPlanStatus status, String message) {
        if (plan.getStatus() != status) {
            throw new BadRequestAlertException(message, ENTITY_NAME, "status");
        }
    }

    /** Why the actor can't start a plan from the review ({@code !}-prefixed when it's about who they are), or {@code null}. */
    private String startProblem(PerformanceReview review, Actor actor) {
        Employee employee = review.getEmployee();
        if (employee.getId().equals(actor.id())) {
            return "!Nobody starts an improvement plan for themselves";
        }
        if (!isDirectManager(employee, actor) && !actor.hrManage()) {
            return "!Only the employee's manager or HR starts an improvement plan";
        }
        if (review.getStatus() != PerformanceReviewStatus.SHARED) {
            return "An improvement plan starts from a review shared with the employee";
        }
        if (review.getRating() == null || review.getRating() > MAX_RATING) {
            return "An improvement plan starts from a review rated " + MAX_RATING + " or lower";
        }
        if (planRepository.existsByEmployeeIdAndStatusIn(employee.getId(), OPEN)) {
            return "This employee already has an open improvement plan";
        }
        return null;
    }

    private static void validate(ImprovementPlanRequest request) {
        long days = ChronoUnit.DAYS.between(request.startDate(), request.endDate());
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new BadRequestAlertException("A plan runs for " + MIN_DAYS + " to " + MAX_DAYS + " days", ENTITY_NAME, "duration");
        }
        if (request.objectives().isEmpty()) {
            throw new BadRequestAlertException("Add at least one objective", ENTITY_NAME, "noobjectives");
        }
        for (ImprovementPlanRequest.Objective o : request.objectives()) {
            if (o.dueDate() != null && (o.dueDate().isBefore(request.startDate()) || o.dueDate().isAfter(request.endDate()))) {
                throw new BadRequestAlertException("An objective is due within the plan's period", ENTITY_NAME, "objectivedue");
            }
        }
    }

    private static void apply(ImprovementPlan plan, ImprovementPlanRequest request) {
        plan.setConcerns(request.concerns().trim());
        plan.setSupport(blankToNull(request.support()));
        plan.setMeetingCadence(blankToNull(request.meetingCadence()));
        plan.setStartDate(request.startDate());
        plan.setEndDate(request.endDate());
        plan.getObjectives().clear();
        List<ImprovementPlanRequest.Objective> objectives = request.objectives();
        for (int i = 0; i < objectives.size(); i++) {
            ImprovementPlanRequest.Objective o = objectives.get(i);
            plan
                .getObjectives()
                .add(new ImprovementPlanObjective(plan, i, o.description().trim(), blankToNull(o.successMeasure()), o.dueDate()));
        }
    }

    private ImprovementPlan findPlan(UUID planId) {
        return planRepository.findWithDetailsById(planId).orElseThrow(() -> EntityNotFoundException.create("ImprovementPlan", planId));
    }

    private PerformanceReview findReview(UUID reviewId) {
        return reviewRepository
            .findByIdAndCycleIsNotNull(reviewId)
            .orElseThrow(() -> EntityNotFoundException.create("PerformanceReview", reviewId));
    }

    private ImprovementPlanResponse toResponse(ImprovementPlan p, Actor actor, boolean detailed) {
        boolean manage = canManage(p, actor);
        boolean draft = p.getStatus() == ImprovementPlanStatus.DRAFT;
        boolean active = p.getStatus() == ImprovementPlanStatus.ACTIVE;
        ImprovementPlanResponse.Actions can = new ImprovementPlanResponse.Actions(
            manage && draft,
            manage && draft,
            manage && draft,
            manage && active,
            manage && active && p.getOriginalEndDate() == null,
            manage && active,
            isEmployee(p, actor) && active && p.getAcknowledgedAt() == null
        );
        PerformanceReview review = p.getReview();
        List<ImprovementPlanResponse.Objective> objectives = detailed
            ? p
                .getObjectives()
                .stream()
                .map(o -> new ImprovementPlanResponse.Objective(o.getId(), o.getDescription(), o.getSuccessMeasure(), o.getDueDate()))
                .toList()
            : List.of();
        List<ImprovementPlanResponse.CheckIn> checkIns = detailed && p.getId() != null
            ? checkInRepository
                .findByPlanIdOrderByCheckedInAtDescIdDesc(p.getId())
                .stream()
                .map(c ->
                    new ImprovementPlanResponse.CheckIn(
                        c.getId(),
                        c.getProgress(),
                        c.getNote(),
                        c.getAuthor() != null ? fullName(c.getAuthor()) : null,
                        c.getCheckedInAt()
                    )
                )
                .toList()
            : List.of();
        return new ImprovementPlanResponse(
            p.getId(),
            p.getEmployee().getId(),
            fullName(p.getEmployee()),
            p.getEmployee().getJobTitle(),
            p.getOwner() != null ? fullName(p.getOwner()) : null,
            review.getId(),
            review.getCycle() != null ? review.getCycle().getName() : null,
            review.getRating(),
            p.getStatus(),
            p.getConcerns(),
            p.getSupport(),
            p.getMeetingCadence(),
            p.getStartDate(),
            p.getEndDate(),
            p.getOriginalEndDate(),
            p.getExtensionReason(),
            p.getActivatedAt(),
            p.getAcknowledgedAt(),
            p.getEmployeeComment(),
            p.getOutcome(),
            p.getOutcomeNote(),
            p.getClosedAt(),
            p.getClosedBy() != null ? fullName(p.getClosedBy()) : null,
            objectives,
            checkIns,
            can
        );
    }

    private static String fullName(Employee e) {
        return ((e.getFirstName() != null ? e.getFirstName() : "") + " " + (e.getLastName() != null ? e.getLastName() : "")).trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
