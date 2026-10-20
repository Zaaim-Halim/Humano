"""Build src/main/resources/payroll-templates/bg/bg-employment.json.

Input: annexes.json produced by bg_annex.py (and checked by bg_check.py) from the State Gazette text of
the 2026 State Social Security Budget Act. Statutory rates are written out below with the article they
come from.

The test cases' expected amounts are computed by expected_lines() — an implementation written directly
from the cited articles, independent of the Java engine. The engine must reproduce them to the cent.
This guards against engine/template mistakes; it does not replace an official worked example (none was
found), which is why the template's status is LAW_DERIVED.

Usage: python3 -I build_template.py annexes.json ../../../src/main/resources/payroll-templates/bg/bg-employment.json
"""
import json
import re
import sys
from decimal import ROUND_HALF_UP, Decimal

RETRIEVED = "2026-10-20"
VALID_FROM, VALID_TO = "2026-01-01", "2026-12-31"
H1_TO, H2_FROM = "2026-07-31", "2026-08-01"

SOURCES = {
    "zbdoo-2026": {
        "title": "Закон за бюджета на държавното обществено осигуряване за 2026 г. (ДВ, бр. 68 от 28.07.2026, в сила от 01.01.2026) — чл. 9, 14, 15; приложения 1, 1А, 2, 2А",
        "url": "https://www.mlsp.government.bg/uploads/7/tpoout/2026/zakon-za-budjeta-na-dyrjavnoto-obsestveno-osigurqvane-za-2026-g.pdf",
    },
    "kso": {
        "title": "Кодекс за социалното осигуряване — чл. 6, ал. 1 (размери на вноските), ал. 3, т. 7–9 (разпределение); чл. 157, ал. 1, т. 1 и ал. 3 (УПФ)",
        "url": "https://lex.bg/laws/ldoc/1597824512",
    },
    "zbnzok-2026": {
        "title": "Закон за бюджета на НЗОК за 2026 г. (ДВ, бр. 68 от 28.07.2026) — чл. 2 (здравноосигурителна вноска 8 на сто)",
        "url": "https://www.nhif.bg/bg/regulatory/laws",
    },
    "zzo": {
        "title": "Закон за здравното осигуряване — чл. 40, ал. 1, т. 1 (база = осигурителен доход по КСО; съотношение 60:40)",
        "url": "https://lex.bg/laws/ldoc/2134412800",
    },
    "zddfl": {
        "title": "Закон за данъците върху доходите на физическите лица — чл. 24 (облагаем доход), чл. 42 (месечна данъчна основа; 10 на сто)",
        "url": "https://lex.bg/laws/ldoc/2135538631",
    },
    "noi-2026-08": {
        "title": "НОИ разяснява: от 1 август 2026 г. нови размери на осигурителния доход",
        "url": "https://www.noi.bg/dohod01082026/",
    },
    "kik-mod-2026": {
        "title": "KIK-info, МОД 2026 от 01.08.2026 — published table of annex 1A; confirms extracted values and the groups that occupation-specific rows cover",
        "url": "https://www.kik-info.com/spravochnik/mod.php",
    },
    "noi-tzpb-2026-08": {
        "title": "НОИ разяснява: вноски за фонд ТЗПБ от 1 август 2026 г. (7 променени кода по КИД-2025)",
        "url": "https://www.noi.bg/tzpb01082026/",
    },
}
for s in SOURCES.values():
    s["retrieved"] = RETRIEVED

