import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { EmployeeService, LEAVE_TYPE_COLOR, LeaveRequestService, LeaveStatus } from 'app/features/employee';
import { EmployeeDirectoryStats, SimpleEmployeeProfile, personName } from 'app/features/employee/models/employee.model';
import { LeaveCalendarEntry } from 'app/features/employee/models/leave-request.model';
import { TeamAttendance, TeamPresence } from 'app/features/employee/models/time-clock.model';
import { CurrentEmployeeService } from 'app/features/employee/services/current-employee.service';
import { MeWorkService } from 'app/features/employee/services/me-work.service';
import { ApprovalService } from 'app/features/manager';
import { CompanyService } from 'app/core/company/company.service';
import { SubscriptionStatus } from 'app/features/platform/models/enums/subscription-status.enum';
import { MeBillingService } from 'app/features/platform/services/me-billing.service';
import { AvatarComponent, PageHeaderComponent, ProgressComponent, SkeletonComponent } from 'app/shared/ui';
import { MoneyPipe, PayPeriodPipe } from 'app/shared/util/format.pipes';

import { PayrollRun, PayrollRunService, RunStatus } from '../index';

const SETUP_DISMISSED_KEY = 'humano-setup-dismissed';

function readDismissed(): boolean {
  try {
    return localStorage.getItem(SETUP_DISMISSED_KEY) === '1';
  } catch {
    return false;
  }
}

function iso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

const PRESENCE_ORDER: TeamPresence[] = ['WORKING', 'ON_BREAK', 'DONE', 'ON_LEAVE', 'NOT_IN', 'MISSING_CLOCK_OUT', 'ABSENT', 'OFF'];
const PRESENCE_COLOR: Record<TeamPresence, string> = {
  WORKING: 'var(--success-solid)',
  ON_BREAK: 'var(--warning-solid)',
  DONE: 'var(--brand)',
  ON_LEAVE: 'var(--info-solid)',
  NOT_IN: 'var(--slate-400)',
  MISSING_CLOCK_OUT: 'var(--danger-solid)',
  ABSENT: 'var(--danger-solid)',
  OFF: 'var(--slate-300)',
};
const LOCATION_COLOR: Record<string, string> = {
  ONSITE: 'var(--brand)',
  HYBRID: 'var(--violet-500)',
  REMOTE: 'var(--info-solid)',
  UNSPECIFIED: 'var(--slate-300)',
};

/**
 * Dashboard — the organization at a glance for HR, payroll and managers (people with directory
 * access; everyone else is sent to My space by the route guard).
 *
 * - Figures: headcount (and joiners in the last 90 days), clocked in now, on leave today,
 *   pending leave, approvals waiting on you.
 * - Payroll: the last posted run, the run in progress (or a prompt to start one) and net pay
 *   over the last runs.
 * - People: headcount by department and by work location.
 * - Today: attendance by presence (links to Team attendance), who is off in the next two weeks,
 *   and recent joiners.
 *
 * Every block loads independently and only when the viewer has the matching permission.
 */
@Component({
  selector: 'hum-dashboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    RouterLink,
    TranslatePipe,
    LucideAngularModule,
    MoneyPipe,
    PayPeriodPipe,
    PageHeaderComponent,
    AvatarComponent,
    ProgressComponent,
    SkeletonComponent,
  ],
  templateUrl: './dashboard.component.html',
})
export default class DashboardComponent {
  private readonly account = inject(AccountService);
  private readonly employeeService = inject(EmployeeService);
  private readonly leaveService = inject(LeaveRequestService);
  private readonly runService = inject(PayrollRunService);
  private readonly meWork = inject(MeWorkService);
  private readonly approvals = inject(ApprovalService);
  private readonly currentEmployee = inject(CurrentEmployeeService);
  private readonly translate = inject(TranslateService);

  protected readonly canPayroll = this.account.hasPermission(Permission.VIEW_PAYROLL_RUN);
  protected readonly canLeave = this.account.hasPermission(Permission.VIEW_LEAVE_REQUESTS);

