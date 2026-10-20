package com.humano.service.payroll.template.catalog;

import com.humano.domain.enumeration.CountryCode;
import com.humano.service.payroll.template.engine.PayrollTemplateEngine;
import com.humano.service.payroll.template.engine.TemplateCalculationException;
import com.humano.service.payroll.template.engine.TemplateExpressions;
import com.humano.service.payroll.template.engine.TemplateInputs;
import com.humano.service.payroll.template.extension.PayrollTemplateExtension;
import com.humano.service.payroll.template.extension.PayrollTemplateExtensionRegistry;
import com.humano.service.payroll.template.model.DatedValue;
import com.humano.service.payroll.template.model.InputField;
import com.humano.service.payroll.template.model.LineSide;
import com.humano.service.payroll.template.model.PayrollTemplate;
import com.humano.service.payroll.template.model.Step;
import com.humano.service.payroll.template.model.Table;
import com.humano.service.payroll.template.model.TemplateStatus;
import com.humano.service.payroll.template.model.TestCase;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.stereotype.Component;

/**
 * Checks a template before it is offered to tenants: everything that would otherwise show up as a
 * wrong number on a payslip is caught here instead. Returns every problem found, not just the first.
 */
@Component
public class PayrollTemplateValidator {

    private static final Pattern TEMPLATE_ID = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final Pattern NAME = Pattern.compile("^[a-z][A-Za-z0-9]*$");
    private static final Pattern SUBDIVISION = Pattern.compile("^[A-Z]{2}-[A-Z0-9]{1,3}$");
    private static final Pattern EMPLOYEE_KEY = Pattern.compile("#employee\\['([^']+)'\\]");
    private static final Pattern EMPLOYER_KEY = Pattern.compile("#employer\\['([^']+)'\\]");
    private static final Pattern PARAMETER = Pattern.compile("#p\\['([^']+)'\\]");
    private static final Pattern TABLE_CALL = Pattern.compile("#table\\.(\\w+)\\(\\s*('([^']*)')?");
    private static final Pattern YTD_CALL = Pattern.compile("#ytd\\.(\\w+)\\(\\s*'([^']*)'\\s*,\\s*'([^']*)'\\s*\\)");
    private static final Set<String> TABLE_METHODS = Set.of("lookup", "bracketTax");
    private static final Set<String> YTD_METHODS = Set.of("base", "amount");

    private final PayrollTemplateExtensionRegistry extensions;

    public PayrollTemplateValidator(PayrollTemplateExtensionRegistry extensions) {
        this.extensions = extensions;
    }

    public List<String> validate(PayrollTemplate t) {
        List<String> problems = new ArrayList<>();
        if (t.schemaVersion() != 1) problems.add("schemaVersion must be 1");
        if (t.id() == null || !TEMPLATE_ID.matcher(t.id()).matches()) problems.add("id must be lower-case words joined by '-'");
        if (blank(t.name())) problems.add("name is required");
        if (blank(t.version())) problems.add("version is required");
        if (t.status() == null) problems.add("status is required");
        checkJurisdiction(t, problems);
        if (
            t.validity() == null ||
            t.validity().from() == null ||
            t.validity().to() == null ||
            t.validity().from().isAfter(t.validity().to())
        ) {
            problems.add("validity must have from <= to");
            return problems; // coverage checks below need it
        }
        if (t.currency() == null || !t.currency().matches("^[A-Z]{3}$")) problems.add("currency must be an ISO 4217 code");
        if (t.payFrequencies() == null || t.payFrequencies().isEmpty()) problems.add("payFrequencies is required");
        try {
            MonthDay.parse(t.taxYearStart());
        } catch (RuntimeException e) {
            problems.add("taxYearStart must be --MM-DD");
        }
        checkRounding(t.rounding(), "rounding", problems);
        checkChangelog(t, problems);
        Map<String, PayrollTemplate.Source> sources = t.sources() == null ? Map.of() : t.sources();
        if (sources.isEmpty()) problems.add("sources are required: every value must be traceable");
        sources.forEach((id, s) -> {
            if (s == null || blank(s.title()) || s.url() == null || !s.url().startsWith("https://") || s.retrieved() == null) {
                problems.add("source " + id + " needs a title, an https url and a retrieved date");
            }
        });
        checkInputs(t, problems);
        checkParameters(t, sources, problems);
        checkTables(t, sources, problems);
        checkSteps(t, sources, problems);
        checkTestCases(t, problems);
        return problems;
    }

