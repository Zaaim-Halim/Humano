package com.humano.repository.payroll;

import com.humano.domain.payroll.OrganizationSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for the {@link OrganizationSettings} singleton (one row per tenant
 * schema). Fetch the current settings with {@code findAll().stream().findFirst()}.
 */
@Repository
public interface OrganizationSettingsRepository extends JpaRepository<OrganizationSettings, UUID> {}
