package com.humano.service.hr;

import com.humano.domain.hr.OrganizationalUnit;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.responses.hierarchy.EmployeeForestResponse;
import com.humano.dto.hr.responses.hierarchy.EmployeeHierarchyResponse;
import com.humano.dto.hr.responses.hierarchy.EmployeeRef;
import com.humano.dto.hr.responses.hierarchy.EmployeeTreeNode;
import com.humano.dto.hr.responses.hierarchy.HierarchyAncestorsResponse;
import com.humano.dto.hr.responses.hierarchy.OrganizationalUnitHierarchyResponse;
import com.humano.dto.hr.responses.hierarchy.OrganizationalUnitRef;
import com.humano.dto.hr.responses.hierarchy.OrganizationalUnitTreeNode;
import com.humano.repository.hr.OrganizationalUnitRepository;
import com.humano.repository.hr.projection.EmployeeHierarchyRow;
import com.humano.repository.hr.projection.OrganizationalUnitHierarchyRow;
import com.humano.repository.hr.projection.UnitHeadcountRow;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.EntityNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side service for the HR hierarchy API.
 * <p>
 * Implements both the people tree (manager → subordinates) and the organizational-unit
 * tree (parent unit → sub-units) on top of materialized paths. Each public read does
 * at most two SQL round-trips:
 * <ul>
 *   <li>One projection query that pulls the entire subtree with embedded unit /
 *       manager metadata via JOINs (no N+1 lazy fetches).</li>
 *   <li>Optionally one GROUP BY query for per-unit headcounts.</li>
 * </ul>
 * Tree assembly happens in-memory in a single O(N) pass keyed by id.
 * <p>
 * Two write-side helpers ({@code relocateEmployee}, {@code relocateUnit}) keep the
 * stored paths consistent after a reorg by issuing a single bulk UPDATE per move
 * instead of cascading through the Hibernate session.
 */
@Service
public class HrHierarchyService {

    private static final Logger log = LoggerFactory.getLogger(HrHierarchyService.class);

    /** Cap on the path-segment count we are willing to project — guards against pathological strings. */
    private static final int MAX_PATH_SEGMENTS = 64;

    private final EmployeeRepository employeeRepository;
    private final OrganizationalUnitRepository organizationalUnitRepository;

    public HrHierarchyService(EmployeeRepository employeeRepository, OrganizationalUnitRepository organizationalUnitRepository) {
        this.employeeRepository = employeeRepository;
        this.organizationalUnitRepository = organizationalUnitRepository;
    }

    // ============================================================================
    // People tree (Employee → subordinates)
    // ============================================================================

