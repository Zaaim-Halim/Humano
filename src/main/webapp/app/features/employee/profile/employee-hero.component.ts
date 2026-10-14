import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { normalizeHttpError } from 'app/core/api';
import { AvatarComponent, BadgeComponent, ToastService } from 'app/shared/ui';

import { EmployeeProfile } from '../models/employee.model';
import { EmployeeService } from '../services/employee.service';
import { employeeFullName, tenureSince } from './profile-format';

const PHOTO_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const PHOTO_MAX_BYTES = 2 * 1024 * 1024;

/**
 * Employee identity header: banner, photo (with change / remove when `canChangePhoto`), name,
 * status, title and icon facts (department, location, email, phone, number, tenure). Page
 * actions are projected on the right. Emits `imageChanged` with the new photo URL (or null).
 * Used by the employee page and My profile.
 */
@Component({
  selector: 'hum-employee-hero',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, LucideAngularModule, AvatarComponent, BadgeComponent],
  templateUrl: './employee-hero.component.html',
})
export class EmployeeHeroComponent {
  readonly employee = input.required<EmployeeProfile>();
  readonly canChangePhoto = input(false);
  readonly imageChanged = output<string | null>();

  private readonly employeeService = inject(EmployeeService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);

  protected readonly photoBusy = signal(false);
  protected readonly fullName = employeeFullName;

  protected since(isoDate: string | null): string {
    return tenureSince(isoDate, this.translate);
  }

  protected onPhotoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    if (!PHOTO_TYPES.includes(file.type) || file.size > PHOTO_MAX_BYTES) {
      this.toast.danger(this.translate.instant('humano.employee360.photoInvalid'));
      return;
    }
    this.photoBusy.set(true);
    this.employeeService.uploadPhoto(this.employee().id, file).subscribe({
      next: res => this.done(res.imageUrl, 'humano.employee360.photoUpdated'),
      error: (err: unknown) => this.failed(err),
    });
  }

  protected removePhoto(): void {
    this.photoBusy.set(true);
    this.employeeService.removePhoto(this.employee().id).subscribe({
      next: () => this.done(null, 'humano.employee360.photoRemoved'),
      error: (err: unknown) => this.failed(err),
    });
  }

  private done(imageUrl: string | null, messageKey: string): void {
    this.photoBusy.set(false);
    this.imageChanged.emit(imageUrl);
    this.toast.success(this.translate.instant(messageKey));
  }

  private failed(err: unknown): void {
    this.photoBusy.set(false);
    this.toast.danger(normalizeHttpError(err));
  }
}
