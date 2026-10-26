"""Build src/main/resources/payroll-templates/nl/nl-employment.json — Dutch loonbelasting/premie
volksverzekeringen (white table, standard situation, resident), employee-insurance premiums (AWf, Aof,
Wko surcharge, Whk) and the Zvw employer levy for wages paid in 2026, monthly or weekly.

Sources: Belastingdienst 'Rekenvoorschriften voor de geautomatiseerde loonadministratie 2026' (uitgave
januari 2026, versie 2) chapter 2 and its tables 1a-6; the official 2026 white tables (wit, standaard,
Nederland: maand, week, vier weken, kwartaal); 'Handboek Loonheffingen 2026' (versie maart 2026):
chapter 6 (voortschrijdend cumulatief rekenen), 7.4-7.6 (AWf, Aof, Wko, Whk), 8.2 (werkgeversheffing
Zvw), 18.21 (AOW age), Bijlage 1 tables 9, 11 and 12.

Bijzondere beloningen (BONUS earnings: vakantiebijslag, 13e maand, gratificaties, ...) use the official
2026 white tabel bijzondere beloningen (wit_bb_nl_std_20260101.xlsx) per Handboek §9.3.6; its percentages
are checked against the Rekenvoorschriften parameters (tariff rates and the credit build-up and phase-out
factors) before it is written into the template.

Before writing, loonheffing.py must reproduce every amount of the four official white tables.
Test cases sample those rows and the special-payments table (official) and add derived cases for the premiums.

Usage: python3 -I build_template.py <folder with wit_mnd/wk/4wk/kw_nl_std.xlsx, wit_bb_nl_std.xlsx> ../../../src/main/resources/payroll-templates/nl/nl-employment.json
"""
import json
import os
import sys
from decimal import ROUND_CEILING, ROUND_FLOOR, ROUND_HALF_UP, Decimal as D

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import loonheffing as lh  # noqa: E402

RETRIEVED = "2026-10-21"
FROM, TO = "2026-01-01", "2026-12-31"

SOURCES = {
    "bd-rekenvoorschriften-2026": {
        "title": "Belastingdienst, Rekenvoorschriften voor de geautomatiseerde loonadministratie 2026, uitgave januari 2026, versie 2 — hoofdstuk 2, tabellen 1a t/m 6",
        "url": "https://download.belastingdienst.nl/belastingdienst/docs/rekenvoorschriften_voor_geautomatiseerde_loonadministratie_lh991z62fd.pdf",
    },
    "bd-witte-tabellen-2026": {
        "title": "Belastingdienst, loonbelastingtabellen 2026 — witte tijdvaktabellen, standaardsituatie, wonen in Nederland (maand, week, vier weken, kwartaal; v01, 1 januari 2026)",
        "url": "https://download.belastingdienst.nl/belastingdienst/dl/rekenhulpen/loonheffing/2026/v01/excel/wit_mnd_nl_std_20260101.xlsx",
    },
    "bd-tabel-bijzondere-beloningen-2026": {
        "title": "Belastingdienst, witte tabel bijzondere beloningen 2026, standaardsituatie, wonen in Nederland (v01, 1 januari 2026); toepassing per Handboek Loonheffingen 2026 §9.3.6",
        "url": "https://download.belastingdienst.nl/belastingdienst/dl/rekenhulpen/loonheffing/2026/v01/excel/wit_bb_nl_std_20260101.xlsx",
    },
    "bd-handboek-2026": {
        "title": "Belastingdienst, Handboek Loonheffingen 2026, versie maart 2026 — hoofdstuk 6 (VCR), 7.4-7.6 (AWf, Aof, Wko, Whk), 8.2 (werkgeversheffing Zvw), 18.21 (AOW-leeftijd), Bijlage 1 tabellen 9, 11, 12",
        "url": "https://download.belastingdienst.nl/belastingdienst/docs/handboek-loonheffingen-lh0221t61fd.pdf",
    },
}
for s in SOURCES.values():
    s["retrieved"] = RETRIEVED

FREQ = {"MONTHLY": ("MAAND", 12), "WEEKLY": ("WEEK", 52)}
# Handboek Bijlage 1, table 11 (loontijdvakbedragen maximumpremieloon/-bijdrageloon) and table 9 / 12.
MAX_PERIOD = {"MONTHLY": D("6617.41"), "WEEKLY": D("1527.09")}
MAX_YEAR = D("79409")
AWF = {"LOW": D("0.0274"), "HIGH": D("0.0774")}
AOF = {"LOW": D("0.0627"), "HIGH": D("0.0763")}
WKO, ZVW = D("0.0050"), D("0.0610")
AOW_AGE_YEARS = 67


def check_handbook(text):
    flat = " ".join(text.split())
    for needle in ["Premie AWf laag 2,74%", "Premie AWf hoog 7,74%", "Gedifferentieerde premie Aof laag 6,27%",
                   "Gedifferentieerde premie Aof hoog 7,63%", "Opslag Wko 0,50%",
                   "€ 305,41 € 1.527,09 € 6.108,38 € 6.617,41 € 19.852,25 € 79.409,00", "werkgeversheffing Zvw 6,10%",
                   "De AOW-leeftijd is voor 2024 tot en met 2027 vastgesteld op 67 jaar",
                   "Werkgeversheffing Zvw (6,10% van € 2.300,00) € 140,30"]:
        assert needle in flat, needle


def check_rules(text):
    flat = " ".join(text.split())
    for needle in ["€ 54", "€ 133.110", "€ 0 a1.1 € 38.883 a2.1 € 0 a1.1 35,75 b1.1 € 0 c1.1",
                   "€ 38.884 a2.1 + 1 € 78.426 a3.1 € 38.883 a2.1 37,56 b2.1 € 13.900 c2.1",
                   "€ 78.427 a3.1 + 1 en meer € 78.426 a3.1 49,50 b3.1 € 28.752 c3.1",
                   "€ 41.124 a2.2 + 1 € 78.426 a3.2 € 41.123 a2.2 37,56 b2.2 € 7.340 c2.2",
                   "€ 78.427 a3.2 + 1 en meer € 78.426 a3.2 49,50 b3.2 € 21.351 c3.2",
                   "€ 38.884 a2.3 + 1 € 78.426 a3.3 € 38.883 a2.3 37,56 b2.3 € 6.940 c2.3",
                   "€ 78.427 a3.3 + 1 en meer € 78.426 a3.3 49,50 b3.3 € 21.792 c3.3",
                   "€ 3.115 ahkm1.1 € 1.556 ahkm1.2", "€ 29.736 ahkg1", "0,06398 ahka1.1 0,03195 ahka1.2",
                   "€ 2.067 oukm1.2", "€ 46.002 oukg1.2", "€ 59.782 oukg2.2", "0,15000 ouka1.2", "€ 540 aok1.2",
                   "0,08324 arko1.1 0,04156 arko1.2", "0,31009 arko2.1 0,15483 arko2.2", "0,01950 arko3.1 0,00974 arko3.2",
                   "0,06510 arka1.1 0,03250 arka1.2", "€ 11.965 arkg1", "€ 25.845 arkg2", "€ 45.592 arkg3", "€ 132.920 arkg4",
                   "€ 996 arkm1.1 € 498 arkm1.2", "€ 5.300 arkm2.1 € 2.647 arkm2.2", "€ 5.685 arkm3.1 € 2.840 arkm3.2"]:
        assert needle in flat, needle


