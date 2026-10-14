package com.humano.web.rest.hr;

import com.humano.dto.hr.responses.hierarchy.EmployeeForestResponse;
import com.humano.dto.hr.responses.hierarchy.EmployeeHierarchyResponse;
import com.humano.dto.hr.responses.hierarchy.EmployeeTreeNode;
import com.humano.dto.hr.responses.hierarchy.HierarchyAncestorsResponse;
import com.humano.dto.hr.responses.hierarchy.OrganizationalUnitHierarchyResponse;
import com.humano.security.annotation.RequireAuthenticated;
import com.humano.service.hr.HrHierarchyService;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for read-only hierarchy queries.
 * <p>
 * Endpoints are tuned for low DB cost: each route is backed by at most one or two
 * SQL queries (subtree projection + optional headcount aggregate). All returned
 * nodes carry embedded {@code manager} and {@code unit} descriptors so the
 * frontend can render rich org-chart UIs without follow-up lookups.
 *
 * <h2>People tree</h2>
 * <ul>
 *   <li>{@code GET /api/hr/hierarchy/employees/{id}/subtree} — manager + transitive subordinates</li>
 *   <li>{@code GET /api/hr/hierarchy/employees/{id}/ancestors} — root manager → leaf chain</li>
 * </ul>
 *
 * <h2>Org-unit tree</h2>
 * <ul>
 *   <li>{@code GET /api/hr/hierarchy/units/tree} — the whole org-unit forest (+ headcount)</li>
 *   <li>{@code GET /api/hr/hierarchy/employees/tree} — the whole reporting forest with report counts</li>
 *   <li>{@code GET /api/hr/hierarchy/units/{id}/subtree} — unit + descendants (+ headcount)</li>
 *   <li>{@code GET /api/hr/hierarchy/units/{id}/ancestors} — top-level → leaf chain</li>
 *   <li>{@code GET /api/hr/hierarchy/units/{id}/employees} — every employee under a unit subtree</li>
 * </ul>
 */
@RestController
// Org charts are company-wide reference: every signed-in employee of the tenant may read them.
// Nodes carry only directory-level fields (name, title, photo, unit, report counts).
@RequestMapping("/api/hr/hierarchy")
@Validated
public class HrHierarchyResource {

    private static final Logger LOG = LoggerFactory.getLogger(HrHierarchyResource.class);

    private final HrHierarchyService hierarchyService;

    public HrHierarchyResource(HrHierarchyService hierarchyService) {
        this.hierarchyService = hierarchyService;
    }

    // ==================== People tree ====================

    /**
     * Returns the manager-tree subtree rooted at the given employee, optionally
     * trimmed to {@code maxDepth} levels below the root.
     *
     * @param id root employee
     * @param maxDepth max levels below the root; omit for "no limit"
     */
    @GetMapping("/employees/{id}/subtree")
    @RequireAuthenticated
    public ResponseEntity<EmployeeHierarchyResponse> getEmployeeSubtree(
        @PathVariable UUID id,
        @RequestParam(required = false) @Min(0) Integer maxDepth
    ) {
        LOG.debug("REST request to get employee subtree: id={}, maxDepth={}", id, maxDepth);
        return ResponseEntity.ok(hierarchyService.getEmployeeSubtree(id, maxDepth));
    }

    /**
     * Returns the whole reporting hierarchy as a forest (every employee without a manager and
     * their subtree), trimmed to {@code maxDepth} levels. Nodes carry report counts from the full
     * hierarchy; load deeper levels lazily with {@code /employees/{id}/subtree}.
     *
     * @param maxDepth levels below the top-level employees; omit for "no limit"
     */
    @GetMapping("/employees/tree")
    @RequireAuthenticated
    public ResponseEntity<EmployeeForestResponse> getEmployeeTree(@RequestParam(required = false) @Min(0) Integer maxDepth) {
        LOG.debug("REST request to get employee forest: maxDepth={}", maxDepth);
        return ResponseEntity.ok(hierarchyService.getEmployeeTree(maxDepth));
    }

    /**
     * Returns the root-to-leaf manager chain for the given employee — root manager
     * first, the employee themselves last.
     */
    @GetMapping("/employees/{id}/ancestors")
    @RequireAuthenticated
    public ResponseEntity<HierarchyAncestorsResponse> getEmployeeAncestors(@PathVariable UUID id) {
        LOG.debug("REST request to get employee ancestor chain: id={}", id);
        return ResponseEntity.ok(hierarchyService.getEmployeeAncestors(id));
    }

    // ==================== Org-unit tree ====================

    /**
     * Returns the whole org-unit forest (every unit without a parent and its sub-units),
     * trimmed to {@code maxDepth} levels. Nodes carry their sub-unit count; with
     * {@code includeHeadcount=true} also own and rolled-up headcounts.
     */
    @GetMapping("/units/tree")
    @RequireAuthenticated
    public ResponseEntity<OrganizationalUnitHierarchyResponse> getOrganizationalUnitTree(
        @RequestParam(required = false) @Min(0) Integer maxDepth,
        @RequestParam(defaultValue = "false") boolean includeHeadcount
    ) {
        LOG.debug("REST request to get org-unit forest (maxDepth={}, includeHeadcount={})", maxDepth, includeHeadcount);
        return ResponseEntity.ok(hierarchyService.getOrganizationalUnitTree(maxDepth, includeHeadcount));
    }

    /**
     * Returns the org-unit subtree rooted at the given unit.
     *
     * @param maxDepth max levels below the root; omit for "no limit"
     * @param includeHeadcount when true, attaches a per-unit employee headcount
     *                         (adds one GROUP BY query)
     */
    @GetMapping("/units/{id}/subtree")
    @RequireAuthenticated
    public ResponseEntity<OrganizationalUnitHierarchyResponse> getOrganizationalUnitSubtree(
        @PathVariable UUID id,
        @RequestParam(required = false) @Min(0) Integer maxDepth,
        @RequestParam(defaultValue = "false") boolean includeHeadcount
    ) {
        LOG.debug("REST request to get org-unit subtree: id={}, maxDepth={}, includeHeadcount={}", id, maxDepth, includeHeadcount);
        return ResponseEntity.ok(hierarchyService.getOrganizationalUnitSubtree(id, maxDepth, includeHeadcount));
    }

    /**
     * Returns the root-to-leaf parent chain for the given organizational unit —
     * top-level unit first, the unit itself last.
     */
    @GetMapping("/units/{id}/ancestors")
    @RequireAuthenticated
    public ResponseEntity<HierarchyAncestorsResponse> getOrganizationalUnitAncestors(@PathVariable UUID id) {
        LOG.debug("REST request to get org-unit ancestor chain: id={}", id);
        return ResponseEntity.ok(hierarchyService.getOrganizationalUnitAncestors(id));
    }

    /**
     * Returns every employee whose unit lies in the subtree rooted at the given
     * unit. Single-query fused view — each node carries its unit ref.
     */
    @GetMapping("/units/{id}/employees")
    @RequireAuthenticated
    public ResponseEntity<List<EmployeeTreeNode>> getEmployeesInUnitSubtree(@PathVariable UUID id) {
        LOG.debug("REST request to list employees under org-unit subtree: id={}", id);
        return ResponseEntity.ok(hierarchyService.getEmployeesInUnitSubtree(id));
    }
}
