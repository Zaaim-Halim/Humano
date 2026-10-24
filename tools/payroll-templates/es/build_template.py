"""Build src/main/resources/payroll-templates/es/es-employment.json.

Spain (common tax territory), employees in the Régimen General de la Seguridad Social paid monthly in
EUR, 2026. Every rule below cites the text it comes from: the income tax withholding (retención a
cuenta del IRPF) follows the Reglamento del IRPF (arts. 80-88) as the Agencia Tributaria's published
calculation algorithm implements it, and the social security contributions follow the Orden
PJC/297/2026 and the LGSS. The test cases' expected amounts are computed by expected_lines() — an
implementation written directly from those texts (following the algorithm's own loops), independent of
the Java engine; the engine must reproduce them exactly.

Usage: python3 -I build_template.py ../../../src/main/resources/payroll-templates/es/es-employment.json
"""
import json
import sys
from decimal import ROUND_DOWN, ROUND_HALF_UP, Decimal as D

RETRIEVED = "2026-10-24"
FROM, TO = "2026-01-01", "2026-12-31"
BOE = "https://www.boe.es/buscar/act.php?id="
AEAT = "https://sede.agenciatributaria.gob.es/static_files/"

SOURCES = {
    "lirpf": {
        "title": "Ley 35/2006 del IRPF, consolidated text (última actualización publicada el 07/10/2026) — art. 20 (reducción por obtención de rendimientos del trabajo), arts. 56-61 (mínimo del contribuyente 5.550, +1.150 a partir de 65 años, +1.400 a partir de 75; descendientes 2.400 / 2.700 / 4.000 / 4.500, +2.800 menor de tres años; ascendientes 1.150, +1.400 mayor de 75, prorrateo entre descendientes con quienes conviva; discapacidad 3.000 / 9.000 y gastos de asistencia 3.000), art. 101.1 (escala de retención)",
        "url": BOE + "BOE-A-2006-20764",
    },
    "rirpf": {
        "title": "Reglamento del IRPF, Real Decreto 439/2007, consolidated text (última actualización publicada el 07/10/2026) — art. 78 (nace la obligación de retener cuando se satisfacen o abonan las rentas), art. 81 (límite excluyente de la obligación de retener: cuadro por situación y número de descendientes, según RD 142/2024), art. 82 (cálculo de la retención), art. 83 (cuantía total de las retribuciones y base: cotizaciones, otros gastos 2.000 / +2.000 movilidad / +3.500 o 7.750 discapacidad, reducción del art. 20 LIRPF, 600 euros con más de dos descendientes), art. 84 (mínimo personal y familiar), art. 85 (cuota: escala sobre la base menos escala sobre el mínimo; límite del 43% para retribuciones no superiores a 35.200), art. 86 (tipo con dos decimales; menos dos enteros por préstamos de vivienda habitual si las retribuciones son inferiores a 33.007,2; mínimos del 2% para contratos de duración inferior al año y del 15% para relaciones laborales especiales), art. 87 (regularización), art. 88.5 (tipo superior a solicitud del perceptor)",
        "url": BOE + "BOE-A-2007-6820",
    },
    "aeat-algoritmo-2026": {
        "title": "Agencia Tributaria, Departamento de Informática Tributaria, 'Algoritmo de cálculo del tipo de retención a cuenta del IRPF para los rendimientos del trabajo personal, ejercicio 2026 (a partir de 10 de septiembre)', 10-09-2026 (SHA-256 b1a120d93bf541de…) — the variables, validations, rounding (REDONDEAR1, TRUNCAR) and steps the template follows; TABLA 1 (límites excluyentes) and TABLA 2 (escala de retención). The only change from the 26-12-2025 version is the La Palma extension (Real Decreto-ley 23/2026)",
        "url": AEAT + "Sede/Programas_ayuda/Retenciones/2026/Algoritmo%20Retenciones-2026_10sept.pdf",
    },
    "aeat-ayuda-2026": {
        "title": "Agencia Tributaria, 'Las Retenciones e Ingresos a Cuenta del IRPF sobre los Rendimientos del Trabajo en el ejercicio 2026 (Desde 1 de Enero a 9 de Septiembre)' (SHA-256 33f2e86c8245a896…) — p. 12: no hay obligación de retener por debajo del art. 81.1 'siempre que no se trate de rendimientos que tengan reglamentariamente asignado un tipo especial de retención, ya sea éste un tipo fijo o un tipo mínimo'; p. 14: retribuciones sin los atrasos de ejercicios anteriores; CUADRO 1, CUADRO 5, CUADRO 6; p. 56: ejemplo 28.500 euros con cotizaciones de 1.700, tipo 15,96%",
        "url": AEAT + "common/internet/dep/aplicaciones/modelos/rw/retenciones2026.pdf",
    },
    "orden-pjc-297-2026": {
        "title": "Orden PJC/297/2026, de 30 de marzo, BOE núm. 79 de 31/03/2026 (BOE-A-2026-7296) — art. 1 (base: remuneración devengada en el mes más la parte proporcional de las pagas de vencimiento superior al mensual, importe anual dividido por 12; entre la base mínima y máxima del grupo), art. 2 (tope máximo 5.101,20; tope mínimo AT/EP 1.424,40), art. 3 (bases mínimas por grupo: 1.989,30 / 1.649,70 / 1.435,20 / 1.424,40; grupos 8-11 por día), art. 4 (contingencias comunes 28,30%: 23,60 empresa, 4,70 trabajador; AT/EP según la tarifa de la DA 61ª LGSS, a cargo de la empresa), art. 5 (horas extraordinarias), art. 7 (alta sin remuneración), art. 10 (pluriempleo), art. 16 (MEI 0,90%: 0,75 / 0,15), art. 17 (solidaridad 1,15% / 1,25% / 1,46% por tramos sobre 5.101,20), art. 28 (33,62 euros al finalizar contratos inferiores a 30 días), art. 33 (desempleo 7,05% indefinidos / 8,30% duración determinada; FOGASA 0,20%; formación profesional 0,70%: 0,60 / 0,10; base la de AT/EP), arts. 38-39 (tiempo parcial: bases mínimas por hora 11,98 / 9,94 / 8,65 / 8,58; AT/EP 8,58 por hora), art. 46 (contratos formativos)",
        "url": BOE + "BOE-A-2026-7296",
    },
    "lgss": {
        "title": "Texto refundido de la Ley General de la Seguridad Social, Real Decreto Legislativo 8/2015, consolidated text (última actualización publicada el 21/10/2026) — art. 19 bis (cotización adicional de solidaridad), art. 127 bis (mecanismo de equidad intergeneracional), art. 147 (base de cotización: las percepciones de vencimiento superior al mensual se prorratean a lo largo de los doce meses; 147.2.e: las horas extraordinarias solo computan en la base de AT/EP), disposición adicional sexagésima primera (tarifa de primas AT/EP)",
        "url": BOE + "BOE-A-2015-11724",
    },
    "aeat-servicio-calculo": {
        "title": "Agencia Tributaria, Servicio de Módulo de Cálculo de Retenciones (espeSWRET_2026, SHA-256 59fc137bcaa6e7a3…), https://www2.agenciatributaria.gob.es/wlpl/PRET-R200/mc with EJER=2026 and PER=1 (from 10 September 2026), input schema AEATRetenciones2026.xsd (SHA-256 f4acd6c9072c0d18…) — the official calculator; the computed withholding rate of every case was compared with its TipoRetencion on 2026-10-24",
        "url": AEAT + "Sede/Programas_ayuda/Retenciones/2026/espeSWRET_2026.pdf",
    },
    "concierto-pv": {
        "title": "Concierto Económico con la Comunidad Autónoma del País Vasco, Ley 12/2002, consolidated text (última actualización publicada el 30/04/2025) — art. 7.Uno.a): las retenciones sobre rendimientos del trabajo se exigen por la Diputación Foral cuando el trabajo se preste en el País Vasco",
        "url": BOE + "BOE-A-2002-9969",
    },
    "convenio-navarra": {
        "title": "Convenio Económico entre el Estado y la Comunidad Foral de Navarra, Ley 28/1990, consolidated text (última actualización publicada el 25/07/2025) — art. 10.1.a): las retenciones sobre rendimientos del trabajo corresponden a Navarra cuando el trabajo se preste en Navarra",
        "url": BOE + "BOE-A-1990-31117",
    },
}
for s in SOURCES.values():
    s["retrieved"] = RETRIEVED

