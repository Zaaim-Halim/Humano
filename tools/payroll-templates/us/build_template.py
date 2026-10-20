"""Build src/main/resources/payroll-templates/us/us-federal-employment.json — US federal income tax
withholding, social security, Medicare (incl. Additional Medicare) and FUTA for wages paid in 2026.

Sources: IRS Publication 15-T (2026) — Worksheet 1A and the Annual Percentage Method tables for automated
payroll systems, the nonresident alien adjustment (Tables 1 and 2), the rounding rules — and Publication 15
(2026) — social security and Medicare rates, the $184,500 wage base, the $200,000 Additional Medicare
withholding threshold, FUTA 6.0% on $7,000 less the 5.4% credit.

Before writing, every constant is checked against the publication's own text and the independent
implementation below must reproduce every printed amount of the Wage Bracket Method tables (sections 2
and 3, weekly to monthly) at each bracket's midpoint. Test cases sample those rows as official vectors.

Usage: python3 -I build_template.py <p15t.txt from ../bg/pdftext.py> ../../../src/main/resources/payroll-templates/us/us-federal-employment.json
"""
import json
import os
import sys
from decimal import ROUND_HALF_UP, Decimal as D

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pub15t  # noqa: E402

RETRIEVED = "2026-10-20"
FROM, TO = "2026-01-01", "2026-12-31"

SOURCES = {
    "irs-pub15t-2026": {
        "title": "IRS Publication 15-T (2026), Federal Income Tax Withholding Methods — Worksheet 1A, 2026 Annual Percentage Method tables, Withholding Adjustment for Nonresident Alien Employees (Tables 1 and 2), Rounding",
        "url": "https://www.irs.gov/pub/irs-pdf/p15t.pdf",
    },
    "irs-pub15-2026": {
        "title": "IRS Publication 15 (2026), (Circular E) Employer's Tax Guide — section 9 (social security and Medicare: 6.2% to $184,500, 1.45%, Additional Medicare 0.9% over $200,000) and section 14 (FUTA 6.0% on $7,000, credit up to 5.4%)",
        "url": "https://www.irs.gov/pub/irs-pdf/p15.pdf",
    },
    "irs-fw4-2026": {
        "title": "IRS Form W-4 (2026), Employee's Withholding Certificate — steps 1(c), 2(c), 3, 4(a)-(c) and exemption from withholding",
        "url": "https://www.irs.gov/pub/irs-pdf/fw4.pdf",
    },
}
for s in SOURCES.values():
    s["retrieved"] = RETRIEVED

STATUSES = {"SINGLE": "SINGLE", "MARRIED_FILING_JOINTLY": "MFJ", "HEAD_OF_HOUSEHOLD": "HOH"}
# Worksheet 1A line 1g (no Step 2 checkbox), line 1k (2019 or earlier Forms W-4, per allowance).
LINE_1G = {"MARRIED_FILING_JOINTLY": D("12900"), "SINGLE": D("8600"), "HEAD_OF_HOUSEHOLD": D("8600")}
ALLOWANCE = D("4300")
# Nonresident alien adjustment per payroll period: Table 1 (first paid before 2020, no 2020+ W-4), Table 2.
NRA = {"WEEKLY": ("226.90", "309.60"), "BIWEEKLY": ("453.80", "619.20"), "SEMIMONTHLY": ("491.70", "670.80"),
       "MONTHLY": ("983.30", "1341.70")}
PERIODS = {"WEEKLY": 52, "BIWEEKLY": 26, "SEMIMONTHLY": 24, "MONTHLY": 12}
SS_RATE, SS_BASE = D("0.062"), D("184500")
MEDICARE_RATE, ADDITIONAL_RATE, ADDITIONAL_THRESHOLD = D("0.0145"), D("0.009"), D("200000")
FUTA_RATE, FUTA_CREDIT, FUTA_BASE = D("0.060"), D("0.054"), D("7000")
TOP = D("999999999999")


# ------------------------------------------------------------------ checks against the publication

def check_publication(text, schedules):
    flat = " ".join(text.split())
    for needle in [
        "enter $12,900 if the taxpayer is married filing jointly or $8,600 otherwise",
        "Multiply line 1j by $4,300",
        "Weekly ............................ $309.60", "Biweekly ........................... 619.20",
        "Semimonthly ........................ 670.80", "Monthly ........................... 1,341.70",
        "Weekly ............................ $226.90", "Biweekly ........................... 453.80",
        "Semimonthly ........................ 491.70", "Monthly ........................... 983.30",
        "$2.30 becomes $2 and $2.50 becomes $3",
    ]:
        assert needle in flat, needle
    # Each published tentative amount (column C) must agree with the brackets below it (to $1: the IRS
    # rounds some checkbox bracket bounds, e.g. $108,938).
    for key, rows in schedules.items():
        cumulative = D(0)
        for (a, b, c, d), nxt in zip(rows, rows[1:] + [None]):
            assert abs(c - cumulative) <= 1, (key, a, c, cumulative)
            if nxt:
                cumulative += (b - a) * d


