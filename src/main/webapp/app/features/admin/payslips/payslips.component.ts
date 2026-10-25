import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { debounceTime } from 'rxjs/operators';

import { createListResource } from 'app/core/api';
import {
  AlertComponent,
  AvatarComponent,
  ButtonComponent,
  EmptyStateComponent,
  IconButtonComponent,
  InputComponent,
  PageHeaderComponent,
  SkeletonComponent,
} from 'app/shared/ui';
import { MoneyPipe, PayPeriodPipe, RunMoneyPipe } from 'app/shared/util/format.pipes';

import { PayrollRun, PayrollRunService, Payslip, PayslipSearchRequest, PayslipService } from '../index';

const VIEW_KEY = 'hum.payslips.view';

function sessionStorageView(): 'cards' | 'table' {
  try {
    return sessionStorage.getItem(VIEW_KEY) === 'table' ? 'table' : 'cards';
  } catch {
    return 'cards';
  }
}

/**
 * Payslips — browse payslips by pay run.
 *
 * Pay-period chips come from the run history (newest selected first) and the selected run's
 * totals head the page; the grid shows one card per payslip — net pay, the gross → net split
 * and department — or as a compact table (toggle in the toolbar), searchable by employee name or
 * payslip number (`POST /payslips/search` with `payrollRunId` + `query`). A card or row opens
 * the printable payslip.
 */
@Component({
  selector: 'hum-payslips',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MoneyPipe,
    RunMoneyPipe,
    PayPeriodPipe,
    ReactiveFormsModule,
    RouterLink,
    DatePipe,
    TranslatePipe,
    LucideAngularModule,
    PageHeaderComponent,
    InputComponent,
    ButtonComponent,
    IconButtonComponent,
    AvatarComponent,
    AlertComponent,
    EmptyStateComponent,
    SkeletonComponent,
  ],
  templateUrl: './payslips.component.html',
})
export default class PayslipsComponent {
  private readonly fb = inject(FormBuilder);
  private readonly payslipService = inject(PayslipService);
  private readonly runService = inject(PayrollRunService);
  private readonly router = inject(Router);

  protected readonly pageSize = 24;
  /** Card grid or compact table; remembered for the session. */
  protected readonly view = signal<'cards' | 'table'>(sessionStorageView());
  protected readonly filters = this.fb.nonNullable.group({ q: '' });

  /** Runs that have payslips (approved or posted), newest first. */
  protected readonly runs = signal<PayrollRun[] | null>(null);
  protected readonly selectedRun = signal<PayrollRun | null>(null);
  private readonly criteria = signal<PayslipSearchRequest>({});

  protected readonly list = createListResource<Payslip>(req => this.payslipService.search(this.criteria(), req), {
    initial: { size: this.pageSize, sort: ['result.employee.firstName,asc'] },
    autoLoad: false,
  });
  protected readonly page = computed(() => this.list.params().page ?? 0);

  constructor() {
    this.runService.list({ page: 0, size: 12 }).subscribe({
      next: page => {
        const withPayslips = page.content.filter(r => r.status === 'APPROVED' || r.status === 'POSTED');
        this.runs.set(withPayslips);
        this.selectRun(withPayslips[0] ?? null);
      },
      error: () => {
        this.runs.set([]);
        this.selectRun(null);
      },
    });
    this.filters.valueChanges.pipe(debounceTime(250), takeUntilDestroyed()).subscribe(() => this.applyCriteria());
  }

  protected setView(view: 'cards' | 'table'): void {
    this.view.set(view);
    try {
      sessionStorage.setItem(VIEW_KEY, view);
    } catch {
      // Storage unavailable (private mode) — the choice just isn't remembered.
    }
  }

  protected open(p: Payslip): void {
    void this.router.navigate(['/payroll/payslips', p.id]);
  }

  protected selectRun(run: PayrollRun | null): void {
    this.selectedRun.set(run);
    this.applyCriteria();
  }

  protected clearSearch(): void {
    this.filters.reset({ q: '' });
  }

  protected goto(page: number): void {
    this.list.setParams({ page });
  }

  /** Net as a share of gross, for the split bar. */
  protected netShare(p: Payslip): number {
    return p.gross ? Math.max(0, Math.min(100, (p.net / p.gross) * 100)) : 0;
  }

  private applyCriteria(): void {
    const q = this.filters.getRawValue().q.trim();
    const run = this.selectedRun();
    this.criteria.set({ ...(run ? { payrollRunId: run.id } : {}), ...(q ? { query: q } : {}) });
    this.list.setParams({ page: 0 });
  }
}
