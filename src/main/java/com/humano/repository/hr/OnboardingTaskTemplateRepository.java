package com.humano.repository.hr;

import com.humano.domain.hr.OnboardingTaskTemplate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for the company onboarding checklist ({@link OnboardingTaskTemplate}).
 */
@Repository
public interface OnboardingTaskTemplateRepository extends JpaRepository<OnboardingTaskTemplate, UUID> {
    /** The checklist in display order; empty until the company customises it. */
    List<OnboardingTaskTemplate> findAllByOrderBySortOrderAsc();
}
