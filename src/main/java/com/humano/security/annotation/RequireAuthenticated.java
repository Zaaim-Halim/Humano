package com.humano.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Requires the caller to be authenticated, with no further authority constraint.
 * <p>
 * Equivalent to {@code @PreAuthorize("isAuthenticated()")}. Use on endpoints
 * that any logged-in user may reach regardless of role — typically current-user
 * operations (own profile, own sessions) where the controller body itself
 * enforces the principal scope.
 *
 * @see PublicEndpoint
 * @see RequireAdmin
 * @see RequireAuthority
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("isAuthenticated()")
public @interface RequireAuthenticated {
}
