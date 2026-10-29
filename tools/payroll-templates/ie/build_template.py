"""Build src/main/resources/payroll-templates/ie/ie-paye.json.

Ireland, PAYE employment paid weekly or monthly in EUR, 2026: income tax and USC from the Revenue Payroll
Notification (RPN) figures on the cumulative, week 1/month 1 or emergency basis, and Class A PRSI. Every
rule below cites the act or Revenue guidance it comes from. The test cases' expected amounts are computed
by expected_lines() — an implementation written directly from those texts, independent of the Java
engine; the engine must reproduce them exactly. One case reproduces Revenue's own worked example of the
weekly tax calculation and one its worked example of a cumulative refund.

Usage: python3 -I build_template.py ../../../src/main/resources/payroll-templates/ie/ie-paye.json
"""
import json
import sys
from datetime import date
from decimal import ROUND_CEILING, ROUND_HALF_UP, Decimal

RETRIEVED = "2026-10-23"
FROM, TO = "2026-01-01", "2026-12-31"
Q123, Q4 = ("2026-01-01", "2026-09-30"), ("2026-10-01", "2026-12-31")
ISB = "https://www.irishstatutebook.ie/eli/"

SOURCES = {
    "revenue-guide": {
        "title": "Revenue, Tax and Duty Manual Part 42-04-35A, Employers' Guide to PAYE (last reviewed September 2026) — ch. 9.3 cumulative basis with worked examples (tax cut-off and credits per period rounded up to the cent, e.g. €44,000 → €846.16 a week / €3,666.67 a month; USC cut-offs €1,001.00 / €2,391.67 / €5,837.00 a month), 9.3.2 refunds, 9.4 week 1/month 1 basis, 9.5 emergency basis (no PPSN: higher rate on all pay; PPSN: 1/12 or 1/52 of the single cut-off for the first month or 4 weeks, no credits; USC 8 %); PDF SHA-256 1dc6b00b51f0ebeb0e03e2ac14bf2238e8b96f3212c05163b2307b530e7ce9d2",
        "url": "https://www.revenue.ie/en/tax-professionals/tdm/income-tax-capital-gains-tax-corporation-tax/part-42/42-04-35a.pdf",
    },
    "revenue-rates": {
        "title": "Revenue, Tax rates, bands and reliefs 2026 — standard rate 20 %, higher rate 40 %; single person €44,000 at 20 %; Employee PAYE Tax Credit €2,000",
        "url": "https://www.revenue.ie/en/personal-tax-credits-reliefs-and-exemptions/tax-relief-charts/index.aspx",
    },
    "finance-act-2025": {
        "title": "Finance Act 2025 (No. 18 of 2025), s. 2 — TCA 1997 s. 531AN: USC 0.5 % on the first €12,012, 2 % on the next €16,688, 3 % on the next €41,344, 8 % on the remainder for 2026 and later years; PDF SHA-256 91983a9e58c6a6751ac5b2f19752b08f456a33692d798fd3e03ee10313ed9d36",
        "url": ISB + "pdf/2025/en.act.2025.0018.pdf",
    },
    "sw-mp-act-2024": {
        "title": "Social Welfare (Miscellaneous Provisions) Act 2024 (No. 24 of 2024), s. 3 — Social Welfare Consolidation Act 2005 s. 13: Class A employee rate 4.2 % from 1 October 2025 and 4.35 % from 1 October 2026; employer 8 % / 10.25 %, then 8.15 % / 10.4 % from 1 October 2026; PDF SHA-256 a5c1991139d215e76be16af6788e68e9fae4d071b07a5844394941ce72462cc2",
        "url": ISB + "pdf/2024/en.act.2024.0024.pdf",
    },
    "sw-ae-act-2025": {
        "title": "Social Welfare and Automatic Enrolment Retirement Savings System (Amendment) Act 2025 (No. 19 of 2025), s. 2 — employer lower-rate threshold €552 a week (was €527) from 1 January 2026; PDF SHA-256 68d2cb1579504742d8a0edf24e52c3540a19b514e14c000033e2df62563d24a3",
        "url": ISB + "pdf/2025/en.act.2025.0019.pdf",
    },
    "swp-act-2015": {
        "title": "Social Welfare and Pensions Act 2015 (No. 47 of 2015), s. 10 — s. 13(2)(b): employee contribution on earnings above €352 and up to €424 a week reduced by €12 less one-sixth of the earnings over €352.01, or the equivalent for non-weekly pay; PDF SHA-256 d4d00385efbf7e0053f05465a5004304b5a72cb7cf8049d52dd65141a889238e",
        "url": ISB + "2015/act/47/enacted/en/pdf",
    },
    "ntf-levy": {
        "title": "Social Welfare (No. 2) Act 2019, s. 29 — National Training Fund Act 2000 s. 4(1): employer levy 1.0 % of reckonable earnings from 1 January 2020, collected with employer PRSI",
        "url": ISB + "2019/act/48/section/29/enacted/en/html",
    },
    "revenue-usc": {
        "title": "Revenue, Standard rates and thresholds of USC (2026: first €12,012 0.5 %, next €16,688 2 %, next €41,344 3 %, balance 8 %) and Reduced rates of USC (income €60,000 or less and aged 70+ or a full medical card: 0.5 % on the first €12,012 and 2 % on the balance); HTML SHA-256 14452bedd881e914df275c87ff3168010765fae1ef993fee282f52430a5709f7 / b77cee7b6bd4f05836af0cf86ce3558d00645b1de06b9466b3b110719fbc29a3",
        "url": "https://www.revenue.ie/en/jobs-and-pensions/usc/standard-rates-thresholds.aspx",
    },
    "revenue-rpn-fields": {
        "title": "Revenue, PAYE Modernisation software developers' presentation (10 October 2019) — the RPN carries payForUSCToDate and uscDeductedToDate for ceased employments in the tax year, separately from pay and tax for income tax; PDF SHA-256 b8cc4bb6f93872dcf9e04642726abb90eedf701388dc8b0588b9a83512d1b70a",
        "url": "https://www.revenue.ie/ga/corporate/documents/consultations/paye-modernisation/summary-findings/psda-presentation-101019.pdf",
    },
    "dsp-class-a": {
        "title": "Department of Social Protection, PRSI Class A Rates (last updated 10 June 2026) — reckonable pay of €38 or more a week; to 30 September 2026: €38–€352 A0 nil / 9.00, €352.01–€424 AX 4.20 / 9.00, €424.01–€552 AL 4.20 / 9.00, more than €552 A1 4.20 / 11.25; from 1 October 2026: 4.35 % employee, 9.15 / 11.40 employer; tapered employee PRSI credit of €12 a week between €352.01 and €424; HTML SHA-256 189159d6d6050a837ccf7bac8eb86800e138eb2f3d40d4103d5c598310946e0e",
        "url": "https://www.gov.ie/en/department-of-social-protection/publications/prsi-class-a-rates/",
    },
    "prsi-monthly-equivalents": {
        "title": "Monthly equivalents of the weekly PRSI figures, which s. 13(2)(b) leaves to 'the equivalent thereof in the case of an employed contributor remunerated otherwise than on a weekly basis': weekly × 52 / 12, rounded to the euro — €1,525 / €1,525.01 / €1,837, credit €52, employer threshold €2,392 (exact), Class A floor €165 — as used in payroll practice; the DSP's SW14 guide with its monthly table (assets.gov.ie) could not be retrieved to confirm them",
        "url": "https://www.gov.ie/en/department-of-social-protection/publications/prsi-class-a-rates/",
    },
}
for s in SOURCES.values():
    s["retrieved"] = RETRIEVED

