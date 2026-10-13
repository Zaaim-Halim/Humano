import { ChangeDetectionStrategy, Component, TemplateRef, computed, effect, inject, signal, viewChild } from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { debounceTime } from 'rxjs/operators';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { createListResource, normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  AvatarComponent,
  BadgeComponent,
  ButtonComponent,
  Column,
  DataTableComponent,
  DrawerComponent,
  EmptyStateComponent,
  IconButtonComponent,
  InputComponent,
  PageHeaderComponent,
  SelectComponent,
  SkeletonComponent,
  SkeletonRowComponent,
  SortState,
} from 'app/shared/ui';

import { DepartmentService } from 'app/features/admin';

import {
  EmployeeService,
  EmployeeStatus,
  WorkLocationType,
  personName,
  type EmployeeDirectoryStats,
  type EmployeeProfile,
  type EmployeeSearchRequest,
  type SimpleEmployeeProfile,
} from 'app/features/employee';

type ViewMode = 'grid' | 'table';

/** Palette for the department distribution bar (cycled). */
const SEGMENT_COLORS = [
  'var(--brand)',
  'var(--info-solid)',
  'var(--success-solid)',
  'var(--warning-solid)',
  'var(--violet-500)',
  'var(--danger-solid)',
  'var(--indigo-300)',
  'var(--slate-400)',
];

const SORTS = ['firstName,asc', 'startDate,desc', 'startDate,asc', 'jobTitle,asc'] as const;

/**
 * Employee Directory (HR/Admin) — the people list.
 *
 * - Header strip from `GET /employees/stats`: headcount and status split, department
 *   distribution (click a segment to filter), work-location mix and recent joiners.
 * - Search (name, title, email, employee number), department / location / status filters,
 *   sort and a card ↔ table view toggle. All state lives in the URL (shareable, back-button
 *   safe); the list is paged server-side (`GET` or `POST /search` when filtered).
 * - Selecting someone opens a profile panel (`GET /{id}`) with links to the full profile
 *   and their place in the people tree.
 */
@Component({
  selector: 'hum-directory',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    DatePipe,
    TranslatePipe,
    LucideAngularModule,
    PageHeaderComponent,
    ButtonComponent,
    IconButtonComponent,
    InputComponent,
    SelectComponent,
    DataTableComponent,
    AvatarComponent,
    BadgeComponent,
    DrawerComponent,
    AlertComponent,
    EmptyStateComponent,
    SkeletonComponent,
    SkeletonRowComponent,
  ],
  templateUrl: './directory.component.html',
})
export default class DirectoryComponent {
  private readonly fb = inject(FormBuilder);
  private readonly employeeService = inject(EmployeeService);
  private readonly departmentService = inject(DepartmentService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);
  private readonly account = inject(AccountService);

  /** Gate the "Add employee" CTA on the create permission. */
  protected readonly canCreateEmployee = this.account.hasPermission(Permission.CREATE_EMPLOYEE);

  protected readonly pageSize = 24;
  private readonly criteria = signal<EmployeeSearchRequest>({});
  private readonly queryParams = toSignal(this.route.queryParamMap, { requireSync: true });

  // Filters reflect the URL; the URL drives the query (single source of truth).
  protected readonly filters = this.fb.nonNullable.group({ q: '', dept: '', loc: '', sort: SORTS[0] as string });
  protected readonly departments = signal<{ value: string; label: string }[]>([]);

  protected readonly view = computed<ViewMode>(() => (this.queryParams().get('view') === 'table' ? 'table' : 'grid'));
  protected readonly status = computed(() => this.queryParams().get('status') ?? '');
  protected readonly page = computed(() => Number(this.queryParams().get('page') ?? 0));
  protected readonly sort = computed<SortState>(() => {
    const [key, dir] = (this.queryParams().get('sort') ?? SORTS[0]).split(',');
    return { key, dir: dir === 'desc' ? 'desc' : 'asc' };
  });
  protected readonly hasFilters = computed(() => ['q', 'dept', 'loc', 'status'].some(k => !!this.queryParams().get(k)));

  protected readonly list = createListResource<SimpleEmployeeProfile>(
    req => {
      const c = this.criteria();
      return Object.keys(c).length ? this.employeeService.search(c, req) : this.employeeService.query(req);
    },
    { autoLoad: false },
  );

