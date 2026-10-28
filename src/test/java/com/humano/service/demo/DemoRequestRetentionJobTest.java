package com.humano.service.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.humano.domain.tenant.DemoRequest;
import com.humano.domain.tenant.Tenant;
import org.junit.jupiter.api.Test;

class DemoRequestRetentionJobTest {

    private static DemoRequest withWorkspace(Boolean demo) {
        DemoRequest request = new DemoRequest();
        if (demo != null) {
            Tenant tenant = new Tenant();
            tenant.setDemo(demo);
            request.setTenant(tenant);
        }
        return request;
    }

    @Test
    void aRequestWithoutAWorkspaceOrStillADemoIsNotACustomer() {
        assertThat(DemoRequestRetentionJob.notACustomer(withWorkspace(null))).isTrue();
        assertThat(DemoRequestRetentionJob.notACustomer(withWorkspace(true))).isTrue();
    }

    @Test
    void aRequestWhoseDemoWasConvertedIsKept() {
        assertThat(DemoRequestRetentionJob.notACustomer(withWorkspace(false))).isFalse();
    }
}
