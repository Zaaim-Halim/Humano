package com.humano.repository.hr;

import com.humano.domain.hr.EmployeeAttribute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link EmployeeAttribute} entity.
 */
@Repository
public interface EmployeeAttributeRepository extends JpaRepository<EmployeeAttribute, UUID>, JpaSpecificationExecutor<UUID> {
}