# ------------------------------------------------------------------ tabel bijzondere beloningen

# The table's 13 value columns, in the order of the official xlsx (Rekenvoorschriften tabel 10 lay-out).
BB_COLUMNS = ["g1None", "g1Std", "g1Ver", "g2None", "g2Std", "g2Ver", "g2AokStd", "g2AokVer",
              "g3None", "g3Std", "g3Ver", "g3AokStd", "g3AokVer"]


def read_bb_table(path):
    """[(jaarloon, {column: rate})] from the official xlsx, after checking its lay-out."""
    import openpyxl
    import warnings
    warnings.filterwarnings("ignore")
    rows = list(openpyxl.load_workbook(path, data_only=True).active.iter_rows(values_only=True))
    head = " ".join(str(v) for r in rows[:4] for v in r if v)
    for needle in ("Jaarloon", "Jonger dan AOW-leeftijd", "Geboren in 1945 of eerder", "Geboren in 1946 of later", "standaard", "verrekenings"):
        assert needle in head, needle
    bands = []
    for r in rows:
        if isinstance(r[0], (int, float)) and all(isinstance(v, (int, float)) for v in r[1:14]):
            bands.append((D(str(int(r[0]))), {c: D(str(v)).quantize(D("0.0001")) for c, v in zip(BB_COLUMNS, r[1:14])}))
    assert len(bands) == 14 and bands[0][0] == 0, len(bands)
    assert all(a[0] < b[0] for a, b in zip(bands, bands[1:]))
    return bands


def check_bb_table(bands):
    """Every standard rate is the tariff rate of the band's jaarloon, and every verrekeningspercentage is one
    of the credit build-up / phase-out factors of the Rekenvoorschriften (as percentages to 2 decimals)."""
    pct = lambda x: x.quantize(D("0.0001"), rounding=ROUND_HALF_UP)
    for jaarloon, rates in bands:
        for g in (1, 2, 3):
            t = lh.TARIFF[g]
            rate = t["b1"] if jaarloon <= t["a2"] else (t["b2"] if jaarloon <= t["a3"] else t["b3"])
            cols = [f"g{g}None", f"g{g}Std"] + ([f"g{g}AokStd"] if g != 1 else [])
            for c in cols:
                # Columns with the tax credit are left at 0 where the credits cover all the tax (low jaarloon, AOW age).
                assert rates[c] == pct(rate) or (c != f"g{g}None" and rates[c] == 0), (jaarloon, c, rates[c], rate)
        young, aow = lh.ARK["young"], lh.ARK["aow"]
        ahk_y, ahk_a = lh.AHK["young"][1], lh.AHK["aow"][1]
        factors = {
            "g1Ver": {D(0), -young["o1"], -young["o2"], -young["o3"], ahk_y - young["o3"], ahk_y + young["a1"], young["a1"]},
            "g2Ver": {D(0), ahk_a - aow["o3"], ahk_a + aow["a1"], ahk_a + aow["a1"] + lh.OUK[3], aow["a1"]},
        }
        factors["g2AokVer"] = factors["g3Ver"] = factors["g3AokVer"] = factors["g2Ver"]
        for c, allowed in factors.items():
            assert rates[c] in {pct(f) for f in allowed}, (jaarloon, c, rates[c])


def bb_tax(bands, amount, annual_wage, group, lhk, aok, override=None):
    """Loonbelasting/premie volksverzekeringen on a bijzondere beloning, rounded to cents in the employee's favour."""
    if override is not None:
        rate = D(override)
    else:
        rates = [r for k, r in bands if k <= D(annual_wage)][-1]
        g = f"g{group}"
        rate = rates[g + "None"] if not lhk else rates[g + ("AokStd" if aok else "Std")] + rates[g + ("AokVer" if aok else "Ver")]
    return (D(amount) * rate).quantize(D("0.01"), rounding=ROUND_FLOOR)


# ------------------------------------------------------------------ independent premium calculation (VCR)

def cents(x):
    return x.quantize(D("0.01"), rounding=ROUND_HALF_UP)


def group_of(birth, paid):
    y, m, d = (int(v) for v in birth.split("-"))
    py, pm, pd = (int(v) for v in paid.split("-"))
    aow = (py, pm, pd) >= (y + AOW_AGE_YEARS, m, 1)
    return (2 if y <= 1945 else 3) if aow else 1


def expected_lines(c, bands):
    emp, er = c["employee"], c["employer"]
    freq = c["frequency"]
    f = FREQ[freq][1]
    group = group_of(c["birthDate"], c["paymentDate"])
    pension = D(c.get("preTaxDeductionsByType", {}).get("RETIREMENT", "0"))
    special = D(c.get("earningsByComponent", {}).get("BONUS", "0"))
    wage = D(c["grossEarnings"]) - special - pension
    lines = []
    if wage == 0:
        return lines
    lhk, aok = emp["applyLoonheffingskorting"] == "true", emp.get("alleenstaandeOuderenkorting") == "true"
    tax, _ = lh.period_tax(wage, f, group, lhk, aok)
    lines.append({"step": "wageTax", "side": "TAX", "amount": str(tax)})
    if special != 0:
        lines.append({"step": "specialPaymentTax", "side": "TAX", "amount": str(
            bb_tax(bands, special, emp.get("specialPaymentAnnualWage", "0"), group, lhk, aok, emp.get("specialPaymentRate")))})
    ytd = {k: D(v) for k, v in c.get("ytd", {}).items()}
    bf = emp.get("broughtForwardYear") == "2026"
    g = lambda key: D(emp.get(key, "0")) if bf else D(0)
    periods = ytd.get("wageTax.TAX.periods", D(0)) + g("broughtForwardPeriods") + 1
    cum_max = min(periods * MAX_PERIOD[freq], MAX_YEAR)
    cum_wage = (ytd.get("wageTax.TAX.base", D(0)) + ytd.get("specialPaymentTax.TAX.base", D(0)) + g("broughtForwardWage")
                + wage + special)
    if group == 1:
        base = min(cum_wage, cum_max) - (ytd.get("awf.EMPLOYER.base", D(0)) + g("broughtForwardPremiumBase"))
        if base != 0:
            for step, rate in [("awf", AWF[emp["awfPremium"]]), ("aof", AOF[er["aofPremium"]]), ("wko", WKO), ("whk", D(er["whkRate"]))]:
                # Only AWf records a zero line (it carries the cumulative premium base, like the engine's recordZero).
                if cents(base * rate) != 0 or step == "awf":
                    lines.append({"step": step, "side": "EMPLOYER", "amount": str(cents(base * rate))})
    zvw_base = max(min(cum_wage, cum_max), D(0)) - (ytd.get("zvw.EMPLOYER.base", D(0)) + g("broughtForwardZvwBase"))
    if zvw_base != 0:
        lines.append({"step": "zvw", "side": "EMPLOYER", "amount": str(cents(zvw_base * ZVW))})
    return lines


