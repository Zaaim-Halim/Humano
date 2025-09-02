package com.humano.domain.enumeration.payroll;

/**
 * Represents the types of deductions that can be applied to an employee's payroll.
 * Used to categorize different deductions for reporting and processing purposes.
 * <p>
 * Each deduction type has specific implications for payroll processing, tax reporting,
 * and employee compensation statements.
 */
public enum DeductionType {
    /**
     * Income tax withholdings at federal, state, or local levels.
     */
    TAX,

    /**
     * Health, life, disability insurance premium deductions.
     */
    INSURANCE,

    /**
     * 401(k), pension, or other retirement plan contributions.
     */
    RETIREMENT,

    /**
     * Court-ordered wage garnishments for debt, child support, etc.
     */
    GARNISHMENT,

    /**
     * Repayment of loans provided by the employer to the employee.
     */
    LOAN_REPAYMENT,

    /**
     * Union membership fees and related deductions.
     */
    UNION_DUES,

    /**
     * Voluntary charitable contributions deducted from pay.
     */
    CHARITY,

    /**
     * Employee savings plans and related deductions.
     */
    SAVINGS_PLAN,

    /**
     * Miscellaneous deductions not falling into other categories.
     */
    OTHER
}
