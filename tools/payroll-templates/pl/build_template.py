"""Build src/main/resources/payroll-templates/pl/pl-employment.json.

Poland, employment contract (umowa o pracę) paid monthly in PLN, 2026. Every rule below cites the act it
comes from; the texts are the consolidated acts published by the Sejm (api.sejm.gov.pl), the 2026 figures
from Dziennik Ustaw / Monitor Polski, and the Ministry of Finance and ZUS explanations. The test cases'
expected amounts are computed by expected_lines() — an implementation written directly from those texts,
independent of the Java engine; the engine must reproduce them exactly. One case reproduces ZUS's own worked
example of the health contribution cap; two reproduce the net pay published for the 2026 minimum wage and
for 8,000 zł.

Usage: python3 -I build_template.py ../../../src/main/resources/payroll-templates/pl/pl-employment.json
"""
import json
import sys
from datetime import date
from decimal import ROUND_HALF_UP, Decimal

RETRIEVED = "2026-10-23"
FROM, TO = "2026-01-01", "2026-12-31"
ELI = "https://api.sejm.gov.pl/eli/acts/"

SOURCES = {
    "updof": {
        "title": "Ustawa o podatku dochodowym od osób fizycznych, tekst jednolity Dz.U. 2026 poz. 592 — art. 21 ust. 1 pkt 148 i 152–154 oraz ust. 44 (łączny limit 85 528 zł), art. 22 ust. 2 pkt 1 i 3 (koszty 250 / 300 zł) i ust. 3b, art. 26 ust. 1 pkt 2 lit. b (składki od przychodu zwolnionego nie podlegają odliczeniu), art. 27 ust. 1 (12 % / 32 %, 120 000 zł, kwota zmniejszająca 3600 zł), art. 31b (pomniejszenie o 1/12, 1/24, 1/36), art. 32 ust. 2, 4 i 6; PDF SHA-256 d26b883de6c8665244be50852d35a3b34400c0fa58606ab03563da0d74dc1dd2",
        "url": ELI + "DU/2026/592/text.pdf",
    },
    "updof-2021": {
        "title": "Ustawa o podatku dochodowym od osób fizycznych, tekst jednolity Dz.U. 2021 poz. 1128 (brzmienie na 31.12.2021 dla art. 83 ust. 2b ustawy o świadczeniach) — art. 27 ust. 1 (17 % / 32 %, 85 528 zł), art. 32 ust. 1–3 (zaliczka, 1/12 kwoty zmniejszającej = 43,76 zł); PDF SHA-256 06d2a8e8c5b99e80e6b672757e281004ca04aee0a1d01ffcb2dcd60b2ac73c0c",
        "url": ELI + "DU/2021/1128/text.pdf",
    },
    "usus": {
        "title": "Ustawa o systemie ubezpieczeń społecznych, tekst jednolity Dz.U. 2026 poz. 199 — art. 16 (podział składek: emerytalna po połowie, rentowa 1,5 % ubezpieczony / 6,5 % płatnik, chorobowa ubezpieczony, wypadkowa płatnik), art. 19 ust. 1 (roczne ograniczenie podstawy emerytalnej i rentowej), art. 22 ust. 1 (19,52 %, 8 %, 2,45 %, wypadkowa 0,40–8,12 %); PDF SHA-256 b009d5bc5749dba7bab8c391c56f6241f8c6cf53dc0870c5a5200003de6466e9",
        "url": ELI + "DU/2026/199/text.pdf",
    },
    "uswiadczenia": {
        "title": "Ustawa o świadczeniach opieki zdrowotnej finansowanych ze środków publicznych, tekst jednolity Dz.U. 2025 poz. 1461 — art. 79 ust. 1 (9 %), art. 81 ust. 1 i 6 (podstawa pomniejszona o składki społeczne ubezpieczonego), art. 83 ust. 1, 2a i 2b (obniżenie do zaliczki obliczonej według przepisów z 31.12.2021); PDF SHA-256 6b1219883e69636dd4c7c7d1a864297af574db11c9a1ea1adc58f6b330b652d3",
        "url": ELI + "DU/2025/1461/text.pdf",
    },
    "mp-2025-1206": {
        "title": "Obwieszczenie MRPiPS z 19.11.2025 (M.P. 2025 poz. 1206) — kwota ograniczenia rocznej podstawy wymiaru składek emerytalnych i rentowych w 2026 r.: 282 600 zł; PDF SHA-256 19e15bb7fc66266307cc5a5efbefd7ad2700b3a023e91a8e8e0c70cafb57f37d",
        "url": ELI + "MP/2025/1206/text.pdf",
    },
    "rm-2025-1242": {
        "title": "Rozporządzenie Rady Ministrów z 11.09.2025 (Dz.U. 2025 poz. 1242) — minimalne wynagrodzenie od 1.01.2026: 4806 zł; PDF SHA-256 bf547c5f5d601c33296c2ffbf646f6582dbd896d113640bca73f56743f5fb8b9",
        "url": ELI + "DU/2025/1242/text.pdf",
    },
    "budzet-2026": {
        "title": "Ustawa budżetowa na rok 2026 (Dz.U. 2026 poz. 62) — art. 25 Fundusz Pracy 1,0 %, art. 26 Fundusz Solidarnościowy 1,45 %, art. 27 FGŚP 0,10 %; PDF SHA-256 a2486ce997442850401d19c763c767afbfda3a7b187985d343c3d3f10dc960da",
        "url": ELI + "DU/2026/62/text.pdf",
    },
    "rynek-pracy": {
        "title": "Ustawa o rynku pracy i służbach zatrudnienia (Dz.U. 2025 poz. 620) — art. 259 ust. 1 (składka na FP od podstawy emerytalnej bez ograniczenia, gdy w przeliczeniu na miesiąc co najmniej minimalne wynagrodzenie), art. 261–264 (zwolnienia); ustawa o Funduszu Solidarnościowym (Dz.U. 2024 poz. 1848) art. 4 ust. 1 i art. 5 ust. 2 (te same zasady, obliczana łącznie z FP); PDF SHA-256 b4ea60e14600765173e317a19c4d41110395f87a18fbd8e6c7f6b3200c2ecd51",
        "url": ELI + "DU/2025/620/text.pdf",
    },
    "fgsp": {
        "title": "Ustawa o ochronie roszczeń pracowniczych w razie niewypłacalności pracodawcy, tekst jednolity Dz.U. 2026 poz. 186 — art. 9a–9c (zwolnienia), art. 29 ust. 1 (podstawa emerytalna bez ograniczenia); PDF SHA-256 b274afa604afcbde970325869724f4a84065015a09199dd5c6688d8546bfcd90",
        "url": ELI + "DU/2026/186/text.pdf",
    },
    "wypadkowa": {
        "title": "Ustawa o ubezpieczeniu społecznym z tytułu wypadków przy pracy i chorób zawodowych, tekst jednolity Dz.U. 2025 poz. 1644 — art. 28 (płatnik do 9 ubezpieczonych: 50 % najwyższej stopy dla grup działalności; pozostałym stopę ustala ZUS); PDF SHA-256 e6bf35298d359827b327e0edcb0a4fe33ec019bb719745835079b1e0f75c356e",
        "url": ELI + "DU/2025/1644/text.pdf",
    },
    "ordynacja": {
        "title": "Ordynacja podatkowa, tekst jednolity Dz.U. 2026 poz. 622 — art. 63 § 1 (podstawy opodatkowania i kwoty podatków zaokrągla się do pełnych złotych, 50 groszy w górę); PDF SHA-256 e0ac56a7478bd0737ed5b0b47528515483efa97fdf94195706fd58c4c5d20280",
        "url": ELI + "DU/2026/622/text.pdf",
    },
    "mf-objasnienia-mlodzi": {
        "title": "Objaśnienia podatkowe MF z 30.12.2022 „Ulga w PIT dla osób do 26. roku życia” — prawo do ulgi włącznie z dniem 26. urodzin, liczy się dzień uzyskania przychodu; przychody zwolnione nie wliczają się do dochodu od początku roku (przykład 19); składki od przychodów zwolnionych nie podlegają odliczeniu, proporcja przychodów (przykład 16); PDF SHA-256 107df2441baa95e4bbcc5d2134b9665471db2a4c93c953fb4b8627428e7c6de0",
        "url": "https://www.gov.pl/attachment/c0a3c569-120e-4bc8-a8a9-860c09937cb4",
    },
    "mf-objasnienia-oswiadczenia": {
        "title": "Objaśnienia podatkowe MF z 30.12.2022 „Oświadczenia i wnioski mające wpływ na wysokość zaliczek” — PIT-2 i pomniejszenie zaliczki o 1/12, 1/24 albo 1/36 kwoty zmniejszającej podatek; PDF SHA-256 70c90b8e42b64e59f72c9300314f8f75816a55bf662ee67b7ad69d8328734d61",
        "url": "https://www.gov.pl/attachment/ababef8e-17e3-427b-9491-10b68e0b2b08",
    },
    "zus-polski-lad": {
        "title": "ZUS, „Polski Ład — co zmienia się w przepisach…” (poradnik), przykład 37: wynagrodzenie 800 zł, hipotetyczna zaliczka 440 zł × 17 % − 43,76 zł = 31,04 zł, składka zdrowotna 62,13 zł obniżona do 31,04 zł; PDF SHA-256 3ba495b96d92ab0acfcba07f4324c1814319f039d7a0bbed2309718ae8414f68",
        "url": "https://www.zus.pl/documents/10182/3996913/Polski+%C5%81ad+-+poradnik.pdf/f4f5043f-4487-2e82-e93a-8f8d7cc6c5f9?t=1650366838783",
    },
    "published-net-2026": {
        "title": "Net pay published for 2026 by independent payroll calculators: 3 605,85 zł for 4 806 zł gross, 5 783,91 zł for 8 000 zł gross (PIT-2 filed, costs 250 zł, no PPK) — cross-checks only",
        "url": "https://www.zarabiaj.pl/kalkulator-wynagrodzen/8000-brutto/",
    },
}
for s in SOURCES.values():
    s["retrieved"] = RETRIEVED

