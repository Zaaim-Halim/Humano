import { ChangeDetectionStrategy, Component, effect, inject, signal, untracked } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  ButtonComponent,
  CardComponent,
  EmptyStateComponent,
  FormFieldComponent,
  InputComponent,
  PageHeaderComponent,
  SkeletonRowComponent,
  ToastService,
} from 'app/shared/ui';

import { CreateTimesheetRequest, Timesheet, TimesheetService } from '../index';
import { CurrentEmployeeService } from '../services/current-employee.service';

/**
 * Timesheets (Employee self-service) — log hours worked and review recent
 * entries. Create/read-own are `@RequireHrStaffOrEmployee`; logged hours feed
 * payroll inputs downstream.
 *
 * Like the rest of the self-service persona it depends on resolving the
 * caller's own `employeeId` (see {@link CurrentEmployeeService}): while that is
 * unresolved the form validates but submit is disabled behind an honest
 * "not linked to a profile" notice and no entries are fetched, rather than
 * POSTing a fabricated id. It enables automatically once the seam resolves.
 *
 * `projectId` is omitted — no project reference endpoint is exposed to the
 * frontend yet (it is optional on the create request).
 */
@Component({
  selector: 'hum-timesheets',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    PageHeaderComponent,
    CardComponent,
    FormFieldComponent,
    InputComponent,
    ButtonComponent,
    AlertComponent,
    EmptyStateComponent,
    SkeletonRowComponent,
  ],
  templateUrl: './timesheets.component.html',
})
export default class TimesheetsComponent {
  private readonly fb = inject(FormBuilder);
  private readonly timesheetService = inject(TimesheetService);
  private readonly currentEmployee = inject(CurrentEmployeeService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);

  protected readonly employeeId = this.currentEmployee.currentEmployeeId;
  protected readonly resolved = this.currentEmployee.resolved;

  protected readonly form = this.fb.nonNullable.group({
    date: ['', Validators.required],
    hoursWorked: ['', [Validators.required, Validators.min(0.1), Validators.max(24)]],
  });

  protected readonly submitting = signal(false);
  protected readonly entries = signal<Timesheet[] | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.currentEmployee.resolve();
    // Load own entries once a self id resolves; no-op while it stays null.
    effect(() => {
      const id = this.employeeId();
      if (!id) return;
      untracked(() => this.load(id));
    });
  }

  protected invalid(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  protected retry(): void {
    const id = this.employeeId();
    if (id) this.load(id);
  }

  protected submit(): void {
    const employeeId = this.employeeId();
    if (this.form.invalid || !employeeId) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const payload: CreateTimesheetRequest = {
      employeeId,
      date: raw.date,
      hoursWorked: Number(raw.hoursWorked),
    };

    this.submitting.set(true);
    this.timesheetService.create(payload).subscribe({
      next: () => {
        this.toast.success(this.translate.instant('humano.timesheets.logged'));
        this.submitting.set(false);
        this.form.reset({ date: '', hoursWorked: '' });
        this.load(employeeId);
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.submitting.set(false);
      },
    });
  }

  private load(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.timesheetService.searchByEmployee(id, {}, { size: 50, sort: ['date,desc'] }).subscribe({
      next: page => {
        this.entries.set(page.content);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.loading.set(false);
      },
    });
  }
}
