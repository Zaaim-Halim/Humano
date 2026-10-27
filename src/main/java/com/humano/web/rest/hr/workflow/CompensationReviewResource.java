package com.humano.web.rest.hr.workflow;

import com.humano.dto.hr.workflow.requests.CompensationProposalRequest;
import com.humano.dto.hr.workflow.requests.GenerateCompensationProposalsRequest;
import com.humano.dto.hr.workflow.requests.MeritGuidelinesRequest;
import com.humano.dto.hr.workflow.requests.ReturnCompensationProposalRequest;
import com.humano.dto.hr.workflow.responses.CompensationBulkResult;
import com.humano.dto.hr.workflow.responses.CompensationProposalResponse;
import com.humano.dto.hr.workflow.responses.CompensationReviewCycle;
import com.humano.dto.hr.workflow.responses.CompensationReviewResponse;
import com.humano.dto.hr.workflow.responses.MeritGuidelinesResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.SecurityExpressions;
import com.humano.security.SecurityUtils;
import com.humano.security.annotation.RequireFeature;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.billing.entitlement.EntitlementService;
import com.humano.service.billing.entitlement.FeatureCodes;
import com.humano.service.hr.workflow.CompensationProposalService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for the compensation review that follows a regular review cycle: the merit matrix
 * and the pay and promotion proposals. Everything needs {@code MANAGE_COMPENSATION}, since it shows
 * pay; applying a bonus also needs {@code MANAGE_BONUSES}. {@link CompensationProposalService} holds
 * the rules.
 */
@RequireFeature(FeatureCodes.PERFORMANCE)
@RequirePermission(PermissionsConstants.MANAGE_COMPENSATION)
@RestController
@RequestMapping("/api/hr/compensation-reviews")
public class CompensationReviewResource {

    private static final Logger LOG = LoggerFactory.getLogger(CompensationReviewResource.class);

    private final CompensationProposalService proposalService;
    private final SecurityExpressions securityExpressions;
    private final EntitlementService entitlementService;

    public CompensationReviewResource(
        CompensationProposalService proposalService,
        SecurityExpressions securityExpressions,
        EntitlementService entitlementService
    ) {
        this.proposalService = proposalService;
        this.securityExpressions = securityExpressions;
        this.entitlementService = entitlementService;
    }

    /** {@code GET  /guidelines} : The merit matrix. */
    @GetMapping("/guidelines")
    public ResponseEntity<MeritGuidelinesResponse> guidelines() {
        return ResponseEntity.ok(proposalService.guidelines());
    }

    /** {@code PUT  /guidelines} : Save the merit matrix. */
    @PutMapping("/guidelines")
    public ResponseEntity<MeritGuidelinesResponse> saveGuidelines(@Valid @RequestBody MeritGuidelinesRequest request) {
        LOG.debug("REST request to save the merit guidelines");
        return ResponseEntity.ok(proposalService.saveGuidelines(request));
    }

    /** {@code GET  /cycles} : The regular review cycles, latest first. */
    @GetMapping("/cycles")
    public ResponseEntity<List<CompensationReviewCycle>> cycles() {
        return ResponseEntity.ok(proposalService.cycles());
    }

    /** {@code GET  /cycles/:cycleId} : A cycle's proposals and totals. */
    @GetMapping("/cycles/{cycleId}")
    public ResponseEntity<CompensationReviewResponse> review(@PathVariable UUID cycleId) {
        return ResponseEntity.ok(proposalService.review(cycleId, actor()));
    }

    /** {@code POST  /cycles/:cycleId/proposals} : Draft a proposal for each eligible review. */
    @PostMapping("/cycles/{cycleId}/proposals")
    public ResponseEntity<CompensationBulkResult> generate(
        @PathVariable UUID cycleId,
        @Valid @RequestBody GenerateCompensationProposalsRequest request
    ) {
        LOG.debug("REST request to draft compensation proposals for cycle {}", cycleId);
        return ResponseEntity.ok(new CompensationBulkResult(proposalService.generate(cycleId, request, actor())));
    }

