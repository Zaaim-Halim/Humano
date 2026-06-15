import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { normalizeHttpError } from 'app/core/api';
import { CurrentEmployeeService } from 'app/features/employee';
import {
  AlertComponent,
  BadgeComponent,
  ButtonComponent,
  EmptyStateComponent,
  PageHeaderComponent,
  SkeletonRowComponent,
} from 'app/shared/ui';

import { PendingApprovalSummary } from '../index';
import { ApprovalService } from '../index';

/**
 * Approvals inbox (Manager hero screen). The pending queue is discovered only by
 * approver **employee** id (`GET /pending/{approverId}`) — there is no global
 * queue — and a plain manager cannot resolve their own employee id yet (see
 * {@link CurrentEmployeeService}). So today this renders the honest "no approver
 * profile" state; it lists the real pending approvals automatically once the
 * seam resolves.
 *
 * Deferred (Tier-3, documented in BUILD_TRACKER): the multi-level approval-chain
 * visualization and the approve/reject-with-reason decide dialog. The data layer
 * (`ApprovalService` + models) fully supports them — `decide` derives the actor
 * from the session — but building that intricate, design-load-bearing UI blind
 * (it can never render in dev while the seam is null) is exactly the
 * verify-by-nothing risk we avoid; it lands with the seam.
 */
@Component({
  selector: 'hum-approvals',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, PageHeaderComponent, BadgeComponent, ButtonComponent, AlertComponent, EmptyStateComponent, SkeletonRowComponent],
  templateUrl: './approvals.component.html',
})
export default class ApprovalsComponent {
  private readonly currentEmployee = inject(CurrentEmployeeService);
  private readonly approvalService = inject(ApprovalService);

  protected readonly resolved = this.currentEmployee.resolved;
  protected readonly approverId = this.currentEmployee.currentEmployeeId;

  protected readonly items = signal<PendingApprovalSummary[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly empty = computed(() => !this.loading() && !this.error() && this.items().length === 0);

  constructor() {
    this.currentEmployee.resolve();
    // Fetch the pending queue whenever an approver id becomes available.
    effect(() => {
      const id = this.approverId();
      if (id) this.load(id);
    });
  }

  protected reload(): void {
    const id = this.approverId();
    if (id) this.load(id);
  }

  private load(approverId: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.approvalService.pendingForApprover(approverId).subscribe({
      next: rows => {
        this.items.set(rows);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.loading.set(false);
      },
    });
  }
}
