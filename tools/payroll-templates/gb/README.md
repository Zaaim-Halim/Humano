# United Kingdom — `gb-paye-nic`

PAYE income tax and Class 1 National Insurance for the 2026/27 tax year (6 April 2026 – 5 April 2027),
weekly, two-weekly and monthly pay, England & Northern Ireland, Scottish (S) and Welsh (C) codes.

## How it is calculated

- **PAYE** runs in the code extension `uk.paye-routines-v24`
  (`UkPayeTaxTableRoutines`), a direct implementation of HMRC's _Specification for PAYE Tax Table
  Routines_ v24.0: free pay / additional pay (Tables A), Income Tests and Tax Formulae with the
  specification's rounding, cumulative and Week 1/Month 1 bases, BR, D, NT and K codes, and the 50%
  regulatory limit (CWG2: applies to all codes). Rates and bandwidths come from the template
  (Appendices A–C), so a new tax year is a data change.
- **NICs** are declared in the template: the exact percentage method of HMRC's _National Insurance
  guidance for software developers 2026 to 2027_ (section 5), per-period earnings limits (9.2), rates
  for every category letter (Appendix 1), and its rounding rule (4.2: look only at the third decimal,
  5 or less rounds down).
- The template is on a **payment-date basis**: the tax week/month, the parameters and the year-to-date
  sums follow the date of payment.
- PAYE's pay and tax to date come from earlier posted runs plus, for mid-year starts and adoptions,
  the employee's brought-forward figures for the tax year (P45 and/or previous payroll).

## Sources (retrieved 2026-10-20)

| Id                      | Document                                                                                                         |
| ----------------------- | ---------------------------------------------------------------------------------------------------------------- |
| `hmrc-paye-spec-v24`    | Specification for PAYE Tax Table Routines v24.0, Feb 2026 (gov.uk payroll technical specifications – income tax) |
| `hmrc-ni-guidance-2026` | National Insurance guidance for software developers 2026 to 2027 v1.1                                            |
| `hmrc-rates-2026`       | Rates and thresholds for employers 2026 to 2027                                                                  |
| `hmrc-tables-bd-2026`   | Taxable Pay Tables Manual Method, use from 6 April 2026 (worked examples)                                        |
| `hmrc-cwg2-2026`        | CWG2 Employer further guide to PAYE and NICs 2026 to 2027                                                        |

## Verification

- `UkPayeTaxTableRoutinesTest` reproduces HMRC Tables B–D examples 1–7, 10 and 11 (England & NI,
  Scottish and Welsh; suffix and BR codes; month 4 higher and additional rate).
- The template's test cases include eight PAYE examples from Tables B–D and four NI examples from the
  NI guidance; the rest are computed by `build_template.py`'s own implementation of both specifications.
- `PayrollTemplatePayrollRunIT` runs two consecutive posted monthly payrolls around 6 April and checks
  that the second month's tax depends on the first month's recorded (zero-tax) pay.

## Not supported (refused with a message)

Payments in weeks 53/54/56, directors (annual NIC earnings period), student and postgraduate loans,
pre-tax deductions (net pay pensions, salary sacrifice), semi-monthly pay. Payrolled benefits in kind
are not included. Employment Allowance, the Apprenticeship Levy and Small Employers' Relief are
employer-level and outside the payslip.

## Rebuilding

```sh
python3 -I build_template.py ../../../src/main/resources/payroll-templates/gb/gb-paye-nic.json
npx prettier --write ../../../src/main/resources/payroll-templates/gb/gb-paye-nic.json
```