# ------------------------------------------------------------------ test cases

PERIOD = {"MONTHLY": ("2026-06-01", "2026-06-30", "2026-06-25"), "WEEKLY": ("2026-06-08", "2026-06-14", "2026-06-12")}
BIRTH = {1: "1985-04-12", 2: "1944-02-10", 3: "1955-09-30"}
GROUP_LABEL = {1: "younger than AOW age", 2: "AOW age, born 1945 or earlier", 3: "AOW age, born 1946 or later"}
EMPLOYER = {"aofPremium": "LOW", "whkRate": "0.0152"}


def case(name, evidence, freq, gross, employee, birth, period=None, employer=None, **extra):
    start, end, paid = period or PERIOD[freq]
    c = {"name": name, "evidence": evidence, "periodStart": start, "periodEnd": end, "paymentDate": paid, "frequency": freq,
         "grossEarnings": str(gross), "birthDate": birth, "employer": employer or EMPLOYER,
         "employee": {"awfPremium": "LOW", **employee}}
    c.update(extra)
    return c


def official_cases(tables):
    out = []
    columns = [(1, False, False), (1, True, False), (2, False, False), (2, True, False), (2, True, True), (3, True, False), (3, True, True), (3, False, False)]
    for freq, picks in (("MONTHLY", [0.02, 0.1, 0.22, 0.35, 0.5, 0.63, 0.8, 0.999]), ("WEEKLY", [0.05, 0.3, 0.45, 0.7, 0.9, 0.999])):
        name, f = FREQ[freq]
        rows = tables[name]
        for i, fraction in enumerate(picks):
            k = min(int(fraction * len(rows)), len(rows) - 2)
            group, lhk, aok = columns[(i * 3 + (0 if freq == "MONTHLY" else 1)) % len(columns)]
            col = lh.COLUMNS.index((group, lhk, aok, False)) + 1
            # Alternate the table wage itself and the last cent before the next row.
            wage = rows[k][0] if i % 2 == 0 else rows[k + 1][0] - D("0.01")
            printed = rows[k][col]
            employee = {"applyLoonheffingskorting": str(lhk).lower(), "alleenstaandeOuderenkorting": str(aok).lower()}
            out.append(case(
                f"witte {name.lower()}tabel: {wage} — {GROUP_LABEL[group]}, {'met' if lhk else 'zonder'} loonheffingskorting"
                + (" incl. alleenstaande-ouderenkorting" if aok else ""),
                f"Belastingdienst witte {name.lower()}tabel 2026 (standaard, Nederland), tabelloon {rows[k][0]}: € {printed} in that column; "
                "premiums and Zvw by build_template.py", freq, wage, employee, BIRTH[group]))
            assert lh.period_tax(wage, f, group, lhk, aok)[0] == printed
    return out


def special_payment_cases(bands):
    """Salary plus a bijzondere beloning, one per column family of the official table, and the band edges."""
    bb = "Belastingdienst witte tabel bijzondere beloningen 2026 (standaard, Nederland), jaarloon band {}: {}; Handboek §9.3.6; regular wage, premiums and Zvw by build_template.py"
    picks = [  # (name, jaarloon, group, lhk, aok, salary, bonus)
        ("vakantiebijslag, younger than AOW age, met loonheffingskorting", "50000", 1, True, False, "4000.00", "3200.00"),
        ("vakantiebijslag, younger than AOW age, zonder loonheffingskorting", "30000", 1, False, False, "2500.00", "2000.00"),
        ("13e maand on a low jaarloon: negative verrekeningspercentage", "12500", 1, True, False, "1050.00", "1050.00"),
        ("bonus exactly on a band's jaarloon", "78427", 1, True, False, "6500.00", "5000.00"),
        ("bonus above the highest band", "200000", 1, True, False, "15000.00", "20000.00"),
        ("AOW age, born 1945 or earlier, incl. alleenstaande-ouderenkorting", "40000", 2, True, True, "3300.00", "2600.00"),
        ("AOW age, born 1946 or later, met loonheffingskorting", "100000", 3, True, False, "8000.00", "6400.00"),
        ("AOW age, born 1946 or later, zonder loonheffingskorting", "47000", 3, False, False, "3900.00", "3100.00"),
    ]
    out = []
    for name, jaarloon, group, lhk, aok, salary, bonus in picks:
        rates = [r for k, r in bands if k <= D(jaarloon)][-1]
        key = [k for k, _ in bands if k <= D(jaarloon)][-1]
        employee = {"applyLoonheffingskorting": str(lhk).lower(), "alleenstaandeOuderenkorting": str(aok).lower(),
                    "specialPaymentAnnualWage": jaarloon}
        g = f"g{group}"
        cols = [g + "None"] if not lhk else [g + ("AokStd" if aok else "Std"), g + ("AokVer" if aok else "Ver")]
        out.append(case(f"bijzondere beloning: {name}", bb.format(key, " + ".join(str(rates[c]) for c in cols)), "MONTHLY",
                        str(D(salary) + D(bonus)), employee, BIRTH[group], earningsByComponent={"BASIC": salary, "BONUS": bonus}))
    return out


