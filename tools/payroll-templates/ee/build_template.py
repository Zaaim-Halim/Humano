"""Build src/main/resources/payroll-templates/ee/ee-employment.json.

Estonia, employment (tööleping) paid monthly in EUR, 2026. Every rule below cites the act it comes from;
the acts are the consolidated texts on Riigi Teataja (identified by their publication marks), the 2026
figures come from the 2026 State Budget Act and the Government's unemployment insurance regulation, and
the Tax and Customs Board (EMTA) pages explain how employers apply them. The test cases' expected amounts
are computed by expected_lines() — an implementation written directly from those texts, independent of
the Java engine; the engine must reproduce them exactly.

Usage: python3 -I build_template.py ../../../src/main/resources/payroll-templates/ee/ee-employment.json
"""
import json
import sys
from decimal import ROUND_HALF_UP, Decimal as D

RETRIEVED = "2026-10-24"
LEAVE_RETRIEVED = "2026-10-25"
FROM, TO = "2026-01-01", "2026-12-31"
RT = "https://www.riigiteataja.ee/akt/"
EMTA = "https://www.emta.ee/"

SOURCES = {
    "tums": {
        "title": "Tulumaksuseadus (Income Tax Act), consolidated text RT I, 09.07.2026, 66 — § 4 lg 1 (tulumaksu määr 22%), § 23 lg 1 (maksuvaba tulu 8400 eurot), § 23^5 lg 1 (vanaduspensionieas 9312 eurot alates maksustamisperioodist, kui isik jõuab vanaduspensioniikka), § 42 lg 1 (kirjaliku avalduse alusel igas kalendrikuus mitte rohkem kui 1/12 maksuvabast tulust), lg 1^3 (vanaduspensionieas arvab Sotsiaalkindlustusamet maha pensionist, vahe väljamaksja avalduse alusel), lg 2 (avaldus ainult ühele tulumaksu kinnipidajale), lg 5–6 (kinnipeetud töötuskindlustusmakse ja kohustusliku kogumispensioni makse arvatakse enne tulumaksu arvutamist maha)",
        "url": RT + "TuMS",
    },
    "sms": {
        "title": "Sotsiaalmaksuseadus (Social Tax Act), consolidated text RT I, 21.05.2026, 19 — § 2 lg 2 (vähemalt kuumääralt), lg 2^1 (mitme tööandja korral maksab kuumääralt maksuvaba tulu arvestav tööandja), lg 3 (proportsionaalselt töötatud ajaga: tööle asumise või lahkumise kuul, töölepingu seaduse § 19 alusel töö tegemisest keeldumisel, mitte poolte kokkuleppel antud tasustamata puhkusel), lg 4 (kuumäära ei kohaldata: lühendatud tööaeg, alla 3-aastase või kolme alla 19-aastase lapse vanem, RaKS § 5 lg 4 p 5, endine töötu 12 kuud, riikliku pensioni saaja või osalise/puuduva töövõimega isik, volikogu liige), § 6 lg 1 p 5 (riik maksab osalise või puuduva töövõimega töötaja eest), § 7 lg 1 (33%)",
        "url": RT + "SMS",
    },
    "rea-2026": {
        "title": "2026. aasta riigieelarve seadus (State Budget Act 2026), RT I, 18.12.2025, 22 — § 2 lg 6: 'Sotsiaalmaksuseaduse § 2^1 alusel kehtestatav sotsiaalmaksu maksmise aluseks olev kuumäär on 886 eurot'",
        "url": RT + "118122025022",
    },
    "tkindls": {
        "title": "Töötuskindlustuse seadus (Unemployment Insurance Act), consolidated text RT I, 30.06.2026, 40 — § 40 lg 1 (makse objekt; vanaduspensioniealisele ainult tööandja makse), § 41 (määrad kehtestab Vabariigi Valitsus), § 42 lg 1 p 1 (kindlustatu makse kinnipidamise kohustus lõpeb vanaduspensioniikka jõudmise või ennetähtaegse või paindliku vanaduspensioni määramise kuu viimasel kuupäeval), lg 3 (ümardatakse sendi täpsusega)",
        "url": RT + "TKindlS",
    },
    "vv-78-2025": {
        "title": "Vabariigi Valitsuse 25.09.2025 määrus nr 78 'Kindlustatu ja tööandja töötuskindlustusmakse määrad aastatel 2026–2029', RT I, 30.09.2025, 3 — § 1: kindlustatu 1,6%, tööandja 0,8%; jõustub 1. jaanuaril 2026 (määrus nr 59 tunnistatakse kehtetuks)",
        "url": RT + "130092025003",
    },
    "kops": {
        "title": "Kogumispensionide seadus (Funded Pensions Act), consolidated text RT I, 03.12.2024, 16 — § 7 (makse objekt ja kohustuse algus/lõpp), § 8 lg 1 (periood kalendrikuu), § 9 (makse määr 2%, valikuna 4% või 6% vähemalt kalendriaastaks), § 11 lg 1 p 1 (tööandja kontrollib isikukoodi järgi ja peab kinni valitud määras)",
        "url": RT + "KoPS",
    },
    "rm-17-2013": {
        "title": "Rahandusministri 15.03.2013 määrus nr 17 'Sotsiaalmaksu arvestamise ning sotsiaalmaksu andmete Sotsiaalkindlustusametile ja Tervisekassale väljastamise kord', RT I, 28.04.2023, 3 — § 2 (kuumäär tasu väljamaksmiseks kokku lepitud kuu eest; kuu, milles sisaldub palgapäev), § 3–4 (tasu maksmine kokkulepitust erineval kuul), § 5 (mitme tööandja korral kuumäärast teise tööandja makstud tasu lahutamisel, kuid mitte vähem kui makstud tasult), § 6 lg 2 (töötatud aja proportsioon: kalendripäevade arv jagatud 30-ga), § 7 (lõpparve)",
        "url": RT + "122032013001",
    },
    "emta-2026": {
        "title": "EMTA, 'Maksumuudatused 2026. aastal' (27.11.2025) and 'Maksuvaba tulu arvestamine' — 22%; 700 eurot kuus (vanaduspensionieas 776); avaldus ainult ühele tööandjale; maksuvaba tulu kuni töötasu väljamakse ulatuses; kuumäär 886 eurot, miinimum 292,38 eurot; töötuskindlustus 1,6% / 0,8%; kogumispension 2%, 4% või 6%; maksustamine on kassapõhine (detsembri 2025 töötasu jaanuaris 2026 väljamaksmisel 2026. aasta määrad)",
        "url": EMTA + "uudised/maksumuudatused-2026",
    },
}
for s in SOURCES.values():
    s["retrieved"] = RETRIEVED
