import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { LEGAL, SUBPROCESSORS } from './legal-info';
import { LegalNavComponent } from './legal-nav.component';

/** Public Privacy Policy: the personal data Humano itself is responsible for, and people's rights over it. */
@Component({
  selector: 'hum-privacy',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'hum-auth-screen hum-auth-screen--wide hum-legal' },
  imports: [RouterLink, LegalNavComponent],
  templateUrl: './privacy.component.html',
})
export default class PrivacyComponent {
  protected readonly legal = LEGAL;
  protected readonly subprocessors = SUBPROCESSORS;
}
