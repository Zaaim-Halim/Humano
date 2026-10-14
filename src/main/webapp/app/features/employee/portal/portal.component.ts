import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { AccountService } from 'app/core/auth/account.service';
import { Payslip } from 'app/features/admin/models/payslip.model';
import { ApprovalService } from 'app/features/manager';
import { AvatarComponent, PageHeaderComponent, SkeletonComponent } from 'app/shared/ui';
import { MoneyPipe } from 'app/shared/util/format.pipes';

import { LeaveStatus } from '../models/enums/leave-status.enum';
import { LEAVE_TYPE_COLOR } from '../models/enums/leave-type.enum';
import { LeaveRequest } from '../models/leave-request.model';
import { TeamAttendance, TimeClockDay } from '../models/time-clock.model';
import { CurrentEmployeeService } from '../services/current-employee.service';
import { LeaveRequestService } from '../services/leave-request.service';
import { MeWorkService } from '../services/me-work.service';
import { clockDuration, hm } from '../timesheets/clock-timeline';

interface QuickAction {
  icon: string;
  labelKey: string;
  link: string;
}

interface Todo {
  icon: string;
  tone: 'brand' | 'warning' | 'danger' | 'info';
  text: string;
  link: string;
}

function iso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/**
 * My space — every employee's personal home, all self-scoped (`/api/me/**` and own leave).
 *
 * - Today: the time-clock state (clock in right here), worked time, check-in.
 * - Pay: the latest payslip (opens the self-service payslip) and net paid this year.
 * - Time off: days taken this year, pending requests and the next approved absence.
 * - To do: what needs the person's attention — approvals waiting on them, not clocked in yet,
 *   requests still pending — each linking to where it is done.
 * - For managers: who in their team is in today.
 */
@Component({
  selector: 'hum-portal',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, TranslatePipe, RouterLink, LucideAngularModule, MoneyPipe, PageHeaderComponent, AvatarComponent, SkeletonComponent],
  templateUrl: './portal.component.html',
})
export default class PortalComponent {
  private readonly accountService = inject(AccountService);
  private readonly currentEmployee = inject(CurrentEmployeeService);
  private readonly meWork = inject(MeWorkService);
  private readonly leaveService = inject(LeaveRequestService);
  private readonly approvalService = inject(ApprovalService);
  private readonly translate = inject(TranslateService);

  protected readonly account = this.accountService.trackCurrentAccount();
  protected readonly firstName = computed(() => {
    const a = this.account();
    return a?.firstName?.trim() || a?.login || '';
  });
  protected readonly employeeId = this.currentEmployee.currentEmployeeId;
  protected readonly resolved = this.currentEmployee.resolved;
  protected readonly duration = clockDuration;
  protected readonly hm = hm;
  protected readonly today = new Date();

  protected readonly quickActions: QuickAction[] = [
    { icon: 'timer', labelKey: 'humano.portal.actionClock', link: '/timesheets' },
    { icon: 'palmtree', labelKey: 'humano.portal.actionLeave', link: '/leave/new' },
    { icon: 'id-card', labelKey: 'humano.portal.actionProfile', link: '/my-profile' },
    { icon: 'file-text', labelKey: 'humano.portal.actionDocuments', link: '/my-documents' },
    { icon: 'network', labelKey: 'humano.portal.actionTree', link: '/people-tree' },
  ];

  // Today
  protected readonly clock = signal<TimeClockDay | null>(null);
  protected readonly clockBusy = signal(false);

  // Pay
  protected readonly payslips = signal<Payslip[] | null>(null);
  protected readonly latestPayslip = computed(() => this.payslips()?.[0] ?? null);
  protected readonly ytdNet = computed(() => {
    const year = String(this.today.getFullYear());
    return (this.payslips() ?? []).filter(p => p.periodStart?.startsWith(year)).reduce((s, p) => s + Number(p.net), 0);
  });

