package com.humano.web.rest.billing;

import com.humano.config.multitenancy.TenantIdResolver;
import com.humano.dto.billing.requests.ChangeMyPlanRequest;
import com.humano.dto.billing.responses.SubscriptionResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequireAuthenticated;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.billing.SelfServiceBillingService;
import com.humano.service.billing.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service billing scoped to the calling tenant. Reads are open to any authenticated user of
 * the tenant (the SPA's billing panel); changing plan, cancelling and resuming need
 * {@code MANAGE_SUBSCRIPTIONS}. Demo workspaces cannot change anything here.
 */
@RestController
@RequestMapping("/api/billing/me")
@RequireAuthenticated
public class MeBillingResource {

    private final SubscriptionService subscriptionService;
    private final SelfServiceBillingService selfService;
    private final TenantIdResolver tenantIdResolver;

    public MeBillingResource(
        SubscriptionService subscriptionService,
        SelfServiceBillingService selfService,
        TenantIdResolver tenantIdResolver
    ) {
        this.subscriptionService = subscriptionService;
        this.selfService = selfService;
        this.tenantIdResolver = tenantIdResolver;
    }

    @GetMapping("/current-subscription")
    public ResponseEntity<SubscriptionResponse> currentSubscription() {
        return ResponseEntity.ok(subscriptionService.getSubscriptionByTenant(tenantIdResolver.requireCurrentTenantId()));
    }

    /** Upgrade, downgrade or switch billing cycle — or pick a plan again after the subscription ended. */
    @PostMapping("/change-plan")
    @RequirePermission(PermissionsConstants.MANAGE_SUBSCRIPTIONS)
    public SubscriptionResponse changePlan(@Valid @RequestBody ChangeMyPlanRequest request) {
        return selfService.changePlan(tenantIdResolver.requireCurrentTenantId(), request);
    }

    /** Ends the subscription at the end of the current period. */
    @PostMapping("/cancel")
    @RequirePermission(PermissionsConstants.MANAGE_SUBSCRIPTIONS)
    public SubscriptionResponse cancel() {
        return selfService.cancel(tenantIdResolver.requireCurrentTenantId());
    }

    /** Takes back a scheduled cancellation. */
    @PostMapping("/resume")
    @RequirePermission(PermissionsConstants.MANAGE_SUBSCRIPTIONS)
    public SubscriptionResponse resume() {
        return selfService.resume(tenantIdResolver.requireCurrentTenantId());
    }
}
