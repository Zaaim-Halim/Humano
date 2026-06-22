package com.humano.dto.payroll.response;

import java.util.List;

/**
 * Describes the contract of {@code PayrollFormulaEngine} so a UI can build a guided
 * pay-rule formula editor (autocomplete, palette, length limit) driven by the real
 * engine rather than a hardcoded list that could drift.
 *
 * @param functions               callable functions with their parameter types
 * @param variables               static whitelisted variable names (e.g. {@code grossSalary})
 * @param constants               named numeric constants (e.g. {@code WORKDAYS_IN_MONTH})
 * @param dynamicVariablePattern  regex for additional accepted variable names ({@code PayComponentCode}-shaped)
 * @param maxFormulaLength        maximum accepted formula length in characters
 */
public record FormulaMetadataResponse(
    List<FunctionMeta> functions,
    List<String> variables,
    List<String> constants,
    String dynamicVariablePattern,
    int maxFormulaLength
) {
    /** One callable function and the simple type names of its parameters. */
    public record FunctionMeta(String name, List<String> parameterTypes) {}
}