def check_pub15(text):
    flat = " ".join(text.split())
    for needle in ["the social security tax rate is 6.2%", "The social security wage base limit is $184,500",
                   "tax rate for Medicare is 1.45%", "0.9% Additional Medicare T ax from wages you pay to an employee in excess of $200,000",
                   "For 2026, the FUTA tax rate is 6.0%", "credit may be as much as 5.4% of FUTA taxable wages",
                   "single FUTA tax wage base limit ($7,000 for 2026)"]:
        assert needle in flat, needle


# ------------------------------------------------------------------ independent implementation

def schedule_tax(schedules, kind, status, amount):
    for a, b, c, d in schedules[(kind, status)]:
        if b is None or amount < b:
            return c + (amount - a) * d


def q10(x):
    return x.quantize(D("1E-10"), rounding=ROUND_HALF_UP)


def federal_income_tax(schedules, freq, wages, w4, round_dollars=False):
    """Worksheet 1A. Unrounded until the end: per-period divisions kept to 10 decimals, the amount to
    withhold rounded to the cent (or to the dollar if the employer chose to round, as Pub 15-T allows)."""
    if w4.get("exemptFromWithholding") == "true":
        return None
    n = PERIODS[freq]
    modern = w4.get("w4Version", "FORM_2020_OR_LATER") == "FORM_2020_OR_LATER"
    status = w4["filingStatus"]
    if w4.get("nonresidentAlien") == "true":
        wages += D(NRA[freq][1 if modern else 0])
    annual = wages * n
    if modern:
        checkbox = w4.get("multipleJobs") == "true"
        adjusted = annual + D(w4.get("otherIncome", "0")) - D(w4.get("deductions", "0")) - (D(0) if checkbox else LINE_1G[status])
        kind = "CHECKBOX" if checkbox else "STANDARD"
    else:
        adjusted = annual - int(w4.get("allowances", "0")) * ALLOWANCE
        kind = "STANDARD"
    tentative = q10(schedule_tax(schedules, kind, STATUSES[status], max(adjusted, D(0))) / n)
    credits = q10(D(w4.get("dependentsAmount", "0")) / n) if modern else D(0)
    amount = max(tentative - credits, D(0)) + D(w4.get("extraWithholding", "0"))
    return amount.quantize(D(1) if round_dollars else D("0.01"), rounding=ROUND_HALF_UP)


def cents(x):
    return x.quantize(D("0.01"), rounding=ROUND_HALF_UP)


def expected_lines(schedules, c):
    emp, er = c["employee"], c.get("employer", {})
    by_type = c.get("preTaxDeductionsByType", {})
    retirement, insurance = D(by_type.get("RETIREMENT", "0")), D(by_type.get("INSURANCE", "0"))
    gross = D(c["grossEarnings"])
    ytd = {k: D(v) for k, v in c.get("ytd", {}).items()}
    bf = emp.get("broughtForwardYear") == "2026"
    lines = []
    fit = federal_income_tax(schedules, c["frequency"], max(gross - retirement - insurance, D(0)), emp,
                             er.get("roundToWholeDollars") == "true")
    if fit:
        lines.append({"step": "federalIncomeTax", "side": "TAX", "amount": str(fit)})
    fica = max(gross - insurance, D(0))
    ss_ytd = ytd.get("socialSecurity.EMPLOYEE.base", D(0)) + (D(emp.get("broughtForwardSocialSecurityWages", "0")) if bf else 0)
    ss_wages = min(fica, max(SS_BASE - ss_ytd, D(0)))
    if ss_wages > 0:
        lines += [{"step": "socialSecurity", "side": side, "amount": str(cents(ss_wages * SS_RATE))} for side in ("EMPLOYEE", "EMPLOYER")]
    if fica > 0:
        lines += [{"step": "medicare", "side": side, "amount": str(cents(fica * MEDICARE_RATE))} for side in ("EMPLOYEE", "EMPLOYER")]
    med_ytd = ytd.get("medicare.EMPLOYEE.base", D(0)) + (D(emp.get("broughtForwardMedicareWages", "0")) if bf else 0)
    extra = max(med_ytd + fica - ADDITIONAL_THRESHOLD, D(0)) - max(med_ytd - ADDITIONAL_THRESHOLD, D(0))
    if extra > 0:
        lines.append({"step": "additionalMedicare", "side": "EMPLOYEE", "amount": str(cents(extra * ADDITIONAL_RATE))})
    futa_ytd = ytd.get("futa.EMPLOYER.base", D(0)) + (D(emp.get("broughtForwardFutaWages", "0")) if bf else 0)
    futa_wages = min(fica, max(FUTA_BASE - futa_ytd, D(0)))
    if futa_wages > 0 and er.get("futaExempt") != "true":
        lines.append({"step": "futa", "side": "EMPLOYER", "amount": str(cents(futa_wages * (FUTA_RATE - FUTA_CREDIT)))})
    return [l for l in lines if D(l["amount"]) != 0]


