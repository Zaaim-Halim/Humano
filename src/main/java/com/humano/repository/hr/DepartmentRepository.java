package com.humano.repository.hr;

import com.humano.domain.hr.Department;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link Department} entity.
 */
@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    boolean existsByName(String name);

    /** Listing fetches the head in the same query — it is shown on every row. */
    @Override
    @EntityGraph(attributePaths = "head")
    Page<Department> findAll(Pageable pageable);
}
