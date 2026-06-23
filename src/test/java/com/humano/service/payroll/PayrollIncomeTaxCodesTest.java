package com.humano.service.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.humano.domain.enumeration.payroll.TaxCode;
import com.humano.domain.enumeration.payroll.TaxType;
import com.humano.domain.payroll.TaxBracket;
import com.humano.service.payroll.PayrollProcessingService.IncomeTaxComponent;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PayrollProcessingService#computeIncomeTaxes} — the multi-code income-tax
 * iteration behind step 7 (#2, bracket-based income tax beyond PIT). Drives the pure helper with
 * stubbed bracket-lookup / progressive-tax functions, so no payroll or DB stack is needed.
 */
class PayrollIncomeTaxCodesTest {

    private static final BigDecimal TAXABLE = new BigDecimal("50000");

    /** Stub progressive-tax: amount is deterministic from the bracket count so each code is distinguishable. */
    private static final BiFunction<BigDecimal, List<TaxBracket>, BigDecimal> TAX_BY_BRACKET_COUNT = (income, brackets) ->
        BigDecimal.valueOf(brackets.size() * 100L);

    private static List<TaxBracket> brackets(int n) {
        return java.util.Collections.nCopies(n, new TaxBracket());
    }

    private static Function<TaxCode, List<TaxBracket>> lookup(Map<TaxCode, List<TaxBracket>> byCode) {
        return code -> byCode.getOrDefault(code, List.of());
    }

    @Test
    void pitOnlyCountryYieldsExactlyTheSingleIncomeTaxLineAsBefore() {
        List<IncomeTaxComponent> result = PayrollProcessingService.computeIncomeTaxes(
            TAXABLE,
            lookup(Map.of(TaxCode.PIT, brackets(3))),
            TAX_BY_BRACKET_COUNT
        );

        assertThat(result)
            .singleElement()
            .satisfies(it -> {
                assertThat(it.code()).isEqualTo(TaxCode.PIT);
                assertThat(it.ledgerType()).isEqualTo(TaxType.INCOME_TAX);
                assertThat(it.bracketCount()).isEqualTo(3);
                // Identical to a direct calculateProgressiveTax(taxable, pitBrackets) call.
                assertThat(it.amount()).isEqualByComparingTo(TAX_BY_BRACKET_COUNT.apply(TAXABLE, brackets(3)));
            });
    }

    @Test
    void evaluatesStateAndLocalCodesWithTheCorrectLedgerTypesInNationalFirstOrder() {
        List<IncomeTaxComponent> result = PayrollProcessingService.computeIncomeTaxes(
            TAXABLE,
            lookup(Map.of(TaxCode.PIT, brackets(2), TaxCode.STATE_PIT, brackets(3), TaxCode.LOCAL_PIT, brackets(4))),
            TAX_BY_BRACKET_COUNT
        );

        assertThat(result).extracting(IncomeTaxComponent::code).containsExactly(TaxCode.PIT, TaxCode.STATE_PIT, TaxCode.LOCAL_PIT);
        assertThat(result)
            .extracting(IncomeTaxComponent::ledgerType)
            .containsExactly(TaxType.INCOME_TAX, TaxType.STATE_INCOME_TAX, TaxType.LOCAL_INCOME_TAX);
        assertThat(result)
            .extracting(IncomeTaxComponent::amount)
            .containsExactly(new BigDecimal("200"), new BigDecimal("300"), new BigDecimal("400"));
    }

    @Test
    void skipsCodesWithNoBracketsAndZeroComputedTax() {
        List<IncomeTaxComponent> result = PayrollProcessingService.computeIncomeTaxes(
            TAXABLE,
            lookup(Map.of(TaxCode.PIT, brackets(2), TaxCode.STATE_PIT, brackets(1))),
            (income, b) -> b.size() == 1 ? BigDecimal.ZERO : BigDecimal.valueOf(500) // STATE_PIT computes to zero
        );

        assertThat(result).extracting(IncomeTaxComponent::code).containsExactly(TaxCode.PIT);
    }

    @Test
    void returnsEmptyWhenNoIncomeTaxBracketsExist() {
        List<IncomeTaxComponent> result = PayrollProcessingService.computeIncomeTaxes(TAXABLE, lookup(Map.of()), TAX_BY_BRACKET_COUNT);
        assertThat(result).isEmpty();
    }
}