  // Directory aggregates (header strip).
  protected readonly stats = signal<EmployeeDirectoryStats | null>(null);
  protected readonly statsError = signal(false);
  protected readonly statusChips = computed(() => {
    const s = this.stats();
    return [
      { value: '', label: this.translate.instant('humano.directory.all'), count: s?.total ?? null },
      ...Object.values(EmployeeStatus).map(v => ({
        value: v as string,
        label: this.translate.instant(`humano.directory.status.${v}`),
        count: s?.byStatus[v] ?? 0,
      })),
    ];
  });
  protected readonly departmentSegments = computed(() => {
    const s = this.stats();
    if (!s?.total) return [];
    return s.byDepartment.map((d, i) => ({
      id: d.id,
      name: d.name ?? this.translate.instant('humano.directory.noDepartment'),
      count: d.count,
      pct: (d.count / s.total) * 100,
      color: SEGMENT_COLORS[i % SEGMENT_COLORS.length],
    }));
  });
  protected readonly locationSegments = computed(() => {
    const s = this.stats();
    if (!s?.total) return [];
    const order = [WorkLocationType.ONSITE, WorkLocationType.HYBRID, WorkLocationType.REMOTE, 'UNSPECIFIED'];
    const colors = ['var(--info-solid)', 'var(--brand)', 'var(--success-solid)', 'var(--slate-300)'];
    return order
      .map((key, i) => ({ key, count: s.byWorkLocation[key] ?? 0, color: colors[i] }))
      .filter(x => x.count > 0)
      .map(x => ({ ...x, pct: (x.count / s.total) * 100, label: this.translate.instant(`humano.directory.location.${x.key}`) }));
  });

  // Detail drawer state.
  /** The list row that was opened — supplies name, email and avatar (the profile DTO has none). */
  protected readonly selected = signal<SimpleEmployeeProfile | null>(null);
  protected readonly detail = signal<EmployeeProfile | null>(null);
  protected readonly detailLoading = signal(false);
  protected readonly detailError = signal<string | null>(null);

  private readonly personCellTpl = viewChild<TemplateRef<{ $implicit: Record<string, unknown>; value: unknown }>>('personCell');
  private readonly statusCellTpl = viewChild<TemplateRef<{ $implicit: Record<string, unknown>; value: unknown }>>('statusCell');
  private readonly dateCellTpl = viewChild<TemplateRef<{ $implicit: Record<string, unknown>; value: unknown }>>('dateCell');
  private readonly locationCellTpl = viewChild<TemplateRef<{ $implicit: Record<string, unknown>; value: unknown }>>('locationCell');

  protected readonly name = personName;
  /** DataTable is loosely typed (`Row`); rows are cast for binding, cells use `$any`. */
  protected readonly rows = computed<Record<string, unknown>[]>(() => this.list.items().map(e => ({ ...e, name: personName(e) })));

  protected readonly columns = computed<Column[]>(() => [
    { key: 'firstName', label: this.translate.instant('humano.directory.colName'), sortable: true, cell: this.personCellTpl() },
    { key: 'jobTitle', label: this.translate.instant('humano.directory.colJobTitle'), sortable: true },
    { key: 'departmentName', label: this.translate.instant('humano.directory.colDepartment') },
    { key: 'managerName', label: this.translate.instant('humano.directory.colManager') },
    { key: 'workLocation', label: this.translate.instant('humano.directory.colLocation'), cell: this.locationCellTpl() },
    { key: 'startDate', label: this.translate.instant('humano.directory.colStartDate'), sortable: true, cell: this.dateCellTpl() },
    { key: 'status', label: this.translate.instant('humano.directory.colStatus'), cell: this.statusCellTpl() },
  ]);

  protected readonly departmentOptions = computed(() => [
    { value: '', label: this.translate.instant('humano.directory.allDepartments') },
    ...this.departments(),
  ]);
  protected readonly locationOptions = computed(() => [
    { value: '', label: this.translate.instant('humano.directory.allLocations') },
    ...Object.values(WorkLocationType).map(v => ({ value: v as string, label: this.translate.instant(`humano.directory.location.${v}`) })),
  ]);
  protected readonly sortOptions = computed(() =>
    SORTS.map(v => ({ value: v as string, label: this.translate.instant(`humano.directory.sort.${v.replace(',', '_')}`) })),
  );

