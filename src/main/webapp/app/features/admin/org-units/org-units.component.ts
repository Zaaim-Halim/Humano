import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Observable, map } from 'rxjs';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { normalizeHttpError } from 'app/core/api';
import { BadgeTone } from 'app/config/status-map';
import {
  AlertComponent,
  AutocompleteComponent,
  AutocompleteOption,
  AvatarComponent,
  BadgeComponent,
  ButtonComponent,
  DialogComponent,
  DrawerComponent,
  EmptyStateComponent,
  FormFieldComponent,
  IconButtonComponent,
  InputComponent,
  PageHeaderComponent,
  SelectComponent,
  SelectOption,
  SkeletonComponent,
  SkeletonRowComponent,
  ToastService,
} from 'app/shared/ui';

import { CreateOrganizationalUnitRequest, OrganizationalUnitService, OrganizationalUnitType } from '../index';
import { EmployeeService, HrHierarchyService, type OrganizationalUnitTreeNode } from 'app/features/employee';
import { PluralPipe } from 'app/shared/util/format.pipes';

/** OrganizationalUnitType → badge tone (same families as the organization tree). */
const TYPE_TONE: Record<string, BadgeTone> = {
  DIRECTORATE: 'brand',
  DIVISION: 'brand',
  BOARD: 'brand',
  DEPARTMENT: 'info',
  SECTOR: 'info',
  SECTION: 'info',
  BRANCH: 'warning',
  OFFICE: 'warning',
  TEAM: 'success',
  SQUAD: 'success',
  CELL: 'success',
  GROUP: 'success',
};

/** One visible row of the tree table. */
interface UnitRow {
  node: OrganizationalUnitTreeNode;
  depth: number;
  hasChildren: boolean;
}

/**
 * Organizational units management (HR/admin) — paged list with a create/edit
 * drawer and delete confirm. The companion read-only org chart lives at
 * `/organization-tree`. Manage actions are gated on `MANAGE_ORGANIZATIONAL_UNITS`.
 */
