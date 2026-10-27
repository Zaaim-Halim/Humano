import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { Observable } from 'rxjs';

import { BadgeTone } from 'app/config/status-map';
import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { normalizeHttpError } from 'app/core/api';
import { MoneyPipe } from 'app/shared/util/format.pipes';
import {
  AlertComponent,
  BadgeComponent,
  ButtonComponent,
  DialogComponent,
  DrawerComponent,
  EmptyStateComponent,
  FormFieldComponent,
  IconButtonComponent,
  InputComponent,
  PageHeaderComponent,
  SelectComponent,
  SelectOption,
  SkeletonComponent,
  SkeletonRowComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';

import { Position, PositionService } from '../index';
import {
  COMPENSATION_PROPOSAL_STATUSES,
  CompensationProposal,
  CompensationProposalStatus,
  CompensationReview,
  CompensationReviewCycle,
  MeritBand,
} from './compensation-review.model';
import { CompensationReviewService } from './compensation-review.service';

const STATUS_TONE: Record<CompensationProposalStatus, BadgeTone> = {
  DRAFT: 'neutral',
  SUBMITTED: 'info',
  APPROVED: 'brand',
  APPLIED: 'success',
};

/** A confirmation waiting for a click: one proposal, or a whole-cycle action. */
type Pending =
  | { kind: 'apply' | 'delete'; proposal: CompensationProposal }
  | { kind: 'submitAll' | 'approveAll' | 'guidance'; count: number };

/**
 * Compensation review — `/compensation-reviews?cycle=` — where the compensation team turns a regular
 * cycle's shared reviews into pay decisions:
 *
 * - drafting one proposal per shared, rated review (open disputes and people leaving wait), with the
 *   pay in effect on the date and no increase prefilled;
 * - for each, a new base salary, a promotion and/or a one-off bonus, with the merit matrix's range for
 *   the rating — going outside it is flagged, never refused;
 * - submitting, approving by someone else, and applying once: the salary record is split on the date,
 *   the history records it, and payroll pays the bonus in the period of the date;
 * - totals per currency and pay basis.
 *
 * Needs `MANAGE_COMPENSATION`; nobody acts on their own proposal. Ratings never change pay by themselves.
 */
@Component({
  selector: 'hum-compensation-review',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    RouterLink,
    TranslatePipe,
    LucideAngularModule,
    MoneyPipe,
    AlertComponent,
    BadgeComponent,
    ButtonComponent,
    DialogComponent,
    DrawerComponent,
    EmptyStateComponent,
    FormFieldComponent,
    IconButtonComponent,
    InputComponent,
    PageHeaderComponent,
    SelectComponent,
    SkeletonComponent,
    SkeletonRowComponent,
    TextareaComponent,
  ],
  templateUrl: './compensation-review.component.html',
})
export default class CompensationReviewComponent {
  /** The cycle, from the `cycle` query parameter. */
  readonly cycle = input<string>();

