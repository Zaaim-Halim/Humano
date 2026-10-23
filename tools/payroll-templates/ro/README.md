# Romania — `ro-employment`

Salary income paid monthly in RON, 2026.

## Sources (retrieved 2026-10-23)

All texts are ANAF's copies; legislatie.just.ro refused automated requests.

| Id                   | What it provides                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| -------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `cod-fiscal`         | Codul fiscal with the methodological norms, ANAF consolidation (ultima actualizare: Legea nr. 170/2026): art. 60 pct. 1 disability exemption, art. 66 fixed sums rounded to the leu, art. 77 deduction (table in alin. (4), youth and school-child extras in alin. (10)), art. 78 tax 10 %, art. 138 CAS 25 %, art. 146 (5^6)–(5^9) minimum CAS on the minimum wage with the difference paid by the employer, art. 156 CASS 10 %, art. 168 (6^1), art. 220^3–220^4 CAM 2.25 %; Norme pct. 4 (tax base rounding), pct. 13–14 |
| `oug-89-2025`        | OUG nr. 89/2025 art. III: 300 lei (January–June) / 200 lei (July–December) free of tax and contributions at the minimum wage, gross cap 4,300 / 4,600 lei; floor for art. 146 (5^6) reduced by the same amount. PDF SHA-256 `135051fd…7ba728`                                                                                                                                                                                                                                                                               |
| `hg-1506-2024`       | Minimum wage 4,050 lei (kept for January–June 2026). PDF SHA-256 `c829e477…d5e4f5`                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `hg-146-2026`        | Minimum wage 4,325 lei from 1 July 2026. PDF SHA-256 `3dce4be8…cc8f2488`                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `published-net-2026` | Net pay published for the minimum wage (2,574 / 2,699 lei), used to cross-check rounding only                                                                                                                                                                                                                                                                                                                                                                                                                               |

Notes found while researching:

- The art. 77(4) table is exactly linear: 20 / 25 / 30 / 35 / 45 % of the minimum wage up to the minimum
  wage, minus 0.5 points per 50 lei band, down to 0 / 5 / 10 / 15 / 25 % at minimum wage + 2,000 lei.
  The official text has typos in the cells ("3,50%%", "00,50%"); `extract_deductions.py` checks the structure.
- No official text was found for rounding the contributions and the tax itself. Rounding to the leu with
  50 bani up reproduces both published minimum-wage net figures (CASS 412.50 → 413).
- The deduction band uses the whole gross salary, including the tax-free amount (Norme pct. 14 alin. (1)
  defines the gross monthly salary as all salary income of the month).

## Rebuilding

```sh
# only when the Fiscal Code changes art. 77 (update EXPECTED_UPDATE in extract_deductions.py first)
python3 -I extract_deductions.py <saved Cod_fiscal_norme_2023.htm> deduction-table-art77.csv
python3 -I build_template.py ../../../src/main/resources/payroll-templates/ro/ro-employment.json
npx prettier --write ../../../src/main/resources/payroll-templates/ro/ro-employment.json
```

All values are written out in `build_template.py` with the act they come from; its
`expected_lines()` computes the test cases' amounts independently of the Java engine.
