"""Build src/main/resources/payroll-templates/al/al-employment.json.

Albania, employment income paid monthly in ALL, 2026. Every rule below cites the act it comes from.
The test cases' expected amounts are computed by expected_lines() — an implementation written
directly from those acts, independent of the Java engine; the engine must reproduce them exactly.
Three cases reproduce the worked examples in Instruction 26/2023 (points 22 and 24).

Usage: python3 -I build_template.py ../../../src/main/resources/payroll-templates/al/al-employment.json
"""
import json
import sys
from decimal import ROUND_HALF_UP, Decimal

RETRIEVED = "2026-10-20"
FROM, TO = "2026-01-01", "2026-12-31"

SOURCES = {
    "law-29-2023": {
        "title": "Ligji nr. 29/2023 “Për tatimin mbi të ardhurat”, i përditësuar (versioni 6, 11.12.2025) — neni 12 (të ardhurat nga punësimi), 22 (zbritjet), 24 (normat), 65 (agjenti i listëpagesës); PDF SHA-256 46b7391bd5a4fd726a012ddad835fab5a358f5c89e4671ef22e6d0d3a63ad10f",
        "url": "https://qbz.gov.al/eli/ligj/2023/03/30/29",
    },
    "udhezim-26-2023": {
        "title": "Udhëzimi i përgjithshëm nr. 26, datë 8.9.2023 “Për tatimin mbi të ardhurat”, pikat 22–24 (zbritja mujore; 13% deri 170 000 lekë/muaj, 23% mbi) — text as republished by AlProfit Consult",
        "url": "https://alprofitconsult.al/udhezim-i-pergjithshem-nr-26-date-8-9-20203-per-tatimin-mbi-te-ardhurat/",
    },
    "vkm-77-2015": {
        "title": "VKM nr. 77, datë 28.1.2015 “Për kontributet e detyrueshme…”, i përditësuar (deri VKM nr. 702, datë 13.11.2024) — pika 3, 4, 5 (masa dhe baza e kontributeve), pika 15; PDF SHA-256 7a861489d34dccf3e974a92e7777931c3590edb9cce1bee7022fcb0536097c7e",
        "url": "https://qbz.gov.al/eli/vendim/2015/01/28/77",
    },
    "vkm-776-2025": {
        "title": "VKM nr. 776, datë 19.12.2025 “Për përcaktimin e pagës minimale në shkallë vendi” — 50 000 lekë nga 1.1.2026 (FZ 239/2025); PDF SHA-256 b285dc87394a43ab3d5cbe73c0902370637d4932040175edaab1ee802b2250e4",
        "url": "https://qbz.gov.al/eli/vendim/2025/12/19/776",
    },
    "law-10383-2011": {
        "title": "Ligji nr. 10383/2011 “Për sigurimin e detyrueshëm të kujdesit shëndetësor”, ndryshuar me ligjin nr. 184/2013 — neni 7 (baza = paga bruto), neni 8 (3,4%, 50/50)",
        "url": "https://www.qsut.gov.al/wp-content/uploads/2020/01/ligji-nr-10383-per-sigurimin-shendetesor.pdf",
    },
    "max-wage-2026": {
        "title": "Paga maksimale për kontributet shoqërore 186 416 lekë nga 1.1.2026 (DPT notice “Nga 1 janari 2026, rritet paga minimale dhe maksimale”, since removed from tatime.gov.al; confirmed by Boga & Associates Tax Alert on VKM 776/2025, which also states health contributions stay on gross salary)",
        "url": "https://www.bogalaw.com/pdf/2025%20-%204%20-%20Tax%20Alert%20-%20On%20Determining%20the%20National%20Minimum%20Wage.pdf",
    },
}
for s in SOURCES.values():
    s["retrieved"] = RETRIEVED

