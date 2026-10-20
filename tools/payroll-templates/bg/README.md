# Bulgaria — `bg-employment`

Employees under a labour contract (KSO art. 4(1)(1)), labour category III, monthly, EUR.

## Sources (retrieved 2026-10-20)

| Id                                | What it provides                                                                                                                                                                                                                                        |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `zbdoo-2026`                      | State Social Security Budget Act 2026 (SG 68/28.07.2026): art. 9 maximum insurable income (2,111.64 EUR to 31 Jul, 2,300 EUR from 1 Aug); art. 14 + annexes 2/2A work-accident rates; art. 15 guarantee fund 0 %; annexes 1/1A minimum insurable income |
| `kso`                             | Social Insurance Code art. 6(1), 6(3) items 7–9, 157(1)(1), 157(3): contribution rates and splits                                                                                                                                                       |
| `zbnzok-2026`                     | NHIF Budget Act 2026 art. 2: health contribution 8 %                                                                                                                                                                                                    |
| `zzo`                             | Health Insurance Act art. 40(1)(1): health base and 60:40 split                                                                                                                                                                                         |
| `zddfl`                           | Personal Income Taxes Act art. 24, 42: monthly tax base and 10 %                                                                                                                                                                                        |
| `noi-2026-08`, `noi-tzpb-2026-08` | NOI notices confirming the 1 August 2026 changes (used to cross-check the annexes)                                                                                                                                                                      |

## Rebuilding

The annex tables are extracted from the law PDF published by the Ministry of Labour (the State
Gazette HTML was unavailable). Run from this directory, with untrusted downloads kept in their own
folder:

```sh
python3 -m venv /tmp/bg-venv && /tmp/bg-venv/bin/pip install pypdf==5.1.0
/tmp/bg-venv/bin/python -I pdftext.py law.pdf > law.txt     # law.pdf = zbdoo-2026 URL
python3 -I bg_annex.py law.txt annexes.json                 # strict parser: aborts on unexpected rows
python3 -I bg_check.py annexes.json                         # 2 -> 2A diff must equal NOI's 7 changes
python3 -I build_template.py annexes.json ../../../src/main/resources/payroll-templates/bg/bg-employment.json
npx prettier --write ../../../src/main/resources/payroll-templates/bg/bg-employment.json
```

Known extraction artefacts, all handled in the scripts and asserted:

- Leading zeros dropped from class codes ("1.48" is "01.48"; KID-2025 has no one-digit division).
- Three code cells (rows 55, 69, 75) lost tokens into the name column in one of annex 1 / 1A; the
  other annex has the full codes and the missing digits appear in the corrupted row's name.
- Rows 81, 84, 85, 86 fill only some qualification groups and the extraction loses which. The groups
  come from KIK-info's rendering of annex 1A (`kik-mod-2026`) and match the occupations' NKPD groups
  (`PARTIAL_ROW_GROUPS` in `build_template.py`); the other groups are left empty, so those rows don't
  apply to them.
- Gazette typos in names ("Произовдство") are left as published.

## Verification

- Work-accident tables: the parsed 2 -> 2A differences equal NOI's published list of 7 changes
  (`bg_check.py`, and pinned in `ShippedPayrollTemplatesTest`).
- Minimum insurable income: annex 1A rows 13, 14, 61, 70, 76, 81, 84, 85, 86 were compared with KIK-info's
  independent rendering (`kik-mod-2026`) and match, including the occupation-specific rows' groups.
  Annex 1 (to 31 July) is almost entirely 550.66 and was not cross-checked row by row.
- Amounts: `build_template.py` computes the test cases' expected amounts with its own implementation
  of the cited articles; the Java engine must reproduce them to the cent. No official worked example
  was found, so the template is `LAW_DERIVED`.
