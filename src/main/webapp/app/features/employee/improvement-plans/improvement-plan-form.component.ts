import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { normalizeHttpError } from 'app/core/api';
import {
  ButtonComponent,
  DrawerComponent,
  FormFieldComponent,
  IconButtonComponent,
  InputComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';

import { IMPROVEMENT_PLAN_LIMITS, ImprovementPlan, SaveImprovementPlanRequest } from '../models/improvement-plan.model';
import { ImprovementPlanService } from '../services/improvement-plan.service';

interface ObjectiveDraft {
  description: string;
  successMeasure: string;
  dueDate: string;
}

interface PlanDraft {
  concerns: string;
  support: string;
  meetingCadence: string;
  startDate: string;
  endDate: string;
  objectives: ObjectiveDraft[];
}

const DAY = 24 * 60 * 60 * 1000;

function isoDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}

function daysBetween(from: string, to: string): number {
  return Math.round((Date.parse(to) - Date.parse(from)) / DAY);
}

/**
 * The improvement plan drawer: drafts a plan from a review (`reviewId`) or changes a draft (`plan`).
 * What needs to improve, the objectives and how each is measured, the support given, how often
 * progress is reviewed, and the period (30–180 days). Saving keeps it a draft — sharing with the
 * employee is a separate, confirmed step on the plan page.
 */
@Component({
  selector: 'hum-improvement-plan-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    TranslatePipe,
    LucideAngularModule,
    ButtonComponent,
    DrawerComponent,
    FormFieldComponent,
    IconButtonComponent,
    InputComponent,
    TextareaComponent,
  ],
  templateUrl: './improvement-plan-form.component.html',
})
export class ImprovementPlanFormComponent {
  private readonly planService = inject(ImprovementPlanService);
  private readonly toast = inject(ToastService);

  readonly open = input(false);
  /** The review to start a plan from; ignored when `plan` is set. */
  readonly reviewId = input<string | null>(null);
  /** The draft being changed. */
  readonly plan = input<ImprovementPlan | null>(null);
  readonly employeeName = input<string | null>(null);

  readonly saved = output<ImprovementPlan>();
  readonly closed = output();

  protected readonly limits = IMPROVEMENT_PLAN_LIMITS;
  protected readonly draft = signal<PlanDraft | null>(null);
  protected readonly tried = signal(false);
  protected readonly saving = signal(false);

  constructor() {
    effect(() => {
      if (!this.open()) {
        this.draft.set(null);
        return;
      }
      const p = this.plan();
      const today = isoDate(new Date());
      this.tried.set(false);
      this.draft.set(
        p
          ? {
              concerns: p.concerns,
              support: p.support ?? '',
              meetingCadence: p.meetingCadence ?? '',
              startDate: p.startDate,
              endDate: p.endDate,
              objectives: p.objectives.map(o => ({
                description: o.description,
                successMeasure: o.successMeasure ?? '',
                dueDate: o.dueDate ?? '',
              })),
            }
          : {
              concerns: '',
              support: '',
              meetingCadence: '',
              startDate: today,
              endDate: isoDate(new Date(Date.now() + 60 * DAY)),
              objectives: [{ description: '', successMeasure: '', dueDate: '' }],
            },
      );
    });
  }

  protected days(d: PlanDraft): number | null {
    return d.startDate && d.endDate ? daysBetween(d.startDate, d.endDate) : null;
  }

  /** The i18n key of what's wrong with the draft, or `null`. */
  protected problem(d: PlanDraft): string | null {
    if (!d.concerns.trim()) return 'humano.improvementPlans.error.concerns';
    const days = this.days(d);
    if (days === null || days < this.limits.minDays || days > this.limits.maxDays) return 'humano.improvementPlans.error.duration';
    if (!d.objectives.length || d.objectives.some(o => !o.description.trim())) return 'humano.improvementPlans.error.objectives';
    if (d.objectives.some(o => o.dueDate && (o.dueDate < d.startDate || o.dueDate > d.endDate))) {
      return 'humano.improvementPlans.error.objectiveDue';
    }
    return null;
  }

  protected addObjective(d: PlanDraft): void {
    if (d.objectives.length < this.limits.maxObjectives) {
      d.objectives = [...d.objectives, { description: '', successMeasure: '', dueDate: '' }];
    }
  }

  protected removeObjective(d: PlanDraft, index: number): void {
    d.objectives = d.objectives.filter((_, i) => i !== index);
  }

  protected save(): void {
    const d = this.draft();
    this.tried.set(true);
    if (!d || this.problem(d)) return;
    const body: SaveImprovementPlanRequest = {
      concerns: d.concerns.trim(),
      support: d.support.trim() || null,
      meetingCadence: d.meetingCadence.trim() || null,
      startDate: d.startDate,
      endDate: d.endDate,
      objectives: d.objectives.map(o => ({
        description: o.description.trim(),
        successMeasure: o.successMeasure.trim() || null,
        dueDate: o.dueDate || null,
      })),
    };
    const plan = this.plan();
    const reviewId = this.reviewId();
    const request$ = plan ? this.planService.update(plan.id, body) : reviewId ? this.planService.start(reviewId, body) : null;
    if (!request$) return;
    this.saving.set(true);
    request$.subscribe({
      next: saved => {
        this.saving.set(false);
        this.saved.emit(saved);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.toast.danger(normalizeHttpError(err));
      },
    });
  }
}
