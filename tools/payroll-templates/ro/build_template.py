"""Build src/main/resources/payroll-templates/ro/ro-employment.json.

Romania, salary income paid monthly in RON, 2026. Every rule below cites the act it comes from. The
basic personal deduction table comes from deduction-table-art77.csv (see extract_deductions.py). The
test cases' expected amounts are computed by expected_lines() — an implementation written directly from
those acts, independent of the Java engine; the engine must reproduce them exactly. Two cases reproduce
the net pay published for the 2026 minimum wage.

Usage: python3 -I build_template.py ../../../src/main/resources/payroll-templates/ro/ro-employment.json
"""
import csv
import json
import os
import sys
from datetime import date
from decimal import ROUND_CEILING, ROUND_HALF_DOWN, ROUND_HALF_UP, Decimal

RETRIEVED = "2026-10-23"
FROM, TO = "2026-01-01", "2026-12-31"
H1, H2 = ("2026-01-01", "2026-06-30"), ("2026-07-01", "2026-12-31")

SOURCES = {
    "cod-fiscal": {
        "title": "Legea nr. 227/2015 privind Codul fiscal, text consolidat ANAF cu normele metodologice (ultima actualizare: Legea nr. 170/2026) — art. 60 pct. 1 (scutire handicap grav/accentuat), art. 66 (sume fixe rotunjite la leu), art. 76–78 (venituri din salarii, deducere personală, impozit 10 %), art. 138 lit. a) (CAS 25 %), art. 139, art. 146 alin. (5^6)–(5^9) (CAS minimă pe salariul minim, diferența suportată de angajator), art. 156–157 (CASS 10 %), art. 168 alin. (6^1), art. 220^3–220^4 (CAM 2,25 %); Norme pct. 4 (baza de calcul rotunjită la leu, fracțiunile de până la 50 de bani inclusiv neglijate), pct. 13 (deducere personală), pct. 14 (venit brut lunar)",
        "url": "https://static.anaf.ro/static/10/Anaf/legislatie/Cod_fiscal_norme_2023.htm",
    },
    "oug-89-2025": {
        "title": "OUG nr. 89/2025, art. III — 300 lei/lună (1 ianuarie–30 iunie 2026), respectiv 200 lei/lună (1 iulie–31 decembrie 2026) neimpozabili și excluși din baza contribuțiilor, pentru salariul de bază egal cu salariul minim, normă întreagă, funcția de bază, venit brut de cel mult 4.300 / 4.600 lei; alin. (5) salariul minim pentru art. 146 alin. (5^6) diminuat cu 300 / 200 lei; PDF SHA-256 135051fde8008bcddc8982586827fbfaddc49a57c1fa50b25d8dc4521e7ba728",
        "url": "https://static.anaf.ro/static/10/Anaf/legislatie/OUG_89_2025.pdf",
    },
    "hg-1506-2024": {
        "title": "HG nr. 1506/2024 — salariul de bază minim brut pe țară garantat în plată 4.050 lei lunar (menținut pentru 1 ianuarie–30 iunie 2026, cf. preambulul OUG nr. 89/2025); PDF SHA-256 c829e477306d69e8f1e1d523baf2b6375cda7961dd34219bc36a9d6ae4d5e4f5",
        "url": "https://static.anaf.ro/static/10/Anaf/legislatie/HG_1506_2024.pdf",
    },
    "hg-146-2026": {
        "title": "HG nr. 146/2026, art. 1 — salariul de bază minim brut pe țară garantat în plată 4.325 lei lunar de la 1 iulie 2026; PDF SHA-256 3dce4be85c2330ff6c79049014c538081c2e55de647125072e8b6937cc8f2488",
        "url": "https://static.anaf.ro/static/10/Anaf/legislatie/HG_146_2026.pdf",
    },
    "anaf-ghid-cas-minim": {
        "title": "ANAF, DGRFP Brașov — „Modificări privind CAS și CASS datorate de angajatori în cazul contractelor de muncă cu timp parțial sau cu normă întreagă” (art. 146 alin. (5^6)-(5^9), art. 168 alin. (6^1)): diferența plătită de angajator în numele angajatului; salariul minim prorata zilelor lucrătoare în care contractul nu este suspendat",
        "url": "https://static.anaf.ro/static/10/Brasov/Brasov/Contributii_contracte_2022.pdf",
    },
    "published-net-2026": {
        "title": "Net pay published for the 2026 minimum wage by independent payroll calculators: 2.574 lei for 4.050 lei gross (January–June) and 2.699 lei for 4.325 lei gross (July–December) — used as cross-checks of the rounding, not as legal sources",
        "url": "https://folositor.ro/ghid/salariul-minim-romania",
    },
}
for s in SOURCES.values():
    s["retrieved"] = RETRIEVED

