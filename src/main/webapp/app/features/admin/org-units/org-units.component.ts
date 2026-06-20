import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Observable, map } from 'rxjs';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { DEFAULT_PAGE_SIZE, normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  AutocompleteComponent,
  AutocompleteOption,
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
  SkeletonRowComponent,
  ToastService,
} from 'app/shared/ui';

import { CreateOrganizationalUnitRequest, OrganizationalUnit, OrganizationalUnitService, OrganizationalUnitType } from '../index';
import { EmployeeService } from 'app/features/employee';

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
    TranslatePipe,
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

  protected readonly size = DEFAULT_PAGE_SIZE;
  protected readonly units = signal<OrganizationalUnit[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly formOpen = signal(false);
  protected readonly saving = signal(false);
  protected readonly editing = signal<OrganizationalUnit | null>(null);
  protected readonly deleteTarget = signal<OrganizationalUnit | null>(null);
  protected readonly deleting = signal(false);
  protected readonly managerInitial = signal<AutocompleteOption | null>(null);

  /** Reference list for the parent picker (org structure is bounded). */
  private readonly allUnits = signal<OrganizationalUnit[]>([]);

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
        .filter(u => u.id !== editingId)
        .map(u => ({ value: u.id, label: u.name })),
    ];
  });

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    type: ['', [Validators.required]],
    parentUnitId: [''],
    managerId: [''],
  });

  /** Manager picker — employees are unbounded, so search the backend per keystroke. */
  protected readonly searchManagers = (term: string): Observable<AutocompleteOption[]> =>
    this.employeeService
      .search({ jobTitle: term }, { page: 0, size: 10 })
      .pipe(map(res => res.content.map(e => ({ value: e.id, label: e.jobTitle ?? e.id, sublabel: e.departmentName }))));

  constructor() {
    this.load();
    this.loadAllUnits();
  }

  protected titleCase(value: string): string {
    return value.charAt(0) + value.slice(1).toLowerCase();
  }

  protected invalid(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  protected goto(page: number): void {
    this.page.set(page);
    this.load();
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.managerInitial.set(null);
    this.form.reset({ name: '', type: '', parentUnitId: '', managerId: '' });
    this.formOpen.set(true);
  }

  protected openEdit(unit: OrganizationalUnit): void {
    this.editing.set(unit);
    this.managerInitial.set(unit.managerId ? { value: unit.managerId, label: unit.managerName ?? unit.managerId } : null);
    this.form.reset({
      name: unit.name,
      type: unit.type,
      parentUnitId: unit.parentUnitId ?? '',
      managerId: unit.managerId ?? '',
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
        this.loadAllUnits();
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
        this.loadAllUnits();
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
    this.unitService.query({ page: this.page(), size: this.size, sort: ['name,asc'] }).subscribe({
      next: res => {
        this.units.set(res.content);
        this.total.set(res.totalElements);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.loading.set(false);
      },
    });
  }

  private loadAllUnits(): void {
    this.unitService.query({ page: 0, size: 200, sort: ['name,asc'] }).subscribe({
      next: res => this.allUnits.set(res.content),
    });
  }
}
