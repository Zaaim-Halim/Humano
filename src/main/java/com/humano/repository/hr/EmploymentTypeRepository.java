package com.humano.repository.hr;

import com.humano.domain.hr.EmploymentType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link EmploymentType} reference entity.
 */
@Repository
public interface EmploymentTypeRepository extends JpaRepository<EmploymentType, UUID> {
    Optional<EmploymentType> findByCode(String code);
}
