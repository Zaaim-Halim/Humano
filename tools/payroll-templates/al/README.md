# Albania — `al-employment`

Employment income paid monthly in ALL, 2026.

## Sources (retrieved 2026-10-20)

Gazette texts were downloaded from qbz.gov.al (the document view renders in a browser; the PDF
is served at `https://qbz.gov.al/pdf/<sha256>.pdf`) and each file's SHA-256 was checked against
the hash the gazette publishes.

| Id                | What it provides                                                                                                                                                                                                                                                                        |
| ----------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `law-29-2023`     | Law 29/2023 on income tax, consolidated version 6 (11.12.2025): art. 12 employment income (gross pay; contributions not deducted), art. 22 personal deduction, art. 24 13% / 23% at 2,040,000 a year, art. 65 payroll withholding (deduction only with the personal-status declaration) |
| `udhezim-26-2023` | General Instruction 26/2023, pts 22–24: monthly application (13% up to 170,000 a month), worked examples. Text as republished by AlProfit Consult (the tax administration's copy is no longer online)                                                                                   |
| `vkm-77-2015`     | Council of Ministers Decision 77/2015, consolidated to Decision 702/2024: contribution rates and bases by contract length (pts 3–5), several employers (pt 15)                                                                                                                          |
| `vkm-776-2025`    | Decision 776/2025: minimum wage 50,000 ALL from 1 January 2026                                                                                                                                                                                                                          |
| `law-10383-2011`  | Health-insurance law, as amended by law 184/2013: base = gross salary, 3.4 % split 50/50                                                                                                                                                                                                |
| `max-wage-2026`   | Maximum social-insurance base 186,416 ALL: the tax administration's 2026 notice (since removed from tatime.gov.al) and Boga & Associates' alert on Decision 776/2025                                                                                                                    |

Notes found while researching:

- Instruction 26/2023's worked example adds 265,200 + 82,800 as "358,000"; the correct sum is
  348,000 (29,000 a month), which is what the template produces.
- Several payroll guides cap health insurance at the maximum wage; the law and Decision 77/2015
  calculate it on the full gross salary (floor: the minimum wage), and Boga & Associates confirms
  this for 2026.
- The June 2023–December 2024 transitional table in law 29/2023 art. 69(2) no longer applies; arts.
  22–24 apply from 1 January 2025 (art. 72) and are unchanged in the 2025 consolidation.

## Rebuilding

```sh
python3 -I build_template.py ../../../src/main/resources/payroll-templates/al/al-employment.json
npx prettier --write ../../../src/main/resources/payroll-templates/al/al-employment.json
```

All values are written out in `build_template.py` with the act they come from; its
`expected_lines()` computes the test cases' amounts independently of the Java engine.
