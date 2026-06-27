import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { mergeMap } from 'rxjs/operators';

import { Account } from 'app/core/auth/account.model';
import { AccountService } from 'app/core/auth/account.service';
import { AuthServerProvider } from 'app/core/auth/auth-session.service';
import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { TenantContextService } from 'app/core/tenant/tenant-context.service';
import { Login } from './login.model';

@Injectable({ providedIn: 'root' })
export class LoginService {
  private readonly applicationConfigService = inject(ApplicationConfigService);
  private readonly accountService = inject(AccountService);
  private readonly authServerProvider = inject(AuthServerProvider);
  private readonly tenantContext = inject(TenantContextService);

  login(credentials: Login): Observable<Account | null> {
    return this.authServerProvider.login(credentials).pipe(mergeMap(() => this.accountService.identity(true)));
  }

  logoutUrl(): string {
    return this.applicationConfigService.getEndpointFor('api/logout');
  }

  logoutInClient(): void {
    this.tenantContext.clear();
    this.accountService.authenticate(null);
  }

  logout(): void {
    this.authServerProvider.logout().subscribe({
      complete: () => {
        this.tenantContext.clear();
        this.accountService.authenticate(null);
      },
    });
  }
}
