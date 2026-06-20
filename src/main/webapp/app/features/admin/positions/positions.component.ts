import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { DEFAULT_PAGE_SIZE, normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
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
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';

import { CreatePositionRequest, OrganizationalUnit, OrganizationalUnitService, Position, PositionService } from '../index';

/**
 * Positions management (HR/admin) — paged list with a create/edit drawer and
 * delete confirm. A position optionally belongs to an org unit (picked from the
 * bounded unit list). Manage actions are gated on `MANAGE_POSITIONS`.
 */
@Component({
  selector: 'hum-positions',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    PageHeaderComponent,
    ButtonComponent,
    IconButtonComponent,
    AlertComponent,
    EmptyStateComponent,
    SkeletonRowComponent,
    DrawerComponent,
    DialogComponent,
    InputComponent,
    TextareaComponent,
    SelectComponent,
    FormFieldComponent,
  ],
  templateUrl: './positions.component.html',
})
export default class PositionsComponent {
  private readonly fb = inject(FormBuilder);
  private readonly positionService = inject(PositionService);
  private readonly unitService = inject(OrganizationalUnitService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly account = inject(AccountService);

  protected readonly canManage = this.account.hasPermission(Permission.MANAGE_POSITIONS);

  protected readonly size = DEFAULT_PAGE_SIZE;
  protected readonly positions = signal<Position[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly formOpen = signal(false);
  protected readonly saving = signal(false);
  protected readonly editing = signal<Position | null>(null);
  protected readonly deleteTarget = signal<Position | null>(null);
  protected readonly deleting = signal(false);

  /** Unit picker source — org structure is bounded reference data. */
  private readonly units = signal<OrganizationalUnit[]>([]);
  protected readonly unitOptions = computed<SelectOption[]>(() => [
    { value: '', label: this.translate.instant('humano.positions.noUnit') },
    ...this.units().map(u => ({ value: u.id, label: u.name })),
  ]);

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    level: ['', [Validators.required]],
    description: ['', [Validators.maxLength(1000)]],
    unitId: [''],
  });

  constructor() {
    this.load();
    this.loadUnits();
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
    this.form.reset({ name: '', level: '', description: '', unitId: '' });
    this.formOpen.set(true);
  }

  protected openEdit(position: Position): void {
    this.editing.set(position);
    this.form.reset({
      name: position.name,
      level: position.level,
      description: position.description ?? '',
      unitId: position.unitId ?? '',
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
    };

    this.saving.set(true);
    const editing = this.editing();
    const request$ = editing ? this.positionService.update(editing.id, body) : this.positionService.create(body);
    request$.subscribe({
      next: () => {
        this.toast.success(this.translate.instant(editing ? 'humano.positions.updated' : 'humano.positions.created'));
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
        this.toast.success(this.translate.instant('humano.positions.deleted'));
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
    this.loading.set(true);
    this.error.set(null);
    this.positionService.query({ page: this.page(), size: this.size, sort: ['name,asc'] }).subscribe({
      next: res => {
        this.positions.set(res.content);
        this.total.set(res.totalElements);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.loading.set(false);
      },
    });
  }

  private loadUnits(): void {
    this.unitService.query({ page: 0, size: 200, sort: ['name,asc'] }).subscribe({
      next: res => this.units.set(res.content),
    });
  }
}
