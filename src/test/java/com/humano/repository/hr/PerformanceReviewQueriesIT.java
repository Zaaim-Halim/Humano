package com.humano.repository.hr;

import static org.assertj.core.api.Assertions.assertThat;

import com.humano.IntegrationTest;
import com.humano.domain.enumeration.hr.ApprovalType;
import com.humano.domain.enumeration.hr.CompetencyCategory;
import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.domain.enumeration.hr.FeedbackRequestStatus;
import com.humano.domain.enumeration.hr.GoalAchievement;
import com.humano.domain.enumeration.hr.GoalStatus;
import com.humano.domain.enumeration.hr.ImprovementPlanProgress;
import com.humano.domain.enumeration.hr.ImprovementPlanStatus;
import com.humano.domain.enumeration.hr.OrganizationalUnitType;
import com.humano.domain.enumeration.hr.PerformanceReviewStatus;
import com.humano.domain.enumeration.hr.ReviewCycleType;
import com.humano.domain.enumeration.hr.ReviewQuestionAudience;
import com.humano.domain.enumeration.hr.SignOffStatus;
import com.humano.domain.hr.Competency;
import com.humano.domain.hr.Goal;
import com.humano.domain.hr.GoalCheckIn;
import com.humano.domain.hr.ImprovementPlan;
import com.humano.domain.hr.ImprovementPlanCheckIn;
import com.humano.domain.hr.ImprovementPlanObjective;
import com.humano.domain.hr.OrganizationalUnit;
import com.humano.domain.hr.PerformanceReview;
import com.humano.domain.hr.Position;
import com.humano.domain.hr.RatingAnchors;
import com.humano.domain.hr.ReviewCalendarSettings;
import com.humano.domain.hr.ReviewCycle;
import com.humano.domain.hr.ReviewCycleCompetency;
import com.humano.domain.hr.ReviewCycleQuestion;
import com.humano.domain.hr.ReviewGoalRating;
import com.humano.domain.hr.ReviewQuestionDefinition;
import com.humano.domain.hr.ReviewTemplate;
import com.humano.domain.shared.Employee;
import com.humano.repository.hr.workflow.ApprovalRequestRepository;
import com.humano.repository.hr.workflow.ReviewCycleRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.hr.workflow.PerformanceReviewCycleService;
import com.humano.service.hr.workflow.ReviewFeedbackService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

/**
 * Smoke tests that execute the review-cycle queries against the real Hibernate parser and the test
 * database (which also runs the review-cycle changelog). Parameters match no rows; the point is
 * catching unresolved attributes, constructor-expression type mismatches and entity-graph errors
 * that {@code mvn compile} never sees.
 */
@IntegrationTest
@Transactional("tenantTransactionManager")
class PerformanceReviewQueriesIT {

    @Autowired
    private PerformanceReviewRepository reviews;

    @Autowired
    private ReviewCycleRepository cycles;

    @Autowired
    private ReviewCalendarSettingsRepository calendar;

    @Autowired
    private ReviewFeedbackRequestRepository feedback;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private ApprovalRequestRepository approvals;

    @Autowired
    private ReviewRatingChangeRepository ratingChanges;

    @Autowired
    private CompetencyRepository competencies;

    @Autowired
    private ReviewTemplateRepository templates;

    @Autowired
    private ReviewCycleCompetencyRepository cycleCompetencies;

    @Autowired
    private ReviewCycleQuestionRepository cycleQuestions;

    @Autowired
    private ReviewCompetencyRatingRepository competencyRatings;

    @Autowired
    private ReviewQuestionAnswerRepository answers;

    @Autowired
    private GoalRepository goals;

    @Autowired
    private GoalCheckInRepository goalCheckIns;

    @Autowired
    private ReviewGoalRatingRepository goalRatings;

    @Autowired
    private ImprovementPlanRepository improvementPlans;

    @Autowired
    private ImprovementPlanCheckInRepository improvementPlanCheckIns;

