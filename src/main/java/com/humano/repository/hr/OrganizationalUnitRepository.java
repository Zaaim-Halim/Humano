package com.humano.repository.hr;

import com.humano.domain.hr.OrganizationalUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link OrganizationalUnit} entity.
 */
@Repository
public interface OrganizationalUnitRepository extends JpaRepository<OrganizationalUnit, UUID> , JpaSpecificationExecutor<OrganizationalUnit> {
}


