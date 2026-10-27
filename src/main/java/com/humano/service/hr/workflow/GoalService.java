package com.humano.service.hr.workflow;

import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.domain.enumeration.hr.GoalStatus;
import com.humano.domain.hr.Goal;
import com.humano.domain.hr.GoalCheckIn;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.workflow.requests.CancelGoalRequest;
import com.humano.dto.hr.workflow.requests.GoalCheckInRequest;
import com.humano.dto.hr.workflow.requests.GoalRequest;
import com.humano.dto.hr.workflow.responses.GoalCheckInResponse;
import com.humano.dto.hr.workflow.responses.GoalResponse;
import com.humano.repository.hr.GoalCheckInRepository;
import com.humano.repository.hr.GoalRepository;
import com.humano.repository.hr.PerformanceReviewRepository;
import com.humano.repository.hr.ReviewGoalRatingRepository;
import com.humano.repository.hr.projection.TeamMemberRow;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.BadRequestAlertException;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.hr.workflow.infrastructure.NotificationOrchestrationService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Employees' goals: what each person aims to achieve, agreed with their manager, with progress
 * check-ins; the review whose period covers a goal's due date rates it.
 *
 * <p>Who may do what follows the reporting line, not a role:
 * <ul>
 *   <li>the <b>employee</b> proposes their own goals (DRAFT), edits or deletes them until agreed,
 *       checks in progress and marks agreed goals complete;</li>
 *   <li>their <b>direct manager</b> — or HR with {@code MANAGE_PERFORMANCE_REVIEWS} — agrees
 *       proposals, sets goals (agreed at once), edits, completes and cancels them (with a reason),
 *       and checks in too. Nobody agrees or cancels their own goal this way;</li>
 *   <li>the employee, their manager, anyone who reviews them in a cycle and HR with
 *       {@code VIEW_PERFORMANCE_REVIEWS} read them.</li>
 * </ul>
 * A goal rated in a submitted review is locked: it can't be edited, cancelled or deleted.
 */
@Service
@Transactional("tenantTransactionManager")
public class GoalService {

    private static final Logger log = LoggerFactory.getLogger(GoalService.class);
    private static final String ENTITY_NAME = "goal";

    /** The signed-in user and their HR rights. */
    public record Actor(UUID id, boolean hrView, boolean hrManage) {}

    private final GoalRepository goalRepository;
    private final GoalCheckInRepository checkInRepository;
    private final ReviewGoalRatingRepository goalRatingRepository;
    private final PerformanceReviewRepository reviewRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationOrchestrationService notificationService;

    public GoalService(
        GoalRepository goalRepository,
        GoalCheckInRepository checkInRepository,
        ReviewGoalRatingRepository goalRatingRepository,
        PerformanceReviewRepository reviewRepository,
        EmployeeRepository employeeRepository,
        NotificationOrchestrationService notificationService
    ) {
        this.goalRepository = goalRepository;
        this.checkInRepository = checkInRepository;
        this.goalRatingRepository = goalRatingRepository;
        this.reviewRepository = reviewRepository;
        this.employeeRepository = employeeRepository;
        this.notificationService = notificationService;
    }

    // ---------------------------------------------------------------- reads

    /** The signed-in employee's goals, soonest due first. */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<GoalResponse> mine(Actor actor) {
        return toResponses(goalRepository.findByEmployeeIdOrderByDueDateAscCreatedDateAsc(actor.id()), actor);
    }

    /** The goals of the signed-in manager's direct reports. */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<GoalResponse> team(Actor actor) {
        return toResponses(goalRepository.findByEmployeeManagerIdOrderByDueDateAscCreatedDateAsc(actor.id()), actor);
    }

    /** The signed-in manager's direct reports still working here; empty for everyone else. */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<TeamMemberRow> teamMembers(Actor actor) {
        return goalRepository.findTeamMembers(actor.id(), EmployeeStatus.WORKING);
    }

