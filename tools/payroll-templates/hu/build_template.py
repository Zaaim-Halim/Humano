"""Build src/main/resources/payroll-templates/hu/hu-employment.json.

Hungary, employment (munkaviszony) paid monthly in HUF, 2026. Every rule below cites the act it comes
from. The acts are read in the consolidated texts on net.jogtar.hu (njt.hu refused automated requests);
the 2026 amounts are the ones the tax authority (NAV) publishes in its 2026 information booklets with the
legal basis of each. The test cases' expected amounts are computed by expected_lines() — an implementation
written directly from those texts, independent of the Java engine; the engine must reproduce them exactly.

Usage: python3 -I build_template.py ../../../src/main/resources/payroll-templates/hu/hu-employment.json
"""
import json
import sys
from datetime import date
from decimal import ROUND_HALF_UP, Decimal

RETRIEVED = "2026-10-23"
LEAVE_RETRIEVED = "2026-10-25"
FROM, TO = "2026-01-01", "2026-12-31"
JOGTAR = "https://net.jogtar.hu/jogszabaly?docid="
NAV = "https://nav.gov.hu/pfile/file?path=/ugyfeliranytu/nezzen-utana/inf_fuz/2026/"

SOURCES = {
    "szja": {
        "title": "1995. évi CXVII. törvény a személyi jövedelemadóról (hatályos 2026.X.1.) — 8. § (1) 15 %, 26. § (1) (előző évi bér január 10-ig), 29/A. § (2)-(2a) családi kedvezmény 133 340 / 266 660 / 440 000 Ft + 133 340 Ft, 29/B. §, 29/C. § első házasok 33 335 Ft, 29/D. § és köv. anyák kedvezményei, 29/E. § személyi kedvezmény, 29/F. § 25 év alatti fiatalok",
        "url": JOGTAR + "99500117.tv",
    },
    "tbj": {
        "title": "2019. évi CXXII. törvény a társadalombiztosítás ellátásaira jogosultakról, valamint ezen ellátások fedezetéről (hatályos 2026.X.1.) — 6. § (1) (a kiegészítő tevékenységet folytató saját jogú nyugdíjas nem biztosított), 22. § (3) (a foglalkoztató fizeti a járulékfizetési alsó határ és a jövedelem különbözete utáni járulékot), 25. § (1) 18,5 %, 27. § (1) a) (járulékalap: az Szja tv. szerint összevont adóalapba tartozó nem önálló tevékenységből származó bevétel), 27. § (2)-(3) járulékfizetési alsó határ a minimálbér 30 %-a: (2) c) 'az adott hónapban a biztosított táppénzben, baleseti táppénzben részesül' — ezeket a naptári napokat figyelmen kívül kell hagyni, egy-egy naptári napra a járulékalap harmincad részét kell alapul venni; (3) a) nem kell alkalmazni csecsemőgondozási díjban, gyermekgondozási díjban részesülőre, 32. § a) (a foglalkoztató által folyósított társadalombiztosítási ellátás), 34. § családi járulékkedvezmény",
        "url": JOGTAR + "a1900122.tv",
    },
    "szocho": {
        "title": "2018. évi LII. törvény a szociális hozzájárulási adóról (hatályos 2026.X.1.) — adó mértéke 13 %, alapja munkaviszonyban a társadalombiztosítási járulék alapja; saját jogú nyugdíjas jövedelme mentes",
        "url": JOGTAR + "a1800052.tv",
    },
    "nav-73": {
        "title": "NAV információs füzet 73. „Szja adóalap-kedvezmények” (közzétéve 2026. 03. 19.) — kedvezmények sorrendje; 25 év alatti fiatalok kedvezménye 715 765 Ft/hó (utoljára a 25. életév betöltésének hónapjában); személyi kedvezmény 107 600 Ft; családi kedvezmény táblázata; családi járulékkedvezmény 15 %; minimálbér 322 800 Ft; PDF SHA-256 1c5323a8e9511d3d944b180cb095a8d23685a99fc38b91b5b226faa627b1df4a",
        "url": NAV + "73.-Szja-adoalap-kedvezmenyek-2026.-03.19",
    },
    "nav-49": {
        "title": "NAV információs füzet 49. „A szociális hozzájárulási adó” (közzétéve 2026. 01. 16.) — 13 %; munkaviszonyban az alap a tb-járulék alapja, a járulékfizetési alsó határ 2026-ban 96 840 Ft; minimálbér 322 800 Ft a 426/2025. (XII. 23.) Korm. rendelet 2. § (1) alapján; PDF SHA-256 5fa18ac1f4f919912bc096d3d77a75de61bbbe57f85f38ee822ababb5c7b0c33",
        "url": NAV + "49.-A-szocialis-hozzajarulasi-ado-2026.-01.-16",
    },
    "published-net-2026": {
        "title": "Net pay published for the 2026 minimum wage: 214 662 Ft for 322 800 Ft gross (szja 48 420 Ft, tb-járulék 59 718 Ft) — cross-check only",
        "url": "https://berfigyelo.hu/minimalber",
    },
}
for s in SOURCES.values():
    s["retrieved"] = RETRIEVED
