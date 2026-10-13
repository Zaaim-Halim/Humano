import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  AvatarComponent,
  BadgeComponent,
  ButtonComponent,
  DialogComponent,
  DrawerComponent,
  EmptyStateComponent,
  FormFieldComponent,
  IconButtonComponent,
  InputComponent,
  OrgChartComponent,
  OrgChartMemberDirective,
  OrgChartNodeDirective,
  PageHeaderComponent,
  SelectComponent,
  SelectOption,
  SkeletonComponent,
  SkeletonRowComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';
import { EmployeeService, personName, type SimpleEmployeeProfile } from 'app/features/employee';
import { PluralPipe, pluralKey } from 'app/shared/util/format.pipes';

import {
  CreatePositionRequest,
  JobLevelService,
  OrganizationalUnit,
  OrganizationalUnitService,
  Position,
  PositionService,
  ReferenceData,
} from '../index';

type ViewMode = 'ladder' | 'chart';

/** Seniority order of the standard job-level codes; unknown levels sort after these, by name. */
const LEVEL_ORDER = ['INTERN', 'JUNIOR', 'MID', 'SENIOR', 'LEAD', 'PRINCIPAL', 'ARCHITECT'];

/** A position shaped for the generic org chart. */
type PositionNode = Position & { children: PositionNode[] };

/**
 * Positions (HR/admin) — the job architecture.
 *
 * - Ladder view: positions grouped into columns by job level (junior → principal), each card
 *   showing its unit and how many people hold it (vacant positions are flagged).
 * - Chart view: the position hierarchy (`parentPosition`) on the shared org chart.
 * - Overview strip (positions, filled, vacant, levels), search and unit filter — client-side,
 *   positions being a bounded, fully loaded set.
 * - Selecting a position opens a panel with its holders and sub-positions; create/edit is a
 *   drawer (level from job-level reference data, optional parent position), delete a confirm.
 *   Manage actions are gated on `MANAGE_POSITIONS`.
 */
@Component({
  selector: 'hum-positions',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    FormsModule,
    RouterLink,
    LucideAngularModule,
    TranslatePipe,
    PluralPipe,
    PageHeaderComponent,
    ButtonComponent,
    IconButtonComponent,
    AlertComponent,
    AvatarComponent,
    BadgeComponent,
    EmptyStateComponent,
    SkeletonComponent,
    SkeletonRowComponent,
    DrawerComponent,
    DialogComponent,
    InputComponent,
    TextareaComponent,
    SelectComponent,
    FormFieldComponent,
    OrgChartComponent,
    OrgChartNodeDirective,
    OrgChartMemberDirective,
  ],
  templateUrl: './positions.component.html',
})
export default class PositionsComponent {
  private readonly fb = inject(FormBuilder);
  private readonly positionService = inject(PositionService);
  private readonly unitService = inject(OrganizationalUnitService);
  private readonly jobLevelService = inject(JobLevelService);
  private readonly employeeService = inject(EmployeeService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly account = inject(AccountService);

  protected readonly canManage = this.account.hasPermission(Permission.MANAGE_POSITIONS);

  protected readonly positions = signal<Position[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly view = signal<ViewMode>('ladder');
  protected readonly query = signal('');
  protected readonly unitFilter = signal('');

  protected readonly formOpen = signal(false);
  protected readonly saving = signal(false);
  protected readonly editing = signal<Position | null>(null);
  protected readonly deleteTarget = signal<Position | null>(null);
  protected readonly deleting = signal(false);

  // Detail panel.
  protected readonly selected = signal<Position | null>(null);
  protected readonly holders = signal<SimpleEmployeeProfile[] | null>(null);
  protected readonly holdersError = signal<string | null>(null);
  protected readonly name = personName;

  /** Unit picker source — org structure is bounded reference data. */
  private readonly units = signal<OrganizationalUnit[]>([]);
  private readonly levels = signal<ReferenceData[]>([]);

  protected readonly unitOptions = computed<SelectOption[]>(() => [
    { value: '', label: this.label('humano.positions.noUnit') },
    ...this.units().map(u => ({ value: u.id, label: u.name })),
  ]);
  protected readonly unitFilterOptions = computed<SelectOption[]>(() => [
    { value: '', label: this.label('humano.positions.allUnits') },
    ...this.units().map(u => ({ value: u.id, label: u.name })),
  ]);
  protected readonly levelOptions = computed<SelectOption[]>(() => [
    { value: '', label: this.label('humano.positions.selectLevel') },
    ...this.sortedLevels().map(l => ({ value: l.code, label: l.name })),
  ]);
  protected readonly parentOptions = computed<SelectOption[]>(() => {
    const self = this.editing()?.id;
    return [
      { value: '', label: this.label('humano.positions.noParent') },
      ...[...this.positions()]
        .filter(p => p.id !== self)
        .sort((a, b) => a.name.localeCompare(b.name))
        .map(p => ({ value: p.id, label: p.name })),
    ];
  });

  /** Positions after search + unit filter. */
  protected readonly filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    const unit = this.unitFilter();
    return this.positions().filter(
      p =>
        (!unit || p.unitId === unit) &&
        (!q || p.name.toLowerCase().includes(q) || (p.unitName ?? '').toLowerCase().includes(q) || p.level.toLowerCase().includes(q)),
    );
  });

  protected readonly stats = computed(() => {
    const list = this.positions();
    const filled = list.filter(p => p.employeeCount > 0).length;
    return { count: list.length, filled, vacant: list.length - filled, levels: new Set(list.map(p => p.level)).size };
  });