def check_wage_brackets(schedules, rows):
    """Every printed amount of sections 2 and 3 (weekly-monthly) at the bracket midpoint, rounded to $1."""
    checked = 0
    for r in rows:
        if r["frequency"] not in PERIODS:
            continue
        mid = (r["lower"] + r["upper"]) / 2
        for column, printed in r["amounts"].items():
            got = federal_income_tax(schedules, r["frequency"], mid, w4_for(r, column), round_dollars=True)
            assert got == printed, (r["section"], r["frequency"], column, r["lower"], printed, got)
            checked += 1
    return checked


def w4_for(row, column):
    if row["section"] == 2:
        status, kind = column
        name = {v: k for k, v in STATUSES.items()}[status]
        return {"filingStatus": name, "multipleJobs": "true" if kind == "CHECKBOX" else "false"}
    status, allowances = column
    return {"w4Version": "FORM_2019_OR_EARLIER", "filingStatus": "MARRIED_FILING_JOINTLY" if status == "MARRIED" else "SINGLE",
            "allowances": str(allowances)}


# ------------------------------------------------------------------ test cases

PERIOD = {
    "WEEKLY": ("2026-06-08", "2026-06-14", "2026-06-19"),
    "BIWEEKLY": ("2026-06-01", "2026-06-14", "2026-06-19"),
    "SEMIMONTHLY": ("2026-06-01", "2026-06-15", "2026-06-15"),
    "MONTHLY": ("2026-06-01", "2026-06-30", "2026-06-30"),
}
COLUMN_LABEL = {"MFJ": "married filing jointly", "HOH": "head of household", "SINGLE": "single",
                "STANDARD": "standard", "CHECKBOX": "Step 2 checkbox", "MARRIED": "married"}


def case(name, evidence, freq, gross, employee, employer=None, period=None, **extra):
    start, end, paid = period or PERIOD[freq]
    c = {"name": name, "evidence": evidence, "periodStart": start, "periodEnd": end, "paymentDate": paid,
         "frequency": freq, "grossEarnings": str(gross), "employer": employer or {}, "employee": employee}
    c.update(extra)
    return c


def official_cases(rows):
    """Sampled Wage Bracket Method rows: FIT is the printed amount at the bracket midpoint."""
    out = []
    for section in (2, 3):
        for freq in PERIODS:
            table = [r for r in rows if r["section"] == section and r["frequency"] == freq]
            picks = ([(0.25, 0), (0.6, 3), (1.0, 5)] if section == 2 else [(0.3, 1), (0.7, 4), (0.5, 7), (0.95, 10)])
            for i, (fraction, column_index) in enumerate(picks):
                if section == 3:
                    status = "MARRIED" if i % 2 == 0 else "SINGLE"
                    table_s = [r for r in table if r["status"] == status]
                    r = table_s[min(int(fraction * len(table_s)), len(table_s) - 1)]
                    column = (status, column_index)
                    label = f"{COLUMN_LABEL[status]}, {column_index} allowances"
                    w4 = "Form W-4 2019 or earlier (section 3)"
                else:
                    r = table[min(int(fraction * len(table)), len(table) - 1)]
                    column = pub15t.SECTION2_COLUMNS[column_index]
                    label = f"{COLUMN_LABEL[column[0]]}, {COLUMN_LABEL[column[1]]}"
                    w4 = "Form W-4 2020 or later (section 2)"
                mid = (r["lower"] + r["upper"]) / 2
                printed = r["amounts"][column]
                out.append(case(
                    f"Pub 15-T wage bracket: {freq.lower()} ${r['lower']:,}-${r['upper']:,}, {label}",
                    f"IRS Pub 15-T (2026) Wage Bracket Method table, {w4}, page {r['page']}: ${printed} tentative withholding "
                    f"for wages of at least ${r['lower']:,} but less than ${r['upper']:,} (computed at the ${mid} midpoint, rounded to "
                    f"the dollar); FICA and FUTA lines by build_template.py",
                    freq, mid, w4_for(r, column), {"roundToWholeDollars": "true"}))
    return out