PARAMS = {
    "minimumWage": ("4806", "rm-2025-1242"),
    "annualPensionBaseLimit": ("282600", "mp-2025-1206"),  # usus art. 19 ust. 1
    "pensionRate": ("0.0976", "usus"),  # 19,52 % split equally (art. 16 ust. 1, art. 22 ust. 1 pkt 1)
    "disabilityEmployee": ("0.015", "usus"),  # art. 16 ust. 1b
    "disabilityEmployer": ("0.065", "usus"),  # 8 % - 1,5 %
    "sicknessEmployee": ("0.0245", "usus"),  # art. 22 ust. 1 pkt 3, art. 16 ust. 2
    "labourAndSolidarityFund": ("0.0245", "budzet-2026"),  # FP 1,0 % + FS 1,45 %, computed together (FS art. 5 ust. 2)
    "fgspRate": ("0.001", "budzet-2026"),
    "healthRate": ("0.09", "uswiadczenia"),
    "lowerRate": ("0.12", "updof"),
    "higherRate": ("0.32", "updof"),
    "higherRateThreshold": ("120000", "updof"),  # art. 32 ust. 2: income from the payer since the start of the year
    "monthlyReductionOneTwelfth": ("300", "updof"),  # art. 31b ust. 3: 1/12 of 3600 zł
    "monthlyReductionOneTwentyFourth": ("150", "updof"),  # 1/24
    "monthlyReductionOneThirtySixth": ("100", "updof"),  # 1/36
    "workCostsBasic": ("250", "updof"),  # art. 22 ust. 2 pkt 1
    "workCostsCommuter": ("300", "updof"),  # art. 22 ust. 2 pkt 3
    "exemptRevenueLimit": ("85528", "updof"),  # art. 21 ust. 44
    "rate2021": ("0.17", "updof-2021"),
    "higherRate2021": ("0.32", "updof-2021"),
    "threshold2021": ("85528", "updof-2021"),
    "monthlyReduction2021": ("43.76", "updof-2021"),
}