    @Autowired
    private OrganizationalUnitRepository units;

    @Autowired
    private PositionRepository positions;

    @PersistenceContext(unitName = "tenant")
    private EntityManager entityManager;

    private final UUID unknown = UUID.randomUUID();

    @Test
    void progressByDepartmentParsesAndExecutes() {
        assertThat(reviews.countProgressByDepartment(unknown, PerformanceReviewStatus.SHARED)).isEmpty();
    }

    @Test
    void cycleReviewLookupsParseAndExecute() {
        assertThat(reviews.findByCycleIdAndEmployeeId(unknown, unknown)).isEmpty();
        assertThat(reviews.findByIdAndCycleId(unknown, unknown)).isEmpty();
        assertThat(reviews.findByIdAndCycleIsNotNull(unknown)).isEmpty();
        assertThat(reviews.findByCycleId(unknown)).isEmpty();
        assertThat(reviews.findByCycleIdAndStatusIn(unknown, List.of(PerformanceReviewStatus.MANAGER_SUBMITTED))).isEmpty();
        assertThat(reviews.findEmployeeIdsByCycleId(unknown)).isEmpty();
    }

    @Test
    void openCycleQueuesParseAndExecute() {
        assertThat(reviews.findByEmployeeIdAndCycleActiveTrueOrderByReviewDateDesc(unknown)).isEmpty();
        assertThat(reviews.findByReviewerIdAndCycleActiveTrueOrderByReviewDateDesc(unknown)).isEmpty();
    }

    @Test
    void pagedReadsWithEntityGraphParseAndExecute() {
        assertThat(reviews.findByEmployeeId(unknown, Pageable.ofSize(10))).isEmpty();
        assertThat(
            reviews.findAll((root, query, cb) -> cb.equal(root.get("status"), PerformanceReviewStatus.SHARED), Pageable.ofSize(10))
        ).isNotNull();
    }

    @Test
    void overlapCheckAcceptsAMissingCycleToExclude() {
        LocalDate day = LocalDate.of(1900, 1, 1);
        assertThat(cycles.findActiveOverlapping(ReviewCycleType.REGULAR, null, day, day)).isEmpty();
        assertThat(cycles.findActiveOverlapping(ReviewCycleType.REGULAR, unknown, day, day)).isEmpty();
        assertThat(cycles.existsByTypeAndReviewPeriodStartAndReviewPeriodEnd(ReviewCycleType.REGULAR, day, day)).isFalse();
        assertThat(cycles.findActiveOn(day)).isEmpty();
    }

    @Test
    void reviewCalendarRoundTrips() {
        ReviewCalendarSettings settings = new ReviewCalendarSettings();
        settings.setCalibrationDays(0);
        calendar.saveAndFlush(settings);

        assertThat(calendar.findAll()).singleElement().extracting(ReviewCalendarSettings::getCalibrationDays).isEqualTo(0);
    }

    @Test
    void feedbackRequestQueriesParseAndExecute() {
        List<FeedbackRequestStatus> open = List.of(FeedbackRequestStatus.PENDING_APPROVAL, FeedbackRequestStatus.REQUESTED);
        assertThat(feedback.findWithReviewById(unknown)).isEmpty();
        assertThat(feedback.findByReviewIdOrderByRequestedAtAscIdAsc(unknown)).isEmpty();
        assertThat(feedback.findByReviewIdAndStatusIn(unknown, open)).isEmpty();
        assertThat(feedback.findByReviewCycleIdAndStatusIn(unknown, open)).isEmpty();
        assertThat(feedback.findByContributorIdAndStatusInAndReviewCycleActiveTrueOrderByDueDateAscRequestedAtAsc(unknown, open)).isEmpty();
        assertThat(feedback.existsByReviewIdAndContributorIdAndStatusIn(unknown, unknown, open)).isFalse();
        assertThat(feedback.countByReviewIdAndStatusIn(unknown, open)).isZero();
        assertThat(feedback.countByCycle(unknown)).isEmpty();
    }

