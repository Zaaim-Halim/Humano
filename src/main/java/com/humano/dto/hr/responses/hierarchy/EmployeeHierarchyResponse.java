package com.humano.dto.hr.responses.hierarchy;

import java.util.UUID;

/**
 * Envelope for a single employee subtree response. Lets the frontend know the
 * total number of people in the payload and the deepest level reached, without
 * having to walk the tree itself.
 */
public record EmployeeHierarchyResponse(UUID rootId, int totalNodes, int maxDepth, EmployeeTreeNode root) {}
