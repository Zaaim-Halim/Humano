import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { EmptyStateComponent, PageHeaderComponent } from 'app/shared/ui';

/**
 * Access-denied page — where `UserRouteAccessService` lands an authenticated
 * user who lacks the route's authority (e.g. a non-admin hitting an
 * ADMIN-gated route). Rendered in-shell since the user is signed in.
 */
@Component({
  selector: 'hum-access-denied',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PageHeaderComponent, EmptyStateComponent, TranslatePipe],
  template: `
    <div class="hum-page">
      <hum-page-header [title]="'error.title' | translate" />
      <hum-empty-state icon="shield-x" [title]="'error.http.403' | translate" [description]="'error.http.403' | translate" />
    </div>
  `,
})
export default class AccessDeniedComponent {}
