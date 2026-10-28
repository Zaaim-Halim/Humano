import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { LEGAL_UPDATED } from './legal-info';

/** The tabs across the top of each legal page, and the date they were last changed. */
@Component({
  selector: 'hum-legal-nav',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, TranslatePipe],
  template: `
    <nav class="hum-legal__nav" [attr.aria-label]="'legal.nav' | translate">
      @for (doc of docs; track doc.path) {
        <a class="hum-legal__tab" [routerLink]="doc.path" routerLinkActive="is-active" ariaCurrentWhenActive="page">{{
          doc.label | translate
        }}</a>
      }
    </nav>
    <p class="hum-legal__updated">{{ 'legal.updated' | translate: { date: updated } }}</p>
  `,
})
export class LegalNavComponent {
  protected readonly docs = [
    { path: '/legal/terms', label: 'legal.terms.title' },
    { path: '/legal/privacy', label: 'legal.privacy.title' },
    { path: '/legal/data-protection', label: 'legal.dpa.title' },
  ];
  protected readonly updated = LEGAL_UPDATED;
}
