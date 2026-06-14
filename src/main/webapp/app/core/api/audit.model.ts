/**
 * Audit columns every persisted entity DTO carries (JHipster `AbstractAuditingEntity`).
 * Dates are ISO-8601 strings (the backend serialises `Instant`/`LocalDate` as
 * strings — Spring Boot disables write-dates-as-timestamps) — convert at the
 * view edge with `config/dayjs`, never type them as `Date`.
 */
export interface AuditFields {
  createdBy?: string;
  /** ISO-8601 instant. */
  createdDate?: string;
  lastModifiedBy?: string;
  /** ISO-8601 instant. */
  lastModifiedDate?: string;
}
