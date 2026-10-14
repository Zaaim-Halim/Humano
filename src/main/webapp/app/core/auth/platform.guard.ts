import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';

import { AccountService } from './account.service';

/**
 * The owner console (`/platform`) and the company workspace are separate worlds: platform
 * accounts only ever see the console, company accounts never do. Both guards wait for the
 * account so the decision never runs on a not-yet-loaded identity.
 */
export const platformGuard: CanActivateFn = () => {
  const account = inject(AccountService);
  const router = inject(Router);
  return account.identity().pipe(map(a => (a && account.isPlatformUser() ? true : router.createUrlTree(['/']))));
};

/** Company workspace routes: platform accounts are sent to their console. */
export const workspaceGuard: CanActivateFn = () => {
  const account = inject(AccountService);
  const router = inject(Router);
  return account.identity().pipe(map(a => (a && account.isPlatformUser() ? router.createUrlTree(['/platform']) : true)));
};
