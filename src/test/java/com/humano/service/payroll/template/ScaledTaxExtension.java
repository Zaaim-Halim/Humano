package com.humano.service.payroll.template;

import com.humano.service.payroll.template.extension.PayrollTemplateExtension;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/** Test extension for the fixture templates: {@code tax = amount × factor}. */
class ScaledTaxExtension implements PayrollTemplateExtension {

    @Override
    public String id() {
        return "test.scaled-tax-2030";
    }

    @Override
    public Set<String> inputNames() {
        return Set.of("amount", "factor");
    }

    @Override
    public Set<String> outputNames() {
        return Set.of("tax");
    }

    @Override
    public Map<String, BigDecimal> compute(Map<String, Object> inputs) {
        return Map.of("tax", ((BigDecimal) inputs.get("amount")).multiply((BigDecimal) inputs.get("factor")));
    }
}