    private void checkJurisdiction(PayrollTemplate t, List<String> problems) {
        if (t.jurisdiction() == null || t.jurisdiction().country() == null) {
            problems.add("jurisdiction.country is required");
            return;
        }
        try {
            CountryCode.valueOf(t.jurisdiction().country());
        } catch (IllegalArgumentException e) {
            problems.add("jurisdiction.country " + t.jurisdiction().country() + " is not a known country code");
        }
        String sub = t.jurisdiction().subdivision();
        if (sub != null && (!SUBDIVISION.matcher(sub).matches() || !sub.startsWith(t.jurisdiction().country() + "-"))) {
            problems.add("jurisdiction.subdivision must be an ISO 3166-2 code of " + t.jurisdiction().country() + " (e.g. US-CA)");
        }
    }

    private static void checkRounding(PayrollTemplate.Rounding r, String where, List<String> problems) {
        if (r == null) return;
        if (r.scale() < 0 || r.scale() > 6) problems.add(where + ".scale must be 0–6");
        try {
            RoundingMode.valueOf(r.mode());
        } catch (RuntimeException e) {
            problems.add(where + ".mode must be a java.math.RoundingMode name");
        }
    }

    private static void checkChangelog(PayrollTemplate t, List<String> problems) {
        if (t.changelog() == null || t.changelog().isEmpty()) {
            problems.add("changelog is required");
        } else if (!t.changelog().get(0).version().equals(t.version())) {
            problems.add("the first changelog entry must describe the current version " + t.version());
        }
    }

    private static void checkInputs(PayrollTemplate t, List<String> problems) {
        if (t.inputs() == null) {
            problems.add("inputs is required (use empty lists)");
            return;
        }
        checkInputGroup(t.inputs().employer(), "inputs.employer", problems);
        checkInputGroup(t.inputs().employee(), "inputs.employee", problems);
    }

    private static void checkInputGroup(List<InputField> fields, String where, List<String> problems) {
        Set<String> seen = new HashSet<>();
        for (InputField f : fields == null ? List.<InputField>of() : fields) {
            String at = where + "." + f.key();
            if (f.key() == null || !NAME.matcher(f.key()).matches()) problems.add(where + ": key " + f.key() + " must be camelCase");
            if (!seen.add(f.key())) problems.add(at + " is declared twice");
            if (f.type() == null) problems.add(at + ".type is required");
            if (blank(f.description())) problems.add(at + ".description is required");
            if (f.type() == InputField.Type.ENUM && (f.values() == null || f.values().isEmpty())) problems.add(at + " needs values");
            if (f.pattern() != null) {
                try {
                    Pattern.compile(f.pattern());
                } catch (PatternSyntaxException e) {
                    problems.add(at + ".pattern does not compile");
                }
            }
            if (f.defaultValue() != null && f.type() != null) {
                try {
                    TemplateInputs.coerce(List.of(f), Map.of(f.key(), f.defaultValue()), at, false);
                } catch (TemplateCalculationException e) {
                    problems.add(at + ".defaultValue: " + e.getMessage());
                }
            }
        }
    }

    private static void checkParameters(PayrollTemplate t, Map<String, PayrollTemplate.Source> sources, List<String> problems) {
        if (t.parameters() == null) return;
        t
            .parameters()
            .forEach((name, values) -> {
                String at = "parameter " + name;
                if (!NAME.matcher(name).matches()) problems.add(at + ": name must be camelCase");
                if (values == null || values.isEmpty()) {
                    problems.add(at + " has no values");
                    return;
                }
                for (DatedValue v : values) {
                    if (v.value() == null) problems.add(at + " has a value without an amount");
                    if (v.source() == null || !sources.containsKey(v.source())) problems.add(at + " cites unknown source " + v.source());
                }
                checkCoverage(values, DatedValue::from, DatedValue::to, t.validity(), at, problems);
            });
    }

