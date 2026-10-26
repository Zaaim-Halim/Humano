# Payroll templates

Statutory payroll templates (income tax + social contributions) ship with the application under
`src/main/resources/payroll-templates/<country>/`. A tenant adopts a template by id and always runs the
shipped version, so updating a template file and releasing reaches every adopter.

## Format

One JSON file per template; the model is `com.humano.service.payroll.template.model.PayrollTemplate`.

- **Data, not code.** Rates, ceilings and thresholds are dated `parameters`; published schedules are
  `tables` (`EXACT`, `HIERARCHICAL` code lookups, or progressive `BRACKETS`). Every value cites a
  `source` (title, https URL, retrieval date).
- **Steps** run in order: `REQUIRE` (refuse unsupported cases), `VALUE`, `CONTRIBUTION` (employee /
  employer shares), `TAX`, `EXTENSION` (a code building block for published algorithms, e.g.
  `de.lohnsteuer-pap-2026`, implementing `PayrollTemplateExtension`).
- **Expressions** are sandboxed SpEL. No decimal literals (put numbers in parameters), no `/` (use
  `#divide(a, b, scale, mode)`), no assignment. Functions: `#min #max #clamp #pos #round #divide
#coalesce`; helpers `#table.lookup`, `#table.bracketTax`, `#table.band`, `#ytd.base`, `#ytd.amount`,
  `#ytd.periods`, `#ytd.last`. See `PayrollTemplateEngine` for every variable.
- **Carrying a value to the next period.** `#ytd` sums what earlier posted periods produced. For a
  figure that is determined once and then kept (a withholding rate, the figures a later regularization
  starts from), mark the VALUE step `"remember": true`: its value is stored with the payroll result
  (`payroll_result.template_memo`, template id → step → value) and `#ytd.last('<step>')` returns it from
  the latest earlier posted period of the tax year, or null at the first. Only VALUE steps remember,
  only numbers (booleans are kept as 1 / 0); test cases give earlier values as `ytd` keys
  `last:<step>`. Comparing with `#ytd.last` detects a change in the data — a remembered step added in a
  later version reads null against periods posted before it.
- **Statutory leave pay.** `leavePay` declares what the law makes the employer pay for a type of leave, as a
  leave pay rule: `leaveType`, `deductionPercentage` (share of the daily salary not paid on days no band
  covers), `payBands` (calendar days of the absence, e.g. `{fromDay: 1, toDay: 42, deductionPercentage: 0}`),
  a `source` and a `note` on what it leaves out. It applies wherever the workspace has no rule of its own for
  that country and leave type; declare it only where per-absence bands of a percentage of salary express the
  law. In the builders it is the module-level `LEAVE_PAY` / `LEAVE_SOURCES` / `LEAVE_CHANGE` literals.
- **Templates read leave**: `#reductions` (pay not earned), `#leavePay['SICK']` (pay kept for leave days),
  `#leaveDays['SICK']` and `#unpaidLeaveDays['SICK']` (calendar days, those wholly unpaid), for leave types
  with a rule (the workspace's or the template's).
- **Test cases** are part of the template. The catalog runs them at startup on the deployed engine and
  withholds any template whose cases fail; `ShippedPayrollTemplatesTest` runs them in CI.
- **Status**: `OFFICIAL_VECTORS` only when the cases reproduce an authority's own worked examples or
  calculator; otherwise `LAW_DERIVED` (values cited, cases derived by hand) or `DRAFT`.

## Rules for changing a shipped template

- **Never rename or remove an input key.** Tenants' saved settings and employee profiles use them;
  an unknown key fails every calculation for that tenant. Add new keys instead, and deprecate in the
  description.
- **Never rename a step id within a tax year.** Year-to-date sums (`#ytd`) match payroll lines by step
  id across template versions, so a renamed step restarts its year-to-date mid-year; the same holds for
  `remember` steps read with `#ytd.last`.
- **Never change values for periods already paid** except to correct an error, and say so in the
  changelog: posted runs keep their results, but recalculations of unposted runs pick up the change.

## Shipping an update

1. Change the data from the primary sources and add a new entry at the top of `changelog` with the new
   `version` (tenants see "update available" until an admin acknowledges it).
2. Extend `validity`, add dated values / table versions for the new period, add test cases for it.
3. `npx prettier --write src/main/resources/payroll-templates` (generated files included).
4. `./mvnw test -Dtest='ShippedPayrollTemplatesTest,PayrollTemplateEngineTest'`.

Lines on every payslip carry `templateId@version` and the step id, so past results stay traceable to
the exact rules that produced them. Posted runs are never recalculated by an update.

## Countries

- `bg/` — Bulgaria (`bg-employment`). Built from the State Gazette text by the scripts in `bg/`.
- `al/` — Albania (`al-employment`). Written out with citations in `al/build_template.py`.
- `gb/` — United Kingdom (`gb-paye-nic`). PAYE via the `uk.paye-routines-v24` extension; NICs declared.
- `us/` — United States federal (`us-federal-employment`). Pub 15-T withholding, FICA, FUTA; all declared, no extension.
- `nl/` — Nederland (`nl-employment`). Rekenvoorschriften wage tax, AWf/Aof/Wko/Whk and Zvw with VCR; all declared, no extension.
- `hr/` — Hrvatska (`hr-employment`). Contributions with the MO I base reduction, local rates for all 556 units from the Tax Administration's table; all declared, no extension.
- `ro/` — România (`ro-employment`). CAS/CASS/CAM, the art. 77 deduction table and the OUG 89/2025 tax-free amount, changing on 1 July; all declared, no extension.
- `pl/` — Polska (`pl-employment`). ZUS contributions with the 282,600 zł limit, PIT advances with the 120,000 zł threshold and the 26-and-under relief, health capped at the 2021-rules advance; all declared, no extension.
- `hu/` — Magyarország (`hu-employment`). Szja 15 % with the mothers', under-25, personal, first-married and family allowances, tb-járulék 18.5 % with the minimum base and family contribution relief, szocho 13 %; all declared, no extension.
- `ie/` — Ireland (`ie-paye`). PAYE 20 % / 40 % from the RPN on the cumulative, week 1/month 1 or emergency basis, USC 0.5 / 2 / 3 / 8 % (ordinary, reduced or exempt), Class A PRSI with the PRSI credit and the 1 October 2026 rate rise, employer PRSI incl. the NTF levy; weekly or monthly, all declared, no extension.
- `ee/` — Eesti (`ee-employment`). Income tax 22 % with the flat 700 EUR monthly tax-free amount (776 EUR at pension age), unemployment insurance 1.6 % / 0.8 %, funded pension 2 / 4 / 6 %, social tax 33 % with the 886 EUR monthly minimum and its exemptions; all declared, no extension.
- `es/` — España (`es-employment`). IRPF withholding rate per the AEAT 2026 algorithm (personal and family minimum, work income reduction, 43 % cap, 2 % / 15 % minimums) with a year-to-date check that asks for the regularized rate; rates checked against the AEAT calculation service; Régimen General contributions with group and part-time bases, extra pays prorated, MEI, overtime, the solidarity contribution and the exemption past retirement age; common territory only, all declared, no extension.
- `de/` — Deutschland (`de-employment`). Lohnsteuer via the `de.lohnsteuer-pap-2026` extension, generated from the BMF XML; contributions declared.
