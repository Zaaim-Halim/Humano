import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { debounceTime } from 'rxjs';
import { Router } from '@angular/router';
import { DatePipe } from '@angular/common';
import { TranslatePipe } from '@ngx-translate/core';

import { createListResource } from 'app/core/api';
import {
  AlertComponent,
  ButtonComponent,
  EmptyStateComponent,
  InputComponent,
  PageHeaderComponent,
  SkeletonRowComponent,
} from 'app/shared/ui';

import { Payslip, PayslipSearchRequest } from '../index';
import { PayslipService } from '../index';

/**
 * Payslips list (HR/Admin) — closes the list→detail loop for the existing
 * payslip detail screen (`/payroll/payslips/:id`). Backed by the real
 * `POST /api/payroll/payslips/search` (`@RequirePayrollOrHrManager`), paged,
 * with a payslip-number filter; row → detail.
 */
@Component({
  selector: 'hum-payslips',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    DatePipe,
    TranslatePipe,
    PageHeaderComponent,
    InputComponent,
    ButtonComponent,
    AlertComponent,
    EmptyStateComponent,
    SkeletonRowComponent,
  ],
  templateUrl: './payslips.component.html',
})
export default class PayslipsComponent {
  private readonly fb = inject(FormBuilder);
  private readonly payslipService = inject(PayslipService);
  private readonly router = inject(Router);

  protected readonly filters = this.fb.nonNullable.group({ number: '' });
  private readonly criteria = signal<PayslipSearchRequest>({});

  protected readonly list = createListResource<Payslip>(req => this.payslipService.search(this.criteria(), req), {
    initial: { size: 25 },
  });

  constructor() {
    this.filters.valueChanges.pipe(debounceTime(250), takeUntilDestroyed()).subscribe(v => {
      const number = v.number?.trim();
      this.criteria.set(number ? { payslipNumber: number } : {});
      this.list.setParams({ page: 0 });
    });
  }

  protected readonly hasFilter = (): boolean => !!this.filters.getRawValue().number.trim();

  protected clearFilters(): void {
    this.filters.reset({ number: '' });
  }

  protected open(p: Payslip): void {
    void this.router.navigate(['/payroll/payslips', p.id]);
  }
}
