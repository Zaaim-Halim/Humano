// Platform persona — tenant ops (superadmin) + billing + tenant self-context.

// Enums
export { TenantStatus } from './models/enums/tenant-status.enum';
export { InvoiceStatus } from './models/enums/invoice-status.enum';
export { SubscriptionStatus } from './models/enums/subscription-status.enum';
export { BillingCycle } from './models/enums/billing-cycle.enum';
export { SubscriptionType } from './models/enums/subscription-type.enum';

// Tenant (superadmin)
export { TenantService } from './services/tenant.service';
export type { Tenant, TenantPoolStats, TenantDetail } from './models/tenant.model';

// Tenant self-context (/api/tenant)
export { CurrentTenantService } from './services/current-tenant.service';
export type { UpdateCurrentTenantRequest } from './models/current-tenant.model';

// Billing
export { InvoiceService } from './services/invoice.service';
export type { Invoice } from './models/invoice.model';

export { SubscriptionPlanService } from './services/subscription-plan.service';
export type { SubscriptionPlan, CreateSubscriptionPlanRequest, UpdateSubscriptionPlanRequest } from './models/subscription-plan.model';

export { SubscriptionService } from './services/subscription.service';
export type { Subscription, UpdateSubscriptionRequest } from './models/subscription.model';

// Self-service billing
export { MeBillingService } from './services/me-billing.service';

// Owner console: cross-customer billing (reads + all billing writes), KPIs and team
export { PlatformBillingService } from './services/platform-billing.service';
export { PlatformConsoleService } from './services/platform-console.service';
export { PlatformDemoService } from './services/platform-demo.service';
export type * from './models/demo.model';
export * from './models/platform.model';
