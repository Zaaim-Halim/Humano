# Deutschland — `de-employment`

Lohnsteuer, Solidaritätszuschlag and Kirchensteuer on regular monthly wages, and the
Gesamtsozialversicherungsbeitrag (KV, RV, AV, PV) with the U1, U2 and U3 levies, for wage periods in
2026 (whole calendar months).

## How it is calculated

- **Lohnsteuer, SolZ, Maßstabsteuer** come from the extension `de.lohnsteuer-pap-2026`, which runs the
  BMF's _Programmablaufplan 2026_ (BMF-Schreiben of 12 November 2025, Anlage 1). The Java class
  `Lohnsteuer2026` is **generated** from the official ITZBund XML pseudocode (`Lohnsteuer2026.xml`,
  SHA-256 in the class header) by `pap2java.py`. The transpiler only rewrites `BigDecimal.ROUND_*` to
  `RoundingMode.*`, and refuses to write unless the generated code has the same 23 methods, 215
  assignments, 78 conditions and 41 calls as the XML. `DeLohnsteuerPap2026` only converts euros to cents
  and back, with one PAP instance per calculation.
- **Kirchensteuer**: the Maßstabsteuer (BK) × 8 % (BY, BW) or 9 % (other Länder, by the Betriebsstätte),
  fractions of a cent dropped.
- **Contributions** follow BVV §§ 1–2. Equal halves (KV 7,3 % + half the Zusatzbeitrag, RV 9,3 %,
  AV 1,3 %, PV 1,8 %) are one rounded half on each side. Unequal PV shares are rounded separately:
  Sachsen 2,3 / 1,3, the childless surcharge (+0,6 from the month after completing 23), and the child
  reductions (−0,25 for the 2nd to 5th child under 25). Ceilings are € 5.812,50 (KV/PV) and € 8.450
  (RV/AV) a month. U1/U2 use the employee's Krankenkasse's rates; U3 is 0,15 %; all three use the
  RV-capped wage.
- **Sonstige Bezüge** (BONUS earnings: Weihnachts-/Urlaubsgeld, Prämien, 13. Gehalt), paid with a period's current
  wage:
  - Lohnsteuer, SolZ and the church-tax base come from the same PAP call (SONSTB, JRE4, JFREIB, JHINZU → STS,
    SOLZS, BKS). § 39b Abs. 3 EStG: the voraussichtlicher Jahresarbeitslohn is this year's posted current wages
    and sonstige Bezüge with you (or the brought-forward figures from before Humano), earlier employers' wages
    from their Lohnsteuerbescheinigungen, and the current monthly wage for the rest of the year and for months
    at earlier employers without one. They are taxed in the year they are paid (§ 38a Abs. 1 Satz 3), so a
    payment date in another year is refused.
  - Contributions treat them as einmalig gezahltes Arbeitsentgelt (§ 23a SGB IV): added to the month's base up to
    the anteilige Beitragsbemessungsgrenze (months with contributions this year × the monthly ceiling, less what
    was already charged), separately for KV/PV and RV/AV. The März-Klausel case is refused. U1 leaves them out
    (§ 7 Abs. 2 AAG); U2 and the Insolvenzgeldumlage include them.
  - Not supported: Entschädigungen with the Fünftelregelung, one-off payments after leaving, Vermögensbeteiligungen.
- **Period basis**: current wages count in the year in which the pay period ends (§ 38a Abs. 1 Satz 2
  EStG); contributions count in the month earned. December wages paid in January use 2026.
- Ages follow §§ 187, 188 BGB (a year of life is completed the day before the birthday). That decides
  the Altersentlastungsbetrag (ALTER1/AJAHR) and the childless surcharge.

## Sources (retrieved 2026-10-21)

The PAP XML is from `bmf-steuerrechner.de`. The final Anlage 1 PDF (12 November 2025, used to read the
input definitions) is the IHK München copy, because bundesfinanzministerium.de blocks automated
access. Statutes are from `gesetze-im-internet.de`: EStG §§ 9a, 10c, 24a, 24b, 32, 32a, 39b, 39f, 51a;
SolzG §§ 3–4; SVBezGrV 2026 §§ 2, 4; SGB V § 241; BSV 2018 § 1 (no later Beitragssatzverordnung);
SGB III §§ 341, 358, 360; SGB XI §§ 55, 58 with PBAV 2025 § 1; AAG §§ 1, 7; BVV §§ 1, 2; SGB IV § 20.
Kirchensteuer rates come from the EKD statistics page, and the cent rule from the Nordkirche
Kirchensteuerbeschluss § 7 Abs. 2 and the Bavarian KirchStG. `build_template.py` checks each rate and
ceiling against the downloaded statute texts.

