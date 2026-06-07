package com.humano.web.rest.billing;

import com.humano.config.multitenancy.TenantIdResolver;
import com.humano.dto.billing.requests.CreateSubscriptionRequest;
import com.humano.dto.billing.requests.UpdateSubscriptionRequest;
import com.humano.dto.billing.responses.SubscriptionResponse;
import com.humano.security.annotation.RequireAdmin;
import com.humano.service.billing.SubscriptionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Subscription management for the current tenant. All endpoints scope to the
 * current tenant via {@link TenantIdResolver} so a tenant admin can never read or mutate
 * another tenant's subscription. Cross-tenant listing is exposed separately at
 * {@code /api/platform/billing/subscriptions} ({@link PlatformBillingResource}).
 */
@RestController
@RequestMapping("/api/billing/subscriptions")
@RequireAdmin
public class SubscriptionResource {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionResource.class);

    private final SubscriptionService subscriptionService;
    private final TenantIdResolver tenantIdResolver;

    public SubscriptionResource(SubscriptionService subscriptionService, TenantIdResolver tenantIdResolver) {
        this.subscriptionService = subscriptionService;
        this.tenantIdResolver = tenantIdResolver;
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubscriptionResponse> get(@PathVariable UUID id) {
        SubscriptionResponse sub = subscriptionService.getSubscriptionById(id);
        verifyTenantOwnership(sub);
        return ResponseEntity.ok(sub);
    }

    @PostMapping
    public ResponseEntity<SubscriptionResponse> create(@Valid @RequestBody CreateSubscriptionRequest request) {
        LOG.debug("REST request to create Subscription: {}", request);
        UUID currentTenantId = tenantIdResolver.requireCurrentTenantId();
        // Override the request tenantId with the resolved current tenant so a client can't
        // accidentally (or maliciously) create a subscription against another tenant.
        CreateSubscriptionRequest safe = new CreateSubscriptionRequest(
            currentTenantId,
            request.subscriptionPlanId(),
            request.billingCycle(),
            request.autoRenew(),
            request.trialEnd(),
            request.couponCode()
        );
        SubscriptionResponse created = subscriptionService.createSubscription(safe);
        return ResponseEntity.created(URI.create("/api/billing/subscriptions/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SubscriptionResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateSubscriptionRequest request) {
        verifyTenantOwnership(subscriptionService.getSubscriptionById(id));
        return ResponseEntity.ok(subscriptionService.updateSubscription(id, request));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<SubscriptionResponse> cancel(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean immediate) {
        verifyTenantOwnership(subscriptionService.getSubscriptionById(id));
        return ResponseEntity.ok(subscriptionService.cancelSubscription(id, immediate));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        verifyTenantOwnership(subscriptionService.getSubscriptionById(id));
        subscriptionService.deleteSubscription(id);
        return ResponseEntity.noContent().build();
    }

    private void verifyTenantOwnership(SubscriptionResponse sub) {
        UUID currentTenantId = tenantIdResolver.requireCurrentTenantId();
        if (!currentTenantId.equals(sub.tenantId())) {
            throw new org.springframework.security.access.AccessDeniedException("Subscription does not belong to the current tenant");
        }
    }
}
