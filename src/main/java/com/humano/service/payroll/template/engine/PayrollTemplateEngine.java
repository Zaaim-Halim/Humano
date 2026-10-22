package com.humano.service.payroll.template.engine;

import com.humano.domain.enumeration.payroll.DeductionType;
import com.humano.domain.enumeration.payroll.PayComponentCode;
import com.humano.service.payroll.template.extension.PayrollTemplateExtension;
import com.humano.service.payroll.template.extension.PayrollTemplateExtensionRegistry;
import com.humano.service.payroll.template.model.DatedValue;
import com.humano.service.payroll.template.model.LineSide;
import com.humano.service.payroll.template.model.PayrollTemplate;
import com.humano.service.payroll.template.model.Step;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Runs a {@link PayrollTemplate} for one employee and pay period. Pure: everything it needs is in
 * the template and the {@link TemplateInput} (year-to-date sums included), so the same inputs always
 * give the same lines.
 * <p>
 * Variables every expression can use:
 * <ul>
 *   <li>Period: {@code #periodStart}, {@code #periodEnd}, {@code #frequency}, {@code #periodsPerYear},
 *   {@code #isCalendarMonth}.</li>
 *   <li>Earnings: {@code #grossEarnings}, {@code #earningsByComponent['BONUS']} (by pay-component code; 0 for a
 *   code with none), {@code #socialEarnings}, {@code #taxableEarnings},
 *   {@code #reductions} (unpaid leave), {@code #preTaxDeductions} (tenant pre-tax deductions; a template
 *   that doesn't model them should REQUIRE them to be zero), {@code #preTaxDeductionsByType['RETIREMENT']}
 *   (the same by deduction type; 0 for a type with none).</li>
 *   <li>Employee: {@code #fullPeriod}, {@code #fte}, {@code #birthDate}, {@code #birthYear}, {@code #ageAtPeriodEnd}
 *   (an expression using any of them fails when the birth date is unknown), {@code #employmentStartDate}
 *   (fails when unknown), {@code #employedThroughout} (employed from the first to the last day of the
 *   period), {@code #employee['key']}.</li>
 *   <li>Employer: {@code #employer['key']}.</li>
 *   <li>Data: {@code #p['parameter']} (the value in force for the period), {@code #table.lookup(...)},
 *   {@code #table.bracketTax(...)}, {@code #ytd.base(step, side)}, {@code #ytd.amount(step, side)}.</li>
 *   <li>Running totals: {@code #employeeContributions}, {@code #employerContributions}, {@code #taxes}.</li>
 *   <li>Earlier steps: see {@link Step}.</li>
 * </ul>
 */
@Component
public class PayrollTemplateEngine {

    /** Names the engine binds; template step ids may not reuse them. */
    public static final Set<String> ENGINE_VARIABLES = Set.of(
        "periodStart",
        "periodEnd",
        "paymentDate",
        "taxYear",
        "taxYearStartDate",
        "taxMonth",
        "taxWeek",
        "frequency",
        "periodsPerYear",
        "isCalendarMonth",
        "grossEarnings",
        "earningsByComponent",
        "socialEarnings",
        "taxableEarnings",
        "reductions",
        "preTaxDeductions",
        "preTaxDeductionsByType",
        "fullPeriod",
        "fte",
        "birthDate",
        "birthYear",
        "ageAtPeriodEnd",
        "employmentStartDate",
        "employedThroughout",
        "currency",
        "employee",
        "employer",
        "p",
        "table",
        "ytd",
        "employeeContributions",
        "employerContributions",
        "taxes"
    );

    private static final PayrollTemplate.Rounding DEFAULT_ROUNDING = new PayrollTemplate.Rounding(2, "HALF_UP");

    private final PayrollTemplateExtensionRegistry extensions;

    public PayrollTemplateEngine(PayrollTemplateExtensionRegistry extensions) {
        this.extensions = extensions;
    }

    public List<TemplateLine> calculate(PayrollTemplate template, TemplateInput in) {
        LocalDate paymentDate = in.paymentDate() != null ? in.paymentDate() : in.periodEnd();
        boolean byPaymentDate = template.effectiveDateBasis() == PayrollTemplate.DateBasis.PAYMENT_DATE;
        // The dates that must fall within the template's validity and select its dated values.
        LocalDate valuesFrom = byPaymentDate ? paymentDate : in.periodStart();
        LocalDate valuesTo = byPaymentDate ? paymentDate : in.periodEnd();
        checkApplicable(template, in, valuesFrom, valuesTo);
        LocalDate taxYearStart = taxYearStart(template, valuesFrom);
        Map<String, Object> vars = new HashMap<>();
        vars.put("periodStart", in.periodStart());
        vars.put("periodEnd", in.periodEnd());
        vars.put("paymentDate", paymentDate);
        vars.put("taxYear", taxYearStart.getYear());
        vars.put("taxYearStartDate", taxYearStart);
        // Tax month / week of the basis date, counted from the tax-year start (UK: month 1 = 6 April-5 May).
        vars.put("taxMonth", (int) ChronoUnit.MONTHS.between(taxYearStart, valuesFrom) + 1);
        vars.put("taxWeek", (int) (ChronoUnit.DAYS.between(taxYearStart, valuesFrom) / 7) + 1);
        vars.put("frequency", in.frequency().name());
        vars.put("periodsPerYear", in.frequency().periodsPerYear());
        vars.put(
            "isCalendarMonth",
            in.periodStart().getDayOfMonth() == 1 &&
            in.periodEnd().equals(in.periodStart().withDayOfMonth(in.periodStart().lengthOfMonth()))
        );
        vars.put("grossEarnings", nz(in.grossEarnings()));
        Map<String, BigDecimal> byComponent = new HashMap<>();
        for (PayComponentCode code : PayComponentCode.values()) {
            BigDecimal amount = in.earningsByComponent() == null ? null : in.earningsByComponent().get(code.name());
            byComponent.put(code.name(), nz(amount));
        }
        vars.put("earningsByComponent", new StrictMap("pay component", byComponent));
        vars.put("socialEarnings", nz(in.socialEarnings()));
        vars.put("taxableEarnings", nz(in.taxableEarnings()));
        vars.put("reductions", nz(in.reductions()));
        vars.put("preTaxDeductions", nz(in.preTaxDeductions()));
        Map<String, BigDecimal> preTaxByType = new HashMap<>();
        for (DeductionType type : DeductionType.values()) {
            BigDecimal amount = in.preTaxDeductionsByType() == null ? null : in.preTaxDeductionsByType().get(type.name());
            preTaxByType.put(type.name(), nz(amount));
        }
        vars.put("preTaxDeductionsByType", new StrictMap("pre-tax deduction type", preTaxByType));
        vars.put("fullPeriod", in.fullPeriod());
        vars.put("fte", in.fte() != null ? in.fte() : BigDecimal.ONE);
        vars.put("birthDate", in.birthDate());
        vars.put("birthYear", in.birthDate() != null ? in.birthDate().getYear() : null);
        vars.put("ageAtPeriodEnd", in.birthDate() != null ? Period.between(in.birthDate(), in.periodEnd()).getYears() : null);
        vars.put("employmentStartDate", in.employmentStartDate());
        vars.put("employedThroughout", in.employedThroughout());
        vars.put("currency", in.currency());
        vars.put("employer", TemplateInputs.coerce(template.inputs().employer(), in.employer(), "employer setting", true));
        // Checked apart from coerce so the run can say which template's details to fill in, and link to them.
        List<String> missingDetails = TemplateInputs.missingRequired(template.inputs().employee(), in.employee());
        if (!missingDetails.isEmpty()) {
            throw new TemplateProfileIncompleteException(template.id(), missingDetails);
        }
        vars.put("employee", TemplateInputs.coerce(template.inputs().employee(), in.employee(), "employee attribute", true));
        vars.put("p", new StrictMap("parameter", resolveParameters(template, valuesFrom, valuesTo)));
        vars.put("table", new TableAccess(template.id(), template.tables(), valuesFrom, valuesTo));
        vars.put("ytd", new YtdAccess(in.ytd()));
        vars.put("employeeContributions", BigDecimal.ZERO);
        vars.put("employerContributions", BigDecimal.ZERO);
        vars.put("taxes", BigDecimal.ZERO);

        List<TemplateLine> lines = new ArrayList<>();
        for (Step step : template.steps()) {
            try {
                runStep(template, step, vars, lines);
            } catch (TemplateCalculationException e) {
                throw e;
            } catch (RuntimeException e) {
                // SpEL and function errors: name the template and step so the payroll error is actionable.
                throw new TemplateCalculationException(
                    template.ref() +
                    " step '" +
                    step.id() +
                    "' failed: " +
                    (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
                );
            }
        }
        return lines;
    }

    private void runStep(PayrollTemplate template, Step step, Map<String, Object> vars, List<TemplateLine> lines) {
        boolean active = step.when() == null || bool(step.when(), vars, "'when' of " + step.id());
        switch (step.type()) {
            case REQUIRE -> {
                if (active && !bool(step.condition(), vars, "condition of " + step.id())) {
                    throw new TemplateCalculationException(step.message());
                }
            }
            case VALUE -> vars.put(step.id(), active ? value(step.expr(), vars, step.id()) : BigDecimal.ZERO);
            case CONTRIBUTION -> {
                BigDecimal base = active ? decimal(step.base(), vars, "base of " + step.id()) : BigDecimal.ZERO;
                // Bound before the shares so an amount-style share (e.g. bands) can read its own base.
                vars.put(step.id() + "_base", base);
                BigDecimal employee = active
                    ? share(template, step, step.employee(), LineSide.EMPLOYEE, base, vars, lines)
                    : BigDecimal.ZERO;
                BigDecimal employer = active
                    ? share(template, step, step.employer(), LineSide.EMPLOYER, base, vars, lines)
                    : BigDecimal.ZERO;
                vars.put(step.id() + "_employee", employee);
                vars.put(step.id() + "_employer", employer);
                vars.put("employeeContributions", ((BigDecimal) vars.get("employeeContributions")).add(employee));
                vars.put("employerContributions", ((BigDecimal) vars.get("employerContributions")).add(employer));
            }
            case TAX -> {
                BigDecimal base = active && step.base() != null ? decimal(step.base(), vars, "base of " + step.id()) : null;
                // Bound before rate/amount so a non-flat amount (e.g. annualised brackets) can read its own base.
                vars.put(step.id() + "_base", base != null ? base : BigDecimal.ZERO);
                BigDecimal tax = BigDecimal.ZERO;
                if (active) {
                    BigDecimal rate = step.rate() != null ? decimal(step.rate(), vars, "rate of " + step.id()) : null;
                    BigDecimal raw = rate != null ? base.multiply(rate) : decimal(step.amount(), vars, "amount of " + step.id());
                    tax = round(template, step, raw);
                    requireNonNegative(template, step, tax);
                    if (tax.signum() != 0 || step.recordZero()) {
                        lines.add(line(template, step, LineSide.TAX, base, rate, tax));
                    }
                }
                vars.put(step.id(), tax);
                vars.put("taxes", ((BigDecimal) vars.get("taxes")).add(tax));
            }
            case EXTENSION -> {
                Map<String, BigDecimal> outputs = new HashMap<>();
                PayrollTemplateExtension extension = extensions
                    .find(step.extension())
                    .orElseThrow(() ->
                        new TemplateCalculationException(
                            template.ref() + " needs extension " + step.extension() + ", which isn't installed"
                        )
                    );
                if (active) {
                    Map<String, Object> inputs = new HashMap<>();
                    step.inputs().forEach((name, expr) -> inputs.put(name, value(expr, vars, step.id() + "." + name)));
                    outputs.putAll(extension.compute(inputs));
                    if (!outputs.keySet().containsAll(extension.outputNames())) {
                        throw new TemplateCalculationException(
                            "Extension " + extension.id() + " did not return all of " + extension.outputNames()
                        );
                    }
                } else {
                    extension.outputNames().forEach(name -> outputs.put(name, BigDecimal.ZERO));
                }
                vars.put(step.id(), new StrictMap("output of " + step.id(), outputs));
            }
        }
    }

    private BigDecimal share(
        PayrollTemplate template,
        Step step,
        Step.Share share,
        LineSide side,
        BigDecimal base,
        Map<String, Object> vars,
        List<TemplateLine> lines
    ) {
        if (share == null) {
            return BigDecimal.ZERO;
        }
        String what = side.name().toLowerCase() + " share of " + step.id();
        BigDecimal rate = share.rate() != null ? decimal(share.rate(), vars, "rate of " + what) : null;
        BigDecimal amount = round(template, step, rate != null ? base.multiply(rate) : decimal(share.amount(), vars, "amount of " + what));
        requireNonNegative(template, step, amount);
        if (amount.signum() != 0 || step.recordZero()) {
            lines.add(line(template, step, side, base, rate, amount));
        }
        return amount;
    }

    private static TemplateLine line(
        PayrollTemplate template,
        Step step,
        LineSide side,
        BigDecimal base,
        BigDecimal rate,
        BigDecimal amount
    ) {
        String label = step.label() + (side == LineSide.EMPLOYEE ? " (employee)" : side == LineSide.EMPLOYER ? " (employer)" : "");
        StringBuilder explain = new StringBuilder(label).append(": ");
        if (rate != null) {
            explain
                .append(rate.movePointRight(2).stripTrailingZeros().toPlainString())
                .append("% × ")
                .append(base.toPlainString())
                .append(" = ");
        } else if (base != null) {
            explain.append("on ").append(base.toPlainString()).append(" = ");
        }
        explain.append(amount.toPlainString());
        explain.append(" [").append(template.ref()).append(", step ").append(step.id());
        if (step.source() != null) {
            explain.append(", source ").append(step.source());
        }
        explain.append(']');
        return new TemplateLine(template.ref(), step.id(), side, label, step.ledger(), base, rate, amount, explain.toString());
    }

    /** First day of the template's tax year containing {@code date}. */
    public static LocalDate taxYearStart(PayrollTemplate template, LocalDate date) {
        LocalDate start = java.time.MonthDay.parse(template.taxYearStart()).atYear(date.getYear());
        return start.isAfter(date) ? start.minusYears(1) : start;
    }

    private static void checkApplicable(PayrollTemplate template, TemplateInput in, LocalDate from, LocalDate to) {
        if (!template.payFrequencies().contains(in.frequency())) {
            throw new TemplateCalculationException(
                template.ref() + " supports " + template.payFrequencies() + " pay, not " + in.frequency()
            );
        }
        if (!template.currency().equals(in.currency())) {
            throw new TemplateCalculationException(
                template.ref() + " computes in " + template.currency() + "; this employee is paid in " + in.currency()
            );
        }
        if (!template.validity().contains(from) || !template.validity().contains(to)) {
            throw new TemplateCalculationException(
                template.ref() +
                " has data for " +
                template.validity().from() +
                "–" +
                template.validity().to() +
                ", not for " +
                from +
                (from.equals(to) ? "" : "–" + to) +
                "; a newer template version is needed"
            );
        }
    }

    private static Map<String, BigDecimal> resolveParameters(PayrollTemplate template, LocalDate start, LocalDate end) {
        Map<String, BigDecimal> resolved = new HashMap<>();
        if (template.parameters() == null) return resolved;
        template
            .parameters()
            .forEach((name, values) -> {
                DatedValue value = values
                    .stream()
                    .filter(v -> v.covers(start, end))
                    .findFirst()
                    .orElseThrow(() ->
                        new TemplateCalculationException(
                            "Parameter " +
                            name +
                            " of " +
                            template.ref() +
                            " changes during " +
                            start +
                            "–" +
                            end +
                            "; split the pay period at the change"
                        )
                    );
                resolved.put(name, value.value());
            });
        return resolved;
    }

    private static BigDecimal round(PayrollTemplate template, Step step, BigDecimal value) {
        PayrollTemplate.Rounding r = step.rounding() != null
            ? step.rounding()
            : template.rounding() != null ? template.rounding() : DEFAULT_ROUNDING;
        return value.setScale(r.scale(), RoundingMode.valueOf(r.mode()));
    }

    private static void requireNonNegative(PayrollTemplate template, Step step, BigDecimal amount) {
        if (amount.signum() < 0 && !step.allowNegative()) {
            throw new TemplateCalculationException(
                template.ref() + " step '" + step.id() + "' produced a negative amount " + amount.toPlainString()
            );
        }
    }

    private static Object value(String expr, Map<String, Object> vars, String what) {
        checkBound(expr, vars, what);
        Object value = TemplateExpressions.evaluate(expr, vars, Object.class);
        if (value == null) {
            throw new TemplateCalculationException(what + " evaluated to nothing: " + expr);
        }
        return value instanceof Integer i ? BigDecimal.valueOf(i) : value;
    }

    private static BigDecimal decimal(String expr, Map<String, Object> vars, String what) {
        checkBound(expr, vars, what);
        return TemplateExpressions.decimal(expr, vars, what);
    }

    private static boolean bool(String expr, Map<String, Object> vars, String what) {
        checkBound(expr, vars, what);
        return TemplateExpressions.bool(expr, vars, what);
    }

    /** An expression may not use a variable that has no value (e.g. #birthYear without a birth date). */
    private static void checkBound(String expr, Map<String, Object> vars, String what) {
        for (String name : TemplateExpressions.variablesIn(expr)) {
            if (TemplateExpressions.functionNames().contains(name)) continue;
            if (!vars.containsKey(name)) {
                throw new TemplateCalculationException(what + " uses #" + name + ", which is not defined at this point");
            }
            if (vars.get(name) == null) {
                throw new TemplateCalculationException(
                    what +
                    " needs #" +
                    name +
                    ", which is unknown for this employee" +
                    (name.equals("birthDate") || name.equals("birthYear") || name.equals("ageAtPeriodEnd") ? " (no birth date)" : "")
                );
            }
        }
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    /** A map whose missing keys are errors rather than nulls — used for parameters and extension outputs. */
    static final class StrictMap extends HashMap<String, BigDecimal> {

        private static final long serialVersionUID = 1L;
        private final String what;

        StrictMap(String what, Map<String, BigDecimal> values) {
            super(values);
            this.what = what;
        }

        @Override
        public BigDecimal get(Object key) {
            if (!containsKey(key)) {
                throw new TemplateCalculationException("Unknown " + what + " '" + key + "'");
            }
            return super.get(key);
        }
    }
}
