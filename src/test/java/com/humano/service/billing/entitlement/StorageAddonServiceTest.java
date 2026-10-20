package com.humano.service.billing.entitlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.humano.domain.billing.Subscription;
import com.humano.domain.billing.SubscriptionAddon;
import com.humano.domain.billing.SubscriptionPlan;
import com.humano.domain.enumeration.billing.SubscriptionType;
import com.humano.domain.tenant.Tenant;
import com.humano.repository.billing.SubscriptionAddonRepository;
import com.humano.repository.billing.SubscriptionRepository;
import com.humano.repository.tenant.TenantRepository;
import com.humano.service.errors.BadRequestAlertException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class StorageAddonServiceTest {

    private static final long GB = 1024L * 1024 * 1024;

    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final SubscriptionAddonRepository addons = mock(SubscriptionAddonRepository.class);
    private final StorageQuotaService quota = mock(StorageQuotaService.class);
    private final EntitlementService entitlements = mock(EntitlementService.class);
    private final StorageAddonOffer offer = new StorageAddonOffer(100, new BigDecimal("25"), Set.of(SubscriptionType.ENTERPRISE));
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final StorageAddonService service = new StorageAddonService(
        subscriptions,
        tenants,
        addons,
        offer,
        quota,
        entitlements,
        "USD",
        mock(PlatformTransactionManager.class)
    );

    private final UUID tenantId = UUID.randomUUID();
    private final Subscription subscription = new Subscription();
    private final SubscriptionPlan plan = new SubscriptionPlan();

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setSubdomain("acme");
        subscription.setId(UUID.randomUUID());
        subscription.setTenant(tenant);
        subscription.setSubscriptionPlan(plan);
        plan.setSubscriptionType(SubscriptionType.ENTERPRISE);
        when(subscriptions.findByTenantId(tenantId)).thenReturn(Optional.of(subscription));
        when(addons.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void buysPacksAtTheOfferPrice() {
        when(addons.findBySubscriptionIdAndFeatureCode(subscription.getId(), FeatureCodes.STORAGE)).thenReturn(Optional.empty());

        var response = service.setPacks(tenantId, 2);

        assertThat(response.packs()).isEqualTo(2);
        assertThat(response.monthlyTotal()).isEqualByComparingTo("50");
        verify(entitlements).evict("acme");
    }

    @Test
    void platformCanSetANegotiatedPrice() {
        Tenant tenant = subscription.getTenant();
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(addons.findBySubscriptionIdAndFeatureCode(subscription.getId(), FeatureCodes.STORAGE)).thenReturn(Optional.empty());

        var response = service.setPacksAsPlatform(tenantId, 4, new BigDecimal("15"));

        assertThat(response.packs()).isEqualTo(4);
        assertThat(response.packMonthlyPrice()).isEqualByComparingTo("15");
        assertThat(response.monthlyTotal()).isEqualByComparingTo("60");
    }

    @Test
    void notOnPlansThatCannotHaveIt() {
        plan.setSubscriptionType(SubscriptionType.PRO);
        assertThatThrownBy(() -> service.setPacks(tenantId, 1)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void removingPacksIsRefusedWhenTheFilesWouldNotFit() {
        SubscriptionAddon existing = existingPacks(3);
        when(addons.findBySubscriptionIdAndFeatureCode(subscription.getId(), FeatureCodes.STORAGE)).thenReturn(Optional.of(existing));
        when(entitlements.forSubdomain("acme")).thenReturn(storageLimit(1024 + 300));
        when(quota.usedBytes()).thenReturn(1200 * GB);

        assertThatThrownBy(() -> service.setPacks(tenantId, 1)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void removingPacksIsFineWhenTheFilesStillFit() {
        SubscriptionAddon existing = existingPacks(3);
        when(addons.findBySubscriptionIdAndFeatureCode(subscription.getId(), FeatureCodes.STORAGE)).thenReturn(Optional.of(existing));
        when(entitlements.forSubdomain("acme")).thenReturn(storageLimit(1024 + 300));
        when(quota.usedBytes()).thenReturn(900 * GB);

        assertThat(service.setPacks(tenantId, 1).packs()).isEqualTo(1);
    }

    private SubscriptionAddon existingPacks(int quantity) {
        SubscriptionAddon a = new SubscriptionAddon();
        a.setSubscription(subscription);
        a.setFeatureCode(FeatureCodes.STORAGE);
        a.setQuantity(quantity);
        a.setUnitSize(100);
        a.setUnitPrice(new BigDecimal("25"));
        return a;
    }

    private static Entitlements storageLimit(int gb) {
        return new Entitlements(
            UUID.randomUUID(),
            "Enterprise",
            Map.of(FeatureCodes.STORAGE, new Entitlements.Label(UUID.randomUUID(), "Document storage", "GB")),
            Map.of(FeatureCodes.STORAGE, new Entitlements.Grant(true, gb))
        );
    }
}
