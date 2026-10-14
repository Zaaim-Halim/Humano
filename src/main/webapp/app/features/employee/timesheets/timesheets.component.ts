import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  AvatarComponent,
  ButtonComponent,
  EmptyStateComponent,
  FormFieldComponent,
  IconButtonComponent,
  InputComponent,
  PageHeaderComponent,
  SelectComponent,
  SkeletonComponent,
  TabItem,
  TabsComponent,
  ToastService,
} from 'app/shared/ui';

import { CreateTimesheetRequest, Timesheet } from '../models/timesheet.model';
import { Project } from '../models/project.model';
import { CompanyService } from 'app/core/company/company.service';

import { CurrentEmployeeService } from '../services/current-employee.service';
import { ProjectService } from '../services/project.service';
import { TimesheetService } from '../services/timesheet.service';
import { MeWorkService } from '../services/me-work.service';
import { TeamAttendanceComponent } from './team-attendance.component';
import { TimeClockComponent } from './time-clock.component';

/** Colours cycled across projects (stable by project order). */
const PROJECT_COLORS = [
  'var(--brand)',
  'var(--info-solid)',
  'var(--success-solid)',
  'var(--warning-solid)',
  'var(--violet-500)',
  'var(--danger-solid)',
];

function iso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** Monday of the week containing `d`. */
function weekStart(d: Date): Date {
  const day = (d.getDay() + 6) % 7;
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() - day);
}

/**
 * Timesheets — clock in/out and log project hours, by week.
 *
 * - Time clock (anyone with an employee record): clock in/out and breaks, stamped by the server
 *   (see TimeClockComponent).
 * - Project hours: Monday–Sunday bars against an 8h day, the week's
 *   total against 40h, a per-project split, each day's entries, and a quick log form (date,
 *   hours, project).
 * - Team attendance (managers and HR): who is in, on a break, done, on leave, by day
 *   (see TeamAttendanceComponent).
 * - Team week (`VIEW_TIMESHEETS`): a heatmap with one row per person and a cell per day shaded
 *   by hours, with weekly totals; under-/over-logged weeks are flagged.
 * Both views page by week; data is one search per week.
 */
