package com.humano.service.payroll;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Service;

/**
 * SpEL-backed formula engine for tenant-defined {@code PayRule} formulas (P3.6).
 *
 * <p><strong>Design intent.</strong> Payroll regulations vary by country and change
 * over time; the static DB schema (TaxBracket / TaxWithholding / PayComponent) can't
 * model every special-case computation a tenant in a new jurisdiction may need
 * (compound caps, multi-tier banded contributions, seniority bonuses tied to hire
 * date, country-specific rounding to the nearest cent / nickel / leu). This engine
 * fills the gap: a sandboxed SpEL evaluator with a curated function library that
 * lets a payroll admin express almost any country's rule as text — without us
 * touching a Java file every time a tax law changes.
 *
 * <h2>Hardening (security layers)</h2>
 *
 * Tenant formulas are <strong>untrusted input</strong> — they are stored in the
 * tenant DB and edited by HR/payroll admins. A naive
 * {@link org.springframework.expression.spel.support.StandardEvaluationContext}
 * evaluation lets {@code T(java.lang.Runtime).getRuntime().exec(...)} resolve a class
 * via reflection and execute arbitrary commands. This engine closes that door with
 * four layers, each useful on its own:
 *
 * <ol>
 *   <li><strong>Length + token rejection BEFORE parsing.</strong> Formulas longer than
 *       {@link #MAX_FORMULA_LENGTH} are rejected (an over-long expression is the cheapest
 *       way to abuse a parser/evaluator with no time budget), as are formulas containing
 *       {@code T(} (type reference) or {@code @} (bean reference) — rejected with a
 *       {@link SecurityException} at the engine entry. SpEL's parser would happily accept
 *       the latter; we never let them reach it.</li>
 *   <li><strong>{@link SimpleEvaluationContext} (read-only data binding).</strong>
 *       Used instead of {@code StandardEvaluationContext}. Disables type references,
 *       bean references, constructor calls, property writes, and reflective method
 *       resolution. Only operators, variables and registered functions work.</li>
 *   <li><strong>Variable + function whitelist.</strong> Only well-known root-variable
 *       names (or names matching the {@link #ALLOWED_DYNAMIC_NAME} {@code PayComponentCode}
 *       pattern) are forwarded into the evaluation context. The function library is a
 *       fixed, curated set of pure helpers from {@link Functions} — no I/O, no
 *       reflection, no DB.</li>
 *   <li><strong>Parsed-{@link Expression} cache.</strong> SpEL parsing isn't free; the
 *       same formula string fires once per employee × payroll run. We cache by formula
 *       text in a bounded LRU capped at {@link #CACHE_MAX_SIZE} entries — the least-recently-used
 *       entry is evicted on overflow, so a burst of one-off formulas can't drop the hot set.</li>
 * </ol>
 *
 * <h2>What a formula can reach</h2>
 *
 * <p><strong>Variables</strong> (referenced as {@code #name}; bare names work too when
 * the calling code injects them):
 *
 * <ul>
 *   <li>Per-result intermediates the calc pipeline writes: {@code grossSalary},
 *       {@code baseSalary}, {@code taxableIncome}, {@code preTaxDeductions},
 *       {@code postTaxDeductions}, {@code netPay}, {@code employerCost}.</li>
 *   <li>Per-component values (set by {@code buildCalculationContext} as each component
 *       computes): every {@code PayComponentCode}-shaped name plus its {@code _QTY} /
 *       {@code _RATE} companions, e.g. {@code BASIC}, {@code OT}, {@code BONUS},
 *       {@code OT_QTY}, {@code OT_RATE}, {@code TAX_PIT}.</li>
 *   <li>Period attributes: {@code periodStartDate}, {@code periodEndDate},
 *       {@code paymentDate}, {@code workDays}, {@code periodYear}, {@code periodMonth}.</li>
 *   <li>Employee attributes (when the calc service injects them): {@code employeeId},
 *       {@code employeeCountry}, {@code employeeBirthDate}, {@code employeeHireDate},
 *       {@code employeeAge}, {@code employeeYearsOfService}, {@code employeeMaritalStatus},
 *       {@code employeeDependents}, {@code currencyCode}.</li>
 *   <li>Numeric constants (registered as variables): {@code MONTHS_IN_YEAR=12},
 *       {@code WEEKS_IN_YEAR=52}, {@code DAYS_IN_YEAR=365}, {@code HOURS_IN_MONTH=160},
 *       {@code WORKDAYS_IN_MONTH=22}. Adjust at config time if your jurisdiction
 *       expects different defaults.</li>
 * </ul>
 *
 * <p><strong>Functions</strong> (all pure; called as {@code #funcName(args)}):
 *
 * <ul>
 *   <li>Math: {@code min, max, abs, clamp, round, roundUp, roundDown, ceil, floor,
 *       roundToIncrement, pct}.</li>
 *   <li>Logical: {@code iif(cond, ifTrue, ifFalse)} (sugar — SpEL's
 *       {@code cond ? a : b} ternary works natively).</li>
 *   <li>Threshold: {@code cap(value, ceiling)},
 *       {@code threshold(value, floor)} (returns {@code max(0, value - floor)}).</li>
 *   <li>Progressive bands: {@code band(value, bandList)} — slice-wise progressive over
 *       a list-of-lists {@code {{lo, hi, rate}, ...}}. Lets tenants embed a country's
 *       full bracket schedule inline when it doesn't belong in TaxBracket rows.</li>
 *   <li>Date: {@code yearsBetween, monthsBetween, daysBetween} between two LocalDate
 *       values.</li>
 * </ul>
 *
 * <p>SpEL's own operators (arithmetic {@code + - * / %}, comparison {@code < <= > >=
 * == !=}, logical {@code and or not}, ternary {@code ? :}, list literals {@code {a,
 * b, c}}, string literals) are all available because they're context-independent.
 *
 * <h2>Recipe examples (real-world)</h2>
 *
 * <pre>{@code
 * // Romania CAS (25% on gross, capped at 24× minimum wage — minimum wage seeded as
 * // a constant or passed as variable):
 *   #cap(#pct(#grossSalary, 25), 24 * #MINIMUM_WAGE)
 *
 * // US Social Security 6.2% on wages up to the 2025 cap of 176,100:
 *   #pct(#min(#grossSalary, 176100), 6.2)
 *
 * // UK PAYE banded 2025/26 (PA-adjusted):
 *   #band(#max(0, #grossSalary - 12570),
 *         {{0, 37700, 0.20}, {37700, 112430, 0.40}, {112430, 9999999, 0.45}})
 *
 * // Seniority bonus: 5% per year of service, capped at 30%:
 *   #pct(#baseSalary, #min(5 * #employeeYearsOfService, 30))
 *
 * // Switzerland — round to nearest 5 centimes (0.05):
 *   #roundToIncrement(#netPay, 0.05)
 *
 * // Conditional: married-with-dependent rate vs single rate:
 *   (#employeeMaritalStatus == 'MARRIED' and #employeeDependents > 0)
 *     ? #pct(#taxableIncome, 22)
 *     : #pct(#taxableIncome, 25)
 * }</pre>
 *
 * <p><strong>Acceptance (§P3.6).</strong>
 * {@code T(java.lang.Runtime).getRuntime().exec(...)} is rejected at parse time —
 * verified by {@link #isFormulaSafe} returning {@code false} and by
 * {@link #evaluateFormula} throwing {@link SecurityException} on the same input.
 */
