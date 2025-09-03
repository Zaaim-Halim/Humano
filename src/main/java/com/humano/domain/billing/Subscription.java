package com.humano.domain.billing;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.enumeration.billing.BillingCycle;
import com.humano.domain.enumeration.billing.SubscriptionStatus;
import com.humano.domain.tenant.Tenant;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Subscription entity represents a tenant's active service agreement.
 * <p>
 * This entity defines the relationship between a tenant and their chosen subscription plan,
 * including the lifecycle of the subscription from creation through renewal, cancellation,
 * or termination. It tracks the time periods, renewal settings, and billing cycles that
 * determine when and how a tenant is billed for the HR and payroll service.
 * <ul>
 *   <li><b>startDate</b>: When the subscription began, used for billing calculations.</li>
 *   <li><b>endDate</b>: When the subscription terminates (if not auto-renewing).</li>
 *   <li><b>status</b>: Current state of the subscription (ACTIVE, TRIAL, CANCELLED, etc.)</li>
 *   <li><b>autoRenew</b>: Whether the subscription automatically renews at the end of each period.</li>
 *   <li><b>billingCycle</b>: The recurring interval for invoicing (MONTHLY, YEARLY).</li>
 *   <li><b>currentPeriodStart/End</b>: The dates of the current billing period.</li>
 *   <li><b>cancelAtPeriodEnd</b>: Whether the subscription will terminate at the end of the current period.</li>
 *   <li><b>trialStart/End</b>: The dates of the trial period, if applicable.</li>
 * </ul>
 * <p>
 * The subscription connects a tenant to their selected subscription plan and serves as the basis
 * for generating invoices at regular intervals according to the billing cycle.
 */
