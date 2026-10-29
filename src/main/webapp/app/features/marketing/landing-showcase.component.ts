import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';

import { AvatarComponent, BadgeComponent } from 'app/shared/ui';

type Tab = 'payroll' | 'attendance' | 'approvals' | 'insights';
type Tone = 'success' | 'warning' | 'brand' | 'danger' | 'info' | 'neutral';

const money = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 });
/** Day scale of the attendance tracks, in minutes since midnight (07:00–19:00). */
const DAY_FROM = 7 * 60;
const DAY_TO = 19 * 60;
const pos = (min: number): number => ((min - DAY_FROM) / (DAY_TO - DAY_FROM)) * 100;
const hm = (h: number, m = 0): number => h * 60 + m;

/**
 * "See Humano at work" on the landing page: the real screens' building blocks (KPI tiles, pay by
 * department, the results table, attendance tracks, the approvals queue, trend and mix charts)
 * rendered with the app's own classes and illustrative data, in a browser frame with one tab per
 * screen. Nothing here calls the server.
 */
@Component({
  selector: 'hum-landing-showcase',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LucideAngularModule, AvatarComponent, BadgeComponent],
  templateUrl: './landing-showcase.component.html',
})
export class LandingShowcaseComponent {
  protected readonly tab = signal<Tab>('payroll');

  protected readonly tabs: { id: Tab; label: string; icon: string; path: string }[] = [
    { id: 'payroll', label: 'Payroll run', icon: 'wallet', path: 'payroll/runs/march-2026' },
    { id: 'attendance', label: 'Team attendance', icon: 'timer', path: 'timesheets' },
    { id: 'approvals', label: 'Approvals', icon: 'check-check', path: 'approvals' },
    { id: 'insights', label: 'Insights', icon: 'chart-line', path: 'dashboard' },
  ];

  // ---- Payroll run
  protected readonly run = {
    net: 486204,
    netChange: 2.4,
    gross: 641820,
    grossChange: 1.9,
    deductions: 155616,
    cost: 742390,
    employees: 312,
  };
  protected readonly departments = [
    { name: 'Engineering', count: 96, avg: 7420, gross: 212400, net: 158100 },
    { name: 'Sales', count: 64, avg: 5980, gross: 141300, net: 108200 },
    { name: 'Operations', count: 72, avg: 4210, gross: 118600, net: 92400 },
    { name: 'Customer success', count: 48, avg: 3920, gross: 96700, net: 76100 },
    { name: 'People & finance', count: 32, avg: 4880, gross: 72820, net: 51404 },
  ];
  protected readonly maxDeptGross = Math.max(...this.departments.map(d => d.gross));
  protected readonly results = [
    { name: 'Ava Romano', ref: 'PS-2026-03-0142', gross: 7850, deductions: 2280 },
    { name: 'Diego Santos', ref: 'PS-2026-03-0143', gross: 6120, deductions: 1720 },
    { name: 'Mei Chen', ref: 'PS-2026-03-0144', gross: 8400, deductions: 2510 },
    { name: 'Omar Haddad', ref: 'PS-2026-03-0145', gross: 4980, deductions: 1310 },
    { name: 'Sofia Marin', ref: 'PS-2026-03-0146', gross: 5640, deductions: 1540 },
  ];
  protected readonly maxGross = Math.max(...this.results.map(r => r.gross));

  // ---- Team attendance
  protected readonly presence: { label: string; count: number; tone: Tone }[] = [
    { label: 'Working', count: 41, tone: 'success' },
    { label: 'On break', count: 6, tone: 'warning' },
    { label: 'On leave', count: 4, tone: 'info' },
    { label: 'Not in yet', count: 3, tone: 'neutral' },
    { label: 'Missing clock-out', count: 1, tone: 'danger' },
  ];
  protected readonly scaleHours = [8, 10, 12, 14, 16, 18].map(h => ({ h, left: pos(hm(h)) }));
  protected readonly team = [
    {
      name: 'Ava Romano',
      role: 'Payroll lead · People & finance',
      status: 'Working',
      tone: 'success' as Tone,
      inOut: '08:52 – now',
      worked: '5h 18m',
      breakShort: '32m break',
      segs: [
        { from: hm(8, 52), to: hm(12, 30), brk: false, open: false },
        { from: hm(12, 30), to: hm(13, 2), brk: true, open: false },
        { from: hm(13, 2), to: hm(14, 42), brk: false, open: true },
      ],
    },
    {
      name: 'Diego Santos',
      role: 'Support engineer · Customer success',
      status: 'On break',
      tone: 'warning' as Tone,
      inOut: '09:05 – now',
      worked: '4h 40m',
      breakShort: null,
      segs: [
        { from: hm(9, 5), to: hm(14, 10), brk: false, open: false },
        { from: hm(14, 10), to: hm(14, 42), brk: true, open: true },
      ],
    },
    {
      name: 'Mei Chen',
      role: 'Account executive · Sales',
      status: 'On leave',
      tone: 'info' as Tone,
      inOut: '—',
      worked: '',
      breakShort: null,
      segs: [],
    },
    {
      name: 'Omar Haddad',
      role: 'Warehouse lead · Operations',
      status: 'Done for today',
      tone: 'brand' as Tone,
      inOut: '06:58 – 13:04',
      worked: '5h 36m',
      breakShort: '30m break',
      segs: [
        { from: hm(7), to: hm(10, 15), brk: false, open: false },
        { from: hm(10, 15), to: hm(10, 45), brk: true, open: false },
        { from: hm(10, 45), to: hm(13, 4), brk: false, open: false },
      ],
    },
    {
      name: 'Sofia Marin',
      role: 'Recruiter · People & finance',
      status: 'Missing clock-out',
      tone: 'danger' as Tone,
      inOut: 'Yesterday 08:40 – ?',
      worked: '',
      breakShort: null,
      segs: [],
    },
  ].map(p => ({ ...p, segs: p.segs.map(s => ({ ...s, left: pos(s.from), width: pos(s.to) - pos(s.from) })) }));

