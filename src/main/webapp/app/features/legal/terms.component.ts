import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { LEGAL } from './legal-info';
import { LegalNavComponent } from './legal-nav.component';

/** Public Terms of Service: the agreement a workspace accepts when it signs up. */
@Component({
  selector: 'hum-terms',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'hum-auth-screen hum-auth-screen--wide hum-legal' },
  imports: [RouterLink, LegalNavComponent],
  templateUrl: './terms.component.html',
})
export default class TermsComponent {
  protected readonly legal = LEGAL;
}