    @Test
    void feedbackCandidateSearchParsesAndExecutes() {
        assertThat(
            employees.findAll(
                ReviewFeedbackService.candidateSpec(Set.of(unknown), "zz-no-such-name"),
                PageRequest.of(0, 10, Sort.by("firstName", "lastName"))
            )
        ).isEmpty();
    }

    @Test
    void acknowledgementCountsAndRatingLogParseAndExecute() {
        assertThat(reviews.countByCycleIdAndAcknowledgedAtIsNotNull(unknown)).isZero();
        assertThat(reviews.countByCycleIdAndDisputedTrueAndDisputeResolvedAtIsNull(unknown)).isZero();
        assertThat(ratingChanges.findByReviewIdOrderByChangedAtAscIdAsc(unknown)).isEmpty();
    }

    @Test
    void signOffQueriesParseAndExecute() {
        assertThat(reviews.countByCycleIdAndSignOffStatus(unknown, SignOffStatus.PENDING)).isZero();
        assertThat(approvals.existsByEntityIdAndApprovalTypeAndApproverId(unknown, ApprovalType.PERFORMANCE_REVIEW, unknown)).isFalse();
        assertThat(approvals.findFirstByEntityIdAndApprovalTypeOrderBySubmittedAtDesc(unknown, ApprovalType.PERFORMANCE_REVIEW)).isEmpty();
    }

    @Test
    void eligibilityOfEachCycleTypeParsesAndExecutes() {
        for (ReviewCycleType type : ReviewCycleType.values()) {
            ReviewCycle cycle = new ReviewCycle();
            cycle.setType(type);
            cycle.setReviewPeriodStart(LocalDate.of(2026, 1, 1));
            cycle.setReviewPeriodEnd(LocalDate.of(2026, 6, 30));
            cycle.setDepartmentIds(Set.of(unknown));
            cycle.setEmployeeIds(Set.of(unknown));
            assertThat(employees.findAll(PerformanceReviewCycleService.eligibleSpec(cycle))).isEmpty();
        }
    }

    @Test
    void templatesAndTheirCycleCopyRoundTrip() {
        Competency ownership = new Competency();
        ownership.setName("Ownership");
        ownership.setCategory(CompetencyCategory.CORE);
        ownership.setAnchors(RatingAnchors.of(List.of("Avoids decisions", "", "Owns their work")));
        ownership = competencies.saveAndFlush(ownership);

        ReviewTemplate template = new ReviewTemplate();
        template.setName("Annual review");
        template.getCompetencies().add(ownership);
        template
            .getQuestions()
            .add(new ReviewQuestionDefinition(ReviewQuestionAudience.EMPLOYEE, "Proudest achievement?", "One example", true));
        template = templates.saveAndFlush(template);

        ReviewCycle cycle = new ReviewCycle();
        cycle.setName("2026 Annual Review");
        cycle.setReviewPeriodStart(LocalDate.of(2026, 1, 1));
        cycle.setReviewPeriodEnd(LocalDate.of(2026, 12, 31));
        cycle.setStartDate(LocalDate.of(2026, 11, 1));
        cycle.setEndDate(LocalDate.of(2026, 12, 31));
        cycle.setActive(true);
        cycle.setTemplate(template);
        cycle = cycles.saveAndFlush(cycle);
        cycleCompetencies.saveAndFlush(ReviewCycleCompetency.of(cycle, ownership, 0));
        cycleQuestions.saveAndFlush(ReviewCycleQuestion.of(cycle, template.getQuestions().get(0), 0));
        entityManager.clear();

        assertThat(templates.findNamesUsingCompetency(ownership.getId())).containsExactly("Annual review");
        assertThat(cycles.countByTemplateId(template.getId())).isEqualTo(1);
        assertThat(templates.findById(template.getId()).orElseThrow().getQuestions())
            .singleElement()
            .satisfies(q -> {
                assertThat(q.getText()).isEqualTo("Proudest achievement?");
                assertThat(q.isRequired()).isTrue();
            });
        assertThat(cycleCompetencies.findByCycleIdOrderBySortOrderAsc(cycle.getId()))
            .singleElement()
            .satisfies(c -> assertThat(c.getAnchors().toList()).containsExactly("Avoids decisions", null, "Owns their work", null, null));
        assertThat(cycleQuestions.findByCycleIdOrderBySortOrderAsc(cycle.getId())).hasSize(1);
        assertThat(cycleCompetencies.existsByCycleId(cycle.getId())).isTrue();
        assertThat(competencyRatings.findByReviewId(unknown)).isEmpty();
        assertThat(answers.findByReviewId(unknown)).isEmpty();
        assertThat(competencies.findAllByOrderByCategoryAscNameAsc()).extracting(Competency::getName).contains("Ownership");
    }

