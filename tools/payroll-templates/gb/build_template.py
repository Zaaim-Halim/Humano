"""Build src/main/resources/payroll-templates/gb/gb-paye-nic.json — UK PAYE income tax and Class 1 NICs,
tax year 2026/27 (6 April 2026 – 5 April 2027), weekly, two-weekly and monthly pay.

Sources: HMRC Specification for PAYE Tax Table Routines v24.0 (Appendices A–C), HMRC National Insurance
guidance for software developers 2026 to 2027 (earnings limits 9.2, rates 9.3, Appendix 1, rounding 4),
"Rates and thresholds for employers 2026 to 2027", Taxable Pay Tables B–D 2026/27 (worked examples)
and CWG2 2026/27 (regulatory limit applies to all codes).

PAYE runs in the `uk.paye-routines-v24` extension; NICs are declared in the template. The test cases'
expected amounts come from HMRC's worked examples or from expected_lines() — an implementation of both
specifications written independently of the Java engine.

Usage: python3 -I build_template.py ../../../src/main/resources/payroll-templates/gb/gb-paye-nic.json
"""
import json
import sys
from decimal import ROUND_CEILING, ROUND_DOWN, ROUND_FLOOR, ROUND_HALF_DOWN, Decimal as D

RETRIEVED = "2026-10-20"
FROM, TO = "2026-04-06", "2027-04-05"

SOURCES = {
    "hmrc-paye-spec-v24": {
        "title": "HMRC Specification for PAYE Tax Table Routines, version 24.0 (February 2026) — paras 3–16, Appendices A (England & NI), B (Scotland), C (Wales) for 2026/27",
        "url": "https://www.gov.uk/government/publications/payroll-technical-specifications-income-tax",
    },
    "hmrc-ni-guidance-2026": {
        "title": "HMRC National Insurance guidance for software developers 2026 to 2027 v1.1 — rounding (4), exact percentage method (5), earnings limits (9.2), rates (9.3), Appendix 1 (rates by category letter), worked examples",
        "url": "https://www.gov.uk/government/publications/payroll-technical-specifications-national-insurance",
    },
    "hmrc-rates-2026": {
        "title": "HMRC Rates and thresholds for employers 2026 to 2027",
        "url": "https://www.gov.uk/guidance/rates-and-thresholds-for-employers-2026-to-2027",
    },
    "hmrc-tables-bd-2026": {
        "title": "HMRC Taxable Pay Tables Manual Method, use from 6 April 2026 (Tables B to D) — worked examples 1–13",
        "url": "https://assets.publishing.service.gov.uk/media/69a05a803e672177d0bc773f/Tax_Tables_B-D_2026_to_2027.pdf",
    },
    "hmrc-cwg2-2026": {
        "title": "HMRC CWG2 Employer further guide to PAYE and NICs 2026 to 2027 — the regulatory limit applies to all tax codes",
        "url": "https://www.gov.uk/government/publications/cwg2-further-guide-to-paye-and-national-insurance-contributions/2026-to-2027-employer-further-guide-to-paye-and-national-insurance-contributions",
    },
}
for s in SOURCES.values():
    s["retrieved"] = RETRIEVED

# Spec v24.0 Appendix A (England & NI), C (Wales: same bandwidths, Welsh rates), B (Scotland).
RUK_RATES = ["0.10", "0.20", "0.40", "0.45"]
RUK_BANDS = ["0", "37700", "125140"]
WELSH_RATES = ["0.10", "0.20", "0.40", "0.45"]
SCOT_RATES = ["0.19", "0.20", "0.21", "0.42", "0.45", "0.48"]
SCOT_BANDS = ["3967", "16956", "31092", "62430", "125140"]
MAXRATE = "0.50"

# NI guidance 9.2: per pay period (ST, LEL, PT, FUST/IZUST, UEL = UST/AUST/VUST).
NI_LIMITS = {
    "WEEKLY": ["96", "129", "242", "481", "967"],
    "BIWEEKLY": ["193", "258", "484", "962", "1934"],
    "MONTHLY": ["417", "559", "1048", "2083", "4189"],
}
# Appendix 1: employee band D/E/F rates by letter; employer band D (= B, C), E, F rates by letter.
EMPLOYEE = {}
for letters, rates in [("AMHFVN", ["0.08", "0.08", "0.02"]), ("BIE", ["0.0185", "0.0185", "0.02"]),
                       ("CSK", ["0", "0", "0"]), ("JLZD", ["0.02", "0.02", "0.02"])]:
    for letter in letters:
        EMPLOYEE[letter] = rates
