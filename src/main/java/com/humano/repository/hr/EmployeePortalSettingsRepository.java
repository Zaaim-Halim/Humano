package com.humano.repository.hr;

import com.humano.domain.hr.EmployeePortalSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link EmployeePortalSettings} entity.
 */
@Repository
public interface EmployeePortalSettingsRepository extends JpaRepository<EmployeePortalSettings, UUID> {}