  /** Ladder columns: one per level present, in seniority order. */
  protected readonly ladder = computed(() => {
    const byLevel = new Map<string, Position[]>();
    for (const p of this.filtered()) {
      byLevel.set(p.level, [...(byLevel.get(p.level) ?? []), p]);
    }
    return [...byLevel.entries()]
      .sort(([a], [b]) => this.levelRank(a) - this.levelRank(b) || a.localeCompare(b))
      .map(([level, items]) => ({
        level,
        label: this.levelLabel(level),
        people: items.reduce((sum, p) => sum + p.employeeCount, 0),
        items: [...items].sort((a, b) => b.employeeCount - a.employeeCount || a.name.localeCompare(b.name)),
      }));
  });

  /** Chart roots: the position hierarchy built from `parentPositionId` (filters applied). */
  protected readonly chartRoots = computed<PositionNode[]>(() => {
    const visible = this.filtered();
    const nodes = new Map<string, PositionNode>(visible.map(p => [p.id, { ...p, children: [] }]));
    const roots: PositionNode[] = [];
    for (const node of nodes.values()) {
      const parent = node.parentPositionId ? nodes.get(node.parentPositionId) : undefined;
      if (parent) {
        parent.children.push(node);
      } else {
        roots.push(node);
      }
    }
    const size = (n: PositionNode): number => n.children.reduce((sum, c) => sum + 1 + size(c), 0);
    // Standalone positions (no parent, no children) would clutter the chart's top row.
    return roots.filter(r => r.children.length > 0).sort((a, b) => size(b) - size(a));
  });
  protected readonly standalone = computed(() =>
    this.filtered().filter(p => !p.parentPositionId && !this.positions().some(c => c.parentPositionId === p.id)),
  );

  protected readonly subPositions = computed(() => {
    const s = this.selected();
    return s ? this.positions().filter(p => p.parentPositionId === s.id) : [];
  });

  protected readonly childCountLabel = (node: PositionNode): string =>
    this.label(pluralKey('humano.positions.subPositions', node.children.length), { count: node.children.length });

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    level: ['', [Validators.required]],
    description: ['', [Validators.maxLength(1000)]],
    unitId: [''],
    parentPositionId: [''],
  });

  constructor() {
    this.load();
    this.unitService.query({ page: 0, size: 500, sort: ['name,asc'] }).subscribe({ next: res => this.units.set(res.content) });
    this.jobLevelService.query({ page: 0, size: 100 }).subscribe({ next: res => this.levels.set(res.content) });
  }

  protected levelLabel(code: string): string {
    return this.levels().find(l => l.code === code)?.name ?? code.charAt(0) + code.slice(1).toLowerCase();
  }

  protected invalid(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  protected openDetail(position: Position): void {
    this.selected.set(position);
    this.holders.set(null);
    this.holdersError.set(null);
    this.employeeService.search({ positionId: position.id }, { page: 0, size: 50, sort: ['firstName,asc'] }).subscribe({
      next: page => this.holders.set(page.content),
      error: (err: unknown) => this.holdersError.set(normalizeHttpError(err)),
    });
  }

  protected openDetailById(id: string): void {
    const p = this.positions().find(x => x.id === id);
    if (p) this.openDetail(p);
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.form.reset({ name: '', level: '', description: '', unitId: '', parentPositionId: '' });
    this.formOpen.set(true);
  }

  protected openEdit(position: Position): void {
    this.selected.set(null);
    this.editing.set(position);
    this.form.reset({
      name: position.name,
      level: position.level,
      description: position.description ?? '',
      unitId: position.unitId ?? '',
      parentPositionId: position.parentPositionId ?? '',
    });
    this.formOpen.set(true);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const body: CreatePositionRequest = {
      name: raw.name,
      level: raw.level,
      ...(raw.description ? { description: raw.description } : {}),
      ...(raw.unitId ? { unitId: raw.unitId } : {}),
      ...(raw.parentPositionId ? { parentPositionId: raw.parentPositionId } : {}),
    };

    this.saving.set(true);
    const editing = this.editing();
    const request$ = editing ? this.positionService.update(editing.id, body) : this.positionService.create(body);
    request$.subscribe({
      next: () => {
        this.toast.success(this.label(editing ? 'humano.positions.updated' : 'humano.positions.created'));
        this.formOpen.set(false);
        this.saving.set(false);
        this.load();
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.saving.set(false);
      },
    });
  }

  protected confirmDelete(): void {
    const target = this.deleteTarget();
    if (!target) return;
    this.deleting.set(true);
    this.positionService.delete(target.id).subscribe({
      next: () => {
        this.toast.success(this.label('humano.positions.deleted'));
        this.deleting.set(false);
        this.deleteTarget.set(null);
        this.selected.set(null);
        this.load();
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.deleting.set(false);
      },
    });
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    // Positions are bounded reference data: load them all and filter on the client.
    this.positionService.query({ page: 0, size: 1000, sort: ['name,asc'] }).subscribe({
      next: res => {
        this.positions.set(res.content);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.loading.set(false);
      },
    });
  }

  private sortedLevels(): ReferenceData[] {
    return [...this.levels()].sort((a, b) => this.levelRank(a.code) - this.levelRank(b.code) || a.name.localeCompare(b.name));
  }

  private levelRank(code: string): number {
    const i = LEVEL_ORDER.indexOf(code.toUpperCase());
    return i === -1 ? LEVEL_ORDER.length : i;
  }

  private label(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
