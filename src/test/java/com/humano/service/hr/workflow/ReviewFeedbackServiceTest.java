package com.humano.service.hr.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.domain.enumeration.hr.FeedbackRelationship;
import com.humano.domain.enumeration.hr.FeedbackRequestStatus;
import com.humano.domain.enumeration.hr.FeedbackVisibility;
import com.humano.domain.enumeration.hr.PerformanceReviewStatus;
import com.humano.domain.enumeration.hr.ReviewCyclePhase;
import com.humano.domain.hr.PerformanceReview;
import com.humano.domain.hr.ReviewCycle;
import com.humano.domain.hr.ReviewFeedbackRequest;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.workflow.requests.RequestFeedbackRequest;
import com.humano.dto.hr.workflow.requests.SubmitFeedbackRequest;
import com.humano.dto.hr.workflow.responses.FeedbackCandidate;
import com.humano.dto.hr.workflow.responses.ReviewFeedbackCount;
import com.humano.dto.hr.workflow.responses.ReviewFeedbackOverview;
import com.humano.dto.hr.workflow.responses.ReviewFeedbackResponse;
import com.humano.repository.hr.PerformanceReviewRepository;
import com.humano.repository.hr.ReviewFeedbackRequestRepository;
import com.humano.repository.hr.projection.FeedbackCountRow;
import com.humano.repository.hr.workflow.ApprovalRequestRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.BadRequestAlertException;
import com.humano.service.hr.workflow.infrastructure.NotificationOrchestrationService;
import com.humano.service.payroll.OrganizationSettingsService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

/** Colleagues give feedback on a review; the reviewer reads it, the employee only as the cycle allows. */
class ReviewFeedbackServiceTest {

