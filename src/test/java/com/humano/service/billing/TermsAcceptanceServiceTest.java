package com.humano.service.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.humano.domain.tenant.Tenant;
import org.junit.jupiter.api.Test;

class TermsAcceptanceServiceTest {

    private static Tenant workspace(String subdomain, boolean demo) {
        Tenant t = new Tenant();
        t.setSubdomain(subdomain);
        t.setDemo(demo);
        return t;
    }

    @Test
    void customerWorkspacesAreUnderTheTerms() {
        assertThat(TermsAcceptanceService.applies(workspace("acme", false), "default")).isTrue();
    }

    @Test
    void demosAndThePlatformWorkspaceAreNot() {
        assertThat(TermsAcceptanceService.applies(workspace("acme", true), "default")).isFalse();
        assertThat(TermsAcceptanceService.applies(workspace("DEFAULT", false), "default")).isFalse();
    }
}