# (value, source) for the whole year, or [(from, to, value, source), ...] when it changes on 1 July.
PARAMS = {
    "minimumWage": [(*H1, "4050", "hg-1506-2024"), (*H2, "4325", "hg-146-2026")],
    "nonTaxableAmount": [(*H1, "300", "oug-89-2025"), (*H2, "200", "oug-89-2025")],  # OUG 89 art. III(1)
    "nonTaxableGrossCap": [(*H1, "4300", "oug-89-2025"), (*H2, "4600", "oug-89-2025")],  # art. III(1)(b), inclusive
    "casRate": ("0.25", "cod-fiscal"),  # art. 138 lit. a)
    "cassRate": ("0.10", "cod-fiscal"),  # art. 156
    "camRate": ("0.0225", "cod-fiscal"),  # art. 220^3
    "taxRate": ("0.10", "cod-fiscal"),  # art. 78 alin. (2)
    "deductionBandWidth": ("50", "cod-fiscal"),  # art. 77 alin. (4): bands of 50 lei above the minimum wage
    "deductionLimitAboveMinimum": ("2000", "cod-fiscal"),  # art. 77 alin. (3)
    "maxDependentsColumn": ("4", "cod-fiscal"),  # art. 77 alin. (4): "4 și peste"
    "youthDeductionRate": ("0.15", "cod-fiscal"),  # art. 77 alin. (10) lit. a)
    "youthAgeLimit": ("26", "cod-fiscal"),  # art. 77 alin. (10) lit. a): "până la 26 de ani"
    "schoolChildDeduction": ("100", "cod-fiscal"),  # art. 77 alin. (10) lit. b)
}

HERE = os.path.dirname(os.path.abspath(__file__))
COLUMNS = ["dependents0", "dependents1", "dependents2", "dependents3", "dependents4OrMore"]
with open(os.path.join(HERE, "deduction-table-art77.csv"), encoding="utf-8") as f:
    DEDUCTION = {int(r["band"]): [Decimal(r[c]) for c in COLUMNS] for r in csv.DictReader(f)}
assert sorted(DEDUCTION) == list(range(41))


def lei(x, mode=ROUND_HALF_UP):
    return x.quantize(Decimal(1), rounding=mode)


def half(month):
    """minimum wage, tax-free amount, its gross cap for a month of 2026"""
    return (Decimal(4050), Decimal(300), Decimal(4300)) if month <= 6 else (Decimal(4325), Decimal(200), Decimal(4600))


def age_at(birth, end):
    return end.year - birth.year - ((end.month, end.day) < (birth.month, birth.day))


