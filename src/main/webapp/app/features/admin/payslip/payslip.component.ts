import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { normalizeHttpError } from 'app/core/api';
import { AlertComponent, ButtonComponent, EmptyStateComponent, SkeletonRowComponent, ToastService } from 'app/shared/ui';

import { Payslip, PayslipService } from '../index';

/**
 * Payslip (HR/Admin hero screen) — print-ready letterhead for a single payslip
 * (`GET /api/payroll/payslips/{id}`), with earnings / deductions / employer
 * contributions line items (from `details`), totals, and a YTD footer
 * (`/employees/{id}/ytd?year=`). Print drops the app chrome via global
 * `@media print` rules; PDF download streams `/{id}/pdf`. `id` is route-bound.
 */
@Component({
  selector: 'hum-payslip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, ButtonComponent, AlertComponent, EmptyStateComponent, SkeletonRowComponent],
  templateUrl: './payslip.component.html',
})
export default class PayslipComponent {
  /** Payslip id from the route (`/payroll/payslips/:id`). */
  readonly id = input.required<string>();

  private readonly payslipService = inject(PayslipService);
  private readonly toast = inject(ToastService);
  private readonly document = inject(DOCUMENT);

  protected readonly payslip = signal<Payslip | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  /** YTD footer — a backend map (`Map<String,Object>`); rendered generically. */
  protected readonly ytd = signal<Record<string, unknown> | null>(null);
  protected readonly ytdEntries = computed(() => Object.entries(this.ytd() ?? {}));

  constructor() {
    effect(() => {
      const id = this.id();
      if (id) untracked(() => this.load(id));
    });
  }

  protected load(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.ytd.set(null);
    this.payslipService.find(id).subscribe({
      next: p => {
        this.payslip.set(p);
        this.loading.set(false);
        this.loadYtd(p);
      },
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.loading.set(false);
      },
    });
  }

  private loadYtd(p: Payslip): void {
    const year = Number((p.paymentDate ?? p.periodEnd ?? '').slice(0, 4));
    if (!p.employeeId || !year) return;
    // YTD is supplementary; its failure must not break the payslip view.
    this.payslipService.ytdForEmployee(p.employeeId, year).subscribe({
      next: data => this.ytd.set(data),
      error: () => this.ytd.set(null),
    });
  }

  protected print(): void {
    this.document.defaultView?.print();
  }

  protected downloadPdf(): void {
    const p = this.payslip();
    if (!p) return;
    this.payslipService.downloadPdf(p.id).subscribe({
      next: res => {
        const blob = res.body;
        if (!blob) return;
        const url = URL.createObjectURL(blob);
        const a = this.document.createElement('a');
        a.href = url;
        a.download = `payslip-${p.number}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (err: unknown) => this.toast.danger(normalizeHttpError(err)),
    });
  }
}