  private readonly service = inject(CompensationReviewService);
  private readonly positionService = inject(PositionService);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);

  protected readonly canAwardBonuses = inject(AccountService).hasPermission(Permission.MANAGE_BONUSES);
  protected readonly statuses = COMPENSATION_PROPOSAL_STATUSES;

  protected readonly cycles = signal<CompensationReviewCycle[] | null>(null);
  protected readonly review = signal<CompensationReview | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly statusFilter = signal<CompensationProposalStatus | ''>('');
  protected readonly generateDate = signal(firstOfNextMonth());

  protected readonly selectedCycle = computed(() => {
    const all = this.cycles() ?? [];
    return all.find(c => c.id === this.cycle()) ?? all.find(c => c.proposals > 0) ?? (all.length ? all[0] : null);
  });
  /** Only a change of cycle reloads, not a refreshed cycle list. */
  private readonly selectedCycleId = computed(() => this.selectedCycle()?.id);
  protected readonly cycleOptions = computed<SelectOption[]>(() => (this.cycles() ?? []).map(c => ({ value: c.id, label: c.name })));
  protected readonly visible = computed(() => {
    const filter = this.statusFilter();
    return (this.review()?.proposals ?? []).filter(p => !filter || p.status === filter);
  });
  protected readonly counts = computed(() => {
    const counts: Record<CompensationProposalStatus, number> = { DRAFT: 0, SUBMITTED: 0, APPROVED: 0, APPLIED: 0 };
    (this.review()?.proposals ?? []).forEach(p => counts[p.status]++);
    return counts;
  });
  protected readonly bulk = computed(() => {
    const proposals = this.review()?.proposals ?? [];
    return {
      guidance: proposals.filter(p => p.actions.edit && p.newSalary === null && p.currentSalary !== null).length,
      submit: proposals.filter(p => p.actions.submit).length,
      approve: proposals.filter(p => p.actions.approve).length,
    };
  });

  // ------------------------------------------------------------ merit matrix
  protected readonly guidelinesOpen = signal(false);
  protected readonly bands = signal<MeritBand[]>([]);
  protected readonly bandsInvalid = computed(() =>
    this.bands().some(b => !isPercent(b.minPercent) || !isPercent(b.maxPercent) || b.minPercent > b.maxPercent),
  );

  // ------------------------------------------------------------ edit drawer
  protected readonly editing = signal<CompensationProposal | null>(null);
  protected readonly positions = signal<Position[]>([]);
  /** Amounts as typed; blank is none. */
  protected readonly newSalary = signal('');
  protected readonly newPositionId = signal('');
  protected readonly bonusAmount = signal('');
  protected readonly effectiveDate = signal('');
  protected readonly justification = signal('');

  protected readonly positionOptions = computed<SelectOption[]>(() => {
    const p = this.editing();
    return [
      { value: '', label: this.label('humano.compensationReview.edit.keepPosition', { name: p?.currentPositionName ?? '—' }) },
      ...this.positions()
        .filter(pos => pos.id !== p?.currentPositionId)
        .map(pos => ({ value: pos.id, label: pos.name })),
    ];
  });
  /** The increase the drawer's salary makes, in percent. */
  protected readonly editIncrease = computed(() => {
    const current = this.editing()?.currentSalary ?? null;
    const salary = amount(this.newSalary());
    if (current === null || !(current > 0) || salary === null || Number.isNaN(salary)) {
      return null;
    }
    return ((salary - current) * 100) / current;
  });
  protected readonly editOutside = computed(() => {
    const p = this.editing();
    const current = p?.currentSalary ?? null;
    if (p === null || current === null) {
      return false;
    }
    const increase = this.editIncrease() ?? 0;
    return increase < p.guidanceMin - 0.005 || increase > p.guidanceMax + 0.005;
  });
  protected readonly salaryInvalid = computed(() => !positiveOrBlank(this.newSalary()));
  protected readonly bonusInvalid = computed(() => !positiveOrBlank(this.bonusAmount()));
  protected readonly editInvalid = computed(() => !this.effectiveDate() || this.salaryInvalid() || this.bonusInvalid());

  // ------------------------------------------------------------ dialogs
  protected readonly pending = signal<Pending | null>(null);
  protected readonly pendingDescription = computed(() => {
    const p = this.pending();
    if (!p) {
      return '';
    }
    const key = `humano.compensationReview.confirm.${p.kind}.body`;
    return 'proposal' in p ? this.label(key, { name: p.proposal.employeeName }) : this.label(key, { count: p.count });
  });
  protected readonly returning = signal<CompensationProposal | null>(null);
  protected readonly returnComment = signal('');

  constructor() {
    this.service.cycles().subscribe({
      next: cycles => this.cycles.set(cycles),
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
    effect(() => {
      const id = this.selectedCycleId();
      if (id) {
        untracked(() => this.load(id));
      }
    });
  }

  protected selectCycle(id: string): void {
    this.statusFilter.set('');
    void this.router.navigate([], { queryParams: { cycle: id }, queryParamsHandling: 'merge', replaceUrl: true });
  }

  protected load(cycleId = this.selectedCycle()?.id): void {
    if (!cycleId) {
      return;
    }
    this.error.set(null);
    this.service.review(cycleId).subscribe({
      next: r => this.review.set(r),
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
  }

  protected statusTone(s: CompensationProposalStatus): BadgeTone {
    return STATUS_TONE[s];
  }

  // ------------------------------------------------------------ cycle-wide actions

  protected generate(): void {
    const r = this.review();
    if (!r || !this.generateDate()) {
      return;
    }
    this.run(this.service.generate(r.cycleId, this.generateDate()), 'humano.compensationReview.drafted');
  }

  protected confirmBulk(kind: 'submitAll' | 'approveAll' | 'guidance'): void {
    const b = this.bulk();
    this.pending.set({ kind, count: kind === 'submitAll' ? b.submit : kind === 'approveAll' ? b.approve : b.guidance });
  }

  // ------------------------------------------------------------ merit matrix

  protected openGuidelines(): void {
    this.service.guidelines().subscribe({
      next: g => {
        this.bands.set(g.bands.map(b => ({ ...b })));
        this.guidelinesOpen.set(true);
      },
      error: (err: unknown) => this.toast.danger(normalizeHttpError(err)),
    });
  }

  /** A blank band is kept as NaN, so the matrix shows it as missing. */
  protected setBand(rating: number, field: 'minPercent' | 'maxPercent', value: string): void {
    this.bands.update(bands => bands.map(b => (b.rating === rating ? { ...b, [field]: value.trim() === '' ? NaN : Number(value) } : b)));
  }

  protected bandText(value: number): string {
    return Number.isNaN(value) ? '' : `${value}`;
  }

  protected saveGuidelines(): void {
    this.busy.set(true);
    this.service.saveGuidelines({ bands: this.bands() }).subscribe({
      next: () => {
        this.busy.set(false);
        this.guidelinesOpen.set(false);
        this.toast.success(this.label('humano.compensationReview.guidelines.saved'));
        this.load();
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.toast.danger(normalizeHttpError(err));
      },
    });
  }

  // ------------------------------------------------------------ one proposal

  protected edit(p: CompensationProposal): void {
    this.editing.set(p);
    this.newSalary.set(p.newSalary === null ? '' : `${p.newSalary}`);
    this.newPositionId.set(p.newPositionId ?? '');
    this.bonusAmount.set(p.bonusAmount === null ? '' : `${p.bonusAmount}`);
    this.effectiveDate.set(p.effectiveDate);
    this.justification.set(p.justification ?? '');
    if (!this.positions().length) {
      this.positionService.query({ page: 0, size: 500, sort: ['name,asc'] }).subscribe({ next: res => this.positions.set(res.content) });
    }
  }

  /** Sets the salary to the merit matrix's midpoint for the rating. */
  protected useMidpoint(): void {
    const p = this.editing();
    const current = p?.currentSalary ?? null;
    if (p === null || current === null) {
      return;
    }
    const midpoint = (p.guidanceMin + p.guidanceMax) / 2;
    this.newSalary.set(midpoint > 0 ? (Math.round(current * (100 + midpoint)) / 100).toFixed(2) : '');
  }

  protected save(): void {
    const p = this.editing();
    if (!p || this.editInvalid()) {
      return;
    }
    this.busy.set(true);
    this.service
      .update(p.id, {
        newSalary: amount(this.newSalary()),
        newPositionId: this.newPositionId() || null,
        bonusAmount: amount(this.bonusAmount()),
        effectiveDate: this.effectiveDate(),
        justification: this.justification().trim() || null,
      })
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.editing.set(null);
          this.toast.success(this.label('humano.compensationReview.saved'));
          this.load();
        },
        error: (err: unknown) => {
          this.busy.set(false);
          this.toast.danger(normalizeHttpError(err));
        },
      });
  }

  protected submit(p: CompensationProposal): void {
    this.run(this.service.submit(p.id), 'humano.compensationReview.submitted');
  }

  protected approve(p: CompensationProposal): void {
    this.run(this.service.approve(p.id), 'humano.compensationReview.approved');
  }

  protected openReturn(p: CompensationProposal): void {
    this.returnComment.set('');
    this.returning.set(p);
  }

  protected sendBack(): void {
    const p = this.returning();
    const comment = this.returnComment().trim();
    if (!p || !comment) {
      return;
    }
    this.run(this.service.returnToDraft(p.id, comment), 'humano.compensationReview.returned', () => this.returning.set(null));
  }

  /** Runs the confirmed action. */
  protected confirm(): void {
    const pending = this.pending();
    const r = this.review();
    if (!pending || !r) {
      return;
    }
    const done = (): void => this.pending.set(null);
    switch (pending.kind) {
      case 'apply':
        this.run(this.service.apply(pending.proposal.id), 'humano.compensationReview.applied', done);
        break;
      case 'delete':
        this.run(this.service.delete(pending.proposal.id), 'humano.compensationReview.deleted', done);
        break;
      case 'submitAll':
        this.run(this.service.submitAll(r.cycleId), 'humano.compensationReview.submittedAll', done);
        break;
      case 'approveAll':
        this.run(this.service.approveAll(r.cycleId), 'humano.compensationReview.approvedAll', done);
        break;
      case 'guidance':
        this.run(this.service.useGuidance(r.cycleId), 'humano.compensationReview.guidanceUsed', done);
        break;
    }
  }

  private label(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }

  /** Runs a change, then says so (with how many, for cycle-wide ones) and reloads. */
  private run(request: Observable<unknown>, successKey: string, after?: () => void): void {
    this.busy.set(true);
    request.subscribe({
      next: result => {
        this.busy.set(false);
        after?.();
        this.toast.success(this.label(successKey, { count: typeof result === 'number' ? result : 1 }));
        this.load();
        this.service.cycles().subscribe({ next: cycles => this.cycles.set(cycles) });
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.toast.danger(normalizeHttpError(err));
      },
    });
  }
}

function isPercent(value: number): boolean {
  return !Number.isNaN(value) && value >= 0 && value <= 100;
}

/** A typed amount: null when blank, NaN when not a number. */
function amount(value: string): number | null {
  return value.trim() === '' ? null : Number(value);
}

function positiveOrBlank(value: string): boolean {
  const n = amount(value);
  return n === null || (!Number.isNaN(n) && n > 0);
}

/** Pay changes usually take effect on the first of a month; the next one by default. */
function firstOfNextMonth(): string {
  const d = new Date();
  const next = new Date(d.getFullYear(), d.getMonth() + 1, 1);
  return `${next.getFullYear()}-${String(next.getMonth() + 1).padStart(2, '0')}-01`;
}