    @Test
    void goalsCheckInsAndTheirReviewRatingsRoundTrip() {
        Employee manager = employees.saveAndFlush(employee("Maria", null));
        Employee alice = employees.saveAndFlush(employee("Alice", manager));

        Goal goal = new Goal();
        goal.setEmployee(alice);
        goal.setTitle("Ship billing");
        goal.setSuccessMeasure("Live in production");
        goal.setWeight(60);
        goal.setStartDate(LocalDate.of(2026, 1, 1));
        goal.setDueDate(LocalDate.of(2026, 9, 30));
        goal.setStatus(GoalStatus.ACTIVE);
        goal.setAgreedAt(Instant.now());
        goal.setAgreedBy(manager);
        goal = goals.saveAndFlush(goal);
        goalCheckIns.saveAndFlush(new GoalCheckIn(goal, alice, 40, "Halfway", Instant.now()));

        PerformanceReview review = new PerformanceReview();
        review.setEmployee(alice);
        review.setReviewer(manager);
        review.setReviewDate(LocalDate.of(2026, 11, 1));
        review = reviews.saveAndFlush(review);
        ReviewGoalRating rating = new ReviewGoalRating(review, goal);
        rating.setGoalTitle(goal.getTitle());
        rating.setGoalWeight(goal.getWeight());
        rating.setAchievement(GoalAchievement.EXCEEDED);
        goalRatings.saveAndFlush(rating);
        entityManager.clear();

        assertThat(goals.findByEmployeeIdOrderByDueDateAscCreatedDateAsc(alice.getId()))
            .extracting(Goal::getTitle)
            .containsExactly("Ship billing");
        assertThat(goals.findByEmployeeManagerIdOrderByDueDateAscCreatedDateAsc(manager.getId()))
            .singleElement()
            .satisfies(g -> assertThat(g.getAgreedBy().getFirstName()).isEqualTo("Maria"));
        assertThat(
            goals.findByEmployeeIdAndStatusInAndDueDateBetweenOrderByDueDateAscTitleAsc(
                alice.getId(),
                EnumSet.of(GoalStatus.ACTIVE, GoalStatus.COMPLETED),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
            )
        ).hasSize(1);
        assertThat(goalCheckIns.findByGoalIdOrderByCheckedInAtDescIdDesc(goal.getId()))
            .singleElement()
            .satisfies(c -> assertThat(c.getProgress()).isEqualTo(40));
        assertThat(goalRatings.findByReviewId(review.getId()))
            .singleElement()
            .satisfies(r -> {
                assertThat(r.getGoal().getTitle()).isEqualTo("Ship billing");
                assertThat(r.getAchievement()).isEqualTo(GoalAchievement.EXCEEDED);
            });
        assertThat(goals.findTeamMembers(manager.getId(), EmployeeStatus.WORKING))
            .singleElement()
            .satisfies(m -> assertThat(m.name()).isEqualTo("Alice Tester"));
        assertThat(goalRatings.existsByGoalId(goal.getId())).isTrue();
        assertThat(goalRatings.findRatedGoalIds(List.of(goal.getId(), unknown))).containsExactly(goal.getId());
        assertThat(reviews.existsByEmployeeIdAndReviewerIdAndCycleIsNotNull(alice.getId(), manager.getId())).isFalse(); // no cycle
    }