SOURCES["tbj"]["retrieved"] = LEAVE_RETRIEVED

PARAMS = {
    "taxRate": ("0.15", "szja"),
    "socialSecurityRate": ("0.185", "tbj"),
    "socialContributionTaxRate": ("0.13", "szocho"),
    "minimumContributionBase": ("96840", "nav-49"),  # Tbj. 27. § (2): 30 % of the 322 800 Ft minimum wage
    "youthMonthlyLimit": ("715765", "nav-73"),  # Szja tv. 29/F. §
    "personalAllowance": ("107600", "nav-73"),  # Szja tv. 29/E. §: a third of the minimum wage, rounded to 100 Ft
    "firstMarriedAllowance": ("33335", "szja"),  # Szja tv. 29/C. § (3)
    "familyOneDependant": ("133340", "szja"),  # 29/A. § (2) a)
    "familyTwoDependants": ("266660", "szja"),  # b)
    "familyThreeOrMoreDependants": ("440000", "szja"),  # c)
    "familyDisabledSupplement": ("133340", "szja"),  # 29/A. § (2a)
    "familyContributionReliefRate": ("0.15", "tbj"),  # Tbj. 34. § (3)
}


def ft(x):
    return Decimal(x).quantize(Decimal(1), rounding=ROUND_HALF_UP)


def expected_lines(c):
    """The amounts the texts give for a test case, computed directly (no engine)."""
    wage = Decimal(c["grossEarnings"]) - Decimal(c.get("reductions", "0"))
    emp = c.get("employee", {})
    start = date.fromisoformat(c["periodStart"])
    lines = []

    def add(step, side, amount):
        if amount != 0:
            lines.append({"step": step, "side": side, "amount": str(amount)})

    insured = emp.get("ownRightPensioner") != "true"  # Tbj. 6. § (1)
    floor = Decimal(0)
    parental = int(c.get("leaveDays", {}).get("MATERNITY", "0")) > 0  # Tbj. 27. § (3) a)
    if insured and wage > 0 and emp.get("minimumBaseExempt") != "true" and not parental:
        if "minimumContributionBase" in emp:
            floor = Decimal(emp["minimumContributionBase"])
        else:
            # Tbj. 27. § (2) c): days on sickness benefit (the rule leaves them unpaid) are disregarded; each
            # remaining calendar day counts a thirtieth of the minimum
            end = date.fromisoformat(c["periodEnd"])
            # 27. § (2) a): days of the month outside the employment are disregarded too
            first = date.fromisoformat(c.get("employmentStartDate", c["periodStart"]))
            last = date.fromisoformat(c["employmentEndDate"]) if "employmentEndDate" in c else end
            outside = max((first - start).days, 0) + max((end - last).days, 0)
            days = end.day - outside - int(c.get("unpaidLeaveDays", {}).get("SICK", "0"))
            floor = Decimal(96840) if days >= 30 else ft(Decimal(96840) * max(days, 0) / 30)
    # allowances in NAV's order (booklet 73, point 1)
    rest = wage
    mothers = rest if emp.get("mothersAllowance", "NONE") != "NONE" else Decimal(0)
    rest -= mothers
    if emp.get("mothersAllowance", "NONE") == "NONE" and wage > 0:
        b = date.fromisoformat(c["birthDate"])
        if b.replace(year=b.year + 25) >= start:  # through the month of the 25th birthday
            limit = Decimal(emp["youthAllowanceLimit"]) if "youthAllowanceLimit" in emp else Decimal(715765)
            rest -= min(rest, limit)
    if emp.get("personalAllowance") == "true":
        rest -= min(rest, Decimal(107600))
    rest -= min(rest, Decimal(emp.get("firstMarriedAllowance", "0")))
    deps, ben, dis, alt = (int(emp.get(k, "0")) for k in ("dependants", "beneficiaries", "disabledBeneficiaries", "alternateCareBeneficiaries"))
    per_child = Decimal([0, 133340, 266660][deps] if deps < 3 else 440000)
    # Szja tv. 29/B. § (1e): a child cared for alternately by the separated parents counts at half
    family = ft((ben * per_child - alt * per_child / 2 + dis * Decimal(133340)) * Decimal(emp.get("familyAllowanceShare", "1")))
    family_used = min(rest, family)
    tax_base = rest - family_used
    ss_full = ft(wage * Decimal("0.185")) if insured else Decimal(0)
    relief = min(ft((family - family_used) * Decimal("0.15")), ss_full) if insured else Decimal(0)  # Tbj. 34. § (3)
    add("socialSecurity", "EMPLOYEE", ss_full - relief)
    if floor > wage:
        add("socialSecurityMinimum", "EMPLOYER", ft((floor - wage) * Decimal("0.185")))  # Tbj. 22. § (3)
    if insured and wage > 0:
        add("socialContributionTax", "EMPLOYER", ft(max(wage, floor) * Decimal("0.13")))
    add("incomeTax", "TAX", ft(tax_base * Decimal("0.15")))
    return lines


