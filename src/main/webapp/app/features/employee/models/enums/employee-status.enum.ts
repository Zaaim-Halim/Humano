/** `com.humano.domain.enumeration.hr.EmployeeStatus`. */
export enum EmployeeStatus {
  ACTIVE = 'ACTIVE',
  /** Set automatically while the employee's onboarding runs; counts as employed. */
  ONBOARDING = 'ONBOARDING',
  /** Set automatically while the employee is leaving; counts as employed until their last day. */
  OFFBOARDING = 'OFFBOARDING',
  INACTIVE = 'INACTIVE',
  TERMINATED = 'TERMINATED',
}
