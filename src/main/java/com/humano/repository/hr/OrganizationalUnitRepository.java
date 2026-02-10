package com.humano.repository.hr;

import com.humano.domain.hr.OrganizationalUnit;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link OrganizationalUnit} entity.
 */
@Repository
public interface OrganizationalUnitRepository
    extends JpaRepository<OrganizationalUnit, UUID>, JpaSpecificationExecutor<OrganizationalUnit> {
    Page<OrganizationalUnit> findByParentUnitIsNull(Pageable pageable);

    Page<OrganizationalUnit> findByParentUnitId(UUID parentId, Pageable pageable);
}
