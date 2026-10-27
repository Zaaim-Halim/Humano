package com.humano.service.hr.workflow;

import com.humano.domain.enumeration.hr.ApprovalType;
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
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.hr.workflow.infrastructure.NotificationOrchestrationService;
import com.humano.service.payroll.OrganizationSettingsService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Additional reviewers (multi-rater feedback) on cycle reviews.
 *
 * <h2>Who does what</h2>
 *
 * <ul>
 *   <li>The reviewer, or HR ({@code MANAGE_PERFORMANCE_REVIEWS}), asks a contributor directly
 *       ({@code REQUESTED}), approves or turns down nominations, and cancels open requests.</li>
 *   <li>The reviewed employee nominates contributors when the cycle allows it
 *       ({@code PENDING_APPROVAL} until approved), and may withdraw a nomination not yet approved.</li>
 *   <li>The contributor gives feedback (narrative only) or declines with a reason.</li>
 * </ul>
 *
 * Contributors are added during the self-assessment and manager review phases, until the reviewer
 * writes the review; feedback is accepted until then too. Writing the review closes the requests
 * still open, and so does closing the cycle. A contributor must be working and can be neither the
 * employee nor the reviewer, at most once per review, up to the cycle's {@code maxContributors}.
 *
 * <h2>Confidentiality</h2>
 *
 * The reviewer and HR see every request and every contribution, with names. The reviewed employee
 * sees the nominations they made (status only) and, once the review is shared:
 * <ul>
 *   <li>{@link FeedbackVisibility#MANAGER_ONLY}: no contributions;</li>
 *   <li>{@link FeedbackVisibility#SHARED_NAMED}: the contributions, with names;</li>
 *   <li>{@link FeedbackVisibility#SHARED_ANONYMOUS}: the contributions without anything that
 *       identifies their author, in an order unrelated to when they were given, and only when at
 *       least {@value #MIN_ANONYMOUS_RESPONSES} contributors answered.</li>
 * </ul>
 * Contributors only ever see their own request, never the review.
 */
@Service
@Transactional("tenantTransactionManager")
public class ReviewFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(ReviewFeedbackService.class);

    /** Fewest answers shown to the employee in an anonymous cycle, so no answer can be traced to its author. */
    public static final int MIN_ANONYMOUS_RESPONSES = 3;

    /** Days before the manager review deadline a request is due by default, so the reviewer has time to read it. */
    static final int DUE_DAYS_BEFORE_MANAGER_DEADLINE = 3;

    private static final int CANDIDATE_LIMIT = 10;
    private static final String ENTITY_NAME = "reviewFeedback";
    private static final String NOTIFICATION_ENTITY = "ReviewFeedbackRequest";
    private static final Set<FeedbackRequestStatus> ACTIVE = EnumSet.of(
        FeedbackRequestStatus.PENDING_APPROVAL,
        FeedbackRequestStatus.REQUESTED,
        FeedbackRequestStatus.SUBMITTED
    );
    private static final Set<FeedbackRequestStatus> OPEN = EnumSet.of(
        FeedbackRequestStatus.PENDING_APPROVAL,
        FeedbackRequestStatus.REQUESTED
    );
    /** Reviews that take no more feedback: written, or taken out of the cycle. */
    private static final Set<PerformanceReviewStatus> CLOSED_TO_FEEDBACK = EnumSet.of(
        PerformanceReviewStatus.MANAGER_SUBMITTED,
        PerformanceReviewStatus.SHARED,
        PerformanceReviewStatus.EXCLUDED
    );

    /** Order of anonymous contributions: by their text, so it says nothing about who answered when. */
    private static final Comparator<ReviewFeedbackRequest> ANONYMOUS_ORDER = Comparator.comparing(
        ReviewFeedbackRequest::getStrengths,
        Comparator.nullsFirst(String::compareTo)
    )
        .thenComparing(ReviewFeedbackRequest::getImprovements, Comparator.nullsFirst(String::compareTo))
        .thenComparing(ReviewFeedbackRequest::getComments, Comparator.nullsFirst(String::compareTo));

    private enum Viewer {
        EMPLOYEE,
        REVIEWER,
        /** An approver of the review's sign-off: reads everything, manages nothing. */
        APPROVER,
        HR,
    }

    private final ReviewFeedbackRequestRepository repository;
    private final PerformanceReviewRepository performanceReviewRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationOrchestrationService notificationService;
    private final OrganizationSettingsService organizationSettingsService;
    private final ApprovalRequestRepository approvalRequestRepository;

    public ReviewFeedbackService(
        ReviewFeedbackRequestRepository repository,
        PerformanceReviewRepository performanceReviewRepository,
        EmployeeRepository employeeRepository,
        NotificationOrchestrationService notificationService,
        OrganizationSettingsService organizationSettingsService,
        ApprovalRequestRepository approvalRequestRepository
    ) {
        this.repository = repository;
        this.performanceReviewRepository = performanceReviewRepository;
        this.employeeRepository = employeeRepository;
        this.notificationService = notificationService;
        this.organizationSettingsService = organizationSettingsService;
        this.approvalRequestRepository = approvalRequestRepository;
    }

    // ==================== Reading ====================

    /**
     * A review's feedback requests as {@code actorId} may see them (see the class comment).
     *
     * @param hrView the caller can read every review ({@code VIEW_PERFORMANCE_REVIEWS})
     * @param hrManage the caller can manage every review ({@code MANAGE_PERFORMANCE_REVIEWS})
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public ReviewFeedbackOverview getForReview(UUID cycleId, UUID reviewId, UUID actorId, boolean hrView, boolean hrManage) {
        PerformanceReview review = findReview(cycleId, reviewId);
        Viewer viewer = viewerOf(review, actorId, hrView || hrManage);
        ReviewCycle cycle = review.getCycle();
        List<ReviewFeedbackRequest> requests = repository.findByReviewIdOrderByRequestedAtAscIdAsc(reviewId);

        boolean editable = acceptsRequests(review);
        boolean manages = viewer == Viewer.REVIEWER || (viewer == Viewer.HR && hrManage);
        int active = (int) requests.stream().filter(r -> r.getStatus().isActive()).count();

        boolean anonymous = false;
        boolean withheld = false;
        List<ReviewFeedbackResponse> shown;
        if (viewer != Viewer.EMPLOYEE) {
            shown = requests.stream().map(r -> toResponse(r, true, true)).toList();
        } else {
            boolean shared = review.getStatus() == PerformanceReviewStatus.SHARED;
            FeedbackVisibility visibility = cycle.getFeedbackVisibility();
            List<ReviewFeedbackRequest> answered = requests.stream().filter(r -> r.getStatus() == FeedbackRequestStatus.SUBMITTED).toList();
            shown = new ArrayList<>();
            if (shared && visibility == FeedbackVisibility.SHARED_ANONYMOUS) {
                anonymous = true;
                withheld = !answered.isEmpty() && answered.size() < MIN_ANONYMOUS_RESPONSES;
                if (!withheld) {
                    answered.stream().sorted(ANONYMOUS_ORDER).map(ReviewFeedbackService::toAnonymousResponse).forEach(shown::add);
                }
            }
            boolean named = shared && visibility == FeedbackVisibility.SHARED_NAMED;
            for (ReviewFeedbackRequest r : requests) {
                boolean withFeedback = named && r.getStatus() == FeedbackRequestStatus.SUBMITTED;
                boolean own = isRequestedBy(r, actorId);
                if (withFeedback || own) {
                    // The reviewer's note to a contributor stays between them.
                    shown.add(toResponse(r, withFeedback, false, own));
                }
            }
        }

        return new ReviewFeedbackOverview(
            cycle.getFeedbackVisibility(),
            anonymous,
            withheld,
            MIN_ANONYMOUS_RESPONSES,
            cycle.getMaxContributors(),
            active,
            editable && manages,
            editable && viewer == Viewer.EMPLOYEE && cycle.isAllowFeedbackNominations(),
            manages,
            shown
        );
    }

    /**
     * Colleagues who could be asked for feedback on a review, by name: working, neither the
     * employee nor the reviewer, and not already on the review. Only for someone who can add a
     * contributor right now — the reviewer, HR managing reviews, or the reviewed employee when the
     * cycle takes nominations — so the reviewed employee nominates without access to the employee
     * directory, and nobody else gets a directory through it.
     *
     * @param hrManage the caller can manage every review ({@code MANAGE_PERFORMANCE_REVIEWS})
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<FeedbackCandidate> candidates(UUID cycleId, UUID reviewId, UUID actorId, boolean hrManage, String query) {
        PerformanceReview review = findReview(cycleId, reviewId);
        Viewer viewer = viewerOf(review, actorId, hrManage);
        boolean canAdd =
            acceptsRequests(review) &&
            switch (viewer) {
                case REVIEWER, HR -> true;
                case EMPLOYEE -> review.getCycle().isAllowFeedbackNominations();
                case APPROVER -> false;
            };
        if (!canAdd) {
            throw new AccessDeniedException("You can't add contributors to this review now");
        }
        Set<UUID> excluded = Stream.concat(
            Stream.of(review.getEmployee().getId(), review.getReviewer() != null ? review.getReviewer().getId() : null),
            repository.findByReviewIdAndStatusIn(reviewId, ACTIVE).stream().map(r -> r.getContributor().getId())
        )
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        String term = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        return employeeRepository
            .findAll(candidateSpec(excluded, term), PageRequest.of(0, CANDIDATE_LIMIT, Sort.by("firstName", "lastName")))
            .map(e ->
                new FeedbackCandidate(
                    e.getId(),
                    fullName(e),
                    e.getJobTitle(),
                    e.getDepartment() != null ? e.getDepartment().getName() : null
                )
            )
            .getContent();
    }

    /** Working employees not in {@code excluded} whose name contains {@code term} (lower case; empty matches all). */
    public static Specification<Employee> candidateSpec(Set<UUID> excluded, String term) {
        return (root, q, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("status").in(EmployeeStatus.WORKING));
            if (!excluded.isEmpty()) {
                predicates.add(cb.not(root.get("id").in(excluded)));
            }
            if (!term.isEmpty()) {
                predicates.add(
                    cb.like(cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), root.<String>get("lastName"))), "%" + term + "%")
                );
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    /** A request, for the contributor answering it. */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public ReviewFeedbackResponse getForContributor(UUID requestId, UUID actorId) {
        ReviewFeedbackRequest request = findRequest(requestId);
        requireContributor(request, actorId);
        return toResponse(request, true, false);
    }

    /** Requests the signed-in employee was asked to answer (or answered) in running cycles. */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<ReviewFeedbackResponse> getMyRequests(UUID contributorId) {
        return repository
            .findByContributorIdAndStatusInAndReviewCycleActiveTrueOrderByDueDateAscRequestedAtAsc(
                contributorId,
                EnumSet.of(FeedbackRequestStatus.REQUESTED, FeedbackRequestStatus.SUBMITTED)
            )
            .stream()
            .map(r -> toResponse(r, true, false))
            .toList();
    }

    /** Feedback counts of every review of a cycle that has requests (one grouped query). */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<ReviewFeedbackCount> countsByReview(UUID cycleId) {
        Map<UUID, int[]> counts = new LinkedHashMap<>();
        for (FeedbackCountRow row : repository.countByCycle(cycleId)) {
            int[] c = counts.computeIfAbsent(row.reviewId(), id -> new int[3]);
            int n = row.count().intValue();
            switch (row.status()) {
                case PENDING_APPROVAL -> c[0] += n;
                case REQUESTED -> c[1] += n;
                case SUBMITTED -> {
                    c[1] += n;
                    c[2] += n;
                }
                default -> {}
            }
        }
        return counts
            .entrySet()
            .stream()
            .map(e -> new ReviewFeedbackCount(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
            .toList();
    }

    // ==================== Requesting ====================

    /**
     * Ask {@code body.contributorId()} for feedback on a review. The reviewed employee nominates
     * (the reviewer approves it); the reviewer and HR ask directly.
     *
     * @param hrManage the caller can manage every review ({@code MANAGE_PERFORMANCE_REVIEWS})
     */
    public ReviewFeedbackResponse request(UUID cycleId, UUID reviewId, UUID actorId, boolean hrManage, RequestFeedbackRequest body) {
        log.info("Requesting feedback from {} on review {}", body.contributorId(), reviewId);

        PerformanceReview review = findReview(cycleId, reviewId);
        ReviewCycle cycle = review.getCycle();
        boolean nomination = review.getEmployee().getId().equals(actorId);
        if (nomination) {
            if (!cycle.isAllowFeedbackNominations()) {
                throw new BadRequestAlertException("This cycle doesn't take nominations", ENTITY_NAME, "nominationsoff");
            }
        } else {
            requireReviewerOrHr(review, actorId, hrManage);
        }
        requireAcceptsRequests(review);

        Employee contributor = employeeRepository
            .findById(body.contributorId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", body.contributorId()));
        requireEligibleContributor(review, contributor);
        if (repository.countByReviewIdAndStatusIn(reviewId, ACTIVE) >= cycle.getMaxContributors()) {
            throw new BadRequestAlertException(
                "This review already has " + cycle.getMaxContributors() + " contributors",
                ENTITY_NAME,
                "toomanycontributors"
            );
        }

        ReviewFeedbackRequest request = new ReviewFeedbackRequest();
        request.setReview(review);
        request.setContributor(contributor);
        request.setRelationship(body.relationship());
        request.setStatus(nomination ? FeedbackRequestStatus.PENDING_APPROVAL : FeedbackRequestStatus.REQUESTED);
        request.setRequestedBy(employeeRepository.findById(actorId).orElse(null));
        request.setRequestedAt(Instant.now());
        request.setDueDate(dueDate(cycle, body.dueDate()));
        request.setMessage(blankToNull(body.message()));
        repository.save(request);

        if (nomination) {
            if (review.getReviewer() != null) {
                notificationService.notifyApprovalRequired(
                    review.getReviewer().getId(),
                    "Feedback Nomination",
                    fullName(review.getEmployee()) + " would like feedback from " + fullName(contributor) + " for " + cycle.getName() + ".",
                    request.getId(),
                    NOTIFICATION_ENTITY
                );
            }
        } else {
            notifyAsked(request);
        }
        return toResponse(request, true, true);
    }

    /** Approve an employee's nomination: the contributor is asked. */
    public ReviewFeedbackResponse approve(UUID requestId, UUID actorId, boolean hrManage) {
        ReviewFeedbackRequest request = findRequest(requestId);
        PerformanceReview review = request.getReview();
        requireNotOwnReview(review, actorId);
        requireReviewerOrHr(review, actorId, hrManage);
        requireStatus(request, "Only a nomination awaiting approval can be approved", FeedbackRequestStatus.PENDING_APPROVAL);
        requireAcceptsRequests(review);
        // The reviewer may have changed, or the contributor left, since the nomination.
        requireEligibleContributor(review, request.getContributor(), request.getId());

        request.setStatus(FeedbackRequestStatus.REQUESTED);
        repository.save(request);
        notifyAsked(request);
        return toResponse(request, true, true);
    }

    /**
     * Decline a request. The contributor declines one they were asked (a reason is required); the
     * reviewer or HR turns down a nomination.
     */
    public ReviewFeedbackResponse decline(UUID requestId, UUID actorId, boolean hrManage, String reason) {
        ReviewFeedbackRequest request = findRequest(requestId);
        PerformanceReview review = request.getReview();
        String why = blankToNull(reason);

        if (request.getContributor().getId().equals(actorId)) {
            requireStatus(request, "Only an unanswered request can be declined", FeedbackRequestStatus.REQUESTED);
            requireOpenForFeedback(review);
            if (why == null) {
                throw new BadRequestAlertException("Say why you decline", ENTITY_NAME, "declinereason");
            }
            request.setStatus(FeedbackRequestStatus.DECLINED);
            request.setDeclineReason(why);
            repository.save(request);
            if (review.getReviewer() != null) {
                notificationService.notifyTaskAssignment(
                    review.getReviewer().getId(),
                    "Feedback Declined",
                    fullName(request.getContributor()) + " declined to give feedback on " + fullName(review.getEmployee()) + ": " + why,
                    review.getId(),
                    "PerformanceReview"
                );
            }
            return toResponse(request, true, false);
        }

        requireNotOwnReview(review, actorId);
        requireReviewerOrHr(review, actorId, hrManage);
        requireStatus(request, "Only a nomination awaiting approval can be turned down", FeedbackRequestStatus.PENDING_APPROVAL);
        request.setStatus(FeedbackRequestStatus.DECLINED);
        request.setDeclineReason(why);
        repository.save(request);
        if (isRequestedBy(request, review.getEmployee().getId())) {
            notificationService.notifyApprovalDecision(
                review.getEmployee().getId(),
                "Feedback Nomination",
                "Your nomination of " + fullName(request.getContributor()) + " was not approved." + (why != null ? " " + why : ""),
                request.getId(),
                NOTIFICATION_ENTITY,
                false
            );
        }
        return toResponse(request, true, true);
    }

    /**
     * Cancel a request nobody answered yet: the reviewer or HR cancels any, the reviewed employee
     * withdraws their own nomination while it awaits approval.
     */
    public ReviewFeedbackResponse cancel(UUID requestId, UUID actorId, boolean hrManage) {
        ReviewFeedbackRequest request = findRequest(requestId);
        PerformanceReview review = request.getReview();
        boolean employee = review.getEmployee().getId().equals(actorId);
        if (employee) {
            if (!isRequestedBy(request, actorId)) {
                throw new AccessDeniedException("You can only withdraw your own nominations");
            }
            requireStatus(request, "Only a nomination awaiting approval can be withdrawn", FeedbackRequestStatus.PENDING_APPROVAL);
        } else {
            requireReviewerOrHr(review, actorId, hrManage);
            requireStatus(
                request,
                "Only a request nobody answered can be cancelled",
                FeedbackRequestStatus.PENDING_APPROVAL,
                FeedbackRequestStatus.REQUESTED
            );
        }

        boolean wasAsked = request.getStatus() == FeedbackRequestStatus.REQUESTED;
        request.setStatus(FeedbackRequestStatus.CANCELLED);
        repository.save(request);
        if (wasAsked) {
            notificationService.notifyTaskCompleted(
                request.getContributor().getId(),
                "Feedback Request Cancelled",
                "Your feedback on " + fullName(review.getEmployee()) + " is no longer needed.",
                request.getId(),
                NOTIFICATION_ENTITY
            );
        }
        return toResponse(request, true, !employee);
    }

    /** Give (or revise) feedback, as the contributor, until the review is written. */
    public ReviewFeedbackResponse submit(UUID requestId, UUID actorId, SubmitFeedbackRequest body) {
        ReviewFeedbackRequest request = findRequest(requestId);
        requireContributor(request, actorId);
        requireStatus(request, "This request is closed", FeedbackRequestStatus.REQUESTED, FeedbackRequestStatus.SUBMITTED);
        PerformanceReview review = request.getReview();
        requireOpenForFeedback(review);
        if (blankToNull(body.strengths()) == null && blankToNull(body.improvements()) == null) {
            throw new BadRequestAlertException("Describe strengths or areas to improve", ENTITY_NAME, "emptyfeedback");
        }

        boolean first = request.getStatus() != FeedbackRequestStatus.SUBMITTED;
        request.setStrengths(blankToNull(body.strengths()));
        request.setImprovements(blankToNull(body.improvements()));
        request.setComments(blankToNull(body.comments()));
        request.setSubmittedAt(Instant.now());
        request.setStatus(FeedbackRequestStatus.SUBMITTED);
        repository.save(request);

        if (first && review.getReviewer() != null) {
            notificationService.notifyTaskCompleted(
                review.getReviewer().getId(),
                "Feedback Received",
                fullName(request.getContributor()) + " gave feedback on " + fullName(review.getEmployee()) + ".",
                review.getId(),
                "PerformanceReview"
            );
        }
        return toResponse(request, true, false);
    }

    // ==================== Cycle hooks ====================

    /**
     * The review's reviewer changed: the new reviewer stops being a contributor (if they were asked
     * and hadn't answered), and the previous one, if still working, is asked for feedback as
     * {@link FeedbackRelationship#PREVIOUS_MANAGER} — they knew the employee's work for part of the period.
     */
    void onReviewerChanged(PerformanceReview review, Employee previousReviewer, UUID actorId) {
        Employee reviewer = review.getReviewer();
        for (ReviewFeedbackRequest open : repository.findByReviewIdAndStatusIn(review.getId(), OPEN)) {
            if (reviewer != null && open.getContributor().getId().equals(reviewer.getId())) {
                open.setStatus(FeedbackRequestStatus.CANCELLED);
                repository.save(open);
            }
        }

        ReviewCycle cycle = review.getCycle();
        if (
            previousReviewer == null ||
            cycle.getMaxContributors() == 0 ||
            !EmployeeStatus.WORKING.contains(previousReviewer.getStatus()) ||
            (reviewer != null && previousReviewer.getId().equals(reviewer.getId())) ||
            previousReviewer.getId().equals(review.getEmployee().getId()) ||
            repository.existsByReviewIdAndContributorIdAndStatusIn(review.getId(), previousReviewer.getId(), ACTIVE)
        ) {
            return;
        }
        ReviewFeedbackRequest request = new ReviewFeedbackRequest();
        request.setReview(review);
        request.setContributor(previousReviewer);
        request.setRelationship(FeedbackRelationship.PREVIOUS_MANAGER);
        request.setStatus(FeedbackRequestStatus.REQUESTED);
        request.setRequestedBy(actorId != null ? employeeRepository.findById(actorId).orElse(null) : null);
        request.setRequestedAt(Instant.now());
        request.setDueDate(dueDate(cycle, null));
        repository.save(request);
        notifyAsked(request);
    }

    /** The review was written: requests nobody answered are closed. */
    void closeOpenRequests(PerformanceReview review) {
        List<ReviewFeedbackRequest> open = repository.findByReviewIdAndStatusIn(review.getId(), OPEN);
        open.forEach(r -> r.setStatus(FeedbackRequestStatus.CANCELLED));
        repository.saveAll(open);
    }

    /**
     * A colleague left: the requests they haven't answered in running cycles are cancelled, and each
     * review's reviewer is told so they can ask someone else.
     */
    void cancelOpenRequestsFrom(Employee contributor) {
        List<ReviewFeedbackRequest> open = repository.findByContributorIdAndStatusInAndReviewCycleActiveTrueOrderByDueDateAscRequestedAtAsc(
            contributor.getId(),
            OPEN
        );
        for (ReviewFeedbackRequest request : open) {
            request.setStatus(FeedbackRequestStatus.CANCELLED);
            Employee reviewer = request.getReview().getReviewer();
            if (reviewer != null && isOpenForFeedback(request.getReview())) {
                notificationService.notifyTaskAssignment(
                    reviewer.getId(),
                    "Feedback Request Cancelled",
                    fullName(contributor) +
                    " has left, so their feedback on " +
                    fullName(request.getReview().getEmployee()) +
                    "'s review was cancelled. You can ask someone else.",
                    request.getReview().getId(),
                    "PerformanceReview"
                );
            }
        }
        repository.saveAll(open);
    }

    /** The cycle closed: requests still open are closed. */
    void closeOpenRequests(ReviewCycle cycle) {
        List<ReviewFeedbackRequest> open = repository.findByReviewCycleIdAndStatusIn(cycle.getId(), OPEN);
        open.forEach(r -> r.setStatus(FeedbackRequestStatus.CANCELLED));
        repository.saveAll(open);
    }

    /** Remind contributors with unanswered requests on reviews still being written; one notice each. */
    void sendContributorReminders(ReviewCycle cycle) {
        repository
            .findByReviewCycleIdAndStatusIn(cycle.getId(), EnumSet.of(FeedbackRequestStatus.REQUESTED))
            .stream()
            .filter(r -> !CLOSED_TO_FEEDBACK.contains(r.getReview().getStatus()))
            .collect(Collectors.groupingBy(ReviewFeedbackRequest::getContributor, LinkedHashMap::new, Collectors.counting()))
            .forEach((contributor, count) ->
                notificationService.notifyTaskAssignment(
                    contributor.getId(),
                    "Feedback Reminder",
                    "You still have " + count + " feedback request(s) to answer for " + cycle.getName() + ".",
                    cycle.getId(),
                    "ReviewCycle"
                )
            );
    }

    /** Requests sent (answered or not) and feedback received in a cycle. */
    int[] totals(UUID cycleId) {
        int requested = 0;
        int received = 0;
        for (ReviewFeedbackCount count : countsByReview(cycleId)) {
            requested += count.requested();
            received += count.received();
        }
        return new int[] { requested, received };
    }

    // ==================== Helpers ====================

    private PerformanceReview findReview(UUID cycleId, UUID reviewId) {
        return performanceReviewRepository
            .findByIdAndCycleId(reviewId, cycleId)
            .orElseThrow(() -> EntityNotFoundException.create("PerformanceReview", reviewId));
    }

    private ReviewFeedbackRequest findRequest(UUID requestId) {
        return repository
            .findWithReviewById(requestId)
            .orElseThrow(() -> EntityNotFoundException.create("ReviewFeedbackRequest", requestId));
    }

    private Viewer viewerOf(PerformanceReview review, UUID actorId, boolean hr) {
        if (review.getEmployee().getId().equals(actorId)) {
            return Viewer.EMPLOYEE;
        }
        if (review.getReviewer() != null && review.getReviewer().getId().equals(actorId)) {
            return Viewer.REVIEWER;
        }
        if (hr) {
            return Viewer.HR;
        }
        if (
            actorId != null &&
            approvalRequestRepository.existsByEntityIdAndApprovalTypeAndApproverId(review.getId(), ApprovalType.PERFORMANCE_REVIEW, actorId)
        ) {
            return Viewer.APPROVER;
        }
        throw new AccessDeniedException("You can't see this review's feedback");
    }

    /** Contributors can be added: the review is being written and the cycle takes additional feedback. */
    private static boolean acceptsRequests(PerformanceReview review) {
        return isOpenForFeedback(review) && review.getCycle().getMaxContributors() > 0;
    }

    /** Feedback is collected during the self-assessment and manager review phases, until the reviewer writes the review. */
    private static boolean isOpenForFeedback(PerformanceReview review) {
        ReviewCyclePhase phase = review.getCycle().getPhase();
        return (
            (phase == ReviewCyclePhase.SELF_ASSESSMENT || phase == ReviewCyclePhase.MANAGER_REVIEW) &&
            !CLOSED_TO_FEEDBACK.contains(review.getStatus())
        );
    }

    private static void requireAcceptsRequests(PerformanceReview review) {
        requireOpenForFeedback(review);
        if (review.getCycle().getMaxContributors() == 0) {
            throw new BadRequestAlertException("This cycle doesn't collect additional feedback", ENTITY_NAME, "feedbackoff");
        }
    }

    private static void requireOpenForFeedback(PerformanceReview review) {
        if (!isOpenForFeedback(review)) {
            throw new BadRequestAlertException(
                "Feedback is collected during the self-assessment and manager review phases, until the review is written",
                ENTITY_NAME,
                "feedbackclosed"
            );
        }
    }

    private void requireEligibleContributor(PerformanceReview review, Employee contributor) {
        requireEligibleContributor(review, contributor, null);
    }

    /** Working, not the employee or the reviewer, and not already on the review (other than as {@code requestId}). */
    private void requireEligibleContributor(PerformanceReview review, Employee contributor, UUID requestId) {
        if (contributor.getId().equals(review.getEmployee().getId())) {
            throw new BadRequestAlertException("Employees can't give feedback on their own review", ENTITY_NAME, "selffeedback");
        }
        if (review.getReviewer() != null && contributor.getId().equals(review.getReviewer().getId())) {
            throw new BadRequestAlertException("The reviewer writes the review itself", ENTITY_NAME, "reviewerfeedback");
        }
        if (!EmployeeStatus.WORKING.contains(contributor.getStatus())) {
            throw new BadRequestAlertException("Feedback can only come from current employees", ENTITY_NAME, "inactivecontributor");
        }
        if (requestId == null && repository.existsByReviewIdAndContributorIdAndStatusIn(review.getId(), contributor.getId(), ACTIVE)) {
            throw new BadRequestAlertException("This person is already giving feedback on the review", ENTITY_NAME, "duplicatecontributor");
        }
    }

    private static void requireContributor(ReviewFeedbackRequest request, UUID actorId) {
        if (!request.getContributor().getId().equals(actorId)) {
            throw new AccessDeniedException("This feedback request isn't yours");
        }
    }

    private static void requireStatus(ReviewFeedbackRequest request, String message, FeedbackRequestStatus... allowed) {
        if (!Arrays.asList(allowed).contains(request.getStatus())) {
            throw new BadRequestAlertException(message, ENTITY_NAME, "invalidstatus");
        }
    }

    private static void requireNotOwnReview(PerformanceReview review, UUID actorId) {
        if (review.getEmployee().getId().equals(actorId)) {
            throw new AccessDeniedException("You can't act on your own review");
        }
    }

    private static void requireReviewerOrHr(PerformanceReview review, UUID actorId, boolean hrManage) {
        boolean isReviewer = review.getReviewer() != null && review.getReviewer().getId().equals(actorId);
        if (!isReviewer && !hrManage) {
            throw new AccessDeniedException("Only the reviewer or HR can do this");
        }
    }

    private static boolean isRequestedBy(ReviewFeedbackRequest request, UUID employeeId) {
        return request.getRequestedBy() != null && request.getRequestedBy().getId().equals(employeeId);
    }

    /**
     * The requested due date, or by default {@value #DUE_DAYS_BEFORE_MANAGER_DEADLINE} days before
     * the manager review deadline. Never before today, nor after that deadline while it is ahead.
     */
    private LocalDate dueDate(ReviewCycle cycle, LocalDate requested) {
        LocalDate today = LocalDate.now(organizationSettingsService.zone());
        LocalDate limit = cycle.getManagerReviewDeadline();
        if (requested != null) {
            if (requested.isBefore(today)) {
                throw new BadRequestAlertException("The due date can't be in the past", ENTITY_NAME, "invalidduedate");
            }
            if (limit != null && !limit.isBefore(today) && requested.isAfter(limit)) {
                throw new BadRequestAlertException(
                    "Feedback is due by the manager review deadline (" + limit + ") at the latest",
                    ENTITY_NAME,
                    "invalidduedate"
                );
            }
            return requested;
        }
        if (limit == null) {
            return null;
        }
        LocalDate due = limit.minusDays(DUE_DAYS_BEFORE_MANAGER_DEADLINE);
        return due.isBefore(today) ? today : due;
    }

    private void notifyAsked(ReviewFeedbackRequest request) {
        PerformanceReview review = request.getReview();
        notificationService.notifyTaskAssignment(
            request.getContributor().getId(),
            "Feedback Requested",
            "Your feedback on " +
            fullName(review.getEmployee()) +
            " is requested for " +
            review.getCycle().getName() +
            (request.getDueDate() != null ? ", by " + request.getDueDate() : "") +
            ".",
            request.getId(),
            NOTIFICATION_ENTITY
        );
    }

    private static ReviewFeedbackResponse toResponse(ReviewFeedbackRequest r, boolean withFeedback, boolean withDeclineReason) {
        return toResponse(r, withFeedback, withDeclineReason, true);
    }

    /**
     * Maps a request.
     *
     * @param withFeedback include the feedback itself
     * @param withDeclineReason include why it was declined (for the reviewer and HR, and the contributor's own)
     * @param withMessage include the note to the contributor (not for the reviewed employee, unless they wrote it)
     */
    private static ReviewFeedbackResponse toResponse(
        ReviewFeedbackRequest r,
        boolean withFeedback,
        boolean withDeclineReason,
        boolean withMessage
    ) {
        PerformanceReview review = r.getReview();
        Employee employee = review.getEmployee();
        Employee contributor = r.getContributor();
        Employee requestedBy = r.getRequestedBy();
        boolean open =
            isOpenForFeedback(review) &&
            (r.getStatus() == FeedbackRequestStatus.REQUESTED || r.getStatus() == FeedbackRequestStatus.SUBMITTED);
        return new ReviewFeedbackResponse(
            r.getId(),
            review.getId(),
            review.getCycle().getId(),
            review.getCycle().getName(),
            employee.getId(),
            fullName(employee),
            employee.getJobTitle(),
            contributor.getId(),
            fullName(contributor),
            contributor.getJobTitle(),
            r.getRelationship(),
            r.getStatus(),
            requestedBy != null ? requestedBy.getId() : null,
            requestedBy != null ? fullName(requestedBy) : null,
            r.getRequestedAt(),
            r.getDueDate(),
            withMessage ? r.getMessage() : null,
            withFeedback ? r.getStrengths() : null,
            withFeedback ? r.getImprovements() : null,
            withFeedback ? r.getComments() : null,
            withFeedback ? r.getSubmittedAt() : null,
            withDeclineReason ? r.getDeclineReason() : null,
            review.getCycle().getFeedbackVisibility(),
            open
        );
    }

    /** A contribution shown to the reviewed employee without anything that identifies its author. */
    private static ReviewFeedbackResponse toAnonymousResponse(ReviewFeedbackRequest r) {
        PerformanceReview review = r.getReview();
        return new ReviewFeedbackResponse(
            null,
            review.getId(),
            review.getCycle().getId(),
            review.getCycle().getName(),
            review.getEmployee().getId(),
            fullName(review.getEmployee()),
            review.getEmployee().getJobTitle(),
            null,
            null,
            null,
            null,
            FeedbackRequestStatus.SUBMITTED,
            null,
            null,
            null,
            null,
            null,
            r.getStrengths(),
            r.getImprovements(),
            r.getComments(),
            null,
            null,
            review.getCycle().getFeedbackVisibility(),
            false
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String fullName(Employee employee) {
        return employee.getFirstName() + " " + employee.getLastName();
    }
}
