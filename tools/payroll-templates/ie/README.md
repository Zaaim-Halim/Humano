# Ireland — `ie-paye`

PAYE employment paid weekly or monthly in EUR, 2026: income tax and USC from the Revenue Payroll
Notification (RPN), and Class A PRSI.

## Sources (retrieved 2026-10-23)

The acts are read in the PDFs on irishstatutebook.ie (the revised acts on revisedacts.lawreform.ie were not
reachable); the payroll method comes from Revenue's Employers' Guide to PAYE.

| Id                         | What it provides                                                                                                                                                                                                                                         |
| -------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `revenue-guide`            | Tax and Duty Manual 42-04-35A (SHA-256 `1dc6b00b…e7ce9d2`): cumulative basis with worked examples (ch. 9.3), refunds (9.3.2), week 1/month 1 (9.4), emergency basis (9.5), USC exemption €13,000 (9.8), week 53 (14.3), previous pay, tax and USC (15.4) |
| `revenue-rates`            | Revenue's 2026 tax rates, bands and reliefs: €44,000 @ 20 %, balance @ 40 %; Employee PAYE Tax Credit €2,000                                                                                                                                             |
| `finance-act-2025`         | Finance Act 2025 (No. 18 of 2025, SHA-256 `91983a9e…ed9d36`), s. 2: USC 0.5 / 2 / 3 / 8 % on the first €12,012, the next €16,688 and €41,344, and the remainder                                                                                          |
| `revenue-usc`              | Revenue's USC pages: 2026 standard rates and thresholds; reduced rates (income €60,000 or less and 70+ or a full medical card: 0.5 % then 2 %)                                                                                                           |
| `revenue-rpn-fields`       | Revenue's PAYE Modernisation developers' presentation (2019): the RPN carries `payForUSCToDate` / `uscDeductedToDate` separately                                                                                                                         |
| `sw-mp-act-2024`           | Social Welfare (Miscellaneous Provisions) Act 2024 (No. 24 of 2024, SHA-256 `a5c19911…462cc2`), s. 3: Class A 4.2 % → 4.35 % and employer 8 % / 10.25 % → 8.15 % / 10.4 % from 1 October 2026                                                            |
| `sw-ae-act-2025`           | Social Welfare and Automatic Enrolment … (Amendment) Act 2025 (No. 19 of 2025, SHA-256 `68d2cb15…d24a3`), s. 2: employer lower-rate threshold €552 a week from 1 January 2026                                                                            |
| `swp-act-2015`             | Social Welfare and Pensions Act 2015 (No. 47 of 2015, SHA-256 `d4d00385…9238e`), s. 10: PRSI credit €12 less 1/6 of earnings over €352.01, for more than €352 and up to €424 a week                                                                      |
| `ntf-levy`                 | Social Welfare (No. 2) Act 2019, s. 29: National Training Fund levy 1.0 % with employer PRSI                                                                                                                                                             |
| `dsp-class-a`              | Department of Social Protection's PRSI Class A table (updated 10 June 2026): weekly bands from €38, rates before and from 1 October 2026 incl. employer 9.00 / 11.25 → 9.15 / 11.40                                                                      |
| `prsi-monthly-equivalents` | The monthly equivalents of the weekly figures (weekly × 52 / 12, rounded to the euro): €1,525 / €1,837, credit €52, employer €2,392                                                                                                                      |

Notes found while researching:

- The monthly PRSI thresholds are not in the acts, which give weekly figures and "the equivalent thereof"
  for other pay periods, nor on the DSP's Class A page, which tables weekly bands only. The template uses
  weekly × 52 / 12 rounded to the euro (€1,525 / €1,525.01–€1,837, as payroll summaries publish them;
  €2,392 is exact). The DSP's SW14 guide with the official monthly table (assets.gov.ie) could not be
  downloaded to confirm them.
- Revenue's examples round the per-period tax cut-off and credits up to the cent (€44,000 / 52 = €846.16,
  €4,000 / 12 = €333.34) and the USC cut-offs to the nearest cent (€28,700 / 52 = €551.92; €2,391.67 a
  month), each multiplied by the period number on the cumulative basis (month 2: €4,783.34).
- Revenue's USC worked example in the guide is internally inconsistent (it uses a €526.58 cut-off and
  €473.42 @ 3 % = €18.94), so it is not used as a test; the weekly tax example (€133.84) and the refund
  example (−€110) are reproduced exactly.
- The RPN gives previous pay for USC separately from previous pay for income tax; the template takes
  `previousUscPay` and falls back to `previousPay`.
- PRSI rises on 1 October 2026 and is chosen by the payment date, as are the tax week and month.

## Rebuilding

```sh
python3 -I build_template.py ../../../src/main/resources/payroll-templates/ie/ie-paye.json
npx prettier --write ../../../src/main/resources/payroll-templates/ie/ie-paye.json
```

All values are written out in `build_template.py` with the act they come from; its
`expected_lines()` computes the test cases' amounts independently of the Java engine.
