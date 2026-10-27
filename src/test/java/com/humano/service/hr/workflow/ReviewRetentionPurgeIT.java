package com.humano.service.hr.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.humano.IntegrationTest;
import com.humano.domain.audit.AuditEvent;
import com.humano.domain.enumeration.hr.ApprovalType;
import com.humano.domain.enumeration.hr.CompensationProposalStatus;
import com.humano.domain.enumeration.hr.CompetencyCategory;
import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.domain.enumeration.hr.FeedbackRelationship;
import com.humano.domain.enumeration.hr.FeedbackRequestStatus;
import com.humano.domain.enumeration.hr.GoalAchievement;
import com.humano.domain.enumeration.hr.ImprovementPlanProgress;
import com.humano.domain.enumeration.hr.ImprovementPlanStatus;
import com.humano.domain.enumeration.hr.OrganizationalUnitType;
import com.humano.domain.enumeration.hr.PerformanceReviewStatus;
import com.humano.domain.enumeration.hr.RatingChangeSource;
import com.humano.domain.enumeration.hr.ReviewCyclePhase;
import com.humano.domain.enumeration.hr.ReviewQuestionAudience;
import com.humano.domain.enumeration.hr.WorkflowStatus;
import com.humano.domain.enumeration.hr.WorkflowType;
import com.humano.domain.hr.ApprovalRequest;
import com.humano.domain.hr.CompensationProposal;
import com.humano.domain.hr.Competency;
import com.humano.domain.hr.Goal;
import com.humano.domain.hr.ImprovementPlan;
import com.humano.domain.hr.ImprovementPlanCheckIn;
import com.humano.domain.hr.ImprovementPlanObjective;
import com.humano.domain.hr.OrganizationalUnit;
import com.humano.domain.hr.PerformanceReview;
import com.humano.domain.hr.Position;
import com.humano.domain.hr.ReviewCompetencyRating;
import com.humano.domain.hr.ReviewCycle;
import com.humano.domain.hr.ReviewCycleCompetency;
import com.humano.domain.hr.ReviewCycleQuestion;
import com.humano.domain.hr.ReviewFeedbackRequest;
import com.humano.domain.hr.ReviewGoalRating;
import com.humano.domain.hr.ReviewQuestionAnswer;
import com.humano.domain.hr.ReviewQuestionDefinition;
import com.humano.domain.hr.ReviewRatingChange;
import com.humano.domain.hr.WorkflowDeadline;
import com.humano.domain.hr.WorkflowInstance;
import com.humano.domain.hr.WorkflowStateTransition;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.workflow.requests.PurgeReviewCyclesRequest;
import com.humano.dto.hr.workflow.responses.ReviewRetentionPurgeResult;
import com.humano.dto.hr.workflow.responses.ReviewRetentionResponse;
import com.humano.repository.audit.AuditEventRepository;
import com.humano.repository.hr.ImprovementPlanRepository;
import com.humano.repository.hr.PerformanceReviewRepository;
import com.humano.repository.hr.projection.ReviewStatRow;
import com.humano.service.errors.BadRequestAlertException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Deleting review cycles past the retention period, on MySQL: a settled, archived cycle goes with
 * every row recorded under it — answers, ratings, feedback, calibration changes, improvement plan,
 * compensation proposal, sign-off approval and the workflows behind it and the cycle — while a cycle
 * held back by an open dispute, the employees, their goals and the audit log stay.
 * <p>
 * Each step commits in its own transaction, as through the API, so the fixtures are committed and
 * removed again after each test.
 */
@IntegrationTest
class ReviewRetentionPurgeIT {

    @Autowired
    private ReviewRetentionService service;

    @Autowired
    private ReviewCyclePurger purger;

    @Autowired
    @Qualifier("tenantTransactionManager")
    private PlatformTransactionManager txManager;

    @Autowired
    private PerformanceReviewRepository reviews;

    @Autowired
    private ImprovementPlanRepository plans;

