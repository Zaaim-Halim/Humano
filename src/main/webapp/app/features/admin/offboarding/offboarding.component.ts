import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { Observable } from 'rxjs';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { normalizeHttpError } from 'app/core/api';
import { EmployeeService } from 'app/features/employee';
import {
  AlertComponent,
  AutocompleteComponent,
  AutocompleteOption,
  BadgeComponent,
  ButtonComponent,
  CheckboxComponent,
  DrawerComponent,
  EmptyStateComponent,
  FormFieldComponent,
  InputComponent,
  PageHeaderComponent,
  ProgressComponent,
  SelectComponent,
  SelectOption,
  SkeletonComponent,
  TabItem,
  TabsComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';

import {
  EmployeeProcessStatus,
  EmployeeProcessSummary,
  InitiateOffboardingRequest,
  OffboardingService,
  addDays,
  daysBetween,
  localIsoDate,
} from '../index';
import { TerminationReasonService } from '../services/termination-reason.service';

type View = 'active' | 'history';
const HISTORY_STATUSES = [EmployeeProcessStatus.COMPLETED, EmployeeProcessStatus.CANCELLED];
/** Default last working day offered in the start panel: a month's notice. */
const DEFAULT_NOTICE_DAYS = 30;

/**
 * Offboarding — people who are leaving, in two tabs (`?view=`):
 *
 * - Active: overview (leaving, leaving this week, past their last day with tasks open, average
 *   completion) and one row per open process — the leaver, their last working day with a
 *   countdown, checklist progress and status.
 * - History: completed and cancelled offboardings, most recent first, with the cancel reason.
 *
 * A row opens the checklist. "Start offboarding" (`MANAGE_OFFBOARDING`, plus `READ_EMPLOYEE` to
 * search for the employee) asks for the leaver, last working day, termination reason (the
 * company's list), why they're leaving, an optional exit interview and notes. Starting marks them
 * as leaving; the day after their last working day they're terminated automatically (a date in
 * the past ends employment at once). `?start=<employeeId>` opens the panel for that person.
 */
@Component({
  selector: 'hum-offboarding',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    DatePipe,
    TranslatePipe,
    LucideAngularModule,
    PageHeaderComponent,
    FormFieldComponent,
    AutocompleteComponent,
    InputComponent,
    SelectComponent,
    TextareaComponent,
    CheckboxComponent,
    ButtonComponent,
    BadgeComponent,
    ProgressComponent,
    TabsComponent,
    DrawerComponent,
    AlertComponent,
    EmptyStateComponent,
    SkeletonComponent,
  ],
  templateUrl: './offboarding.component.html',
})
export default class OffboardingComponent {
  private readonly fb = inject(FormBuilder);
  private readonly offboardingService = inject(OffboardingService);
  private readonly employeeService = inject(EmployeeService);
  private readonly reasonService = inject(TerminationReasonService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly account = inject(AccountService);

  protected readonly canStart =
    this.account.hasPermission(Permission.MANAGE_OFFBOARDING) && this.account.hasPermission(Permission.READ_EMPLOYEE);
  protected readonly today = localIsoDate();

  protected readonly view = signal<View>(this.route.snapshot.queryParamMap.get('view') === 'history' ? 'history' : 'active');
  protected readonly tabs = computed<TabItem[]>(() => [
    { id: 'active', label: this.label('humano.offboarding.tabActive'), icon: 'user-minus' },
    { id: 'history', label: this.label('humano.offboarding.tabHistory'), icon: 'history' },
  ]);
  protected readonly rows = signal<EmployeeProcessSummary[] | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly stats = computed(() => {
    const list = this.rows() ?? [];
    const weekAhead = addDays(this.today, 7);
    return {
      leaving: list.length,
      thisWeek: list.filter(r => !!r.dueDate && r.dueDate >= this.today && r.dueDate <= weekAhead).length,
      pastLastDay: list.filter(r => this.hasLeft(r)).length,
      average: list.length ? Math.round(list.reduce((sum, r) => sum + r.completionPercentage, 0) / list.length) : 0,
    };
  });

  // Start panel.
  protected readonly formOpen = signal(false);
  protected readonly submitting = signal(false);
  protected readonly startFor = signal<AutocompleteOption | null>(null);
  protected readonly reasonOptions = signal<SelectOption[]>([]);
  protected readonly form = this.fb.nonNullable.group({
    employeeId: ['', Validators.required],
    lastWorkingDate: [addDays(this.today, DEFAULT_NOTICE_DAYS), Validators.required],
    terminationReasonId: [''],
    reason: ['', [Validators.required, Validators.maxLength(500)]],
    conductExitInterview: [false],
    exitInterviewerId: [''],
    notes: ['', Validators.maxLength(2000)],
  });
  private readonly formValue = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  /** Days from today to the chosen last working day (negative = already past). */
  protected readonly noticeDays = computed(() => {
    const last = this.formValue().lastWorkingDate;
    return last ? daysBetween(this.today, last) : null;
  });
  protected readonly exitInterview = computed(() => this.formValue().conductExitInterview === true);
  protected readonly searchEmployees = (term: string): Observable<AutocompleteOption[]> => this.employeeService.searchOptions(term);

  constructor() {
    this.load();
    const startId = this.route.snapshot.queryParamMap.get('start');
    if (this.canStart && startId) this.openStartFor(startId);
  }

  /** Past the last working day (employment has ended) while the checklist is still open. */
  protected hasLeft(row: EmployeeProcessSummary): boolean {
    return !!row.dueDate && row.dueDate < this.today;
  }

  /** "In 5 d", "Today", "Left 3 d ago". */
  protected countdown(row: EmployeeProcessSummary): string {
    if (!row.dueDate) return '';
    const days = daysBetween(this.today, row.dueDate);
    if (days > 0) return this.label('humano.offboarding.inDays', { count: days });
    if (days === 0) return this.label('humano.offboarding.lastDayToday');
    return this.label('humano.offboarding.leftAgo', { count: -days });
  }

  protected endedOn(row: EmployeeProcessSummary): string | null {
    return row.status === EmployeeProcessStatus.COMPLETED ? row.completionDate : row.lastModifiedDate;
  }

  protected setView(view: string | undefined): void {
    const next: View = view === 'history' ? 'history' : 'active';
    if (next === this.view()) return;
    this.view.set(next);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { view: next === 'history' ? 'history' : null },
      replaceUrl: true,
    });
    this.load();
  }

  protected openStart(employee: AutocompleteOption | null = null): void {
    this.startFor.set(employee);
    this.form.reset({
      employeeId: employee?.value ?? '',
      lastWorkingDate: addDays(this.today, DEFAULT_NOTICE_DAYS),
      terminationReasonId: '',
      reason: '',
      conductExitInterview: false,
      exitInterviewerId: '',
      notes: '',
    });
    this.formOpen.set(true);
    if (!this.reasonOptions().length) {
      this.reasonService.query({ page: 0, size: 100, sort: ['name,asc'] }).subscribe({
        next: page =>
          this.reasonOptions.set([
            { value: '', label: this.label('humano.offboarding.reasonNone') },
            ...page.content.filter(r => r.active).map(r => ({ value: r.id, label: r.name })),
          ]),
        error: () => this.reasonOptions.set([]),
      });
    }
  }

  protected invalid(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  protected load(): void {
    this.error.set(null);
    this.rows.set(null);
    const history = this.view() === 'history';
    const request$ = history
      ? this.offboardingService.list({ status: HISTORY_STATUSES }, { page: 0, size: 100, sort: ['lastModifiedDate,desc'] })
      : this.offboardingService.list({}, { page: 0, size: 100 });
    request$.subscribe({
      next: page =>
        this.rows.set(history ? page.content : [...page.content].sort((a, b) => (a.dueDate ?? '').localeCompare(b.dueDate ?? ''))),
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.rows.set([]);
      },
    });
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const body: InitiateOffboardingRequest = {
      employeeId: raw.employeeId,
      lastWorkingDate: raw.lastWorkingDate,
      reason: raw.reason.trim(),
      conductExitInterview: raw.conductExitInterview,
      ...(raw.terminationReasonId ? { terminationReasonId: raw.terminationReasonId } : {}),
      ...(raw.conductExitInterview && raw.exitInterviewerId ? { exitInterviewerId: raw.exitInterviewerId } : {}),
      ...(raw.notes.trim() ? { notes: raw.notes.trim() } : {}),
    };
    this.submitting.set(true);
    this.offboardingService.initiate(body).subscribe({
      next: process => {
        this.toast.success(this.label('humano.offboarding.started', { name: process.employeeName }));
        this.submitting.set(false);
        this.formOpen.set(false);
        void this.router.navigate(['/offboarding', process.processId]);
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.submitting.set(false);
      },
    });
  }

  /** Opens the start panel for one employee (from their profile), then drops `?start=`. */
  private openStartFor(employeeId: string): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: { start: null }, queryParamsHandling: 'merge', replaceUrl: true });
    this.employeeService.find(employeeId).subscribe({
      next: e => this.openStart({ value: e.id, label: [e.firstName, e.lastName].filter(Boolean).join(' '), sublabel: e.jobTitle }),
      error: (err: unknown) => this.toast.danger(normalizeHttpError(err)),
    });
  }

  private label(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
