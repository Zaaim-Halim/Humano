package com.humano.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.humano.security.annotation.RequireFeature;
import com.humano.service.billing.entitlement.EntitlementService;
import com.humano.service.billing.entitlement.Entitlements;
import com.humano.service.billing.entitlement.FeatureCodes;
import com.humano.service.errors.PlanLimitException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class PlanFeatureInterceptorTest {

    @RequireFeature(FeatureCodes.PAYROLL)
    static class PayrollController {

        public void list() {}

        public void create() {}
    }

    static class OpenController {

        public void create() {}
    }

    private final EntitlementService entitlements = mock(EntitlementService.class);
    private final PlanFeatureInterceptor interceptor = new PlanFeatureInterceptor(entitlements);

    private void withoutPayroll() {
        when(entitlements.current()).thenReturn(
            new Entitlements(
                UUID.randomUUID(),
                "Starter",
                Map.of(FeatureCodes.PAYROLL, new Entitlements.Label(UUID.randomUUID(), "Payroll", null)),
                Map.of()
            )
        );
    }

    private boolean call(String method, String uri, Object controller, String name) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        HandlerMethod handler = new HandlerMethod(controller, controller.getClass().getMethod(name));
        return interceptor.preHandle(request, new MockHttpServletResponse(), handler);
    }

    @Test
    void readsOfAModuleOutsideThePlanStillWork() throws Exception {
        withoutPayroll();
        assertThat(call("GET", "/api/payroll/runs", new PayrollController(), "list")).isTrue();
        assertThat(call("POST", "/api/payroll/payslips/search", new PayrollController(), "list")).isTrue();
    }

    @Test
    void changesToAModuleOutsideThePlanAreRefused() {
        withoutPayroll();
        assertThatThrownBy(() -> call("POST", "/api/payroll/runs", new PayrollController(), "create")).isInstanceOf(
            PlanLimitException.class
        );
        assertThatThrownBy(() -> call("DELETE", "/api/payroll/runs/1", new PayrollController(), "create")).isInstanceOf(
            PlanLimitException.class
        );
    }

    @Test
    void includedModulesAndUnannotatedEndpointsAreUntouched() throws Exception {
        when(entitlements.current()).thenReturn(Entitlements.UNLIMITED);
        assertThat(call("POST", "/api/payroll/runs", new PayrollController(), "create")).isTrue();
        withoutPayroll();
        assertThat(call("POST", "/api/hr/employees", new OpenController(), "create")).isTrue();
    }
}