PARAMS = {
    "standardRate": ("0.20", "revenue-rates"),
    "higherRate": ("0.40", "revenue-rates"),
    "singleCutOff": ("44000", "revenue-rates"),
    "uscRate1": ("0.005", "finance-act-2025"),
    "uscRate2": ("0.02", "finance-act-2025"),
    "uscRate3": ("0.03", "finance-act-2025"),
    "uscRate4": ("0.08", "finance-act-2025"),
    "uscCutOff1": ("12012", "finance-act-2025"),
    "uscCutOff2": ("28700", "finance-act-2025"),
    "uscCutOff3": ("70044", "finance-act-2025"),
    "prsiEmployeeRate": [(*Q123, "0.042", "sw-mp-act-2024"), (*Q4, "0.0435", "sw-mp-act-2024")],
    "prsiEmployerLowerRate": [(*Q123, "0.08", "sw-mp-act-2024"), (*Q4, "0.0815", "sw-mp-act-2024")],
    "prsiEmployerHigherRate": [(*Q123, "0.1025", "sw-mp-act-2024"), (*Q4, "0.104", "sw-mp-act-2024")],
    "nationalTrainingFundLevy": ("0.01", "ntf-levy"),
    # weekly thresholds (statute) and their monthly equivalents (DSP tables)
    "prsiExemptUpToWeekly": ("352", "swp-act-2015"),
    "prsiCreditStartWeekly": ("352.01", "swp-act-2015"),
    "prsiCreditUpToWeekly": ("424", "swp-act-2015"),
    "prsiMaxCreditWeekly": ("12", "swp-act-2015"),
    "prsiEmployerThresholdWeekly": ("552", "sw-ae-act-2025"),
    "prsiExemptUpToMonthly": ("1525", "prsi-monthly-equivalents"),
    "prsiCreditStartMonthly": ("1525.01", "prsi-monthly-equivalents"),
    "prsiCreditUpToMonthly": ("1837", "prsi-monthly-equivalents"),
    "prsiMaxCreditMonthly": ("52", "prsi-monthly-equivalents"),
    "prsiEmployerThresholdMonthly": ("2392", "prsi-monthly-equivalents"),
    "creditTaperDivisor": ("6", "swp-act-2015"),
}


def D(x):
    return Decimal(str(x))


def c2(x):
    return x.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def up2(x):
    return x.quantize(Decimal("0.01"), rounding=ROUND_CEILING)


def tax_week(d):
    return (d - date(d.year, 1, 1)).days // 7 + 1