@Service
public class PayrollFormulaEngine {

    private static final Logger log = LoggerFactory.getLogger(PayrollFormulaEngine.class);

    /**
     * Static whitelist of root variable names accepted by the engine. Covers the
     * existing pipeline state plus the broader per-employee / per-period context that
     * country-specific formulas may want to reach.
     */
    private static final Set<String> ALLOWED_VARIABLE_NAMES = Set.of(
        // Pipeline intermediates (PayrollProcessingService.buildCalculationContext)
        "employeeId",
        "baseSalary",
        "grossSalary",
        "gross",
        "taxableIncome",
        "preTaxDeductions",
        "postTaxDeductions",
        "netPay",
        "employerCost",
        // Period attributes
        "periodStartDate",
        "periodEndDate",
        "paymentDate",
        "workDays",
        "periodYear",
        "periodMonth",
        // Employee attributes (injected by the calc service when present)
        "employeeCountry",
        "employeeBirthDate",
        "employeeHireDate",
        "employeeAge",
        "employeeYearsOfService",
        "employeeMaritalStatus",
        "employeeDependents",
        "currencyCode",
        // Country-specific numeric input (set by the calc service or a future
        // country-constants service; formulas reference as e.g. #MINIMUM_WAGE)
        "MINIMUM_WAGE",
        "TAX_FREE_ALLOWANCE",
        "SOCIAL_SECURITY_CAP"
    );

