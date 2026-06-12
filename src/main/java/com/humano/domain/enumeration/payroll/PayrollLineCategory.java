package com.humano.domain.enumeration.payroll;

/**
 * Structured classification of a {@link com.humano.domain.payroll.PayrollLine}, set by the
 * calculation pipeline as each line is emitted.
 *
 * <p>This is the typed replacement for parsing the human-readable {@code explain} text. The
 * post-time year-to-date reader keys off {@link #INCOME_TAX} / {@link #WITHHOLDING} (plus the
 * line's {@code taxType}) instead of {@code explain.startsWith("Step 7")} etc., so an edit to
 * a log/explain string can no longer silently break the tax ledger.
 */
public enum PayrollLineCategory {
    /** Step 1 — base salary line. */
    BASE_SALARY,
    /** Steps 2a/2b/2c — earnings (inputs, bonuses, formula-driven earning components). */
    EARNING,
    /** Step 3 — leave deduction derived from a LeaveTypeRule. */
    LEAVE_DEDUCTION,
    /** Step 5 — pre-tax deduction. */
    PRE_TAX_DEDUCTION,
    /** Step 7 — progressive income tax (carries {@code taxType = INCOME_TAX}). */
    INCOME_TAX,
    /** Step 8 — other statutory withholding (carries the originating {@code taxType}). */
    WITHHOLDING,
    /** Step 9 — employee-borne portion of a benefit (a deduction). */
    BENEFIT_DEDUCTION,
    /** Steps 10 — post-tax deduction (configured or formula-driven). */
    POST_TAX_DEDUCTION,
    /** Steps 9/12 — employer-side charge (benefit employer cost, formula employer charges). */
    EMPLOYER_CHARGE,
}