def test_cases(tables, bands):
    derived = "Derived from the Rekenvoorschriften 2026 and Handboek Loonheffingen 2026 by build_template.py"
    young = {"applyLoonheffingskorting": "true"}
    with_bb = {"applyLoonheffingskorting": "true", "specialPaymentAnnualWage": "52000"}
    cases = official_cases(tables) + special_payment_cases(bands) + [
        case("bijzondere beloning at a machtiging lager percentage", "Handboek §9.3.6 'Machtiging lager percentage': the authorised 30% replaces the table",
             "MONTHLY", "9000.00", {"applyLoonheffingskorting": "true", "specialPaymentRate": "0.30"}, BIRTH[1],
             earningsByComponent={"BASIC": "4500.00", "BONUS": "4500.00"}),
        case("vakantiebijslag in May: premiums on salary and bonus, capped at the cumulative maximum", derived + "; Handboek §6.3 (VCR) and §9.3.6",
             "MONTHLY", "9000.00", with_bb, BIRTH[1], period=("2026-05-01", "2026-05-31", "2026-05-22"),
             earningsByComponent={"BASIC": "4300.00", "BONUS": "4700.00"},
             ytd={"wageTax.TAX.base": "17200.00", "wageTax.TAX.periods": "4", "awf.EMPLOYER.base": "17200.00", "zvw.EMPLOYER.base": "17200.00"}),
        case("the month after the vakantiebijslag counts it in the cumulative wage", derived + "; Handboek §6.3 (VCR): the bonus month's wage stays in the cumulative wage",
             "MONTHLY", "4300.00", young, BIRTH[1], period=("2026-06-01", "2026-06-30", "2026-06-25"),
             ytd={"wageTax.TAX.base": "21500.00", "wageTax.TAX.periods": "5", "specialPaymentTax.TAX.base": "4700.00",
                  "awf.EMPLOYER.base": "26200.00", "zvw.EMPLOYER.base": "26200.00"}),
        case("Handboek Zvw example: € 2.400 with € 100 pension premium", "Handboek Loonheffingen 2026 §8.2 'Voorbeeldberekening werkgeversheffing Zvw': "
             "€ 2.300 loon voor de Zvw, werkgeversheffing 6,10% = € 140,30; wage tax and premiums by build_template.py",
             "MONTHLY", "2400.00", young, BIRTH[1], preTaxDeductionsByType={"RETIREMENT": "100.00"}),
        case("above the highest table wage, monthly", derived + "; tax at € 11.092,50 plus 49,50% of the excess, rounded down",
             "MONTHLY", "15000.00", young, BIRTH[1]),
        case("above the highest table wage, weekly, AOW incl. alleenstaande-ouderenkorting", derived, "WEEKLY", "3100.00",
             {"applyLoonheffingskorting": "true", "alleenstaandeOuderenkorting": "true"}, BIRTH[3]),
        case("high AWf premium and large-employer Aof", derived, "MONTHLY", "4200.00", {"applyLoonheffingskorting": "true", "awfPremium": "HIGH"},
             BIRTH[1], employer={"aofPremium": "HIGH", "whkRate": "0.0094"}),
        case("cumulative maximum catches up in March", derived + "; Handboek §6.3 voorbeeld 2 with the 2026 maximum", "MONTHLY", "8000.00", young,
             BIRTH[1], period=("2026-03-01", "2026-03-31", "2026-03-25"),
             ytd={"wageTax.TAX.base": "12000.00", "wageTax.TAX.periods": "2", "awf.EMPLOYER.base": "12000.00", "zvw.EMPLOYER.base": "12000.00"}),
        case("cumulative maximum already reached", derived, "MONTHLY", "9000.00", young, BIRTH[1], period=("2026-07-01", "2026-07-31", "2026-07-24"),
             ytd={"wageTax.TAX.base": "54000.00", "wageTax.TAX.periods": "6", "awf.EMPLOYER.base": "39704.46", "zvw.EMPLOYER.base": "39704.46"}),
        case("annual maximum in a 53-week year", derived + "; Handboek §6.3 Let op 1: € 79.409 is an absolute maximum", "WEEKLY", "2000.00", young,
             BIRTH[1], period=("2026-12-28", "2027-01-03", "2026-12-31"),
             ytd={"wageTax.TAX.base": "104000.00", "wageTax.TAX.periods": "52", "awf.EMPLOYER.base": "79408.68", "zvw.EMPLOYER.base": "79408.68"}),
        case("turns 67 in June: AOW column, no employee-insurance premiums, Zvw continues", derived + "; Handboek §18.21",
             "MONTHLY", "3500.00", young, "1959-06-20"),
        case("turns 67 in July: still younger than AOW age in June", derived + "; Handboek §18.21", "MONTHLY", "3500.00", young, "1959-07-02"),
        case("mid-year adoption with brought-forward figures", derived, "MONTHLY", "5000.00",
             {"applyLoonheffingskorting": "true", "broughtForwardYear": "2026", "broughtForwardPeriods": "9", "broughtForwardWage": "45000.00",
              "broughtForwardPremiumBase": "45000.00", "broughtForwardZvwBase": "45000.00"}, BIRTH[1],
             period=("2026-10-01", "2026-10-31", "2026-10-23"), employmentStartDate="2018-02-01"),
        case("no wage in the period: no loontijdvak, nothing charged", derived + "; Handboek §6.2 d", "MONTHLY", "0.00",
             {"applyLoonheffingskorting": "true", "broughtForwardYear": "2026", "broughtForwardPeriods": "5", "broughtForwardWage": "40000.00",
              "broughtForwardPremiumBase": "30000.00", "broughtForwardZvwBase": "30000.00"}, BIRTH[1],
             period=("2026-07-01", "2026-07-31", "2026-07-24"), employmentStartDate="2018-02-01"),
    ]
    for c in cases:
        c["expectLines"] = expected_lines(c, bands)
    errors = [
        case("bonus without the jaarloon for the table", "Handboek §9.3.6: the table needs the jaarloon", "MONTHLY", "6000.00", young, BIRTH[1],
             earningsByComponent={"BASIC": "4000.00", "BONUS": "2000.00"}, expectError="jaarloon"),
        case("bonus paid without the period's regular wage", "a bijzondere beloning on its own is not supported", "MONTHLY", "2000.00", with_bb, BIRTH[1],
             earningsByComponent={"BONUS": "2000.00"}, expectError="regular wage"),
        case("employee paid earlier this year without brought-forward figures", "VCR needs this year's earlier wages", "MONTHLY", "5000.00", young, BIRTH[1],
             period=("2026-10-01", "2026-10-31", "2026-10-23"), employmentStartDate="2018-02-01", expectError="No earlier payroll this year"),
        case("alleenstaande-ouderenkorting below AOW age is refused", "Rekenvoorschriften 2.2.3.3", "MONTHLY", "3000.00",
             {"applyLoonheffingskorting": "true", "alleenstaandeOuderenkorting": "true"}, BIRTH[1], expectError="alleenstaande-ouderenkorting"),
        case("jonggehandicaptenkorting is refused", "not in the tables (Rekenvoorschriften chapter 5)", "MONTHLY", "3000.00",
             {"applyLoonheffingskorting": "true", "jonggehandicaptenkorting": "true"}, BIRTH[1], expectError="jonggehandicaptenkorting"),
        case("non-resident is refused", "chapter 8 tables not modelled", "MONTHLY", "3000.00",
             {"applyLoonheffingskorting": "true", "residentOfNetherlands": "false"}, BIRTH[1], expectError="Nederland"),
        case("government employer is refused", "Ufo premium not modelled", "MONTHLY", "3000.00", young, BIRTH[1],
             employer={**EMPLOYER, "governmentEmployer": "true"}, expectError="Overheidswerkgevers"),
        case("employee-paid Zvw is refused", "inhouding bijdrage Zvw not modelled", "MONTHLY", "3000.00",
             {"applyLoonheffingskorting": "true", "zvwWithheldFromEmployee": "true"}, BIRTH[1], expectError="bijdrage Zvw"),
        case("earnings not subject to employee insurance are refused", "SV wage must equal the wage for the wage tax", "MONTHLY", "3000.00", young,
             BIRTH[1], socialEarnings="2500.00", expectError="werknemersverzekeringen"),
        case("other pre-tax deduction types are refused", "only the employee's pension premium is deductible here", "MONTHLY", "3000.00", young,
             BIRTH[1], preTaxDeductionsByType={"INSURANCE": "50.00"}, expectError="pensioenpremie"),
        case("2027 is not covered", "2027 tables are published in December 2026", "MONTHLY", "3000.00", young, BIRTH[1],
             period=("2027-01-01", "2027-01-31", "2027-01-25"), expectError="a newer template version is needed"),
    ]
    return cases + errors


