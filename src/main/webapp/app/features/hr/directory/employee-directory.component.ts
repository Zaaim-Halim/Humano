import { ChangeDetectionStrategy, Component, TemplateRef, computed, effect, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { debounceTime } from 'rxjs/operators';

import { DEFAULT_PAGE_SIZE, createListResource, normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  BadgeComponent,
  ButtonComponent,
  Column,
  DataTableComponent,
  DrawerComponent,
  EmptyStateComponent,
  InputComponent,
  PageHeaderComponent,
  SelectComponent,
  SkeletonRowComponent,
  SortState,
} from 'app/shared/ui';

import { DepartmentService } from '../department.service';
import { EmployeeStatus } from '../enums/employee-status.enum';
import { EmployeeProfile, EmployeeSearchRequest, SimpleEmployeeProfile } from '../employee.model';
import { EmployeeService } from '../employee.service';

/**
 * Employee Directory (HR/Admin hero screen) — paged `GET /api/hr/employees`
 * (or `POST /search` when filtered), row → detail Drawer (`GET /{id}`). Search,
 * department + status filters, sort and pagination are all route-aware (URL is
 * the single source of truth), so the view is shareable and back-button-correct.
 * Full async states: skeleton / empty-with-CTA / error-retry. Bulk-select
 * structure is present but actions are disabled (backend pending).
 */
@Component({
  selector: 'hum-employee-directory',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    PageHeaderComponent,
    ButtonComponent,
    InputComponent,
    SelectComponent,
    DataTableComponent,
    BadgeComponent,
    DrawerComponent,
    AlertComponent,
    EmptyStateComponent,
    SkeletonRowComponent,
  ],
  template: `
    <ng-template #statusCell let-row>
      <hum-badge [status]="$any(row).status" />
    </ng-template>

    <div class="hum-page">
      <hum-page-header
        [title]="'humano.directory.title' | translate"
        [subtitle]="'humano.directory.count' | translate: { total: list.total() }"
      />

      <form [formGroup]="filters" style="display:flex;flex-wrap:wrap;gap:var(--space-3);margin-bottom:var(--space-4)">
        <div style="flex:1;min-width:220px">
          <hum-input icon="search" formControlName="q" [placeholder]="'humano.directory.search' | translate" />
        </div>
        <hum-select formControlName="dept" [options]="departmentOptions()" />
        <hum-select formControlName="status" [options]="statusOptions()" />
      </form>

      @if (list.loading()) {
        <div class="hum-table-wrap">
          <hum-skeleton-row /><hum-skeleton-row /><hum-skeleton-row /><hum-skeleton-row /><hum-skeleton-row />
        </div>
      } @else if (list.error(); as e) {
        <hum-alert tone="danger" [title]="e">
          <hum-button variant="outline" size="sm" (click)="list.reload()">{{ 'humano.action.retry' | translate }}</hum-button>
        </hum-alert>
      } @else if (list.empty()) {
        <hum-empty-state
          icon="users"
          [title]="'humano.directory.emptyTitle' | translate"
          [description]="'humano.directory.emptyDescription' | translate"
          hasAction
        >
          <hum-button hum-empty-action variant="secondary" (click)="clearFilters()">{{
            'humano.action.clearFilters' | translate
          }}</hum-button>
        </hum-empty-state>
      } @else {
        <hum-data-table
          [columns]="columns()"
          [rows]="rows()"
          rowKey="id"
          [clickableRows]="true"
          [manualSort]="true"
          [initialSort]="sort()"
          selectable
          [selectedSuffix]="'humano.directory.bulkSelected' | translate"
          hasFooter
          (rowClick)="openDetail($event)"
          (sortChange)="onSort($event)"
        >
          <div hum-table-bulk>
            <!-- TODO: backend endpoint missing — bulk actions (deactivate/export) not available; structure kept, controls disabled. -->
            <hum-button variant="outline" size="sm" [disabled]="true">{{ 'humano.action.delete' | translate }}</hum-button>
          </div>

          <div hum-table-footer style="display:flex;align-items:center;gap:var(--space-3);width:100%;justify-content:flex-end">
            <span class="tabular-nums">{{ rangeLabel() }}</span>
            <hum-button variant="outline" size="sm" [disabled]="page() === 0" (click)="goto(page() - 1)" aria-label="Previous page"
              >‹</hum-button
            >
            <hum-button variant="outline" size="sm" [disabled]="isLastPage()" (click)="goto(page() + 1)" aria-label="Next page"
              >›</hum-button
            >
          </div>
        </hum-data-table>
      }
    </div>

    <hum-drawer [open]="selectedId() !== null" [title]="'humano.employee.drawerTitle' | translate" hasFooter (closed)="closeDetail()">
      @if (detailLoading()) {
        <div style="display:grid;gap:var(--space-3)"><hum-skeleton-row /><hum-skeleton-row /><hum-skeleton-row /></div>
      } @else if (detailError(); as e) {
        <hum-alert tone="danger" [title]="e">
          <hum-button variant="outline" size="sm" (click)="reloadDetail()">{{ 'humano.action.retry' | translate }}</hum-button>
        </hum-alert>
      } @else if (detail(); as d) {
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:var(--space-4)">
          <div>
            <div class="hum-field__label">{{ 'humano.employee.jobTitle' | translate }}</div>
            <div>{{ d.jobTitle ?? '—' }}</div>
          </div>
          <div>
            <div class="hum-field__label">{{ 'humano.employee.status' | translate }}</div>
            <hum-badge [status]="d.status" />
          </div>
          <div>
            <div class="hum-field__label">{{ 'humano.employee.department' | translate }}</div>
            <div>{{ d.departmentName ?? '—' }}</div>
          </div>
          <div>
            <div class="hum-field__label">{{ 'humano.employee.position' | translate }}</div>
            <div>{{ d.positionName ?? '—' }}</div>
          </div>
          <div>
            <div class="hum-field__label">{{ 'humano.employee.unit' | translate }}</div>
            <div>{{ d.unitName ?? '—' }}</div>
          </div>
          <div>
            <div class="hum-field__label">{{ 'humano.employee.manager' | translate }}</div>
            <div>{{ d.managerInfo ?? '—' }}</div>
          </div>
          <div>
            <div class="hum-field__label">{{ 'humano.employee.country' | translate }}</div>
            <div>{{ d.countryName ?? '—' }}</div>
          </div>
          <div>
            <div class="hum-field__label">{{ 'humano.employee.startDate' | translate }}</div>
            <div class="tabular-nums">{{ d.startDate ?? '—' }}</div>
          </div>
        </div>
      }
      <div hum-drawer-footer style="display:flex;gap:var(--space-2);justify-content:flex-end">
        <hum-button variant="secondary" (click)="closeDetail()">{{ 'humano.action.cancel' | translate }}</hum-button>
        @if (detail(); as d) {
          <hum-button variant="primary" trailingIcon="arrow-right" (click)="openFull(d.id)">{{
            'humano.employee.openProfile' | translate
          }}</hum-button>
        }
      </div>
    </hum-drawer>
  `,
})
export default class EmployeeDirectoryComponent {
  private readonly fb = inject(FormBuilder);
  private readonly employeeService = inject(EmployeeService);
  private readonly departmentService = inject(DepartmentService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);

