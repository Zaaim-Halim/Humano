package com.humano.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.humano.IntegrationTest;
import com.humano.security.AuthenticatedUser;
import com.humano.security.AuthoritiesConstants;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The operator endpoints through the real security chain: customers' admins stay out, including one who
 * created a role named like the platform's in their own database.
 */
@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = { "management.endpoints.web.base-path=/management", "management.endpoints.web.exposure.include=health,loggers" }
)
class ManagementAccessIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void customerAdminIsRefused() throws Exception {
        mockMvc
            .perform(get("/management/loggers").with(user(person("acme", AuthoritiesConstants.ADMIN))))
            .andExpect(status().isForbidden());
    }

    @Test
    void customerWithARoleNamedLikeThePlatformOwnerIsRefused() throws Exception {
        mockMvc
            .perform(get("/management/loggers").with(user(person("acme", AuthoritiesConstants.PLATFORM_OWNER))))
            .andExpect(status().isForbidden());
    }

    @Test
    void platformAdminWithoutOwnerRoleIsRefused() throws Exception {
        mockMvc
            .perform(get("/management/loggers").with(user(person("default", AuthoritiesConstants.ADMIN))))
            .andExpect(status().isForbidden());
    }

    @Test
    void platformOwnerIsAllowed() throws Exception {
        mockMvc
            .perform(get("/management/loggers").with(user(person("default", AuthoritiesConstants.PLATFORM_OWNER))))
            .andExpect(status().isOk());
    }

    @Test
    void healthStaysPublicForUptimeChecks() throws Exception {
        mockMvc.perform(get("/management/health")).andExpect(status().isOk());
    }

    @Test
    void customerCannotReachAnotherTenantThroughTheRealChain() throws Exception {
        mockMvc
            .perform(get("/api/authorities").header("X-Tenant-ID", "default").with(user(person("acme", AuthoritiesConstants.ADMIN))))
            .andExpect(status().isUnauthorized());
    }

    private static AuthenticatedUser person(String tenant, String... authorities) {
        var granted = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        return new AuthenticatedUser("someone", "", granted, UUID.randomUUID(), tenant);
    }
}