SOURCES["emta-sick-pay"] = {
    "title": "EMTA, TSD vormi täitmise näide 10 'Tööandja vabatahtlik töövõimetushüvitis 30% keskmisest töötasust (haigusleht)': 'Vabatahtlik töövõimetushüvitis piirmäära ulatuses maksustatakse ainult tulumaksuga', väljamakse liik 24; töötasu 1000 eurot (sotsiaalmaks 330, töötuskindlustus 16 / 8, tulumaks 62,48) ja hüvitis 72 eurot (tulumaks 15,84)",
    "url": EMTA + "en/business-client/taxes-and-payment/income-and-social-taxes/submission-declaration-form-tsd/naide-10-tooandja-vabatahtlik-toovoimetushuvitis-30-keskmisest-tootasust-haigusleht",
    "retrieved": LEAVE_RETRIEVED,
}
SOURCES["emta-social-tax"] = {
    "title": "EMTA, 'Social tax' — minimum obligation: the employer does not have to pay the minimum social tax when the 'employee is away from work for a full month' because the 'employee is temporarily incapacitated for work within the meaning of the Health Insurance Act' or 'takes leave (including maternity leave), except unpaid leave by agreement between parties', or is raising a child under three or three or more children under 19, or is in conscript service",
    "url": EMTA + "en/business-client/taxes-and-payment/income-and-social-taxes/social-tax",
    "retrieved": LEAVE_RETRIEVED,
}

