import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import {
  EmployeeHierarchyResponse,
  EmployeeTreeNode,
  OrganizationalUnitHierarchyResponse,
  HierarchyAncestorsResponse,
} from '../models/hierarchy.model';

/**
 * Service for fetching HR hierarchy data (people trees and org-unit trees)
 * from the backend REST API.
 *
 * All endpoints are read-only and tuned for low DB cost:
 * each route is backed by at most one or two SQL queries.
 */
@Injectable({ providedIn: 'root' })
export class HrHierarchyService {
  private readonly baseUrl = '/api/hr/hierarchy';
  private readonly http = inject(HttpClient);

  /**
   * Fetches the people-tree subtree rooted at the given employee,
   * optionally trimmed to maxDepth levels below the root.
   *
   * @param employeeId root employee ID
   * @param maxDepth max levels below the root; omit for no limit
   */
  getEmployeeSubtree(employeeId: string, maxDepth?: number): Observable<EmployeeHierarchyResponse> {
    const params: Record<string, any> = {};
    if (maxDepth !== undefined) {
      params['maxDepth'] = maxDepth;
    }
    return this.http.get<EmployeeHierarchyResponse>(`${this.baseUrl}/employees/${encodeURIComponent(employeeId)}/subtree`, { params });
  }

  /**
   * Fetches the root-to-leaf manager chain for the given employee
   * (root manager first, the employee themselves last).
   *
   * @param employeeId root employee ID
   */
  getEmployeeAncestors(employeeId: string): Observable<HierarchyAncestorsResponse> {
    return this.http.get<HierarchyAncestorsResponse>(`${this.baseUrl}/employees/${encodeURIComponent(employeeId)}/ancestors`);
  }

  /**
   * Fetches every top-level organizational unit as a flat list.
   * Pass includeHeadcount=true to attach per-root employee counts.
   *
   * @param includeHeadcount if true, includes per-unit employee counts
   */
  getOrganizationalUnitRoots(includeHeadcount = false): Observable<OrganizationalUnitHierarchyResponse> {
    return this.http.get<OrganizationalUnitHierarchyResponse>(`${this.baseUrl}/units/roots`, {
      params: { includeHeadcount: includeHeadcount.toString() },
    });
  }

  /**
   * Fetches the org-unit subtree rooted at the given unit.
   *
   * @param unitId root unit ID
   * @param maxDepth max levels below the root; omit for no limit
   * @param includeHeadcount if true, includes per-unit employee counts
   */
  getOrganizationalUnitSubtree(
    unitId: string,
    maxDepth?: number,
    includeHeadcount = false,
  ): Observable<OrganizationalUnitHierarchyResponse> {
    const params: Record<string, any> = {
      includeHeadcount: includeHeadcount.toString(),
    };
    if (maxDepth !== undefined) {
      params['maxDepth'] = maxDepth;
    }
    return this.http.get<OrganizationalUnitHierarchyResponse>(`${this.baseUrl}/units/${encodeURIComponent(unitId)}/subtree`, { params });
  }

  /**
   * Fetches the root-to-leaf parent chain for the given organizational unit
   * (top-level unit first, the unit itself last).
   *
   * @param unitId unit ID
   */
  getOrganizationalUnitAncestors(unitId: string): Observable<HierarchyAncestorsResponse> {
    return this.http.get<HierarchyAncestorsResponse>(`${this.baseUrl}/units/${encodeURIComponent(unitId)}/ancestors`);
  }

  /**
   * Fetches every employee whose unit lies in the subtree rooted at the given unit.
   * Single-query fused view — each node carries its unit reference.
   *
   * @param unitId root unit ID
   */
  getEmployeesInUnitSubtree(unitId: string): Observable<EmployeeTreeNode[]> {
    return this.http.get<EmployeeTreeNode[]>(`${this.baseUrl}/units/${encodeURIComponent(unitId)}/employees`);
  }
}