  // ---- Approvals
  protected readonly approvalStats = [
    { label: 'Pending', value: '12', sub: '', warn: false },
    { label: 'Overdue', value: '2', sub: 'Waiting more than 3 days', warn: true },
    { label: 'Oldest', value: '5 d', sub: '', warn: false },
    { label: 'Leave days asked', value: '23', sub: 'Across pending requests', warn: false },
  ];
  protected readonly approvals = [
    {
      icon: 'palmtree',
      color: 'var(--brand)',
      name: 'Mei Chen',
      type: 'Time off',
      reason: 'Family trip, Jun 12–16',
      figure: '5 d',
      wait: 'Waiting 1 d',
      overdue: false,
    },
    {
      icon: 'receipt',
      color: 'var(--success-solid)',
      name: 'Diego Santos',
      type: 'Expense claim',
      reason: 'Client visit — train and hotel',
      figure: money.format(642),
      wait: 'Overdue',
      overdue: true,
    },
    {
      icon: 'clock',
      color: 'var(--warning-solid)',
      name: 'Omar Haddad',
      type: 'Overtime',
      reason: 'Inventory count, Saturday',
      figure: '6 h',
      wait: 'Waiting 2 d',
      overdue: false,
    },
    {
      icon: 'trending-up',
      color: 'var(--info-solid)',
      name: 'Ava Romano',
      type: 'Salary adjustment',
      reason: 'Annual review — new grade P4',
      figure: '+6%',
      wait: 'Overdue',
      overdue: true,
    },
    {
      icon: 'graduation-cap',
      color: 'var(--violet-500)',
      name: 'Sofia Marin',
      type: 'Training',
      reason: 'Employment law refresher',
      figure: money.format(480),
      wait: 'Waiting today',
      overdue: false,
    },
  ];

  // ---- Insights
  protected readonly netTrend = [
    { label: 'Oct', value: 451200 },
    { label: 'Nov', value: 458900 },
    { label: 'Dec', value: 497300 },
    { label: 'Jan', value: 462100 },
    { label: 'Feb', value: 474800 },
    { label: 'Mar', value: 486204 },
  ];
  private readonly maxTrend = Math.max(...this.netTrend.map(t => t.value));
  private readonly minTrend = Math.min(...this.netTrend.map(t => t.value));
  protected readonly headcount = [
    { label: 'Engineering', count: 96, color: 'var(--brand)' },
    { label: 'Operations', count: 72, color: 'var(--info-solid)' },
    { label: 'Sales', count: 64, color: 'var(--success-solid)' },
    { label: 'Customer success', count: 48, color: 'var(--warning-solid)' },
    { label: 'People & finance', count: 32, color: 'var(--violet-500)' },
  ];
  protected readonly locations = [
    { label: 'Onsite', count: 168, color: 'var(--brand)' },
    { label: 'Hybrid', count: 97, color: 'var(--violet-500)' },
    { label: 'Remote', count: 47, color: 'var(--info-solid)' },
  ];
  protected readonly total = 312;
  protected readonly money = (n: number): string => money.format(n);

  protected select(id: Tab): void {
    this.tab.set(id);
  }

  /** Arrow keys move between tabs, as in any tab list. */
  protected onKey(event: KeyboardEvent): void {
    const i = this.tabs.findIndex(t => t.id === this.tab());
    const step = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0;
    if (!step) return;
    event.preventDefault();
    const next = this.tabs[(i + step + this.tabs.length) % this.tabs.length];
    this.tab.set(next.id);
    (event.currentTarget as HTMLElement).querySelector<HTMLElement>(`[data-tab="${next.id}"]`)?.focus();
  }

  protected path(): string {
    return this.tabs.find(t => t.id === this.tab())!.path;
  }
  /** Bars start from a baseline so month-to-month differences are visible (25%–80% of the chart). */
  protected readonly barHeight = (v: number): number => 25 + ((v - this.minTrend) / (this.maxTrend - this.minTrend)) * 55;
  protected readonly pct = (n: number): number => (n / this.total) * 100;
}