PARAMS = {
    "incomeTaxRate": ("0.22", "tums"),
    "monthlyTaxFreeAmount": ("700", "tums"),  # § 42 lg 1: 1/12 of § 23 lg 1 8400
    "monthlyTaxFreeAmountPensionAge": ("776", "tums"),  # 1/12 of § 23^5 lg 1 9312
    "unemploymentEmployeeRate": ("0.016", "vv-78-2025"),
    "unemploymentEmployerRate": ("0.008", "vv-78-2025"),
    "socialTaxRate": ("0.33", "sms"),
    "socialTaxMonthlyBase": ("886", "rea-2026"),
    "fundedPension2": ("0.02", "kops"),
    "fundedPension4": ("0.04", "kops"),
    "fundedPension6": ("0.06", "kops"),
}


def cent(x):
    return x.quantize(D("0.01"), rounding=ROUND_HALF_UP)


def expected_lines(c):
    """The amounts the texts give for a test case, computed directly (no engine)."""
    taxable_pay = D(c["grossEarnings"]) - D(c.get("reductions", "0"))
    # EMTA TSD example 10: the employer's sickness benefit is taxed with income tax only
    wage = taxable_pay - D(c.get("leavePay", {}).get("SICK", "0"))
    emp = c.get("employee", {})
    leave = {k: int(v) for k, v in c.get("leaveDays", {}).items()}
    lines = []

    def add(step, side, amount):
        if amount != 0:
            lines.append({"step": step, "side": side, "amount": str(amount)})
        return amount

    if taxable_pay <= 0 and not leave:
        return lines
    wage = max(wage, D(0))
    ui_ee = cent(wage * D("0.016")) if emp.get("unemploymentInsuranceEnded") != "true" else D(0)  # TKindlS § 42 lg 1 p 1
    add("unemploymentInsurance", "EMPLOYEE", ui_ee)
    add("unemploymentInsurance", "EMPLOYER", cent(wage * D("0.008")))  # § 40 lg 1: also for pension-age employees
    rate = {"NONE": D(0), "2": D("0.02"), "4": D("0.04"), "6": D("0.06")}[emp["fundedPensionRate"]]
    pension = add("fundedPension", "EMPLOYEE", cent(wage * rate))  # KoPS § 9
    # SMS § 2 lg 2-4: at least the monthly base, unless exempt or another employer applies the tax-free amount;
    # EMTA: not when away the whole month on sick leave or leave other than unpaid leave by agreement
    month_days = int(c["periodEnd"][8:10])
    away = leave.get("SICK", 0) + leave.get("MATERNITY", 0) + leave.get("VACATION", 0) >= month_days
    applies = (emp.get("socialTaxMinimumExempt") != "true" and not away
               and (emp.get("otherEmployment") != "true" or D(emp.get("taxFreeAmount", "0")) > 0))
    floor = D(emp["socialTaxMinimumBase"]) if "socialTaxMinimumBase" in emp else D(886)
    base = max(wage, floor) if applies else wage
    if wage > 0 or (applies and c.get("employedThroughout", True) and leave):
        add("socialTax", "EMPLOYER", cent(base * D("0.33")))
    # TuMS § 42 lg 1, 5, 6: contributions and the tax-free amount (up to the payment) come off before the 22%
    before = taxable_pay - ui_ee - pension
    taxable = max(before - D(emp.get("taxFreeAmount", "0")), D(0))
    add("incomeTax", "TAX", cent(taxable * D("0.22")))
    return lines


def case(name, evidence, gross, employee, start="2026-03-01", end="2026-03-31", payment=None, whole=True, error=None,
         pre_tax=None, reductions=None, leave_pay=None, leave_days=None):
    c = {"name": name, "evidence": evidence, "periodStart": start, "periodEnd": end, "frequency": "MONTHLY"}
    if payment:
        c["paymentDate"] = payment
    c.update({"grossEarnings": gross, "fullPeriod": whole})
    if not whole:
        c["employedThroughout"] = False
    c.update({"employer": {}, "employee": employee})
    if pre_tax:
        c["preTaxDeductions"] = pre_tax
    if reductions:
        c["reductions"] = reductions
        c["fullPeriod"] = False
    if leave_pay:
        c["leavePay"] = leave_pay
        c["fullPeriod"] = False
    if leave_days:
        c["leaveDays"] = leave_days
    if error:
        c["expectError"] = error
    return c