# ------------------------------------------------------------------ template

def p(value, source):
    return [{"from": FROM, "to": TO, "value": str(value), "source": source}]



# Statutory leave pay (PayrollTemplate.leavePay): applied as a leave pay rule where the workspace has none of its own.
LEAVE_SOURCES = {'bw-7-629': {'title': 'Burgerlijk Wetboek Boek 7 art. 629 (loondoorbetaling bij ziekte), as explained by the '
                       "Rijksoverheid, 'Hoeveel loon krijg ik doorbetaald als ik ziek ben?': 'U ontvangt in het 1e "
                       "ziektejaar minstens 70% van uw loon en minimaal het minimumloon', 'Ook in uw 2e ziektejaar "
                       "betaalt uw werkgever u minimaal 70% van uw loon', 100% bij ziekte door zwangerschap, bevalling "
                       'of orgaandonatie',
              'url': 'https://www.rijksoverheid.nl/vraag-en-antwoord/ziekteverzuim-van-het-werk/hoeveel-loon-krijg-ik-doorbetaald-als-ik-ziek-ben',
              'retrieved': '2026-10-26'}}

LEAVE_PAY = [{'leaveType': 'SICK',
  'deductionPercentage': '100',
  'payBands': [{'fromDay': 1, 'toDay': 728, 'deductionPercentage': '30'}],
  'source': 'bw-7-629',
  'note': "The legal minimum: 70% of the wage for up to 104 weeks. Not applied: the first year's floor at the minimum "
          'wage, and 100% for illness from pregnancy, childbirth or organ donation. Most collective agreements (cao) '
          'pay more, often 100% in the first year: add a workspace rule.'}]

LEAVE_CHANGE = {'version': '2026.1.2',
 'date': '2026-10-26',
 'summary': 'Statutory sick pay as a leave pay rule: 70% of the wage for up to 104 weeks (BW 7:629), the legal '
            'minimum; applied where the workspace has no sick leave rule of its own. Before, sick days were paid in '
            'full unless a rule was added.'}


