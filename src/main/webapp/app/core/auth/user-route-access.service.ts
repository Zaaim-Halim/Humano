import { inject, isDevMode } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot } from '@angular/router';
import { map } from 'rxjs/operators';

import { AccountService } from 'app/core/auth/account.service';
import { StateStorageService } from './state-storage.service';

export const UserRouteAccessService: CanActivateFn = (next: ActivatedRouteSnapshot, state: RouterStateSnapshot) => {
  const accountService = inject(AccountService);
  const router = inject(Router);
  const stateStorageService = inject(StateStorageService);
  return accountService.identity().pipe(
    map(account => {
      if (account) {
        const { authorities, permissions } = next.data;

        const authoritiesOk = !authorities || authorities.length === 0 || accountService.hasAnyAuthority(authorities);
        const permissionsOk = !permissions || permissions.length === 0 || accountService.hasAnyPermission(permissions);

        // A route may gate on authorities, permissions, or both; the user must satisfy
        // every gate that is present.
        if (authoritiesOk && permissionsOk) {
          return true;
        }

        if (isDevMode()) {
          console.error('User does not satisfy the required access:', { authorities, permissions });
        }
        router.navigate(['accessdenied']);
        return false;
      }

      stateStorageService.storeUrl(state.url);
      router.navigate(['/login']);
      return false;
    }),
  );
};
