package com.humano.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Requires the caller to hold any of {@code ROLE_ADMIN},
 * {@code ROLE_HR_MANAGER}, {@code ROLE_HR_SPECIALIST}, or {@code ROLE_EMPLOYEE}.
 * <p>
 * Equivalent to
 * {@code @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_HR_MANAGER', 'ROLE_HR_SPECIALIST', 'ROLE_EMPLOYEE')")}.
 * The widest "any tenant user" gate, used on read endpoints (skills,
 * benefits, departments, holidays) where every authenticated user inside
 * the tenant should see the reference data. Controller bodies that scope
 * results to the caller's own record use this combination with a
 * {@code SecurityUtils.getCurrentUserLogin()} filter inside the service.
 *
 * @see RequireHrStaff
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_HR_MANAGER', 'ROLE_HR_SPECIALIST', 'ROLE_EMPLOYEE')")
public @interface RequireHrStaffOrEmployee {
}
