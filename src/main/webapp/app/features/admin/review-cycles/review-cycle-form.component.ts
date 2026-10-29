import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { Observable, map, tap } from 'rxjs';

import { normalizeHttpError } from 'app/core/api';
import {
  EmployeeService,
  FEEDBACK_VISIBILITIES,
  PerformanceFrameworkService,
  ReviewTemplate,
  FeedbackVisibility,
  REVIEW_CYCLE_TYPES,
  ReviewCycle,
  ReviewCycleEmployee,
  ReviewCycleType,
  ReviewCycleProposal,
  ReviewCycleService,
  SaveReviewCycleRequest,
} from 'app/features/employee';
import {
  AlertComponent,
  AutocompleteComponent,
  AutocompleteOption,
  ButtonComponent,
  CheckboxComponent,
  DrawerComponent,
  FormFieldComponent,
  InputComponent,
  SelectComponent,
  SelectOption,
  SwitchComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';

import { Department } from '../models/department.model';
import { DepartmentService } from '../services/department.service';

/** Deadline controls in phase order, with the i18n key naming each. */
const DEFAULT_MAX_CONTRIBUTORS = 8;

const DEADLINES = [
  { control: 'selfAssessmentDeadline', key: 'self' },
  { control: 'managerReviewDeadline', key: 'manager' },
  { control: 'calibrationDeadline', key: 'calibration' },
  { control: 'feedbackDeadline', key: 'feedback' },
] as const;

/**
 * The new-cycle / edit-draft drawer. A new cycle starts from the review calendar's proposal
 * (`proposal`); a draft opens with its own values (`cycle`). Asks for the name and description,
 * the period under review, when the cycle runs, one deadline per phase (calibration optional) and
 * its type, who is in scope — the whole company or some departments, or named employees for an
 * ad hoc cycle; a probation cycle takes whoever's probation ends in the period — whether reviewers gather feedback
 * from colleagues (how many per review, whether employees nominate them, and whether the employee
 * sees it once the review is shared). Dates are checked here in the order the server checks them,
 * so mistakes show before saving.
 */
@Component({
  selector: 'hum-review-cycle-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    ReactiveFormsModule,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    AutocompleteComponent,
    ButtonComponent,
    CheckboxComponent,
    DrawerComponent,
    FormFieldComponent,
    InputComponent,
    SelectComponent,
    SwitchComponent,
    TextareaComponent,
  ],
  templateUrl: './review-cycle-form.component.html',
})
export class ReviewCycleFormComponent {
  readonly open = input(false);
  /** The draft to edit; `null` creates a cycle. */
  readonly cycle = input<ReviewCycle | null>(null);
  /** Pre-fills a new cycle. */
  readonly proposal = input<ReviewCycleProposal | null>(null);
  readonly saved = output<ReviewCycle>();
  readonly closed = output();

  private readonly fb = inject(FormBuilder);
  private readonly reviewCycleService = inject(ReviewCycleService);
  private readonly departmentService = inject(DepartmentService);
  private readonly employeeService = inject(EmployeeService);
  private readonly frameworkService = inject(PerformanceFrameworkService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);

