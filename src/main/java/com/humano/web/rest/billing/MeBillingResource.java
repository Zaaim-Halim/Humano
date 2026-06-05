package com.humano.web.rest.billing;

import com.humano.config.multitenancy.TenantIdResolver;
import com.humano.dto.billing.responses.SubscriptionResponse;
import com.humano.service.billing.SubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service billing reads scoped to the calling tenant. Available to any
 * authenticated user belonging to the tenant — the SPA uses this for the billing/
 * subscription panel without needing {@code ROLE_ADMIN}. Mutations stay on the per-
 * aggregate admin-gated resources.
 */
@RestController
@RequestMapping("/api/billing/me")
@PreAuthorize("isAuthenticated()")
public class MeBillingResource {

    private final SubscriptionService subscriptionService;
    private final TenantIdResolver tenantIdResolver;

    public MeBillingResource(SubscriptionService subscriptionService, TenantIdResolver tenantIdResolver) {
        this.subscriptionService = subscriptionService;
        this.tenantIdResolver = tenantIdResolver;
    }

    @GetMapping("/current-subscription")
    public ResponseEntity<SubscriptionResponse> currentSubscription() {
        return ResponseEntity.ok(subscriptionService.getSubscriptionByTenant(tenantIdResolver.requireCurrentTenantId()));
    }
}