STD = {"taxFreeAmount": "700", "fundedPensionRate": "2"}


def test_cases():
    law = "TuMS § 4, 23, 23^5, 42; SMS § 2, 7; 2026 State Budget Act (886); VV määrus nr 78/2025 (1.6% / 0.8%); KoPS § 9; amounts from build_template.py expected_lines()"
    computed = [
        case("2,000 EUR with the tax-free amount and 2% pension", "UI 32.00, pension 40.00, (2000 - 32 - 40 - 700) x 22% = 270.16; social tax 660.00, employer UI 16.00. " + law,
             "2000.00", dict(STD)),
        case("minimum wage 946 EUR from April", "Minimum wage 946 EUR from 1 April 2026 (EMTA); above the 886 EUR base, so no top-up. " + law,
             "946.00", dict(STD), start="2026-05-01", end="2026-05-31"),
        case("part-time 600 EUR: social tax on the 886 EUR minimum", "SMS § 2 lg 2: 886 x 33% = 292.38; the tax-free amount covers the rest of the pay. " + law,
             "600.00", dict(STD)),
        case("part-time parent of a child under 3: no minimum", "SMS § 2 lg 4 p 3: social tax on the pay only, 600 x 33% = 198.00. " + law,
             "600.00", {**STD, "socialTaxMinimumExempt": "true"}),
        case("second employer without the tax-free application: no minimum", "SMS § 2 lg 2^1: the minimum falls on the employer applying the tax-free amount; tax from the first euro (TuMS § 42 lg 2). " + law,
             "600.00", {"fundedPensionRate": "2", "otherEmployment": "true"}),
        case("employer applying the tax-free amount pays the minimum despite another job", "SMS § 2 lg 2^1. " + law,
             "600.00", {**STD, "otherEmployment": "true"}),
        case("no application, not in the funded pension", "TuMS § 42 lg 1: without a written application no tax-free amount; (1500 - 24) x 22% = 324.72. " + law,
             "1500.00", {"fundedPensionRate": "NONE"}),
        case("6% funded pension chosen", "KoPS § 9 lg 2: 3000 x 6% = 180.00 before tax. " + law, "3000.00", {"taxFreeAmount": "700", "fundedPensionRate": "6"}),
        case("4% funded pension and a lower tax-free amount", "KoPS § 9 lg 2; EMTA: the employee may ask for less than 700 EUR, e.g. 500 EUR. " + law,
             "2500.00", {"taxFreeAmount": "500", "fundedPensionRate": "4"}),
        case("pension age: 776 EUR tax-free, no employee UI", "TuMS § 23^5, § 42 lg 1^3 (the pension uses none here); TKindlS § 42 lg 1 p 1: only the employer's 0.8%. " + law,
             "1500.00", {"taxFreeAmount": "776", "pensionAge": "true", "unemploymentInsuranceEnded": "true", "fundedPensionRate": "NONE", "socialTaxMinimumExempt": "true"}),
        case("pay below the tax-free amount: no tax", "EMTA: the tax-free amount is applied only up to the payment. " + law,
             "500.00", {**STD, "socialTaxMinimumExempt": "true"}),
        case("joined on 10 January: prorated minimum entered", "SMS § 2 lg 3 p 3; määrus nr 17 § 6 lg 2: 22 calendar days / 30 x 886 = 649.73; 649.73 x 33% = 214.41. " + law,
             "400.00", {**STD, "socialTaxMinimumBase": "649.73"}, start="2026-01-01", end="2026-01-31", whole=False),
        case("second job with the other employer's pay certified", "SMS § 2 lg 2^2; määrus nr 17 § 5: 886 - 500 paid by the other employer = 386, but not less than this employer's 450, so 450 x 33% = 148.50. " + law,
             "450.00", {**STD, "otherEmployment": "true", "socialTaxMinimumBase": "386.00"}),
        case("December 2025 salary paid in January 2026", "EMTA: taxation follows the payment date, so 2026 rates and the 700 EUR tax-free amount apply. " + law,
             "1800.00", dict(STD), start="2025-12-01", end="2025-12-31", payment="2026-01-09"),
        case("EMTA example 10: employer sick pay taxed with income tax only", "EMTA TSD example 10: wage 1000 (social tax 330, UI 16 / 8) and sickness benefit 72 taxed only with income tax; 62.48 + 15.84 = 78.32. " + law,
             "1072.00", {"taxFreeAmount": "700", "fundedPensionRate": "NONE"}, start="2026-06-01", end="2026-06-30",
             leave_pay={"SICK": "72.00"}, leave_days={"SICK": "16"}),
        case("part of the month sick: the minimum applies in full", "EMTA: the minimum is excused only for a whole month away; 886 x 33% = 292.38. " + law,
             "1072.00", {"taxFreeAmount": "700", "fundedPensionRate": "NONE"}, start="2026-06-01", end="2026-06-30",
             reductions="300.00", leave_pay={"SICK": "72.00"}, leave_days={"SICK": "16"}),
        case("a whole month on sick leave: no minimum", "EMTA: no minimum when temporarily incapacitated for work the whole month; the employer's sickness benefit is taxed with income tax only. " + law,
             "2000.00", {"fundedPensionRate": "NONE"}, start="2026-06-01", end="2026-06-30",
             reductions="1900.00", leave_pay={"SICK": "100.00"}, leave_days={"SICK": "30"}),
        case("a whole month of agreed unpaid leave owes the minimum", "SMS § 2 lg 3; EMTA: unpaid leave by agreement does not excuse the minimum; 886 x 33% = 292.38. " + law,
             "2000.00", dict(STD), start="2026-06-01", end="2026-06-30", reductions="2000.00", leave_days={"UNPAID": "30"}),
        case("a whole month of maternity leave: no minimum", "EMTA: leave including maternity leave for the whole month excuses the minimum. " + law,
             "2000.00", dict(STD), reductions="2000.00", leave_days={"MATERNITY": "31"}),
        case("pay kept for maternity leave days is wage", "SMS § 2: the employer's pay is subject to social tax unless exempt (SMS § 3). " + law,
             "2000.00", dict(STD), leave_pay={"MATERNITY": "500.00"}, leave_days={"MATERNITY": "10"}),
    ]
    for c in computed:
        c["expectLines"] = expected_lines(c)
    zero = case("no pay, no lines", "nothing is paid, so nothing is due", "0", dict(STD))
    zero["expectLines"] = []
    errors = [
        case("missing funded-pension answer", "the employer checks the employee's funded-pension status (KoPS § 11 lg 1 p 1)", "2000.00", {"taxFreeAmount": "700"},
             error="fundedPensionRate"),
        case("776 EUR tax-free amount before pension age", "TuMS § 42 lg 1: at most 1/12 of 8400 = 700 EUR", "2000.00", {"taxFreeAmount": "776", "fundedPensionRate": "2"},
             error="700"),
        case("partial month below the minimum without a prorated base", "SMS § 2 lg 3: the minimum is prorated by the time worked", "400.00", dict(STD),
             start="2026-01-01", end="2026-01-31", whole=False, error="socialTaxMinimumBase"),
        case("state-paid social tax is refused", "SMS § 6 lg 1 p 5 relief not modelled", "1000.00", {**STD, "stateSocialTaxRelief": "true"},
             error="not supported"),
        case("pre-tax deductions are refused", "III pillar and other deductions not modelled", "2000.00", dict(STD), pre_tax="50.00",
             error="Pre-tax deductions are not supported"),
        case("2027 is not covered", "2027 values not yet set", "2000.00", dict(STD), start="2027-01-01", end="2027-01-31",
             error="a newer template version is needed"),
    ]
    return computed + [zero] + errors