def expected_lines(c):
    """The amounts the acts give for a test case, computed directly (no engine)."""
    gross = Decimal(c["grossEarnings"])
    emp = c.get("employee", {})
    end = date.fromisoformat(c["periodEnd"])
    year, month = int(c["periodStart"][:4]), int(c["periodStart"][5:7])
    assert year == 2026
    minimum, tax_free, cap = half(month)
    main = emp.get("mainEmployer") == "true"
    lines = []

    def add(step, side, amount):
        if amount != 0:
            lines.append({"step": step, "side": side, "amount": str(amount)})
        return amount

    # OUG 89/2025 art. III: full-time, main job, contract base salary = minimum wage, gross within the cap.
    eligible = main and emp.get("contractBaseSalaryAtMinimum") == "true" and Decimal(0) < gross <= cap
    if eligible:
        # art. III(4): the 300 / 200 lei amount can only be reduced ("se diminuează") for a partial month
        amount = min(Decimal(emp["nonTaxableAmountOverride"]), tax_free) if "nonTaxableAmountOverride" in emp else tax_free
        non_taxable = min(amount, gross)
    else:
        non_taxable = Decimal(0)
    base = max(gross - non_taxable, Decimal(0))
    cas = add("cas", "EMPLOYEE", lei(base * Decimal("0.25")))
    cass = add("cass", "EMPLOYEE", lei(base * Decimal("0.10")))
    # art. 146 (5^6)-(5^9), art. 168 (6^1); OUG 89 art. III(5): floor on the minimum wage less the tax-free amount.
    if gross > 0 and emp.get("minimumContributionException", "NONE") == "NONE":
        floor = Decimal(emp["minimumContributionBase"]) if "minimumContributionBase" in emp else minimum - tax_free
        add("casMinimum", "EMPLOYER", max(lei(floor * Decimal("0.25")) - cas, Decimal(0)))
        add("cassMinimum", "EMPLOYER", max(lei(floor * Decimal("0.10")) - cass, Decimal(0)))
    add("cam", "EMPLOYER", lei(base * Decimal("0.0225")))
    taxable_net = max(gross - non_taxable - cas - cass, Decimal(0))
    deduction = Decimal(0)
    if main and gross > 0:
        band = 0 if gross <= minimum else int(((gross - minimum) / 50).to_integral_value(rounding=ROUND_CEILING))
        if band <= 40:  # art. 77 (3)-(4); art. 66: rounded to the leu, 50 bani and under dropped
            deduction += lei(DEDUCTION[band][min(int(emp.get("dependents", "0")), 4)] * minimum, ROUND_HALF_DOWN)
            if age_at(date.fromisoformat(c["birthDate"]), end) < 26:  # art. 77 (10)(a)
                deduction += lei(minimum * Decimal("0.15"), ROUND_HALF_DOWN)
        deduction += 100 * int(emp.get("schoolChildren", "0"))  # art. 77 (10)(b)
        deduction = min(deduction, taxable_net)  # art. 77 (2)
    tax_base = lei(max(taxable_net - deduction, Decimal(0)), ROUND_HALF_DOWN)  # Norme pct. 4
    if emp.get("incomeTaxExempt") != "true":
        add("incomeTax", "TAX", lei(tax_base * Decimal("0.10")))
    return lines


def case(name, evidence, gross, employee, month=3, full=True, error=None, birth=None, pre_tax=None, year=2026):
    last = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][month - 1]
    c = {
        "name": name,
        "evidence": evidence,
        "periodStart": f"{year}-{month:02d}-01",
        "periodEnd": f"{year}-{month:02d}-{last}",
        "frequency": "MONTHLY",
        "grossEarnings": gross,
        "fullPeriod": full,
    }
    if birth:
        c["birthDate"] = birth
    c.update({"employer": {}, "employee": employee})
    if pre_tax:
        c["preTaxDeductions"] = pre_tax
    if error:
        c["expectError"] = error
    return c


def emp(main=True, **extra):
    e = {"mainEmployer": "true" if main else "false"}
    e.update(extra)
    return e


ADULT = "1980-05-10"
AT_MINIMUM = {"contractBaseSalaryAtMinimum": "true"}


