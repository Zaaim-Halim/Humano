package com.humano.dto.hr.responses.hierarchy;

import java.util.List;
import java.util.UUID;

/**
 * Ordered chain of ancestors (root first, leaf last) of a single node — useful
 * for breadcrumbs / "where does this person/unit sit in the org" UIs.
 * <p>
 * The chain includes the node itself as its last entry so the frontend can
 * render the full breadcrumb without a separate fetch.
 *
 * @param leafId the node whose ancestor chain was requested
 * @param employeeChain populated when the chain comes from the people tree
 * @param unitChain populated when the chain comes from the org-unit tree
 */
public record HierarchyAncestorsResponse(UUID leafId, List<EmployeeTreeNode> employeeChain, List<OrganizationalUnitTreeNode> unitChain) {
    public static HierarchyAncestorsResponse forEmployee(UUID leafId, List<EmployeeTreeNode> chain) {
        return new HierarchyAncestorsResponse(leafId, chain, null);
    }

    public static HierarchyAncestorsResponse forUnit(UUID leafId, List<OrganizationalUnitTreeNode> chain) {
        return new HierarchyAncestorsResponse(leafId, null, chain);
    }
}
