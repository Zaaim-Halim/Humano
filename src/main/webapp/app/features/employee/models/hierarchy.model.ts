/**
 * EmployeRef — minimal reference to an employee (id, full name, job title)
 * Used to display manager/employee chips without full profile lookup
 */
export interface EmployeeRef {
  id: string;
  fullName: string;
  jobTitle: string | null;
  imageUrl: string | null;
}

/**
 * OrganizationalUnitRef — minimal reference to a unit (id, name, type, path)
 * Used to display unit metadata in employee tree nodes
 */
export interface OrganizationalUnitRef {
  id: string;
  name: string;
  type: string; // OrganizationalUnitType enum
  path: string;
}

/**
 * EmployeeTreeNode — one node in the people (manager) tree
 * Carries both people-tree fields and the unit the employee belongs to
 */
export interface EmployeeTreeNode {
  id: string;
  fullName: string;
  jobTitle: string | null;
  imageUrl: string | null;
  status: string; // EmployeeStatus enum
  depth: number;
  path: string;
  manager: EmployeeRef | null;
  unit: OrganizationalUnitRef | null;
  /** Direct reports in the full hierarchy (tree responses only; `children` may be trimmed). */
  directReports?: number | null;
  /** All reports below this person (tree responses only). */
  totalReports?: number | null;
  children?: EmployeeTreeNode[];
}

/**
 * EmployeeHierarchyResponse — envelope for a single employee subtree
 * Provides metadata about the tree (totalNodes, maxDepth) and the root node
 */
/** `GET /employees/tree` — the whole reporting forest; totals describe the full hierarchy. */
export interface EmployeeForestResponse {
  totalPeople: number;
  managers: number;
  levels: number;
  roots: EmployeeTreeNode[];
}

export interface EmployeeHierarchyResponse {
  rootId: string;
  totalNodes: number;
  maxDepth: number;
  root: EmployeeTreeNode;
}

/**
 * OrganizationalUnitTreeNode — one node in the org-unit (org chart) tree
 * Carries unit fields, manager descriptor, and optional headcount
 */
export interface OrganizationalUnitTreeNode {
  id: string;
  name: string;
  type: string; // OrganizationalUnitType enum
  depth: number;
  path: string;
  parentUnitId: string | null;
  parentUnitName: string | null;
  manager: EmployeeRef | null;
  // Backend `Long headcount` — populated when includeHeadcount is requested,
  // null otherwise (Jackson always emits the record component).
  headcount?: number | null;
  /** Sub-units in the full hierarchy (`children` may be trimmed by depth). */
  childCount?: number | null;
  /** People in this unit and all its sub-units (when headcount was requested). */
  totalHeadcount?: number | null;
  children?: OrganizationalUnitTreeNode[];
}

/**
 * OrganizationalUnitHierarchyResponse — envelope for org-unit hierarchy response
 * When a specific root is requested, rootId is set and roots contains one tree
 * When asking for the whole company chart, rootId is null and roots contains all top-level units
 */
export interface OrganizationalUnitHierarchyResponse {
  rootId: string | null;
  totalNodes: number;
  maxDepth: number;
  roots: OrganizationalUnitTreeNode[];
}

/**
 * HierarchyAncestorsResponse — envelope for ancestor chain response
 * Contains the root-to-leaf chain for either people or org-unit hierarchies
 */
export interface HierarchyAncestorsResponse {
  ancestors: (EmployeeRef | OrganizationalUnitRef)[];
}
