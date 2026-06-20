package com.humano.repository.hr;

import com.humano.domain.hr.EmployeeAttribute;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link EmployeeAttribute} entity.
 */
@Repository
public interface EmployeeAttributeRepository extends JpaRepository<EmployeeAttribute, UUID>, JpaSpecificationExecutor<UUID> {
    /** All custom attributes for an employee, ordered by key. */
    List<EmployeeAttribute> findByEmployeeIdOrderByKeyAsc(UUID employeeId);

    /** Remove every attribute belonging to an employee (used for full replace). */
    void deleteByEmployeeId(UUID employeeId);
}
