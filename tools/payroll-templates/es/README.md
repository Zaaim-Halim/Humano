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
  data change. Each payroll result keeps the determination (`remember` steps, read next period as
  `#ytd.last`): the rate applied, the rate before overrides, the annual withholding (the algorithm's
  IMPORTE), the base, the minimum and the data they came from. While none of these change, the kept rate
  carries on (a regularized rate is not recomputed from scratch). When they change, the new rate is the
  new annual quota less what was withheld this year, over the pay still to come, truncated to two
  decimals (art. 87.3), capped at 47 % (19 % in Ceuta and Melilla), with the art. 86.2 minimums.
- Art. 87.5 limits, as the algorithm applies them to its causes 1 to 4 (base, minimum, spousal pension,
  child maintenance) but not to cause 5 (situation 2 to 3) or 11 (others): when the change does not raise
  base minus minimum, the rate may not rise above the one applied before; when it does, the annual
  withholding may not rise by more than base minus minimum did. The template infers the causes from what
  changed, as the AEAT service would be told them.
- Not automated, so refused until the employer enters the rate (`withholdingRateOverride`, `REGULARIZED`,
  which then carries on): changes while a housing-loan reduction is or was applied (its own
  regularization, art. 87.3.b), moving into or out of the Ceuta, Melilla and La Palma regime, and a change
  against months posted before 2026.1.3, which kept no determination. Pay before Humano is brought forward
  (`broughtForwardYear`, `broughtForwardMonths`, `broughtForwardPay`, `broughtForwardWithheld`); without
  it a regularization with earlier months missing is refused.
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
- Irregular income (LIRPF art. 18.2): income generated over more than two years (for termination pay, the
  years of service) or notoriously irregular in time (RIRPF art. 12.1, e.g. payments for termination by
  mutual agreement), imputed in a single year, has a 30% reduction on at most 300,000 EUR a year. The
  payer subtracts the reduction from the annual pay for the rate's base (RIRPF art. 83.3.a); for the
  five-year rule it counts only the income it reduced itself. The AEAT algorithm takes the reduction as
  `IRREGULAR1` (at most 90,000 and 30% of the annual pay) and subtracts it from the net work income
  before the art. 20 reduction; the "other expenses" cap still uses the pay less contributions. The
  template asks for the income (`irregularIncomeAmount`, part of `annualPay`) and computes the reduction,
  cents dropped so it never exceeds 30% of the pay, which the AEAT service refuses. The rate then applies
  to every payment, the irregular one included. Above 700,000 EUR it is refused: for termination pay the
  reducible amount shrinks to zero at 1,000,000 EUR, which neither the algorithm nor the template models.
- Dependants with a disability (LIRPF art. 60.2), counted by grade: 3,000 / 9,000 and 3,000 assistance
  each, by whole or half like the descendants, shared like the ascendants; ascendants under 65 count
  only with a disability (art. 59.1). (The algorithm text reads "under 64" there; the AEAT service
  treats one aged 64 like one aged 63, so the template's "under 65" matches it.)
- Court-ordered payments: the spousal pension reduces the base (the algorithm's CONYUGE); child
  maintenance below the base gets the scale separately and 1,980 more minimum (RIRPF art. 85.2).
- Ceuta, Melilla and La Palma (DA 57ª LIRPF, extended to 2026 by RDL 23/2026): for residents whose work
  income is obtained there, the quota is multiplied by 0.40 (RIRPF art. 80.2: the rate reduced by 60 %),
  with minimums of 0.8 % and 6 % (art. 86.2).
- Training contracts (Orden art. 46): while the common-contingencies base does not exceed the minimum
  base, fixed monthly quotas (57.72 + 11.51 common, 7.95 professional, 4.38 FOGASA, 2.16 + 0.28
  vocational training), unemployment on the AT/EP minimum base (art. 33.2.a.1.º) and the MEI on the
  minimum base. Above it the second rule adds the normal rates on the excess, but the third rule's MEI
  base ("sobre la base de cotización por contingencias comunes … sobre la base de cotización mínima") can
  be read two ways, so that case is refused; so are groups 1 to 3, where "base mínima de dicho régimen"
  (1,424.40) and the group's minimum differ.
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

Irregular income was compared on 2026-10-25 (version 2026.1.2) with the same service, sending the
reduction as `Reducciones/Irregularidad1`:

- the five irregular-income test cases and the live check's profile: all identical;
- 1,500 random profiles with irregular income from 5% to 100% of the pay (all three family situations,
  descendants by whole or half, disabilities, mobility, housing loan, the three contract types, pay up
  to 400,000 EUR; 65 of them above the 90,000 cap, 169 with a zero rate): all identical.

Version 2026.1.3 was compared on 2026-10-25:

- 2,000 random profiles with disabled descendants and ascendants (every grade, by whole and half,
  shared by up to three), spousal pensions, child maintenance above and below the base, and Ceuta /
  Melilla / La Palma, on top of the earlier features: all identical;
- 1,500 random regularizations (a determination, 1 to 10 months paid, then a raise, a cut, a new child,
  a disability, situation 2 to 3, a spousal pension, child maintenance or a contract change), sent with
  the causes the template infers and the earlier figures (`RetribSatisfechas`, `RetencionPracticada`,
  `RetribAnualesIniciales`, `RetencionAnualInicial`, `BaseRetencion`, `TipoRetencion`,
  `MinimoPersonalFamiliarInicial`): all identical, 25 of them where an art. 87.5 limit binds;
- the template's group 2 and regularization test cases: all identical. Training contracts are checked
  against the Orden text only (the service covers withholding).

The service covers only the withholding rate; the contributions are checked against a separate integer
implementation of the Orden.

## Rebuilding

```sh
python3 -I build_template.py ../../../src/main/resources/payroll-templates/es/es-employment.json
npx prettier --write ../../../src/main/resources/payroll-templates/es/es-employment.json
```

All values are written out in `build_template.py` with the text they come from; its
`expected_lines()` computes the test cases' amounts independently of the Java engine.