    @Test
    void improvementPlansWithObjectivesAndCheckInsRoundTrip() {
        Employee manager = employees.saveAndFlush(employee("Maria", null));
        Employee alice = employees.saveAndFlush(employee("Alice", manager));
        PerformanceReview review = new PerformanceReview();
        review.setEmployee(alice);
        review.setReviewer(manager);
        review.setReviewDate(LocalDate.of(2026, 11, 1));
        review.setRating(2);
        review = reviews.saveAndFlush(review);

        ImprovementPlan plan = new ImprovementPlan();
        plan.setEmployee(alice);
        plan.setOwner(manager);
        plan.setReview(review);
        plan.setStatus(ImprovementPlanStatus.ACTIVE);
        plan.setConcerns("Tickets regularly miss the SLA. ".repeat(200));
        plan.setSupport("Weekly coaching");
        plan.setStartDate(LocalDate.of(2026, 11, 2));
        plan.setEndDate(LocalDate.of(2027, 1, 1));
        plan.getObjectives().add(new ImprovementPlanObjective(plan, 0, "Close tickets within SLA", "95% for 4 weeks", null));
        plan.getObjectives().add(new ImprovementPlanObjective(plan, 1, "Document fixes", null, LocalDate.of(2026, 12, 1)));
        plan = improvementPlans.saveAndFlush(plan);
        improvementPlanCheckIns.saveAndFlush(
            new ImprovementPlanCheckIn(plan, manager, ImprovementPlanProgress.SOME_PROGRESS, "Two late", Instant.now())
        );
        entityManager.clear();

        ImprovementPlan loaded = improvementPlans.findWithDetailsById(plan.getId()).orElseThrow();
        assertThat(loaded.getConcerns()).hasSizeGreaterThan(5000);
        assertThat(loaded.getEmployee().getManager().getFirstName()).isEqualTo("Maria");
        assertThat(loaded.getObjectives())
            .extracting(ImprovementPlanObjective::getDescription)
            .containsExactly("Close tickets within SLA", "Document fixes");
        assertThat(
            improvementPlans.existsByEmployeeIdAndStatusIn(
                alice.getId(),
                EnumSet.of(ImprovementPlanStatus.DRAFT, ImprovementPlanStatus.ACTIVE)
            )
        ).isTrue();
        assertThat(
            improvementPlans.findByEmployeeIdAndStatusInOrderByCreatedDateDesc(alice.getId(), EnumSet.of(ImprovementPlanStatus.ACTIVE))
        ).hasSize(1);
        assertThat(improvementPlans.findManagedBy(manager.getId())).extracting(ImprovementPlan::getId).containsExactly(plan.getId());
        assertThat(improvementPlans.findManagedBy(alice.getId())).isEmpty();
        assertThat(improvementPlans.findAllByOrderByCreatedDateDesc()).extracting(ImprovementPlan::getId).contains(plan.getId());
        assertThat(improvementPlans.findByReviewIdOrderByCreatedDateDesc(review.getId())).hasSize(1);
        assertThat(improvementPlanCheckIns.findByPlanIdOrderByCheckedInAtDescIdDesc(plan.getId()))
            .singleElement()
            .satisfies(c -> assertThat(c.getAuthor().getFirstName()).isEqualTo("Maria"));
    }

