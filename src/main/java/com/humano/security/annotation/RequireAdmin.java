package com.humano.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Requires the caller to hold {@code ROLE_ADMIN}.
 * <p>
 * Equivalent to {@code @PreAuthorize("hasAuthority('ROLE_ADMIN')")}. Shorthand
 * for the most common authority gate; for any other single authority use
 * {@link RequireAuthority}.
 *
 * @see RequireAuthority
 * @see RequirePermission
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public @interface RequireAdmin {
}