    /** Someone's goals, for those who may read them. */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<GoalResponse> ofEmployee(UUID employeeId, Actor actor) {
        Employee employee = findEmployee(employeeId);
        requireRead(employee, actor);
        return toResponses(goalRepository.findByEmployeeIdOrderByDueDateAscCreatedDateAsc(employeeId), actor);
    }

    /** A goal's check-ins, newest first. */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<GoalCheckInResponse> checkIns(UUID goalId, Actor actor) {
        Goal goal = findGoal(goalId);
        requireRead(goal.getEmployee(), actor);
        return checkInRepository
            .findByGoalIdOrderByCheckedInAtDescIdDesc(goalId)
            .stream()
            .map(c ->
                new GoalCheckInResponse(
                    c.getId(),
                    c.getProgress(),
                    c.getComment(),
                    c.getAuthor() != null ? fullName(c.getAuthor()) : null,
                    c.getCheckedInAt()
                )
            )
            .toList();
    }

    // ---------------------------------------------------------------- changes

    /**
     * A new goal: the employee's own proposal (DRAFT, their manager is told), or one their manager or
     * HR sets for them (agreed at once, the employee is told).
     */
    public GoalResponse create(GoalRequest request, Actor actor) {
        UUID employeeId = request.employeeId() != null ? request.employeeId() : actor.id();
        Employee employee = findEmployee(employeeId);
        boolean own = employeeId.equals(actor.id());
        if (!own && !managesAsManagerOrHr(employee, actor)) {
            throw new AccessDeniedException("Only the employee, their manager or HR sets their goals");
        }
        validate(request);

        Goal goal = new Goal();
        goal.setEmployee(employee);
        apply(goal, request);
        if (own) {
            goal.setStatus(GoalStatus.DRAFT);
        } else {
            agree(goal, actor);
        }
        goal = goalRepository.save(goal);

        if (own && employee.getManager() != null) {
            notificationService.notifyTaskAssignment(
                employee.getManager().getId(),
                "Goal Proposed",
                fullName(employee) + " proposed a goal for you to agree: " + goal.getTitle(),
                goal.getId(),
                "Goal"
            );
        } else if (!own) {
            notificationService.notifyTaskAssignment(
                employeeId,
                "New Goal",
                "A goal was set for you: " + goal.getTitle(),
                goal.getId(),
                "Goal"
            );
        }
        log.info("Goal {} created for employee {} ({})", goal.getId(), employeeId, goal.getStatus());
        return toResponse(goal, actor, false);
    }

    /** Change a goal: the employee until it is agreed; their manager or HR while it is open. */
    public GoalResponse update(UUID goalId, GoalRequest request, Actor actor) {
        Goal goal = findGoal(goalId);
        requireUnlocked(goal);
        requireOpen(goal);
        boolean own = isOwner(goal, actor);
        if (own ? goal.getStatus() != GoalStatus.DRAFT : !managesAsManagerOrHr(goal.getEmployee(), actor)) {
            throw new AccessDeniedException(
                own ? "An agreed goal is changed by your manager" : "Only the employee, their manager or HR changes this goal"
            );
        }
        validate(request);
        apply(goal, request);
        return toResponse(goalRepository.save(goal), actor, false);
    }

    /** The manager (or HR) agrees a proposed goal. */
    public GoalResponse agree(UUID goalId, Actor actor) {
        Goal goal = findGoal(goalId);
        requireManagerSide(goal, actor);
        if (goal.getStatus() != GoalStatus.DRAFT) {
            throw new BadRequestAlertException("Only a proposed goal can be agreed", ENTITY_NAME, "notdraft");
        }
        agree(goal, actor);
        goal = goalRepository.save(goal);
        notificationService.notifyWorkflowCompleted(
            goal.getEmployee().getId(),
            "Goal Agreed",
            "Your goal was agreed: " + goal.getTitle(),
            goal.getId(),
            "Goal"
        );
        return toResponse(goal, actor, false);
    }

