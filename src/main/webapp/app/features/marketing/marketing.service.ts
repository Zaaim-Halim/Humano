import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

/** `GET /api/public/pricing` — the plans the platform lists publicly, compared on the feature catalog. */
export interface PublicPricing {
  currency: string;
  plans: PublicPlan[];
  features: PublicFeature[];
}

export interface PublicPlan {
  id: string;
  type: string;
  name: string;
  description: string | null;
  monthlyPrice: number;
  featured: boolean;
  items: { featureId: string; included: boolean; limitValue: number | null; note: string | null }[];
}

export interface PublicFeature {
  id: string;
  name: string;
  description: string | null;
  category: string | null;
  limitType: 'BOOLEAN' | 'QUANTITY';
  unit: string | null;
}

/** `POST /api/public/demo-requests`. `website` is a honeypot: hidden from people, left empty. */
export interface DemoRequestForm {
  fullName: string;
  workEmail: string;
  companyName: string;
  companySize?: string;
  country?: string;
  phone?: string;
  message?: string;
  website?: string;
}

/** The marketing site's two server calls; both work signed out. */
@Injectable({ providedIn: 'root' })
export class MarketingService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(ApplicationConfigService);

  pricing(): Observable<PublicPricing> {
    return this.http.get<PublicPricing>(this.config.getEndpointFor('api/public/pricing'));
  }

  requestDemo(form: DemoRequestForm): Observable<void> {
    return this.http.post<void>(this.config.getEndpointFor('api/public/demo-requests'), form);
  }
}