def dated(value, source):
    return [{"from": FROM, "to": TO, "value": value, "source": source}]



# Statutory leave pay (PayrollTemplate.leavePay): applied as a leave pay rule where the workspace has none of its own.
LEAVE_SOURCES = {'tooelu-sick-pay': {'title': "Labour Inspectorate (Tööinspektsioon), tooelu.ee 'Other remuneration and benefits': "
                              "'For days 4 to 8 of sickness, sickness benefit is paid by the employer', '70% of the "
                              "employee's average wages for the last six months'; Tervisekassa (Health Insurance "
                              "Fund), 'Benefit for temporary incapacity for work': 'EHIF pays from day 9, the benefit "
                              "rate is 70%'",
                     'url': 'https://www.tooelu.ee/en/174/other-remuneration-and-benefits',
                     'retrieved': '2026-10-26'}}

LEAVE_PAY = [{'leaveType': 'SICK',
  'deductionPercentage': '100',
  'payBands': [{'fromDay': 1, 'toDay': 3, 'deductionPercentage': '100'},
               {'fromDay': 4, 'toDay': 8, 'deductionPercentage': '30'}],
  'source': 'tooelu-sick-pay',
  'note': 'Days 1-3 unpaid; days 4-8 the employer pays 70% of the average wage of the last six months (the rule pays '
          '70% of the salary); from day 9 Tervisekassa pays, outside payroll.'}]