# Rates as fractions. Pension: KSO art. 6(1) items 1-2 (17.8% / 12.8%) + item 4 (2 x 1 point, 0.56 employer /
# 0.44 employee each); split KSO art. 6(3) items 8-9 (7.9 / 9.9 and 5.7 / 7.1) + the item-4 increases.
RATES = {
    "pensionEmployeeBefore1960": ("0.0878", "kso"),
    "pensionEmployerBefore1960": ("0.1102", "kso"),
    "pensionEmployeeFrom1960": ("0.0658", "kso"),
    "pensionEmployerFrom1960": ("0.0822", "kso"),
    # KSO art. 157(1)(1)(c) 5%; split art. 157(3): 2.2 employee / 2.8 employer
    "universalFundEmployee": ("0.022", "kso"),
    "universalFundEmployer": ("0.028", "kso"),
    # KSO art. 6(1) item 5: 3.5%; art. 6(3) item 7: 60:40
    "sicknessEmployee": ("0.014", "kso"),
    "sicknessEmployer": ("0.021", "kso"),
    # KSO art. 6(1) item 6: 1%; art. 6(3) item 7: 60:40
    "unemploymentEmployee": ("0.004", "kso"),
    "unemploymentEmployer": ("0.006", "kso"),
    # NHIF budget act 2026 art. 2: 8%; ZZO art. 40(1)(1): 60:40
    "healthEmployee": ("0.032", "zbnzok-2026"),
    "healthEmployer": ("0.048", "zbnzok-2026"),
    # ZDDFL art. 42(4)
    "incomeTaxRate": ("0.10", "zddfl"),
    # ZBDOO 2026 annex 1 / 1A, note 2: specialised enterprises of persons with disabilities pay on 50% of the minimum
    "specializedEnterpriseShare": ("0.5", "zbdoo-2026"),
}
MAX_INSURABLE = [(VALID_FROM, H1_TO, "2111.64"), (H2_FROM, VALID_TO, "2300.00")]  # ZBDOO 2026 art. 9


def whole_year(value, source):
    return [{"from": VALID_FROM, "to": VALID_TO, "value": value, "source": source}]


def code_tokens(raw):
    return re.findall(r"\d{1,2}(?:\.\d{1,2})?", raw)


def repaired(rows_a, rows_b):
    """Annex 1 and 1A list the same rows. Where one side's code cell lost tokens into the name column in
    extraction, use the longer code list — only if the missing tokens appear in the shorter side's name."""
    out_a, out_b = [], []
    for a, b in zip(rows_a, rows_b, strict=True):
        assert a["seq"] == b["seq"]
        if a["rawCodes"] != b["rawCodes"]:
            longer, shorter = (a, b) if len(code_tokens(a["rawCodes"])) > len(code_tokens(b["rawCodes"])) else (b, a)
            missing = [t for t in code_tokens(longer["rawCodes"]) if t not in code_tokens(shorter["rawCodes"])]
            assert missing and all(t in shorter["name"] for t in missing), (a["seq"], a["rawCodes"], b["rawCodes"])
            fixed = dict(shorter, codes=longer["codes"], rawCodes=longer["rawCodes"], name=re.sub(r"[\d.;]+", "", shorter["name"]))
            a, b = (longer, fixed) if longer is a else (fixed, longer)
        out_a.append(a)
        out_b.append(b)
    return out_a, out_b


# Occupation-specific rows fill only some NKPD groups; the PDF extraction loses which. The groups come
# from the KIK-info rendering of annex 1A (source kik-mod-2026) and match the occupations' NKPD groups:
# 81 professional footballer -> 3 (3421), 84 head of clinic -> 1, 85 nurse/midwife/... -> 1-3,
# 86 religious organisations -> 2 (2636). Annex 1 lists the same rows.
PARTIAL_ROW_GROUPS = {81: [3], 84: [1], 85: [1, 2, 3], 86: [2]}


def minimum_rows(rows, annex):
    out = []
    for r in rows:
        row = {"label": f"annex {annex} row {r['seq']}: {r['name']}"}
        if r["codes"] is not None:
            row["codes"] = r["codes"]
        if "byGroup" in r:
            row["values"] = r["byGroup"]
        else:
            groups = PARTIAL_ROW_GROUPS[r["seq"]]
            assert len(groups) == len(r["partialValues"]), (r["seq"], r["partialValues"])
            values = [None] * 9
            for g, v in zip(groups, r["partialValues"]):
                values[g - 1] = v
            row["values"] = values
        out.append(row)
    return out


def accident_rows(rows):
    return [{"key": r["codes"]["include"][0], "values": [str((Decimal(r["rate"]) / 100).normalize())]} for r in rows]


# ---------------------------------------------------------------- independent expected-amount calculation