EMPLOYER = {}
for letters, rates in [("ABCJ", ["0.15", "0.15", "0.15"]), ("HMVZ", ["0", "0", "0.15"]),
                       ("FISL", ["0", "0.15", "0.15"]), ("DEKN", ["0", "0.15", "0.15"])]:
    for letter in letters:
        EMPLOYER[letter] = rates
assert sorted(EMPLOYEE) == sorted(EMPLOYER) and len(EMPLOYEE) == 16

# ------------------------------------------------------------------ independent implementation

def trunc4(x):
    return x.quantize(D("0.0001"), rounding=ROUND_DOWN)


def floor_penny(x):
    return x.quantize(D("0.01"), rounding=ROUND_FLOOR)


def ni_round(x):
    """NI guidance 4.2: look only at the third decimal place — 5 or less down, 6 or more up."""
    return x.quantize(D("0.001"), rounding=ROUND_DOWN).quantize(D("0.01"), rounding=ROUND_HALF_DOWN)


def week1(number, per_year):
    if number == 0:
        return D(0)
    q, r = divmod(number - 1, 500)
    r += 1
    value = trunc4(D(r * 10 + 9) / D(per_year)).quantize(D("0.01"), rounding=ROUND_CEILING)
    return value + q * (D("96.16") if per_year == 52 else D("416.67"))


def paye(code, n_period, per_year, interval, pay, pay_to_date, tax_to_date):
    raw = code.upper().replace(" ", "")
    cumulative = True
    for suffix in ("W1", "M1", "X"):
        if raw.endswith(suffix) and len(raw) > len(suffix):
            raw, cumulative = raw[: -len(suffix)], False
            break
    country = "E"
    if raw != "NT" and raw[0] in "SC":
        country, raw = raw[0], raw[1:]
    rates = [D(r) for r in {"E": RUK_RATES, "C": WELSH_RATES, "S": SCOT_RATES}[country]]
    bands = [D(b) for b in (SCOT_BANDS if country == "S" else RUK_BANDS)]
    n = n_period if cumulative else interval
    total = pay_to_date + pay if cumulative else pay
    if raw == "NT":
        liability = D(0)
    elif raw == "BR":
        liability = floor_penny(trunc4(total.quantize(D(1), rounding=ROUND_FLOOR) * rates[1]))
    elif raw.startswith("D"):
        liability = floor_penny(trunc4(total.quantize(D(1), rounding=ROUND_FLOOR) * rates[2 + int(raw[1:])]))
    else:
        k = raw.startswith("K")
        number = int(raw[1:] if k else raw[:-1])
        adjustment = week1(number, per_year) * n
        un = total + adjustment if k else total - adjustment
        if not k and un <= 0:
            liability = D(0)
        else:
            tn = un.quantize(D(1), rounding=ROUND_FLOOR)
            annual_tax, previous, acc = [], D(0), D(0)
            for i, b in enumerate(bands):
                acc += (b - previous) * rates[i]
                annual_tax.append(acc)
                previous = b
            formula = len(bands) + 1
            for i, b in enumerate(bands):
                if un <= trunc4(b * n / per_year).quantize(D(1), rounding=ROUND_CEILING):
                    formula = i + 1
                    break
            c_prev = trunc4(bands[formula - 2] * n / per_year) if formula > 1 else D(0)
            k_prev = trunc4(annual_tax[formula - 2] * n / per_year) if formula > 1 else D(0)
            liability = floor_penny(trunc4(k_prev + (tn - c_prev) * rates[formula - 1]))
    tax = liability - tax_to_date if cumulative else liability
    if tax > 0:
        limit = floor_penny(trunc4(D(MAXRATE) * pay)) if pay > 0 else D(0)
        tax = min(tax, limit)
    elif not cumulative and tax < 0:
        tax = D(0)
    return tax


def nics(category, frequency, gross):
    st, lel, pt, fust, uel = (D(v) for v in NI_LIMITS[frequency])
    if gross <= st:
        return D(0), D(0)
    s2 = min(gross, lel) - st
    s3 = max(D(0), min(gross, pt) - lel)
    s4 = max(D(0), min(gross, fust) - pt)
    s5 = max(D(0), min(gross, uel) - fust)
    s6 = max(D(0), gross - uel)
    ee_d, ee_e, ee_f = (D(r) for r in EMPLOYEE[category])
    er_d, er_e, er_f = (D(r) for r in EMPLOYER[category])
    employee = ni_round((s4 + s5) * ee_e) + ni_round(s6 * ee_f)
    employer = ni_round((s2 + s3 + s4) * er_d + s5 * er_e) + ni_round(s6 * er_f)
    return employee, employer


