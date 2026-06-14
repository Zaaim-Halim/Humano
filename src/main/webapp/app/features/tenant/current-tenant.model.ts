/**
 * Body for `PUT /api/tenant/me` (self-service tenant edit). All fields optional.
 * The response is a `Tenant` (same `TenantResponse` shape as the platform list).
 */
export interface UpdateCurrentTenantRequest {
  name?: string;
  domain?: string;
  subdomain?: string;
  logo?: string;
  /** IANA/Java TimeZone id. */
  timezone?: string;
  bookingPolicies?: string;
  hrPolicies?: string;
}
