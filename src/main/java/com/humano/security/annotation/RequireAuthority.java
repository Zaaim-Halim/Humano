package com.humano.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Requires the caller to hold the named authority.
 * <p>
 * Equivalent to {@code @PreAuthorize("hasAuthority('<value>')")} where
 * {@code <value>} is the {@link #value()} attribute, expected to be a
 * constant from {@code AuthoritiesConstants}.
 * <p>
 * Uses Spring Security 6.4 meta-annotation template substitution
 * ({@code {value}}); requires {@code @EnableMethodSecurity} on a config bean.
 * For multi-authority checks (any of N) write {@code @PreAuthorize} directly —
 * Spring's template substitution does not flatten arrays.
 *
 * @see RequireAdmin
 * @see RequirePermission
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAuthority('{value}')")
public @interface RequireAuthority {
    /**
     * @return the authority name (e.g. {@code AuthoritiesConstants.HR_MANAGER})
     */
    String value();
}
