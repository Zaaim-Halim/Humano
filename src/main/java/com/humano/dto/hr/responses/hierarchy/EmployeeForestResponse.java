package com.humano.dto.hr.responses.hierarchy;

import java.util.List;

/**
 * The whole reporting hierarchy as a forest: every top-level employee (no manager) with their
 * subtree, possibly trimmed by depth. Totals describe the full hierarchy, not just the returned
 * nodes, so clients can show accurate figures before expanding anything.
 *
 * @param totalPeople every employee in the tenant
 * @param managers employees with at least one direct report
 * @param levels depth of the deepest reporting line (a lone root counts as 1)
 * @param roots top-level employees, largest organization first
 */
public record EmployeeForestResponse(int totalPeople, int managers, int levels, List<EmployeeTreeNode> roots) {}
