import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { ButtonComponent, EmptyStateComponent } from 'app/shared/ui';

/**
 * Access-denied page — where `UserRouteAccessService` lands an authenticated
 * user who lacks the route's authority (e.g. a non-admin hitting an
 * ADMIN-gated route). Rendered in-shell since the user is signed in.
 */
@Component({
  selector: 'hum-access-denied',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EmptyStateComponent, ButtonComponent, RouterLink, TranslatePipe],
  template: `
    <div class="hum-page">
      <hum-empty-state
        icon="shield-x"
        [title]="'error.forbidden.title' | translate"
        [description]="'error.forbidden.description' | translate"
      >
        <hum-button hum-empty-action variant="outline" routerLink="/dashboard">{{ 'error.notFound.home' | translate }}</hum-button>
      </hum-empty-state>
    </div>
  `,
})
export default class AccessDeniedComponent {}