PARAMS = {
    "minimumWage": ("50000", "vkm-776-2025"),
    "maximumSocialBase": ("186416", "max-wage-2026"),
    "socialEmployee": ("0.095", "vkm-77-2015"),  # pt 3(a): > 87 h/month
    "socialEmployer": ("0.15", "vkm-77-2015"),
    "socialEmployeeUnder87": ("0.094", "vkm-77-2015"),  # pt 4(a): < 87 h/month
    "socialEmployerUnder87": ("0.139", "vkm-77-2015"),
    "socialEmployerUnderWeek": ("0.003", "vkm-77-2015"),  # pt 5(a): contract < 1 week, employer only
    "healthEmployee": ("0.017", "vkm-77-2015"),  # pt 3(b)/4(b)/5(b); law 10383 art. 8
    "healthEmployer": ("0.017", "vkm-77-2015"),
    # law 29/2023 art. 22(1)(a)-(c), monthly amounts (art. 65(1): 1/12 applied monthly with the personal-status declaration)
    "deductionUpTo50000": ("50000", "law-29-2023"),
    "deductionUpTo60000": ("35000", "law-29-2023"),
    "deductionAbove60000": ("30000", "law-29-2023"),
    "deductionBand1": ("50000", "law-29-2023"),
    "deductionBand2": ("60000", "law-29-2023"),
}


def money(x):
    return x.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def p(name):
    return Decimal(PARAMS[name][0])


def expected_lines(c):
    """The amounts the acts give for a test case, computed directly (no engine)."""
    gross = Decimal(c["grossEarnings"])
    emp = c.get("employee", {})
    category = emp.get("contractHours", "MORE_THAN_87_HOURS")
    minimum = Decimal(emp["minimumContributionBase"]) if "minimumContributionBase" in emp else p("minimumWage")
    lines = []

    def add(step, side, amount):
        amount = money(amount)
        if amount != 0:
            lines.append({"step": step, "side": side, "amount": str(amount)})

    if gross > 0:
        if category == "LESS_THAN_ONE_WEEK":
            social_base, health_base = gross, gross
            add("socialInsurance", "EMPLOYER", social_base * p("socialEmployerUnderWeek"))
        else:
            social_base = min(max(gross, minimum), p("maximumSocialBase"))
            health_base = max(gross, minimum)
            employee_rate, employer_rate = (
                (p("socialEmployee"), p("socialEmployer"))
                if category == "MORE_THAN_87_HOURS"
                else (p("socialEmployeeUnder87"), p("socialEmployerUnder87"))
            )
            add("socialInsurance", "EMPLOYEE", social_base * employee_rate)
            add("socialInsurance", "EMPLOYER", social_base * employer_rate)
        add("healthInsurance", "EMPLOYEE", health_base * p("healthEmployee"))
        add("healthInsurance", "EMPLOYER", health_base * p("healthEmployer"))
    if emp.get("personalStatusDeclaration") == "SIGNED_WITH_THIS_EMPLOYER":
        deduction = (
            p("deductionUpTo50000") if gross <= p("deductionBand1")
            else p("deductionUpTo60000") if gross <= p("deductionBand2")
            else p("deductionAbove60000")
        )
    else:
        deduction = Decimal(0)
    base = max(gross - deduction, Decimal(0))
    tax = min(base, Decimal(170000)) * Decimal("0.13") + max(base - Decimal(170000), Decimal(0)) * Decimal("0.23")
    add("incomeTax", "TAX", tax)
    return lines


def case(name, evidence, gross, employee, full=True, error=None, start="2026-03-01", end="2026-03-31", pre_tax=None):
    c = {
        "name": name,
        "evidence": evidence,
        "periodStart": start,
        "periodEnd": end,
        "frequency": "MONTHLY",
        "grossEarnings": gross,
        "fullPeriod": full,
        "employer": {},
        "employee": employee,
    }
    if pre_tax:
        c["preTaxDeductions"] = pre_tax
    if error:
        c["expectError"] = error
    return c


SIGNED = {"personalStatusDeclaration": "SIGNED_WITH_THIS_EMPLOYER"}
NOT_SIGNED = {"personalStatusDeclaration": "NOT_SIGNED"}


