package com.humano.service.hr.workflow;

import com.humano.domain.hr.Competency;
import com.humano.domain.hr.RatingAnchors;
import com.humano.domain.hr.ReviewQuestionDefinition;
import com.humano.domain.hr.ReviewTemplate;
import com.humano.dto.hr.workflow.requests.CompetencyRequest;
import com.humano.dto.hr.workflow.requests.ReviewTemplateRequest;
import com.humano.dto.hr.workflow.responses.CompetencyResponse;
import com.humano.dto.hr.workflow.responses.ReviewTemplateResponse;
import com.humano.repository.hr.CompetencyRepository;
import com.humano.repository.hr.ReviewTemplateRepository;
import com.humano.repository.hr.workflow.ReviewCycleRepository;
import com.humano.service.errors.BadRequestAlertException;
import com.humano.service.errors.EntityNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The company's performance framework: competencies (with what each rating level looks like) and
 * the review templates built from them.
 *
 * <ul>
 *   <li>Names are unique, ignoring case.</li>
 *   <li>A competency used by a template can't be deleted, only retired; a retired competency stays
 *       on its templates but can't be added to others.</li>
 *   <li>A template used by a cycle can't be deleted, only retired; a retired template can't be
 *       picked for a cycle. Editing a template never changes a cycle that has opened: cycles copy
 *       their template when self-assessment starts.</li>
 *   <li>A template asks something: at least one competency or question.</li>
 * </ul>
 */
@Service
@Transactional("tenantTransactionManager")
public class PerformanceFrameworkService {

    private static final Logger log = LoggerFactory.getLogger(PerformanceFrameworkService.class);
    private static final String COMPETENCY = "competency";
    private static final String TEMPLATE = "reviewTemplate";

    private final CompetencyRepository competencyRepository;
    private final ReviewTemplateRepository templateRepository;
    private final ReviewCycleRepository cycleRepository;

    public PerformanceFrameworkService(
        CompetencyRepository competencyRepository,
        ReviewTemplateRepository templateRepository,
        ReviewCycleRepository cycleRepository
    ) {
        this.competencyRepository = competencyRepository;
        this.templateRepository = templateRepository;
        this.cycleRepository = cycleRepository;
    }

    // ---------------------------------------------------------------- competencies

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<CompetencyResponse> listCompetencies() {
        return competencyRepository.findAllByOrderByCategoryAscNameAsc().stream().map(this::toResponse).toList();
    }

    public CompetencyResponse createCompetency(CompetencyRequest request) {
        String name = request.name().trim();
        if (competencyRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestAlertException("A competency with this name already exists", COMPETENCY, "nameexists");
        }
        Competency competency = new Competency();
        apply(competency, request, name);
        competency = competencyRepository.save(competency);
        log.info("Competency {} created", competency.getId());
        return toResponse(competency);
    }

    public CompetencyResponse updateCompetency(UUID id, CompetencyRequest request) {
        Competency competency = findCompetency(id);
        String name = request.name().trim();
        if (competencyRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new BadRequestAlertException("A competency with this name already exists", COMPETENCY, "nameexists");
        }
        apply(competency, request, name);
        return toResponse(competencyRepository.save(competency));
    }

    public void deleteCompetency(UUID id) {
        Competency competency = findCompetency(id);
        List<String> usedBy = templateRepository.findNamesUsingCompetency(id);
        if (!usedBy.isEmpty()) {
            throw new BadRequestAlertException(
                "Used by " + String.join(", ", usedBy) + ": retire it instead, or remove it from those templates first",
                COMPETENCY,
                "inuse"
            );
        }
        competencyRepository.delete(competency);
        log.info("Competency {} deleted", id);
    }