PARAMS = {
    # Withholding (IRPF): figures as the algorithm states them
    "generalExpenses": ("2000", "rirpf"),  # art. 83.2.b) 2.º
    "mobilityExpenses": ("2000", "rirpf"),
    "disabledWorkerExpenses": ("3500", "rirpf"),
    "disabledWorkerExpensesHigh": ("7750", "rirpf"),
    "red20Lower": ("14852", "lirpf"),  # art. 20 (RDL 4/2024)
    "red20Middle": ("17673.52", "lirpf"),
    "red20Upper": ("19747.50", "lirpf"),
    "red20Max": ("7302", "lirpf"),
    "red20Slope1": ("1.75", "lirpf"),
    "red20MiddleAmount": ("2364.34", "lirpf"),
    "red20Slope2": ("1.14", "lirpf"),
    "largeFamilyReduction": ("600", "rirpf"),  # art. 83.3.e)
    "personalMinimum": ("5550", "lirpf"),
    "age65Minimum": ("1150", "lirpf"),
    "age75Minimum": ("1400", "lirpf"),
    "descendant1": ("2400", "lirpf"),
    "descendant2": ("2700", "lirpf"),
    "descendant3": ("4000", "lirpf"),
    "descendant4": ("4500", "lirpf"),
    "descendantUnder3": ("2800", "lirpf"),
    "ascendantMinimum": ("1150", "lirpf"),
    "ascendant75Minimum": ("1400", "lirpf"),
    "disabilityMinimum": ("3000", "lirpf"),
    "disabilityMinimumHigh": ("9000", "lirpf"),
    "assistanceMinimum": ("3000", "lirpf"),
    "capIncomeLimit": ("35200", "rirpf"),  # art. 85.3
    "capRate": ("0.43", "rirpf"),
    "housingIncomeLimit": ("33007.20", "rirpf"),  # art. 86.1
    "housingReductionRate": ("0.02", "aeat-algoritmo-2026"),
    "minimumRateShortContract": ("2.00", "rirpf"),  # art. 86.2, in percent like the rate
    "minimumRateSpecial": ("15.00", "rirpf"),
    # Social security
    "maximumBase": ("5101.20", "orden-pjc-297-2026"),
    "accidentMinimumBase": ("1424.40", "orden-pjc-297-2026"),
    "partTimeAccidentHourlyBase": ("8.58", "orden-pjc-297-2026"),
    "commonEmployeeRate": ("0.047", "orden-pjc-297-2026"),
    "commonEmployerRate": ("0.236", "orden-pjc-297-2026"),
    "equityEmployeeRate": ("0.0015", "orden-pjc-297-2026"),
    "equityEmployerRate": ("0.0075", "orden-pjc-297-2026"),
    "unemploymentIndefiniteEmployeeRate": ("0.0155", "orden-pjc-297-2026"),
    "unemploymentIndefiniteEmployerRate": ("0.055", "orden-pjc-297-2026"),
    "unemploymentFixedTermEmployeeRate": ("0.016", "orden-pjc-297-2026"),
    "unemploymentFixedTermEmployerRate": ("0.067", "orden-pjc-297-2026"),
    "wageGuaranteeRate": ("0.002", "orden-pjc-297-2026"),
    "trainingEmployeeRate": ("0.001", "orden-pjc-297-2026"),
    "trainingEmployerRate": ("0.006", "orden-pjc-297-2026"),
    "overtimeEmployeeRate": ("0.047", "orden-pjc-297-2026"),
    "overtimeEmployerRate": ("0.236", "orden-pjc-297-2026"),
    "forceMajeureOvertimeEmployeeRate": ("0.02", "orden-pjc-297-2026"),
    "forceMajeureOvertimeEmployerRate": ("0.12", "orden-pjc-297-2026"),
}

MIN_BASES = {1: ("1989.30", "11.98"), 2: ("1649.70", "9.94"), 3: ("1435.20", "8.65"),
             4: ("1424.40", "8.58"), 5: ("1424.40", "8.58"), 6: ("1424.40", "8.58"), 7: ("1424.40", "8.58")}
EXEMPT = {"1": (None, "17644", "18694"), "2": ("17197", "18130", "19262"), "3": ("15876", "16342", "16867")}
SCALE = [("0", "12450", "0.19", "0"), ("12450", "20200", "0.24", "2365.50"), ("20200", "35200", "0.30", "4225.50"),
         ("35200", "60000", "0.37", "8725.50"), ("60000", "300000", "0.45", "17901.50"),
         ("300000", "999999999999", "0.47", "125901.50")]
SOLIDARITY = [("0", "5101.20", "0", "0"), ("5101.20", "5611.32", "0.0019", "0.0096"),
              ("5611.32", "7651.80", "0.0021", "0.0104"), ("7651.80", "999999999999", "0.0024", "0.0122")]


def cent(x):
    return x.quantize(D("0.01"), rounding=ROUND_HALF_UP)


def trunc2(x):
    return x.quantize(D("0.01"), rounding=ROUND_DOWN)


def escala(base):
    """TABLA 2: the cumulative quota of the bracket plus the rest at its rate."""
    for lower, upper, rate, quota in SCALE:
        if base < D(upper):
            return D(quota) + (max(base, D(lower)) - D(lower)) * D(rate)


def solidarity(remuneration, employee):
    total = D(0)
    for lower, upper, ee, er in SOLIDARITY:
        if remuneration > D(lower):
            total += (min(remuneration, D(upper)) - D(lower)) * D(ee if employee else er)
    return total


def estimated_contributions(emp):
    """12 months of the employee's contributions on the annual pay / 12 (full-time)."""
    monthly = cent(D(emp["annualPay"]) / 12)
    group = int(emp["contributionGroup"])
    cc = min(max(monthly, D(MIN_BASES[group][0])), D("5101.20"))
    at = min(max(monthly, D("1424.40")), D("5101.20"))
    unemployment = D("0.016") if emp["unemploymentRate"] == "FIXED_TERM" else D("0.0155")
    month = (cent(cc * D("0.047")) + cent(cc * D("0.0015")) + cent(at * unemployment) + cent(at * D("0.001"))
             + cent(solidarity(monthly, True)))
    return 12 * month


def withholding_rate(emp, birth_year, year=2026):
    """The AEAT algorithm, step by step (RIRPF arts. 81-86)."""
    retrib = D(emp["annualPay"])
    cotizaciones = D(emp["annualSocialContributions"]) if "annualSocialContributions" in emp else estimated_contributions(emp)
    numdes = int(emp.get("descendants", "0"))
    under3 = int(emp.get("descendantsUnder3", "0"))
    entero = D("0.5") if emp.get("descendantsComputation") == "HALF" else D(1)
    disability = emp.get("disability", "NONE")
    # OTROS GASTOS, capped at RETRIB - COTIZACIONES
    otros = D(2000) + (D(2000) if emp.get("geographicMobility") == "true" else D(0))
    if disability == "FROM_65" or disability == "FROM_33_REDUCED_MOBILITY":
        otros += D(7750)
    elif disability == "FROM_33":
        otros += D(3500)
    if retrib - cotizaciones < 0:
        otros = D(0)
    if otros > retrib - cotizaciones:
        otros = retrib - cotizaciones
    rnt = max(retrib - cotizaciones, D(0))
    if rnt <= D(14852):
        red20 = D(7302)
    elif rnt <= D("17673.52"):
        red20 = D(7302) - D("1.75") * (rnt - D(14852))
    elif rnt < D("19747.50"):
        red20 = D("2364.34") - D("1.14") * (rnt - D("17673.52"))
    else:
        red20 = D(0)
    red20 = cent(red20)
    rntredu = max(rnt - otros - red20, D(0))
    redu = D(600) if numdes > 2 else D(0)
    base = rntredu - redu if rntredu > redu else D(0)
    # MÍNIMO PERSONAL Y FAMILIAR
    age = year - birth_year
    mincon = D(5550) + (D(1150) if age > 64 else D(0)) + (D(1400) if age > 74 else D(0))
    mindesg = D(0)
    for i in range(1, numdes + 1):
        mindesg += {1: D(2400), 2: D(2700), 3: D(4000)}.get(i, D(4500)) * entero
    mindes3 = sum((D(2800) * entero for _ in range(under3)), D(0))
    mindes = cent(mindesg) + cent(mindes3)
    conv = D(emp.get("ascendantsSharedBy", "1"))
    n65, n75 = int(emp.get("ascendants65", "0")), int(emp.get("ascendants75", "0"))
    as65 = sum((D(1150) / conv for _ in range(n65 + n75)), D(0))
    as75 = sum((D(1400) / conv for _ in range(n75)), D(0))
    minas = cent(as65) + cent(as75)
    disper = {"FROM_65": D(9000), "FROM_33": D(3000), "FROM_33_REDUCED_MOBILITY": D(3000)}.get(disability, D(0))
    asisper = D(3000) if disability in ("FROM_65", "FROM_33_REDUCED_MOBILITY") else D(0)
    minperfa = mincon + mindes + minas + disper + asisper
    # TABLA 1
    col = 2 if numdes >= 2 else numdes
    limit = D(EXEMPT[emp["familySituation"]][col])
    exempt = retrib <= limit
    if exempt:
        cuota = D(0)
    else:
        c1, c2 = escala(base), escala(minperfa)
        cuota = c1 - c2 if c1 > c2 else D(0)
        if retrib <= D(35200):
            cuota = min(cuota, (retrib - limit) * D("0.43"))
    minopago = trunc2(D("0.02") * retrib) if emp.get("housingLoan") == "true" and retrib < D("33007.20") else D(0)
    dif = max(cuota - minopago, D(0))
    tipo = trunc2(dif / retrib * 100)
    contract = emp["withholdingContract"]
    if contract == "SPECIAL_RELATIONSHIP" and tipo < D(15):
        tipo = D("15.00")
    elif contract == "UNDER_ONE_YEAR" and tipo < D(2):
        tipo = D("2.00")
    return tipo


