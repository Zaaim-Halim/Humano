import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { AlertComponent, ButtonComponent, FormFieldComponent, InputComponent } from 'app/shared/ui';

import { AuthPublicService } from './auth-public.service';

/** Request a password reset email — `POST /api/account/reset-password/init`. */
@Component({
  selector: 'hum-password-reset-request',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, AlertComponent, ButtonComponent, InputComponent, FormFieldComponent],
  template: `
    <div class="hum-auth">
      <div class="hum-auth__panel">
        <div class="hum-auth__brand">humano<span class="hum-side__dot" aria-hidden="true"></span></div>
        <h1 class="hum-auth__title">{{ 'reset.request.title' | translate }}</h1>

        @if (success()) {
          <hum-alert tone="success">{{ 'reset.request.messages.success' | translate }}</hum-alert>
          <div class="hum-auth__links">
            <a routerLink="/login">{{ 'login.title' | translate }}</a>
          </div>
        } @else {
          <p class="text-muted" style="font-size:var(--text-base)">{{ 'reset.request.messages.info' | translate }}</p>
          <form [formGroup]="form" (ngSubmit)="submit()" novalidate style="display:grid;gap:var(--space-4)">
            <hum-form-field
              [label]="'global.form.email.label' | translate"
              controlId="reset-email"
              [error]="invalid() ? ('global.messages.validate.email.invalid' | translate) : undefined"
            >
              <hum-input
                inputId="reset-email"
                type="email"
                formControlName="email"
                [placeholder]="'global.form.email.placeholder' | translate"
                [invalid]="invalid()"
              />
            </hum-form-field>
            <hum-button type="submit" variant="primary" [block]="true" [loading]="loading()" [disabled]="form.invalid">{{
              'reset.request.form.button' | translate
            }}</hum-button>
          </form>
          <div class="hum-auth__links">
            <a routerLink="/login">{{ 'login.title' | translate }}</a>
          </div>
        }
      </div>
    </div>
  `,
})
export default class PasswordResetRequestComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(AuthPublicService);

  protected readonly loading = signal(false);
  protected readonly success = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.minLength(5), Validators.maxLength(254)]],
  });

  protected invalid(): boolean {
    const c = this.form.controls.email;
    return c.invalid && (c.dirty || c.touched);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.api.requestPasswordReset(this.form.getRawValue().email).subscribe({
      // Always report success — don't disclose whether the email is registered.
      next: () => {
        this.success.set(true);
        this.loading.set(false);
      },
      error: () => {
        this.success.set(true);
        this.loading.set(false);
      },
    });
  }
}
