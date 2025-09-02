package com.humano.domain.enumeration.payroll;

/**
 * Represents the types of benefits that can be provided to employees.
 * <p>
 * Used to categorize different employee benefits for reporting, processing, and compliance purposes.
 * Each benefit type may have different tax implications, enrollment processes, and regulatory requirements.
 */
public enum BenefitType {
    /**
     * Medical, dental, and vision insurance coverage.
     * Core health benefits that protect employees from healthcare costs.
     */
    HEALTH_INSURANCE,

    /**
     * Insurance coverage that provides a benefit to an employee's beneficiaries upon death.
     * May include basic, supplemental, and dependent life insurance options.
     */
    LIFE_INSURANCE,

    /**
     * Short-term or long-term disability insurance coverage.
     * Provides income protection if an employee becomes unable to work due to disability.
     */
    DISABILITY_INSURANCE,

    /**
     * 401(k), pension, or similar retirement savings and income plans.
     * Help employees save for retirement with potential employer contributions.
     */
    RETIREMENT_PLAN,

    /**
     * Employee stock purchase plans, restricted stock units, or stock options.
     * Allows employees to share in company ownership and potential growth.
     */
    STOCK_OPTION,

    /**
     * Flexible spending accounts for healthcare, dependent care, or transit expenses.
     * Allow pre-tax payment for qualifying expenses, reducing employee tax burden.
     */
    FLEXIBLE_SPENDING,

    /**
     * Health savings accounts used with high-deductible health plans.
     * Tax-advantaged accounts for healthcare expenses with potential employer contributions.
     */
    HEALTH_SAVINGS,

    /**
     * Tuition reimbursement or other educational assistance programs.
     * Support employee career development and continuing education.
     */
    EDUCATIONAL_ASSISTANCE,

    /**
     * Wellness programs, fitness benefits, or health promotion initiatives.
     * Encourage healthy behaviors and may include incentives for participation.
     */
    WELLNESS_PROGRAM,

    /**
     * Childcare subsidies, on-site childcare, or backup childcare services.
     * Help employees balance work and family responsibilities.
     */
    CHILDCARE_ASSISTANCE,

    /**
     * Transit subsidies, parking benefits, or company vehicles.
     * Support employee commuting needs and reduce commuting costs.
     */
    TRANSPORTATION,

    /**
     * Housing subsidies, stipends, or relocation assistance.
     * Help with housing costs, especially in high-cost areas or for relocated employees.
     */
    HOUSING_ALLOWANCE,

    /**
     * Meal subsidies, cafeteria benefits, or food allowances.
     * Provide nutritional support and convenience for employees during work hours.
     */
    MEAL_ALLOWANCE,

    /**
     * Vacation time, sick leave, personal days, and other paid time off.
     * Allow employees to take time away from work while maintaining income.
     */
    PAID_TIME_OFF,

    /**
     * Miscellaneous benefit types not falling into other categories.
     * Used for unique or company-specific benefit offerings.
     */
    OTHER
}
