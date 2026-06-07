package com.humano.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Requires the caller to hold the named permission, resolved through
 * {@code SecurityExpressions.hasPermission}.
 * <p>
 * Equivalent to
 * {@code @PreAuthorize("@securityExpressions.hasPermission('<value>')")} where
 * {@code <value>} is the {@link #value()} attribute, expected to be a
 * constant from {@code PermissionsConstants}. The permission is looked up
 * via the authority-permission cache, so a single authority may transitively
 * unlock several permissions.
 * <p>
 * Uses Spring Security 6.4 meta-annotation template substitution
 * ({@code {value}}); requires {@code @EnableMethodSecurity} on a config bean.
 *
 * @see RequireAuthority
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@securityExpressions.hasPermission('{value}')")
public @interface RequirePermission {
    /**
     * @return the permission name (e.g. {@code PermissionsConstants.CREATE_EMPLOYEE})
     */
    String value();
}