  protected readonly deadlines = DEADLINES;
  protected readonly saving = signal(false);
  protected readonly departments = signal<Department[] | null>(null);
  protected readonly templates = signal<ReviewTemplate[]>([]);
  protected readonly selected = signal<ReadonlySet<string>>(new Set());
  /** An ad hoc cycle's employees. */
  protected readonly people = signal<ReviewCycleEmployee[]>([]);
  /** Bumped after each pick, so the search box starts empty again. */
  protected readonly pickerKey = signal(0);
  private readonly seen = new Map<string, string>();
  protected readonly typeOptions: SelectOption[] = REVIEW_CYCLE_TYPES.map(t => ({
    value: t,
    label: this.label(`humano.performance.cycleType.${t}`),
  }));

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    type: this.fb.nonNullable.control<ReviewCycleType>('REGULAR'),
    description: ['', Validators.maxLength(500)],
    reviewPeriodStart: ['', Validators.required],
    reviewPeriodEnd: ['', Validators.required],
    startDate: ['', Validators.required],
    endDate: ['', Validators.required],
    selfAssessmentDeadline: ['', Validators.required],
    managerReviewDeadline: ['', Validators.required],
    calibrate: [true],
    calibrationDeadline: [''],
    feedbackDeadline: ['', Validators.required],
    companyWide: [true],
    collectFeedback: [true],
    // A string, as the number input gives it.
    maxContributors: [String(DEFAULT_MAX_CONTRIBUTORS), [Validators.required, Validators.min(1), Validators.max(20)]],
    allowNominations: [true],
    feedbackVisibility: this.fb.nonNullable.control<FeedbackVisibility>('MANAGER_ONLY'),
    requiresSignOff: [false],
    templateId: [''],
  });
  private readonly value = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  protected readonly calibrate = computed(() => this.value().calibrate === true);
  protected readonly companyWide = computed(() => this.value().companyWide === true);
  protected readonly type = computed(() => this.value().type ?? 'REGULAR');
  protected readonly collectFeedback = computed(() => this.value().collectFeedback === true);
  protected readonly visibility = computed(() => this.value().feedbackVisibility ?? 'MANAGER_ONLY');
  protected readonly visibilityOptions: SelectOption[] = FEEDBACK_VISIBILITIES.map(v => ({
    value: v,
    label: this.label(`humano.performance.form.feedbackVisibility.${v}`),
  }));

  /** The first date that breaks the order (i18n key + params), or `null`. */
  protected readonly dateError = computed((): { key: string; params?: Record<string, string> } | null => {
    const v = this.value();
    if (v.reviewPeriodStart && v.reviewPeriodEnd && v.reviewPeriodEnd < v.reviewPeriodStart) {
      return { key: 'humano.performance.form.error.period' };
    }
    if (v.startDate && v.endDate && v.endDate < v.startDate) {
      return { key: 'humano.performance.form.error.window' };
    }
    let previous = v.startDate;
    let previousKey = 'start';
    for (const d of DEADLINES) {
      if (d.key === 'calibration' && !v.calibrate) continue;
      const date = v[d.control];
      if (!date) continue;
      const phase = this.label(`humano.performance.form.deadline.${d.key}`);
      if (previous && date < previous) {
        return {
          key: 'humano.performance.form.error.before',
          params: { phase, previous: this.label(`humano.performance.form.after.${previousKey}`) },
        };
      }
      if (v.endDate && date > v.endDate) {
        return { key: 'humano.performance.form.error.after', params: { phase } };
      }
      previous = date;
      previousKey = d.key;
    }
    return null;
  });
  protected readonly scopeMissing = computed(() =>
    this.type() === 'AD_HOC' ? this.people().length === 0 : !this.companyWide() && this.selected().size === 0,
  );

  /** Active templates, plus the cycle's own if it was retired since. */
  protected readonly templateOptions = computed<SelectOption[]>(() => [
    { value: '', label: this.label('humano.performance.form.standardForm') },
    ...this.templates()
      .filter(t => t.active || t.id === this.cycle()?.template?.id)
      .map(t => ({ value: t.id, label: t.name })),
  ]);
  protected readonly template = computed(() => this.templates().find(t => t.id === this.value().templateId) ?? null);

  constructor() {
    // Reset on every opening: from the draft, or from the calendar's proposal.
    effect(() => {
      if (!this.open()) return;
      untracked(() => this.reset());
    });
  }
  protected readonly searchEmployees = (term: string): Observable<AutocompleteOption[]> =>
    this.employeeService.searchOptions(term).pipe(
      map(options => options.filter(o => !this.people().some(p => p.id === o.value))),
      tap(options => options.forEach(o => this.seen.set(o.value, o.label))),
    );

  protected invalid(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  protected toggle(departmentId: string, on: boolean): void {
    this.selected.update(set => {
      const next = new Set(set);
      if (on) next.add(departmentId);
      else next.delete(departmentId);
      return next;
    });
  }

  protected addPerson(id: string | null): void {
    if (!id || this.people().some(p => p.id === id)) return;
    this.people.update(list => [...list, { id, name: this.seen.get(id) ?? id }]);
    this.pickerKey.update(k => k + 1);
  }

  protected removePerson(id: string): void {
    this.people.update(list => list.filter(p => p.id !== id));
  }

  protected submit(): void {
    if (!this.form.controls.collectFeedback.value) {
      // Contributors don't matter when feedback is off.
      this.form.controls.maxContributors.setValue(String(DEFAULT_MAX_CONTRIBUTORS));
    }
    if (
      this.form.invalid ||
      this.dateError() ||
      this.scopeMissing() ||
      (this.calibrate() && !this.form.controls.calibrationDeadline.value)
    ) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const body: SaveReviewCycleRequest = {
      name: v.name.trim(),
      ...(v.description.trim() ? { description: v.description.trim() } : {}),
      reviewPeriodStart: v.reviewPeriodStart,
      reviewPeriodEnd: v.reviewPeriodEnd,
      startDate: v.startDate,
      endDate: v.endDate,
      selfAssessmentDeadline: v.selfAssessmentDeadline,
      managerReviewDeadline: v.managerReviewDeadline,
      calibrationDeadline: v.calibrate ? v.calibrationDeadline : null,
      feedbackDeadline: v.feedbackDeadline,
      type: v.type,
      departmentIds: v.type === 'AD_HOC' || v.companyWide ? [] : [...this.selected()],
      includeAllDepartments: v.type === 'AD_HOC' || v.companyWide,
      employeeIds: v.type === 'AD_HOC' ? this.people().map(p => p.id) : [],
      feedbackVisibility: v.feedbackVisibility,
      allowFeedbackNominations: v.allowNominations,
      maxContributors: v.collectFeedback ? Number(v.maxContributors) : 0,
      requiresSignOff: v.requiresSignOff,
      templateId: v.templateId || null,
    };
    const existing = this.cycle();
    this.saving.set(true);
    (existing ? this.reviewCycleService.update(existing.cycleId, body) : this.reviewCycleService.create(body)).subscribe({
      next: cycle => {
        this.saving.set(false);
        this.toast.success(
          this.label(existing ? 'humano.performance.form.updated' : 'humano.performance.form.created', { name: cycle.name }),
        );
        this.saved.emit(cycle);
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.saving.set(false);
      },
    });
  }

  private reset(): void {
    const c = this.cycle();
    const p = this.proposal();
    this.form.reset({
      name: c?.name ?? p?.name ?? '',
      type: c?.type ?? 'REGULAR',
      description: c?.description ?? '',
      reviewPeriodStart: c?.reviewPeriodStart ?? p?.reviewPeriodStart ?? '',
      reviewPeriodEnd: c?.reviewPeriodEnd ?? p?.reviewPeriodEnd ?? '',
      startDate: c?.startDate ?? p?.startDate ?? '',
      endDate: c?.endDate ?? p?.endDate ?? '',
      selfAssessmentDeadline: c?.deadlines.selfAssessmentDeadline ?? p?.selfAssessmentDeadline ?? '',
      managerReviewDeadline: c?.deadlines.managerReviewDeadline ?? p?.managerReviewDeadline ?? '',
      calibrate: c ? !!c.deadlines.calibrationDeadline : p ? !!p.calibrationDeadline : true,
      calibrationDeadline: c?.deadlines.calibrationDeadline ?? p?.calibrationDeadline ?? '',
      feedbackDeadline: c?.deadlines.feedbackDeadline ?? p?.feedbackDeadline ?? '',
      companyWide: !c?.departmentIds.length,
      collectFeedback: c ? c.feedback.maxContributors > 0 : true,
      maxContributors: String(c && c.feedback.maxContributors > 0 ? c.feedback.maxContributors : DEFAULT_MAX_CONTRIBUTORS),
      allowNominations: c?.feedback.allowNominations ?? true,
      feedbackVisibility: c?.feedback.visibility ?? 'MANAGER_ONLY',
      requiresSignOff: c?.requiresSignOff ?? false,
      templateId: c?.template?.id ?? '',
    });
    this.frameworkService.templates().subscribe({
      next: list => this.templates.set(list),
      error: () => this.templates.set([]),
    });
    this.selected.set(new Set(c?.departmentIds ?? []));
    this.people.set(c?.employees ?? []);
    if (this.departments() === null) {
      this.departmentService.query({ page: 0, size: 500, sort: ['name,asc'] }).subscribe({
        next: page => this.departments.set(page.content),
        error: () => this.departments.set([]),
      });
    }
  }

  private label(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
