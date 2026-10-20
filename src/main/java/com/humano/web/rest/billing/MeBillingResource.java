package com.humano.web.rest.billing;

import com.humano.dto.billing.requests.ChangeMyPlanRequest;
import com.humano.dto.billing.requests.SetStorageAddonRequest;
import com.humano.dto.billing.responses.EntitlementsResponse;
import com.humano.dto.billing.responses.StorageAddonResponse;
import com.humano.dto.billing.responses.SubscriptionResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequireAuthenticated;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.billing.SelfServiceBillingService;
import com.humano.service.billing.SubscriptionService;
import com.humano.service.billing.entitlement.PlanUsageService;
import com.humano.service.billing.entitlement.StorageAddonService;
import com.humano.tenancy.TenantIdResolver;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final PlanUsageService planUsageService;
    private final StorageAddonService storageAddonService;

    public MeBillingResource(
        SubscriptionService subscriptionService,
        SelfServiceBillingService selfService,
        TenantIdResolver tenantIdResolver,
        PlanUsageService planUsageService,
        StorageAddonService storageAddonService
    ) {
        this.subscriptionService = subscriptionService;
        this.selfService = selfService;
        this.tenantIdResolver = tenantIdResolver;
        this.planUsageService = planUsageService;
        this.storageAddonService = storageAddonService;
    }

    @GetMapping("/current-subscription")
    public ResponseEntity<SubscriptionResponse> currentSubscription() {
        return ResponseEntity.ok(subscriptionService.getSubscriptionByTenant(tenantIdResolver.requireCurrentTenantId()));
    }

    /** What the plan allows, feature by feature, with current usage of the counted ones (any signed-in user). */
    @GetMapping("/entitlements")
    public EntitlementsResponse entitlements() {
        return planUsageService.current();
    }

    /** Extra storage on top of the plan: whether it can be bought, at what price, and how much is held. */
    @GetMapping("/addons/storage")
    public StorageAddonResponse storageAddon() {
        return storageAddonService.view(tenantIdResolver.requireCurrentTenantId());
    }

    /** Sets the number of extra storage packs (0 removes them); billed monthly on top of the subscription. */
    @PutMapping("/addons/storage")
    @RequirePermission(PermissionsConstants.MANAGE_SUBSCRIPTIONS)
    public StorageAddonResponse setStorageAddon(@Valid @RequestBody SetStorageAddonRequest request) {
        return storageAddonService.setPacks(tenantIdResolver.requireCurrentTenantId(), request.packs());
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
