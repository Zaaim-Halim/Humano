"""Build src/main/resources/payroll-templates/hr/hr-employment.json.

Croatia, salary (plaća) paid monthly in EUR, 2026. Every rule below cites the act it comes from.
The local income-tax rates come from municipal-rates-2026.csv (see extract_rates.py). The test cases'
expected amounts are computed by expected_lines() — an implementation written directly from those
acts, independent of the Java engine; the engine must reproduce them exactly.

Usage: python3 -I build_template.py ../../../src/main/resources/payroll-templates/hr/hr-employment.json
"""
import csv
import json
import os
import sys
from decimal import ROUND_HALF_UP, Decimal

RETRIEVED = "2026-10-23"
FROM, TO = "2026-01-01", "2026-12-31"

SOURCES = {
    "zpd": {
        "title": "Zakon o porezu na dohodak (NN 115/16, 106/18, 121/19, 32/20, 138/20, 151/22, 114/23, 152/24), pročišćeni tekst na snazi od 1.1.2025. — čl. 14 (osobni odbitak 600,00 eura i koeficijenti), čl. 19.a (stope jedinica lokalne samouprave), čl. 23 (doprinosi iz plaće kao izdatak), čl. 24 (predujam: niža stopa do 5000,00 eura mjesečne osnovice, viša iznad; st. 5 umanjenje 50 % za I. skupinu razvijenosti i Grad Vukovar), čl. 46 st. 2 (umanjenje za mlade u godišnjem obračunu)",
        "url": "https://www.zakon.hr/z/85/Zakon-o-porezu-na-dohodak",
    },
    "zod": {
        "title": "Zakon o doprinosima (NN 84/08 … 114/23, 152/24), pročišćeni tekst na snazi od 1.1.2025. — čl. 13 st. 1 t. 1 (MO I. stup 20 % / 15 %), čl. 14 st. 1 t. 1 (zdravstveno 16,5 %), čl. 17 st. 1 t. 1 (MO II. stup 5 %), čl. 20 (doprinosi iz i na plaću; st. 2–3 osoba koja se prvi put zapošljava), čl. 21 (osnovica = plaća), čl. 21.a (umanjenje osnovice MO I. stupa do 1300,00 eura), čl. 200 (najniža osnovica, razmjerno za nepuno radno vrijeme), čl. 204–205 (najviša mjesečna osnovica za MO I. i II. stup)",
        "url": "https://www.zakon.hr/z/365/Zakon-o-doprinosima",
    },
    "naredba-2026": {
        "title": "Naredba o iznosima osnovica za obračun doprinosa za obvezna osiguranja za 2026. godinu (NN 150/25) — čl. 2 prosječna plaća 1.993,00 eura, čl. 3 najniža mjesečna osnovica 757,34 eura, čl. 4 najviša mjesečna osnovica 11.958,00 eura",
        "url": "https://narodne-novine.nn.hr/clanci/sluzbeni/2025_12_150_2237.html",
    },
    "naredba-2025": {
        "title": "Naredba o iznosima osnovica za obračun doprinosa za obvezna osiguranja za 2025. godinu (NN 137/24; izmjena NN 25/25 mijenja samo čl. 14) — čl. 3 najniža mjesečna osnovica 683,24 eura, čl. 4 najviša mjesečna osnovica 10.788,00 eura",
        "url": "https://www.zakon.hr/cms.htm?id=540573",
    },
    "pravilnik-doprinosi-143-2023": {
        "title": "Pravilnik o izmjenama i dopunama Pravilnika o doprinosima (NN 143/23) — umanjenje mjesečne osnovice iz čl. 21.a Zakona najviše do iznosa bruto plaće; više poslodavaca prema izjavi radnika ili podacima Porezne uprave",
        "url": "https://narodne-novine.nn.hr/clanci/sluzbeni/2023_12_143_1957.html",
    },
    "pu-stope-2026": {
        "title": "Porezna uprava — Tablica poreznih stopa godišnjeg poreza na dohodak za 2026. godinu (556 jedinica lokalne samouprave; odluke objavljene u Narodnim novinama, stanje 1. siječnja 2026.); xlsx SHA-256 87733f3eb50a39650813a4ea7a6efbb8c556d4bf9c119ece9ea411f37e6f1592",
        "url": "https://porezna-uprava.gov.hr/hr/porezne-stope-godisnjeg-poreza-na-dohodak/4764",
    },
}
for s in SOURCES.values():
    s["retrieved"] = RETRIEVED

