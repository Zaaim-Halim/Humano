package com.humano.config;

import com.humano.security.annotation.RequireFeature;
import com.humano.service.billing.entitlement.EntitlementService;
import com.humano.service.billing.entitlement.Entitlements;
import com.humano.service.errors.PlanLimitException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Applies {@link RequireFeature}: a module the workspace's plan does not include is read-only.
 * Reads go through (including {@code POST …/search}); anything that changes data is refused with
 * 403 {@code error.featurenotinplan}.
 */
@Component
public class PlanFeatureInterceptor implements HandlerInterceptor {

    private static final Set<String> READS = Set.of("GET", "HEAD", "OPTIONS");

    private final EntitlementService entitlementService;

    public PlanFeatureInterceptor(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || isRead(request)) {
            return true;
        }
        RequireFeature feature = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequireFeature.class);
        if (feature == null) {
            feature = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequireFeature.class);
        }
        if (feature != null) {
            Entitlements e = entitlementService.current();
            if (!e.includes(feature.value())) {
                throw PlanLimitException.notInPlan(feature.value(), e.nameOf(feature.value()), e.planName());
            }
        }
        return true;
    }

    /** Reads by method, and the {@code POST …/search} endpoints, which only query. */
    private static boolean isRead(HttpServletRequest request) {
        return READS.contains(request.getMethod()) || request.getRequestURI().endsWith("/search");
    }
}