def p(name):
    return Decimal(PARAMS[name][0])


def gr(x):
    return x.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def zl(x):
    return x.quantize(Decimal(1), rounding=ROUND_HALF_UP)


def expected_lines(c):
    """The amounts the texts give for a test case, computed directly (no engine)."""
    gross = Decimal(c["grossEarnings"])
    emp, er = c.get("employee", {}), c.get("employer", {})
    ytd = {k: Decimal(v) for k, v in c.get("ytd", {}).items()}
    pay = date.fromisoformat(c.get("paymentDate") or c["periodEnd"])
    lines = []

    def add(step, side, amount, record_zero=False):
        if amount != 0 or record_zero:
            lines.append({"step": step, "side": side, "amount": str(amount)})
        return amount

    revenue = gross  # no reductions in the cases
    # usus art. 19: pension and disability stop at 282 600 zł a year
    pension_base = min(revenue, max(Decimal(282600) - ytd.get("pension.EMPLOYEE.base", Decimal(0)), Decimal(0)))
    pe = add("pension", "EMPLOYEE", gr(pension_base * Decimal("0.0976")))
    add("pension", "EMPLOYER", gr(pension_base * Decimal("0.0976")))
    de = add("disability", "EMPLOYEE", gr(pension_base * Decimal("0.015")))
    add("disability", "EMPLOYER", gr(pension_base * Decimal("0.065")))
    se = add("sickness", "EMPLOYEE", gr(revenue * Decimal("0.0245")))
    add("accident", "EMPLOYER", gr(revenue * Decimal(er["accidentRate"])))
    fund = emp.get("labourFundPartialMonth", "AUTO")
    # rynek pracy art. 259 ust. 1: the conversion to a full month (the employee's answer) matters only in a partial month
    if c.get("fullPeriod", True):
        fund_due = revenue >= 4806
    else:
        fund_due = fund == "DUE" or (fund == "AUTO" and revenue >= 4806)
    if emp.get("labourFundExempt") != "true" and revenue > 0 and fund_due:
        add("labourFund", "EMPLOYER", gr(revenue * Decimal("0.0245")))
    if emp.get("fgspExempt") != "true":
        add("fgsp", "EMPLOYER", gr(revenue * Decimal("0.001")))
    social = pe + de + se
    # updof art. 21 ust. 1 pkt 148 (to the 26th birthday inclusive, MF explanations) and 152-154, joint limit ust. 44
    category = emp.get("taxExemption", "NONE") != "NONE"
    if not category and emp.get("youthReliefWaived") != "true" and revenue > 0:
        b = date.fromisoformat(c["birthDate"])
        category = pay <= b.replace(year=b.year + 26)
    exempt = min(revenue, max(Decimal(85528) - ytd.get("exemptRevenue.TAX.base", Decimal(0)), Decimal(0))) if category else Decimal(0)
    if exempt > 0:
        add("exemptRevenue", "TAX", Decimal("0.00"), record_zero=True)
    taxable = revenue - exempt
    deductible = gr(social * taxable / revenue) if revenue > 0 else Decimal(0)  # art. 26 ust. 1 pkt 2 lit. b; MF example 16
    costs_full = {"BASIC": Decimal(250), "COMMUTER": Decimal(300), "NONE": Decimal(0)}[emp.get("workCosts", "BASIC")]
    costs = min(costs_full, taxable)  # art. 22 ust. 3b
    base = zl(max(taxable - deductible - costs, Decimal(0)))  # Ordynacja art. 63
    if base > 0:
        lower = min(base, max(Decimal(120000) - ytd.get("incomeTax.TAX.base", Decimal(0)), Decimal(0)))
        reduction = {"NONE": 0, "ONE_TWELFTH": 300, "ONE_TWENTY_FOURTH": 150, "ONE_THIRTY_SIXTH": 100}[emp["pit2Reduction"]]
        tax = zl(max(lower * Decimal("0.12") + (base - lower) * Decimal("0.32") - reduction, Decimal(0)))
        add("incomeTax", "TAX", tax, record_zero=True)
    # uswiadczenia art. 81 ust. 6, art. 83 ust. 1, 2a, 2b: capped at the advance on the 31.12.2021 rules, as if not exempt
    health_base = max(revenue - social, Decimal(0))
    health = gr(health_base * Decimal("0.09"))
    income2021 = zl(max(revenue - social - costs_full, Decimal(0)))
    lower2021 = min(income2021, max(Decimal(85528) - ytd.get("incomeTax.TAX.base", Decimal(0)), Decimal(0)))
    cap = max(lower2021 * Decimal("0.17") + (income2021 - lower2021) * Decimal("0.32")
              - (Decimal("43.76") if emp["pit2Reduction"] != "NONE" else 0), Decimal(0))
    health_line = add("health", "EMPLOYEE", gr(min(health, cap)))
    # keep the engine's step order
    order = ["pension", "disability", "sickness", "accident", "labourFund", "fgsp", "exemptRevenue", "incomeTax", "health"]
    lines.sort(key=lambda l: (order.index(l["step"]), ["EMPLOYEE", "EMPLOYER", "TAX"].index(l["side"])))
    return lines


