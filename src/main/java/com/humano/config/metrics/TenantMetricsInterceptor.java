package com.humano.config.metrics;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * P7.1 — increments {@link TenantMetrics#REQUESTS_METRIC} on every
 * served request whose handler resolves to a templated MVC path.
 *
 * <p>Runs in {@code afterCompletion} so the final HTTP status is
 * available. {@link com.humano.config.multitenancy.TenantContext} is
 * still populated here — {@code TenantResolutionFilter}'s
 * {@code finally} clear runs after the DispatcherServlet returns,
 * and {@code afterCompletion} fires inside the dispatcher.
 *
 * <p>Endpoint tag is sourced from
 * {@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE} so path IDs
 * (e.g. {@code /api/employees/{id}}) collapse to their template and
 * the metric's cardinality stays bounded. Requests with no matched
 * pattern (404s, static-resource serves) are skipped — the meter
 * shouldn't proliferate one-off series for malformed URIs.
 */
public class TenantMetricsInterceptor implements HandlerInterceptor {

    private final TenantMetrics tenantMetrics;

    public TenantMetricsInterceptor(TenantMetrics tenantMetrics) {
        this.tenantMetrics = tenantMetrics;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern == null) {
            return;
        }
        tenantMetrics.incrementRequest(pattern.toString(), Integer.toString(response.getStatus()));
    }
}