  protected readonly greeting = computed(() => {
    const h = new Date().getHours();
    const name = this.account.trackCurrentAccount()()?.firstName ?? '';
    return this.t(h < 12 ? 'humano.dashboard.morning' : h < 18 ? 'humano.dashboard.afternoon' : 'humano.dashboard.evening', { name });
  });
  protected readonly today = new Date();

  // People
  protected readonly stats = signal<EmployeeDirectoryStats | null>(null);
  protected readonly departments = computed(() => {
    const list = (this.stats()?.byDepartment ?? []).filter(d => d.count > 0).slice(0, 7);
    const max = Math.max(1, ...list.map(d => d.count));
    return list.map(d => ({ ...d, pct: (d.count / max) * 100 }));
  });
  protected readonly locations = computed(() => {
    const s = this.stats();
    if (!s) return [];
    const total = Math.max(1, s.total);
    return Object.entries(s.byWorkLocation)
      .filter(([, n]) => n > 0)
      .sort((a, b) => b[1] - a[1])
      .map(([key, count]) => ({ key, count, pct: (count / total) * 100, color: LOCATION_COLOR[key] ?? 'var(--slate-400)' }));
  });
  protected readonly joiners = signal<SimpleEmployeeProfile[] | null>(null);
  protected readonly personName = personName;

  // Leave
  protected readonly pendingLeave = signal<number | null>(null);
  protected readonly upcomingLeave = signal<LeaveCalendarEntry[] | null>(null);
  protected readonly onLeaveToday = computed(
    () => (this.upcomingLeave() ?? []).filter(e => e.status === LeaveStatus.APPROVED && e.startDate <= iso(this.today)).length,
  );
  /** Next two weeks, one entry per person (their next absence). */
  protected readonly offSoon = computed(() => {
    const seen = new Set<string>();
    return (this.upcomingLeave() ?? [])
      .filter(e => e.status === LeaveStatus.APPROVED)
      .sort((a, b) => a.startDate.localeCompare(b.startDate))
      .filter(e => !seen.has(e.employeeId) && seen.add(e.employeeId))
      .slice(0, 6);
  });
  protected readonly leaveColor = (t: string | null): string => (t ? (LEAVE_TYPE_COLOR[t] ?? 'var(--info-solid)') : 'var(--slate-400)');

  // Attendance
  protected readonly attendance = signal<TeamAttendance | null>(null);
  protected readonly presence = computed(() => {
    const rows = this.attendance()?.rows ?? [];
    const counts = new Map<TeamPresence, number>();
    for (const r of rows) counts.set(r.presence, (counts.get(r.presence) ?? 0) + 1);
    const total = Math.max(1, rows.length);
    return PRESENCE_ORDER.filter(p => counts.has(p)).map(p => ({
      presence: p,
      count: counts.get(p)!,
      pct: (counts.get(p)! / total) * 100,
      color: PRESENCE_COLOR[p],
    }));
  });
  protected readonly clockedIn = computed(() => {
    const rows = this.attendance()?.rows ?? [];
    return { now: rows.filter(r => r.presence === 'WORKING' || r.presence === 'ON_BREAK').length, total: rows.length };
  });

  // Approvals
  protected readonly waitingOnMe = signal<number | null>(null);

  // Payroll
  protected readonly runs = signal<PayrollRun[] | null>(null);
  protected readonly lastPosted = computed(() => (this.runs() ?? []).find(r => r.status === RunStatus.POSTED) ?? null);
  protected readonly inProgress = computed(() => (this.runs() ?? []).find(r => r.status !== RunStatus.POSTED) ?? null);
  protected readonly trend = computed(() => {
    const list = [...(this.runs() ?? [])].reverse().slice(-6);
    const max = Math.max(1, ...list.map(r => r.totalNet));
    return list.map(r => ({ run: r, pct: (r.totalNet / max) * 100 }));
  });
  protected readonly stages = [RunStatus.DRAFT, RunStatus.CALCULATED, RunStatus.APPROVED, RunStatus.POSTED];

