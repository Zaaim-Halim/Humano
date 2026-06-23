package com.humano.service.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies the #1 annual/YTD-ceiling feature from the consumer side: a PayRule formula can reference
 * the {@code #YTD_<TaxType>} variable that {@code PayrollProcessingService.buildCalculationContext}
 * injects, and use it to cap a contribution so it stops once the annual base is hit across runs.
 * <p>
 * Drives the real {@link PayrollFormulaEngine} (including {@code filterToAllowed}), so it also proves
 * the dynamic {@code YTD_SOCIAL_SECURITY} name survives the variable allowlist.
 */
class PayrollFormulaEngineYtdTest {

    private final PayrollFormulaEngine engine = new PayrollFormulaEngine();

    // This period's social-security contribution (rate% of gross), capped at the annual ceiling minus
    // what's already been contributed year-to-date. #YTD_SOCIAL_SECURITY carries the running total from
    // prior posted runs; #threshold(cap, ytd) = max(0, cap - ytd) is the remaining room.
    private static final String CAPPED_SS = "#cap(#pct(#grossSalary, 6.2), #threshold(#SOCIAL_SECURITY_CAP, #YTD_SOCIAL_SECURITY))";

    private BigDecimal contribution(String gross, String cap, String ytd) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("grossSalary", new BigDecimal(gross));
        vars.put("SOCIAL_SECURITY_CAP", new BigDecimal(cap));
        vars.put("YTD_SOCIAL_SECURITY", new BigDecimal(ytd));
        return engine.evaluateFormula(CAPPED_SS, vars, BigDecimal.class);
    }

    @Test
    void firstRunContributesFullPeriodAmountWhenWellBelowCap() {
        // 10000 * 6.2% = 620; remaining 9000 -> min(620, 9000) = 620.
        assertThat(contribution("10000", "9000", "0")).isEqualByComparingTo("620");
    }

    @Test
    void contributionIsTrimmedToRemainingCapRoomNearTheCeiling() {
        // ytd 8700 of 9000 -> remaining 300; period 620 -> min(620, 300) = 300.
        assertThat(contribution("10000", "9000", "8700")).isEqualByComparingTo("300");
    }

    @Test
    void contributionStopsOnceCapReached() {
        // ytd == cap -> remaining 0 -> 0 (the rule stops in later runs).
        assertThat(contribution("10000", "9000", "9000")).isEqualByComparingTo("0");
    }

    @Test
    void contributionStaysZeroWhenYtdAlreadyExceedsCap() {
        assertThat(contribution("10000", "9000", "9500")).isEqualByComparingTo("0");
    }
}
