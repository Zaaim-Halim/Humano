package com.humano.service.payroll.template.catalog;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.humano.service.payroll.template.engine.PayrollTemplateEngine;
import com.humano.service.payroll.template.model.PayrollTemplate;
import com.humano.service.payroll.template.model.TemplateStatus;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * The statutory payroll templates shipped with this build ({@code classpath:payroll-templates/**}).
 * <p>
 * Each template is parsed strictly, validated, and its test cases run on the deployed engine. Only
 * templates that pass all three are {@link #templates() available}; the rest are listed by
 * {@link #rejected()} with their problems and never used for payroll. One broken template doesn't
 * stop the application or the other templates.
 */
@Component
public class PayrollTemplateCatalog {

    public static final String LOCATION = "classpath*:payroll-templates/**/*.json";

    private static final Logger log = LoggerFactory.getLogger(PayrollTemplateCatalog.class);

    /** A template file (by file name) that was not made available, and why. */
    public record Rejected(String resource, String templateId, List<String> problems) {}

    private final Map<String, PayrollTemplate> available;
    private final List<Rejected> rejected;

    @Autowired
    public PayrollTemplateCatalog(PayrollTemplateValidator validator, PayrollTemplateEngine engine) {
        this(validator, engine, LOCATION);
    }

    public PayrollTemplateCatalog(PayrollTemplateValidator validator, PayrollTemplateEngine engine, String location) {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        Map<String, PayrollTemplate> loaded = new HashMap<>();
        Map<String, String> resourceOf = new HashMap<>();
        List<Rejected> problems = new ArrayList<>();
        for (Resource resource : resources(location)) {
            String name = shortName(resource);
            PayrollTemplate template;
            try (InputStream in = resource.getInputStream()) {
                template = mapper.readValue(in, PayrollTemplate.class);
            } catch (IOException e) {
                problems.add(new Rejected(name, null, List.of("unreadable: " + e.getMessage())));
                continue;
            }
            List<String> errors = new ArrayList<>();
            try {
                errors.addAll(validator.validate(template));
                if (errors.isEmpty()) {
                    errors.addAll(TemplateTestCaseRunner.run(template, engine));
                }
            } catch (RuntimeException e) {
                // A malformed template (e.g. a missing field the validator dereferences) is rejected, never
                // allowed to stop the application or the other templates.
                errors.add("invalid template: " + e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : ""));
            }
            if (!errors.isEmpty()) {
                problems.add(new Rejected(name, template.id(), errors));
                continue;
            }
            if (loaded.containsKey(template.id())) {
                problems.add(new Rejected(name, template.id(), List.of("duplicate template id, also in " + resourceOf.get(template.id()))));
                continue;
            }
            loaded.put(template.id(), template);
            resourceOf.put(template.id(), name);
        }
        problems.forEach(r -> log.error("Payroll template {} ({}) rejected: {}", r.templateId(), r.resource(), r.problems()));
        log.info("Payroll template catalog: {} available, {} rejected", loaded.size(), problems.size());
        this.available = Collections.unmodifiableMap(loaded);
        this.rejected = List.copyOf(problems);
    }

    /** Templates that passed validation and their test cases, ordered by id. */
    public List<PayrollTemplate> templates() {
        return available.values().stream().sorted(Comparator.comparing(PayrollTemplate::id)).toList();
    }

    public Optional<PayrollTemplate> find(String id) {
        return Optional.ofNullable(available.get(id));
    }

    /** Whether tenants may adopt the template (drafts are listed but not adoptable). */
    public static boolean adoptable(PayrollTemplate template) {
        return template.status() != TemplateStatus.DRAFT;
    }

    public List<Rejected> rejected() {
        return rejected;
    }

    /** The file name only — never a server path, since rejections are shown through the API. */
    private static String shortName(Resource resource) {
        return resource.getFilename() != null ? resource.getFilename() : "unknown";
    }

    private static Resource[] resources(String location) {
        try {
            return new PathMatchingResourcePatternResolver().getResources(location);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot list payroll templates at " + location, e);
        }
    }
}
