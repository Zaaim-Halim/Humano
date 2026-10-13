import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { Observable, map } from 'rxjs';

import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  AvatarComponent,
  ButtonComponent,
  CardComponent,
  EmptyStateComponent,
  OrgChartComponent,
  OrgChartMemberDirective,
  OrgChartNode,
  OrgChartNodeDirective,
  PageHeaderComponent,
  SkeletonRowComponent,
  StatTileComponent,
} from 'app/shared/ui';

import { HrHierarchyService } from '../services/hr-hierarchy.service';
import { EmployeeTreeNode } from '../models/hierarchy.model';

/** An employee node shaped for the generic org chart (`childCount` = direct reports). */
type PersonNode = EmployeeTreeNode & OrgChartNode & { children?: PersonNode[] };

function toPersonNode(n: EmployeeTreeNode): PersonNode {
  return { ...n, childCount: n.directReports ?? n.children?.length ?? 0, children: n.children?.map(toPersonNode) };
}

interface TreeStats {
  people: number;
  managers: number;
  levels: number;
}

/**
 * People Tree — the reporting hierarchy as an org chart.
 *
 * `/people-tree` loads the whole company in one request (`/employees/tree`), only as deep as
 * the selected level; deeper branches are fetched when expanded. People with neither manager
 * nor reports are listed separately. `/people-tree/:employeeId` (or the `rootEmployeeId` input)
 * focuses on one person's organization. Searching loads every level so all people can match.
 */
@Component({
  selector: 'hum-people-tree',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    LucideAngularModule,
    RouterLink,
    PageHeaderComponent,
    CardComponent,
    StatTileComponent,
    AlertComponent,
    AvatarComponent,
    ButtonComponent,
    EmptyStateComponent,
    SkeletonRowComponent,
    OrgChartComponent,
    OrgChartNodeDirective,
    OrgChartMemberDirective,
  ],
  templateUrl: './people-tree.component.html',
})
export class PeopleTreeComponent implements OnInit {
  /** Root employee id when embedded; the route param wins for the routed page. */
  readonly rootEmployeeId = input<string>();

  protected readonly expandOptions = [
    { depth: 1, label: 'humano.peopleTree.expandLeaders' },
    { depth: 2, label: 'humano.peopleTree.expandTwo' },
    { depth: 99, label: 'humano.peopleTree.expandAll' },
  ];
  protected readonly expandDepth = signal(1);

  /** Top-level people who have reports (or the focused person). */
  protected readonly roots = signal<PersonNode[]>([]);
  /** Top-level people with no reports — not part of any reporting line. */
  protected readonly unassigned = signal<PersonNode[]>([]);
  protected readonly stats = signal<TreeStats>({ people: 0, managers: 0, levels: 0 });
  protected readonly focused = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly subtitle = computed(() => {
    const root = this.focused() ? this.roots()[0] : null;
    return String(
      root
        ? this.translate.instant('humano.peopleTree.focusedOn', { name: root.fullName })
        : this.translate.instant('humano.peopleTree.subtitle'),
    );
  });

  /** Fetches one level of reports for a node whose children were trimmed. */
  protected readonly loadReports = (node: PersonNode): Observable<PersonNode[]> =>
    this.hierarchy.getEmployeeSubtree(node.id, 1).pipe(map(r => (r.root.children ?? []).map(toPersonNode)));

  protected readonly reportsLabel = (node: PersonNode): string =>
    String(this.translate.instant('humano.peopleTree.reports', { direct: node.directReports ?? 0, total: node.totalReports ?? 0 }));

  /** Deepest level already fetched; selecting a deeper level re-fetches once. */
  private loadedDepth = -1;

  private readonly hierarchy = inject(HrHierarchyService);
  private readonly translate = inject(TranslateService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.reload();
  }

  protected setDepth(depth: number): void {
    this.expandDepth.set(depth);
    if (depth > this.loadedDepth) {
      this.reload();
    }
  }

  /** Searching needs every person rendered, so load and expand everything. */
  protected onSearchActive(active: boolean): void {
    if (active && this.expandDepth() < 99) {
      this.setDepth(99);
    }
  }

  protected reload(): void {
    const id = this.rootEmployeeId() ?? this.route.snapshot.paramMap.get('employeeId');
    this.focused.set(id);
    const depth = this.expandDepth() >= 99 ? undefined : this.expandDepth();
    this.loading.set(this.roots().length === 0);
    this.error.set(null);

    const source: Observable<{ roots: EmployeeTreeNode[]; stats: TreeStats }> = id
      ? this.hierarchy.getEmployeeSubtree(id, depth).pipe(
          map(r => ({
            roots: [r.root],
            stats: { people: (r.root.totalReports ?? 0) + 1, managers: 0, levels: 0 },
          })),
        )
      : this.hierarchy
          .getEmployeeTree(depth)
          .pipe(map(r => ({ roots: r.roots, stats: { people: r.totalPeople, managers: r.managers, levels: r.levels } })));

    source.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({ roots, stats }) => {
        const nodes = roots.map(toPersonNode);
        const withReports = id ? nodes : nodes.filter(n => (n.directReports ?? 0) > 0);
        this.roots.set(withReports);
        this.unassigned.set(nodes.filter(n => !withReports.includes(n)));
        this.stats.set(stats);
        this.loadedDepth = depth ?? 99;
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.loading.set(false);
      },
    });
  }

  protected showWholeCompany(): void {
    void this.router.navigate(['/people-tree']).then(() => {
      this.roots.set([]);
      this.reload();
    });
  }

  protected goToDirectory(): void {
    void this.router.navigate(['/employees']);
  }
}
