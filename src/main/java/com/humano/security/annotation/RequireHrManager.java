package com.humano.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Requires the caller to hold {@code ROLE_ADMIN} or {@code ROLE_HR_MANAGER}.
 * <p>
 * Equivalent to
 * {@code @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_HR_MANAGER')")}.
 * The most common authority gate in the codebase (52 occurrences) — most HR
 * mutations (employee create/update, department/position management) are
 * scoped to this combination, with {@code ROLE_ADMIN} as the implicit
 * baseline that subsumes every business role.
 *
 * @see RequireHrStaff
 * @see RequirePayrollAdmin
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_HR_MANAGER')")
public @interface RequireHrManager {
}
