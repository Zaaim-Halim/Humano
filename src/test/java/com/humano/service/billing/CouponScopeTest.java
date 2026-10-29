package com.humano.service.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.humano.domain.billing.Coupon;
import com.humano.domain.enumeration.billing.BillingCycle;
import com.humano.domain.enumeration.billing.DiscountType;
import com.humano.dto.billing.requests.CreateCouponRequest;
import com.humano.repository.billing.CouponRepository;
import com.humano.repository.billing.SubscriptionPlanRepository;
import com.humano.service.errors.BadRequestAlertException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Coupons limited to a billing cycle and to some plans: refused for other subscriptions, before any
 * redemption is counted.
 */
class CouponScopeTest {

    private static final UUID GROWTH = UUID.randomUUID();
    private static final UUID STARTER = UUID.randomUUID();

    private final CouponRepository coupons = mock(CouponRepository.class);
    private final SubscriptionPlanRepository plans = mock(SubscriptionPlanRepository.class);
    private final CouponService service = new CouponService(coupons, plans);
    private Coupon founding;

    @BeforeEach
    void setUp() {
        founding = new Coupon();
        founding.setCode("FOUNDING");
        founding.setType(DiscountType.PERCENT);
        founding.setDiscount(BigDecimal.ZERO);
        founding.setPercentage(new BigDecimal("30"));
        founding.setExpiryDate(Instant.now().plus(30, ChronoUnit.DAYS));
        founding.setTimesRedeemed(0);
        founding.setBillingCycle(BillingCycle.YEARLY);
        founding.setPlanIds(Set.of(GROWTH));
        when(coupons.findByCodeContainingIgnoreCase("FOUNDING")).thenReturn(List.of(founding));
        when(coupons.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void appliesToItsPlanOnItsBillingCycle() {
        CouponService.CouponApplication applied = service.applyToAmount("FOUNDING", new BigDecimal("1000"), GROWTH, BillingCycle.YEARLY);

        assertThat(applied.discountAmount()).isEqualByComparingTo("300");
        assertThat(founding.getTimesRedeemed()).isEqualTo(1);
    }

    @Test
    void refusesTheOtherBillingCycleWithoutCountingARedemption() {
        assertThatThrownBy(() -> service.applyToAmount("FOUNDING", new BigDecimal("100"), GROWTH, BillingCycle.MONTHLY))
            .isInstanceOf(BadRequestAlertException.class)
            .hasMessageContaining("only applies to yearly billing");
        assertThat(founding.getTimesRedeemed()).isZero();
    }

    @Test
    void refusesAnotherPlan() {
        assertThatThrownBy(() -> service.validateOnly("FOUNDING", STARTER, BillingCycle.YEARLY))
            .isInstanceOf(BadRequestAlertException.class)
            .hasMessageContaining("doesn't apply to this plan");
    }

    @Test
    void anUnlimitedCouponAppliesToAnyPlanAndCycle() {
        founding.setBillingCycle(null);
        founding.setPlanIds(Set.of());

        assertThat(service.validateOnly("FOUNDING", STARTER, BillingCycle.MONTHLY).code()).isEqualTo("FOUNDING");
    }

    @Test
    void createsACouponWithItsLimits() {
        when(plans.existsById(GROWTH)).thenReturn(true);

        var created = service.createCoupon(
            new CreateCouponRequest(
                "YEARLY30",
                DiscountType.PERCENT,
                BigDecimal.ZERO,
                new BigDecimal("30"),
                Instant.now().plus(30, ChronoUnit.DAYS),
                null,
                10,
                BillingCycle.YEARLY,
                Set.of(GROWTH)
            )
        );

        assertThat(created.billingCycle()).isEqualTo(BillingCycle.YEARLY);
        assertThat(created.planIds()).containsExactly(GROWTH);
    }

    @Test
    void refusesAnUnknownPlan() {
        assertThatThrownBy(() ->
            service.createCoupon(
                new CreateCouponRequest(
                    "NOPLAN",
                    DiscountType.PERCENT,
                    BigDecimal.ZERO,
                    BigDecimal.TEN,
                    Instant.now().plus(1, ChronoUnit.DAYS),
                    null,
                    null,
                    null,
                    Set.of(UUID.randomUUID())
                )
            )
        ).isInstanceOf(BadRequestAlertException.class);
    }
}
