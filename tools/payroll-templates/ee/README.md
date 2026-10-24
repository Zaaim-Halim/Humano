# Estonia — `ee-employment`

Employment contracts (tööleping) paid monthly in EUR, 2026.

## Sources (retrieved 2026-10-24)

The acts are the consolidated texts on Riigi Teataja, identified by their publication marks (the site
is a JavaScript application; the texts were read in a rendered browser). The Tax and Customs Board
(EMTA) pages explain how employers apply them.

| Id           | What it provides                                                                                                                                                                                                                                                                                                                  |
| ------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `tums`       | Income Tax Act, RT I, 09.07.2026, 66: 22 % (§ 4 lg 1); tax-free amount 8 400 EUR a year (§ 23), 9 312 EUR from the year of reaching pension age (§ 23^5); at most 1/12 a month on a written application to one employer (§ 42 lg 1, 2); employee unemployment insurance and funded pension come off before the tax (§ 42 lg 5, 6) |
| `sms`        | Social Tax Act, RT I, 21.05.2026, 19: 33 % (§ 7); at least the monthly base (§ 2 lg 2), paid by the employer applying the tax-free amount when there are several (lg 2^1), prorated when starting or leaving (lg 3), exemptions (lg 4)                                                                                            |
| `rea-2026`   | State Budget Act 2026, RT I, 18.12.2025, 22, § 2 lg 6: monthly base 886 EUR                                                                                                                                                                                                                                                       |
| `tkindls`    | Unemployment Insurance Act, RT I, 30.06.2026, 40: rates set by the Government (§ 41); the employee's contribution ends after the month of reaching pension age or an early/flexible pension (§ 42 lg 1 p 1); rounded to the cent (lg 3)                                                                                           |
| `vv-78-2025` | Government regulation nr 78 of 25.09.2025, RT I, 30.09.2025, 3: 1.6 % employee, 0.8 % employer for 2026–2029                                                                                                                                                                                                                      |
| `kops`       | Funded Pensions Act, RT I, 03.12.2024, 16: 2 %, or 4 % or 6 % by choice (§ 9)                                                                                                                                                                                                                                                     |
| `rm-17-2013` | Finance Minister regulation nr 17 of 15.03.2013, RT I, 28.04.2023, 3: minimum per month containing the agreed pay day (§ 2); several employers (§ 5); proration by calendar days / 30 (§ 6)                                                                                                                                       |
| `emta-2026`  | EMTA, "Maksumuudatused 2026" and "Maksuvaba tulu arvestamine": the 2026 figures and the employer's application of the tax-free amount                                                                                                                                                                                             |

Notes found while researching:

- The income-dependent "tax hump" ended on 1 January 2026: the tax-free amount is a flat 700 EUR a month
  (8 400 a year) whatever the income, but only on the employee's written application, and at one
  employer only; the employee may ask for less.
- EMTA's unemployment insurance page still cites regulation nr 59 (rates for 2025–2028); that regulation
  was repealed from 1 January 2026 by regulation nr 78, which keeps 1.6 % / 0.8 % for 2026–2029. The
  template cites nr 78.
- The minimum wage rose to 946 EUR on 1 April 2026, but the social tax monthly base stays 886 EUR for the
  whole of 2026: the Budget Act sets it for the year, at no less than the minimum wage in force on 1 July
  of the previous year (SMS § 2^1).
- Taxation follows the payment date (EMTA: "kassapõhine"): a December 2025 salary paid in January 2026
  uses the 2026 rules.
- With several employers, the social tax minimum falls only on the employer that applies the tax-free
  amount (SMS § 2 lg 2^1); an unpaid leave agreed by the parties does not reduce the minimum (lg 3 p 1).
- The prorated minimum in a month of starting or leaving is 886 x calendar days worked / 30 (regulation
  nr 17 § 6), not divided by the days in the month: EMTA's 886 / 31 x 22 example is for social tax the
  state pays (SMS § 6), a different rule.
- EMTA's declaration guidance: the tax-free amount is monthly, can't be carried to the next month, can't
  exceed the payment, and several payments in one month share the same limit.

## Rebuilding

```sh
python3 -I build_template.py ../../../src/main/resources/payroll-templates/ee/ee-employment.json
npx prettier --write ../../../src/main/resources/payroll-templates/ee/ee-employment.json
```

All values are written out in `build_template.py` with the act they come from; its
`expected_lines()` computes the test cases' amounts independently of the Java engine.
