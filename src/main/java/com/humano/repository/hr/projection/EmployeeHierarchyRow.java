package com.humano.repository.hr.projection;

import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.domain.enumeration.hr.OrganizationalUnitType;
import java.util.UUID;

/**
 * Flat row projected directly from JPQL for hierarchy reads.
 * <p>
 * One row per employee in the requested subtree, with both unit metadata and a
 * minimal manager descriptor fetched in the same query to avoid N+1. Tree
 * assembly happens in-memory in the service layer.
 */
public record EmployeeHierarchyRow(
    UUID id,
    String firstName,
    String lastName,
    String jobTitle,
    String imageUrl,
    EmployeeStatus status,
    String path,
    UUID managerId,
    String managerFirstName,
    String managerLastName,
    String managerJobTitle,
    String managerImageUrl,
    UUID unitId,
    String unitName,
    OrganizationalUnitType unitType,
    String unitPath
) {}
