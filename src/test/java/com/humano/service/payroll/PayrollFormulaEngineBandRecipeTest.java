package com.humano.service.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Runs the UK PAYE recipe shown in the pay-rule editor ({@code formula-catalog.ts}) through the real
 * {@link PayrollFormulaEngine}. Its bands are annual, so the recipe annualises monthly pay (× 12) and
 * divides the annual tax back to the month — this pins that the division keeps pence.
 */
class PayrollFormulaEngineBandRecipeTest {

    private static final String UK_PAYE_MONTHLY =
        "#band(#max(0, #grossSalary * 12 - 12570), {{0, 37700, 0.20}, {37700, 112430, 0.40}, {112430, 9999999, 0.45}}) / 12";

    private final PayrollFormulaEngine engine = new PayrollFormulaEngine();

    private BigDecimal monthlyTax(String grossSalary) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("grossSalary", new BigDecimal(grossSalary));
        return engine.evaluateFormula(UK_PAYE_MONTHLY, vars, BigDecimal.class);
    }

    @Test
    void monthlyPayIsTaxedOnItsAnnualEquivalent() {
        // 5,000 × 12 = 60,000; − 12,570 = 47,430 → 37,700 × 20% + 9,730 × 40% = 11,432 a year → 952.67 a month.
        assertThat(monthlyTax("5000")).isEqualByComparingTo("952.67");
    }

    @Test
    void payBelowThePersonalAllowanceIsUntaxed() {
        // 1,000 × 12 = 12,000 < 12,570.
        assertThat(monthlyTax("1000")).isEqualByComparingTo("0");
    }
}
