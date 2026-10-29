package com.humano.service.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.humano.repository.payroll.PayComponentRepository;
import com.humano.repository.payroll.PayRuleRepository;
import com.humano.service.errors.BusinessRuleViolationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * The pay rule editor's check: a formula is tried with sample values for every variable it names, so
 * one the payroll run provides (an input's hours, the payroll settings, year-to-date totals) is accepted
 * and a misspelled one is not.
 */
class PayRuleFormulaValidationTest {

    private final PayComponentService service = new PayComponentService(
        mock(PayComponentRepository.class),
        mock(PayRuleRepository.class),
        new PayrollFormulaEngine()
    );

    @Test
    void overtimeHoursArePricedWithThePayrollSettingsDefaults() {
        // 10 sample hours × 5000 / 160 hours × 1.5
        assertThat(
            (BigDecimal) service.validateFormula("#OT_QTY * #baseSalary / #standardMonthlyHours * #overtimeMultiplier").get("testResult")
        ).isEqualByComparingTo("468.75");
    }

    @Test
    void yearToDateTotalsAndDatesCanBeUsed() {
        assertThat(
            service.validateFormula("#cap(#pct(#grossSalary, 6.2), #threshold(#SOCIAL_SECURITY_CAP, #YTD_SOCIAL_SECURITY))")
        ).containsEntry("valid", true);
        assertThat(
            service.validateFormula("#employeeCountry == 'NL' ? #yearsBetween(#employeeHireDate, #periodEndDate) * 10 : 0")
        ).containsEntry("valid", true);
    }

    @Test
    void aMisspelledVariableIsRefused() {
        assertThatThrownBy(() -> service.validateFormula("#baseSalry * 2")).isInstanceOf(BusinessRuleViolationException.class);
    }
}
