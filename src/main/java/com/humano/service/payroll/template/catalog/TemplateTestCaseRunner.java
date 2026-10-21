package com.humano.service.payroll.template.catalog;

import com.humano.service.payroll.template.engine.PayrollTemplateEngine;
import com.humano.service.payroll.template.engine.TemplateCalculationException;
import com.humano.service.payroll.template.engine.TemplateInput;
import com.humano.service.payroll.template.engine.TemplateLine;
import com.humano.service.payroll.template.model.PayrollTemplate;
import com.humano.service.payroll.template.model.TestCase;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Runs a template's own {@link TestCase}s through the engine and reports every mismatch. The catalog
 * runs this when it loads, so a template only reaches tenants if it reproduces its worked examples on
 * the engine actually deployed; CI runs the same check.
 */
public final class TemplateTestCaseRunner {

    private TemplateTestCaseRunner() {}

    public static List<String> run(PayrollTemplate template, PayrollTemplateEngine engine) {
        List<String> failures = new ArrayList<>();
        for (TestCase c : template.testCases() == null ? List.<TestCase>of() : template.testCases()) {
            String at = "test case '" + c.name() + "'";
            List<TemplateLine> lines;
            try {
                lines = engine.calculate(template, input(template, c));
            } catch (TemplateCalculationException e) {
                if (c.expectError() == null) {
                    failures.add(at + " failed: " + e.getMessage());
                } else if (!e.getMessage().contains(c.expectError())) {
                    failures.add(at + " raised '" + e.getMessage() + "', expected an error containing '" + c.expectError() + "'");
                }
                continue;
            }
            if (c.expectError() != null) {
                failures.add(at + " expected an error containing '" + c.expectError() + "' but calculated " + describe(lines));
                continue;
            }
            // Compare numerically ("151.3" in a test file equals 151.30) on (step, side) keys.
            List<String> actual = lines.stream().map(l -> key(l.step(), l.side(), l.amount())).sorted().toList();
            List<String> expected = c.expectLines().stream().map(l -> key(l.step(), l.side(), l.amount())).sorted().toList();
            if (!actual.equals(expected)) {
                failures.add(at + ": expected " + expected + " but got " + actual);
            }
        }
        return failures;
    }

    private static TemplateInput input(PayrollTemplate template, TestCase c) {
        return new TemplateInput(
            c.periodStart(),
            c.periodEnd(),
            c.paymentDate() != null ? c.paymentDate() : c.periodEnd(),
            c.frequency(),
            template.currency(),
            c.grossEarnings(),
            c.earningsByComponent() != null ? c.earningsByComponent() : Map.of("BASIC", c.grossEarnings()),
            c.socialEarnings() != null ? c.socialEarnings() : c.grossEarnings(),
            c.taxableEarnings() != null ? c.taxableEarnings() : c.grossEarnings(),
            c.reductions() != null ? c.reductions() : BigDecimal.ZERO,
            c.preTaxDeductions() != null
                ? c.preTaxDeductions()
                : c.preTaxDeductionsByType() != null
                    ? c.preTaxDeductionsByType().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    : BigDecimal.ZERO,
            c.preTaxDeductionsByType() != null ? c.preTaxDeductionsByType() : Map.of(),
            c.fullPeriod() == null || c.fullPeriod(),
            BigDecimal.ONE,
            c.birthDate(),
            c.employmentStartDate() != null ? c.employmentStartDate() : c.periodStart(),
            c.employer() != null ? c.employer() : Map.of(),
            c.employee() != null ? c.employee() : Map.of(),
            c.ytd() != null ? c.ytd() : Map.of()
        );
    }

    private static String key(String step, Object side, BigDecimal amount) {
        return step + "/" + side + "=" + amount.stripTrailingZeros().toPlainString();
    }

    private static String describe(List<TemplateLine> lines) {
        return lines
            .stream()
            .map(l -> l.step() + "/" + l.side() + "=" + l.amount().toPlainString())
            .collect(Collectors.joining(", ", "[", "]"));
    }
}
