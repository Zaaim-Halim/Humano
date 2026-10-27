package com.humano.service.hr.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.humano.domain.enumeration.hr.ApprovalType;
import com.humano.domain.enumeration.hr.CompetencyCategory;
import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.domain.enumeration.hr.GoalAchievement;
import com.humano.domain.enumeration.hr.GoalStatus;
import com.humano.domain.enumeration.hr.PerformanceReviewStatus;
import com.humano.domain.enumeration.hr.ProbationRecommendation;
import com.humano.domain.enumeration.hr.RatingChangeSource;
import com.humano.domain.enumeration.hr.ReviewCyclePhase;
import com.humano.domain.enumeration.hr.ReviewCycleType;
import com.humano.domain.enumeration.hr.ReviewPotential;
import com.humano.domain.enumeration.hr.ReviewQuestionAudience;
import com.humano.domain.enumeration.hr.SignOffStatus;
import com.humano.domain.hr.Competency;
import com.humano.domain.hr.Goal;
import com.humano.domain.hr.PerformanceReview;
import com.humano.domain.hr.RatingAnchors;
import com.humano.domain.hr.ReviewCompetencyRating;
import com.humano.domain.hr.ReviewCycle;
import com.humano.domain.hr.ReviewCycleCompetency;
import com.humano.domain.hr.ReviewCycleQuestion;
import com.humano.domain.hr.ReviewGoalRating;
import com.humano.domain.hr.ReviewQuestionAnswer;
import com.humano.domain.hr.ReviewQuestionDefinition;
import com.humano.domain.hr.ReviewRatingChange;
import com.humano.domain.hr.ReviewTemplate;
import com.humano.domain.hr.WorkflowDeadline;
import com.humano.domain.hr.WorkflowInstance;
import com.humano.domain.hr.WorkflowStateTransition;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.responses.PerformanceReviewResponse;
import com.humano.dto.hr.workflow.requests.AcknowledgeReviewRequest;
import com.humano.dto.hr.workflow.requests.CalibrateReviewRequest;
import com.humano.dto.hr.workflow.requests.CompetencyRatingInput;
import com.humano.dto.hr.workflow.requests.GoalRatingInput;
import com.humano.dto.hr.workflow.requests.InitiateReviewCycleRequest;
import com.humano.dto.hr.workflow.requests.ManagerReviewRequest;
import com.humano.dto.hr.workflow.requests.MoveBackPhaseRequest;
import com.humano.dto.hr.workflow.requests.ResolveDisputeRequest;
import com.humano.dto.hr.workflow.requests.ReviewAnswerInput;
import com.humano.dto.hr.workflow.requests.SelfAssessmentRequest;
import com.humano.dto.hr.workflow.requests.SubmitApprovalRequest;
import com.humano.dto.hr.workflow.responses.ReviewCycleResponse;
import com.humano.dto.hr.workflow.responses.ReviewForm;
import com.humano.repository.hr.DepartmentRepository;
import com.humano.repository.hr.GoalRepository;
import com.humano.repository.hr.PerformanceReviewRepository;
import com.humano.repository.hr.ReviewCompetencyRatingRepository;
import com.humano.repository.hr.ReviewCycleCompetencyRepository;
import com.humano.repository.hr.ReviewCycleQuestionRepository;
import com.humano.repository.hr.ReviewGoalRatingRepository;
import com.humano.repository.hr.ReviewQuestionAnswerRepository;
import com.humano.repository.hr.ReviewRatingChangeRepository;
import com.humano.repository.hr.projection.ReviewProgressRow;
import com.humano.repository.hr.workflow.ApprovalRequestRepository;
import com.humano.repository.hr.workflow.ReviewCycleRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.audit.AuditEventService;
import com.humano.service.errors.BadRequestAlertException;
import com.humano.service.hr.PerformanceReviewService;
import com.humano.service.hr.workflow.infrastructure.DeadlineMonitorService;
import com.humano.service.hr.workflow.infrastructure.NotificationOrchestrationService;
import com.humano.service.hr.workflow.infrastructure.WorkflowStateManager;
import com.humano.service.payroll.OrganizationSettingsService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

/** A review cycle creates one review per employee and walks it from self-assessment to the feedback meeting. */
class PerformanceReviewCycleServiceTest {