    private static void checkTables(PayrollTemplate t, Map<String, PayrollTemplate.Source> sources, List<String> problems) {
        if (t.tables() == null) return;
        t
            .tables()
            .forEach((name, table) -> {
                String at = "table " + name;
                if (!NAME.matcher(name).matches()) problems.add(at + ": name must be camelCase");
                if (table.kind() == null) {
                    problems.add(at + ".kind is required");
                    return;
                }
                if (table.versions() == null || table.versions().isEmpty()) {
                    problems.add(at + " has no versions");
                    return;
                }
                checkCoverage(table.versions(), Table.Version::from, Table.Version::to, t.validity(), at, problems);
                int width = table.columns() == null || table.columns().isEmpty() ? 1 : table.columns().size();
                for (Table.Version v : table.versions()) {
                    String vat = at + " version " + v.from();
                    if (v.source() == null || !sources.containsKey(v.source())) problems.add(vat + " cites unknown source " + v.source());
                    if (v.rows() == null || v.rows().isEmpty()) {
                        problems.add(vat + " has no rows");
                        continue;
                    }
                    switch (table.kind()) {
                        case EXACT -> {
                            Set<String> keys = new HashSet<>();
                            for (Table.Row r : v.rows()) {
                                if (r.key() == null || !keys.add(r.key())) problems.add(vat + ": missing or duplicate key " + r.key());
                                checkValues(r, width, vat + " key " + r.key(), problems);
                            }
                        }
                        case HIERARCHICAL -> {
                            for (Table.Row r : v.rows()) {
                                String rat = vat + " row " + (r.label() != null ? r.label() : r.codes());
                                if (r.codes() != null && (r.codes().include() == null || r.codes().include().isEmpty())) {
                                    problems.add(rat + ": codes.include is empty");
                                }
                                checkValues(r, width, rat, problems);
                            }
                        }
                        case BRACKETS -> checkBrackets(v.rows(), vat, problems);
                    }
                }
            });
    }

    /** One entry per column; null entries (columns the published table leaves empty) are allowed, an all-empty row is not. */
    private static void checkValues(Table.Row r, int width, String at, List<String> problems) {
        if (r.values() == null || r.values().size() != width || r.values().stream().allMatch(java.util.Objects::isNull)) {
            problems.add(at + " must have " + width + " value(s), at least one of them set");
        }
    }

    private static void checkBrackets(List<Table.Row> rows, String at, List<String> problems) {
        BigDecimal expectedLower = BigDecimal.ZERO;
        for (Table.Row r : rows) {
            if (r.lower() == null || r.upper() == null || r.rate() == null) {
                problems.add(at + ": each bracket needs lower, upper and rate");
                return;
            }
            if (r.lower().compareTo(expectedLower) != 0) {
                problems.add(
                    at + ": brackets must start at 0 and be contiguous (expected lower " + expectedLower + ", got " + r.lower() + ")"
                );
            }
            if (r.upper().compareTo(r.lower()) <= 0) problems.add(at + ": bracket upper must exceed lower at " + r.lower());
            if (r.rate().signum() < 0 || r.rate().compareTo(BigDecimal.ONE) > 0) problems.add(at + ": rate must be 0–1 at " + r.lower());
            expectedLower = r.upper();
        }
    }