def case(name, evidence, gross, employee, birth="1988-06-15", start="2026-03-01", end="2026-03-31", payment=None,
         full=True, error=None, pre_tax=None, throughout=None, reductions=None, leave_pay=None, leave_days=None, unpaid_days=None,
         joined=None, left=None):
    c = {"name": name, "evidence": evidence, "periodStart": start, "periodEnd": end, "frequency": "MONTHLY"}
    if payment:
        c["paymentDate"] = payment
    c.update({"grossEarnings": gross, "fullPeriod": full})
    if reductions:
        c["reductions"] = reductions
    if leave_pay:
        c["leavePay"] = leave_pay
    if leave_days:
        c["leaveDays"] = leave_days
    if unpaid_days:
        c["unpaidLeaveDays"] = unpaid_days
    if throughout is not None:
        c["employedThroughout"] = throughout
    if joined:
        c["employmentStartDate"] = joined
    if left:
        c["employmentEndDate"] = left
    if birth:
        c["birthDate"] = birth
    c.update({"employer": {}, "employee": employee})
    if pre_tax:
        c["preTaxDeductions"] = pre_tax
    if error:
        c["expectError"] = error
    return c


def test_cases():
    law = "Szja tv. 8. §, 29/A-29/F. §; Tbj. 22. § (3), 25. §, 27. § (2), 34. §; Szocho tv.; NAV booklets 49 and 73; amounts from build_template.py expected_lines()"
    two_children = {"dependants": "2", "beneficiaries": "2"}
    computed = [
        case("published net: minimum wage 322,800", "Net 214,662 Ft as published for 2026: tb-járulék 59,718, szja 48,420. " + law, "322800", {}),
        case("two children at the minimum wage", "2 x 266,660 = 533,320 Ft family allowance; base 322,800 used, the rest x 15% = 31,578 Ft family contribution relief (net 294,660). " + law,
             "322800", dict(two_children)),
        case("NAV table: three dependants, three beneficiaries", "NAV booklet 73 point 10.4: 3 x 440,000 = 1,320,000 Ft allowance. " + law,
             "1500000", {"dependants": "3", "beneficiaries": "3"}),
        case("NAV example: under-25 allowance shared between employers (case II)", "NAV booklet 73 point 13: 415,765 Ft declared at the 500,000 Ft employer. " + law,
             "500000", {"youthAllowanceLimit": "415765"}, birth="2003-05-20"),
        case("under 25 in the month of the 25th birthday", "Szja tv. 29/F. §; NAV: the month of turning 25 is the last eligible month. " + law,
             "800000", {}, birth="2001-03-31"),
        case("the month after the 25th birthday", "Turned 25 on 28 February: no allowance in March. " + law, "800000", {}, birth="2001-02-28"),
        case("mother of two with family allowance", "Szja tv. 29/D. § ff.: the mothers' allowance clears the base; the unused 533,320 x 15% = 79,998 Ft reduces tb-járulék (Tbj. 34. §). " + law,
             "600000", {"mothersAllowance": "TWO_CHILDREN", **two_children}, birth=None),
        case("part-time pay below the minimum base", "Tbj. 27. § (2), 22. § (3): employer pays 18.5% on 96,840 - 80,000; szocho on 96,840. " + law,
             "80000", {}),
        case("full-time student: no minimum base", "Tbj. 27. § (3) b): the minimum base does not apply. " + law,
             "80000", {"minimumBaseExempt": "true"}, birth="2004-01-10"),
        case("working pensioner: income tax only", "Tbj. 6. § (1): not insured; Szocho tv.: own-right pensioner exempt. " + law,
             "400000", {"ownRightPensioner": "true"}, birth="1958-02-01"),
        case("personal allowance and shared first-married allowance", "Szja tv. 29/E. § 107,600 Ft; 29/C. § half of 33,335 Ft. " + law,
             "450000", {"personalAllowance": "true", "firstMarriedAllowance": "16668"}),
        case("disabled child and shared family allowance", "Szja tv. 29/A. § (2a): +133,340 Ft; claimed half by this parent. " + law,
             "700000", {"dependants": "1", "beneficiaries": "1", "disabledBeneficiaries": "1", "familyAllowanceShare": "0.5"}),
        case("NAV example: child in alternate care (father's family)", "NAV booklet 73 point 10.5: 3 dependants, (2 x 440,000) + (440,000 / 2) = 1,100,000 Ft. " + law,
             "1500000", {"dependants": "3", "beneficiaries": "3", "alternateCareBeneficiaries": "1"}),
        case("NAV example: child in alternate care (mother's family)", "NAV booklet 73 point 10.5: 2 dependants, 266,660 + (266,660 / 2) = 399,990 Ft. " + law,
             "1500000", {"dependants": "2", "beneficiaries": "2", "alternateCareBeneficiaries": "1"}),
        case("December 2026 salary paid on 8 January 2027", "Szja tv. 26. § (1): pay for the previous year paid by 10 January belongs to that year. " + law,
             "500000", {}, start="2026-12-01", end="2026-12-31", payment="2027-01-08"),
        case("sick pay (betegszabadság) is wage", "Tbj. 27. § (1) a): the employer's sick pay is employment income in the contribution base, taxed and insured like the rest of the pay. " + law,
             "400000", {}, full=False, leave_pay={"SICK": "45000"}, leave_days={"SICK": "7"}),
        case("sickness benefit days reduce the minimum base", "Tbj. 27. § (2) c): 20 days on táppénz are disregarded, 11 of 31 days x 96,840 / 30 = 35,508 Ft; employer 18.5% on 35,508 - 30,000; szocho on 35,508. " + law,
             "322800", {}, full=False, reductions="292800", leave_days={"SICK": "20"}, unpaid_days={"SICK": "20"}),
        case("a whole month on sickness benefit: no minimum", "Tbj. 27. § (2) c): every day disregarded. " + law,
             "322800", {}, full=False, reductions="322800", leave_days={"SICK": "31"}, unpaid_days={"SICK": "31"}),
        case("unpaid leave keeps the full minimum base", "Tbj. 27. § (2): unpaid leave (other than for a sick child under 12) is not among the disregarded days. " + law,
             "322800", {}, full=False, reductions="292800", leave_days={"UNPAID": "20"}, unpaid_days={"UNPAID": "20"}),
        case("parental leave: no minimum base", "Tbj. 27. § (3) a): the minimum does not apply to a person receiving csecsemőgondozási díj or gyermekgondozási díj. " + law,
             "322800", {}, full=False, reductions="292800", leave_days={"MATERNITY": "20"}, unpaid_days={"MATERNITY": "20"}),
        case("joined on 11 March: the minimum base for 21 days", "Tbj. 27. § (2) a): the 10 days before the employment are disregarded, 21 x 96,840 / 30 = 67,788 Ft; employer 18.5% on 67,788 - 60,000. " + law,
             "60000", {}, full=False, throughout=False, joined="2026-03-11"),
        case("left on 20 March: the minimum base for 20 days", "Tbj. 27. § (2) a): 11 days after the employment ended are disregarded, 20 x 96,840 / 30 = 64,560 Ft. " + law,
             "50000", {}, full=False, throughout=False, left="2026-03-20"),
    ]
    for c in computed:
        c["expectLines"] = expected_lines(c)
    zero = case("no earnings, no lines", "nothing is insured or taxed", "0", {})
    zero["expectLines"] = []
    errors = [
        case("no birth date without a mothers' allowance", "the under-25 allowance depends on the age", "400000", {}, birth=None, error="no birth date"),
        case("paid parental leave refused", "Tbj. 32. § a): benefits paid through the employer have their own contribution rules, not modelled", "400000", {},
             full=False, leave_pay={"MATERNITY": "100000"}, leave_days={"MATERNITY": "10"}, error="Paid parental leave is not supported"),
        case("more beneficiaries than dependants", "Szja tv. 29/A. §: beneficiaries are among the dependants", "500000",
             {"dependants": "1", "beneficiaries": "2"}, error="beneficiaries"),
        case("December 2026 salary paid on 15 January 2027", "Szja tv. 26. § (1): after 10 January it is income of 2027", "500000", {},
             start="2026-12-01", end="2026-12-31", payment="2027-01-15", error="10 January"),
        case("pre-tax deductions refuse", "not modelled", "500000", {}, pre_tax="10000", error="Pre-tax deductions are not supported"),
        case("2027 is not covered", "2027 amounts not yet published", "500000", {}, start="2027-01-01", end="2027-01-31",
             error="a newer template version is needed"),
    ]
    return computed + [zero] + errors


