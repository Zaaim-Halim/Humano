package com.humano.web.rest.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.humano.security.AuthorityPermissionService;
import com.humano.security.PermissionsConstants;
import com.humano.security.SecurityExpressions;
import com.humano.service.payroll.PayComponentService;
import com.humano.service.payroll.template.PayrollTemplateService;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Someone who manages pay rules but not tax brackets (a custom role) can't open the template catalog,
 * yet the pay rules screen must still tell them which templates payroll uses: through its own endpoint,
 * under the screen's permission.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TemplatesInUsePermissionTest.Config.class)
class TemplatesInUsePermissionTest {

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
        PayrollTemplateService payrollTemplateService() {
            PayrollTemplateService service = mock(PayrollTemplateService.class);
            when(service.inUse()).thenReturn(List.of(new PayrollTemplateService.TemplateInUse("nl-employment", "Nederland", "NL")));
            return service;
        }

        @Bean
        PayRuleResource payRuleResource(PayrollTemplateService templateService) {
            return new PayRuleResource(mock(PayComponentService.class), templateService);
        }

        @Bean
        PayrollTemplateResource payrollTemplateResource(PayrollTemplateService templateService) {
            return new PayrollTemplateResource(templateService);
        }
    }

    @Autowired
    private AuthorityPermissionService permissions;

    @Autowired
    private PayRuleResource payRules;

    @Autowired
    private PayrollTemplateResource templates;

    @BeforeEach
    void signInAsAPayRulesEditorWithoutTaxBrackets() {
        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken("payroll", "pw", List.of(new SimpleGrantedAuthority("ROLE_CUSTOM")))
            );
        when(permissions.hasPermission(any(), eq(PermissionsConstants.MANAGE_PAY_COMPONENTS))).thenReturn(true);
        when(permissions.hasPermission(any(), eq(PermissionsConstants.MANAGE_TAX_BRACKETS))).thenReturn(false);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void thePayRulesScreenListsTheTemplatesInUse() {
        assertThat(payRules.templatesInUse().getBody()).extracting(PayrollTemplateService.TemplateInUse::country).containsExactly("NL");
    }

    @Test
    void theTemplateCatalogStaysClosed() {
        assertThatThrownBy(() -> templates.list()).isInstanceOf(AccessDeniedException.class);
    }
}