def case(name, evidence, gross, employee, birth="1985-04-12", accident="0.0167", ytd=None, payment=None,
         start="2026-03-01", end="2026-03-31", full=True, error=None, pre_tax=None):
    c = {"name": name, "evidence": evidence, "periodStart": start, "periodEnd": end, "frequency": "MONTHLY"}
    if payment:
        c["paymentDate"] = payment
    c.update({"grossEarnings": gross, "fullPeriod": full})
    if birth:
        c["birthDate"] = birth
    c.update({"employer": {"accidentRate": accident}, "employee": employee})
    if ytd:
        c["ytd"] = ytd
    if pre_tax:
        c["preTaxDeductions"] = pre_tax
    if error:
        c["expectError"] = error
    return c


PIT2 = {"pit2Reduction": "ONE_TWELFTH"}


def test_cases():
    law = "usus art. 16, 19, 22; uswiadczenia art. 79, 81, 83; updof art. 21, 22, 26, 27, 31b, 32; Ordynacja art. 63; budget 2026 art. 25-27; amounts from build_template.py expected_lines()"
    computed = [
        case("published net: minimum wage 4,806", "Net 3,605.85 zł as published for 2026: social 469.07 + 72.09 + 117.75, health 373.24, tax 3,897 x 12% - 300 = 168. " + law,
             "4806.00", dict(PIT2)),
        case("published net: 8,000 zł", "Net 5,783.91 zł as published for 2026: social 1,096.80, health 621.29, tax 6,653 x 12% - 300 = 498. " + law,
             "8000.00", dict(PIT2)),
        case("ZUS example 37: health capped at the 2021-rules advance", "ZUS Polski Ład guide, example 37: 800 zł, income 440, 440 x 17% - 43.76 = 31.04 caps the 62.13 health contribution; no tax (52.80 - 300 < 0). " + law,
             "800.00", dict(PIT2)),
        case("no PIT-2: no 300 zł reduction", "art. 31b: the advance is reduced only with the statement; 2021-rules cap without 43.76. " + law,
             "6000.00", {"pit2Reduction": "NONE"}),
        case("PIT-2 for 1/24 and commuting costs", "art. 31b ust. 3 pkt 2: 150 zł; art. 22 ust. 2 pkt 3: 300 zł costs. " + law,
             "7000.00", {"pit2Reduction": "ONE_TWENTY_FOURTH", "workCosts": "COMMUTER"}),
        case("income from this payer crosses 120,000 this month", "art. 32 ust. 2 pkt 2: 12% up to 120,000, 32% above; ytd income 115,000. " + law,
             "15000.00", dict(PIT2), ytd={"incomeTax.TAX.base": "115000", "pension.EMPLOYEE.base": "130000"}),
        case("pension base crosses 282,600 this month", "usus art. 19: pension and disability on 282,600 - 275,000 = 7,600; sickness, accident and funds on the whole pay. " + law,
             "25000.00", dict(PIT2), ytd={"pension.EMPLOYEE.base": "275000", "incomeTax.TAX.base": "230000"}),
        case("pension base already at 282,600", "usus art. 19 ust. 3: no pension or disability contributions above the limit. " + law,
             "25000.00", dict(PIT2), ytd={"pension.EMPLOYEE.base": "282600", "incomeTax.TAX.base": "250000"}),
        case("under 26: tax-free, health capped as if taxed", "art. 21 ust. 1 pkt 148; uswiadczenia art. 83 ust. 2a: health compared with the 2021-rules advance on the whole pay. " + law,
             "6000.00", dict(PIT2), birth="2002-05-10"),
        case("26th birthday on the payment date: still tax-free", "MF explanations 2022: the relief applies including the day of the 26th birthday. " + law,
             "6000.00", dict(PIT2), birth="2000-03-31"),
        case("paid the day after the 26th birthday: taxed", "MF explanations 2022: revenue after the 26th birthday is taxed. " + law,
             "6000.00", dict(PIT2), birth="2000-03-30"),
        case("85,528 limit crossed this month", "art. 21 ust. 44: 85,528 - 80,000 = 5,528 exempt, 4,472 taxed; contributions deductible in proportion (MF example 16), costs capped at the taxable part (art. 22 ust. 3b). " + law,
             "10000.00", dict(PIT2), birth="2003-01-01", ytd={"exemptRevenue.TAX.base": "80000", "pension.EMPLOYEE.base": "80000"}),
        case("relief for families with four children", "art. 21 ust. 1 pkt 153 with the joint 85,528 limit; no birth date needed. " + law,
             "9000.00", {"pit2Reduction": "ONE_TWELFTH", "taxExemption": "FAMILY_FOUR_CHILDREN"}, birth=None),
        case("youth relief waived", "art. 32 ust. 6: the employee asked for advances without the exemption. " + law,
             "6000.00", {"pit2Reduction": "ONE_TWELFTH", "youthReliefWaived": "true"}, birth="2002-05-10"),
        case("pay below the minimum wage: no Labour Fund", "rynek pracy art. 259 ust. 1: FP/FS only from 4,806 a month. " + law,
             "3000.00", dict(PIT2)),
        case("employee aged 55/60+: no Labour Fund or FGŚP", "rynek pracy art. 261; FGŚP art. 9b ust. 2. " + law,
             "7000.00", {"pit2Reduction": "ONE_TWELFTH", "labourFundExempt": "true", "fgspExempt": "true"}, birth="1964-01-01"),
        case("partial month with Labour Fund due", "rynek pracy art. 259 ust. 1: 'w przeliczeniu na okres miesiąca' at least the minimum wage; entered by the tenant. " + law,
             "3500.00", {"pit2Reduction": "ONE_TWELFTH", "labourFundPartialMonth": "DUE"}, full=False),
        case("full month: a partial-month answer left on NOT_DUE is ignored", "rynek pracy art. 259 ust. 1: in a full month the pay itself (8,000 >= 4,806) decides; the conversion answer is for partial months only. " + law,
             "8000.00", {"pit2Reduction": "ONE_TWELFTH", "labourFundPartialMonth": "NOT_DUE"}),
        case("full month part-time below the minimum wage: DUE is ignored", "rynek pracy art. 259 ust. 1: 3,000 < 4,806 in a full month, no FP/FS whatever the partial-month answer. " + law,
             "3000.00", {"pit2Reduction": "ONE_TWELFTH", "labourFundPartialMonth": "DUE"}),
        case("December 2025 salary paid in January 2026", "Payment-date basis: 2026 rules and limits apply (updof art. 11, usus art. 18). " + law,
             "8000.00", dict(PIT2), start="2025-12-01", end="2025-12-31", payment="2026-01-09"),
    ]
    for c in computed:
        c["expectLines"] = expected_lines(c)
    zero = case("no earnings, no lines", "nothing is insured or taxed", "0", dict(PIT2))
    zero["expectLines"] = []
    errors = [
        case("missing PIT-2 answer", "whether the 300 zł reduction applies must be stated per employee", "6000.00", {}, error="pit2Reduction"),
        case("no birth date without another exemption", "art. 21 ust. 1 pkt 148 depends on the age", "6000.00", dict(PIT2), birth=None, error="no birth date"),
        case("partial month below the minimum wage", "rynek pracy art. 259: the Labour Fund test is per full month", "3500.00", dict(PIT2),
             full=False, error="labourFundPartialMonth"),
        case("missing accident rate", "the rate is set per payer (wypadkowa art. 28)", "6000.00", dict(PIT2), accident=None,
             error="accidentRate"),
        case("pre-tax deductions refuse", "PPK and other deductions are not modelled", "6000.00", dict(PIT2), pre_tax="100.00",
             error="Pre-tax deductions are not supported"),
        case("December 2026 salary paid in January 2027", "2027 limits not yet published", "8000.00", dict(PIT2),
             start="2026-12-01", end="2026-12-31", payment="2027-01-09", error="a newer template version is needed"),
    ]
    for c in errors:
        if c["employer"].get("accidentRate") is None:
            c["employer"] = {}
    return computed + [zero] + errors


