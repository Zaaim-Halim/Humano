package com.humano.web.rest.hr.workflow;

import com.humano.domain.enumeration.hr.EmployeeProcessStatus;
import com.humano.domain.enumeration.hr.EmployeeProcessType;
import com.humano.dto.hr.workflow.requests.CompleteTaskRequest;
import com.humano.dto.hr.workflow.requests.InitiateOffboardingRequest;
import com.humano.dto.hr.workflow.requests.InitiateOnboardingRequest;
import com.humano.dto.hr.workflow.responses.EmployeeProcessSummary;
import com.humano.dto.hr.workflow.responses.OffboardingProcessResponse;
import com.humano.dto.hr.workflow.responses.OnboardingProcessResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.hr.workflow.EmployeeLifecycleWorkflowService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.PaginationUtil;

/**
 * REST controller for employee lifecycle workflows (onboarding + offboarding).
 *
 * <p>Onboarding is gated by {@code MANAGE_ONBOARDING} (writes) and
 * {@code VIEW_ONBOARDING_PROGRESS} / {@code MANAGE_ONBOARDING} (reads);
 * offboarding by {@code MANAGE_OFFBOARDING} (writes) and
 * {@code VIEW_OFFBOARDING} / {@code MANAGE_OFFBOARDING} (reads).
 */
