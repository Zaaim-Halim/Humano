import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { forkJoin } from 'rxjs';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  AvatarComponent,
  BadgeComponent,
  ButtonComponent,
  DrawerComponent,
  EmptyStateComponent,
  IconButtonComponent,
  InputComponent,
  PageHeaderComponent,
  SelectComponent,
  SkeletonComponent,
  SkeletonRowComponent,
  TabItem,
  TabsComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';
import { PluralPipe } from 'app/shared/util/format.pipes';

import { LeaveStatus } from '../models/enums/leave-status.enum';
import { LeaveType } from '../models/enums/leave-type.enum';
import { LeaveRequest } from '../models/leave-request.model';
import { CurrentEmployeeService } from '../services/current-employee.service';
import { LeaveRequestService } from '../services/leave-request.service';

type Tab = 'mine' | 'calendar' | 'requests';

/** Calendar bar colour per leave type. */
const TYPE_COLOR: Record<string, string> = {
  VACATION: 'var(--brand)',
  SICK: 'var(--danger-solid)',
  MATERNITY: 'var(--violet-500)',
  UNPAID: 'var(--slate-400)',
};

function iso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function parseIso(value: string): Date {
  return new Date(`${value}T00:00:00`);
}

/**
 * Leave — one place for time off.
 *
 * - My leave (anyone with an employee record): this year's taken / pending / upcoming days and
 *   the person's requests as a timeline; "Request leave" opens the request form.
 * - Team calendar (`VIEW_LEAVE_REQUESTS`): a month grid with one row per person on leave and a
 *   bar per request, coloured by type (pending ones hatched); today is marked.
 * - Requests (`VIEW_LEAVE_REQUESTS`): status chips, type filter and name search; a request opens
 *   a panel where approvers (`APPROVE_LEAVE` / `MANAGE_LEAVE_REQUESTS`) approve or reject it.
 *   Deciding here also clears the request from the approver's workflow inbox (server-side).
 */