def dated(value, source):
    return [{"from": FROM, "to": TO, "value": value, "source": source}]


def main():
    pit2 = "#employee['pit2Reduction']"
    costs = "(#employee['workCosts'] == 'BASIC' ? #p['workCostsBasic'] : (#employee['workCosts'] == 'COMMUTER' ? #p['workCostsCommuter'] : 0))"
    template = {
        "schemaVersion": 1,
        "id": "pl-employment",
        "name": "Poland — employment contract (umowa o pracę)",
        "version": "2026.1.1",
        "status": "LAW_DERIVED",
        "jurisdiction": {"country": "PL"},
        "validity": {"from": FROM, "to": TO},
        "currency": "PLN",
        "payFrequencies": ["MONTHLY"],
        "taxYearStart": "--01-01",
        "dateBasis": "PAYMENT_DATE",
        "rounding": {"scale": 2, "mode": "HALF_UP"},
        "changelog": [
            {"version": "2026.1.1", "date": "2026-10-24",
             "summary": "Corrects an error: the Labour Fund / Solidarity Fund answer for partial months (labourFundPartialMonth) is now ignored in a full month, where the month's pay alone decides (ustawa o rynku pracy art. 259 ust. 1); before, an answer left on NOT_DUE or DUE overrode it."},
            {"version": "2026.1.0", "date": RETRIEVED,
             "summary": "2026: minimum wage 4,806 zł, pension/disability limit 282,600 zł, FP+FS 2.45 %, FGŚP 0.10 %; PIT 12 % / 32 % above 120,000 zł with the 300 zł monthly reduction; health 9 % capped at the 2021-rules advance."}
        ],
        "sources": SOURCES,
        "inputs": {
            "employer": [
                {"key": "accidentRate", "type": "DECIMAL", "required": True, "min": 0.004, "max": 0.0812,
                 "description": "Accident insurance rate notified by ZUS, as a fraction (e.g. 0.0167 for 1.67 %); payers reporting up to 9 insured pay 50 % of the year's highest group rate (ustawa wypadkowa art. 28; range 0.40-8.12 %, usus art. 22 ust. 1 pkt 4)"},
            ],
            "employee": [
                {"key": "pit2Reduction", "type": "ENUM", "required": True,
                 "values": ["NONE", "ONE_TWELFTH", "ONE_TWENTY_FOURTH", "ONE_THIRTY_SIXTH"],
                 "description": "The employee's statement on reducing the advance (PIT-2, updof art. 31b): by 1/12 (300 zł), 1/24 (150 zł) or 1/36 (100 zł) of the 3,600 zł tax-reducing amount, or none"},
                {"key": "workCosts", "type": "ENUM", "required": False, "defaultValue": "BASIC", "values": ["BASIC", "COMMUTER", "NONE"],
                 "description": "Monthly costs of earning income (updof art. 22 ust. 2): 250 zł, 300 zł when living in another locality without a separation allowance (statement, art. 32 ust. 7), or none at the employee's request (art. 32 ust. 6)"},
                {"key": "taxExemption", "type": "ENUM", "required": False, "defaultValue": "NONE",
                 "values": ["NONE", "RETURN_RELIEF", "FAMILY_FOUR_CHILDREN", "WORKING_SENIOR"],
                 "description": "Exemption claimed by statement (updof art. 31a ust. 8): relief for returning to Poland (art. 21 ust. 1 pkt 152), for families with four or more children (pkt 153) or for working seniors (pkt 154); the under-26 relief (pkt 148) applies from the birth date. All share the 85,528 zł yearly limit (ust. 44)"},
                {"key": "youthReliefWaived", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "The employee asked for advances without the under-26 relief (updof art. 32 ust. 6)"},
                {"key": "labourFundExempt", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "No Labour Fund / Solidarity Fund contribution: woman aged 55+ or man aged 60+, 36 months after maternity/parental/childcare leave, 12 months for a hired 50+ or referred under-30 unemployed person (ustawa o rynku pracy art. 261-264)"},
                {"key": "labourFundPartialMonth", "type": "ENUM", "required": False, "defaultValue": "AUTO", "values": ["AUTO", "DUE", "NOT_DUE"],
                 "description": "For a partial month: whether the pay, converted to a full month, reaches the minimum wage so the Labour Fund / Solidarity Fund contribution is due (ustawa o rynku pracy art. 259 ust. 1); ignored in a full month, where the month's pay decides"},
                {"key": "fgspExempt", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "No FGŚP contribution: woman aged 55+ or man aged 60+, 36 months after maternity/parental/childcare leave, or 12 months for certain hired unemployed persons (ustawa o ochronie roszczeń pracowniczych art. 9a-9c)"},
            ],
        },
        "parameters": {name: dated(value, src) for name, (value, src) in PARAMS.items()},
        "tables": {},
        "steps": [
            {"id": "calendarMonth", "type": "REQUIRE", "condition": "#isCalendarMonth",
             "message": "Polish payroll is monthly: the pay period must be a whole calendar month"},
            {"id": "noPreTaxDeductions", "type": "REQUIRE", "condition": "#preTaxDeductions == 0",
             "message": "Pre-tax deductions are not supported by this template (PPK and other deductions are not modelled)"},
            {"id": "revenue", "type": "VALUE", "expr": "#pos(#socialEarnings - #reductions)"},
            {"id": "taxRevenue", "type": "VALUE", "expr": "#pos(#taxableEarnings - #reductions)"},
            {"id": "pensionBase", "type": "VALUE", "source": "mp-2025-1206",
             "expr": "#min(#revenue, #pos(#p['annualPensionBaseLimit'] - #ytd.base('pension', 'EMPLOYEE')))"},
            {"id": "pension", "type": "CONTRIBUTION", "label": "Pension insurance (emerytalne)", "ledger": "PENSION_CONTRIBUTION", "source": "usus",
             "base": "#pensionBase", "employee": {"rate": "#p['pensionRate']"}, "employer": {"rate": "#p['pensionRate']"}},
            {"id": "disability", "type": "CONTRIBUTION", "label": "Disability insurance (rentowe)", "ledger": "DISABILITY", "source": "usus",
             "base": "#pensionBase", "employee": {"rate": "#p['disabilityEmployee']"}, "employer": {"rate": "#p['disabilityEmployer']"}},
            {"id": "sickness", "type": "CONTRIBUTION", "label": "Sickness insurance (chorobowe)", "ledger": "SOCIAL_SECURITY", "source": "usus",
             "base": "#revenue", "employee": {"rate": "#p['sicknessEmployee']"}},
            {"id": "accident", "type": "CONTRIBUTION", "label": "Accident insurance (wypadkowe)", "ledger": "WORK_INJURY", "source": "wypadkowa",
             "base": "#revenue", "employer": {"rate": "#employer['accidentRate']"}},
            {"id": "labourFundPartial", "type": "REQUIRE", "source": "rynek-pracy",
             "when": "!#employee['labourFundExempt'] and #employee['labourFundPartialMonth'] == 'AUTO' and #revenue > 0 and #revenue < #p['minimumWage']",
             "condition": "#fullPeriod and #reductions == 0",
             "message": "Partial month below the minimum wage: whether the Labour Fund contribution is due depends on the pay converted to a full month (ustawa o rynku pracy art. 259 ust. 1), so set the employee's labourFundPartialMonth"},
            {"id": "labourFund", "type": "CONTRIBUTION", "label": "Labour Fund and Solidarity Fund (FP + FS)", "ledger": "UNEMPLOYMENT", "source": "budzet-2026",
             "when": "!#employee['labourFundExempt'] and #revenue > 0 and (#fullPeriod and #reductions == 0 ? #revenue >= #p['minimumWage'] : (#employee['labourFundPartialMonth'] == 'DUE' or (#employee['labourFundPartialMonth'] == 'AUTO' and #revenue >= #p['minimumWage'])))",
             "base": "#revenue", "employer": {"rate": "#p['labourAndSolidarityFund']"}},
            {"id": "fgsp", "type": "CONTRIBUTION", "label": "Guaranteed Employee Benefits Fund (FGŚP)", "ledger": "OTHER", "source": "fgsp",
             "when": "!#employee['fgspExempt']", "base": "#revenue", "employer": {"rate": "#p['fgspRate']"}},
            {"id": "employeeSocial", "type": "VALUE", "expr": "#pension_employee + #disability_employee + #sickness_employee"},
            {"id": "youthRelief", "type": "VALUE", "source": "mf-objasnienia-mlodzi",
             "when": "#employee['taxExemption'] == 'NONE' and !#employee['youthReliefWaived'] and #taxRevenue > 0",
             "expr": "#birthDate.plusYears(26) >= #paymentDate ? 1 : 0"},
            {"id": "exemptCategory", "type": "VALUE", "expr": "#employee['taxExemption'] != 'NONE' ? 1 : #youthRelief"},
            {"id": "exemptAmount", "type": "VALUE", "when": "#exemptCategory == 1", "source": "updof",
             "expr": "#min(#taxRevenue, #pos(#p['exemptRevenueLimit'] - #ytd.base('exemptRevenue', 'TAX')))"},
            {"id": "exemptRevenue", "type": "TAX", "label": "Revenue exempt from income tax (updof art. 21 ust. 1 pkt 148, 152-154)", "ledger": "INCOME_TAX", "source": "updof",
             "when": "#exemptAmount > 0", "base": "#exemptAmount", "rate": "0", "recordZero": True},
            {"id": "taxableRevenue", "type": "VALUE", "expr": "#taxRevenue - #exemptAmount"},
            {"id": "deductibleSocial", "type": "VALUE", "source": "mf-objasnienia-mlodzi",
             "expr": "#taxRevenue == 0 ? 0 : #divide(#employeeSocial * #taxableRevenue, #taxRevenue, 2, 'HALF_UP')"},
            {"id": "workCosts", "type": "VALUE", "source": "updof", "expr": "#min(" + costs + ", #taxableRevenue)"},
            {"id": "taxBase", "type": "VALUE", "source": "ordynacja",
             "expr": "#round(#pos(#taxableRevenue - #deductibleSocial - #workCosts), 0, 'HALF_UP')"},
            {"id": "lowerRatePart", "type": "VALUE", "source": "updof",
             "expr": "#min(#taxBase, #pos(#p['higherRateThreshold'] - #ytd.base('incomeTax', 'TAX')))"},
            {"id": "advanceReduction", "type": "VALUE", "source": "mf-objasnienia-oswiadczenia",
             "expr": pit2 + " == 'ONE_TWELFTH' ? #p['monthlyReductionOneTwelfth'] : (" + pit2 + " == 'ONE_TWENTY_FOURTH' ? #p['monthlyReductionOneTwentyFourth'] : (" + pit2 + " == 'ONE_THIRTY_SIXTH' ? #p['monthlyReductionOneThirtySixth'] : 0))"},
            {"id": "incomeTax", "type": "TAX", "label": "Income tax advance (zaliczka na PIT)", "ledger": "INCOME_TAX", "source": "updof",
             "when": "#taxBase > 0", "base": "#taxBase", "recordZero": True, "rounding": {"scale": 0, "mode": "HALF_UP"},
             "amount": "#pos(#lowerRatePart * #p['lowerRate'] + (#taxBase - #lowerRatePart) * #p['higherRate'] - #advanceReduction)"},
            {"id": "healthBase", "type": "VALUE", "source": "uswiadczenia", "expr": "#pos(#revenue - #employeeSocial)"},
            {"id": "income2021", "type": "VALUE", "source": "updof-2021",
             "expr": "#round(#pos(#taxRevenue - #employeeSocial - " + costs + "), 0, 'HALF_UP')"},
            {"id": "lowerRatePart2021", "type": "VALUE", "source": "updof-2021",
             "expr": "#min(#income2021, #pos(#p['threshold2021'] - #ytd.base('incomeTax', 'TAX')))"},
            {"id": "healthCap", "type": "VALUE", "source": "zus-polski-lad",
             "expr": "#pos(#lowerRatePart2021 * #p['rate2021'] + (#income2021 - #lowerRatePart2021) * #p['higherRate2021'] - (" + pit2 + " != 'NONE' ? #p['monthlyReduction2021'] : 0))"},
            {"id": "health", "type": "CONTRIBUTION", "label": "Health insurance (zdrowotne)", "ledger": "MEDICARE", "source": "uswiadczenia",
             "base": "#healthBase", "employee": {"amount": "#min(#round(#healthBase * #p['healthRate'], 2, 'HALF_UP'), #healthCap)"}},
        ],
        "limitations": [
            "Employment contracts (umowa o pracę) paid monthly in PLN in 2026, one payment per payer per month: a second run in the same month would apply the costs and the 300 zł reduction again. Rules follow the payment date (revenue is taxed and insured when paid).",
            "The 282,600 zł pension/disability limit, the 120,000 zł threshold and the 85,528 zł exemption limit are tracked per payer from this template's posted runs; amounts from other payers or from before adopting the template are not known.",
            "The 2021-rules cap on the health contribution uses this payer's taxable income since the start of the year for the 85,528 zł threshold of the 2021 scale; for employees with exempt revenue this understates the 2021 income (it only matters in low-pay months after high earlier income). With a 1/24 or 1/36 statement the 2021 reduction of 43.76 zł is still applied.",
            "Sex is not recorded, so the 55/60 age exemptions from the Labour Fund, Solidarity Fund and FGŚP are entered per employee; whether a partial month reaches the minimum wage for the Labour Fund is entered per employee.",
            "Not modelled: PPK, 50 % author's costs, the bridging-pension fund (FEP), the higher costs for several employment relationships (art. 22 ust. 2 pkt 2 and 4, settled in the annual return), the joint/single-parent statements (art. 32 ust. 3), work abroad (art. 32 ust. 9), sickness and maternity benefits paid by the employer, and benefits in kind spread over several months; employees with pre-tax deductions are refused.",
            "Contributions are rounded to the grosz, half up, per line; the tax base and the advance to the whole złoty (Ordynacja art. 63); the 2021-rules cap is not rounded (ZUS example 37).",
            "Status LAW_DERIVED: values are cited from the acts; one case reproduces ZUS's worked example of the health cap and two the net pay published for 2026.",
        ],
        "testCases": test_cases(),
    }
    with open(sys.argv[1], "w", encoding="utf-8") as f:
        json.dump(template, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("wrote", sys.argv[1], "with", len(template["testCases"]), "test cases")


main()
