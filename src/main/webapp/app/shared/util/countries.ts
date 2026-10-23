/**
 * Countries offered in short pickers (the demo request form, the console's new-demo form), as
 * ISO 3166 alpha-2 codes the server's `CountryCode` accepts.
 */
export const COMMON_COUNTRIES = [
  'US',
  'GB',
  'CA',
  'FR',
  'DE',
  'ES',
  'IT',
  'NL',
  'BE',
  'CH',
  'IE',
  'PT',
  'SE',
  'MA',
  'AE',
  'SA',
  'IN',
  'SG',
  'AU',
  'BR',
  // Countries with a shipped payroll template.
  'AL',
  'BG',
  'HR',
  'RO',
  'PL',
  'HU',
] as const;

/** `FR` → `France` in the given language (the code itself if the browser can't name it). */
export function countryName(code: string | null | undefined, lang = 'en'): string {
  if (!code) return '';
  try {
    return new Intl.DisplayNames([lang], { type: 'region' }).of(code) ?? code;
  } catch {
    return code;
  }
}

/** The common countries as select options, sorted by name. */
export function countryOptions(lang = 'en'): { value: string; label: string }[] {
  return COMMON_COUNTRIES.map(c => ({ value: c, label: countryName(c, lang) })).sort((a, b) => a.label.localeCompare(b.label));
}