@Entity
@Table(name = "billing_subscription")
public class Subscription extends AbstractAuditingEntity<UUID> {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
        name = "UUID",
        strategy = "org.hibernate.id.UUIDGenerator",
        parameters = {
            @Parameter(
                name = "uuid_gen_strategy_class",
                value = "org.hibernate.id.uuid.CustomVersionOneStrategy"
            )
        }
    )
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * When the subscription began.
     * <p>
     * This date marks the beginning of the subscription and is used as the anchor point
     * for calculating billing periods and renewal dates.
     */
    @Column(name = "start_date", nullable = false)
    @NotNull(message = "Start date is required")
    private Instant startDate;

    /**
     * When the subscription terminates, if applicable.
     * <p>
     * For fixed-term subscriptions, this is the date when service will end.
     * For auto-renewing subscriptions, this may be null until cancellation is scheduled.
     */
    @Column(name = "end_date")
    private Instant endDate;

    /**
     * Current state of the subscription.
     * <p>
     * Tracks the lifecycle stage of the subscription (e.g., ACTIVE, TRIAL, CANCELLED).
     * This status determines whether the subscription is eligible for billing and service access.
     */
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Subscription status is required")
    private SubscriptionStatus status;

    /**
     * Whether the subscription automatically renews.
     * <p>
     * When true, the subscription will automatically continue at the end of each billing period.
     * When false, the subscription will terminate at the end of the current period.
     */
    @Column(name = "auto_renew", nullable = false)
    @NotNull(message = "Auto-renew setting is required")
    private Boolean autoRenew = true;

    /**
     * The recurring interval for invoicing.
     * <p>
     * Defines how frequently the tenant is billed (MONTHLY or YEARLY).
     * This affects the duration of each billing period and the amount charged.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false)
    @NotNull(message = "Billing cycle is required")
    private BillingCycle billingCycle = BillingCycle.MONTHLY;

    /**
     * The start date of the current billing period.
     * <p>
     * Used to track the beginning of the current service period for which
     * the tenant has been or will be charged.
     */
    @Column(name = "current_period_start")
    private Instant currentPeriodStart;

    /**
     * The end date of the current billing period.
     * <p>
     * Marks when the current service period ends and when the next
     * billing should occur (if auto-renewing).
     */
    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    /**
     * Whether the subscription will terminate at period end.
     * <p>
     * When true, indicates the subscription has been cancelled but service
     * will continue until the end of the current billing period.
     */
    @Column(name = "cancel_at_period_end")
    private Boolean cancelAtPeriodEnd = false;

    /**
     * The start date of the trial period, if applicable.
     * <p>
     * For subscriptions that begin with a trial, this marks when the trial started.
     */
    @Column(name = "trial_start")
    private Instant trialStart;

    /**
     * The end date of the trial period, if applicable.
     * <p>
     * For subscriptions with a trial, this indicates when the trial ends and
     * regular billing begins.
     */
    @Column(name = "trial_end")
    private Instant trialEnd;

    /**
     * The subscription plan associated with this subscription.
     * <p>
     * Defines the feature set and base pricing for this subscription.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "subscription_plan_id", nullable = false)
    @NotNull(message = "Subscription plan is required")
    private SubscriptionPlan subscriptionPlan;

    /**
     * The tenant who owns this subscription.
     * <p>
     * Links to the Tenant entity to identify the customer paying for this subscription.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    @NotNull(message = "Tenant is required")
    private Tenant tenant;


    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public Subscription startDate(Instant startDate) {
        this.startDate = startDate;
        return this;
    }

    public void setStartDate(Instant startDate) {
        this.startDate = startDate;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public Subscription endDate(Instant endDate) {
        this.endDate = endDate;
        return this;
    }

    public void setEndDate(Instant endDate) {
        this.endDate = endDate;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public Subscription status(SubscriptionStatus status) {
        this.status = status;
        return this;
    }

    public void setStatus(SubscriptionStatus status) {
        this.status = status;
    }

    public Boolean getAutoRenew() {
        return autoRenew;
    }

    public Subscription autoRenew(Boolean autoRenew) {
        this.autoRenew = autoRenew;
        return this;
    }

    public void setAutoRenew(Boolean autoRenew) {
        this.autoRenew = autoRenew;
    }

    public BillingCycle getBillingCycle() {
        return billingCycle;
    }

    public Subscription billingCycle(BillingCycle billingCycle) {
        this.billingCycle = billingCycle;
        return this;
    }

    public void setBillingCycle(BillingCycle billingCycle) {
        this.billingCycle = billingCycle;
    }

    public Instant getCurrentPeriodStart() {
        return currentPeriodStart;
    }

    public Subscription currentPeriodStart(Instant currentPeriodStart) {
        this.currentPeriodStart = currentPeriodStart;
        return this;
    }

    public void setCurrentPeriodStart(Instant currentPeriodStart) {
        this.currentPeriodStart = currentPeriodStart;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public Subscription currentPeriodEnd(Instant currentPeriodEnd) {
        this.currentPeriodEnd = currentPeriodEnd;
        return this;
    }

    public void setCurrentPeriodEnd(Instant currentPeriodEnd) {
        this.currentPeriodEnd = currentPeriodEnd;
    }

    public Boolean getCancelAtPeriodEnd() {
        return cancelAtPeriodEnd;
    }

    public Subscription cancelAtPeriodEnd(Boolean cancelAtPeriodEnd) {
        this.cancelAtPeriodEnd = cancelAtPeriodEnd;
        return this;
    }

    public void setCancelAtPeriodEnd(Boolean cancelAtPeriodEnd) {
        this.cancelAtPeriodEnd = cancelAtPeriodEnd;
    }

    public Instant getTrialStart() {
        return trialStart;
    }

    public Subscription trialStart(Instant trialStart) {
        this.trialStart = trialStart;
        return this;
    }

    public void setTrialStart(Instant trialStart) {
        this.trialStart = trialStart;
    }

    public Instant getTrialEnd() {
        return trialEnd;
    }

    public Subscription trialEnd(Instant trialEnd) {
        this.trialEnd = trialEnd;
        return this;
    }

    public void setTrialEnd(Instant trialEnd) {
        this.trialEnd = trialEnd;
    }

    public SubscriptionPlan getSubscriptionPlan() {
        return subscriptionPlan;
    }

    public Subscription subscriptionPlan(SubscriptionPlan subscriptionPlan) {
        this.subscriptionPlan = subscriptionPlan;
        return this;
    }

    public void setSubscriptionPlan(SubscriptionPlan subscriptionPlan) {
        this.subscriptionPlan = subscriptionPlan;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public Subscription tenant(Tenant tenant) {
        this.tenant = tenant;
        return this;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Subscription that = (Subscription) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Subscription{" +
            "id=" + id +
            ", startDate=" + startDate +
            ", endDate=" + endDate +
            ", status=" + status +
            ", autoRenew=" + autoRenew +
            ", billingCycle=" + billingCycle +
            ", currentPeriodStart=" + currentPeriodStart +
            ", currentPeriodEnd=" + currentPeriodEnd +
            ", cancelAtPeriodEnd=" + cancelAtPeriodEnd +
            ", trialStart=" + trialStart +
            ", trialEnd=" + trialEnd +
            '}';
    }
}
