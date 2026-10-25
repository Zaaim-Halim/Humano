# Nederland — `nl-employment`

Loonbelasting/premie volksverzekeringen (witte tabel, standaardsituatie, wonen in Nederland), premies
werknemersverzekeringen (AWf, Aof, opslag Wko, Whk) and the werkgeversheffing Zvw for wages paid in
2026, monthly or weekly.

## How it is calculated

- **Loonheffing**: the formulas of the Belastingdienst _Rekenvoorschriften voor de geautomatiseerde
  loonadministratie 2026_ (uitgave januari 2026, versie 2), chapter 2. The annual wage is floored to
  steps of € 54. The bracket tariff and the heffingskortingen (AHK, OUK, AOK, ARK) are applied with the
  document's rounding rules, then converted to the period amount. Above the highest table wage the
  _tabelsystematiek_ adds 49,50% of the excess, rounded down. Columns: younger than the AOW age, and
  AOW age born 1945 or earlier / 1946 or later. The AOW column applies from the first day of the month
  in which the employee turns 67 (Handboek §18.21), judged on the payment date.
- **Premies werknemersverzekeringen and Zvw** use _voortschrijdend cumulatief rekenen_ (Handboek
  chapter 6): cumulative wage, against loontijdvakken × the table 11 period maximum (€ 6.617,41 a month,
  € 1.527,09 a week, never more than € 79.409), minus the base already charged. From the AOW month no
  employee-insurance premiums are due; the Zvw continues.
- **Bijzondere beloningen** (BONUS earnings: vakantiebijslag, 13e maand, gratificaties): the official _witte tabel
  bijzondere beloningen 2026_ (`wit_bb_nl_std_20260101.xlsx`), read with `#table.band` on the employee's jaarloon
  (`specialPaymentAnnualWage`, Handboek §9.3.6), standaardtarief plus verrekeningspercentage when the
  loonheffingskorting applies, rounded down to cents; or `specialPaymentRate` from a machtiging. They are
  added to the VCR cumulative wage. `build_template.py` checks every percentage against the tariff rates
  and credit factors of the Rekenvoorschriften.
- The employee's pensioenpremie (pre-tax `RETIREMENT` deduction) reduces all three wages (Handboek
  §8.2 example).
- Payment-date basis: the loontijdvak counts in the year in which the wage is paid. The wage-tax line
  records the period wage; its year-to-date sum and count give the cumulative wage and the number of
  loontijdvakken. The AWf and Zvw lines record the cumulative bases.

## Sources (retrieved 2026-10-21)

| Id                           | Document                                                                                   |
| ---------------------------- | ------------------------------------------------------------------------------------------ |
| `bd-rekenvoorschriften-2026` | Rekenvoorschriften voor de geautomatiseerde loonadministratie 2026, januari 2026, versie 2 |
| `bd-witte-tabellen-2026`     | Loonbelastingtabellen 2026: wit, standaard, Nederland — maand, week, vier weken, kwartaal  |
| `bd-handboek-2026`           | Handboek Loonheffingen 2026, versie maart 2026                                             |

The white tables come from
`https://download.belastingdienst.nl/belastingdienst/dl/rekenhulpen/loonheffing/2026/v01/excel/`
(`wit_mnd_nl_std_20260101.xlsx`, `wit_wk_…`, `wit_4wk_…`, `wit_kw_…`). No later 2026 version had been
published as of the retrieval date.

## Verification

- `loonheffing.py`, a separate implementation of chapter 2, reproduces **all 108,460 amounts of the
  four official 2026 white tables**, every row and all 11 columns, including the verrekende
  arbeidskorting. `build_template.py` refuses to build otherwise, and also checks each parameter
  against the text of the Rekenvoorschriften and the Handboek.
- 14 sampled table rows (monthly and weekly, all age groups, with and without loonheffingskorting and
  alleenstaande-ouderenkorting) and the Handboek's Zvw example (€ 140,30) are template test cases.
  Derived cases cover VCR catch-up, the reached and annual maximum, the AOW month and mid-year adoption.
- `DutchHandbookExamplesTest` runs the Handboek's own worked examples through the template: VCR
  voorbeelden 2, 3 and 4 of §6.3, including the negative wage and the Zvw floor at € 0, with the
  handbook's fictitious maximum and rates patched in. It also runs both §18.21 AOW examples (the month
  of the 67th birthday, judged on the payment date).
- Second verification (2026-10-21, not committed: generated data): 48,168 generated cases ran through
  the Java engine with no difference from the Python reference. They covered every row of the monthly
  and weekly tables in all 8 column variants, the last cent before each next row, 800 random wages
  (including above the table) and 3,000 random VCR states (negative wages, caps, week 53,
  brought-forward figures). All 63 tariff and credit parameters were also checked against the separate
  annex _Bijlage parameterwaarden_.
- `PayrollTemplatePayrollRunIT` runs January (€ 8.000) and February (€ 5.000). February's premium base
  is € 6.382,59, catching up under the cumulative maximum, which proves the posted January lines drive
  it.

## Not supported (refused where an input allows it)

Bijzondere beloningen (a period with BONUS pay is refused; holiday allowance must not be paid through
another earning component), herleidingssituaties, non-residents, the green table, the
jonggehandicaptenkorting, overheidswerkgevers (Ufo), inhouding bijdrage Zvw (dga's), eigenrisicodragers,
premiekortingen/LIV, deductions other than the pensioenpremie, two-weekly or semi-monthly pay.

## Rebuilding

```sh
# folder: the four wit_*_nl_std xlsx files (renamed without the date), rekenvoorschriften.txt and
# handboek.txt (the two PDFs converted with ../bg/pdftext.py); openpyxl is required
python3 -I build_template.py /path/to/folder ../../../src/main/resources/payroll-templates/nl/nl-employment.json
npx prettier --write ../../../src/main/resources/payroll-templates/nl/nl-employment.json
```
