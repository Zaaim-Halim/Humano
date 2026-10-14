import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { normalizeHttpError } from 'app/core/api';
import { CurrentEmployeeService } from 'app/features/employee';
import {
  AlertComponent,
  AvatarComponent,
  BadgeComponent,
  ButtonComponent,
  DrawerComponent,
  EmptyStateComponent,
  SkeletonComponent,
  SkeletonRowComponent,
  PageHeaderComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';
import { MoneyPipe } from 'app/shared/util/format.pipes';

import { ApprovalDecision, ApprovalService, ApprovalType, ApprovalWorkflow, PendingApprovalSummary } from '../index';

/** Icon + accent per request type. */
const TYPE_STYLE: Partial<Record<ApprovalType, { icon: string; color: string }>> = {
  [ApprovalType.LEAVE_REQUEST]: { icon: 'palmtree', color: 'var(--brand)' },
  [ApprovalType.EXPENSE_CLAIM]: { icon: 'receipt', color: 'var(--success-solid)' },
  [ApprovalType.OVERTIME_REQUEST]: { icon: 'clock', color: 'var(--warning-solid)' },
};

/**
 * Approvals inbox — requests waiting on the signed-in approver.
 *
 * Overview (pending, overdue, oldest wait), type chips with counts, and one row per request
 * (requester, what and how much, how long it has waited; overdue rows flagged). Selecting a
 * request opens a panel with the full approval chain — each level, who decided and when, and
 * the current level — and the decision: approve, reject or ask for more information, with a
 * comment. The approver is the signed-in user's employee record.
 */
@Component({
  selector: 'hum-approvals',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    RouterLink,
    DatePipe,
    TranslatePipe,
    MoneyPipe,
    LucideAngularModule,
    PageHeaderComponent,
    ButtonComponent,
    AvatarComponent,
    BadgeComponent,
    DrawerComponent,
    TextareaComponent,
    AlertComponent,
    EmptyStateComponent,
    SkeletonComponent,
    SkeletonRowComponent,
  ],
  templateUrl: './approvals.component.html',
})
export default class ApprovalsComponent {
  private readonly currentEmployee = inject(CurrentEmployeeService);
  private readonly approvalService = inject(ApprovalService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);

  protected readonly resolved = this.currentEmployee.resolved;
  protected readonly approverId = this.currentEmployee.currentEmployeeId;

  protected readonly items = signal<PendingApprovalSummary[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly typeFilter = signal<string>('');

  protected readonly visible = computed(() => {
    const t = this.typeFilter();
    return (this.items() ?? []).filter(i => !t || i.approvalType === t);
  });
  protected readonly typeChips = computed(() => {
    const counts = new Map<string, number>();
    for (const i of this.items() ?? []) counts.set(i.approvalType, (counts.get(i.approvalType) ?? 0) + 1);
    return [
      { value: '', label: this.label('humano.approvals.all'), count: this.items()?.length ?? 0 },
      ...[...counts.entries()].map(([value, count]) => ({ value, label: this.label(`humano.approvals.type.${value}`), count })),
    ];
  });
  protected readonly stats = computed(() => {
    const list = this.items() ?? [];
    return {
      pending: list.length,
      overdue: list.filter(i => i.isOverdue).length,
      oldest: list.reduce((max, i) => Math.max(max, i.daysWaiting), 0),
      leaveDays: list.filter(i => i.approvalType === ApprovalType.LEAVE_REQUEST).reduce((sum, i) => sum + (i.daysCount ?? 0), 0),
    };
  });

  // Detail panel.
  protected readonly selected = signal<PendingApprovalSummary | null>(null);
  protected readonly workflow = signal<ApprovalWorkflow | null>(null);
  protected readonly workflowError = signal<string | null>(null);
  protected readonly comment = signal('');
  protected readonly deciding = signal<ApprovalDecision | null>(null);
  /** One entry per level: its decision (if made) and whether it is the current level. */
  protected readonly chain = computed(() => {
    const wf = this.workflow();
    if (!wf) return [];
    const total = wf.totalLevels ?? Math.max(1, wf.approvalHistory?.length ?? 1);
    return Array.from({ length: total }, (_, i) => {
      const level = i + 1;
      const decided = (wf.approvalHistory ?? []).find(h => h.level === level) ?? null;
      return {
        level,
        decided,
        current: !decided && level === wf.currentLevel,
        approverName: decided?.approverName ?? (level === wf.currentLevel ? wf.currentApproverName : null),
      };
    });
  });
  protected readonly decisions = ApprovalDecision;

  constructor() {
    this.currentEmployee.resolve();
    effect(() => {
      const id = this.approverId();
      if (id) untracked(() => this.load(id));
    });
  }

  protected style(type: ApprovalType): { icon: string; color: string } {
    return TYPE_STYLE[type] ?? { icon: 'inbox', color: 'var(--info-solid)' };
  }

  protected reload(): void {
    const id = this.approverId();
    if (id) this.load(id);
  }

  protected open(item: PendingApprovalSummary): void {
    this.selected.set(item);
    this.workflow.set(null);
    this.workflowError.set(null);
    this.comment.set('');
    this.approvalService.find(item.approvalRequestId).subscribe({
      next: wf => this.workflow.set(wf),
      error: (err: unknown) => this.workflowError.set(normalizeHttpError(err)),
    });
  }

  protected decide(decision: ApprovalDecision): void {
    const item = this.selected();
    if (!item) return;
    if (decision !== ApprovalDecision.APPROVE && !this.comment().trim()) {
      this.toast.warning(this.label('humano.approvals.commentRequired'));
      return;
    }
    this.deciding.set(decision);
    this.approvalService
      .decide(item.approvalRequestId, { decision, ...(this.comment().trim() ? { comments: this.comment().trim() } : {}) })
      .subscribe({
        next: () => {
          this.toast.success(this.label(`humano.approvals.decided.${decision}`));
          this.deciding.set(null);
          this.selected.set(null);
          this.reload();
        },
        error: (err: unknown) => {
          this.toast.danger(normalizeHttpError(err));
          this.deciding.set(null);
        },
      });
  }

  private load(approverId: string): void {
    this.error.set(null);
    this.approvalService.pendingForApprover(approverId).subscribe({
      next: rows => this.items.set([...rows].sort((a, b) => Number(b.isOverdue) - Number(a.isOverdue) || b.daysWaiting - a.daysWaiting)),
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.items.set([]);
      },
    });
  }

  private label(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
