package com.humano.service.hr.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.humano.domain.enumeration.hr.EmployeeProcessStatus;
import com.humano.domain.enumeration.hr.EmployeeProcessType;
import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.domain.enumeration.hr.LeaveStatus;
import com.humano.domain.enumeration.hr.OnboardingTaskLink;
import com.humano.domain.enumeration.hr.OnboardingTaskOwner;
import com.humano.domain.enumeration.hr.TrainingStatus;
import com.humano.domain.enumeration.hr.WorkflowStatus;
import com.humano.domain.enumeration.hr.WorkflowType;
import com.humano.domain.hr.EmployeeProcess;
import com.humano.domain.hr.EmployeeProcessTask;
import com.humano.domain.hr.EmployeeTraining;
import com.humano.domain.hr.LeaveRequest;
import com.humano.domain.hr.OnboardingSettings;
import com.humano.domain.hr.OnboardingTaskTemplate;
import com.humano.domain.hr.TerminationReason;
import com.humano.domain.hr.Training;
import com.humano.domain.hr.WorkflowInstance;
import com.humano.domain.shared.Employee;
import com.humano.domain.shared.PersistentToken;
import com.humano.dto.hr.workflow.requests.CompleteTaskRequest;
import com.humano.dto.hr.workflow.requests.InitiateOffboardingRequest;
import com.humano.dto.hr.workflow.requests.InitiateOnboardingRequest;
import com.humano.dto.hr.workflow.responses.EmployeeProcessSummary;
import com.humano.dto.hr.workflow.responses.OffboardingProcessResponse;
import com.humano.dto.hr.workflow.responses.OnboardingProcessResponse;
import com.humano.repository.hr.EmployeeDocumentRepository;
import com.humano.repository.hr.EmployeeProcessRepository;
import com.humano.repository.hr.EmployeeProcessTaskRepository;
import com.humano.repository.hr.EmployeeTrainingRepository;
import com.humano.repository.hr.ExpenseClaimRepository;
import com.humano.repository.hr.LeaveRequestRepository;
import com.humano.repository.hr.OnboardingSettingsRepository;
import com.humano.repository.hr.OnboardingTaskTemplateRepository;
import com.humano.repository.hr.TerminationReasonRepository;
import com.humano.repository.hr.TrainingRepository;
import com.humano.repository.payroll.EmployeeBenefitRepository;
import com.humano.repository.shared.AuthorityRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.repository.shared.PersistentTokenRepository;
import com.humano.repository.shared.UserRepository;
import com.humano.service.admin.UserAccountService;
import com.humano.service.billing.entitlement.EntitlementService;
import com.humano.service.billing.entitlement.FeatureCodes;
import com.humano.service.errors.BadRequestAlertException;
import com.humano.service.errors.BusinessRuleViolationException;
import com.humano.service.hr.workflow.infrastructure.DeadlineMonitorService;
import com.humano.service.hr.workflow.infrastructure.NotificationOrchestrationService;
import com.humano.service.hr.workflow.infrastructure.WorkflowStateManager;
import com.humano.tenancy.TenantIteration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Onboarding creates a tracked checklist whose process and workflow stay in step. */
class EmployeeLifecycleWorkflowServiceTest {