def expected_lines(c):
    emp = c["employee"]
    freq = c["frequency"]
    per_year = 12 if freq == "MONTHLY" else 52
    interval = 2 if freq == "BIWEEKLY" else 1
    pay = D(c["grossEarnings"])
    bf = emp.get("broughtForwardTaxYear") == "2026"
    pay_to_date = D(emp["broughtForwardPay"]) if bf else D(0)
    code = emp["taxCode"]
    raw = code.upper().replace(" ", "")
    if freq != "MONTHLY" and c["_taxPeriod"] > 52 and not raw.endswith(("W1", "M1", "X")):
        # CWG2 1.13 / spec para 14: week 53 (54 two-weekly) on a cumulative code is worked out with the week 1
        # (week 2) table on a non-cumulative basis; for a suffix code no PAYE is due when the free pay to date
        # at week 52 is at least the year's total pay (CWG2 1.13.1).
        bare = raw[1:] if raw[0] in "SC" and raw != "NT" else raw
        if bare[-1] in "LMNT" and bare[:-1].isdigit() and week1(int(bare[:-1]), 52) * 52 >= pay_to_date + pay:
            tax = D("0.00")
        else:
            tax = paye(code + " W1", c["_taxPeriod"], per_year, interval, pay, D(0), D(0))
    else:
        tax = paye(code, c["_taxPeriod"], per_year, interval, pay,
                   pay_to_date, D(emp["broughtForwardTax"]) if bf else D(0))
    lines = [{"step": "incomeTax", "side": "TAX", "amount": str(tax)}]
    employee, employer = nics(emp["niCategory"], freq, pay)
    if employee:
        lines.append({"step": "nationalInsurance", "side": "EMPLOYEE", "amount": str(employee)})
    if employer:
        lines.append({"step": "nationalInsurance", "side": "EMPLOYER", "amount": str(employer)})
    return lines


# ------------------------------------------------------------------ test cases

def case(name, evidence, freq, start, end, paid, period, gross, employee, born, expect=None, error=None):
    c = {"name": name, "evidence": evidence, "periodStart": start, "periodEnd": end, "paymentDate": paid,
         "frequency": freq, "grossEarnings": gross, "birthDate": born, "employer": {}, "employee": employee, "_taxPeriod": period}
    if error:
        c["expectError"] = error
    elif expect is not None:
        c["expectLines"] = expect
    return c


def bf(pay, tax):
    return {"broughtForwardTaxYear": "2026", "broughtForwardPay": pay, "broughtForwardTax": tax}


UNDER_21 = "2007-03-01"
WEEK11 = ("2026-06-15", "2026-06-21", "2026-06-19", 11)
WEEK18 = ("2026-08-03", "2026-08-09", "2026-08-07", 18)
WEEK1 = ("2026-04-06", "2026-04-12", "2026-04-10", 1)
MONTH4 = ("2026-07-01", "2026-07-31", "2026-07-31", 4)
MONTH7 = ("2026-10-01", "2026-10-31", "2026-10-30", 7)


