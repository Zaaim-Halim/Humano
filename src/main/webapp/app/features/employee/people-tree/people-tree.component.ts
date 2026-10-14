import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { Observable, map, of, switchMap } from 'rxjs';

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
 * nor reports are listed separately. `/people-tree/:employeeId` shows that person in context:
 * the whole company with their reporting line opened and their card highlighted and centred.
 * Searching loads every level so all people can match.
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
  /** HR opens the person's profile; everyone else sees them in context in the people tree. */
  private readonly canOpenProfiles = inject(AccountService).hasPermission(Permission.READ_EMPLOYEE);
  protected readonly personLink = (id: string): string[] => (this.canOpenProfiles ? ['/employees', id] : ['/people-tree', id]);

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
  /** Employee shown in context (`/people-tree/:employeeId`): highlighted, centred, path opened. */
  protected readonly focused = signal<string | null>(null);
  protected readonly focusPerson = signal<PersonNode | null>(null);
  /** The focused person's reporting line (top → them), opened in the chart. */
  protected readonly revealIds = signal<string[]>([]);
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
    const focusId = this.route.snapshot.paramMap.get('employeeId');
    this.focused.set(focusId);
    this.loading.set(this.roots().length === 0);
    this.error.set(null);

    // Focusing on someone: fetch their reporting line first, then load the company deep enough to
    // contain them and open every branch on the way down.
    const path$: Observable<string[]> = focusId
      ? this.hierarchy.getEmployeeAncestors(focusId).pipe(map(r => (r.employeeChain ?? []).map(a => a.id)))
      : of([]);

    path$
      .pipe(
        switchMap(path => {
          this.revealIds.set(path);
          const needed = Math.max(this.expandDepth(), path.length);
          const depth = needed >= 99 ? undefined : needed;
          return this.hierarchy.getEmployeeTree(depth).pipe(map(r => ({ r, depth })));
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ r, depth }) => {
          const nodes = r.roots.map(toPersonNode);
          const withReports = nodes.filter(n => (n.directReports ?? 0) > 0 || n.id === focusId);
          this.roots.set(withReports);
          this.unassigned.set(nodes.filter(n => !withReports.includes(n)));
          this.stats.set({ people: r.totalPeople, managers: r.managers, levels: r.levels });
          this.focusPerson.set(focusId ? this.findPerson(withReports, focusId) : null);
          this.loadedDepth = depth ?? 99;
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.error.set(normalizeHttpError(err));
          this.loading.set(false);
        },
      });
  }

  private findPerson(nodes: PersonNode[], id: string): PersonNode | null {
    for (const n of nodes) {
      if (n.id === id) return n;
      const found = this.findPerson(n.children ?? [], id);
      if (found) return found;
    }
    return null;
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
