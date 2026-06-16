import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { AlertComponent, ButtonComponent, FormFieldComponent, InputComponent } from 'app/shared/ui';

import { AuthPublicService } from './auth-public.service';
import { PasswordStrengthComponent } from './password-strength.component';

/**
 * Public registration — `POST /api/register`. The work email doubles as the
 * JHipster `login` (the register endpoint requires one); first/last name map to
 * the DTO's optional fields. There is no company field on the contract.
 */
@Component({
  selector: 'hum-register',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    ButtonComponent,
    InputComponent,
    FormFieldComponent,
    PasswordStrengthComponent,
  ],
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
  protected readonly showPassword = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.maxLength(50)]],
    lastName: ['', [Validators.maxLength(50)]],
    email: ['', [Validators.required, Validators.email, Validators.minLength(5), Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(50)]],
  });

  /** Live password value for the strength meter (reactive under zoneless CD). */
  protected readonly passwordValue = toSignal(this.form.controls.password.valueChanges, { initialValue: '' });

  protected invalid(control: 'firstName' | 'lastName' | 'email' | 'password'): boolean {
    const c = this.form.controls[control];
    return c.invalid && (c.dirty || c.touched);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.errorKey.set(null);
    const { firstName, lastName, email, password } = this.form.getRawValue();
    this.api.register({ login: email, firstName, lastName, email, password, langKey: this.translate.currentLang || 'en' }).subscribe({
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
