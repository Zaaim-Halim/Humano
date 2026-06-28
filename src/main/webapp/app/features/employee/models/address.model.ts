import { AuditFields } from 'app/core/api';

/** Address — `/api/hr/addresses`. */
export interface Address extends AuditFields {
  id: string;
  employeeId: string | null;
  type: string | null;
  street: string | null;
  building: string | null;
  apartment: string | null;
  city: string | null;
  state: string | null;
  postalCode: string | null;
  countryId: string | null;
  primary: boolean;
}

export interface CreateAddressRequest {
  /** Required. */
  employeeId: string;
  type?: string;
  street?: string;
  building?: string;
  apartment?: string;
  city?: string;
  state?: string;
  postalCode?: string;
  countryId?: string;
  primary?: boolean;
}

export interface UpdateAddressRequest {
  type?: string;
  street?: string;
  building?: string;
  apartment?: string;
  city?: string;
  state?: string;
  postalCode?: string;
  countryId?: string;
  primary?: boolean;
}