def test_cases():
    law = "law 29/2023 arts. 22, 24, 65; VKM 77/2015 pts 3-5; VKM 776/2025; law 10383 arts. 7-8; amounts from build_template.py expected_lines()"
    computed = [
        case("official example: 230,000 with the declaration",
             "Instruction 26/2023 pt 24: (2,760,000 - 360,000) a year taxed 2,040,000 x 13% + 360,000 x 23% = 265,200 + 82,800 "
             "(the instruction misprints the sum as 358,000; it is 348,000) = 29,000 a month. Contributions: " + law,
             "230000.00", SIGNED),
        case("official example: 40,000 job with the declaration",
             "Instruction 26/2023 pt 22: 40,000 a month at the employer holding the declaration, 50,000 deduction, base and tax 0. "
             "Contributions on the 2026 minimum wage 50,000: " + law,
             "40000.00", SIGNED),
        case("official example: second job without the declaration",
             "Instruction 26/2023 pt 22: the other employer applies 13%/23% with no deduction, so 70,000 x 13%. Contributions: " + law,
             "70000.00", NOT_SIGNED),
        case("deduction band 50,001-60,000", "55,000 - 35,000 = 20,000 x 13%. " + law, "55000.00", SIGNED),
        case("deduction band upper edge 60,000", "60,000 is in the 35,000 band: 25,000 x 13%. " + law, "60000.00", SIGNED),
        case("deduction band above 60,000", "60,001 - 30,000 = 30,001 x 13%. " + law, "60001.00", SIGNED),
        case("top of the 13% band", "200,000 - 30,000 = 170,000 x 13% = 22,100. " + law, "200000.00", SIGNED),
        case("contract under 87 hours a month", "VKM 77/2015 pt 4: 9.4%/13.9% on the 50,000 floor; health on 50,000. " + law,
             "30000.00", {**SIGNED, "contractHours": "LESS_THAN_87_HOURS"}),
        case("contract under one week", "VKM 77/2015 pt 5: social 0.3% employer only, health 3.4% on pay, no floor. " + law,
             "10000.00", {**SIGNED, "contractHours": "LESS_THAN_ONE_WEEK"}),
        case("partial month with an explicit minimum base", "minimumContributionBase 25,000 set by the tenant for a half month. " + law,
             "25000.00", {**SIGNED, "minimumContributionBase": "25000"}, full=False),
    ]
    for c in computed:
        c["expectLines"] = expected_lines(c)
    zero = case("no earnings, no lines", "nothing is insured or taxed", "0", SIGNED)
    zero["expectLines"] = []
    errors = [
        case("missing personal-status declaration answer", "whether the deduction applies must be stated per employee",
             "100000.00", {}, error="personalStatusDeclaration"),
        case("partial month below the minimum wage refuses", "VKM 77/2015 does not say how the floor is prorated",
             "25000.00", SIGNED, full=False, error="minimumContributionBase"),
        case("pre-tax deductions refuse", "law 29/2023 art. 20 private-pension deduction not modelled",
             "100000.00", SIGNED, pre_tax="5000.00", error="Pre-tax deductions are not supported"),
        case("second job below the minimum wage refuses", "VKM 77/2015 pt 15: contributions across employers",
             "30000.00", {**NOT_SIGNED, "otherEmployment": "true"}, error="other employment"),
        case("2027 is not covered", "2027 values not yet published", "100000.00", SIGNED,
             start="2027-01-01", end="2027-01-31", error="a newer template version is needed"),
    ]
    return computed + [zero] + errors


def dated(value, source):
    return [{"from": FROM, "to": TO, "value": value, "source": source}]