def money(x):
    return x.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def expected_lines(case, annexes):
    """The amounts the law gives for a test case, computed directly (no engine)."""
    month = case["periodStart"][:7]
    second_half = month >= "2026-08"
    gross = Decimal(case["grossEarnings"])
    born = int(case["birthDate"][:4])
    kid = case["employer"]["kidCode"]
    etype = case["employer"].get("employerType", "STANDARD")
    emp = case.get("employee", {})
    cap = Decimal("2300.00" if second_half else "2111.64")
    if etype == "BUDGET_ENTERPRISE":
        minimum = Decimal(0)
    elif "minimumInsurableIncome" in emp:
        minimum = Decimal(emp["minimumInsurableIncome"])
    else:
        table = minimum_rows(annexes["annex1A" if second_half else "annex1"], "")
        digits = kid.replace(".", "")
        group = int(emp["qualificationGroup"])
        hits = [r for r in table if "codes" in r and any(digits.startswith(c.replace(".", "")) for c in r["codes"]["include"])
                and not any(digits.startswith(c.replace(".", "")) for c in r["codes"]["exclude"])]
        values = {Decimal(r["values"][group - 1]) for r in hits if r["values"][group - 1] is not None}
        assert len(values) == 1, (kid, group, hits)
        minimum = values.pop()
        if etype == "SPECIALIZED_ENTERPRISE":
            minimum = money(minimum * Decimal("0.5"))
    insurable = min(max(gross, minimum), cap) if gross > 0 else Decimal(0)
    rate = lambda k: Decimal(RATES[k][0])
    lines = []

    def contribution(step, employee_rate, employer_rate):
        for side, r in (("EMPLOYEE", employee_rate), ("EMPLOYER", employer_rate)):
            if r is not None and money(insurable * r) != 0:
                lines.append({"step": step, "side": side, "amount": str(money(insurable * r))})

    if born < 1960:
        contribution("pension", rate("pensionEmployeeBefore1960"), rate("pensionEmployerBefore1960"))
    else:
        contribution("pension", rate("pensionEmployeeFrom1960"), rate("pensionEmployerFrom1960"))
        step = "universalFundRedirected" if emp.get("pensionFund") == "DOO" else "universalFund"
        contribution(step, rate("universalFundEmployee"), rate("universalFundEmployer"))
    contribution("sickness", rate("sicknessEmployee"), rate("sicknessEmployer"))
    contribution("unemployment", rate("unemploymentEmployee"), rate("unemploymentEmployer"))
    contribution("health", rate("healthEmployee"), rate("healthEmployer"))
    accident = {r["codes"]["include"][0]: Decimal(r["rate"]) / 100 for r in annexes["annex2A" if second_half else "annex2"]}[kid[:2]]
    contribution("workAccident", None, accident)
    employee_total = sum(Decimal(l["amount"]) for l in lines if l["side"] == "EMPLOYEE")
    tax = money(max(gross - employee_total, Decimal(0)) * rate("incomeTaxRate"))
    if tax != 0:
        lines.append({"step": "incomeTax", "side": "TAX", "amount": str(tax)})
    return lines


def case(name, evidence, start, end, gross, employer, employee, born="1985-04-12", full=True, error=None):
    c = {
        "name": name,
        "evidence": evidence,
        "periodStart": start,
        "periodEnd": end,
        "frequency": "MONTHLY",
        "grossEarnings": gross,
        "fullPeriod": full,
        "employer": employer,
        "employee": employee,
    }
    if born is not None:
        c["birthDate"] = born
    if error:
        c["expectError"] = error
    return c


