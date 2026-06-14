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

import { AdminUserService } from './admin-user.service';
import { ManagedUser } from './managed-user.model';

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
  template: `
    <div class="hum-page">
      <hum-page-header [title]="'userManagement.home.title' | translate" [hasActions]="true">
        <hum-button hum-page-actions variant="primary" icon="plus" (click)="openCreate()">
          {{ 'userManagement.home.createLabel' | translate }}
        </hum-button>
      </hum-page-header>

      @if (loading()) {
        <div class="hum-table-wrap">
          <hum-skeleton-row /><hum-skeleton-row /><hum-skeleton-row /><hum-skeleton-row /><hum-skeleton-row />
        </div>
      } @else if (error(); as e) {
        <hum-alert tone="danger" [title]="e">
          <hum-button variant="outline" size="sm" (click)="load()">{{ 'humano.action.retry' | translate }}</hum-button>
        </hum-alert>
      } @else if (users().length === 0) {
        <hum-empty-state icon="users" [title]="'userManagement.home.title' | translate" hasAction>
          <hum-button hum-empty-action variant="primary" icon="plus" (click)="openCreate()">{{
            'userManagement.home.createLabel' | translate
          }}</hum-button>
        </hum-empty-state>
      } @else {
        <table class="hum-table">
          <thead>
            <tr>
              <th>{{ 'userManagement.login' | translate }}</th>
              <th>{{ 'userManagement.email' | translate }}</th>
              <th>{{ 'userManagement.activated' | translate }}</th>
              <th>{{ 'userManagement.profiles' | translate }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (u of users(); track u.id) {
              <tr>
                <td>{{ u.login }}</td>
                <td>{{ u.email }}</td>
                <td><hum-badge [status]="u.activated ? 'ACTIVE' : 'INACTIVE'" /></td>
                <td>
                  @for (a of u.authorities; track a) {
                    <hum-tag>{{ a }}</hum-tag>
                  }
                </td>
                <td style="text-align:right;white-space:nowrap">
                  <hum-icon-button icon="pencil" [label]="'entity.action.edit' | translate" (click)="openEdit(u)" />
                  <hum-icon-button
                    icon="trash-2"
                    variant="ghost"
                    [label]="'entity.action.delete' | translate"
                    (click)="deleteTarget.set(u)"
                  />
                </td>
              </tr>
            }
          </tbody>
        </table>

        <div style="display:flex;align-items:center;justify-content:space-between;margin-top:var(--space-3)">
          <span class="text-muted" style="font-size:var(--text-sm)">{{ total() }}</span>
          <div style="display:flex;gap:var(--space-2)">
            <hum-button variant="outline" size="sm" [disabled]="page() === 0" (click)="goto(page() - 1)">‹</hum-button>
            <hum-button variant="outline" size="sm" [disabled]="(page() + 1) * size >= total()" (click)="goto(page() + 1)">›</hum-button>
          </div>
        </div>
      }
    </div>

    <!-- Create / edit -->
    <hum-drawer [open]="formOpen()" [title]="'userManagement.home.createOrEditLabel' | translate" hasFooter (closed)="formOpen.set(false)">
      <form [formGroup]="form" style="display:grid;gap:var(--space-4)">
        <hum-form-field [label]="'userManagement.login' | translate" controlId="um-login">
          <hum-input inputId="um-login" formControlName="login" [invalid]="ctrlInvalid('login')" />
        </hum-form-field>
        <hum-form-field [label]="'userManagement.firstName' | translate" controlId="um-first">
          <hum-input inputId="um-first" formControlName="firstName" />
        </hum-form-field>
        <hum-form-field [label]="'userManagement.lastName' | translate" controlId="um-last">
          <hum-input inputId="um-last" formControlName="lastName" />
        </hum-form-field>
        <hum-form-field
          [label]="'userManagement.email' | translate"
          controlId="um-email"
          [error]="ctrlInvalid('email') ? ('global.messages.validate.email.invalid' | translate) : undefined"
        >
          <hum-input inputId="um-email" type="email" formControlName="email" [invalid]="ctrlInvalid('email')" />
        </hum-form-field>
        <hum-switch formControlName="activated" [label]="'userManagement.activated' | translate" />
        <fieldset style="border:0;padding:0;margin:0;display:grid;gap:var(--space-2)">
          <legend class="hum-field__label">{{ 'userManagement.profiles' | translate }}</legend>
          @for (a of authorities(); track a) {
            <label style="display:flex;align-items:center;gap:var(--space-2)">
              <hum-checkbox [checked]="selectedAuthorities().has(a)" (checkedChange)="toggleAuthority(a, $event)" />
              <span>{{ a }}</span>
            </label>
          }
        </fieldset>
      </form>
      <div hum-drawer-footer style="display:flex;gap:var(--space-2);justify-content:flex-end">
        <hum-button variant="secondary" (click)="formOpen.set(false)">{{ 'humano.action.cancel' | translate }}</hum-button>
        <hum-button variant="primary" [loading]="saving()" [disabled]="form.invalid" (click)="save()">{{
          'humano.action.save' | translate
        }}</hum-button>
      </div>
    </hum-drawer>

    <!-- Delete confirm -->
    <hum-dialog
      [open]="deleteTarget() !== null"
      [title]="'entity.delete.title' | translate"
      [description]="deleteTarget() ? ('userManagement.delete.question' | translate: { login: deleteTarget()!.login }) : ''"
      hasFooter
      (closed)="deleteTarget.set(null)"
    >
      <div hum-dialog-footer style="display:flex;gap:var(--space-2);justify-content:flex-end">
        <hum-button variant="secondary" (click)="deleteTarget.set(null)">{{ 'humano.action.cancel' | translate }}</hum-button>
        <hum-button variant="destructive" [loading]="deleting()" (click)="confirmDelete()">{{
          'humano.action.delete' | translate
        }}</hum-button>
      </div>
    </hum-dialog>
  `,
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
