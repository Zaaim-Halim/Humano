import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { ButtonComponent, FormFieldComponent, InputComponent } from 'app/shared/ui';

import { AuthPublicService } from './auth-public.service';

/** Request a password reset email — `POST /api/account/reset-password/init`. */
@Component({
  selector: 'hum-password-reset-request',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, LucideAngularModule, ButtonComponent, InputComponent, FormFieldComponent],
  templateUrl: './password-reset-request.component.html',
})
export default class PasswordResetRequestComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(AuthPublicService);

  protected readonly loading = signal(false);
  protected readonly success = signal(false);
  /** Email we sent the link to — shown on the "check inbox" screen. */
  protected readonly sentEmail = signal('');

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
    const email = this.form.getRawValue().email;
    this.api.requestPasswordReset(email).subscribe({
      // Always report success — don't disclose whether the email is registered.
      next: () => this.done(email),
      error: () => this.done(email),
    });
  }

  /** Switch to the "check inbox" confirmation. */
  protected reset(): void {
    this.success.set(false);
  }

  private done(email: string): void {
    this.sentEmail.set(email);
    this.success.set(true);
    this.loading.set(false);
  }
}