def expected_lines(c):
    """The amounts the texts give for a test case, computed directly (no engine)."""
    gross = D(c["grossEarnings"])
    pre = D(c.get("preTaxDeductions", "0"))
    emp = c.get("employee", {})
    ytd = {k: D(v) for k, v in c.get("ytd", {}).items()}
    pay = date.fromisoformat(c.get("paymentDate") or c["periodEnd"])
    weekly = c["frequency"] == "WEEKLY"
    P = Decimal(52 if weekly else 12)
    n = tax_week(pay) if weekly else pay.month
    q4 = pay >= date(2026, 10, 1)
    lines = []

    def add(step, side, amount, record_zero=False):
        if amount != 0 or record_zero:
            lines.append({"step": step, "side": side, "amount": str(amount)})

    # PRSI, per contribution period (not cumulative)
    if emp.get("prsiExempt") != "true":
        E = gross
        thr, cstart, cup, maxc, ethr = ((D(352), D("352.01"), D(424), D(12), D(552)) if weekly
                                        else (D(1525), D("1525.01"), D(1837), D(52), D(2392)))
        rate = D("0.0435") if q4 else D("0.042")
        if E > thr:
            ee = c2(E * rate)
            if E <= cup:
                ee = max(ee - c2(maxc - (E - cstart) / 6), D(0))
            add("prsi", "EMPLOYEE", ee)
        er = (D("0.0815") if q4 else D("0.08")) if E <= ethr else (D("0.104") if q4 else D("0.1025"))
        add("prsi", "EMPLOYER", c2(E * (er + D("0.01"))))
    # income tax (Revenue guide ch. 9)
    basis = emp["taxBasis"]
    if basis == "CUMULATIVE" and n == 53:
        basis = "WEEK1"  # guide 14.3: a week-53 payment gets the week 1 basis, for tax and USC
    it_pay = max(gross - pre, D(0))
    cop_p = up2(D(emp.get("annualCutOff", "0")) / P)
    cred_p = up2(D(emp.get("annualTaxCredits", "0")) / P)
    if basis == "CUMULATIVE":
        cop, cred = cop_p * n, cred_p * n
        cum_pay = ytd.get("incomeTax.TAX.base", D(0)) + D(emp.get("previousPay", "0")) + it_pay
        paid = ytd.get("incomeTax.TAX.amount", D(0)) + D(emp.get("previousTax", "0"))
    else:
        cum_pay, paid = it_pay, D(0)
        if basis == "WEEK1":
            cop, cred = cop_p, cred_p
        elif basis == "EMERGENCY":
            first = ytd.get("incomeTax.TAX.periods", D(0)) < (4 if weekly else 1)
            cop, cred = (up2(D(44000) / P) if first else D(0)), D(0)
        else:  # EMERGENCY_NO_PPSN
            cop, cred = D(0), D(0)
    gross_tax = c2(min(cum_pay, cop) * D("0.20")) + c2(max(cum_pay - cop, D(0)) * D("0.40"))
    add("incomeTax", "TAX", max(gross_tax - cred, D(0)) - paid, record_zero=True)
    # USC
    status = emp.get("uscStatus", "ORDINARY")
    if status != "EXEMPT" or basis.startswith("EMERGENCY"):  # 9.5.1: emergency USC applies to all payments
        if basis.startswith("EMERGENCY"):
            usc = c2(gross * D("0.08"))
        else:
            m = n if basis == "CUMULATIVE" else 1
            cuts = [D(emp.get(k, d)) for k, d in (("uscCutOff1", "12012"), ("uscCutOff2", "28700"), ("uscCutOff3", "70044"))]
            pc = [c2(x / P) * m for x in cuts]
            u_pay = gross + (ytd.get("usc.TAX.base", D(0)) + D(emp.get("previousUscPay", emp.get("previousPay", "0"))) if basis == "CUMULATIVE" else D(0))
            if status == "REDUCED":
                usc = c2(min(u_pay, pc[0]) * D("0.005")) + c2(max(u_pay - pc[0], D(0)) * D("0.02"))
            else:
                b1, b2, b3 = min(u_pay, pc[0]), max(min(u_pay, pc[1]) - pc[0], D(0)), max(min(u_pay, pc[2]) - pc[1], D(0))
                b4 = max(u_pay - pc[2], D(0))
                usc = c2(b1 * D("0.005")) + c2(b2 * D("0.02")) + c2(b3 * D("0.03")) + c2(b4 * D("0.08"))
            if basis == "CUMULATIVE":
                usc -= ytd.get("usc.TAX.amount", D(0)) + D(emp.get("previousUsc", "0"))
        add("usc", "TAX", usc, record_zero=True)
    order = [("prsi", "EMPLOYEE"), ("prsi", "EMPLOYER"), ("incomeTax", "TAX"), ("usc", "TAX")]
    lines.sort(key=lambda l: order.index((l["step"], l["side"])))
    return lines


def case(name, evidence, gross, employee, frequency="MONTHLY", start="2026-03-01", end="2026-03-31", payment=None,
         ytd=None, pre_tax=None, error=None, employment_start=None):
    c = {"name": name, "evidence": evidence, "periodStart": start, "periodEnd": end, "frequency": frequency}
    if payment:
        c["paymentDate"] = payment
    c.update({"grossEarnings": gross, "fullPeriod": True})
    if employment_start:
        c["employmentStartDate"] = employment_start
    c.update({"employer": {}, "employee": employee})
    if ytd:
        c["ytd"] = ytd
    if pre_tax:
        c["preTaxDeductions"] = pre_tax
    if error:
        c["expectError"] = error
    return c


RPN = {"taxBasis": "CUMULATIVE", "annualCutOff": "44000", "annualTaxCredits": "4000"}


