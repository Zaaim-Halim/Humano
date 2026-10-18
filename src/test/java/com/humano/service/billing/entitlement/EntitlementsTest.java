package com.humano.service.billing.entitlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EntitlementsTest {

    private final Entitlements growth = new Entitlements(
        UUID.randomUUID(),
        "Growth",
        catalog(FeatureCodes.EMPLOYEES, FeatureCodes.PAYROLL, FeatureCodes.PERFORMANCE, FeatureCodes.ADMIN_SEATS),
        Map.of(
            FeatureCodes.EMPLOYEES,
            new Entitlements.Grant(true, 25),
            FeatureCodes.PAYROLL,
            new Entitlements.Grant(true, null),
            FeatureCodes.PERFORMANCE,
            new Entitlements.Grant(false, null),
            FeatureCodes.ADMIN_SEATS,
            new Entitlements.Grant(true, null)
        )
    );

    @Test
    void featureTheCatalogDoesNotSellRestrictsNothing() {
        assertThat(growth.includes(FeatureCodes.TRAINING)).isTrue();
        assertThat(growth.allowsOneMore(FeatureCodes.TRAINING, 10_000)).isTrue();
    }

    @Test
    void catalogFeatureMissingFromThePlanOrExcludedIsNotIncluded() {
        Entitlements withoutTimesheetsRow = new Entitlements(UUID.randomUUID(), "Starter", catalog(FeatureCodes.TIMESHEETS), Map.of());
        assertThat(withoutTimesheetsRow.includes(FeatureCodes.TIMESHEETS)).isFalse();
        assertThat(growth.includes(FeatureCodes.PERFORMANCE)).isFalse();
    }

    @Test
    void countedLimitAllowsUpToTheLimit() {
        assertThat(growth.allowsOneMore(FeatureCodes.EMPLOYEES, 24)).isTrue();
        assertThat(growth.allowsOneMore(FeatureCodes.EMPLOYEES, 25)).isFalse();
    }

    @Test
    void nullLimitIsUnlimited() {
        assertThat(growth.allowsOneMore(FeatureCodes.ADMIN_SEATS, 1_000)).isTrue();
    }

    @Test
    void namesFeaturesForMessages() {
        Entitlements e = new Entitlements(
            UUID.randomUUID(),
            "Starter",
            Map.of(FeatureCodes.EMPLOYEES, new Entitlements.Label(UUID.randomUUID(), "Employees", "employees")),
            Map.of()
        );
        assertThat(e.nameOf(FeatureCodes.EMPLOYEES)).isEqualTo("Employees");
        assertThat(e.unitOf(FeatureCodes.EMPLOYEES)).isEqualTo("employees");
        assertThat(e.nameOf("UNKNOWN")).isEqualTo("UNKNOWN");
    }

    private static Map<String, Entitlements.Label> catalog(String... codes) {
        Map<String, Entitlements.Label> m = new java.util.HashMap<>();
        for (String c : codes) {
            m.put(c, new Entitlements.Label(UUID.randomUUID(), c, null));
        }
        return m;
    }

    @Test
    void unlimitedRestrictsNothing() {
        assertThat(Entitlements.UNLIMITED.includes(FeatureCodes.PAYROLL)).isTrue();
        assertThat(Entitlements.UNLIMITED.allowsOneMore(FeatureCodes.EMPLOYEES, 1_000_000)).isTrue();
    }
}