def test_cases():
    tables = "HMRC Tables B-D 2026/27 "
    ni = "HMRC NI guidance 2026/27 "
    derived = "Derived from the PAYE specification v24.0 and NI guidance 2026/27 by build_template.py expected_lines()"
    cases = [
        # Official PAYE examples; category M (under 21) and pay below the primary threshold keep NICs at nil.
        case("Tables B-D example 1: 431L, month 4", tables + "Example 1: £3,400 - £1,439.68 = £1,960 taxable to date; £392.00 due (Table B), nothing deducted before",
             "MONTHLY", *MONTH4, "925.00", {"taxCode": "431L", "niCategory": "M", **bf("2475.00", "0")}, UNDER_21),
        case("Tables B-D example 2: BR, month 4", tables + "Example 2: £3,200 to date at 20% = £640.00 due; £480.00 deducted in months 1-3",
             "MONTHLY", *MONTH4, "800.00", {"taxCode": "BR", "niCategory": "M", **bf("2400.00", "480.00")}, UNDER_21),
        case("Tables B-D example 3: 431L, week 11", tables + "Example 3: £2,030 - £913.66 = £1,116 taxable; £223.20 due; £200.00 deducted before",
             "WEEKLY", *WEEK11, "203.00", {"taxCode": "431L", "niCategory": "M", **bf("1827.00", "200.00")}, UNDER_21),
        case("Tables B-D example 3 with the regulatory limit", tables + "Example 3 with nothing deducted before: £223.20 due exceeds 50% of £203 (CWG2), so £101.50",
             "WEEKLY", *WEEK11, "203.00", {"taxCode": "431L", "niCategory": "M", **bf("1827.00", "0")}, UNDER_21),
        case("Tables B-D example 4: BR, week 11", tables + "Example 4: £1,500 to date = £300.00 due; £272.00 deducted before",
             "WEEKLY", *WEEK11, "140.00", {"taxCode": "BR", "niCategory": "M", **bf("1360.00", "272.00")}, UNDER_21),
        case("Tables B-D example 7: S431L, week 18", tables + "Example 7: £1,612 - £1,495.08 = £116 at the Scottish starter rate 19% = £22.04",
             "WEEKLY", *WEEK18, "85.00", {"taxCode": "S431L", "niCategory": "M", **bf("1527.00", "0")}, UNDER_21),
        case("Tables B-D example 10: C431L, week 11", tables + "Example 10: Welsh, as example 3",
             "WEEKLY", *WEEK11, "203.00", {"taxCode": "C431L", "niCategory": "M", **bf("1827.00", "200.00")}, UNDER_21),
        case("Tables B-D example 11: CBR, week 11", tables + "Example 11: Welsh, as example 4",
             "WEEKLY", *WEEK11, "140.00", {"taxCode": "CBR", "niCategory": "M", **bf("1360.00", "272.00")}, UNDER_21),
        # Official NIC examples; code NT keeps PAYE at nil (a zero tax line is still recorded).
        case("NI guidance example 1: A, £129.53 a week", ni + "Example 1: employee £0.00, employer £5.03",
             "WEEKLY", *WEEK1, "129.53", {"taxCode": "NT", "niCategory": "A"}, "1998-01-01"),
        case("NI guidance example 2: A, £1,000 a week", ni + "Example 2: employee £58.66, employer £135.60",
             "WEEKLY", *WEEK1, "1000.00", {"taxCode": "NT", "niCategory": "A"}, "1982-01-01"),
        case("NI guidance example 3: B, £508 two-weekly", ni + "Example 3: employee £0.44, employer £47.25",
             "BIWEEKLY", "2026-04-06", "2026-04-19", "2026-04-17", 2, "508.00", {"taxCode": "NT", "niCategory": "B"}, "1975-01-01"),
        case("NI guidance example 4: M, £1,004 a week", ni + "Example 4: employee £58.74, employer £5.55",
             "WEEKLY", *WEEK1, "1004.00", {"taxCode": "NT", "niCategory": "M"}, UNDER_21),
        # Derived cases.
        case("standard monthly employee, month 7", derived, "MONTHLY", *MONTH7, "3000.00",
             {"taxCode": "1257L", "niCategory": "A", **bf("18000.00", "2412.00")}, "1990-05-01"),
        case("higher rate at month 4 (example 5 shape)", derived + "; tax to date £5,606.66 as HMRC example 5", "MONTHLY", *MONTH4, "5300.00",
             {"taxCode": "0T", "niCategory": "A", **bf("15000.00", "3000.00")}, "1980-01-01"),
        case("K code limited to 50% of pay", derived, "MONTHLY", *MONTH4, "1500.00",
             {"taxCode": "K2500", "niCategory": "A", **bf("4500.00", "0")}, "1980-01-01"),
        case("refund on a cumulative code", derived, "MONTHLY", *MONTH7, "1000.00",
             {"taxCode": "1257L", "niCategory": "A", **bf("6000.00", "700.00")}, "1980-01-01"),
        case("emergency code on a Month 1 basis", derived, "MONTHLY", *MONTH7, "3000.00",
             {"taxCode": "1257L M1", "niCategory": "A"}, "1980-01-01"),
        case("Scottish higher rate, monthly", derived, "MONTHLY", *MONTH7, "6000.00",
             {"taxCode": "S1257L", "niCategory": "A", **bf("36000.00", "6500.00")}, "1980-01-01"),
        case("two-weekly cumulative in week 12", derived, "BIWEEKLY", "2026-06-15", "2026-06-28", "2026-06-26", 12, "2000.00",
             {"taxCode": "1257L", "niCategory": "A", **bf("10000.00", "1100.00")}, "1980-01-01"),
        case("freeport employer, category F", derived, "MONTHLY", *MONTH7, "3000.00",
             {"taxCode": "1257L", "niCategory": "F", **bf("18000.00", "2412.00")}, "1980-01-01"),
        case("week 53, cumulative code: week 1 table, non-cumulative", "CWG2 1.13.1: free pay to date at week 52 is less than the year's pay, so PAYE on the week 53 pay less the week 1 free pay; " + derived,
             "WEEKLY", "2027-03-30", "2027-04-05", "2027-04-05", 53, "600.00", {"taxCode": "1257L", "niCategory": "A", **bf("31200.00", "3741.60")}, "1980-01-01"),
        case("week 53, low year's pay: no PAYE", "CWG2 1.13.1: free pay to date at week 52 (£12,579.84 for 1257L) covers the year's pay (£5,000 + £2,000), so no PAYE although the week 1 table alone would tax £2,000; " + derived,
             "WEEKLY", "2027-03-30", "2027-04-05", "2027-04-05", 53, "2000.00", {"taxCode": "1257L", "niCategory": "A", **bf("5000.00", "0")}, "1980-01-01"),
        case("week 53, K code", "CWG2 1.13.2: pay plus the week 1 additional pay, subject to the regulatory limit; " + derived,
             "WEEKLY", "2027-03-30", "2027-04-05", "2027-04-05", 53, "600.00", {"taxCode": "K500", "niCategory": "A", **bf("31200.00", "7000.00")}, "1980-01-01"),
        case("week 53 on a Week 1 code", "CWG2 1.13.1: an employee on a week 1 coding is simply paid another week 1 payment; " + derived,
             "WEEKLY", "2027-03-30", "2027-04-05", "2027-04-05", 53, "600.00", {"taxCode": "1257L W1", "niCategory": "A"}, "1980-01-01"),
        case("two-weekly payday in week 53 ('week 54')", "CWG2 1.13: the 27th fortnightly payday is week 54, worked out with the week 2 table on a non-cumulative basis; " + derived,
             "BIWEEKLY", "2027-03-23", "2027-04-05", "2027-04-05", 53, "2000.00", {"taxCode": "1257L", "niCategory": "A", **bf("52000.00", "8000.00")}, "1980-01-01"),
    ]
    for c in cases:
        c["expectLines"] = expected_lines(c)
    errors = [
        case("mid-year start without brought-forward figures", "cumulative code past period 1 with no earlier period recorded",
             "MONTHLY", *MONTH7, "3000.00", {"taxCode": "1257L", "niCategory": "A"}, "1980-01-01", error="brought forward"),
        case("directors are refused", "annual earnings period not modelled", "MONTHLY", *MONTH7, "3000.00",
             {"taxCode": "1257L", "niCategory": "A", "director": "true", **bf("18000.00", "2412.00")}, "1980-01-01", error="Directors"),
        case("student loans are refused", "student loan deductions not modelled", "MONTHLY", *MONTH7, "3000.00",
             {"taxCode": "1257L", "niCategory": "A", "studentLoan": "PLAN_2", **bf("18000.00", "2412.00")}, "1980-01-01", error="Student"),
        case("category M needs an employee under 21", "category letter checked against age", "MONTHLY", *MONTH7, "3000.00",
             {"taxCode": "1257L", "niCategory": "M", **bf("18000.00", "2412.00")}, "1980-01-01", error="under 21"),
        case("category C under State Pension age is refused", "Categories C, S and K have no employee NICs and are for employees over State Pension age (66 at the earliest in 2026/27)",
             "MONTHLY", *MONTH7, "3000.00", {"taxCode": "1257L", "niCategory": "C", **bf("18000.00", "2412.00")}, "1980-01-01", error="State Pension age"),
        case("tax code that is not a code", "unknown code format", "MONTHLY", *MONTH7, "3000.00",
             {"taxCode": "1257Q", "niCategory": "A", **bf("18000.00", "2412.00")}, "1980-01-01", error="must match"),
        case("2027/28 is not covered", "next tax year not yet published here", "MONTHLY", "2027-04-01", "2027-04-30", "2027-04-30", 1, "3000.00",
             {"taxCode": "1257L", "niCategory": "A"}, "1980-01-01", error="a newer template version is needed"),
    ]
    out = cases + errors
    for c in out:
        del c["_taxPeriod"]
    return out


