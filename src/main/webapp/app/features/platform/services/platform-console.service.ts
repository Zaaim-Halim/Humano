import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

import { PlatformOverview, PlatformRole, TeamMember } from '../models/platform.model';

/** Owner console data: KPIs (`/api/platform/metrics`) and the platform team (`/api/platform/team`). */
@Injectable({ providedIn: 'root' })
export class PlatformConsoleService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(ApplicationConfigService);
  private currencyLoaded = false;

  /** The currency the platform bills in (server setting `humano.billing.currency`). */
  readonly currency = signal('USD');

  overview(): Observable<PlatformOverview> {
    return this.http.get<PlatformOverview>(this.config.getEndpointFor('api/platform/metrics/overview')).pipe(
      tap(o => {
        this.currency.set(o.currency);
        this.currencyLoaded = true;
      }),
    );
  }

  /** Loads the billing currency once, for pages other than the overview. */
  ensureCurrency(): void {
    if (this.currencyLoaded) return;
    this.currencyLoaded = true;
    this.overview().subscribe({ error: () => (this.currencyLoaded = false) });
  }

  team(): Observable<TeamMember[]> {
    return this.http.get<TeamMember[]>(this.config.getEndpointFor('api/platform/team'));
  }

  invite(body: { email: string; firstName?: string; lastName?: string; role: PlatformRole }): Observable<TeamMember> {
    return this.http.post<TeamMember>(this.config.getEndpointFor('api/platform/team'), body);
  }

  updateMember(id: string, body: { role?: PlatformRole; activated?: boolean }): Observable<TeamMember> {
    return this.http.patch<TeamMember>(this.config.getEndpointFor(`api/platform/team/${encodeURIComponent(id)}`), body);
  }

  resendInvite(id: string): Observable<void> {
    return this.http.post<void>(this.config.getEndpointFor(`api/platform/team/${encodeURIComponent(id)}/resend-invite`), {});
  }

  removeMember(id: string): Observable<void> {
    return this.http.delete<void>(this.config.getEndpointFor(`api/platform/team/${encodeURIComponent(id)}`));
  }
}