def applied_rate(emp, birth_year):
    tipo = withholding_rate(emp, birth_year)
    reason = emp.get("withholdingRateOverrideReason", "NONE")
    if reason == "REGULARIZED":
        return D(emp["withholdingRateOverride"])
    if reason == "REQUESTED_HIGHER":
        return max(tipo, D(emp["withholdingRateOverride"]))
    return tipo


def expected_lines(c):
    """The amounts the texts give for a test case, computed directly (no engine)."""
    emp = c["employee"]
    er = c["employer"]
    by = c.get("earningsByComponent", {"BASIC": c["grossEarnings"]})
    gross = D(c["grossEarnings"])
    lines = []

    def add(step, side, amount, keep_zero=False):
        if amount != 0 or keep_zero:
            lines.append({"step": step, "side": side, "amount": str(amount)})

    # Social security: monthly pay (without the extra payments) plus 1/12 of the annual extra payments
    overtime = D(by.get("OT", "0"))
    pay = gross - D(by.get("BONUS", "0")) - overtime
    if pay > 0:
        rem = pay + cent(D(emp.get("annualExtraPay", "0")) / 12)
        group = int(emp["contributionGroup"])
        if "partTimeHours" in emp:
            cc_min = cent(D(emp["partTimeHours"]) * D(MIN_BASES[group][1]))
            at_min = cent(D(emp["partTimeHours"]) * D("8.58"))
        else:
            cc_min, at_min = D(MIN_BASES[group][0]), D("1424.40")
        cc = min(max(rem, cc_min), D("5101.20"))
        at = min(max(rem + overtime, at_min), D("5101.20"))  # overtime only in the AT/EP base (LGSS art. 147.2.e)
        add("commonContingencies", "EMPLOYEE", cent(cc * D("0.047")))
        add("commonContingencies", "EMPLOYER", cent(cc * D("0.236")))
        add("intergenerationalEquity", "EMPLOYEE", cent(cc * D("0.0015")))
        add("intergenerationalEquity", "EMPLOYER", cent(cc * D("0.0075")))
        fixed = emp["unemploymentRate"] == "FIXED_TERM"
        add("unemployment", "EMPLOYEE", cent(at * (D("0.016") if fixed else D("0.0155"))))
        add("unemployment", "EMPLOYER", cent(at * (D("0.067") if fixed else D("0.055"))))
        add("wageGuaranteeFund", "EMPLOYER", cent(at * D("0.002")))
        add("vocationalTraining", "EMPLOYEE", cent(at * D("0.001")))
        add("vocationalTraining", "EMPLOYER", cent(at * D("0.006")))
        add("workAccident", "EMPLOYER", cent(at * D(emp.get("accidentRate", er["accidentRate"]))))
        if rem > D("5101.20"):
            add("solidarity", "EMPLOYEE", cent(solidarity(rem, True)))
            add("solidarity", "EMPLOYER", cent(solidarity(rem, False)))
        if overtime > 0:  # Orden art. 5: 14% (12 / 2) for force majeure, otherwise 28.30% (23.60 / 4.70)
            fm = emp.get("overtimeForceMajeure") == "true"
            add("overtime", "EMPLOYEE", cent(overtime * (D("0.02") if fm else D("0.047"))))
            add("overtime", "EMPLOYER", cent(overtime * (D("0.12") if fm else D("0.236"))))
    # Withholding: the payment times the rate, to the cent; recorded even at 0%
    if gross > 0:
        rate = applied_rate(emp, int(c["birthDate"][:4]))
        add("incomeTax", "TAX", cent(gross * rate / 100), keep_zero=True)
    return lines


def case(name, evidence, gross, employee, start="2026-03-01", end="2026-03-31", payment=None, whole=True,
         error=None, by_component=None, birth="1985-06-15", employer=None, ytd=None, pre_tax=None,
         reductions=None, fte=None):
    c = {"name": name, "evidence": evidence, "periodStart": start, "periodEnd": end, "frequency": "MONTHLY"}
    if payment:
        c["paymentDate"] = payment
    c["grossEarnings"] = gross
    if by_component:
        c["earningsByComponent"] = by_component
    if reductions:
        c["reductions"] = reductions
    if pre_tax:
        c["preTaxDeductions"] = pre_tax
    c["fullPeriod"] = whole and fte is None
    if not whole:
        c["employedThroughout"] = False
    if birth:
        c["birthDate"] = birth
    c.update({"employer": employer if employer is not None else {"accidentRate": "0.015"}, "employee": employee})
    if ytd:
        c["ytd"] = ytd
    if error:
        c["expectError"] = error
    return c


def std(**kw):
    e = {"annualPay": "28500.00", "familySituation": "3", "withholdingContract": "GENERAL", "contributionGroup": "5",
         "unemploymentRate": "INDEFINITE", "taxTerritory": "COMMON"}
    e.update(kw)
    return e


