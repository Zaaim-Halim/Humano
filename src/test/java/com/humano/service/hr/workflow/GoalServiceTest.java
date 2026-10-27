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
import com.humano.domain.enumeration.hr.GoalStatus;
import com.humano.domain.hr.Goal;
import com.humano.domain.hr.GoalCheckIn;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.workflow.requests.CancelGoalRequest;
import com.humano.dto.hr.workflow.requests.GoalCheckInRequest;
import com.humano.dto.hr.workflow.requests.GoalRequest;
import com.humano.dto.hr.workflow.responses.GoalResponse;
import com.humano.repository.hr.GoalCheckInRepository;
import com.humano.repository.hr.GoalRepository;
import com.humano.repository.hr.PerformanceReviewRepository;
import com.humano.repository.hr.ReviewGoalRatingRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.BadRequestAlertException;
import com.humano.service.hr.workflow.GoalService.Actor;
import com.humano.service.hr.workflow.infrastructure.NotificationOrchestrationService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

/** Goals follow the reporting line: the employee proposes, their manager (or HR) agrees and steers. */
class GoalServiceTest {

    private final GoalRepository goals = mock(GoalRepository.class);
    private final GoalCheckInRepository checkIns = mock(GoalCheckInRepository.class);
    private final ReviewGoalRatingRepository goalRatings = mock(ReviewGoalRatingRepository.class);
    private final PerformanceReviewRepository reviews = mock(PerformanceReviewRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final NotificationOrchestrationService notifications = mock(NotificationOrchestrationService.class);
    private final GoalService service = new GoalService(goals, checkIns, goalRatings, reviews, employees, notifications);

    private final Employee manager = employee("Maria", "Manager", null);
    private final Employee alice = employee("Alice", "Analyst", manager);
    private final Employee peer = employee("Paul", "Peer", manager);

    private final Actor asAlice = new Actor(alice.getId(), false, false);
    private final Actor asManager = new Actor(manager.getId(), false, false);
    private final Actor asPeer = new Actor(peer.getId(), false, false);
    private final Actor asHr = new Actor(UUID.randomUUID(), true, true);

    @BeforeEach
    void setUp() {
        when(goals.save(any())).thenAnswer(inv -> {
            Goal g = inv.getArgument(0);
            if (g.getId() == null) g.setId(UUID.randomUUID());
            return g;
        });
        when(employees.findById(any())).thenAnswer(inv ->
            Optional.ofNullable(
                List.of(manager, alice, peer).stream().filter(e -> e.getId().equals(inv.getArgument(0))).findFirst().orElse(null)
            )
        );
    }

    @Test
    void anEmployeeProposesAndTheirManagerAgrees() {
        GoalResponse proposed = service.create(request(null, "Ship billing"), asAlice);
        assertThat(proposed.status()).isEqualTo(GoalStatus.DRAFT);
        assertThat(proposed.can().agree()).isFalse(); // nobody agrees their own goal
        verify(notifications).notifyTaskAssignment(eq(manager.getId()), anyString(), anyString(), eq(proposed.id()), eq("Goal"));

        Goal goal = stored(proposed);
        assertThatThrownBy(() -> service.agree(goal.getId(), asAlice)).isInstanceOf(AccessDeniedException.class);
        GoalResponse agreed = service.agree(goal.getId(), asManager);
        assertThat(agreed.status()).isEqualTo(GoalStatus.ACTIVE);
        assertThat(agreed.agreedByName()).isEqualTo("Maria Manager");

        assertThatThrownBy(() -> service.update(goal.getId(), request(null, "Ship less"), asAlice)).hasMessageContaining("manager");
        assertThat(service.update(goal.getId(), request(null, "Ship billing and invoicing"), asManager).title()).isEqualTo(
            "Ship billing and invoicing"
        );
    }

    @Test
    void aGoalSetByTheManagerIsAgreedAtOnce() {
        GoalResponse set = service.create(request(alice.getId(), "Hire two engineers"), asManager);

        assertThat(set.status()).isEqualTo(GoalStatus.ACTIVE);
        verify(notifications).notifyTaskAssignment(eq(alice.getId()), anyString(), anyString(), eq(set.id()), eq("Goal"));
    }

    @Test
    void goalRightsComeFromTheReportingLineNotFromARole() {
        // Paul shares Alice's manager but isn't hers; a goals role (MANAGE_GOALS) isn't consulted at all.
        assertThatThrownBy(() -> service.create(request(alice.getId(), "Do my work"), asPeer)).isInstanceOf(AccessDeniedException.class);
        Goal goal = activeGoal(alice);
        assertThatThrownBy(() -> service.cancel(goal.getId(), new CancelGoalRequest("No"), asPeer)).isInstanceOf(
            AccessDeniedException.class
        );
        assertThatThrownBy(() -> service.ofEmployee(alice.getId(), asPeer)).isInstanceOf(AccessDeniedException.class);

        when(reviews.existsByEmployeeIdAndReviewerIdAndCycleIsNotNull(alice.getId(), peer.getId())).thenReturn(true);
        assertThat(service.ofEmployee(alice.getId(), asPeer)).isEmpty(); // her reviewer in a cycle may read them

        assertThat(service.create(request(alice.getId(), "Close the audit"), asHr).status()).isEqualTo(GoalStatus.ACTIVE);
    }

    @Test
    void checkInsAreAppendedOnAgreedGoalsOnly() {
        Goal draft = goal(alice, GoalStatus.DRAFT);
        assertThatThrownBy(() -> service.checkIn(draft.getId(), new GoalCheckInRequest(30, null), asAlice)).hasMessageContaining("agreed");

        Goal goal = activeGoal(alice);
        GoalResponse after = service.checkIn(goal.getId(), new GoalCheckInRequest(40, "  Halfway on invoicing "), asAlice);

        assertThat(after.progress()).isEqualTo(40);
        ArgumentCaptor<GoalCheckIn> entry = ArgumentCaptor.forClass(GoalCheckIn.class);
        verify(checkIns).save(entry.capture());
        assertThat(entry.getValue().getAuthor()).isEqualTo(alice);
        assertThat(entry.getValue().getComment()).isEqualTo("Halfway on invoicing");
        assertThatThrownBy(() -> service.checkIn(goal.getId(), new GoalCheckInRequest(50, null), asPeer)).isInstanceOf(
            AccessDeniedException.class
        );
    }

    @Test
    void aCancelledGoalNeedsTheManagerAndAnAgreedGoalIsNotDeleted() {
        Goal goal = activeGoal(alice);
        assertThatThrownBy(() -> service.cancel(goal.getId(), new CancelGoalRequest("Moved on"), asAlice)).isInstanceOf(
            AccessDeniedException.class
        );
        assertThatThrownBy(() -> service.delete(goal.getId(), asManager)).hasMessageContaining("cancelled, not deleted");

        GoalResponse cancelled = service.cancel(goal.getId(), new CancelGoalRequest(" Priorities changed "), asManager);
        assertThat(cancelled.status()).isEqualTo(GoalStatus.CANCELLED);
        assertThat(cancelled.cancelReason()).isEqualTo("Priorities changed");
        assertThatThrownBy(() -> service.update(goal.getId(), request(null, "Back on"), asManager)).hasMessageContaining(
            "no longer change"
        );
    }

    @Test
    void aGoalRatedInAReviewIsLocked() {
        Goal goal = activeGoal(alice);
        when(goalRatings.existsByGoalId(goal.getId())).thenReturn(true);
        when(goalRatings.findRatedGoalIds(any())).thenReturn(Set.of(goal.getId()));
        when(goals.findByEmployeeIdOrderByDueDateAscCreatedDateAsc(alice.getId())).thenReturn(List.of(goal));

        assertThatThrownBy(() -> service.update(goal.getId(), request(null, "Rewrite history"), asManager)).isInstanceOf(
            BadRequestAlertException.class
        );
        assertThatThrownBy(() -> service.cancel(goal.getId(), new CancelGoalRequest("Too late"), asManager)).hasMessageContaining(
            "rated in a review"
        );
        GoalResponse listed = service.ofEmployee(alice.getId(), asManager).get(0);
        assertThat(listed.locked()).isTrue();
        assertThat(listed.can().edit()).isFalse();
        assertThat(listed.can().cancel()).isFalse();
        assertThat(listed.can().checkIn()).isTrue(); // progress can still be recorded
    }

    @Test
    void aGoalIsNotDueBeforeItStarts() {
        GoalRequest backwards = new GoalRequest(null, "Ship billing", null, null, 50, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 5, 1));

        assertThatThrownBy(() -> service.create(backwards, asAlice)).hasMessageContaining("due before it starts");
        verify(goals, never()).save(any());
    }

    private Goal stored(GoalResponse response) {
        ArgumentCaptor<Goal> saved = ArgumentCaptor.forClass(Goal.class);
        verify(goals).save(saved.capture());
        Goal goal = saved.getValue();
        when(goals.findById(response.id())).thenReturn(Optional.of(goal));
        return goal;
    }

    private Goal activeGoal(Employee employee) {
        return goal(employee, GoalStatus.ACTIVE);
    }

    private Goal goal(Employee employee, GoalStatus status) {
        Goal g = new Goal();
        g.setId(UUID.randomUUID());
        g.setEmployee(employee);
        g.setTitle("Ship billing");
        g.setStartDate(LocalDate.of(2026, 1, 1));
        g.setDueDate(LocalDate.of(2026, 9, 30));
        g.setStatus(status);
        when(goals.findById(g.getId())).thenReturn(Optional.of(g));
        return g;
    }

    private static GoalRequest request(UUID employeeId, String title) {
        return new GoalRequest(employeeId, title, null, "Live in production", 50, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 30));
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
