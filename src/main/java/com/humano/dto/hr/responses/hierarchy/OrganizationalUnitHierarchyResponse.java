package com.humano.dto.hr.responses.hierarchy;

import java.util.List;
import java.util.UUID;

/**
 * Envelope for an org-unit hierarchy response. When the caller passes a specific
 * root, {@code rootId} is set and {@code roots} contains exactly that one tree.
 * When the caller asks for the whole company chart, {@code rootId} is null and
 * {@code roots} contains every top-level unit.
 */
public record OrganizationalUnitHierarchyResponse(UUID rootId, int totalNodes, int maxDepth, List<OrganizationalUnitTreeNode> roots) {}
