package com.humano.service.payroll;

import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * A simple formula evaluation engine using Spring Expression Language (SpEL).
 * This allows dynamic evaluation of payroll formulas defined in PayRule entities.
 * Example usage:
 * Map<String, Object> variables = new HashMap<>();
 * variables.put("grossSalary", 2000.0);
 * variables.put("allowances", 500.0);
 * variables.put("deductions", 300.0);
 *
 * String formula = "(grossSalary + allowances - deductions) * 0.15";
 *
 * double tax = payrollFormulaEngine.evaluateFormula(formula, variables);
 * System.out.println("Calculated Tax: " + tax);
 */
/**
 * @author halimzaaim
 */
@Service
public class PayrollFormulaEngine {

    public <T extends Number> T evaluateFormula(String formula, Map<String, Object> variables, Class<T> resultType) {
        ExpressionParser parser = new SpelExpressionParser();
        Expression expression = parser.parseExpression(formula);

        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariables(variables);

        return expression.getValue(context, resultType);
    }
}
