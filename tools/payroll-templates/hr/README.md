# Croatia — `hr-employment`

Salary (plaća) paid monthly in EUR, 2026.

## Sources (retrieved 2026-10-23)

| Id                             | What it provides                                                                                                                                                                                                                                                                                                      |
| ------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `zpd`                          | Zakon o porezu na dohodak, consolidated to NN 152/24 (in force 1.1.2025): art. 14 allowance 600 EUR and coefficients, art. 19.a local rates, art. 23 contributions deducted, art. 24 advance (lower rate up to 5,000 EUR a month; 50 % for development group I and Vukovar), art. 46(2) youth reduction (annual only) |
| `zod`                          | Zakon o doprinosima, consolidated to NN 152/24: MO I 20 % / 15 % (art. 13), health 16.5 % (art. 14), MO II 5 % (art. 17), first permanent employment (art. 20(2)-(3)), MO I base reduction (art. 21.a), minimum base (art. 200), maximum base (arts. 204–205)                                                         |
| `naredba-2026`                 | Naredba o iznosima osnovica … za 2026. godinu (NN 150/25): average wage 1,993.00, minimum monthly base 757.34, maximum monthly base 11,958.00 EUR                                                                                                                                                                     |
| `pravilnik-doprinosi-143-2023` | Pravilnik amending the Pravilnik o doprinosima (NN 143/23): the art. 21.a reduction is at most the gross pay; sharing between employers                                                                                                                                                                               |
| `pu-stope-2026`                | Porezna uprava, "Tablica poreznih stopa godišnjeg poreza na dohodak za 2026. godinu" (xlsx, SHA-256 `87733f3e…6f1592`): lower and higher rate for each of the 556 local units                                                                                                                                         |

Notes found while researching:

- Since NN 152/24 the higher rate starts above 5,000 EUR of monthly base (60,000 EUR a year), and a local
  unit without its own decision applies 20 % / 30 %. Some local decisions and third-party pages still quote the pre-2025 4,200 / 50,400.
- The youth reduction is in art. 46(2) (the annual calculation) only; art. 24 no longer reduces the
  monthly advance for it.
- The MO I reduction (art. 21.a) applies to the generational-solidarity contribution only, whether 15 % or
  20 %; MO II is always on the full base.
- The pension ceiling (art. 204) covers MO I and MO II; health insurance has no ceiling.

## Rebuilding

```sh
# only when the Tax Administration republishes the rate table (update SHA256 in extract_rates.py first)
python3 -I extract_rates.py <downloaded .xlsx> municipal-rates-2026.csv
python3 -I build_template.py ../../../src/main/resources/payroll-templates/hr/hr-employment.json
npx prettier --write ../../../src/main/resources/payroll-templates/hr/hr-employment.json
```

All values are written out in `build_template.py` with the act they come from; its
`expected_lines()` computes the test cases' amounts independently of the Java engine.
