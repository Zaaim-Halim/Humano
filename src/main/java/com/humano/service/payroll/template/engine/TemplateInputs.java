package com.humano.service.payroll.template.engine;

import com.humano.service.payroll.template.model.InputField;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converts raw string settings/attributes into the typed values a template declares, rejecting
 * unknown keys and values that don't fit. Shared by save-time validation and by the engine, so a
 * value accepted on save is the value calculated with.
 */
public final class TemplateInputs {

    private TemplateInputs() {}

    /**
     * Typed values with defaults applied. Absent optional keys are absent from the result (they read
     * as null in expressions).
     *
     * @param who            "employer setting" / "employee attribute", for messages
     * @param enforceRequired whether missing required keys are an error (true at calculation time)
     */
    public static Map<String, Object> coerce(List<InputField> fields, Map<String, String> raw, String who, boolean enforceRequired) {
        List<InputField> declared = fields == null ? List.of() : fields;
        Map<String, String> values = raw == null ? Map.of() : raw;
        Set<String> known = declared.stream().map(InputField::key).collect(Collectors.toSet());
        for (String key : values.keySet()) {
            if (!known.contains(key)) {
                throw new TemplateCalculationException("Unknown " + who + " '" + key + "'; this template reads " + known);
            }
        }
        Map<String, Object> typed = new HashMap<>();
        for (InputField field : declared) {
            String value = values.get(field.key());
            if (value == null || value.isBlank()) {
                value = field.defaultValue();
            }
            if (value != null) {
                typed.put(field.key(), convert(field, value.trim(), who));
            }
        }
        List<String> missing = missingRequired(declared, values);
        if (enforceRequired && !missing.isEmpty()) {
            throw new TemplateCalculationException("Missing required " + who + (missing.size() > 1 ? "s " : " ") + missing);
        }
        return typed;
    }

    /**
     * The required inputs that are neither set (blank counts as unset) nor defaulted, in declaration
     * order: what {@link #coerce} refuses when it enforces required inputs. The one rule for "missing",
     * shared by calculation, the employee list and the run's errors.
     */
    public static List<String> missingRequired(List<InputField> fields, Map<String, String> raw) {
        if (fields == null) return List.of();
        Map<String, String> values = raw == null ? Map.of() : raw;
        return fields
            .stream()
            .filter(InputField::required)
            .filter(f -> f.defaultValue() == null)
            .filter(f -> {
                String value = values.get(f.key());
                return value == null || value.isBlank();
            })
            .map(InputField::key)
            .toList();
    }

    private static Object convert(InputField field, String value, String who) {
        String name = who + " '" + field.key() + "'";
        return switch (field.type()) {
            case STRING -> {
                if (field.pattern() != null && !value.matches(field.pattern())) {
                    throw new TemplateCalculationException(name + " must match " + field.pattern() + " (got '" + value + "')");
                }
                yield value;
            }
            case ENUM -> {
                if (field.values() == null || !field.values().contains(value)) {
                    throw new TemplateCalculationException(name + " must be one of " + field.values() + " (got '" + value + "')");
                }
                yield value;
            }
            case BOOLEAN -> {
                if (!value.equals("true") && !value.equals("false")) {
                    throw new TemplateCalculationException(name + " must be true or false (got '" + value + "')");
                }
                yield Boolean.valueOf(value);
            }
            case INTEGER, DECIMAL -> {
                BigDecimal number;
                try {
                    number = new BigDecimal(value);
                } catch (NumberFormatException e) {
                    throw new TemplateCalculationException(name + " must be a number (got '" + value + "')");
                }
                if (field.type() == InputField.Type.INTEGER && number.stripTrailingZeros().scale() > 0) {
                    throw new TemplateCalculationException(name + " must be a whole number (got '" + value + "')");
                }
                if (field.min() != null && number.compareTo(field.min()) < 0) {
                    throw new TemplateCalculationException(name + " must be at least " + field.min().toPlainString());
                }
                if (field.max() != null && number.compareTo(field.max()) > 0) {
                    throw new TemplateCalculationException(name + " must be at most " + field.max().toPlainString());
                }
                yield number;
            }
        };
    }
}