    private void checkSteps(PayrollTemplate t, Map<String, PayrollTemplate.Source> sources, List<String> problems) {
        if (t.steps() == null || t.steps().isEmpty()) {
            problems.add("steps are required");
            return;
        }
        Set<String> defined = new HashSet<>(PayrollTemplateEngine.ENGINE_VARIABLES);
        Set<String> stepIds = new HashSet<>();
        Set<String> employeeKeys = keys(t.inputs() == null ? null : t.inputs().employee());
        Set<String> employerKeys = keys(t.inputs() == null ? null : t.inputs().employer());
        Set<String> parameters = t.parameters() == null ? Set.of() : t.parameters().keySet();
        Map<String, Table> tables = t.tables() == null ? Map.of() : t.tables();
        for (Step s : t.steps()) {
            String at = "step " + s.id();
            if (s.id() == null || !NAME.matcher(s.id()).matches()) problems.add("step id " + s.id() + " must be camelCase");
            if (PayrollTemplateEngine.ENGINE_VARIABLES.contains(s.id()) || TemplateExpressions.functionNames().contains(s.id())) {
                problems.add(at + ": id is reserved");
            }
            if (!stepIds.add(s.id())) problems.add(at + " is declared twice");
            if (s.type() == null) {
                problems.add(at + ".type is required");
                continue;
            }
            if (s.source() != null && !sources.containsKey(s.source())) problems.add(at + " cites unknown source " + s.source());
            checkRounding(s.rounding(), at + ".rounding", problems);
            // Checked in the order the engine binds names: `before` expressions run before the step's
            // own <id>_base exists; `after` expressions (shares, rate, amount) can read it.
            List<String> before = new ArrayList<>();
            List<String> after = new ArrayList<>();
            if (s.when() != null) before.add(s.when());
            switch (s.type()) {
                case REQUIRE -> {
                    require(s.condition(), at + ".condition", problems, before);
                    if (blank(s.message())) problems.add(at + ".message is required");
                }
                case VALUE -> require(s.expr(), at + ".expr", problems, before);
                case CONTRIBUTION -> {
                    requireLabelled(s, at, problems);
                    require(s.base(), at + ".base", problems, before);
                    if (s.employee() == null && s.employer() == null) problems.add(at + " needs an employee or employer share");
                    share(s.employee(), at + ".employee", problems, after);
                    share(s.employer(), at + ".employer", problems, after);
                }
                case TAX -> {
                    requireLabelled(s, at, problems);
                    if ((s.rate() == null) == (s.amount() == null)) problems.add(at + " needs exactly one of rate or amount");
                    if (s.rate() != null && s.base() == null) problems.add(at + ": a rate needs a base");
                    if (s.base() != null) before.add(s.base());
                    if (s.rate() != null) after.add(s.rate());
                    if (s.amount() != null) after.add(s.amount());
                }
                case EXTENSION -> {
                    PayrollTemplateExtension ext = s.extension() == null ? null : extensions.find(s.extension()).orElse(null);
                    if (ext == null) {
                        problems.add(at + ": extension " + s.extension() + " is not installed");
                    } else if (s.inputs() == null || !s.inputs().keySet().equals(ext.inputNames())) {
                        problems.add(at + ": inputs must be exactly " + ext.inputNames());
                    }
                    if (s.inputs() != null) before.addAll(s.inputs().values());
                }
            }
            for (String expr : before) {
                checkExpression(expr, at, defined, stepIds, employeeKeys, employerKeys, parameters, tables, problems);
            }
            if (s.type() == Step.Type.CONTRIBUTION || s.type() == Step.Type.TAX) defined.add(s.id() + "_base");
            for (String expr : after) {
                checkExpression(expr, at, defined, stepIds, employeeKeys, employerKeys, parameters, tables, problems);
            }
            // Names a step defines become usable by the steps after it.
            switch (s.type()) {
                case VALUE, TAX, EXTENSION -> defined.add(s.id());
                default -> {}
            }
            if (s.type() == Step.Type.CONTRIBUTION) {
                defined.add(s.id() + "_employee");
                defined.add(s.id() + "_employer");
            }
        }
    }

    private static void requireLabelled(Step s, String at, List<String> problems) {
        if (blank(s.label())) problems.add(at + ".label is required");
        if (s.ledger() == null) problems.add(at + ".ledger is required");
    }

    private static void require(String expr, String at, List<String> problems, List<String> expressions) {
        if (expr == null) problems.add(at + " is required");
        else expressions.add(expr);
    }

    private static void share(Step.Share share, String at, List<String> problems, List<String> expressions) {
        if (share == null) return;
        if ((share.rate() == null) == (share.amount() == null)) problems.add(at + " needs exactly one of rate or amount");
        if (share.rate() != null) expressions.add(share.rate());
        if (share.amount() != null) expressions.add(share.amount());
    }

