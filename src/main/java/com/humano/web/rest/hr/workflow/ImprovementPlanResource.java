package com.humano.web.rest.hr.workflow;

import com.humano.dto.hr.workflow.requests.AcknowledgeImprovementPlanRequest;
import com.humano.dto.hr.workflow.requests.CloseImprovementPlanRequest;
import com.humano.dto.hr.workflow.requests.ExtendImprovementPlanRequest;
import com.humano.dto.hr.workflow.requests.ImprovementPlanCheckInRequest;
import com.humano.dto.hr.workflow.requests.ImprovementPlanRequest;
import com.humano.dto.hr.workflow.responses.ImprovementPlanResponse;
import com.humano.dto.hr.workflow.responses.ReviewImprovementPlanStatus;
import com.humano.security.PermissionsConstants;
import com.humano.security.SecurityExpressions;
import com.humano.security.SecurityUtils;
import com.humano.security.annotation.RequireAuthenticated;
import com.humano.security.annotation.RequireFeature;
import com.humano.service.billing.entitlement.FeatureCodes;
import com.humano.service.hr.workflow.ImprovementPlanService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for performance improvement plans.
 * <p>
 * Access is relational: the employee's direct manager or whoever runs the plan — or HR with
 * {@code MANAGE_PERFORMANCE_REVIEWS} — runs it, and the employee reads and acknowledges it once
 * shared. {@link ImprovementPlanService} holds the rules.
 */
@RequireFeature(FeatureCodes.PERFORMANCE)
@RestController
@RequestMapping("/api/hr/improvement-plans")
public class ImprovementPlanResource {

    private static final Logger LOG = LoggerFactory.getLogger(ImprovementPlanResource.class);

    private final ImprovementPlanService planService;
    private final SecurityExpressions securityExpressions;

    public ImprovementPlanResource(ImprovementPlanService planService, SecurityExpressions securityExpressions) {
        this.planService = planService;
        this.securityExpressions = securityExpressions;
    }

    /** {@code GET  /mine} : The signed-in employee's plans, once shared with them. */
    @GetMapping("/mine")
    @RequireAuthenticated
    public ResponseEntity<List<ImprovementPlanResponse>> mine() {
        return ResponseEntity.ok(planService.mine(actor()));
    }

    /** {@code GET  /} : The plans the signed-in user runs (every plan for HR). */
    @GetMapping
    @RequireAuthenticated
    public ResponseEntity<List<ImprovementPlanResponse>> managed() {
        return ResponseEntity.ok(planService.managed(actor()));
    }

    /** {@code GET  /:planId} : One plan with its objectives and check-ins. */
    @GetMapping("/{planId}")
    @RequireAuthenticated
    public ResponseEntity<ImprovementPlanResponse> get(@PathVariable UUID planId) {
        return ResponseEntity.ok(planService.get(planId, actor()));
    }

    /** {@code GET  /reviews/:reviewId} : Whether a plan can start from this review, and the latest one started from it. */
    @GetMapping("/reviews/{reviewId}")
    @RequireAuthenticated
    public ResponseEntity<ReviewImprovementPlanStatus> forReview(@PathVariable UUID reviewId) {
        return ResponseEntity.ok(planService.forReview(reviewId, actor()));
    }

    /** {@code POST  /reviews/:reviewId} : Draft a plan from a shared review rated 2 or lower. */
    @PostMapping("/reviews/{reviewId}")
    @RequireAuthenticated
    public ResponseEntity<ImprovementPlanResponse> start(@PathVariable UUID reviewId, @Valid @RequestBody ImprovementPlanRequest request) {
        LOG.debug("REST request to start an improvement plan from review {}", reviewId);
        return ResponseEntity.ok(planService.start(reviewId, request, actor()));
    }

    /** {@code PUT  /:planId} : Change a draft. */
    @PutMapping("/{planId}")
    @RequireAuthenticated
    public ResponseEntity<ImprovementPlanResponse> update(@PathVariable UUID planId, @Valid @RequestBody ImprovementPlanRequest request) {
        return ResponseEntity.ok(planService.update(planId, request, actor()));
    }

    /** {@code DELETE  /:planId} : Delete a draft. */
    @DeleteMapping("/{planId}")
    @RequireAuthenticated
    public ResponseEntity<Void> delete(@PathVariable UUID planId) {
        planService.delete(planId, actor());
        return ResponseEntity.noContent().build();
    }

    /** {@code POST  /:planId/activate} : Share the draft with the employee. */
    @PostMapping("/{planId}/activate")
    @RequireAuthenticated
    public ResponseEntity<ImprovementPlanResponse> activate(@PathVariable UUID planId) {
        return ResponseEntity.ok(planService.activate(planId, actor()));
    }

    /** {@code POST  /:planId/acknowledgement} : The employee confirms they received the plan. */
    @PostMapping("/{planId}/acknowledgement")
    @RequireAuthenticated
    public ResponseEntity<ImprovementPlanResponse> acknowledge(
        @PathVariable UUID planId,
        @Valid @RequestBody(required = false) AcknowledgeImprovementPlanRequest request
    ) {
        return ResponseEntity.ok(planService.acknowledge(planId, request, actor()));
    }

    /** {@code POST  /:planId/check-ins} : Record a progress review. */
    @PostMapping("/{planId}/check-ins")
    @RequireAuthenticated
    public ResponseEntity<ImprovementPlanResponse> checkIn(
        @PathVariable UUID planId,
        @Valid @RequestBody ImprovementPlanCheckInRequest request
    ) {
        return ResponseEntity.ok(planService.checkIn(planId, request, actor()));
    }

    /** {@code POST  /:planId/extension} : Extend the plan once. */
    @PostMapping("/{planId}/extension")
    @RequireAuthenticated
    public ResponseEntity<ImprovementPlanResponse> extend(
        @PathVariable UUID planId,
        @Valid @RequestBody ExtendImprovementPlanRequest request
    ) {
        return ResponseEntity.ok(planService.extend(planId, request, actor()));
    }

    /** {@code POST  /:planId/close} : End the plan with its outcome. */
    @PostMapping("/{planId}/close")
    @RequireAuthenticated
    public ResponseEntity<ImprovementPlanResponse> close(
        @PathVariable UUID planId,
        @Valid @RequestBody CloseImprovementPlanRequest request
    ) {
        return ResponseEntity.ok(planService.close(planId, request, actor()));
    }

    private ImprovementPlanService.Actor actor() {
        UUID id = SecurityUtils.getCurrentUserId().orElseThrow(() -> new AccessDeniedException("Not signed in as an employee"));
        return new ImprovementPlanService.Actor(id, securityExpressions.hasPermission(PermissionsConstants.MANAGE_PERFORMANCE_REVIEWS));
    }
}