  constructor() {
    this.departmentService.query({ size: 200, sort: ['name,asc'] }).subscribe({
      next: page => this.departments.set(page.content.map(d => ({ value: d.id, label: d.name }))),
    });
    this.loadStats();

    // URL → state: rebuild criteria + page/sort and (re)load whenever the query changes.
    effect(() => {
      const qp = this.queryParams();
      const q = qp.get('q') ?? '';
      const dept = qp.get('dept') ?? '';
      const loc = qp.get('loc') ?? '';
      const status = qp.get('status') ?? '';
      const sort = `${this.sort().key},${this.sort().dir}`;
      this.filters.patchValue({ q, dept, loc, sort }, { emitEvent: false });
      this.criteria.set({
        ...(q ? { query: q } : {}),
        ...(dept ? { departmentId: dept } : {}),
        ...(loc ? { workLocation: loc as WorkLocationType } : {}),
        ...(status ? { status: status as EmployeeStatus } : {}),
      });
      this.list.setParams({ page: this.page(), size: this.pageSize, sort: [sort, 'lastName,asc'] });
    });

    // Filter changes → URL (debounced so typing doesn't spam navigations); reset to page 0.
    this.filters.valueChanges.pipe(debounceTime(250), takeUntilDestroyed()).subscribe(v => {
      this.patchQuery({
        q: v.q || null,
        dept: v.dept || null,
        loc: v.loc || null,
        sort: !v.sort || v.sort === SORTS[0] ? null : v.sort,
        page: 0,
      });
    });
  }

  protected setStatus(status: string): void {
    this.patchQuery({ status: status || null, page: 0 });
  }

  protected setDepartment(id: string | null): void {
    this.patchQuery({ dept: id && this.queryParams().get('dept') !== id ? id : null, page: 0 });
  }

  protected setView(view: ViewMode): void {
    this.patchQuery({ view: view === 'grid' ? null : view });
  }

  protected onSort(s: SortState): void {
    this.patchQuery({ sort: `${s.key},${s.dir}`, page: 0 });
  }

  protected goto(page: number): void {
    this.patchQuery({ page });
  }

  protected clearFilters(): void {
    this.patchQuery({ q: null, dept: null, loc: null, status: null, page: 0 });
  }

  protected isLastPage(): boolean {
    return (this.page() + 1) * this.pageSize >= this.list.total();
  }

  protected rangeLabel(): string {
    const start = this.page() * this.pageSize + 1;
    const end = Math.min(start + this.list.items().length - 1, this.list.total());
    return this.translate.instant('humano.directory.range', { start, end, total: this.list.total() });
  }

  /** "2 yrs 3 mos" style tenure from an ISO start date. */
  protected tenure(start: string | null): string {
    if (!start) return '';
    const from = new Date(`${start}T00:00:00`);
    const now = new Date();
    let months = (now.getFullYear() - from.getFullYear()) * 12 + now.getMonth() - from.getMonth();
    if (now.getDate() < from.getDate()) months--;
    if (months < 1) return this.translate.instant('humano.directory.tenureNew');
    const years = Math.floor(months / 12);
    const rest = months % 12;
    return this.translate.instant(years ? 'humano.directory.tenureYears' : 'humano.directory.tenureMonths', { years, months: rest });
  }

  protected openDetail(employee: SimpleEmployeeProfile): void {
    this.selected.set(employee);
    this.loadDetail(employee.id);
  }

  protected reloadDetail(): void {
    const e = this.selected();
    if (e) this.loadDetail(e.id);
  }

  protected closeDetail(): void {
    this.selected.set(null);
    this.detail.set(null);
    this.detailError.set(null);
  }

  /** Open the create form, which provisions the account and HR profile in one step. */
  protected addEmployee(): void {
    void this.router.navigate(['/employees', 'new']);
  }

  private loadStats(): void {
    this.employeeService.stats().subscribe({
      next: s => this.stats.set(s),
      error: () => this.statsError.set(true),
    });
  }

  private loadDetail(id: string): void {
    this.detailLoading.set(true);
    this.detailError.set(null);
    this.employeeService.find(id).subscribe({
      next: profile => {
        this.detail.set(profile);
        this.detailLoading.set(false);
      },
      error: (err: unknown) => {
        this.detailError.set(normalizeHttpError(err));
        this.detailLoading.set(false);
      },
    });
  }

  private patchQuery(patch: Record<string, string | number | null>): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: patch, queryParamsHandling: 'merge' });
  }
}
