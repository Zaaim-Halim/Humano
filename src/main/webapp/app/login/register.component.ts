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
  templateUrl: './register.component.html',
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
