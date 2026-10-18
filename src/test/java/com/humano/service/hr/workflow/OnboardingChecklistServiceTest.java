package com.humano.service.hr.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.humano.domain.enumeration.hr.OnboardingTaskLink;
import com.humano.domain.enumeration.hr.OnboardingTaskOwner;
import com.humano.domain.hr.OnboardingSettings;
import com.humano.domain.hr.OnboardingTaskTemplate;
import com.humano.dto.hr.workflow.requests.UpdateOnboardingChecklistRequest;
import com.humano.dto.hr.workflow.responses.OnboardingChecklistResponse;
import com.humano.repository.hr.OnboardingSettingsRepository;
import com.humano.repository.hr.OnboardingTaskTemplateRepository;
import com.humano.web.rest.errors.BadRequestAlertException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** The company decides how long onboarding takes and what is on the checklist. */
class OnboardingChecklistServiceTest {

    private final OnboardingSettingsRepository settings = mock(OnboardingSettingsRepository.class);
    private final OnboardingTaskTemplateRepository templates = mock(OnboardingTaskTemplateRepository.class);
    private final OnboardingChecklistService service = new OnboardingChecklistService(settings, templates);

    private final List<OnboardingSettings> savedSettings = new ArrayList<>();
    private final List<OnboardingTaskTemplate> savedTemplates = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(settings.findAll()).thenAnswer(inv -> List.copyOf(savedSettings));
        when(settings.save(any())).thenAnswer(inv -> {
            OnboardingSettings s = inv.getArgument(0);
            if (!savedSettings.contains(s)) savedSettings.add(s);
            return s;
        });
        when(templates.findAllByOrderBySortOrderAsc()).thenAnswer(inv -> List.copyOf(savedTemplates));
        when(templates.saveAll(any())).thenAnswer(inv -> {
            Iterable<OnboardingTaskTemplate> list = inv.getArgument(0);
            list.forEach(savedTemplates::add);
            return list;
        });
    }

    @Test
    void untilCustomisedTheBuiltInChecklistRunsThirtyDays() {
        OnboardingChecklistResponse checklist = service.get();

        assertThat(checklist.customized()).isFalse();
        assertThat(checklist.defaultDays()).isEqualTo(30);
        assertThat(checklist.tasks()).hasSize(OnboardingChecklistService.BUILT_IN.size());
        assertThat(checklist.tasks()).allSatisfy(t -> assertThat(t.dueDay()).isBetween(0, 30));
    }

    @Test
    void aShorterDefaultScalesTheBuiltInChecklist() {
        OnboardingSettings tenDays = new OnboardingSettings();
        tenDays.setDefaultDays(10);
        savedSettings.add(tenDays);

        OnboardingChecklistResponse checklist = service.get();

        assertThat(checklist.defaultDays()).isEqualTo(10);
        assertThat(checklist.tasks()).allSatisfy(t -> assertThat(t.dueDay()).isBetween(0, 10));
        assertThat(checklist.tasks().get(checklist.tasks().size() - 1).dueDay()).isEqualTo(10);
    }

    @Test
    void savingReplacesTheLengthAndTheChecklistInOrder() {
        OnboardingChecklistResponse saved = service.update(
            new UpdateOnboardingChecklistRequest(
                10,
                List.of(
                    new UpdateOnboardingChecklistRequest.Task(
                        "  Laptop handover ",
                        " ",
                        0,
                        OnboardingTaskOwner.HR,
                        OnboardingTaskLink.ACCOUNT
                    ),
                    new UpdateOnboardingChecklistRequest.Task("Team lunch", "With the whole squad", 10, OnboardingTaskOwner.MANAGER, null)
                )
            )
        );

        verify(templates).deleteAllInBatch();
        assertThat(saved.customized()).isTrue();
        assertThat(saved.defaultDays()).isEqualTo(10);
        assertThat(saved.tasks()).extracting(OnboardingChecklistResponse.Task::title).containsExactly("Laptop handover", "Team lunch");
        assertThat(saved.tasks().get(0).description()).isNull();
        assertThat(savedTemplates).extracting(OnboardingTaskTemplate::getSortOrder).containsExactly(0, 1);
        assertThat(saved.tasks()).extracting(OnboardingChecklistResponse.Task::link).containsExactly(OnboardingTaskLink.ACCOUNT, null);
    }

    @Test
    void aTaskDueAfterTheDefaultLengthIsRejected() {
        UpdateOnboardingChecklistRequest request = new UpdateOnboardingChecklistRequest(
            7,
            List.of(new UpdateOnboardingChecklistRequest.Task("Review", null, 14, OnboardingTaskOwner.MANAGER, null))
        );

        assertThatThrownBy(() -> service.update(request)).isInstanceOf(BadRequestAlertException.class).hasMessageContaining("day 14");
        verify(templates, never()).deleteAllInBatch();
        ArgumentCaptor<OnboardingSettings> unused = ArgumentCaptor.forClass(OnboardingSettings.class);
        verify(settings, never()).save(unused.capture());
    }
}
