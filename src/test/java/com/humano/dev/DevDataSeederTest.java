package com.humano.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.humano.security.AuthoritiesConstants;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pure role → demo-login mapping in {@link DevDataSeeder}. The seeder's runtime
 * behaviour (provisioning + user creation) requires a dev MySQL environment and is not unit-tested.
 */
class DevDataSeederTest {

    @Test
    void derivesEmailFromRoleNameStrippingPrefixAndLoweringWithDots() {
        assertThat(DevDataSeeder.emailForRole(AuthoritiesConstants.ADMIN)).isEqualTo("admin@demo.humano");
        assertThat(DevDataSeeder.emailForRole(AuthoritiesConstants.HR_MANAGER)).isEqualTo("hr.manager@demo.humano");
        assertThat(DevDataSeeder.emailForRole(AuthoritiesConstants.PAYROLL_ADMIN)).isEqualTo("payroll.admin@demo.humano");
        assertThat(DevDataSeeder.emailForRole(AuthoritiesConstants.EMPLOYEE)).isEqualTo("employee@demo.humano");
    }
}
