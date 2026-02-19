package com.humano.domain.payroll;

import com.humano.domain.enumeration.payroll.BonusType;
import com.humano.domain.shared.AbstractAuditingEntity;
import com.humano.domain.shared.Employee;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

/**
 * Bonus entity represents additional compensation awarded to employees beyond their base salary.
 * <p>
 * Tracks different types of bonuses (performance, signing, referral, etc.), their amounts, award dates,
 * and payment status. Used for both one-time and recurring bonuses.
 * <p>
 * Bonuses are an important component of employee compensation that can affect employee retention,
 * motivation, and performance. They can be awarded for various reasons and may have different
 * tax implications depending on their type and structure.
 */
@Entity
@Table(name = "bonus")
public class Bonus extends AbstractAuditingEntity<UUID> {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
        name = "UUID",
        strategy = "org.hibernate.id.UUIDGenerator",
        parameters = { @Parameter(name = "uuid_gen_strategy_class", value = "org.hibernate.id.uuid.CustomVersionOneStrategy") }
    )
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * The employee receiving the bonus.
     * <p>
     * Links to the Employee entity in the HR domain to maintain
     * relationship between bonuses and employees.
     */
    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /**
     * The type of bonus (PERFORMANCE, SIGNING, REFERRAL, etc.).
     * <p>
     * Categorizes the bonus for reporting, processing, and tax purposes.
     * Different types may have different approval workflows and tax implications.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private BonusType type;

    /**
     * The monetary amount of the bonus.
     * <p>
     * Represents the gross bonus amount before any applicable taxes or deductions.
     * <p>
     * Minimum value is 0, as bonus amounts cannot be negative.
     */
    @Column(name = "amount", nullable = false)
    @NotNull(message = "Bonus amount is required")
    @DecimalMin(value = "0.01", inclusive = true, message = "Bonus amount must be positive")
    private BigDecimal amount;

    /**
     * The date when the bonus was awarded/approved.
     * <p>
     * Used to track when the bonus was officially granted to the employee,
     * which may differ from the payment date.
     */
    @Column(name = "award_date", nullable = false)
    @NotNull(message = "Award date is required")
    private LocalDate awardDate;

    /**
     * The date when the bonus was or will be paid.
     * <p>
     * Used to track when the bonus amount is actually disbursed to the employee.
     * May be different from the award date for bonuses with deferred payment.
     */
    @Column(name = "payment_date")
    private LocalDate paymentDate;

    /**
     * Indicates whether the bonus has been paid.
     * <p>
     * Tracks the payment status of the bonus, allowing for tracking of
     * pending versus completed bonus payments.
     * Default is false (not paid).
     */
    @Column(name = "is_paid", nullable = false)
    @NotNull(message = "Payment status is required")
    private Boolean isPaid = false;

    /**
     * Detailed description of the bonus award.
     * <p>
     * Provides additional context about the purpose, achievement,
     * or reason for awarding the bonus.
     */
    @Column(name = "description")
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    /**
     * Indicates whether the bonus is subject to taxation.
     * <p>
     * Most bonuses are taxable, but some special categories may have
     * different tax treatment depending on local regulations.
     * Default is true (taxable).
     */
    @Column(name = "is_taxable", nullable = false)
    @NotNull(message = "Taxable status is required")
    private Boolean isTaxable = true;

    /**
     * The currency of the bonus amount.
     * <p>
     * Links to the Currency entity to specify the currency in which
     * the bonus amount is denominated.
     */
    @ManyToOne
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public Bonus employee(Employee employee) {
        this.employee = employee;
        return this;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public BonusType getType() {
        return type;
    }

    public Bonus type(BonusType type) {
        this.type = type;
        return this;
    }

    public void setType(BonusType type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Bonus amount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getAwardDate() {
        return awardDate;
    }

    public Bonus awardDate(LocalDate awardDate) {
        this.awardDate = awardDate;
        return this;
    }

    public void setAwardDate(LocalDate awardDate) {
        this.awardDate = awardDate;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public Bonus paymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
        return this;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }

    public Boolean getIsPaid() {
        return isPaid;
    }

    public Bonus isPaid(Boolean isPaid) {
        this.isPaid = isPaid;
        return this;
    }

    public void setIsPaid(Boolean isPaid) {
        this.isPaid = isPaid;
    }

    public String getDescription() {
        return description;
    }

    public Bonus description(String description) {
        this.description = description;
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getIsTaxable() {
        return isTaxable;
    }

    public Bonus isTaxable(Boolean isTaxable) {
        this.isTaxable = isTaxable;
        return this;
    }

    public void setIsTaxable(Boolean isTaxable) {
        this.isTaxable = isTaxable;
    }

    public Currency getCurrency() {
        return currency;
    }

    public Bonus currency(Currency currency) {
        this.currency = currency;
        return this;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Bonus bonus = (Bonus) o;
        return Objects.equals(id, bonus.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return (
            "Bonus{" +
            "id=" +
            id +
            ", type=" +
            type +
            ", amount=" +
            amount +
            ", awardDate=" +
            awardDate +
            ", paymentDate=" +
            paymentDate +
            ", isPaid=" +
            isPaid +
            ", description='" +
            description +
            '\'' +
            ", isTaxable=" +
            isTaxable +
            '}'
        );
    }
}