    @Autowired
    private AuditEventRepository auditEvents;

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    private TransactionTemplate tx;
    private UUID employeeId;
    private UUID managerId;
    private UUID unitId;
    private UUID positionId;
    private UUID goalId;
    private UUID settledId;
    private UUID disputedId;
    private UUID disputedReviewId;
    /** Every row recorded under the settled cycle, by entity. */
    private final Map<Class<?>, List<UUID>> settledRows = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(s -> {
            OrganizationalUnit unit = new OrganizationalUnit();
            unit.setName("Retention team " + UUID.randomUUID());
            unit.setType(OrganizationalUnitType.TEAM);
            em.persist(unit);
            unitId = unit.getId();
            Position position = new Position();
            position.setName("Retention analyst " + UUID.randomUUID());
            position.setLevel("L2");
            em.persist(position);
            positionId = position.getId();
            Employee employee = employee("Camille", unit, position);
            Employee manager = employee("Noor", unit, position);
            employeeId = employee.getId();
            managerId = manager.getId();

            Goal goal = new Goal();
            goal.setEmployee(employee);
            goal.setTitle("Ship the ledger export");
            goal.setStartDate(LocalDate.of(2015, 1, 1));
            goal.setDueDate(LocalDate.of(2015, 12, 31));
            em.persist(goal);
            goalId = goal.getId();

            // Settled and archived, ten years ago: due.
            ReviewCycle settled = cycle("Retention 2015 " + UUID.randomUUID(), 2015);
            settled.setDepartmentIds(Set.of(UUID.randomUUID()));
            settled.setEmployeeIds(Set.of(employeeId));
            WorkflowInstance cycleFlow = workflow(WorkflowType.PERFORMANCE_REVIEW_CYCLE, null, "ReviewCycle");
            settled.setWorkflowInstance(cycleFlow);
            em.persist(settled);
            settledId = settled.getId();
            cycleFlow.setEntityId(settledId);
            row(WorkflowInstance.class, cycleFlow.getId());
            row(WorkflowDeadline.class, deadline(cycleFlow));
            row(WorkflowStateTransition.class, transition(cycleFlow));

            ReviewCycleQuestion question = ReviewCycleQuestion.of(
                settled,
                new ReviewQuestionDefinition(ReviewQuestionAudience.EMPLOYEE, "What went well?", null, true),
                1
            );
            em.persist(question);
            row(ReviewCycleQuestion.class, question.getId());
            Competency ownership = new Competency();
            ownership.setName("Ownership");
            ownership.setCategory(CompetencyCategory.CORE);
            ReviewCycleCompetency competency = ReviewCycleCompetency.of(settled, ownership, 1);
            em.persist(competency);
            row(ReviewCycleCompetency.class, competency.getId());

            PerformanceReview review = review(settled, employee, manager);
            row(PerformanceReview.class, review.getId());

            ReviewQuestionAnswer answer = new ReviewQuestionAnswer(review, question);
            answer.setAnswer("Led the ledger migration.");
            em.persist(answer);
            row(ReviewQuestionAnswer.class, answer.getId());
            ReviewCompetencyRating competencyRating = new ReviewCompetencyRating(review, competency);
            competencyRating.setRating(4);
            em.persist(competencyRating);
            row(ReviewCompetencyRating.class, competencyRating.getId());
            ReviewGoalRating goalRating = new ReviewGoalRating(review, goal);
            goalRating.setGoalTitle(goal.getTitle());
            goalRating.setAchievement(GoalAchievement.MET);
            em.persist(goalRating);
            row(ReviewGoalRating.class, goalRating.getId());
            ReviewFeedbackRequest feedback = new ReviewFeedbackRequest();
            feedback.setReview(review);
            feedback.setContributor(manager);
            feedback.setRelationship(FeedbackRelationship.PEER);
            feedback.setStatus(FeedbackRequestStatus.SUBMITTED);
            feedback.setRequestedAt(Instant.now());
            em.persist(feedback);
            row(ReviewFeedbackRequest.class, feedback.getId());
            ReviewRatingChange change = new ReviewRatingChange();
            change.setReview(review);
            change.setToRating(4);
            change.setSource(RatingChangeSource.CALIBRATION);
            change.setChangedAt(Instant.now());
            em.persist(change);
            row(ReviewRatingChange.class, change.getId());

            ImprovementPlan plan = new ImprovementPlan();
            plan.setEmployee(employee);
            plan.setReview(review);
            plan.setStatus(ImprovementPlanStatus.CLOSED);
            plan.setConcerns("Deadlines slipped.");
            plan.setStartDate(LocalDate.of(2016, 2, 1));
            plan.setEndDate(LocalDate.of(2016, 4, 30));
            em.persist(plan);
            row(ImprovementPlan.class, plan.getId());
            ImprovementPlanObjective objective = new ImprovementPlanObjective();
            objective.setPlan(plan);
            objective.setSortOrder(1);
            objective.setDescription("Deliver on the agreed dates.");
            em.persist(objective);
            row(ImprovementPlanObjective.class, objective.getId());
            ImprovementPlanCheckIn checkIn = new ImprovementPlanCheckIn();
            checkIn.setPlan(plan);
            checkIn.setProgress(ImprovementPlanProgress.ON_TRACK);
            checkIn.setNote("On track.");
            checkIn.setCheckedInAt(Instant.now());
            em.persist(checkIn);
            row(ImprovementPlanCheckIn.class, checkIn.getId());

            CompensationProposal proposal = new CompensationProposal();
            proposal.setCycle(settled);
            proposal.setReview(review);
            proposal.setEmployee(employee);
            proposal.setRating(4);
            proposal.setStatus(CompensationProposalStatus.APPLIED);
            proposal.setEffectiveDate(LocalDate.of(2016, 3, 1));
            em.persist(proposal);
            row(CompensationProposal.class, proposal.getId());

            WorkflowInstance signOffFlow = workflow(WorkflowType.PERFORMANCE_REVIEW_SIGN_OFF, review.getId(), "PerformanceReview");
            row(WorkflowInstance.class, signOffFlow.getId());
            row(WorkflowDeadline.class, deadline(signOffFlow));
            row(WorkflowStateTransition.class, transition(signOffFlow));
            ApprovalRequest signOff = new ApprovalRequest();
            signOff.setWorkflowInstance(signOffFlow);
            signOff.setApprovalType(ApprovalType.PERFORMANCE_REVIEW);
            signOff.setEntityId(review.getId());
            signOff.setEntityType("PerformanceReview");
            signOff.setRequestor(manager);
            signOff.setApprover(manager);
            signOff.setStatus(WorkflowStatus.APPROVED);
            em.persist(signOff);
            row(ApprovalRequest.class, signOff.getId());

            // Archived and as old, but a dispute is still open: held.
            ReviewCycle disputed = cycle("Retention 2014 " + UUID.randomUUID(), 2014);
            em.persist(disputed);
            disputedId = disputed.getId();
            PerformanceReview disputedReview = review(disputed, employee, manager);
            disputedReview.setDisputed(true);
            disputedReviewId = disputedReview.getId();
            em.flush();
        });
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(s -> {
            for (UUID cycleId : List.of(settledId, disputedId)) {
                ReviewCycle cycle = em.find(ReviewCycle.class, cycleId);
                if (cycle != null) {
                    purger.purge(cycle);
                }
            }
            em.remove(em.find(Goal.class, goalId));
            em.remove(em.find(Employee.class, employeeId));
            em.remove(em.find(Employee.class, managerId));
            em.remove(em.find(OrganizationalUnit.class, unitId));
            em.remove(em.find(Position.class, positionId));
        });
    }

    @Test
    void aSettledArchivedCycleIsDueAndOneWithAnOpenDisputeIsHeld() {
        ReviewRetentionResponse view = service.get();

        assertThat(view.due()).extracting(ReviewRetentionResponse.Cycle::id).contains(settledId).doesNotContain(disputedId);
        ReviewRetentionResponse.Cycle held = view.held().stream().filter(c -> c.id().equals(disputedId)).findFirst().orElseThrow();
        assertThat(held.holds()).isEqualTo(new ReviewRetentionResponse.Holds(false, 1, 0, 0));
        assertThat(held.reviews()).isEqualTo(1);
    }

    @Test
    void theAnalyticsQueriesReadTheCyclesReviews() {
        List<ReviewStatRow> stats = reviews.findStatRowsByCycleIds(List.of(settledId, disputedId));

        assertThat(stats).hasSize(2);
        assertThat(stats).filteredOn(r -> r.cycleId().equals(disputedId)).singleElement().satisfies(r -> assertThat(r.disputed()).isTrue());
        assertThat(plans.findReviewIdsByCycleIds(List.of(settledId, disputedId))).containsExactly(
            settledRows.get(PerformanceReview.class).get(0)
        );
    }

    @Test
    void deletingTheDueCyclesRemovesEverythingRecordedUnderThemAndKeepsTheRest() {
        List<UUID> due = service.get().due().stream().map(ReviewRetentionResponse.Cycle::id).toList();

        ReviewRetentionPurgeResult result = service.purge(new PurgeReviewCyclesRequest(due));

        assertThat(result.failed()).isZero();
        assertThat(result.cycles()).isEqualTo(due.size());
        tx.executeWithoutResult(s -> {
            assertThat(em.find(ReviewCycle.class, settledId)).isNull();
            settledRows.forEach((type, ids) -> ids.forEach(id -> assertThat(em.find(type, id)).as(type.getSimpleName()).isNull()));
            Number lists = (Number) em
                .createNativeQuery(
                    "select (select count(*) from review_cycle_departments where cycle_id = :c) + (select count(*) from review_cycle_employees where cycle_id = :c)"
                )
                .setParameter("c", settledId)
                .getSingleResult();
            assertThat(lists.intValue()).isZero();

            assertThat(em.find(ReviewCycle.class, disputedId)).isNotNull();
            assertThat(em.find(PerformanceReview.class, disputedReviewId)).isNotNull();
            assertThat(em.find(Goal.class, goalId)).isNotNull();
            assertThat(em.find(Employee.class, employeeId)).isNotNull();
        });
        List<AuditEvent> audit = auditEvents.findByTargetTypeAndTargetIdOrderByOccurredAtDesc(
            "ReviewCycle",
            settledId.toString(),
            Pageable.unpaged()
        );
        assertThat(audit)
            .singleElement()
            .satisfies(e -> {
                assertThat(e.getAction()).isEqualTo("PERFORMANCE_CYCLE_PURGED");
                assertThat(e.getPayloadJson())
                    .containsEntry("trigger", "MANUAL")
                    .containsEntry("reviews", 1)
                    .containsEntry("approvalRequests", 1);
            });
    }

    @Test
    void aListThatIsNotTheOneDueNowDeletesNothing() {
        assertThatThrownBy(() -> service.purge(new PurgeReviewCyclesRequest(List.of(disputedId))))
            .isInstanceOf(BadRequestAlertException.class)
            .hasMessageContaining("changed since you looked");

        tx.executeWithoutResult(s -> {
            assertThat(em.find(ReviewCycle.class, settledId)).isNotNull();
            assertThat(em.find(ReviewCycle.class, disputedId)).isNotNull();
        });
    }

    private Employee employee(String firstName, OrganizationalUnit unit, Position position) {
        String login = "ret-" + UUID.randomUUID().toString().substring(0, 8);
        Employee e = new Employee();
        e.setLogin(login);
        e.setEmail(login + "@example.com");
        e.setPassword("x".repeat(60));
        e.setActivated(true);
        e.setFirstName(firstName);
        e.setLastName("Retention");
        e.setStatus(EmployeeStatus.ACTIVE);
        e.setStartDate(LocalDate.of(2014, 1, 1));
        e.setUnit(unit);
        e.setPosition(position);
        em.persist(e);
        return e;
    }

    private ReviewCycle cycle(String name, int year) {
        ReviewCycle cycle = new ReviewCycle();
        cycle.setName(name);
        cycle.setReviewPeriodStart(LocalDate.of(year, 1, 1));
        cycle.setReviewPeriodEnd(LocalDate.of(year, 12, 31));
        cycle.setStartDate(LocalDate.of(year + 1, 1, 5));
        cycle.setEndDate(LocalDate.of(year + 1, 2, 27));
        cycle.setPhase(ReviewCyclePhase.ARCHIVED);
        cycle.setActive(false);
        return cycle;
    }

    private PerformanceReview review(ReviewCycle cycle, Employee employee, Employee manager) {
        PerformanceReview review = new PerformanceReview();
        review.setEmployee(employee);
        review.setReviewer(manager);
        review.setCycle(cycle);
        review.setReviewDate(cycle.getEndDate());
        review.setStatus(PerformanceReviewStatus.SHARED);
        review.setManagerRating(4);
        review.setRating(4);
        em.persist(review);
        return review;
    }

    private WorkflowInstance workflow(WorkflowType type, UUID entityId, String entityType) {
        WorkflowInstance w = new WorkflowInstance();
        w.setWorkflowType(type);
        w.setEntityId(entityId != null ? entityId : UUID.randomUUID());
        w.setEntityType(entityType);
        w.setStatus(WorkflowStatus.COMPLETED);
        em.persist(w);
        return w;
    }

    private UUID deadline(WorkflowInstance w) {
        WorkflowDeadline d = new WorkflowDeadline();
        d.setWorkflowInstance(w);
        d.setDeadlineType("PHASE");
        d.setDeadlineAt(Instant.now());
        d.setCompleted(true);
        em.persist(d);
        return d.getId();
    }

    private UUID transition(WorkflowInstance w) {
        WorkflowStateTransition t = new WorkflowStateTransition();
        t.setWorkflowInstance(w);
        t.setToState("COMPLETED");
        t.setTransitionedAt(Instant.now());
        em.persist(t);
        return t.getId();
    }

    private void row(Class<?> type, UUID id) {
        settledRows.computeIfAbsent(type, k -> new ArrayList<>()).add(id);
    }
}
