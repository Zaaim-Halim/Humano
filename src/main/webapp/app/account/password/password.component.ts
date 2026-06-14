import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { AccountService } from 'app/core/auth/account.service';
import { ButtonComponent, FormFieldComponent, InputComponent, PageHeaderComponent, ToastService } from 'app/shared/ui';
import { stripHtml } from 'app/shared/util/strip-html';

import { AccountManagementService } from '../account-management.service';

/** Validator: `newPassword` must equal `confirmPassword` (error on the group). */
function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  return group.get('newPassword')!.value === group.get('confirmPassword')!.value ? null : { dontmatch: true };
}

/** Change own password — `POST /api/account/change-password`. In-shell. */
@Component({
  selector: 'hum-password',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe, PageHeaderComponent, ButtonComponent, InputComponent, FormFieldComponent],
  template: `
    <div class="hum-page" style="max-width:560px">
      <hum-page-header [title]="title()" />

      <form [formGroup]="form" (ngSubmit)="save()" novalidate style="display:grid;gap:var(--space-4)">
        <hum-form-field [label]="'global.form.currentpassword.label' | translate" controlId="pw-current">
          <hum-input
            inputId="pw-current"
            type="password"
            formControlName="currentPassword"
            [placeholder]="'global.form.currentpassword.placeholder' | translate"
          />
        </hum-form-field>

        <hum-form-field
          [label]="'global.form.newpassword.label' | translate"
          controlId="pw-new"
          [error]="invalid('newPassword') ? ('global.messages.validate.newpassword.minlength' | translate) : undefined"
        >
          <hum-input
            inputId="pw-new"
            type="password"
            formControlName="newPassword"
            [placeholder]="'global.form.newpassword.placeholder' | translate"
            [invalid]="invalid('newPassword')"
          />
        </hum-form-field>

        <hum-form-field
          [label]="'global.form.confirmpassword.label' | translate"
          controlId="pw-confirm"
          [error]="mismatch() ? ('global.messages.error.dontmatch' | translate) : undefined"
        >
          <hum-input
            inputId="pw-confirm"
            type="password"
            formControlName="confirmPassword"
            [placeholder]="'global.form.confirmpassword.placeholder' | translate"
            [invalid]="mismatch()"
          />
        </hum-form-field>

        <div>
          <hum-button type="submit" variant="primary" [loading]="saving()" [disabled]="form.invalid">{{
            'password.form.button' | translate
          }}</hum-button>
        </div>
      </form>
    </div>
  `,
})
export default class PasswordComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(AccountManagementService);
  private readonly accountService = inject(AccountService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);

  private readonly account = this.accountService.trackCurrentAccount();
  protected readonly saving = signal(false);
  protected readonly title = signal(this.translate.instant('password.title', { username: '' }));

  protected readonly form = this.fb.nonNullable.group(
    {
      currentPassword: ['', [Validators.required]],
      newPassword: ['', [Validators.required, Validators.minLength(4), Validators.maxLength(50)]],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: passwordsMatch },
  );

  constructor() {
    effect(() => {
      const a = this.account();
      if (a) {
        this.title.set(this.translate.instant('password.title', { username: a.login }));
      }
    });
  }

  protected invalid(control: 'newPassword'): boolean {
    const c = this.form.controls[control];
    return c.invalid && (c.dirty || c.touched);
  }

  protected mismatch(): boolean {
    const confirm = this.form.controls.confirmPassword;
    return this.form.hasError('dontmatch') && (confirm.dirty || confirm.touched);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const { currentPassword, newPassword } = this.form.getRawValue();
    this.api.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.toast.success(stripHtml(this.translate.instant('password.messages.success')));
        this.form.reset();
        this.saving.set(false);
      },
      error: () => {
        this.toast.danger(stripHtml(this.translate.instant('password.messages.error')));
        this.saving.set(false);
      },
    });
  }
}
