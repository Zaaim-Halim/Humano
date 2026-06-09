package com.humano.dto.hr.responses.hierarchy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.humano.domain.enumeration.hr.EmployeeStatus;
import java.util.List;
import java.util.UUID;

/**
 * One node of the employee (people) tree. Carries both people-tree fields and
 * the unit metadata the employee belongs to, so the frontend can render either
 * a manager-tree or a manager-tree-with-org-context view from the same payload.
 * <p>
 * {@code children} is omitted from the JSON when empty so leaf nodes stay light.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record EmployeeTreeNode(
    UUID id,
    String fullName,
    String jobTitle,
    EmployeeStatus status,
    int depth,
    String path,
    EmployeeRef manager,
    OrganizationalUnitRef unit,
    List<EmployeeTreeNode> children
) {}