    /**
     * Dynamic-name allowlist for {@code PayComponentCode}-shaped variables and their
     * {@code _QTY} / {@code _RATE} companions. Uppercase letters + digits + underscores,
     * with optional {@code _QTY} or {@code _RATE} suffix. Matches what
     * {@code buildCalculationContext} writes for each {@code PayrollInput}.
     */
    private static final Pattern ALLOWED_DYNAMIC_NAME = Pattern.compile("^[A-Z][A-Z0-9_]*(_QTY|_RATE)?$");

    /**
     * Forbidden token pattern run against the raw formula text before parsing.
     * {@code T(} (with optional whitespace) is the SpEL syntax for a type reference;
     * {@code @} introduces a bean reference. We reject either on sight — the parser
     * doesn't need to see them.
     */
    private static final Pattern FORBIDDEN_TOKENS = Pattern.compile("T\\s*\\(|@");

    /**
     * Hard cap on the parsed-expression cache. The cache is a bounded LRU (see
     * {@link #expressionCache}); reaching this size evicts the least-recently-used entry
     * rather than clearing the whole cache. Reached only on tenants with thousands of distinct
     * formula strings (formulas are reused per employee, so the cardinality is per-tenant
     * pay-rule-count, not per-employee-run).
     */
    private static final int CACHE_MAX_SIZE = 1000;

    /**
     * Maximum accepted formula length. Tenant formulas are untrusted input; an absurdly long
     * expression is the cheapest DoS vector against the SpEL parser/evaluator (which has no
     * built-in time budget). Real country rules are well under this; reject the rest before
     * parsing. Generous on purpose so legitimate banded formulas still fit.
     */
    private static final int MAX_FORMULA_LENGTH = 2000;

