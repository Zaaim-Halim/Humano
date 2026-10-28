import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

import { LEGAL } from './legal-info';

/** How long a workspace is kept after its subscription ends; `upcoming` is a shorter period already announced. */
export interface Retention {
  days: number;
  warningDays: number;
  upcoming: { effectiveFrom: string; days: number; warningDays: number } | null;
}

/**
 * The workspace retention period the Terms, Privacy Policy, DPA, website and app quote — set by the owner
 * in the platform console (`GET /api/public/legal/retention`, no sign-in). Fetched once, on first use;
 * until then (or if it fails) the defaults in {@link LEGAL}.
 */
@Injectable({ providedIn: 'root' })
export class LegalRetentionService {
  private readonly http = inject(HttpClient);
  private readonly url = inject(ApplicationConfigService).getEndpointFor('api/public/legal/retention');

  readonly retention = signal<Retention>({ days: LEGAL.deletionDays, warningDays: LEGAL.deletionWarningDays, upcoming: null });

  constructor() {
    this.http.get<Retention>(this.url).subscribe({ next: r => this.retention.set(r), error: () => undefined });
  }
}