    private final WorkflowStateManager workflows = mock(WorkflowStateManager.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final EmployeeProcessRepository processes = mock(EmployeeProcessRepository.class);
    private final EmployeeProcessTaskRepository tasks = mock(EmployeeProcessTaskRepository.class);
    private final EmployeeTrainingRepository trainings = mock(EmployeeTrainingRepository.class);
    private final EmployeeBenefitRepository benefits = mock(EmployeeBenefitRepository.class);
    private final EmployeeDocumentRepository documents = mock(EmployeeDocumentRepository.class);
    private final ExpenseClaimRepository expenses = mock(ExpenseClaimRepository.class);
    private final OnboardingSettingsRepository settings = mock(OnboardingSettingsRepository.class);
    private final OnboardingTaskTemplateRepository templates = mock(OnboardingTaskTemplateRepository.class);
    private final TrainingRepository trainingCatalog = mock(TrainingRepository.class);
    private final EntitlementService entitlements = mock(EntitlementService.class);
    private final LeaveRequestRepository leave = mock(LeaveRequestRepository.class);
    private final TerminationReasonRepository terminationReasons = mock(TerminationReasonRepository.class);
    private final PersistentTokenRepository rememberedSignIns = mock(PersistentTokenRepository.class);
    private final EmployeeLifecycleWorkflowService service = new EmployeeLifecycleWorkflowService(
        workflows,
        mock(NotificationOrchestrationService.class),
        mock(DeadlineMonitorService.class),
        employees,
        processes,
        tasks,
        trainings,
        benefits,
        documents,
        leave,
        expenses,
        new OnboardingChecklistService(settings, templates),
        trainingCatalog,
        entitlements,
        terminationReasons,
        new UserAccountService(
            mock(UserRepository.class),
            mock(AuthorityRepository.class),
            rememberedSignIns,
            mock(PasswordEncoder.class),
            mock(TenantIteration.class),
            new SimpleMeterRegistry(),
            entitlements
        )
    );

    private static final LocalDate START = LocalDate.of(2026, 10, 1);

    private final Employee nadia = new Employee();
    private final List<WorkflowInstance> nadiasWorkflows = new ArrayList<>();
    private EmployeeProcess saved;

    @BeforeEach
    void setUp() {
        nadia.setId(UUID.randomUUID());
        nadia.setFirstName("Nadia");
        nadia.setLastName("Benali");
        nadia.setActivated(true);
        nadia.setStatus(EmployeeStatus.ACTIVE);

        when(employees.findById(nadia.getId())).thenReturn(Optional.of(nadia));
        when(processes.findByEmployeeIdAndProcessTypeAndStatusIn(any(), any(), any())).thenReturn(Optional.empty());
        when(processes.save(any())).thenAnswer(inv -> {
            EmployeeProcess p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            saved = p;
            return p;
        });
        when(processes.findById(any())).thenAnswer(inv -> Optional.ofNullable(saved).filter(p -> p.getId().equals(inv.getArgument(0))));
        when(tasks.save(any())).thenAnswer(inv -> {
            EmployeeProcessTask t = inv.getArgument(0);
            if (t.getId() == null) t.setId(UUID.randomUUID());
            return t;
        });
        when(tasks.findById(any())).thenAnswer(inv ->
            saved.getTasks().stream().filter(t -> t.getId().equals(inv.getArgument(0))).findFirst()
        );
        when(workflows.createWorkflow(any(), eq(nadia.getId()), anyString(), any(), isNull())).thenAnswer(inv -> {
            WorkflowInstance w = new WorkflowInstance();
            w.setId(UUID.randomUUID());
            w.setWorkflowType(inv.getArgument(0));
            w.setStatus(WorkflowStatus.DRAFT);
            w.setContext(new HashMap<>(inv.<Map<String, Object>>getArgument(3)));
            nadiasWorkflows.add(w);
            return w;
        });
        when(workflows.updateContext(any(), anyString(), any())).thenAnswer(inv -> {
            WorkflowInstance w = workflow(inv.getArgument(0));
            w.addContext(inv.getArgument(1), inv.getArgument(2));
            return w;
        });
        when(workflows.completeWorkflow(any(), any())).thenAnswer(inv -> {
            WorkflowInstance w = workflow(inv.getArgument(0));
            w.setStatus(WorkflowStatus.COMPLETED);
            return w;
        });
        when(workflows.findByEntityId(nadia.getId())).thenReturn(nadiasWorkflows);
        when(leave.findByEmployeeId(eq(nadia.getId()), any())).thenReturn(Page.empty());
        when(expenses.findByEmployeeId(eq(nadia.getId()), any())).thenReturn(Page.empty());
    }

    @Test
    void initiatingOnboardingReturnsTheFullChecklist() {
        OnboardingProcessResponse response = service.initiateOnboarding(onboard());

        assertThat(response.tasks()).hasSize(7);
        assertThat(response.progress().totalTasks()).isEqualTo(7);
        assertThat(response.workflowId()).isEqualTo(nadiasWorkflows.get(0).getId());
        assertThat(nadiasWorkflows.get(0).getContext()).containsEntry("processId", response.processId().toString());
    }

    @Test
    void progressReflectsTheEmployeesRealRecords() {
        when(documents.existsByEmployeeId(nadia.getId())).thenReturn(true);

        OnboardingProcessResponse.OnboardingProgress progress = service.initiateOnboarding(onboard()).progress();

        assertThat(progress.documentsUploaded()).isTrue();
        assertThat(progress.benefitsSetup()).isFalse();
        assertThat(progress.trainingsAssigned()).isFalse();
        assertThat(progress.systemAccessGranted()).isTrue();
    }

    @Test
    void completingEveryTaskReportsTheCompletedWorkflow() {
        OnboardingProcessResponse started = service.initiateOnboarding(onboard());

        OnboardingProcessResponse done = started;
        for (OnboardingProcessResponse.OnboardingTaskResponse task : started.tasks()) {
            done = service.completeOnboardingTask(started.processId(), new CompleteTaskRequest(task.taskId(), null, true, null));
        }

        assertThat(done.processStatus()).isEqualTo(EmployeeProcessStatus.COMPLETED);
        assertThat(done.completionPercentage()).isEqualTo(100);
        assertThat(done.workflowId()).isEqualTo(started.workflowId());
        assertThat(done.workflowStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    @Test
    void aCancelledOnboardingCannotBeCompleted() {
        OnboardingProcessResponse started = service.initiateOnboarding(onboard());
        service.cancelOnboarding(started.processId(), "Offer withdrawn");
        UUID taskId = started.tasks().get(0).taskId();

        assertThatThrownBy(() -> service.completeOnboardingTask(started.processId(), new CompleteTaskRequest(taskId, null, true, null)))
            .isInstanceOf(BadRequestAlertException.class)
            .hasMessageContaining("CANCELLED");
        assertThat(saved.getStatus()).isEqualTo(EmployeeProcessStatus.CANCELLED);
        verify(workflows, never()).completeWorkflow(any(), any());
    }

    @Test
    void activeProcessesExcludeCancelledAndCompletedOnes() {
        service.initiateOnboarding(onboard(LocalDate.now(), null)); // starts today: nothing overdue yet
        Pageable pageable = Pageable.ofSize(20);
        when(processes.findByProcessTypeAndStatusIn(eq(EmployeeProcessType.ONBOARDING), any(), eq(pageable))).thenAnswer(inv -> {
            List<EmployeeProcessStatus> statuses = inv.getArgument(1);
            assertThat(statuses).doesNotContain(EmployeeProcessStatus.CANCELLED, EmployeeProcessStatus.COMPLETED);
            return new PageImpl<>(List.of(saved));
        });

        Page<EmployeeProcessSummary> page = service.getActiveProcesses(EmployeeProcessType.ONBOARDING, pageable);

        assertThat(page.getContent())
            .singleElement()
            .satisfies(row -> {
                assertThat(row.employeeName()).isEqualTo("Nadia Benali");
                assertThat(row.totalTasks()).isEqualTo(7);
                assertThat(row.overdueTasks()).isZero();
            });
    }

    @Test
    void byDefaultTheChecklistRunsThirtyDays() {
        OnboardingProcessResponse response = service.initiateOnboarding(onboard());

        assertThat(response.dueDate()).isEqualTo(START.plusDays(30));
        assertThat(response.tasks())
            .extracting(OnboardingProcessResponse.OnboardingTaskResponse::dueDate)
            .allSatisfy(due -> assertThat(due).isBetween(START, START.plusDays(30)));
        assertThat(lastDue(response)).isEqualTo(START.plusDays(30));
    }

    @Test
    void aShorterTargetCompressesTheScheduleInOrder() {
        OnboardingProcessResponse thirtyDays = service.initiateOnboarding(onboard());
        saved = null;
        nadiasWorkflows.clear();
        OnboardingProcessResponse oneWeek = service.initiateOnboarding(onboard(START, START.plusDays(7)));

        assertThat(oneWeek.dueDate()).isEqualTo(START.plusDays(7));
        assertThat(lastDue(oneWeek)).isEqualTo(START.plusDays(7));
        assertThat(oneWeek.tasks()).allSatisfy(t -> assertThat(t.dueDate()).isBetween(START, START.plusDays(7)));
        // Compressing may put tasks on the same day, but never reorders them.
        Map<String, LocalDate> weekDue = new HashMap<>();
        oneWeek.tasks().forEach(t -> weekDue.put(t.title(), t.dueDate()));
        List<LocalDate> inThirtyDayOrder = order(thirtyDays).stream().map(weekDue::get).toList();
        assertThat(inThirtyDayOrder).isSortedAccordingTo(LocalDate::compareTo);
    }

    @Test
    void theCompanyChecklistAndDefaultLengthAreUsed() {
        OnboardingSettings tenDays = new OnboardingSettings();
        tenDays.setDefaultDays(10);
        when(settings.findAll()).thenReturn(List.of(tenDays));
        when(templates.findAllByOrderBySortOrderAsc()).thenReturn(
            List.of(template("Laptop handover", 0, OnboardingTaskOwner.HR), template("Team lunch", 10, OnboardingTaskOwner.MANAGER))
        );
        Employee kenji = new Employee();
        kenji.setId(UUID.randomUUID());
        kenji.setFirstName("Kenji");
        kenji.setLastName("Watanabe");
        nadia.setManager(kenji);

        OnboardingProcessResponse byDefault = service.initiateOnboarding(onboard());
        assertThat(byDefault.dueDate()).isEqualTo(START.plusDays(10));
        assertThat(byDefault.tasks())
            .extracting(OnboardingProcessResponse.OnboardingTaskResponse::title)
            .containsExactlyInAnyOrder("Laptop handover", "Team lunch");

        saved = null;
        nadiasWorkflows.clear();
        OnboardingProcessResponse fiveDays = service.initiateOnboarding(onboard(START, START.plusDays(5)));
        Map<String, OnboardingProcessResponse.OnboardingTaskResponse> byTitle = new HashMap<>();
        fiveDays.tasks().forEach(t -> byTitle.put(t.title(), t));
        assertThat(byTitle.get("Laptop handover").dueDate()).isEqualTo(START);
        assertThat(byTitle.get("Laptop handover").assignedToId()).isNull();
        assertThat(byTitle.get("Team lunch").dueDate()).isEqualTo(START.plusDays(5));
        assertThat(byTitle.get("Team lunch").assignedToName()).isEqualTo("Kenji Watanabe");
    }

    @Test
    void linkedTasksShowWhetherTheRecordIsOnFile() {
        OnboardingProcessResponse response = service.initiateOnboarding(onboard());
        Map<String, OnboardingProcessResponse.OnboardingTaskResponse> byTitle = new HashMap<>();
        response.tasks().forEach(t -> byTitle.put(t.title(), t));

        // Nadia's account is activated; she has no benefit enrollment yet.
        assertThat(byTitle.get("IT Systems Setup").link()).isEqualTo(OnboardingTaskLink.ACCOUNT);
        assertThat(byTitle.get("IT Systems Setup").linkSatisfied()).isTrue();
        assertThat(byTitle.get("Benefits Enrollment").link()).isEqualTo(OnboardingTaskLink.BENEFITS);
        assertThat(byTitle.get("Benefits Enrollment").linkSatisfied()).isFalse();
        assertThat(byTitle.get("Policy Acknowledgment").link()).isNull();
        assertThat(byTitle.get("Policy Acknowledgment").linkSatisfied()).isNull();
    }

    @Test
    void requiredTrainingsAreAssignedButOnlyCountOnceCompleted() {
        Training security = training("Security awareness");
        Training gdpr = training("GDPR basics");
        when(trainingCatalog.findById(security.getId())).thenReturn(Optional.of(security));
        when(trainingCatalog.findById(gdpr.getId())).thenReturn(Optional.of(gdpr));
        when(trainings.existsByEmployeeIdAndTrainingId(nadia.getId(), gdpr.getId())).thenReturn(true); // already enrolled
        when(trainings.existsByEmployeeId(nadia.getId())).thenReturn(true);
        when(trainings.existsByEmployeeIdAndStatus(nadia.getId(), TrainingStatus.PLANNED)).thenReturn(true);

        OnboardingProcessResponse response = service.initiateOnboarding(
            onboard(START, null, List.of(security.getId(), gdpr.getId(), security.getId()))
        );

        ArgumentCaptor<EmployeeTraining> enrolled = ArgumentCaptor.forClass(EmployeeTraining.class);
        verify(trainings).save(enrolled.capture());
        assertThat(enrolled.getValue().getTraining()).isSameAs(security);
        assertThat(enrolled.getValue().getStatus()).isEqualTo(TrainingStatus.PLANNED);
        assertThat(response.progress().trainingsAssigned()).isTrue();
        assertThat(response.tasks())
            .filteredOn(t -> t.title().equals("Complete Mandatory Trainings"))
            .singleElement()
            .satisfies(t -> {
                assertThat(t.link()).isEqualTo(OnboardingTaskLink.TRAININGS);
                assertThat(t.linkSatisfied()).isFalse(); // planned, not completed
            });
    }

    @Test
    void anUnknownTrainingIsRejectedBeforeAnythingIsCreated() {
        UUID unknown = UUID.randomUUID();
        when(trainingCatalog.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initiateOnboarding(onboard(START, null, List.of(unknown))))
            .isInstanceOf(BadRequestAlertException.class)
            .hasMessageContaining("Unknown training");
        verify(processes, never()).save(any());
    }

    @Test
    void trainingsNeedTheTrainingModule() {
        doThrow(new IllegalStateException("not in plan")).when(entitlements).checkIncluded(FeatureCodes.TRAINING);

        assertThatThrownBy(() -> service.initiateOnboarding(onboard(START, null, List.of(UUID.randomUUID())))).hasMessageContaining(
            "not in plan"
        );
        verify(processes, never()).save(any());
    }

    @Test
    void historyAndPerEmployeeListsUseTheirFilters() {
        Pageable pageable = Pageable.ofSize(20);
        List<EmployeeProcessStatus> history = List.of(EmployeeProcessStatus.COMPLETED, EmployeeProcessStatus.CANCELLED);
        when(processes.findByProcessTypeAndStatusIn(EmployeeProcessType.ONBOARDING, history, pageable)).thenReturn(Page.empty());
        when(
            processes.findByEmployeeIdAndProcessTypeAndStatusIn(eq(nadia.getId()), eq(EmployeeProcessType.ONBOARDING), any(), eq(pageable))
        ).thenReturn(Page.empty());

        service.getProcesses(EmployeeProcessType.ONBOARDING, history, null, pageable);
        service.getProcesses(EmployeeProcessType.ONBOARDING, List.of(EmployeeProcessStatus.values()), nadia.getId(), pageable);

        verify(processes).findByProcessTypeAndStatusIn(EmployeeProcessType.ONBOARDING, history, pageable);
        verify(processes).findByEmployeeIdAndProcessTypeAndStatusIn(
            nadia.getId(),
            EmployeeProcessType.ONBOARDING,
            List.of(EmployeeProcessStatus.values()),
            pageable
        );
    }

    @Test
    void startingOnboardingMarksTheEmployeeOnboarding() {
        service.initiateOnboarding(onboard());

        assertThat(nadia.getStatus()).isEqualTo(EmployeeStatus.ONBOARDING);
        assertThat(nadiasWorkflows.get(0).getContext()).containsEntry(EmployeeLifecycleWorkflowService.CONTEXT_STATUS_BEFORE, "ACTIVE");
        verify(employees).save(nadia);
    }

    @Test
    void finishingTheChecklistMakesThemActive() {
        OnboardingProcessResponse started = service.initiateOnboarding(onboard());
        for (OnboardingProcessResponse.OnboardingTaskResponse task : started.tasks()) {
            service.completeOnboardingTask(started.processId(), new CompleteTaskRequest(task.taskId(), null, true, null));
        }

        assertThat(nadia.getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
    }

    @Test
    void cancellingRestoresTheStatusTheyHadBefore() {
        nadia.setStatus(null); // a profile without a status yet
        OnboardingProcessResponse started = service.initiateOnboarding(onboard());
        assertThat(nadia.getStatus()).isEqualTo(EmployeeStatus.ONBOARDING);

        service.cancelOnboarding(started.processId(), "Offer withdrawn");

        assertThat(nadia.getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
    }

    @Test
    void inactiveEmployeesKeepTheirStatus() {
        nadia.setStatus(EmployeeStatus.INACTIVE);

        service.initiateOnboarding(onboard());

        assertThat(nadia.getStatus()).isEqualTo(EmployeeStatus.INACTIVE);
        verify(employees, never()).save(any());
    }

    @Test
    void aStatusChangedByHandDuringOnboardingIsKept() {
        OnboardingProcessResponse started = service.initiateOnboarding(onboard());
        nadia.setStatus(EmployeeStatus.TERMINATED); // HR changed it on the profile

        service.cancelOnboarding(started.processId(), "Left before finishing");

        assertThat(nadia.getStatus()).isEqualTo(EmployeeStatus.TERMINATED);
    }

    // ==================== Offboarding ====================

    @Test
    void startingOffboardingMarksTheEmployeeOffboarding() {
        OffboardingProcessResponse response = service.initiateOffboarding(offboard(LocalDate.now().plusDays(20), null));

        assertThat(nadia.getStatus()).isEqualTo(EmployeeStatus.OFFBOARDING);
        assertThat(response.startDate()).isEqualTo(LocalDate.now());
        assertThat(response.offboardingReason()).isEqualTo("Moving abroad");
        assertThat(nadiasWorkflows.get(0).getContext()).containsEntry(EmployeeLifecycleWorkflowService.CONTEXT_STATUS_BEFORE, "ACTIVE");
    }

    @Test
    void aShortNoticeKeepsEveryTaskWithinTheNoticePeriod() {
        LocalDate lastDay = LocalDate.now().plusDays(5);

        OffboardingProcessResponse response = service.initiateOffboarding(offboard(lastDay, null));

        assertThat(response.tasks()).isNotEmpty();
        assertThat(response.tasks()).allSatisfy(t -> assertThat(t.dueDate()).isBetween(LocalDate.now(), lastDay));
        assertThat(response.tasks()).anySatisfy(t -> assertThat(t.dueDate()).isEqualTo(lastDay));
    }

    @Test
    void aLastDayInThePastTerminatesRightAway() {
        TerminationReason resignation = new TerminationReason();
        resignation.setId(UUID.randomUUID());
        resignation.setName("Resignation");
        when(terminationReasons.existsById(resignation.getId())).thenReturn(true);
        when(terminationReasons.findById(resignation.getId())).thenReturn(Optional.of(resignation));
        LocalDate yesterday = LocalDate.now().minusDays(1);

        OffboardingProcessResponse response = service.initiateOffboarding(offboard(yesterday, resignation.getId()));

        assertThat(nadia.getStatus()).isEqualTo(EmployeeStatus.TERMINATED);
        assertThat(nadia.isActivated()).isFalse();
        assertThat(nadia.getEndDate()).isEqualTo(yesterday);
        assertThat(nadia.getTerminationReason()).isSameAs(resignation);
        assertThat(nadia.getTerminationNotes()).isEqualTo("Moving abroad");
        assertThat(response.terminationReasonName()).isEqualTo("Resignation");
    }

    @Test
    void theDailyJobTerminatesOnlyAfterTheLastDayAndOnlyOnce() {
        service.initiateOffboarding(offboard(LocalDate.now().plusDays(3), null));
        when(processes.findByProcessTypeAndStatusInAndDueDateBeforeAndEmployeeStatus(any(), any(), any(), any())).thenAnswer(inv ->
            List.of(saved)
        );

        assertThat(service.terminateDueOffboardings()).isZero(); // last day still ahead
        assertThat(nadia.getStatus()).isEqualTo(EmployeeStatus.OFFBOARDING);

        saved.setDueDate(LocalDate.now().minusDays(2)); // a missed run: two days later
        assertThat(service.terminateDueOffboardings()).isEqualTo(1);
        assertThat(nadia.getStatus()).isEqualTo(EmployeeStatus.TERMINATED);
        assertThat(nadia.getEndDate()).isEqualTo(LocalDate.now().minusDays(2));

        assertThat(service.terminateDueOffboardings()).isZero(); // idempotent
    }

    @Test
    void terminationDeactivatesTheAccountAndForgetsRememberedSignIns() {
        PersistentToken laptop = new PersistentToken();
        laptop.setSeries("laptop");
        when(rememberedSignIns.findByUser(nadia)).thenReturn(List.of(laptop));

        service.initiateOffboarding(offboard(LocalDate.now().minusDays(1), null));

        assertThat(nadia.isActivated()).isFalse();
        verify(rememberedSignIns).deleteAll(List.of(laptop));
    }

    @Test
    void anOffboardingThatIsCancelledBeforeTheLastDayKeepsTheAccountActive() {
        OffboardingProcessResponse started = service.initiateOffboarding(offboard(LocalDate.now().plusDays(5), null));
        service.cancelOffboarding(started.processId(), "Decided to stay");

        assertThat(nadia.isActivated()).isTrue();
        verify(rememberedSignIns, never()).deleteAll(any());
    }

    @Test
    void theJobLeavesAStatusChangedByHandAlone() {
        service.initiateOffboarding(offboard(LocalDate.now().plusDays(3), null));
        nadia.setStatus(EmployeeStatus.INACTIVE);
        saved.setDueDate(LocalDate.now().minusDays(1));
        when(processes.findByProcessTypeAndStatusInAndDueDateBeforeAndEmployeeStatus(any(), any(), any(), any())).thenReturn(
            List.of(saved)
        );

        assertThat(service.terminateDueOffboardings()).isZero();
        assertThat(nadia.getStatus()).isEqualTo(EmployeeStatus.INACTIVE);
    }

    @Test
    void cancellingBeforeTheLastDayRestoresTheStatus() {
        OffboardingProcessResponse started = service.initiateOffboarding(offboard(LocalDate.now().plusDays(10), null));

        service.cancelOffboarding(started.processId(), "Decided to stay");

        assertThat(nadia.getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
        assertThat(saved.getStatus()).isEqualTo(EmployeeProcessStatus.CANCELLED);
    }

    @Test
    void cancellingAfterTheyLeftKeepsThemTerminated() {
        OffboardingProcessResponse started = service.initiateOffboarding(offboard(LocalDate.now().minusDays(1), null));
        assertThat(nadia.getStatus()).isEqualTo(EmployeeStatus.TERMINATED);

        service.cancelOffboarding(started.processId(), "Checklist no longer needed");

        assertThat(nadia.getStatus()).isEqualTo(EmployeeStatus.TERMINATED);
    }

    @Test
    void offboardingIsRefusedForLeaversAndUnknownReferences() {
        nadia.setStatus(EmployeeStatus.TERMINATED);
        assertThatThrownBy(() -> service.initiateOffboarding(offboard(LocalDate.now().plusDays(5), null)))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("already left");

        nadia.setStatus(EmployeeStatus.ACTIVE);
        UUID unknown = UUID.randomUUID();
        assertThatThrownBy(() -> service.initiateOffboarding(offboard(LocalDate.now().plusDays(5), unknown)))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("termination reason");

        InitiateOffboardingRequest withInterviewer = new InitiateOffboardingRequest(
            nadia.getId(),
            LocalDate.now().plusDays(5),
            "Moving abroad",
            null,
            null,
            true,
            UUID.randomUUID(),
            null,
            null
        );
        assertThatThrownBy(() -> service.initiateOffboarding(withInterviewer))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("exit interviewer");
        verify(processes, never()).save(any());
    }

    @Test
    void onlyLeaveStartingAfterTheLastDayIsWithdrawn() {
        LocalDate lastDay = LocalDate.now().plusDays(14);
        LeaveRequest before = pendingLeave(lastDay.minusDays(3));
        LeaveRequest after = pendingLeave(lastDay.plusDays(1));
        when(leave.findByEmployeeId(eq(nadia.getId()), any())).thenReturn(new PageImpl<>(List.of(before, after)));

        service.initiateOffboarding(offboard(lastDay, null));

        assertThat(before.getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(after.getStatus()).isEqualTo(LeaveStatus.CANCELED);
    }

    @Test
    void aTargetBeforeTheStartIsRejected() {
        assertThatThrownBy(() -> service.initiateOnboarding(onboard(START, START.minusDays(1))))
            .isInstanceOf(BadRequestAlertException.class)
            .hasMessageContaining("Target date");
    }

    @Test
    void settlementOnlyTotalsComponentsItCanCompute() {
        when(expenses.findByEmployeeId(eq(nadia.getId()), any())).thenReturn(Page.empty());

        var settlement = service.calculateFinalSettlement(nadia.getId());

        assertThat(settlement.remainingLeaveDays()).isNull();
        assertThat(settlement.leaveEncashment()).isNull();
        assertThat(settlement.pendingOvertimePay()).isNull();
        assertThat(settlement.totalSettlement()).isEqualByComparingTo(settlement.pendingExpenseReimbursements());
    }

    private InitiateOnboardingRequest onboard() {
        return onboard(START, null);
    }

    private InitiateOnboardingRequest onboard(LocalDate startDate, LocalDate targetDate) {
        return onboard(startDate, targetDate, null);
    }

    private InitiateOnboardingRequest onboard(LocalDate startDate, LocalDate targetDate, List<UUID> trainingIds) {
        return new InitiateOnboardingRequest(nadia.getId(), startDate, targetDate, trainingIds, null, null);
    }

    private InitiateOffboardingRequest offboard(LocalDate lastDay, UUID terminationReasonId) {
        return new InitiateOffboardingRequest(
            nadia.getId(),
            lastDay,
            "Moving abroad",
            "RESIGNATION",
            terminationReasonId,
            false,
            null,
            null,
            null
        );
    }

    private static LeaveRequest pendingLeave(LocalDate startDate) {
        LeaveRequest request = new LeaveRequest();
        request.setStartDate(startDate);
        request.setEndDate(startDate.plusDays(2));
        request.setStatus(LeaveStatus.PENDING);
        return request;
    }

    private static Training training(String name) {
        Training t = new Training();
        t.setId(UUID.randomUUID());
        t.setName(name);
        return t;
    }

    private static OnboardingTaskTemplate template(String title, int dueDay, OnboardingTaskOwner owner) {
        OnboardingTaskTemplate t = new OnboardingTaskTemplate();
        t.setTitle(title);
        t.setDueDay(dueDay);
        t.setOwner(owner);
        return t;
    }

    private static LocalDate lastDue(OnboardingProcessResponse response) {
        return response
            .tasks()
            .stream()
            .map(OnboardingProcessResponse.OnboardingTaskResponse::dueDate)
            .max(LocalDate::compareTo)
            .orElseThrow();
    }

    /** Task titles by due date (ties by title), to compare schedules. */
    private static List<String> order(OnboardingProcessResponse response) {
        return response
            .tasks()
            .stream()
            .sorted(
                java.util.Comparator.comparing(OnboardingProcessResponse.OnboardingTaskResponse::dueDate).thenComparing(
                    OnboardingProcessResponse.OnboardingTaskResponse::title
                )
            )
            .map(OnboardingProcessResponse.OnboardingTaskResponse::title)
            .toList();
    }

    private WorkflowInstance workflow(UUID id) {
        return nadiasWorkflows.stream().filter(w -> w.getId().equals(id)).findFirst().orElseThrow();
    }
}