    private final ReviewFeedbackRequestRepository requests = mock(ReviewFeedbackRequestRepository.class);
    private final PerformanceReviewRepository reviews = mock(PerformanceReviewRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final NotificationOrchestrationService notifications = mock(NotificationOrchestrationService.class);
    private final OrganizationSettingsService settings = mock(OrganizationSettingsService.class);
    private final ApprovalRequestRepository approvals = mock(ApprovalRequestRepository.class);

    private final ReviewFeedbackService service = new ReviewFeedbackService(
        requests,
        reviews,
        employees,
        notifications,
        settings,
        approvals
    );

    private final List<ReviewFeedbackRequest> store = new ArrayList<>();
    private final ZoneId zone = ZoneId.of("Europe/Berlin");

    private final Employee manager = employee("Maria", "Manager");
    private final Employee alice = employee("Alice", "Analyst");
    private final Employee peer1 = employee("Paul", "Peer");
    private final Employee peer2 = employee("Petra", "Peer");
    private final Employee peer3 = employee("Pia", "Peer");
    private final Employee peer4 = employee("Pete", "Peer");
    private final UUID hrUser = UUID.randomUUID();
    private ReviewCycle cycle;
    private PerformanceReview review;

    @BeforeEach
    void setUp() {
        cycle = new ReviewCycle();
        cycle.setId(UUID.randomUUID());
        cycle.setName("2026 Annual Review");
        cycle.setPhase(ReviewCyclePhase.SELF_ASSESSMENT);
        cycle.setManagerReviewDeadline(LocalDate.now(zone).plusDays(20));

        review = new PerformanceReview();
        review.setId(UUID.randomUUID());
        review.setCycle(cycle);
        review.setEmployee(alice);
        review.setReviewer(manager);
        review.setStatus(PerformanceReviewStatus.NOT_STARTED);

        when(settings.zone()).thenReturn(zone);
        when(reviews.findByIdAndCycleId(review.getId(), cycle.getId())).thenReturn(Optional.of(review));
        when(employees.findById(any())).thenAnswer(inv ->
            Stream.of(manager, alice, peer1, peer2, peer3, peer4).filter(e -> e.getId().equals(inv.getArgument(0))).findFirst()
        );

        when(requests.save(any())).thenAnswer(inv -> {
            ReviewFeedbackRequest r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            if (!store.contains(r)) store.add(r);
            return r;
        });
        when(requests.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(requests.findWithReviewById(any())).thenAnswer(inv ->
            store.stream().filter(r -> r.getId().equals(inv.getArgument(0))).findFirst()
        );
        when(requests.findByReviewIdOrderByRequestedAtAscIdAsc(any())).thenAnswer(inv -> List.copyOf(store));
        when(requests.findByReviewIdAndStatusIn(any(), any())).thenAnswer(inv -> withStatus(inv.getArgument(1)));
        when(requests.findByReviewCycleIdAndStatusIn(any(), any())).thenAnswer(inv -> withStatus(inv.getArgument(1)));
        when(requests.countByReviewIdAndStatusIn(any(), any())).thenAnswer(inv -> (long) withStatus(inv.getArgument(1)).size());
        when(requests.existsByReviewIdAndContributorIdAndStatusIn(any(), any(), any())).thenAnswer(inv ->
            withStatus(inv.getArgument(2)).stream().anyMatch(r -> r.getContributor().getId().equals(inv.getArgument(1)))
        );
        when(requests.countByCycle(any())).thenAnswer(inv ->
            store
                .stream()
                .collect(Collectors.groupingBy(ReviewFeedbackRequest::getStatus, Collectors.counting()))
                .entrySet()
                .stream()
                .map(e -> new FeedbackCountRow(review.getId(), e.getKey(), e.getValue()))
                .toList()
        );
    }

    @Test
    void theReviewerAsksAColleagueDirectly() {
        ReviewFeedbackResponse response = service.request(cycle.getId(), review.getId(), manager.getId(), false, ask(peer1));

        assertThat(response.status()).isEqualTo(FeedbackRequestStatus.REQUESTED);
        assertThat(response.dueDate()).isEqualTo(cycle.getManagerReviewDeadline().minusDays(3));
        assertThat(response.requestedById()).isEqualTo(manager.getId());
        verify(notifications).notifyTaskAssignment(eq(peer1.getId()), eq("Feedback Requested"), anyString(), any(), anyString());
    }

    @Test
    void anEmployeeNominatesAndTheReviewerApproves() {
        ReviewFeedbackResponse nomination = service.request(cycle.getId(), review.getId(), alice.getId(), false, ask(peer1));
        assertThat(nomination.status()).isEqualTo(FeedbackRequestStatus.PENDING_APPROVAL);
        verify(notifications).notifyApprovalRequired(eq(manager.getId()), anyString(), anyString(), any(), anyString());

        assertThatThrownBy(() -> service.approve(nomination.id(), alice.getId(), true)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.approve(nomination.id(), peer2.getId(), false)).isInstanceOf(AccessDeniedException.class);

        assertThat(service.approve(nomination.id(), manager.getId(), false).status()).isEqualTo(FeedbackRequestStatus.REQUESTED);
        verify(notifications).notifyTaskAssignment(eq(peer1.getId()), eq("Feedback Requested"), anyString(), any(), anyString());
    }

    @Test
    void anEmployeeWithdrawsOnlyTheirOwnPendingNominations() {
        ReviewFeedbackResponse nomination = service.request(cycle.getId(), review.getId(), alice.getId(), false, ask(peer1));
        ReviewFeedbackResponse asked = service.request(cycle.getId(), review.getId(), manager.getId(), false, ask(peer2));

        assertThatThrownBy(() -> service.cancel(asked.id(), alice.getId(), false)).isInstanceOf(AccessDeniedException.class);
        assertThat(service.cancel(nomination.id(), alice.getId(), false).status()).isEqualTo(FeedbackRequestStatus.CANCELLED);
    }

    @Test
    void nominationsCanBeTurnedOffPerCycle() {
        cycle.setAllowFeedbackNominations(false);

        assertThatThrownBy(() -> service.request(cycle.getId(), review.getId(), alice.getId(), false, ask(peer1))).hasMessageContaining(
            "doesn't take nominations"
        );
        assertThat(service.request(cycle.getId(), review.getId(), manager.getId(), false, ask(peer1)).status()).isEqualTo(
            FeedbackRequestStatus.REQUESTED
        );
    }

    @Test
    void contributorsAreCurrentColleaguesOnceEachWithinTheCap() {
        assertThatThrownBy(() -> service.request(cycle.getId(), review.getId(), manager.getId(), false, ask(alice))).hasMessageContaining(
            "own review"
        );
        assertThatThrownBy(() -> service.request(cycle.getId(), review.getId(), hrUser, true, ask(manager))).hasMessageContaining(
            "reviewer writes"
        );
        peer4.setStatus(EmployeeStatus.INACTIVE);
        assertThatThrownBy(() -> service.request(cycle.getId(), review.getId(), manager.getId(), false, ask(peer4))).hasMessageContaining(
            "current employees"
        );

        service.request(cycle.getId(), review.getId(), manager.getId(), false, ask(peer1));
        assertThatThrownBy(() -> service.request(cycle.getId(), review.getId(), alice.getId(), false, ask(peer1))).hasMessageContaining(
            "already giving feedback"
        );

        cycle.setMaxContributors(2);
        service.request(cycle.getId(), review.getId(), manager.getId(), false, ask(peer2));
        assertThatThrownBy(() -> service.request(cycle.getId(), review.getId(), manager.getId(), false, ask(peer3))).hasMessageContaining(
            "already has 2 contributors"
        );
    }

    @Test
    void onlyTheReviewerOrHrAskDirectly() {
        assertThatThrownBy(() -> service.request(cycle.getId(), review.getId(), peer2.getId(), false, ask(peer1))).isInstanceOf(
            AccessDeniedException.class
        );
        assertThat(service.request(cycle.getId(), review.getId(), hrUser, true, ask(peer1)).status()).isEqualTo(
            FeedbackRequestStatus.REQUESTED
        );
    }

    @Test
    void feedbackIsCollectedUntilTheReviewIsWritten() {
        ReviewFeedbackResponse asked = service.request(cycle.getId(), review.getId(), manager.getId(), false, ask(peer1));

        assertThatThrownBy(() -> service.submit(asked.id(), peer2.getId(), feedback("Calm"))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.submit(asked.id(), peer1.getId(), new SubmitFeedbackRequest(" ", null, "Nice"))
        ).hasMessageContaining("strengths or areas");

        ReviewFeedbackResponse given = service.submit(asked.id(), peer1.getId(), feedback("Calm under pressure"));
        assertThat(given.status()).isEqualTo(FeedbackRequestStatus.SUBMITTED);
        assertThat(given.strengths()).isEqualTo("Calm under pressure");
        verify(notifications).notifyTaskCompleted(eq(manager.getId()), eq("Feedback Received"), anyString(), any(), anyString());

        review.setStatus(PerformanceReviewStatus.MANAGER_SUBMITTED);
        assertThatThrownBy(() -> service.submit(asked.id(), peer1.getId(), feedback("Revised"))).isInstanceOf(
            BadRequestAlertException.class
        );
        assertThatThrownBy(() -> service.request(cycle.getId(), review.getId(), manager.getId(), false, ask(peer2))).hasMessageContaining(
            "until the review is written"
        );
        assertThat(service.getForContributor(asked.id(), peer1.getId()).open()).isFalse();
    }

    @Test
    void aContributorDeclinesWithAReason() {
        ReviewFeedbackResponse asked = service.request(cycle.getId(), review.getId(), manager.getId(), false, ask(peer1));

        assertThatThrownBy(() -> service.decline(asked.id(), peer1.getId(), false, " ")).hasMessageContaining("why");
        ReviewFeedbackResponse declined = service.decline(asked.id(), peer1.getId(), false, "Haven't worked together this year");

        assertThat(declined.status()).isEqualTo(FeedbackRequestStatus.DECLINED);
        verify(notifications).notifyTaskAssignment(eq(manager.getId()), eq("Feedback Declined"), anyString(), any(), anyString());
    }

    @Test
    void theReviewerAndHrSeeEveryContributionWithNames() {
        giveFeedback(peer1, "Calm");
        assertThatThrownBy(() -> service.getForReview(cycle.getId(), review.getId(), peer2.getId(), false, false)).isInstanceOf(
            AccessDeniedException.class
        );

        for (UUID viewer : List.of(manager.getId(), hrUser)) {
            ReviewFeedbackOverview overview = service.getForReview(cycle.getId(), review.getId(), viewer, true, false);
            assertThat(overview.requests())
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.contributorName()).isEqualTo("Paul Peer");
                    assertThat(r.strengths()).isEqualTo("Calm");
                });
        }
        assertThat(service.getForReview(cycle.getId(), review.getId(), manager.getId(), false, false).canRequest()).isTrue();
        assertThat(service.getForReview(cycle.getId(), review.getId(), hrUser, true, false).canRequest()).isFalse();
    }

    @Test
    void theEmployeeSeesOnlyTheirNominationsUntilTheReviewIsShared() {
        cycle.setFeedbackVisibility(FeedbackVisibility.SHARED_NAMED);
        ReviewFeedbackResponse nomination = service.request(cycle.getId(), review.getId(), alice.getId(), false, ask(peer1));
        service.approve(nomination.id(), manager.getId(), false);
        service.submit(nomination.id(), peer1.getId(), feedback("Calm"));
        giveFeedback(peer2, "Thorough");

        ReviewFeedbackOverview before = service.getForReview(cycle.getId(), review.getId(), alice.getId(), false, false);
        assertThat(before.requests())
            .singleElement()
            .satisfies(r -> {
                assertThat(r.contributorId()).isEqualTo(peer1.getId());
                assertThat(r.status()).isEqualTo(FeedbackRequestStatus.SUBMITTED);
                assertThat(r.strengths()).isNull();
            });
        assertThat(before.canNominate()).isTrue();
        assertThat(before.canRequest()).isFalse();

        share();
        ReviewFeedbackOverview after = service.getForReview(cycle.getId(), review.getId(), alice.getId(), false, false);
        assertThat(after.requests()).extracting(ReviewFeedbackResponse::strengths).containsExactly("Calm", "Thorough");
        assertThat(after.requests()).extracting(ReviewFeedbackResponse::contributorName).containsExactly("Paul Peer", "Petra Peer");
    }

    @Test
    void theReviewersNoteToAContributorStaysPrivate() {
        cycle.setFeedbackVisibility(FeedbackVisibility.SHARED_NAMED);
        ReviewFeedbackResponse asked = service.request(
            cycle.getId(),
            review.getId(),
            manager.getId(),
            false,
            new RequestFeedbackRequest(peer1.getId(), FeedbackRelationship.PEER, null, "Focus on the ops escalations")
        );
        service.submit(asked.id(), peer1.getId(), feedback("Calm"));
        share();

        assertThat(service.getForReview(cycle.getId(), review.getId(), alice.getId(), false, false).requests())
            .singleElement()
            .satisfies(r -> {
                assertThat(r.strengths()).isEqualTo("Calm");
                assertThat(r.message()).isNull();
            });
        assertThat(service.getForContributor(asked.id(), peer1.getId()).message()).isEqualTo("Focus on the ops escalations");
    }

    @Test
    void colleaguesCanBeLookedUpOnlyBySomeoneWhoCanAddThemNow() {
        when(employees.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(peer1)));

        assertThat(service.candidates(cycle.getId(), review.getId(), alice.getId(), false, "pa"))
            .extracting(FeedbackCandidate::name)
            .containsExactly("Paul Peer");
        assertThatThrownBy(() -> service.candidates(cycle.getId(), review.getId(), peer2.getId(), false, "pa")).isInstanceOf(
            AccessDeniedException.class
        );
        assertThatThrownBy(() -> service.candidates(cycle.getId(), review.getId(), hrUser, false, "pa")).isInstanceOf(
            AccessDeniedException.class
        );

        cycle.setAllowFeedbackNominations(false);
        assertThatThrownBy(() -> service.candidates(cycle.getId(), review.getId(), alice.getId(), false, "pa")).isInstanceOf(
            AccessDeniedException.class
        );
        assertThat(service.candidates(cycle.getId(), review.getId(), manager.getId(), false, "pa")).hasSize(1);

        review.setStatus(PerformanceReviewStatus.MANAGER_SUBMITTED);
        assertThatThrownBy(() -> service.candidates(cycle.getId(), review.getId(), manager.getId(), false, "pa")).isInstanceOf(
            AccessDeniedException.class
        );
    }

    @Test
    void feedbackForTheManagerOnlyIsNeverShownToTheEmployee() {
        giveFeedback(peer1, "Calm");
        share();

        assertThat(service.getForReview(cycle.getId(), review.getId(), alice.getId(), false, false).requests()).isEmpty();
    }

    @Test
    void anonymousFeedbackIsShownWithoutNamesAndOnlyFromThreeAnswers() {
        cycle.setFeedbackVisibility(FeedbackVisibility.SHARED_ANONYMOUS);
        giveFeedback(peer1, "Calm");
        giveFeedback(peer2, "Thorough");
        share();

        ReviewFeedbackOverview two = service.getForReview(cycle.getId(), review.getId(), alice.getId(), false, false);
        assertThat(two.anonymous()).isTrue();
        assertThat(two.withheld()).isTrue();
        assertThat(two.requests()).isEmpty();

        review.setStatus(PerformanceReviewStatus.SELF_SUBMITTED);
        cycle.setPhase(ReviewCyclePhase.MANAGER_REVIEW);
        giveFeedback(peer3, "Asks good questions");
        share();

        ReviewFeedbackOverview three = service.getForReview(cycle.getId(), review.getId(), alice.getId(), false, false);
        assertThat(three.withheld()).isFalse();
        assertThat(three.requests())
            .hasSize(3)
            .allSatisfy(r -> {
                assertThat(r.id()).isNull();
                assertThat(r.contributorId()).isNull();
                assertThat(r.contributorName()).isNull();
                assertThat(r.relationship()).isNull();
                assertThat(r.requestedAt()).isNull();
                assertThat(r.submittedAt()).isNull();
            });
        // Not the order they answered in
        assertThat(three.requests())
            .extracting(ReviewFeedbackResponse::strengths)
            .containsExactly("Asks good questions", "Calm", "Thorough");
    }

    @Test
    void aReassignedReviewAsksThePreviousReviewer() {
        ReviewFeedbackResponse asked = service.request(cycle.getId(), review.getId(), manager.getId(), false, ask(peer1));

        review.setReviewer(peer1);
        service.onReviewerChanged(review, manager, hrUser);

        assertThat(store.stream().filter(r -> r.getId().equals(asked.id())).findFirst().orElseThrow().getStatus()).isEqualTo(
            FeedbackRequestStatus.CANCELLED
        );
        assertThat(store)
            .filteredOn(r -> r.getContributor().equals(manager))
            .singleElement()
            .satisfies(r -> {
                assertThat(r.getRelationship()).isEqualTo(FeedbackRelationship.PREVIOUS_MANAGER);
                assertThat(r.getStatus()).isEqualTo(FeedbackRequestStatus.REQUESTED);
            });
    }

    @Test
    void writingTheReviewClosesUnansweredRequests() {
        service.request(cycle.getId(), review.getId(), manager.getId(), false, ask(peer1));
        giveFeedback(peer2, "Calm");

        service.closeOpenRequests(review);

        assertThat(store)
            .extracting(ReviewFeedbackRequest::getStatus)
            .containsExactlyInAnyOrder(FeedbackRequestStatus.CANCELLED, FeedbackRequestStatus.SUBMITTED);
        assertThat(service.countsByReview(cycle.getId())).containsExactly(new ReviewFeedbackCount(review.getId(), 0, 1, 1));
    }

    // ---- helpers ----

    private void giveFeedback(Employee contributor, String strengths) {
        ReviewFeedbackResponse asked = service.request(cycle.getId(), review.getId(), manager.getId(), false, ask(contributor));
        service.submit(asked.id(), contributor.getId(), feedback(strengths));
    }

    private void share() {
        cycle.setPhase(ReviewCyclePhase.FEEDBACK_DELIVERY);
        review.setStatus(PerformanceReviewStatus.SHARED);
        review.setSharedAt(Instant.now());
    }

    private List<ReviewFeedbackRequest> withStatus(Collection<FeedbackRequestStatus> statuses) {
        return store.stream().filter(r -> statuses.contains(r.getStatus())).toList();
    }

    private static RequestFeedbackRequest ask(Employee contributor) {
        return new RequestFeedbackRequest(contributor.getId(), FeedbackRelationship.PEER, null, null);
    }

    private static SubmitFeedbackRequest feedback(String strengths) {
        return new SubmitFeedbackRequest(strengths, "Delegate more", null);
    }

    private static Employee employee(String firstName, String lastName) {
        Employee e = new Employee();
        e.setId(UUID.randomUUID());
        e.setFirstName(firstName);
        e.setLastName(lastName);
        e.setStatus(EmployeeStatus.ACTIVE);
        return e;
    }
}