LEAVE_CHANGE = {'version': '2026.1.2',
 'date': '2026-10-26',
 'summary': 'Statutory sick pay as a leave pay rule: days 1-3 unpaid, days 4-8 at 70% paid by the employer, from day 9 '
            'unpaid as Tervisekassa pays (Health Insurance Act; Labour Inspectorate); applied where the workspace has '
            'no sick leave rule of its own. Before, sick days were paid in full unless a rule was added.'}


def main():
    template = {
        "schemaVersion": 1,
        "id": "ee-employment",
        "name": "Estonia — employment (tööleping)",
        "version": "2026.1.2",
        "status": "LAW_DERIVED",
        "jurisdiction": {"country": "EE"},
        "validity": {"from": FROM, "to": TO},
        "currency": "EUR",
        "payFrequencies": ["MONTHLY"],
        "taxYearStart": "--01-01",
        "dateBasis": "PAYMENT_DATE",
        "rounding": {"scale": 2, "mode": "HALF_UP"},
        "changelog": [
            LEAVE_CHANGE,
            {"version": "2026.1.1", "date": LEAVE_RETRIEVED,
             "summary": "Leave pay rules: pay kept for sick days is the employer's sickness benefit, taxed with income tax only (EMTA, payment type 24); before, it was also charged social tax, unemployment insurance and funded pension. No social tax minimum for a month away on sick leave or leave other than unpaid leave by agreement (EMTA); a month of agreed unpaid leave without pay owes social tax on the minimum, which it did not before."},
            {"version": "2026.1.0", "date": RETRIEVED,
             "summary": "2026: income tax 22% with the flat 700 EUR monthly tax-free amount (776 EUR at pension age); unemployment insurance 1.6% / 0.8%; funded pension 2%, 4% or 6%; social tax 33% with the 886 EUR monthly minimum."}
        ],
        "sources": {**SOURCES, **LEAVE_SOURCES},
        "inputs": {
            "employer": [],
            "employee": [
                {"key": "taxFreeAmount", "type": "DECIMAL", "required": False, "defaultValue": "0", "min": 0, "max": 776,
                 "description": "Monthly tax-free amount from the employee's written application (TuMS § 42 lg 1): up to 700 EUR (0 without an application; one employer only, lg 2). From the year of reaching old-age pension age up to 776 EUR less what the pension already uses (§ 23^5, § 42 lg 1^3)"},
                {"key": "pensionAge", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "The employee reaches or has reached old-age pension age in this tax year (TuMS § 23^5): the tax-free amount may be up to 776 EUR"},
                {"key": "unemploymentInsuranceEnded", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "No employee unemployment insurance: from the month after reaching old-age pension age or being granted an early or flexible old-age pension (TKindlS § 42 lg 1 p 1); the employer's 0.8% remains"},
                {"key": "fundedPensionRate", "type": "ENUM", "required": True, "values": ["NONE", "2", "4", "6"],
                 "description": "Mandatory funded pension (II pillar) contribution: 2%, or 4% or 6% if chosen (KoPS § 9); NONE if the employee is not a contributor"},
                {"key": "otherEmployment", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "The employee also works for another employer: the social tax minimum is then paid only by the employer applying the tax-free amount (SMS § 2 lg 2^1)"},
                {"key": "socialTaxMinimumExempt", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "The social tax minimum does not apply (SMS § 2 lg 4): shortened working time under TLS § 43 lg 4 or 6, parent of a child under 3 or of three or more children under 19, RaKS § 5 lg 4 p 5, within 12 months of hiring after at least six months registered unemployed, state pension recipient or partial/no work ability, local council member"},
                {"key": "socialTaxMinimumBase", "type": "DECIMAL", "required": False, "min": 0,
                 "description": "Social tax minimum base in EUR replacing 886: for a month in which the employee started or left, or refused work under TLS § 19, 886 x calendar days worked / 30 (SMS § 2 lg 3; määrus nr 17 § 6); with another employer's pay certified, 886 less that pay (SMS § 2 lg 2^2; määrus nr 17 § 5)"},
                {"key": "stateSocialTaxRelief", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "The state pays social tax for this employee with partial or no work ability on the employer's application (SMS § 6 lg 1 p 5) — not supported"},
            ],
        },
        "parameters": {name: dated(value, src) for name, (value, src) in PARAMS.items()},
        "tables": {},
        "steps": [
            {"id": "calendarMonth", "type": "REQUIRE", "condition": "#isCalendarMonth",
             "message": "Estonian payroll is monthly: the pay period must be a whole calendar month"},
            {"id": "noPreTaxDeductions", "type": "REQUIRE", "condition": "#preTaxDeductions == 0",
             "message": "Pre-tax deductions are not supported by this template"},
            {"id": "noStateSocialTax", "type": "REQUIRE", "source": "sms", "condition": "!#employee['stateSocialTaxRelief']",
             "message": "Employees for whom the state pays social tax (SMS § 6 lg 1 p 5) are not supported by this template"},
            {"id": "taxFreeLimit", "type": "REQUIRE", "source": "tums",
             "condition": "#employee['taxFreeAmount'] <= (#employee['pensionAge'] ? #p['monthlyTaxFreeAmountPensionAge'] : #p['monthlyTaxFreeAmount'])",
             "message": "The monthly tax-free amount is at most 700 EUR (TuMS § 42 lg 1), or 776 EUR from the year of reaching old-age pension age (§ 23^5)"},
            {"id": "wage", "type": "VALUE", "source": "emta-sick-pay", "expr": "#pos(#socialEarnings - #reductions - #leavePay['SICK'])"},
            {"id": "taxablePay", "type": "VALUE", "expr": "#pos(#taxableEarnings - #reductions)"},
            {"id": "unemploymentInsurance", "type": "CONTRIBUTION", "label": "Unemployment insurance (töötuskindlustusmakse)", "ledger": "UNEMPLOYMENT", "source": "vv-78-2025",
             "when": "#wage > 0", "base": "#wage",
             "employee": {"rate": "#employee['unemploymentInsuranceEnded'] ? 0 : #p['unemploymentEmployeeRate']"},
             "employer": {"rate": "#p['unemploymentEmployerRate']"}},
            {"id": "fundedPension", "type": "CONTRIBUTION", "label": "Mandatory funded pension (II pillar)", "ledger": "PENSION_CONTRIBUTION", "source": "kops",
             "when": "#wage > 0 and #employee['fundedPensionRate'] != 'NONE'", "base": "#wage",
             "employee": {"rate": "#employee['fundedPensionRate'] == '2' ? #p['fundedPension2'] : (#employee['fundedPensionRate'] == '4' ? #p['fundedPension4'] : #p['fundedPension6'])"}},
            {"id": "awayWholeMonth", "type": "VALUE", "source": "emta-social-tax",
             "expr": "#leaveDays['SICK'] + #leaveDays['MATERNITY'] + #leaveDays['VACATION'] >= #periodEnd.getDayOfMonth() ? 1 : 0"},
            {"id": "minimumApplies", "type": "VALUE", "source": "sms",
             "expr": "!#employee['socialTaxMinimumExempt'] and #awayWholeMonth == 0 and (!#employee['otherEmployment'] or #employee['taxFreeAmount'] > 0) ? 1 : 0"},
            {"id": "partialMonthMinimum", "type": "REQUIRE", "source": "rm-17-2013",
             "when": "#minimumApplies == 1 and #employee['socialTaxMinimumBase'] == null and #wage > 0 and #wage < #p['socialTaxMonthlyBase']",
             "condition": "#employedThroughout",
             "message": "The employee started or left during the month: the social tax minimum is prorated by calendar days worked / 30 (SMS § 2 lg 3; määrus nr 17 § 6), so set the employee's socialTaxMinimumBase"},
            {"id": "socialTaxBase", "type": "VALUE", "source": "rea-2026",
             "expr": "#minimumApplies == 1 ? #max(#wage, #coalesce(#employee['socialTaxMinimumBase'], #p['socialTaxMonthlyBase'])) : #wage"},
            {"id": "socialTax", "type": "CONTRIBUTION", "label": "Social tax (sotsiaalmaks)", "ledger": "SOCIAL_SECURITY", "source": "sms",
             "when": "#wage > 0 or (#minimumApplies == 1 and #employedThroughout and #leaveDays['SICK'] + #leaveDays['MATERNITY'] + #leaveDays['VACATION'] + #leaveDays['UNPAID'] > 0)",
             "base": "#socialTaxBase", "employer": {"rate": "#p['socialTaxRate']"}},
            {"id": "incomeTax", "type": "TAX", "label": "Income tax (tulumaks)", "ledger": "INCOME_TAX", "source": "tums",
             "when": "#taxablePay > 0",
             "base": "#pos(#taxablePay - #unemploymentInsurance_employee - #fundedPension_employee - #employee['taxFreeAmount'])",
             "rate": "#p['incomeTaxRate']"},
        ],
        "leavePay": LEAVE_PAY,
        "limitations": [
            "Employment contracts paid monthly in EUR in 2026, one payment per employee per month; taxation follows the payment date (EMTA: kassapõhine), and the tax-free amount and the social tax minimum are applied to this payment. A second run in the same month would apply them again.",
            "The tax-free amount, pension age, the end of employee unemployment insurance, the funded-pension rate, other employment and the social tax minimum exemptions are entered per employee from the employee's application and the registers; they are not checked.",
            "In a month the employee started or left (or refused work under TLS § 19), the prorated social tax minimum base (886 x calendar days / 30, määrus nr 17 § 6) must be entered; unpaid leave agreed by the parties does not reduce the minimum (SMS § 2 lg 3 p 1). The minimum belongs to the month containing the agreed pay day (määrus nr 17 § 2); pay made earlier or later than agreed (§ 3-4) and the final settlement (§ 7) are not reconciled across months.",
            "Leave pay rules: pay kept for sick days is the employer's sickness benefit, taxed with income tax only (EMTA, payment type 24), assumed within the limit of the average wage; pay kept for other leave is wage. The social tax minimum is not applied for a month away on sick, maternity or other paid leave with a leave pay rule (EMTA), and applies in full for part of a month away and for unpaid leave by agreement (SMS § 2 lg 3). Leave of a type without a leave pay rule is not seen.",
            "Not modelled: the state-paid social tax for employees with partial or no work ability (SMS § 6 lg 1 p 5, refused), fringe benefits (erisoodustused), III pillar contributions paid by the employer, non-residents, seafarers and board members' fees, and other payments exempt from social tax (SMS § 3).",
            "Contributions and the tax are rounded to the cent, half up, per line (TKindlS § 42 lg 3; EMTA examples).",
            "Status LAW_DERIVED: values are cited from the consolidated acts, the 2026 State Budget Act and Government regulation nr 78/2025; the cases are derived from them.",
        ],
        "testCases": test_cases(),
    }
    with open(sys.argv[1], "w", encoding="utf-8") as f:
        json.dump(template, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("wrote", sys.argv[1], "with", len(template["testCases"]), "test cases")


main()