    /** {@code POST  /cycles/:cycleId/guidance} : Fill the drafts without a new salary with the merit matrix's midpoint. */
    @PostMapping("/cycles/{cycleId}/guidance")
    public ResponseEntity<CompensationBulkResult> useGuidance(@PathVariable UUID cycleId) {
        return ResponseEntity.ok(new CompensationBulkResult(proposalService.useGuidance(cycleId, actor())));
    }

    /** {@code POST  /cycles/:cycleId/submit-all} : Submit every draft one may. */
    @PostMapping("/cycles/{cycleId}/submit-all")
    public ResponseEntity<CompensationBulkResult> submitAll(@PathVariable UUID cycleId) {
        return ResponseEntity.ok(new CompensationBulkResult(proposalService.submitAll(cycleId, actor())));
    }

    /** {@code POST  /cycles/:cycleId/approve-all} : Approve every submitted proposal one may. */
    @PostMapping("/cycles/{cycleId}/approve-all")
    public ResponseEntity<CompensationBulkResult> approveAll(@PathVariable UUID cycleId) {
        return ResponseEntity.ok(new CompensationBulkResult(proposalService.approveAll(cycleId, actor())));
    }

    /** {@code PUT  /proposals/:proposalId} : Change a draft. */
    @PutMapping("/proposals/{proposalId}")
    public ResponseEntity<CompensationProposalResponse> update(
        @PathVariable UUID proposalId,
        @Valid @RequestBody CompensationProposalRequest request
    ) {
        return ResponseEntity.ok(proposalService.update(proposalId, request, actor()));
    }

    /** {@code DELETE  /proposals/:proposalId} : Delete a draft. */
    @DeleteMapping("/proposals/{proposalId}")
    public ResponseEntity<Void> delete(@PathVariable UUID proposalId) {
        proposalService.delete(proposalId, actor());
        return ResponseEntity.noContent().build();
    }

    /** {@code POST  /proposals/:proposalId/submit} : Send a draft for approval. */
    @PostMapping("/proposals/{proposalId}/submit")
    public ResponseEntity<CompensationProposalResponse> submit(@PathVariable UUID proposalId) {
        return ResponseEntity.ok(proposalService.submit(proposalId, actor()));
    }

    /** {@code POST  /proposals/:proposalId/approve} : Approve someone else's submission. */
    @PostMapping("/proposals/{proposalId}/approve")
    public ResponseEntity<CompensationProposalResponse> approve(@PathVariable UUID proposalId) {
        return ResponseEntity.ok(proposalService.approve(proposalId, actor()));
    }

    /** {@code POST  /proposals/:proposalId/return} : Send it back to draft with a comment. */
    @PostMapping("/proposals/{proposalId}/return")
    public ResponseEntity<CompensationProposalResponse> returnToDraft(
        @PathVariable UUID proposalId,
        @Valid @RequestBody ReturnCompensationProposalRequest request
    ) {
        return ResponseEntity.ok(proposalService.returnToDraft(proposalId, request, actor()));
    }

    /**
     * {@code POST  /proposals/:proposalId/apply} : Change the pay, position and bonus. It changes pay, so
     * it belongs to the compensation feature — and to performance, checked here since the method's
     * feature replaces the class's.
     */
    @PostMapping("/proposals/{proposalId}/apply")
    @RequireFeature(FeatureCodes.COMPENSATION)
    public ResponseEntity<CompensationProposalResponse> apply(@PathVariable UUID proposalId) {
        entitlementService.checkIncluded(FeatureCodes.PERFORMANCE);
        LOG.debug("REST request to apply compensation proposal {}", proposalId);
        return ResponseEntity.ok(proposalService.apply(proposalId, actor()));
    }

    private CompensationProposalService.Actor actor() {
        UUID id = SecurityUtils.getCurrentUserId().orElseThrow(() -> new AccessDeniedException("Not signed in as an employee"));
        return new CompensationProposalService.Actor(id, securityExpressions.hasPermission(PermissionsConstants.MANAGE_BONUSES));
    }
}