    private static void checkExpression(
        String expr,
        String at,
        Set<String> defined,
        Set<String> stepIds,
        Set<String> employeeKeys,
        Set<String> employerKeys,
        Set<String> parameters,
        Map<String, Table> tables,
        List<String> problems
    ) {
        for (String p : TemplateExpressions.check(expr)) problems.add(at + ": " + p + " in `" + expr + "`");
        for (String name : TemplateExpressions.variablesIn(expr)) {
            if (!defined.contains(name) && !TemplateExpressions.functionNames().contains(name)) {
                problems.add(at + ": #" + name + " is not defined before this step");
            }
        }
        matchAll(EMPLOYEE_KEY, expr, k -> {
            if (!employeeKeys.contains(k)) problems.add(at + ": employee attribute '" + k + "' is not declared in inputs");
        });
        matchAll(EMPLOYER_KEY, expr, k -> {
            if (!employerKeys.contains(k)) problems.add(at + ": employer setting '" + k + "' is not declared in inputs");
        });
        matchAll(PARAMETER, expr, k -> {
            if (!parameters.contains(k)) problems.add(at + ": parameter '" + k + "' does not exist");
        });
        Matcher table = TABLE_CALL.matcher(expr);
        while (table.find()) {
            if (!TABLE_METHODS.contains(table.group(1))) problems.add(at + ": #table." + table.group(1) + " does not exist");
            else if (table.group(3) == null) problems.add(
                at + ": #table." + table.group(1) + " needs the table name as a literal first argument"
            );
            else if (!tables.containsKey(table.group(3))) problems.add(at + ": table '" + table.group(3) + "' does not exist");
            else if (table.group(1).equals("bracketTax") != (tables.get(table.group(3)).kind() == Table.Kind.BRACKETS)) {
                problems.add(
                    at +
                    ": #table." +
                    table.group(1) +
                    " can't be used on " +
                    tables.get(table.group(3)).kind() +
                    " table '" +
                    table.group(3) +
                    "'"
                );
            }
        }
        Matcher ytd = YTD_CALL.matcher(expr);
        int ytdCalls = 0;
        while (ytd.find()) {
            ytdCalls++;
            if (!YTD_METHODS.contains(ytd.group(1))) problems.add(at + ": #ytd." + ytd.group(1) + " does not exist");
            if (!stepIds.contains(ytd.group(2))) problems.add(at + ": #ytd refers to unknown step '" + ytd.group(2) + "'");
            try {
                LineSide.valueOf(ytd.group(3));
            } catch (IllegalArgumentException e) {
                problems.add(at + ": #ytd side must be EMPLOYEE, EMPLOYER or TAX");
            }
        }
        if (ytdCalls != count(expr, "#ytd.")) problems.add(at + ": #ytd calls need literal step and side arguments");
    }

    private static void checkTestCases(PayrollTemplate t, List<String> problems) {
        List<TestCase> cases = t.testCases() == null ? List.of() : t.testCases();
        if (t.status() != TemplateStatus.DRAFT && cases.isEmpty()) {
            problems.add("a non-draft template needs test cases");
        }
        Set<String> stepIds = new HashSet<>();
        if (t.steps() != null) t.steps().forEach(s -> stepIds.add(s.id()));
        for (TestCase c : cases) {
            String at = "test case '" + c.name() + "'";
            if (blank(c.name()) || blank(c.evidence())) problems.add(at + " needs a name and its evidence");
            if (c.periodStart() == null || c.periodEnd() == null || c.frequency() == null) problems.add(
                at + " needs a period and frequency"
            );
            if (c.grossEarnings() == null) problems.add(at + " needs grossEarnings");
            if ((c.expectLines() == null) == (c.expectError() == null)) problems.add(
                at + " needs exactly one of expectLines or expectError"
            );
            if (c.expectLines() != null) {
                for (TestCase.ExpectedLine l : c.expectLines()) {
                    if (!stepIds.contains(l.step()) || l.side() == null || l.amount() == null) problems.add(
                        at + ": bad expected line " + l
                    );
                }
            }
        }
    }

    private static <T> void checkCoverage(
        List<T> items,
        Function<T, LocalDate> from,
        Function<T, LocalDate> to,
        PayrollTemplate.DateRange validity,
        String at,
        List<String> problems
    ) {
        if (items.stream().anyMatch(i -> from.apply(i) == null || to.apply(i) == null || from.apply(i).isAfter(to.apply(i)))) {
            problems.add(at + ": every entry needs from <= to");
            return;
        }
        List<T> sorted = items.stream().sorted(Comparator.comparing(from)).toList();
        if (from.apply(sorted.get(0)).isAfter(validity.from())) problems.add(at + " starts after the template's validity");
        if (to.apply(sorted.get(sorted.size() - 1)).isBefore(validity.to())) problems.add(at + " ends before the template's validity");
        for (int i = 1; i < sorted.size(); i++) {
            LocalDate expected = to.apply(sorted.get(i - 1)).plusDays(1);
            if (!from.apply(sorted.get(i)).equals(expected)) {
                problems.add(
                    at + ": dates must be contiguous without overlap (expected " + expected + ", got " + from.apply(sorted.get(i)) + ")"
                );
            }
        }
    }

    private static Set<String> keys(List<InputField> fields) {
        Set<String> keys = new HashSet<>();
        if (fields != null) fields.forEach(f -> keys.add(f.key()));
        return keys;
    }

    private static void matchAll(Pattern pattern, String expr, java.util.function.Consumer<String> each) {
        Matcher m = pattern.matcher(expr);
        while (m.find()) each.accept(m.group(1));
    }

    private static int count(String text, String token) {
        int n = 0;
        for (int i = text.indexOf(token); i >= 0; i = text.indexOf(token, i + 1)) n++;
        return n;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
