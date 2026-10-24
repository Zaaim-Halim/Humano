# Spain — `es-employment`

Employees in the Régimen General de la Seguridad Social working in the common tax territory, paid
monthly in EUR, 2026.

## Sources (retrieved 2026-10-24)

The acts are the BOE consolidated texts; the Agencia Tributaria's calculation algorithm and help
document are identified by their SHA-256 (they are served without version numbers).

| Id                      | What it provides                                                                                                                                                                                       |
| ----------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `lirpf`                 | Ley 35/2006 del IRPF (updated 07/10/2026): work income reduction (art. 20), personal and family minimum (arts. 56-61), withholding scale (art. 101.1)                                                  |
| `rirpf`                 | Reglamento del IRPF, RD 439/2007 (updated 07/10/2026): when withholding arises (art. 78), exemption limits (art. 81), base, minimum, quota and rate (arts. 82-86), regularization (art. 87), art. 88.5 |
| `aeat-algoritmo-2026`   | AEAT algorithm for the 2026 withholding rate, version of 10-09-2026 (SHA-256 b1a120d9…): variables, validations, rounding and steps; TABLA 1 and TABLA 2                                               |
| `aeat-ayuda-2026`       | AEAT help "Las Retenciones e Ingresos a Cuenta del IRPF … 2026" (SHA-256 33f2e86c…): how the rules apply, the 15.96 % worked example                                                                   |
| `orden-pjc-297-2026`    | Orden PJC/297/2026 (BOE-A-2026-7296): bases, rates, MEI, solidarity, unemployment, FOGASA, vocational training, part-time bases                                                                        |
| `lgss`                  | LGSS, RDLeg 8/2015 (updated 21/10/2026): solidarity (art. 19 bis), MEI (art. 127 bis), base and proration (art. 147), AT/EP tariff (DA 61ª)                                                            |
| `aeat-servicio-calculo` | AEAT calculation web service (PRET-R200, 2026 period 1) and its input schema: the official calculator the rates were compared with                                                                     |
| `concierto-pv`          | Concierto Económico with the Basque Country, Ley 12/2002, art. 7.Uno.a)                                                                                                                                |
| `convenio-navarra`      | Convenio Económico with Navarre, Ley 28/1990, art. 10.1.a)                                                                                                                                             |

Notes found while researching:

- The withholding rate is annual: the employer computes it from the expected annual pay and the
  employee's modelo 145 data, applies it to every payment, and regularizes it (RIRPF art. 87) when the
  data change. The template recomputes the rate each run and compares it with the year to date; if they
  disagree it refuses until the regularized rate is entered (`withholdingRateOverride`, `REGULARIZED`),
  since regularization depends on what was paid and withheld so far.
- Exempt pay and minimum rates: RIRPF art. 81.1 says no withholding applies below the limits, and art.
  86.2 sets minimums of 2 % (contracts under a year, artists) and 15 % (other special relationships).
  The AEAT help (p. 12) settles which wins: the exemption applies "siempre que no se trate de
  rendimientos que tengan reglamentariamente asignado un tipo especial de retención, ya sea éste un
  tipo fijo o un tipo mínimo", and the algorithm's exempt branch jumps to the step that applies the
  minimums. So exempt pay on a short contract is withheld at 2 %.
- Withholding follows the payment (RIRPF art. 78) and contributions the month of accrual (Orden art.
  1). The template works on the pay period and refuses a payment made in another calendar year.
- Extra payments (pagas extraordinarias) are prorated into the monthly contribution base (LGSS art.
  147; Orden art. 1, annual amount / 12 for monthly-paid employees), so a run holding only the extra
  pay has withholding but no contributions.
- The solidarity contribution (Orden art. 17) applies to the remuneration above the 5,101.20 maximum
  base in three tiers, split between employer and employee.
- Descendants count by half for withholding unless the employee alone is entitled to their whole
  minimum (RIRPF art. 84.2); the AEAT service also takes a descendant as half unless marked "computado
  por entero". The template asks for it (`descendantsComputation`) whenever there are descendants.
- Overtime is excluded from the common-contingencies base but included in the AT/EP base, which is also
  the base for unemployment, FOGASA and vocational training (LGSS art. 147.2.e; Orden art. 33.1), and
  carries an additional contribution of 28.30 % (23.60 / 4.70), or 14 % (12 / 2) when caused by force
  majeure (Orden art. 5). The solidarity contribution uses the base without overtime (RGC art. 72 bis).
- Employees past the ordinary retirement age (LGSS art. 205.1.a) are exempt from common contingencies
  except temporary incapacity, and from unemployment, FOGASA and vocational training (LGSS art. 152;
  not in public administrations); IT is 1.55 % (1.30 / 0.25, Orden art. 32.1.a) and the MEI applies
  only where retirement is contributed (art. 127 bis). Pay above the maximum base and overtime for them
  are refused, since the texts don't say how the solidarity and overtime contributions apply. Working
  pensioners (art. 153) are refused.
- Not supported, for lack of a rule in the texts used (the Orden, the Reglamento General de Cotización
  and the Social Security's 2026 bases page): part months (bases prorated by days in practice, stated
  in the Orden only for particular groups) and the daily bases of groups 8 to 11 (47.48 and 170.04 a day,
  without a stated day count for monthly-paid employees).

## Checked against the AEAT calculation service

The withholding rate was compared on 2026-10-24 with the Agencia Tributaria's own calculator
(`https://www2.agenciatributaria.gob.es/wlpl/PRET-R200/mc`, EJER=2026, PER=1, input per
`AEATRetenciones2026.xsd`):

- every test case's profile: 36 of 36 identical, including the help's 15.96 % example;
- 88 boundary profiles (each exemption limit ±0.01 under the three contract types, the work income
  reduction breakpoints, the housing loan threshold, both sides of the 35,200 cap): all identical;
- 5,000 random profiles (family situations, up to six descendants by whole or half, children under 3,
  ascendants shared by up to four, every disability level, ages over 65 and 75, mobility, housing loan,
  the 2 % and 15 % minimums, pay up to 400,000 EUR): all identical.

The service covers only the withholding rate; the contributions are checked against a separate integer
implementation of the Orden.

## Rebuilding

```sh
python3 -I build_template.py ../../../src/main/resources/payroll-templates/es/es-employment.json
npx prettier --write ../../../src/main/resources/payroll-templates/es/es-employment.json
```

All values are written out in `build_template.py` with the text they come from; its
`expected_lines()` computes the test cases' amounts independently of the Java engine.
