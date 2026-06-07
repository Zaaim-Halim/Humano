package com.humano.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Requires the caller to hold any of {@code ROLE_ADMIN},
 * {@code ROLE_PAYROLL_ADMIN}, or {@code ROLE_HR_MANAGER}.
 * <p>
 * Equivalent to
 * {@code @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_PAYROLL_ADMIN', 'ROLE_HR_MANAGER')")}.
 * The class-level gate on compensation-related controllers — payslips,
 * employee benefits, compensation records, bonuses — where both payroll
 * (the system of record for money) and HR (the system of record for
 * people) need access.
 *
 * @see RequirePayrollAdmin
 * @see RequireHrManager
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_PAYROLL_ADMIN', 'ROLE_HR_MANAGER')")
public @interface RequirePayrollOrHrManager {
}