def test_cases(annexes):
    law = "KSO art. 6 & 157, ZZO art. 40, NHIF act art. 2, ZDDFL art. 42, ZBDOO 2026 art. 9 & annexes; amounts from build_template.py expected_lines()"
    computed = [
        case("ordinary month, born after 1959", "2000 between the annex 1 minimum and the 2111.64 cap. " + law,
             "2026-03-01", "2026-03-31", "2000.00", {"kidCode": "62"}, {"qualificationGroup": "2"}),
        case("first-half cap", "5000 capped at 2111.64 (art. 9 item 1). " + law,
             "2026-07-01", "2026-07-31", "5000.00", {"kidCode": "62"}, {"qualificationGroup": "2"}, born="1990-11-30"),
        case("second-half cap, born before 1960", "3000 capped at 2300 from 1 August (art. 9 item 2); pension 8.78/11.02, no universal fund. " + law,
             "2026-09-01", "2026-09-30", "3000.00", {"kidCode": "62"}, {"qualificationGroup": "1"}, born="1958-06-15"),
        case("raised to the annex 1 minimum", "500 raised to 550.66 (annex 1, KID 62, group 2). " + law,
             "2026-03-01", "2026-03-31", "500.00", {"kidCode": "62"}, {"qualificationGroup": "2"}),
        case("raised to the annex 1A minimum", "700 raised to 826.00 (annex 1A row 70, KID 62, group 2). " + law,
             "2026-10-01", "2026-10-31", "700.00", {"kidCode": "62"}, {"qualificationGroup": "2"}),
        case("specialised enterprise pays on half the minimum", "annex 1A note 2: 50% of 826.00 = 413.00. " + law,
             "2026-10-01", "2026-10-31", "400.00", {"kidCode": "62", "employerType": "SPECIALIZED_ENTERPRISE"}, {"qualificationGroup": "2"}),
        case("budget enterprise has no minimum", "annex 1A note 4: no minimum, so 400 is insured as paid. " + law,
             "2026-10-01", "2026-10-31", "400.00", {"kidCode": "84", "employerType": "BUDGET_ENTERPRISE"}, {}),
        case("partial month above the minimum", "900 >= 826, so the unprorated minimum cannot bind. " + law,
             "2026-10-01", "2026-10-31", "900.00", {"kidCode": "62"}, {"qualificationGroup": "2"}, full=False),
        case("universal-fund contribution moved to the pension fund", "KSO art. 4b / art. 6(3) item 10: same 5% split 2.8/2.2. " + law,
             "2026-03-01", "2026-03-31", "2000.00", {"kidCode": "62"}, {"qualificationGroup": "2", "pensionFund": "DOO"}),
        case("rows that agree resolve without an override", "KID 86.10, group 2: the hospital row and the nurse-only row both give 826.00. " + law,
             "2026-10-01", "2026-10-31", "700.00", {"kidCode": "86.10"}, {"qualificationGroup": "2"}),
        case("occupation-specific row resolved by an override", "hospital nurse: annex 1A row 85 is ambiguous, minimum set to 936.00 by the tenant. " + law,
             "2026-10-01", "2026-10-31", "900.00", {"kidCode": "86.10"}, {"minimumInsurableIncome": "936.00"}),
        case("work-accident rate change on 1 August", "KID 10: 0.7% in annex 2, 0.9% in annex 2A (NOI notice). " + law,
             "2026-08-01", "2026-08-31", "1500.00", {"kidCode": "10.71"}, {"qualificationGroup": "3"}),
    ]
    for c in computed:
        c["expectLines"] = expected_lines(c, annexes)
    zero = case("no earnings, no lines", "nothing is insured or taxed", "2026-03-01", "2026-03-31", "0", {"kidCode": "62"}, {"qualificationGroup": "2"})
    zero["expectLines"] = []
    errors = [
        case("division-level code where the annex distinguishes classes", "annex 1A has rows for 86 except 86.1 and for 86.1",
             "2026-10-01", "2026-10-31", "1500.00", {"kidCode": "86"}, {"qualificationGroup": "3"}, error="distinguishes codes below 86"),
        case("ambiguous occupation-specific rows refuse", "86.10 group 1: rows 76, 84, 85 give 1161, 959, 936",
             "2026-10-01", "2026-10-31", "1500.00", {"kidCode": "86.10"}, {"qualificationGroup": "1"}, error="has more than one value for 86.10 / 1"),
        case("partial month below the minimum refuses", "the minimum must be prorated by Bulgarian working days",
             "2026-10-01", "2026-10-31", "500.00", {"kidCode": "62"}, {"qualificationGroup": "2"}, full=False, error="prorated"),
        case("labour category II refuses", "professional pension fund not supported",
             "2026-03-01", "2026-03-31", "2000.00", {"kidCode": "62"}, {"qualificationGroup": "2", "labourCategory": "2"}, error="Labour categories I and II"),
        case("disability relief refuses", "ZDDFL art. 18 not supported",
             "2026-03-01", "2026-03-31", "2000.00", {"kidCode": "62"}, {"qualificationGroup": "2", "disabilityTaxRelief": "true"}, error="art. 18"),
        dict(case("pre-tax deductions refuse", "ZDDFL art. 19 reliefs not modelled",
             "2026-03-01", "2026-03-31", "2000.00", {"kidCode": "62"}, {"qualificationGroup": "2"}, error="Pre-tax deductions are not supported"),
             preTaxDeductions="100.00"),
        case("missing qualification group refuses", "the annex lookup needs the NKPD group",
             "2026-03-01", "2026-03-31", "2000.00", {"kidCode": "62"}, {}, error="qualificationGroup"),
        case("missing birth date refuses", "pension rates depend on the birth cohort",
             "2026-03-01", "2026-03-31", "2000.00", {"kidCode": "62"}, {"qualificationGroup": "2"}, born=None, error="no birth date"),
        case("2027 is not covered", "2027 values are not yet published in this template",
             "2027-01-01", "2027-01-31", "2000.00", {"kidCode": "62"}, {"qualificationGroup": "2"}, error="a newer template version is needed"),
    ]
    return computed + [zero] + errors