    /**
     * Returns the people-tree subtree rooted at {@code rootEmployeeId}, optionally
     * trimmed to {@code maxDepth} levels below the root.
     *
     * @param rootEmployeeId the manager whose org is being rendered
     * @param maxDepth maximum levels of subordinates to include below the root
     *                 (null = no limit; 0 = root only)
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public EmployeeHierarchyResponse getEmployeeSubtree(UUID rootEmployeeId, Integer maxDepth) {
        log.debug("Building employee subtree for {} (maxDepth={})", rootEmployeeId, maxDepth);

        Employee root = employeeRepository
            .findById(rootEmployeeId)
            .orElseThrow(() -> EntityNotFoundException.create("Employee", rootEmployeeId));

        List<EmployeeHierarchyRow> rows = employeeRepository.findManagerSubtree(root.getPath());
        int rootDepth = countSegments(root.getPath());

        List<EmployeeHierarchyRow> filtered = trimByDepth(rows, EmployeeHierarchyRow::path, rootDepth, maxDepth);
        EmployeeTreeNode tree = assembleEmployeeForest(filtered, reportCounts(rows)).get(rootEmployeeId);

        int returned = filtered.size();
        int reachedDepth = filtered.stream().mapToInt(r -> countSegments(r.path()) - rootDepth).max().orElse(0);
        return new EmployeeHierarchyResponse(rootEmployeeId, returned, reachedDepth, tree);
    }

    /**
     * Returns the whole reporting hierarchy as a forest — every employee without a manager with
     * their subtree — trimmed to {@code maxDepth} levels below the top. One query; every node
     * carries its direct/total report counts from the full hierarchy, so a client can render a
     * shallow tree and lazily load deeper levels via {@link #getEmployeeSubtree}.
     *
     * @param maxDepth levels below each top-level employee (null = no limit; 0 = top level only)
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public EmployeeForestResponse getEmployeeTree(Integer maxDepth) {
        log.debug("Building employee forest (maxDepth={})", maxDepth);

        List<EmployeeHierarchyRow> rows = employeeRepository.findAllHierarchyRows();
        Map<String, int[]> counts = reportCounts(rows);
        List<EmployeeHierarchyRow> filtered = trimByDepth(rows, EmployeeHierarchyRow::path, 1, maxDepth);

        Map<UUID, EmployeeTreeNode> forest = assembleEmployeeForest(filtered, counts);
        List<EmployeeTreeNode> roots = new ArrayList<>();
        for (EmployeeHierarchyRow row : filtered) {
            if (row.managerId() == null) {
                roots.add(forest.get(row.id()));
            }
        }
        roots.sort(Comparator.comparingInt((EmployeeTreeNode n) -> n.totalReports() == null ? 0 : n.totalReports()).reversed());

        int managers = (int) counts.values().stream().filter(c -> c[0] > 0).count();
        int levels = rows.stream().mapToInt(r -> countSegments(r.path())).max().orElse(0);
        return new EmployeeForestResponse(rows.size(), managers, levels, roots);
    }

    /**
     * Returns the ancestor chain of an employee (root manager → ... → the employee
     * itself), in two queries: one to load the leaf's path, one IN-list lookup.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public HierarchyAncestorsResponse getEmployeeAncestors(UUID employeeId) {
        log.debug("Resolving employee ancestor chain for {}", employeeId);

        Employee leaf = employeeRepository.findById(employeeId).orElseThrow(() -> EntityNotFoundException.create("Employee", employeeId));

        List<UUID> chainIds = parseUuidPath(leaf.getPath());
        if (chainIds.isEmpty()) {
            return HierarchyAncestorsResponse.forEmployee(employeeId, List.of());
        }

        List<EmployeeHierarchyRow> rows = employeeRepository.findHierarchyRowsByIds(chainIds);
        Map<UUID, EmployeeHierarchyRow> byId = indexById(rows, EmployeeHierarchyRow::id);

        List<EmployeeTreeNode> chain = new ArrayList<>(chainIds.size());
        for (UUID id : chainIds) {
            EmployeeHierarchyRow row = byId.get(id);
            if (row != null) {
                chain.add(toEmployeeNode(row, countSegments(row.path()), null, Collections.emptyList()));
            }
        }
        return HierarchyAncestorsResponse.forEmployee(employeeId, chain);
    }

    // ============================================================================
    // Org-unit tree (OrganizationalUnit → sub-units)
    // ============================================================================

    /**
     * Returns the org-unit subtree rooted at {@code rootUnitId}.
     *
     * @param maxDepth max levels below the root (null = no limit; 0 = root only)
     * @param includeHeadcount when true, attaches a per-unit employee headcount
     *                         (one extra GROUP BY query)
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public OrganizationalUnitHierarchyResponse getOrganizationalUnitSubtree(UUID rootUnitId, Integer maxDepth, boolean includeHeadcount) {
        log.debug("Building org-unit subtree for {} (maxDepth={}, includeHeadcount={})", rootUnitId, maxDepth, includeHeadcount);

        OrganizationalUnit root = organizationalUnitRepository
            .findById(rootUnitId)
            .orElseThrow(() -> EntityNotFoundException.create("OrganizationalUnit", rootUnitId));

        List<OrganizationalUnitHierarchyRow> rows = organizationalUnitRepository.findSubtree(root.getPath());
        int rootDepth = countSegments(root.getPath());
        List<OrganizationalUnitHierarchyRow> filtered = trimByDepth(rows, OrganizationalUnitHierarchyRow::path, rootDepth, maxDepth);

        Map<UUID, Long> headcounts = includeHeadcount ? loadHeadcounts(root.getPath()) : null;

        OrganizationalUnitTreeNode tree = assembleUnitForest(filtered, unitRollups(rows, headcounts)).get(rootUnitId);
        int reachedDepth = filtered.stream().mapToInt(r -> countSegments(r.path()) - rootDepth).max().orElse(0);
        return new OrganizationalUnitHierarchyResponse(rootUnitId, filtered.size(), reachedDepth, List.of(tree));
    }

    /**
     * Returns the whole org-unit forest — every unit without a parent with its sub-units —
     * trimmed to {@code maxDepth} levels. Two queries at most (units + one GROUP BY for
     * headcounts); every node carries its sub-unit count and, with headcounts, the rolled-up
     * total of its subtree.
     *
     * @param maxDepth levels below each top-level unit (null = no limit; 0 = top level only)
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public OrganizationalUnitHierarchyResponse getOrganizationalUnitTree(Integer maxDepth, boolean includeHeadcount) {
        log.debug("Building org-unit forest (maxDepth={}, includeHeadcount={})", maxDepth, includeHeadcount);

        List<OrganizationalUnitHierarchyRow> rows = organizationalUnitRepository.findAllHierarchyRows();
        Map<UUID, Long> headcounts = includeHeadcount ? loadAllHeadcounts() : null;
        List<OrganizationalUnitHierarchyRow> filtered = trimByDepth(rows, OrganizationalUnitHierarchyRow::path, 1, maxDepth);

        Map<UUID, OrganizationalUnitTreeNode> forest = assembleUnitForest(filtered, unitRollups(rows, headcounts));
        List<OrganizationalUnitTreeNode> roots = new ArrayList<>();
        for (OrganizationalUnitHierarchyRow row : filtered) {
            if (row.parentUnitId() == null) {
                roots.add(forest.get(row.id()));
            }
        }
        int levels = rows.stream().mapToInt(r -> countSegments(r.path())).max().orElse(0);
        return new OrganizationalUnitHierarchyResponse(null, rows.size(), levels, roots);
    }

    /**
     * Returns the org-unit ancestor chain (root → ... → leaf), root-first.
     * <p>
     * Two queries total: one to fetch the leaf's path, one prefix-match query that
     * returns every ancestor row. No lazy-fetch walk through {@code parentUnit}.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public HierarchyAncestorsResponse getOrganizationalUnitAncestors(UUID unitId) {
        log.debug("Resolving org-unit ancestor chain for {}", unitId);

        OrganizationalUnit leaf = organizationalUnitRepository
            .findById(unitId)
            .orElseThrow(() -> EntityNotFoundException.create("OrganizationalUnit", unitId));

        List<OrganizationalUnitHierarchyRow> rows = organizationalUnitRepository.findAncestorChainByLeafPath(leaf.getPath());

        List<OrganizationalUnitTreeNode> chain = new ArrayList<>(rows.size());
        for (OrganizationalUnitHierarchyRow row : rows) {
            chain.add(toUnitNode(row, countSegments(row.path()), null, Collections.emptyList()));
        }
        return HierarchyAncestorsResponse.forUnit(unitId, chain);
    }

    /**
     * Returns every employee whose unit lies in the subtree rooted at {@code rootUnitId}.
     * Single JOIN query — bounded by the size of the requested subtree.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<EmployeeTreeNode> getEmployeesInUnitSubtree(UUID rootUnitId) {
        log.debug("Listing employees under org-unit subtree {}", rootUnitId);

        OrganizationalUnit root = organizationalUnitRepository
            .findById(rootUnitId)
            .orElseThrow(() -> EntityNotFoundException.create("OrganizationalUnit", rootUnitId));

        List<EmployeeHierarchyRow> rows = employeeRepository.findEmployeesInUnitSubtree(root.getPath());
        List<EmployeeTreeNode> result = new ArrayList<>(rows.size());
        for (EmployeeHierarchyRow row : rows) {
            result.add(toEmployeeNode(row, countSegments(row.path()), null, Collections.emptyList()));
        }
        return result;
    }

    // ============================================================================
    // Internal — tree assembly
    // ============================================================================

    /**
     * Assembles employee rows into trees; returns every node by id (roots are the nodes whose
     * manager is absent from {@code rows}). {@code counts} maps id → [direct, total] reports.
     */
    private Map<UUID, EmployeeTreeNode> assembleEmployeeForest(List<EmployeeHierarchyRow> rows, Map<String, int[]> counts) {
        Map<UUID, List<EmployeeTreeNode>> childrenByParent = new HashMap<>(rows.size());
        Map<UUID, EmployeeHierarchyRow> rowsById = indexById(rows, EmployeeHierarchyRow::id);

        // Materialise nodes bottom-up by depth so each node is built with its finished children.
        List<EmployeeHierarchyRow> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparingInt(r -> -countSegments(r.path()))); // deepest first