    private final ReviewCycleRepository cycles = mock(ReviewCycleRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final PerformanceReviewRepository reviews = mock(PerformanceReviewRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final NotificationOrchestrationService notifications = mock(NotificationOrchestrationService.class);
    private final DeadlineMonitorService deadlines = mock(DeadlineMonitorService.class);
    private final OrganizationSettingsService settings = mock(OrganizationSettingsService.class);
    private final ReviewFeedbackService feedback = mock(ReviewFeedbackService.class);
    private final ReviewRatingChangeRepository ratingChanges = mock(ReviewRatingChangeRepository.class);
    private final ApprovalWorkflowOrchestratorService approvals = mock(ApprovalWorkflowOrchestratorService.class);
    private final ApprovalRequestRepository approvalRequests = mock(ApprovalRequestRepository.class);
    private final ReviewCycleCompetencyRepository cycleCompetencies = mock(ReviewCycleCompetencyRepository.class);
    private final ReviewCycleQuestionRepository cycleQuestions = mock(ReviewCycleQuestionRepository.class);
    private final ReviewCompetencyRatingRepository competencyRatings = mock(ReviewCompetencyRatingRepository.class);
    private final ReviewQuestionAnswerRepository answers = mock(ReviewQuestionAnswerRepository.class);
    private final PerformanceFrameworkService framework = mock(PerformanceFrameworkService.class);
    private final GoalRepository goals = mock(GoalRepository.class);
    private final ReviewGoalRatingRepository goalRatings = mock(ReviewGoalRatingRepository.class);

    private final AuditEventService audit = mock(AuditEventService.class);
    private final WorkflowStateManager workflows = mock(WorkflowStateManager.class);
    private final PerformanceReviewCycleService service = new PerformanceReviewCycleService(
        cycles,
        reviews,
        new PerformanceReviewService(reviews, employees),
        employees,
        departments,
        workflows,
        notifications,
        deadlines,
        settings,
        feedback,
        ratingChanges,
        approvals,
        approvalRequests,
        new ReviewFormService(cycleCompetencies, cycleQuestions, competencyRatings, answers, goals, goalRatings),
        framework,
        audit
    );

    private final List<PerformanceReview> saved = new ArrayList<>();
    private final UUID hrUser = UUID.randomUUID();

    private final Employee manager = employee("Maria", "Manager", null);
    private final Employee alice = employee("Alice", "Analyst", manager);
    private final Employee bob = employee("Bob", "Builder", manager);
    private ReviewCycle cycle;

    @BeforeEach
    void setUp() {
        cycle = new ReviewCycle();
        cycle.setId(UUID.randomUUID());
        cycle.setName("2026 Annual Review");
        cycle.setPhase(ReviewCyclePhase.DRAFT);
        cycle.setReviewPeriodStart(LocalDate.of(2026, 1, 1));
        cycle.setReviewPeriodEnd(LocalDate.of(2026, 12, 31));
        cycle.setStartDate(LocalDate.of(2026, 11, 1));
        cycle.setEndDate(LocalDate.of(2026, 12, 31));
        cycle.setSelfAssessmentDeadline(LocalDate.of(2026, 11, 15));
        cycle.setManagerReviewDeadline(LocalDate.of(2026, 11, 30));
        cycle.setFeedbackDeadline(LocalDate.of(2026, 12, 20));

        when(cycles.findById(cycle.getId())).thenReturn(Optional.of(cycle));
        when(cycles.save(any())).thenAnswer(inv -> {
            ReviewCycle saved = inv.getArgument(0);
            if (saved.getId() == null) saved.setId(UUID.randomUUID());
            return saved;
        });
        when(employees.findAll(any(Specification.class))).thenReturn(List.of(alice, bob));
        when(employees.findAllById(any())).thenAnswer(inv -> {
            Iterable<UUID> ids = inv.getArgument(0);
            List<Employee> found = new ArrayList<>();
            ids.forEach(id -> List.of(manager, alice, bob).stream().filter(e -> e.getId().equals(id)).forEach(found::add));
            return found;
        });
        when(employees.findById(any())).thenAnswer(inv ->
            Optional.ofNullable(
                List.of(manager, alice, bob).stream().filter(e -> e.getId().equals(inv.getArgument(0))).findFirst().orElse(null)
            )
        );
        when(settings.zone()).thenReturn(ZoneId.of("Europe/Berlin"));
        when(feedback.totals(any())).thenReturn(new int[] { 0, 0 });

        when(reviews.saveAll(any())).thenAnswer(inv -> {
            Iterable<PerformanceReview> list = inv.getArgument(0);
            List<PerformanceReview> out = new ArrayList<>();
            list.forEach(r -> {
                if (r.getId() == null) r.setId(UUID.randomUUID());
                if (!saved.contains(r)) saved.add(r);
                out.add(r);
            });
            return out;
        });
        when(reviews.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reviews.findEmployeeIdsByCycleId(any())).thenAnswer(inv ->
            saved.stream().map(r -> r.getEmployee().getId()).collect(Collectors.toSet())
        );
        when(reviews.findByCycleId(any())).thenAnswer(inv -> List.copyOf(saved));
        when(reviews.findByCycleIdAndEmployeeId(any(), any())).thenAnswer(inv ->
            saved.stream().filter(r -> r.getEmployee().getId().equals(inv.getArgument(1))).findFirst()
        );
        when(reviews.findByIdAndCycleId(any(), any())).thenAnswer(inv ->
            saved.stream().filter(r -> r.getId().equals(inv.getArgument(0))).findFirst()
        );
        when(reviews.findByIdAndCycleIsNotNull(any())).thenAnswer(inv ->
            saved.stream().filter(r -> r.getId().equals(inv.getArgument(0))).findFirst()
        );
        when(reviews.findByCycleIdAndStatusIn(any(), any())).thenAnswer(inv -> {
            Collection<PerformanceReviewStatus> statuses = inv.getArgument(1);
            return saved.stream().filter(r -> statuses.contains(r.getStatus())).toList();
        });
        when(reviews.countProgressByDepartment(any(), any())).thenAnswer(inv ->
            saved.isEmpty()
                ? List.of()
                : List.of(
                    // Like the query: excluded reviews don't count.
                    new ReviewProgressRow(
                        null,
                        null,
                        included().count(),
                        included().filter(r -> r.getSelfSubmittedAt() != null).count(),
                        included().filter(r -> r.getManagerSubmittedAt() != null).count(),
                        included().filter(r -> r.getStatus() == PerformanceReviewStatus.SHARED).count()
                    )
                )
        );
    }

    @Test
    void startingSelfAssessmentCreatesOneReviewPerEmployeeWithTheirManagerAsReviewer() {
        service.startSelfAssessmentPhase(cycle.getId());

        assertThat(cycle.getPhase()).isEqualTo(ReviewCyclePhase.SELF_ASSESSMENT);
        assertThat(saved).extracting(PerformanceReview::getEmployee).containsExactly(alice, bob);
        assertThat(saved).allSatisfy(r -> {
            assertThat(r.getReviewer()).isEqualTo(manager);
            assertThat(r.getStatus()).isEqualTo(PerformanceReviewStatus.NOT_STARTED);
            assertThat(r.getReviewDate()).isEqualTo(cycle.getReviewPeriodEnd());
            assertThat(r.getRating()).isNull();
        });
        assertThat(cycle.getTotalEmployees()).isEqualTo(2);
        verify(notifications).notifyTaskAssignment(eq(alice.getId()), eq("Self-Assessment Required"), anyString(), any(), any());
    }

    @Test
    void anEmployeeWithoutAWorkingManagerIsLeftForHrToAssign() {
        manager.setStatus(EmployeeStatus.TERMINATED);

        service.startSelfAssessmentPhase(cycle.getId());

        assertThat(saved).allSatisfy(r -> assertThat(r.getReviewer()).isNull());
    }

    @Test
    void aCycleWithNobodyInScopeCannotStart() {
        when(employees.findAll(any(Specification.class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.startSelfAssessmentPhase(cycle.getId())).isInstanceOf(BadRequestAlertException.class);
        assertThat(cycle.getPhase()).isEqualTo(ReviewCyclePhase.DRAFT);
    }

    @Test
    void theSelfAssessmentIsNarrativeAndResubmittingDoesNotCountTwice() {
        service.startSelfAssessmentPhase(cycle.getId());

        service.submitSelfAssessment(cycle.getId(), alice.getId(), selfAssessment("Shipped the billing rewrite"));
        PerformanceReviewResponse response = service.submitSelfAssessment(cycle.getId(), alice.getId(), selfAssessment("Shipped billing"));

        PerformanceReview review = reviewOf(alice);
        assertThat(review.getStatus()).isEqualTo(PerformanceReviewStatus.SELF_SUBMITTED);
        assertThat(review.getSelfAchievements()).isEqualTo("Shipped billing");
        assertThat(review.getRating()).isNull();
        assertThat(response.selfAssessment().achievements()).isEqualTo("Shipped billing");
        assertThat(cycle.getCompletedSelfAssessments()).isEqualTo(1);
        assertThat(cycle.getCompletionPercentage()).isEqualTo(50);
        verify(notifications).notifyTaskAssignment(eq(manager.getId()), eq("Self-Assessment Submitted"), anyString(), any(), any());
    }

    @Test
    void onlyEmployeesInTheCycleHaveASelfAssessment() {
        service.startSelfAssessmentPhase(cycle.getId());

        assertThatThrownBy(() -> service.submitSelfAssessment(cycle.getId(), manager.getId(), selfAssessment("Not my review"))
        ).hasMessageContaining("No review");
    }

    @Test
    void onlyTheAssignedReviewerWritesTheManagerReview() {
        startManagerReview();

        assertThatThrownBy(() -> service.submitManagerReview(cycle.getId(), bob.getId(), false, managerReview(alice, 4))).isInstanceOf(
            AccessDeniedException.class
        );
        assertThat(reviewOf(alice).getManagerSubmittedAt()).isNull();
    }

    @Test
    void theManagerReviewSetsTheRatingAndHrMayWriteItForAnyone() {
        startManagerReview();

        service.submitManagerReview(cycle.getId(), manager.getId(), false, managerReview(alice, 4));
        service.submitManagerReview(cycle.getId(), UUID.randomUUID(), true, managerReview(bob, 3));

        assertThat(reviewOf(alice).getRating()).isEqualTo(4);
        assertThat(reviewOf(alice).getManagerRating()).isEqualTo(4);
        assertThat(reviewOf(alice).getStatus()).isEqualTo(PerformanceReviewStatus.MANAGER_SUBMITTED);
        assertThat(reviewOf(bob).getRating()).isEqualTo(3);
        assertThat(cycle.getCompletedManagerReviews()).isEqualTo(2);
    }

    @Test
    void afterTheManagerReviewTheEmployeeCanNoLongerChangeTheirSelfAssessment() {
        startManagerReview();
        service.submitManagerReview(cycle.getId(), manager.getId(), false, managerReview(alice, 4));

        assertThatThrownBy(() -> service.submitSelfAssessment(cycle.getId(), alice.getId(), selfAssessment("Late"))).hasMessageContaining(
            "already completed"
        );
    }

    @Test
    void theEmployeeDoesNotSeeTheRatingUntilTheReviewIsShared() {
        startManagerReview();
        service.submitManagerReview(cycle.getId(), manager.getId(), false, managerReview(alice, 4));
        when(reviews.findByEmployeeIdAndCycleActiveTrueOrderByReviewDateDesc(alice.getId())).thenReturn(List.of(reviewOf(alice)));

        PerformanceReviewResponse beforeMeeting = service.getMyCycleReviews(alice.getId()).get(0);
        assertThat(beforeMeeting.rating()).isNull();
        assertThat(beforeMeeting.comments()).isNull();
        assertThat(beforeMeeting.managerAssessment()).isNull();

        service.startFeedbackDeliveryPhase(cycle.getId());
        service.recordFeedbackMeeting(cycle.getId(), alice.getId(), manager.getId(), false, "Discussed in 1:1");

        PerformanceReviewResponse afterMeeting = service.getMyCycleReviews(alice.getId()).get(0);
        assertThat(afterMeeting.status()).isEqualTo(PerformanceReviewStatus.SHARED);
        assertThat(afterMeeting.rating()).isEqualTo(4);
        assertThat(afterMeeting.managerAssessment().strengths()).isEqualTo("Ownership");
        assertThat(afterMeeting.feedbackNotes()).isEqualTo("Discussed in 1:1");
    }

    @Test
    void aSharedReviewStillKeepsCalibrationAndPromotionDetailsFromTheEmployee() {
        cycle.setCalibrationDeadline(LocalDate.of(2026, 12, 5));
        startManagerReview();
        service.submitManagerReview(
            cycle.getId(),
            manager.getId(),
            false,
            new ManagerReviewRequest(alice.getId(), 4, "Ownership", null, null, null, true, "Ready for senior")
        );
        service.startCalibrationPhase(cycle.getId());
        service.calibrateReview(cycle.getId(), reviewOf(alice).getId(), hrUser, new CalibrateReviewRequest(3, "Aligned with peers"));
        service.startFeedbackDeliveryPhase(cycle.getId());
        service.recordFeedbackMeeting(cycle.getId(), alice.getId(), manager.getId(), false, null);
        when(reviews.findByEmployeeIdAndCycleActiveTrueOrderByReviewDateDesc(alice.getId())).thenReturn(List.of(reviewOf(alice)));
        when(reviews.findByReviewerIdAndCycleActiveTrueOrderByReviewDateDesc(manager.getId())).thenReturn(List.of(reviewOf(alice)));

        PerformanceReviewResponse seenByAlice = service.getMyCycleReviews(alice.getId()).get(0);
        PerformanceReviewResponse.ManagerAssessment seenByManager = service.getReviewsToWrite(manager.getId()).get(0).managerAssessment();

        assertThat(seenByAlice.rating()).isEqualTo(3);
        assertThat(seenByAlice.managerAssessment().rating()).isNull();
        assertThat(seenByAlice.managerAssessment().calibrationNote()).isNull();
        assertThat(seenByAlice.managerAssessment().recommendForPromotion()).isNull();
        assertThat(seenByAlice.managerAssessment().promotionJustification()).isNull();
        assertThat(seenByManager.rating()).isEqualTo(4);
        assertThat(seenByManager.calibrationNote()).isEqualTo("Aligned with peers");
        assertThat(seenByManager.recommendForPromotion()).isTrue();
    }

    @Test
    void aReviewPageIsForTheEmployeeTheReviewerAndHrOnly() {
        startManagerReview();
        service.submitManagerReview(cycle.getId(), manager.getId(), false, managerReview(alice, 4));
        UUID reviewId = reviewOf(alice).getId();

        PerformanceReviewResponse seenByAlice = service.getReview(reviewId, alice.getId(), true);
        PerformanceReviewResponse seenByManager = service.getReview(reviewId, manager.getId(), false);
        PerformanceReviewResponse seenByHr = service.getReview(reviewId, hrUser, true);

        assertThat(seenByAlice.managerAssessment()).isNull();
        assertThat(seenByAlice.rating()).isNull();
        assertThat(seenByManager.managerAssessment().rating()).isEqualTo(4);
        assertThat(seenByHr.managerAssessment().rating()).isEqualTo(4);
        assertThat(seenByManager.phaseDeadline()).isEqualTo(cycle.getManagerReviewDeadline());
        assertThatThrownBy(() -> service.getReview(reviewId, bob.getId(), false)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nobodyActsOnTheirOwnReviewEvenWithHrRights() {
        cycle.setCalibrationDeadline(LocalDate.of(2026, 12, 5));
        startManagerReview();
        UUID reviewId = reviewOf(alice).getId();

        assertThatThrownBy(() -> service.submitManagerReview(cycle.getId(), alice.getId(), true, managerReview(alice, 5))).isInstanceOf(
            AccessDeniedException.class
        );
        assertThatThrownBy(() -> service.assignReviewer(cycle.getId(), reviewId, alice.getId(), bob.getId())).isInstanceOf(
            AccessDeniedException.class
        );

        service.submitManagerReview(cycle.getId(), manager.getId(), false, managerReview(alice, 3));
        service.startCalibrationPhase(cycle.getId());
        assertThatThrownBy(() ->
            service.calibrateReview(cycle.getId(), reviewId, alice.getId(), new CalibrateReviewRequest(5, "Self-promotion"))
        ).isInstanceOf(AccessDeniedException.class);
        assertThat(reviewOf(alice).getRating()).isEqualTo(3);
    }

    @Test
    void calibrationIsOptional() {
        startManagerReview();

        service.startFeedbackDeliveryPhase(cycle.getId());

        assertThat(cycle.getPhase()).isEqualTo(ReviewCyclePhase.FEEDBACK_DELIVERY);
    }

    @Test
    void aCalibratedRatingKeepsTheReviewersRatingAndNeedsAReason() {
        cycle.setCalibrationDeadline(LocalDate.of(2026, 12, 5));
        startManagerReview();
        service.submitManagerReview(cycle.getId(), manager.getId(), false, managerReview(alice, 4));
        service.startCalibrationPhase(cycle.getId());
        UUID reviewId = reviewOf(alice).getId();

        assertThatThrownBy(() -> service.calibrateReview(cycle.getId(), reviewId, hrUser, new CalibrateReviewRequest(3, " "))
        ).hasMessageContaining("Explain");

        service.calibrateReview(cycle.getId(), reviewId, hrUser, new CalibrateReviewRequest(3, "Aligned with peers in the same level"));
        assertThat(reviewOf(alice).getRating()).isEqualTo(3);
        assertThat(reviewOf(alice).getManagerRating()).isEqualTo(4);
        assertThat(reviewOf(alice).getCalibrationNote()).isEqualTo("Aligned with peers in the same level");

        service.calibrateReview(cycle.getId(), reviewId, hrUser, new CalibrateReviewRequest(4, null));
        assertThat(reviewOf(alice).getCalibrationNote()).isNull();
    }

    @Test
    void closingTheCycleSharesFinishedReviewsOnly() {
        startManagerReview();
        service.submitManagerReview(cycle.getId(), manager.getId(), false, managerReview(alice, 4));
        service.startFeedbackDeliveryPhase(cycle.getId());

        service.closeCycle(cycle.getId());

        assertThat(cycle.getPhase()).isEqualTo(ReviewCyclePhase.COMPLETED);
        assertThat(cycle.getActive()).isFalse();
        assertThat(reviewOf(alice).getStatus()).isEqualTo(PerformanceReviewStatus.SHARED);
        assertThat(reviewOf(alice).getSharedAt()).isNotNull();
        assertThat(reviewOf(bob).getStatus()).isEqualTo(PerformanceReviewStatus.SELF_SUBMITTED);
        assertThat(cycle.getDeliveredFeedbacks()).isEqualTo(1);
    }

    @Test
    void aNewPhaseClosesThePreviousDeadlineAndDueDatesEndAtMidnightCompanyTime() {
        WorkflowInstance workflow = new WorkflowInstance();
        workflow.setId(UUID.randomUUID());
        cycle.setWorkflowInstance(workflow);
        WorkflowDeadline previous = new WorkflowDeadline();
        previous.setId(UUID.randomUUID());
        when(deadlines.getIncompleteDeadlines(workflow.getId())).thenReturn(List.of(previous));

        service.startSelfAssessmentPhase(cycle.getId());

        verify(deadlines).completeDeadline(previous.getId());
        Instant endOfDeadlineDay = LocalDate.of(2026, 11, 16).atStartOfDay(ZoneId.of("Europe/Berlin")).toInstant();
        verify(deadlines).registerDeadline(eq(workflow.getId()), eq("SELF_ASSESSMENT"), anyString(), eq(endOfDeadlineDay), anyInt(), any());
    }

    @Test
    void deadlinesMustFollowThePhaseOrder() {
        InitiateReviewCycleRequest request = new InitiateReviewCycleRequest(
            "2027 Mid-year",
            null,
            LocalDate.of(2027, 1, 1),
            LocalDate.of(2027, 6, 30),
            LocalDate.of(2027, 7, 1),
            LocalDate.of(2027, 8, 31),
            LocalDate.of(2027, 7, 20),
            LocalDate.of(2027, 7, 10),
            null,
            null,
            Set.of(),
            true,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        assertThatThrownBy(() -> service.initiateCycle(request)).hasMessageContaining("Manager review deadline can't be before");
        verify(cycles, never()).save(any());
    }

    @Test
    void reassigningOrWritingAReviewUpdatesItsFeedbackRequests() {
        startManagerReview();
        PerformanceReview review = reviewOf(alice);

        service.assignReviewer(cycle.getId(), review.getId(), hrUser, bob.getId());
        verify(feedback).onReviewerChanged(review, manager, hrUser);

        service.submitManagerReview(cycle.getId(), bob.getId(), false, managerReview(alice, 4));
        verify(feedback).closeOpenRequests(review);
    }

    @Test
    void theEmployeeAcknowledgesTheSharedReviewOnce() {
        UUID reviewId = shareAlicesReview(4);

        assertThatThrownBy(() -> service.acknowledgeReview(reviewId, manager.getId(), new AcknowledgeReviewRequest(null, false))
        ).isInstanceOf(AccessDeniedException.class);
        PerformanceReviewResponse acknowledged = service.acknowledgeReview(
            reviewId,
            alice.getId(),
            new AcknowledgeReviewRequest("Thanks", false)
        );

        assertThat(acknowledged.acknowledgement().comment()).isEqualTo("Thanks");
        assertThat(acknowledged.acknowledgement().disputed()).isFalse();
        verify(notifications).notifyTaskCompleted(
            eq(manager.getId()),
            eq("Performance Review Acknowledged"),
            anyString(),
            any(),
            anyString()
        );
        assertThatThrownBy(() -> service.acknowledgeReview(reviewId, alice.getId(), new AcknowledgeReviewRequest(null, false))
        ).hasMessageContaining("already acknowledged");
        // The meeting notes are locked from then on.
        assertThatThrownBy(() -> service.recordFeedbackMeeting(cycle.getId(), alice.getId(), manager.getId(), false, "Edited")
        ).hasMessageContaining("acknowledged");
    }

    @Test
    void aReviewIsAcknowledgedOnlyOnceShared() {
        startManagerReview();
        service.submitManagerReview(cycle.getId(), manager.getId(), false, managerReview(alice, 4));

        assertThatThrownBy(() ->
            service.acknowledgeReview(reviewOf(alice).getId(), alice.getId(), new AcknowledgeReviewRequest(null, false))
        ).hasMessageContaining("hasn't been shared");
    }

    @Test
    void aDisputeNeedsAReasonAndIsResolvedByHrOnly() {
        UUID reviewId = shareAlicesReview(2);

        assertThatThrownBy(() -> service.acknowledgeReview(reviewId, alice.getId(), new AcknowledgeReviewRequest(" ", true))
        ).hasMessageContaining("disagree");
        service.acknowledgeReview(reviewId, alice.getId(), new AcknowledgeReviewRequest("The Q3 launch isn't mentioned", true));
        verify(notifications).notifyTaskAssignment(eq(manager.getId()), eq("Performance Review Disputed"), anyString(), any(), anyString());

        ResolveDisputeRequest resolution = new ResolveDisputeRequest("Reviewed with the manager; Q3 counted", 3);
        assertThatThrownBy(() -> service.resolveDispute(reviewId, alice.getId(), resolution)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.resolveDispute(reviewId, manager.getId(), resolution)).isInstanceOf(AccessDeniedException.class);

        PerformanceReviewResponse resolved = service.resolveDispute(reviewId, hrUser, resolution);

        assertThat(resolved.rating()).isEqualTo(3);
        assertThat(resolved.acknowledgement().resolution()).isEqualTo("Reviewed with the manager; Q3 counted");
        assertThat(resolved.acknowledgement().resolvedAt()).isNotNull();
        ArgumentCaptor<ReviewRatingChange> change = ArgumentCaptor.forClass(ReviewRatingChange.class);
        verify(ratingChanges).save(change.capture());
        assertThat(change.getValue().getFromRating()).isEqualTo(2);
        assertThat(change.getValue().getToRating()).isEqualTo(3);
        assertThat(change.getValue().getSource()).isEqualTo(RatingChangeSource.DISPUTE_RESOLUTION);
        assertThatThrownBy(() -> service.resolveDispute(reviewId, hrUser, resolution)).hasMessageContaining("already resolved");
    }

    @Test
    void anUndisputedReviewHasNothingToResolve() {
        UUID reviewId = shareAlicesReview(4);
        service.acknowledgeReview(reviewId, alice.getId(), new AcknowledgeReviewRequest(null, false));

        assertThatThrownBy(() -> service.resolveDispute(reviewId, hrUser, new ResolveDisputeRequest("Nothing", null))).hasMessageContaining(
            "isn't disputed"
        );
    }

    @Test
    void calibrationChangesAreLogged() {
        cycle.setCalibrationDeadline(LocalDate.of(2026, 12, 5));
        startManagerReview();
        service.submitManagerReview(cycle.getId(), manager.getId(), false, managerReview(alice, 4));
        service.startCalibrationPhase(cycle.getId());
        UUID reviewId = reviewOf(alice).getId();

        service.calibrateReview(cycle.getId(), reviewId, hrUser, new CalibrateReviewRequest(3, "Aligned with peers"));
        service.calibrateReview(cycle.getId(), reviewId, hrUser, new CalibrateReviewRequest(3, "Same rating, better note"));

        ArgumentCaptor<ReviewRatingChange> change = ArgumentCaptor.forClass(ReviewRatingChange.class);
        verify(ratingChanges).save(change.capture());
        assertThat(change.getValue().getSource()).isEqualTo(RatingChangeSource.CALIBRATION);
        assertThat(change.getValue().getReason()).isEqualTo("Aligned with peers");
    }

    @Test
    void aCycleRequiringSignOffSendsTheWrittenReviewToTheReviewersManager() {
        Employee director = requireSignOffBy(employee("Dana", "Director", null));
        cycle.setCalibrationDeadline(LocalDate.of(2026, 12, 5));
        startManagerReview();

        service.submitManagerReview(cycle.getId(), manager.getId(), false, managerReview(alice, 4));

        PerformanceReview review = reviewOf(alice);
        assertThat(review.getSignOffStatus()).isEqualTo(SignOffStatus.PENDING);
        ArgumentCaptor<SubmitApprovalRequest> sent = ArgumentCaptor.forClass(SubmitApprovalRequest.class);
        verify(approvals).submitForApproval(sent.capture());
        assertThat(sent.getValue().approvalType()).isEqualTo(ApprovalType.PERFORMANCE_REVIEW);
        assertThat(sent.getValue().entityId()).isEqualTo(review.getId());
        assertThat(sent.getValue().requestorId()).isEqualTo(manager.getId());
        assertThat(sent.getValue().comments()).contains("Maria Manager's review of Alice Analyst");

        service.startCalibrationPhase(cycle.getId());
        assertThatThrownBy(() -> service.calibrateReview(cycle.getId(), review.getId(), hrUser, new CalibrateReviewRequest(3, "Peers"))
        ).hasMessageContaining("waiting for its sign-off");

        signOff(review, SignOffStatus.APPROVED, director);
        service.calibrateReview(cycle.getId(), review.getId(), hrUser, new CalibrateReviewRequest(3, "Aligned with peers"));
        assertThat(review.getRating()).isEqualTo(3);
    }

    @Test
    void aReturnedReviewIsRevisedUntilFeedbackDeliveryAndSignedOffAgain() {
        Employee director = requireSignOffBy(employee("Dana", "Director", null));
        startManagerReview();
        service.submitManagerReview(cycle.getId(), manager.getId(), false, managerReview(alice, 5));
        PerformanceReview review = reviewOf(alice);
        signOff(review, SignOffStatus.RETURNED, director);
        service.startFeedbackDeliveryPhase(cycle.getId());

        assertThatThrownBy(() -> service.recordFeedbackMeeting(cycle.getId(), alice.getId(), manager.getId(), false, "Discussed")
        ).hasMessageContaining("returned");

        service.submitManagerReview(cycle.getId(), manager.getId(), false, managerReview(alice, 4));

        assertThat(review.getSignOffStatus()).isEqualTo(SignOffStatus.PENDING);
        assertThat(review.getSignOffComment()).isNull();
        assertThat(review.getRating()).isEqualTo(4);
        verify(approvals, times(2)).submitForApproval(any());
        verify(approvals, times(2)).closePendingForEntity(review.getId(), ApprovalType.PERFORMANCE_REVIEW, "revised by the reviewer");

        // Only a returned review reopens after manager review.
        assertThatThrownBy(() -> service.submitManagerReview(cycle.getId(), manager.getId(), false, managerReview(bob, 3))
        ).hasMessageContaining("manager review phase");
    }

    @Test
    void withNobodyAboveTheReviewerTheSignOffIsWaived() {
        cycle.setRequiresSignOff(true);
        when(approvals.firstApprover(eq(ApprovalType.PERFORMANCE_REVIEW), any())).thenReturn(Optional.empty());
        startManagerReview();

        service.submitManagerReview(cycle.getId(), manager.getId(), false, managerReview(alice, 4));
        service.startFeedbackDeliveryPhase(cycle.getId());
        service.recordFeedbackMeeting(cycle.getId(), alice.getId(), manager.getId(), false, "Discussed");

        assertThat(reviewOf(alice).getSignOffStatus()).isEqualTo(SignOffStatus.WAIVED);
        assertThat(reviewOf(alice).getStatus()).isEqualTo(PerformanceReviewStatus.SHARED);
        verify(approvals, never()).submitForApproval(any());
    }

    @Test
    void closingTheCycleKeepsReviewsWaitingOnSignOffUnshared() {
        requireSignOffBy(employee("Dana", "Director", null));
        startManagerReview();
        service.submitManagerReview(cycle.getId(), manager.getId(), false, managerReview(alice, 4));
        service.startFeedbackDeliveryPhase(cycle.getId());

        service.closeCycle(cycle.getId());

        assertThat(reviewOf(alice).getStatus()).isEqualTo(PerformanceReviewStatus.MANAGER_SUBMITTED);
        assertThat(reviewOf(alice).getSharedAt()).isNull();
        verify(approvals).closePendingForEntity(reviewOf(alice).getId(), ApprovalType.PERFORMANCE_REVIEW, "closed with the review cycle");
    }

    @Test
    void theApproverReadsTheReviewAndTheEmployeeNeverSeesTheSignOff() {
        Employee director = requireSignOffBy(employee("Dana", "Director", null));
        startManagerReview();
        service.submitManagerReview(cycle.getId(), manager.getId(), false, managerReview(alice, 4));
        UUID reviewId = reviewOf(alice).getId();
        when(
            approvalRequests.existsByEntityIdAndApprovalTypeAndApproverId(reviewId, ApprovalType.PERFORMANCE_REVIEW, director.getId())
        ).thenReturn(true);

        PerformanceReviewResponse seenByDirector = service.getReview(reviewId, director.getId(), false);
        assertThat(seenByDirector.managerAssessment().rating()).isEqualTo(4);
        assertThat(seenByDirector.signOff().status()).isEqualTo(SignOffStatus.PENDING);

        assertThat(service.getReview(reviewId, alice.getId(), false).signOff()).isNull();
        assertThatThrownBy(() -> service.getSignOff(reviewId, alice.getId(), true)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getReview(reviewId, bob.getId(), false)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void potentialIsSetByTheReviewerAdjustedInCalibrationAndNeverShownToTheEmployee() {
        cycle.setCalibrationDeadline(LocalDate.of(2026, 12, 5));
        startManagerReview();
        ManagerReviewRequest written = managerReview(alice, 4);
        service.submitManagerReview(
            cycle.getId(),
            manager.getId(),
            false,
            new ManagerReviewRequest(
                written.employeeId(),
                written.rating(),
                written.strengths(),
                written.areasForImprovement(),
                written.managerComments(),
                written.developmentRecommendations(),
                false,
                null,
                ReviewPotential.MODERATE,
                null
            )
        );
        UUID reviewId = reviewOf(alice).getId();
        service.startCalibrationPhase(cycle.getId());

        service.calibrateReview(cycle.getId(), reviewId, hrUser, new CalibrateReviewRequest(4, null, ReviewPotential.HIGH));
        assertThat(reviewOf(alice).getPotential()).isEqualTo(ReviewPotential.HIGH);
        service.calibrateReview(cycle.getId(), reviewId, hrUser, new CalibrateReviewRequest(4, null));
        assertThat(reviewOf(alice).getPotential()).isEqualTo(ReviewPotential.HIGH);
        verify(ratingChanges, never()).save(any());

        service.startFeedbackDeliveryPhase(cycle.getId());
        service.recordFeedbackMeeting(cycle.getId(), alice.getId(), manager.getId(), false, "Discussed");
        assertThat(service.getReview(reviewId, alice.getId(), false).managerAssessment().potential()).isNull();
        assertThat(service.getReview(reviewId, hrUser, true).managerAssessment().potential()).isEqualTo(ReviewPotential.HIGH);
    }

    @Test
    void aProbationReviewNeedsARecommendationThatNeverChangesTheEmployee() {
        cycle.setType(ReviewCycleType.PROBATION);
        startManagerReview();
        ManagerReviewRequest written = managerReview(alice, 3);

        assertThatThrownBy(() -> service.submitManagerReview(cycle.getId(), manager.getId(), false, written)).hasMessageContaining(
            "Recommend confirming"
        );

        service.submitManagerReview(cycle.getId(), manager.getId(), false, probation(written, ProbationRecommendation.EXTEND));

        assertThat(reviewOf(alice).getProbationRecommendation()).isEqualTo(ProbationRecommendation.EXTEND);
        assertThat(alice.getConfirmationDate()).isNull();
        verify(employees, never()).save(any());
        UUID reviewId = reviewOf(alice).getId();
        assertThat(service.getReview(reviewId, manager.getId(), false).managerAssessment().probationRecommendation()).isEqualTo(
            ProbationRecommendation.EXTEND
        );
        assertThat(service.getReview(reviewId, alice.getId(), false).cycleType()).isEqualTo(ReviewCycleType.PROBATION);
    }

    @Test
    void aRecommendationOutsideAProbationCycleIsIgnored() {
        startManagerReview();

        service.submitManagerReview(
            cycle.getId(),
            manager.getId(),
            false,
            probation(managerReview(alice, 4), ProbationRecommendation.CONFIRM)
        );

        assertThat(reviewOf(alice).getProbationRecommendation()).isNull();
    }

    @Test
    void onlyRegularCyclesForTheSamePeopleCannotOverlap() {
        UUID sales = UUID.randomUUID();
        UUID finance = UUID.randomUUID();
        when(departments.existsById(any())).thenReturn(true);
        ReviewCycle running = new ReviewCycle();
        running.setId(UUID.randomUUID());
        running.setDepartmentIds(Set.of(sales));
        when(cycles.findActiveOverlapping(eq(ReviewCycleType.REGULAR), any(), any(), any())).thenReturn(List.of(running));

        assertThatThrownBy(() -> service.initiateCycle(cycleRequest("Sales again", ReviewCycleType.REGULAR, Set.of(sales), Set.of()))
        ).hasMessageContaining("already runs");
        assertThatThrownBy(() -> service.initiateCycle(cycleRequest("Everyone", ReviewCycleType.REGULAR, Set.of(), Set.of()))
        ).hasMessageContaining("already runs");

        assertThat(service.initiateCycle(cycleRequest("Finance", ReviewCycleType.REGULAR, Set.of(finance), Set.of())).type()).isEqualTo(
            ReviewCycleType.REGULAR
        );
        assertThat(service.initiateCycle(cycleRequest("Probation Q4", ReviewCycleType.PROBATION, Set.of(), Set.of())).type()).isEqualTo(
            ReviewCycleType.PROBATION
        );
    }

    @Test
    void anAdHocCycleReviewsNamedCurrentEmployeesOnly() {
        assertThatThrownBy(() -> service.initiateCycle(cycleRequest("Project Atlas", ReviewCycleType.AD_HOC, Set.of(), Set.of()))
        ).hasMessageContaining("Choose the employees");

        bob.setStatus(EmployeeStatus.INACTIVE);
        assertThatThrownBy(() ->
            service.initiateCycle(cycleRequest("Project Atlas", ReviewCycleType.AD_HOC, Set.of(), Set.of(alice.getId(), bob.getId())))
        ).hasMessageContaining("isn't a current employee");

        var created = service.initiateCycle(
            cycleRequest("Project Atlas", ReviewCycleType.AD_HOC, Set.of(UUID.randomUUID()), Set.of(alice.getId()))
        );
        assertThat(created.employees()).extracting(ReviewCycleResponse.CycleEmployee::id).containsExactly(alice.getId());
        assertThat(created.departmentIds()).isEmpty();
    }

    // ---- helpers ----

    @Test
    void theCycleCopiesItsTemplateWhenSelfAssessmentOpensSoLaterEditsDoNotChangeIt() {
        Competency ownership = competency("Ownership");
        ReviewTemplate template = new ReviewTemplate();
        template.setName("Annual review");
        template.getCompetencies().add(ownership);
        template.getQuestions().add(new ReviewQuestionDefinition(ReviewQuestionAudience.EMPLOYEE, "Proudest achievement?", null, true));
        cycle.setTemplate(template);

        service.startSelfAssessmentPhase(cycle.getId());

        ArgumentCaptor<ReviewCycleCompetency> copied = ArgumentCaptor.forClass(ReviewCycleCompetency.class);
        verify(cycleCompetencies).save(copied.capture());
        ArgumentCaptor<ReviewCycleQuestion> asked = ArgumentCaptor.forClass(ReviewCycleQuestion.class);
        verify(cycleQuestions).save(asked.capture());
        ownership.setName("Accountability");
        ownership.setAnchors(RatingAnchors.of(List.of("Changed")));
        assertThat(copied.getValue().getCycle()).isEqualTo(cycle);
        assertThat(copied.getValue().getName()).isEqualTo("Ownership");
        assertThat(copied.getValue().getAnchors().toList()).containsExactly(
            "Avoids decisions",
            null,
            "Owns their work",
            null,
            "Owns outcomes"
        );
        assertThat(asked.getValue().getDefinition().getText()).isEqualTo("Proudest achievement?");
    }

    @Test
    void requiredQuestionsMustBeAnsweredAndEveryCompetencyRated() {
        ReviewCycleCompetency ownership = cycleCompetency("Ownership", 0);
        ReviewCycleQuestion proudest = cycleQuestion(ReviewQuestionAudience.EMPLOYEE, "Proudest achievement?", true, 0);
        ReviewCycleQuestion nextStep = cycleQuestion(ReviewQuestionAudience.REVIEWER, "Next step?", false, 1);
        when(cycleCompetencies.findByCycleIdOrderBySortOrderAsc(cycle.getId())).thenReturn(List.of(ownership));
        when(cycleQuestions.findByCycleIdOrderBySortOrderAsc(cycle.getId())).thenReturn(List.of(proudest, nextStep));
        service.startSelfAssessmentPhase(cycle.getId());

        assertThatThrownBy(() -> service.submitSelfAssessment(cycle.getId(), alice.getId(), selfAssessment("Shipped billing"))
        ).hasMessageContaining("Proudest achievement?");
        assertThatThrownBy(() ->
            service.submitSelfAssessment(cycle.getId(), alice.getId(), selfAssessment("Shipped billing", answer(nextStep, "Lead billing")))
        ).hasMessageContaining("isn't yours");

        service.submitSelfAssessment(
            cycle.getId(),
            alice.getId(),
            selfAssessment("Shipped billing", answer(proudest, "  The billing rewrite "))
        );
        ArgumentCaptor<ReviewQuestionAnswer> saved = ArgumentCaptor.forClass(ReviewQuestionAnswer.class);
        verify(answers).save(saved.capture());
        assertThat(saved.getValue().getQuestion()).isEqualTo(proudest);
        assertThat(saved.getValue().getAnswer()).isEqualTo("The billing rewrite");

        service.startManagerReviewPhase(cycle.getId());
        assertThatThrownBy(() -> service.submitManagerReview(cycle.getId(), manager.getId(), false, managerReview(alice, 4))
        ).hasMessageContaining("Ownership is missing");
        assertThat(reviewOf(alice).getManagerSubmittedAt()).isNull();

        service.submitManagerReview(
            cycle.getId(),
            manager.getId(),
            false,
            withForm(managerReview(alice, 4), List.of(new CompetencyRatingInput(ownership.getId(), 5, "Owns outcomes")), List.of())
        );
        ArgumentCaptor<ReviewCompetencyRating> rated = ArgumentCaptor.forClass(ReviewCompetencyRating.class);
        verify(competencyRatings).save(rated.capture());
        assertThat(rated.getValue().getCompetency()).isEqualTo(ownership);
        assertThat(rated.getValue().getRating()).isEqualTo(5);
        assertThat(reviewOf(alice).getRating()).isEqualTo(4); // the overall rating stays the reviewer's own
    }

    @Test
    void theEmployeeSeesTheReviewersRatingsAndAnswersOnlyOnceTheReviewIsShared() {
        ReviewCycleCompetency ownership = cycleCompetency("Ownership", 0);
        ReviewCycleQuestion proudest = cycleQuestion(ReviewQuestionAudience.EMPLOYEE, "Proudest achievement?", false, 0);
        ReviewCycleQuestion nextStep = cycleQuestion(ReviewQuestionAudience.REVIEWER, "Next step?", false, 1);
        when(cycleCompetencies.findByCycleIdOrderBySortOrderAsc(cycle.getId())).thenReturn(List.of(ownership));
        when(cycleQuestions.findByCycleIdOrderBySortOrderAsc(cycle.getId())).thenReturn(List.of(proudest, nextStep));
        startManagerReview();
        PerformanceReview review = reviewOf(alice);
        ReviewQuestionAnswer hers = new ReviewQuestionAnswer(review, proudest);
        hers.setAnswer("The billing rewrite");
        ReviewQuestionAnswer reviewers = new ReviewQuestionAnswer(review, nextStep);
        reviewers.setAnswer("Lead the payments team");
        ReviewCompetencyRating rating = new ReviewCompetencyRating(review, ownership);
        rating.setRating(5);
        when(answers.findByReviewId(review.getId())).thenReturn(List.of(hers, reviewers));
        when(competencyRatings.findByReviewId(review.getId())).thenReturn(List.of(rating));
        review.setStatus(PerformanceReviewStatus.MANAGER_SUBMITTED);

        ReviewForm beforeSharing = service.getReview(review.getId(), alice.getId(), false).form();
        assertThat(beforeSharing.competencies()).extracting(ReviewForm.FormCompetency::name).containsExactly("Ownership");
        assertThat(beforeSharing.answers()).extracting(ReviewForm.Answer::questionId).containsExactly(proudest.getId());
        assertThat(beforeSharing.ratings()).isEmpty();
        assertThat(service.getReview(review.getId(), manager.getId(), false).form().ratings()).hasSize(1);

        review.setStatus(PerformanceReviewStatus.SHARED);
        ReviewForm shared = service.getReview(review.getId(), alice.getId(), false).form();
        assertThat(shared.answers()).extracting(ReviewForm.Answer::questionId).containsExactly(proudest.getId(), nextStep.getId());
        assertThat(shared.ratings()).extracting(ReviewForm.CompetencyRating::rating).containsExactly(5);
    }

    @Test
    void aCycleWithoutATemplateKeepsTheStandardForm() {
        startManagerReview();

        assertThat(service.getReview(reviewOf(alice).getId(), manager.getId(), false).form()).isNull();
        verify(cycleCompetencies, never()).save(any());
    }

    @Test
    void goalsDueInThePeriodAppearWithoutATemplateAndEachMustBeRated() {
        Goal billing = goal(alice, "Ship billing", 60, LocalDate.of(2026, 9, 30));
        Goal hiring = goal(alice, "Hire two engineers", 40, LocalDate.of(2026, 6, 30));
        when(
            goals.findByEmployeeIdAndStatusInAndDueDateBetweenOrderByDueDateAscTitleAsc(eq(alice.getId()), any(), any(), any())
        ).thenReturn(List.of(hiring, billing));
        startManagerReview();
        UUID reviewId = reviewOf(alice).getId();

        ReviewForm form = service.getReview(reviewId, manager.getId(), false).form();
        assertThat(form.competencies()).isEmpty();
        assertThat(form.goals()).extracting(ReviewForm.FormGoal::title).containsExactly("Hire two engineers", "Ship billing");
        assertThat(service.getReview(reviewOf(bob).getId(), manager.getId(), false).form()).isNull();

        assertThatThrownBy(() ->
            service.submitManagerReview(
                cycle.getId(),
                manager.getId(),
                false,
                withGoals(managerReview(alice, 4), rate(billing, GoalAchievement.MET))
            )
        ).hasMessageContaining("Hire two engineers is missing");
        assertThatThrownBy(() ->
            service.submitManagerReview(
                cycle.getId(),
                manager.getId(),
                false,
                withGoals(
                    managerReview(alice, 4),
                    rate(billing, GoalAchievement.MET),
                    rate(hiring, GoalAchievement.MET),
                    rate(goal(bob, "Other", null, LocalDate.of(2026, 5, 1)), GoalAchievement.MET)
                )
            )
        ).hasMessageContaining("isn't part of the review");

        service.submitManagerReview(
            cycle.getId(),
            manager.getId(),
            false,
            withGoals(managerReview(alice, 4), rate(billing, GoalAchievement.EXCEEDED), rate(hiring, GoalAchievement.PARTIALLY_MET))
        );
        ArgumentCaptor<ReviewGoalRating> rated = ArgumentCaptor.forClass(ReviewGoalRating.class);
        verify(goalRatings, times(2)).save(rated.capture());
        assertThat(rated.getAllValues())
            .extracting(ReviewGoalRating::getGoalTitle, ReviewGoalRating::getGoalWeight, ReviewGoalRating::getAchievement)
            .containsExactlyInAnyOrder(
                tuple("Ship billing", 60, GoalAchievement.EXCEEDED),
                tuple("Hire two engineers", 40, GoalAchievement.PARTIALLY_MET)
            );
        assertThat(reviewOf(alice).getRating()).isEqualTo(4); // goals guide the rating, they don't set it
    }

    @Test
    void submittedGoalRatingsAreFrozenAndShownToTheEmployeeOnlyOnceShared() {
        Goal billing = goal(alice, "Ship billing", 100, LocalDate.of(2026, 9, 30));
        Goal late = goal(alice, "Added after submission", null, LocalDate.of(2026, 10, 30));
        startManagerReview();
        PerformanceReview review = reviewOf(alice);
        ReviewGoalRating rating = new ReviewGoalRating(review, billing);
        rating.setGoalTitle("Ship billing v1");
        rating.setGoalWeight(100);
        rating.setAchievement(GoalAchievement.MET);
        rating.setComment("On time");
        billing.setTitle("Ship billing v2"); // renamed after the review: the review keeps what it rated
        when(goalRatings.findByReviewId(review.getId())).thenReturn(List.of(rating));
        when(
            goals.findByEmployeeIdAndStatusInAndDueDateBetweenOrderByDueDateAscTitleAsc(eq(alice.getId()), any(), any(), any())
        ).thenReturn(List.of(billing, late));
        review.setStatus(PerformanceReviewStatus.MANAGER_SUBMITTED);
        review.setManagerSubmittedAt(Instant.now());

        ReviewForm reviewers = service.getReview(review.getId(), manager.getId(), false).form();
        assertThat(reviewers.goals()).extracting(ReviewForm.FormGoal::title).containsExactly("Ship billing v1");
        ReviewForm hers = service.getReview(review.getId(), alice.getId(), false).form();
        assertThat(hers.goals())
            .extracting(ReviewForm.FormGoal::achievement, ReviewForm.FormGoal::comment)
            .containsExactly(tuple(null, null));

        review.setStatus(PerformanceReviewStatus.SHARED);
        assertThat(service.getReview(review.getId(), alice.getId(), false).form().goals())
            .extracting(ReviewForm.FormGoal::achievement, ReviewForm.FormGoal::comment)
            .containsExactly(tuple(GoalAchievement.MET, "On time"));
    }

    @Test
    void resubmittingRatesTheGoalsOfTheFirstSubmissionEvenWhenAnotherIsAgreedSince() {
        Goal billing = goal(alice, "Ship billing", null, LocalDate.of(2026, 9, 30));
        Goal later = goal(alice, "Agreed after the review", null, LocalDate.of(2026, 10, 30));
        List<Goal> inPeriod = new ArrayList<>(List.of(billing));
        List<ReviewGoalRating> stored = new ArrayList<>();
        when(
            goals.findByEmployeeIdAndStatusInAndDueDateBetweenOrderByDueDateAscTitleAsc(eq(alice.getId()), any(), any(), any())
        ).thenAnswer(inv -> List.copyOf(inPeriod));
        when(goalRatings.findByReviewId(any())).thenAnswer(inv -> List.copyOf(stored));
        when(goalRatings.save(any())).thenAnswer(inv -> {
            ReviewGoalRating r = inv.getArgument(0);
            if (!stored.contains(r)) stored.add(r);
            return r;
        });
        startManagerReview();
        service.submitManagerReview(
            cycle.getId(),
            manager.getId(),
            false,
            withGoals(managerReview(alice, 4), rate(billing, GoalAchievement.MET))
        );

        inPeriod.add(later);
        service.submitManagerReview(
            cycle.getId(),
            manager.getId(),
            false,
            withGoals(managerReview(alice, 3), rate(billing, GoalAchievement.PARTIALLY_MET))
        );

        assertThat(reviewOf(alice).getRating()).isEqualTo(3);
        assertThat(stored).singleElement().satisfies(r -> assertThat(r.getAchievement()).isEqualTo(GoalAchievement.PARTIALLY_MET));
        assertThat(service.getReview(reviewOf(alice).getId(), manager.getId(), false).form().goals())
            .extracting(ReviewForm.FormGoal::title)
            .containsExactly("Ship billing");
    }

    @Test
    void afterFeedbackMeetingsTheCycleMayGoThroughGoalSettingBeforeClosing() {
        startManagerReview();
        service.submitManagerReview(cycle.getId(), manager.getId(), false, managerReview(alice, 4));
        service.submitManagerReview(cycle.getId(), manager.getId(), false, managerReview(bob, 3));
        service.startFeedbackDeliveryPhase(cycle.getId());
        service.recordFeedbackMeeting(cycle.getId(), alice.getId(), manager.getId(), false, "Discussed");

        service.startGoalSettingPhase(cycle.getId());
        assertThat(cycle.getPhase()).isEqualTo(ReviewCyclePhase.GOAL_SETTING);
        service.recordFeedbackMeeting(cycle.getId(), bob.getId(), manager.getId(), false, "Discussed late");
        service.closeCycle(cycle.getId());

        assertThat(cycle.getPhase()).isEqualTo(ReviewCyclePhase.COMPLETED);
        assertThat(reviewOf(bob).getStatus()).isEqualTo(PerformanceReviewStatus.SHARED);
    }

    private static Goal goal(Employee employee, String title, Integer weight, LocalDate dueDate) {
        Goal g = new Goal();
        g.setId(UUID.randomUUID());
        g.setEmployee(employee);
        g.setTitle(title);
        g.setWeight(weight);
        g.setStartDate(LocalDate.of(2026, 1, 1));
        g.setDueDate(dueDate);
        g.setStatus(GoalStatus.ACTIVE);
        return g;
    }

    private static GoalRatingInput rate(Goal goal, GoalAchievement achievement) {
        return new GoalRatingInput(goal.getId(), achievement, null);
    }

    private static ManagerReviewRequest withGoals(ManagerReviewRequest r, GoalRatingInput... ratings) {
        return new ManagerReviewRequest(
            r.employeeId(),
            r.rating(),
            r.strengths(),
            r.areasForImprovement(),
            r.managerComments(),
            r.developmentRecommendations(),
            r.recommendForPromotion(),
            r.promotionJustification(),
            r.potential(),
            r.probationRecommendation(),
            r.competencyRatings(),
            r.answers(),
            List.of(ratings)
        );
    }

    private static Competency competency(String name) {
        Competency c = new Competency();
        c.setId(UUID.randomUUID());
        c.setName(name);
        c.setCategory(CompetencyCategory.CORE);
        c.setAnchors(RatingAnchors.of(List.of("Avoids decisions", "", "Owns their work", " ", "Owns outcomes")));
        return c;
    }

    private ReviewCycleCompetency cycleCompetency(String name, int order) {
        ReviewCycleCompetency copy = ReviewCycleCompetency.of(cycle, competency(name), order);
        copy.setId(UUID.randomUUID());
        return copy;
    }

    private ReviewCycleQuestion cycleQuestion(ReviewQuestionAudience audience, String text, boolean required, int order) {
        ReviewCycleQuestion copy = ReviewCycleQuestion.of(cycle, new ReviewQuestionDefinition(audience, text, null, required), order);
        copy.setId(UUID.randomUUID());
        return copy;
    }

    private static ReviewAnswerInput answer(ReviewCycleQuestion question, String text) {
        return new ReviewAnswerInput(question.getId(), text);
    }

    private static SelfAssessmentRequest selfAssessment(String achievements, ReviewAnswerInput... answers) {
        return new SelfAssessmentRequest(achievements, "Scope changes", "Lead a project", null, List.of(answers));
    }

    private static ManagerReviewRequest withForm(
        ManagerReviewRequest r,
        List<CompetencyRatingInput> ratings,
        List<ReviewAnswerInput> answers
    ) {
        return new ManagerReviewRequest(
            r.employeeId(),
            r.rating(),
            r.strengths(),
            r.areasForImprovement(),
            r.managerComments(),
            r.developmentRecommendations(),
            r.recommendForPromotion(),
            r.promotionJustification(),
            r.potential(),
            r.probationRecommendation(),
            ratings,
            answers
        );
    }

    private static ManagerReviewRequest probation(ManagerReviewRequest r, ProbationRecommendation recommendation) {
        return new ManagerReviewRequest(
            r.employeeId(),
            r.rating(),
            r.strengths(),
            r.areasForImprovement(),
            r.managerComments(),
            r.developmentRecommendations(),
            r.recommendForPromotion(),
            r.promotionJustification(),
            null,
            recommendation
        );
    }

    private static InitiateReviewCycleRequest cycleRequest(String name, ReviewCycleType type, Set<UUID> departments, Set<UUID> people) {
        return new InitiateReviewCycleRequest(
            name,
            null,
            LocalDate.of(2027, 1, 1),
            LocalDate.of(2027, 6, 30),
            LocalDate.of(2027, 7, 1),
            LocalDate.of(2027, 8, 31),
            LocalDate.of(2027, 7, 10),
            LocalDate.of(2027, 7, 20),
            null,
            LocalDate.of(2027, 8, 20),
            departments,
            departments.isEmpty(),
            null,
            null,
            null,
            null,
            type,
            people,
            null
        );
    }

    // ==================== Cycle membership ====================

    @Test
    void anExcludedReviewLeavesProgressTheReviewersQueueAndItsFeedbackAndCantBeSubmitted() {
        service.startSelfAssessmentPhase(cycle.getId());
        PerformanceReview bobs = reviewOf(bob);
        when(reviews.findByReviewerIdAndCycleActiveTrueOrderByReviewDateDesc(manager.getId())).thenAnswer(inv -> List.copyOf(saved));

        assertThatThrownBy(() -> service.excludeReview(cycle.getId(), bobs.getId(), hrUser, " ")).hasMessageContaining("Say why");
        service.excludeReview(cycle.getId(), bobs.getId(), hrUser, "On parental leave until March");

        assertThat(bobs.getStatus()).isEqualTo(PerformanceReviewStatus.EXCLUDED);
        assertThat(bobs.getExclusionReason()).isEqualTo("On parental leave until March");
        assertThat(bobs.getExcludedAt()).isNotNull();
        assertThat(cycle.getTotalEmployees()).isEqualTo(1);
        verify(feedback).closeOpenRequests(bobs);
        verify(notifications).notifyTaskCompleted(
            eq(manager.getId()),
            eq("Performance Review No Longer Needed"),
            anyString(),
            any(),
            any()
        );
        assertThat(service.getReviewsToWrite(manager.getId()))
            .extracting(PerformanceReviewResponse::employeeId)
            .containsExactly(alice.getId());
        assertThatThrownBy(() -> service.submitSelfAssessment(cycle.getId(), bob.getId(), selfAssessment("Too late"))).hasMessageContaining(
            "taken out of the cycle"
        );
        assertThatThrownBy(() -> service.excludeReview(cycle.getId(), bobs.getId(), hrUser, "Again")).hasMessageContaining(
            "already excluded"
        );
    }

    @Test
    void nobodyExcludesTheirOwnReviewAndASharedReviewStaysIn() {
        UUID reviewId = shareAlicesReview(4);

        assertThatThrownBy(() -> service.excludeReview(cycle.getId(), reviewId, alice.getId(), "Mine")).isInstanceOf(
            AccessDeniedException.class
        );
        assertThatThrownBy(() -> service.excludeReview(cycle.getId(), reviewId, hrUser, "Left")).hasMessageContaining("already shared");
    }

    @Test
    void excludingClosesAPendingSignOffAndPuttingBackAsksForItAgain() {
        requireSignOffBy(employee("Dana", "Director", null));
        startManagerReview();
        service.submitManagerReview(cycle.getId(), manager.getId(), false, managerReview(bob, 3));
        PerformanceReview bobs = reviewOf(bob);
        assertThat(bobs.getSignOffStatus()).isEqualTo(SignOffStatus.PENDING);

        service.excludeReview(cycle.getId(), bobs.getId(), hrUser, "Long-term sick leave");

        verify(approvals).closePendingForEntity(bobs.getId(), ApprovalType.PERFORMANCE_REVIEW, "review excluded from the cycle");
        assertThat(bobs.getSignOffStatus()).isNull();

        service.restoreReview(cycle.getId(), bobs.getId(), hrUser);

        assertThat(bobs.getStatus()).isEqualTo(PerformanceReviewStatus.MANAGER_SUBMITTED);
        assertThat(bobs.getExcludedAt()).isNull();
        assertThat(bobs.getExclusionReason()).isNull();
        assertThat(bobs.getSignOffStatus()).isEqualTo(SignOffStatus.PENDING);
        assertThat(cycle.getTotalEmployees()).isEqualTo(2);
    }

    @Test
    void aReviewGoesBackWhereItWasAndOnlyWhileItCanStillBeFinished() {
        startManagerReview();
        PerformanceReview alices = reviewOf(alice);
        service.excludeReview(cycle.getId(), alices.getId(), hrUser, "Joined by mistake");

        service.restoreReview(cycle.getId(), alices.getId(), hrUser);
        assertThat(alices.getStatus()).isEqualTo(PerformanceReviewStatus.SELF_SUBMITTED);

        service.excludeReview(cycle.getId(), alices.getId(), hrUser, "Joined by mistake");
        service.submitManagerReview(cycle.getId(), manager.getId(), false, managerReview(bob, 3));
        service.startFeedbackDeliveryPhase(cycle.getId());
        assertThatThrownBy(() -> service.restoreReview(cycle.getId(), alices.getId(), hrUser)).hasMessageContaining(
            "until manager reviews end"
        );

        alice.setStatus(EmployeeStatus.TERMINATED);
        assertThatThrownBy(() -> service.restoreReview(cycle.getId(), alices.getId(), hrUser)).hasMessageContaining("no longer works here");
    }

    @Test
    void hrAddsEligiblePeopleUntilManagerReviewsEnd() {
        Employee carol = employee("Carol", "Newhire", manager);
        Employee dave = employee("Dave", "Elsewhere", null);
        service.startSelfAssessmentPhase(cycle.getId());

        // In scope: Carol (the query is the cycle's scope, minus those already in it).
        when(employees.findAll(any(Specification.class))).thenReturn(List.of(carol));
        doReturn(List.of(alice)).when(employees).findAllById(any());
        assertThatThrownBy(() -> service.addEmployees(cycle.getId(), hrUser, Set.of(alice.getId()))).hasMessageContaining(
            "Already in this cycle: Alice Analyst"
        );
        assertThatThrownBy(() -> service.addEmployees(cycle.getId(), carol.getId(), Set.of(carol.getId()))).isInstanceOf(
            AccessDeniedException.class
        );
        doReturn(List.of(dave)).when(employees).findAllById(any());
        assertThatThrownBy(() -> service.addEmployees(cycle.getId(), hrUser, Set.of(carol.getId(), dave.getId()))).hasMessageContaining(
            "Not eligible for this cycle: Dave Elsewhere"
        );

        List<PerformanceReviewResponse> added = service.addEmployees(cycle.getId(), hrUser, Set.of(carol.getId()));

        assertThat(added).extracting(PerformanceReviewResponse::employeeId).containsExactly(carol.getId());
        PerformanceReview carols = reviewOf(carol);
        assertThat(carols.getReviewer()).isEqualTo(manager);
        assertThat(carols.getStatus()).isEqualTo(PerformanceReviewStatus.NOT_STARTED);
        assertThat(cycle.getTotalEmployees()).isEqualTo(3);
        verify(notifications).notifyTaskAssignment(eq(carol.getId()), eq("Self-Assessment Required"), anyString(), any(), any());

        service.startManagerReviewPhase(cycle.getId());
        service.submitManagerReview(cycle.getId(), manager.getId(), false, managerReview(alice, 3));
        service.startFeedbackDeliveryPhase(cycle.getId());
        assertThatThrownBy(() -> service.addEmployees(cycle.getId(), hrUser, Set.of(dave.getId()))).hasMessageContaining(
            "until manager reviews end"
        );
    }

    @Test
    void anAdHocCycleNamesThePeopleAdded() {
        cycle.setType(ReviewCycleType.AD_HOC);
        cycle.setEmployeeIds(new java.util.HashSet<>(Set.of(alice.getId(), bob.getId())));
        service.startSelfAssessmentPhase(cycle.getId());
        Employee carol = employee("Carol", "Newhire", manager);
        when(employees.findAll(any(Specification.class))).thenReturn(List.of(carol));

        service.addEmployees(cycle.getId(), hrUser, Set.of(carol.getId()));

        assertThat(cycle.getEmployeeIds()).contains(carol.getId());
    }

    @Test
    void aLeaversOpenReviewIsExcludedTheReviewsTheyWroteLoseTheirReviewerAndTheirFeedbackIsCancelled() {
        service.startSelfAssessmentPhase(cycle.getId());
        PerformanceReview bobs = reviewOf(bob);
        bob.setStatus(EmployeeStatus.TERMINATED);
        when(reviews.findByEmployeeIdAndCycleActiveTrueOrderByReviewDateDesc(bob.getId())).thenReturn(List.of(bobs));

        service.onEmployeeLeft(bob.getId(), LocalDate.of(2026, 11, 20));

        assertThat(bobs.getStatus()).isEqualTo(PerformanceReviewStatus.EXCLUDED);
        assertThat(bobs.getExclusionReason()).isEqualTo("Left the company on 2026-11-20");
        assertThat(bobs.getExcludedBy()).isNull();
        verify(feedback).cancelOpenRequestsFrom(bob);
        assertThat(cycle.getTotalEmployees()).isEqualTo(1);

        // The manager leaves next: Alice's review, not yet written, needs another reviewer.
        Employee hr = employee("Hanna", "Hr", null);
        WorkflowInstance workflow = new WorkflowInstance();
        workflow.setId(UUID.randomUUID());
        cycle.setWorkflowInstance(workflow);
        WorkflowDeadline deadline = new WorkflowDeadline();
        deadline.setAssignee(hr);
        when(deadlines.getIncompleteDeadlines(workflow.getId())).thenReturn(List.of(deadline));
        manager.setStatus(EmployeeStatus.TERMINATED);
        when(reviews.findByReviewerIdAndCycleActiveTrueOrderByReviewDateDesc(manager.getId())).thenReturn(List.copyOf(saved));

        service.onEmployeeLeft(manager.getId(), null);

        PerformanceReview alices = reviewOf(alice);
        assertThat(alices.getReviewer()).isNull();
        assertThat(bobs.getReviewer()).isEqualTo(manager); // excluded: left alone
        verify(feedback).onReviewerChanged(alices, manager, null);
        verify(notifications).notifyTaskAssignment(eq(hr.getId()), eq("Reviewer Left"), contains("1 review(s)"), any(), any());
    }

    @Test
    void someoneReinstatedBeforeTheirLeavingIsHandledKeepsTheirReview() {
        service.startSelfAssessmentPhase(cycle.getId());
        when(reviews.findByEmployeeIdAndCycleActiveTrueOrderByReviewDateDesc(bob.getId())).thenReturn(List.of(reviewOf(bob)));

        service.onEmployeeLeft(bob.getId(), null);

        assertThat(reviewOf(bob).getStatus()).isEqualTo(PerformanceReviewStatus.NOT_STARTED);
        verify(feedback, never()).cancelOpenRequestsFrom(any());
    }

    @Test
    void nobodyReadsTheRatingLogOfTheirOwnReview() {
        UUID reviewId = shareAlicesReview(4);

        assertThatThrownBy(() -> service.getRatingChanges(reviewId, alice.getId())).isInstanceOf(AccessDeniedException.class);
        assertThat(service.getRatingChanges(reviewId, hrUser)).isEmpty();
    }

    // ==================== Going back a phase ====================

    @Test
    void managerReviewGoesBackToSelfAssessmentWithANewDeadlineAndRemindsWhoIsLeft() {
        relativeDates();
        service.startSelfAssessmentPhase(cycle.getId());
        service.submitSelfAssessment(cycle.getId(), alice.getId(), selfAssessment("Shipped billing"));
        assertThat(service.startManagerReviewPhase(cycle.getId()).previousPhase()).isEqualTo(ReviewCyclePhase.SELF_ASSESSMENT);
        LocalDate newDeadline = LocalDate.now().plusDays(5);

        assertThatThrownBy(() -> service.moveBackPhase(cycle.getId(), back(LocalDate.now().minusDays(1), null, null, null))
        ).hasMessageContaining("today or later");
        assertThatThrownBy(() -> service.moveBackPhase(cycle.getId(), back(cycle.getManagerReviewDeadline().plusDays(1), null, null, null))
        ).hasMessageContaining("Manager review deadline can't be before the self-assessment deadline");

        service.moveBackPhase(cycle.getId(), back(newDeadline, null, null, null));

        assertThat(cycle.getPhase()).isEqualTo(ReviewCyclePhase.SELF_ASSESSMENT);
        assertThat(cycle.getSelfAssessmentDeadline()).isEqualTo(newDeadline);
        verify(notifications).notifyDeadlineApproaching(eq(bob.getId()), eq("Self-Assessment Reminder"), anyString(), any(), any(), any());
        verify(notifications, never()).notifyDeadlineApproaching(eq(alice.getId()), anyString(), anyString(), any(), any(), any());
        verify(audit).record(eq("PERFORMANCE_CYCLE_MOVED_BACK"), eq("ReviewCycle"), eq(cycle.getId().toString()), any());
    }

    @Test
    void selfAssessmentNeverGoesBackToDraft() {
        relativeDates();
        assertThat(service.startSelfAssessmentPhase(cycle.getId()).previousPhase()).isNull();

        assertThatThrownBy(() -> service.moveBackPhase(cycle.getId(), back(LocalDate.now(), null, null, null))).hasMessageContaining(
            "can't go back to draft"
        );
    }

    @Test
    void calibrationGoesBackToManagerReviewsOnlyWhileNoRatingChanged() {
        relativeDates();
        cycle.setCalibrationDeadline(cycle.getManagerReviewDeadline().plusDays(2));
        startManagerReview();
        service.submitManagerReview(cycle.getId(), manager.getId(), false, managerReview(alice, 3));
        service.startCalibrationPhase(cycle.getId());
        service.calibrateReview(cycle.getId(), reviewOf(alice).getId(), hrUser, new CalibrateReviewRequest(4, "Raised", null));

        assertThat(service.getCycleStatus(cycle.getId()).previousPhase()).isNull();
        assertThatThrownBy(() -> service.moveBackPhase(cycle.getId(), back(null, null, null, null))).hasMessageContaining(
            "already changed in calibration"
        );

        service.calibrateReview(cycle.getId(), reviewOf(alice).getId(), hrUser, new CalibrateReviewRequest(3, null, null));
        assertThat(service.getCycleStatus(cycle.getId()).previousPhase()).isEqualTo(ReviewCyclePhase.MANAGER_REVIEW);
        service.moveBackPhase(cycle.getId(), back(null, null, null, null));

        assertThat(cycle.getPhase()).isEqualTo(ReviewCyclePhase.MANAGER_REVIEW);
    }

    @Test
    void feedbackDeliveryGoesBackToThePhaseItCameFromOnlyWhileNothingWasShared() {
        relativeDates();
        cycle.setCalibrationDeadline(cycle.getManagerReviewDeadline().plusDays(2));
        WorkflowInstance workflow = new WorkflowInstance();
        workflow.setId(UUID.randomUUID());
        cycle.setWorkflowInstance(workflow);
        startManagerReview();
        service.submitManagerReview(cycle.getId(), manager.getId(), false, managerReview(alice, 3));
        // Calibration was skipped although it has a deadline: feedback started from manager review.
        when(workflows.getWorkflowHistory(workflow.getId())).thenReturn(
            List.of(transition("SELF_ASSESSMENT", "MANAGER_REVIEW"), transition("MANAGER_REVIEW", "FEEDBACK_DELIVERY"))
        );
        ReviewCycleResponse started = service.startFeedbackDeliveryPhase(cycle.getId());
        assertThat(started.previousPhase()).isEqualTo(ReviewCyclePhase.MANAGER_REVIEW);

        service.moveBackPhase(cycle.getId(), back(null, null, null, null));
        assertThat(cycle.getPhase()).isEqualTo(ReviewCyclePhase.MANAGER_REVIEW);

        service.startFeedbackDeliveryPhase(cycle.getId());
        service.recordFeedbackMeeting(cycle.getId(), alice.getId(), manager.getId(), false, "Discussed");
        assertThat(service.getCycleStatus(cycle.getId()).previousPhase()).isNull();
        assertThatThrownBy(() -> service.moveBackPhase(cycle.getId(), back(null, null, null, null))).hasMessageContaining("already shared");
    }

    @Test
    void goalSettingGoesBackToFeedbackDelivery() {
        relativeDates();
        shareAlicesReview(4);
        service.startGoalSettingPhase(cycle.getId());

        service.moveBackPhase(cycle.getId(), back(null, null, null, null));

        assertThat(cycle.getPhase()).isEqualTo(ReviewCyclePhase.FEEDBACK_DELIVERY);
    }

    /** Deadlines from today on, so going back can reopen a phase whatever day the tests run. */
    private void relativeDates() {
        LocalDate today = LocalDate.now();
        cycle.setStartDate(today.minusDays(10));
        cycle.setSelfAssessmentDeadline(today.plusDays(10));
        cycle.setManagerReviewDeadline(today.plusDays(20));
        cycle.setFeedbackDeadline(today.plusDays(40));
        cycle.setEndDate(today.plusDays(60));
    }

    private static MoveBackPhaseRequest back(LocalDate self, LocalDate manager, LocalDate calibration, LocalDate feedback) {
        return new MoveBackPhaseRequest("Started too early", self, manager, calibration, feedback);
    }

    private static WorkflowStateTransition transition(String from, String to) {
        WorkflowStateTransition t = new WorkflowStateTransition();
        t.setFromState(from);
        t.setToState(to);
        return t;
    }

    /** The cycle requires sign-off and {@code approver} is the one to give it; returns the approver. */
    private Employee requireSignOffBy(Employee approver) {
        cycle.setRequiresSignOff(true);
        when(approvals.firstApprover(eq(ApprovalType.PERFORMANCE_REVIEW), any())).thenReturn(Optional.of(approver));
        return approver;
    }

    /** The approver's decision, as the approval workflow records it on the review. */
    private static void signOff(PerformanceReview review, SignOffStatus decision, Employee approver) {
        review.setSignOffStatus(decision);
        review.setSignOffComment(decision == SignOffStatus.RETURNED ? "The rating needs evidence" : null);
        review.setSignOffDecidedBy(approver);
        review.setSignOffDecidedAt(Instant.now());
    }

    /** Alice's review written with {@code rating} and shared in the feedback meeting; returns its id. */
    private UUID shareAlicesReview(int rating) {
        startManagerReview();
        service.submitManagerReview(cycle.getId(), manager.getId(), false, managerReview(alice, rating));
        service.startFeedbackDeliveryPhase(cycle.getId());
        service.recordFeedbackMeeting(cycle.getId(), alice.getId(), manager.getId(), false, "Discussed");
        return reviewOf(alice).getId();
    }

    private void startManagerReview() {
        service.startSelfAssessmentPhase(cycle.getId());
        service.submitSelfAssessment(cycle.getId(), alice.getId(), selfAssessment("Shipped billing"));
        service.submitSelfAssessment(cycle.getId(), bob.getId(), selfAssessment("Ran the migration"));
        service.startManagerReviewPhase(cycle.getId());
    }

    private java.util.stream.Stream<PerformanceReview> included() {
        return saved.stream().filter(r -> r.getStatus() != PerformanceReviewStatus.EXCLUDED);
    }

    private PerformanceReview reviewOf(Employee employee) {
        return saved.stream().filter(r -> r.getEmployee().equals(employee)).findFirst().orElseThrow();
    }

    private static SelfAssessmentRequest selfAssessment(String achievements) {
        return new SelfAssessmentRequest(achievements, "Scope changes", "Lead a project", null);
    }

    private static ManagerReviewRequest managerReview(Employee employee, int rating) {
        return new ManagerReviewRequest(
            employee.getId(),
            rating,
            "Ownership",
            "Delegation",
            "Strong year",
            "Leadership course",
            false,
            null
        );
    }

    private static Employee employee(String firstName, String lastName, Employee manager) {
        Employee e = new Employee();
        e.setId(UUID.randomUUID());
        e.setFirstName(firstName);
        e.setLastName(lastName);
        e.setStatus(EmployeeStatus.ACTIVE);
        e.setManager(manager);
        return e;
    }
}
