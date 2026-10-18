/**
 * An amount of a plan feature with its unit, as people read it: storage past 1024 GB in TB
 * ("1 TB", "1.5 TB"), everything else as is ("25 employees", "100 GB").
 */
export function formatPlanAmount(value: number, unit: string | null | undefined): { value: string; unit: string } {
  if (unit === 'GB' && value >= 1024) {
    return { value: String(Math.round((value / 1024) * 10) / 10), unit: 'TB' };
  }
  const shown = Number.isInteger(value) ? String(value) : value.toFixed(value < 10 ? 2 : 1);
  return { value: shown, unit: unit ?? '' };
}
