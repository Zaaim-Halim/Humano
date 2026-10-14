import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { Authority } from 'app/config/authority.constants';
import { AccountService } from 'app/core/auth/account.service';
import { PageHeaderComponent } from 'app/shared/ui';

import { AccessSettingsComponent } from './sections/access-settings.component';
import { CompanySettingsComponent } from './sections/company-settings.component';
import { ListsSettingsComponent } from './sections/lists-settings.component';
import { PayrollSettingsComponent } from './sections/payroll-settings.component';

type SectionId = 'company' | 'payroll' | 'lists' | 'access';

interface Section {
  id: SectionId;
  icon: string;
  /** Hidden unless the viewer is an administrator. */
  adminOnly?: boolean;
}

const SECTIONS: Section[] = [
  { id: 'company', icon: 'building-2' },
  { id: 'payroll', icon: 'wallet' },
  { id: 'lists', icon: 'list' },
  { id: 'access', icon: 'shield-check', adminOnly: true },
];

/**
 * Settings — `/settings/:section`, everything an administrator configures for the company:
 *
 * - Company: name, website, workspace address, timezone and currency.
 * - Payroll & time: working hours, overtime, pay basis, payroll calendars.
 * - Lists: the choices used across forms (employment types, levels, grades, categories,
 *   marital statuses, termination reasons).
 * - Users & access (administrators): accounts, roles, and what each role may do.
 *
 * Each section is its own component and loads its own data; the nav deep-links each one.
 */
@Component({
  selector: 'hum-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    TranslatePipe,
    LucideAngularModule,
    PageHeaderComponent,
    CompanySettingsComponent,
    PayrollSettingsComponent,
    ListsSettingsComponent,
    AccessSettingsComponent,
  ],
  templateUrl: './settings.component.html',
})
export default class SettingsComponent {
  /** Route param (`/settings/:section`); defaults to the company section. */
  readonly section = input<string | undefined>();

  private readonly isAdmin = inject(AccountService).hasAnyAuthority(Authority.ADMIN);

  protected readonly sections = SECTIONS.filter(s => !s.adminOnly || this.isAdmin);
  protected readonly active = computed<SectionId>(() => {
    const id = this.section() as SectionId | undefined;
    return id && this.sections.some(s => s.id === id) ? id : 'company';
  });
}
