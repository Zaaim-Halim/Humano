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

import com.humano.domain.enumeration.hr.ApprovalType;
import com.humano.domain.enumeration.hr.ApproverType;
import com.humano.domain.enumeration.hr.SignOffStatus;
import com.humano.domain.enumeration.hr.WorkflowStatus;
import com.humano.domain.hr.ApprovalChainConfig;
import com.humano.domain.hr.ApprovalRequest;
import com.humano.domain.hr.PerformanceReview;
import com.humano.domain.hr.WorkflowInstance;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.workflow.requests.ApprovalDecisionRequest;
import com.humano.dto.hr.workflow.requests.ApprovalDecisionRequest.ApprovalDecision;
import com.humano.repository.hr.ExpenseClaimRepository;
import com.humano.repository.hr.LeaveRequestRepository;
import com.humano.repository.hr.OvertimeRecordRepository;
import com.humano.repository.hr.PerformanceReviewRepository;
import com.humano.repository.hr.workflow.ApprovalChainConfigRepository;
import com.humano.repository.hr.workflow.ApprovalRequestRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.security.AuthenticatedUser;
import com.humano.service.billing.entitlement.EntitlementService;
import com.humano.service.billing.entitlement.Entitlements;
import com.humano.service.hr.workflow.infrastructure.DeadlineMonitorService;
import com.humano.service.hr.workflow.infrastructure.NotificationOrchestrationService;
import com.humano.service.hr.workflow.infrastructure.WorkflowStateManager;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** The approver's decision on a review's sign-off lands on the review, and only while it waits. */
class ApprovalReviewSignOffTest {

