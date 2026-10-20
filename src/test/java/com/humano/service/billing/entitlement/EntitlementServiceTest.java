package com.humano.service.billing.entitlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.humano.domain.billing.Feature;
import com.humano.domain.billing.PlanFeature;
import com.humano.domain.billing.Subscription;
import com.humano.domain.billing.SubscriptionAddon;
import com.humano.domain.billing.SubscriptionPlan;
import com.humano.domain.tenant.Tenant;
import com.humano.repository.billing.FeatureRepository;
import com.humano.repository.billing.SubscriptionAddonRepository;
import com.humano.repository.billing.SubscriptionRepository;
import com.humano.repository.tenant.TenantRepository;
import com.humano.service.errors.PlanLimitException;
import com.humano.tenancy.MultiTenantProperties;
import com.humano.tenancy.TenantContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class EntitlementServiceTest {

    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
    private final FeatureRepository featureRepository = mock(FeatureRepository.class);
    private final SubscriptionAddonRepository addonRepository = mock(SubscriptionAddonRepository.class);
    private final MultiTenantProperties properties = new MultiTenantProperties();
    private EntitlementService service;

    private final Tenant acme = new Tenant();
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        properties.setPlatformTenant("default");
        service = new EntitlementService(
            tenantRepository,
            subscriptionRepository,
            featureRepository,
            addonRepository,
            properties,
            mock(PlatformTransactionManager.class)
        );

        subscription = new Subscription();
        Feature employees = feature(FeatureCodes.EMPLOYEES);
        Feature payroll = feature(FeatureCodes.PAYROLL);
        when(featureRepository.findAll()).thenReturn(List.of(employees, payroll));

        SubscriptionPlan starter = new SubscriptionPlan();
        starter.setId(UUID.randomUUID());
        starter.setDisplayName("Starter");
        starter.getFeatures().add(planFeature(starter, employees, true, 5));
        starter.getFeatures().add(planFeature(starter, payroll, false, null));

        acme.setId(UUID.randomUUID());
        acme.setSubdomain("acme");
        Feature storage = feature(FeatureCodes.STORAGE);
        when(featureRepository.findAll()).thenReturn(List.of(employees, payroll, storage));
        starter.getFeatures().add(planFeature(starter, storage, true, 1024));
        subscription.setId(UUID.randomUUID());
        subscription.setSubscriptionPlan(starter);
        when(tenantRepository.findBySubdomain("acme")).thenReturn(Optional.of(acme));
        when(subscriptionRepository.findByTenantId(acme.getId())).thenReturn(Optional.of(subscription));
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void appliesTheSubscriptionPlan() {
        Entitlements e = service.forSubdomain("acme");
        assertThat(e.planName()).isEqualTo("Starter");
        assertThat(e.includes(FeatureCodes.PAYROLL)).isFalse();
        assertThat(e.allowsOneMore(FeatureCodes.EMPLOYEES, 4)).isTrue();
        assertThat(e.allowsOneMore(FeatureCodes.EMPLOYEES, 5)).isFalse();
    }

    @Test
    void boughtPacksRaiseTheLimit() {
        SubscriptionAddon packs = new SubscriptionAddon();
        packs.setFeatureCode(FeatureCodes.STORAGE);
        packs.setQuantity(2);
        packs.setUnitSize(100);
        when(addonRepository.findBySubscriptionId(subscription.getId())).thenReturn(List.of(packs));

        assertThat(service.forSubdomain("acme").limit(FeatureCodes.STORAGE)).isEqualTo(1224);
    }

    @Test
    void platformAndUnknownWorkspacesAreNotLimited() {
        assertThat(service.forSubdomain("default")).isSameAs(Entitlements.UNLIMITED);
        assertThat(service.forSubdomain(TenantContext.MASTER)).isSameAs(Entitlements.UNLIMITED);
        when(tenantRepository.findBySubdomain("ghost")).thenReturn(Optional.empty());
        assertThat(service.forSubdomain("ghost")).isSameAs(Entitlements.UNLIMITED);
    }

    @Test
    void cachesUntilEvicted() {
        service.forSubdomain("acme");
        service.forSubdomain("acme");
        verify(tenantRepository, times(1)).findBySubdomain("acme");
        service.evict("acme");
        service.forSubdomain("acme");
        verify(tenantRepository, times(2)).findBySubdomain("acme");
    }

    @Test
    void checkOneMoreRefusesAtTheLimitAndForExcludedFeatures() {
        TenantContext.setCurrentTenant("acme");
        service.checkOneMore(FeatureCodes.EMPLOYEES, 4);
        assertThatThrownBy(() -> service.checkOneMore(FeatureCodes.EMPLOYEES, 5)).isInstanceOf(PlanLimitException.class);
        assertThatThrownBy(() -> service.checkIncluded(FeatureCodes.PAYROLL)).isInstanceOf(PlanLimitException.class);
    }

    private static Feature feature(String code) {
        Feature f = new Feature();
        f.setId(UUID.randomUUID());
        f.setCode(code);
        f.setActive(true);
        return f;
    }

    private static PlanFeature planFeature(SubscriptionPlan plan, Feature feature, boolean included, Integer limit) {
        PlanFeature pf = new PlanFeature();
        pf.setPlan(plan);
        pf.setFeature(feature);
        pf.setIncluded(included);
        pf.setLimitValue(limit);
        return pf;
    }
}
