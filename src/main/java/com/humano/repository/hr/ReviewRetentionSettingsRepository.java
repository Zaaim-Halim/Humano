package com.humano.repository.hr;

import com.humano.domain.hr.ReviewRetentionSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for the {@link ReviewRetentionSettings} singleton (one row per tenant schema). Fetch the
 * current settings with {@code findAll().stream().findFirst()}.
 */
@Repository
public interface ReviewRetentionSettingsRepository extends JpaRepository<ReviewRetentionSettings, UUID> {}