  private readonly size = DEFAULT_PAGE_SIZE;
  private readonly criteria = signal<EmployeeSearchRequest>({});
  private readonly queryParams = toSignal(this.route.queryParamMap, { requireSync: true });

  // Filters reflect the URL; the URL drives the query (single source of truth).
  protected readonly filters = this.fb.nonNullable.group({ q: '', dept: '', status: '' });
  protected readonly departments = signal<{ value: string; label: string }[]>([]);

  protected readonly page = computed(() => Number(this.queryParams().get('page') ?? 0));
  protected readonly sort = computed<SortState>(() => {
    const raw = this.queryParams().get('sort');
    const [key, dir] = (raw ?? 'jobTitle,asc').split(',');
    return { key, dir: dir === 'desc' ? 'desc' : 'asc' };
  });

  protected readonly list = createListResource<SimpleEmployeeProfile>(
    req => {
      const c = this.criteria();
      return Object.keys(c).length ? this.employeeService.search(c, req) : this.employeeService.query(req);
    },
    { autoLoad: false },
  );

  // Detail drawer state.
  protected readonly selectedId = signal<string | null>(null);
  protected readonly detail = signal<EmployeeProfile | null>(null);
  protected readonly detailLoading = signal(false);
  protected readonly detailError = signal<string | null>(null);

