import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';

import { LEGAL } from './legal-info';
import { LegalNavComponent } from './legal-nav.component';
import { LegalRetentionService } from './legal-retention.service';

/** Public Terms of Service: the agreement a workspace accepts when it signs up. */
@Component({
  selector: 'hum-terms',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'hum-auth-screen hum-auth-screen--wide hum-legal' },
  imports: [DatePipe, RouterLink, LegalNavComponent],
  templateUrl: './terms.component.html',
})
export default class TermsComponent {
  protected readonly legal = LEGAL;
  /** The retention period, as set in the platform console. */
  protected readonly retention = inject(LegalRetentionService).retention;
}
