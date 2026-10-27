package com.humano.service.hr.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.domain.enumeration.hr.ImprovementPlanOutcome;
import com.humano.domain.enumeration.hr.ImprovementPlanProgress;
import com.humano.domain.enumeration.hr.ImprovementPlanStatus;
import com.humano.domain.enumeration.hr.PerformanceReviewStatus;
import com.humano.domain.hr.ImprovementPlan;
import com.humano.domain.hr.ImprovementPlanCheckIn;
import com.humano.domain.hr.PerformanceReview;
import com.humano.domain.hr.ReviewCycle;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.workflow.requests.AcknowledgeImprovementPlanRequest;
import com.humano.dto.hr.workflow.requests.CloseImprovementPlanRequest;
import com.humano.dto.hr.workflow.requests.ExtendImprovementPlanRequest;
import com.humano.dto.hr.workflow.requests.ImprovementPlanCheckInRequest;
import com.humano.dto.hr.workflow.requests.ImprovementPlanRequest;
import com.humano.dto.hr.workflow.responses.ImprovementPlanResponse;
import com.humano.repository.hr.ImprovementPlanCheckInRepository;
import com.humano.repository.hr.ImprovementPlanRepository;
import com.humano.repository.hr.PerformanceReviewRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.BadRequestAlertException;
import com.humano.service.hr.workflow.ImprovementPlanService.Actor;
import com.humano.service.hr.workflow.infrastructure.NotificationOrchestrationService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

/** Improvement plans: started deliberately from a low shared review, run by the manager side, recorded only. */
class ImprovementPlanServiceTest {

