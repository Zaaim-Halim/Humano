package com.humano.repository.hr;

import com.humano.domain.hr.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link Department} entity.
 */
@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID> {
}
