package com.humano.service.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.humano.config.multitenancy.TenantAccessGuard;
import com.humano.domain.billing.Coupon;
import com.humano.domain.billing.Invoice;
import com.humano.domain.billing.Subscription;
import com.humano.domain.billing.SubscriptionAddon;
import com.humano.domain.billing.SubscriptionPlan;
import com.humano.domain.enumeration.billing.BillingCycle;
import com.humano.domain.enumeration.billing.SubscriptionStatus;
import com.humano.domain.tenant.Tenant;
import com.humano.repository.billing.InvoiceRepository;
import com.humano.repository.billing.SubscriptionAddonRepository;
import com.humano.repository.billing.SubscriptionRepository;
import com.humano.repository.tenant.TenantRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Add-ons are billed monthly on top of the subscription. */
class AddonBillingTest {

    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final BillingTaxResolver tax = mock(BillingTaxResolver.class);
    private final TenantAdminEmailResolver emails = mock(TenantAdminEmailResolver.class);
    private final SubscriptionAddonRepository addons = mock(SubscriptionAddonRepository.class);
    private final CouponService coupons = mock(CouponService.class);
    private final BillingLifecycleService lifecycle = new BillingLifecycleService(
        subscriptions,
        invoices,
        mock(TenantRepository.class),
        tax,
        mock(BillingMailService.class),
        emails,
        coupons,
        new SimpleMeterRegistry(),
        mock(TenantAccessGuard.class),
        addons
    );

    private final Subscription subscription = new Subscription();
    private final SubscriptionAddon storage = new SubscriptionAddon();

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setSubdomain("acme");
        SubscriptionPlan enterprise = new SubscriptionPlan();
        enterprise.setBasePrice(new BigDecimal("499"));
        enterprise.setDisplayName("Enterprise");
        subscription.setId(UUID.randomUUID());
        subscription.setTenant(tenant);
        subscription.setSubscriptionPlan(enterprise);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodEnd(Instant.now().plus(30, ChronoUnit.DAYS));

        storage.setSubscription(subscription);
        storage.setFeatureCode("DOCUMENTS");
        storage.setQuantity(2);
        storage.setUnitSize(100);
        storage.setUnitPrice(new BigDecimal("25"));

        when(subscriptions.findById(subscription.getId())).thenReturn(Optional.of(subscription));
        when(addons.findBySubscriptionId(subscription.getId())).thenReturn(List.of(storage));
        when(tax.resolve(any(), any(), any(), any())).thenReturn(new BillingTaxResolver.TaxResult(BigDecimal.ZERO, BigDecimal.ZERO, null));
        when(invoices.save(any())).thenAnswer(i -> i.getArgument(0));
        when(emails.resolveBillingContact(any())).thenReturn(Optional.empty());
    }

    @Test
    void monthlyPlanCarriesItsAddonsOnTheSameInvoice() {
        subscription.setBillingCycle(BillingCycle.MONTHLY);

        Invoice invoice = lifecycle.issueFirstInvoice(subscription.getId());

        assertThat(invoice.getAmount()).isEqualByComparingTo("549");
        assertThat(invoice.getLines()).hasSize(2);
        assertThat(invoice.getLines().get(1).getDescription()).isEqualTo("Extra storage, 100 GB pack");
        assertThat(invoice.getLines().get(1).getQuantity()).isEqualTo(2);
    }

    @Test
    void yearlyPlanBillsItsAddonsOnTheirOwnMonthlyInvoice() {
        subscription.setBillingCycle(BillingCycle.YEARLY);
        Instant due = Instant.now().minus(1, ChronoUnit.HOURS);
        storage.setBilledUntil(due);
        when(addons.findDueForMonthlyInvoice(any())).thenReturn(List.of(storage));

        lifecycle.processAddonBilling();

        ArgumentCaptor<Invoice> saved = ArgumentCaptor.forClass(Invoice.class);
        verify(invoices).save(saved.capture());
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("50");
        assertThat(storage.getBilledUntil()).isEqualTo(due.atZone(ZoneOffset.UTC).plusMonths(1).toInstant());
    }

    @Test
    void yearlyPlanRenewalDoesNotIncludeTheAddons() {
        subscription.setBillingCycle(BillingCycle.YEARLY);

        Invoice invoice = lifecycle.issueFirstInvoice(subscription.getId());

        assertThat(invoice.getAmount()).isEqualByComparingTo(
            new BigDecimal("499").multiply(new BigDecimal("12")).multiply(new BigDecimal("0.85"))
        );
        assertThat(invoice.getLines()).hasSize(1);
    }

    @Test
    void couponDiscountsThePlanButNotTheAddons() {
        subscription.setBillingCycle(BillingCycle.MONTHLY);
        subscription.setCouponCode("SAVE10");
        Coupon coupon = new Coupon();
        coupon.setCode("SAVE10");
        when(coupons.applyToAmount(eq("SAVE10"), any())).thenReturn(new CouponService.CouponApplication(coupon, new BigDecimal("49.90")));

        Invoice invoice = lifecycle.issueFirstInvoice(subscription.getId());

        verify(coupons).applyToAmount(eq("SAVE10"), argThat(amount -> amount.compareTo(new BigDecimal("499")) == 0));
        assertThat(invoice.getAmount()).isEqualByComparingTo("549");
        assertThat(invoice.getDiscountAmount()).isEqualByComparingTo("49.90");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("499.10");
    }
}
