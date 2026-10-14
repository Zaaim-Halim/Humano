import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { AvatarComponent, ButtonComponent } from 'app/shared/ui';

import { EmployeeProfile } from '../models/employee.model';
import { ageFrom, tenureSince } from './profile-format';

/**
 * Employee overview cards: job & organization (manager linked), employment timeline, personal,
 * contact, and government ids masked to their last four characters (with a Show / Hide toggle
 * when `revealable`). Used by the employee page
 * and My profile.
 */
@Component({
  selector: 'hum-employee-overview',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, TranslatePipe, LucideAngularModule, AvatarComponent, ButtonComponent],
  templateUrl: './employee-overview.component.html',
})
export class EmployeeOverviewComponent {
  readonly employee = input.required<EmployeeProfile>();
  /** Offer a Show / Hide toggle on the government ids (the person's own profile only). */
  readonly revealable = input(false);

  private readonly translate = inject(TranslateService);

  protected readonly age = ageFrom;
  protected readonly revealed = signal(false);

  protected since(isoDate: string | null): string {
    return tenureSince(isoDate, this.translate);
  }

  /** Government identifiers are shown masked to their last four characters. */
  protected mask(value: string | null | undefined): string {
    if (!value) return '—';
    return this.revealed() || value.length <= 4 ? value : `•••• ${value.slice(-4)}`;
  }
}