    // ---------------------------------------------------------------- templates

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<ReviewTemplateResponse> listTemplates() {
        return templateRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public ReviewTemplateResponse getTemplate(UUID id) {
        return toResponse(findTemplate(id));
    }

    public ReviewTemplateResponse createTemplate(ReviewTemplateRequest request) {
        String name = request.name().trim();
        if (templateRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestAlertException("A template with this name already exists", TEMPLATE, "nameexists");
        }
        ReviewTemplate template = new ReviewTemplate();
        apply(template, request, name);
        template = templateRepository.save(template);
        log.info("Review template {} created", template.getId());
        return toResponse(template);
    }

    public ReviewTemplateResponse updateTemplate(UUID id, ReviewTemplateRequest request) {
        ReviewTemplate template = findTemplate(id);
        String name = request.name().trim();
        if (templateRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new BadRequestAlertException("A template with this name already exists", TEMPLATE, "nameexists");
        }
        apply(template, request, name);
        return toResponse(templateRepository.save(template));
    }

    public void deleteTemplate(UUID id) {
        ReviewTemplate template = findTemplate(id);
        if (cycleRepository.countByTemplateId(id) > 0) {
            throw new BadRequestAlertException("Review cycles use this template: retire it instead", TEMPLATE, "inuse");
        }
        templateRepository.delete(template);
        log.info("Review template {} deleted", id);
    }

    /** The template a cycle may pick: it exists and isn't retired (unless the cycle already had it). */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public ReviewTemplate templateForCycle(UUID templateId, ReviewTemplate current) {
        ReviewTemplate template = findTemplate(templateId);
        if (!template.isActive() && !template.equals(current)) {
            throw new BadRequestAlertException("This review template is retired", TEMPLATE, "retired");
        }
        return template;
    }

    // ---------------------------------------------------------------- helpers

    private void apply(Competency competency, CompetencyRequest request, String name) {
        competency.setName(name);
        competency.setDescription(blankToNull(request.description()));
        competency.setCategory(request.category());
        competency.setAnchors(RatingAnchors.of(request.anchors()));
        if (request.active() != null) {
            competency.setActive(request.active());
        }
    }

    private void apply(ReviewTemplate template, ReviewTemplateRequest request, String name) {
        List<UUID> ids = request.competencyIds() != null ? request.competencyIds() : List.of();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new BadRequestAlertException("A competency is listed twice", TEMPLATE, "duplicatecompetency");
        }
        Map<UUID, Competency> found = new LinkedHashMap<>();
        competencyRepository.findAllById(ids).forEach(c -> found.put(c.getId(), c));
        Set<UUID> already = new HashSet<>();
        template.getCompetencies().forEach(c -> already.add(c.getId()));
        List<Competency> competencies = new ArrayList<>();
        for (UUID id : ids) {
            Competency competency = found.get(id);
            if (competency == null) {
                throw EntityNotFoundException.create("Competency", id);
            }
            if (!competency.isActive() && !already.contains(id)) {
                throw new BadRequestAlertException(competency.getName() + " is retired", TEMPLATE, "retiredcompetency");
            }
            competencies.add(competency);
        }
        List<ReviewQuestionDefinition> questions =
            (request.questions() != null ? request.questions() : List.<ReviewTemplateRequest.Question>of()).stream()
                .map(q -> new ReviewQuestionDefinition(q.audience(), q.text().trim(), blankToNull(q.helpText()), q.required()))
                .toList();
        if (competencies.isEmpty() && questions.isEmpty()) {
            throw new BadRequestAlertException("Add at least one competency or question", TEMPLATE, "empty");
        }

        template.setName(name);
        template.setDescription(blankToNull(request.description()));
        template.getCompetencies().clear();
        template.getCompetencies().addAll(competencies);
        template.getQuestions().clear();
        template.getQuestions().addAll(questions);
        if (request.active() != null) {
            template.setActive(request.active());
        }
    }

    private Competency findCompetency(UUID id) {
        return competencyRepository.findById(id).orElseThrow(() -> EntityNotFoundException.create("Competency", id));
    }

    private ReviewTemplate findTemplate(UUID id) {
        return templateRepository.findById(id).orElseThrow(() -> EntityNotFoundException.create("ReviewTemplate", id));
    }

    private CompetencyResponse toResponse(Competency c) {
        return new CompetencyResponse(
            c.getId(),
            c.getName(),
            c.getDescription(),
            c.getCategory(),
            c.getAnchors().toList(),
            c.isActive(),
            c.getId() != null ? templateRepository.findNamesUsingCompetency(c.getId()) : List.of()
        );
    }

    private ReviewTemplateResponse toResponse(ReviewTemplate t) {
        return new ReviewTemplateResponse(
            t.getId(),
            t.getName(),
            t.getDescription(),
            t.isActive(),
            t
                .getCompetencies()
                .stream()
                .filter(Objects::nonNull)
                .map(c ->
                    new CompetencyResponse(
                        c.getId(),
                        c.getName(),
                        c.getDescription(),
                        c.getCategory(),
                        c.getAnchors().toList(),
                        c.isActive(),
                        List.of()
                    )
                )
                .toList(),
            t
                .getQuestions()
                .stream()
                .map(q -> new ReviewTemplateResponse.Question(q.getAudience(), q.getText(), q.getHelpText(), q.isRequired()))
                .toList(),
            t.getId() != null ? cycleRepository.countByTemplateId(t.getId()) : 0
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
