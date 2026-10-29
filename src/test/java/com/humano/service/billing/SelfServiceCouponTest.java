package com.humano.service.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.humano.domain.billing.Coupon;
import com.humano.domain.billing.Subscription;
import com.humano.domain.billing.SubscriptionPlan;
import com.humano.domain.enumeration.billing.BillingCycle;
import com.humano.domain.enumeration.billing.DiscountType;
import com.humano.domain.enumeration.billing.SubscriptionStatus;
import com.humano.domain.tenant.Tenant;
import com.humano.dto.billing.requests.ChangeMyPlanRequest;
import com.humano.repository.billing.CouponRepository;
import com.humano.repository.billing.SubscriptionAddonRepository;
import com.humano.repository.billing.SubscriptionPlanRepository;
import com.humano.repository.billing.SubscriptionRepository;
import com.humano.repository.tenant.TenantRepository;
import com.humano.service.billing.entitlement.EntitlementService;
import com.humano.service.billing.entitlement.StorageAddonOffer;
import com.humano.service.errors.BadRequestAlertException;
import com.humano.tenancy.TenantAccessGuard;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A customer entering a coupon code itself: for its running subscription, or with a plan change. It is
 * checked against the plan and billing cycle, counted only when an invoice uses it, and never replaces
 * a coupon already on the subscription.
 */
class SelfServiceCouponTest {

    private final TenantRepository tenants = mock(TenantRepository.class);
    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final SubscriptionPlanRepository plans = mock(SubscriptionPlanRepository.class);
    private final CouponRepository coupons = mock(CouponRepository.class);
    private final SelfServiceBillingService service = new SelfServiceBillingService(
        tenants,
        subscriptions,
        plans,
        mock(SubscriptionService.class),
        mock(BillingLifecycleService.class),
        mock(TenantAccessGuard.class),
        mock(EntitlementService.class),
        mock(SubscriptionAddonRepository.class),
        mock(StorageAddonOffer.class),
        new CouponService(coupons, plans)
    );

    private Tenant tenant;
    private SubscriptionPlan starter;
    private SubscriptionPlan growth;
    private Subscription subscription;
    private Coupon founding;
    private final List<Coupon> allCoupons = new ArrayList<>();

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setSubdomain("acme");
        when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));

        starter = plan("Starter");
        growth = plan("Growth");

        subscription = new Subscription();
        subscription.setTenant(tenant);
        subscription.setSubscriptionPlan(growth);
        subscription.setBillingCycle(BillingCycle.YEARLY);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        when(subscriptions.findByTenantId(tenant.getId())).thenReturn(Optional.of(subscription));

        when(coupons.findByCodeContainingIgnoreCase(any())).thenAnswer(i ->
            allCoupons.stream().filter(c -> c.getCode().equalsIgnoreCase(i.getArgument(0))).toList()
        );
        founding = coupon("FOUNDING40");
        founding.setBillingCycle(BillingCycle.YEARLY);
        founding.setPlanIds(Set.of(growth.getId()));
        coupon("PARTNER20");
    }

    @Test
    void aCustomerEntersACouponForItsRunningSubscription() {
        service.applyCoupon(tenant.getId(), " founding40 ");

        assertThat(subscription.getCouponCode()).isEqualTo("FOUNDING40");
        assertThat(founding.getTimesRedeemed()).isZero();
    }

    @Test
    void aCouponForAnotherBillingCycleIsRefused() {
        subscription.setBillingCycle(BillingCycle.MONTHLY);

        assertThatThrownBy(() -> service.applyCoupon(tenant.getId(), "FOUNDING40"))
            .isInstanceOf(BadRequestAlertException.class)
            .hasMessageContaining("only applies to yearly billing");
        assertThat(subscription.getCouponCode()).isNull();
    }

    @Test
    void aCouponAlreadyOnTheSubscriptionIsNotReplaced() {
        subscription.setCouponCode("PARTNER20");

        assertThatThrownBy(() -> service.applyCoupon(tenant.getId(), "FOUNDING40"))
            .isInstanceOf(BadRequestAlertException.class)
            .hasMessageContaining("already has coupon PARTNER20");
        assertThat(subscription.getCouponCode()).isEqualTo("PARTNER20");
    }

    @Test
    void aPlanChangeWithACodeAppliesItToTheNewPlanAndCycle() {
        subscription.setSubscriptionPlan(starter);
        subscription.setBillingCycle(BillingCycle.MONTHLY);

        service.changePlan(tenant.getId(), new ChangeMyPlanRequest(growth.getId(), BillingCycle.YEARLY, "FOUNDING40"));

        assertThat(subscription.getSubscriptionPlan()).isSameAs(growth);
        assertThat(subscription.getCouponCode()).isEqualTo("FOUNDING40");
    }

    @Test
    void aPlanChangeWithoutACodeKeepsTheCoupon() {
        subscription.setCouponCode("PARTNER20");

        service.changePlan(tenant.getId(), new ChangeMyPlanRequest(starter.getId(), BillingCycle.YEARLY, null));

        assertThat(subscription.getCouponCode()).isEqualTo("PARTNER20");
    }

    @Test
    void aPlanChangeWithACouponThatDoesNotFitChangesNothing() {
        subscription.setSubscriptionPlan(starter);
        subscription.setBillingCycle(BillingCycle.MONTHLY);

        assertThatThrownBy(() ->
            service.changePlan(tenant.getId(), new ChangeMyPlanRequest(starter.getId(), BillingCycle.YEARLY, "FOUNDING40"))
        ).isInstanceOf(BadRequestAlertException.class);
        assertThat(subscription.getBillingCycle()).isEqualTo(BillingCycle.MONTHLY);
        assertThat(subscription.getCouponCode()).isNull();
    }

    private SubscriptionPlan plan(String name) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(UUID.randomUUID());
        plan.setDisplayName(name);
        plan.setActive(true);
        plan.setPublicListed(true);
        when(plans.findById(plan.getId())).thenReturn(Optional.of(plan));
        return plan;
    }

    private Coupon coupon(String code) {
        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setType(DiscountType.PERCENT);
        coupon.setDiscount(BigDecimal.ZERO);
        coupon.setPercentage(new BigDecimal("20"));
        coupon.setExpiryDate(Instant.now().plus(30, ChronoUnit.DAYS));
        coupon.setTimesRedeemed(0);
        allCoupons.add(coupon);
        return coupon;
    }
}
