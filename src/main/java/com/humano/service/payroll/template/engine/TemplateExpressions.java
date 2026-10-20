package com.humano.service.payroll.template.engine;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

/**
 * Parses, checks and evaluates template expressions (SpEL, sandboxed like tenant formulas).
 * <p>
 * Templates are shipped code, but the rules still keep money exact and failures loud:
 * <ul>
 *   <li>No decimal literals — SpEL would read {@code 0.0878} as a binary double. Every non-integer
 *   number lives in the template's parameters or tables (parsed to BigDecimal).</li>
 *   <li>No {@code /} — SpEL's BigDecimal division picks its own scale and rounding; use
 *   {@code #divide(a, b, scale, mode)}.</li>
 *   <li>No assignment, type references ({@code T(...)}), bean references ({@code @x}) or {@code new}.</li>
 *   <li>Every {@code #variable} must be one the engine binds — checked by {@link #variablesIn} at
 *   load time, so a typo can't silently evaluate to null.</li>
 * </ul>
 */
public final class TemplateExpressions {

    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:[^']|'')*'");
    private static final Pattern DECIMAL_LITERAL = Pattern.compile("(?<![\\w.])\\d+\\.\\d+");
    private static final Pattern FORBIDDEN = Pattern.compile("T\\s*\\(|@|\\bnew\\b|/|\\bdiv\\b|(?<![=!<>])=(?!=)");
    private static final Pattern VARIABLE = Pattern.compile("#([A-Za-z_][A-Za-z0-9_]*)");
    private static final int MAX_LENGTH = 2000;

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final Map<String, Expression> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Method> FUNCTIONS = TemplateFunctions.registry();

    private TemplateExpressions() {}

    public static Set<String> functionNames() {
        return FUNCTIONS.keySet();
    }

    /** Returns the problems with {@code expr} (empty when it's acceptable); does not check variable names. */
    public static List<String> check(String expr) {
        List<String> problems = new ArrayList<>();
        if (expr == null || expr.isBlank()) {
            problems.add("expression is empty");
            return problems;
        }
        if (expr.length() > MAX_LENGTH) {
            problems.add("expression longer than " + MAX_LENGTH + " characters");
        }
        String code = STRING_LITERAL.matcher(expr).replaceAll("''");
        Matcher decimal = DECIMAL_LITERAL.matcher(code);
        if (decimal.find()) {
            problems.add("decimal literal " + decimal.group() + " — put non-integer numbers in parameters or tables");
        }
        Matcher forbidden = FORBIDDEN.matcher(code);
        if (forbidden.find()) {
            problems.add(
                "forbidden token '" + forbidden.group().trim() + "' (no division operator, assignment, T(), @ or new; use #divide)"
            );
        }
        try {
            PARSER.parseExpression(expr);
        } catch (RuntimeException e) {
            problems.add("does not parse: " + e.getMessage());
        }
        return problems;
    }

    /** The {@code #names} an expression references (functions included). */
    public static Set<String> variablesIn(String expr) {
        String code = STRING_LITERAL.matcher(expr).replaceAll("''");
        Set<String> names = new java.util.LinkedHashSet<>();
        Matcher m = VARIABLE.matcher(code);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    /** Evaluates {@code expr} with {@code variables} bound, converting the result to {@code type}. */
    public static <T> T evaluate(String expr, Map<String, Object> variables, Class<T> type) {
        Expression expression = CACHE.computeIfAbsent(expr, PARSER::parseExpression);
        // Instance methods are enabled so expressions can call the engine's table and year-to-date
        // helpers (#table.lookup(...), #ytd.base(...)); the read-only data binding still blocks
        // type references, constructors and property writes.
        SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().withInstanceMethods().build();
        FUNCTIONS.forEach(context::setVariable);
        variables.forEach(context::setVariable);
        return expression.getValue(context, type);
    }

    static BigDecimal decimal(String expr, Map<String, Object> variables, String what) {
        BigDecimal value = evaluate(expr, variables, BigDecimal.class);
        if (value == null) {
            throw new TemplateCalculationException(what + " evaluated to nothing: " + expr);
        }
        return value;
    }

    static boolean bool(String expr, Map<String, Object> variables, String what) {
        Boolean value = evaluate(expr, variables, Boolean.class);
        if (value == null) {
            throw new TemplateCalculationException(what + " evaluated to nothing: " + expr);
        }
        return value;
    }
}
