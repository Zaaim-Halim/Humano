import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { Observable } from 'rxjs';

import { BadgeTone } from 'app/config/status-map';
import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  AvatarComponent,
  BadgeComponent,
  ButtonComponent,
  DialogComponent,
  FormFieldComponent,
  InputComponent,
  SelectComponent,
  SelectOption,
  SkeletonComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';

import {
  IMPROVEMENT_PLAN_LIMITS,
  IMPROVEMENT_PLAN_OUTCOMES,
  IMPROVEMENT_PLAN_PROGRESS,
  ImprovementPlan,
  ImprovementPlanOutcome,
  ImprovementPlanProgress,
  ImprovementPlanStatus,
} from '../models/improvement-plan.model';
import { CurrentEmployeeService } from '../services/current-employee.service';
import { ImprovementPlanService } from '../services/improvement-plan.service';
import { ImprovementPlanFormComponent } from './improvement-plan-form.component';

type Dialog = 'activate' | 'delete' | 'checkIn' | 'extend' | 'close' | 'acknowledge';

const DAY = 24 * 60 * 60 * 1000;
const STATUS_TONE: Record<ImprovementPlanStatus, BadgeTone> = { DRAFT: 'neutral', ACTIVE: 'warning', CLOSED: 'info' };
const OUTCOME_TONE: Record<ImprovementPlanOutcome, BadgeTone> = { SUCCESSFUL: 'success', UNSUCCESSFUL: 'danger', CANCELLED: 'neutral' };
const PROGRESS_TONE: Record<ImprovementPlanProgress, BadgeTone> = { ON_TRACK: 'success', SOME_PROGRESS: 'warning', NOT_ON_TRACK: 'danger' };

/**
 * One performance improvement plan — `/improvement-plans/:id`.
 *
 * - The manager side (the employee's manager, whoever runs the plan, or HR) edits the draft, shares
 *   it with the employee (confirmed), deletes a draft, records check-ins, extends it once and closes
 *   it with an outcome. Unsuccessful only once the end date is reached.
 * - The employee reads it once shared and confirms they received it — not that they agree —
 *   optionally with a comment.
 * - What needs to improve, the objectives, the support, the check-ins (newest first) and the outcome.
 *   The outcome is a record only: it never changes the employee's status.
 */
@Component({
  selector: 'hum-improvement-plan',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    FormsModule,
    RouterLink,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    AvatarComponent,
    BadgeComponent,
    ButtonComponent,
    DialogComponent,
    FormFieldComponent,
    InputComponent,
    SelectComponent,
    SkeletonComponent,
    TextareaComponent,
    ImprovementPlanFormComponent,
  ],
  templateUrl: './improvement-plan.component.html',
})
export default class ImprovementPlanComponent {
  /** Route param. */
  readonly id = input.required<string>();

