package com.humano.web.rest.hr.workflow;

import com.humano.dto.hr.workflow.requests.CancelGoalRequest;
import com.humano.dto.hr.workflow.requests.GoalCheckInRequest;
import com.humano.dto.hr.workflow.requests.GoalRequest;
import com.humano.dto.hr.workflow.responses.GoalCheckInResponse;
import com.humano.dto.hr.workflow.responses.GoalResponse;
import com.humano.repository.hr.projection.TeamMemberRow;
import com.humano.security.PermissionsConstants;
import com.humano.security.SecurityExpressions;
import com.humano.security.SecurityUtils;
import com.humano.security.annotation.RequireAuthenticated;
import com.humano.security.annotation.RequireFeature;
import com.humano.service.billing.entitlement.FeatureCodes;
import com.humano.service.hr.workflow.GoalService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for employees' goals.
 * <p>
 * Access follows the reporting line rather than a role: the signed-in employee works on their own
 * goals and their direct reports' (as their manager); HR acts on anyone's with
 * {@code MANAGE_PERFORMANCE_REVIEWS} and reads them with {@code VIEW_PERFORMANCE_REVIEWS}.
 * {@link GoalService} holds the rules.
 */
@RequireFeature(FeatureCodes.PERFORMANCE)
@RestController
@RequestMapping("/api/hr/goals")
public class GoalResource {

    private static final Logger LOG = LoggerFactory.getLogger(GoalResource.class);

    private final GoalService goalService;
    private final SecurityExpressions securityExpressions;

    public GoalResource(GoalService goalService, SecurityExpressions securityExpressions) {
        this.goalService = goalService;
        this.securityExpressions = securityExpressions;
    }

    /** {@code GET  /mine} : The signed-in employee's goals. */
    @GetMapping("/mine")
    @RequireAuthenticated
    public ResponseEntity<List<GoalResponse>> mine() {
        return ResponseEntity.ok(goalService.mine(actor()));
    }

    /** {@code GET  /team} : The goals of the signed-in employee's direct reports. */
    @GetMapping("/team")
    @RequireAuthenticated
    public ResponseEntity<List<GoalResponse>> team() {
        return ResponseEntity.ok(goalService.team(actor()));
    }

    /** {@code GET  /team-members} : The signed-in employee's direct reports, whose goals they set. */
    @GetMapping("/team-members")
    @RequireAuthenticated
    public ResponseEntity<List<TeamMemberRow>> teamMembers() {
        return ResponseEntity.ok(goalService.teamMembers(actor()));
    }

    /** {@code GET  /employees/:employeeId} : Someone's goals, for those who may read them. */
    @GetMapping("/employees/{employeeId}")
    @RequireAuthenticated
    public ResponseEntity<List<GoalResponse>> ofEmployee(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(goalService.ofEmployee(employeeId, actor()));
    }

    /** {@code POST  /} : Propose one's own goal, or set one for a direct report. */
    @PostMapping
    @RequireAuthenticated
    public ResponseEntity<GoalResponse> create(@Valid @RequestBody GoalRequest request) {
        LOG.debug("REST request to create a goal: {}", request.title());
        return ResponseEntity.ok(goalService.create(request, actor()));
    }

    /** {@code PUT  /:goalId} : Change a goal. */
    @PutMapping("/{goalId}")
    @RequireAuthenticated
    public ResponseEntity<GoalResponse> update(@PathVariable UUID goalId, @Valid @RequestBody GoalRequest request) {
        LOG.debug("REST request to update goal: {}", goalId);
        return ResponseEntity.ok(goalService.update(goalId, request, actor()));
    }

    /** {@code DELETE  /:goalId} : Delete a goal never agreed. */
    @DeleteMapping("/{goalId}")
    @RequireAuthenticated
    public ResponseEntity<Void> delete(@PathVariable UUID goalId) {
        LOG.debug("REST request to delete goal: {}", goalId);
        goalService.delete(goalId, actor());
        return ResponseEntity.noContent().build();
    }

    /** {@code POST  /:goalId/agree} : The manager agrees a proposed goal. */
    @PostMapping("/{goalId}/agree")
    @RequireAuthenticated
    public ResponseEntity<GoalResponse> agree(@PathVariable UUID goalId) {
        return ResponseEntity.ok(goalService.agree(goalId, actor()));
    }

    /** {@code POST  /:goalId/complete} : Mark an agreed goal done. */
    @PostMapping("/{goalId}/complete")
    @RequireAuthenticated
    public ResponseEntity<GoalResponse> complete(@PathVariable UUID goalId) {
        return ResponseEntity.ok(goalService.complete(goalId, actor()));
    }

    /** {@code POST  /:goalId/cancel} : The manager stops a goal, saying why. */
    @PostMapping("/{goalId}/cancel")
    @RequireAuthenticated
    public ResponseEntity<GoalResponse> cancel(@PathVariable UUID goalId, @Valid @RequestBody CancelGoalRequest request) {
        return ResponseEntity.ok(goalService.cancel(goalId, request, actor()));
    }

    /** {@code GET  /:goalId/check-ins} : A goal's progress updates, newest first. */
    @GetMapping("/{goalId}/check-ins")
    @RequireAuthenticated
    public ResponseEntity<List<GoalCheckInResponse>> checkIns(@PathVariable UUID goalId) {
        return ResponseEntity.ok(goalService.checkIns(goalId, actor()));
    }

    /** {@code POST  /:goalId/check-ins} : Record progress on an agreed goal. */
    @PostMapping("/{goalId}/check-ins")
    @RequireAuthenticated
    public ResponseEntity<GoalResponse> checkIn(@PathVariable UUID goalId, @Valid @RequestBody GoalCheckInRequest request) {
        return ResponseEntity.ok(goalService.checkIn(goalId, request, actor()));
    }

    private GoalService.Actor actor() {
        UUID id = SecurityUtils.getCurrentUserId().orElseThrow(() -> new AccessDeniedException("Not signed in as an employee"));
        return new GoalService.Actor(
            id,
            securityExpressions.hasPermission(PermissionsConstants.VIEW_PERFORMANCE_REVIEWS),
            securityExpressions.hasPermission(PermissionsConstants.MANAGE_PERFORMANCE_REVIEWS)
        );
    }
}
