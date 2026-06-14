import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { AlertComponent, ButtonComponent, FormFieldComponent, InputComponent } from 'app/shared/ui';

import { AuthPublicService } from './auth-public.service';

function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  return group.get('password')!.value === group.get('confirmPassword')!.value ? null : { dontmatch: true };
}

/** Public registration — `POST /api/register`. */
@Component({
  selector: 'hum-register',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, AlertComponent, ButtonComponent, InputComponent, FormFieldComponent],
  template: `
    <div class="hum-auth">
      <div class="hum-auth__panel">
        <div class="hum-auth__brand">humano<span class="hum-side__dot" aria-hidden="true"></span></div>
        <h1 class="hum-auth__title">{{ 'register.title' | translate }}</h1>

        @if (success()) {
          <hum-alert tone="success"><span [innerHTML]="'register.messages.success' | translate"></span></hum-alert>
          <div class="hum-auth__links">
            <a routerLink="/login">{{ 'login.title' | translate }}</a>
          </div>
        } @else {
          @if (errorKey(); as k) {
            <hum-alert tone="danger"><span [innerHTML]="k | translate"></span></hum-alert>
          }
          <form [formGroup]="form" (ngSubmit)="submit()" novalidate style="display:grid;gap:var(--space-4)">
            <hum-form-field
              [label]="'global.form.username.label' | translate"
              controlId="reg-login"
              [error]="invalid('login') ? ('register.messages.validate.login.required' | translate) : undefined"
            >
              <hum-input
                inputId="reg-login"
                formControlName="login"
                [placeholder]="'global.form.username.placeholder' | translate"
                [invalid]="invalid('login')"
              />
            </hum-form-field>

            <hum-form-field
              [label]="'global.form.email.label' | translate"
              controlId="reg-email"
              [error]="invalid('email') ? ('global.messages.validate.email.invalid' | translate) : undefined"
            >
              <hum-input
                inputId="reg-email"
                type="email"
                formControlName="email"
                [placeholder]="'global.form.email.placeholder' | translate"
                [invalid]="invalid('email')"
              />
            </hum-form-field>

            <hum-form-field [label]="'global.form.newpassword.label' | translate" controlId="reg-password">
              <hum-input
                inputId="reg-password"
                type="password"
                formControlName="password"
                [placeholder]="'global.form.newpassword.placeholder' | translate"
                [invalid]="invalid('password')"
              />
            </hum-form-field>

            <hum-form-field
              [label]="'global.form.confirmpassword.label' | translate"
              controlId="reg-confirm"
              [error]="mismatch() ? ('global.messages.error.dontmatch' | translate) : undefined"
            >
              <hum-input
                inputId="reg-confirm"
                type="password"
                formControlName="confirmPassword"
                [placeholder]="'global.form.confirmpassword.placeholder' | translate"
                [invalid]="mismatch()"
              />
            </hum-form-field>

            <hum-button type="submit" variant="primary" [block]="true" [loading]="loading()" [disabled]="form.invalid">{{
              'register.form.button' | translate
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
export default class RegisterComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(AuthPublicService);
  private readonly translate = inject(TranslateService);
  protected readonly router = inject(Router);

  protected readonly loading = signal(false);
  protected readonly success = signal(false);
  protected readonly errorKey = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group(
    {
      login: ['', [Validators.required, Validators.minLength(1), Validators.maxLength(50)]],
      email: ['', [Validators.required, Validators.email, Validators.minLength(5), Validators.maxLength(254)]],
      password: ['', [Validators.required, Validators.minLength(4), Validators.maxLength(50)]],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: passwordsMatch },
  );

  protected invalid(control: 'login' | 'email' | 'password'): boolean {
    const c = this.form.controls[control];
    return c.invalid && (c.dirty || c.touched);
  }

  protected mismatch(): boolean {
    const confirm = this.form.controls.confirmPassword;
    return this.form.hasError('dontmatch') && (confirm.dirty || confirm.touched);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.errorKey.set(null);
    const { login, email, password } = this.form.getRawValue();
    this.api.register({ login, email, password, langKey: this.translate.currentLang || 'en' }).subscribe({
      next: () => {
        this.success.set(true);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.errorKey.set(this.mapError(err));
        this.loading.set(false);
      },
    });
  }

  private mapError(err: HttpErrorResponse): string {
    const detail = String(
      (err.error as { detail?: string; title?: string })?.detail ?? (err.error as { title?: string })?.title ?? '',
    ).toLowerCase();
    if (err.status === 400 && detail.includes('login')) return 'register.messages.error.userexists';
    if (err.status === 400 && detail.includes('email')) return 'register.messages.error.emailexists';
    return 'register.messages.error.fail';
  }
}
