package com.humano.service.hr.workflow;

import com.humano.domain.enumeration.hr.*;
import com.humano.domain.hr.*;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.workflow.requests.CompleteTaskRequest;
import com.humano.dto.hr.workflow.requests.InitiateOffboardingRequest;
import com.humano.dto.hr.workflow.requests.InitiateOnboardingRequest;
import com.humano.dto.hr.workflow.responses.EmployeeProcessSummary;
import com.humano.dto.hr.workflow.responses.OffboardingProcessResponse;
import com.humano.dto.hr.workflow.responses.OnboardingChecklistResponse;
import com.humano.dto.hr.workflow.responses.OnboardingProcessResponse;
import com.humano.repository.hr.*;
import com.humano.repository.payroll.EmployeeBenefitRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.repository.shared.PersistentTokenRepository;
import com.humano.service.billing.entitlement.EntitlementService;
import com.humano.service.billing.entitlement.FeatureCodes;
import com.humano.service.errors.BusinessRuleViolationException;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.hr.workflow.infrastructure.DeadlineMonitorService;
import com.humano.service.hr.workflow.infrastructure.NotificationOrchestrationService;
import com.humano.service.hr.workflow.infrastructure.WorkflowStateManager;
import com.humano.web.rest.errors.BadRequestAlertException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrator service for managing employee lifecycle workflows
 * (onboarding + offboarding).
 *
 * <p>Each flow writes to BOTH a {@code WorkflowInstance}
 * (string {@code currentState}, tracked by {@code WorkflowStateManager}) AND
 * an {@code EmployeeProcess} (typed {@code EmployeeProcessStatus}). The
 * process status is the load-bearing one; the workflow state is granular
 * but mostly static.
 *
 * <h3>Onboarding</h3>
 * <pre>
 *  EmployeeProcessStatus:  PLANNED → IN_PROGRESS → COMPLETED
 *                                 ↘ CANCELLED (via cancelOnboarding)
 *
 *  WorkflowInstance.currentState:
 *      (created) → IN_PROGRESS → PROFILE_SETUP → COMPLETED
 * </pre>
 *
 * <h3>Offboarding</h3>
 * <pre>
 *  EmployeeProcessStatus:  PLANNED → IN_PROGRESS → COMPLETED
 *
 *  WorkflowInstance.currentState:
 *      (created) → IN_PROGRESS → PENDING_REQUESTS_PROCESSING → COMPLETED
 * </pre>
 *
 * An onboarding runs from its start date to a target date chosen when it is
 * started (default: the company's default length, see
 * {@link OnboardingChecklistService}). The tasks come from the company
 * checklist, whose due days are written for a default-length onboarding and
 * scaled to this one's window, so a shorter onboarding keeps the same order with
 * proportionally earlier due dates.
 *
 * The workflow is linked to its process through the {@value #CONTEXT_PROCESS_ID}
 * context key, so an employee's historical processes each resolve to their
 * own workflow.
 *
 * Each {@code initiate} call is idempotent on (employeeId, processType):
 * an active process for the same employee will reject the call. Completing
 * an already-completed task is a no-op.
 */
@Service
@Transactional("tenantTransactionManager")
public class EmployeeLifecycleWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeLifecycleWorkflowService.class);

    // Onboarding states
    public static final String STATE_PROFILE_SETUP = "PROFILE_SETUP";
    public static final String STATE_TASKS_IN_PROGRESS = "TASKS_IN_PROGRESS";

    // Offboarding states
    public static final String STATE_PENDING_REQUESTS_PROCESSING = "PENDING_REQUESTS_PROCESSING";
    public static final String STATE_EXIT_TASKS = "EXIT_TASKS";

    /** Workflow context key holding the id of the {@code EmployeeProcess} it drives. */
    public static final String CONTEXT_PROCESS_ID = "processId";

    /** Workflow context key holding the employee's status before onboarding/offboarding switched it. */
    public static final String CONTEXT_STATUS_BEFORE = "employeeStatusBefore";

    /** Offboarding workflow context keys: why they leave and the company termination reason. */
    public static final String CONTEXT_REASON = "reason";
    public static final String CONTEXT_TERMINATION_REASON_ID = "terminationReasonId";
    public static final String CONTEXT_EXIT_INTERVIEW = "conductExitInterview";

    // Statuses that indicate an in-flight process (used for idempotency checks).
    private static final List<EmployeeProcessStatus> ACTIVE_PROCESS_STATUSES = List.of(
        EmployeeProcessStatus.PLANNED,
        EmployeeProcessStatus.IN_PROGRESS,
        EmployeeProcessStatus.DELAYED
    );

    private final WorkflowStateManager workflowStateManager;
    private final NotificationOrchestrationService notificationService;
    private final DeadlineMonitorService deadlineMonitorService;
    private final EmployeeRepository employeeRepository;
    private final EmployeeProcessRepository processRepository;
    private final EmployeeProcessTaskRepository taskRepository;
    private final EmployeeTrainingRepository employeeTrainingRepository;
    private final EmployeeBenefitRepository employeeBenefitRepository;
    private final EmployeeDocumentRepository employeeDocumentRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ExpenseClaimRepository expenseClaimRepository;
    private final OnboardingChecklistService checklistService;
    private final TrainingRepository trainingRepository;
    private final EntitlementService entitlementService;
    private final TerminationReasonRepository terminationReasonRepository;
    private final PersistentTokenRepository persistentTokenRepository;

    public EmployeeLifecycleWorkflowService(
        WorkflowStateManager workflowStateManager,
        NotificationOrchestrationService notificationService,
        DeadlineMonitorService deadlineMonitorService,
        EmployeeRepository employeeRepository,
        EmployeeProcessRepository processRepository,
        EmployeeProcessTaskRepository taskRepository,
        EmployeeTrainingRepository employeeTrainingRepository,
        EmployeeBenefitRepository employeeBenefitRepository,
        EmployeeDocumentRepository employeeDocumentRepository,
        LeaveRequestRepository leaveRequestRepository,
        ExpenseClaimRepository expenseClaimRepository,
        OnboardingChecklistService checklistService,
        TrainingRepository trainingRepository,
        EntitlementService entitlementService,
        TerminationReasonRepository terminationReasonRepository,
        PersistentTokenRepository persistentTokenRepository
    ) {
        this.workflowStateManager = workflowStateManager;
        this.notificationService = notificationService;
        this.deadlineMonitorService = deadlineMonitorService;
        this.employeeRepository = employeeRepository;
        this.processRepository = processRepository;
        this.taskRepository = taskRepository;
        this.employeeTrainingRepository = employeeTrainingRepository;
        this.employeeBenefitRepository = employeeBenefitRepository;
        this.employeeDocumentRepository = employeeDocumentRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.expenseClaimRepository = expenseClaimRepository;
        this.checklistService = checklistService;
        this.trainingRepository = trainingRepository;
        this.entitlementService = entitlementService;
        this.terminationReasonRepository = terminationReasonRepository;
        this.persistentTokenRepository = persistentTokenRepository;
    }

    // ==================== ONBOARDING ====================

    /**
     * Initiate an employee onboarding workflow.
     */
    public OnboardingProcessResponse initiateOnboarding(InitiateOnboardingRequest request) {
        log.info("Initiating onboarding for employee {}", request.employeeId());

        // Validate employee exists
        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.employeeId()));

        // Reject if an active onboarding already exists for this employee
        processRepository
            .findByEmployeeIdAndProcessTypeAndStatusIn(request.employeeId(), EmployeeProcessType.ONBOARDING, ACTIVE_PROCESS_STATUSES)
            .ifPresent(existing -> {
                throw new BadRequestAlertException("Active onboarding process already exists for employee", "process", "duplicate");
            });

        LocalDate startDate = request.startDate() != null ? request.startDate() : LocalDate.now();
        OnboardingChecklistResponse checklist = checklistService.get();
        LocalDate targetDate = request.targetDate() != null ? request.targetDate() : startDate.plusDays(checklist.defaultDays());
        if (targetDate.isBefore(startDate)) {
            throw new BadRequestAlertException("Target date must not be before the start date", "process", "invaliddates");
        }
        boolean withTrainings = request.requiredTrainingIds() != null && !request.requiredTrainingIds().isEmpty();
        if (withTrainings) {
            assignTrainings(employee, request.requiredTrainingIds());
        }

        // Create workflow instance — caller-supplied context is overlaid first
        // so trusted keys (employeeId, dates, ids) cannot be overridden.
        Map<String, Object> context = new HashMap<>();
        if (request.additionalContext() != null) context.putAll(request.additionalContext());
        context.put("employeeId", request.employeeId().toString());
        context.put("startDate", startDate.toString());
        context.put("targetDate", targetDate.toString());

        WorkflowInstance workflow = workflowStateManager.createWorkflow(
            WorkflowType.ONBOARDING,
            request.employeeId(),
            "Employee",
            context,
            null
        );

        // Create employee process
        EmployeeProcess process = createOnboardingProcess(employee, request, startDate, targetDate);
        workflowStateManager.updateContext(workflow.getId(), CONTEXT_PROCESS_ID, process.getId().toString());
        markOnboarding(employee, workflow);

        // Create onboarding tasks
        createOnboardingTasks(process, request, checklist);

        // Start the workflow
        workflowStateManager.startWorkflow(workflow.getId());
        workflowStateManager.transitionState(workflow.getId(), STATE_PROFILE_SETUP, "Starting onboarding process");

        // Due on the target date
        Instant dueDate = targetDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        workflowStateManager.updateDueDate(workflow.getId(), dueDate);

        // Register deadline
        deadlineMonitorService.registerDeadline(
            workflow.getId(),
            "ONBOARDING_COMPLETION",
            "Complete onboarding for " + employee.getFirstName() + " " + employee.getLastName(),
            dueDate,
            // 3 days warning, capped to the window so short onboardings don't warn before they start
            (int) Math.min(72, Math.max(1, ChronoUnit.DAYS.between(startDate, targetDate)) * 24),
            employee.getManager() != null ? employee.getManager().getId() : null
        );

        // Send welcome notification
        notificationService.notifyWelcome(
            employee.getId(),
            employee.getFirstName(),
            "Welcome to the team! Your onboarding process has started. Please complete the assigned tasks."
        );

        // Notify manager
        if (employee.getManager() != null) {
            notificationService.notifyTaskAssignment(
                employee.getManager().getId(),
                "New Employee Onboarding",
                employee.getFirstName() + " " + employee.getLastName() + " has joined your team. Onboarding tasks are assigned.",
                process.getId(),
                "EmployeeProcess"
            );
        }

        log.info("Onboarding initiated successfully for employee {} with workflow {}", request.employeeId(), workflow.getId());

        return getOnboardingStatus(process.getId());
    }

    /**
     * Get onboarding process status.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public OnboardingProcessResponse getOnboardingStatus(UUID processId) {
        EmployeeProcess process = processRepository
            .findById(processId)
            .orElseThrow(() -> EntityNotFoundException.create("EmployeeProcess", processId));

        if (process.getProcessType() != EmployeeProcessType.ONBOARDING) {
            throw new BadRequestAlertException("Process is not an onboarding process", "process", "invalidtype");
        }

        return mapToOnboardingResponse(process);
    }

    /**
     * Complete an onboarding task.
     */
    public OnboardingProcessResponse completeOnboardingTask(UUID processId, CompleteTaskRequest request) {
        log.debug("Completing onboarding task {} for process {}", request.taskId(), processId);

        EmployeeProcess process = processRepository
            .findById(processId)
            .orElseThrow(() -> EntityNotFoundException.create("EmployeeProcess", processId));

        if (process.getProcessType() != EmployeeProcessType.ONBOARDING) {
            throw new BadRequestAlertException("Process is not an onboarding process", "process", "invalidtype");
        }

        EmployeeProcessTask task = taskRepository
            .findById(request.taskId())
            .orElseThrow(() -> EntityNotFoundException.create("EmployeeProcessTask", request.taskId()));

        if (!task.getProcess().getId().equals(processId)) {
            throw new BadRequestAlertException("Task does not belong to this process", "task", "invalidtask");
        }

        if (Boolean.TRUE.equals(task.getCompleted())) {
            log.debug("Onboarding task {} already completed; skipping", request.taskId());
            return getOnboardingStatus(processId);
        }

        requireActive(process);

        // Complete the task
        task.complete(request.completionNotes());
        taskRepository.save(task);

        // Check if all tasks are completed
        updateProcessProgress(process);

        // Notify task completion
        if (process.getEmployee().getManager() != null) {
            notificationService.notifyTaskCompleted(
                process.getEmployee().getManager().getId(),
                "Onboarding Task Completed",
                task.getTitle() +
                " has been completed for " +
                process.getEmployee().getFirstName() +
                " " +
                process.getEmployee().getLastName(),
                task.getId(),
                "EmployeeProcessTask"
            );
        }

        log.info("Completed onboarding task {} for process {}", request.taskId(), processId);

        return getOnboardingStatus(processId);
    }

    /**
     * Cancel an onboarding process.
     */
    public void cancelOnboarding(UUID processId, String reason) {
        log.info("Cancelling onboarding process {} with reason: {}", processId, reason);

        EmployeeProcess process = processRepository
            .findById(processId)
            .orElseThrow(() -> EntityNotFoundException.create("EmployeeProcess", processId));

        if (process.getProcessType() != EmployeeProcessType.ONBOARDING) {
            throw new BadRequestAlertException("Process is not an onboarding process", "process", "invalidtype");
        }

        EmployeeProcessStatus status = process.getStatus();
        if (status == EmployeeProcessStatus.COMPLETED || status == EmployeeProcessStatus.CANCELLED) {
            throw new BadRequestAlertException("Cannot cancel a process that is already " + status, "process", "invalidstatus");
        }

        process.setStatus(EmployeeProcessStatus.CANCELLED);
        process.setNotes(reason);
        processRepository.save(process);
        endOnboardingStatus(process.getEmployee(), statusBefore(findWorkflowForProcess(process)));

        // Cancel workflow if exists
        WorkflowInstance workflow = findWorkflowForProcess(process);
        if (workflow != null && isActive(workflow)) {
            workflowStateManager.cancelWorkflow(workflow.getId(), reason);
        }

        // Notify stakeholders
        notificationService.notifyWorkflowCompleted(
            process.getEmployee().getId(),
            "Onboarding Cancelled",
            "Your onboarding process has been cancelled. Reason: " + reason,
            processId,
            "ONBOARDING"
        );

        log.info("Cancelled onboarding process {}", processId);
    }

    // ==================== OFFBOARDING ====================

    /**
     * Initiate an employee offboarding workflow.
     */
    public OffboardingProcessResponse initiateOffboarding(InitiateOffboardingRequest request) {
        log.info("Initiating offboarding for employee {}", request.employeeId());

        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.employeeId()));

        processRepository
            .findByEmployeeIdAndProcessTypeAndStatusIn(request.employeeId(), EmployeeProcessType.OFFBOARDING, ACTIVE_PROCESS_STATUSES)
            .ifPresent(existing -> {
                throw new BadRequestAlertException("Active offboarding process already exists for employee", "process", "duplicate");
            });
        validateOffboardingStart(employee, request);

        // Create workflow context — caller-supplied context is overlaid first
        // so trusted keys cannot be overridden.
        Map<String, Object> context = new HashMap<>();
        if (request.additionalContext() != null) context.putAll(request.additionalContext());
        context.put("employeeId", request.employeeId().toString());
        context.put("lastWorkingDate", request.lastWorkingDate().toString());
        context.put(CONTEXT_REASON, request.reason());
        context.put("offboardingType", request.offboardingType());
        context.put(CONTEXT_EXIT_INTERVIEW, request.conductExitInterview());
        if (request.terminationReasonId() != null) {
            context.put(CONTEXT_TERMINATION_REASON_ID, request.terminationReasonId().toString());
        }

        WorkflowInstance workflow = workflowStateManager.createWorkflow(
            WorkflowType.OFFBOARDING,
            request.employeeId(),
            "Employee",
            context,
            null
        );

        // Create employee process
        EmployeeProcess process = createOffboardingProcess(employee, request, workflow);
        workflowStateManager.updateContext(workflow.getId(), CONTEXT_PROCESS_ID, process.getId().toString());
        markOffboarding(employee, workflow);

        // Create offboarding tasks
        createOffboardingTasks(process, request);

        // Start the workflow
        workflowStateManager.startWorkflow(workflow.getId());
        workflowStateManager.transitionState(workflow.getId(), STATE_PENDING_REQUESTS_PROCESSING, "Starting offboarding process");

        // Set due date to last working date
        Instant dueDate = request.lastWorkingDate().atStartOfDay(ZoneId.systemDefault()).toInstant();
        workflowStateManager.updateDueDate(workflow.getId(), dueDate);

        // Register deadline
        deadlineMonitorService.registerDeadline(
            workflow.getId(),
            "OFFBOARDING_COMPLETION",
            "Complete offboarding for " + employee.getFirstName() + " " + employee.getLastName(),
            dueDate,
            168, // 7 days warning
            employee.getManager() != null ? employee.getManager().getId() : null
        );

        // Leave that would fall after they've gone is withdrawn
        processPendingRequests(employee, request.lastWorkingDate());

        // Notify employee
        notificationService.sendReminder(
            employee.getId(),
            "Offboarding Process Started",
            "Your offboarding process has been initiated. Last working date: " + request.lastWorkingDate(),
            process.getId(),
            "EmployeeProcess"
        );

        // Notify manager and HR
        if (employee.getManager() != null) {
            notificationService.notifyTaskAssignment(
                employee.getManager().getId(),
                "Employee Offboarding",
                employee.getFirstName() + " " + employee.getLastName() + " is leaving. Offboarding tasks are assigned.",
                process.getId(),
                "EmployeeProcess"
            );
        }

        log.info("Offboarding initiated successfully for employee {} with workflow {}", request.employeeId(), workflow.getId());

        // A last working day already in the past means they have left: terminate now.
        terminateIfPastLastDay(process);

        return getOffboardingStatus(process.getId());
    }

    /**
     * Get offboarding process status.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public OffboardingProcessResponse getOffboardingStatus(UUID processId) {
        EmployeeProcess process = processRepository
            .findById(processId)
            .orElseThrow(() -> EntityNotFoundException.create("EmployeeProcess", processId));

        if (process.getProcessType() != EmployeeProcessType.OFFBOARDING) {
            throw new BadRequestAlertException("Process is not an offboarding process", "process", "invalidtype");
        }

        return mapToOffboardingResponse(process);
    }

    /**
     * Complete an offboarding task.
     */
    public OffboardingProcessResponse completeOffboardingTask(UUID processId, CompleteTaskRequest request) {
        log.debug("Completing offboarding task {} for process {}", request.taskId(), processId);

        EmployeeProcess process = processRepository
            .findById(processId)
            .orElseThrow(() -> EntityNotFoundException.create("EmployeeProcess", processId));

        if (process.getProcessType() != EmployeeProcessType.OFFBOARDING) {
            throw new BadRequestAlertException("Process is not an offboarding process", "process", "invalidtype");
        }

        EmployeeProcessTask task = taskRepository
            .findById(request.taskId())
            .orElseThrow(() -> EntityNotFoundException.create("EmployeeProcessTask", request.taskId()));

        if (!task.getProcess().getId().equals(processId)) {
            throw new BadRequestAlertException("Task does not belong to this process", "task", "invalidtask");
        }

        if (Boolean.TRUE.equals(task.getCompleted())) {
            log.debug("Offboarding task {} already completed; skipping", request.taskId());
            return getOffboardingStatus(processId);
        }

        requireActive(process);

        task.complete(request.completionNotes());
        taskRepository.save(task);

        updateProcessProgress(process);

        log.info("Completed offboarding task {} for process {}", request.taskId(), processId);

        return getOffboardingStatus(processId);
    }

    /**
     * Calculate final settlement for an employee.
     *
     * <p>Only approved expense claims are computed. Leave encashment and
     * overtime pay are returned as {@code null} because there is no leave
     * balance or overtime pay-rate model to derive them from yet; the total
     * covers the known components only.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public OffboardingProcessResponse.FinalSettlementSummary calculateFinalSettlement(UUID employeeId) {
        log.debug("Calculating final settlement for employee {}", employeeId);

        Employee employee = employeeRepository
            .findById(employeeId)
            .orElseThrow(() -> EntityNotFoundException.create("Employee", employeeId));

        // Calculate pending expense reimbursements
        BigDecimal pendingExpenses = expenseClaimRepository
            .findByEmployeeId(employeeId, Pageable.unpaged())
            .stream()
            .filter(e -> e.getStatus() == ExpenseClaimStatus.APPROVED)
            .map(ExpenseClaim::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new OffboardingProcessResponse.FinalSettlementSummary(null, null, pendingExpenses, null, pendingExpenses);
    }

    // ==================== COMMON ====================

    /**
     * Get all active (planned, in-progress or delayed) processes with pagination.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public Page<EmployeeProcessSummary> getActiveProcesses(EmployeeProcessType type, Pageable pageable) {
        return getProcesses(type, null, null, pageable);
    }

    /**
     * Processes of a type in the given statuses (active ones when none are given),
     * optionally for one employee.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public Page<EmployeeProcessSummary> getProcesses(
        EmployeeProcessType type,
        List<EmployeeProcessStatus> statuses,
        UUID employeeId,
        Pageable pageable
    ) {
        List<EmployeeProcessStatus> wanted = statuses == null || statuses.isEmpty() ? ACTIVE_PROCESS_STATUSES : statuses;
        Page<EmployeeProcess> page;
        if (employeeId != null) {
            page = processRepository.findByEmployeeIdAndProcessTypeAndStatusIn(employeeId, type, wanted, pageable);
        } else if (type != null) {
            page = processRepository.findByProcessTypeAndStatusIn(type, wanted, pageable);
        } else {
            page = processRepository.findByStatusIn(wanted, pageable);
        }
        return page.map(this::mapToSummary);
    }

    /**
     * Escalate overdue tasks of a process of the given type.
     */
    public void escalateOverdueTasks(UUID processId, EmployeeProcessType type) {
        log.info("Escalating overdue tasks for process {}", processId);

        EmployeeProcess process = processRepository
            .findById(processId)
            .orElseThrow(() -> EntityNotFoundException.create("EmployeeProcess", processId));

        if (process.getProcessType() != type) {
            throw new BadRequestAlertException("Process is not an " + type.name().toLowerCase() + " process", "process", "invalidtype");
        }

        LocalDate today = LocalDate.now();
        List<EmployeeProcessTask> overdueTasks = process
            .getTasks()
            .stream()
            .filter(t -> !t.getCompleted() && t.getDueDate() != null && t.getDueDate().isBefore(today))
            .collect(Collectors.toList());

        for (EmployeeProcessTask task : overdueTasks) {
            if (task.getAssignedTo() != null && task.getAssignedTo().getManager() != null) {
                notificationService.notifyEscalation(
                    task.getAssignedTo().getManager().getId(),
                    "Overdue Task Escalation",
                    "Task '" +
                    task.getTitle() +
                    "' is overdue for " +
                    process.getEmployee().getFirstName() +
                    " " +
                    process.getEmployee().getLastName(),
                    task.getId(),
                    "EmployeeProcessTask",
                    1
                );
            }
        }

        log.info("Escalated {} overdue tasks for process {}", overdueTasks.size(), processId);
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private EmployeeProcess createOnboardingProcess(
        Employee employee,
        InitiateOnboardingRequest request,
        LocalDate startDate,
        LocalDate targetDate
    ) {
        EmployeeProcess process = new EmployeeProcess();
        process.setEmployee(employee);
        process.setProcessType(EmployeeProcessType.ONBOARDING);
        process.setStatus(EmployeeProcessStatus.IN_PROGRESS);
        process.setStartDate(startDate);
        process.setDueDate(targetDate);
        process.setNotes(request.notes());

        return processRepository.save(process);
    }

    /**
     * Copies the company checklist into this process's tasks. Due days are on a
     * {@code checklist.defaultDays()}-day onboarding; {@link #scheduled} scales
     * them to the process's own start → due window.
     */
    private void createOnboardingTasks(EmployeeProcess process, InitiateOnboardingRequest request, OnboardingChecklistResponse checklist) {
        Employee employee = process.getEmployee();
        int baselineDays = checklist.defaultDays();

        for (OnboardingChecklistResponse.Task task : checklist.tasks()) {
            Employee assignee =
                switch (task.owner()) {
                    case NEW_HIRE -> employee;
                    case MANAGER -> employee.getManager();
                    case HR -> null;
                };
            createTask(process, task.title(), task.description(), scheduled(process, task.dueDay(), baselineDays), assignee, task.link());
        }

        // Mandatory trainings, when the request names any (70% of the way in); done once they're completed
        if (request.requiredTrainingIds() != null && !request.requiredTrainingIds().isEmpty()) {
            createTask(
                process,
                "Complete Mandatory Trainings",
                "Complete all assigned mandatory training programs",
                scheduled(process, (int) Math.round(baselineDays * 0.7), baselineDays),
                employee,
                OnboardingTaskLink.TRAININGS
            );
        }
    }

    /**
     * Enrolls the new hire in each training (planned), skipping ones they already
     * have. Needs the Training module; unknown training ids are rejected.
     */
    private void assignTrainings(Employee employee, List<UUID> trainingIds) {
        entitlementService.checkIncluded(FeatureCodes.TRAINING);
        for (UUID trainingId : new LinkedHashSet<>(trainingIds)) {
            Training training = trainingRepository
                .findById(trainingId)
                .orElseThrow(() -> new BadRequestAlertException("Unknown training " + trainingId, "process", "unknowntraining"));
            if (employeeTrainingRepository.existsByEmployeeIdAndTrainingId(employee.getId(), trainingId)) {
                continue;
            }
            EmployeeTraining enrollment = new EmployeeTraining();
            enrollment.setEmployee(employee);
            enrollment.setTraining(training);
            enrollment.setStatus(TrainingStatus.PLANNED);
            employeeTrainingRepository.save(enrollment);
        }
    }

    /**
     * Due date for a task {@code dueDay} days into a {@code baselineDays}-day
     * onboarding, scaled to this process and kept within its start → due window.
     */
    private static LocalDate scheduled(EmployeeProcess process, int dueDay, int baselineDays) {
        long window = ChronoUnit.DAYS.between(process.getStartDate(), process.getDueDate());
        long offset = Math.round(((double) dueDay * window) / Math.max(1, baselineDays));
        return process.getStartDate().plusDays(Math.min(window, Math.max(0, offset)));
    }

    private EmployeeProcess createOffboardingProcess(Employee employee, InitiateOffboardingRequest request, WorkflowInstance workflow) {
        EmployeeProcess process = new EmployeeProcess();
        process.setEmployee(employee);
        process.setProcessType(EmployeeProcessType.OFFBOARDING);
        process.setStatus(EmployeeProcessStatus.IN_PROGRESS);
        process.setStartDate(LocalDate.now());
        process.setDueDate(request.lastWorkingDate());
        process.setNotes(request.notes());

        return processRepository.save(process);
    }

    /**
     * Creates the leaver's checklist. Each task is written as "N days before the last working
     * day" on a {@value #OFFBOARDING_BASELINE_DAYS}-day notice period and scaled to this
     * offboarding's own start → last-day window, so a short notice keeps the same order without
     * tasks falling due before it started.
     */
    private void createOffboardingTasks(EmployeeProcess process, InitiateOffboardingRequest request) {
        Employee employee = process.getEmployee();

        createTask(
            process,
            "Knowledge Transfer Documentation",
            "Document all ongoing projects, processes, and key contacts",
            beforeLastDay(process, 14),
            employee
        );
        createTask(
            process,
            "Project Handover",
            "Complete handover of all assigned projects to designated colleagues",
            beforeLastDay(process, 7),
            employee
        );
        createTask(
            process,
            "Return Company Assets",
            "Return laptop, ID card, keys, and other company property",
            beforeLastDay(process, 0),
            employee
        );
        createTask(
            process,
            "Submit Pending Expenses",
            "Submit all pending expense claims for reimbursement",
            beforeLastDay(process, 10),
            employee
        );
        createTask(
            process,
            "Benefits Termination Review",
            "Review when benefits end and any options to continue them",
            beforeLastDay(process, 7),
            null
        );
        createTask(process, "IT Access Revocation", "Revoke all system access and collect credentials", beforeLastDay(process, 0), null);
        if (request.conductExitInterview()) {
            createTask(
                process,
                "Exit Interview",
                "Conduct exit interview to gather feedback",
                beforeLastDay(process, 3),
                request.exitInterviewerId() != null ? employeeRepository.findById(request.exitInterviewerId()).orElse(null) : null
            );
        }
        createTask(
            process,
            "Final Paycheck Processing",
            "Process final paycheck including any settlements",
            beforeLastDay(process, 0),
            null
        );
        createTask(
            process,
            "Farewell Communication",
            "Send farewell email to team and relevant stakeholders",
            beforeLastDay(process, 1),
            employee
        );
    }

    /** Notice period the offboarding checklist's "days before the last day" are written for. */
    static final int OFFBOARDING_BASELINE_DAYS = 30;

    /**
     * Due date for a task {@code daysBefore} the last working day on a
     * {@value #OFFBOARDING_BASELINE_DAYS}-day notice, scaled to this process and kept
     * within its start → last-day window (a backdated last day puts everything on that day).
     */
    private static LocalDate beforeLastDay(EmployeeProcess process, int daysBefore) {
        LocalDate lastDay = process.getDueDate();
        long window = ChronoUnit.DAYS.between(process.getStartDate(), lastDay);
        if (window <= 0) {
            return lastDay;
        }
        int fromStart = Math.max(0, OFFBOARDING_BASELINE_DAYS - daysBefore);
        long offset = Math.round(((double) fromStart * window) / OFFBOARDING_BASELINE_DAYS);
        return process.getStartDate().plusDays(Math.min(window, Math.max(0, offset)));
    }

    private EmployeeProcessTask createTask(
        EmployeeProcess process,
        String title,
        String description,
        LocalDate dueDate,
        Employee assignedTo
    ) {
        return createTask(process, title, description, dueDate, assignedTo, null);
    }

    private EmployeeProcessTask createTask(
        EmployeeProcess process,
        String title,
        String description,
        LocalDate dueDate,
        Employee assignedTo,
        OnboardingTaskLink link
    ) {
        EmployeeProcessTask task = new EmployeeProcessTask();
        task.setProcess(process);
        task.setTitle(title);
        task.setDescription(description);
        task.setDueDate(dueDate);
        task.setCompleted(false);
        task.setAssignedTo(assignedTo);
        task.setLink(link);

        // Add after save: equals/hashCode are id-based, and the response is
        // built from process.getTasks() in this same persistence context.
        EmployeeProcessTask saved = taskRepository.save(task);
        process.getTasks().add(saved);
        return saved;
    }

    private void updateProcessProgress(EmployeeProcess process) {
        Set<EmployeeProcessTask> tasks = process.getTasks();
        long completedCount = tasks.stream().filter(EmployeeProcessTask::getCompleted).count();

        int completionPercentage = tasks.isEmpty() ? 0 : (int) ((completedCount * 100) / tasks.size());
        boolean allDone = !tasks.isEmpty() && completedCount == tasks.size();

        // Process completion is independent of whether an active workflow still exists.
        if (allDone && process.getStatus() != EmployeeProcessStatus.COMPLETED) {
            process.setStatus(EmployeeProcessStatus.COMPLETED);
            process.setCompletionDate(LocalDate.now());
            processRepository.save(process);
            if (process.getProcessType() == EmployeeProcessType.ONBOARDING) {
                endOnboardingStatus(process.getEmployee(), EmployeeStatus.ACTIVE);
            }

            notificationService.notifyWorkflowCompleted(
                process.getEmployee().getId(),
                process.getProcessType() == EmployeeProcessType.ONBOARDING ? "Onboarding Complete" : "Offboarding Complete",
                "Your " + process.getProcessType().name().toLowerCase() + " process has been completed.",
                process.getId(),
                process.getProcessType().name()
            );
        }

        WorkflowInstance workflow = findWorkflowForProcess(process);
        if (workflow != null && isActive(workflow)) {
            workflowStateManager.updateContext(workflow.getId(), "completionPercentage", completionPercentage);
            if (allDone) {
                workflowStateManager.completeWorkflow(workflow.getId(), "All tasks completed");
            }
        }
    }

    /**
     * Cancel an offboarding. Someone still OFFBOARDING gets back the status they had before it
     * started; someone already terminated (past their last day) stays terminated — reinstating
     * them is a decision for their profile, not a side effect of closing the checklist.
     */
    public void cancelOffboarding(UUID processId, String reason) {
        log.info("Cancelling offboarding process {} with reason: {}", processId, reason);

        EmployeeProcess process = processRepository
            .findById(processId)
            .orElseThrow(() -> EntityNotFoundException.create("EmployeeProcess", processId));
        if (process.getProcessType() != EmployeeProcessType.OFFBOARDING) {
            throw new BusinessRuleViolationException("PROCESS_INVALID_TYPE", "Process is not an offboarding process");
        }
        if (!ACTIVE_PROCESS_STATUSES.contains(process.getStatus())) {
            throw new BusinessRuleViolationException(
                "PROCESS_INVALID_STATUS",
                "Cannot cancel a process that is already " + process.getStatus()
            );
        }

        process.setStatus(EmployeeProcessStatus.CANCELLED);
        process.setNotes(reason);
        processRepository.save(process);

        WorkflowInstance workflow = findWorkflowForProcess(process);
        endProcessStatus(process.getEmployee(), EmployeeStatus.OFFBOARDING, statusBefore(workflow));
        if (workflow != null && isActive(workflow)) {
            workflowStateManager.cancelWorkflow(workflow.getId(), reason);
        }
        log.info("Cancelled offboarding process {}", processId);
    }

    /**
     * Daily: everyone whose last working day has passed and who is still OFFBOARDING becomes
     * TERMINATED (see {@link #terminateIfPastLastDay}). Selecting "before today" rather than
     * "yesterday" lets a missed run catch up. Runs within the current tenant.
     *
     * @return how many employees were terminated
     */
    public int terminateDueOffboardings() {
        List<EmployeeProcessStatus> notCancelled = List.of(
            EmployeeProcessStatus.PLANNED,
            EmployeeProcessStatus.IN_PROGRESS,
            EmployeeProcessStatus.DELAYED,
            EmployeeProcessStatus.COMPLETED
        );
        int terminated = 0;
        for (EmployeeProcess process : processRepository.findByProcessTypeAndStatusInAndDueDateBeforeAndEmployeeStatus(
            EmployeeProcessType.OFFBOARDING,
            notCancelled,
            LocalDate.now(),
            EmployeeStatus.OFFBOARDING
        )) {
            if (terminateIfPastLastDay(process)) {
                terminated++;
            }
        }
        return terminated;
    }

    /**
     * Ends employment once the last working day is over: an employee still OFFBOARDING becomes
     * TERMINATED, with the last working day as end date (unless one is already set) and the
     * chosen termination reason and stated reason recorded. Their account is deactivated (sign-in
     * and "remember me" refused) and their remembered sign-ins are removed. Idempotent; leaves a
     * status changed by hand (or a cancelled offboarding) alone.
     *
     * @return whether the employee was terminated by this call
     */
    boolean terminateIfPastLastDay(EmployeeProcess process) {
        Employee employee = process.getEmployee();
        LocalDate lastDay = process.getDueDate();
        if (
            process.getProcessType() != EmployeeProcessType.OFFBOARDING ||
            process.getStatus() == EmployeeProcessStatus.CANCELLED ||
            lastDay == null ||
            !lastDay.isBefore(LocalDate.now()) ||
            employee.getStatus() != EmployeeStatus.OFFBOARDING
        ) {
            return false;
        }
        WorkflowInstance workflow = findWorkflowForProcess(process);
        Map<String, Object> context = workflow != null && workflow.getContext() != null ? workflow.getContext() : Map.of();

        employee.setStatus(EmployeeStatus.TERMINATED);
        employee.setActivated(false);
        persistentTokenRepository.deleteAll(persistentTokenRepository.findByUser(employee));
        if (employee.getEndDate() == null) {
            employee.setEndDate(lastDay);
        }
        Object reasonId = context.get(CONTEXT_TERMINATION_REASON_ID);
        if (reasonId != null && employee.getTerminationReason() == null) {
            terminationReasonRepository.findById(UUID.fromString(reasonId.toString())).ifPresent(employee::setTerminationReason);
        }
        Object reason = context.get(CONTEXT_REASON);
        if (reason != null && (employee.getTerminationNotes() == null || employee.getTerminationNotes().isBlank())) {
            employee.setTerminationNotes(reason.toString());
        }
        employeeRepository.save(employee);
        log.info("Employee {} terminated after their last working day {}", employee.getId(), lastDay);
        return true;
    }

    /**
     * Refuses an offboarding for someone who has already left, or that names a termination
     * reason or exit interviewer that doesn't exist.
     */
    private void validateOffboardingStart(Employee employee, InitiateOffboardingRequest request) {
        if (employee.getStatus() == EmployeeStatus.TERMINATED) {
            throw new BusinessRuleViolationException("OFFBOARDING_ALREADY_TERMINATED", "This employee has already left");
        }
        if (request.terminationReasonId() != null && !terminationReasonRepository.existsById(request.terminationReasonId())) {
            throw new BusinessRuleViolationException(
                "OFFBOARDING_UNKNOWN_REASON",
                "Unknown termination reason " + request.terminationReasonId()
            );
        }
        if (
            request.conductExitInterview() &&
            request.exitInterviewerId() != null &&
            !employeeRepository.existsById(request.exitInterviewerId())
        ) {
            throw new BusinessRuleViolationException(
                "OFFBOARDING_UNKNOWN_INTERVIEWER",
                "Unknown exit interviewer " + request.exitInterviewerId()
            );
        }
    }

    /**
     * Offboarding starts: the employee becomes OFFBOARDING (still employed — paid and on team
     * views — until their last day). The previous status is kept on the workflow so a
     * cancellation can restore it. An onboarding still running is left as it is.
     */
    private void markOffboarding(Employee employee, WorkflowInstance workflow) {
        EmployeeStatus before = employee.getStatus();
        workflowStateManager.updateContext(
            workflow.getId(),
            CONTEXT_STATUS_BEFORE,
            (before == null || before == EmployeeStatus.ONBOARDING || before == EmployeeStatus.OFFBOARDING
                    ? EmployeeStatus.ACTIVE
                    : before).name()
        );
        if (before != EmployeeStatus.OFFBOARDING) {
            employee.setStatus(EmployeeStatus.OFFBOARDING);
            employeeRepository.save(employee);
        }
    }

    /**
     * Onboarding starts: an active employee (or one with no status yet) becomes ONBOARDING; the
     * previous status is kept on the workflow so a cancellation can restore it. Inactive or
     * terminated employees keep their status.
     */
    private void markOnboarding(Employee employee, WorkflowInstance workflow) {
        EmployeeStatus before = employee.getStatus();
        if (before != null && before != EmployeeStatus.ACTIVE && before != EmployeeStatus.ONBOARDING) {
            return;
        }
        workflowStateManager.updateContext(
            workflow.getId(),
            CONTEXT_STATUS_BEFORE,
            (before == null || before == EmployeeStatus.ONBOARDING ? EmployeeStatus.ACTIVE : before).name()
        );
        if (before != EmployeeStatus.ONBOARDING) {
            employee.setStatus(EmployeeStatus.ONBOARDING);
            employeeRepository.save(employee);
        }
    }

    /**
     * Onboarding ends (completed or cancelled): an employee still in ONBOARDING moves to
     * {@code next}. A status HR changed by hand in the meantime is left alone.
     */
    private void endOnboardingStatus(Employee employee, EmployeeStatus next) {
        endProcessStatus(employee, EmployeeStatus.ONBOARDING, next);
    }

    /** Moves an employee still in {@code expected} to {@code next}; any other status is left alone. */
    private void endProcessStatus(Employee employee, EmployeeStatus expected, EmployeeStatus next) {
        if (employee.getStatus() == expected) {
            employee.setStatus(next);
            employeeRepository.save(employee);
        }
    }

    /** Status recorded when onboarding started; ACTIVE when unknown (older onboardings). */
    private static EmployeeStatus statusBefore(WorkflowInstance workflow) {
        Object value = workflow != null && workflow.getContext() != null ? workflow.getContext().get(CONTEXT_STATUS_BEFORE) : null;
        try {
            return value != null ? EmployeeStatus.valueOf(value.toString()) : EmployeeStatus.ACTIVE;
        } catch (IllegalArgumentException e) {
            return EmployeeStatus.ACTIVE;
        }
    }

    private void requireActive(EmployeeProcess process) {
        if (!ACTIVE_PROCESS_STATUSES.contains(process.getStatus())) {
            throw new BadRequestAlertException(
                "Cannot complete tasks on a process that is " + process.getStatus(),
                "process",
                "invalidstatus"
            );
        }
    }

    /**
     * Resolve the workflow that drives {@code process}, in any status. Workflows
     * carrying {@link #CONTEXT_PROCESS_ID} match exactly; workflows created
     * before that key existed fall back to the employee's most recent
     * unlinked workflow of the matching type.
     */
    private WorkflowInstance findWorkflowForProcess(EmployeeProcess process) {
        WorkflowType type = process.getProcessType() == EmployeeProcessType.ONBOARDING ? WorkflowType.ONBOARDING : WorkflowType.OFFBOARDING;
        String processId = process.getId().toString();
        List<WorkflowInstance> candidates = workflowStateManager
            .findByEntityId(process.getEmployee().getId())
            .stream()
            .filter(w -> w.getWorkflowType() == type)
            .toList();
        return candidates
            .stream()
            .filter(w -> processId.equals(linkedProcessId(w)))
            .findFirst()
            .orElseGet(() ->
                candidates
                    .stream()
                    .filter(w -> linkedProcessId(w) == null)
                    .max(Comparator.comparing(WorkflowInstance::getCreatedDate, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElse(null)
            );
    }

    private static Object linkedProcessId(WorkflowInstance workflow) {
        return workflow.getContext() != null ? workflow.getContext().get(CONTEXT_PROCESS_ID) : null;
    }

    private static boolean isActive(WorkflowInstance workflow) {
        return switch (workflow.getStatus()) {
            case COMPLETED, CANCELLED, REJECTED, FAILED -> false;
            default -> true;
        };
    }

    /** Workflow status to report when no backing workflow can be found. */
    private static WorkflowStatus fallbackWorkflowStatus(EmployeeProcess process) {
        return switch (process.getStatus()) {
            case COMPLETED -> WorkflowStatus.COMPLETED;
            case CANCELLED -> WorkflowStatus.CANCELLED;
            default -> WorkflowStatus.IN_PROGRESS;
        };
    }

    /** Withdraws pending leave that would start after the last working day. */
    private void processPendingRequests(Employee employee, LocalDate lastWorkingDate) {
        leaveRequestRepository
            .findByEmployeeId(employee.getId(), Pageable.unpaged())
            .stream()
            .filter(lr -> lr.getStatus() == LeaveStatus.PENDING)
            .filter(lr -> lr.getStartDate() == null || lr.getStartDate().isAfter(lastWorkingDate))
            .forEach(lr -> {
                lr.setStatus(LeaveStatus.CANCELED);
                leaveRequestRepository.save(lr);
            });

        log.debug("Processed pending requests for employee {}", employee.getId());
    }

    private EmployeeProcessSummary mapToSummary(EmployeeProcess process) {
        Employee employee = process.getEmployee();
        Set<EmployeeProcessTask> tasks = process.getTasks();
        return new EmployeeProcessSummary(
            process.getId(),
            process.getProcessType(),
            process.getStatus(),
            employee.getId(),
            employee.getFirstName() + " " + employee.getLastName(),
            process.getStartDate(),
            process.getDueDate(),
            process.getCompletionDate(),
            process.getCompletionPercentage(),
            tasks.size(),
            (int) tasks.stream().filter(EmployeeProcessTask::getCompleted).count(),
            (int) tasks
                .stream()
                .filter(t -> !Boolean.TRUE.equals(t.getCompleted()) && t.getDueDate() != null && t.getDueDate().isBefore(LocalDate.now()))
                .count(),
            process.getNotes(),
            process.getLastModifiedDate()
        );
    }

    private OnboardingProcessResponse mapToOnboardingResponse(EmployeeProcess process) {
        Employee employee = process.getEmployee();

        WorkflowInstance workflow = findWorkflowForProcess(process);

        // What the employee's records show, read once for the readiness flags and the linked tasks.
        boolean benefitsOnFile = employeeBenefitRepository.existsByEmployeeId(employee.getId());
        boolean trainingsAssigned = employeeTrainingRepository.existsByEmployeeId(employee.getId());
        boolean documentsOnFile = employeeDocumentRepository.existsByEmployeeId(employee.getId());
        boolean trainingsCompleted =
            employeeTrainingRepository.existsByEmployeeIdAndStatus(employee.getId(), TrainingStatus.COMPLETED) &&
            !employeeTrainingRepository.existsByEmployeeIdAndStatus(employee.getId(), TrainingStatus.PLANNED);
        Map<OnboardingTaskLink, Boolean> evidence = Map.of(
            OnboardingTaskLink.ACCOUNT,
            employee.isActivated(),
            OnboardingTaskLink.DOCUMENTS,
            documentsOnFile,
            OnboardingTaskLink.BENEFITS,
            benefitsOnFile,
            OnboardingTaskLink.TRAININGS,
            trainingsCompleted
        );

        List<OnboardingProcessResponse.OnboardingTaskResponse> taskResponses = process
            .getTasks()
            .stream()
            .map(task ->
                new OnboardingProcessResponse.OnboardingTaskResponse(
                    task.getId(),
                    task.getTitle(),
                    task.getDescription(),
                    task.getDueDate(),
                    task.getCompleted(),
                    task.getCompletionDate(),
                    task.getAssignedTo() != null ? task.getAssignedTo().getId() : null,
                    task.getAssignedTo() != null ? task.getAssignedTo().getFirstName() + " " + task.getAssignedTo().getLastName() : null,
                    task.getCompletionNotes(),
                    task.getLink(),
                    task.getLink() != null ? evidence.get(task.getLink()) : null
                )
            )
            .collect(Collectors.toList());

        OnboardingProcessResponse.OnboardingProgress progress = new OnboardingProcessResponse.OnboardingProgress(
            true, // profileCreated: the employee record exists
            employee.getDepartment() != null,
            benefitsOnFile,
            trainingsAssigned,
            documentsOnFile,
            employee.isActivated(),
            process.getTasks().size(),
            (int) process.getTasks().stream().filter(EmployeeProcessTask::getCompleted).count()
        );

        return new OnboardingProcessResponse(
            process.getId(),
            workflow != null ? workflow.getId() : null,
            employee.getId(),
            employee.getFirstName() + " " + employee.getLastName(),
            employee.getEmail(),
            workflow != null ? workflow.getStatus() : fallbackWorkflowStatus(process),
            process.getStatus(),
            workflow != null ? workflow.getCurrentState() : STATE_TASKS_IN_PROGRESS,
            process.getStartDate(),
            process.getDueDate(),
            process.getCompletionPercentage(),
            taskResponses,
            progress,
            process.getCreatedDate(),
            process.getLastModifiedDate()
        );
    }

    private OffboardingProcessResponse mapToOffboardingResponse(EmployeeProcess process) {
        Employee employee = process.getEmployee();

        WorkflowInstance workflow = findWorkflowForProcess(process);

        List<OffboardingProcessResponse.OffboardingTaskResponse> taskResponses = process
            .getTasks()
            .stream()
            .map(task ->
                new OffboardingProcessResponse.OffboardingTaskResponse(
                    task.getId(),
                    task.getTitle(),
                    task.getDescription(),
                    task.getDueDate(),
                    task.getCompleted(),
                    task.getCompletionDate(),
                    task.getAssignedTo() != null ? task.getAssignedTo().getId() : null,
                    task.getAssignedTo() != null ? task.getAssignedTo().getFirstName() + " " + task.getAssignedTo().getLastName() : null,
                    task.getCompletionNotes()
                )
            )
            .collect(Collectors.toList());

        Set<EmployeeProcessTask> tasks = process.getTasks();
        OffboardingProcessResponse.OffboardingProgress progress = new OffboardingProcessResponse.OffboardingProgress(
            tasks.stream().anyMatch(t -> t.getTitle().contains("Pending") && t.getCompleted()),
            tasks.stream().anyMatch(t -> t.getTitle().contains("Final Paycheck") && t.getCompleted()),
            tasks.stream().anyMatch(t -> t.getTitle().contains("Benefits") && t.getCompleted()),
            tasks.stream().anyMatch(t -> t.getTitle().contains("Knowledge Transfer") && t.getCompleted()),
            tasks.stream().anyMatch(t -> t.getTitle().contains("Assets") && t.getCompleted()),
            tasks.stream().anyMatch(t -> t.getTitle().contains("Exit Interview") && t.getCompleted()),
            tasks.stream().anyMatch(t -> t.getTitle().contains("IT Access") && t.getCompleted()),
            tasks.size(),
            (int) tasks.stream().filter(EmployeeProcessTask::getCompleted).count()
        );

        OffboardingProcessResponse.FinalSettlementSummary settlement = calculateFinalSettlement(employee.getId());

        Map<String, Object> context = workflow != null && workflow.getContext() != null ? workflow.getContext() : Map.of();
        Object reason = context.get(CONTEXT_REASON);
        Object reasonId = context.get(CONTEXT_TERMINATION_REASON_ID);
        TerminationReason terminationReason = reasonId != null
            ? terminationReasonRepository.findById(UUID.fromString(reasonId.toString())).orElse(null)
            : null;

        return new OffboardingProcessResponse(
            process.getId(),
            workflow != null ? workflow.getId() : null,
            employee.getId(),
            employee.getFirstName() + " " + employee.getLastName(),
            employee.getEmail(),
            workflow != null ? workflow.getStatus() : fallbackWorkflowStatus(process),
            process.getStatus(),
            workflow != null ? workflow.getCurrentState() : STATE_EXIT_TASKS,
            process.getStartDate(),
            process.getDueDate(),
            reason != null ? reason.toString() : null,
            terminationReason != null ? terminationReason.getId() : null,
            terminationReason != null ? terminationReason.getName() : null,
            Boolean.TRUE.equals(context.get(CONTEXT_EXIT_INTERVIEW)),
            process.getCompletionPercentage(),
            taskResponses,
            progress,
            settlement,
            process.getCreatedDate(),
            process.getLastModifiedDate()
        );
    }
}
