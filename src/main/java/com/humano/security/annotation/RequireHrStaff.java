package com.humano.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Requires the caller to hold {@code ROLE_ADMIN}, {@code ROLE_HR_MANAGER}, or
 * {@code ROLE_HR_SPECIALIST}.
 * <p>
 * Equivalent to
 * {@code @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_HR_MANAGER', 'ROLE_HR_SPECIALIST')")}.
 * The three-way HR combination seen on read paths and lighter HR mutations
 * (employee profile updates, document uploads) where the specialist role is
 * also trusted to perform the action.
 *
 * @see RequireHrManager
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_HR_MANAGER', 'ROLE_HR_SPECIALIST')")
public @interface RequireHrStaff {
}
