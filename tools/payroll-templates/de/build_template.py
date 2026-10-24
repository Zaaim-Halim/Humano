"""Build src/main/resources/payroll-templates/de/de-employment.json — German Lohnsteuer,
Solidaritätszuschlag and Kirchensteuer on current monthly wages (PAP 2026 via the de.lohnsteuer-pap-2026
extension) and the Gesamtsozialversicherungsbeitrag (KV, RV, AV, PV) with the U1, U2 and U3 levies, for
wage periods in 2026.

Every rate and ceiling is checked against the statute texts (gesetze-im-internet.de snapshots) before
writing. Expected tax amounts in the test cases come from lohnsteuer_statute.py (written from the EStG,
independent of the PAP code); contributions from the BVV rules implemented below.

Usage: python3 -I build_template.py <folder with the law snapshots> ../../../src/main/resources/payroll-templates/de/de-employment.json
"""
import html
import json
import os
import re
import sys
from decimal import ROUND_FLOOR, ROUND_HALF_UP, Decimal as D

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lohnsteuer_statute as ls  # noqa: E402

RETRIEVED = "2026-10-21"
FROM, TO = "2026-01-01", "2026-12-31"
GII = "https://www.gesetze-im-internet.de/"

SOURCES = {
    "bmf-pap-2026": {
        "title": "BMF-Schreiben vom 12. November 2025 (IV C 5 - S 2361/00025/016/028), Anlage 1: Programmablaufplan für die maschinelle Berechnung der vom Arbeitslohn einzubehaltenden Lohnsteuer, des Solidaritätszuschlags und der Maßstabsteuer für die Kirchenlohnsteuer für 2026; XML-Pseudocode Lohnsteuer2026 (ITZBund)",
        "url": "https://www.bmf-steuerrechner.de/javax.faces.resource/daten/xmls/Lohnsteuer2026.xml.xhtml",
    },
    "estg": {"title": "Einkommensteuergesetz §§ 9a, 10c, 24a, 24b, 32 Abs. 6, 32a, 39b, 39f, 51a", "url": GII + "estg/"},
    "solzg": {"title": "Solidaritätszuschlaggesetz 1995 §§ 3, 4", "url": GII + "solzg_1995/"},
    "svbezgrv-2026": {"title": "Sozialversicherungsrechengrößen-Verordnung 2026 §§ 2, 4 (BGBl. 2025 I Nr. 278)", "url": GII + "svbezgrv_2026/"},
    "sgb5": {"title": "SGB V § 241 (allgemeiner Beitragssatz 14,6 %), § 242 (Zusatzbeitrag), § 249 (Tragung)", "url": GII + "sgb_5/__241.html"},
    "bsv-2018": {"title": "Beitragssatzverordnung 2018 § 1: allgemeine Rentenversicherung 18,6 % (no later Beitragssatzverordnung)", "url": GII + "bsv_2018/__1.html"},
    "sgb3": {"title": "SGB III § 341 (Arbeitslosenversicherung 2,6 %), §§ 358, 360 (Insolvenzgeldumlage 0,15 % of the RV wage)", "url": GII + "sgb_3/__341.html"},
    "sgb11": {"title": "SGB XI § 55 (Beitragssatz, Kinderlosenzuschlag 0,6, Abschläge 0,25), § 58 (Tragung, Sachsen); PBAV 2025 § 1 (3,6 %)", "url": GII + "pbav_2025/__1.html"},
    "aag": {"title": "Aufwendungsausgleichsgesetz §§ 1, 7 (U1 for employers with up to 30 employees, U1/U2 on the RV wage)", "url": GII + "aufag/__7.html"},
    "bvv": {"title": "Beitragsverfahrensverordnung §§ 1, 2 (calendar month, rounding, halves)", "url": GII + "beitrvv/__2.html"},
    "sgb4": {"title": "SGB IV § 20 Abs. 2 (Übergangsbereich up to 2 000 EUR)", "url": GII + "sgb_4/__20.html"},
    "sgb6": {"title": "SGB VI § 5 Abs. 4, § 172 (pensioners, Regelaltersgrenze: employer-only share), § 235 (Regelaltersgrenze)", "url": GII + "sgb_6/__235.html"},
    "ekd-kist-2026": {"title": "EKD-Statistik Kirchensteuer: Kirchensteuerhebesatz 9 %, in Baden-Württemberg und Bayern 8 % (2026)", "url": "https://www.ekd.de/statistik-kirchensteuer-44297.htm"},
    "kist-rounding": {"title": "Kirchensteuerbeschluss der Nordkirche § 7 Abs. 2 and the Bavarian KirchStG: fractions of a cent are disregarded", "url": "https://www.kirchenrecht-nordkirche.de/document/28498"},
}
for s in SOURCES.values():
    s["retrieved"] = RETRIEVED

