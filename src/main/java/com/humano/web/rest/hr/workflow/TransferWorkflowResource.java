package com.humano.web.rest.hr.workflow;

import com.humano.domain.hr.EmployeePositionHistory;
import com.humano.dto.hr.workflow.requests.ApprovalDecisionRequest;
import com.humano.dto.hr.workflow.requests.InitiateTransferRequest;
import com.humano.dto.hr.workflow.responses.TransferWorkflowResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.hr.workflow.TransferWorkflowService;
import jakarta.validation.Valid;
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
 * REST controller for managing employee transfer workflows.
 */
@RestController
@RequestMapping("/api/hr/workflows/transfers")
public class TransferWorkflowResource {

    private static final Logger LOG = LoggerFactory.getLogger(TransferWorkflowResource.class);

    private final TransferWorkflowService transferService;

    public TransferWorkflowResource(TransferWorkflowService transferService) {
        this.transferService = transferService;
    }

    /**
     * {@code POST  /} : Initiate a transfer request.
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<TransferWorkflowResponse> initiateTransfer(@Valid @RequestBody InitiateTransferRequest request) {
        LOG.debug("REST request to initiate transfer for employee: {}", request.employeeId());
        TransferWorkflowResponse result = transferService.initiateTransfer(request);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET  /:workflowId} : Get transfer workflow status.
     */
    @GetMapping("/{workflowId}")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "', '" +
        AuthoritiesConstants.EMPLOYEE +
        "')"
    )
    public ResponseEntity<TransferWorkflowResponse> getTransferStatus(@PathVariable UUID workflowId) {
        LOG.debug("REST request to get transfer status: {}", workflowId);
        TransferWorkflowResponse result = transferService.getTransferStatus(workflowId);
        return ResponseEntity.ok(result);
    }

    // ==================== APPROVAL ENDPOINTS ====================

    /**
     * {@code POST  /:workflowId/approve/current-manager} : Process current manager approval.
     */
    @PostMapping("/{workflowId}/approve/current-manager")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.EMPLOYEE +
        "')"
    )
    public ResponseEntity<TransferWorkflowResponse> processCurrentManagerApproval(
        @PathVariable UUID workflowId,
        @Valid @RequestBody ApprovalDecisionRequest decision
    ) {
        LOG.debug("REST request to process current manager approval for transfer: {}", workflowId);
        TransferWorkflowResponse result = transferService.processCurrentManagerApproval(workflowId, decision);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code POST  /:workflowId/approve/new-manager} : Process new manager approval.
     */
    @PostMapping("/{workflowId}/approve/new-manager")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.EMPLOYEE +
        "')"
    )
    public ResponseEntity<TransferWorkflowResponse> processNewManagerApproval(
        @PathVariable UUID workflowId,
        @Valid @RequestBody ApprovalDecisionRequest decision
    ) {
        LOG.debug("REST request to process new manager approval for transfer: {}", workflowId);
        TransferWorkflowResponse result = transferService.processNewManagerApproval(workflowId, decision);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code POST  /:workflowId/approve/hr} : Process HR approval.
     */
    @PostMapping("/{workflowId}/approve/hr")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<TransferWorkflowResponse> processHRApproval(
        @PathVariable UUID workflowId,
        @Valid @RequestBody ApprovalDecisionRequest decision
    ) {
        LOG.debug("REST request to process HR approval for transfer: {}", workflowId);
        TransferWorkflowResponse result = transferService.processHRApproval(workflowId, decision);
        return ResponseEntity.ok(result);
    }

    // ==================== EXECUTION ENDPOINTS ====================

    /**
     * {@code POST  /:workflowId/execute} : Execute an approved transfer.
     */
    @PostMapping("/{workflowId}/execute")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<TransferWorkflowResponse> executeTransfer(@PathVariable UUID workflowId) {
        LOG.debug("REST request to execute transfer: {}", workflowId);
        TransferWorkflowResponse result = transferService.executeTransfer(workflowId);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code POST  /:workflowId/cancel} : Cancel a transfer request.
     */
    @PostMapping("/{workflowId}/cancel")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<Void> cancelTransfer(@PathVariable UUID workflowId, @RequestParam String reason) {
        LOG.debug("REST request to cancel transfer: {}", workflowId);
        transferService.cancelTransfer(workflowId, reason);
        return ResponseEntity.noContent().build();
    }

    // ==================== HISTORY ENDPOINT ====================

    /**
     * {@code GET  /history/:employeeId} : Get employee's position history.
     */
    @GetMapping("/history/{employeeId}")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "', '" +
        AuthoritiesConstants.EMPLOYEE +
        "')"
    )
    public ResponseEntity<Page<EmployeePositionHistory>> getEmployeePositionHistory(@PathVariable UUID employeeId, Pageable pageable) {
        LOG.debug("REST request to get position history for employee: {}", employeeId);
        Page<EmployeePositionHistory> page = transferService.getEmployeePositionHistory(employeeId, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page);
    }
}
