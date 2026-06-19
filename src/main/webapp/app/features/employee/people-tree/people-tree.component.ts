import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
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
import { CurrentEmployeeService } from '../services/current-employee.service';
import { EmployeeHierarchyResponse, EmployeeTreeNode } from '../models/hierarchy.model';
import { EmployeeTreeNodeComponent } from './employee-tree-node/employee-tree-node.component';

/**
 * People Tree — browse the reporting hierarchy rooted at an employee.
 *
 * Routed at `/people-tree/:employeeId`; the no-param `/people-tree` route resolves
 * the signed-in user's own employee id via {@link CurrentEmployeeService} (today a
 * known-null seam — no backend endpoint), so it degrades to a "pick someone from
 * the directory" empty state rather than erroring. Also embeddable via the
 * `rootEmployeeId` input.
 */
@Component({
  selector: 'hum-people-tree',
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
    EmployeeTreeNodeComponent,
  ],
  templateUrl: './people-tree.component.html',
})
export class PeopleTreeComponent implements OnInit {
  /** Root employee id when embedded; the route param wins for the routed page. */
  readonly rootEmployeeId = input<string>();
  /** Max levels below the root; omit for the full subtree. */
  readonly maxDepth = input<number>();

  protected readonly root = signal<EmployeeTreeNode | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly totalNodes = signal(0);
  protected readonly maxLevels = signal(0);

  private readonly hierarchy = inject(HrHierarchyService);
  private readonly currentEmployee = inject(CurrentEmployeeService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    // `resolve()` is synchronous today (a known-null seam). When the backend
    // endpoint lands and it turns async, the no-param route should read
    // `currentEmployeeId` reactively rather than snapshotting it here.
    this.currentEmployee.resolve();
    const id = this.resolveRootId();
    if (id) this.load(id);
  }

  protected reload(): void {
    const id = this.resolveRootId();
    if (id) this.load(id);
  }

  protected goToDirectory(): void {
    void this.router.navigate(['/employees']);
  }

  private resolveRootId(): string | null {
    return this.rootEmployeeId() ?? this.route.snapshot.paramMap.get('employeeId') ?? this.currentEmployee.currentEmployeeId();
  }

  private load(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.hierarchy
      .getEmployeeSubtree(id, this.maxDepth())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response: EmployeeHierarchyResponse) => {
          this.root.set(response.root);
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