PARAMS = {
    "pensionFirstPillarOnly": ("0.20", "zod"),  # čl. 13 st. 1 t. 1.1
    "pensionFirstPillarWithSecond": ("0.15", "zod"),  # čl. 13 st. 1 t. 1.2
    "pensionSecondPillar": ("0.05", "zod"),  # čl. 17 st. 1 t. 1
    "healthEmployer": ("0.165", "zod"),  # čl. 14 st. 1 t. 1
    # ZOD čl. 21.a: up to 700,00 the base is reduced by 300,00; 700,01-1300,00 by 0,5 x (1300,00 - gross)
    "reductionFlatLimit": ("700.00", "zod"),
    "reductionFlatAmount": ("300.00", "zod"),
    "reductionTaperLimit": ("1300.00", "zod"),
    "reductionTaperRate": ("0.5", "zod"),
    "basicPersonalAllowance": ("600.00", "zpd"),  # čl. 14 st. 1
    "lowerRateMonthlyLimit": ("5000.00", "zpd"),  # čl. 24 st. 3
    "assistedAreaTaxShare": ("0.5", "zpd"),  # čl. 24 st. 5: advance reduced by 50 %
}

HERE = os.path.dirname(os.path.abspath(__file__))
with open(os.path.join(HERE, "municipal-rates-2026.csv"), encoding="utf-8") as f:
    RATES = {int(r["code"]): r for r in csv.DictReader(f)}

# ZPD čl. 19.a st. 2: the widest band any local unit may set (Grad Zagreb).
for r in RATES.values():
    assert Decimal("0.15") <= Decimal(r["lower"]) <= Decimal("0.23"), r
    assert Decimal("0.25") <= Decimal(r["higher"]) <= Decimal("0.33"), r

# ZOD čl. 200, 204: the minimum and maximum monthly bases of the year the salary month belongs to
# (Naredba čl. 3 and 4 of each year), so a December salary paid in January keeps its own year's bases.
CONTRIBUTION_BASES = {
    2025: ("683.24", "10788.00", "Naredba NN 137/24"),
    2026: ("757.34", "11958.00", "Naredba NN 150/25"),
}

ZAGREB, SPLIT, RIJEKA, OSIJEK, VUKOVAR = 1333, 4090, 3735, 3123, 5185


def money(x):
    return x.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def p(name):
    return Decimal(PARAMS[name][0])


def expected_lines(c):
    """The amounts the acts give for a test case, computed directly (no engine)."""
    gross = Decimal(c["grossEarnings"])
    emp = c.get("employee", {})
    minimum, maximum = (Decimal(v) for v in CONTRIBUTION_BASES[int(c["periodStart"][:4])][:2])
    second_pillar = emp.get("secondPillarMember") == "true"
    lines = []

    def add(step, side, amount):
        amount = money(amount)
        if amount != 0:
            lines.append({"step": step, "side": side, "amount": str(amount)})
        return amount

    employee_contributions = Decimal(0)
    if gross > 0:
        floor = Decimal(emp["minimumContributionBase"]) if "minimumContributionBase" in emp else minimum
        base = max(gross, floor)  # ZOD čl. 21, 200
        pension_base = min(base, maximum)  # čl. 204
        if "pensionBaseReduction" in emp:
            reduction = Decimal(emp["pensionBaseReduction"])
        elif gross <= Decimal(700):
            reduction = min(Decimal(300), gross)  # čl. 21.a st. 1; Pravilnik NN 143/23: at most the gross pay
        elif gross <= Decimal(1300):
            reduction = money((Decimal(1300) - gross) / 2)  # čl. 21.a st. 2
        else:
            reduction = Decimal(0)
        first_base = max(pension_base - reduction, Decimal(0))
        employee_contributions += add("pensionFirstPillar", "EMPLOYEE", first_base * (Decimal("0.15") if second_pillar else Decimal("0.20")))
        if second_pillar:
            employee_contributions += add("pensionSecondPillar", "EMPLOYEE", pension_base * Decimal("0.05"))
        if emp.get("firstPermanentEmployment") != "true":
            add("healthInsurance", "EMPLOYER", base * Decimal("0.165"))
    rates = RATES[int(emp["municipalityCode"])]
    lower, higher = Decimal(rates["lower"]), Decimal(rates["higher"])
    allowance = Decimal(600) * Decimal(emp["personalAllowanceCoefficient"])  # ZPD čl. 14, 24 st. 2
    tax_base = max(gross - employee_contributions - allowance, Decimal(0))
    tax = min(tax_base, Decimal(5000)) * lower + max(tax_base - Decimal(5000), Decimal(0)) * higher  # čl. 24 st. 3
    if emp.get("assistedAreaResident") == "true":
        tax = tax / 2  # čl. 24 st. 5
    add("incomeTax", "TAX", tax)
    return lines


