package com.humano.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The endpoint belongs to a plan feature (a {@code FeatureCodes} constant). When the workspace's plan
 * does not include it, the module is read-only: reads still work (history stays reachable after a
 * downgrade), changes are refused with 403 {@code error.featurenotinplan}.
 * <p>
 * Checked by {@code PlanFeatureInterceptor}, separately from {@link RequirePermission} (a method can
 * carry both: one is about the plan, the other about the person).
 */
@Documented
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireFeature {
    /** The feature code. */
    String value();
}
