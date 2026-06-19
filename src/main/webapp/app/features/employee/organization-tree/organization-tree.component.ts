import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  ButtonComponent,
  CardComponent,
  EmptyStateComponent,
  PageHeaderComponent,
  SkeletonRowComponent,
  StatTileComponent,
} from 'app/shared/ui';

import { HrHierarchyService } from '../services/hr-hierarchy.service';
import { OrganizationalUnitHierarchyResponse, OrganizationalUnitTreeNode } from '../models/hierarchy.model';
import { OrganizationalUnitNodeComponent } from './organizational-unit-node/organizational-unit-node.component';

/**
 * Organization Tree — browse organizational units, sub-units and their leadership.
 *
 * Routed at `/organization-tree` (all top-level units) and
 * `/organization-tree/:unitId` (a single unit's subtree). Headcount is requested
 * by default so each unit shows its size; also embeddable via `rootUnitId`.
 */
@Component({
  selector: 'hum-organization-tree',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    PageHeaderComponent,
    CardComponent,
    StatTileComponent,
    AlertComponent,
    ButtonComponent,
    EmptyStateComponent,
    SkeletonRowComponent,
    OrganizationalUnitNodeComponent,
  ],
  templateUrl: './organization-tree.component.html',
})
export class OrganizationTreeComponent implements OnInit {
  /** Root unit id when embedded; the route param wins for the routed page. */
  readonly rootUnitId = input<string>();
  /** Max levels below the root; omit for the full subtree. */
  readonly maxDepth = input<number>();
  /** Attach per-unit employee counts. */
  readonly includeHeadcount = input(true);

  protected readonly roots = signal<OrganizationalUnitTreeNode[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly totalNodes = signal(0);
  protected readonly maxLevels = signal(0);

  private readonly hierarchy = inject(HrHierarchyService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.load();
  }

  protected reload(): void {
    this.load();
  }

  private load(): void {
    const unitId = this.rootUnitId() ?? this.route.snapshot.paramMap.get('unitId');
    this.loading.set(true);
    this.error.set(null);

    const request$ = unitId
      ? this.hierarchy.getOrganizationalUnitSubtree(unitId, this.maxDepth(), this.includeHeadcount())
      : this.hierarchy.getOrganizationalUnitRoots(this.includeHeadcount());

    request$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (response: OrganizationalUnitHierarchyResponse) => {
        this.roots.set(response.roots);
        this.totalNodes.set(response.totalNodes);
        this.maxLevels.set(response.maxDepth);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.loading.set(false);
      },
    });
  }
}