def case(name, evidence, gross, employee, full=True, error=None, start="2026-03-01", end="2026-03-31",
         payment=None, pre_tax=None):
    c = {
        "name": name,
        "evidence": evidence,
        "periodStart": start,
        "periodEnd": end,
        "frequency": "MONTHLY",
    }
    if payment:
        c["paymentDate"] = payment
    c.update({"grossEarnings": gross, "fullPeriod": full, "employer": {}, "employee": employee})
    if pre_tax:
        c["preTaxDeductions"] = pre_tax
    if error:
        c["expectError"] = error
    return c


def emp(municipality, coefficient="1", second_pillar=True, **extra):
    e = {"municipalityCode": str(municipality), "personalAllowanceCoefficient": coefficient,
         "secondPillarMember": "true" if second_pillar else "false"}
    e.update({k: v for k, v in extra.items()})
    return e


def test_cases():
    law = "ZOD čl. 13, 14, 17, 21, 21.a, 200, 204; Naredba NN 150/25; ZPD čl. 14, 24; Porezna uprava 2026 rate table; amounts from build_template.py expected_lines()"
    computed = [
        case("Zagreb 2,000 with the second pillar", "MO 15% + 5% of 2,000 = 400; 2,000 - 400 - 600 = 1,000 x 23% (Zagreb). " + law,
             "2000.00", emp(ZAGREB)),
        case("minimum wage 1,050 in Split", "MO I base 1,050 - 0.5 x (1,300 - 1,050) = 925; Split 21.5%. " + law,
             "1050.00", emp(SPLIT), payment="2026-04-15"),
        case("first pillar only, 20%", "Not a second-pillar member: MO I 20%, no MO II. " + law,
             "1800.00", emp(OSIJEK, second_pillar=False)),
        case("tapered reduction at 1,200", "MO I base 1,200 - 0.5 x (1,300 - 1,200) = 1,150. " + law, "1200.00", emp(ZAGREB)),
        case("reduction ends above 1,300", "1,300.01: no reduction. " + law, "1300.01", emp(ZAGREB)),
        case("flat reduction at 700", "700 or less: MO I base reduced by 300 (part-time). " + law,
             "700.00", emp(RIJEKA, minimumContributionBase="378.67"), full=True),
        case("reduction capped at gross pay", "Pravilnik NN 143/23: under 300 the reduction is the gross pay, so MO I is 0. " + law,
             "250.00", emp(RIJEKA, minimumContributionBase="189.34")),
        case("children raise the allowance", "Coefficient 1 + 0.5 + 0.7 (two children) = 2.2 x 600 = 1,320. " + law,
             "2500.00", emp(ZAGREB, "2.2")),
        case("higher rate above 5,000 of monthly base", "Base 9,200 - 1,840 - 600 = 6,760: 5,000 x 23% + 1,760 x 33%. " + law,
             "9200.00", emp(ZAGREB)),
        case("pension ceiling 11,958", "MO I/II on 11,958; health 16.5% on the full 15,000. " + law,
             "15000.00", emp(ZAGREB)),
        case("second employer without the tax card", "Coefficient 0: no personal allowance at this employer. " + law,
             "800.00", emp(OSIJEK, "0", otherEmployment="true", pensionBaseReduction="100.00", minimumContributionBase="378.67")),
        case("Vukovar resident: advance halved", "ZPD čl. 24 st. 5: 50% for Grad Vukovar. " + law,
             "1600.00", emp(VUKOVAR, assistedAreaResident="true")),
        case("first permanent employment", "ZOD čl. 20 st. 2-3: no employer health contribution for up to a year. " + law,
             "1400.00", emp(ZAGREB, firstPermanentEmployment="true")),
        case("full-time pay below the minimum base", "ZOD čl. 200 st. 1: contributions on 757.34; reduction on the 600 paid. " + law,
             "600.00", emp(OSIJEK)),
        case("December 2025 salary paid in January 2026", "Contributions on the 2025 minimum base 683.24 (Naredba NN 137/24, ZOD čl. 200); tax on the 2026 rules in force on the payment date (ZPD čl. 24 st. 1). " + law,
             "650.00", emp(ZAGREB), start="2025-12-01", end="2025-12-31", payment="2026-01-15"),
    ]
    for c in computed:
        c["expectLines"] = expected_lines(c)
    zero = case("no earnings, no lines", "nothing is insured or taxed", "0", emp(ZAGREB))
    zero["expectLines"] = []
    errors = [
        case("unknown municipality code", "every employee needs the code of the local unit of residence from the tax card",
             "2000.00", emp(9999), error="No value for 9999"),
        case("missing allowance coefficient", "the coefficient comes from the porezna kartica",
             "2000.00", {"municipalityCode": str(ZAGREB), "secondPillarMember": "true"}, error="personalAllowanceCoefficient"),
        case("partial month below the minimum base refuses", "ZOD čl. 200, 202: the floor is prorated by insured days",
             "500.00", emp(ZAGREB), full=False, error="minimumContributionBase"),
        case("second job at 1,300 or less needs the reduction share", "ZOD čl. 21.a st. 5: the reduction is shared by declaration",
             "900.00", emp(ZAGREB, otherEmployment="true"), error="pensionBaseReduction"),
        case("pre-tax deductions refuse", "voluntary pension and similar deductions are not modelled",
             "2000.00", emp(ZAGREB), pre_tax="50.00", error="Pre-tax deductions are not supported"),
        case("December pay in January 2027 needs the 2027 version", "ZPD čl. 24 st. 1: the advance follows the rules on the payment date",
             "2000.00", emp(ZAGREB), start="2026-12-01", end="2026-12-31", payment="2027-01-15", error="a newer template version is needed"),
        case("2027 is not covered", "2027 values not yet published", "2000.00", emp(ZAGREB),
             start="2027-01-01", end="2027-01-31", error="a newer template version is needed"),
    ]
    return computed + [zero] + errors


