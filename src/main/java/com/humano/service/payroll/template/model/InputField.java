package com.humano.service.payroll.template.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * An employer setting or employee attribute a template reads. Values are stored as strings and
 * converted to the declared type before evaluation; expressions see them as
 * {@code #employer['key']} / {@code #employee['key']}.
 *
 * @param key          name, e.g. {@code qualificationGroup}
 * @param type         value type
 * @param required     whether calculation refuses to run without it
 * @param values       allowed values for {@link Type#ENUM}
 * @param pattern      regular expression a {@link Type#STRING} value must match
 * @param min          inclusive lower bound for numeric types
 * @param max          inclusive upper bound for numeric types
 * @param defaultValue value used when none is set
 * @param description  what the value means, for the tenant
 */
public record InputField(
    String key,
    Type type,
    boolean required,
    List<String> values,
    String pattern,
    BigDecimal min,
    BigDecimal max,
    String defaultValue,
    String description
) {
    public enum Type {
        STRING,
        INTEGER,
        DECIMAL,
        BOOLEAN,
        ENUM,
    }
}
