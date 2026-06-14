import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { Page, normalizeHttpError } from 'app/core/api';
import { Authority } from 'app/config/authority.constants';
import { AccountService } from 'app/core/auth/account.service';
import { SimpleEmployeeProfile, EmployeeService, LeaveRequestService, LeaveStatus } from 'app/features/employee';

import { DepartmentService } from '../index';
import {
  AlertComponent,
  ButtonComponent,
  CardComponent,
  EmptyStateComponent,
  PageHeaderComponent,
  SkeletonComponent,
  StatTileComponent,
} from 'app/shared/ui';

interface Kpi {
  value: number | null;
  loading: boolean;
  error: boolean;
}
const kpiIdle = (): Kpi => ({ value: null, loading: true, error: false });

/**
 * HR / Admin Dashboard (hero screen) — the landing surface. The spec's
 * aggregate `GET /api/admin/dashboard` does not exist, so each KPI is assembled
 * from a real list endpoint's `totalElements` (parallel fetch, per-tile skeleton),
 * plus a recent-joiners feed. Role-aware: non-admins get a welcome instead of
 * admin data (their portal endpoints are absent — Tier 3).
 *
 * TODO: backend — headcount sparkline needs a time series, and "next pay run"
 * needs a calendar-scoped period lookup; neither endpoint exists. Omitted (no
 * fabricated KPIs), shown as a TODO card.
 */
@Component({
  selector: 'hum-dashboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    PageHeaderComponent,
    StatTileComponent,
    CardComponent,
    ButtonComponent,
    AlertComponent,
    EmptyStateComponent,
    SkeletonComponent,
  ],
  templateUrl: './dashboard.component.html',
})
export default class DashboardComponent {
  private readonly employeeService = inject(EmployeeService);
  private readonly departmentService = inject(DepartmentService);
  private readonly leaveService = inject(LeaveRequestService);
  private readonly accountService = inject(AccountService);
  private readonly router = inject(Router);

  protected readonly isAdmin = this.accountService.hasAnyAuthority(Authority.ADMIN);
  protected readonly today = new Date().toISOString().slice(0, 10);

  protected readonly headcount = signal<Kpi>(kpiIdle());
  protected readonly departments = signal<Kpi>(kpiIdle());
  protected readonly pendingLeave = signal<Kpi>(kpiIdle());
  protected readonly onLeaveToday = signal<Kpi>(kpiIdle());

  protected readonly joiners = signal<SimpleEmployeeProfile[] | null>(null);
  protected readonly joinersLoading = signal(true);
  protected readonly joinersError = signal<string | null>(null);

  constructor() {
    if (this.isAdmin) {
      this.loadKpis();
      this.loadJoiners();
    }
  }

  private loadKpis(): void {
    // Each KPI is one list call read for its total — fired in parallel, each tile
    // resolves independently (concurrent skeletons).
    this.count(this.headcount, this.employeeService.query({ size: 1 }));
    this.count(this.departments, this.departmentService.query({ size: 1 }));
    this.count(this.pendingLeave, this.leaveService.search({ status: LeaveStatus.PENDING }, { size: 1 }));
    this.count(
      this.onLeaveToday,
      this.leaveService.search({ status: LeaveStatus.APPROVED, startDateTo: this.today, endDateFrom: this.today }, { size: 1 }),
    );
  }

  private count(target: ReturnType<typeof signal<Kpi>>, call: Observable<Page<unknown>>): void {
    call.pipe(map(p => p.totalElements)).subscribe({
      next: total => target.set({ value: total, loading: false, error: false }),
      error: () => target.set({ value: null, loading: false, error: true }),
    });
  }

  private loadJoiners(): void {
    this.joinersLoading.set(true);
    this.joinersError.set(null);
    this.employeeService.query({ size: 5, sort: ['startDate,desc'] }).subscribe({
      next: page => {
        this.joiners.set(page.content);
        this.joinersLoading.set(false);
      },
      error: (err: unknown) => {
        this.joinersError.set(normalizeHttpError(err));
        this.joinersLoading.set(false);
      },
    });
  }

  protected retryJoiners(): void {
    this.loadJoiners();
  }

  protected goToEmployees(): void {
    void this.router.navigate(['/employees']);
  }

  protected openEmployee(id: string): void {
    void this.router.navigate(['/employees', id]);
  }
}
