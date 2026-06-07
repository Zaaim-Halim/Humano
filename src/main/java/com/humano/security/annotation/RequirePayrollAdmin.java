package com.humano.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Requires the caller to hold {@code ROLE_ADMIN} or {@code ROLE_PAYROLL_ADMIN}.
 * <p>
 * Equivalent to
 * {@code @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_PAYROLL_ADMIN')")}.
 * The default class-level gate on every {@code web/rest/payroll/*Resource} —
 * payroll calendar, pay components, deductions, tax brackets, exchange
 * rates, compensations, bonuses, payroll runs, payslips, etc. Use at the
 * class level on payroll-domain controllers; override per-method when a
 * specific endpoint needs a narrower or broader gate.
 *
 * @see RequireHrManager
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_PAYROLL_ADMIN')")
public @interface RequirePayrollAdmin {
}