    /** A progress update by the employee or their manager (or HR) on an agreed goal. */
    public GoalResponse checkIn(UUID goalId, GoalCheckInRequest request, Actor actor) {
        Goal goal = findGoal(goalId);
        requireOwnerOrManagerSide(goal, actor);
        if (goal.getStatus() != GoalStatus.ACTIVE) {
            throw new BadRequestAlertException("Progress is checked in on agreed goals", ENTITY_NAME, "notactive");
        }
        Employee author = employeeRepository.findById(actor.id()).orElse(null);
        checkInRepository.save(new GoalCheckIn(goal, author, request.progress(), blankToNull(request.comment()), Instant.now()));
        goal.setProgress(request.progress());
        return toResponse(goalRepository.save(goal), actor, goalRatingRepository.existsByGoalId(goalId));
    }

    /** The employee or their manager (or HR) marks an agreed goal done. */
    public GoalResponse complete(UUID goalId, Actor actor) {
        Goal goal = findGoal(goalId);
        requireOwnerOrManagerSide(goal, actor);
        if (goal.getStatus() != GoalStatus.ACTIVE) {
            throw new BadRequestAlertException("Only an agreed goal can be completed", ENTITY_NAME, "notactive");
        }
        goal.setStatus(GoalStatus.COMPLETED);
        goal.setCompletedAt(Instant.now());
        return toResponse(goalRepository.save(goal), actor, goalRatingRepository.existsByGoalId(goalId));
    }

    /** The manager (or HR) stops a goal, with the reason; the employee is told. */
    public GoalResponse cancel(UUID goalId, CancelGoalRequest request, Actor actor) {
        Goal goal = findGoal(goalId);
        requireManagerSide(goal, actor);
        requireUnlocked(goal);
        requireOpen(goal);
        goal.setStatus(GoalStatus.CANCELLED);
        goal.setCancelReason(request.reason().trim());
        goal = goalRepository.save(goal);
        notificationService.notifyTaskCompleted(
            goal.getEmployee().getId(),
            "Goal Cancelled",
            "Your goal was cancelled: " + goal.getTitle() + ". " + goal.getCancelReason(),
            goal.getId(),
            "Goal"
        );
        return toResponse(goal, actor, false);
    }

    /** Delete a goal never agreed: the employee's own proposal, or by their manager or HR. */
    public void delete(UUID goalId, Actor actor) {
        Goal goal = findGoal(goalId);
        requireOwnerOrManagerSide(goal, actor);
        requireUnlocked(goal);
        if (goal.getStatus() != GoalStatus.DRAFT) {
            throw new BadRequestAlertException("An agreed goal is cancelled, not deleted", ENTITY_NAME, "notdraft");
        }
        goalRepository.delete(goal);
        log.info("Goal {} deleted", goalId);
    }

    // ---------------------------------------------------------------- rules

    private static boolean isOwner(Goal goal, Actor actor) {
        return goal.getEmployee().getId().equals(actor.id());
    }

    private static boolean isManagerOf(Employee employee, Actor actor) {
        return employee.getManager() != null && actor.id().equals(employee.getManager().getId());
    }

    /** The direct manager or HR — never the employee themselves. */
    private static boolean managesAsManagerOrHr(Employee employee, Actor actor) {
        return !employee.getId().equals(actor.id()) && (isManagerOf(employee, actor) || actor.hrManage());
    }

    private static void requireManagerSide(Goal goal, Actor actor) {
        if (!managesAsManagerOrHr(goal.getEmployee(), actor)) {
            throw new AccessDeniedException("Only the employee's manager or HR does this");
        }
    }

    private static void requireOwnerOrManagerSide(Goal goal, Actor actor) {
        if (!isOwner(goal, actor) && !managesAsManagerOrHr(goal.getEmployee(), actor)) {
            throw new AccessDeniedException("Only the employee, their manager or HR does this");
        }
    }

