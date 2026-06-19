package com.humano.repository.hr;

import com.humano.domain.hr.OrganizationalUnit;
import com.humano.repository.hr.projection.OrganizationalUnitHierarchyRow;
import com.humano.repository.hr.projection.UnitHeadcountRow;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link OrganizationalUnit} entity.
 */
@Repository
public interface OrganizationalUnitRepository
    extends JpaRepository<OrganizationalUnit, UUID>, JpaSpecificationExecutor<OrganizationalUnit> {
    Page<OrganizationalUnit> findByParentUnitIsNull(Pageable pageable);

    Page<OrganizationalUnit> findByParentUnitId(UUID parentId, Pageable pageable);

    /**
     * Fetches the org-unit subtree (the unit at {@code rootPath} plus all transitive
     * descendants) with parent reference and a minimal manager descriptor in a single
     * query. Backed by {@code idx_organizational_unit_path}.
     */
    @Query(
        """
        SELECT new com.humano.repository.hr.projection.OrganizationalUnitHierarchyRow(
            u.id, u.name, u.type, u.path,
            p.id, p.name,
            m.id, m.firstName, m.lastName, m.jobTitle, m.imageUrl
        )
        FROM OrganizationalUnit u
        LEFT JOIN u.parentUnit p
        LEFT JOIN u.manager m
        WHERE u.path = :rootPath OR u.path LIKE CONCAT(:rootPath, '/%')
        """
    )
    List<OrganizationalUnitHierarchyRow> findSubtree(@Param("rootPath") String rootPath);

    /**
     * Projects all root units (no parent) with manager descriptor. Used to render the
     * full top-level org chart when no specific root is given.
     */
    @Query(
        """
        SELECT new com.humano.repository.hr.projection.OrganizationalUnitHierarchyRow(
            u.id, u.name, u.type, u.path,
            p.id, p.name,
            m.id, m.firstName, m.lastName, m.jobTitle, m.imageUrl
        )
        FROM OrganizationalUnit u
        LEFT JOIN u.parentUnit p
        LEFT JOIN u.manager m
        WHERE u.parentUnit IS NULL
        ORDER BY u.name
        """
    )
    List<OrganizationalUnitHierarchyRow> findAllRoots();

    /**
     * Projects an arbitrary set of units by id — used to materialise an ancestor chain
     * parsed from the path string (one IN-list lookup, no recursion).
     */
    @Query(
        """
        SELECT new com.humano.repository.hr.projection.OrganizationalUnitHierarchyRow(
            u.id, u.name, u.type, u.path,
            p.id, p.name,
            m.id, m.firstName, m.lastName, m.jobTitle, m.imageUrl
        )
        FROM OrganizationalUnit u
        LEFT JOIN u.parentUnit p
        LEFT JOIN u.manager m
        WHERE u.id IN :ids
        """
    )
    List<OrganizationalUnitHierarchyRow> findHierarchyRowsByIds(@Param("ids") Collection<UUID> ids);

    /**
     * Returns every ancestor of the unit whose path is {@code leafPath}, root-first.
     * <p>
     * Single query — no per-level lazy fetch. Matches any unit whose stored path is
     * a slash-bounded prefix of the leaf path (or equal to it), which by the
     * materialized-path invariant is exactly the ancestor chain.
     */
    @Query(
        """
        SELECT new com.humano.repository.hr.projection.OrganizationalUnitHierarchyRow(
            u.id, u.name, u.type, u.path,
            p.id, p.name,
            m.id, m.firstName, m.lastName, m.jobTitle, m.imageUrl
        )
        FROM OrganizationalUnit u
        LEFT JOIN u.parentUnit p
        LEFT JOIN u.manager m
        WHERE u.path = :leafPath OR :leafPath LIKE CONCAT(u.path, '/%')
        ORDER BY LENGTH(u.path)
        """
    )
    List<OrganizationalUnitHierarchyRow> findAncestorChainByLeafPath(@Param("leafPath") String leafPath);

    /**
     * Single GROUP BY query returning per-unit employee counts for every unit in the
     * subtree at {@code rootPath}. Units with no employees still appear (count = 0)
     * because the join goes outward from the unit side.
     */
    @Query(
        """
        SELECT new com.humano.repository.hr.projection.UnitHeadcountRow(u.id, COUNT(e.id))
        FROM OrganizationalUnit u
        LEFT JOIN Employee e ON e.unit = u
        WHERE u.path = :rootPath OR u.path LIKE CONCAT(:rootPath, '/%')
        GROUP BY u.id
        """
    )
    List<UnitHeadcountRow> findSubtreeHeadcounts(@Param("rootPath") String rootPath);

    /**
     * One GROUP BY query returning the direct employee count for every root unit
     * (no parent). Used by the roots overview when {@code includeHeadcount=true} —
     * single SQL round-trip regardless of root count.
     */
    @Query(
        """
        SELECT new com.humano.repository.hr.projection.UnitHeadcountRow(u.id, COUNT(e.id))
        FROM OrganizationalUnit u
        LEFT JOIN Employee e ON e.unit = u
        WHERE u.parentUnit IS NULL
        GROUP BY u.id
        """
    )
    List<UnitHeadcountRow> findRootDirectHeadcounts();

    /**
     * Rewrites the materialized-path prefix on every descendant of a unit whose own
     * path has just changed (reorg under a new parent or rename). One bulk UPDATE.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE OrganizationalUnit u
        SET u.path = CONCAT(:newPrefix, SUBSTRING(u.path, LENGTH(:oldPrefix) + 1))
        WHERE u.path LIKE CONCAT(:oldPrefix, '/%')
        """
    )
    int rewriteDescendantPaths(@Param("oldPrefix") String oldPrefix, @Param("newPrefix") String newPrefix);
}
