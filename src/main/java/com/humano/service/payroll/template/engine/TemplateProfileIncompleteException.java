package com.humano.service.payroll.template.engine;

import java.util.List;

/**
 * An employee's profile for a payroll template lacks required attributes, so the template can't
 * calculate them. Carries the template and the missing keys so the payroll run can point the
 * administrator at the details to fill in, not just report the failure.
 */
public class TemplateProfileIncompleteException extends TemplateCalculationException {

    private static final long serialVersionUID = 1L;

    private final String templateId;
    private final List<String> missing;

    public TemplateProfileIncompleteException(String templateId, List<String> missing) {
        this(templateId, missing, "Missing required employee attribute" + (missing.size() > 1 ? "s " : " ") + missing);
    }

    /** With the template's own explanation, e.g. a detail needed only in some periods. */
    public TemplateProfileIncompleteException(String templateId, List<String> missing, String message) {
        super(message);
        this.templateId = templateId;
        this.missing = List.copyOf(missing);
    }

    public String getTemplateId() {
        return templateId;
    }

    public List<String> getMissing() {
        return missing;
    }
}