def dated(value, source):
    return [{"from": FROM, "to": TO, "value": value, "source": source}]


def main():
    insured = "!#employee['ownRightPensioner']"
    template = {
        "schemaVersion": 1,
        "id": "hu-employment",
        "name": "Hungary — employment (munkaviszony)",
        "version": "2026.1.2",
        "status": "LAW_DERIVED",
        "jurisdiction": {"country": "HU"},
        "validity": {"from": FROM, "to": TO},
        "currency": "HUF",
        "payFrequencies": ["MONTHLY"],
        "taxYearStart": "--01-01",
        "dateBasis": "PERIOD",
        "rounding": {"scale": 0, "mode": "HALF_UP"},
        "changelog": [
            {"version": "2026.1.2", "date": "2026-10-26",
             "summary": "A month the employment starts or ends: the minimum base leaves out the days outside the employment, each other day a thirtieth (Tbj. 27. § (2) a)); before, pay below the minimum was refused until the prorated base was entered. Part-time pay below the minimum is still refused."},
            {"version": "2026.1.1", "date": LEAVE_RETRIEVED,
             "summary": "Leave pay rules: the employer's sick pay (betegszabadság) is wage (Tbj. 27. § (1) a)); days on sickness benefit, which the rule leaves unpaid, are disregarded for the minimum base, each other day counting a thirtieth (27. § (2) c)); no minimum in a month with parental leave (27. § (3) a)). Before, a month with leave below the minimum was refused until the minimum was entered. Pay kept for parental leave days is refused (benefits paid through the employer, 32. § a))."},
            {"version": "2026.1.0", "date": RETRIEVED,
             "summary": "2026: szja 15 %, tb-járulék 18.5 % with the 96,840 Ft minimum base, szocho 13 %; mothers', under-25, personal, first-married and family allowances (family allowance doubled from 2026) with the family contribution relief."}
        ],
        "sources": SOURCES,
        "inputs": {
            "employer": [],
            "employee": [
                {"key": "mothersAllowance", "type": "ENUM", "required": False, "defaultValue": "NONE",
                 "values": ["NONE", "UNDER_30", "FOUR_OR_MORE_CHILDREN", "THREE_CHILDREN", "TWO_CHILDREN"],
                 "description": "Mothers' allowance declared on the tax-advance declaration (adóelőleg-nyilatkozat): under-30 mothers, mothers of four or more, of three, or of two children (Szja tv. 29/D. § ff., including the birth-year conditions); it clears the tax base of the month"},
                {"key": "youthAllowanceLimit", "type": "DECIMAL", "required": False, "min": 0, "max": 715765,
                 "description": "Under-25 allowance this employer may apply in a month, from the employee's declaration when shared between employers or waived (0); without it the full 715,765 Ft applies (Szja tv. 29/F. §, NAV booklet 73 point 13)"},
                {"key": "personalAllowance", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "Personal allowance for severe disability (Szja tv. 29/E. §): 107,600 Ft a month"},
                {"key": "firstMarriedAllowance", "type": "DECIMAL", "required": False, "defaultValue": "0", "min": 0, "max": 33335,
                 "description": "Part of the 33,335 Ft first-married couples' allowance this employee claims (Szja tv. 29/C. §; the spouses share it)"},
                {"key": "dependants", "type": "INTEGER", "required": False, "defaultValue": "0", "min": 0, "max": 30,
                 "description": "Number of dependants (eltartottak) setting the family allowance per beneficiary: 1, 2, 3 or more (Szja tv. 29/A. § (2))"},
                {"key": "beneficiaries", "type": "INTEGER", "required": False, "defaultValue": "0", "min": 0, "max": 30,
                 "description": "Number of beneficiary dependants (kedvezményezett eltartottak), e.g. children with family benefit, for whom the family allowance is claimed"},
                {"key": "disabledBeneficiaries", "type": "INTEGER", "required": False, "defaultValue": "0", "min": 0, "max": 30,
                 "description": "Beneficiaries who are permanently ill or severely disabled: +133,340 Ft each (Szja tv. 29/A. § (2a))"},
                {"key": "alternateCareBeneficiaries", "type": "INTEGER", "required": False, "defaultValue": "0", "min": 0, "max": 30,
                 "description": "Beneficiaries cared for alternately by the separated parents, who count at half (Szja tv. 29/B. § (1e))"},
                {"key": "familyAllowanceShare", "type": "DECIMAL", "required": False, "defaultValue": "1", "min": 0, "max": 1,
                 "description": "Share of the family allowance claimed by this employee when the parents claim it jointly (Szja tv. 29/B. §), e.g. 0.5"},
                {"key": "ownRightPensioner", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "Working own-right pensioner: not insured (Tbj. 6. § (1)), so no tb-járulék and no szocho; income tax only"},
                {"key": "minimumBaseExempt", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "The 96,840 Ft minimum contribution base does not apply: full-time student, or receiving childcare/nursing benefits (Tbj. 27. § (3))"},
                {"key": "minimumContributionBase", "type": "DECIMAL", "required": False, "min": 0,
                 "description": "Minimum contribution base in Ft for a partial month or a month with sick pay, prorated by calendar days (Tbj. 27. § (2))"},
            ],
        },
        "parameters": {name: dated(value, src) for name, (value, src) in PARAMS.items()},
        "tables": {},
        "steps": [
            {"id": "calendarMonth", "type": "REQUIRE", "condition": "#isCalendarMonth",
             "message": "Hungarian payroll is monthly: the pay period must be a whole calendar month"},
            {"id": "noPreTaxDeductions", "type": "REQUIRE", "condition": "#preTaxDeductions == 0",
             "message": "Pre-tax deductions are not supported by this template"},
            {"id": "paidByTenthOfJanuary", "type": "REQUIRE", "source": "szja",
             "condition": "#paymentDate.getYear() == #periodEnd.getYear() or #paymentDate <= #periodEnd.plusDays(10)",
             "message": "Pay for the previous year paid after 10 January is income of the year of payment (Szja tv. 26. § (1)); it is not supported by this template"},
            {"id": "familyCounts", "type": "REQUIRE", "source": "szja",
             "condition": "#employee['beneficiaries'] <= #employee['dependants'] and #employee['disabledBeneficiaries'] <= #employee['beneficiaries'] and #employee['alternateCareBeneficiaries'] <= #employee['beneficiaries']",
             "message": "The number of beneficiaries cannot exceed the number of dependants, nor disabled or alternate-care beneficiaries the beneficiaries (Szja tv. 29/A.-29/B. §)"},
            {"id": "noPaidParentalLeave", "type": "REQUIRE", "source": "tbj", "condition": "#leavePay['MATERNITY'] == 0",
             "message": "Paid parental leave is not supported by this template: benefits paid through the employer (csecsemőgondozási díj, gyermekgondozási díj) have their own contribution rules (Tbj. 32. § a))"},
            {"id": "wage", "type": "VALUE", "expr": "#pos(#socialEarnings - #reductions)"},
            {"id": "taxableWage", "type": "VALUE", "expr": "#pos(#taxableEarnings - #reductions)"},
            {"id": "partialMonthMinimum", "type": "REQUIRE", "source": "tbj",
             "when": insured + " and !#employee['minimumBaseExempt'] and #employee['minimumContributionBase'] == null and #wage > 0 and #wage < #p['minimumContributionBase']",
             "condition": "#fte >= 1",
             "message": "A part-time employee is paid below the minimum contribution base: set the employee's minimumContributionBase (Tbj. 27. § (2))"},
            {"id": "daysOutsideEmployment", "type": "VALUE", "source": "tbj", "when": "!#employedThroughout",
             "expr": "(#employmentStartDate > #periodStart ? #employmentStartDate.getDayOfMonth() - 1 : 0) + (#employmentEndDate != null and #employmentEndDate < #periodEnd ? #periodEnd.getDayOfMonth() - #employmentEndDate.getDayOfMonth() : 0)"},
            {"id": "minimumBaseDays", "type": "VALUE", "source": "tbj",
             "expr": "#periodEnd.getDayOfMonth() - #daysOutsideEmployment - #unpaidLeaveDays['SICK']"},
            {"id": "minimumBase", "type": "VALUE", "source": "tbj",
             "when": insured + " and !#employee['minimumBaseExempt'] and #wage > 0 and #leaveDays['MATERNITY'] == 0",
             "expr": "#employee['minimumContributionBase'] != null ? #employee['minimumContributionBase'] : (#minimumBaseDays >= 30 ? #p['minimumContributionBase'] : #divide(#p['minimumContributionBase'] * #pos(#minimumBaseDays), 30, 0, 'HALF_UP'))"},
            {"id": "mothersBase", "type": "VALUE", "source": "szja", "when": "#employee['mothersAllowance'] != 'NONE'", "expr": "#taxableWage"},
            {"id": "afterMothers", "type": "VALUE", "expr": "#taxableWage - #mothersBase"},
            {"id": "youthEligible", "type": "VALUE", "source": "nav-73",
             "when": "#employee['mothersAllowance'] == 'NONE' and #taxableWage > 0",
             "expr": "#birthDate.plusYears(25) >= #periodStart ? 1 : 0"},
            {"id": "youthBase", "type": "VALUE", "source": "szja", "when": "#youthEligible == 1",
             "expr": "#min(#afterMothers, #employee['youthAllowanceLimit'] != null ? #employee['youthAllowanceLimit'] : #p['youthMonthlyLimit'])"},
            {"id": "afterYouth", "type": "VALUE", "expr": "#afterMothers - #youthBase"},
            {"id": "personalBase", "type": "VALUE", "source": "nav-73", "when": "#employee['personalAllowance']",
             "expr": "#min(#afterYouth, #p['personalAllowance'])"},
            {"id": "afterPersonal", "type": "VALUE", "expr": "#afterYouth - #personalBase"},
            {"id": "firstMarriedBase", "type": "VALUE", "source": "szja", "expr": "#min(#afterPersonal, #employee['firstMarriedAllowance'])"},
            {"id": "afterFirstMarried", "type": "VALUE", "expr": "#afterPersonal - #firstMarriedBase"},
            {"id": "familyPerBeneficiary", "type": "VALUE", "source": "szja",
             "expr": "#employee['dependants'] == 0 ? 0 : (#employee['dependants'] == 1 ? #p['familyOneDependant'] : (#employee['dependants'] == 2 ? #p['familyTwoDependants'] : #p['familyThreeOrMoreDependants']))"},
            {"id": "familyAllowance", "type": "VALUE", "source": "szja",
             "expr": "#round((#employee['beneficiaries'] * #familyPerBeneficiary - #divide(#employee['alternateCareBeneficiaries'] * #familyPerBeneficiary, 2, 2, 'HALF_UP') + #employee['disabledBeneficiaries'] * #p['familyDisabledSupplement']) * #employee['familyAllowanceShare'], 0, 'HALF_UP')"},
            {"id": "familyUsed", "type": "VALUE", "expr": "#min(#afterFirstMarried, #familyAllowance)"},
            {"id": "familyContributionRelief", "type": "VALUE", "source": "tbj", "when": insured,
             "expr": "#min(#round((#familyAllowance - #familyUsed) * #p['familyContributionReliefRate'], 0, 'HALF_UP'), #round(#wage * #p['socialSecurityRate'], 0, 'HALF_UP'))"},
            {"id": "socialSecurity", "type": "CONTRIBUTION", "label": "Social security contribution (tb-járulék), after family contribution relief", "ledger": "SOCIAL_SECURITY", "source": "tbj",
             "when": insured, "base": "#wage",
             "employee": {"amount": "#round(#wage * #p['socialSecurityRate'], 0, 'HALF_UP') - #familyContributionRelief"}},
            {"id": "socialSecurityMinimum", "type": "CONTRIBUTION", "label": "Social security contribution on the minimum base, paid by the employer", "ledger": "SOCIAL_SECURITY", "source": "tbj",
             "when": "#minimumBase > #wage", "base": "#minimumBase - #wage", "employer": {"rate": "#p['socialSecurityRate']"}},
            {"id": "socialContributionTax", "type": "CONTRIBUTION", "label": "Social contribution tax (szocho)", "ledger": "OTHER", "source": "szocho",
             "when": insured + " and #wage > 0", "base": "#max(#wage, #minimumBase)", "employer": {"rate": "#p['socialContributionTaxRate']"}},
            {"id": "incomeTax", "type": "TAX", "label": "Personal income tax advance (szja-előleg)", "ledger": "INCOME_TAX", "source": "szja",
             "base": "#afterFirstMarried - #familyUsed", "rate": "#p['taxRate']"},
        ],
        "limitations": [
            "Employment paid monthly in HUF in 2026, for whole calendar months; pay is placed in the month it is accounted for, and previous-year pay paid after 10 January is refused (Szja tv. 26. § (1)).",
            "Allowances follow the employee's tax-advance declaration: mothers' eligibility (including the 2026 birth-year conditions), the split of the under-25, first-married and family allowances between employers or spouses are entered, not checked. The under-25 allowance is applied automatically from the birth date unless limited.",
            "The family contribution relief reduces this employer's tb-járulék on the actual pay, never the employer's contribution on the gap to the minimum base (NAV booklet 73, point 11).",
            "Leave pay rules: pay kept for sick days is the employer's sick pay (betegszabadság), wage in every base (Tbj. 27. § (1) a)); the days a rule leaves wholly unpaid are taken as days on sickness benefit (táppénz) and disregarded for the minimum base (27. § (2) c)), and a month with parental leave has no minimum (27. § (3) a)). Sickness benefit paid through the employer as a kifizetőhely, and pay kept for parental leave days (refused), are not modelled.",
            "Not modelled: szocho reliefs (labour-market entrants, career starters and others), the rehabilitation contribution, social insurance benefits paid through the employer, tax-exempt income subject to contributions, and pre-tax deductions (refused).",
            "Amounts are rounded to the forint, half up, per line; no official rounding rule for the monthly advance and contributions was found.",
            "Status LAW_DERIVED: values are cited from the acts and NAV's 2026 booklets; one case reproduces the published minimum-wage net pay and four follow NAV's worked figures.",
        ],
        "testCases": test_cases(),
    }
    with open(sys.argv[1], "w", encoding="utf-8") as f:
        json.dump(template, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("wrote", sys.argv[1], "with", len(template["testCases"]), "test cases")


main()