    private final ImprovementPlanRepository plans = mock(ImprovementPlanRepository.class);
    private final ImprovementPlanCheckInRepository checkIns = mock(ImprovementPlanCheckInRepository.class);
    private final PerformanceReviewRepository reviews = mock(PerformanceReviewRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final NotificationOrchestrationService notifications = mock(NotificationOrchestrationService.class);
    private final ImprovementPlanService service = new ImprovementPlanService(plans, checkIns, reviews, employees, notifications);

    private final Employee manager = employee("Maria", "Manager", null);
    private final Employee alice = employee("Alice", "Analyst", manager);
    private final Employee reviewer = employee("Rob", "Reviewer", null);

    private final Actor asManager = new Actor(manager.getId(), false);
    private final Actor asAlice = new Actor(alice.getId(), false);
    private final Actor asReviewer = new Actor(reviewer.getId(), false);
    private final Actor asHr = new Actor(UUID.randomUUID(), true);

    private PerformanceReview review;

    @BeforeEach
    void setUp() {
        review = review(alice, PerformanceReviewStatus.SHARED, 2);
        when(reviews.findByIdAndCycleIsNotNull(review.getId())).thenReturn(Optional.of(review));
        when(plans.save(any())).thenAnswer(inv -> {
            ImprovementPlan p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            when(plans.findWithDetailsById(p.getId())).thenReturn(Optional.of(p));
            return p;
        });
        when(employees.findById(any())).thenAnswer(inv ->
            Optional.ofNullable(
                List.of(manager, alice, reviewer).stream().filter(e -> e.getId().equals(inv.getArgument(0))).findFirst().orElse(null)
            )
        );
    }

    @Test
    void aPlanStartsOnlyFromASharedReviewRatedTwoOrLowerAndNeverForOneself() {
        PerformanceReview good = review(alice, PerformanceReviewStatus.SHARED, 3);
        when(reviews.findByIdAndCycleIsNotNull(good.getId())).thenReturn(Optional.of(good));
        assertThatThrownBy(() -> service.start(good.getId(), request(), asManager)).hasMessageContaining("rated 2 or lower");

        review.setStatus(PerformanceReviewStatus.MANAGER_SUBMITTED);
        assertThatThrownBy(() -> service.start(review.getId(), request(), asManager)).hasMessageContaining("shared with the employee");
        review.setStatus(PerformanceReviewStatus.SHARED);

        assertThatThrownBy(() -> service.start(review.getId(), request(), asAlice)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.start(review.getId(), request(), asReviewer)).isInstanceOf(AccessDeniedException.class);

        ImprovementPlanResponse draft = service.start(review.getId(), request(), asManager);
        assertThat(draft.status()).isEqualTo(ImprovementPlanStatus.DRAFT);
        assertThat(draft.ownerName()).isEqualTo("Maria Manager");
        assertThat(draft.objectives())
            .extracting(ImprovementPlanResponse.Objective::description)
            .containsExactly("Close tickets within SLA");
        verify(notifications, never()).notifyTaskAssignment(any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void anEmployeeHasAtMostOneOpenPlan() {
        when(plans.existsByEmployeeIdAndStatusIn(eq(alice.getId()), any())).thenReturn(true);

        assertThatThrownBy(() -> service.start(review.getId(), request(), asHr)).hasMessageContaining("already has an open");
        assertThat(service.forReview(review.getId(), asHr).canStart()).isFalse();
    }

    @Test
    void aPlanRunsThirtyToOneHundredEightyDaysWithObjectivesInsideIt() {
        LocalDate start = LocalDate.of(2026, 11, 2);
        assertThatThrownBy(() -> service.start(review.getId(), request(start, start.plusDays(14)), asManager)).hasMessageContaining(
            "30 to 180 days"
        );
        assertThatThrownBy(() -> service.start(review.getId(), request(start, start.plusDays(200)), asManager)).hasMessageContaining(
            "30 to 180 days"
        );
        ImprovementPlanRequest late = new ImprovementPlanRequest(
            "Missed SLAs",
            null,
            null,
            start,
            start.plusDays(60),
            List.of(new ImprovementPlanRequest.Objective("Close tickets", null, start.plusDays(90)))
        );
        assertThatThrownBy(() -> service.start(review.getId(), late, asManager)).hasMessageContaining("within the plan's period");
    }

    @Test
    void theDraftIsPrivateUntilSharedThenTheEmployeeAcknowledgesOnce() {
        UUID id = service.start(review.getId(), request(), asManager).id();
        assertThatThrownBy(() -> service.get(id, asAlice)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.get(id, asReviewer)).isInstanceOf(AccessDeniedException.class); // a cycle reviewer isn't told

        service.activate(id, asManager);
        verify(notifications).notifyTaskAssignment(eq(alice.getId()), anyString(), anyString(), eq(id), eq("ImprovementPlan"));
        ImprovementPlanResponse hers = service.get(id, asAlice);
        assertThat(hers.can().acknowledge()).isTrue();
        assertThat(hers.can().close()).isFalse();
        assertThatThrownBy(() -> service.update(id, request(), asManager)).hasMessageContaining("Only a draft");

        ImprovementPlanResponse acknowledged = service.acknowledge(
            id,
            new AcknowledgeImprovementPlanRequest(" I disagree with example 2 "),
            asAlice
        );
        assertThat(acknowledged.acknowledgedAt()).isNotNull();
        assertThat(acknowledged.employeeComment()).isEqualTo("I disagree with example 2");
        assertThatThrownBy(() -> service.acknowledge(id, null, asAlice)).hasMessageContaining("already acknowledged");
        assertThatThrownBy(() -> service.acknowledge(id, null, asManager)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void checkInsAreRecordedByTheManagerSideAndThePlanIsExtendedOnce() {
        UUID id = service.start(review.getId(), request(), asManager).id();
        assertThatThrownBy(() -> service.checkIn(id, checkIn(), asManager)).hasMessageContaining("running plan");
        service.activate(id, asManager);

        assertThatThrownBy(() -> service.checkIn(id, checkIn(), asAlice)).isInstanceOf(AccessDeniedException.class);
        service.checkIn(id, checkIn(), asHr);
        ArgumentCaptor<ImprovementPlanCheckIn> saved = ArgumentCaptor.forClass(ImprovementPlanCheckIn.class);
        verify(checkIns).save(saved.capture());
        assertThat(saved.getValue().getNote()).isEqualTo("Two tickets late");

        ImprovementPlan plan = plans.findWithDetailsById(id).orElseThrow();
        LocalDate end = plan.getEndDate();
        assertThatThrownBy(() -> service.extend(id, new ExtendImprovementPlanRequest(end.plusDays(120), "Leave"), asManager)
        ).hasMessageContaining("at most 90 days");
        ImprovementPlanResponse extended = service.extend(
            id,
            new ExtendImprovementPlanRequest(end.plusDays(30), " Two weeks of leave "),
            asManager
        );
        assertThat(extended.endDate()).isEqualTo(end.plusDays(30));
        assertThat(extended.originalEndDate()).isEqualTo(end);
        assertThat(extended.can().extend()).isFalse();
        assertThatThrownBy(() -> service.extend(id, new ExtendImprovementPlanRequest(end.plusDays(40), "Again"), asManager)
        ).hasMessageContaining("extended once");
    }

    @Test
    void theOutcomeIsRecordedOnlyAndUnsuccessfulWaitsForTheEndDate() {
        LocalDate start = LocalDate.now().minusDays(10);
        UUID id = service.start(review.getId(), request(start, start.plusDays(60)), asManager).id();
        service.activate(id, asManager);

        assertThatThrownBy(() ->
            service.close(id, new CloseImprovementPlanRequest(ImprovementPlanOutcome.UNSUCCESSFUL, "Not met"), asManager)
        ).hasMessageContaining("end date is reached");

        ImprovementPlanResponse closed = service.close(
            id,
            new CloseImprovementPlanRequest(ImprovementPlanOutcome.SUCCESSFUL, " All met "),
            asManager
        );
        assertThat(closed.status()).isEqualTo(ImprovementPlanStatus.CLOSED);
        assertThat(closed.outcomeNote()).isEqualTo("All met");
        assertThat(alice.getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
        verify(employees, never()).save(any());
        assertThatThrownBy(() -> service.delete(id, asManager)).hasMessageContaining("not deleted");
    }

    @Test
    void theEmployeeSeesOnlySharedPlansAndManagersNeverListTheirOwn() {
        service.start(review.getId(), request(), asManager);
        when(plans.findByEmployeeIdAndStatusInOrderByCreatedDateDesc(eq(alice.getId()), any())).thenReturn(List.of());
        assertThat(service.mine(asAlice)).isEmpty();

        ImprovementPlan hrOwn = new ImprovementPlan();
        hrOwn.setId(UUID.randomUUID());
        Employee hrPerson = employee("Hana", "Hr", null);
        hrPerson.setId(asHr.id());
        hrOwn.setEmployee(hrPerson);
        hrOwn.setReview(review(hrPerson, PerformanceReviewStatus.SHARED, 1));
        when(plans.findAllByOrderByCreatedDateDesc()).thenReturn(List.of(hrOwn));
        assertThat(service.managed(asHr)).isEmpty();
    }

    private static ImprovementPlanCheckInRequest checkIn() {
        return new ImprovementPlanCheckInRequest(ImprovementPlanProgress.SOME_PROGRESS, " Two tickets late ");
    }

    private static ImprovementPlanRequest request() {
        LocalDate start = LocalDate.of(2026, 11, 2);
        return request(start, start.plusDays(60));
    }

    private static ImprovementPlanRequest request(LocalDate start, LocalDate end) {
        return new ImprovementPlanRequest(
            "Tickets regularly miss the SLA",
            "Weekly coaching with the team lead",
            "Weekly one-to-one",
            start,
            end,
            List.of(new ImprovementPlanRequest.Objective(" Close tickets within SLA ", "95% within SLA for 4 weeks", null))
        );
    }

    private static PerformanceReview review(Employee employee, PerformanceReviewStatus status, int rating) {
        PerformanceReview r = new PerformanceReview();
        r.setId(UUID.randomUUID());
        r.setEmployee(employee);
        r.setStatus(status);
        r.setRating(rating);
        ReviewCycle cycle = new ReviewCycle();
        cycle.setId(UUID.randomUUID());
        cycle.setName("2026 Annual Review");
        r.setCycle(cycle);
        return r;
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
