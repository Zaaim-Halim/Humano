package com.humano.repository.tenant;

import com.humano.domain.tenant.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link Organization} entity.
 */
@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID>, JpaSpecificationExecutor<Organization> {
}

