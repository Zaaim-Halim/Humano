package com.humano.service.payroll.template.extension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** All {@link PayrollTemplateExtension} beans, by id. */
@Component
public class PayrollTemplateExtensionRegistry {

    private final Map<String, PayrollTemplateExtension> byId;

    public PayrollTemplateExtensionRegistry(List<PayrollTemplateExtension> extensions) {
        this.byId = extensions.stream().collect(Collectors.toUnmodifiableMap(PayrollTemplateExtension::id, Function.identity()));
    }

    public Optional<PayrollTemplateExtension> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }
}
