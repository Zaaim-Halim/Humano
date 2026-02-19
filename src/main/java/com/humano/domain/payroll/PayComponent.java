package com.humano.domain.payroll;

import com.humano.domain.enumeration.payroll.Kind;
import com.humano.domain.enumeration.payroll.Measurement;
import com.humano.domain.enumeration.payroll.PayComponentCode;
import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

/**
 * PayComponent represents a single element of employee compensation or deduction.
 * <p>
 * It defines the structure of payroll components like base salary, overtime, bonuses, taxes, and benefits.
 * Each component has a code, name, kind (earning, deduction, employer charge), measurement type (amount, rate, hours),
 * and various attributes that determine its tax treatment and calculation behavior.
 * <p>
 * Example PayComponents:
 * <table>
 * <tr><th>Code</th><th>Kind</th><th>Measure</th><th>Notes</th></tr>
 * <tr><td>BASIC</td><td>EARNING</td><td>AMOUNT</td><td>Fixed base salary</td></tr>
 * <tr><td>OT</td><td>EARNING</td><td>HOURS</td><td>Overtime hours × rate</td></tr>
 * <tr><td>BONUS</td><td>EARNING</td><td>AMOUNT</td><td>One-time bonus</td></tr>
 * <tr><td>LEAVE_DEDUCTION</td><td>DEDUCTION</td><td>AMOUNT</td><td>Unpaid leave deduction</td></tr>
 * <tr><td>TAX_PIT</td><td>DEDUCTION</td><td>RATE</td><td>Personal income tax deduction</td></tr>
 * <tr><td>HEALTH_INSURANCE</td><td>EMPLOYER_CHARGE</td><td>AMOUNT</td><td>Employer contribution for benefits</td></tr>
 * </table>
 */
@Entity
@Table(name = "pay_component")
public class PayComponent extends AbstractAuditingEntity<UUID> {

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
     * Unique identifier code for the pay component (e.g., BASIC, OT, TAX_PIT).
     * <p>
     * Used as a standardized reference for payroll calculations and reporting.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, unique = true)
    @NotNull(message = "Pay component code is required")
    private PayComponentCode code;

    /**
     * Human-readable name for the pay component.
     * <p>
     * Used for display on payslips and reports.
     */
    @Column(name = "name", nullable = false)
    @NotNull(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    /**
     * The category of the pay component (EARNING, DEDUCTION, EMPLOYER_CHARGE).
     * <p>
     * Determines how the component affects gross and net pay calculations.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    @NotNull(message = "Kind is required")
    private Kind kind;

    /**
     * The measurement type for the component (AMOUNT, RATE, HOURS).
     * <p>
     * Determines how the component is calculated in payroll processing.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "measure", nullable = false)
    @NotNull(message = "Measure is required")
    private Measurement measure;

    /**
     * Indicates whether this component is subject to income tax.
     * <p>
     * Affects tax calculations on employee earnings.
     * Default is false.
     */
    @Column(name = "taxable", nullable = false)
    @NotNull(message = "Taxable status is required")
    private Boolean taxable = false;

    /**
     * Indicates whether this component contributes to social security calculations.
     * <p>
     * Affects social security contribution calculations.
     * Default is false.
     */
    @Column(name = "contributes_to_social", nullable = false)
    @NotNull(message = "Social contribution status is required")
    private Boolean contributesToSocial = false;

    /**
     * Indicates whether this component is percentage-based.
     * <p>
     * True if the component is calculated as a percentage of another value.
     * Default is false.
     */
    @Column(name = "percentage", nullable = false)
    @NotNull(message = "Percentage status is required")
    private Boolean percentage = false;

    /**
     * The phase or stage of calculation for this component.
     * <p>
     * Used to determine the order in which components are calculated
     * during payroll processing.
     */
    @Column(name = "calc_phase")
    private Integer calcPhase;

    /**
     * The set of rules that determine how this component is calculated.
     * <p>
     * Contains the logic for calculating this component's value for different
     * scenarios, employee groups, or time periods.
     */
    @OneToMany(mappedBy = "payComponent", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PayRule> payRules = new HashSet<>();

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public PayComponentCode getCode() {
        return code;
    }

    public PayComponent code(PayComponentCode code) {
        this.code = code;
        return this;
    }

    public void setCode(PayComponentCode code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public PayComponent name(String name) {
        this.name = name;
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Kind getKind() {
        return kind;
    }

    public PayComponent kind(Kind kind) {
        this.kind = kind;
        return this;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    public Measurement getMeasure() {
        return measure;
    }

    public PayComponent measure(Measurement measure) {
        this.measure = measure;
        return this;
    }

    public void setMeasure(Measurement measure) {
        this.measure = measure;
    }

    public Boolean getTaxable() {
        return taxable;
    }

    public PayComponent taxable(Boolean taxable) {
        this.taxable = taxable;
        return this;
    }

    public void setTaxable(Boolean taxable) {
        this.taxable = taxable;
    }

    public Boolean getContributesToSocial() {
        return contributesToSocial;
    }

    public PayComponent contributesToSocial(Boolean contributesToSocial) {
        this.contributesToSocial = contributesToSocial;
        return this;
    }

    public void setContributesToSocial(Boolean contributesToSocial) {
        this.contributesToSocial = contributesToSocial;
    }

    public Boolean getPercentage() {
        return percentage;
    }

    public PayComponent percentage(Boolean percentage) {
        this.percentage = percentage;
        return this;
    }

    public void setPercentage(Boolean percentage) {
        this.percentage = percentage;
    }

    public Integer getCalcPhase() {
        return calcPhase;
    }

    public PayComponent calcPhase(Integer calcPhase) {
        this.calcPhase = calcPhase;
        return this;
    }

    public void setCalcPhase(Integer calcPhase) {
        this.calcPhase = calcPhase;
    }

    public Set<PayRule> getPayRules() {
        return payRules;
    }

    public PayComponent payRules(Set<PayRule> payRules) {
        this.payRules = payRules;
        return this;
    }

    public void setPayRules(Set<PayRule> payRules) {
        this.payRules = payRules;
    }

    public PayComponent addPayRule(PayRule payRule) {
        this.payRules.add(payRule);
        payRule.setPayComponent(this);
        return this;
    }

    public PayComponent removePayRule(PayRule payRule) {
        this.payRules.remove(payRule);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PayComponent that = (PayComponent) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return (
            "PayComponent{" +
            "id=" +
            id +
            ", code=" +
            code +
            ", name='" +
            name +
            '\'' +
            ", kind=" +
            kind +
            ", measure=" +
            measure +
            ", taxable=" +
            taxable +
            ", contributesToSocial=" +
            contributesToSocial +
            ", percentage=" +
            percentage +
            '}'
        );
    }
}
