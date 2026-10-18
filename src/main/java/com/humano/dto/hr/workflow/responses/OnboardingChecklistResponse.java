package com.humano.dto.hr.workflow.responses;

import com.humano.domain.enumeration.hr.OnboardingTaskLink;
import com.humano.domain.enumeration.hr.OnboardingTaskOwner;
import java.util.List;

/**
 * Response DTO for the company onboarding policy. {@code customized} is false
 * while the built-in checklist is in use.
 */
public record OnboardingChecklistResponse(int defaultDays, boolean customized, List<Task> tasks) {
    public record Task(String title, String description, int dueDay, OnboardingTaskOwner owner, OnboardingTaskLink link) {}
}