    private void requireRead(Employee employee, Actor actor) {
        boolean allowed =
            employee.getId().equals(actor.id()) ||
            isManagerOf(employee, actor) ||
            actor.hrView() ||
            actor.hrManage() ||
            reviewRepository.existsByEmployeeIdAndReviewerIdAndCycleIsNotNull(employee.getId(), actor.id());
        if (!allowed) {
            throw new AccessDeniedException("You can't see this person's goals");
        }
    }

    private void requireUnlocked(Goal goal) {
        if (goalRatingRepository.existsByGoalId(goal.getId())) {
            throw new BadRequestAlertException("This goal was rated in a review and can no longer change", ENTITY_NAME, "locked");
        }
    }

    private static void requireOpen(Goal goal) {
        if (goal.getStatus() == GoalStatus.COMPLETED || goal.getStatus() == GoalStatus.CANCELLED) {
            throw new BadRequestAlertException("A completed or cancelled goal can no longer change", ENTITY_NAME, "closed");
        }
    }

    private static void validate(GoalRequest request) {
        if (request.dueDate().isBefore(request.startDate())) {
            throw new BadRequestAlertException("The goal is due before it starts", ENTITY_NAME, "dates");
        }
    }

    private void agree(Goal goal, Actor actor) {
        goal.setStatus(GoalStatus.ACTIVE);
        goal.setAgreedAt(Instant.now());
        goal.setAgreedBy(employeeRepository.findById(actor.id()).orElse(null));
    }

    private static void apply(Goal goal, GoalRequest request) {
        goal.setTitle(request.title().trim());
        goal.setDescription(blankToNull(request.description()));
        goal.setSuccessMeasure(blankToNull(request.successMeasure()));
        goal.setWeight(request.weight());
        goal.setStartDate(request.startDate());
        goal.setDueDate(request.dueDate());
    }

    private Goal findGoal(UUID goalId) {
        return goalRepository.findById(goalId).orElseThrow(() -> EntityNotFoundException.create("Goal", goalId));
    }

    private Employee findEmployee(UUID employeeId) {
        return employeeRepository.findById(employeeId).orElseThrow(() -> EntityNotFoundException.create("Employee", employeeId));
    }

    private List<GoalResponse> toResponses(List<Goal> goals, Actor actor) {
        Set<UUID> locked = goals.isEmpty() ? Set.of() : goalRatingRepository.findRatedGoalIds(goals.stream().map(Goal::getId).toList());
        return goals.stream().map(g -> toResponse(g, actor, locked.contains(g.getId()))).toList();
    }

    private GoalResponse toResponse(Goal g, Actor actor, boolean locked) {
        boolean own = isOwner(g, actor);
        boolean managerSide = managesAsManagerOrHr(g.getEmployee(), actor);
        boolean draft = g.getStatus() == GoalStatus.DRAFT;
        boolean active = g.getStatus() == GoalStatus.ACTIVE;
        GoalResponse.Actions can = new GoalResponse.Actions(
            !locked && ((own && draft) || (managerSide && (draft || active))),
            managerSide && draft,
            (own || managerSide) && active,
            (own || managerSide) && active,
            !locked && managerSide && (draft || active),
            !locked && (own || managerSide) && draft
        );
        return new GoalResponse(
            g.getId(),
            g.getEmployee().getId(),
            fullName(g.getEmployee()),
            g.getTitle(),
            g.getDescription(),
            g.getSuccessMeasure(),
            g.getWeight(),
            g.getStartDate(),
            g.getDueDate(),
            g.getStatus(),
            g.getProgress(),
            g.getAgreedAt(),
            g.getAgreedBy() != null ? fullName(g.getAgreedBy()) : null,
            g.getCompletedAt(),
            g.getCancelReason(),
            locked,
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
