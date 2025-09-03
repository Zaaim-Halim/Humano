package com.humano.domain.billing;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.enumeration.billing.DiscountType;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Coupon entity represents a discount that can be applied to subscriptions.
 * <p>
 * This entity defines promotional codes that can reduce the price of subscription plans
 * for tenants. Coupons can offer fixed amount or percentage discounts and have
 * various constraints on usage and validity periods.
 * <ul>
 *   <li><b>code</b>: The unique promotional code that tenants enter to claim the discount.</li>
 *   <li><b>discount</b>: The fixed amount discount (when type is FIXED).</li>
 *   <li><b>type</b>: Whether the discount is a fixed amount or percentage (FIXED, PERCENT).</li>
 *   <li><b>percentage</b>: The percentage discount amount (when type is PERCENT).</li>
 *   <li><b>currency</b>: The currency of the fixed discount amount.</li>
 *   <li><b>startDate/expiryDate</b>: The validity period of the coupon.</li>
 *   <li><b>active</b>: Whether the coupon can currently be used.</li>
 *   <li><b>maxRedemptions</b>: The maximum number of times this coupon can be used.</li>
 *   <li><b>timesRedeemed</b>: How many times this coupon has been used.</li>
 * </ul>
 * <p>
 * Coupons can be applied during subscription creation or renewal to provide
 * promotional pricing to tenants.
 */
@Entity
@Table(name = "billing_coupon")
public class Coupon extends AbstractAuditingEntity<UUID> {
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
     * The unique promotional code.
     * <p>
     * This is the code that tenants enter to apply the discount.
     * Must be unique across all coupons in the system.
     */
    @Column(name = "code", nullable = false, unique = true)
    @NotBlank(message = "Coupon code is required")
    @Size(min = 3, max = 50, message = "Coupon code must be between 3 and 50 characters")
    private String code;

    /**
     * The fixed amount discount.
     * <p>
     * When type is FIXED, this represents the monetary amount to be deducted.
     * Not used when type is PERCENT.
     */
    @Column(name = "discount", nullable = false, precision = 19, scale = 4)
    @NotNull(message = "Discount amount is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Discount cannot be negative")
    private BigDecimal discount;

    /**
     * The expiration date of the coupon.
     * <p>
     * The coupon can no longer be used after this date.
     * Required to prevent indefinite coupon usage.
     */
    @Column(name = "expiry_date", nullable = false)
    @NotNull(message = "Expiry date is required")
    private Instant expiryDate;

    /**
     * The type of discount offered.
     * <p>
     * Determines whether the coupon applies a fixed amount (FIXED)
     * or percentage (PERCENT) discount.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type")
    @NotNull(message = "Discount type is required")
    private DiscountType type = DiscountType.FIXED;

    /**
     * The percentage discount amount.
     * <p>
     * When type is PERCENT, this represents the percentage (0-100)
     * to be discounted from the price. Not used when type is FIXED.
     */
    @Column(name = "percentage", precision = 5, scale = 2)
    @DecimalMin(value = "0.0", inclusive = true, message = "Percentage cannot be negative")
    @DecimalMax(value = "100.0", inclusive = true, message = "Percentage cannot exceed 100")
    private BigDecimal percentage;

    /**
     * The currency of the fixed discount amount.
     * <p>
     * ISO 4217 currency code (e.g., "USD", "EUR") for the discount.
     * Only relevant when type is FIXED.
     */
    @Column(name = "currency", length = 3)
    @Size(min = 3, max = 3, message = "Currency code must be exactly 3 characters")
    private String currency = "USD";

    /**
     * The start date of the coupon's validity period.
     * <p>
     * The coupon can only be used on or after this date.
     * If null, the coupon is valid from creation.
     */
    @Column(name = "start_date")
    private Instant startDate;

    /**
     * Whether the coupon can currently be used.
     * <p>
     * When false, the coupon cannot be applied to new subscriptions
     * even if within the validity period.
     */
    @Column(name = "active", nullable = false)
    @NotNull(message = "Active status is required")
    private boolean active = true;

