import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

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
  code: string;
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

/** `POST /api/public/signup` — the "Start free trial" form. `website` is a honeypot, left empty. */
export interface SignupForm {
  email: string;
  companyName: string;
  subdomain: string;
  country: string;
  timezone?: string;
  planId: string;
  /** The statutory payroll template the workspace starts with; omitted when the visitor chose none. */
  payrollTemplateId?: string;
  /** The Terms of Service and Data Processing Addendum were ticked, in this version (`LEGAL_VERSION`). */
  acceptedTerms: boolean;
  termsVersion: string;
  website?: string;
}

/** `GET /api/public/payroll-templates?country=` — a template a new workspace in that country can start with. */
export interface PublicPayrollTemplate {
  id: string;
  name: string;
  version: string;
  status: 'OFFICIAL_VECTORS' | 'LAW_DERIVED' | 'DRAFT';
  country: string;
  currency: string;
  validFrom: string;
  validTo: string;
  payFrequencies: string[];
  limitations: string[];
}

/** `GET /api/public/signup/subdomains/{subdomain}`. */
export interface SubdomainCheck {
  subdomain: string;
  available: boolean;
  reason: 'invalid' | 'reserved' | 'taken' | null;
}

/** What the emailed link shows: the sign-up and how its workspace setup is going. */
export interface SignupView {
  stage: 'CONFIRM' | 'SETTING_UP' | 'READY' | 'FAILED';
  email: string;
  companyName: string;
  subdomain: string;
  planName: string | null;
  trialDays: number;
}

/** The marketing site's server calls; all work signed out. */
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

  /** Statutory payroll templates for a country, the one valid longest first. */
  payrollTemplates(country: string): Observable<PublicPayrollTemplate[]> {
    return this.http.get<PublicPayrollTemplate[]>(this.config.getEndpointFor('api/public/payroll-templates'), { params: { country } });
  }

  checkSubdomain(subdomain: string): Observable<SubdomainCheck> {
    return this.http.get<SubdomainCheck>(this.config.getEndpointFor(`api/public/signup/subdomains/${encodeURIComponent(subdomain)}`));
  }

  /** Emails the confirmation link (202 whether or not the address already has a workspace). */
  startSignup(form: SignupForm): Observable<void> {
    return this.http.post(this.config.getEndpointFor('api/public/signup'), form).pipe(map((): void => undefined));
  }

  /** The emailed link's sign-up; polled while the workspace is set up. The token goes in the body, not the URL. */
  signupLink(token: string): Observable<SignupView> {
    return this.http.post<SignupView>(this.config.getEndpointFor('api/public/signup/link'), { token });
  }

  /** Confirms the email with the admin's name and password, and starts (or retries) the setup. */
  completeSignup(body: { token: string; firstName: string; lastName: string; password: string }): Observable<SignupView> {
    return this.http.post<SignupView>(this.config.getEndpointFor('api/public/signup/complete'), body);
  }
}
