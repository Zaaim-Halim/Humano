import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { LEGAL, SUBPROCESSORS } from './legal-info';
import { LegalNavComponent } from './legal-nav.component';
import { LegalRetentionService } from './legal-retention.service';

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
  /** The retention period, as set in the platform console. */
  protected readonly retention = inject(LegalRetentionService).retention;
  protected readonly subprocessors = SUBPROCESSORS;
}