@Component({
  selector: 'hum-timesheets',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TimeClockComponent,
    TeamAttendanceComponent,
    ReactiveFormsModule,
    RouterLink,
    DatePipe,
    DecimalPipe,
    TranslatePipe,
    LucideAngularModule,
    PageHeaderComponent,
    TabsComponent,
    ButtonComponent,
    IconButtonComponent,
    InputComponent,
    SelectComponent,
    FormFieldComponent,
    AvatarComponent,
    AlertComponent,
    EmptyStateComponent,
    SkeletonComponent,
  ],
  templateUrl: './timesheets.component.html',
})
export default class TimesheetsComponent {
  private readonly fb = inject(FormBuilder);
  private readonly timesheetService = inject(TimesheetService);
  private readonly projectService = inject(ProjectService);
  private readonly currentEmployee = inject(CurrentEmployeeService);
  private readonly account = inject(AccountService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly meWork = inject(MeWorkService);

  protected readonly employeeId = this.currentEmployee.currentEmployeeId;
  protected readonly resolved = this.currentEmployee.resolved;
  protected readonly canViewTeam = this.account.hasPermission(Permission.VIEW_TIMESHEETS);
  // Targets follow the company's working-time policy (Settings → Payroll & time).
  private readonly company = inject(CompanyService);
  protected readonly dailyTarget = this.company.hoursPerDay;
  protected readonly weeklyTarget = this.company.hoursPerWeek;

  protected readonly tab = signal<'clock' | 'mine' | 'team' | 'attendance'>('clock');
  /** Team attendance is shown to managers (anyone with reports) and HR; probed on load. */
  protected readonly attendanceScope = signal<'ALL' | 'REPORTS' | 'NONE'>('NONE');
  protected readonly tabs = computed<TabItem[]>(() => [
    { id: 'clock', label: this.label('humano.timeclock.tab'), icon: 'timer' },
    { id: 'mine', label: this.label('humano.timesheets.tabMine'), icon: 'folder-kanban' },
    ...(this.attendanceScope() !== 'NONE'
      ? [{ id: 'attendance', label: this.label('humano.teamAttendance.tab'), icon: 'user-check' }]
      : []),
    ...(this.canViewTeam ? [{ id: 'team', label: this.label('humano.timesheets.tabTeam'), icon: 'users' }] : []),
  ]);

  /** Monday of the displayed week. */
  protected readonly week = signal(weekStart(new Date()));
  protected readonly days = computed(() =>
    Array.from({ length: 7 }, (_, i) => {
      const d = new Date(this.week().getFullYear(), this.week().getMonth(), this.week().getDate() + i);
      return { iso: iso(d), date: d, weekend: i >= 5 };
    }),
  );
  protected readonly weekLabel = computed(() => {
    const days = this.days();
    const fmt = (d: Date, withYear: boolean): string =>
      d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', ...(withYear ? { year: 'numeric' } : {}) });
    return `${fmt(days[0].date, false)} – ${fmt(days[6].date, true)}`;
  });
  protected readonly isCurrentWeek = computed(() => iso(this.week()) === iso(weekStart(new Date())));
  protected readonly today = iso(new Date());

  protected readonly projects = signal<Project[]>([]);
  protected readonly projectOptions = computed(() => [
    { value: '', label: this.label('humano.timesheets.noProject') },
    ...this.projects().map(p => ({ value: p.id, label: p.name })),
  ]);
  private readonly projectColor = computed(() => new Map(this.projects().map((p, i) => [p.id, PROJECT_COLORS[i % PROJECT_COLORS.length]])));

  // ---------------------------------------------------------------- my week
  protected readonly mine = signal<Timesheet[] | null>(null);
  protected readonly mineError = signal<string | null>(null);
  protected readonly myDays = computed(() => {
    const entries = this.mine() ?? [];
    return this.days().map(d => {
      const list = entries.filter(e => e.date === d.iso);
      return { ...d, entries: list, hours: list.reduce((sum, e) => sum + Number(e.hoursWorked), 0) };
    });
  });
  protected readonly myTotal = computed(() => this.myDays().reduce((sum, d) => sum + d.hours, 0));
  /** Bar scale: at least a 10h day so the target line sits below the top. */
  protected readonly myScale = computed(() => Math.max(this.dailyTarget() + 2, ...this.myDays().map(d => d.hours)));
  protected readonly myProjects = computed(() => {
    const totals = new Map<string, { name: string; hours: number; color: string }>();
    for (const e of this.mine() ?? []) {
      const key = e.projectId ?? '';
      const t = totals.get(key) ?? {
        name: e.projectName ?? this.label('humano.timesheets.noProject'),
        hours: 0,
        color: (e.projectId && this.projectColor().get(e.projectId)) ?? 'var(--slate-400)',
      };
      t.hours += Number(e.hoursWorked);
      totals.set(key, t);
    }
    const total = this.myTotal();
    return [...totals.values()].sort((a, b) => b.hours - a.hours).map(t => ({ ...t, pct: total ? (t.hours / total) * 100 : 0 }));
  });

  protected readonly form = this.fb.nonNullable.group({
    date: [this.today, Validators.required],
    hoursWorked: ['', [Validators.required, Validators.min(0.25), Validators.max(24)]],
    projectId: [''],
  });
  protected readonly submitting = signal(false);

  /** Hours clocked per day this week (from the time clock), to prefill project hours. */
  protected readonly clocked = signal<Record<string, number>>({});
  private readonly formDate = toSignal(this.form.controls.date.valueChanges, { initialValue: this.form.controls.date.value });
  protected readonly clockedForForm = computed(() => this.clocked()[this.formDate()] || null);

  // ---------------------------------------------------------------- team week
  protected readonly team = signal<Timesheet[] | null>(null);
  protected readonly teamError = signal<string | null>(null);
  protected readonly teamRows = computed(() => {
    const rows = new Map<string, { id: string; name: string; hours: Record<string, number>; total: number }>();
    for (const e of this.team() ?? []) {
      const row = rows.get(e.employeeId) ?? { id: e.employeeId, name: e.employeeName ?? '—', hours: {}, total: 0 };
      row.hours[e.date] = (row.hours[e.date] ?? 0) + Number(e.hoursWorked);
      row.total += Number(e.hoursWorked);
      rows.set(e.employeeId, row);
    }
    return [...rows.values()].sort((a, b) => a.name.localeCompare(b.name));
  });
  protected readonly teamStats = computed(() => {
    const rows = this.teamRows();
    const total = rows.reduce((sum, r) => sum + r.total, 0);
    return {
      people: rows.length,
      total,
      average: rows.length ? total / rows.length : 0,
      under: rows.filter(r => r.total < this.weeklyTarget() * 0.9).length,
    };
  });

  constructor() {
    this.currentEmployee.resolve();
    this.meWork.teamAttendance().subscribe({ next: t => this.attendanceScope.set(t.scope), error: () => undefined });
    this.projectService.query({ page: 0, size: 200, sort: ['name,asc'] }).subscribe({ next: res => this.projects.set(res.content) });

    effect(() => {
      const id = this.employeeId();
      this.week();
      if (id) untracked(() => this.loadMine(id));
    });
    effect(() => {
      this.week();
      if (this.canViewTeam && this.tab() === 'team') untracked(() => this.loadTeam());
    });
  }

  protected setTab(id: string | undefined): void {
    if (id === 'clock' || id === 'mine' || id === 'team' || id === 'attendance') this.tab.set(id);
  }

  protected shiftWeek(delta: number): void {
    const w = this.week();
    this.week.set(new Date(w.getFullYear(), w.getMonth(), w.getDate() + delta * 7));
  }

  protected thisWeek(): void {
    this.week.set(weekStart(new Date()));
  }

  protected colorOf(projectId: string | null): string {
    return (projectId && this.projectColor().get(projectId)) ?? 'var(--slate-400)';
  }

  /** Heatmap shade: 0 → empty, the daily target → full brand. */
  protected heat(hours: number | undefined): number {
    return hours ? Math.min(1, hours / this.dailyTarget()) : 0;
  }

  protected logFor(day: string): void {
    this.form.patchValue({ date: day });
  }

  protected useClocked(hours: number): void {
    this.form.patchValue({ hoursWorked: String(hours) });
  }

  protected invalid(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
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
      ...(raw.projectId ? { projectId: raw.projectId } : {}),
    };
    this.submitting.set(true);
    this.timesheetService.create(payload).subscribe({
      next: () => {
        this.toast.success(this.label('humano.timesheets.logged'));
        this.submitting.set(false);
        this.form.patchValue({ hoursWorked: '' });
        this.form.controls.hoursWorked.markAsUntouched();
        // Jump to the week of the logged entry so it is visible.
        this.week.set(weekStart(new Date(`${raw.date}T00:00:00`)));
        this.loadMine(employeeId);
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.submitting.set(false);
      },
    });
  }

  protected loadMine(id = this.employeeId()): void {
    if (!id) return;
    const days = this.days();
    this.mineError.set(null);
    this.timesheetService
      .searchByEmployee(id, { dateFrom: days[0].iso, dateTo: days[6].iso }, { page: 0, size: 200, sort: ['date,asc'] })
      .subscribe({
        next: page => this.mine.set(page.content),
        error: (err: unknown) => this.mineError.set(normalizeHttpError(err)),
      });
  }

  protected loadTeam(): void {
    const days = this.days();
    this.teamError.set(null);
    this.team.set(null);
    this.timesheetService.search({ dateFrom: days[0].iso, dateTo: days[6].iso }, { page: 0, size: 2000, sort: ['date,asc'] }).subscribe({
      next: page => this.team.set(page.content),
      error: (err: unknown) => this.teamError.set(normalizeHttpError(err)),
    });
  }

  private label(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
