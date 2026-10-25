package com.humano.service.payroll.template.extension.de;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The PAP adapter with sonstige Bezüge: the current wage's tax is unchanged by a sonstiger Bezug in the same
 * call, and the tax on the sonstiger Bezug is the § 39b Abs. 3 EStG difference (values from
 * tools/payroll-templates/de/lohnsteuer_statute.py, which matched the PAP on 20,000 random cases).
 */
class DeLohnsteuerPap2026Test {

    private final DeLohnsteuerPap2026 pap = new DeLohnsteuerPap2026();

    private static Map<String, Object> inputs(
        int stkl,
        String wage,
        String zkf,
        String kvz,
        int pvz,
        int pva,
        String otherPayment,
        String jre4
    ) {
        Map<String, Object> in = new HashMap<>();
        in.put("stkl", BigDecimal.valueOf(stkl));
        in.put("lzz", BigDecimal.valueOf(2));
        in.put("wage", new BigDecimal(wage));
        in.put("allowance", BigDecimal.ZERO);
        in.put("addition", BigDecimal.ZERO);
        in.put("childAllowances", new BigDecimal(zkf));
        in.put("church", BigDecimal.ONE);
        in.put("kvz", new BigDecimal(kvz));
        in.put("pvs", BigDecimal.ZERO);
        in.put("pvz", BigDecimal.valueOf(pvz));
        in.put("pva", BigDecimal.valueOf(pva));
        in.put("alter1", BigDecimal.ZERO);
        in.put("ajahr", BigDecimal.ZERO);
        in.put("factorProcedure", BigDecimal.ZERO);
        in.put("factor", BigDecimal.ONE);
        in.put("otherPayment", new BigDecimal(otherPayment));
        in.put("expectedAnnualWage", new BigDecimal(jre4));
        in.put("annualAllowance", BigDecimal.ZERO);
        in.put("annualAddition", BigDecimal.ZERO);
        return in;
    }

    @Test
    void aSonstigerBezugLeavesTheCurrentWagesTaxUnchanged() {
        Map<String, BigDecimal> without = pap.compute(inputs(1, "4000.00", "0", "2.90", 1, 0, "0", "48000.00"));
        Map<String, BigDecimal> with = pap.compute(inputs(1, "4000.00", "0", "2.90", 1, 0, "4000.00", "48000.00"));

        assertThat(with.get("lst")).isEqualByComparingTo(without.get("lst")).isEqualByComparingTo("524.50");
        assertThat(with.get("solz")).isEqualByComparingTo(without.get("solz"));
        assertThat(with.get("bk")).isEqualByComparingTo(without.get("bk"));
        assertThat(without.get("sts")).isEqualByComparingTo("0");
    }

    @Test
    void theTaxOnASonstigerBezugIsTheAnnualDifference() {
        Map<String, BigDecimal> classI = pap.compute(inputs(1, "4000.00", "0", "2.90", 1, 0, "4000.00", "48000.00"));
        assertThat(classI.get("sts")).isEqualByComparingTo("996");
        assertThat(classI.get("solzs")).isEqualByComparingTo("0");
        assertThat(classI.get("bks")).isEqualByComparingTo("996");

        Map<String, BigDecimal> classIII = pap.compute(inputs(3, "6500.00", "2", "2.50", 0, 1, "6500.00", "78000.00"));
        assertThat(classIII.get("sts")).isEqualByComparingTo("1720");
    }
}