def p(value, source):
    return [{"from": FROM, "to": TO, "value": value, "source": source}]


def main():
    params = {}
    for i, r in enumerate(RUK_RATES, 1):
        params[f"rukRate{i}"] = p(r, "hmrc-paye-spec-v24")
    for i, b in enumerate(RUK_BANDS, 1):
        params[f"rukBand{i}"] = p(b, "hmrc-paye-spec-v24")
    for i, r in enumerate(WELSH_RATES, 1):
        params[f"welshRate{i}"] = p(r, "hmrc-paye-spec-v24")
    for i, r in enumerate(SCOT_RATES, 1):
        params[f"scotRate{i}"] = p(r, "hmrc-paye-spec-v24")
    for i, b in enumerate(SCOT_BANDS, 1):
        params[f"scotBand{i}"] = p(b, "hmrc-paye-spec-v24")
    params["maxRate"] = p(MAXRATE, "hmrc-paye-spec-v24")

    def plist(prefix, n):
        return "{" + ", ".join(f"#p['{prefix}{i}']" for i in range(1, n + 1)) + "}"

    ni = lambda col: f"#table.lookup('niLimits', #frequency, '{col}')"
    template = {
        "schemaVersion": 1,
        "id": "gb-paye-nic",
        "name": "United Kingdom — PAYE income tax and Class 1 National Insurance",
        "version": "2026.1.1",
        "status": "LAW_DERIVED",
        "jurisdiction": {"country": "GB"},
        "validity": {"from": FROM, "to": TO},
        "currency": "GBP",
        "payFrequencies": ["WEEKLY", "BIWEEKLY", "MONTHLY"],
        "taxYearStart": "--04-06",
        "dateBasis": "PAYMENT_DATE",
        "rounding": {"scale": 2, "mode": "HALF_UP"},
        "changelog": [{"version": "2026.1.1", "date": "2026-10-24",
                       "summary": "Corrects a silent error: NI categories C, S and K (no employee NICs, for employees over State Pension age) are refused for employees under 66. Payments in week 53 (54 for two-weekly pay) are now calculated per CWG2 1.13 instead of being refused."},
                      {"version": "2026.1.0", "date": RETRIEVED,
                       "summary": "2026/27: PAYE per HMRC's Specification for PAYE Tax Table Routines v24.0 (England & NI, Scottish and Welsh rates); Class 1 NICs, all category letters, by the exact percentage method."}],
        "sources": SOURCES,
        "inputs": {
            "employer": [],
            "employee": [
                {"key": "taxCode", "type": "STRING", "required": True,
                 "pattern": "^\\s*[SsCc]?(\\d{1,6}[LMNTlmnt]|[Kk]\\d{1,6}|[Bb][Rr]|[Dd]\\d|[Nn][Tt])(\\s*([WwMm]1|[Xx]))?\\s*$",
                 "description": "PAYE tax code as issued by HMRC (e.g. 1257L, S1257L, C1257L M1, K475, BR, D0, NT)"},
                {"key": "niCategory", "type": "ENUM", "required": True, "values": sorted(EMPLOYEE),
                 "description": "Class 1 National Insurance category letter (CA38 / NI guidance Appendix 1)"},
                {"key": "broughtForwardTaxYear", "type": "INTEGER", "required": False, "min": 2000, "max": 2100,
                 "description": "Tax year (starting year, e.g. 2026) the brought-forward pay and tax belong to; other years' figures are ignored"},
                {"key": "broughtForwardPay", "type": "DECIMAL", "required": False, "min": 0,
                 "description": "Pay to date in that tax year before the first payroll in Humano (P45 from a previous employer plus this employer's earlier payroll)"},
                {"key": "broughtForwardTax", "type": "DECIMAL", "required": False,
                 "description": "Tax deducted to date in that tax year before the first payroll in Humano"},
                {"key": "director", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "Company director (NICs on an annual earnings period — not supported)"},
                {"key": "studentLoan", "type": "ENUM", "required": False, "values": ["NONE", "PLAN_1", "PLAN_2", "PLAN_4", "PLAN_5", "POSTGRADUATE"],
                 "defaultValue": "NONE", "description": "Student or postgraduate loan plan (deductions not supported)"},
            ],
        },
        "parameters": params,
        "tables": {
            "niLimits": {
                "kind": "EXACT",
                "description": "Class 1 earnings limits per pay period: ST, LEL, PT, FUST/IZUST, UEL (= UST/AUST/VUST) — NI guidance 9.2",
                "columns": ["ST", "LEL", "PT", "FUST", "UEL"],
                "versions": [{"from": FROM, "to": TO, "source": "hmrc-ni-guidance-2026",
                              "rows": [{"key": k, "values": v} for k, v in NI_LIMITS.items()]}],
            },
            "niEmployeeRates": {
                "kind": "EXACT",
                "description": "Employee Class 1 rates by category letter for bands D (PT-FUST), E (FUST-UEL), F (above UEL) — NI guidance Appendix 1",
                "columns": ["D", "E", "F"],
                "versions": [{"from": FROM, "to": TO, "source": "hmrc-ni-guidance-2026",
                              "rows": [{"key": k, "values": v} for k, v in sorted(EMPLOYEE.items())]}],
            },
            "niEmployerRates": {
                "kind": "EXACT",
                "description": "Employer Class 1 rates by category letter for bands B-D (ST-FUST), E (FUST-UEL), F (above UEL) — NI guidance Appendix 1",
                "columns": ["D", "E", "F"],
                "versions": [{"from": FROM, "to": TO, "source": "hmrc-ni-guidance-2026",
                              "rows": [{"key": k, "values": v} for k, v in sorted(EMPLOYER.items())]}],
            },
        },
        "steps": [
            {"id": "notDirector", "type": "REQUIRE", "condition": "!#employee['director']",
             "message": "Directors' NICs use an annual earnings period, which this template does not support"},
            {"id": "noStudentLoan", "type": "REQUIRE", "condition": "#employee['studentLoan'] == 'NONE'",
             "message": "Student and postgraduate loan deductions are not supported by this template"},
            {"id": "noPreTaxDeductions", "type": "REQUIRE", "condition": "#preTaxDeductions == 0",
             "message": "Pre-tax deductions (net pay pension schemes, salary sacrifice) are not supported by this template"},
            {"id": "categoryAge", "type": "REQUIRE",
             "when": "#employee['niCategory'] == 'M' or #employee['niCategory'] == 'Z'",
             "condition": "#ageAtPeriodEnd < 21", "message": "NI categories M and Z are for employees under 21"},
            {"id": "categoryApprenticeAge", "type": "REQUIRE", "when": "#employee['niCategory'] == 'H'",
             "condition": "#ageAtPeriodEnd < 25", "message": "NI category H is for apprentices under 25"},
            {"id": "categoryPensionAge", "type": "REQUIRE",
             "when": "#employee['niCategory'] == 'C' or #employee['niCategory'] == 'S' or #employee['niCategory'] == 'K'",
             "condition": "#ageAtPeriodEnd >= 66",
             "message": "NI categories C, S and K are for employees over State Pension age, which nobody under 66 has reached in 2026/27"},
            {"id": "taxPeriod", "type": "VALUE", "expr": "#frequency == 'MONTHLY' ? #taxMonth : #taxWeek"},
            {"id": "intervalPeriods", "type": "VALUE", "expr": "#frequency == 'BIWEEKLY' ? 2 : 1"},
            {"id": "broughtForward", "type": "VALUE",
             "expr": "#employee['broughtForwardTaxYear'] != null and #employee['broughtForwardTaxYear'] == #taxYear"},
            {"id": "payePay", "type": "VALUE", "expr": "#taxableEarnings - #reductions"},
            {"id": "week53", "type": "VALUE", "source": "hmrc-cwg2-2026", "expr": "#frequency != 'MONTHLY' and #taxWeek > 52 ? 1 : 0"},
            {"id": "codeNonCumulative", "type": "VALUE",
             "expr": "#employee['taxCode'].replace(' ', '').toUpperCase().matches('.+(W1|M1|X)') ? 1 : 0"},
            {"id": "payeToDate", "type": "VALUE",
             "expr": "#ytd.base('incomeTax', 'TAX') + (#broughtForward ? #coalesce(#employee['broughtForwardPay'], 0) : 0)"},
            {"id": "paye", "type": "EXTENSION", "extension": "uk.paye-routines-v24", "source": "hmrc-paye-spec-v24",
             "inputs": {
                 "taxCode": "#week53 == 1 and #codeNonCumulative == 0 ? #employee['taxCode'] + ' W1' : #employee['taxCode']",
                 "period": "#taxPeriod",
                 "periodsPerYear": "#frequency == 'MONTHLY' ? 12 : 52",
                 "intervalPeriods": "#intervalPeriods",
                 "pay": "#payePay",
                 "payToDate": "#payeToDate",
                 "taxToDate": "#ytd.amount('incomeTax', 'TAX') + (#broughtForward ? #coalesce(#employee['broughtForwardTax'], 0) : 0)",
                 "rates": plist("rukRate", 4), "bands": plist("rukBand", 3), "basicRateIndex": "2",
                 "scottishRates": plist("scotRate", 6), "scottishBands": plist("scotBand", 5), "scottishBasicRateIndex": "2",
                 "welshRates": plist("welshRate", 4), "welshBands": plist("rukBand", 3), "welshBasicRateIndex": "2",
                 "maxRate": "#p['maxRate']",
             }},
            {"id": "midYearStart", "type": "REQUIRE",
             "when": "(#paye['cumulative'] == 1 or (#week53 == 1 and #codeNonCumulative == 0)) and #taxPeriod > #intervalPeriods and #ytd.periods('incomeTax', 'TAX') == 0",
             "condition": "#broughtForward",
             "message": "No earlier payroll this tax year is recorded for this employee: set the pay and tax brought forward (P45 / previous payroll, 0 if none) and broughtForwardTaxYear before running a cumulative tax code"},
            {"id": "incomeTax", "type": "TAX", "label": "Income tax (PAYE)", "ledger": "INCOME_TAX", "source": "hmrc-paye-spec-v24",
             "base": "#payePay", "recordZero": True, "allowNegative": True,
             "amount": "#week53 == 1 and #codeNonCumulative == 0 and #paye['freePay'] > 0 and #paye['freePay'] * (#intervalPeriods == 2 ? 26 : 52) >= #payeToDate + #payePay ? 0 : #paye['tax']"},
            {"id": "niPay", "type": "VALUE", "expr": "#socialEarnings - #reductions"},
            {"id": "niBand2", "type": "VALUE", "expr": f"#pos(#min(#niPay, {ni('LEL')}) - {ni('ST')})"},
            {"id": "niBand3", "type": "VALUE", "expr": f"#pos(#min(#niPay, {ni('PT')}) - {ni('LEL')})"},
            {"id": "niBand4", "type": "VALUE", "expr": f"#pos(#min(#niPay, {ni('FUST')}) - {ni('PT')})"},
            {"id": "niBand5", "type": "VALUE", "expr": f"#pos(#min(#niPay, {ni('UEL')}) - {ni('FUST')})"},
            {"id": "niBand6", "type": "VALUE", "expr": f"#pos(#niPay - {ni('UEL')})"},
            {"id": "nationalInsurance", "type": "CONTRIBUTION", "label": "National Insurance (Class 1)", "ledger": "SOCIAL_SECURITY",
             "source": "hmrc-ni-guidance-2026", "when": f"#niPay > {ni('ST')}", "base": "#niPay",
             "employee": {"amount":
                          "#round(#round((#niBand4 + #niBand5) * #table.lookup('niEmployeeRates', #employee['niCategory'], 'E'), 3, 'DOWN'), 2, 'HALF_DOWN')"
                          " + #round(#round(#niBand6 * #table.lookup('niEmployeeRates', #employee['niCategory'], 'F'), 3, 'DOWN'), 2, 'HALF_DOWN')"},
             "employer": {"amount":
                          "#round(#round((#niBand2 + #niBand3 + #niBand4) * #table.lookup('niEmployerRates', #employee['niCategory'], 'D')"
                          " + #niBand5 * #table.lookup('niEmployerRates', #employee['niCategory'], 'E'), 3, 'DOWN'), 2, 'HALF_DOWN')"
                          " + #round(#round(#niBand6 * #table.lookup('niEmployerRates', #employee['niCategory'], 'F'), 3, 'DOWN'), 2, 'HALF_DOWN')"}},
        ],
        "limitations": [
            "Weekly, two-weekly and monthly pay. A payday in tax week 53 (week 54 for two-weekly pay) is worked out with the week 1 (week 2) table on a non-cumulative basis, and a suffix code pays no PAYE there when the free pay to date at week 52 covers the year's pay (CWG2 1.13, specification para 14); NICs follow the normal earnings period.",
            "Directors (annual NIC earnings period), student and postgraduate loans, and pre-tax deductions (net pay pensions, salary sacrifice) are refused.",
            "Mid-year starts and adoptions need the pay and tax brought forward for the tax year (P45 and/or previous payroll) on a cumulative code.",
            "Payrolled benefits in kind are not included; the regulatory limit is applied to the whole pay for the period.",
            "Employment Allowance, the Apprenticeship Levy and Small Employers' Relief are employer-level reliefs claimed outside the payslip.",
            "The NI category letter, including employer reliefs (freeport, investment zone, veterans), is the employer's responsibility; M, Z and H are checked against age, and C, S and K are refused under 66. An employee past State Pension age left on a category with employee NICs (e.g. A) is not detected, since State Pension age depends on the exact birth date and the template does not require one.",
            "Status LAW_DERIVED: PAYE amounts of eight cases reproduce HMRC Tables B-D examples and NICs of four cases reproduce HMRC NI guidance examples; the others are derived from the same specifications.",
        ],
        "testCases": test_cases(),
    }
    with open(sys.argv[1], "w", encoding="utf-8") as f:
        json.dump(template, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("wrote", sys.argv[1], "with", len(template["testCases"]), "test cases")


main()
