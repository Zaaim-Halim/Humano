import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { LEGAL, SUBPROCESSORS } from './legal-info';
import { LegalNavComponent } from './legal-nav.component';

/**
 * Public Data Processing Addendum (GDPR art. 28): the terms on which Humano processes the HR and
 * payroll data in a customer's workspace, with the security measures and sub-processors as annexes.
 */
@Component({
  selector: 'hum-data-protection',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'hum-auth-screen hum-auth-screen--wide hum-legal' },
  imports: [RouterLink, LegalNavComponent],
  templateUrl: './data-protection.component.html',
})
export default class DataProtectionComponent {
  protected readonly legal = LEGAL;
  protected readonly subprocessors = SUBPROCESSORS;
}