@Component({
  selector: 'hum-org-units',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    FormsModule,
    RouterLink,
    LucideAngularModule,
    TranslatePipe,
    PluralPipe,
    AvatarComponent,
    SkeletonComponent,
    PageHeaderComponent,
    ButtonComponent,
    IconButtonComponent,
    BadgeComponent,
    AlertComponent,
    EmptyStateComponent,
    SkeletonRowComponent,
    DrawerComponent,
    DialogComponent,
    InputComponent,
    SelectComponent,
    AutocompleteComponent,
    FormFieldComponent,
  ],
  templateUrl: './org-units.component.html',
})
export default class OrgUnitsComponent {
  private readonly fb = inject(FormBuilder);
  private readonly unitService = inject(OrganizationalUnitService);
  private readonly employeeService = inject(EmployeeService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly account = inject(AccountService);

  protected readonly canManage = this.account.hasPermission(Permission.MANAGE_ORGANIZATIONAL_UNITS);

  /** The whole unit forest (one request, headcounts rolled up server-side). */
  protected readonly roots = signal<OrganizationalUnitTreeNode[]>([]);
  protected readonly loading = signal(false);
  protected readonly query = signal('');
  /** Collapsed unit ids (everything starts expanded). */
  protected readonly collapsed = signal(new Set<string>());
  protected readonly error = signal<string | null>(null);

  protected readonly formOpen = signal(false);
  protected readonly saving = signal(false);
  protected readonly editing = signal<OrganizationalUnitTreeNode | null>(null);
  protected readonly deleteTarget = signal<OrganizationalUnitTreeNode | null>(null);
  protected readonly deleting = signal(false);
  protected readonly managerInitial = signal<AutocompleteOption | null>(null);

  /** Reference list for the parent picker (org structure is bounded). */
  /** Every unit, depth-first, for the parent picker and stats. */
  private readonly allUnits = computed(() => {
    const out: { node: OrganizationalUnitTreeNode; depth: number }[] = [];
    const walk = (n: OrganizationalUnitTreeNode, depth: number): void => {
      out.push({ node: n, depth });
      (n.children ?? []).forEach(c => walk(c, depth + 1));
    };
    this.roots().forEach(r => walk(r, 0));
    return out;
  });

  /** Visible rows: depth-first, honouring collapse; a search keeps matches plus their ancestors. */
  protected readonly rows = computed<UnitRow[]>(() => {
    const q = this.query().trim().toLowerCase();
    const collapsed = this.collapsed();
    const matches = (n: OrganizationalUnitTreeNode): boolean =>
      n.name.toLowerCase().includes(q) || (n.manager?.fullName ?? '').toLowerCase().includes(q) || n.type.toLowerCase().includes(q);
    const keep = (n: OrganizationalUnitTreeNode): boolean => !q || matches(n) || (n.children ?? []).some(keep);
    const out: UnitRow[] = [];
    const walk = (n: OrganizationalUnitTreeNode, depth: number): void => {
      if (!keep(n)) return;
      const children = n.children ?? [];
      out.push({ node: n, depth, hasChildren: children.length > 0 });
      // While searching, everything on a matching path is shown expanded.
      if (q || !collapsed.has(n.id)) children.forEach(c => walk(c, depth + 1));
    };
    this.roots().forEach(r => walk(r, 0));
    return out;
  });

  protected readonly stats = computed(() => {
    const all = this.allUnits();
    return {
      units: all.length,
      topLevel: this.roots().length,
      levels: all.reduce((max, u) => Math.max(max, u.depth + 1), 0),
      withoutManager: all.filter(u => !u.node.manager).length,
      people: this.roots().reduce((sum, r) => sum + (r.totalHeadcount ?? 0), 0),
    };
  });

  protected readonly typeOptions: SelectOption[] = [
    { value: '', label: this.translate.instant('humano.orgUnits.typeUnset') },
    ...Object.values(OrganizationalUnitType).map(t => ({ value: t, label: this.titleCase(t) })),
  ];

  /** Parent options exclude the unit being edited (a unit can't be its own parent). */
  protected readonly parentOptions = computed<SelectOption[]>(() => {
    const editingId = this.editing()?.id;
    return [
      { value: '', label: this.translate.instant('humano.orgUnits.noParent') },
      ...this.allUnits()
        .filter(u => u.node.id !== editingId)
        .map(u => ({ value: u.node.id, label: `${'\u2003'.repeat(u.depth)}${u.node.name}` })),
    ];
  });

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    type: ['', [Validators.required]],
    parentUnitId: [''],
    managerId: [''],
  });

  private readonly hierarchy = inject(HrHierarchyService);

  constructor() {
    this.load();
  }

  /** Manager picker — employees are unbounded, so search the backend per keystroke. */
  protected readonly searchManagers = (term: string): Observable<AutocompleteOption[]> => this.employeeService.searchOptions(term);

  protected tone(type: string): BadgeTone {
    return TYPE_TONE[type] ?? 'neutral';
  }

  /** Share of all people that sit in this unit's subtree. */
  protected share(node: OrganizationalUnitTreeNode): number {
    const total = this.stats().people;
    return total ? ((node.totalHeadcount ?? 0) / total) * 100 : 0;
  }

  protected toggle(id: string): void {
    this.collapsed.update(set => {
      const next = new Set(set);
      if (!next.delete(id)) next.add(id);
      return next;
    });
  }

  protected setAllExpanded(expanded: boolean): void {
    this.collapsed.set(
      expanded
        ? new Set()
        : new Set(
            this.allUnits()
              .filter(u => u.node.children?.length)
              .map(u => u.node.id),
          ),
    );
  }

  protected titleCase(value: string): string {
    return value.charAt(0) + value.slice(1).toLowerCase();
  }

  protected invalid(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.managerInitial.set(null);
    this.form.reset({ name: '', type: '', parentUnitId: '', managerId: '' });
    this.formOpen.set(true);
  }

  protected openEdit(unit: OrganizationalUnitTreeNode): void {
    this.editing.set(unit);
    this.managerInitial.set(unit.manager ? { value: unit.manager.id, label: unit.manager.fullName } : null);
    this.form.reset({
      name: unit.name,
      type: unit.type,
      parentUnitId: unit.parentUnitId ?? '',
      managerId: unit.manager?.id ?? '',
    });
    this.formOpen.set(true);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const body: CreateOrganizationalUnitRequest = {
      name: raw.name,
      type: raw.type as OrganizationalUnitType,
      ...(raw.parentUnitId ? { parentUnitId: raw.parentUnitId } : {}),
      ...(raw.managerId ? { managerId: raw.managerId } : {}),
    };

    this.saving.set(true);
    const editing = this.editing();
    const request$ = editing ? this.unitService.update(editing.id, body) : this.unitService.create(body);
    request$.subscribe({
      next: () => {
        this.toast.success(this.translate.instant(editing ? 'humano.orgUnits.updated' : 'humano.orgUnits.created'));
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
    this.unitService.delete(target.id).subscribe({
      next: () => {
        this.toast.success(this.translate.instant('humano.orgUnits.deleted'));
        this.deleting.set(false);
        this.deleteTarget.set(null);
        this.load();
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.deleting.set(false);
      },
    });
  }

  protected load(): void {
    this.loading.set(this.roots().length === 0);
    this.error.set(null);
    this.hierarchy.getOrganizationalUnitTree(undefined, true).subscribe({
      next: res => {
        this.roots.set(res.roots);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.loading.set(false);
      },
    });
  }
}
