/**
 * Who stands behind the public legal pages (Terms, Privacy Policy, Data Processing Addendum).
 * Values in [brackets] are placeholders and render as such until filled in: replace them before
 * launch, keep the entity in step with `humano.billing.issuer.*` (it is printed on every invoice),
 * and bump {@link LEGAL_UPDATED} whenever the wording of a page changes.
 */
export const LEGAL = {
  product: 'Humano',
  entity: '[Legal entity name]',
  address: '[Registered office address]',
  registration: '[Company registration number]',
  taxId: '[Tax / VAT number]',
  contactEmail: '[legal@your-domain]',
  privacyEmail: '[privacy@your-domain]',
  securityEmail: '[security@your-domain]',
  /** Representative / DPO, where the law requires one; otherwise the privacy contact. */
  dpo: '[Data protection officer or representative, if appointed]',
  governingLaw: '[Country / state whose law governs the Terms]',
  courts: '[City and country of the competent courts]',
  supervisoryAuthority: '[Lead data protection authority]',
  hostingRegion: '[Hosting region, e.g. European Union]',
  /**
   * Days a workspace's data is kept after its subscription ends (lapsed trial or cancellation) before it is
   * deleted. Must match `humano.retention.workspace-deletion.days`, which the nightly deletion job uses.
   */
  deletionDays: 90,
  /** Days before deletion the workspace admin is emailed. Must match `humano.retention.workspace-deletion.warning-days`. */
  deletionWarningDays: 7,
  /** How long backups take to roll over once data is deleted from the live system. */
  backupDays: '[N]',
  /** Months an untouched demo request that never became a customer is kept. Must match `humano.retention.demo-requests.months`. */
  demoRetentionMonths: 24,
  /** Months our own server and application logs are kept: set by the deployment's logging, not by the app. */
  logRetentionMonths: '[N]',
  /** Written notice of new sub-processors before they start processing customer data. */
  subprocessorNoticeDays: 30,
  /** Notice of a personal data breach affecting customer data. */
  breachNoticeHours: 48,
} as const;

/** Last change to any of the legal pages; shown at the top of each. */
export const LEGAL_UPDATED = '28 October 2026';

/**
 * The version a sign-up accepts, sent with the form and recorded on the workspace. Must match the
 * server's `Constants.TERMS_VERSION`, which refuses any other: bump both (and {@link LEGAL_UPDATED})
 * together whenever the Terms or the Data Processing Addendum change.
 */
export const LEGAL_VERSION = '2026-10-28';

export interface Subprocessor {
  name: string;
  purpose: string;
  data: string;
  location: string;
}

/** Third parties that process customer or visitor data on our behalf. Keep in step with the deployment. */
export const SUBPROCESSORS: Subprocessor[] = [
  {
    name: '[Hosting / cloud provider]',
    purpose: 'Application servers, databases and file storage',
    data: 'All customer data held in the service',
    location: '[Region]',
  },
  {
    name: '[Email delivery provider]',
    purpose: 'Sign-up, sign-in, password reset and notification emails',
    data: 'Name, email address, message content',
    location: '[Region]',
  },
  {
    name: 'Stripe, Inc. / Stripe Payments Europe, Ltd.',
    purpose: 'Subscription billing and card payments',
    data: 'Billing contact, company details, payment card data (held by Stripe, never by us)',
    location: 'United States / European Union',
  },
];