    @Test
    void anExcludedReviewRoundTripsAndLeavesProgressAndAnalytics() {
        Employee manager = employees.saveAndFlush(employee("Maria", null));
        Employee alice = employees.saveAndFlush(employee("Alice", manager));
        Employee bob = employees.saveAndFlush(employee("Bob", manager));

        ReviewCycle cycle = new ReviewCycle();
        cycle.setName("Membership " + UUID.randomUUID());
        cycle.setReviewPeriodStart(LocalDate.of(2026, 1, 1));
        cycle.setReviewPeriodEnd(LocalDate.of(2026, 12, 31));
        cycle.setStartDate(LocalDate.of(2026, 11, 1));
        cycle.setEndDate(LocalDate.of(2026, 12, 31));
        cycle.setActive(true);
        cycle = cycles.saveAndFlush(cycle);

        PerformanceReview alices = new PerformanceReview();
        alices.setCycle(cycle);
        alices.setEmployee(alice);
        alices.setReviewer(manager);
        alices.setReviewDate(cycle.getReviewPeriodEnd());
        alices.setStatus(PerformanceReviewStatus.NOT_STARTED);
        reviews.saveAndFlush(alices);
        PerformanceReview bobs = new PerformanceReview();
        bobs.setCycle(cycle);
        bobs.setEmployee(bob);
        bobs.setReviewer(manager);
        bobs.setReviewDate(cycle.getReviewPeriodEnd());
        bobs.setStatus(PerformanceReviewStatus.EXCLUDED);
        bobs.setExcludedAt(Instant.now());
        bobs.setExclusionReason("On parental leave until March");
        bobs.setExcludedBy(manager);
        bobs = reviews.saveAndFlush(bobs);
        entityManager.clear();

        PerformanceReview read = reviews.findById(bobs.getId()).orElseThrow();
        assertThat(read.getStatus()).isEqualTo(PerformanceReviewStatus.EXCLUDED);
        assertThat(read.getExclusionReason()).isEqualTo("On parental leave until March");
        assertThat(read.getExcludedBy().getId()).isEqualTo(manager.getId());

        assertThat(reviews.countProgressByDepartment(cycle.getId(), PerformanceReviewStatus.SHARED))
            .singleElement()
            .satisfies(row -> assertThat(row.total()).isEqualTo(1));
        assertThat(reviews.findStatRowsByCycleIds(List.of(cycle.getId())))
            .singleElement()
            .satisfies(row -> assertThat(row.status()).isEqualTo(PerformanceReviewStatus.NOT_STARTED));
        // Both stay in the cycle: an excluded person is put back, never added again.
        assertThat(reviews.findEmployeeIdsByCycleId(cycle.getId())).containsExactlyInAnyOrder(alice.getId(), bob.getId());
    }

    @Test
    void addableEmployeeSearchParsesAndExecutes() {
        ReviewCycle cycle = new ReviewCycle();
        cycle.setType(ReviewCycleType.PROBATION);
        cycle.setReviewPeriodStart(LocalDate.of(2026, 1, 1));
        cycle.setReviewPeriodEnd(LocalDate.of(2026, 6, 30));
        cycle.setDepartmentIds(Set.of(unknown));
        assertThat(
            employees.findAll(
                ReviewFeedbackService.candidateSpec(Set.of(unknown), "zz-no-such-name").and(
                    PerformanceReviewCycleService.eligibleSpec(cycle)
                ),
                PageRequest.of(0, 20, Sort.by("firstName", "lastName"))
            )
        ).isEmpty();
    }

    private Employee employee(String firstName, Employee manager) {
        OrganizationalUnit unit = new OrganizationalUnit();
        unit.setName("Goals team " + UUID.randomUUID());
        unit.setType(OrganizationalUnitType.TEAM);
        Position position = new Position();
        position.setName("Analyst " + UUID.randomUUID());
        position.setLevel("L2");
        String login = "goal-" + UUID.randomUUID().toString().substring(0, 8);
        Employee e = new Employee();
        e.setLogin(login);
        e.setEmail(login + "@example.com");
        e.setPassword("x".repeat(60));
        e.setActivated(true);
        e.setFirstName(firstName);
        e.setLastName("Tester");
        e.setStatus(EmployeeStatus.ACTIVE);
        e.setStartDate(LocalDate.of(2025, 1, 1));
        e.setPosition(positions.saveAndFlush(position));
        e.setUnit(units.saveAndFlush(unit));
        e.setManager(manager);
        return e;
    }
}