    private final ApprovalRequestRepository approvals = mock(ApprovalRequestRepository.class);
    private final ApprovalChainConfigRepository chains = mock(ApprovalChainConfigRepository.class);
    private final PerformanceReviewRepository reviews = mock(PerformanceReviewRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final NotificationOrchestrationService notifications = mock(NotificationOrchestrationService.class);
    private final EntitlementService entitlements = mock(EntitlementService.class);

    private final ApprovalWorkflowOrchestratorService service = new ApprovalWorkflowOrchestratorService(
        mock(WorkflowStateManager.class),
        notifications,
        mock(DeadlineMonitorService.class),
        approvals,
        chains,
        employees,
        mock(LeaveRequestRepository.class),
        mock(ExpenseClaimRepository.class),
        mock(OvertimeRecordRepository.class),
        reviews,
        entitlements
    );

    private final Employee director = employee("Dana", "Director", null);
    private final Employee manager = employee("Maria", "Manager", director);
    private PerformanceReview review;
    private ApprovalRequest request;

    @BeforeEach
    void setUp() {
        review = new PerformanceReview();
        review.setId(UUID.randomUUID());
        review.setEmployee(employee("Alice", "Analyst", manager));
        review.setSignOffStatus(SignOffStatus.PENDING);

        WorkflowInstance workflow = new WorkflowInstance();
        workflow.setId(UUID.randomUUID());
        workflow.setContext(new HashMap<>());

        request = new ApprovalRequest();
        request.setId(UUID.randomUUID());
        request.setApprovalType(ApprovalType.PERFORMANCE_REVIEW);
        request.setEntityId(review.getId());
        request.setEntityType("PerformanceReview");
        request.setRequestor(manager);
        request.setApprover(director);
        request.setCurrentLevel(1);
        request.setStatus(WorkflowStatus.PENDING_APPROVAL);
        request.setSubmittedAt(Instant.now());
        request.setWorkflowInstance(workflow);

        when(approvals.findById(request.getId())).thenReturn(Optional.of(request));
        when(reviews.findById(review.getId())).thenReturn(Optional.of(review));
        when(chains.findByApprovalTypeAndActiveTrueOrderBySequenceOrderAsc(ApprovalType.PERFORMANCE_REVIEW)).thenReturn(List.of());
    }

    @Test
    void signingOffMarksTheReviewSignedOffByTheApprover() {
        service.processApprovalDecision(request.getId(), new ApprovalDecisionRequest(ApprovalDecision.APPROVE, null));

        assertThat(review.getSignOffStatus()).isEqualTo(SignOffStatus.APPROVED);
        assertThat(review.getSignOffDecidedBy()).isEqualTo(director);
        assertThat(review.getSignOffDecidedAt()).isNotNull();
        verify(notifications).notifyApprovalDecision(
            eq(manager.getId()),
            eq("Performance Review Signed Off"),
            anyString(),
            any(),
            any(),
            eq(true)
        );
    }

    @Test
    void returningAReviewNeedsACommentAndAskingForMoreReturnsItToo() {
        assertThatThrownBy(() -> service.processApprovalDecision(request.getId(), new ApprovalDecisionRequest(ApprovalDecision.REJECT, " "))
        ).hasMessageContaining("what the reviewer should change");
        assertThat(review.getSignOffStatus()).isEqualTo(SignOffStatus.PENDING);

        service.processApprovalDecision(
            request.getId(),
            new ApprovalDecisionRequest(ApprovalDecision.REQUEST_MORE_INFO, "Back the rating with examples")
        );

        assertThat(review.getSignOffStatus()).isEqualTo(SignOffStatus.RETURNED);
        assertThat(review.getSignOffComment()).isEqualTo("Back the rating with examples");
        assertThat(request.getStatus()).isEqualTo(WorkflowStatus.REJECTED);
    }

    @Test
    void aDecisionOnAReviewNoLongerWaitingLeavesItAlone() {
        review.setSignOffStatus(SignOffStatus.WAIVED);

        service.processApprovalDecision(request.getId(), new ApprovalDecisionRequest(ApprovalDecision.APPROVE, null));

        assertThat(review.getSignOffStatus()).isEqualTo(SignOffStatus.WAIVED);
        verify(reviews, never()).save(any());
    }

    @Test
    void nobodyDecidesTheSignOffOfAReviewTheyWroteOrThatIsAboutThem() {
        try {
            signInAs(manager);
            assertThatThrownBy(() ->
                service.processApprovalDecision(request.getId(), new ApprovalDecisionRequest(ApprovalDecision.APPROVE, null))
            ).isInstanceOf(AccessDeniedException.class);

            signInAs(review.getEmployee());
            assertThatThrownBy(() ->
                service.processApprovalDecision(request.getId(), new ApprovalDecisionRequest(ApprovalDecision.APPROVE, null))
            ).isInstanceOf(AccessDeniedException.class);
            assertThat(review.getSignOffStatus()).isEqualTo(SignOffStatus.PENDING);

            signInAs(director);
            service.processApprovalDecision(request.getId(), new ApprovalDecisionRequest(ApprovalDecision.APPROVE, null));
            assertThat(review.getSignOffStatus()).isEqualTo(SignOffStatus.APPROVED);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void aSignOffIsNeverWithdrawn() {
        assertThatThrownBy(() -> service.withdrawApprovalRequest(request.getId(), "Changed my mind")).hasMessageContaining(
            "can't be withdrawn"
        );
        assertThat(request.getStatus()).isEqualTo(WorkflowStatus.PENDING_APPROVAL);
    }

    @Test
    void aLaterLevelLandingOnTheReviewedEmployeeOrTheReviewerIsSkipped() {
        when(entitlements.current()).thenReturn(Entitlements.UNLIMITED);
        List<ApprovalChainConfig> chain = List.of(
            level(1, null),
            level(2, review.getEmployee().getId()),
            level(3, manager.getId()),
            level(4, director.getId())
        );
        when(chains.findByApprovalTypeAndActiveTrueOrderBySequenceOrderAsc(ApprovalType.PERFORMANCE_REVIEW)).thenReturn(chain);

        service.processApprovalDecision(request.getId(), new ApprovalDecisionRequest(ApprovalDecision.APPROVE, null));

        // Levels 2 (the employee) and 3 (the reviewer) are skipped: level 4 decides next.
        assertThat(request.getCurrentLevel()).isEqualTo(4);
        assertThat(request.getApprover()).isEqualTo(director);
        assertThat(request.getStatus()).isEqualTo(WorkflowStatus.PENDING_APPROVAL);
        assertThat(review.getSignOffStatus()).isEqualTo(SignOffStatus.PENDING);
    }

    @Test
    void whenOnlyPartiesToTheReviewAreLeftTheSignOffIsComplete() {
        when(entitlements.current()).thenReturn(Entitlements.UNLIMITED);
        List<ApprovalChainConfig> chain = List.of(level(1, null), level(2, review.getEmployee().getId()));
        when(chains.findByApprovalTypeAndActiveTrueOrderBySequenceOrderAsc(ApprovalType.PERFORMANCE_REVIEW)).thenReturn(chain);

        service.processApprovalDecision(request.getId(), new ApprovalDecisionRequest(ApprovalDecision.APPROVE, null));

        assertThat(request.getStatus()).isEqualTo(WorkflowStatus.APPROVED);
        assertThat(review.getSignOffStatus()).isEqualTo(SignOffStatus.APPROVED);
    }

    /** A chain step: the reviewer's manager at level 1, or a named approver. */
    private ApprovalChainConfig level(int order, UUID specificApproverId) {
        ApprovalChainConfig config = new ApprovalChainConfig();
        config.setId(UUID.randomUUID());
        config.setApprovalType(ApprovalType.PERFORMANCE_REVIEW);
        config.setSequenceOrder(order);
        config.setApproverType(specificApproverId == null ? ApproverType.DIRECT_MANAGER : ApproverType.SPECIFIC_EMPLOYEE);
        config.setSpecificApproverId(specificApproverId);
        config.setActive(true);
        if (specificApproverId != null) {
            Employee approver = specificApproverId.equals(director.getId())
                ? director
                : specificApproverId.equals(manager.getId()) ? manager : review.getEmployee();
            when(employees.findById(specificApproverId)).thenReturn(Optional.of(approver));
        }
        return config;
    }

    private static void signInAs(Employee employee) {
        AuthenticatedUser user = new AuthenticatedUser(employee.getFirstName(), "", List.of(), employee.getId());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private static Employee employee(String firstName, String lastName, Employee manager) {
        Employee e = new Employee();
        e.setId(UUID.randomUUID());
        e.setFirstName(firstName);
        e.setLastName(lastName);
        e.setManager(manager);
        return e;
    }
}
