import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { DEFAULT_PAGE_SIZE, normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  BadgeComponent,
  ButtonComponent,
  CheckboxComponent,
  DialogComponent,
  DrawerComponent,
  EmptyStateComponent,
  FormFieldComponent,
  IconButtonComponent,
  InputComponent,
  PageHeaderComponent,
  SkeletonRowComponent,
  SwitchComponent,
  TagComponent,
  ToastService,
} from 'app/shared/ui';
import { stripHtml } from 'app/shared/util/strip-html';

import { AdminUserService } from '../index';
import { ManagedUser } from '../index';

/** Admin user management — list + create/edit drawer + delete confirm. ADMIN-gated. */
@Component({
  selector: 'hum-user-management',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    PageHeaderComponent,
    ButtonComponent,
    IconButtonComponent,
    BadgeComponent,
    TagComponent,
    AlertComponent,
    EmptyStateComponent,
    SkeletonRowComponent,
    DrawerComponent,
    DialogComponent,
    InputComponent,
    SwitchComponent,
    CheckboxComponent,
    FormFieldComponent,
  ],
  templateUrl: './user-management.component.html',
})
export default class UserManagementComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(AdminUserService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);

  protected readonly size = DEFAULT_PAGE_SIZE;
  protected readonly users = signal<ManagedUser[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly authorities = signal<string[]>([]);
  protected readonly selectedAuthorities = signal<Set<string>>(new Set());
  protected readonly formOpen = signal(false);
  protected readonly saving = signal(false);
  private readonly editing = signal<ManagedUser | null>(null);

  protected readonly deleteTarget = signal<ManagedUser | null>(null);
  protected readonly deleting = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    login: ['', [Validators.required, Validators.maxLength(50)]],
    firstName: ['', [Validators.maxLength(50)]],
    lastName: ['', [Validators.maxLength(50)]],
    email: ['', [Validators.required, Validators.email, Validators.minLength(5), Validators.maxLength(254)]],
    activated: [true],
  });

  constructor() {
    this.load();
    this.api.authorities().subscribe({ next: list => this.authorities.set(list) });
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.query({ page: this.page(), size: this.size, sort: ['login,asc'] }).subscribe({
      next: res => {
        this.users.set(res.content);
        this.total.set(res.total);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.loading.set(false);
      },
    });
  }

  protected goto(page: number): void {
    this.page.set(page);
    this.load();
  }

  protected ctrlInvalid(control: 'login' | 'email'): boolean {
    const c = this.form.controls[control];
    return c.invalid && (c.dirty || c.touched);
  }

  protected toggleAuthority(authority: string, checked: boolean): void {
    this.selectedAuthorities.update(set => {
      const next = new Set(set);
      checked ? next.add(authority) : next.delete(authority);
      return next;
    });
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.form.reset({ login: '', firstName: '', lastName: '', email: '', activated: true });
    this.form.controls.login.enable();
    this.selectedAuthorities.set(new Set(['ROLE_USER']));
    this.formOpen.set(true);
  }

  protected openEdit(user: ManagedUser): void {
    this.editing.set(user);
    this.form.reset({
      login: user.login,
      firstName: user.firstName ?? '',
      lastName: user.lastName ?? '',
      email: user.email,
      activated: user.activated,
    });
    this.form.controls.login.disable();
    this.selectedAuthorities.set(new Set(user.authorities));
    this.formOpen.set(true);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const raw = this.form.getRawValue();
    const authorities = [...this.selectedAuthorities()];
    const editing = this.editing();
    const request$ = editing
      ? this.api.update({ ...raw, id: editing.id, authorities })
      : this.api.create({ login: raw.login, firstName: raw.firstName, lastName: raw.lastName, email: raw.email, authorities });
    request$.subscribe({
      next: () => {
        this.toast.success(
          stripHtml(this.translate.instant(editing ? 'userManagement.updated' : 'userManagement.created', { param: raw.login })),
        );
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
    this.api.delete(target.login).subscribe({
      next: () => {
        this.toast.success(stripHtml(this.translate.instant('userManagement.deleted', { param: target.login })));
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
}
