package com.humano.repository.shared;

import com.humano.domain.shared.Employee;
import com.humano.repository.hr.projection.EmployeeCountRow;
import com.humano.repository.hr.projection.EmployeeHierarchyRow;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
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
     * Directory listing: fetches the to-one relations shown on each row in the same query, so a
     * page of employees costs one SELECT (plus the count) instead of one per relation per row.
     */
    @Override
    @EntityGraph(attributePaths = { "department", "position", "unit", "manager" })
    Page<Employee> findAll(Pageable pageable);

    /** Filtered directory listing — same eager to-one fetch as {@link #findAll(Pageable)}. */
    @Override
    @EntityGraph(attributePaths = { "department", "position", "unit", "manager" })
    Page<Employee> findAll(Specification<Employee> spec, Pageable pageable);

    @Query("SELECT new com.humano.repository.hr.projection.EmployeeCountRow(e.status, null, COUNT(e)) FROM Employee e GROUP BY e.status")
    List<EmployeeCountRow> countByStatus();

    @Query(
        """
        SELECT new com.humano.repository.hr.projection.EmployeeCountRow(d.id, d.name, COUNT(e))
        FROM Employee e LEFT JOIN e.department d
        GROUP BY d.id, d.name
        ORDER BY COUNT(e) DESC, d.name ASC
        """
    )
    List<EmployeeCountRow> countByDepartment();

    @Query(
        "SELECT new com.humano.repository.hr.projection.EmployeeCountRow(e.workLocation, null, COUNT(e)) FROM Employee e GROUP BY e.workLocation"
    )
    List<EmployeeCountRow> countByWorkLocation();

    long countByStartDateGreaterThanEqual(LocalDate date);

    long countByDepartmentId(UUID departmentId);

    @Query(
        "SELECT new com.humano.repository.hr.projection.EmployeeCountRow(e.position.id, null, COUNT(e)) FROM Employee e GROUP BY e.position.id"
    )
    List<EmployeeCountRow> countByPosition();

    long countByPositionId(UUID positionId);

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
            e.id, e.firstName, e.lastName, e.jobTitle, e.imageUrl, e.status, e.path,
            m.id, m.firstName, m.lastName, m.jobTitle, m.imageUrl,
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
     * Every employee with unit metadata and manager descriptor — the whole reporting forest in
     * one query, in the same row shape as {@link #findManagerSubtree}.
     */
    @Query(
        """
        SELECT new com.humano.repository.hr.projection.EmployeeHierarchyRow(
            e.id, e.firstName, e.lastName, e.jobTitle, e.imageUrl, e.status, e.path,
            m.id, m.firstName, m.lastName, m.jobTitle, m.imageUrl,
            u.id, u.name, u.type, u.path
        )
        FROM Employee e
        JOIN e.unit u
        LEFT JOIN e.manager m
        ORDER BY e.firstName, e.lastName
        """
    )
    List<EmployeeHierarchyRow> findAllHierarchyRows();

    /**
     * Fetches every employee whose unit lies in the org-unit subtree rooted at
     * {@code rootUnitPath}, with unit metadata and minimal manager descriptor
     * embedded. Single query joining via unit.path — backed by
     * {@code idx_organizational_unit_path}.
     */
    @Query(
        """
        SELECT new com.humano.repository.hr.projection.EmployeeHierarchyRow(
            e.id, e.firstName, e.lastName, e.jobTitle, e.imageUrl, e.status, e.path,
            m.id, m.firstName, m.lastName, m.jobTitle, m.imageUrl,
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
            e.id, e.firstName, e.lastName, e.jobTitle, e.imageUrl, e.status, e.path,
            m.id, m.firstName, m.lastName, m.jobTitle, m.imageUrl,
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