        Map<UUID, EmployeeTreeNode> nodesById = new HashMap<>(rows.size());
        for (EmployeeHierarchyRow row : sorted) {
            List<EmployeeTreeNode> childList = childrenByParent.getOrDefault(row.id(), Collections.emptyList());
            EmployeeTreeNode node = toEmployeeNode(row, countSegments(row.path()), counts.get(row.id().toString()), childList);
            nodesById.put(row.id(), node);
            if (row.managerId() != null && rowsById.containsKey(row.managerId())) {
                childrenByParent.computeIfAbsent(row.managerId(), k -> new ArrayList<>()).add(node);
            }
        }
        return nodesById;
    }

    /** Assembles unit rows into trees; returns every node by id. */
    private Map<UUID, OrganizationalUnitTreeNode> assembleUnitForest(
        List<OrganizationalUnitHierarchyRow> rows,
        Map<UUID, UnitRollup> rollups
    ) {
        Map<UUID, List<OrganizationalUnitTreeNode>> childrenByParent = new HashMap<>(rows.size());
        Map<UUID, OrganizationalUnitHierarchyRow> rowsById = indexById(rows, OrganizationalUnitHierarchyRow::id);

        List<OrganizationalUnitHierarchyRow> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparingInt(r -> -countSegments(r.path())));

        Map<UUID, OrganizationalUnitTreeNode> nodesById = new HashMap<>(rows.size());
        for (OrganizationalUnitHierarchyRow row : sorted) {
            List<OrganizationalUnitTreeNode> childList = childrenByParent.getOrDefault(row.id(), Collections.emptyList());
            OrganizationalUnitTreeNode node = toUnitNode(row, countSegments(row.path()), rollups.get(row.id()), childList);
            nodesById.put(row.id(), node);
            if (row.parentUnitId() != null && rowsById.containsKey(row.parentUnitId())) {
                childrenByParent.computeIfAbsent(row.parentUnitId(), k -> new ArrayList<>()).add(node);
            }
        }
        return nodesById;
    }

    /**
     * Direct and total report counts for every employee in {@code rows}, derived from the
     * materialized paths in one O(N × depth) pass: id → [direct, total]. Keyed by the path
     * segment string so no UUID parsing is needed.
     */
    private Map<String, int[]> reportCounts(List<EmployeeHierarchyRow> rows) {
        Map<String, int[]> counts = new HashMap<>(rows.size() * 2);
        for (EmployeeHierarchyRow row : rows) {
            counts.computeIfAbsent(row.id().toString(), k -> new int[2]);
            String[] segments = row.path() == null ? new String[0] : row.path().substring(1).split("/");
            for (int i = 0; i < segments.length - 1; i++) {
                int[] c = counts.computeIfAbsent(segments[i], k -> new int[2]);
                c[1]++;
                if (i == segments.length - 2) {
                    c[0]++;
                }
            }
        }
        return counts;
    }

    /** Per-unit figures from the full hierarchy: sub-unit count, own headcount and subtree total. */
    private record UnitRollup(int childCount, Long headcount, Long totalHeadcount) {}

    /**
     * Sub-unit counts (always) and rolled-up headcounts (when {@code headcounts} is non-null)
     * for every unit in {@code rows}. Uses parent ids rather than paths — unit paths are built
     * from names — and sums each subtree once (post-order), so the pass is O(N).
     */
    private Map<UUID, UnitRollup> unitRollups(List<OrganizationalUnitHierarchyRow> rows, Map<UUID, Long> headcounts) {
        Map<UUID, List<UUID>> childrenOf = new HashMap<>();
        for (OrganizationalUnitHierarchyRow row : rows) {
            if (row.parentUnitId() != null) {
                childrenOf.computeIfAbsent(row.parentUnitId(), k -> new ArrayList<>()).add(row.id());
            }
        }
        Map<UUID, Long> totals = new HashMap<>(rows.size());
        if (headcounts != null) {
            // Deepest first, so every child's total is known before its parent's.
            List<OrganizationalUnitHierarchyRow> sorted = new ArrayList<>(rows);
            sorted.sort(Comparator.comparingInt(r -> -countSegments(r.path())));
            for (OrganizationalUnitHierarchyRow row : sorted) {
                long total = headcounts.getOrDefault(row.id(), 0L);
                for (UUID child : childrenOf.getOrDefault(row.id(), List.of())) {
                    total += totals.getOrDefault(child, 0L);
                }
                totals.put(row.id(), total);
            }
        }
        Map<UUID, UnitRollup> rollups = new HashMap<>(rows.size());
        for (OrganizationalUnitHierarchyRow row : rows) {
            rollups.put(
                row.id(),
                new UnitRollup(
                    childrenOf.getOrDefault(row.id(), List.of()).size(),
                    headcounts == null ? null : headcounts.getOrDefault(row.id(), 0L),
                    headcounts == null ? null : totals.get(row.id())
                )
            );
        }
        return rollups;
    }

    private EmployeeTreeNode toEmployeeNode(EmployeeHierarchyRow row, int depth, int[] counts, List<EmployeeTreeNode> children) {
        EmployeeRef manager = EmployeeRef.of(
            row.managerId(),
            row.managerFirstName(),
            row.managerLastName(),
            row.managerJobTitle(),
            row.managerImageUrl()
        );
        OrganizationalUnitRef unit = new OrganizationalUnitRef(row.unitId(), row.unitName(), row.unitType(), row.unitPath());
        String fullName = joinFullName(row.firstName(), row.lastName());
        return new EmployeeTreeNode(
            row.id(),
            fullName,
            row.jobTitle(),
            row.imageUrl(),
            row.status(),
            depth,
            row.path(),
            manager,
            unit,
            counts == null ? null : counts[0],
            counts == null ? null : counts[1],
            children
        );
    }

    private static String joinFullName(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        if (first.isEmpty()) return last;
        if (last.isEmpty()) return first;
        return first + " " + last;
    }

    private OrganizationalUnitTreeNode toUnitNode(
        OrganizationalUnitHierarchyRow row,
        int depth,
        UnitRollup rollup,
        List<OrganizationalUnitTreeNode> children
    ) {
        EmployeeRef manager = EmployeeRef.of(
            row.managerId(),
            row.managerFirstName(),
            row.managerLastName(),
            row.managerJobTitle(),
            row.managerImageUrl()
        );
        return new OrganizationalUnitTreeNode(
            row.id(),
            row.name(),
            row.type(),
            depth,
            row.path(),
            row.parentUnitId(),
            row.parentUnitName(),
            manager,
            rollup == null ? null : rollup.headcount(),
            rollup == null ? null : rollup.childCount(),
            rollup == null ? null : rollup.totalHeadcount(),
            children
        );
    }

    private Map<UUID, Long> loadHeadcounts(String rootPath) {
        Map<UUID, Long> headcounts = new HashMap<>();
        for (UnitHeadcountRow row : organizationalUnitRepository.findSubtreeHeadcounts(rootPath)) {
            headcounts.put(row.unitId(), row.headcount());
        }
        return headcounts;
    }

    private Map<UUID, Long> loadAllHeadcounts() {
        Map<UUID, Long> headcounts = new HashMap<>();
        for (UnitHeadcountRow row : organizationalUnitRepository.findAllHeadcounts()) {
            headcounts.put(row.unitId(), row.headcount());
        }
        return headcounts;
    }

    // ============================================================================
    // Internal — path helpers
    // ============================================================================

    /**
     * Trims rows whose depth in the tree is greater than {@code rootDepth + maxDepth}.
     * A null {@code maxDepth} disables the trim and returns the input as-is.
     */
    private <T> List<T> trimByDepth(List<T> rows, java.util.function.Function<T, String> pathOf, int rootDepth, Integer maxDepth) {
        if (maxDepth == null) {
            return rows;
        }
        int cap = rootDepth + maxDepth;
        List<T> trimmed = new ArrayList<>(rows.size());
        for (T row : rows) {
            if (countSegments(pathOf.apply(row)) <= cap) {
                trimmed.add(row);
            }
        }
        return trimmed;
    }

    private List<UUID> parseUuidPath(String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        String[] segments = path.split("/");
        List<UUID> ids = new ArrayList<>(segments.length);
        for (String segment : segments) {
            if (segment.isEmpty()) continue;
            try {
                ids.add(UUID.fromString(segment));
            } catch (IllegalArgumentException ignored) {
                // Path was not built from UUIDs (older rows or test data) — skip silently.
            }
            if (ids.size() >= MAX_PATH_SEGMENTS) break;
        }
        return ids;
    }

    private int countSegments(String path) {
        if (path == null || path.isEmpty()) return 0;
        int count = 0;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '/') count++;
        }
        return count;
    }

    private <T> Map<UUID, T> indexById(List<T> rows, java.util.function.Function<T, UUID> idOf) {
        Map<UUID, T> index = new LinkedHashMap<>(rows.size());
        for (T row : rows) {
            index.put(idOf.apply(row), row);
        }
        return index;
    }
}
