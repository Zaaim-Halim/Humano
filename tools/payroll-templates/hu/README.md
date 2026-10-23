# Hungary — `hu-employment`

Employment (munkaviszony) paid monthly in HUF, 2026.

## Sources (retrieved 2026-10-23)

The acts are read in the consolidated texts on net.jogtar.hu; the official database njt.hu refused automated
requests. The 2026 amounts come from the tax authority's (NAV) 2026 information booklets, which cite the
legal basis of each figure.

| Id       | What it provides                                                                                                                                                                                                                                               |
| -------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `szja`   | PIT act (1995. évi CXVII.): 15 % (8. §), previous-year pay paid by 10 January (26. § (1)), family allowance amounts (29/A. §), sharing (29/B. §), first-married 33,335 Ft (29/C. §), mothers' allowances (29/D. § ff.), personal (29/E. §), under-25 (29/F. §) |
| `tbj`    | Social security act (2019. évi CXXII.): pensioners not insured (6. § (1)), employer pays on the gap to the minimum base (22. § (3)), 18.5 % (25. §), minimum base 30 % of the minimum wage (27. § (2)–(3)), family contribution relief (34. §)                 |
| `szocho` | Social contribution tax act (2018. évi LII.): 13 %, base = tb-járulék base, pensioners exempt                                                                                                                                                                  |
| `nav-73` | NAV booklet 73 (19.03.2026): order of the allowances, under-25 limit 715,765 Ft, personal 107,600 Ft, family table, family contribution relief                                                                                                                 |
| `nav-49` | NAV booklet 49 (16.01.2026): szocho 13 %, minimum base 96,840 Ft, minimum wage 322,800 Ft (426/2025. Korm. rendelet)                                                                                                                                           |

Notes found while researching:

- The allowances are applied in a fixed order (NAV booklet 73, point 1): mothers' allowances, the under-25
  allowance, personal, first-married, then family; the unused family allowance × 15 % reduces the 18.5 %
  tb-járulék, but not the contribution on the gap to the minimum base (NAV booklet 73, point 11).
- The family allowance doubled from 1 January 2026 (133,340 / 266,660 / 440,000 Ft per beneficiary); the
  January and March versions of NAV booklet 73 carry the same figures.
- The under-25 allowance applies through the month of the 25th birthday and needs no declaration; a
  declaration limits it (e.g. when shared between employers).

## Rebuilding

```sh
python3 -I build_template.py ../../../src/main/resources/payroll-templates/hu/hu-employment.json
npx prettier --write ../../../src/main/resources/payroll-templates/hu/hu-employment.json
```

All values are written out in `build_template.py` with the act they come from; its
`expected_lines()` computes the test cases' amounts independently of the Java engine.