def test_cases(rows):
    derived = "Derived from Pub 15-T Worksheet 1A and Pub 15 by build_template.py expected_lines()"
    single = {"filingStatus": "SINGLE"}
    cases = official_cases(rows) + [
        case("Pub 15-T nonresident alien example", "IRS Pub 15-T (2026) Withholding Adjustment for Nonresident Alien Employees, example: "
             "$300 weekly, 2019 Form W-4 single with one allowance, $226.90 added (Table 1), $31 withheld",
             "WEEKLY", "300.00", {"w4Version": "FORM_2019_OR_EARLIER", "filingStatus": "SINGLE", "allowances": "1", "nonresidentAlien": "true"},
             {"roundToWholeDollars": "true"}),
        case("single, monthly, to the cent", derived, "MONTHLY", "6250.00", single),
        case("401(k) reduces income tax wages only; section 125 reduces both", derived, "MONTHLY", "5000.00", single,
             preTaxDeductionsByType={"RETIREMENT": "500.00", "INSURANCE": "200.00"}),
        case("married filing jointly, Step 2 checkbox, dependents and extra withholding", derived, "BIWEEKLY", "3846.15",
             {"filingStatus": "MARRIED_FILING_JOINTLY", "multipleJobs": "true", "dependentsAmount": "4400", "extraWithholding": "25.00"}),
        case("head of household with other income and deductions", derived, "SEMIMONTHLY", "2900.00",
             {"filingStatus": "HEAD_OF_HOUSEHOLD", "otherIncome": "6000", "deductions": "3500"}),
        case("credits larger than the tentative amount", derived, "WEEKLY", "700.00",
             {"filingStatus": "MARRIED_FILING_JOINTLY", "dependentsAmount": "6600", "extraWithholding": "10.00"}),
        case("2019 Form W-4, married, 3 allowances, to the cent", derived, "MONTHLY", "7425.50",
             {"w4Version": "FORM_2019_OR_EARLIER", "filingStatus": "MARRIED_FILING_JOINTLY", "allowances": "3"}),
        case("nonresident alien on a 2020 or later Form W-4", derived + " (Table 2 adds $619.20 biweekly to income tax wages only)",
             "BIWEEKLY", "2400.00", {"filingStatus": "SINGLE", "nonresidentAlien": "true"}),
        case("top bracket, single, monthly", derived, "MONTHLY", "70000.00", single),
        case("exempt from withholding: FICA and FUTA still due", derived, "BIWEEKLY", "1500.00",
             {"filingStatus": "SINGLE", "exemptFromWithholding": "true"}),
        case("social security wage base reached this period", derived + "; Pub 15: wages above $184,500 owe no social security tax",
             "MONTHLY", "20000.00", single, period=("2026-11-01", "2026-11-30", "2026-11-30"),
             ytd={"socialSecurity.EMPLOYEE.base": "170000.00", "medicare.EMPLOYEE.base": "190000.00", "futa.EMPLOYER.base": "7000.00",
                  "medicare.EMPLOYEE.periods": "10"}),
        case("past the wage base and the Additional Medicare threshold", derived, "MONTHLY", "20000.00", single,
             period=("2026-12-01", "2026-12-31", "2026-12-31"),
             ytd={"socialSecurity.EMPLOYEE.base": "184500.00", "medicare.EMPLOYEE.base": "210000.00", "futa.EMPLOYER.base": "7000.00",
                  "medicare.EMPLOYEE.periods": "11"}),
        case("FUTA wage base reached this period", derived, "MONTHLY", "4000.00", single,
             period=("2026-02-01", "2026-02-28", "2026-02-27"),
             ytd={"socialSecurity.EMPLOYEE.base": "4000.00", "medicare.EMPLOYEE.base": "4000.00", "futa.EMPLOYER.base": "4000.00",
                  "medicare.EMPLOYEE.periods": "1"}),
        case("FUTA-exempt employer", derived, "MONTHLY", "4000.00", single, {"futaExempt": "true"}),
        case("mid-year adoption with wages brought forward", derived + "; Pub 15 successor employer: earlier wages count toward the bases",
             "MONTHLY", "18000.00", {"filingStatus": "SINGLE", "broughtForwardYear": "2026", "broughtForwardSocialSecurityWages": "180000.00",
                                     "broughtForwardMedicareWages": "180000.00", "broughtForwardFutaWages": "7000.00"},
             period=("2026-10-01", "2026-10-31", "2026-10-30"), employmentStartDate="2019-03-01"),
        case("new hire in October needs nothing brought forward", derived, "MONTHLY", "5000.00", single,
             period=("2026-10-01", "2026-10-31", "2026-10-30"), employmentStartDate="2026-10-05"),
        case("first payroll of the year", derived, "BIWEEKLY", "3000.00", single,
             period=("2025-12-22", "2026-01-04", "2026-01-09"), employmentStartDate="2018-01-01"),
    ]
    for c in cases:
        c["expectLines"] = expected_lines(SCHEDULES, c)
    errors = [
        case("employee paid earlier this year without brought-forward wages", "wage bases need this year's earlier wages",
             "MONTHLY", "5000.00", single, period=("2026-10-01", "2026-10-31", "2026-10-30"), employmentStartDate="2019-03-01",
             expectError="No earlier payroll this year"),
        case("other pre-tax deduction types are refused", "only 401(k)-type and section 125 deductions are modelled",
             "MONTHLY", "5000.00", single, preTaxDeductionsByType={"OTHER": "100.00"}, expectError="pre-tax"),
        case("head of household on a 2019 Form W-4 is refused", "Pub 15-T: don't use the Head of Household table for 2019 or earlier forms",
             "MONTHLY", "5000.00", {"w4Version": "FORM_2019_OR_EARLIER", "filingStatus": "HEAD_OF_HOUSEHOLD"}, expectError="Head of household"),
        case("allowances on a 2020 or later Form W-4 are refused", "the 2020 or later form has no allowances",
             "MONTHLY", "5000.00", {"filingStatus": "SINGLE", "allowances": "2"}, expectError="allowances"),
        case("Form W-4 steps 2-4 on a 2019 form are refused", "the 2019 or earlier form has no steps 2-4",
             "MONTHLY", "5000.00", {"w4Version": "FORM_2019_OR_EARLIER", "filingStatus": "SINGLE", "dependentsAmount": "2000"},
             expectError="steps 2"),
        case("2027 is not covered", "2027 tables are published in December 2026", "MONTHLY", "5000.00", single,
             period=("2027-01-01", "2027-01-31", "2027-01-29"), expectError="a newer template version is needed"),
    ]
    return cases + errors