@RestController
@RequestMapping("/api/hr/workflows")
public class EmployeeLifecycleResource {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeLifecycleResource.class);

    private static final String CAN_VIEW_ONBOARDING =
        "@securityExpressions.hasAnyPermission('" +
        PermissionsConstants.VIEW_ONBOARDING_PROGRESS +
        "', '" +
        PermissionsConstants.MANAGE_ONBOARDING +
        "')";

    private static final String CAN_VIEW_OFFBOARDING =
        "@securityExpressions.hasAnyPermission('" +
        PermissionsConstants.VIEW_OFFBOARDING +
        "', '" +
        PermissionsConstants.MANAGE_OFFBOARDING +
        "')";

    private final EmployeeLifecycleWorkflowService lifecycleService;

    public EmployeeLifecycleResource(EmployeeLifecycleWorkflowService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    // ==================== ONBOARDING ENDPOINTS ====================

    /**
     * {@code POST  /onboarding} : Initiate onboarding for an employee.
     */
    @PostMapping("/onboarding")
    @RequirePermission(PermissionsConstants.MANAGE_ONBOARDING)
    public ResponseEntity<OnboardingProcessResponse> initiateOnboarding(@Valid @RequestBody InitiateOnboardingRequest request) {
        LOG.debug("REST request to initiate onboarding for employee: {}", request.employeeId());
        OnboardingProcessResponse result = lifecycleService.initiateOnboarding(request);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET  /onboarding?status=&employeeId=} : Onboarding processes — active ones by
     * default; pass {@code status} (repeatable) for history, {@code employeeId} for one person.
     */
    @GetMapping("/onboarding")
    @PreAuthorize(CAN_VIEW_ONBOARDING)
    public ResponseEntity<Page<EmployeeProcessSummary>> getOnboardings(
        @RequestParam(required = false) List<EmployeeProcessStatus> status,
        @RequestParam(required = false) UUID employeeId,
        Pageable pageable
    ) {
        LOG.debug("REST request to get onboarding processes (status {}, employee {})", status, employeeId);
        return paged(lifecycleService.getProcesses(EmployeeProcessType.ONBOARDING, status, employeeId, pageable));
    }

    /**
     * {@code GET  /onboarding/:processId} : Get onboarding process status.
     */
    @GetMapping("/onboarding/{processId}")
    @PreAuthorize(CAN_VIEW_ONBOARDING)
    public ResponseEntity<OnboardingProcessResponse> getOnboardingStatus(@PathVariable UUID processId) {
        LOG.debug("REST request to get onboarding status: {}", processId);
        OnboardingProcessResponse result = lifecycleService.getOnboardingStatus(processId);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code POST  /onboarding/:processId/tasks/complete} : Complete an onboarding task.
     */
    @PostMapping("/onboarding/{processId}/tasks/complete")
    @RequirePermission(PermissionsConstants.MANAGE_ONBOARDING)
    public ResponseEntity<OnboardingProcessResponse> completeOnboardingTask(
        @PathVariable UUID processId,
        @Valid @RequestBody CompleteTaskRequest request
    ) {
        LOG.debug("REST request to complete onboarding task {} for process: {}", request.taskId(), processId);
        OnboardingProcessResponse result = lifecycleService.completeOnboardingTask(processId, request);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code POST  /onboarding/:processId/cancel} : Cancel an onboarding process.
     */
    @PostMapping("/onboarding/{processId}/cancel")
    @RequirePermission(PermissionsConstants.MANAGE_ONBOARDING)
    public ResponseEntity<Void> cancelOnboarding(@PathVariable UUID processId, @RequestParam String reason) {
        LOG.debug("REST request to cancel onboarding: {}", processId);
        lifecycleService.cancelOnboarding(processId, reason);
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code POST  /onboarding/:processId/escalate} : Escalate overdue onboarding tasks.
     */
    @PostMapping("/onboarding/{processId}/escalate")
    @RequirePermission(PermissionsConstants.MANAGE_ONBOARDING)
    public ResponseEntity<Void> escalateOnboarding(@PathVariable UUID processId) {
        LOG.debug("REST request to escalate overdue onboarding tasks: {}", processId);
        lifecycleService.escalateOverdueTasks(processId, EmployeeProcessType.ONBOARDING);
        return ResponseEntity.noContent().build();
    }

    // ==================== OFFBOARDING ENDPOINTS ====================

    /**
     * {@code POST  /offboarding} : Initiate offboarding for an employee.
     */
    @PostMapping("/offboarding")
    @RequirePermission(PermissionsConstants.MANAGE_OFFBOARDING)
    public ResponseEntity<OffboardingProcessResponse> initiateOffboarding(@Valid @RequestBody InitiateOffboardingRequest request) {
        LOG.debug("REST request to initiate offboarding for employee: {}", request.employeeId());
        OffboardingProcessResponse result = lifecycleService.initiateOffboarding(request);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET  /offboarding?status=&employeeId=} : Offboarding processes — active ones by
     * default; pass {@code status} (repeatable) for history, {@code employeeId} for one person.
     */
    @GetMapping("/offboarding")
    @PreAuthorize(CAN_VIEW_OFFBOARDING)
    public ResponseEntity<Page<EmployeeProcessSummary>> getOffboardings(
        @RequestParam(required = false) List<EmployeeProcessStatus> status,
        @RequestParam(required = false) UUID employeeId,
        Pageable pageable
    ) {
        LOG.debug("REST request to get offboarding processes (status {}, employee {})", status, employeeId);
        return paged(lifecycleService.getProcesses(EmployeeProcessType.OFFBOARDING, status, employeeId, pageable));
    }

    /**
     * {@code GET  /offboarding/:processId} : Get offboarding process status.
     */
    @GetMapping("/offboarding/{processId}")
    @PreAuthorize(CAN_VIEW_OFFBOARDING)
    public ResponseEntity<OffboardingProcessResponse> getOffboardingStatus(@PathVariable UUID processId) {
        LOG.debug("REST request to get offboarding status: {}", processId);
        OffboardingProcessResponse result = lifecycleService.getOffboardingStatus(processId);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code POST  /offboarding/:processId/tasks/complete} : Complete an offboarding task.
     */
    @PostMapping("/offboarding/{processId}/tasks/complete")
    @RequirePermission(PermissionsConstants.MANAGE_OFFBOARDING)
    public ResponseEntity<OffboardingProcessResponse> completeOffboardingTask(
        @PathVariable UUID processId,
        @Valid @RequestBody CompleteTaskRequest request
    ) {
        LOG.debug("REST request to complete offboarding task {} for process: {}", request.taskId(), processId);
        OffboardingProcessResponse result = lifecycleService.completeOffboardingTask(processId, request);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code POST  /offboarding/:processId/escalate} : Escalate overdue offboarding tasks.
     */
    /**
     * {@code POST  /offboarding/:processId/cancel?reason=} : Cancel an offboarding (status restored unless they've already left).
     */
    @PostMapping("/offboarding/{processId}/cancel")
    @RequirePermission(PermissionsConstants.MANAGE_OFFBOARDING)
    public ResponseEntity<Void> cancelOffboarding(@PathVariable UUID processId, @RequestParam String reason) {
        LOG.debug("REST request to cancel offboarding: {}", processId);
        lifecycleService.cancelOffboarding(processId, reason);
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code POST  /offboarding/:processId/escalate} : Escalate overdue offboarding tasks.
     */
    @PostMapping("/offboarding/{processId}/escalate")
    @RequirePermission(PermissionsConstants.MANAGE_OFFBOARDING)
    public ResponseEntity<Void> escalateOffboarding(@PathVariable UUID processId) {
        LOG.debug("REST request to escalate overdue offboarding tasks: {}", processId);
        lifecycleService.escalateOverdueTasks(processId, EmployeeProcessType.OFFBOARDING);
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code GET  /offboarding/settlement/:employeeId} : Preview an employee's final settlement.
     */
    @GetMapping("/offboarding/settlement/{employeeId}")
    @PreAuthorize(CAN_VIEW_OFFBOARDING)
    public ResponseEntity<OffboardingProcessResponse.FinalSettlementSummary> getFinalSettlement(@PathVariable UUID employeeId) {
        LOG.debug("REST request to calculate final settlement for employee: {}", employeeId);
        return ResponseEntity.ok(lifecycleService.calculateFinalSettlement(employeeId));
    }

    private static ResponseEntity<Page<EmployeeProcessSummary>> paged(Page<EmployeeProcessSummary> page) {
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page);
    }
}
