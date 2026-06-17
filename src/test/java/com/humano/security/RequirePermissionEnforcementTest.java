package com.humano.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.humano.security.annotation.RequirePermission;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.PrePostTemplateDefaults;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Proves {@link RequirePermission} actually enforces through the Spring Security method-security
 * pipeline — the chain that had never run via annotations before this change:
 * <ol>
 *   <li>the Spring Security 6.4 {@code {value}} meta-annotation template substitution, enabled by
 *       the {@code PrePostTemplateDefaults} bean (mirrored here from {@code SecurityConfiguration});</li>
 *   <li>the {@code @securityExpressions.hasPermission(...)} SpEL → {@link AuthorityPermissionService}
 *       call chain.</li>
 * </ol>
 * Uses a minimal method-security slice (no full app context, so it does not depend on the
 * application's integration-test harness) and mocks {@link AuthorityPermissionService} so the
 * per-tenant DB cache (unit-tested separately) is out of scope. The mock is stubbed on the
 * <em>literal</em> permission code: a passing "granted" case is only possible if {@code {value}}
 * was substituted to {@code UNIT_TEST_PERM} — otherwise the expression would query {@code "{value}"}
 * and the stub would not match.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RequirePermissionEnforcementTest.Config.class)
class RequirePermissionEnforcementTest {

    static final String TEST_PERMISSION = "UNIT_TEST_PERM";

    @Configuration
    @EnableMethodSecurity(securedEnabled = true)
    static class Config {

        @Bean
        static PrePostTemplateDefaults prePostTemplateDefaults() {
            return new PrePostTemplateDefaults();
        }

        @Bean
        AuthorityPermissionService authorityPermissionService() {
            return mock(AuthorityPermissionService.class);
        }

        @Bean("securityExpressions")
        SecurityExpressions securityExpressions(AuthorityPermissionService authorityPermissionService) {
            return new SecurityExpressions(authorityPermissionService);
        }

        @Bean
        GuardedTestService guardedTestService() {
            return new GuardedTestService();
        }
    }

    @Autowired
    private AuthorityPermissionService authorityPermissionService;

    @Autowired
    private GuardedTestService guardedTestService;

    @BeforeEach
    void setAuthenticatedUser() {
        SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken("tester", "pw", List.of(new SimpleGrantedAuthority("ROLE_TESTER"))));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsWhenPermissionGranted() {
        when(authorityPermissionService.hasPermission(any(), eq(TEST_PERMISSION))).thenReturn(true);
        assertThatCode(() -> guardedTestService.guarded()).doesNotThrowAnyException();
    }

    @Test
    void deniesWhenPermissionMissing() {
        when(authorityPermissionService.hasPermission(any(), eq(TEST_PERMISSION))).thenReturn(false);
        assertThatThrownBy(() -> guardedTestService.guarded()).isInstanceOf(AccessDeniedException.class);
    }

    /** A Spring bean whose method is gated by the real {@link RequirePermission} annotation. */
    @Service
    static class GuardedTestService {

        @RequirePermission(TEST_PERMISSION)
        public String guarded() {
            return "ok";
        }
    }
}
