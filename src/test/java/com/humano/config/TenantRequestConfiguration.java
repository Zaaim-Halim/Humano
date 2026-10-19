package com.humano.config;

import com.humano.config.multitenancy.MultiTenantProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Web integration tests call the API as the platform tenant ({@code default}). Every {@code /api/**}
 * request needs a tenant (see {@code TenantResolutionFilter}); {@code default} is a statically
 * configured target backed by the test database, so no tenant has to be provisioned.
 */
@Configuration
public class TenantRequestConfiguration {

    @Bean
    MockMvcBuilderCustomizer tenantHeaderCustomizer(MultiTenantProperties properties) {
        return builder -> builder.defaultRequest(MockMvcRequestBuilders.get("/").header("X-Tenant-ID", properties.getPlatformTenant()));
    }
}