KV_HALF, RV_HALF, AV_HALF = D("0.073"), D("0.093"), D("0.013")
PV_HALF, PV_SN_EMPLOYEE, PV_SN_EMPLOYER = D("0.018"), D("0.023"), D("0.013")
PV_CHILDLESS, PV_CHILD = D("0.006"), D("0.0025")
U3 = D("0.0015")
KV_BBG, RV_BBG = D("5812.50"), D("8450")
CHURCH, CHURCH_BY_BW = D("0.09"), D("0.08")
STATES = ["BW", "BY", "BE", "BB", "HB", "HH", "HE", "MV", "NI", "NW", "RP", "SL", "SN", "ST", "SH", "TH"]


def law_text(folder, name):
    s = open(os.path.join(folder, name), encoding="utf-8", errors="replace").read()
    return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", s)))


def check_law(folder):
    needles = {
        "sgb_5___241.html": ["Der allgemeine Beitragssatz beträgt 14,6 Prozent"],
        "sgb_3___341.html": ["Der Beitragssatz beträgt 2,6 Prozent"],
        "sgb_3___360.html": ["Der Umlagesatz beträgt 0,15 Prozent"],
        "sgb_3___358.html": ["Arbeitsentgelt, nach dem die Beiträge zur gesetzlichen Rentenversicherung"],
        "pbav_2025_1.html": ["auf 3,6 Prozent der beitragspflichtigen Einnahmen"],
        "sgb_11___55.html": ["um einen Beitragszuschlag in Höhe von 0,6 Beitragssatzpunkten", "0,25 Beitragssatzpunkten", "vor dem 1. Januar 1940 geboren"],
        "sgb_11___58.html": ["die Beiträge in Höhe von 1 vom Hundert allein"],
        "bsv2018.html": ["in der allgemeinen Rentenversicherung 18,6 Prozent"],
        "rg_2.html": ["auf 69 750 Euro festgesetzt. Umgerechnet auf den Monat ergeben sich 5 812,50 Euro"],
        "rg_svbezgrv_2026___4.html": ["auf 101 400 Euro jährlich; umgerechnet auf den Monat ergeben sich 8 450 Euro"],
        "beitrvv_1.html": ["ein voller Kalendermonat wird mit 30 Sozialversicherungstagen angesetzt",
                           "die zweite Dezimalstelle wird um 1 erhöht, wenn sich in der dritten Dezimalstelle eine der Zahlen 5 bis 9 ergibt"],
        "beitrvv_2.html": ["durch Anwendung des halben Beitragssatzes auf das Arbeitsentgelt und anschließender Verdoppelung des gerundeten Ergebnisses",
                           "ergibt sich der Beitrag aus der Summe der getrennt berechneten gerundeten Anteile"],
        "sgb_4___20.html": ["die regelmäßig 2 000 Euro im Monat nicht übersteigen"],
        "aufag_1.html": ["nicht mehr als 30 Arbeitnehmer"],
        "aufag_7.html": ["nach dem die Beiträge zur gesetzlichen Rentenversicherung"],
        "estg___51a.html": ["beim Steuerabzug vom Arbeitslohn Bemessungsgrundlage die Lohnsteuer"],
        "sgb_6___235.html": ["1959 14 66 2 1960 16 66 4 1961 18 66 6"],
        "sgb_3___28.html": ["mit Ablauf des Monats, in dem sie das maßgebliche Lebensjahr vollenden"],
        "sgb_6___172.html": ["tragen die Arbeitgeber die Hälfte des Beitrags"],
        "sgb_5___243.html": ["Der ermäßigte Beitragssatz beträgt 14,0 Prozent"],
    }
    for name, texts in needles.items():
        t = law_text(folder, name)
        for needle in texts:
            assert needle in t, (name, needle)


# ------------------------------------------------------------------ independent contribution calculation (BVV)

def r2(x):
    return x.quantize(D("0.01"), rounding=ROUND_HALF_UP)


def completed(birth, years):
    """§ 187 Abs. 2 / § 188 BGB: the year of life is completed at the end of the day before the birthday."""
    y, m, d = (int(v) for v in birth.split("-"))
    from datetime import date, timedelta
    try:
        anniversary = date(y + years, m, d)
    except ValueError:  # 29 February
        anniversary = date(y + years, 3, 1)
    return anniversary - timedelta(days=1)


