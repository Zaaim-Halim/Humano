package com.humano.repository.hr;

import com.humano.domain.hr.EmployeeCategory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link EmployeeCategory} reference entity.
 */
@Repository
public interface EmployeeCategoryRepository extends JpaRepository<EmployeeCategory, UUID> {
    Optional<EmployeeCategory> findByCode(String code);
}
