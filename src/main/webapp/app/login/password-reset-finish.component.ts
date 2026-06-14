import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { AlertComponent, ButtonComponent, FormFieldComponent, InputComponent } from 'app/shared/ui';

import { AuthPublicService } from './auth-public.service';

function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  return group.get('newPassword')!.value === group.get('confirmPassword')!.value ? null : { dontmatch: true };
}

/** Finish a password reset — `POST /api/account/reset-password/finish`. `key` from `?key=`. */
@Component({
  selector: 'hum-password-reset-finish',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, AlertComponent, ButtonComponent, InputComponent, FormFieldComponent],
  template: `
    <div class="hum-auth">
      <div class="hum-auth__panel">
        <div class="hum-auth__brand">humano<span class="hum-side__dot" aria-hidden="true"></span></div>
        <h1 class="hum-auth__title">{{ 'reset.finish.title' | translate }}</h1>

        @if (!key()) {
          <hum-alert tone="danger">{{ 'reset.finish.messages.keymissing' | translate }}</hum-alert>
        } @else if (success()) {
          <hum-alert tone="success">
            <span [innerHTML]="'reset.finish.messages.success' | translate"></span
            ><a routerLink="/login">{{ 'login.title' | translate }}</a>
          </hum-alert>
        } @else {
          @if (failed()) {
            <hum-alert tone="danger">{{ 'reset.finish.messages.error' | translate }}</hum-alert>
          }
          <p class="text-muted" style="font-size:var(--text-base)">{{ 'reset.finish.messages.info' | translate }}</p>
          <form [formGroup]="form" (ngSubmit)="submit()" novalidate style="display:grid;gap:var(--space-4)">
            <hum-form-field [label]="'global.form.newpassword.label' | translate" controlId="rf-password">
              <hum-input
                inputId="rf-password"
                type="password"
                formControlName="newPassword"
                [placeholder]="'global.form.newpassword.placeholder' | translate"
                [invalid]="invalid()"
              />
            </hum-form-field>
            <hum-form-field
              [label]="'global.form.confirmpassword.label' | translate"
              controlId="rf-confirm"
              [error]="mismatch() ? ('global.messages.error.dontmatch' | translate) : undefined"
            >
              <hum-input
                inputId="rf-confirm"
                type="password"
                formControlName="confirmPassword"
                [placeholder]="'global.form.confirmpassword.placeholder' | translate"
                [invalid]="mismatch()"
              />
            </hum-form-field>
            <hum-button type="submit" variant="primary" [block]="true" [loading]="loading()" [disabled]="form.invalid">{{
              'reset.finish.form.button' | translate
            }}</hum-button>
          </form>
        }
      </div>
    </div>
  `,
})
export default class PasswordResetFinishComponent {
  /** Reset key from `?key=` (bound via `withComponentInputBinding()`). */
  readonly key = input<string>();

  private readonly fb = inject(FormBuilder);
  private readonly api = inject(AuthPublicService);

  protected readonly loading = signal(false);
  protected readonly success = signal(false);
  protected readonly failed = signal(false);

  protected readonly form = this.fb.nonNullable.group(
    {
      newPassword: ['', [Validators.required, Validators.minLength(4), Validators.maxLength(50)]],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: passwordsMatch },
  );

  protected invalid(): boolean {
    const c = this.form.controls.newPassword;
    return c.invalid && (c.dirty || c.touched);
  }

  protected mismatch(): boolean {
    const confirm = this.form.controls.confirmPassword;
    return this.form.hasError('dontmatch') && (confirm.dirty || confirm.touched);
  }

  protected submit(): void {
    const key = this.key();
    if (this.form.invalid || !key) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.failed.set(false);
    this.api.finishPasswordReset(key, this.form.getRawValue().newPassword).subscribe({
      next: () => {
        this.success.set(true);
        this.loading.set(false);
      },
      error: () => {
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }
}