def main():
    annexes = json.load(open(sys.argv[1], encoding="utf-8"))
    a1, a1A = repaired(annexes["annex1"], annexes["annex1A"])
    fixed = dict(annexes, annex1=a1, annex1A=a1A)
    params = {name: whole_year(value, src) for name, (value, src) in RATES.items()}
    params["maxInsurableIncome"] = [{"from": f, "to": t, "value": v, "source": "zbdoo-2026"} for f, t, v in MAX_INSURABLE]
    no_minimum = "#employer['employerType'] == 'BUDGET_ENTERPRISE' or #employee['minimumInsurableIncome'] != null"
    template = {
        "schemaVersion": 1,
        "id": "bg-employment",
        "name": "Bulgaria — employees under a labour contract",
        "version": "2026.1.0",
        "status": "LAW_DERIVED",
        "jurisdiction": {"country": "BG"},
        "validity": {"from": VALID_FROM, "to": VALID_TO},
        "currency": "EUR",
        "payFrequencies": ["MONTHLY"],
        "taxYearStart": "--01-01",
        "rounding": {"scale": 2, "mode": "HALF_UP"},
        "changelog": [
            {"version": "2026.1.0", "date": RETRIEVED,
             "summary": "2026 rates and limits, including the 1 August 2026 changes to the maximum insurable income, the minimum insurable income (annex 1A) and the work-accident rates (annex 2A)."}
        ],
        "sources": SOURCES,
        "inputs": {
            "employer": [
                {"key": "kidCode", "type": "STRING", "required": True, "pattern": "^\\d{2}(\\.\\d{1,2})?$",
                 "description": "KID-2025 code of the main economic activity (by number of employees): a division (\"62\") or class (\"86.10\")"},
                {"key": "employerType", "type": "ENUM", "required": False, "values": ["STANDARD", "BUDGET_ENTERPRISE", "SPECIALIZED_ENTERPRISE"],
                 "defaultValue": "STANDARD",
                 "description": "STANDARD; BUDGET_ENTERPRISE (no minimum insurable income, annex 1A note 4); SPECIALIZED_ENTERPRISE (50% of the minimum, note 2)"},
            ],
            "employee": [
                {"key": "qualificationGroup", "type": "INTEGER", "required": False, "min": 1, "max": 9,
                 "description": "NKPD qualification group 1–9 (first digit of the occupation code)"},
                {"key": "minimumInsurableIncome", "type": "DECIMAL", "required": False, "min": 0,
                 "description": "Monthly minimum insurable income in EUR replacing the annex lookup — for occupation-specific annex rows, and prorated for partial months below the minimum"},
                {"key": "labourCategory", "type": "ENUM", "required": False, "values": ["1", "2", "3"], "defaultValue": "3",
                 "description": "Labour category; only 3 is supported"},
                {"key": "pensionFund", "type": "ENUM", "required": False, "values": ["UPF", "DOO"], "defaultValue": "UPF",
                 "description": "UPF, or DOO if the universal-fund contribution was moved to the state pension fund (KSO art. 4b)"},
                {"key": "disabilityTaxRelief", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "Whether the employee claims the ZDDFL art. 18 disability relief (not supported)"},
            ],
        },
        "parameters": params,
        "tables": {
            "minimumInsurableIncome": {
                "kind": "HIERARCHICAL",
                "description": "Minimum monthly insurable income by KID-2025 activity and NKPD qualification group (ZBDOO 2026 annex 1 / 1A)",
                "columns": [str(g) for g in range(1, 10)],
                "requireMostSpecific": True,
                "ambiguityHint": "The annex sets occupation-specific minimums here; set the employee's minimumInsurableIncome to the one that applies",
                "versions": [
                    {"from": VALID_FROM, "to": H1_TO, "source": "zbdoo-2026", "rows": minimum_rows(fixed["annex1"], "1")},
                    {"from": H2_FROM, "to": VALID_TO, "source": "zbdoo-2026", "rows": minimum_rows(fixed["annex1A"], "1A")},
                ],
            },
            "workAccidentRate": {
                "kind": "EXACT",
                "description": "Employer work-accident (TZPB) rate by KID-2025 division (ZBDOO 2026 annex 2 / 2A)",
                "versions": [
                    {"from": VALID_FROM, "to": H1_TO, "source": "zbdoo-2026", "rows": accident_rows(annexes["annex2"])},
                    {"from": H2_FROM, "to": VALID_TO, "source": "noi-tzpb-2026-08", "rows": accident_rows(annexes["annex2A"])},
                ],
            },
        },
        "steps": [
            {"id": "labourCategoryThree", "type": "REQUIRE", "condition": "#employee['labourCategory'] == '3'",
             "message": "Labour categories I and II (professional pension fund) are not supported by this template"},
            {"id": "noDisabilityRelief", "type": "REQUIRE", "condition": "!#employee['disabilityTaxRelief']",
             "message": "The ZDDFL art. 18 disability relief is not supported by this template"},
            {"id": "noPreTaxDeductions", "type": "REQUIRE", "condition": "#preTaxDeductions == 0",
             "message": "Pre-tax deductions are not supported by this template: the ZDDFL art. 19 reliefs on the monthly tax base are not modelled"},
            {"id": "calendarMonth", "type": "REQUIRE", "condition": "#isCalendarMonth",
             "message": "Bulgarian contributions are monthly: the pay period must be a whole calendar month"},
            {"id": "remuneration", "type": "VALUE", "expr": "#pos(#socialEarnings - #reductions)"},
            {"id": "qualificationGroupKnown", "type": "REQUIRE", "when": "#remuneration > 0 and !(" + no_minimum + ")",
             "condition": "#employee['qualificationGroup'] != null",
             "message": "The employee's statutory profile needs qualificationGroup (NKPD group 1–9), or a minimumInsurableIncome"},
            {"id": "minimum", "type": "VALUE", "when": "#remuneration > 0",
             "expr": "#employer['employerType'] == 'BUDGET_ENTERPRISE' ? 0 : (#employee['minimumInsurableIncome'] != null ? #employee['minimumInsurableIncome'] : ("
                     "#employer['employerType'] == 'SPECIALIZED_ENTERPRISE' ? #round(#table.lookup('minimumInsurableIncome', #employer['kidCode'], #employee['qualificationGroup']) * #p['specializedEnterpriseShare'], 2, 'HALF_UP')"
                     " : #table.lookup('minimumInsurableIncome', #employer['kidCode'], #employee['qualificationGroup'])))",
             "source": "zbdoo-2026"},
            {"id": "partialMonthMinimum", "type": "REQUIRE", "when": "#remuneration > 0 and !(" + no_minimum + ")",
             "condition": "#fullPeriod or #remuneration >= #minimum",
             "message": "Remuneration is below the full-month minimum insurable income in a partial month (or part-time / unpaid leave). The minimum must be prorated by Bulgarian working days; set the prorated amount as the employee's minimumInsurableIncome"},
            {"id": "insurable", "type": "VALUE", "expr": "#remuneration > 0 ? #min(#max(#remuneration, #minimum), #p['maxInsurableIncome']) : 0",
             "source": "zbdoo-2026"},
            {"id": "pension", "type": "CONTRIBUTION", "label": "Pension fund (DOO)", "ledger": "PENSION_CONTRIBUTION", "source": "kso",
             "base": "#insurable",
             "employee": {"rate": "#birthYear < 1960 ? #p['pensionEmployeeBefore1960'] : #p['pensionEmployeeFrom1960']"},
             "employer": {"rate": "#birthYear < 1960 ? #p['pensionEmployerBefore1960'] : #p['pensionEmployerFrom1960']"}},
            {"id": "universalFund", "type": "CONTRIBUTION", "label": "Universal pension fund (DZPO)", "ledger": "PENSION_CONTRIBUTION", "source": "kso",
             "when": "#birthYear >= 1960 and #employee['pensionFund'] == 'UPF'", "base": "#insurable",
             "employee": {"rate": "#p['universalFundEmployee']"}, "employer": {"rate": "#p['universalFundEmployer']"}},
            {"id": "universalFundRedirected", "type": "CONTRIBUTION", "label": "Pension fund (DOO, in place of the universal fund)",
             "ledger": "PENSION_CONTRIBUTION", "source": "kso",
             "when": "#birthYear >= 1960 and #employee['pensionFund'] == 'DOO'", "base": "#insurable",
             "employee": {"rate": "#p['universalFundEmployee']"}, "employer": {"rate": "#p['universalFundEmployer']"}},
            {"id": "sickness", "type": "CONTRIBUTION", "label": "General illness and maternity", "ledger": "DISABILITY", "source": "kso",
             "base": "#insurable", "employee": {"rate": "#p['sicknessEmployee']"}, "employer": {"rate": "#p['sicknessEmployer']"}},
            {"id": "unemployment", "type": "CONTRIBUTION", "label": "Unemployment", "ledger": "UNEMPLOYMENT", "source": "kso",
             "base": "#insurable", "employee": {"rate": "#p['unemploymentEmployee']"}, "employer": {"rate": "#p['unemploymentEmployer']"}},
            {"id": "health", "type": "CONTRIBUTION", "label": "Health insurance", "ledger": "MEDICARE", "source": "zbnzok-2026",
             "base": "#insurable", "employee": {"rate": "#p['healthEmployee']"}, "employer": {"rate": "#p['healthEmployer']"}},
            {"id": "workAccident", "type": "CONTRIBUTION", "label": "Work accident and occupational disease", "ledger": "WORK_INJURY",
             "source": "zbdoo-2026", "base": "#insurable",
             "employer": {"rate": "#table.lookup('workAccidentRate', #employer['kidCode'].substring(0, 2))"}},
            {"id": "incomeTax", "type": "TAX", "label": "Personal income tax (advance)", "ledger": "INCOME_TAX", "source": "zddfl",
             "base": "#pos(#taxableEarnings - #reductions - #employeeContributions)", "rate": "#p['incomeTaxRate']"},
        ],
        "limitations": [
            "Monthly payroll calendars whose periods are whole calendar months, paid in EUR.",
            "Labour category III only; categories I and II (professional pension fund) are refused.",
            "Partial months, part-time work or unpaid leave with pay below the full-month minimum insurable income need a prorated minimumInsurableIncome: working days come from Bulgaria's official calendar, which Humano does not hold.",
            "Occupation-specific annex rows (e.g. hospital nurses, professional footballers) need the employee's minimumInsurableIncome.",
            "One payroll run per month: remuneration from other runs in the same month is not added before applying the maximum insurable income.",
            "Monthly tax-base reliefs (ZDDFL art. 18 disability, art. 19 voluntary insurance) and the annual tax recalculation are not applied; employees with tenant pre-tax deductions are refused.",
            "Remuneration for work in past months (KSO art. 6(2) redistribution) is treated as current-month income.",
            "Each contribution and the tax are rounded to the cent, half up, line by line; this is common practice but not cited to a legal provision.",
            "Status LAW_DERIVED: every value is cited to the law; no official worked example was found to compare the results against.",
        ],
        "testCases": test_cases(fixed),
    }
    with open(sys.argv[2], "w", encoding="utf-8") as f:
        json.dump(template, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("wrote", sys.argv[2], "with", len(template["testCases"]), "test cases")


main()
