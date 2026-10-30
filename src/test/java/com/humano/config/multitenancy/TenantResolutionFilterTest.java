package com.humano.config.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.humano.security.AuthenticatedUser;
import com.humano.tenancy.MultiTenantProperties;
import com.humano.tenancy.TenantAccessGuard;
import com.humano.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Tenant isolation at the door. The principal decides, not a session attribute, so every way of signing
 * in is covered: a session created from a remember-me cookie must be as bound as one from the login form.
 */
class TenantResolutionFilterTest {

    private TenantResolutionFilter filter;
    private final AtomicReference<String> tenantSeenByChain = new AtomicReference<>();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void setUp() {
        MultiTenantProperties properties = new MultiTenantProperties();
        properties.setPlatformTenant("default");
        filter = new TenantResolutionFilter(new TenantResolver(), properties, mock(TenantAccessGuard.class));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void signedInPersonReachesTheirOwnTenant() throws Exception {
        signInAs("acme");

        run(request("/api/employees", "acme"));

        assertThat(tenantSeenByChain.get()).isEqualTo("acme");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void anotherTenantIsRefusedThroughTheHeader() throws Exception {
        signInAs("acme");

        run(request("/api/employees", "victim"));

        assertRefused();
    }

    @Test
    void anotherTenantIsRefusedThroughTheSubdomain() throws Exception {
        signInAs("acme");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/employees");
        request.setServerName("victim.humano.com");

        run(request);

        assertRefused();
    }

    @Test
    void sessionWithoutAnyTenantAttributeIsStillBound() throws Exception {
        // What remember-me leaves behind: a new session holding only the security context.
        signInAs("acme");
        MockHttpServletRequest request = request("/api/employees", "victim");
        request.setSession(new MockHttpSession());

        run(request);

        assertRefused();
    }

    @Test
    void principalWithoutATenantIsRefused() throws Exception {
        signInAs(null);

        run(request("/api/employees", "acme"));

        assertRefused();
    }

    @Test
    void platformApiIsRefusedToACustomer() throws Exception {
        signInAs("acme");

        run(new MockHttpServletRequest("GET", "/api/platform/metrics"));

        assertRefused();
    }

    @Test
    void fileSuffixDoesNotSkipTheChecksOnTheApi() throws Exception {
        signInAs("acme");

        run(request("/api/employees/photo.png", "victim"));

        assertRefused();
    }

    @Test
    void plainBrowserRequestFallsBackToTheTenantSignedInTo() throws Exception {
        signInAs("acme");

        run(new MockHttpServletRequest("GET", "/api/files/photo"));

        assertThat(tenantSeenByChain.get()).isEqualTo("acme");
    }

    @Test
    void signingInToAnotherOrganizationDropsTheOldSession() throws Exception {
        signInAs("acme");
        MockHttpServletRequest request = request("/api/authentication", "other");
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);

        run(request);

        assertThat(tenantSeenByChain.get()).isEqualTo("other");
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void visitorsAreLeftToSpringSecurity() throws Exception {
        run(request("/api/employees", "acme"));

        assertThat(tenantSeenByChain.get()).isEqualTo("acme");
    }

    @Test
    void publicEndpointsAndStripeWebhooksCarryNoTenant() {
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/api/public/signup"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/api/billing/webhooks/stripe"))).isTrue();
    }

    private static MockHttpServletRequest request(String path, String tenant) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader("X-Tenant-ID", tenant);
        return request;
    }

    private static void signInAs(String tenant) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        AuthenticatedUser user = new AuthenticatedUser("admin", "", authorities, UUID.randomUUID(), tenant);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
    }

    private void run(HttpServletRequest request) throws Exception {
        filter.doFilter(request, response, (req, res) -> tenantSeenByChain.set(TenantContext.getCurrentTenant()));
    }

    private void assertRefused() {
        assertThat(tenantSeenByChain.get()).as("request must not reach the application").isNull();
        assertThat(response.getStatus()).isEqualTo(401);
    }
}
