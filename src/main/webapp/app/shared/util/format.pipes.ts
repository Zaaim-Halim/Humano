import { Pipe, PipeTransform } from '@angular/core';

const formatters = new Map<string, Intl.NumberFormat>();

function formatter(currency: string | null | undefined, signed: boolean): Intl.NumberFormat {
  const key = `${currency ?? ''}|${signed}`;
  let f = formatters.get(key);
  if (!f) {
    const options: Intl.NumberFormatOptions = {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
      ...(signed ? { signDisplay: 'exceptZero' } : {}),
    };
    try {
      f = currency
        ? new Intl.NumberFormat(undefined, { ...options, style: 'currency', currency })
        : new Intl.NumberFormat(undefined, options);
    } catch {
      // Unknown ISO code — fall back to a grouped number; the caller still shows the code.
      f = new Intl.NumberFormat(undefined, options);
    }
    formatters.set(key, f);
  }
  return f;
}

/**
 * Formats an amount for display: `13806 | money: 'USD'` → `$13,806.00`.
 * Accepts numbers or numeric strings (BigDecimal JSON). `signed` prefixes +/− for deltas.
 * Null/blank renders an em dash so tables never show `null USD`.
 */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(value: number | string | null | undefined, currency?: string | null, signed = false): string {
    if (value === null || value === undefined || value === '') {
      return '—';
    }
    const n = typeof value === 'number' ? value : Number(value);
    if (Number.isNaN(n)) {
      return String(value);
    }
    return formatter(currency, signed).format(n);
  }
}

/**
 * Human label for a payroll period (`September 2026`) from its start date, or from a
 * `YYYY-MM-…` period code; falls back to the raw code.
 */
@Pipe({ name: 'payPeriod' })
export class PayPeriodPipe implements PipeTransform {
  transform(start: string | null | undefined, code?: string | null): string {
    // Period codes are `YYYY-MM-<calendar>`; derive the month when no start date is supplied.
    if (!start && code && /^\d{4}-\d{2}/.test(code)) {
      start = `${code.slice(0, 7)}-01`;
    }
    if (start) {
      const d = new Date(`${start}T00:00:00`);
      if (!Number.isNaN(d.getTime())) {
        return d.toLocaleDateString(undefined, { month: 'long', year: 'numeric' });
      }
    }
    return code ?? '—';
  }
}

/** `SENIOR_MANAGER` → `Senior manager`; for enum codes that have no translation key. */
@Pipe({ name: 'humanize' })
export class HumanizePipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    if (!value) {
      return '—';
    }
    const s = value
      .replace(/^ROLE_/, '')
      .replace(/_/g, ' ')
      .toLowerCase();
    return s.charAt(0).toUpperCase() + s.slice(1);
  }
}

/**
 * Picks the singular/plural variant of a translation key: `pluralKey('x.people', 1)` →
 * `x.people.one`, otherwise `x.people.other`. The JSON holds `{ "one": …, "other": … }`.
 */
export function pluralKey(key: string, count: number | null | undefined): string {
  return `${key}.${count === 1 ? 'one' : 'other'}`;
}

/** Template form of {@link pluralKey}: `('x.people' | plural: n) | translate: { count: n }`. */
@Pipe({ name: 'plural' })
export class PluralPipe implements PipeTransform {
  transform(key: string, count: number | null | undefined): string {
    return pluralKey(key, count);
  }
}