def test_cases():
    law = "Revenue Employers' Guide ch. 9; Finance Act 2025 s. 2; SW (MP) Act 2024 s. 3; SWP Act 2015 s. 10; NTF levy 1 %; amounts from build_template.py expected_lines()"
    computed = [
        case("Revenue example: weekly tax, week 1", "Employers' Guide 9.3: pay €1,000, pension €50, €846.16 @ 20% = 169.23, €103.84 @ 40% = 41.54, less €76.93 = €133.84. " + law,
             "1000.00", dict(RPN), frequency="WEEKLY", start="2026-01-01", end="2026-01-07", payment="2026-01-02", pre_tax="50.00"),
        case("Revenue example: cumulative refund, week 8 with no pay", "Employers' Guide 9.3.2: cumulative pay €4,600, credits 8 x €110 = 880, tax due 40, paid 150: €110 refunded. " + law,
             "0", {"taxBasis": "CUMULATIVE", "annualCutOff": "44000", "annualTaxCredits": "5720", "uscStatus": "EXEMPT"}, frequency="WEEKLY",
             start="2026-02-19", end="2026-02-25", payment="2026-02-20", ytd={"incomeTax.TAX.base": "4600", "incomeTax.TAX.amount": "150", "incomeTax.TAX.periods": "7"}),
        case("monthly cumulative, month 3", "Employers' Guide 9.3.1: cut-off 3 x 3,666.67 = 11,000.01, credits 3 x 333.34 = 1,000.02; months 1-2 of €4,500 paid 733.32 + 733.33 tax and 2 x 96.07 USC. " + law,
             "4500.00", dict(RPN), ytd={"incomeTax.TAX.base": "9000", "incomeTax.TAX.amount": "1466.65", "usc.TAX.base": "9000", "usc.TAX.amount": "192.14"}),
        case("monthly €3,000: PRSI, USC bands", "PRSI 4.2% / employer 11.25%; USC 1,001 @ 0.5% + 1,390.67 @ 2% + 608.33 @ 3%. " + law,
             "3000.00", dict(RPN), start="2026-01-01", end="2026-01-31"),
        case("monthly pay in the PRSI credit band", "€1,600: 4.2% = 67.20 less credit 52 - (1,600 - 1,525.01) / 6 = 39.50; employer lower rate 9%. " + law,
             "1600.00", dict(RPN)),
        case("monthly pay at the PRSI exemption threshold", "€1,525 a month: no employee PRSI; employer 9%. " + law, "1525.00", dict(RPN)),
        case("weekly €377: PRSI credit", "SWP Act 2015 s. 10: 377 x 4.2% = 15.83 less (12 - (377 - 352.01) / 6) = 7.84 -> 7.99. " + law,
             "377.00", dict(RPN), frequency="WEEKLY", start="2026-03-05", end="2026-03-11", payment="2026-03-06"),
        case("weekly €600 before and after 1 October", "SW (MP) Act 2024: 4.2% / 11.25% in September; week 1 basis. " + law,
             "600.00", {**RPN, "taxBasis": "WEEK1"}, frequency="WEEKLY", start="2026-09-24", end="2026-09-30", payment="2026-09-25"),
        case("weekly €600 from 1 October 2026", "SW (MP) Act 2024: 4.35% / 11.4% from 1 October 2026; week 1 basis. " + law,
             "600.00", {**RPN, "taxBasis": "WEEK1"}, frequency="WEEKLY", start="2026-10-08", end="2026-10-14", payment="2026-10-09"),
        case("week 1 / month 1 basis", "Employers' Guide 9.4: month 1 cut-off and credits every month, no refunds. " + law,
             "3000.00", {"taxBasis": "WEEK1", "annualCutOff": "44000", "annualTaxCredits": "4000"}, start="2026-06-01", end="2026-06-30",
             ytd={"incomeTax.TAX.base": "15000", "incomeTax.TAX.amount": "1333.30"}),
        case("emergency basis with PPSN, first month", "Employers' Guide 9.5.2: 1/12 of the single cut-off (3,666.67), no credits; USC 8%. " + law,
             "3000.00", {"taxBasis": "EMERGENCY"}, start="2026-05-01", end="2026-05-31"),
        case("emergency basis with PPSN, second month", "Employers' Guide 9.5.2: no cut-off from the second month. " + law,
             "3000.00", {"taxBasis": "EMERGENCY"}, start="2026-06-01", end="2026-06-30", ytd={"incomeTax.TAX.periods": "1", "incomeTax.TAX.base": "3000", "incomeTax.TAX.amount": "600"}),
        case("emergency basis, weekly, fourth week of pay", "Employers' Guide 9.5.2: 1/52 of the single cut-off (846.16) for the first 4 weeks, no credits. " + law,
             "1000.00", {"taxBasis": "EMERGENCY"}, frequency="WEEKLY", start="2026-05-07", end="2026-05-13", payment="2026-05-08",
             ytd={"incomeTax.TAX.periods": "3"}),
        case("emergency basis, weekly, fifth week of pay", "Employers' Guide 9.5.2: no cut-off after the first 4 weeks. " + law,
             "1000.00", {"taxBasis": "EMERGENCY"}, frequency="WEEKLY", start="2026-05-14", end="2026-05-20", payment="2026-05-15",
             ytd={"incomeTax.TAX.periods": "4"}),
        case("emergency basis without PPSN", "Employers' Guide 9.5.2: higher rate on all pay; USC 8%. " + law,
             "3000.00", {"taxBasis": "EMERGENCY_NO_PPSN"}),
        case("reduced USC (medical card or 70+)", "Revenue, Reduced rates of USC: 0.5% on the first €12,012 and 2% on the balance. " + law,
             "3000.00", {**RPN, "uscStatus": "REDUCED"}, start="2026-01-01", end="2026-01-31"),
        case("previous employment pay and tax from the RPN", "Employers' Guide 15.4: pay and tax to date from a previous employment count on the cumulative basis. " + law,
             "4000.00", {**RPN, "previousPay": "12000", "previousTax": "1800", "previousUsc": "300"}, start="2026-04-01", end="2026-04-30"),
        case("previous employment with pension contributions: USC pay to date", "RPN payForUSCToDate (Revenue PAYE Modernisation presentation 2019): USC on €12,600 previous gross, income tax on €12,000. " + law,
             "4000.00", {**RPN, "previousPay": "12000", "previousUscPay": "12600", "previousTax": "1800", "previousUsc": "312"}, start="2026-04-01", end="2026-04-30"),
        case("PRSI-exempt employee (A1 certificate)", "Not insurable in Ireland: no PRSI; tax and USC as per RPN. " + law,
             "3000.00", {**RPN, "prsiExempt": "true"}, start="2026-01-01", end="2026-01-31"),
        case("week 53 on the cumulative basis: week 1 figures", "Employers' Guide 14.3: where 'Week 53' occurs (31 December is a pay day, 10.2), tax credits and the tax and USC cut-off points are set against the payment 'As for week 1 basis'; the year to date is not used. " + law,
             "1000.00", dict(RPN), frequency="WEEKLY", start="2026-12-31", end="2027-01-06", payment="2026-12-31",
             ytd={"incomeTax.TAX.base": "52000", "incomeTax.TAX.amount": "9000", "incomeTax.TAX.periods": "52", "usc.TAX.base": "52000", "usc.TAX.amount": "1300"}),
        case("emergency basis ignores a USC exemption status", "Employers' Guide 9.5.1: on the emergency basis USC is a flat 8% 'applied to all payments'. " + law,
             "3000.00", {"taxBasis": "EMERGENCY_NO_PPSN", "uscStatus": "EXEMPT"}),
    ]
    for c in computed:
        c["expectLines"] = expected_lines(c)
    errors = [
        case("missing RPN basis", "the basis comes from the Revenue Payroll Notification", "3000.00", {}, error="taxBasis"),
        case("cumulative basis without cut-off", "the RPN gives the yearly cut-off and credits", "3000.00", {"taxBasis": "CUMULATIVE"}, error="annualCutOff"),
        case("weekly pay under €38", "Class J/M pay under €38 a week is not modelled", "30.00", dict(RPN), frequency="WEEKLY",
             start="2026-03-05", end="2026-03-11", payment="2026-03-06", error="€38"),
        case("December 2026 pay paid in January 2027", "the payment date places pay in the tax year", "3000.00", dict(RPN),
             start="2026-12-01", end="2026-12-31", payment="2027-01-08", error="a newer template version is needed"),
    ]
    return computed + errors


