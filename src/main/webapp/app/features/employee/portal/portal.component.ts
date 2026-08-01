import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';

import { AccountService } from 'app/core/auth/account.service';
import { CardComponent, EmptyStateComponent, PageHeaderComponent, SkeletonComponent } from 'app/shared/ui';

import { CurrentEmployeeService } from '../services/current-employee.service';

interface QuickAction {
  icon: string;
  labelKey: string;
  link: string;
}

/**
 * Employee self-service portal home (mobile-first) — the persona's landing
 * surface. Wired to identity (`GET /api/account` via {@link AccountService});
 * everything employee-scoped (latest payslip, leave balance, tasks) has no
 * self-service endpoint and is rendered as an honest empty/TODO state rather
 * than fabricated.
 *
 * TODO: backend endpoints missing — the spec's `/api/me/dashboard`,
 * `/api/me/leaves` (balance), `/api/me/payslips` and `/api/me/onboarding` are
 * not implemented, and payslips are additionally `@RequirePayrollOrHrManager`.
 * The caller's own `employeeId` does resolve now (`GET /api/me/employee`, see
 * {@link CurrentEmployeeService}), so the data cards below light up as soon as
 * those endpoints exist.
 */
@Component({
  selector: 'hum-portal',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, RouterLink, LucideAngularModule, PageHeaderComponent, CardComponent, EmptyStateComponent, SkeletonComponent],
  templateUrl: './portal.component.html',
})
export default class PortalComponent {
  private readonly accountService = inject(AccountService);
  private readonly currentEmployee = inject(CurrentEmployeeService);

  protected readonly account = this.accountService.trackCurrentAccount();
  protected readonly firstName = computed(() => {
    const a = this.account();
    return a?.firstName?.trim() || a?.login || '';
  });

  /** Drives the "latest payslip" / "leave balance" cards once a self id resolves. */
  protected readonly employeeId = this.currentEmployee.currentEmployeeId;

  protected readonly quickActions: QuickAction[] = [
    { icon: 'id-card', labelKey: 'humano.portal.actionProfile', link: '/my-profile' },
    { icon: 'palmtree', labelKey: 'humano.portal.actionLeave', link: '/leave' },
    { icon: 'clock', labelKey: 'humano.portal.actionTimesheet', link: '/timesheets' },
    { icon: 'file-text', labelKey: 'humano.portal.actionDocuments', link: '/my-documents' },
    { icon: 'user', labelKey: 'humano.portal.actionAccount', link: '/account/settings' },
  ];

  constructor() {
    // Resolve the caller's own employee id; a no-op once any screen has already done it.
    this.currentEmployee.resolve();
  }
}
