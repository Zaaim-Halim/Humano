# Poland — `pl-employment`

Employment contract (umowa o pracę) paid monthly in PLN, 2026, on a payment-date basis.

## Sources (retrieved 2026-10-23)

Acts as PDFs from the Sejm's ELI API (`https://api.sejm.gov.pl/eli/acts/<publisher>/<year>/<item>/text.pdf`);
the SHA-256 of each file is in `build_template.py`.

| Id                    | What it provides                                                                                                                                                                                                                          |
| --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `updof`               | PIT act, Dz.U. 2026 poz. 592: exemptions art. 21 ust. 1 pkt 148, 152–154 with the joint 85,528 zł limit (ust. 44), costs art. 22 ust. 2 and 3b, contribution deduction art. 26, scale art. 27, PIT-2 reduction art. 31b, advances art. 32 |
| `updof-2021`          | PIT act as at 2021 (Dz.U. 2021 poz. 1128) for the health cap: 17 % / 32 % above 85,528 zł, 43.76 zł monthly reduction                                                                                                                     |
| `usus`                | Social insurance act, Dz.U. 2026 poz. 199: rates art. 22, split art. 16, yearly limit art. 19                                                                                                                                             |
| `uswiadczenia`        | Health care act, Dz.U. 2025 poz. 1461: 9 % (art. 79), base (art. 81 ust. 6), cap at the 2021-rules advance (art. 83 ust. 1, 2a, 2b)                                                                                                       |
| `mp-2025-1206`        | 2026 pension/disability limit 282,600 zł                                                                                                                                                                                                  |
| `rm-2025-1242`        | 2026 minimum wage 4,806 zł                                                                                                                                                                                                                |
| `budzet-2026`         | Budget act 2026 art. 25–27: Labour Fund 1.0 %, Solidarity Fund 1.45 %, FGŚP 0.10 %                                                                                                                                                        |
| `rynek-pracy`, `fgsp` | Labour Fund base and exemptions (art. 259–264), FGŚP base and exemptions (art. 9a–9c, 29)                                                                                                                                                 |
| `wypadkowa`           | Accident rate rule (art. 28)                                                                                                                                                                                                              |
| `ordynacja`           | Rounding of tax bases and taxes to the złoty (art. 63 § 1)                                                                                                                                                                                |
| `mf-objasnienia-*`    | Ministry of Finance explanations of 30.12.2022 on the under-26 relief and on PIT-2 statements                                                                                                                                             |
| `zus-polski-lad`      | ZUS guide, example 37 (health cap: 800 zł → 31.04 zł)                                                                                                                                                                                     |

Notes found while researching:

- The under-26 relief applies including the day of the 26th birthday, by payment date (MF explanations).
- Contributions on exempt revenue are not deductible; with mixed revenue they are split in proportion to
  revenue (MF explanations, example 16). From 2026 the costs are capped at the taxable part (art. 22 ust. 3b).
- Exempt revenue does not count towards the 120,000 zł threshold (MF explanations, example 19).
- The 2021-rules health cap is computed on the income rounded to the złoty but is not rounded itself
  (ZUS example 37), and without deducting the health contribution.
- The Labour Fund (1.0 %) and Solidarity Fund (1.45 %) are computed together at 2.45 % with the same
  conditions (Solidarity Fund act art. 4–5).
- The Labour Fund test converts the pay to a full month only when the month is incomplete ("w przeliczeniu
  na okres miesiąca", art. 259 ust. 1); in a full month the pay itself decides, so `labourFundPartialMonth`
  is ignored there (corrected in 2026.1.1).

## Rebuilding

```sh
python3 -I build_template.py ../../../src/main/resources/payroll-templates/pl/pl-employment.json
npx prettier --write ../../../src/main/resources/payroll-templates/pl/pl-employment.json
```

All values are written out in `build_template.py` with the act they come from; its
`expected_lines()` computes the test cases' amounts independently of the Java engine.
