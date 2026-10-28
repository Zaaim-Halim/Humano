package com.humano.service.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.humano.domain.billing.PlanFeature;
import com.humano.domain.billing.SubscriptionPlan;
import com.humano.repository.billing.FeatureRepository;
import com.humano.repository.billing.SubscriptionPlanRepository;
import com.humano.service.billing.entitlement.FeatureCodes;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class DefaultPlanCatalogTest {

    private final SubscriptionPlanRepository plans = mock(SubscriptionPlanRepository.class);
    private final FeatureRepository features = mock(FeatureRepository.class);
    private final List<SubscriptionPlan> savedPlans = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(plans.save(any())).thenAnswer(inv -> {
            SubscriptionPlan p = inv.getArgument(0);
            // By identity: unsaved plans have no id, and equality is by id.
            if (savedPlans.stream().noneMatch(x -> x == p)) savedPlans.add(p);
            return p;
        });
        when(features.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private DefaultPlanCatalog catalog(boolean enabled) {
        return new DefaultPlanCatalog(plans, features, mock(PlatformTransactionManager.class), enabled);
    }

    private SubscriptionPlan saved(String name) {
        return savedPlans.stream().filter(p -> name.equals(p.getDisplayName())).findFirst().orElseThrow();
    }

    private static PlanFeature cell(SubscriptionPlan plan, String code) {
        return plan.getFeatures().stream().filter(pf -> code.equals(pf.getFeature().getCode())).findFirst().orElseThrow();
    }

    @Test
    void anEmptyDatabaseGetsTheDefaultPlansAndCatalog() {
        when(plans.count()).thenReturn(0L);
        when(features.count()).thenReturn(0L);

        catalog(true).seedIfEmpty();

        assertThat(savedPlans)
            .extracting(SubscriptionPlan::getDisplayName)
            .containsExactlyInAnyOrder(
                DefaultPlanCatalog.FREE,
                DefaultPlanCatalog.STARTER,
                DefaultPlanCatalog.GROWTH,
                DefaultPlanCatalog.ENTERPRISE
            );
        SubscriptionPlan free = saved(DefaultPlanCatalog.FREE);
        SubscriptionPlan starter = saved(DefaultPlanCatalog.STARTER);
        SubscriptionPlan growth = saved(DefaultPlanCatalog.GROWTH);
        assertThat(free.isPublicListed()).isFalse();
        assertThat(growth.isFeatured()).isTrue();
        assertThat(starter.getFeatures()).hasSize(DefaultPlanCatalog.CATALOG.size());
        // Spot checks against the pricing the guide and the pricing page describe.
        assertThat(cell(starter, "EMPLOYEES").getLimitValue()).isEqualTo(25);
        assertThat(cell(starter, "PAYROLL_TEMPLATES").isIncluded()).isTrue();
        assertThat(cell(starter, FeatureCodes.PERFORMANCE).isIncluded()).isFalse();
        assertThat(cell(growth, FeatureCodes.PERFORMANCE).isIncluded()).isTrue();
        assertThat(cell(saved(DefaultPlanCatalog.ENTERPRISE), "EMPLOYEES").getLimitValue()).isNull();
    }

    @Test
    void anExistingCatalogIsNeverTouched() {
        when(plans.count()).thenReturn(1L);
        when(features.count()).thenReturn(0L);
        catalog(true).seedIfEmpty();

        when(plans.count()).thenReturn(0L);
        when(features.count()).thenReturn(3L);
        catalog(true).seedIfEmpty();

        verify(plans, never()).save(any());
        verify(features, never()).save(any());
    }

    @Test
    void canBeSwitchedOff() {
        when(plans.count()).thenReturn(0L);
        when(features.count()).thenReturn(0L);
        catalog(false).seedIfEmpty();
        verify(plans, never()).save(any());
    }

    @Test
    void everyFeatureTheAppChecksIsInTheCatalog() throws IllegalAccessException {
        Set<String> catalogCodes = DefaultPlanCatalog.CATALOG.stream().map(DefaultPlanCatalog.Row::code).collect(Collectors.toSet());
        List<String> checked = new ArrayList<>();
        for (Field f : FeatureCodes.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                f.setAccessible(true);
                checked.add((String) f.get(null));
            }
        }
        assertThat(checked).isNotEmpty();
        assertThat(catalogCodes).containsAll(checked);
        assertThat(DefaultPlanCatalog.CATALOG).allSatisfy(row -> assertThat(row.values()).hasSize(4));
        assertThat(Arrays.stream(DefaultPlanCatalog.CATALOG.toArray()).distinct().count()).isEqualTo(catalogCodes.size());
    }
}
