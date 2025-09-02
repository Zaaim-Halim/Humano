package com.humano.domain.enumeration.payroll;

/**
 * Represents the types of taxes that can be withheld from employee pay.
 * <p>
 * Used to categorize different tax withholdings for reporting, processing, and compliance purposes.
 * Each tax type may have different calculation rules, rates, and reporting requirements
 * based on local, state/provincial, and federal/national tax regulations.
 */
public enum TaxType {
    /**
     * Federal or national income tax withheld from employee pay.
     * The primary income tax levied by the national government.
     */
    INCOME_TAX,

    /**
     * State or provincial income tax withheld from employee pay.
     * Varies by state/province and may not apply in all locations.
     */
    STATE_INCOME_TAX,

    /**
     * City, county, or municipal income tax withheld from employee pay.
     * Applies in specific localities that levy local income taxes.
     */
    LOCAL_INCOME_TAX,

    /**
     * Social security or similar national pension system contributions.
     * Used to fund retirement, disability, and survivor benefits.
     */
    SOCIAL_SECURITY,

    /**
     * Medicare, national health insurance, or similar health system contributions.
     * Used to fund healthcare programs for eligible individuals.
     */
    MEDICARE,

    /**
     * Unemployment insurance tax withholdings.
     * Funds programs that provide benefits to unemployed workers.
     */
    UNEMPLOYMENT,

    /**
     * Disability insurance tax withholdings.
     * Funds programs that provide benefits to workers with disabilities.
     */
    DISABILITY,

    /**
     * Mandatory contributions to government-mandated pension systems.
     * Distinct from voluntary retirement plans like 401(k) or similar.
     */
    PENSION_CONTRIBUTION,

    /**
     * Tax on investment income, stock options, or capital assets.
     * May have different rates and rules than regular income tax.
     */
    CAPITAL_GAINS,

    /**
     * Value-added tax, goods and services tax, or sales tax.
     * Consumption taxes that may be relevant for certain employee benefits or reimbursements.
     */
    VALUE_ADDED_TAX,

    /**
     * Miscellaneous tax types not falling into other categories.
     * Used for unique or jurisdiction-specific tax requirements.
     */
    OTHER
}
