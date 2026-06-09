package com.humano.repository.shared;

import com.humano.domain.shared.Employee;
import com.humano.repository.hr.projection.EmployeeHierarchyRow;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link Employee} entity.
 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID>, JpaSpecificationExecutor<Employee> {
    /**
     * Fetches the people-tree subtree (the manager at {@code rootPath} plus all
     * transitive subordinates) in a single query, including the unit metadata each
     * employee belongs to and a minimal descriptor of the direct manager.
     * <p>
     * Backed by the {@code idx_employee_path} prefix index. Tree assembly is the
     * caller's job.
     */
    @Query(
        """
        SELECT new com.humano.repository.hr.projection.EmployeeHierarchyRow(
            e.id, e.firstName, e.lastName, e.jobTitle, e.status, e.path,
            m.id, m.firstName, m.lastName, m.jobTitle,
            u.id, u.name, u.type, u.path
        )
        FROM Employee e
        JOIN e.unit u
        LEFT JOIN e.manager m
        WHERE e.path = :rootPath OR e.path LIKE CONCAT(:rootPath, '/%')
        """
    )
    List<EmployeeHierarchyRow> findManagerSubtree(@Param("rootPath") String rootPath);

    /**
     * Fetches every employee whose unit lies in the org-unit subtree rooted at
     * {@code rootUnitPath}, with unit metadata and minimal manager descriptor
     * embedded. Single query joining via unit.path — backed by
     * {@code idx_organizational_unit_path}.
     */
    @Query(
        """
        SELECT new com.humano.repository.hr.projection.EmployeeHierarchyRow(
            e.id, e.firstName, e.lastName, e.jobTitle, e.status, e.path,
            m.id, m.firstName, m.lastName, m.jobTitle,
            u.id, u.name, u.type, u.path
        )
        FROM Employee e
        JOIN e.unit u
        LEFT JOIN e.manager m
        WHERE u.path = :rootUnitPath OR u.path LIKE CONCAT(:rootUnitPath, '/%')
        """
    )
    List<EmployeeHierarchyRow> findEmployeesInUnitSubtree(@Param("rootUnitPath") String rootUnitPath);

    /**
     * Projects a fixed set of employees (used to materialise an ancestor chain whose
     * IDs we parsed from the path string — one IN-list lookup, no traversal).
     */
    @Query(
        """
        SELECT new com.humano.repository.hr.projection.EmployeeHierarchyRow(
            e.id, e.firstName, e.lastName, e.jobTitle, e.status, e.path,
            m.id, m.firstName, m.lastName, m.jobTitle,
            u.id, u.name, u.type, u.path
        )
        FROM Employee e
        JOIN e.unit u
        LEFT JOIN e.manager m
        WHERE e.id IN :ids
        """
    )
    List<EmployeeHierarchyRow> findHierarchyRowsByIds(@Param("ids") Collection<UUID> ids);

    /**
     * Rewrites the materialized-path prefix on every descendant of an employee whose
     * own path has just changed (reorg under a new manager). One bulk UPDATE; no
     * cascade through the Hibernate session.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE Employee e
        SET e.path = CONCAT(:newPrefix, SUBSTRING(e.path, LENGTH(:oldPrefix) + 1))
        WHERE e.path LIKE CONCAT(:oldPrefix, '/%')
        """
    )
    int rewriteDescendantPaths(@Param("oldPrefix") String oldPrefix, @Param("newPrefix") String newPrefix);
}
