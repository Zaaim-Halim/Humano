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
#coalesce`; helpers `#table.lookup`, `#table.bracketTax`, `#ytd.base`, `#ytd.amount`. See
  `PayrollTemplateEngine` for every variable.
- **Test cases** are part of the template. The catalog runs them at startup on the deployed engine and
  withholds any template whose cases fail; `ShippedPayrollTemplatesTest` runs them in CI.
- **Status**: `OFFICIAL_VECTORS` only when the cases reproduce an authority's own worked examples or
  calculator; otherwise `LAW_DERIVED` (values cited, cases derived by hand) or `DRAFT`.

## Rules for changing a shipped template

- **Never rename or remove an input key.** Tenants' saved settings and employee profiles use them;
  an unknown key fails every calculation for that tenant. Add new keys instead, and deprecate in the
  description.
- **Never rename a step id within a tax year.** Year-to-date sums (`#ytd`) match payroll lines by step
  id across template versions, so a renamed step restarts its year-to-date mid-year.
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
- `de/` — Deutschland (`de-employment`). Lohnsteuer via the `de.lohnsteuer-pap-2026` extension, generated from the BMF XML; contributions declared.