def test_cases():
    law = "Codul fiscal art. 77, 78, 138, 146, 156, 168, 220^3; OUG 89/2025 art. III; HG 1506/2024, HG 146/2026; amounts from build_template.py expected_lines()"
    computed = [
        case("published net: minimum wage 4,050 in March", "Net 2,574 lei as published for 2026 H1: 300 lei tax-free; CAS 937.50 -> 938, CASS 375, deduction 20% x 4,050 = 810, tax base 1,627, tax 162.70 -> 163. " + law,
             "4050.00", emp(**AT_MINIMUM), birth=ADULT),
        case("published net: minimum wage 4,325 in August", "Net 2,699 lei as published for 2026 H2: 200 lei tax-free; CAS 1,031.25 -> 1,031, CASS 412.50 -> 413, deduction 865, tax 181.60 -> 182. " + law,
             "4325.00", emp(**AT_MINIMUM), month=8, birth=ADULT),
        case("4,300 in June keeps the tax-free amount", "OUG 89 art. III(1)(b): 4,300 inclusive; band ceil(250/50) = 5, 17.5% x 4,050 = 708.75 -> 709 (the band uses the whole gross, Norme pct. 14). " + law,
             "4300.00", emp(**AT_MINIMUM), month=6, birth=ADULT),
        case("4,301 in June loses the tax-free amount", "Above the 4,300 cap: no tax-free amount. " + law,
             "4301.00", emp(**AT_MINIMUM), month=6, birth=ADULT),
        case("4,500 in June", "Minimum 4,050: band 9, 15.5% x 4,050 = 627.75 -> 628. " + law, "4500.00", emp(), month=6, birth=ADULT),
        case("4,500 in July", "Minimum 4,325: band 4, 18% x 4,325 = 778.50 -> 778 (art. 66: 50 bani dropped). " + law,
             "4500.00", emp(), month=7, birth=ADULT),
        case("above minimum + 2,000 with two school children", "No basic deduction above 6,050; 2 x 100 lei for children in school. " + law,
             "7000.00", emp(schoolChildren="2"), birth=ADULT),
        case("under 26: youth deduction", "Age 24: 15% x 4,050 = 607.50 -> 607 on top of band 19 (10.5% x 4,050 = 425.25 -> 425). " + law,
             "5000.00", emp(), birth="2001-05-01"),
        case("26 by the end of the month: no youth deduction", "Age 26 on 31 March: art. 77 (10)(a) no longer applies. " + law,
             "5000.00", emp(), birth="2000-03-15"),
        case("four or more dependents", "5 dependents use the '4 și peste' column: 45% x 4,050 = 1,822.50 -> 1,822. " + law,
             "4050.00", emp(dependents="5"), birth=ADULT),
        case("second job below the minimum: employer pays the CAS/CASS difference", "art. 146 (5^6)-(5^9): floor 4,050 - 300 = 3,750; CAS 750 vs 938, CASS 300 vs 375; no deduction outside the main job (art. 78 (2)(b)). " + law,
             "3000.00", emp(main=False)),
        case("student under 26: no minimum contribution", "art. 146 (5^7)(a): the floor does not apply. " + law,
             "2000.00", emp(minimumContributionException="STUDENT_UNDER_26"), birth="2004-09-01"),
        case("severe disability: no income tax", "art. 60 pt. 1: exempt from income tax; contributions still due. " + law,
             "6000.00", emp(incomeTaxExempt="true"), birth=ADULT),
        case("tax base ending in 50 bani rounds down", "Norme pct. 4: 5,204.50 -> 5,204, so the tax is 520 (not 521). " + law,
             "8007.50", emp(main=False)),
        case("tax-free override above the July amount is capped at 200", "OUG 89 art. III(1), (4): 200 lei from July, which a partial month can only reduce ('se diminuează'); an override of 300 left from June counts as 200. " + law,
             "4325.00", emp(**AT_MINIMUM, nonTaxableAmountOverride="300"), month=8, birth=ADULT),
        case("partial month with both overrides", "OUG 89 art. III(4) and art. 146 (5^6): tax-free amount and floor prorated by the tenant. " + law,
             "2025.00", emp(**AT_MINIMUM, nonTaxableAmountOverride="150", minimumContributionBase="1875"), full=False, birth=ADULT),
    ]
    for c in computed:
        c["expectLines"] = expected_lines(c)
    zero = case("no earnings, no lines", "nothing is insured or taxed", "0", emp(), birth=ADULT)
    zero["expectLines"] = []
    errors = [
        case("missing main-job answer", "the deduction and the tax-free amount apply only at the main job", "4000.00", {},
             birth=ADULT, error="mainEmployer"),
        case("main job without a birth date", "art. 77 (10)(a) needs the age", "4000.00", emp(), error="no birth date"),
        case("partial month eligible for the tax-free amount", "OUG 89 art. III(4): the amount is prorated", "2025.00",
             emp(**AT_MINIMUM), full=False, birth=ADULT, error="nonTaxableAmountOverride"),
        case("partial month below the minimum base", "art. 146 (5^6): the floor is prorated by working days", "1500.00",
             emp(), full=False, birth=ADULT, error="minimumContributionBase"),
        case("pre-tax deductions refuse", "union dues, voluntary pension and health insurance (art. 78 (2)(a)(ii)-(iv)) are not modelled",
             "4000.00", emp(), birth=ADULT, pre_tax="50.00", error="Pre-tax deductions are not supported"),
        dict(case("paid sick leave refused", "medical-leave indemnities (OUG 158/2005) are not modelled", "4000.00", emp(), full=False, birth=ADULT,
                  error="Paid sick or maternity leave is not supported"),
             leavePay={"SICK": "900.00"}, leaveDays={"SICK": "7"}),
        case("2027 is not covered", "2027 values not yet published", "4000.00", emp(), birth=ADULT, year=2027,
             error="a newer template version is needed"),
    ]
    return computed + [zero] + errors