    /**
     * Numeric constants exposed as SpEL variables. These let formulas reference common
     * payroll periodicity ratios by name instead of magic numbers. Override at config
     * time if your jurisdiction expects different defaults (e.g. some labour codes
     * pro-rate over 21.67 workdays-per-month instead of 22).
     */
    private static final Map<String, BigDecimal> CONSTANTS = Map.of(
        "MONTHS_IN_YEAR",
        BigDecimal.valueOf(12),
        "WEEKS_IN_YEAR",
        BigDecimal.valueOf(52),
        "DAYS_IN_YEAR",
        BigDecimal.valueOf(365),
        "HOURS_IN_MONTH",
        BigDecimal.valueOf(160),
        "WORKDAYS_IN_MONTH",
        BigDecimal.valueOf(22)
    );

    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * Parsed-expression cache as a bounded LRU. Replaces a clear-the-whole-thing-on-overflow
     * policy (which could drop every hot entry at once and cause a re-parse stampede).
     * Access-ordered {@link LinkedHashMap} wrapped for thread-safety; the eldest
     * (least-recently-used) entry is evicted once the size would exceed {@link #CACHE_MAX_SIZE}.
     */
    private final Map<String, Expression> expressionCache = Collections.synchronizedMap(
        new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Expression> eldest) {
                return size() > CACHE_MAX_SIZE;
            }
        }
    );

    private final Map<String, Method> functionRegistry;

    public PayrollFormulaEngine() {
        this.functionRegistry = buildFunctionRegistry();
    }

    /**
     * Evaluates {@code formula} with {@code variables} bound in the SpEL context,
     * coerced to {@code resultType}. The four hardening layers documented at the
     * class level all apply, plus the curated function and constant registries are
     * registered so the formula can call {@code #min(...)} / {@code #band(...)} /
     * {@code #pct(...)} / etc. and reference {@code #WORKDAYS_IN_MONTH} etc.
     *
     * @throws IllegalArgumentException when {@code formula} is null/blank or exceeds
     *         {@link #MAX_FORMULA_LENGTH} characters.
     * @throws SecurityException when {@code formula} contains forbidden tokens
     *         ({@code T(...)} or {@code @bean}).
     * @throws org.springframework.expression.spel.SpelEvaluationException on runtime
     *         evaluation failure (e.g. division by zero, missing variable referenced
     *         as a property).
     */
    public <T extends Number> T evaluateFormula(String formula, Map<String, Object> variables, Class<T> resultType) {
        if (formula == null || formula.isBlank()) {
            throw new IllegalArgumentException("Formula must not be blank");
        }
        // Length guard before parsing — checked explicitly here so the caller gets an accurate
        // message rather than the generic forbidden-tokens one from isFormulaSafe.
        if (formula.length() > MAX_FORMULA_LENGTH) {
            log.warn("Rejected formula of length {} (max {})", formula.length(), MAX_FORMULA_LENGTH);
            throw new IllegalArgumentException("Formula exceeds maximum length of " + MAX_FORMULA_LENGTH + " characters");
        }
        if (!isFormulaSafe(formula)) {
            log.warn("Rejected formula containing forbidden tokens (T(...) or @bean): {}", formula);
            throw new SecurityException("Formula contains forbidden tokens (T(...) or @bean references)");
        }
        Expression expression = expressionCache.computeIfAbsent(formula, parser::parseExpression);
        SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
        functionRegistry.forEach(context::setVariable);
        CONSTANTS.forEach(context::setVariable);
        filterToAllowed(variables).forEach(context::setVariable);
        return expression.getValue(context, resultType);
    }

    /**
     * Returns {@code true} when {@code formula} does NOT contain any forbidden tokens.
     * Use this for early validation when persisting a {@code PayRule} so the tenant
     * gets immediate feedback instead of discovering the rejection at calc time.
     */
    public boolean isFormulaSafe(String formula) {
        if (formula == null || formula.isBlank() || formula.length() > MAX_FORMULA_LENGTH) return false;
        return !FORBIDDEN_TOKENS.matcher(formula).find();
    }

    /** Returns the set of function names a formula may call (for diagnostics / UI hints). */
    public Set<String> registeredFunctionNames() {
        return Collections.unmodifiableSet(functionRegistry.keySet());
    }

    /** Returns the set of variable names the engine will accept (whitelist + dynamic-pattern hint). */
    public Set<String> allowedVariableNames() {
        return ALLOWED_VARIABLE_NAMES;
    }

    /**
     * Returns a copy of {@code variables} containing only entries whose key is in the
     * static whitelist OR matches the {@link #ALLOWED_DYNAMIC_NAME} pattern. Anything
     * else is silently dropped (logged at DEBUG so abuse is observable in dev).
     *
     * <p>Function names from {@link #functionRegistry} and constant names from
     * {@link #CONSTANTS} are <strong>also</strong> reserved — a caller-supplied
     * variable that collides with either is dropped to prevent a tenant from shadowing
     * a function (e.g. binding {@code #min} to a Number, breaking subsequent
     * {@code #min(a, b)} calls).
     */
    private Map<String, Object> filterToAllowed(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) return Map.of();
        Map<String, Object> filtered = new HashMap<>(variables.size());
        for (Map.Entry<String, Object> e : variables.entrySet()) {
            String name = e.getKey();
            if (functionRegistry.containsKey(name) || CONSTANTS.containsKey(name)) {
                log.debug("PayrollFormulaEngine: caller-supplied '{}' shadows a reserved name; dropping", name);
                continue;
            }
            if (ALLOWED_VARIABLE_NAMES.contains(name) || ALLOWED_DYNAMIC_NAME.matcher(name).matches()) {
                filtered.put(name, e.getValue());
            } else {
                log.debug("PayrollFormulaEngine: dropping non-whitelisted variable '{}'", name);
            }
        }
        return filtered;
    }

    /** Reflectively wires the {@link Functions} pure-helper class into a name→Method map. */
    private static Map<String, Method> buildFunctionRegistry() {
        Map<String, Method> m = new HashMap<>();
        try {
            // Math
            m.put("min", Functions.class.getMethod("min", BigDecimal.class, BigDecimal.class));
            m.put("max", Functions.class.getMethod("max", BigDecimal.class, BigDecimal.class));
            m.put("abs", Functions.class.getMethod("abs", BigDecimal.class));
            m.put("clamp", Functions.class.getMethod("clamp", BigDecimal.class, BigDecimal.class, BigDecimal.class));
            m.put("round", Functions.class.getMethod("round", BigDecimal.class, int.class));
            m.put("roundUp", Functions.class.getMethod("roundUp", BigDecimal.class, int.class));
            m.put("roundDown", Functions.class.getMethod("roundDown", BigDecimal.class, int.class));
            m.put("ceil", Functions.class.getMethod("ceil", BigDecimal.class));
            m.put("floor", Functions.class.getMethod("floor", BigDecimal.class));
            m.put("roundToIncrement", Functions.class.getMethod("roundToIncrement", BigDecimal.class, BigDecimal.class));
            m.put("pct", Functions.class.getMethod("pct", BigDecimal.class, BigDecimal.class));
            // Logical
            m.put("iif", Functions.class.getMethod("iif", boolean.class, BigDecimal.class, BigDecimal.class));
            // Threshold helpers
            m.put("cap", Functions.class.getMethod("cap", BigDecimal.class, BigDecimal.class));
            m.put("threshold", Functions.class.getMethod("threshold", BigDecimal.class, BigDecimal.class));
            // Progressive band (in-formula bracket table)
            m.put("band", Functions.class.getMethod("band", BigDecimal.class, List.class));
            // Date helpers (LocalDate args; ChronoUnit math)
            m.put("yearsBetween", Functions.class.getMethod("yearsBetween", LocalDate.class, LocalDate.class));
            m.put("monthsBetween", Functions.class.getMethod("monthsBetween", LocalDate.class, LocalDate.class));
            m.put("daysBetween", Functions.class.getMethod("daysBetween", LocalDate.class, LocalDate.class));
            return Collections.unmodifiableMap(m);
        } catch (NoSuchMethodException e) {
            // Shouldn't happen — methods are on the same compilation unit. Fail loud if
            // the registry can't be built; bad-state worse than wrong-state.
            throw new IllegalStateException("Failed to wire PayrollFormulaEngine functions", e);
        }
    }

    /**
     * Pure, side-effect-free helpers exposed to formulas via SpEL function syntax.
     * Every method here MUST be deterministic and free of I/O — no DB, no clock, no
     * network. Tenant formulas reach these by name; anything that escapes the rules
     * opens the same door {@link SimpleEvaluationContext} closes.
     *
     * <p>Returns are {@link BigDecimal} (or {@code int} for date arithmetic). Null
     * inputs are coerced to {@link BigDecimal#ZERO} so a formula referencing an
     * unbound variable evaluates to zero instead of NPE-ing the whole payroll run.
     */
    public static final class Functions {

        private static final int DEFAULT_SCALE = 2;
        private static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;

        private Functions() {}

        // ----- Math primitives -----

        public static BigDecimal min(BigDecimal a, BigDecimal b) {
            if (a == null) return b == null ? BigDecimal.ZERO : b;
            if (b == null) return a;
            return a.min(b);
        }

        public static BigDecimal max(BigDecimal a, BigDecimal b) {
            if (a == null) return b == null ? BigDecimal.ZERO : b;
            if (b == null) return a;
            return a.max(b);
        }

        public static BigDecimal abs(BigDecimal a) {
            return a == null ? BigDecimal.ZERO : a.abs();
        }

        public static BigDecimal clamp(BigDecimal v, BigDecimal lo, BigDecimal hi) {
            return min(max(v, lo), hi);
        }

        /** Round to {@code scale} decimals, HALF_UP (banker's choice for payroll). */
        public static BigDecimal round(BigDecimal v, int scale) {
            return v == null ? BigDecimal.ZERO : v.setScale(scale, DEFAULT_ROUNDING);
        }

        /** Round always-up (toward positive infinity) to {@code scale} decimals. */
        public static BigDecimal roundUp(BigDecimal v, int scale) {
            return v == null ? BigDecimal.ZERO : v.setScale(scale, RoundingMode.UP);
        }

        /** Round always-down (toward zero) to {@code scale} decimals. */
        public static BigDecimal roundDown(BigDecimal v, int scale) {
            return v == null ? BigDecimal.ZERO : v.setScale(scale, RoundingMode.DOWN);
        }

        /** Ceiling: round to integer toward positive infinity. */
        public static BigDecimal ceil(BigDecimal v) {
            return v == null ? BigDecimal.ZERO : v.setScale(0, RoundingMode.CEILING);
        }

        /** Floor: round to integer toward negative infinity. */
        public static BigDecimal floor(BigDecimal v) {
            return v == null ? BigDecimal.ZERO : v.setScale(0, RoundingMode.FLOOR);
        }

        /**
         * Round {@code v} to the nearest multiple of {@code increment}. Useful for
         * Switzerland-style nearest-5-centime rounding ({@code increment = 0.05}) or
         * Japan/Korea nearest-yen / won ({@code increment = 1}).
         */
        public static BigDecimal roundToIncrement(BigDecimal v, BigDecimal increment) {
            if (v == null || increment == null || increment.signum() == 0) return v == null ? BigDecimal.ZERO : v;
            BigDecimal divided = v.divide(increment, 0, DEFAULT_ROUNDING);
            return divided.multiply(increment).setScale(DEFAULT_SCALE, DEFAULT_ROUNDING);
        }

        /** {@code v × percent / 100} at {@link #DEFAULT_SCALE}/{@link #DEFAULT_ROUNDING}. */
        public static BigDecimal pct(BigDecimal v, BigDecimal percent) {
            if (v == null || percent == null) return BigDecimal.ZERO;
            return v.multiply(percent).divide(BigDecimal.valueOf(100), DEFAULT_SCALE, DEFAULT_ROUNDING);
        }

        // ----- Logical -----

        /**
         * Sugar for SpEL's ternary. {@code #iif(#x > 0, #a, #b)} reads the same as
         * {@code (#x > 0) ? #a : #b}; keep it because some payroll admins find the
         * function-style more readable than infix operators.
         */
        public static BigDecimal iif(boolean cond, BigDecimal ifTrue, BigDecimal ifFalse) {
            return cond ? ifTrue : ifFalse;
        }

        // ----- Threshold helpers -----

        /** {@code min(v, ceiling)} — caps {@code v} from above. */
        public static BigDecimal cap(BigDecimal v, BigDecimal ceiling) {
            if (v == null) return BigDecimal.ZERO;
            if (ceiling == null) return v;
            return v.compareTo(ceiling) > 0 ? ceiling : v;
        }

        /**
         * Slice-off floor: returns {@code max(0, v - floor)}. Useful for "tax-free
         * allowance" computations: {@code #threshold(#grossSalary, #TAX_FREE_ALLOWANCE)}
         * yields the taxable portion above the allowance.
         */
        public static BigDecimal threshold(BigDecimal v, BigDecimal floorValue) {
            if (v == null) return BigDecimal.ZERO;
            if (floorValue == null) return v;
            BigDecimal sub = v.subtract(floorValue);
            return sub.signum() < 0 ? BigDecimal.ZERO : sub;
        }

        // ----- Progressive bracket (in-formula bracket table) -----

        /**
         * Slice-wise progressive across {@code bands}. Each band is a 3-element list
         * {@code [lo, hi, rate]}; bands MUST be sorted by {@code lo} ascending and be
         * contiguous (each band's {@code lo} equals the previous band's {@code hi}).
         * Uses the same algorithm as
         * {@link TaxCalculationService#calculateProgressiveTax} but with bands inlined
         * in the formula text — convenient for one-off country rules that don't
         * justify a DB row per bracket.
         *
         * <p>Returns {@link BigDecimal#ZERO} for null/non-positive {@code value} or
         * empty {@code bands}.
         */
        @SuppressWarnings("unchecked")
        public static BigDecimal band(BigDecimal value, List<?> bands) {
            if (value == null || bands == null || bands.isEmpty() || value.signum() <= 0) {
                return BigDecimal.ZERO;
            }
            BigDecimal remaining = value;
            BigDecimal tax = BigDecimal.ZERO;
            for (Object bandObj : bands) {
                if (!(bandObj instanceof List<?>)) continue;
                List<Object> band = (List<Object>) bandObj;
                if (band.size() < 3) continue;
                BigDecimal lo = asBig(band.get(0));
                BigDecimal hi = asBig(band.get(1));
                BigDecimal rate = asBig(band.get(2));
                if (remaining.signum() <= 0) break;
                BigDecimal width = hi.subtract(lo);
                if (width.signum() <= 0) continue;
                BigDecimal slice = remaining.min(width);
                tax = tax.add(slice.multiply(rate));
                remaining = remaining.subtract(slice);
            }
            return tax.setScale(DEFAULT_SCALE, DEFAULT_ROUNDING);
        }

        // ----- Date helpers -----

        public static int yearsBetween(LocalDate a, LocalDate b) {
            if (a == null || b == null) return 0;
            return (int) ChronoUnit.YEARS.between(a, b);
        }

        public static int monthsBetween(LocalDate a, LocalDate b) {
            if (a == null || b == null) return 0;
            return (int) ChronoUnit.MONTHS.between(a, b);
        }

        public static int daysBetween(LocalDate a, LocalDate b) {
            if (a == null || b == null) return 0;
            return (int) ChronoUnit.DAYS.between(a, b);
        }

        /** Best-effort coercion of {@link Number}/{@link String} into {@link BigDecimal}. */
        private static BigDecimal asBig(Object o) {
            if (o == null) return BigDecimal.ZERO;
            if (o instanceof BigDecimal b) return b;
            if (o instanceof Long l) return BigDecimal.valueOf(l);
            if (o instanceof Integer i) return BigDecimal.valueOf(i);
            if (o instanceof Double d) return BigDecimal.valueOf(d);
            if (o instanceof Number n) return new BigDecimal(n.toString());
            return new BigDecimal(o.toString());
        }
    }
}
