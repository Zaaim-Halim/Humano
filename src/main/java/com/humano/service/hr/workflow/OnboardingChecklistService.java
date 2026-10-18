package com.humano.service.hr.workflow;

import static com.humano.domain.enumeration.hr.OnboardingTaskLink.ACCOUNT;
import static com.humano.domain.enumeration.hr.OnboardingTaskLink.BENEFITS;
import static com.humano.domain.enumeration.hr.OnboardingTaskOwner.HR;
import static com.humano.domain.enumeration.hr.OnboardingTaskOwner.MANAGER;
import static com.humano.domain.enumeration.hr.OnboardingTaskOwner.NEW_HIRE;

import com.humano.domain.hr.OnboardingSettings;
import com.humano.domain.hr.OnboardingTaskTemplate;
import com.humano.dto.hr.workflow.requests.UpdateOnboardingChecklistRequest;
import com.humano.dto.hr.workflow.responses.OnboardingChecklistResponse;
import com.humano.repository.hr.OnboardingSettingsRepository;
import com.humano.repository.hr.OnboardingTaskTemplateRepository;
import com.humano.web.rest.errors.BadRequestAlertException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Company onboarding policy — the default onboarding length
 * ({@link OnboardingSettings}) and the checklist new onboardings are built from
 * ({@link OnboardingTaskTemplate}).
 *
 * <p>Until the company saves its own checklist the built-in one is used, with its
 * due days (written for a {@value OnboardingSettings#DEFAULT_DAYS}-day onboarding)
 * scaled to the default length. {@link #get()} never persists; {@link #update}
 * replaces both the length and the whole checklist. Editing the checklist only
 * affects onboardings started afterwards.
 */
@Service
@Transactional("tenantTransactionManager")
public class OnboardingChecklistService {

    /** The checklist used until the company customises it; due days are on a 30-day onboarding. */
    static final List<OnboardingChecklistResponse.Task> BUILT_IN = List.of(
        new OnboardingChecklistResponse.Task(
            "IT Systems Setup",
            "Get system access, email setup, and necessary software installations",
            2,
            HR,
            ACCOUNT
        ),
        new OnboardingChecklistResponse.Task(
            "Complete Profile Information",
            "Update personal details, emergency contacts, and upload profile photo",
            3,
            NEW_HIRE,
            null
        ),
        new OnboardingChecklistResponse.Task(
            "Department Orientation",
            "Meet with team members and understand department structure",
            5,
            NEW_HIRE,
            null
        ),
        new OnboardingChecklistResponse.Task(
            "Policy Acknowledgment",
            "Read and acknowledge company policies and handbook",
            7,
            NEW_HIRE,
            null
        ),
        new OnboardingChecklistResponse.Task(
            "First Check-in with Manager",
            "Schedule and complete an early check-in meeting with the manager",
            7,
            MANAGER,
            null
        ),
        new OnboardingChecklistResponse.Task(
            "Benefits Enrollment",
            "Review and enroll in company benefits programs",
            14,
            NEW_HIRE,
            BENEFITS
        ),
        new OnboardingChecklistResponse.Task(
            "Onboarding Review Meeting",
            "Schedule and complete the end-of-onboarding review",
            OnboardingSettings.DEFAULT_DAYS,
            MANAGER,
            null
        )
    );

    private final OnboardingSettingsRepository settingsRepository;
    private final OnboardingTaskTemplateRepository templateRepository;

    public OnboardingChecklistService(
        OnboardingSettingsRepository settingsRepository,
        OnboardingTaskTemplateRepository templateRepository
    ) {
        this.settingsRepository = settingsRepository;
        this.templateRepository = templateRepository;
    }

    /**
     * The current policy: the saved length (or 30 days) and the saved checklist,
     * or the built-in one scaled to that length. Read-only — does not persist.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public OnboardingChecklistResponse get() {
        int defaultDays = settingsRepository
            .findAll()
            .stream()
            .findFirst()
            .map(OnboardingSettings::getDefaultDays)
            .orElse(OnboardingSettings.DEFAULT_DAYS);
        List<OnboardingTaskTemplate> templates = templateRepository.findAllByOrderBySortOrderAsc();
        if (templates.isEmpty()) {
            List<OnboardingChecklistResponse.Task> scaled = BUILT_IN.stream()
                .map(t ->
                    new OnboardingChecklistResponse.Task(
                        t.title(),
                        t.description(),
                        (int) Math.round(((double) t.dueDay() * defaultDays) / OnboardingSettings.DEFAULT_DAYS),
                        t.owner(),
                        t.link()
                    )
                )
                .toList();
            return new OnboardingChecklistResponse(defaultDays, false, scaled);
        }
        return new OnboardingChecklistResponse(
            defaultDays,
            true,
            templates
                .stream()
                .map(t -> new OnboardingChecklistResponse.Task(t.getTitle(), t.getDescription(), t.getDueDay(), t.getOwner(), t.getLink()))
                .toList()
        );
    }

    /** Replace the default length and the whole checklist (in the given order). */
    public OnboardingChecklistResponse update(UpdateOnboardingChecklistRequest request) {
        for (UpdateOnboardingChecklistRequest.Task task : request.tasks()) {
            if (task.dueDay() > request.defaultDays()) {
                throw new BadRequestAlertException(
                    "\"" +
                    task.title().trim() +
                    "\" is due on day " +
                    task.dueDay() +
                    ", after the " +
                    request.defaultDays() +
                    "-day default length",
                    "onboardingChecklist",
                    "dueafterend"
                );
            }
        }

        OnboardingSettings settings = settingsRepository.findAll().stream().findFirst().orElseGet(OnboardingSettings::new);
        settings.setDefaultDays(request.defaultDays());
        settingsRepository.save(settings);

        templateRepository.deleteAllInBatch();
        List<OnboardingTaskTemplate> templates = new ArrayList<>();
        for (int i = 0; i < request.tasks().size(); i++) {
            UpdateOnboardingChecklistRequest.Task task = request.tasks().get(i);
            OnboardingTaskTemplate template = new OnboardingTaskTemplate();
            template.setTitle(task.title().trim());
            template.setDescription(task.description() == null || task.description().isBlank() ? null : task.description().trim());
            template.setDueDay(task.dueDay());
            template.setOwner(task.owner());
            template.setLink(task.link());
            template.setSortOrder(i);
            templates.add(template);
        }
        templateRepository.saveAll(templates);

        return get();
    }
}