def test_cases():
    law = "RIRPF arts. 81-86 as the AEAT 2026 algorithm implements them, computed rate matching the AEAT calculation service (aeat-servicio-calculo); Orden PJC/297/2026 arts. 1-5, 16, 17, 33; amounts from build_template.py expected_lines()"
    fam = dict(annualPay="28000.00", annualExtraPay="4000.00", familySituation="2", descendants="2", descendantsUnder3="1", descendantsComputation="WHOLE")
    computed = [
        case("AEAT example: 28,500 EUR with 1,700 EUR contributions gives 15.96%",
             "AEAT Retenciones 2026 help, p. 56: 28,500 gross, about 1,700 contributions, rate 15.96%; the example gives no family data, so no dependants, under 65 and situation 3 are assumed (situation 2 gives the same rate). 2,375 x 15.96% = 379.05. " + law,
             "2375.00", std(annualSocialContributions="1700.00"), birth="1980-02-01"),
        case("14 payments with the extra pays prorated for social security",
             "Orden art. 1 and LGSS art. 147: 2,000 + 4,000 / 12 = 2,333.33 is the base; situation 2 with two children, one born in 2025; contributions estimated from 28,000 / 12. " + law,
             "2000.00", std(**fam)),
        case("extra pay paid with June salary: base unchanged, tax on both",
             "The extra pay is already in the monthly base (LGSS art. 147); withholding applies to the whole payment (RIRPF art. 82). " + law,
             "4000.00", std(**fam), start="2026-06-01", end="2026-06-30", by_component={"BASIC": "2000.00", "BONUS": "2000.00"}),
        case("extra pay paid on its own: withholding only",
             "No contributions on a run holding only the extra pay, which the monthly bases already prorate. " + law,
             "2000.00", std(**fam), start="2026-12-01", end="2026-12-31", by_component={"BONUS": "2000.00"}),
        case("pay under the exemption limit: 0% recorded",
             "RIRPF art. 81.1: 15,000 is not above 15,876 (situation 3, no descendants). " + law,
             "1250.00", std(annualPay="15000.00")),
        case("exempt pay on a contract under a year: the 2% minimum still applies",
             "AEAT help p. 12: the exemption does not apply where a minimum rate applies; RIRPF art. 86.2: 2%. " + law,
             "1250.00", std(annualPay="15000.00", withholdingContract="UNDER_ONE_YEAR", unemploymentRate="FIXED_TERM")),
        case("special employment relationship: 15% minimum",
             "RIRPF art. 86.2. " + law, "1800.00", std(annualPay="21600.00", withholdingContract="SPECIAL_RELATIONSHIP")),
        case("43% cap just above the exemption limit",
             "RIRPF art. 85.3: (16,500 - 15,876) x 43% = 268.32 caps the quota. " + law,
             "1375.00", std(annualPay="16500.00")),
        case("work income reduction in its second stretch",
             "LIRPF art. 20: 7,302 - 1.75 x (net - 14,852). " + law, "1500.00", std(annualPay="18000.00", familySituation="2")),
        case("work income reduction in its third stretch",
             "LIRPF art. 20: 2,364.34 - 1.14 x (net - 17,673.52). " + law, "1650.00", std(annualPay="19800.00")),
        case("group 1 minimum base top-up",
             "Orden art. 3: group 1 minimum 1,989.30 above the 1,800 paid. " + law, "1800.00", std(annualPay="21600.00", contributionGroup="1")),
        case("fixed-term contract: unemployment 8.30%",
             "Orden art. 33.2.a) 2.º: 6.70% employer, 1.60% employee. " + law, "2200.00", std(annualPay="26400.00", unemploymentRate="FIXED_TERM")),
        case("7,500 EUR a month: maximum base and two solidarity tiers",
             "Orden arts. 2, 17: base 5,101.20; 1.15% on 5,101.20-5,611.32 and 1.25% on 5,611.32-7,500. " + law,
             "7500.00", std(annualPay="90000.00", contributionGroup="1")),
        case("10,000 EUR a month: all three solidarity tiers",
             "Orden art. 17: 1.46% above 7,651.80. " + law, "10000.00", std(annualPay="120000.00", contributionGroup="1", familySituation="2")),
        case("housing loan: rate less two points",
             "RIRPF art. 86.1; algorithm MINOPAGO = TRUNCAR(2% x RETRIB) for pay under 33,007.20. " + law,
             "2500.00", std(annualPay="30000.00", housingLoan="true")),
        case("ascendant over 75 shared by three: 1,150 / 3 and 1,400 / 3",
             "LIRPF arts. 59, 61: the minimum is shared among descendants living with the ascendant; REDONDEAR1(383.333...) = 383.33. " + law,
             "2000.00", std(annualPay="24000.00", ascendants75="1", ascendantsSharedBy="3")),
        case("ascendant aged 65 to 74, whole",
             "LIRPF art. 59. " + law, "2000.00", std(annualPay="24000.00", ascendants65="1")),
        case("worker with a disability of 65% or more",
             "RIRPF art. 83.2.b) 7,750 expenses; LIRPF art. 60: 9,000 + 3,000 assistance. " + law,
             "2400.00", std(annualPay="28800.00", disability="FROM_65")),
        case("worker with a 33% disability and reduced mobility",
             "Same rules, 3,000 minimum + 3,000 assistance, 7,750 expenses. " + law,
             "2400.00", std(annualPay="28800.00", disability="FROM_33_REDUCED_MOBILITY")),
        case("worker with a 33% disability",
             "3,500 expenses, 3,000 minimum. " + law, "2400.00", std(annualPay="28800.00", disability="FROM_33")),
        case("aged 65 or more in 2026",
             "LIRPF art. 57: +1,150 (2026 - 1961 = 65 > 64). " + law, "2400.00", std(annualPay="28800.00"), birth="1961-12-31"),
        case("aged 75 or more in 2026",
             "LIRPF art. 57: +1,150 +1,400. " + law, "2400.00", std(annualPay="28800.00"), birth="1951-01-01"),
        case("three children shared with the other parent: half minimum, 600 reduction",
             "LIRPF art. 58: each descendant by half; RIRPF art. 83.3.e): 600 with more than two descendants. " + law,
             "3000.00", std(annualPay="36000.00", descendants="3", descendantsComputation="HALF")),
        case("five descendants, single parent",
             "LIRPF art. 58: 2,400 + 2,700 + 4,000 + 4,500 x 2; situation 1 with two or more: limit 18,694. " + law,
             "3500.00", std(annualPay="42000.00", familySituation="1", descendants="5", descendantsUnder3="2", descendantsComputation="WHOLE")),
        case("geographic mobility: +2,000 expenses",
             "RIRPF art. 83.2.b) 2.º. " + law, "2200.00", std(annualPay="26400.00", geographicMobility="true")),
        case("part-time 86 hours, pay above the hourly minimum",
             "Orden arts. 38-39: 86 x 8.58 = 737.88 minimum; contributions declared. " + law,
             "900.00", std(annualPay="10800.00", partTimeHours="86", annualSocialContributions="770.00"), fte="0.5"),
        case("part-time group 1, pay below the hourly minimum",
             "Orden art. 39: 100 x 11.98 = 1,198.00 for common contingencies; AT/EP 100 x 8.58 = 858.00. " + law,
             "1000.00", std(annualPay="12000.00", contributionGroup="1", partTimeHours="100", annualSocialContributions="850.00"), fte="0.6"),
        case("accident rate set for this employee",
             "DA 61ª LGSS tariff by occupation: 3.70% instead of the employer's 1.50%. " + law,
             "2375.00", std(accidentRate="0.037")),
        case("regularized rate entered after a pay rise",
             "RIRPF art. 87: the employer regularizes and enters the new rate, 18.40%. " + law,
             "3000.00", std(annualPay="34000.00", withholdingRateOverride="18.40", withholdingRateOverrideReason="REGULARIZED"),
             start="2026-07-01", end="2026-07-31",
             ytd={"incomeTax.TAX.base": "14250.00", "incomeTax.TAX.amount": "2274.30", "incomeTax.TAX.periods": "6"}),
        case("higher rate requested by the employee",
             "RIRPF art. 88.5: the requested 20% exceeds the computed rate. " + law,
             "2375.00", std(withholdingRateOverride="20.00", withholdingRateOverrideReason="REQUESTED_HIGHER")),
        case("requested rate below the computed one is ignored",
             "RIRPF art. 88.5 only allows a higher rate. " + law,
             "2375.00", std(withholdingRateOverride="10.00", withholdingRateOverrideReason="REQUESTED_HIGHER")),
        case("overtime: AT/EP base and the additional contribution",
             "LGSS art. 147.2.e: overtime stays out of the common base (2,000) but enters the AT/EP, unemployment, FOGASA and training base (2,300); Orden art. 5: 28.30% (23.60 / 4.70) on the 300. " + law,
             "2300.00", std(annualPay="27600.00"), by_component={"BASIC": "2000.00", "OT": "300.00"}),
        case("force majeure overtime: 14%",
             "Orden art. 5: 12% employer, 2% employee on overtime caused by force majeure (ET art. 35.3). " + law,
             "2300.00", std(annualPay="27600.00", overtimeForceMajeure="true"), by_component={"BASIC": "2000.00", "OT": "300.00"}),
        case("overtime above the maximum base",
             "The AT/EP base is capped at 5,101.20 with overtime included; solidarity applies to the remuneration without overtime. " + law,
             "6000.00", std(annualPay="72000.00", contributionGroup="1"), by_component={"BASIC": "5000.00", "OT": "1000.00"}),
        case("year to date consistent with the rate, including a second run in a month",
             "Earlier lines at 15.96%: 2,375 x 3 months and a 1,000 extra run: 379.05 x 3 + 159.60 = 1,296.75 on 8,125. " + law,
             "2375.00", std(annualSocialContributions="1700.00"), birth="1980-02-01", start="2026-04-01", end="2026-04-30",
             ytd={"incomeTax.TAX.base": "8125.00", "incomeTax.TAX.amount": "1296.75", "incomeTax.TAX.periods": "4"}),
    ]
    for c in computed:
        c["expectLines"] = expected_lines(c)
    zero = case("no pay, no lines", "nothing is paid, so nothing is due", "0", std())
    zero["expectLines"] = []
    errors = [
        case("annual pay is required", "RIRPF art. 83.2: the rate needs the expected annual pay", "2000.00",
             {k: v for k, v in std().items() if k != "annualPay"}, error="annualPay"),
        case("Basque Country workplace refused", "Concierto Económico art. 7.Uno.a)", "2000.00", std(taxTerritory="BASQUE_COUNTRY"), error="Concierto"),
        case("Navarre workplace refused", "Convenio Económico art. 10.1.a)", "2000.00", std(taxTerritory="NAVARRE"), error="Convenio"),
        case("Ceuta, Melilla or La Palma refused", "RIRPF art. 80.2 reduction not modelled", "2000.00", std(taxTerritory="CEUTA_MELILLA_LA_PALMA"), error="Ceuta"),
        case("daily-base group refused", "Orden art. 3: groups 8-11 contribute by day", "2000.00", std(contributionGroup="9"), error="groups 8 to 11"),
        case("overtime without regular pay", "the bases start from the month's ordinary pay", "300.00", std(), by_component={"OT": "300.00"}, error="Overtime"),
        case("descendants without how they count", "RIRPF art. 84.2: by half unless the employee has the exclusive right", "2000.00", std(descendants="1"), error="descendantsComputation"),
        case("part-time without hours", "Orden art. 39: minimum = hours x hourly base", "1000.00", std(), fte="0.5", error="partTimeHours"),
        case("part-time without declared contributions", "the estimate assumes full-time bases", "1000.00", std(partTimeHours="80"), fte="0.5", error="annualSocialContributions"),
        case("partial month refused", "bases are prorated by days", "1000.00", std(), whole=False, error="whole month"),
        case("unpaid leave refused", "Orden art. 7", "1500.00", std(), reductions="500.00", error="Unpaid leave"),
        case("extra pay without the annual amount", "LGSS art. 147 proration needs it", "4000.00", std(),
             by_component={"BASIC": "2000.00", "BONUS": "2000.00"}, error="annualExtraPay"),
        case("situation 1 without descendants", "RIRPF art. 81.1 and algorithm validation 14", "2000.00", std(familySituation="1"), error="situation 1"),
        case("more children under 3 than descendants", "they are counted among the descendants", "2000.00", std(descendants="1", descendantsUnder3="2", descendantsComputation="WHOLE"), error="descendantsUnder3"),
        case("housing loan at 33,007.20 or more", "RIRPF art. 86.1; algorithm validation 21", "2800.00", std(annualPay="33007.20", housingLoan="true"), error="33,007.20"),
        case("rate override without a reason", "the reason decides how it applies", "2000.00", std(withholdingRateOverride="18.00"), error="withholdingRateOverrideReason"),
        case("rate no longer matches the year to date", "RIRPF art. 87: a change in the data requires regularization",
             "2500.00", std(annualPay="30000.00"), start="2026-04-01", end="2026-04-30",
             ytd={"incomeTax.TAX.base": "7125.00", "incomeTax.TAX.amount": "1137.15", "incomeTax.TAX.periods": "3"}, error="regulariz"),
        case("daily manual workers refused", "RIRPF art. 83.2: 100 x the daily wage", "2000.00", std(withholdingContract="DAILY_MANUAL"), error="daily"),
        case("training contracts refused", "Orden art. 46 fixed quotas", "1200.00", std(trainingContract="true"), error="Training"),
        case("multiple employment refused", "Orden art. 10", "2000.00", std(multipleEmployment="true"), error="multiple employment"),
        case("dependants with a disability refused", "not modelled", "2000.00", std(descendants="1", descendantsComputation="WHOLE", dependantsWithDisability="true"), error="disability"),
        case("court-ordered payments refused", "RIRPF arts. 83.3, 85.2 not modelled", "2000.00", std(courtOrderedPayments="true"), error="Court-ordered"),
        case("irregular income refused", "RIRPF art. 83.3.a) not modelled", "2000.00", std(irregularIncome="true"), error="Irregular"),
        case("no birth date", "the minimum depends on the year of birth", "2000.00", std(), birth=None, error="birth date"),
        case("paid in the next year", "RIRPF art. 78: withholding follows the payment, 2027 rules", "2000.00", std(),
             start="2026-12-01", end="2026-12-31", payment="2027-01-05", error="same year"),
        case("pre-tax deductions are refused", "not modelled", "2000.00", std(), pre_tax="50.00", error="Pre-tax deductions are not supported"),
        case("2027 is not covered", "2027 values not yet set", "2000.00", std(), start="2027-01-01", end="2027-01-31", error="a newer template version is needed"),
    ]
    return computed + [zero] + errors


