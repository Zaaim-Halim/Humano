import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';

import { Permission } from 'app/config/permission.constants';

import { AccountService } from './account.service';

/**
 * The dashboard is an organization overview (directory, payroll, team data); people without
 * directory access are sent to My space, their personal home, instead of a page of errors.
 * Waits for the account so the decision never runs on a not-yet-loaded identity.
 */
export const dashboardGuard: CanActivateFn = () => {
  const account = inject(AccountService);
  const router = inject(Router);
  return account.identity().pipe(
    map(a => {
      if (a && account.isPlatformUser()) return router.createUrlTree(['/platform']);
      return !a || a.permissions?.includes(Permission.READ_EMPLOYEE) ? true : router.createUrlTree(['/me']);
    }),
  );
};
