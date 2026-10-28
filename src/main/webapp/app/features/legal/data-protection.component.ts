import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ButtonComponent } from 'app/shared/ui';

import { LEGAL, LEGAL_UPDATED, SUBPROCESSORS } from './legal-info';
import { LegalNavComponent } from './legal-nav.component';

/**
 * Public Data Processing Addendum (GDPR art. 28): the terms on which Humano processes the HR and
 * payroll data in a customer's workspace, with the security measures and sub-processors as annexes.
 * It applies by accepting the Terms; a customer that needs a signed copy prints this page (the print
 * styles drop the page chrome), signs the block at the end and sends it to be countersigned — one
 * text for both, so the signed copy can never differ from the published one.
 */
@Component({
  selector: 'hum-data-protection',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'hum-auth-screen hum-auth-screen--wide hum-legal' },
  imports: [RouterLink, LegalNavComponent, ButtonComponent],
  templateUrl: './data-protection.component.html',
})
export default class DataProtectionComponent {
  protected readonly legal = LEGAL;
  protected readonly subprocessors = SUBPROCESSORS;
  protected readonly updated = LEGAL_UPDATED;

  protected print(): void {
    window.print();
  }
}
