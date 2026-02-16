package com.humano.service.hr.workflow;

import com.humano.domain.enumeration.hr.*;
import com.humano.domain.hr.*;
import com.humano.dto.hr.workflow.requests.CompleteTaskRequest;
import com.humano.dto.hr.workflow.requests.InitiateOffboardingRequest;
import com.humano.dto.hr.workflow.requests.InitiateOnboardingRequest;
import com.humano.dto.hr.workflow.responses.OffboardingProcessResponse;
import com.humano.dto.hr.workflow.responses.OnboardingProcessResponse;
import com.humano.repository.hr.*;
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
 * Orchestrator service for managing employee lifecycle workflows.
 * Handles complete onboarding and offboarding processes.
 */
@Service
@Transactional
public class EmployeeLifecycleWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeLifecycleWorkflowService.class);

    // Onboarding states
    public static final String STATE_INITIATED = "INITIATED";
    public static final String STATE_PROFILE_SETUP = "PROFILE_SETUP";
    public static final String STATE_DEPARTMENT_ASSIGNMENT = "DEPARTMENT_ASSIGNMENT";
    public static final String STATE_BENEFITS_SETUP = "BENEFITS_SETUP";
    public static final String STATE_TRAINING_ASSIGNMENT = "TRAINING_ASSIGNMENT";
    public static final String STATE_TASKS_IN_PROGRESS = "TASKS_IN_PROGRESS";
    public static final String STATE_COMPLETED = "COMPLETED";

    // Offboarding states
    public static final String STATE_OFFBOARDING_INITIATED = "OFFBOARDING_INITIATED";
    public static final String STATE_PENDING_REQUESTS_PROCESSING = "PENDING_REQUESTS_PROCESSING";
    public static final String STATE_SETTLEMENT_CALCULATION = "SETTLEMENT_CALCULATION";
    public static final String STATE_BENEFITS_TERMINATION = "BENEFITS_TERMINATION";
    public static final String STATE_EXIT_TASKS = "EXIT_TASKS";
    public static final String STATE_EXIT_INTERVIEW = "EXIT_INTERVIEW";
    public static final String STATE_FINAL_PROCESSING = "FINAL_PROCESSING";

    private final WorkflowStateManager workflowStateManager;
    private final NotificationOrchestrationService notificationService;
    private final DeadlineMonitorService deadlineMonitorService;
    private final EmployeeRepository employeeRepository;
    private final EmployeeProcessRepository processRepository;
    private final EmployeeProcessTaskRepository taskRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final BenefitRepository benefitRepository;
    private final HealthInsuranceRepository healthInsuranceRepository;
    private final EmployeeTrainingRepository employeeTrainingRepository;
    private final TrainingRepository trainingRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ExpenseClaimRepository expenseClaimRepository;
    private final OvertimeRecordRepository overtimeRecordRepository;

    public EmployeeLifecycleWorkflowService(
        WorkflowStateManager workflowStateManager,
        NotificationOrchestrationService notificationService,
        DeadlineMonitorService deadlineMonitorService,
        EmployeeRepository employeeRepository,
        EmployeeProcessRepository processRepository,
        EmployeeProcessTaskRepository taskRepository,
        DepartmentRepository departmentRepository,
        PositionRepository positionRepository,
        BenefitRepository benefitRepository,
        HealthInsuranceRepository healthInsuranceRepository,
        EmployeeTrainingRepository employeeTrainingRepository,
        TrainingRepository trainingRepository,
        LeaveRequestRepository leaveRequestRepository,
        ExpenseClaimRepository expenseClaimRepository,
        OvertimeRecordRepository overtimeRecordRepository
    ) {
        this.workflowStateManager = workflowStateManager;
        this.notificationService = notificationService;
        this.deadlineMonitorService = deadlineMonitorService;
        this.employeeRepository = employeeRepository;
        this.processRepository = processRepository;
        this.taskRepository = taskRepository;
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.benefitRepository = benefitRepository;
        this.healthInsuranceRepository = healthInsuranceRepository;
        this.employeeTrainingRepository = employeeTrainingRepository;
        this.trainingRepository = trainingRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.expenseClaimRepository = expenseClaimRepository;
        this.overtimeRecordRepository = overtimeRecordRepository;
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

        // Create workflow instance
        Map<String, Object> context = new HashMap<>();
        context.put("employeeId", request.employeeId().toString());
        context.put("startDate", request.startDate() != null ? request.startDate().toString() : LocalDate.now().toString());
        if (request.departmentId() != null) context.put("departmentId", request.departmentId().toString());
        if (request.positionId() != null) context.put("positionId", request.positionId().toString());
        if (request.managerId() != null) context.put("managerId", request.managerId().toString());
        if (request.additionalContext() != null) context.putAll(request.additionalContext());

        WorkflowInstance workflow = workflowStateManager.createWorkflow(
            WorkflowType.ONBOARDING,
            request.employeeId(),
            "Employee",
            context,
            null
        );

        // Create employee process
        EmployeeProcess process = createOnboardingProcess(employee, request, workflow);

        // Create onboarding tasks
        createOnboardingTasks(process, request);

        // Start the workflow
        workflowStateManager.startWorkflow(workflow.getId());
        workflowStateManager.transitionState(workflow.getId(), STATE_PROFILE_SETUP, "Starting onboarding process");

        // Set due date (default 30 days from start)
        LocalDate startDate = request.startDate() != null ? request.startDate() : LocalDate.now();
        Instant dueDate = startDate.plusDays(30).atStartOfDay(ZoneId.systemDefault()).toInstant();
        workflowStateManager.updateDueDate(workflow.getId(), dueDate);

        // Register deadline
        deadlineMonitorService.registerDeadline(
            workflow.getId(),
            "ONBOARDING_COMPLETION",
            "Complete onboarding for " + employee.getFirstName() + " " + employee.getLastName(),
            dueDate,
            72, // 3 days warning
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
    @Transactional(readOnly = true)
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

        EmployeeProcessTask task = taskRepository
            .findById(request.taskId())
            .orElseThrow(() -> EntityNotFoundException.create("EmployeeProcessTask", request.taskId()));

        if (!task.getProcess().getId().equals(processId)) {
            throw new BadRequestAlertException("Task does not belong to this process", "task", "invalidtask");
        }

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

        process.setStatus(EmployeeProcessStatus.CANCELLED);
        process.setNotes(reason);
        processRepository.save(process);

        // Cancel workflow if exists
        List<WorkflowInstance> workflows = workflowStateManager.findActiveWorkflowsByEntityId(process.getEmployee().getId());
        for (WorkflowInstance workflow : workflows) {
            if (workflow.getWorkflowType() == WorkflowType.ONBOARDING) {
                workflowStateManager.cancelWorkflow(workflow.getId(), reason);
            }
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

        // Create workflow context
        Map<String, Object> context = new HashMap<>();
        context.put("employeeId", request.employeeId().toString());
        context.put("lastWorkingDate", request.lastWorkingDate().toString());
        context.put("reason", request.reason());
        context.put("offboardingType", request.offboardingType());
        context.put("conductExitInterview", request.conductExitInterview());
        if (request.additionalContext() != null) context.putAll(request.additionalContext());

        WorkflowInstance workflow = workflowStateManager.createWorkflow(
            WorkflowType.OFFBOARDING,
            request.employeeId(),
            "Employee",
            context,
            null
        );

        // Create employee process
        EmployeeProcess process = createOffboardingProcess(employee, request, workflow);

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
    @Transactional(readOnly = true)
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

        EmployeeProcessTask task = taskRepository
            .findById(request.taskId())
            .orElseThrow(() -> EntityNotFoundException.create("EmployeeProcessTask", request.taskId()));

        if (!task.getProcess().getId().equals(processId)) {
            throw new BadRequestAlertException("Task does not belong to this process", "task", "invalidtask");
        }

        task.complete(request.completionNotes());
        taskRepository.save(task);

        updateProcessProgress(process);

        log.info("Completed offboarding task {} for process {}", request.taskId(), processId);

        return getOffboardingStatus(processId);
    }

    /**
     * Calculate final settlement for an employee.
     */
    @Transactional(readOnly = true)
    public OffboardingProcessResponse.FinalSettlementSummary calculateFinalSettlement(UUID employeeId) {
        log.debug("Calculating final settlement for employee {}", employeeId);

        Employee employee = employeeRepository
            .findById(employeeId)
            .orElseThrow(() -> EntityNotFoundException.create("Employee", employeeId));

        // Calculate remaining leave days (simplified - would need leave balance tracking)
        int remainingLeaveDays = 15; // Placeholder - should come from leave balance

        // Calculate leave encashment
        BigDecimal leaveEncashment = BigDecimal.valueOf(remainingLeaveDays * 100); // Placeholder calculation

        // Calculate pending expense reimbursements
        BigDecimal pendingExpenses = expenseClaimRepository
            .findByEmployeeId(employeeId, Pageable.unpaged())
            .stream()
            .filter(e -> e.getStatus() == ExpenseClaimStatus.APPROVED)
            .map(ExpenseClaim::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate pending overtime pay
        BigDecimal pendingOvertime = overtimeRecordRepository
            .findByEmployeeId(employeeId, Pageable.unpaged())
            .stream()
            .filter(o -> o.getApprovalStatus() == OvertimeApprovalStatus.APPROVED)
            .map(o -> o.getHours().multiply(BigDecimal.valueOf(25))) // Placeholder rate
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSettlement = leaveEncashment.add(pendingExpenses).add(pendingOvertime);

        return new OffboardingProcessResponse.FinalSettlementSummary(
            remainingLeaveDays,
            leaveEncashment,
            pendingExpenses,
            pendingOvertime,
            totalSettlement
        );
    }

    // ==================== COMMON ====================

    /**
     * Get all active processes with pagination.
     */
    @Transactional(readOnly = true)
    public Page<EmployeeProcess> getActiveProcesses(EmployeeProcessType type, Pageable pageable) {
        if (type != null) {
            return processRepository.findByProcessTypeAndStatusNot(type, EmployeeProcessStatus.COMPLETED, pageable);
        }
        return processRepository.findByStatusNot(EmployeeProcessStatus.COMPLETED, pageable);
    }

    /**
     * Escalate overdue tasks.
     */
    public void escalateOverdueTasks(UUID processId) {
        log.info("Escalating overdue tasks for process {}", processId);

        EmployeeProcess process = processRepository
            .findById(processId)
            .orElseThrow(() -> EntityNotFoundException.create("EmployeeProcess", processId));

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

    private EmployeeProcess createOnboardingProcess(Employee employee, InitiateOnboardingRequest request, WorkflowInstance workflow) {
        EmployeeProcess process = new EmployeeProcess();
        process.setEmployee(employee);
        process.setProcessType(EmployeeProcessType.ONBOARDING);
        process.setStatus(EmployeeProcessStatus.IN_PROGRESS);
        process.setStartDate(request.startDate() != null ? request.startDate() : LocalDate.now());
        process.setDueDate(process.getStartDate().plusDays(30));
        process.setNotes(request.notes());

        return processRepository.save(process);
    }

    private void createOnboardingTasks(EmployeeProcess process, InitiateOnboardingRequest request) {
        LocalDate startDate = process.getStartDate();
        Employee employee = process.getEmployee();

        // Task 1: Complete profile information
        createTask(
            process,
            "Complete Profile Information",
            "Update personal details, emergency contacts, and upload profile photo",
            startDate.plusDays(3),
            employee
        );

        // Task 2: Department orientation
        createTask(
            process,
            "Department Orientation",
            "Meet with team members and understand department structure",
            startDate.plusDays(5),
            employee
        );

        // Task 3: IT setup
        createTask(
            process,
            "IT Systems Setup",
            "Get system access, email setup, and necessary software installations",
            startDate.plusDays(2),
            null
        );

        // Task 4: Benefits enrollment
        createTask(process, "Benefits Enrollment", "Review and enroll in company benefits programs", startDate.plusDays(14), employee);

        // Task 5: Policy acknowledgment
        createTask(process, "Policy Acknowledgment", "Read and acknowledge company policies and handbook", startDate.plusDays(7), employee);

        // Task 6: Mandatory trainings
        if (request.requiredTrainingIds() != null && !request.requiredTrainingIds().isEmpty()) {
            createTask(
                process,
                "Complete Mandatory Trainings",
                "Complete all assigned mandatory training programs",
                startDate.plusDays(21),
                employee
            );
        }

        // Task 7: First week check-in with manager
        createTask(
            process,
            "First Week Check-in",
            "Schedule and complete first week check-in meeting with manager",
            startDate.plusDays(7),
            employee.getManager()
        );

        // Task 8: 30-day review meeting
        createTask(
            process,
            "30-Day Review Meeting",
            "Schedule and complete 30-day onboarding review",
            startDate.plusDays(30),
            employee.getManager()
        );
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

        return taskRepository.save(task);
    }

    private void updateProcessProgress(EmployeeProcess process) {
        Set<EmployeeProcessTask> tasks = process.getTasks();
        long completedCount = tasks.stream().filter(EmployeeProcessTask::getCompleted).count();

        int completionPercentage = tasks.isEmpty() ? 0 : (int) ((completedCount * 100) / tasks.size());

        // Update workflow state based on progress
        List<WorkflowInstance> workflows = workflowStateManager.findActiveWorkflowsByEntityId(process.getEmployee().getId());
        for (WorkflowInstance workflow : workflows) {
            if (
                (workflow.getWorkflowType() == WorkflowType.ONBOARDING && process.getProcessType() == EmployeeProcessType.ONBOARDING) ||
                (workflow.getWorkflowType() == WorkflowType.OFFBOARDING && process.getProcessType() == EmployeeProcessType.OFFBOARDING)
            ) {
                workflowStateManager.updateContext(workflow.getId(), "completionPercentage", completionPercentage);

                // Complete workflow if all tasks are done
                if (completedCount == tasks.size()) {
                    process.setStatus(EmployeeProcessStatus.COMPLETED);
                    process.setCompletionDate(LocalDate.now());
                    processRepository.save(process);

                    workflowStateManager.completeWorkflow(workflow.getId(), "All tasks completed");

                    notificationService.notifyWorkflowCompleted(
                        process.getEmployee().getId(),
                        process.getProcessType() == EmployeeProcessType.ONBOARDING ? "Onboarding Complete" : "Offboarding Complete",
                        "Your " + process.getProcessType().name().toLowerCase() + " process has been completed.",
                        process.getId(),
                        process.getProcessType().name()
                    );
                }
            }
        }
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

    private OnboardingProcessResponse mapToOnboardingResponse(EmployeeProcess process) {
        Employee employee = process.getEmployee();

        List<WorkflowInstance> workflows = workflowStateManager.findActiveWorkflowsByEntityId(employee.getId());
        WorkflowInstance workflow = workflows.stream().filter(w -> w.getWorkflowType() == WorkflowType.ONBOARDING).findFirst().orElse(null);

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
            true, // profileCreated
            employee.getDepartment() != null,
            false, // benefitsSetup - would need to check benefit enrollments
            !employeeTrainingRepository.findByEmployeeId(employee.getId(), Pageable.unpaged()).isEmpty(),
            false, // documentsUploaded
            true, // systemAccessGranted
            process.getTasks().size(),
            (int) process.getTasks().stream().filter(EmployeeProcessTask::getCompleted).count()
        );

        return new OnboardingProcessResponse(
            process.getId(),
            workflow != null ? workflow.getId() : null,
            employee.getId(),
            employee.getFirstName() + " " + employee.getLastName(),
            employee.getEmail(),
            workflow != null ? workflow.getStatus() : WorkflowStatus.IN_PROGRESS,
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

        List<WorkflowInstance> workflows = workflowStateManager.findActiveWorkflowsByEntityId(employee.getId());
        WorkflowInstance workflow = workflows
            .stream()
            .filter(w -> w.getWorkflowType() == WorkflowType.OFFBOARDING)
            .findFirst()
            .orElse(null);

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
            tasks.stream().anyMatch(t -> t.getTitle().contains("Settlement") && t.getCompleted()),
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
            workflow != null ? workflow.getStatus() : WorkflowStatus.IN_PROGRESS,
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