def dated(value, source):
    return [{"from": FROM, "to": TO, "value": value, "source": source}]


def emp_key(k):
    return "#employee['" + k + "']"


GROUP = emp_key("contributionGroup")
PAY = emp_key("annualPay")
PART = emp_key("partTimeHours") + " != null"
E_DESC = emp_key("descendants")
E_COMP = emp_key("descendantsComputation")
EMP_FM = emp_key("overtimeForceMajeure")


def main():
    template = {
        "schemaVersion": 1,
        "id": "es-employment",
        "name": "Spain — employment (Régimen General, territorio común)",
        "version": "2026.1.0",
        "status": "LAW_DERIVED",
        "jurisdiction": {"country": "ES"},
        "validity": {"from": FROM, "to": TO},
        "currency": "EUR",
        "payFrequencies": ["MONTHLY"],
        "taxYearStart": "--01-01",
        "dateBasis": "PERIOD",
        "rounding": {"scale": 2, "mode": "HALF_UP"},
        "changelog": [
            {"version": "2026.1.0", "date": RETRIEVED,
             "summary": "2026: IRPF withholding rate per the AEAT algorithm (RIRPF arts. 81-86) with a year-to-date check that asks for the regularized rate; social security per Orden PJC/297/2026: common contingencies 28.30%, MEI 0.90%, unemployment 7.05% / 8.30%, FOGASA 0.20%, vocational training 0.70%, AT/EP from the tariff, solidarity contribution above 5,101.20, overtime in the AT/EP base with its 28.30% / 14% additional contribution. Withholding rates checked against the AEAT calculation service."}
        ],
        "sources": SOURCES,
        "inputs": {
            "employer": [
                {"key": "accidentRate", "type": "DECIMAL", "required": True, "min": 0, "max": "0.2",
                 "description": "AT/EP contribution rate (IT + IMS, as a fraction, e.g. 0.015 for 1.50%) from the tariff of the LGSS disposición adicional sexagésima primera for the company's activity (Orden PJC/297/2026 art. 4.b); an employee's own rate overrides it"},
            ],
            "employee": [
                {"key": "taxTerritory", "type": "ENUM", "required": True, "values": ["COMMON", "BASQUE_COUNTRY", "NAVARRE", "CEUTA_MELILLA_LA_PALMA"],
                 "description": "Where the withholding belongs: COMMON for work performed in the common territory. Work performed in the Basque Country or Navarre is withheld under the foral rules (Concierto art. 7.Uno.a; Convenio art. 10.1.a) and income of residents of Ceuta, Melilla or La Palma earned there has a 60% reduction (RIRPF art. 80.2) — those are refused"},
                {"key": "annualPay", "type": "DECIMAL", "required": True, "min": "0.01",
                 "description": "Expected gross pay from this employer in 2026 (RETRIB, RIRPF art. 83.2): fixed and foreseeable variable pay, in cash and in kind, without arrears belonging to earlier years. For a contract renewed in the year, include what was paid earlier in the year"},
                {"key": "annualExtraPay", "type": "DECIMAL", "required": False, "defaultValue": "0", "min": 0,
                 "description": "Annual amount of the extra payments and other pay accruing over periods longer than a month (pagas extraordinarias), prorated over 12 months in the social security base (LGSS art. 147; Orden art. 1). Pay them as BONUS"},
                {"key": "annualSocialContributions", "type": "DECIMAL", "required": False, "min": 0,
                 "description": "Expected employee social security contributions for the year (COTIZACIONES, RIRPF art. 83.2.a). Without it the template estimates 12 months of contributions on annualPay / 12; required for part-time employees"},
                {"key": "familySituation", "type": "ENUM", "required": True, "values": ["1", "2", "3"],
                 "description": "Family situation from the employee's modelo 145 (RIRPF art. 81.1): 1 single, widowed, divorced or separated with descendants and the single-parent joint-filing reduction; 2 married with a spouse earning no more than 1,500 EUR a year; 3 any other (and when not stated)"},
                {"key": "descendants", "type": "INTEGER", "required": False, "defaultValue": "0", "min": 0, "max": 16,
                 "description": "Children and other descendants giving right to the minimum (LIRPF art. 58): under 25 at the end of 2026 (born 2002 or later), living with the employee, earning no more than 8,000 EUR and not filing a return with more than 1,800 EUR (LIRPF art. 61.2.ª)"},
                {"key": "descendantsUnder3", "type": "INTEGER", "required": False, "defaultValue": "0", "min": 0, "max": 16,
                 "description": "Of those descendants, the ones born in 2024 or later, or adopted or fostered in 2024 or later (+2,800 each, LIRPF art. 58.2)"},
                {"key": "descendantsComputation", "type": "ENUM", "required": False, "values": ["WHOLE", "HALF"],
                 "description": "How the descendants count, from modelo 145 (required with descendants): HALF unless the employee alone is entitled to their whole minimum (RIRPF art. 84.2; LIRPF art. 61.1.ª); all of them are taken the same way"},
                {"key": "ascendants65", "type": "INTEGER", "required": False, "defaultValue": "0", "min": 0, "max": 6,
                 "description": "Parents or other ascendants aged 65 to 74 in 2026 (born 1952 to 1961) living with the employee at least half the year, earning no more than 8,000 EUR and not filing a return with more than 1,800 EUR (LIRPF arts. 59, 61.2.ª)"},
                {"key": "ascendants75", "type": "INTEGER", "required": False, "defaultValue": "0", "min": 0, "max": 6,
                 "description": "Ascendants aged 75 or more in 2026 (born 1951 or earlier), same conditions (+1,400 each, LIRPF art. 59.2)"},
                {"key": "ascendantsSharedBy", "type": "INTEGER", "required": False, "defaultValue": "1", "min": 1, "max": 10,
                 "description": "Number of descendants (the employee included) the ascendants live with, who share their minimum (LIRPF art. 61.1.ª); the same for all of them"},
                {"key": "disability", "type": "ENUM", "required": False, "defaultValue": "NONE", "values": ["NONE", "FROM_33", "FROM_33_REDUCED_MOBILITY", "FROM_65"],
                 "description": "The employee's own disability: 33% to 65%, the same with reduced mobility or needing help from another person, or 65% or more (LIRPF art. 60; RIRPF art. 83.2.b)"},
                {"key": "geographicMobility", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "The employee, registered unemployed, accepted a job requiring a change of residence (+2,000 EUR expenses this year and next, RIRPF art. 83.2.b)"},
                {"key": "housingLoan", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "The employee stated in modelo 145 loan payments for a main home bought before 2013 giving right to the transitional deduction; only with annual pay under 33,007.20 (RIRPF art. 86.1)"},
                {"key": "withholdingContract", "type": "ENUM", "required": True, "values": ["GENERAL", "UNDER_ONE_YEAR", "SPECIAL_RELATIONSHIP", "DAILY_MANUAL"],
                 "description": "For the minimum rate (RIRPF art. 86.2): GENERAL (also special relationships of people with disabilities in special employment centres and of inmates), UNDER_ONE_YEAR (contracts under a year and artists, minimum 2%), SPECIAL_RELATIONSHIP (other special dependent relationships, minimum 15%), DAILY_MANUAL (sporadic manual work paid by the day — refused)"},
                {"key": "withholdingRateOverride", "type": "DECIMAL", "required": False, "min": 0, "max": 47,
                 "description": "Withholding rate in percent with two decimals replacing the computed one, with its reason"},
                {"key": "withholdingRateOverrideReason", "type": "ENUM", "required": False, "defaultValue": "NONE", "values": ["NONE", "REGULARIZED", "REQUESTED_HIGHER"],
                 "description": "REGULARIZED: the rate the employer regularized under RIRPF art. 87 after a change in the data (used as entered); REQUESTED_HIGHER: a higher rate the employee asked for in writing (RIRPF art. 88.5; used when above the computed one)"},
                {"key": "contributionGroup", "type": "INTEGER", "required": True, "min": 1, "max": 11,
                 "description": "Social security contribution group (grupo de cotización) of the employee's professional category, 1 to 7 (Orden art. 3); groups 8 to 11 contribute by day and are refused"},
                {"key": "unemploymentRate", "type": "ENUM", "required": True, "values": ["INDEFINITE", "FIXED_TERM"],
                 "description": "Unemployment rate (Orden art. 33.2.a): INDEFINITE 7.05% for open-ended contracts (also part-time, fijos discontinuos, training, relief, substitution and interim contracts, and any contract with a worker with a disability of 33% or more); FIXED_TERM 8.30% for other fixed-term contracts"},
                {"key": "partTimeHours", "type": "DECIMAL", "required": False, "min": 0, "max": 300,
                 "description": "Part-time contract: hours actually worked this month; the minimum bases are hours x the hourly minimum of the group, and x 8.58 for AT/EP (Orden arts. 38-39)"},
                {"key": "overtimeForceMajeure", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "The overtime (OT) paid is for preventing or repairing extraordinary and urgent damage (ET art. 35.3): additional contribution 14% instead of 28.30% (Orden art. 5)"},
                {"key": "accidentRate", "type": "DECIMAL", "required": False, "min": 0, "max": "0.2",
                 "description": "This employee's AT/EP rate where the tariff sets it by occupation (DA 61ª LGSS, cuadro II), replacing the employer's rate"},
                {"key": "trainingContract", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "Training contract (formación en alternancia or the earlier training contracts) with the fixed quotas of Orden art. 46 — not supported"},
                {"key": "multipleEmployment", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "The employee also works for another company (pluriempleo), sharing the maximum base (Orden art. 10) — not supported"},
                {"key": "dependantsWithDisability", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "A descendant or ascendant has a disability, or a descendant of 25 or more counts for that reason (LIRPF art. 60.2) — not supported"},
                {"key": "courtOrderedPayments", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "The employee pays a court-ordered compensatory pension to a spouse or maintenance to children (RIRPF arts. 83.3.f, 85.2) — not supported"},
                {"key": "irregularIncome", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "The annual pay includes income generated over more than two years or obtained irregularly with the 30% reduction (RIRPF art. 83.3.a) — not supported"},
            ],
        },
        "parameters": {name: dated(value, src) for name, (value, src) in PARAMS.items()},
        "tables": {
            "minimumBases": {
                "kind": "EXACT",
                "description": "Minimum common-contingencies bases by contribution group: monthly (Orden PJC/297/2026 art. 3) and per hour for part-time contracts (art. 39)",
                "columns": ["monthly", "hourly"],
                "versions": [{"from": FROM, "to": TO, "source": "orden-pjc-297-2026",
                              "rows": [{"key": str(g), "values": list(v)} for g, v in MIN_BASES.items()]}],
            },
            "exemptLimits": {
                "kind": "EXACT",
                "description": "Annual pay up to which no withholding applies, by family situation and number of descendants (RIRPF art. 81.1, RD 142/2024; algorithm TABLA 1); situation 1 needs a descendant",
                "columns": ["d0", "d1", "d2"],
                "versions": [{"from": FROM, "to": TO, "source": "rirpf",
                              "rows": [{"key": k, "values": list(v)} for k, v in EXEMPT.items()]}],
            },
            "withholdingScale": {
                "kind": "BRACKETS",
                "description": "Withholding scale (LIRPF art. 101.1; algorithm TABLA 2) with the published cumulative quotas",
                "versions": [{"from": FROM, "to": TO, "source": "aeat-algoritmo-2026",
                              "rows": [{"lower": lo, "upper": up, "rate": r, "base": b} for lo, up, r, b in SCALE]}],
            },
            "solidarity": {
                "kind": "BRACKETS",
                "description": "Solidarity contribution on monthly remuneration above the maximum base, employee and employer shares (Orden PJC/297/2026 art. 17; LGSS art. 19 bis)",
                "versions": [{"from": FROM, "to": TO, "source": "orden-pjc-297-2026",
                              "rows": [{"key": side, "lower": lo, "upper": up, "rate": ee if side == "EMPLOYEE" else er}
                                       for side in ("EMPLOYEE", "EMPLOYER") for lo, up, ee, er in SOLIDARITY]}],
            },
        },
        "steps": [
            {"id": "calendarMonth", "type": "REQUIRE", "condition": "#isCalendarMonth",
             "message": "Spanish payroll is monthly: the pay period must be a whole calendar month"},
            {"id": "paidInSameYear", "type": "REQUIRE", "source": "rirpf", "condition": "#paymentDate.getYear() == #periodEnd.getYear()",
             "message": "The payment must fall in the same year as the pay period: withholding follows the payment (RIRPF art. 78) and contributions the month of accrual, so pay made in another year is not supported"},
            {"id": "noPreTaxDeductions", "type": "REQUIRE", "condition": "#preTaxDeductions == 0",
             "message": "Pre-tax deductions are not supported by this template"},
            {"id": "noUnpaidLeave", "type": "REQUIRE", "source": "orden-pjc-297-2026", "condition": "#reductions == 0",
             "message": "Unpaid leave is not supported: days registered without pay contribute on the minimum base (Orden PJC/297/2026 art. 7)"},
            {"id": "wholeMonth", "type": "REQUIRE", "source": "orden-pjc-297-2026", "condition": "#employedThroughout",
             "message": "The employee must be employed for the whole month: bases for a month started or ended part-way are prorated by days, which this template does not do"},
            {"id": "commonTerritory", "type": "REQUIRE", "condition": emp_key("taxTerritory") + " == 'COMMON'",
             "message": "Only the common tax territory is supported: withholding on work performed in the Basque Country or Navarre follows the foral rules (Concierto Económico art. 7.Uno.a; Convenio Económico art. 10.1.a), and income earned in Ceuta, Melilla or La Palma by their residents has the 60% reduction of RIRPF art. 80.2"},
            {"id": "noDailyManual", "type": "REQUIRE", "source": "rirpf", "condition": emp_key("withholdingContract") + " != 'DAILY_MANUAL'",
             "message": "Manual workers paid by the daily wage (peonadas) are not supported: their annual pay is 100 x the daily wage (RIRPF art. 83.2)"},
            {"id": "noTrainingContract", "type": "REQUIRE", "source": "orden-pjc-297-2026", "condition": "!" + emp_key("trainingContract"),
             "message": "Training contracts contribute fixed quotas (Orden PJC/297/2026 art. 46), which this template does not do"},
            {"id": "noMultipleEmployment", "type": "REQUIRE", "source": "orden-pjc-297-2026", "condition": "!" + emp_key("multipleEmployment"),
             "message": "Employees in multiple employment (pluriempleo) share the maximum base between companies (Orden PJC/297/2026 art. 10), which this template does not do"},
            {"id": "noDisabledDependants", "type": "REQUIRE", "source": "lirpf", "condition": "!" + emp_key("dependantsWithDisability"),
             "message": "Descendants or ascendants with a disability are not supported by this template (LIRPF art. 60.2)"},
            {"id": "noCourtOrderedPayments", "type": "REQUIRE", "source": "rirpf", "condition": "!" + emp_key("courtOrderedPayments"),
             "message": "Court-ordered compensatory pensions or child maintenance are not supported (RIRPF arts. 83.3.f, 85.2)"},
            {"id": "noIrregularIncome", "type": "REQUIRE", "source": "rirpf", "condition": "!" + emp_key("irregularIncome"),
             "message": "Irregular income with the 30% reduction is not supported (RIRPF art. 83.3.a)"},
            {"id": "monthlyContributionGroup", "type": "REQUIRE", "source": "orden-pjc-297-2026", "condition": GROUP + " <= 7",
             "message": "Contribution groups 8 to 11 contribute on daily bases (Orden PJC/297/2026 art. 3), which this template does not do"},
            {"id": "overtimeWithPay", "type": "REQUIRE", "source": "lgss",
             "condition": "#earningsByComponent['OT'] == 0 or #socialEarnings - #earningsByComponent['BONUS'] - #earningsByComponent['OT'] > 0",
             "message": "Overtime paid without ordinary pay in the same run is not supported: the contribution bases start from the month's ordinary pay"},
            {"id": "partTimeHoursGiven", "type": "REQUIRE", "source": "orden-pjc-297-2026", "condition": "(#fte >= 1 and #fullPeriod) or " + PART,
             "message": "Part-time employee: set the employee's partTimeHours (hours worked this month) for the hourly minimum bases (Orden PJC/297/2026 arts. 38-39)"},
            {"id": "partTimeContributions", "type": "REQUIRE", "source": "rirpf", "when": PART,
             "condition": emp_key("annualSocialContributions") + " != null",
             "message": "Part-time employee: set the employee's annualSocialContributions; the template's estimate assumes full-time bases"},
            {"id": "extraPayDeclared", "type": "REQUIRE", "source": "lgss",
             "condition": "#earningsByComponent['BONUS'] == 0 or " + emp_key("annualExtraPay") + " > 0",
             "message": "Extra payments (BONUS) are prorated over the year in the contribution base (LGSS art. 147): set the employee's annualExtraPay"},
            {"id": "singleParentHasDescendants", "type": "REQUIRE", "source": "rirpf",
             "condition": emp_key("familySituation") + " != '1' or " + emp_key("descendants") + " >= 1",
             "message": "Family situation 1 requires at least one descendant (RIRPF art. 81.1)"},
            {"id": "under3WithinDescendants", "type": "REQUIRE", "source": "lirpf",
             "condition": emp_key("descendantsUnder3") + " <= " + emp_key("descendants"),
             "message": "descendantsUnder3 counts descendants already included in descendants, so it cannot be larger"},
            {"id": "descendantsComputationGiven", "type": "REQUIRE", "source": "rirpf",
             "condition": E_DESC + " == 0 or " + E_COMP + " != null",
             "message": "Set the employee's descendantsComputation: descendants count by half unless the employee alone is entitled to their whole minimum (RIRPF art. 84.2)"},
            {"id": "housingLoanLimit", "type": "REQUIRE", "source": "rirpf",
             "condition": "!" + emp_key("housingLoan") + " or " + PAY + " < #p['housingIncomeLimit']",
             "message": "The housing loan reduction applies only with annual pay under 33,007.20 EUR (RIRPF art. 86.1)"},
            {"id": "overrideReason", "type": "REQUIRE", "source": "rirpf",
             "condition": "(" + emp_key("withholdingRateOverride") + " == null) == (" + emp_key("withholdingRateOverrideReason") + " == 'NONE')",
             "message": "Set withholdingRateOverride together with withholdingRateOverrideReason (REGULARIZED or REQUESTED_HIGHER), or neither"},
            # Withholding rate (RIRPF arts. 82-86, AEAT algorithm)
            {"id": "estimateMonthly", "type": "VALUE", "expr": "#divide(" + PAY + ", 12, 2, 'HALF_UP')"},
            {"id": "estimateCommonBase", "type": "VALUE", "source": "orden-pjc-297-2026",
             "expr": "#clamp(#estimateMonthly, #table.lookup('minimumBases', " + GROUP + ", 'monthly'), #p['maximumBase'])"},
            {"id": "estimateAccidentBase", "type": "VALUE", "source": "orden-pjc-297-2026",
             "expr": "#clamp(#estimateMonthly, #p['accidentMinimumBase'], #p['maximumBase'])"},
            {"id": "estimatedContributions", "type": "VALUE", "source": "rirpf",
             "expr": "12 * (#round(#estimateCommonBase * #p['commonEmployeeRate'], 2, 'HALF_UP') + #round(#estimateCommonBase * #p['equityEmployeeRate'], 2, 'HALF_UP')"
                     " + #round(#estimateAccidentBase * (" + emp_key("unemploymentRate") + " == 'FIXED_TERM' ? #p['unemploymentFixedTermEmployeeRate'] : #p['unemploymentIndefiniteEmployeeRate']), 2, 'HALF_UP')"
                     " + #round(#estimateAccidentBase * #p['trainingEmployeeRate'], 2, 'HALF_UP') + #round(#table.bracketTax('solidarity', 'EMPLOYEE', #estimateMonthly), 2, 'HALF_UP'))"},
            {"id": "socialContributions", "type": "VALUE", "source": "rirpf",
             "expr": "#coalesce(" + emp_key("annualSocialContributions") + ", #estimatedContributions)"},
            {"id": "netWorkIncome", "type": "VALUE", "source": "rirpf", "expr": "#pos(" + PAY + " - #socialContributions)"},
            {"id": "otherExpenses", "type": "VALUE", "source": "rirpf",
             "expr": "#min(#p['generalExpenses'] + (" + emp_key("geographicMobility") + " ? #p['mobilityExpenses'] : 0)"
                     " + (" + emp_key("disability") + " == 'FROM_65' or " + emp_key("disability") + " == 'FROM_33_REDUCED_MOBILITY' ? #p['disabledWorkerExpensesHigh']"
                     " : (" + emp_key("disability") + " == 'FROM_33' ? #p['disabledWorkerExpenses'] : 0)), #netWorkIncome)"},
            {"id": "workIncomeReduction", "type": "VALUE", "source": "lirpf",
             "expr": "#netWorkIncome <= #p['red20Lower'] ? #p['red20Max'] : (#netWorkIncome <= #p['red20Middle']"
                     " ? #round(#p['red20Max'] - #p['red20Slope1'] * (#netWorkIncome - #p['red20Lower']), 2, 'HALF_UP')"
                     " : (#netWorkIncome < #p['red20Upper'] ? #round(#p['red20MiddleAmount'] - #p['red20Slope2'] * (#netWorkIncome - #p['red20Middle']), 2, 'HALF_UP') : 0))"},
            {"id": "withholdingBase", "type": "VALUE", "source": "rirpf",
             "expr": "#pos(#pos(#netWorkIncome - #otherExpenses - #workIncomeReduction) - (" + emp_key("descendants") + " > 2 ? #p['largeFamilyReduction'] : 0))"},
            {"id": "taxpayerAge", "type": "VALUE", "source": "aeat-algoritmo-2026", "expr": "#periodEnd.getYear() - #birthYear"},
            {"id": "descendantsWhole", "type": "VALUE", "source": "lirpf",
             "expr": "(" + emp_key("descendants") + " >= 1 ? #p['descendant1'] : 0) + (" + emp_key("descendants") + " >= 2 ? #p['descendant2'] : 0)"
                     " + (" + emp_key("descendants") + " >= 3 ? #p['descendant3'] : 0) + (" + emp_key("descendants") + " > 3 ? (" + emp_key("descendants") + " - 3) * #p['descendant4'] : 0)"
                     " + " + emp_key("descendantsUnder3") + " * #p['descendantUnder3']"},
            {"id": "personalFamilyMinimum", "type": "VALUE", "source": "lirpf",
             "expr": "#p['personalMinimum'] + (#taxpayerAge > 64 ? #p['age65Minimum'] : 0) + (#taxpayerAge > 74 ? #p['age75Minimum'] : 0)"
                     " + (" + E_COMP + " == 'HALF' ? #divide(#descendantsWhole, 2, 2, 'HALF_UP') : #descendantsWhole)"
                     " + #divide((" + emp_key("ascendants65") + " + " + emp_key("ascendants75") + ") * #p['ascendantMinimum'], " + emp_key("ascendantsSharedBy") + ", 2, 'HALF_UP')"
                     " + #divide(" + emp_key("ascendants75") + " * #p['ascendant75Minimum'], " + emp_key("ascendantsSharedBy") + ", 2, 'HALF_UP')"
                     " + (" + emp_key("disability") + " == 'FROM_65' ? #p['disabilityMinimumHigh'] + #p['assistanceMinimum']"
                     " : (" + emp_key("disability") + " == 'FROM_33_REDUCED_MOBILITY' ? #p['disabilityMinimum'] + #p['assistanceMinimum']"
                     " : (" + emp_key("disability") + " == 'FROM_33' ? #p['disabilityMinimum'] : 0)))"},
            {"id": "exemptLimit", "type": "VALUE", "source": "rirpf",
             "expr": "#table.lookup('exemptLimits', " + emp_key("familySituation") + ", " + emp_key("descendants") + " >= 2 ? 'd2' : (" + emp_key("descendants") + " == 1 ? 'd1' : 'd0'))"},
            {"id": "withholdingQuota", "type": "VALUE", "source": "rirpf",
             "expr": "#pos(#table.bracketTax('withholdingScale', #withholdingBase) - #table.bracketTax('withholdingScale', #personalFamilyMinimum))"},
            {"id": "cappedQuota", "type": "VALUE", "source": "rirpf",
             "expr": PAY + " <= #p['capIncomeLimit'] ? #pos(#min(#withholdingQuota, (" + PAY + " - #exemptLimit) * #p['capRate'])) : #withholdingQuota"},
            {"id": "housingReduction", "type": "VALUE", "source": "aeat-algoritmo-2026",
             "expr": emp_key("housingLoan") + " ? #round(" + PAY + " * #p['housingReductionRate'], 2, 'DOWN') : 0"},
            {"id": "computedRate", "type": "VALUE", "source": "rirpf",
             "expr": PAY + " <= #exemptLimit ? 0 : #divide(#pos(#cappedQuota - #housingReduction) * 100, " + PAY + ", 2, 'DOWN')"},
            {"id": "withholdingRate", "type": "VALUE", "source": "rirpf",
             "expr": emp_key("withholdingContract") + " == 'UNDER_ONE_YEAR' ? #max(#computedRate, #p['minimumRateShortContract'])"
                     " : (" + emp_key("withholdingContract") + " == 'SPECIAL_RELATIONSHIP' ? #max(#computedRate, #p['minimumRateSpecial']) : #computedRate)"},
            {"id": "appliedRate", "type": "VALUE", "source": "rirpf",
             "expr": emp_key("withholdingRateOverrideReason") + " == 'REGULARIZED' ? " + emp_key("withholdingRateOverride")
                     + " : (" + emp_key("withholdingRateOverrideReason") + " == 'REQUESTED_HIGHER' ? #max(#withholdingRate, " + emp_key("withholdingRateOverride") + ") : #withholdingRate)"},
            {"id": "rateMatchesYearToDate", "type": "REQUIRE", "source": "rirpf",
             "when": "#ytd.periods('incomeTax', 'TAX') > 0 and " + emp_key("withholdingRateOverrideReason") + " == 'NONE'",
             "condition": "#max(#ytd.amount('incomeTax', 'TAX') - #divide(#ytd.base('incomeTax', 'TAX') * #withholdingRate, 100, 6, 'HALF_UP'),"
                          " #divide(#ytd.base('incomeTax', 'TAX') * #withholdingRate, 100, 6, 'HALF_UP') - #ytd.amount('incomeTax', 'TAX')) * 200 <= #ytd.periods('incomeTax', 'TAX')",
             "message": "The withholding rate computed from the current data differs from the rate applied earlier this year: regularize it under RIRPF art. 87 and enter the regularized rate as withholdingRateOverride with reason REGULARIZED"},
            # Social security (Orden PJC/297/2026)
            {"id": "monthlyPay", "type": "VALUE", "source": "lgss", "expr": "#pos(#socialEarnings - #earningsByComponent['BONUS'] - #earningsByComponent['OT'])"},
            {"id": "contributionPay", "type": "VALUE", "source": "orden-pjc-297-2026",
             "expr": "#monthlyPay > 0 ? #monthlyPay + #divide(" + emp_key("annualExtraPay") + ", 12, 2, 'HALF_UP') : 0"},
            {"id": "commonMinimumBase", "type": "VALUE", "source": "orden-pjc-297-2026",
             "expr": PART + " ? #round(" + emp_key("partTimeHours") + " * #table.lookup('minimumBases', " + GROUP + ", 'hourly'), 2, 'HALF_UP') : #table.lookup('minimumBases', " + GROUP + ", 'monthly')"},
            {"id": "accidentMinimumBase", "type": "VALUE", "source": "orden-pjc-297-2026",
             "expr": PART + " ? #round(" + emp_key("partTimeHours") + " * #p['partTimeAccidentHourlyBase'], 2, 'HALF_UP') : #p['accidentMinimumBase']"},
            {"id": "commonBase", "type": "VALUE", "source": "orden-pjc-297-2026", "expr": "#clamp(#contributionPay, #commonMinimumBase, #p['maximumBase'])"},
            {"id": "accidentBase", "type": "VALUE", "source": "orden-pjc-297-2026", "expr": "#clamp(#contributionPay + #earningsByComponent['OT'], #accidentMinimumBase, #p['maximumBase'])"},
            {"id": "commonContingencies", "type": "CONTRIBUTION", "label": "Common contingencies (contingencias comunes)", "ledger": "SOCIAL_SECURITY", "source": "orden-pjc-297-2026",
             "when": "#contributionPay > 0", "base": "#commonBase",
             "employee": {"rate": "#p['commonEmployeeRate']"}, "employer": {"rate": "#p['commonEmployerRate']"}},
            {"id": "intergenerationalEquity", "type": "CONTRIBUTION", "label": "Intergenerational equity mechanism (MEI)", "ledger": "PENSION_CONTRIBUTION", "source": "orden-pjc-297-2026",
             "when": "#contributionPay > 0", "base": "#commonBase",
             "employee": {"rate": "#p['equityEmployeeRate']"}, "employer": {"rate": "#p['equityEmployerRate']"}},
            {"id": "unemployment", "type": "CONTRIBUTION", "label": "Unemployment (desempleo)", "ledger": "UNEMPLOYMENT", "source": "orden-pjc-297-2026",
             "when": "#contributionPay > 0", "base": "#accidentBase",
             "employee": {"rate": emp_key("unemploymentRate") + " == 'FIXED_TERM' ? #p['unemploymentFixedTermEmployeeRate'] : #p['unemploymentIndefiniteEmployeeRate']"},
             "employer": {"rate": emp_key("unemploymentRate") + " == 'FIXED_TERM' ? #p['unemploymentFixedTermEmployerRate'] : #p['unemploymentIndefiniteEmployerRate']"}},
            {"id": "wageGuaranteeFund", "type": "CONTRIBUTION", "label": "Wage Guarantee Fund (FOGASA)", "ledger": "OTHER", "source": "orden-pjc-297-2026",
             "when": "#contributionPay > 0", "base": "#accidentBase", "employer": {"rate": "#p['wageGuaranteeRate']"}},
            {"id": "vocationalTraining", "type": "CONTRIBUTION", "label": "Vocational training (formación profesional)", "ledger": "OTHER", "source": "orden-pjc-297-2026",
             "when": "#contributionPay > 0", "base": "#accidentBase",
             "employee": {"rate": "#p['trainingEmployeeRate']"}, "employer": {"rate": "#p['trainingEmployerRate']"}},
            {"id": "workAccident", "type": "CONTRIBUTION", "label": "Work accidents and occupational diseases (AT/EP)", "ledger": "WORK_INJURY", "source": "lgss",
             "when": "#contributionPay > 0", "base": "#accidentBase",
             "employer": {"rate": "#coalesce(" + emp_key("accidentRate") + ", #employer['accidentRate'])"}},
            {"id": "solidarity", "type": "CONTRIBUTION", "label": "Solidarity contribution (cotización adicional de solidaridad)", "ledger": "SOCIAL_SECURITY", "source": "orden-pjc-297-2026",
             "when": "#contributionPay > #p['maximumBase']", "base": "#contributionPay",
             "employee": {"amount": "#table.bracketTax('solidarity', 'EMPLOYEE', #solidarity_base)"},
             "employer": {"amount": "#table.bracketTax('solidarity', 'EMPLOYER', #solidarity_base)"}},
            {"id": "overtime", "type": "CONTRIBUTION", "label": "Overtime additional contribution (horas extraordinarias)", "ledger": "SOCIAL_SECURITY", "source": "orden-pjc-297-2026",
             "when": "#contributionPay > 0 and #earningsByComponent['OT'] > 0", "base": "#earningsByComponent['OT']",
             "employee": {"rate": EMP_FM + " ? #p['forceMajeureOvertimeEmployeeRate'] : #p['overtimeEmployeeRate']"},
             "employer": {"rate": EMP_FM + " ? #p['forceMajeureOvertimeEmployerRate'] : #p['overtimeEmployerRate']"}},
            {"id": "incomeTax", "type": "TAX", "label": "Income tax withholding (retención IRPF)", "ledger": "INCOME_TAX", "source": "rirpf",
             "when": "#taxableEarnings > 0", "base": "#taxableEarnings", "rate": "#divide(#appliedRate, 100, 4, 'HALF_UP')", "recordZero": True},
        ],
        "limitations": [
            "Common tax territory, Régimen General employees paid monthly in EUR in 2026, for whole months with the payment in the same year. Work performed in the Basque Country or Navarre, residents of Ceuta, Melilla and La Palma, contribution groups 8 to 11, training contracts, multiple employment, part months and unpaid leave are refused. Overtime (OT) enters only the AT/EP base and carries the additional contribution of Orden art. 5 (28.30%, or 14% for force majeure); it must be paid with ordinary pay in the same run.",
            "The withholding rate is recomputed each run from the annual figures entered (annual pay, contributions, family data from modelo 145) and applied to the whole payment. RIRPF art. 87 regularization is not automated: once the data change during the year the run refuses until the employer enters the regularized rate (withholdingRateOverride, REGULARIZED). A higher rate requested under art. 88.5 can be entered as REQUESTED_HIGHER.",
            "Without annualSocialContributions the expected employee contributions are estimated as 12 months on annualPay / 12 at full-time bases.",
            "Not modelled: dependants with a disability, court-ordered payments (arts. 83.3.f, 85.2), irregular income (art. 83.3.a), pensioners and the unemployed, pay in kind and its ingreso a cuenta, arrears of earlier years (15% fixed rate), fixed rates (directors, courses, authors), the special regime for workers posted to Spain, sick leave and birth leave months (their own bases), contribution reductions and incentives, and the 33.62 EUR charge at the end of fixed-term contracts under 30 days (Orden art. 28).",
            "Extra payments (BONUS) are prorated over the year in the contribution base from annualExtraPay (Orden art. 1, monthly-paid employees); a run holding only BONUS pays withholding but no contributions.",
            "Contributions are rounded to the cent, half up, per line; part-time minimum bases (hours x hourly base) and the extra-pay proration to the cent, half up.",
            "Status LAW_DERIVED: values are cited from the BOE consolidated texts, the Orden PJC/297/2026 and the AEAT 2026 algorithm and help; the cases are derived from them (one reproduces the AEAT help's 15.96% example) and every case's withholding rate matches the AEAT calculation service.",
        ],
        "testCases": test_cases(),
    }
    with open(sys.argv[1], "w", encoding="utf-8") as f:
        json.dump(template, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("wrote", sys.argv[1], "with", len(template["testCases"]), "test cases")


main()
