package com.humano.dto.hr.responses.hierarchy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.humano.domain.enumeration.hr.OrganizationalUnitType;
import java.util.List;
import java.util.UUID;

/**
 * One node of the organizational-unit (org chart) tree. Carries the unit's own
 * fields plus a manager descriptor and an optional headcount (populated only
 * when {@code includeHeadcount=true} is requested by the caller).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OrganizationalUnitTreeNode(
    UUID id,
    String name,
    OrganizationalUnitType type,
    int depth,
    String path,
    UUID parentUnitId,
    String parentUnitName,
    EmployeeRef manager,
    Long headcount,
    List<OrganizationalUnitTreeNode> children
) {}