  private readonly statusCellTpl = viewChild<TemplateRef<{ $implicit: Record<string, unknown>; value: unknown }>>('statusCell');

  /** DataTable is loosely typed (`Row`); rows are cast for binding, cells use `$any`. */
  protected readonly rows = computed<Record<string, unknown>[]>(() => this.list.items() as unknown as Record<string, unknown>[]);

  protected readonly columns = computed<Column[]>(() => [
    { key: 'jobTitle', label: this.translate.instant('humano.directory.colJobTitle'), sortable: true },
    { key: 'departmentName', label: this.translate.instant('humano.directory.colDepartment') },
    { key: 'positionName', label: this.translate.instant('humano.directory.colPosition') },
    { key: 'status', label: this.translate.instant('humano.directory.colStatus'), cell: this.statusCellTpl() },
  ]);

  protected readonly departmentOptions = computed(() => [{ value: '', label: this.allLabel() }, ...this.departments()]);
  protected readonly statusOptions = computed(() => [
    { value: '', label: this.allLabel() },
    ...Object.values(EmployeeStatus).map(s => ({ value: s, label: this.translate.instant('humano.directory.colStatus') + ': ' + s })),
  ]);

  constructor() {
    this.departmentService.query({ size: 200, sort: ['name,asc'] }).subscribe({
      next: page => this.departments.set(page.content.map(d => ({ value: d.id, label: d.name }))),
    });

    // URL → state: rebuild criteria + page/sort and (re)load whenever the query changes.
    effect(() => {
      const qp = this.queryParams();
      const q = qp.get('q') ?? '';
      const dept = qp.get('dept') ?? '';
      const status = qp.get('status') ?? '';
      this.filters.patchValue({ q, dept, status }, { emitEvent: false });
      this.criteria.set({
        ...(q ? { jobTitle: q } : {}),
        ...(dept ? { departmentId: dept } : {}),
        ...(status ? { status: status as EmployeeStatus } : {}),
      });
      this.list.setParams({ page: this.page(), size: this.size, sort: [`${this.sort().key},${this.sort().dir}`] });
    });

    // Filter changes → URL (debounced so typing doesn't spam navigations); reset to page 0.
    this.filters.valueChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe(v => {
      this.patchQuery({ q: v.q || null, dept: v.dept || null, status: v.status || null, page: 0 });
    });
  }

  protected onSort(s: SortState): void {
    this.patchQuery({ sort: `${s.key},${s.dir}`, page: 0 });
  }

  protected goto(page: number): void {
    this.patchQuery({ page });
  }

  protected clearFilters(): void {
    this.patchQuery({ q: null, dept: null, status: null, page: 0 });
  }

  protected isLastPage(): boolean {
    return (this.page() + 1) * this.size >= this.list.total();
  }

  protected rangeLabel(): string {
    const start = this.page() * this.size + 1;
    const end = Math.min(start + this.list.items().length - 1, this.list.total());
    return `${start}–${end} / ${this.list.total()}`;
  }

  protected openDetail(row: Record<string, unknown>): void {
    const id = String(row['id']);
    this.selectedId.set(id);
    this.loadDetail(id);
  }

  protected reloadDetail(): void {
    const id = this.selectedId();
    if (id) this.loadDetail(id);
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

  protected closeDetail(): void {
    this.selectedId.set(null);
    this.detail.set(null);
    this.detailError.set(null);
  }

  protected openFull(id: string): void {
    void this.router.navigate(['/employees', id]);
  }

  private patchQuery(patch: Record<string, string | number | null>): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: patch, queryParamsHandling: 'merge' });
  }

  private allLabel(): string {
    return this.translate.instant('humano.directory.all');
  }
}
