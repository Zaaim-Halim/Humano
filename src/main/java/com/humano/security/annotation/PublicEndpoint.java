package com.humano.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Marks an endpoint as publicly reachable — no authentication required.
 * <p>
 * Equivalent to {@code @PreAuthorize("permitAll()")}. Use on endpoints that are
 * legitimately exposed without a principal (e.g. signup, password reset, signed
 * webhook ingress). The presence of this annotation makes the public-by-design
 * intent visible at the method declaration, so the URL-filter posture in
 * {@code SecurityConfiguration} is no longer the sole source of truth.
 *
 * @see RequireAuthenticated
 * @see RequireAdmin
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("permitAll()")
public @interface PublicEndpoint {
}