    /**
     * The maximum number of times this coupon can be used.
     * <p>
     * Limits the total usage of this coupon across all tenants.
     * If null, there is no maximum (limited only by expiry date).
     */
    @Column(name = "max_redemptions")
    private Integer maxRedemptions;

    /**
     * How many times this coupon has been used.
     * <p>
     * Counter that tracks the total number of times this coupon
     * has been applied to subscriptions.
     */
    @Column(name = "times_redeemed")
    private Integer timesRedeemed = 0;

    /**
     * Optimistic locking version for concurrent modifications.
     */
    @Version
    private Long version;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public Coupon code(String code) {
        this.code = code;
        return this;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public Coupon discount(BigDecimal discount) {
        this.discount = discount;
        return this;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
    }

    public DiscountType getType() {
        return type;
    }

    public Coupon type(DiscountType type) {
        this.type = type;
        return this;
    }

    public void setType(DiscountType type) {
        this.type = type;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public Coupon percentage(BigDecimal percentage) {
        this.percentage = percentage;
        return this;
    }

    public void setPercentage(BigDecimal percentage) {
        this.percentage = percentage;
    }

    public String getCurrency() {
        return currency;
    }

    public Coupon currency(String currency) {
        this.currency = currency;
        return this;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public Coupon startDate(Instant startDate) {
        this.startDate = startDate;
        return this;
    }

    public void setStartDate(Instant startDate) {
        this.startDate = startDate;
    }

    public Instant getExpiryDate() {
        return expiryDate;
    }

    public Coupon expiryDate(Instant expiryDate) {
        this.expiryDate = expiryDate;
        return this;
    }

    public void setExpiryDate(Instant expiryDate) {
        this.expiryDate = expiryDate;
    }

    public boolean isActive() {
        return active;
    }

    public Coupon active(boolean active) {
        this.active = active;
        return this;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Integer getMaxRedemptions() {
        return maxRedemptions;
    }

    public Coupon maxRedemptions(Integer maxRedemptions) {
        this.maxRedemptions = maxRedemptions;
        return this;
    }

    public void setMaxRedemptions(Integer maxRedemptions) {
        this.maxRedemptions = maxRedemptions;
    }

    public Integer getTimesRedeemed() {
        return timesRedeemed;
    }

    public Coupon timesRedeemed(Integer timesRedeemed) {
        this.timesRedeemed = timesRedeemed;
        return this;
    }

    public void setTimesRedeemed(Integer timesRedeemed) {
        this.timesRedeemed = timesRedeemed;
    }

    public Long getVersion() {
        return version;
    }

    /**
     * Checks if the coupon is currently applicable.
     * @return true if the coupon is active, not expired, and hasn't exceeded max redemptions
     */
    public boolean isApplicable() {
        Instant now = Instant.now();
        return active
            && (startDate == null || !startDate.isAfter(now))
            && !expiryDate.isBefore(now)
            && (maxRedemptions == null || timesRedeemed < maxRedemptions);
    }

    /**
     * Calculates the discount amount for a given price.
     * @param price The price to apply the discount to
     * @return The discount amount
     */
    public BigDecimal calculateDiscountAmount(BigDecimal price) {
        if (type == DiscountType.FIXED) {
            return discount;
        } else if (type == DiscountType.PERCENT && percentage != null) {
            return price.multiply(percentage.divide(new BigDecimal("100")));
        }
        return BigDecimal.ZERO;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Coupon coupon = (Coupon) o;
        return Objects.equals(id, coupon.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Coupon{" +
            "id=" + id +
            ", code='" + code + '\'' +
            ", type=" + type +
            ", discount=" + (type == DiscountType.FIXED ? discount : percentage + "%") +
            ", currency='" + (type == DiscountType.FIXED ? currency : "") + '\'' +
            ", expiryDate=" + expiryDate +
            ", active=" + active +
            ", timesRedeemed=" + timesRedeemed +
            '}';
    }
}
