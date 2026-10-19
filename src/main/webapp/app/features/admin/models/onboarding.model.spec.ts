import { addDays, daysBetween, localIsoDate } from './onboarding.model';

describe('onboarding date helpers', () => {
  it('formats the local calendar date', () => {
    expect(localIsoDate(new Date(2026, 0, 5, 23, 30))).toBe('2026-01-05');
  });

  it('counts whole days, including across a daylight-saving change', () => {
    // Europe moves its clocks back on 2026-10-25; a 23/25-hour day must still count as one.
    expect(daysBetween('2026-10-24', '2026-10-26')).toBe(2);
    expect(daysBetween('2026-03-28', '2026-03-30')).toBe(2);
    expect(daysBetween('2026-10-18', '2026-10-08')).toBe(-10);
  });

  it('adds days across month ends and clock changes', () => {
    expect(addDays('2026-10-24', 7)).toBe('2026-10-31');
    expect(addDays('2026-10-18', 14)).toBe('2026-11-01');
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
  });
});
