import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  ButtonComponent,
  CardComponent,
  FormFieldComponent,
  InputComponent,
  PageHeaderComponent,
  ProgressComponent,
  SelectComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';

import { CreateLeaveRequest, LeaveType } from '../index';
import { LeaveRequestService } from '../index';
import { CurrentEmployeeService } from '../services/current-employee.service';

/** End date must not precede start date. */
function dateOrder(group: AbstractControl): ValidationErrors | null {
  const start = group.get('startDate')?.value;
  const end = group.get('endDate')?.value;
  return start && end && end < start ? { dateOrder: true } : null;
}

/**
 * Leave request (Employee hero screen) — a reactive form wired to the real
 * `POST /api/hr/leave-requests` (`@RequireHrStaffOrEmployee`). The working-days
 * estimate and balance-impact preview are computed client-side.
 *
 * Submission depends on the signed-in user's `employeeId`, which the request
 * body requires but which no self-service endpoint exposes yet (see
 * {@link CurrentEmployeeService}) — so when it is unresolved the form validates
 * and previews normally but submit is disabled with an explanatory notice
 * rather than POSTing a fabricated id. It enables automatically once the seam
 * resolves.
 *
 * TODO: backend endpoint missing — no leave-balance endpoint exists, so the
 * "balance impact" card shows the requested days only (no remaining-balance
 * figure), and no coverage/overlap endpoint exists for a conflict check.
 */
@Component({
  selector: 'hum-leave-request',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    PageHeaderComponent,
    CardComponent,
    FormFieldComponent,
    SelectComponent,
    InputComponent,
    TextareaComponent,
    ButtonComponent,
    AlertComponent,
    ProgressComponent,
  ],
  templateUrl: './leave-request.component.html',
})
export default class LeaveRequestComponent {
  private readonly fb = inject(FormBuilder);
  private readonly leaveService = inject(LeaveRequestService);
  private readonly currentEmployee = inject(CurrentEmployeeService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);

  protected readonly employeeId = this.currentEmployee.currentEmployeeId;

  protected readonly form = this.fb.nonNullable.group(
    {
      leaveType: ['', Validators.required],
      startDate: ['', Validators.required],
      endDate: ['', Validators.required],
      reason: ['', [Validators.required, Validators.minLength(20), Validators.maxLength(1000)]],
    },
    { validators: dateOrder },
  );

  private readonly value = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });

  /** Inclusive working-day count (excludes Sat/Sun) for the selected range. */
  protected readonly workingDays = computed(() => {
    const { startDate, endDate } = this.value();
    if (!startDate || !endDate || endDate < startDate) return 0;
    let count = 0;
    const cursor = new Date(startDate + 'T00:00:00');
    const end = new Date(endDate + 'T00:00:00');
    while (cursor <= end) {
      const day = cursor.getDay();
      if (day !== 0 && day !== 6) count++;
      cursor.setDate(cursor.getDate() + 1);
    }
    return count;
  });

  protected readonly typeOptions = computed(() => [
    { value: '', label: this.translate.instant('humano.leaveRequest.selectType') },
    ...Object.values(LeaveType).map(t => ({ value: t, label: t })),
  ]);

  protected readonly submitting = signal(false);

  protected invalid(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  constructor() {
    this.currentEmployee.resolve();
  }

  protected submit(): void {
    const employeeId = this.employeeId();
    if (this.form.invalid || !employeeId) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const payload: CreateLeaveRequest = {
      employeeId,
      leaveType: raw.leaveType as LeaveType,
      startDate: raw.startDate,
      endDate: raw.endDate,
      reason: raw.reason,
    };

    this.submitting.set(true);
    this.leaveService.create(payload).subscribe({
      next: () => {
        this.toast.success(this.translate.instant('humano.leaveRequest.submitted'));
        this.submitting.set(false);
        this.form.reset({ leaveType: '', startDate: '', endDate: '', reason: '' });
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.submitting.set(false);
      },
    });
  }
}
