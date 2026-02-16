package com.humano.web.rest.hr.workflow;

import com.humano.dto.hr.workflow.requests.ApprovalDecisionRequest;
import com.humano.dto.hr.workflow.requests.SubmitApprovalRequest;
import com.humano.dto.hr.workflow.responses.ApprovalWorkflowResponse;
import com.humano.dto.hr.workflow.responses.PendingApprovalSummary;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.hr.workflow.ApprovalWorkflowOrchestratorService;
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
 * REST controller for managing approval workflows.
 */
@RestController
@RequestMapping("/api/hr/workflows/approvals")
public class ApprovalWorkflowResource {

    private static final Logger LOG = LoggerFactory.getLogger(ApprovalWorkflowResource.class);

    private final ApprovalWorkflowOrchestratorService approvalService;

    public ApprovalWorkflowResource(ApprovalWorkflowOrchestratorService approvalService) {
        this.approvalService = approvalService;
    }

    /**
     * {@code POST  /} : Submit a request for approval.
     */
    @PostMapping
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
    public ResponseEntity<ApprovalWorkflowResponse> submitForApproval(@Valid @RequestBody SubmitApprovalRequest request) {
        LOG.debug("REST request to submit for approval: {}", request);
        ApprovalWorkflowResponse result = approvalService.submitForApproval(request);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET  /:approvalId} : Get approval status.
     */
    @GetMapping("/{approvalId}")
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
    public ResponseEntity<ApprovalWorkflowResponse> getApprovalStatus(@PathVariable UUID approvalId) {
        LOG.debug("REST request to get approval status: {}", approvalId);
        ApprovalWorkflowResponse result = approvalService.getApprovalStatus(approvalId);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code POST  /:approvalId/decide} : Process an approval decision.
     */
    @PostMapping("/{approvalId}/decide")
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
    public ResponseEntity<ApprovalWorkflowResponse> processDecision(
        @PathVariable UUID approvalId,
        @Valid @RequestBody ApprovalDecisionRequest decision
    ) {
        LOG.debug("REST request to process approval decision for {}: {}", approvalId, decision);
        ApprovalWorkflowResponse result = approvalService.processApprovalDecision(approvalId, decision);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code POST  /:approvalId/withdraw} : Withdraw an approval request.
     */
    @PostMapping("/{approvalId}/withdraw")
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
    public ResponseEntity<Void> withdrawApproval(@PathVariable UUID approvalId, @RequestParam String reason) {
        LOG.debug("REST request to withdraw approval: {}", approvalId);
        approvalService.withdrawApprovalRequest(approvalId, reason);
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code POST  /:approvalId/escalate} : Escalate an approval to the next level.
     */
    @PostMapping("/{approvalId}/escalate")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<Void> escalateApproval(@PathVariable UUID approvalId) {
        LOG.debug("REST request to escalate approval: {}", approvalId);
        approvalService.escalateToNextApprover(approvalId);
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code GET  /pending/:approverId} : Get pending approvals for an approver.
     */
    @GetMapping("/pending/{approverId}")
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
    public ResponseEntity<List<PendingApprovalSummary>> getPendingApprovals(@PathVariable UUID approverId) {
        LOG.debug("REST request to get pending approvals for: {}", approverId);
        List<PendingApprovalSummary> result = approvalService.getPendingApprovalsForApprover(approverId);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET  /pending/:approverId/paged} : Get pending approvals for an approver with pagination.
     */
    @GetMapping("/pending/{approverId}/paged")
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
    public ResponseEntity<Page<PendingApprovalSummary>> getPendingApprovalsPaged(@PathVariable UUID approverId, Pageable pageable) {
        LOG.debug("REST request to get pending approvals (paged) for: {}", approverId);
        Page<PendingApprovalSummary> page = approvalService.getPendingApprovalsForApprover(approverId, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /pending/:approverId/count} : Count pending approvals for an approver.
     */
    @GetMapping("/pending/{approverId}/count")
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
    public ResponseEntity<Long> countPendingApprovals(@PathVariable UUID approverId) {
        LOG.debug("REST request to count pending approvals for: {}", approverId);
        long count = approvalService.countPendingApprovals(approverId);
        return ResponseEntity.ok(count);
    }

    /**
     * {@code GET  /requestor/:requestorId} : Get approvals by requestor.
     */
    @GetMapping("/requestor/{requestorId}")
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
    public ResponseEntity<Page<ApprovalWorkflowResponse>> getApprovalsByRequestor(@PathVariable UUID requestorId, Pageable pageable) {
        LOG.debug("REST request to get approvals by requestor: {}", requestorId);
        Page<ApprovalWorkflowResponse> page = approvalService.getApprovalsByRequestor(requestorId, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code POST  /bulk-approve} : Bulk approve multiple requests.
     */
    @PostMapping("/bulk-approve")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<List<ApprovalWorkflowResponse>> bulkApprove(
        @RequestBody List<UUID> approvalRequestIds,
        @RequestParam(required = false) String comments
    ) {
        LOG.debug("REST request to bulk approve {} requests", approvalRequestIds.size());
        List<ApprovalWorkflowResponse> results = approvalService.bulkApprove(approvalRequestIds, comments);
        return ResponseEntity.ok(results);
    }

    // ==================== SPECIFIC APPROVAL SHORTCUTS ====================

    /**
     * {@code POST  /leave/:leaveRequestId} : Submit a leave request for approval.
     */
    @PostMapping("/leave/{leaveRequestId}")
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
    public ResponseEntity<ApprovalWorkflowResponse> submitLeaveRequest(@PathVariable UUID leaveRequestId) {
        LOG.debug("REST request to submit leave request for approval: {}", leaveRequestId);
        ApprovalWorkflowResponse result = approvalService.submitLeaveRequest(leaveRequestId);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code POST  /expense/:expenseClaimId} : Submit an expense claim for approval.
     */
    @PostMapping("/expense/{expenseClaimId}")
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
    public ResponseEntity<ApprovalWorkflowResponse> submitExpenseClaim(@PathVariable UUID expenseClaimId) {
        LOG.debug("REST request to submit expense claim for approval: {}", expenseClaimId);
        ApprovalWorkflowResponse result = approvalService.submitExpenseClaim(expenseClaimId);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code POST  /overtime/:overtimeRecordId} : Submit an overtime request for approval.
     */
    @PostMapping("/overtime/{overtimeRecordId}")
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
    public ResponseEntity<ApprovalWorkflowResponse> submitOvertimeRequest(@PathVariable UUID overtimeRecordId) {
        LOG.debug("REST request to submit overtime request for approval: {}", overtimeRecordId);
        ApprovalWorkflowResponse result = approvalService.submitOvertimeRequest(overtimeRecordId);
        return ResponseEntity.ok(result);
    }
}