def parameter(spec):
    if isinstance(spec, tuple):
        value, source = spec
        return [{"from": FROM, "to": TO, "value": value, "source": source}]
    return [{"from": f, "to": t, "value": v, "source": s} for f, t, v, s in spec]


# Statutory leave pay (PayrollTemplate.leavePay): applied as a leave pay rule where the workspace has none of its own.
LEAVE_SOURCES = {"mpa-1994-s22": {
    "title": "Maternity Protection Act 1994 (No. 34 of 1994), s. 22(1), revised: absence on maternity leave keeps the "
             "employee's employment rights 'other than, except in the case of natal care absence, the employee's right "
             "to remuneration during such absence'; Maternity Benefit is a Department of Social Protection payment",
    "url": "https://revisedacts.lawreform.ie/eli/1994/act/34/section/22/revised/en/html",
    "retrieved": "2026-10-29"}}

LEAVE_PAY = [{"leaveType": "MATERNITY",
              "deductionPercentage": "100",
              "payBands": [],
              "source": "mpa-1994-s22",
              "note": "Unpaid by the employer: maternity leave carries no right to pay (Maternity Protection Act 1994 s. 22(1)); "
                      "Maternity Benefit is paid by the Department of Social Protection, outside payroll. An employer that keeps "
                      "paying salary (and may have the benefit paid to it) needs a workspace rule. Paternity or parent's leave recorded as "
                      "parental leave is treated the same: add a rule if the employer pays it."}]


