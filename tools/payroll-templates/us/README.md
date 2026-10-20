# United States (federal) — `us-federal-employment`

Federal income tax withholding, social security, Medicare (with Additional Medicare) and FUTA on wages
paid in 2026 — weekly, biweekly, semimonthly and monthly pay. State and local taxes are separate
templates.

## How it is calculated

- **Federal income tax**: IRS Publication 15-T, Worksheet 1A and the 2026 Annual Percentage Method
  tables for automated payroll systems, for 2020-or-later Forms W-4 (filing status, Step 2 checkbox,
  Steps 3 and 4) and 2019-or-earlier forms (allowances), plus the nonresident alien adjustment (Tables 1
  and 2, income tax wages only). Intermediate amounts are kept unrounded (per-period divisions to 10
  decimals); the amount to withhold is rounded to the cent, or to the dollar when the employer sets
  `roundToWholeDollars` (Pub 15-T allows either, used consistently). The published tentative amounts
  (column C) are used as printed — the engine checks them against the brackets below them.
- **FICA** (Pub 15 section 9): 6.2% + 6.2% social security up to the $184,500 wage base, 1.45% + 1.45%
  Medicare, 0.9% Additional Medicare withheld on wages above $200,000 in the year.
- **FUTA** (Pub 15 section 14): 0.6% (6.0% less the maximum 5.4% credit) on the first $7,000.
- **Pre-tax deductions**: `RETIREMENT` reduces income tax wages only (401(k)/403(b)/457(b) elective
  deferrals); `INSURANCE` reduces income tax, FICA and FUTA wages (section 125 cafeteria plan). Any
  other pre-tax deduction type is refused.
- Payment-date basis: the year, the parameters and the wage bases follow the date wages are paid.
  Wage bases count earlier posted runs plus brought-forward wages. An employee employed before the
  period, with nothing recorded earlier this year and a period starting after 1 January, must have
  `broughtForwardYear` and the year's earlier wages set (0 if none) — that is the mid-year adoption case.

## Sources (retrieved 2026-10-20)

| Id                | Document                                                        |
| ----------------- | --------------------------------------------------------------- |
| `irs-pub15t-2026` | Publication 15-T (2026), Federal Income Tax Withholding Methods |
| `irs-pub15-2026`  | Publication 15 (2026), (Circular E) Employer's Tax Guide        |
| `irs-fw4-2026`    | Form W-4 (2026), Employee's Withholding Certificate             |

## Verification

- `build_template.py` refuses to build unless the IRS text contains each constant it uses, every
  published tentative amount agrees with its brackets, and its own implementation of Worksheet 1A
  reproduces **all 15,845 weekly-to-monthly amounts of the Pub 15-T Wage Bracket Method tables**
  (sections 2 and 3: every filing status, Step 2 checkbox, 0–10 allowances) at each bracket's midpoint.
  Daily tables are printed to the dime and Humano has no daily pay, so they are not compared.
- 28 of those rows and the Pub 15-T nonresident alien example ($31) are template test cases, run by
  the Java engine at startup and in CI; derived cases cover pre-tax deductions, credits, the wage
  bases, Additional Medicare, FUTA and mid-year adoption.
- `PayrollTemplatePayrollRunIT` runs January and February for a $120,000-a-month employee with a
  $1,000 401(k) deferral: the second month stops social security at $184,500, starts Additional
  Medicare at $200,000 and owes no more FUTA.

## Not supported

Supplemental wage flat rates (22% optional / 37% mandatory over $1M — supplemental pay is withheld as
regular wages), FICA/FUTA-exempt employment, tips, fringe benefits, group-term life over $50,000,
third-party sick pay, FUTA credit reduction states (reconciled on Form 940), daily or quarterly pay.

## Rebuilding

```sh
# p15t.pdf and p15.pdf from irs.gov/pub/irs-pdf/, converted with ../bg/pdftext.py to p15t.txt and p15.txt (same folder)
python3 -I build_template.py /path/to/p15t.txt ../../../src/main/resources/payroll-templates/us/us-federal-employment.json
npx prettier --write ../../../src/main/resources/payroll-templates/us/us-federal-employment.json
```
