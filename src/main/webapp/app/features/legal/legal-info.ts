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
  /** How long a cancelled or paused workspace's data is kept before it is deleted. */
  deletionDays: '[N]',
  /** How long backups take to roll over once data is deleted from the live system. */
  backupDays: '[N]',
  /** How long demo requests that don't become customers, and security logs, are kept. */
  demoRetentionMonths: '[N]',
  logRetentionMonths: '[N]',
  /** Written notice of new sub-processors before they start processing customer data. */
  subprocessorNoticeDays: 30,
  /** Notice of a personal data breach affecting customer data. */
  breachNoticeHours: 48,
} as const;

/** Last change to any of the legal pages; shown at the top of each. */
export const LEGAL_UPDATED = '28 October 2026';

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
  {
    name: 'Google LLC (Google Fonts)',
    purpose: 'Delivery of the web fonts used by the site and the app',
    data: 'IP address and browser details of the device loading the page',
    location: 'United States',
  },
];
