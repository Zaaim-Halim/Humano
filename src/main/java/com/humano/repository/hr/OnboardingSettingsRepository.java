package com.humano.repository.hr;

import com.humano.domain.hr.OnboardingSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for the {@link OnboardingSettings} singleton (one row per tenant
 * schema). Fetch the current settings with {@code findAll().stream().findFirst()}.
 */
@Repository
public interface OnboardingSettingsRepository extends JpaRepository<OnboardingSettings, UUID> {}
