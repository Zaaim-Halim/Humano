package com.humano.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers {@link PlanFeatureInterceptor} on the API. */
@Configuration
public class PlanFeatureWebConfiguration implements WebMvcConfigurer {

    private final PlanFeatureInterceptor planFeatureInterceptor;

    public PlanFeatureWebConfiguration(PlanFeatureInterceptor planFeatureInterceptor) {
        this.planFeatureInterceptor = planFeatureInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(planFeatureInterceptor).addPathPatterns("/api/**");
    }
}
