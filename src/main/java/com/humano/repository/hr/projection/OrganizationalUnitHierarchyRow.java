package com.humano.repository.hr.projection;

import com.humano.domain.enumeration.hr.OrganizationalUnitType;
import java.util.UUID;

/**
 * Flat row projected directly from JPQL for organizational-unit hierarchy reads.
 * <p>
 * One row per unit in the requested subtree, with parent reference and a minimal
 * manager descriptor (id + names + job title) fetched on the same query.
 * Headcount is loaded separately as a grouped aggregate when requested.
 */
public record OrganizationalUnitHierarchyRow(
    UUID id,
    String name,
    OrganizationalUnitType type,
    String path,
    UUID parentUnitId,
    String parentUnitName,
    UUID managerId,
    String managerFirstName,
    String managerLastName,
    String managerJobTitle
) {}
