import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { ButtonComponent, EmptyStateComponent } from 'app/shared/ui';

/**
 * Catch-all for unknown URLs. Rendered in-shell (behind the auth guard), so a signed-in user
 * keeps their navigation and an anonymous visitor is sent to sign in first.
 */
@Component({
  selector: 'hum-not-found',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EmptyStateComponent, ButtonComponent, RouterLink, TranslatePipe],
  template: `
    <div class="hum-page">
      <hum-empty-state icon="compass" [title]="'error.notFound.title' | translate" [description]="'error.notFound.description' | translate">
        <hum-button hum-empty-action routerLink="/dashboard">{{ 'error.notFound.home' | translate }}</hum-button>
      </hum-empty-state>
    </div>
  `,
})
export default class NotFoundComponent {}
