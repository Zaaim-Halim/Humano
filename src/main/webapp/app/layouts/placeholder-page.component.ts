import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { EmptyStateComponent, PageHeaderComponent } from 'app/shared/ui';

/**
 * Shared placeholder for nav targets whose persona surface (Phase 7) is not yet
 * built. Keeps navigation, active state, `PageHeader` and the empty state real
 * without fabricating data — each route swaps this out for its real component.
 * `titleKey` is the i18n key bound from route `data` via `withComponentInputBinding()`.
 */
@Component({
  selector: 'hum-placeholder-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PageHeaderComponent, EmptyStateComponent, TranslatePipe],
  template: `
    <div class="hum-page">
      <hum-page-header [title]="titleKey() | translate" [subtitle]="'humano.page.placeholder.subtitle' | translate" />
      <hum-empty-state
        icon="hammer"
        [title]="'humano.page.placeholder.title' | translate"
        [description]="'humano.page.placeholder.description' | translate: { title: titleKey() | translate }"
      />
    </div>
  `,
})
export default class PlaceholderPageComponent {
  /** i18n key for the page title, supplied via route `data`. */
  readonly titleKey = input('humano.nav.dashboard');
}
