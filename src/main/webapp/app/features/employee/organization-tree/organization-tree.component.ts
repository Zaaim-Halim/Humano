import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { Observable, map } from 'rxjs';

import { normalizeHttpError } from 'app/core/api';
import { BadgeTone } from 'app/config/status-map';
import {
  AlertComponent,
  AvatarComponent,
  BadgeComponent,
  ButtonComponent,
  DrawerComponent,
  EmptyStateComponent,
  OrgChartComponent,
  OrgChartNodeDirective,
  PageHeaderComponent,
  SkeletonRowComponent,
  StatTileComponent,
} from 'app/shared/ui';

import { PluralPipe, pluralKey } from 'app/shared/util/format.pipes';

import { HrHierarchyService } from '../services/hr-hierarchy.service';
import { EmployeeTreeNode, OrganizationalUnitTreeNode } from '../models/hierarchy.model';

/** OrganizationalUnitType → tone (badge + card accent). Unmapped types fall back to neutral. */
const TYPE_TONE: Record<string, BadgeTone> = {
  DIRECTORATE: 'brand',
  DIVISION: 'brand',
  BOARD: 'brand',
  DEPARTMENT: 'info',
  SECTOR: 'info',
  SECTION: 'info',
  BRANCH: 'warning',
  OFFICE: 'warning',
  TEAM: 'success',
  SQUAD: 'success',
  CELL: 'success',
  GROUP: 'success',
};

function walk(node: OrganizationalUnitTreeNode, visit: (n: OrganizationalUnitTreeNode) => void): void {
  visit(node);
  (node.children ?? []).forEach(c => walk(c, visit));
}

/**
 * Organization Tree — organizational units as an org chart with leaders and headcount.
 *
 * `/organization-tree` loads the whole unit forest in one request (`/units/tree`, headcounts
 * rolled up server-side); `/organization-tree/:unitId` (or the `rootUnitId` input) focuses on
 * one unit. Selecting a unit opens a side panel with its figures, sub-units and people (fetched
 * on open).
 */
@Component({
  selector: 'hum-organization-tree',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    PluralPipe,
    LucideAngularModule,
    RouterLink,
    PageHeaderComponent,
    StatTileComponent,
    AlertComponent,
    AvatarComponent,
    BadgeComponent,
    ButtonComponent,
    DrawerComponent,
    EmptyStateComponent,
    SkeletonRowComponent,
    OrgChartComponent,
    OrgChartNodeDirective,
  ],
  templateUrl: './organization-tree.component.html',
})
export class OrganizationTreeComponent implements OnInit {
  /** HR opens the person's profile; everyone else sees them in context in the people tree. */
  private readonly canOpenProfiles = inject(AccountService).hasPermission(Permission.READ_EMPLOYEE);

  /** Root unit id when embedded; the route param wins for the routed page. */
  readonly rootUnitId = input<string>();

  protected readonly expandOptions = [
    { depth: 1, label: 'humano.organizationTree.expandTop' },
    { depth: 99, label: 'humano.organizationTree.expandAll' },
  ];
  protected readonly expandDepth = signal(99);

  protected readonly roots = signal<OrganizationalUnitTreeNode[]>([]);
  protected readonly focused = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly stats = signal({ units: 0, levels: 0, people: 0 });

  /** Unit shown in the side panel, with its people (loaded when the panel opens). */
  protected readonly selected = signal<OrganizationalUnitTreeNode | null>(null);
  protected readonly members = signal<EmployeeTreeNode[] | null>(null);
  protected readonly membersError = signal<string | null>(null);
  /** Members grouped by the unit they sit in (own unit first). */
  protected readonly memberGroups = computed(() => {
    const unit = this.selected();
    const list = this.members();
    if (!unit || !list) return [];
    const groups = new Map<string, { name: string; people: EmployeeTreeNode[] }>();
    for (const m of list) {
      const key = m.unit?.id ?? '';
      const group = groups.get(key) ?? { name: m.unit?.name ?? '—', people: [] };
      group.people.push(m);
      groups.set(key, group);
    }
    return [...groups.entries()].sort(([a], [b]) => (a === unit.id ? -1 : b === unit.id ? 1 : 0)).map(([, g]) => g);
  });

  private readonly hierarchy = inject(HrHierarchyService);
  private readonly translate = inject(TranslateService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.reload();
  }
  protected readonly personLink = (id: string): string[] => (this.canOpenProfiles ? ['/employees', id] : ['/people-tree', id]);

  protected readonly tone = (node: OrganizationalUnitTreeNode): BadgeTone => TYPE_TONE[node.type] ?? 'neutral';
  protected readonly typeLabel = (type: string): string => type.charAt(0) + type.slice(1).toLowerCase();
  protected readonly subUnitsLabel = (node: OrganizationalUnitTreeNode): string =>
    this.subUnitsText(node.childCount ?? node.children?.length ?? 0);

  protected reload(): void {
    const unitId = this.rootUnitId() ?? this.route.snapshot.paramMap.get('unitId');
    this.focused.set(unitId);
    this.loading.set(true);
    this.error.set(null);

    const source: Observable<OrganizationalUnitTreeNode[]> = unitId
      ? this.hierarchy.getOrganizationalUnitSubtree(unitId, undefined, true).pipe(map(r => r.roots))
      : this.hierarchy.getOrganizationalUnitTree(undefined, true).pipe(map(r => r.roots));

    source.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: roots => {
        let units = 0;
        let levels = 0;
        roots.forEach(r => walk(r, n => (units++, (levels = Math.max(levels, n.depth - r.depth + 1)))));
        const people = roots.reduce((sum, r) => sum + (r.totalHeadcount ?? 0), 0);
        this.stats.set({ units, levels, people });
        this.roots.set([...roots].sort((a, b) => (b.totalHeadcount ?? 0) - (a.totalHeadcount ?? 0)));
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.loading.set(false);
      },
    });
  }

  protected select(unit: OrganizationalUnitTreeNode): void {
    this.selected.set(unit);
    this.members.set(null);
    this.membersError.set(null);
    this.hierarchy
      .getEmployeesInUnitSubtree(unit.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: people => this.members.set(people),
        error: (err: unknown) => this.membersError.set(normalizeHttpError(err)),
      });
  }

  protected focusOn(unit: OrganizationalUnitTreeNode): void {
    this.selected.set(null);
    void this.router.navigate(['/organization-tree', unit.id]).then(() => this.reload());
  }

  protected showWholeOrganization(): void {
    void this.router.navigate(['/organization-tree']).then(() => this.reload());
  }

  private subUnitsText(count: number): string {
    return String(this.translate.instant(pluralKey('humano.organizationTree.subUnits', count), { count }));
  }
}