def parameter(spec):
    if isinstance(spec, tuple):
        value, source = spec
        return [{"from": FROM, "to": TO, "value": value, "source": source}]
    return [{"from": f, "to": t, "value": v, "source": s} for f, t, v, s in spec]


def main():
    main_job = "#employee['mainEmployer']"
    no_exception = "#employee['minimumContributionException'] == 'NONE'"
    floor_default = "#p['minimumWage'] - #p['nonTaxableAmount']"
    template = {
        "schemaVersion": 1,
        "id": "ro-employment",
        "name": "Romania — salary income",
        "version": "2026.1.2",
        "status": "LAW_DERIVED",
        "jurisdiction": {"country": "RO"},
        "validity": {"from": FROM, "to": TO},
        "currency": "RON",
        "payFrequencies": ["MONTHLY"],
        "taxYearStart": "--01-01",
        "dateBasis": "PERIOD",
        "rounding": {"scale": 0, "mode": "HALF_UP"},
        "changelog": [
            {"version": "2026.1.2", "date": "2026-10-25",
             "summary": "Pay kept for sick or maternity leave days (a leave pay rule) is refused: medical-leave indemnities (OUG 158/2005) are computed on the average income of the last 6 months and have their own tax and contribution treatment (e.g. maternity indemnities are not taxable, Cod fiscal art. 62 c)), which the template does not model. Before, they were taxed and insured as ordinary salary."},
            {"version": "2026.1.1", "date": "2026-10-24",
             "summary": "Corrects an error: the partial-month tax-free amount (nonTaxableAmountOverride) is capped at the statutory 300 lei (January-June) / 200 lei (July-December), which OUG 89/2025 art. III(4) only allows to be reduced; before, up to 300 lei was accepted in the second half of the year."},
            {"version": "2026.1.0", "date": RETRIEVED,
             "summary": "2026: CAS 25 %, CASS 10 %, CAM 2.25 %, income tax 10 % with the art. 77 deduction; minimum wage 4,050 / 4,325 lei and the OUG 89/2025 tax-free amount 300 / 200 lei, changing on 1 July."}
        ],
        "sources": SOURCES,
        "inputs": {
            "employer": [],
            "employee": [
                {"key": "mainEmployer", "type": "BOOLEAN", "required": True,
                 "description": "Whether this employer is the employee's main job (funcția de bază): only there are the personal deduction (art. 77) and the OUG 89/2025 tax-free amount applied"},
                {"key": "dependents", "type": "INTEGER", "required": False, "defaultValue": "0", "min": 0, "max": 50,
                 "description": "Persons in the employee's care (persoane aflate în întreținere) for the basic personal deduction, art. 77 alin. (4)-(9); minor children count"},
                {"key": "schoolChildren", "type": "INTEGER", "required": False, "defaultValue": "0", "min": 0, "max": 50,
                 "description": "Children under 18 enrolled in education for whom this parent takes the 100 lei additional deduction, art. 77 alin. (10) lit. b)"},
                {"key": "contractBaseSalaryAtMinimum", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "Whether the base salary in the employment contract equals the national minimum gross wage (OUG 89/2025 art. III(1)(a)): with full time at the main job and gross pay within the cap, 300 lei (January-June) / 200 lei (July-December) are free of tax and contributions"},
                {"key": "incomeTaxExempt", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "Exempt from income tax on salary as a person with severe or pronounced disability (Codul fiscal art. 60 pct. 1); contributions remain due"},
                {"key": "minimumContributionException", "type": "ENUM", "required": False, "defaultValue": "NONE",
                 "values": ["NONE", "STUDENT_UNDER_26", "APPRENTICE_UNDER_18", "REDUCED_HOURS_DISABILITY", "AGE_PENSIONER", "MULTIPLE_CONTRACTS"],
                 "description": "Exception from the minimum CAS/CASS on the minimum wage (art. 146 alin. (5^7)): pupil or student under 26, apprentice under 18, person allowed by law to work under 8 hours a day, age pensioner, or several contracts whose combined base reaches the minimum wage"},
                {"key": "minimumContributionBase", "type": "DECIMAL", "required": False, "min": 0,
                 "description": "Minimum CAS/CASS base in lei replacing the minimum wage less the tax-free amount, for months in which the contract was active only some working days (art. 146 alin. (5^6))"},
                {"key": "nonTaxableAmountOverride", "type": "DECIMAL", "required": False, "min": 0, "max": 300,
                 "description": "Tax-free amount in lei replacing 300 / 200 for a partial month (OUG 89/2025 art. III(4): prorated by the part of the month at the minimum wage); capped at the month's statutory amount"},
            ],
        },
        "parameters": {name: parameter(spec) for name, spec in PARAMS.items()},
        "tables": {
            "basicDeduction": {
                "kind": "EXACT",
                "description": "Basic personal deduction as a share of the minimum wage, by 50-lei band of gross monthly salary above the minimum wage (band 0: up to the minimum wage) and number of dependents (Codul fiscal art. 77 alin. (4))",
                "columns": ["0", "1", "2", "3", "4"],
                "versions": [
                    {"from": FROM, "to": TO, "source": "cod-fiscal",
                     "rows": [{"key": str(band), "label": "up to the minimum wage" if band == 0 else f"minimum wage + {50 * band - 49} to + {50 * band} lei",
                               "values": [str(v) for v in values]}
                              for band, values in sorted(DEDUCTION.items())]}
                ],
            }
        },
        "steps": [
            {"id": "calendarMonth", "type": "REQUIRE", "condition": "#isCalendarMonth",
             "message": "Romanian salary tax is monthly: the pay period must be a whole calendar month"},
            {"id": "noPaidSickOrMaternityLeave", "type": "REQUIRE", "condition": "#leavePay['SICK'] == 0 and #leavePay['MATERNITY'] == 0",
             "message": "Paid sick or maternity leave is not supported by this template: medical-leave indemnities are computed on the average income of the last 6 months and follow their own tax and contribution rules (OUG 158/2005)"},
            {"id": "noPreTaxDeductions", "type": "REQUIRE", "condition": "#preTaxDeductions == 0",
             "message": "Pre-tax deductions are not supported by this template: union dues, voluntary pension and health insurance (art. 78 alin. (2) lit. a)) are not modelled"},
            {"id": "grossIncome", "type": "VALUE", "expr": "#pos(#taxableEarnings - #reductions)"},
            {"id": "socialIncome", "type": "VALUE", "expr": "#pos(#socialEarnings - #reductions)"},
            {"id": "nonTaxableEligible", "type": "VALUE", "source": "oug-89-2025",
             "expr": main_job + " and #employee['contractBaseSalaryAtMinimum'] and #fte == 1 and #grossIncome > 0 and #grossIncome <= #p['nonTaxableGrossCap'] ? 1 : 0"},
            {"id": "partialMonthNonTaxable", "type": "REQUIRE",
             "when": "#nonTaxableEligible == 1 and #employee['nonTaxableAmountOverride'] == null",
             "condition": "#fullPeriod and #reductions == 0",
             "message": "Partial month at the minimum wage: the tax-free amount is prorated (OUG 89/2025 art. III(4)), so set the employee's nonTaxableAmountOverride"},
            {"id": "nonTaxable", "type": "VALUE", "when": "#nonTaxableEligible == 1", "source": "oug-89-2025",
             "expr": "#min(#employee['nonTaxableAmountOverride'] != null ? #min(#employee['nonTaxableAmountOverride'], #p['nonTaxableAmount']) : #p['nonTaxableAmount'], #grossIncome)"},
            {"id": "contributionBase", "type": "VALUE", "expr": "#pos(#socialIncome - #nonTaxable)", "source": "cod-fiscal"},
            {"id": "cas", "type": "CONTRIBUTION", "label": "Pension contribution (CAS)", "ledger": "PENSION_CONTRIBUTION", "source": "cod-fiscal",
             "base": "#contributionBase", "employee": {"rate": "#p['casRate']"}},
            {"id": "cass", "type": "CONTRIBUTION", "label": "Health contribution (CASS)", "ledger": "MEDICARE", "source": "cod-fiscal",
             "base": "#contributionBase", "employee": {"rate": "#p['cassRate']"}},
            {"id": "partialMonthMinimum", "type": "REQUIRE",
             "when": "#socialIncome > 0 and " + no_exception + " and #employee['minimumContributionBase'] == null and #contributionBase < " + floor_default,
             "condition": "#fullPeriod and #reductions == 0", "source": "anaf-ghid-cas-minim",
             "message": "Pay is below the minimum wage in a partial month: the minimum CAS/CASS base is prorated by working days (art. 146 alin. (5^6)), so set the employee's minimumContributionBase"},
            {"id": "minimumBase", "type": "VALUE", "when": "#socialIncome > 0 and " + no_exception, "source": "oug-89-2025",
             "expr": "#employee['minimumContributionBase'] != null ? #employee['minimumContributionBase'] : " + floor_default},
            {"id": "casMinimum", "type": "CONTRIBUTION", "label": "CAS up to the minimum wage, paid by the employer", "ledger": "PENSION_CONTRIBUTION", "source": "cod-fiscal",
             "when": "#minimumBase > 0", "base": "#minimumBase",
             "employer": {"amount": "#pos(#round(#minimumBase * #p['casRate'], 0, 'HALF_UP') - #cas_employee)"}},
            {"id": "cassMinimum", "type": "CONTRIBUTION", "label": "CASS up to the minimum wage, paid by the employer", "ledger": "MEDICARE", "source": "cod-fiscal",
             "when": "#minimumBase > 0", "base": "#minimumBase",
             "employer": {"amount": "#pos(#round(#minimumBase * #p['cassRate'], 0, 'HALF_UP') - #cass_employee)"}},
            {"id": "cam", "type": "CONTRIBUTION", "label": "Work insurance contribution (CAM)", "ledger": "OTHER", "source": "cod-fiscal",
             "base": "#contributionBase", "employer": {"rate": "#p['camRate']"}},
            {"id": "taxableNet", "type": "VALUE", "expr": "#pos(#grossIncome - #nonTaxable - #cas_employee - #cass_employee)"},
            {"id": "deductionBand", "type": "VALUE", "when": main_job + " and #grossIncome > 0", "source": "cod-fiscal",
             "expr": "#grossIncome <= #p['minimumWage'] ? 0 : #divide(#grossIncome - #p['minimumWage'], #p['deductionBandWidth'], 0, 'CEILING')"},
            {"id": "withinDeductionLimit", "type": "VALUE", "source": "cod-fiscal",
             "expr": main_job + " and #grossIncome > 0 and #grossIncome <= #p['minimumWage'] + #p['deductionLimitAboveMinimum'] ? 1 : 0"},
            {"id": "basicDeduction", "type": "VALUE", "when": "#withinDeductionLimit == 1", "source": "cod-fiscal",
             "expr": "#round(#table.lookup('basicDeduction', #deductionBand, #min(#employee['dependents'], #p['maxDependentsColumn'])) * #p['minimumWage'], 0, 'HALF_DOWN')"},
            {"id": "youthDeduction", "type": "VALUE", "when": "#withinDeductionLimit == 1", "source": "cod-fiscal",
             "expr": "#ageAtPeriodEnd < #p['youthAgeLimit'] ? #round(#p['minimumWage'] * #p['youthDeductionRate'], 0, 'HALF_DOWN') : 0"},
            {"id": "childDeduction", "type": "VALUE", "when": main_job + " and #grossIncome > 0", "source": "cod-fiscal",
             "expr": "#p['schoolChildDeduction'] * #employee['schoolChildren']"},
            {"id": "personalDeduction", "type": "VALUE", "source": "cod-fiscal",
             "expr": "#min(#basicDeduction + #youthDeduction + #childDeduction, #taxableNet)"},
            {"id": "incomeTax", "type": "TAX", "label": "Income tax on salary", "ledger": "INCOME_TAX", "source": "cod-fiscal",
             "when": "!#employee['incomeTaxExempt']",
             "base": "#round(#pos(#taxableNet - #personalDeduction), 0, 'HALF_DOWN')", "rate": "#p['taxRate']"},
        ],
        "limitations": [
            "Not modelled: medical-leave indemnities (concediu medical, OUG 158/2005), computed on the average income of the last 6 months with their own tax and contribution treatment; pay kept for sick or maternity leave days is refused.",
            "Monthly salary for whole calendar months in 2026, in RON; values follow the month the salary relates to (minimum wage and tax-free amount change on 1 July). Back pay for earlier periods taxed on the payment-date rules (art. 78 alin. (3)) is not modelled.",
            "The basic deduction band uses the whole gross salary of the month, including the OUG 89/2025 tax-free amount (Norme pct. 14 alin. (1)); no official example settles this.",
            "CAS, CASS, CAM and income tax are rounded to the leu with 50 bani rounded up (reproducing the published minimum-wage net pay); the tax base and the deduction are rounded to the leu with 50 bani and under dropped (Norme pct. 4, art. 66). No official text was found for the rounding of the contributions and of the tax itself.",
            "Meal and holiday vouchers count for income tax and CASS but not for CAS or CAM, and are left out of the OUG 89/2025 cap; the template treats all social earnings alike, so vouchers must be kept out of the tenant's pay components.",
            "The employer's CAS/CASS top-up up to the minimum wage (art. 146 alin. (5^9)) is neither deducted from nor added to the employee's tax base, so the employee's net pay is unchanged; no official text was found on this, and practice examples agree.",
            "Partial months need the prorated tax-free amount and minimum contribution base entered per employee; the minimum-contribution exception for several contracts (art. 146 alin. (5^7) lit. e)) is entered, not verified.",
            "The construction and agriculture minimum wages, special or difficult working conditions (art. 138 lit. b)-c)), the R&D exemption (art. 60 pct. 3) and the deductible union dues, voluntary pension and health insurance are not modelled; employees with pre-tax deductions are refused.",
            "The youth deduction applies when the employee is under 26 at the end of the month; the employee's birth date is required at the main job when gross pay is within minimum wage + 2,000 lei.",
            "Status LAW_DERIVED: values are cited from the acts; the cases are derived from them, two reproduce independently published minimum-wage net pay.",
        ],
        "testCases": test_cases(),
    }
    with open(sys.argv[1], "w", encoding="utf-8") as f:
        json.dump(template, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("wrote", sys.argv[1], "with", len(template["testCases"]), "test cases")


main()