def dated(value, source):
    return [{"from": FROM, "to": TO, "value": value, "source": source}]


def main():
    second = "#employee['secondPillarMember']"
    template = {
        "schemaVersion": 1,
        "id": "hr-employment",
        "name": "Croatia — salary (plaća)",
        "version": "2026.1.0",
        "status": "LAW_DERIVED",
        "jurisdiction": {"country": "HR"},
        "validity": {"from": FROM, "to": TO},
        "currency": "EUR",
        "payFrequencies": ["MONTHLY"],
        "taxYearStart": "--01-01",
        "dateBasis": "PAYMENT_DATE",
        "rounding": {"scale": 2, "mode": "HALF_UP"},
        "changelog": [
            {"version": "2026.1.0", "date": RETRIEVED,
             "summary": "2026: contribution bases from Naredba NN 150/25 (757.34 / 11,958.00 EUR), MO I base reduction (ZOD čl. 21.a), personal allowance 600 EUR, local rates for all 556 local units from the Tax Administration's 2026 table."}
        ],
        "sources": SOURCES,
        "inputs": {
            "employer": [],
            "employee": [
                {"key": "municipalityCode", "type": "INTEGER", "required": True, "min": 1, "max": 99999,
                 "description": "Code (šifra) of the city or municipality of residence from the tax card (porezna kartica), e.g. 1333 Zagreb, 4090 Split, 3735 Rijeka; selects the local income-tax rates (ZPD čl. 19.a)"},
                {"key": "personalAllowanceCoefficient", "type": "DECIMAL", "required": True, "min": 0, "max": 50,
                 "description": "Personal-allowance coefficient from the tax card, applied to 600 EUR (ZPD čl. 14): 1 basic; + 0.5 per dependent family member; children + 0.5, 0.7, 1.0, 1.4, 1.9, 2.5, 3.2, 4.0, 4.9 …; disability + 0.3 (or + 1.0 at 100 %). 0 at an employer that does not hold the tax card"},
                {"key": "secondPillarMember", "type": "BOOLEAN", "required": True,
                 "description": "Whether the employee is a member of a mandatory pension fund (II. stup): then MO I is 15 % and MO II 5 %; otherwise MO I is 20 % (ZOD čl. 13, 17)"},
                {"key": "otherEmployment", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "Whether the employee also has a salary from another employer this month; the MO I base reduction is then shared (ZOD čl. 21.a st. 5)"},
                {"key": "pensionBaseReduction", "type": "DECIMAL", "required": False, "min": 0, "max": 300,
                 "description": "MO I base reduction in EUR this employer applies, from the employee's declaration or the Tax Administration's data (ZOD čl. 21.a st. 5); replaces the reduction computed from this employer's pay"},
                {"key": "assistedAreaResident", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "Resident in a local unit of development group I or in Grad Vukovar: the income-tax advance is reduced by 50 % (ZPD čl. 24 st. 5)"},
                {"key": "firstPermanentEmployment", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "First employment on a permanent contract (ZOD čl. 7 t. 54): no employer health contribution for up to one year (čl. 20 st. 2-3); set back to false when the year ends"},
                {"key": "minimumContributionBase", "type": "DECIMAL", "required": False, "min": 0,
                 "description": "Minimum contribution base in EUR replacing 757.34 — for part-time work (prorated by weekly hours, ZOD čl. 200 st. 2), partial months, or management-board members (0.65 x average wage)"},
            ],
        },
        "parameters": {name: dated(value, src) for name, (value, src) in PARAMS.items()},
        "tables": {
            "contributionBases": {
                "kind": "EXACT",
                "description": "Minimum and maximum monthly contribution base by the year the salary month belongs to (ZOD čl. 200, 204-205; Naredba čl. 3-4: NN 137/24 for 2025, NN 150/25 for 2026)",
                "columns": ["minimum", "maximum"],
                "versions": [
                    {"from": FROM, "to": TO, "source": "naredba-2026",
                     "rows": [{"key": str(year), "label": label, "values": [low, high]}
                              for year, (low, high, label) in sorted(CONTRIBUTION_BASES.items())]}
                ],
            },
            "municipalTaxRates": {
                "kind": "EXACT",
                "description": "Lower and higher income-tax rate by local-unit code (ZPD čl. 19.a; Porezna uprava 2026 table)",
                "columns": ["lower", "higher"],
                "versions": [
                    {"from": FROM, "to": TO, "source": "pu-stope-2026",
                     "rows": [{"key": str(code), "label": r["name"], "values": [r["lower"], r["higher"]]}
                              for code, r in sorted(RATES.items())]}
                ],
            }
        },
        "steps": [
            {"id": "calendarMonth", "type": "REQUIRE", "condition": "#isCalendarMonth",
             "message": "Croatian salary is calculated per calendar month: the pay period must be a whole calendar month"},
            {"id": "noPreTaxDeductions", "type": "REQUIRE", "condition": "#preTaxDeductions == 0",
             "message": "Pre-tax deductions are not supported by this template"},
            {"id": "remuneration", "type": "VALUE", "expr": "#pos(#socialEarnings - #reductions)"},
            {"id": "minimumBase", "type": "VALUE",
             "expr": "#employee['minimumContributionBase'] != null ? #employee['minimumContributionBase'] : #round(#table.lookup('contributionBases', #periodStart.getYear(), 'minimum') * #fte, 2, 'HALF_UP')",
             "source": "naredba-2026"},
            {"id": "partialMonthMinimum", "type": "REQUIRE",
             "when": "#remuneration > 0 and #employee['minimumContributionBase'] == null",
             "condition": "#fullPeriod or #remuneration >= #minimumBase",
             "message": "Pay is below the minimum contribution base in a partial month; the base is prorated by insured days (ZOD čl. 202), so set the employee's minimumContributionBase"},
            {"id": "contributionBase", "type": "VALUE", "expr": "#remuneration == 0 ? 0 : #max(#remuneration, #minimumBase)",
             "source": "zod"},
            {"id": "pensionBase", "type": "VALUE", "expr": "#min(#contributionBase, #table.lookup('contributionBases', #periodStart.getYear(), 'maximum'))", "source": "naredba-2026"},
            {"id": "sharedReduction", "type": "REQUIRE",
             "when": "#employee['otherEmployment'] and #remuneration > 0 and #remuneration <= #p['reductionTaperLimit']",
             "condition": "#employee['pensionBaseReduction'] != null",
             "message": "The employee has another employer: the MO I base reduction is shared by the employee's declaration or the Tax Administration's data (ZOD čl. 21.a st. 5); set pensionBaseReduction"},
            {"id": "baseReduction", "type": "VALUE",
             "when": "#remuneration > 0",
             "expr": "#employee['pensionBaseReduction'] != null ? #employee['pensionBaseReduction'] : (#remuneration <= #p['reductionFlatLimit'] ? #min(#p['reductionFlatAmount'], #remuneration) : (#remuneration <= #p['reductionTaperLimit'] ? #round((#p['reductionTaperLimit'] - #remuneration) * #p['reductionTaperRate'], 2, 'HALF_UP') : 0))",
             "source": "zod"},
            {"id": "pensionFirstPillar", "type": "CONTRIBUTION", "label": "Pension insurance, 1st pillar (MO I)", "ledger": "PENSION_CONTRIBUTION", "source": "zod",
             "base": "#pos(#pensionBase - #baseReduction)",
             "employee": {"rate": second + " ? #p['pensionFirstPillarWithSecond'] : #p['pensionFirstPillarOnly']"}},
            {"id": "pensionSecondPillar", "type": "CONTRIBUTION", "label": "Pension insurance, 2nd pillar (MO II)", "ledger": "PENSION_CONTRIBUTION", "source": "zod",
             "when": second, "base": "#pensionBase", "employee": {"rate": "#p['pensionSecondPillar']"}},
            {"id": "healthInsurance", "type": "CONTRIBUTION", "label": "Health insurance (ZO)", "ledger": "MEDICARE", "source": "zod",
             "when": "!#employee['firstPermanentEmployment']", "base": "#contributionBase", "employer": {"rate": "#p['healthEmployer']"}},
            {"id": "income", "type": "VALUE", "expr": "#pos(#taxableEarnings - #reductions)"},
            {"id": "personalAllowance", "type": "VALUE", "expr": "#p['basicPersonalAllowance'] * #employee['personalAllowanceCoefficient']",
             "source": "zpd"},
            {"id": "lowerRate", "type": "VALUE", "expr": "#table.lookup('municipalTaxRates', #employee['municipalityCode'], 'lower')",
             "source": "pu-stope-2026"},
            {"id": "higherRate", "type": "VALUE", "expr": "#table.lookup('municipalTaxRates', #employee['municipalityCode'], 'higher')",
             "source": "pu-stope-2026"},
            {"id": "incomeTax", "type": "TAX", "label": "Income tax advance (predujam poreza na dohodak)", "ledger": "INCOME_TAX", "source": "zpd",
             "base": "#pos(#income - #employeeContributions - #personalAllowance)",
             "amount": "(#min(#incomeTax_base, #p['lowerRateMonthlyLimit']) * #lowerRate + #pos(#incomeTax_base - #p['lowerRateMonthlyLimit']) * #higherRate) * (#employee['assistedAreaResident'] ? #p['assistedAreaTaxShare'] : 1)"},
        ],
        "limitations": [
            "Monthly salary for whole calendar months, paid in EUR in 2026. Income tax uses the rules in force on the payment date (ZPD čl. 24 st. 1); contributions use the bases of the year the salary month belongs to (2025 and 2026 held). A salary paid in 2027 needs the 2027 version.",
            "The youth reduction (100 % up to age 25, 50 % from 26 to 30) is granted only in the annual tax calculation (ZPD čl. 46 st. 2), not in the monthly advance, so it is not applied here.",
            "The employee's local unit, allowance coefficient and pension-fund membership come from the tax card and HZMO data and are entered per employee; development group I residence (50 % advance reduction) is entered per employee, not derived from the unit.",
            "The minimum contribution base is prorated by FTE for part-time work; partial months below the minimum, and management-board members (0.65 x average wage), need an explicit minimumContributionBase.",
            "The MO I base reduction for several employers, or for a salary paid in several instalments, must be entered as pensionBaseReduction; the computed reduction assumes one employer and one payment, and is rounded to 0.01 EUR.",
            "The annual pension ceiling (143,496.00 EUR) applied by the Tax Administration in the annual calculation, other contribution exemptions (e.g. employment of persons with disabilities) and increased-length insurance contributions (staž s povećanim trajanjem) are not modelled.",
            "Non-taxable payments (e.g. bonuses within the non-taxable limits) must be excluded from taxable and social earnings by the tenant's pay components; pre-tax deductions are refused.",
            "Amounts are rounded to 0.01 EUR, half up, per line.",
            "Status LAW_DERIVED: values are cited from the acts and the Tax Administration's rate table; the cases are derived from them, not from official worked examples.",
        ],
        "testCases": test_cases(),
    }
    with open(sys.argv[1], "w", encoding="utf-8") as f:
        json.dump(template, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("wrote", sys.argv[1], "with", len(template["testCases"]), "test cases")


main()
