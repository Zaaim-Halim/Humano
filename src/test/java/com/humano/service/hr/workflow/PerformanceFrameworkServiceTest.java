package com.humano.service.hr.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.humano.domain.enumeration.hr.CompetencyCategory;
import com.humano.domain.enumeration.hr.ReviewQuestionAudience;
import com.humano.domain.hr.Competency;
import com.humano.domain.hr.ReviewTemplate;
import com.humano.dto.hr.workflow.requests.CompetencyRequest;
import com.humano.dto.hr.workflow.requests.ReviewTemplateRequest;
import com.humano.dto.hr.workflow.responses.ReviewTemplateResponse;
import com.humano.repository.hr.CompetencyRepository;
import com.humano.repository.hr.ReviewTemplateRepository;
import com.humano.repository.hr.workflow.ReviewCycleRepository;
import com.humano.service.errors.BadRequestAlertException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Competencies and review templates: names, retiring instead of deleting what is in use. */
class PerformanceFrameworkServiceTest {

    private final CompetencyRepository competencies = mock(CompetencyRepository.class);
    private final ReviewTemplateRepository templates = mock(ReviewTemplateRepository.class);
    private final ReviewCycleRepository cycles = mock(ReviewCycleRepository.class);
    private final PerformanceFrameworkService service = new PerformanceFrameworkService(competencies, templates, cycles);

    private final Competency ownership = competency("Ownership", true);
    private final Competency retired = competency("Hustle", false);

    @BeforeEach
    void setUp() {
        when(competencies.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(templates.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(competencies.findAllById(any())).thenAnswer(inv -> {
            Iterable<UUID> ids = inv.getArgument(0);
            List<Competency> found = new ArrayList<>();
            ids.forEach(id -> List.of(ownership, retired).stream().filter(c -> c.getId().equals(id)).forEach(found::add));
            return found;
        });
    }

    @Test
    void competencyNamesAreUniqueIgnoringCaseAndAnchorsKeepTheirLevel() {
        when(competencies.existsByNameIgnoreCase("ownership")).thenReturn(true);
        assertThatThrownBy(() -> service.createCompetency(competencyRequest(" ownership "))).isInstanceOf(BadRequestAlertException.class);

        var created = service.createCompetency(
            new CompetencyRequest("Craft", null, CompetencyCategory.FUNCTIONAL, List.of("Sloppy", " ", "Solid"), null)
        );
        assertThat(created.anchors()).containsExactly("Sloppy", null, "Solid", null, null);
        assertThat(created.active()).isTrue();
    }

    @Test
    void aCompetencyInATemplateIsRetiredNotDeleted() {
        when(competencies.findById(ownership.getId())).thenReturn(Optional.of(ownership));
        when(templates.findNamesUsingCompetency(ownership.getId())).thenReturn(List.of("Annual review"));

        assertThatThrownBy(() -> service.deleteCompetency(ownership.getId())).hasMessageContaining("Annual review");
        verify(competencies, never()).delete(any());
    }

    @Test
    void aTemplateAsksSomethingAndOnlyTakesActiveCompetencies() {
        assertThatThrownBy(() -> service.createTemplate(new ReviewTemplateRequest("Empty", null, List.of(), List.of(), null))
        ).hasMessageContaining("at least one");
        assertThatThrownBy(() -> service.createTemplate(new ReviewTemplateRequest("Old", null, List.of(retired.getId()), List.of(), null))
        ).hasMessageContaining("retired");
        assertThatThrownBy(() ->
            service.createTemplate(new ReviewTemplateRequest("Twice", null, List.of(ownership.getId(), ownership.getId()), List.of(), null))
        ).hasMessageContaining("twice");

        ReviewTemplateResponse created = service.createTemplate(
            new ReviewTemplateRequest(
                "Annual review",
                null,
                List.of(ownership.getId()),
                List.of(new ReviewTemplateRequest.Question(ReviewQuestionAudience.EMPLOYEE, " Proudest achievement? ", "", true)),
                null
            )
        );
        assertThat(created.competencies()).extracting(c -> c.name()).containsExactly("Ownership");
        assertThat(created.questions()).containsExactly(
            new ReviewTemplateResponse.Question(ReviewQuestionAudience.EMPLOYEE, "Proudest achievement?", null, true)
        );
    }

    @Test
    void aCompetencyRetiredLaterStaysOnTheTemplatesUsingIt() {
        ReviewTemplate template = template("Annual review");
        template.getCompetencies().add(retired);
        when(templates.findById(template.getId())).thenReturn(Optional.of(template));

        ReviewTemplateResponse updated = service.updateTemplate(
            template.getId(),
            new ReviewTemplateRequest("Annual review", "Renamed", List.of(retired.getId()), List.of(), null)
        );

        assertThat(updated.competencies()).extracting(c -> c.name()).containsExactly("Hustle");
    }

    @Test
    void aTemplateInUseIsRetiredNotDeletedAndARetiredOneCannotBePicked() {
        ReviewTemplate template = template("Annual review");
        when(templates.findById(template.getId())).thenReturn(Optional.of(template));
        when(cycles.countByTemplateId(template.getId())).thenReturn(2L);

        assertThatThrownBy(() -> service.deleteTemplate(template.getId())).hasMessageContaining("retire");

        template.setActive(false);
        assertThatThrownBy(() -> service.templateForCycle(template.getId(), null)).hasMessageContaining("retired");
        assertThat(service.templateForCycle(template.getId(), template)).isSameAs(template); // a cycle keeps the one it has
    }

    private static Competency competency(String name, boolean active) {
        Competency c = new Competency();
        c.setId(UUID.randomUUID());
        c.setName(name);
        c.setCategory(CompetencyCategory.CORE);
        c.setActive(active);
        return c;
    }

    private static ReviewTemplate template(String name) {
        ReviewTemplate t = new ReviewTemplate();
        t.setId(UUID.randomUUID());
        t.setName(name);
        return t;
    }

    private static CompetencyRequest competencyRequest(String name) {
        return new CompetencyRequest(name, null, CompetencyCategory.CORE, List.of(), null);
    }
}