  // ---- Get started: shown to workspace admins during the free trial, until done or dismissed
  private readonly company = inject(CompanyService);
  private readonly meBilling = inject(MeBillingService);
  private readonly canConfigure = this.account.hasPermission(Permission.CONFIGURE_TENANT_SETTINGS);
  protected readonly setupDismissed = signal(readDismissed());

  /** Each step is done when the workspace data says so; nothing is tracked separately. */
  protected readonly setupSteps = computed(() => {
    const profile = this.company.profile();
    const sub = this.meBilling.current();
    const steps = [
      { id: 'company', icon: 'building-2', link: '/settings/company', done: !!profile && (!!profile.logo || !!profile.defaultCurrencyId) },
      { id: 'people', icon: 'user-plus', link: '/employees/new', done: (this.stats()?.total ?? 0) > 0 },
      ...(this.canPayroll ? [{ id: 'payroll', icon: 'wallet', link: '/payroll/runs', done: (this.runs()?.length ?? 0) > 0 }] : []),
      {
        id: 'plan',
        icon: 'credit-card',
        link: '/settings/billing',
        done: !!sub && (sub.status !== SubscriptionStatus.TRIAL || !!sub.autoRenew),
      },
    ];
    return steps;
  });
  protected readonly setupDone = computed(() => this.setupSteps().filter(s => s.done).length);
  protected readonly showSetup = computed(
    () =>
      this.canConfigure &&
      !this.setupDismissed() &&
      this.meBilling.current()?.status === SubscriptionStatus.TRIAL &&
      this.setupDone() < this.setupSteps().length,
  );

  protected dismissSetup(): void {
    this.setupDismissed.set(true);
    try {
      localStorage.setItem(SETUP_DISMISSED_KEY, '1');
    } catch {
      // Private mode / blocked storage: hidden for this visit only.
    }
  }

  constructor() {
    this.currentEmployee.resolve();
    this.employeeService.stats().subscribe({ next: s => this.stats.set(s), error: () => this.stats.set(null) });
    this.employeeService.query({ page: 0, size: 6, sort: ['startDate,desc'] }).subscribe({
      next: page => this.joiners.set(page.content),
      error: () => this.joiners.set([]),
    });
    const in14 = new Date(this.today.getFullYear(), this.today.getMonth(), this.today.getDate() + 14);
    this.leaveService.calendar(iso(this.today), iso(in14)).subscribe({
      next: e => this.upcomingLeave.set(e),
      error: () => this.upcomingLeave.set([]),
    });
    if (this.canLeave) {
      this.leaveService.search({ status: LeaveStatus.PENDING }, { page: 0, size: 1 }).subscribe({
        next: p => this.pendingLeave.set(p.totalElements),
        error: () => this.pendingLeave.set(null),
      });
    }
    this.meWork.teamAttendance().subscribe({ next: a => this.attendance.set(a), error: () => this.attendance.set(null) });
    if (this.canPayroll) {
      this.runService.list({ page: 0, size: 12 }).subscribe({ next: p => this.runs.set(p.content), error: () => this.runs.set([]) });
    }
    effect(() => {
      const me = this.currentEmployee.currentEmployeeId();
      if (me) {
        untracked(() =>
          this.approvals
            .pendingForApprover(me)
            .subscribe({ next: list => this.waitingOnMe.set(list.length), error: () => this.waitingOnMe.set(null) }),
        );
      }
    });
  }

  protected stageIndex(status: RunStatus): number {
    return this.stages.indexOf(status);
  }

  protected tenure(startDate: string | null): string {
    if (!startDate) return '';
    const days = Math.round((Date.now() - new Date(`${startDate}T00:00:00`).getTime()) / 86_400_000);
    if (days < 0) return this.t('humano.dashboard.startsIn', { days: -days });
    if (days === 0) return this.t('humano.dashboard.startsToday');
    return days < 60
      ? this.t('humano.dashboard.joinedDays', { days })
      : this.t('humano.dashboard.joinedMonths', { months: Math.round(days / 30) });
  }

  private t(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