def main():
    weekly = "#frequency == 'WEEKLY'"
    basis = "#employee['taxBasis']"
    cum = "#cumulative == 1"

    def usc_cut(i):
        return "#divide(#coalesce(#employee['uscCutOff" + str(i) + "'], #p['uscCutOff" + str(i) + "']), #periodsPerYear, 2, 'HALF_UP') * #uscPeriods"

    def wk(w, m):
        return "(" + weekly + " ? #p['" + w + "'] : #p['" + m + "'])"
    template = {
        "schemaVersion": 1,
        "id": "ie-paye",
        "name": "Ireland — PAYE, USC and PRSI",
        "version": "2026.1.3",
        "status": "LAW_DERIVED",
        "jurisdiction": {"country": "IE"},
        "validity": {"from": FROM, "to": TO},
        "currency": "EUR",
        "payFrequencies": ["WEEKLY", "MONTHLY"],
        "taxYearStart": "--01-01",
        "dateBasis": "PAYMENT_DATE",
        "rounding": {"scale": 2, "mode": "HALF_UP"},
        "changelog": [
            {"version": "2026.1.3", "date": "2026-10-29",
             "summary": "Statutory parental leave as a leave pay rule: unpaid by the employer (Maternity Protection Act 1994 s. 22(1); Maternity Benefit is paid by the Department of Social Protection); applied where the workspace has no parental leave rule of its own. Before, parental leave was paid in full unless a rule was added."},
            {"version": "2026.1.2", "date": "2026-10-29",
             "summary": "States what the template leaves out for leave: statutory sick pay (Sick Leave Act 2022) is not calculated. Calculations are unchanged."},
            {"version": "2026.1.1", "date": "2026-10-24",
             "summary": "Corrects errors: on the emergency basis USC is now 8 % of all pay even when a USC exemption status is set (Employers' Guide 9.5.1); week 53 (pay day 31 December) is calculated on the week 1 basis (14.3) instead of being refused."},
            {"version": "2026.1.0", "date": RETRIEVED,
             "summary": "2026: PAYE 20 % / 40 % from the RPN on the cumulative, week 1/month 1 or emergency basis; USC 0.5 / 2 / 3 / 8 % (Finance Act 2025); Class A PRSI 4.2 % rising to 4.35 % on 1 October 2026 with the PRSI credit; employer 9 % / 11.25 % rising to 9.15 % / 11.4 % (incl. 1 % NTF levy), threshold €552 a week."}
        ],
        "sources": {**SOURCES, **LEAVE_SOURCES},
        "inputs": {
            "employer": [],
            "employee": [
                {"key": "taxBasis", "type": "ENUM", "required": True, "values": ["CUMULATIVE", "WEEK1", "EMERGENCY", "EMERGENCY_NO_PPSN"],
                 "description": "Basis of deduction from the Revenue Payroll Notification (RPN): cumulative, week 1/month 1, or emergency (with or without a PPS number when no RPN is available)"},
                {"key": "annualCutOff", "type": "DECIMAL", "required": False, "min": 0,
                 "description": "Yearly standard rate cut-off point from the RPN (required on the cumulative and week 1/month 1 bases)"},
                {"key": "annualTaxCredits", "type": "DECIMAL", "required": False, "min": 0,
                 "description": "Yearly tax credits from the RPN (required on the cumulative and week 1/month 1 bases)"},
                {"key": "uscStatus", "type": "ENUM", "required": False, "defaultValue": "ORDINARY", "values": ["ORDINARY", "REDUCED", "EXEMPT"],
                 "description": "USC status from the RPN: ordinary rates, reduced rates (full medical card or aged 70+, with income of €60,000 or less: 0.5 % then 2 %), or exempt (income up to the €13,000 exemption threshold); ignored on the emergency basis, where USC is 8 % of all pay"},
                {"key": "uscCutOff1", "type": "DECIMAL", "required": False, "min": 0, "description": "Yearly USC rate 1 cut-off from the RPN, if not €12,012"},
                {"key": "uscCutOff2", "type": "DECIMAL", "required": False, "min": 0, "description": "Yearly USC rate 2 cut-off from the RPN, if not €28,700"},
                {"key": "uscCutOff3", "type": "DECIMAL", "required": False, "min": 0, "description": "Yearly USC rate 3 cut-off from the RPN, if not €70,044"},
                {"key": "previousPay", "type": "DECIMAL", "required": False, "defaultValue": "0", "min": 0,
                 "description": "Pay to date from previous employments this year, from the RPN (cumulative basis)"},
                {"key": "previousTax", "type": "DECIMAL", "required": False, "defaultValue": "0", "min": 0,
                 "description": "Tax deducted to date in previous employments this year, from the RPN"},
                {"key": "previousUscPay", "type": "DECIMAL", "required": False, "min": 0,
                 "description": "Pay for USC to date from previous employments this year, from the RPN (payForUSCToDate); defaults to previousPay — it differs when pension contributions reduced the previous pay for income tax"},
                {"key": "previousUsc", "type": "DECIMAL", "required": False, "defaultValue": "0", "min": 0,
                 "description": "USC deducted to date in previous employments this year, from the RPN"},
                {"key": "prsiExempt", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "Not insurable for PRSI in Ireland (e.g. covered by an A1 certificate from another EU state)"},
            ],
        },
        "parameters": {name: parameter(spec) for name, spec in PARAMS.items()},
        "tables": {},
        "steps": [
            {"id": "calendarMonth", "type": "REQUIRE", "condition": weekly + " or #isCalendarMonth",
             "message": "Monthly pay must cover a whole calendar month"},
            {"id": "periodNumber", "type": "VALUE", "expr": weekly + " ? #taxWeek : #taxMonth"},
            {"id": "cumulative", "type": "VALUE", "source": "revenue-guide",
             "expr": basis + " == 'CUMULATIVE' and #periodNumber <= 52 ? 1 : 0"},
            {"id": "weekOneFigures", "type": "VALUE", "source": "revenue-guide",
             "expr": basis + " == 'WEEK1' or (" + basis + " == 'CUMULATIVE' and #periodNumber > 52) ? 1 : 0"},
            {"id": "rpnFigures", "type": "REQUIRE", "source": "revenue-guide",
             "when": basis + " == 'CUMULATIVE' or " + basis + " == 'WEEK1'",
             "condition": "#employee['annualCutOff'] != null and #employee['annualTaxCredits'] != null",
             "message": "The cumulative and week 1/month 1 bases need the yearly cut-off point (annualCutOff) and tax credits (annualTaxCredits) from the RPN"},
            {"id": "grossPay", "type": "VALUE", "expr": "#pos(#taxableEarnings - #reductions)"},
            {"id": "taxPay", "type": "VALUE", "expr": "#pos(#grossPay - #preTaxDeductions)"},
            {"id": "prsiPay", "type": "VALUE", "expr": "#pos(#socialEarnings - #reductions)"},
            {"id": "classJ", "type": "REQUIRE", "source": "dsp-class-a",
             "when": "!#employee['prsiExempt'] and #prsiPay > 0",
             "condition": "#prsiPay >= (" + weekly + " ? 38 : 165)",
             "message": "Pay under €38 a week (€165 a month) is insured under Class J/M, which this template does not model"},
            {"id": "prsiCredit", "type": "VALUE", "source": "swp-act-2015",
             "when": "!#employee['prsiExempt'] and #prsiPay > " + wk("prsiExemptUpToWeekly", "prsiExemptUpToMonthly") + " and #prsiPay <= " + wk("prsiCreditUpToWeekly", "prsiCreditUpToMonthly"),
             "expr": "#round(" + wk("prsiMaxCreditWeekly", "prsiMaxCreditMonthly") + " - #divide(#prsiPay - " + wk("prsiCreditStartWeekly", "prsiCreditStartMonthly") + ", #p['creditTaperDivisor'], 10, 'HALF_UP'), 2, 'HALF_UP')"},
            {"id": "prsi", "type": "CONTRIBUTION", "label": "PRSI (Class A)", "ledger": "SOCIAL_SECURITY", "source": "sw-mp-act-2024",
             "when": "!#employee['prsiExempt']", "base": "#prsiPay",
             "employee": {"amount": "#prsiPay > " + wk("prsiExemptUpToWeekly", "prsiExemptUpToMonthly") + " ? #pos(#round(#prsiPay * #p['prsiEmployeeRate'], 2, 'HALF_UP') - #prsiCredit) : 0"},
             "employer": {"amount": "#round(#prsiPay * ((#prsiPay <= " + wk("prsiEmployerThresholdWeekly", "prsiEmployerThresholdMonthly") + " ? #p['prsiEmployerLowerRate'] : #p['prsiEmployerHigherRate']) + #p['nationalTrainingFundLevy']), 2, 'HALF_UP')"}},
            {"id": "periodCutOff", "type": "VALUE", "source": "revenue-guide", "when": "#employee['annualCutOff'] != null",
             "expr": "#divide(#employee['annualCutOff'], #periodsPerYear, 2, 'CEILING')"},
            {"id": "periodCredits", "type": "VALUE", "source": "revenue-guide", "when": "#employee['annualTaxCredits'] != null",
             "expr": "#divide(#employee['annualTaxCredits'], #periodsPerYear, 2, 'CEILING')"},
            {"id": "emergencyFirstPeriods", "type": "VALUE", "source": "revenue-guide", "when": basis + " == 'EMERGENCY'",
             "expr": "#ytd.periods('incomeTax', 'TAX') < (" + weekly + " ? 4 : 1) ? 1 : 0"},
            {"id": "cutOff", "type": "VALUE", "source": "revenue-guide",
             "expr": cum + " ? #periodCutOff * #periodNumber : (#weekOneFigures == 1 ? #periodCutOff : (#emergencyFirstPeriods == 1 ? #divide(#p['singleCutOff'], #periodsPerYear, 2, 'CEILING') : 0))"},
            {"id": "credits", "type": "VALUE", "source": "revenue-guide",
             "expr": cum + " ? #periodCredits * #periodNumber : (#weekOneFigures == 1 ? #periodCredits : 0)"},
            {"id": "payForTax", "type": "VALUE",
             "expr": cum + " ? #ytd.base('incomeTax', 'TAX') + #employee['previousPay'] + #taxPay : #taxPay"},
            {"id": "taxPaid", "type": "VALUE",
             "expr": cum + " ? #ytd.amount('incomeTax', 'TAX') + #employee['previousTax'] : 0"},
            {"id": "incomeTax", "type": "TAX", "label": "Income tax (PAYE)", "ledger": "INCOME_TAX", "source": "revenue-guide",
             "base": "#taxPay", "recordZero": True, "allowNegative": True,
             "amount": "#pos(#round(#min(#payForTax, #cutOff) * #p['standardRate'], 2, 'HALF_UP') + #round(#pos(#payForTax - #cutOff) * #p['higherRate'], 2, 'HALF_UP') - #credits) - #taxPaid"},
            {"id": "uscPeriods", "type": "VALUE", "expr": cum + " ? #periodNumber : 1"},
            {"id": "uscCut1", "type": "VALUE", "source": "finance-act-2025", "expr": usc_cut(1)},
            {"id": "uscCut2", "type": "VALUE", "source": "finance-act-2025", "expr": usc_cut(2)},
            {"id": "uscCut3", "type": "VALUE", "source": "finance-act-2025", "expr": usc_cut(3)},
            {"id": "payForUsc", "type": "VALUE", "source": "revenue-rpn-fields",
             "expr": cum + " ? #ytd.base('usc', 'TAX') + #coalesce(#employee['previousUscPay'], #employee['previousPay']) + #grossPay : #grossPay"},
            {"id": "uscPaid", "type": "VALUE",
             "expr": cum + " ? #ytd.amount('usc', 'TAX') + #employee['previousUsc'] : 0"},
            {"id": "usc", "type": "TAX", "label": "Universal Social Charge", "ledger": "INCOME_TAX", "source": "revenue-usc",
             "when": "#employee['uscStatus'] != 'EXEMPT' or " + basis + " == 'EMERGENCY' or " + basis + " == 'EMERGENCY_NO_PPSN'", "base": "#grossPay", "recordZero": True, "allowNegative": True,
             "amount": "(" + basis + " == 'EMERGENCY' or " + basis + " == 'EMERGENCY_NO_PPSN') ? #round(#grossPay * #p['uscRate4'], 2, 'HALF_UP') : ("
                       "#employee['uscStatus'] == 'REDUCED' ? "
                       "#round(#min(#payForUsc, #uscCut1) * #p['uscRate1'], 2, 'HALF_UP') + #round(#pos(#payForUsc - #uscCut1) * #p['uscRate2'], 2, 'HALF_UP') : "
                       "#round(#min(#payForUsc, #uscCut1) * #p['uscRate1'], 2, 'HALF_UP') + #round(#pos(#min(#payForUsc, #uscCut2) - #uscCut1) * #p['uscRate2'], 2, 'HALF_UP')"
                       " + #round(#pos(#min(#payForUsc, #uscCut3) - #uscCut2) * #p['uscRate3'], 2, 'HALF_UP') + #round(#pos(#payForUsc - #uscCut3) * #p['uscRate4'], 2, 'HALF_UP')"
                       ") - #uscPaid"},
        ],
        "leavePay": LEAVE_PAY,
        "limitations": [
            "PAYE employment paid weekly or monthly (whole calendar months) in EUR in 2026; the tax year, the tax week or month and the rates are taken from the payment date (Employers' Guide ch. 9). A payment in week 53 (pay day 31 December) uses the week 1 basis figures (Employers' Guide 14.3); pay dated in 2027 is refused. The tax week follows the payment date: where a weekly pay day is moved (e.g. Friday paid on Thursday), the guide (10.2) keeps the normal pay day for the tax week, so enter the normal pay day as the payment date.",
            "Income tax and USC follow the Revenue Payroll Notification entered on the employee: basis, yearly cut-off point and tax credits, USC status and any non-standard USC cut-offs, and pay, tax and USC from previous employments. The template does not fetch or check RPNs; without one, use the emergency basis.",
            "Tax cut-off and credits per period are the yearly figures divided by the periods and rounded up to the cent, multiplied by the tax week or month on the cumulative basis; USC cut-offs per period are rounded to the cent, as in Revenue's examples. The emergency basis gives the first month (or first four weeks of pay) 1/12 (1/52) of the single cut-off and no credits; later periods and the no-PPSN case pay 40 %; USC is 8 % throughout. The first month or four weeks are counted as this employer's pay periods within the tax year (Employers' Guide 14.3); weeks of employment before 1 January are not counted.",
            "Pension and other pre-tax deductions reduce pay for income tax only; USC and PRSI are charged on gross pay.",
            "PRSI is Class A only, worked out per pay period (not cumulatively). Weekly thresholds are the statutory figures, as tabled by the Department of Social Protection; the monthly thresholds (€1,525 / €1,837, credit €52, employer €2,392) are their monthly equivalents (weekly × 52 / 12, rounded to the euro), which the acts leave to 'the equivalent' — the DSP's SW14 guide with the official monthly table could not be retrieved to confirm them. Pay under €38 a week (€165 a month) is refused; employees aged 66 or over receiving the State Pension (Contributory) (Class J), Class S, B, D and other classes, and fortnightly pay, are not modelled.",
            "Statutory sick pay (Sick Leave Act 2022), which the employer pays at 70 % of normal daily earnings up to a daily cap, is not calculated: a leave pay rule only pays a percentage of salary for leave days, and without one approved sick leave is paid as full salary. Parental leave is unpaid by default (Maternity Protection Act 1994 s. 22(1)); Maternity Benefit and other Department of Social Protection payments are outside payroll.",
            "The employer rate includes the 1 % National Training Fund levy; reliefs such as the JobsPlus incentive are not modelled.",
            "Not modelled: the reduced USC rate's €60,000 income test (entered via the RPN status), tax exemption and marginal relief (Employers' Guide 9.7), separate periods of employment treated as one (9.6), Local Property Tax deductions shown on the RPN, benefit-in-kind and share-based remuneration.",
            "Status LAW_DERIVED: values are cited from the acts, Revenue's Employers' Guide and USC pages, and the DSP's Class A table; one case reproduces Revenue's weekly tax worked example and one its cumulative refund example.",
        ],
        "testCases": test_cases(),
    }
    with open(sys.argv[1], "w", encoding="utf-8") as f:
        json.dump(template, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("wrote", sys.argv[1], "with", len(template["testCases"]), "test cases")


main()