@Component({
  selector: 'hum-leave',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    RouterLink,
    DatePipe,
    TranslatePipe,
    PluralPipe,
    LucideAngularModule,
    PageHeaderComponent,
    TabsComponent,
    ButtonComponent,
    IconButtonComponent,
    InputComponent,
    SelectComponent,
    TextareaComponent,
    AvatarComponent,
    BadgeComponent,
    DrawerComponent,
    AlertComponent,
    EmptyStateComponent,
    SkeletonComponent,
    SkeletonRowComponent,
  ],
  templateUrl: './leave.component.html',
})
export default class LeaveComponent {
  private readonly leaveService = inject(LeaveRequestService);
  private readonly currentEmployee = inject(CurrentEmployeeService);
  private readonly account = inject(AccountService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  protected readonly canViewAll = this.account.hasPermission(Permission.VIEW_LEAVE_REQUESTS);
  protected readonly canDecide =
    this.account.hasPermission(Permission.APPROVE_LEAVE) || this.account.hasPermission(Permission.MANAGE_LEAVE_REQUESTS);
  protected readonly me = this.currentEmployee.currentEmployeeId;

  protected readonly tab = signal<Tab>(this.canViewAll ? 'calendar' : 'mine');
  protected readonly tabs = computed<TabItem[]>(() => [
    ...(this.me() ? [{ id: 'mine', label: this.label('humano.leave.tabMine'), icon: 'user' }] : []),
    ...(this.canViewAll
      ? [
          { id: 'calendar', label: this.label('humano.leave.tabCalendar'), icon: 'calendar' },
          { id: 'requests', label: this.label('humano.leave.tabRequests'), icon: 'inbox', count: this.overview()?.pending ?? undefined },
        ]
      : []),
  ]);

  protected readonly typeColor = (type: string): string => TYPE_COLOR[type] ?? 'var(--info-solid)';
  protected readonly types = Object.values(LeaveType);
  protected readonly today = iso(new Date());

  // ---------------------------------------------------------------- overview (HR)
  protected readonly overview = signal<{ pending: number; onLeaveToday: number; upcoming: number } | null>(null);

  // ---------------------------------------------------------------- my leave
  protected readonly mine = signal<LeaveRequest[] | null>(null);
  protected readonly mineError = signal<string | null>(null);
  protected readonly mineStats = computed(() => {
    const list = this.mine() ?? [];
    const year = new Date().getFullYear();
    const thisYear = list.filter(r => parseIso(r.startDate).getFullYear() === year);
    const days = (rs: LeaveRequest[]): number => rs.reduce((sum, r) => sum + (r.daysCount ?? 0), 0);
    return {
      taken: days(thisYear.filter(r => r.status === LeaveStatus.APPROVED && r.startDate <= this.today)),
      upcoming: days(thisYear.filter(r => r.status === LeaveStatus.APPROVED && r.startDate > this.today)),
      pending: list.filter(r => r.status === LeaveStatus.PENDING).length,
    };
  });

  // ---------------------------------------------------------------- calendar (HR)
  protected readonly month = signal(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  protected readonly monthRequests = signal<LeaveRequest[] | null>(null);
  protected readonly calendarError = signal<string | null>(null);
  protected readonly days = computed(() => {
    const m = this.month();
    const count = new Date(m.getFullYear(), m.getMonth() + 1, 0).getDate();
    return Array.from({ length: count }, (_, i) => {
      const d = new Date(m.getFullYear(), m.getMonth(), i + 1);
      return {
        day: i + 1,
        iso: iso(d),
        weekend: d.getDay() === 0 || d.getDay() === 6,
        weekday: d.toLocaleDateString(undefined, { weekday: 'narrow' }),
      };
    });
  });
  /** One row per person with leave this month; bars clipped to the month. */
  protected readonly calendarRows = computed(() => {
    const list = (this.monthRequests() ?? []).filter(r => r.status !== LeaveStatus.REJECTED && r.status !== LeaveStatus.CANCELED);
    const days = this.days();
    const first = days[0].iso;
    const last = days[days.length - 1].iso;
    const rows = new Map<string, { id: string; name: string; bars: { request: LeaveRequest; start: number; span: number }[] }>();
    for (const r of list) {
      const startIso = r.startDate < first ? first : r.startDate;
      const endIso = r.endDate > last ? last : r.endDate;
      const start = parseIso(startIso).getDate();
      const span = parseIso(endIso).getDate() - start + 1;
      const row = rows.get(r.employeeId) ?? { id: r.employeeId, name: r.employeeName ?? '—', bars: [] };
      row.bars.push({ request: r, start, span });
      rows.set(r.employeeId, row);
    }
    return [...rows.values()].sort((a, b) => a.name.localeCompare(b.name));
  });
  protected readonly monthLabel = computed(() => this.month().toLocaleDateString(undefined, { month: 'long', year: 'numeric' }));
  protected readonly todayInMonth = computed(() => this.days().findIndex(d => d.iso === this.today) + 1);

  // ---------------------------------------------------------------- requests (HR)
  protected readonly statusFilter = signal<string>(LeaveStatus.PENDING);
  protected readonly typeFilter = signal('');
  protected readonly nameFilter = signal('');
  protected readonly requests = signal<LeaveRequest[] | null>(null);
  protected readonly requestsTotal = signal(0);
  protected readonly requestsError = signal<string | null>(null);
  protected readonly visibleRequests = computed(() => {
    const q = this.nameFilter().trim().toLowerCase();
    return (this.requests() ?? []).filter(r => !q || (r.employeeName ?? '').toLowerCase().includes(q));
  });
  protected readonly statusChips = [LeaveStatus.PENDING, LeaveStatus.APPROVED, LeaveStatus.REJECTED, ''];
  protected readonly typeOptions = computed(() => [
    { value: '', label: this.label('humano.leave.allTypes') },
    ...this.types.map(t => ({ value: t as string, label: this.label(`humano.leave.type.${t}`) })),
  ]);

  // ---------------------------------------------------------------- detail panel
  protected readonly selected = signal<LeaveRequest | null>(null);
  protected readonly comment = signal('');
  protected readonly deciding = signal(false);

  constructor() {
    this.currentEmployee.resolve();
    if (this.canViewAll) {
      this.loadOverview();
    }
    // My leave follows the resolved employee id.
    effect(() => {
      const id = this.me();
      if (id) untracked(() => this.loadMine(id));
    });
    // Calendar follows the month; requests follow the filters.
    effect(() => {
      this.month();
      if (this.canViewAll) untracked(() => this.loadMonth());
    });
    effect(() => {
      this.statusFilter();
      this.typeFilter();
      if (this.canViewAll) untracked(() => this.loadRequests());
    });
  }

  protected setTab(id: string | undefined): void {
    if (id) this.tab.set(id as Tab);
  }

  protected shiftMonth(delta: number): void {
    const m = this.month();
    this.month.set(new Date(m.getFullYear(), m.getMonth() + delta, 1));
  }

  protected thisMonth(): void {
    this.month.set(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  }

  protected newRequest(): void {
    void this.router.navigate(['/leave/new']);
  }

  protected open(request: LeaveRequest): void {
    this.selected.set(request);
    this.comment.set('');
  }

  protected decide(status: LeaveStatus.APPROVED | LeaveStatus.REJECTED): void {
    const request = this.selected();
    const approverId = this.me();
    if (!request || !approverId) return;
    this.deciding.set(true);
    this.leaveService
      .process(request.id, { status, approverId, ...(this.comment() ? { approverComments: this.comment() } : {}) })
      .subscribe({
        next: () => {
          this.toast.success(this.label(status === LeaveStatus.APPROVED ? 'humano.leave.approved' : 'humano.leave.rejected'));
          this.deciding.set(false);
          this.selected.set(null);
          this.loadOverview();
          this.loadRequests();
          this.loadMonth();
        },
        error: (err: unknown) => {
          this.toast.danger(normalizeHttpError(err));
          this.deciding.set(false);
        },
      });
  }

  protected loadMine(id = this.me()): void {
    if (!id) return;
    this.mineError.set(null);
    this.leaveService.searchByEmployee(id, {}, { page: 0, size: 100, sort: ['startDate,desc'] }).subscribe({
      next: page => this.mine.set(page.content),
      error: (err: unknown) => this.mineError.set(normalizeHttpError(err)),
    });
  }

  protected loadMonth(): void {
    const first = this.days()[0].iso;
    const last = this.days()[this.days().length - 1].iso;
    this.calendarError.set(null);
    this.leaveService.search({ startDateTo: last, endDateFrom: first }, { page: 0, size: 500, sort: ['startDate,asc'] }).subscribe({
      next: page => this.monthRequests.set(page.content),
      error: (err: unknown) => this.calendarError.set(normalizeHttpError(err)),
    });
  }

  protected loadRequests(): void {
    this.requestsError.set(null);
    const status = this.statusFilter();
    const type = this.typeFilter();
    this.leaveService
      .search(
        { ...(status ? { status: status as LeaveStatus } : {}), ...(type ? { leaveType: type as LeaveType } : {}) },
        { page: 0, size: 200, sort: [status === LeaveStatus.PENDING ? 'startDate,asc' : 'startDate,desc'] },
      )
      .subscribe({
        next: page => {
          this.requests.set(page.content);
          this.requestsTotal.set(page.totalElements);
        },
        error: (err: unknown) => this.requestsError.set(normalizeHttpError(err)),
      });
  }

  private loadOverview(): void {
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const inTwoWeeks = new Date();
    inTwoWeeks.setDate(inTwoWeeks.getDate() + 14);
    const one = { page: 0, size: 1 };
    forkJoin({
      pending: this.leaveService.search({ status: LeaveStatus.PENDING }, one),
      today: this.leaveService.search({ status: LeaveStatus.APPROVED, startDateTo: this.today, endDateFrom: this.today }, one),
      upcoming: this.leaveService.search({ status: LeaveStatus.APPROVED, startDateFrom: iso(tomorrow), startDateTo: iso(inTwoWeeks) }, one),
    }).subscribe({
      next: r =>
        this.overview.set({ pending: r.pending.totalElements, onLeaveToday: r.today.totalElements, upcoming: r.upcoming.totalElements }),
    });
  }

  private label(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
