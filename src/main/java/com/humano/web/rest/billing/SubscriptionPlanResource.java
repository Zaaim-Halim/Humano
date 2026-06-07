package com.humano.web.rest.billing;

import com.humano.domain.enumeration.billing.SubscriptionType;
import com.humano.dto.billing.requests.CreateSubscriptionPlanRequest;
import com.humano.dto.billing.requests.UpdateSubscriptionPlanRequest;
import com.humano.dto.billing.responses.SubscriptionPlanResponse;
import com.humano.security.annotation.RequireAdmin;
import com.humano.security.annotation.RequireAuthenticated;
import com.humano.service.billing.SubscriptionPlanService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Subscription-plan catalog. Plans are platform-level config; reads are open to
 * any authenticated user (so tenants can render upgrade UIs), mutations require
 * {@code ROLE_ADMIN}.
 */
@RestController
@RequestMapping("/api/billing/plans")
@RequireAuthenticated
public class SubscriptionPlanResource {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionPlanResource.class);

    private final SubscriptionPlanService planService;

    public SubscriptionPlanResource(SubscriptionPlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public ResponseEntity<Page<SubscriptionPlanResponse>> list(Pageable pageable) {
        return ResponseEntity.ok(planService.getAllSubscriptionPlans(pageable));
    }

    @GetMapping("/active")
    public ResponseEntity<List<SubscriptionPlanResponse>> listActive() {
        return ResponseEntity.ok(planService.getActiveSubscriptionPlans());
    }

    @GetMapping("/by-type/{type}")
    public ResponseEntity<List<SubscriptionPlanResponse>> listByType(@PathVariable SubscriptionType type) {
        return ResponseEntity.ok(planService.getSubscriptionPlansByType(type));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubscriptionPlanResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(planService.getSubscriptionPlanById(id));
    }

    @PostMapping
    @RequireAdmin
    public ResponseEntity<SubscriptionPlanResponse> create(@Valid @RequestBody CreateSubscriptionPlanRequest request) {
        LOG.debug("REST request to create SubscriptionPlan: {}", request);
        SubscriptionPlanResponse created = planService.createSubscriptionPlan(request);
        return ResponseEntity.created(URI.create("/api/billing/plans/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @RequireAdmin
    public ResponseEntity<SubscriptionPlanResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateSubscriptionPlanRequest request
    ) {
        return ResponseEntity.ok(planService.updateSubscriptionPlan(id, request));
    }

    @PostMapping("/{id}/activate")
    @RequireAdmin
    public ResponseEntity<SubscriptionPlanResponse> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(planService.activateSubscriptionPlan(id));
    }

    @PostMapping("/{id}/deactivate")
    @RequireAdmin
    public ResponseEntity<SubscriptionPlanResponse> deactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(planService.deactivateSubscriptionPlan(id));
    }

    @DeleteMapping("/{id}")
    @RequireAdmin
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        planService.deleteSubscriptionPlan(id);
        return ResponseEntity.noContent().build();
    }
}