  private readonly planService = inject(ImprovementPlanService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly currentEmployee = inject(CurrentEmployeeService);

  protected readonly plan = signal<ImprovementPlan | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly editing = signal(false);
  protected readonly dialog = signal<Dialog | null>(null);

  // Dialog fields.
  protected readonly progress = signal<ImprovementPlanProgress | ''>('');
  protected readonly note = signal('');
  protected readonly endDate = signal('');
  protected readonly outcome = signal<ImprovementPlanOutcome | ''>('');

  protected readonly limits = IMPROVEMENT_PLAN_LIMITS;
  protected readonly progressLevels = IMPROVEMENT_PLAN_PROGRESS;
  protected readonly outcomeOptions: SelectOption[] = [
    { value: '', label: this.label('humano.improvementPlans.close.pick') },
    ...IMPROVEMENT_PLAN_OUTCOMES.map(o => ({ value: o, label: this.label(`humano.improvementPlans.outcome.${o}`) })),
  ];

  /** The employee looking at their own plan. */
  protected readonly isEmployee = computed(() => !!this.plan() && this.plan()!.employeeId === this.currentEmployee.currentEmployeeId());

  protected readonly daysLeft = computed(() => {
    const p = this.plan();
    if (p?.status !== 'ACTIVE') return null;
    return Math.ceil((Date.parse(p.endDate) - Date.parse(new Date().toISOString().slice(0, 10))) / DAY);
  });
  protected readonly duration = computed(() => {
    const p = this.plan();
    return p ? Math.round((Date.parse(p.endDate) - Date.parse(p.startDate)) / DAY) : 0;
  });
  /** The latest date an extension may move the end to. */
  protected readonly maxExtension = computed(() => {
    const p = this.plan();
    return p ? new Date(Date.parse(p.endDate) + this.limits.maxExtensionDays * DAY).toISOString().slice(0, 10) : '';
  });
  protected readonly endReached = computed(() => {
    const p = this.plan();
    return !!p && p.endDate <= new Date().toISOString().slice(0, 10);
  });

  constructor() {
    this.currentEmployee.resolve();
    toObservable(this.id)
      .pipe(takeUntilDestroyed())
      .subscribe(id => this.load(id));
  }

  protected load(id = this.id()): void {
    this.error.set(null);
    this.planService.find(id).subscribe({
      next: p => this.show(p),
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
  }

  protected statusTone(s: ImprovementPlanStatus): BadgeTone {
    return STATUS_TONE[s];
  }

  protected outcomeTone(o: ImprovementPlanOutcome): BadgeTone {
    return OUTCOME_TONE[o];
  }

  protected progressTone(p: ImprovementPlanProgress): BadgeTone {
    return PROGRESS_TONE[p];
  }

  protected openDialog(d: Dialog): void {
    this.note.set('');
    this.progress.set('');
    this.outcome.set('');
    this.endDate.set('');
    this.dialog.set(d);
  }

  protected edited(p: ImprovementPlan): void {
    this.editing.set(false);
    this.show(p);
    this.toast.success(this.label('humano.improvementPlans.saved'));
  }

  protected activate(): void {
    this.act(this.planService.activate(this.id()), 'humano.improvementPlans.activated');
  }

  protected remove(): void {
    const p = this.plan();
    if (!p) return;
    this.saving.set(true);
    this.planService.delete(p.id).subscribe({
      next: () => {
        this.saving.set(false);
        this.dialog.set(null);
        this.toast.success(this.label('humano.improvementPlans.deleted'));
        void this.router.navigate(['/reviews', p.reviewId]);
      },
      error: (err: unknown) => this.failed(err),
    });
  }

  protected acknowledge(): void {
    this.act(this.planService.acknowledge(this.id(), this.note().trim() || null), 'humano.improvementPlans.acknowledged');
  }

  protected checkInInvalid(): boolean {
    return !this.progress() || !this.note().trim();
  }

  protected checkIn(): void {
    const progress = this.progress();
    if (!progress || this.checkInInvalid()) return;
    this.act(this.planService.checkIn(this.id(), progress, this.note().trim()), 'humano.improvementPlans.checkedIn');
  }

  protected extendInvalid(): boolean {
    const p = this.plan();
    const d = this.endDate();
    return !p || !d || d <= p.endDate || d > this.maxExtension() || !this.note().trim();
  }

  protected extend(): void {
    if (this.extendInvalid()) return;
    this.act(this.planService.extend(this.id(), this.endDate(), this.note().trim()), 'humano.improvementPlans.extended');
  }

  /** The i18n key of why the outcome can't be recorded yet, or `null`. */
  protected closeProblem(): string | null {
    const o = this.outcome();
    if (!o || !this.note().trim()) return 'required';
    if (o === 'UNSUCCESSFUL' && !this.endReached()) return 'humano.improvementPlans.close.tooEarly';
    return null;
  }

  protected close(): void {
    const o = this.outcome();
    if (!o || this.closeProblem()) return;
    this.act(this.planService.close(this.id(), o, this.note().trim()), 'humano.improvementPlans.closed');
  }

  private show(p: ImprovementPlan): void {
    this.plan.set(p);
  }

  private act(request$: Observable<ImprovementPlan>, successKey: string): void {
    this.saving.set(true);
    request$.subscribe({
      next: p => {
        this.saving.set(false);
        this.dialog.set(null);
        this.show(p);
        this.toast.success(this.label(successKey));
      },
      error: (err: unknown) => this.failed(err),
    });
  }

  private failed(err: unknown): void {
    this.saving.set(false);
    this.toast.danger(normalizeHttpError(err));
  }

  private label(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