  // Time off
  protected readonly leave = signal<LeaveRequest[] | null>(null);
  protected readonly leaveStats = computed(() => {
    const list = this.leave() ?? [];
    const todayIso = iso(this.today);
    const year = String(this.today.getFullYear());
    const approved = list.filter(r => r.status === LeaveStatus.APPROVED);
    return {
      taken: approved.filter(r => r.startDate.startsWith(year) && r.startDate <= todayIso).reduce((s, r) => s + (r.daysCount ?? 0), 0),
      pending: list.filter(r => r.status === LeaveStatus.PENDING),
      next: approved.filter(r => r.startDate > todayIso).sort((a, b) => a.startDate.localeCompare(b.startDate))[0] ?? null,
    };
  });
  protected readonly leaveColor = (t: string): string => LEAVE_TYPE_COLOR[t] ?? 'var(--info-solid)';

  // Approvals / team
  protected readonly waitingOnMe = signal<number>(0);
  protected readonly team = signal<TeamAttendance | null>(null);
  protected readonly teamIn = computed(() => (this.team()?.rows ?? []).filter(r => r.presence === 'WORKING' || r.presence === 'ON_BREAK'));

  protected readonly todos = computed<Todo[]>(() => {
    const list: Todo[] = [];
    if (this.waitingOnMe() > 0) {
      list.push({
        icon: 'inbox',
        tone: 'brand',
        text: this.t('humano.portal.todoApprovals', { count: this.waitingOnMe() }),
        link: '/approvals',
      });
    }
    const c = this.clock();
    const weekday = this.today.getDay() !== 0 && this.today.getDay() !== 6;
    if (c && c.state === 'NOT_STARTED' && weekday) {
      list.push({ icon: 'timer', tone: 'warning', text: this.t('humano.portal.todoClockIn'), link: '/timesheets' });
    }
    if (c?.state === 'ON_BREAK') {
      list.push({
        icon: 'coffee',
        tone: 'info',
        text: this.t('humano.portal.todoOnBreak', { time: hm(c.events[c.events.length - 1]?.time ?? null) }),
        link: '/timesheets',
      });
    }
    for (const r of this.leaveStats().pending.slice(0, 2)) {
      list.push({
        icon: 'hourglass',
        tone: 'info',
        text: this.t('humano.portal.todoLeavePending', {
          type: this.t(`humano.leave.type.${r.leaveType}`),
          date: new Date(`${r.startDate}T00:00:00`).toLocaleDateString(undefined, { day: 'numeric', month: 'short' }),
        }),
        link: '/leave',
      });
    }
    return list;
  });

  constructor() {
    this.currentEmployee.resolve();
    this.meWork.clockToday().subscribe({ next: d => this.clock.set(d), error: () => this.clock.set(null) });
    this.meWork.payslips({ page: 0, size: 24 }).subscribe({
      next: p => this.payslips.set([...p.content].sort((a, b) => (b.periodStart ?? '').localeCompare(a.periodStart ?? ''))),
      error: () => this.payslips.set([]),
    });
    this.meWork.teamAttendance().subscribe({ next: t => this.team.set(t), error: () => this.team.set(null) });
    effect(() => {
      const id = this.employeeId();
      if (!id) return;
      untracked(() => {
        this.leaveService.searchByEmployee(id, {}, { page: 0, size: 100, sort: ['startDate,desc'] }).subscribe({
          next: p => this.leave.set(p.content),
          error: () => this.leave.set([]),
        });
        this.approvalService.pendingForApprover(id).subscribe({ next: l => this.waitingOnMe.set(l.length), error: () => undefined });
      });
    });
  }

  protected clockIn(): void {
    this.clockBusy.set(true);
    this.meWork.clockIn({}).subscribe({
      next: d => {
        this.clock.set(d);
        this.clockBusy.set(false);
      },
      error: () => this.clockBusy.set(false),
    });
  }

  private t(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
