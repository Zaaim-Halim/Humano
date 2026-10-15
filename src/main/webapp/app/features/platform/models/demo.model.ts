import { BillingCycle } from './enums/billing-cycle.enum';
import { TenantStatus } from './enums/tenant-status.enum';

export type DemoRequestStatus = 'NEW' | 'CONTACTED' | 'DEMO_CREATED' | 'DECLINED';
export type DemoSetupStatus = 'PROVISIONING' | 'SEEDING' | 'READY' | 'FAILED';

/** `GET /api/platform/demo-requests` — the public site's "Book a demo" form. */
export interface DemoRequest {
  id: string;
  fullName: string;
  workEmail: string;
  companyName: string;
  companySize: string | null;
  /** ISO 3166 alpha-2. */
  country: string | null;
  phone: string | null;
  message: string | null;
  status: DemoRequestStatus;
  notes: string | null;
  /** The demo workspace set up for it. */
  tenantId: string | null;
  tenantSubdomain: string | null;
  createdDate: string | null;
  lastModifiedDate: string | null;
}

/** `GET /api/platform/demos`. */
export interface DemoWorkspace {
  id: string;
  name: string;
  subdomain: string;
  country: string | null;
  status: TenantStatus;
  setup: DemoSetupStatus | null;
  expiresAt: string | null;
  contactName: string | null;
  contactEmail: string | null;
  planId: string | null;
  planName: string | null;
  createdDate: string | null;
}

/** Sign-in details of a demo: shown once, the password is not stored. */
export interface DemoCredentials {
  workspace: string;
  adminLogin: string;
  personaLogins: string[];
  password: string;
}

export interface DemoWorkspaceCreated {
  demo: DemoWorkspace;
  credentials: DemoCredentials;
}

export interface CreateDemoWorkspaceRequest {
  companyName: string;
  subdomain: string;
  country: string;
  timezone?: string;
  contactName?: string;
  contactEmail?: string;
  durationDays?: number;
  planId?: string;
  sampleData?: boolean;
  demoRequestId?: string;
}

export interface ConvertDemoRequest {
  planId: string;
  billingCycle: BillingCycle;
  trialDays?: number;
  couponCode?: string;
}

/** `POST /api/public/demo-requests` (anonymous). `website` is a honeypot — leave empty. */
export interface SubmitDemoRequest {
  fullName: string;
  workEmail: string;
  companyName: string;
  companySize?: string;
  country?: string;
  phone?: string;
  message?: string;
  website?: string;
}
