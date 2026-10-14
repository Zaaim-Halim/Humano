package com.humano.web.rest.billing;

import com.humano.domain.enumeration.billing.SubscriptionType;
import com.humano.dto.billing.requests.CreateSubscriptionPlanRequest;
import com.humano.dto.billing.requests.UpdateSubscriptionPlanRequest;
import com.humano.dto.billing.responses.PlanComparisonResponse;
import com.humano.dto.billing.responses.SubscriptionPlanResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequireAuthenticated;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.billing.FeatureService;
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
    private final FeatureService featureService;

    public SubscriptionPlanResource(SubscriptionPlanService planService, FeatureService featureService) {
        this.planService = planService;
        this.featureService = featureService;
    }

    @GetMapping
    public ResponseEntity<Page<SubscriptionPlanResponse>> list(Pageable pageable) {
        return ResponseEntity.ok(planService.getAllSubscriptionPlans(pageable));
    }

    /** Plan comparison: the feature catalog and what each plan includes (any signed-in user). */
    @GetMapping("/comparison")
    public ResponseEntity<PlanComparisonResponse> comparison() {
        return ResponseEntity.ok(featureService.comparison());
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
}
