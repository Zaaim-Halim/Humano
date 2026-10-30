package com.humano.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.humano.domain.shared.PersistentToken;
import com.humano.domain.shared.User;
import com.humano.repository.shared.PersistentTokenRepository;
import com.humano.repository.shared.UserRepository;
import com.humano.tenancy.TenantContext;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import tech.jhipster.config.JHipsterProperties;

/**
 * A cookie replayed just after its token was rotated is honoured for a few seconds (parallel requests).
 * That grace must stay inside the tenant the token belongs to, because the login it yields is then
 * loaded from the current tenant's database.
 */
class PersistentTokenRememberMeServicesTest {

    private static final String SERIES = "series-1";
    private static final String VALUE = "value-1";

    private final PersistentTokenRepository tokens = mock(PersistentTokenRepository.class);
    private final UserDetailsService users = mock(UserDetailsService.class);
    private PersistentTokenRememberMeServices services;

    @BeforeEach
    void setUp() {
        JHipsterProperties properties = new JHipsterProperties();
        properties.getSecurity().getRememberMe().setKey("test-key");
        services = new PersistentTokenRememberMeServices(properties, users, tokens, mock(UserRepository.class));

        User user = new User();
        user.setLogin("admin");
        PersistentToken token = new PersistentToken();
        token.setSeries(SERIES);
        token.setTokenValue(VALUE);
        token.setTokenDate(LocalDate.now());
        token.setUser(user);

        // The token exists in tenant A only.
        when(tokens.findById(SERIES)).thenAnswer(call ->
            "tenant-a".equals(TenantContext.getCurrentTenant()) ? Optional.of(token) : Optional.empty()
        );
        when(users.loadUserByUsername(anyString())).thenAnswer(call ->
            new AuthenticatedUser("admin", "", List.of(), UUID.randomUUID(), TenantContext.getCurrentTenant())
        );
        lenient().when(tokens.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void cookieIsHonouredAgainInItsOwnTenantRightAfterRotation() {
        TenantContext.setCurrentTenant("tenant-a");
        assertThat(autoLogin()).isNotNull();
        assertThat(autoLogin()).isNotNull();
    }

    @Test
    void rotatedCookieIsNotHonouredInAnotherTenant() {
        TenantContext.setCurrentTenant("tenant-a");
        assertThat(autoLogin()).isNotNull();

        TenantContext.setCurrentTenant("tenant-b");
        assertThat(autoLogin()).isNull();
    }

    @Test
    void cookieOfAnotherTenantIsRefusedOutright() {
        TenantContext.setCurrentTenant("tenant-b");
        assertThat(autoLogin()).isNull();
    }

    private Authentication autoLogin() {
        String cookie = Base64.getEncoder().withoutPadding().encodeToString((SERIES + ":" + VALUE).getBytes(StandardCharsets.UTF_8));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/account");
        request.setCookies(new MockCookie("remember-me", cookie));
        request.addHeader("User-Agent", "test");
        return services.autoLogin(request, new MockHttpServletResponse());
    }
}
