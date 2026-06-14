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
  templateUrl: './password-reset-request.component.html',
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