# ------------------------------------------------------------------ template

def p(value, source):
    return [{"from": FROM, "to": TO, "value": value, "source": source}]


SCHEDULES = None


def main():
    global SCHEDULES
    text = open(sys.argv[1], encoding="utf-8").read()
    pub15 = os.path.join(os.path.dirname(sys.argv[1]), "p15.txt")
    check_pub15(open(pub15, encoding="utf-8").read())
    SCHEDULES = pub15t.annual_schedules(text)
    check_publication(text, SCHEDULES)
    rows = pub15t.wage_brackets(text)
    print("wage bracket amounts reproduced:", check_wage_brackets(SCHEDULES, rows))

    brackets = []
    for kind in ("STANDARD", "CHECKBOX"):
        for status, short in STATUSES.items():
            for a, b, c, d in SCHEDULES[(kind, short)]:
                brackets.append({"key": f"{kind}_{status}", "lower": str(a), "upper": str(b if b is not None else TOP),
                                 "rate": str(d), "base": str(c)})

    fit_wages = "#pos(#taxableEarnings - #reductions - #preTaxDeductions)"
    modern = "#employee['w4Version'] == 'FORM_2020_OR_LATER'"
    fica_wages = "#pos(#socialEarnings - #reductions - #preTaxDeductionsByType['INSURANCE'])"
    bf = "#broughtForward ? #coalesce(#employee['{0}'], 0) : 0"
    template = {
        "schemaVersion": 1,
        "id": "us-federal-employment",
        "name": "United States — federal income tax withholding, FICA and FUTA",
        "version": "2026.1.0",
        "status": "LAW_DERIVED",
        "jurisdiction": {"country": "US"},
        "validity": {"from": FROM, "to": TO},
        "currency": "USD",
        "payFrequencies": ["WEEKLY", "BIWEEKLY", "SEMIMONTHLY", "MONTHLY"],
        "taxYearStart": "--01-01",
        "dateBasis": "PAYMENT_DATE",
        "rounding": {"scale": 2, "mode": "HALF_UP"},
        "changelog": [{"version": "2026.1.0", "date": RETRIEVED,
                       "summary": "Wages paid in 2026: Pub 15-T percentage method for automated payroll systems (2020 or later and 2019 or earlier Forms W-4, nonresident alien adjustment); social security, Medicare, Additional Medicare and FUTA per Pub 15."}],
        "sources": SOURCES,
        "inputs": {
            "employer": [
                {"key": "roundToWholeDollars", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "Round federal income tax withholding to the nearest dollar (Pub 15-T allows it if used consistently)"},
                {"key": "futaExempt", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "The employer is exempt from FUTA (e.g. a 501(c)(3) organization or a government employer)"},
            ],
            "employee": [
                {"key": "w4Version", "type": "ENUM", "required": False, "values": ["FORM_2020_OR_LATER", "FORM_2019_OR_EARLIER"],
                 "defaultValue": "FORM_2020_OR_LATER", "description": "Which Form W-4 the employee's withholding is based on"},
                {"key": "filingStatus", "type": "ENUM", "required": True, "values": list(STATUSES),
                 "description": "Form W-4 Step 1(c) (2019 or earlier forms: Married = MARRIED_FILING_JOINTLY; Single or Married but withhold at the higher single rate = SINGLE)"},
                {"key": "multipleJobs", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "Form W-4 Step 2(c) box checked (two jobs / spouse works)"},
                {"key": "dependentsAmount", "type": "DECIMAL", "required": False, "min": 0, "defaultValue": "0",
                 "description": "Form W-4 Step 3 total (claim dependent and other credits), annual"},
                {"key": "otherIncome", "type": "DECIMAL", "required": False, "min": 0, "defaultValue": "0",
                 "description": "Form W-4 Step 4(a) other income, annual"},
                {"key": "deductions", "type": "DECIMAL", "required": False, "min": 0, "defaultValue": "0",
                 "description": "Form W-4 Step 4(b) deductions, annual"},
                {"key": "extraWithholding", "type": "DECIMAL", "required": False, "min": 0, "defaultValue": "0",
                 "description": "Form W-4 Step 4(c) (2019 or earlier forms: line 6) extra withholding per pay period"},
                {"key": "allowances", "type": "INTEGER", "required": False, "min": 0, "max": 99, "defaultValue": "0",
                 "description": "Withholding allowances on a 2019 or earlier Form W-4 (line 5)"},
                {"key": "exemptFromWithholding", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "The employee claimed exemption from withholding on a Form W-4 valid for 2026"},
                {"key": "nonresidentAlien", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "Nonresident alien employee (Pub 15-T Tables 1 and 2 are added to wages for income tax withholding only)"},
                {"key": "broughtForwardYear", "type": "INTEGER", "required": False, "min": 2000, "max": 2100,
                 "description": "Calendar year the brought-forward wages belong to; other years' figures are ignored"},
                {"key": "broughtForwardSocialSecurityWages", "type": "DECIMAL", "required": False, "min": 0,
                 "description": "Social security wages this employer (or a predecessor) already paid this year outside Humano"},
                {"key": "broughtForwardMedicareWages", "type": "DECIMAL", "required": False, "min": 0,
                 "description": "Medicare wages this employer (or a predecessor) already paid this year outside Humano"},
                {"key": "broughtForwardFutaWages", "type": "DECIMAL", "required": False, "min": 0,
                 "description": "FUTA wages this employer (or a predecessor) already paid this year outside Humano"},
            ],
        },
        "parameters": {
            "standardAdjustmentMarried": p("12900", "irs-pub15t-2026"),
            "standardAdjustmentOther": p("8600", "irs-pub15t-2026"),
            "allowanceAmount": p("4300", "irs-pub15t-2026"),
            "socialSecurityRate": p(str(SS_RATE), "irs-pub15-2026"),
            "socialSecurityWageBase": p(str(SS_BASE), "irs-pub15-2026"),
            "medicareRate": p(str(MEDICARE_RATE), "irs-pub15-2026"),
            "additionalMedicareRate": p(str(ADDITIONAL_RATE), "irs-pub15-2026"),
            "additionalMedicareThreshold": p(str(ADDITIONAL_THRESHOLD), "irs-pub15-2026"),
            "futaRate": p(str(FUTA_RATE), "irs-pub15-2026"),
            "futaMaximumCredit": p(str(FUTA_CREDIT), "irs-pub15-2026"),
            "futaWageBase": p(str(FUTA_BASE), "irs-pub15-2026"),
        },
        "tables": {
            "annualWithholding": {
                "kind": "BRACKETS",
                "description": "2026 Annual Percentage Method tables (Pub 15-T section 1): column A as lower, B as upper, C as base, D as rate; one schedule per Step 2 checkbox and filing status",
                "versions": [{"from": FROM, "to": TO, "source": "irs-pub15t-2026", "rows": brackets}],
            },
            "nonresidentAlienAddition": {
                "kind": "EXACT",
                "description": "Amount added to a nonresident alien's wages per payroll period: Table 2 (Form W-4 2020 or later, or first paid in 2020 or later), Table 1 (otherwise)",
                "columns": ["FORM_2020_OR_LATER", "FORM_2019_OR_EARLIER"],
                "versions": [{"from": FROM, "to": TO, "source": "irs-pub15t-2026",
                              "rows": [{"key": f, "values": [v[1], v[0]]} for f, v in NRA.items()]}],
            },
        },
        "steps": [
            {"id": "supportedPreTaxDeductions", "type": "REQUIRE",
             "condition": "#preTaxDeductions == #preTaxDeductionsByType['RETIREMENT'] + #preTaxDeductionsByType['INSURANCE']",
             "message": "Only pre-tax deductions of type RETIREMENT (401(k), 403(b), governmental 457(b) elective deferrals) and INSURANCE (section 125 cafeteria plan) are supported"},
            {"id": "noHeadOfHouseholdOnOldForm", "type": "REQUIRE", "when": f"!({modern})",
             "condition": "#employee['filingStatus'] != 'HEAD_OF_HOUSEHOLD'",
             "message": "Head of household can't be used with a 2019 or earlier Form W-4 (Pub 15-T)"},
            {"id": "noStepsOnOldForm", "type": "REQUIRE", "when": f"!({modern})",
             "condition": "!#employee['multipleJobs'] and #employee['dependentsAmount'] == 0 and #employee['otherIncome'] == 0 and #employee['deductions'] == 0",
             "message": "Form W-4 steps 2-4(b) only exist on 2020 or later forms; a 2019 or earlier form uses allowances"},
            {"id": "noAllowancesOnNewForm", "type": "REQUIRE", "when": modern, "condition": "#employee['allowances'] == 0",
             "message": "Withholding allowances only exist on 2019 or earlier Forms W-4"},
            {"id": "broughtForward", "type": "VALUE",
             "expr": "#employee['broughtForwardYear'] != null and #employee['broughtForwardYear'] == #taxYear"},
            {"id": "earlierPayThisYear", "type": "REQUIRE",
             "when": "#ytd.periods('medicare', 'EMPLOYEE') == 0 and #employmentStartDate < #periodStart and #periodStart > #taxYearStartDate",
             "condition": "#broughtForward",
             "message": "No earlier payroll this year is recorded for this employee, who was employed before this period: set broughtForwardYear and the social security, Medicare and FUTA wages already paid this year (0 if none) so the wage bases and the Additional Medicare threshold apply correctly"},
            # Federal income tax: Pub 15-T Worksheet 1A.
            {"id": "incomeTaxWages", "type": "VALUE", "expr": fit_wages},
            {"id": "nonresidentAddition", "type": "VALUE", "when": "#employee['nonresidentAlien']",
             "expr": f"#table.lookup('nonresidentAlienAddition', #frequency, #employee['w4Version'])"},
            {"id": "annualWages", "type": "VALUE", "expr": "(#incomeTaxWages + #nonresidentAddition) * #periodsPerYear"},
            {"id": "adjustedAnnualWages", "type": "VALUE",
             "expr": f"{modern} ? #pos(#annualWages + #employee['otherIncome'] - #employee['deductions'] - (#employee['multipleJobs'] ? 0 : "
                     f"(#employee['filingStatus'] == 'MARRIED_FILING_JOINTLY' ? #p['standardAdjustmentMarried'] : #p['standardAdjustmentOther'])))"
                     f" : #pos(#annualWages - #employee['allowances'] * #p['allowanceAmount'])"},
            {"id": "tentativeWithholding", "type": "VALUE",
             "expr": f"#divide(#table.bracketTax('annualWithholding', ({modern} and #employee['multipleJobs'] ? 'CHECKBOX_' : 'STANDARD_') + #employee['filingStatus'], #adjustedAnnualWages), #periodsPerYear, 10, 'HALF_UP')"},
            {"id": "creditsPerPeriod", "type": "VALUE", "when": modern,
             "expr": "#divide(#employee['dependentsAmount'], #periodsPerYear, 10, 'HALF_UP')"},
            {"id": "withholding", "type": "VALUE",
             "expr": "#pos(#tentativeWithholding - #creditsPerPeriod) + #employee['extraWithholding']"},
            {"id": "federalIncomeTax", "type": "TAX", "label": "Federal income tax", "ledger": "INCOME_TAX", "source": "irs-pub15t-2026",
             "when": "!#employee['exemptFromWithholding']", "base": "#incomeTaxWages",
             "amount": "#employer['roundToWholeDollars'] ? #round(#withholding, 0, 'HALF_UP') : #withholding"},
            # FICA: Pub 15 section 9. Section 125 deductions are excluded from FICA and FUTA wages; 401(k) deferrals are not.
            {"id": "ficaWages", "type": "VALUE", "expr": fica_wages},
            {"id": "socialSecurityWagesToDate", "type": "VALUE",
             "expr": "#ytd.base('socialSecurity', 'EMPLOYEE') + (" + bf.format("broughtForwardSocialSecurityWages") + ")"},
            {"id": "socialSecurity", "type": "CONTRIBUTION", "label": "Social security", "ledger": "SOCIAL_SECURITY", "source": "irs-pub15-2026",
             "base": "#min(#ficaWages, #pos(#p['socialSecurityWageBase'] - #socialSecurityWagesToDate))",
             "employee": {"rate": "#p['socialSecurityRate']"}, "employer": {"rate": "#p['socialSecurityRate']"}},
            {"id": "medicareWagesToDate", "type": "VALUE",
             "expr": "#ytd.base('medicare', 'EMPLOYEE') + (" + bf.format("broughtForwardMedicareWages") + ")"},
            {"id": "medicare", "type": "CONTRIBUTION", "label": "Medicare", "ledger": "MEDICARE", "source": "irs-pub15-2026",
             "base": "#ficaWages", "employee": {"rate": "#p['medicareRate']"}, "employer": {"rate": "#p['medicareRate']"}},
            {"id": "additionalMedicare", "type": "CONTRIBUTION", "label": "Additional Medicare", "ledger": "MEDICARE", "source": "irs-pub15-2026",
             "base": "#pos(#medicareWagesToDate + #ficaWages - #p['additionalMedicareThreshold']) - #pos(#medicareWagesToDate - #p['additionalMedicareThreshold'])",
             "employee": {"rate": "#p['additionalMedicareRate']"}},
            # FUTA: Pub 15 section 14, net of the maximum 5.4% credit.
            {"id": "futaWagesToDate", "type": "VALUE",
             "expr": "#ytd.base('futa', 'EMPLOYER') + (" + bf.format("broughtForwardFutaWages") + ")"},
            {"id": "futa", "type": "CONTRIBUTION", "label": "FUTA", "ledger": "UNEMPLOYMENT", "source": "irs-pub15-2026",
             "when": "!#employer['futaExempt']",
             "base": "#min(#ficaWages, #pos(#p['futaWageBase'] - #futaWagesToDate))",
             "employer": {"rate": "#p['futaRate'] - #p['futaMaximumCredit']"}},
        ],
        "limitations": [
            "Federal only: state and local income taxes, state unemployment (SUTA) and state disability insurance are separate templates.",
            "Weekly, biweekly, semimonthly and monthly pay; supplemental wages are withheld as regular wages (aggregate), so the optional 22% flat rate and the mandatory 37% rate on supplemental wages over $1,000,000 are not applied.",
            "Pre-tax deductions: RETIREMENT is treated as 401(k)/403(b)/457(b) elective deferrals (reduce income tax wages only) and INSURANCE as section 125 cafeteria plan deductions (reduce income tax, FICA and FUTA wages); other pre-tax types are refused.",
            "FICA- or FUTA-exempt employment (e.g. F-1/J-1 students, family employees, some religious and government employment) is not modelled.",
            "FUTA is accrued at 0.6% (6.0% less the maximum 5.4% credit); credit reductions for a state (announced in November) and late state payments are reconciled on Form 940.",
            "Tips, fringe benefits, group-term life insurance over $50,000 and third-party sick pay are not included.",
            "Wage bases count wages paid by this employer through Humano plus brought-forward wages; mid-year adoptions must enter the year's earlier wages.",
            "Status LAW_DERIVED: income tax reproduces all 15,845 weekly-to-monthly amounts of the Pub 15-T Wage Bracket Method tables (checked by build_template.py; 28 of them plus the Pub 15-T nonresident alien example are test cases); other amounts are derived from the publications.",
        ],
        "testCases": test_cases(rows),
    }
    with open(sys.argv[2], "w", encoding="utf-8") as f:
        json.dump(template, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("wrote", sys.argv[2], "with", len(template["testCases"]), "test cases")


if __name__ == "__main__":
    main()