## Verification

- `lohnsteuer_statute.py` is written from the EStG/SolzG text, not from the PAP code. It
  reproduces **180,000 PAP amounts** (Lohnsteuer, SolZ, BK for 60,000 random monthly cases): all
  Steuerklassen, Kinderfreibeträge, church, Sachsen, PV surcharge/reductions, Altersentlastungsbetrag
  for every AJAHR, Frei- and Hinzurechnungsbeträge and Faktoren. Wages ran up to € 40.000 a month.
  Before applying the PAP's two binding roundings, which the statute leaves open (Vorsorgepauschale
  parts truncated and the total rounded up to the euro; Altersentlastungsbetrag rounded up to the
  euro), the only differences were whole euros of annual tax. The tariff and SolZ constants inside
  the PAP match § 32a Abs. 1 and SolzG §§ 3–4 one by one.
- `Lohnsteuer2026PrueftabelleTest` runs the generated PAP against the **BMF's own test tables** printed
  in Anlage 1 (pp. 39–40, "Allgemeine" and "Besondere maschinelle Jahreslohnsteuer 2026
  (Prüftabelle)"): all 516 values match (annual wages 5,000–110,000 EUR, classes I–VI, with and without
  social insurance). `lohnsteuer_statute.py` also matches all 258 values of the general table.
- Every numeric constant of the XML (247 of 249) appears in the published Anlage 1 PDF. The two that
  don't are an unused constant (`ZAHL500`) and 17,799, the zone base of § 32a Abs. 1 EStG, which is
  checked against the statute.
- `lohnsteuer_statute.other_payment` implements § 39b Abs. 3 from the statute text (the difference of two annual
  taxes, with the PAP's roundings). It matched the generated PAP on **20,000 random sonstige-Bezüge cases**
  (2026-10-25, generated data not committed), and the PAP's current-wage Lohnsteuer, SolZ and BK were identical
  with and without the sonstiger Bezug in all of them (`DeLohnsteuerPap2026Test` keeps two of these checks).
- The template's 29 computed cases and 20 refusals run through the Java engine (the generated PAP)
  at startup and in CI. `PayrollTemplatePayrollRunIT` runs a December 2026 payroll paid in January
  2027 through the Spring-wired extension.
- Second verification (2026-10-21, not committed: generated data): 20,000 random full-template cases
  ran through the Java engine with no difference from the Python reference. They covered all classes,
  child allowances, church tax in all 16 Länder, Sachsen, PV parents/children, factors,
  allowances/additions, U1, U3 exemption, birth dates around the 23rd and 64th year, wages up to
  25,000 EUR, separate social wages, and December paid in January. The PV rounding was confirmed
  against the GKV-Spitzenverband's _Grundsätzliche Hinweise_ of 31 March 2025, § 3.3: the
  "1-Schritt-Methode" (surcharge/reductions added to the half rate, one rounding) is the intended
  reading of § 2 BVV. That verification also found that employees at the Regelaltersgrenze and
  pensioners need other contribution groups; they are now refused.
- Not done: a comparison with the BMF's calculator interface (its terms allow program tests; they
  have not been accepted for this project). The official Prüftabellen cover the same ground for the
  annual tariff.

## Not supported

Sonstige Bezüge (bonus, Weihnachts-/Urlaubsgeld), joining or leaving mid-month, Entgeltumwandlung,
Midijobs (Übergangsbereich), Minijobs, apprentices below € 2.000, private health insurance,
old-age pensioners, employees exempt from pension insurance, employees aged 66 or over
(Regelaltersgrenze), Selbstzahler with a § 257 SGB V subsidy,
Werkstudenten and other Personengruppen, Versorgungsbezüge, Mindestkirchensteuer and Kappung, weekly or
other pay periods.

## Rebuilding

```sh
python3 -I pap2java.py Lohnsteuer2026.xml Lohnsteuer2026 ../../../src/main/java/com/humano/service/payroll/template/extension/de/Lohnsteuer2026.java
python3 -I build_template.py /path/to/law-snapshots ../../../src/main/resources/payroll-templates/de/de-employment.json
npx prettier --write ../../../src/main/resources/payroll-templates/de/de-employment.json ../../../src/main/java/com/humano/service/payroll/template/extension/de/*.java
```