def expected_lines(c):
    from datetime import date
    emp, er = c["employee"], c["employer"]
    start = date.fromisoformat(c["periodStart"])
    wage = D(c["grossEarnings"])
    sv = D(c.get("socialEarnings", c["grossEarnings"]))
    stkl = int(emp["taxClass"])
    kvz = D(emp["healthFundAdditionalRate"])
    pvs = 1 if er["state"] == "SN" else 0
    parent = emp.get("pvParent") == "true"
    c23 = completed(c["birthDate"], 23)
    surcharge_from = date(c23.year + (c23.month // 12), c23.month % 12 + 1, 1)
    pvz = 1 if not parent and int(c["birthDate"][:4]) >= 1940 and start >= surcharge_from else 0
    pva = min(max(int(emp.get("pvChildrenUnder25", "0")) - 1, 0), 4) if parent else 0
    c64 = completed(c["birthDate"], 64)
    alter1 = 1 if c64 < date(start.year, 1, 1) else 0
    factor = D(emp["factor"]) if emp.get("factor") else None
    lst, solz, bk = ls.monthly(stkl, wage, kvz, pvs, pvz, pva, D(emp.get("childAllowances", "0")), D(emp.get("taxAllowance", "0")),
                               D(emp.get("taxAddition", "0")), alter1, c64.year + 1, factor)
    lines = []
    if lst:
        lines.append({"step": "lohnsteuer", "side": "TAX", "amount": str(lst)})
    if solz:
        lines.append({"step": "solidaritaetszuschlag", "side": "TAX", "amount": str(solz)})
    if emp.get("churchMember") == "true":
        kist = (bk * (CHURCH_BY_BW if er["state"] in ("BY", "BW") else CHURCH)).quantize(D("0.01"), rounding=ROUND_FLOOR)
        if kist:
            lines.append({"step": "kirchensteuer", "side": "TAX", "amount": str(kist)})
    kv_base, rv_base = min(sv, KV_BBG), min(sv, RV_BBG)
    kv = r2(kv_base * (KV_HALF + kvz / 200))
    lines += [{"step": "krankenversicherung", "side": s, "amount": str(kv)} for s in ("EMPLOYEE", "EMPLOYER")]
    rv = r2(rv_base * RV_HALF)
    lines += [{"step": "rentenversicherung", "side": s, "amount": str(rv)} for s in ("EMPLOYEE", "EMPLOYER")]
    av = r2(rv_base * AV_HALF)
    lines += [{"step": "arbeitslosenversicherung", "side": s, "amount": str(av)} for s in ("EMPLOYEE", "EMPLOYER")]
    pv_ee = r2(kv_base * ((PV_SN_EMPLOYEE if pvs else PV_HALF) + (PV_CHILDLESS if pvz else 0) - PV_CHILD * pva))
    pv_er = r2(kv_base * (PV_SN_EMPLOYER if pvs else PV_HALF))
    lines += [{"step": "pflegeversicherung", "side": "EMPLOYEE", "amount": str(pv_ee)}, {"step": "pflegeversicherung", "side": "EMPLOYER", "amount": str(pv_er)}]
    if er.get("u1Participation") == "true":
        u1 = r2(rv_base * D(emp["u1Rate"]) / 100)
        if u1:
            lines.append({"step": "umlageU1", "side": "EMPLOYER", "amount": str(u1)})
    u2 = r2(rv_base * D(emp["u2Rate"]) / 100)
    if u2:
        lines.append({"step": "umlageU2", "side": "EMPLOYER", "amount": str(u2)})
    if er.get("insolvencyLevyExempt") != "true":
        lines.append({"step": "umlageU3", "side": "EMPLOYER", "amount": str(r2(rv_base * U3))})
    return lines


# ------------------------------------------------------------------ test cases

MONTH = ("2026-06-01", "2026-06-30", "2026-06-30")
EMPLOYER = {"state": "NW", "u1Participation": "false"}


def employee(**kw):
    e = {"taxClass": "1", "healthFundAdditionalRate": "2.90", "employmentType": "REGULAR", "u2Rate": "0.24"}
    e.update({k: str(v) for k, v in kw.items()})
    return e


def case(name, gross, emp, birth="1988-03-15", employer=None, period=MONTH, **extra):
    start, end, paid = period
    c = {"name": name, "evidence": "Lohnsteuer/SolZ/Maßstabsteuer by lohnsteuer_statute.py (EStG text + the PAP's binding roundings); "
         "contributions by the BVV rules in build_template.py",
         "periodStart": start, "periodEnd": end, "paymentDate": paid, "frequency": "MONTHLY", "grossEarnings": str(gross),
         "birthDate": birth, "employer": employer or EMPLOYER, "employee": emp}
    c.update(extra)
    return c


def test_cases():
    cases = [
        case("Steuerklasse I, 4.000 EUR, childless, no church", "4000.00", employee()),
        case("Steuerklasse III, 6.500 EUR, 2 Kinderfreibeträge, church in NRW, parent of 2", "6500.00",
             employee(taxClass=3, childAllowances="2.0", churchMember="true", pvParent="true", pvChildrenUnder25=2, healthFundAdditionalRate="2.50")),
        case("Steuerklasse IV with Faktor 0,912", "3800.00", employee(taxClass=4, factor="0.912", churchMember="true")),
        case("Steuerklasse II, single parent, 1 Kinderfreibetrag", "3100.00",
             employee(taxClass=2, childAllowances="1.0", pvParent="true", pvChildrenUnder25=1)),
        case("Steuerklasse V", "2600.00", employee(taxClass=5, healthFundAdditionalRate="3.10")),
        case("Steuerklasse VI (second job)", "2400.00", employee(taxClass=6, churchMember="true")),
        case("above both contribution ceilings, church in Bavaria (8 %)", "9500.00",
             employee(churchMember="true"), employer={"state": "BY", "u1Participation": "false"}),
        case("Sachsen: employee 2,3 % plus 0,6 % childless, employer 1,3 %", "4500.00", employee(), employer={"state": "SN", "u1Participation": "false"}),
        case("parent of 5 children under 25: four 0,25-point reductions", "5200.00",
             employee(taxClass=3, childAllowances="5.0", pvParent="true", pvChildrenUnder25=5)),
        case("monthly Freibetrag 250 EUR", "3900.00", employee(taxAllowance="250.00", churchMember="true")),
        case("monthly Hinzurechnungsbetrag 400 EUR", "2700.00", employee(taxAddition="400.00")),
        case("born 1 January 1962: completed 64 on 31 December 2025, Altersentlastungsbetrag (AJAHR 2026)", "5000.00",
             employee(pvParent="true", pvChildrenUnder25=0), birth="1962-01-01"),
        case("born 2 January 1962: completes 64 in 2026, no Altersentlastungsbetrag yet", "5000.00",
             employee(pvParent="true", pvChildrenUnder25=0), birth="1962-01-02"),
        case("born 1 June 2003: completed 23 on 31 May, childless surcharge from June", "3200.00", employee(), birth="2003-06-01"),
        case("born 2 June 2003: completes 23 on 1 June, childless surcharge only from July", "3200.00", employee(), birth="2003-06-02"),
        case("U1 participation (employer with up to 30 employees)", "3600.00", employee(u1Rate="1.60"),
             employer={"state": "HE", "u1Participation": "true"}),
        case("public employer without Insolvenzgeldumlage", "4100.00", employee(), employer={"state": "BE", "u1Participation": "false", "insolvencyLevyExempt": "true"}),
        case("December 2026 wages paid on 8 January 2027 use the 2026 rules", "4400.00", employee(churchMember="true"),
             period=("2026-12-01", "2026-12-31", "2027-01-08")),
    ]
    for c in cases:
        c["expectLines"] = expected_lines(c)
    errors = [
        case("a pay period that is not a calendar month is refused", "4000.00", employee(), period=("2026-06-15", "2026-07-14", "2026-07-14"),
             expectError="calendar month"),
        case("joining or leaving during the month is refused", "2000.00", employee(), employedThroughout=False, expectError="whole calendar month"),
        case("sonstige Bezüge (bonus) are refused", "6000.00", employee(), earningsByComponent={"BASIC": "4000.00", "BONUS": "2000.00"},
             expectError="Sonstige Bezüge"),
        case("pre-tax deductions (Entgeltumwandlung) are refused", "4000.00", employee(), preTaxDeductionsByType={"RETIREMENT": "200.00"},
             expectError="Entgeltumwandlung"),
        case("a regular wage of 2.000 EUR or less is refused (Übergangsbereich)", "1900.00", employee(), expectError="Übergangsbereich"),
        case("a Minijob is refused", "500.00", employee(employmentType="MINIJOB"), expectError="Minijob"),
        case("private health insurance is refused", "7000.00", employee(privateHealthInsurance="true"), expectError="private"),
        case("an old-age pensioner is refused", "4000.00", employee(oldAgePensioner="true"), birth="1961-05-10", expectError="Old-age pensioners"),
        case("an employee exempt from pension insurance is refused", "6000.00", employee(pensionInsuranceExempt="true"), expectError="exempt from statutory pension insurance"),
        case("an employee aged 66 is refused (Regelaltersgrenze)", "4000.00", employee(), birth="1960-05-31", expectError="Regelaltersgrenze"),
        case("a Faktor outside Steuerklasse IV is refused", "4000.00", employee(factor="0.900"), expectError="Faktor"),
        case("a Faktor together with a Freibetrag is refused", "4000.00", employee(taxClass=4, factor="0.900", taxAllowance="100.00"), expectError="Faktor"),
        case("a Hinzurechnungsbetrag in Steuerklasse VI is refused", "4000.00", employee(taxClass=6, taxAddition="100.00"), expectError="Hinzurechnungsbetrag"),
        case("U1 participation without the Krankenkasse's U1 rate is refused", "4000.00", employee(),
             employer={"state": "HE", "u1Participation": "true"}, expectError="u1Rate"),
        case("children under 25 without the parent flag are refused", "4000.00", employee(pvChildrenUnder25=2), expectError="pvParent"),
        case("2027 is not covered", "4000.00", employee(), period=("2027-01-01", "2027-01-31", "2027-01-29"), expectError="a newer template version is needed"),
    ]
    return cases + errors


def p(value, source):
    return [{"from": FROM, "to": TO, "value": str(value), "source": source}]


def main():
    check_law(sys.argv[1])
    state_church = " or ".join(f"#employer['state'] == '{s}'" for s in ("BY", "BW"))
    sv = "#socialEarnings - #reductions"
    template = {
        "schemaVersion": 1,
        "id": "de-employment",
        "name": "Deutschland — Lohnsteuer, Solidaritätszuschlag, Kirchensteuer und Gesamtsozialversicherungsbeitrag",
        "version": "2026.1.1",
        "status": "LAW_DERIVED",
        "jurisdiction": {"country": "DE"},
        "validity": {"from": FROM, "to": TO},
        "currency": "EUR",
        "payFrequencies": ["MONTHLY"],
        "taxYearStart": "--01-01",
        "dateBasis": "PERIOD",
        "rounding": {"scale": 2, "mode": "HALF_UP"},
        "changelog": [{"version": "2026.1.1", "date": "2026-10-24",
                       "summary": "Corrects silent errors: U1 participation without the employee's U1 rate, and care-insurance children under 25 without the parent flag, are now refused instead of giving no U1 levy or the childless surcharge without the child reductions."},
                      {"version": "2026.1.0", "date": RETRIEVED,
                       "summary": "2026: Lohnsteuer, Solidaritätszuschlag and Maßstabsteuer by the BMF PAP 2026 (generated from the official XML pseudocode); Kirchensteuer; KV, RV, AV, PV contributions and the U1, U2, U3 levies for regular monthly wages."}],
        "sources": SOURCES,
        "inputs": {
            "employer": [
                {"key": "state", "type": "ENUM", "required": True, "values": STATES,
                 "description": "Bundesland of the Betriebsstätte: Kirchensteuer rate (8 % in BY and BW, 9 % elsewhere) and care-insurance split in Sachsen (taken as the place of work)"},
                {"key": "u1Participation", "type": "BOOLEAN", "required": True,
                 "description": "The employer takes part in the U1 procedure (as a rule up to 30 employees, § 1 AAG)"},
                {"key": "insolvencyLevyExempt", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "Public employer not liable for the Insolvenzgeldumlage (§ 358 Abs. 1 SGB III)"},
            ],
            "employee": [
                {"key": "taxClass", "type": "ENUM", "required": True, "values": ["1", "2", "3", "4", "5", "6"], "description": "Steuerklasse (ELStAM)"},
                {"key": "childAllowances", "type": "DECIMAL", "required": False, "min": 0, "max": 20, "defaultValue": "0",
                 "description": "Zahl der Kinderfreibeträge (ELStAM, e.g. 0.5, 1.0, 1.5)"},
                {"key": "churchMember", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "Kirchensteuerpflichtig (a church-tax religion in ELStAM)"},
                {"key": "taxAllowance", "type": "DECIMAL", "required": False, "min": 0, "defaultValue": "0",
                 "description": "Monatlicher Freibetrag (ELStAM) in EUR"},
                {"key": "taxAddition", "type": "DECIMAL", "required": False, "min": 0, "defaultValue": "0",
                 "description": "Monatlicher Hinzurechnungsbetrag (ELStAM) in EUR"},
                {"key": "factor", "type": "DECIMAL", "required": False, "min": 0.001, "max": 0.999,
                 "description": "Faktor (§ 39f EStG, Steuerklasse IV only, three decimals)"},
                {"key": "healthFundAdditionalRate", "type": "DECIMAL", "required": True, "min": 0, "max": 10,
                 "description": "Kassenindividueller Zusatzbeitragssatz of the employee's Krankenkasse in percent (e.g. 2.90)"},
                {"key": "pvParent", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "Elterneigenschaft nachgewiesen (§ 55 Abs. 3a SGB XI); without proof the childless surcharge applies from age 23"},
                {"key": "pvChildrenUnder25", "type": "INTEGER", "required": False, "min": 0, "max": 20, "defaultValue": "0",
                 "description": "Nachgewiesene Kinder unter 25 Jahren (reductions from the 2nd to the 5th child)"},
                {"key": "employmentType", "type": "ENUM", "required": True, "values": ["REGULAR", "MIDIJOB", "MINIJOB"],
                 "description": "REGULAR employment (regular wage above 2 000 EUR); Midijobs (Übergangsbereich) and Minijobs are not supported"},
                {"key": "privateHealthInsurance", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "Privately health-insured (not supported)"},
                {"key": "oldAgePensioner", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "Draws an old-age pension (Altersrente) or a Versorgung (other contribution groups and KV rate — not supported)"},
                {"key": "pensionInsuranceExempt", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "Exempt from statutory pension insurance, e.g. member of a berufsständische Versorgungseinrichtung (not supported)"},
                {"key": "u1Rate", "type": "DECIMAL", "required": False, "min": 0, "max": 10, "defaultValue": "0",
                 "description": "U1 Umlagesatz of the employee's Krankenkasse in percent (when the employer takes part in U1)"},
                {"key": "u2Rate", "type": "DECIMAL", "required": True, "min": 0, "max": 10,
                 "description": "U2 Umlagesatz of the employee's Krankenkasse in percent"},
            ],
        },
        "parameters": {
            "kvBbgMonth": p(KV_BBG, "svbezgrv-2026"), "rvBbgMonth": p(RV_BBG, "svbezgrv-2026"),
            "kvHalf": p(KV_HALF, "sgb5"), "rvHalf": p(RV_HALF, "bsv-2018"), "avHalf": p(AV_HALF, "sgb3"),
            "pvHalf": p(PV_HALF, "sgb11"), "pvSaxonyEmployee": p(PV_SN_EMPLOYEE, "sgb11"), "pvSaxonyEmployer": p(PV_SN_EMPLOYER, "sgb11"),
            "pvChildless": p(PV_CHILDLESS, "sgb11"), "pvChildReduction": p(PV_CHILD, "sgb11"), "insolvencyLevy": p(U3, "sgb3"),
            "churchRate": p(CHURCH, "ekd-kist-2026"), "churchRateByBw": p(CHURCH_BY_BW, "ekd-kist-2026"),
            "transitionZoneUpper": p(2000, "sgb4"),
        },
        "tables": {},
        "steps": [
            {"id": "calendarMonth", "type": "REQUIRE", "condition": "#isCalendarMonth",
             "message": "German contributions are computed per calendar month (BVV § 1): the pay period must be a calendar month"},
            {"id": "wholeMonth", "type": "REQUIRE", "condition": "#employedThroughout",
             "message": "Joining or leaving during the month (Teillohnzahlungszeitraum, anteilige Beitragsbemessungsgrenze) is not supported: employ the whole calendar month"},
            {"id": "noBonus", "type": "REQUIRE", "condition": "#earningsByComponent['BONUS'] == 0",
             "message": "Sonstige Bezüge (bonus, Weihnachts-/Urlaubsgeld) need the annual-wage method and one-off-payment contribution rules, which this template does not support"},
            {"id": "noPreTax", "type": "REQUIRE", "condition": "#preTaxDeductions == 0",
             "message": "Pre-tax deductions (Entgeltumwandlung, § 3 Nr. 63 EStG / § 1 SvEV) are not supported"},
            {"id": "regular", "type": "REQUIRE", "condition": "#employee['employmentType'] == 'REGULAR'",
             "message": "Midijob (Übergangsbereich) and Minijob employment are not supported"},
            {"id": "aboveTransitionZone", "type": "REQUIRE", "when": "#reductions == 0",
             "condition": f"{sv} > #p['transitionZoneUpper']",
             "message": "A regular monthly wage of 2 000 EUR or less is in the Übergangsbereich (§ 20 Abs. 2 SGB IV) or below; such employment (and apprentices' low wages) is not supported"},
            {"id": "statutoryHealth", "type": "REQUIRE", "condition": "!#employee['privateHealthInsurance']",
             "message": "Employees with private health insurance are not supported"},
            {"id": "standardGroups", "type": "REQUIRE", "condition": "!#employee['oldAgePensioner'] and !#employee['pensionInsuranceExempt']",
             "message": "Old-age pensioners and employees exempt from statutory pension insurance pay other contribution groups (§ 5 Abs. 4 and § 172 SGB VI, § 243 SGB V), which this template does not support"},
            {"id": "belowStandardRetirementAge", "type": "REQUIRE", "condition": "#ageAtPeriodEnd < 66",
             "message": "From the Regelaltersgrenze (66 years and some months for those born from 1958, § 235 SGB VI) unemployment and pension insurance change to employer-only shares (§ 28, § 346 Abs. 3 SGB III, § 172 SGB VI); employees aged 66 or over are not supported"},
            {"id": "factorClassIV", "type": "REQUIRE", "when": "#employee['factor'] != null",
             "condition": "#employee['taxClass'] == '4' and #employee['taxAllowance'] == 0",
             "message": "A Faktor (§ 39f EStG) applies only in Steuerklasse IV and not together with a Freibetrag"},
            {"id": "noAdditionClassVI", "type": "REQUIRE", "when": "#employee['taxAddition'] > 0", "condition": "#employee['taxClass'] != '6'",
             "message": "A Hinzurechnungsbetrag is not possible in Steuerklasse VI"},
            {"id": "u1RateSet", "type": "REQUIRE", "source": "aag", "when": "#employer['u1Participation']", "condition": "#employee['u1Rate'] > 0",
             "message": "The employer takes part in U1 (§ 1 AAG): set the employee's u1Rate, the U1 Umlagesatz of their Krankenkasse"},
            {"id": "pvChildrenNeedParent", "type": "REQUIRE", "source": "sgb11", "when": "#employee['pvChildrenUnder25'] > 0", "condition": "#employee['pvParent']",
             "message": "Children under 25 for the care-insurance reductions (§ 55 Abs. 3 SGB XI) also prove the Elterneigenschaft: set pvParent, otherwise the childless surcharge would apply"},
            # Age-dependent markers; a year of life is completed at the end of the day before the birthday (§§ 187, 188 BGB).
            {"id": "alter1", "type": "VALUE", "expr": "#birthDate.plusYears(64).minusDays(1) < #taxYearStartDate ? 1 : 0"},
            {"id": "ajahr", "type": "VALUE", "expr": "#birthDate.plusYears(64).minusDays(1).getYear() + 1"},
            {"id": "pvChildless", "type": "VALUE",
             "expr": "!#employee['pvParent'] and #birthYear >= 1940 and #periodStart >= #birthDate.plusYears(23).minusDays(1).withDayOfMonth(1).plusMonths(1) ? 1 : 0"},
            {"id": "pvReductions", "type": "VALUE",
             "expr": "#employee['pvParent'] ? #min(#pos(#employee['pvChildrenUnder25'] - 1), 4) : 0"},
            {"id": "saxony", "type": "VALUE", "expr": "#employer['state'] == 'SN' ? 1 : 0"},
            {"id": "pap", "type": "EXTENSION", "extension": "de.lohnsteuer-pap-2026", "source": "bmf-pap-2026",
             "inputs": {
                 "stkl": "#employee['taxClass']", "lzz": "2", "wage": "#pos(#taxableEarnings - #reductions)",
                 "allowance": "#employee['taxAllowance']", "addition": "#employee['taxAddition']",
                 "childAllowances": "#employee['childAllowances']", "church": "#employee['churchMember'] ? 1 : 0",
                 "kvz": "#employee['healthFundAdditionalRate']", "pvs": "#saxony", "pvz": "#pvChildless", "pva": "#pvReductions",
                 "alter1": "#alter1", "ajahr": "#ajahr",
                 "factorProcedure": "#employee['factor'] != null ? 1 : 0", "factor": "#coalesce(#employee['factor'], 1)",
             }},
            {"id": "lohnsteuer", "type": "TAX", "label": "Lohnsteuer", "ledger": "INCOME_TAX", "source": "bmf-pap-2026",
             "base": "#pos(#taxableEarnings - #reductions)", "amount": "#pap['lst']"},
            {"id": "solidaritaetszuschlag", "type": "TAX", "label": "Solidaritätszuschlag", "ledger": "OTHER", "source": "bmf-pap-2026",
             "amount": "#pap['solz']"},
            {"id": "kirchensteuer", "type": "TAX", "label": "Kirchensteuer", "ledger": "OTHER", "source": "ekd-kist-2026",
             "when": "#employee['churchMember']", "base": "#pap['bk']",
             "amount": f"#round(#pap['bk'] * ({state_church} ? #p['churchRateByBw'] : #p['churchRate']), 2, 'FLOOR')"},
            # Gesamtsozialversicherungsbeitrag (BVV §§ 1, 2): equal halves are one rounded half each; unequal shares rounded separately.
            {"id": "kvBase", "type": "VALUE", "expr": f"#min({sv}, #p['kvBbgMonth'])"},
            {"id": "rvBase", "type": "VALUE", "expr": f"#min({sv}, #p['rvBbgMonth'])"},
            {"id": "kvRateHalf", "type": "VALUE", "expr": "#p['kvHalf'] + #divide(#employee['healthFundAdditionalRate'], 200, 8, 'HALF_UP')"},
            {"id": "krankenversicherung", "type": "CONTRIBUTION", "label": "Krankenversicherung", "ledger": "MEDICARE", "source": "sgb5",
             "base": "#kvBase", "employee": {"rate": "#kvRateHalf"}, "employer": {"rate": "#kvRateHalf"}},
            {"id": "rentenversicherung", "type": "CONTRIBUTION", "label": "Rentenversicherung", "ledger": "PENSION_CONTRIBUTION", "source": "bsv-2018",
             "base": "#rvBase", "employee": {"rate": "#p['rvHalf']"}, "employer": {"rate": "#p['rvHalf']"}},
            {"id": "arbeitslosenversicherung", "type": "CONTRIBUTION", "label": "Arbeitslosenversicherung", "ledger": "UNEMPLOYMENT", "source": "sgb3",
             "base": "#rvBase", "employee": {"rate": "#p['avHalf']"}, "employer": {"rate": "#p['avHalf']"}},
            {"id": "pflegeversicherung", "type": "CONTRIBUTION", "label": "Pflegeversicherung", "ledger": "OTHER", "source": "sgb11",
             "base": "#kvBase",
             "employee": {"rate": "(#saxony == 1 ? #p['pvSaxonyEmployee'] : #p['pvHalf']) + (#pvChildless == 1 ? #p['pvChildless'] : 0) - #pvReductions * #p['pvChildReduction']"},
             "employer": {"rate": "#saxony == 1 ? #p['pvSaxonyEmployer'] : #p['pvHalf']"}},
            {"id": "umlageU1", "type": "CONTRIBUTION", "label": "Umlage U1", "ledger": "OTHER", "source": "aag",
             "when": "#employer['u1Participation']", "base": "#rvBase",
             "employer": {"rate": "#divide(#employee['u1Rate'], 100, 8, 'HALF_UP')"}},
            {"id": "umlageU2", "type": "CONTRIBUTION", "label": "Umlage U2", "ledger": "OTHER", "source": "aag", "base": "#rvBase",
             "employer": {"rate": "#divide(#employee['u2Rate'], 100, 8, 'HALF_UP')"}},
            {"id": "umlageU3", "type": "CONTRIBUTION", "label": "Insolvenzgeldumlage", "ledger": "OTHER", "source": "sgb3",
             "when": "!#employer['insolvencyLevyExempt']", "base": "#rvBase", "employer": {"rate": "#p['insolvencyLevy']"}},
        ],
        "limitations": [
            "Regular monthly wages (laufender Arbeitslohn) for whole calendar months; joining or leaving mid-month, sonstige Bezüge (bonus, Weihnachts-/Urlaubsgeld) and Entgeltumwandlung are refused.",
            "Beitragsgruppe 1111 (KV general rate, RV, AV, PV all statutory); Midijobs (Übergangsbereich), Minijobs, private health insurance, old-age pensioners, employees exempt from pension insurance, employees aged 66 or over (Regelaltersgrenze), Werkstudenten, apprentices below 2 000 EUR and other Personengruppen are refused or out of scope.",
            "Voluntary KV members above the Jahresarbeitsentgeltgrenze are computed as in the Firmenzahlerverfahren (contribution withheld, employer share paid); Selbstzahler with a § 257 SGB V subsidy are not supported.",
            "Kirchensteuer: 8 % in BY and BW, 9 % elsewhere, by the Betriebsstätte's Land, cents truncated; a Mindestkirchensteuer (where a church still levies one) and the Kappung are not applied.",
            "Care insurance in Sachsen follows the employer's Land, taken as the place of work.",
            "U1/U2 rates are the employee's Krankenkasse's; U1 applies when the employer takes part (§ 1 AAG).",
            "Status LAW_DERIVED: the Lohnsteuer code is generated from the BMF's XML pseudocode and reproduced 180,000 amounts of an implementation written from the EStG text (all Steuerklassen, Kinderfreibeträge, church base, Sachsen, PV surcharge/reductions, Altersentlastungsbetrag, Frei-/Hinzurechnungsbeträge, Faktor); the BMF's own calculator interface has not been used.",
        ],
        "testCases": test_cases(),
    }
    out = sys.argv[2]
    with open(out, "w", encoding="utf-8") as f:
        json.dump(template, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("wrote", out, "with", len(template["testCases"]), "test cases")


if __name__ == "__main__":
    main()
