package com.humano.config.metrics;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * P7.1 — registers {@link TenantMetricsInterceptor} for {@code /api/**}
 * only. Static resources, {@code /management/**}, and the SPA shell are
 * intentionally excluded — these are not tenant-attributable traffic and
 * would either inflate the {@code endpoint} tag's cardinality (unmatched
 * static URIs) or pollute the per-tenant series with platform-level
 * scrapes from Prometheus itself.
 */
@Configuration
public class TenantMetricsWebConfig implements WebMvcConfigurer {

    private final TenantMetrics tenantMetrics;

    public TenantMetricsWebConfig(TenantMetrics tenantMetrics) {
        this.tenantMetrics = tenantMetrics;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TenantMetricsInterceptor(tenantMetrics)).addPathPatterns("/api/**");
    }
}
