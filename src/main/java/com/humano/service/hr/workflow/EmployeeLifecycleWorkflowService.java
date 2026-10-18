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
        OnboardingChecklistService checklistService
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

        // Create workflow instance — caller-supplied context is overlaid first
        // so trusted keys (employeeId, dates, ids) cannot be overridden.
        Map<String, Object> context = new HashMap<>();
        if (request.additionalContext() != null) context.putAll(request.additionalContext());
        context.put("employeeId", request.employeeId().toString());
        context.put("startDate", startDate.toString());
        context.put("targetDate", targetDate.toString());
        if (request.departmentId() != null) context.put("departmentId", request.departmentId().toString());
        if (request.positionId() != null) context.put("positionId", request.positionId().toString());
        if (request.managerId() != null) context.put("managerId", request.managerId().toString());

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

        // Create workflow context — caller-supplied context is overlaid first
        // so trusted keys cannot be overridden.
        Map<String, Object> context = new HashMap<>();
        if (request.additionalContext() != null) context.putAll(request.additionalContext());
        context.put("employeeId", request.employeeId().toString());
        context.put("lastWorkingDate", request.lastWorkingDate().toString());
        context.put("reason", request.reason());
        context.put("offboardingType", request.offboardingType());
        context.put("conductExitInterview", request.conductExitInterview());

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

        // Process pending requests
        processPendingRequests(employee);

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
        Page<EmployeeProcess> page = type != null
            ? processRepository.findByProcessTypeAndStatusIn(type, ACTIVE_PROCESS_STATUSES, pageable)
            : processRepository.findByStatusIn(ACTIVE_PROCESS_STATUSES, pageable);
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
            createTask(process, task.title(), task.description(), scheduled(process, task.dueDay(), baselineDays), assignee);
        }

        // Mandatory trainings, when the request names any (70% of the way in)
        if (request.requiredTrainingIds() != null && !request.requiredTrainingIds().isEmpty()) {
            createTask(
                process,
                "Complete Mandatory Trainings",
                "Complete all assigned mandatory training programs",
                scheduled(process, (int) Math.round(baselineDays * 0.7), baselineDays),
                employee
            );
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

    private void createOffboardingTasks(EmployeeProcess process, InitiateOffboardingRequest request) {
        LocalDate lastDate = request.lastWorkingDate();
        Employee employee = process.getEmployee();

        // Task 1: Knowledge transfer documentation
        createTask(
            process,
            "Knowledge Transfer Documentation",
            "Document all ongoing projects, processes, and key contacts",
            lastDate.minusDays(14),
            employee
        );

        // Task 2: Project handover
        createTask(
            process,
            "Project Handover",
            "Complete handover of all assigned projects to designated colleagues",
            lastDate.minusDays(7),
            employee
        );

        // Task 3: Return company assets
        createTask(process, "Return Company Assets", "Return laptop, ID card, keys, and other company property", lastDate, employee);

        // Task 4: Clear pending expenses
        createTask(
            process,
            "Submit Pending Expenses",
            "Submit all pending expense claims for reimbursement",
            lastDate.minusDays(10),
            employee
        );

        // Task 5: Benefits termination review
        createTask(process, "Benefits Termination Review", "Review benefits termination and COBRA options", lastDate.minusDays(7), null);

        // Task 6: IT access revocation
        createTask(process, "IT Access Revocation", "Revoke all system access and collect credentials", lastDate, null);

        // Task 7: Exit interview
        if (request.conductExitInterview()) {
            createTask(
                process,
                "Exit Interview",
                "Conduct exit interview to gather feedback",
                lastDate.minusDays(3),
                request.exitInterviewerId() != null ? employeeRepository.findById(request.exitInterviewerId()).orElse(null) : null
            );
        }

        // Task 8: Final paycheck processing
        createTask(process, "Final Paycheck Processing", "Process final paycheck including any settlements", lastDate, null);

        // Task 9: Send farewell communication
        createTask(
            process,
            "Farewell Communication",
            "Send farewell email to team and relevant stakeholders",
            lastDate.minusDays(1),
            employee
        );
    }

    private EmployeeProcessTask createTask(
        EmployeeProcess process,
        String title,
        String description,
        LocalDate dueDate,
        Employee assignedTo
    ) {
        EmployeeProcessTask task = new EmployeeProcessTask();
        task.setProcess(process);
        task.setTitle(title);
        task.setDescription(description);
        task.setDueDate(dueDate);
        task.setCompleted(false);
        task.setAssignedTo(assignedTo);

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

    private void processPendingRequests(Employee employee) {
        // Cancel pending leave requests
        leaveRequestRepository
            .findByEmployeeId(employee.getId(), Pageable.unpaged())
            .stream()
            .filter(lr -> lr.getStatus() == LeaveStatus.PENDING)
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
                .count()
        );
    }

    private OnboardingProcessResponse mapToOnboardingResponse(EmployeeProcess process) {
        Employee employee = process.getEmployee();

        WorkflowInstance workflow = findWorkflowForProcess(process);

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
                    task.getCompletionNotes()
                )
            )
            .collect(Collectors.toList());

        OnboardingProcessResponse.OnboardingProgress progress = new OnboardingProcessResponse.OnboardingProgress(
            true, // profileCreated: the employee record exists
            employee.getDepartment() != null,
            employeeBenefitRepository.existsByEmployeeId(employee.getId()),
            employeeTrainingRepository.existsByEmployeeId(employee.getId()),
            employeeDocumentRepository.existsByEmployeeId(employee.getId()),
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

        return new OffboardingProcessResponse(
            process.getId(),
            workflow != null ? workflow.getId() : null,
            employee.getId(),
            employee.getFirstName() + " " + employee.getLastName(),
            employee.getEmail(),
            workflow != null ? workflow.getStatus() : fallbackWorkflowStatus(process),
            process.getStatus(),
            workflow != null ? workflow.getCurrentState() : STATE_EXIT_TASKS,
            process.getDueDate(),
            process.getNotes(),
            process.getCompletionPercentage(),
            taskResponses,
            progress,
            settlement,
            process.getCreatedDate(),
            process.getLastModifiedDate()
        );
    }
}