def main():
    folder, out = sys.argv[1], sys.argv[2]
    paths = {"MAAND": "wit_mnd_nl_std.xlsx", "WEEK": "wit_wk_nl_std.xlsx", "VIERWEKEN": "wit_4wk_nl_std.xlsx", "KWARTAAL": "wit_kw_nl_std.xlsx"}
    paths = {k: os.path.join(folder, v) for k, v in paths.items()}
    print("white-table amounts reproduced:", lh.check_tables(paths))
    check_rules(open(os.path.join(folder, "rekenvoorschriften.txt"), encoding="utf-8").read())
    check_handbook(open(os.path.join(folder, "handboek.txt"), encoding="utf-8").read())
    tables = {k: lh.read_table(v) for k, v in paths.items()}
    bands = read_bb_table(os.path.join(folder, "wit_bb_nl_std.xlsx"))
    check_bb_table(bands)
    print("bijzondere-beloningen bands checked:", len(bands))

    R, H, BB = "bd-rekenvoorschriften-2026", "bd-handboek-2026", "bd-tabel-bijzondere-beloningen-2026"
    params = {
        "lv": p(lh.LV, R), "lmax": p(lh.LMAX, R), "ahkg1": p(lh.AHKG1, R), "ahkg2": p(lh.AHKG2, R),
        "oukg1": p(lh.OUK[1], R), "oukg2": p(lh.OUK[2], R),
        "arkg1": p(lh.ARKG[0], R), "arkg2": p(lh.ARKG[1], R), "arkg3": p(lh.ARKG[2], R), "arkg4": p(lh.ARKG[3], R),
        "aowAgeYears": p(AOW_AGE_YEARS, H), "maxPremiumWageYear": p(MAX_YEAR, H),
        "awfLow": p(AWF["LOW"], H), "awfHigh": p(AWF["HIGH"], H), "aofLow": p(AOF["LOW"], H), "aofHigh": p(AOF["HIGH"], H),
        "wko": p(WKO, H), "zvw": p(ZVW, H),
    }
    tariff_rows = [{"key": str(g), "values": [str(t[k]) for k in ("a2", "a3", "b1", "b2", "b3", "c2", "c3")]} for g, t in lh.TARIFF.items()]
    credit_rows = []
    for g in (1, 2, 3):
        kind = "young" if g == 1 else "aow"
        ahkm, ahka = lh.AHK[kind]
        a = lh.ARK[kind]
        oukm, ouka, aok = (D(0), D(0), D(0)) if g == 1 else (lh.OUK[0], lh.OUK[3], lh.AOK)
        credit_rows.append({"key": str(g), "values": [str(v) for v in (ahkm, ahka, oukm, ouka, aok, a["o1"], a["o2"], a["o3"], a["a1"], a["m1"], a["m2"], a["m3"])]})

    t = lambda table, col: f"#table.lookup('{table}', #group, '{col}')"
    cr = lambda col: t("credits", col)
    bf = "(#broughtForward ? #coalesce(#employee['{0}'], 0) : 0)"
    template = {
        "schemaVersion": 1,
        "id": "nl-employment",
        "name": "Nederland — loonheffingen (witte tabel), premies werknemersverzekeringen en werkgeversheffing Zvw",
        "version": "2026.1.2",
        "status": "LAW_DERIVED",
        "jurisdiction": {"country": "NL"},
        "validity": {"from": FROM, "to": TO},
        "currency": "EUR",
        "payFrequencies": ["MONTHLY", "WEEKLY"],
        "taxYearStart": "--01-01",
        "dateBasis": "PAYMENT_DATE",
        "rounding": {"scale": 2, "mode": "HALF_UP"},
        "changelog": [
            LEAVE_CHANGE,{"version": "2026.1.1", "date": "2026-10-25",
                       "summary": "Bijzondere beloningen (BONUS: vakantiebijslag, 13e maand, gratificaties) are taxed with the 2026 witte tabel bijzondere beloningen on the employee's jaarloon (Handboek §9.3.6), or a percentage from a machtiging; they count in the cumulative wage for the premiums and the Zvw. Before, a period with a BONUS was refused."},
                      {"version": "2026.1.0", "date": RETRIEVED,
                       "summary": "2026: loonbelasting/premie volksverzekeringen per de Rekenvoorschriften 2026 (witte tabel, standaardsituatie, wonen in Nederland); AWf, Aof, opslag Wko, Whk en werkgeversheffing Zvw met voortschrijdend cumulatief rekenen."}],
        "sources": {**SOURCES, **LEAVE_SOURCES},
        "inputs": {
            "employer": [
                {"key": "aofPremium", "type": "ENUM", "required": True, "values": ["LOW", "HIGH"],
                 "description": "Gedifferentieerde premie Aof: LOW for a small employer (premieloon 2024 ≤ € 1.082.500, see the Whk beschikking), HIGH otherwise"},
                {"key": "whkRate", "type": "DECIMAL", "required": True, "min": -1, "max": 1,
                 "description": "Gedifferentieerde premie Whk from the 2026 beschikking/mededeling, as a fraction (e.g. 0.0152 for 1,52%)"},
                {"key": "governmentEmployer", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "Overheidswerkgever (Ufo premium instead of AWf for government employees — not supported)"},
            ],
            "employee": [
                {"key": "applyLoonheffingskorting", "type": "BOOLEAN", "required": True,
                 "description": "The employee asked in writing to apply the loonheffingskorting with this employer"},
                {"key": "alleenstaandeOuderenkorting", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "Apply the alleenstaande-ouderenkorting (AOW age only, at the employee's written request)"},
                {"key": "awfPremium", "type": "ENUM", "required": True, "values": ["LOW", "HIGH"],
                 "description": "Premie AWf: LOW for a written permanent contract (or the other cases of Handboek §7.4), HIGH otherwise"},
                {"key": "jonggehandicaptenkorting", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "The employee is entitled to the jonggehandicaptenkorting (not supported)"},
                {"key": "residentOfNetherlands", "type": "BOOLEAN", "required": False, "defaultValue": "true",
                 "description": "Lives in the Netherlands (other countries use other tables — not supported)"},
                {"key": "zvwWithheldFromEmployee", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "Bijdrage Zvw is withheld from the employee (e.g. a dga) instead of the werkgeversheffing (not supported)"},
                {"key": "specialPaymentAnnualWage", "type": "DECIMAL", "required": False, "min": 0,
                 "description": "Jaarloon voor de tabel bijzondere beloningen (Handboek §9.3.6), needed when a bonus, vakantiebijslag or 13e maand is paid: last year's loon voor de loonbelasting/premie volksverzekeringen from you (loonstaat kolom 14); annualised if the employee was paid for part of last year (e.g. € 3.000 over 1,5 months = € 24.000); the expected wage for the whole year if they joined this year"},
                {"key": "specialPaymentRate", "type": "DECIMAL", "required": False, "min": 0, "max": 1,
                 "description": "Percentage for bijzondere beloningen instead of the table, as a fraction: from a machtiging lager percentage of the Belastingdienst for this year, or a higher percentage the employee asked for in writing (Handboek §9.3.6, §9.3.10)"},
                {"key": "broughtForwardYear", "type": "INTEGER", "required": False, "min": 2000, "max": 2100,
                 "description": "Year the brought-forward figures belong to; other years' figures are ignored"},
                {"key": "broughtForwardPeriods", "type": "INTEGER", "required": False, "min": 0, "max": 53,
                 "description": "Loontijdvakken with wage this year before the first payroll in Humano"},
                {"key": "broughtForwardWage", "type": "DECIMAL", "required": False,
                 "description": "Cumulative loon voor de werknemersverzekeringen this year before the first payroll in Humano"},
                {"key": "broughtForwardPremiumBase", "type": "DECIMAL", "required": False,
                 "description": "Cumulative premieloon (grondslag werknemersverzekeringen) this year before the first payroll in Humano"},
                {"key": "broughtForwardZvwBase", "type": "DECIMAL", "required": False, "min": 0,
                 "description": "Cumulative bijdrageloon Zvw (grondslag) this year before the first payroll in Humano"},
            ],
        },
        "parameters": params,
        "tables": {
            "tariff": {"kind": "EXACT", "description": "Rekenvoorschriften tabel 2 per age group (1 younger than AOW age, 2 AOW age born 1945 or earlier, 3 AOW age born 1946 or later)",
                       "columns": ["a2", "a3", "b1", "b2", "b3", "c2", "c3"],
                       "versions": [{"from": FROM, "to": TO, "source": R, "rows": tariff_rows}]},
            "credits": {"kind": "EXACT", "description": "Rekenvoorschriften tabellen 3-6 per age group: AHK, OUK, AOK and ARK amounts and factors",
                        "columns": ["ahkm", "ahka", "oukm", "ouka", "aok", "arko1", "arko2", "arko3", "arka", "arkm1", "arkm2", "arkm3"],
                        "versions": [{"from": FROM, "to": TO, "source": R, "rows": credit_rows}]},
            "specialPayments": {"kind": "EXACT", "description": "Witte tabel bijzondere beloningen: per jaarloon band (the row of the highest jaarloon not above the employee's), the standaardtarief and verrekeningspercentage loonheffingskorting per age group",
                                "columns": BB_COLUMNS,
                                "versions": [{"from": FROM, "to": TO, "source": BB,
                                              "rows": [{"key": str(k), "values": [str(r[c]) for c in BB_COLUMNS]} for k, r in bands]}]},
            "maxPremiumWage": {"kind": "EXACT", "description": "Handboek Bijlage 1 tabel 11: loontijdvakbedrag maximumpremieloon/-bijdrageloon",
                               "versions": [{"from": FROM, "to": TO, "source": H,
                                             "rows": [{"key": k, "values": [str(v)]} for k, v in MAX_PERIOD.items()]}]},
        },
        "steps": [
            {"id": "sameWage", "type": "REQUIRE", "condition": "#socialEarnings == #taxableEarnings",
             "message": "Every earning must count for both the wage tax and the werknemersverzekeringen in this template: check the earnings' Taxable and Subject to social contributions switches under Payroll → Pay rules"},
            {"id": "onlyPension", "type": "REQUIRE", "condition": "#preTaxDeductions == #preTaxDeductionsByType['RETIREMENT']",
             "message": "Only the employee's pensioenpremie (pre-tax RETIREMENT deduction) is supported as a deduction for the loonheffingen"},
            {"id": "resident", "type": "REQUIRE", "condition": "#employee['residentOfNetherlands']",
             "message": "Only employees living in Nederland are supported (standard white table)"},
            {"id": "noJgk", "type": "REQUIRE", "condition": "!#employee['jonggehandicaptenkorting']",
             "message": "The jonggehandicaptenkorting is not supported"},
            {"id": "noGovernment", "type": "REQUIRE", "condition": "!#employer['governmentEmployer']",
             "message": "Overheidswerkgevers (Ufo premium) are not supported"},
            {"id": "zvwByEmployer", "type": "REQUIRE", "condition": "!#employee['zvwWithheldFromEmployee']",
             "message": "Inhouding van de bijdrage Zvw (instead of the werkgeversheffing) is not supported"},
            {"id": "aow", "type": "VALUE",
             "expr": "#paymentDate >= #birthDate.plusYears(#p['aowAgeYears']).withDayOfMonth(1)"},
            {"id": "group", "type": "VALUE", "expr": "#aow ? (#birthYear <= 1945 ? 2 : 3) : 1"},
            {"id": "aokOnlyAow", "type": "REQUIRE", "when": "#employee['alleenstaandeOuderenkorting']", "condition": "#aow",
             "message": "The alleenstaande-ouderenkorting only applies from the month the employee reaches the AOW age"},
            {"id": "broughtForward", "type": "VALUE",
             "expr": "#employee['broughtForwardYear'] != null and #employee['broughtForwardYear'] == #taxYear"},
            {"id": "earlierPayThisYear", "type": "REQUIRE",
             "when": "#ytd.periods('wageTax', 'TAX') == 0 and #employmentStartDate < #periodStart and #periodStart > #taxYearStartDate",
             "condition": "#broughtForward",
             "message": "No earlier payroll this year is recorded for this employee, who was employed before this period: set broughtForwardYear and this year's cumulative figures (loontijdvakken, SV-loon, premieloon, bijdrageloon Zvw; 0 if none) so the maximum premium wage applies correctly"},
            # Bijzondere beloningen (Handboek §9.3.6): the BONUS earnings, taxed apart from the period wage.
            {"id": "specialPayment", "type": "VALUE", "expr": "#earningsByComponent['BONUS']"},
            # Loonbelasting/premie volksverzekeringen: Rekenvoorschriften chapter 2.
            {"id": "wage", "type": "VALUE", "expr": "#taxableEarnings - #specialPayment - #reductions - #preTaxDeductions"},
            {"id": "specialPaymentWithWage", "type": "REQUIRE", "when": "#specialPayment != 0", "condition": "#wage != 0",
             "message": "A bijzondere beloning (bonus, vakantiebijslag, 13e maand) must be paid in a run with the period's regular wage; a payment of only bijzondere beloningen is not supported"},
            {"id": "specialPaymentJaarloon", "type": "REQUIRE",
             "when": "#specialPayment != 0 and #employee['specialPaymentRate'] == null",
             "condition": "#employee['specialPaymentAnnualWage'] != null", "detail": "specialPaymentAnnualWage",
             "message": "A bijzondere beloning is taxed with the tabel bijzondere beloningen, which needs the employee's jaarloon (last year's wage from you, annualised, or this year's expected wage for a new employee): enter specialPaymentAnnualWage in their details for this template"},
            {"id": "annualWage", "type": "VALUE", "expr": "#wage * #periodsPerYear"},
            {"id": "tableWage", "type": "VALUE",
             "expr": "#annualWage <= 0 ? 0 : (#annualWage > #p['lmax'] ? #p['lmax'] : #divide(#annualWage, #p['lv'], 0, 'FLOOR') * #p['lv'])"},
            {"id": "x1", "type": "VALUE",
             "expr": f"#pos(#round(#tableWage <= {t('tariff', 'a2')} ? #tableWage * {t('tariff', 'b1')} : "
                     f"(#tableWage <= {t('tariff', 'a3')} ? (#tableWage - {t('tariff', 'a2')}) * {t('tariff', 'b2')} + {t('tariff', 'c2')} : "
                     f"(#tableWage - {t('tariff', 'a3')}) * {t('tariff', 'b3')} + {t('tariff', 'c3')}), 0, 'FLOOR'))"},
            {"id": "ahk", "type": "VALUE",
             "expr": f"#tableWage <= #p['ahkg1'] ? {cr('ahkm')} : (#tableWage <= #p['ahkg2'] ? #pos(#round({cr('ahkm')} - (#tableWage - #p['ahkg1']) * {cr('ahka')}, 0, 'CEILING')) : 0)"},
            {"id": "ouk", "type": "VALUE",
             "expr": f"#tableWage <= #p['oukg1'] ? {cr('oukm')} : (#tableWage <= #p['oukg2'] ? #pos(#round({cr('oukm')} - (#tableWage - #p['oukg1']) * {cr('ouka')}, 0, 'CEILING')) : 0)"},
            {"id": "ark1", "type": "VALUE", "expr": f"#min(#round({cr('arko1')} * #tableWage, 5, 'HALF_UP'), {cr('arkm1')})"},
            {"id": "ark2", "type": "VALUE", "expr": f"#min(#ark1 + #round({cr('arko2')} * #pos(#tableWage - #p['arkg1']), 5, 'HALF_UP'), {cr('arkm2')})"},
            {"id": "ark3", "type": "VALUE", "expr": f"#min(#ark2 + #round({cr('arko3')} * #pos(#tableWage - #p['arkg2']), 5, 'HALF_UP'), {cr('arkm3')})"},
            {"id": "ark", "type": "VALUE",
             "expr": f"#tableWage > #p['arkg4'] ? 0 : #pos(#round(#ark3 - #round({cr('arka')} * #pos(#tableWage - #p['arkg3']), 5, 'HALF_UP'), 0, 'CEILING'))"},
            {"id": "aok", "type": "VALUE", "when": "#employee['alleenstaandeOuderenkorting']", "expr": cr("aok")},
            {"id": "credits", "type": "VALUE", "expr": "#employee['applyLoonheffingskorting'] ? #ahk + #ouk + #ark + #aok : 0"},
            {"id": "annualTax", "type": "VALUE", "expr": "#pos(#x1 - #credits)"},
            {"id": "aboveTable", "type": "VALUE", "when": "#annualWage > #p['lmax']",
             "expr": f"#round((#wage - #divide(#p['lmax'], #periodsPerYear, 2, 'CEILING')) * {t('tariff', 'b3')}, 2, 'FLOOR')"},
            {"id": "wageTax", "type": "TAX", "label": "Loonbelasting/premie volksverzekeringen", "ledger": "INCOME_TAX", "source": R,
             "when": "#wage != 0", "base": "#wage", "recordZero": True,
             "amount": "#divide(#annualTax, #periodsPerYear, 2, 'HALF_UP') + #aboveTable"},
            {"id": "specialPaymentRate", "type": "VALUE", "when": "#specialPayment != 0",
             "expr": "#employee['specialPaymentRate'] != null ? #employee['specialPaymentRate'] : "
                     "(!#employee['applyLoonheffingskorting'] ? #table.band('specialPayments', #employee['specialPaymentAnnualWage'], 'g' + #group + 'None') : (#employee['alleenstaandeOuderenkorting'] ? #table.band('specialPayments', #employee['specialPaymentAnnualWage'], 'g' + #group + 'AokStd') + #table.band('specialPayments', #employee['specialPaymentAnnualWage'], 'g' + #group + 'AokVer') : #table.band('specialPayments', #employee['specialPaymentAnnualWage'], 'g' + #group + 'Std') + #table.band('specialPayments', #employee['specialPaymentAnnualWage'], 'g' + #group + 'Ver')))"},
            {"id": "specialPaymentTax", "type": "TAX", "label": "Loonbelasting/premie volksverzekeringen bijzondere beloning", "ledger": "INCOME_TAX",
             "source": BB, "when": "#specialPayment != 0", "base": "#specialPayment", "recordZero": True, "allowNegative": True,
             "amount": "#round(#specialPayment * #specialPaymentRate, 2, 'FLOOR')"},
            # Premies werknemersverzekeringen and werkgeversheffing Zvw: voortschrijdend cumulatief rekenen (Handboek chapter 6).
            {"id": "periods", "type": "VALUE",
             "expr": "#ytd.periods('wageTax', 'TAX') + " + bf.format("broughtForwardPeriods") + " + (#wage != 0 ? 1 : 0)"},
            {"id": "cumulativeMaximum", "type": "VALUE",
             "expr": "#min(#periods * #table.lookup('maxPremiumWage', #frequency), #p['maxPremiumWageYear'])"},
            {"id": "cumulativeWage", "type": "VALUE",
             "expr": "#ytd.base('wageTax', 'TAX') + #ytd.base('specialPaymentTax', 'TAX') + " + bf.format("broughtForwardWage") + " + #wage + #specialPayment"},
            {"id": "premiumBase", "type": "VALUE", "when": "!#aow and #wage != 0",
             "expr": "#min(#cumulativeWage, #cumulativeMaximum) - (#ytd.base('awf', 'EMPLOYER') + " + bf.format("broughtForwardPremiumBase") + ")"},
            {"id": "awf", "type": "CONTRIBUTION", "label": "Premie AWf", "ledger": "UNEMPLOYMENT", "source": H,
             "when": "#premiumBase != 0", "base": "#premiumBase", "recordZero": True, "allowNegative": True,
             "employer": {"rate": "#employee['awfPremium'] == 'LOW' ? #p['awfLow'] : #p['awfHigh']"}},
            {"id": "aof", "type": "CONTRIBUTION", "label": "Premie Aof", "ledger": "DISABILITY", "source": H,
             "when": "#premiumBase != 0", "base": "#premiumBase", "allowNegative": True,
             "employer": {"rate": "#employer['aofPremium'] == 'LOW' ? #p['aofLow'] : #p['aofHigh']"}},
            {"id": "wko", "type": "CONTRIBUTION", "label": "Opslag Wko", "ledger": "OTHER", "source": H,
             "when": "#premiumBase != 0", "base": "#premiumBase", "allowNegative": True, "employer": {"rate": "#p['wko']"}},
            {"id": "whk", "type": "CONTRIBUTION", "label": "Premie Whk", "ledger": "DISABILITY", "source": H,
             "when": "#premiumBase != 0", "base": "#premiumBase", "allowNegative": True, "employer": {"rate": "#employer['whkRate']"}},
            # No wage, no loontijdvak (Handboek §6.2 d): nothing is charged, whatever the cumulative figures say.
            {"id": "zvwBase", "type": "VALUE", "when": "#wage != 0",
             "expr": "#pos(#min(#cumulativeWage, #cumulativeMaximum)) - (#ytd.base('zvw', 'EMPLOYER') + " + bf.format("broughtForwardZvwBase") + ")"},
            {"id": "zvw", "type": "CONTRIBUTION", "label": "Werkgeversheffing Zvw", "ledger": "MEDICARE", "source": H,
             "when": "#zvwBase != 0", "base": "#zvwBase", "recordZero": True, "allowNegative": True, "employer": {"rate": "#p['zvw']"}},
        ],
        "leavePay": LEAVE_PAY,
        "limitations": [
            "Monthly and weekly pay (Dutch loontijdvakken); four-weekly, quarterly and daily tables are not used by Humano's pay frequencies.",
            "White table, standard situation, employee living in the Netherlands; herleidingssituaties, non-residents and the green table are refused or out of scope.",
            "Bijzondere beloningen are the BONUS earnings (vakantiebijslag, 13e maand, eindejaarsuitkering, gratificaties, uitbetaalde vakantiedagen): taxed with the witte tabel bijzondere beloningen on the jaarloon entered per employee (specialPaymentAnnualWage; Handboek §9.3.6), or a percentage from a machtiging or the employee's written request (specialPaymentRate). Holiday allowance paid monthly with the wage through another component is period wage (§9.3.6 Let op 1). They must be paid in a run with the period's regular wage. The voordeligheidsregel and the herberekeningsmethode for back pay are not offered; the jaarloon is not derived from earlier payrolls.",
            "Above the highest table wage the 'tabelsystematiek' (systematiek 1) is used; the Rekenvoorschriften allow a small difference with the rekenregel.",
            "The verrekende arbeidskorting is not recorded on the payslip (needed for the loonaangifte).",
            "Not supported: jonggehandicaptenkorting, overheidswerkgevers (Ufo), inhouding bijdrage Zvw (dga's), eigenrisicodragers, the premiekorting/LIV and other reliefs, deductions other than the employee's pensioenpremie.",
            "The AWf premium (LOW/HIGH) and the Aof size class and Whk rate are the employer's responsibility; the AWf correction for flexible hours is not applied, and one AWf rate applies per employee per period (no pro-rata split for concurrent contracts, Handboek §6.7.2).",
            "A negative period wage gives no wage tax (no refund through the period table); premiums and the Zvw follow VCR, including negative premiums.",
            "Status LAW_DERIVED: the wage tax reproduces all 108,460 amounts of the 2026 white tables (maand, week, vier weken, kwartaal; checked by build_template.py; 14 of them are test cases); the tabel bijzondere beloningen is the official 2026 file, its percentages checked against the Rekenvoorschriften parameters (8 sampled rows are test cases); premiums are derived from the Handboek.",
        ],
        "testCases": test_cases(tables, bands),
    }
    with open(out, "w", encoding="utf-8") as fh:
        json.dump(template, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    print("wrote", out, "with", len(template["testCases"]), "test cases")


if __name__ == "__main__":
    main()