def main():
    under_week = "#employee['contractHours'] == 'LESS_THAN_ONE_WEEK'"
    template = {
        "schemaVersion": 1,
        "id": "al-employment",
        "name": "Albania — employment income",
        "version": "2026.1.0",
        "status": "LAW_DERIVED",
        "jurisdiction": {"country": "AL"},
        "validity": {"from": FROM, "to": TO},
        "currency": "ALL",
        "payFrequencies": ["MONTHLY"],
        "taxYearStart": "--01-01",
        "rounding": {"scale": 2, "mode": "HALF_UP"},
        "changelog": [
            {"version": "2026.1.0", "date": RETRIEVED,
             "summary": "2026: minimum wage 50,000 and maximum social-insurance base 186,416 ALL; law 29/2023 employment tax (arts. 22, 24) and VKM 77/2015 contribution rates."}
        ],
        "sources": SOURCES,
        "inputs": {
            "employer": [],
            "employee": [
                {"key": "personalStatusDeclaration", "type": "ENUM", "required": True,
                 "values": ["SIGNED_WITH_THIS_EMPLOYER", "NOT_SIGNED"],
                 "description": "Whether the employee signed the personal-status declaration (law 29/2023 art. 66) with this employer; only then is the monthly personal deduction applied (art. 65)"},
                {"key": "contractHours", "type": "ENUM", "required": False,
                 "values": ["MORE_THAN_87_HOURS", "LESS_THAN_87_HOURS", "LESS_THAN_ONE_WEEK"], "defaultValue": "MORE_THAN_87_HOURS",
                 "description": "Contract length for social-insurance rates (VKM 77/2015 pts 3-5)"},
                {"key": "otherEmployment", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "Whether the employee also works for another employer (VKM 77/2015 pt 15)"},
                {"key": "minimumContributionBase", "type": "DECIMAL", "required": False, "min": 0,
                 "description": "Minimum contribution base in ALL replacing the minimum wage — for partial months, where the floor must be prorated"},
            ],
        },
        "parameters": {name: dated(value, src) for name, (value, src) in PARAMS.items()},
        "tables": {
            "monthlyEmploymentTax": {
                "kind": "BRACKETS",
                "description": "Monthly employment tax on the base after the personal deduction: 13% up to 170,000, 23% above (law 29/2023 art. 24; Instruction 26/2023 pt 24)",
                "versions": [
                    {"from": FROM, "to": TO, "source": "udhezim-26-2023",
                     "rows": [
                         {"lower": "0", "upper": "170000", "rate": "0.13"},
                         {"lower": "170000", "upper": "999999999999", "rate": "0.23"},
                     ]}
                ],
            }
        },
        "steps": [
            {"id": "calendarMonth", "type": "REQUIRE", "condition": "#isCalendarMonth",
             "message": "Albanian payroll (listëpagesa) is monthly: the pay period must be a whole calendar month"},
            {"id": "noPreTaxDeductions", "type": "REQUIRE", "condition": "#preTaxDeductions == 0",
             "message": "Pre-tax deductions are not supported by this template: the law 29/2023 art. 20 private-pension deduction is not modelled"},
            {"id": "contractHoursConsistent", "type": "REQUIRE",
             "condition": "#employee['contractHours'] != 'MORE_THAN_87_HOURS' or #fte * 2 >= 1",
             "message": "The employee works under half time (under 87 hours a month) but contractHours says MORE_THAN_87_HOURS; set contractHours"},
            {"id": "remuneration", "type": "VALUE", "expr": "#pos(#socialEarnings - #reductions)"},
            {"id": "minimumBase", "type": "VALUE",
             "expr": "#employee['minimumContributionBase'] != null ? #employee['minimumContributionBase'] : #p['minimumWage']",
             "source": "vkm-776-2025"},
            {"id": "partialMonthMinimum", "type": "REQUIRE",
             "when": "#remuneration > 0 and !(" + under_week + ") and #employee['minimumContributionBase'] == null",
             "condition": "#fullPeriod or #remuneration >= #minimumBase",
             "message": "Pay is below the minimum wage in a partial month (or part-time / unpaid leave); VKM 77/2015 does not say how the floor is prorated, so set the employee's minimumContributionBase"},
            {"id": "otherEmploymentAboveMinimum", "type": "REQUIRE",
             "when": "#remuneration > 0 and #employee['otherEmployment']",
             "condition": "#remuneration >= #p['minimumWage']",
             "message": "The employee has other employment and earns below the minimum wage here; VKM 77/2015 pt 15 splits contributions across employers in a way this template does not model"},
            {"id": "socialBase", "type": "VALUE",
             "expr": "#remuneration == 0 ? 0 : (" + under_week + " ? #remuneration : #clamp(#remuneration, #minimumBase, #p['maximumSocialBase']))",
             "source": "vkm-77-2015"},
            {"id": "healthBase", "type": "VALUE",
             "expr": "#remuneration == 0 ? 0 : (" + under_week + " ? #remuneration : #max(#remuneration, #minimumBase))",
             "source": "law-10383-2011"},
            {"id": "socialInsurance", "type": "CONTRIBUTION", "label": "Social insurance", "ledger": "SOCIAL_SECURITY", "source": "vkm-77-2015",
             "base": "#socialBase",
             "employee": {"rate": under_week + " ? 0 : (#employee['contractHours'] == 'LESS_THAN_87_HOURS' ? #p['socialEmployeeUnder87'] : #p['socialEmployee'])"},
             "employer": {"rate": under_week + " ? #p['socialEmployerUnderWeek'] : (#employee['contractHours'] == 'LESS_THAN_87_HOURS' ? #p['socialEmployerUnder87'] : #p['socialEmployer'])"}},
            {"id": "healthInsurance", "type": "CONTRIBUTION", "label": "Health insurance", "ledger": "MEDICARE", "source": "law-10383-2011",
             "base": "#healthBase", "employee": {"rate": "#p['healthEmployee']"}, "employer": {"rate": "#p['healthEmployer']"}},
            {"id": "monthlyIncome", "type": "VALUE", "expr": "#pos(#taxableEarnings - #reductions)"},
            {"id": "personalDeduction", "type": "VALUE",
             "when": "#employee['personalStatusDeclaration'] == 'SIGNED_WITH_THIS_EMPLOYER'",
             "expr": "#monthlyIncome <= #p['deductionBand1'] ? #p['deductionUpTo50000'] : (#monthlyIncome <= #p['deductionBand2'] ? #p['deductionUpTo60000'] : #p['deductionAbove60000'])",
             "source": "law-29-2023"},
            {"id": "incomeTax", "type": "TAX", "label": "Tax on employment income", "ledger": "INCOME_TAX", "source": "law-29-2023",
             "base": "#pos(#monthlyIncome - #personalDeduction)",
             "amount": "#table.bracketTax('monthlyEmploymentTax', #incomeTax_base)"},
        ],
        "limitations": [
            "Monthly payroll calendars whose periods are whole calendar months, paid in ALL.",
            "Employees with a pay below the minimum wage in a partial month, or below the minimum wage at one of several employers (VKM 77/2015 pt 15), are refused unless a minimumContributionBase is set.",
            "The art. 20 private-pension deduction and the annual-return deductions (children, education) are not applied; employees with tenant pre-tax deductions are refused.",
            "Social insurance across several employers is computed per employer on its own pay (capped at the maximum each); the refund of the excess by the Social Insurance Institute is outside payroll.",
            "The maximum social-insurance base (186,416 ALL) comes from the tax administration's 2026 notice, since removed from its website, and Boga & Associates' alert; not from a gazette text.",
            "Amounts are rounded to 0.01 ALL, half up, line by line; rounding to whole lek on the e-payroll declaration is not modelled.",
            "Status LAW_DERIVED: the tax amounts of three cases reproduce Instruction 26/2023's worked examples; contribution amounts are derived from the cited acts.",
        ],
        "testCases": test_cases(),
    }
    with open(sys.argv[1], "w", encoding="utf-8") as f:
        json.dump(template, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("wrote", sys.argv[1], "with", len(template["testCases"]), "test cases")


main()
