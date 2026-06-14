import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { AlertComponent, ButtonComponent, CheckboxComponent, FormFieldComponent, InputComponent } from 'app/shared/ui';

import { LoginService } from './login.service';

/**
 * Sign-in screen. Public route (outside the shell). Delegates the transport to
 * `LoginService` (form-encoded `api/authentication` via `AuthServerProvider`)
 * and lets `AccountService.navigateToStoredUrl()` handle the post-login
 * redirect to the intended URL; only falls back to home when none was stored.
 */
@Component({
  selector: 'hum-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslatePipe,
    AlertComponent,
    ButtonComponent,
    InputComponent,
    CheckboxComponent,
    FormFieldComponent,
  ],
  template: `
    <div class="hum-auth">
      <div class="hum-auth__panel">
        <div class="hum-auth__brand">humano<span class="hum-side__dot" aria-hidden="true"></span></div>
        <h1 class="hum-auth__title">{{ 'login.title' | translate }}</h1>

        @if (authError()) {
          <hum-alert tone="danger"><span [innerHTML]="'login.messages.error.authentication' | translate"></span></hum-alert>
        }

        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <hum-form-field [label]="'global.form.username.label' | translate" controlId="login-username">
            <hum-input
              inputId="login-username"
              formControlName="username"
              [placeholder]="'global.form.username.placeholder' | translate"
              [invalid]="invalid('username')"
            />
          </hum-form-field>

          <hum-form-field [label]="'login.form.password' | translate" controlId="login-password">
            <hum-input
              inputId="login-password"
              type="password"
              formControlName="password"
              [placeholder]="'login.form.password.placeholder' | translate"
              [invalid]="invalid('password')"
            />
          </hum-form-field>

          <label class="hum-auth__remember">
            <hum-checkbox formControlName="rememberMe" />
            <span>{{ 'login.form.rememberme' | translate }}</span>
          </label>

          <hum-button type="submit" variant="primary" [block]="true" [loading]="loading()" [disabled]="form.invalid">
            {{ 'login.form.button' | translate }}
          </hum-button>
        </form>

        <div class="hum-auth__links">
          <a routerLink="/account/reset/request">{{ 'login.password.forgot' | translate }}</a>
          <a routerLink="/register">{{ 'global.menu.account.register' | translate }}</a>
        </div>
      </div>
    </div>
  `,
})
export default class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly loginService = inject(LoginService);
  private readonly router = inject(Router);

  protected readonly loading = signal(false);
  protected readonly authError = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    username: ['', [Validators.required]],
    password: ['', [Validators.required]],
    rememberMe: [false],
  });

  protected invalid(control: 'username' | 'password'): boolean {
    const c = this.form.controls[control];
    return c.invalid && (c.dirty || c.touched);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.authError.set(false);
    const { username, password, rememberMe } = this.form.getRawValue();
    this.loginService.login({ username, password, rememberMe }).subscribe({
      next: () => {
        this.loading.set(false);
        // AccountService.navigateToStoredUrl() redirects to the intended URL when
        // the guard stored one; otherwise land on home.
        if (!this.router.getCurrentNavigation()) {
          void this.router.navigate(['']);
        }
      },
      error: () => {
        this.loading.set(false);
        this.authError.set(true);
      },
    });
  }
}
