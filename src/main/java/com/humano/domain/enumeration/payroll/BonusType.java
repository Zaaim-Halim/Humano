package com.humano.domain.enumeration.payroll;

/**
 * Represents the types of bonuses that can be awarded to employees.
 * <p>
 * Used to categorize different bonus payments for reporting, processing, and tax purposes.
 * Each bonus type may have different business rules, approval workflows, and tax implications.
 */
public enum BonusType {
    /**
     * Bonus based on employee or company performance metrics.
     * Typically awarded after performance reviews or achievement of KPIs.
     */
    PERFORMANCE,

    /**
     * One-time bonus offered to new employees upon joining the company.
     * Often used as a recruitment incentive in competitive job markets.
     */
    SIGNING,

    /**
     * Bonus awarded to employees who refer successful job candidates.
     * Usually paid after the referred employee completes a probation period.
     */
    REFERRAL,

    /**
     * Bonus designed to retain key employees and reduce turnover.
     * Often structured with deferred payment or vesting schedules.
     */
    RETENTION,

    /**
     * Immediate recognition bonus for exceptional work or contributions.
     * Used for timely acknowledgment of specific achievements.
     */
    SPOT,

    /**
     * Annual bonus paid at the end of the fiscal or calendar year.
     * Often based on both individual and company performance.
     */
    YEAR_END,

    /**
     * Bonus that distributes a portion of company profits to employees.
     * Usually calculated as a percentage of company earnings.
     */
    PROFIT_SHARING,

    /**
     * Performance-based payment typically for sales or revenue-generating roles.
     * Often calculated as a percentage of sales or revenue targets.
     */
    COMMISSION,

    /**
     * Bonus awarded upon successful completion of special projects.
     * Recognizes extraordinary effort or contribution to specific initiatives.
     */
    PROJECT_COMPLETION,

    /**
     * Seasonal or holiday-related bonus payments.
     * Often given as a goodwill gesture during major holidays.
     */
    HOLIDAY,

    /**
     * Bonus awarded for reaching service anniversaries or career milestones.
     * Recognizes loyalty and long-term contribution to the organization.
     */
    MILESTONE,

    /**
     * Miscellaneous bonus types not falling into other categories.
     * Used for unique or custom bonus arrangements.
     */
    OTHER
}
