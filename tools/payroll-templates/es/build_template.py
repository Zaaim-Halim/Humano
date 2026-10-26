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
        "title": "Orden PJC/297/2026, de 30 de marzo, BOE núm. 79 de 31/03/2026 (BOE-A-2026-7296) — art. 1 (base: remuneración devengada en el mes más la parte proporcional de las pagas de vencimiento superior al mensual, importe anual dividido por 12; entre la base mínima y máxima del grupo), art. 2 (tope máximo 5.101,20; tope mínimo AT/EP 1.424,40), art. 3 (bases mínimas por grupo: 1.989,30 / 1.649,70 / 1.435,20 / 1.424,40; grupos 8-11 por día), art. 4 (contingencias comunes 28,30%: 23,60 empresa, 4,70 trabajador; AT/EP según la tarifa de la DA 61ª LGSS, a cargo de la empresa), art. 5 (horas extraordinarias), art. 6 (IT: base de contingencias comunes del mes anterior al de la baja; nacimiento y cuidado de menor: la del mes anterior al mes previo al del inicio), art. 7 (alta sin remuneración: la base mínima del grupo, de forma proporcional al número de días), art. 10 (pluriempleo), art. 16 (MEI 0,90%: 0,75 / 0,15), art. 17 (solidaridad 1,15% / 1,25% / 1,46% por tramos sobre 5.101,20), art. 28 (33,62 euros al finalizar contratos inferiores a 30 días), art. 32.1.a) (IT por contingencias comunes en los supuestos del art. 152 LGSS: 1,55%, 1,30 empresa y 0,25 trabajador), art. 33 (desempleo 7,05% indefinidos / 8,30% duración determinada; FOGASA 0,20%; formación profesional 0,70%: 0,60 / 0,10; base la de AT/EP), arts. 38-39 (tiempo parcial: bases mínimas por hora 11,98 / 9,94 / 8,65 / 8,58; AT/EP 8,58 por hora), art. 46 (contratos formativos)",
        "url": BOE + "BOE-A-2026-7296",
    },
    "lgss": {
        "title": "Texto refundido de la Ley General de la Seguridad Social, Real Decreto Legislativo 8/2015, consolidated text (última actualización publicada el 21/10/2026) — art. 19 bis (cotización adicional de solidaridad), art. 127 bis (mecanismo de equidad intergeneracional, en los supuestos en que se cotice por jubilación), art. 152 (exención de contingencias comunes salvo IT, desempleo, FOGASA y formación profesional a partir de la edad de jubilación del art. 205.1.a), salvo en las administraciones públicas), art. 153 (jubilación compatible con el trabajo: IT, AT/EP y solidaridad especial del 9%), art. 147 (base de cotización: las percepciones de vencimiento superior al mensual se prorratean a lo largo de los doce meses; 147.2.e: las horas extraordinarias solo computan en la base de AT/EP), disposición adicional sexagésima primera (tarifa de primas AT/EP)",
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
    "irregularReductionRate": ("0.30", "lirpf"),  # art. 18.2
    "irregularIncomeLimit": ("300000", "lirpf"),
    "terminationIrregularThreshold": ("700000", "lirpf"),
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
    "minimumRateShortContractCeuta": ("0.80", "rirpf"),  # art. 86.2: Ceuta and Melilla (and La Palma, DA 57ª LIRPF)
    "minimumRateSpecialCeuta": ("6.00", "rirpf"),
    "ceutaQuotaShare": ("0.40", "rirpf"),  # art. 80.2: the rate reduced by 60%
    "maximumRegularizedRate": ("47.00", "rirpf"),  # art. 87.5
    "maximumRegularizedRateCeuta": ("19.00", "rirpf"),
    "childMaintenanceMinimumIncrease": ("1980", "rirpf"),  # art. 85.2
    # Training contracts, Orden art. 46 first rule: monthly fixed quotas
    "trainingCommonEmployee": ("11.51", "orden-pjc-297-2026"),
    "trainingCommonEmployer": ("57.72", "orden-pjc-297-2026"),
    "trainingProfessionalEmployer": ("7.95", "orden-pjc-297-2026"),
    "trainingWageGuaranteeEmployer": ("4.38", "orden-pjc-297-2026"),
    "trainingVocationalEmployee": ("0.28", "orden-pjc-297-2026"),
    "trainingVocationalEmployer": ("2.16", "orden-pjc-297-2026"),
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
    "retiredIncapacityEmployeeRate": ("0.0025", "orden-pjc-297-2026"),
    "retiredIncapacityEmployerRate": ("0.013", "orden-pjc-297-2026"),
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
    if emp.get("retirementSituation") == "PAST_RETIREMENT_AGE":  # LGSS art. 152: only IT, 0.25%
        return 12 * cent(cc * D("0.0025"))
    if emp.get("trainingContract") == "true":  # Orden art. 46, first rule: 11.51 + 0.28 fixed, MEI and unemployment on the minimum
        return 12 * (D("11.51") + D("0.28") + cent(D("1424.40") * D("0.0015")) + cent(D("1424.40") * D("0.0155")))
    unemployment = D("0.016") if emp["unemploymentRate"] == "FIXED_TERM" else D("0.0155")
    month = (cent(cc * D("0.047")) + cent(cc * D("0.0015")) + cent(at * unemployment) + cent(at * D("0.001"))
             + cent(solidarity(monthly, True)))
    return 12 * month


def withholding_rate(emp, birth_year, year=2026, details=False):
    """The AEAT algorithm, step by step (RIRPF arts. 81-86). With details, also the figures a later
    regularization starts from: BASE, MINPERFA and CUOTA (0 when exempt)."""
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
    # IRREGULAR1: the art. 18.2 LIRPF reduction, 30% of the irregular income, at most 30% of 300,000 (RIRPF art. 83.3.a)
    irregular1 = min(trunc2(D(emp.get("irregularIncomeAmount", "0")) * D("0.30")), D(90000))
    rnt = max(retrib - irregular1 - cotizaciones, D(0))
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
    redu = (D(600) if numdes > 2 else D(0)) + D(emp.get("spousalPension", "0"))
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
    n65 = int(emp.get("ascendants65", "0")) + int(emp.get("ascendantsDisabledUnder65", "0"))
    n75 = int(emp.get("ascendants75", "0"))
    as65 = sum((D(1150) / conv for _ in range(n65 + n75)), D(0))
    as75 = sum((D(1400) / conv for _ in range(n75)), D(0))
    minas = cent(as65) + cent(as75)
    disper = {"FROM_65": D(9000), "FROM_33": D(3000), "FROM_33_REDUCED_MOBILITY": D(3000)}.get(disability, D(0))
    asisper = D(3000) if disability in ("FROM_65", "FROM_33_REDUCED_MOBILITY") else D(0)
    d33, d33m, d65 = (int(emp.get(k, "0")) for k in ("descendantsDisability33", "descendantsDisability33Mobility", "descendantsDisability65"))
    a33, a33m, a65 = (int(emp.get(k, "0")) for k in ("ascendantsDisability33", "ascendantsDisability33Mobility", "ascendantsDisability65"))
    disdes = cent(sum([D(3000) * entero] * (d33 + d33m) + [D(9000) * entero] * d65, D(0)))
    disas = cent(sum([D(3000) / conv] * (a33 + a33m) + [D(9000) / conv] * a65, D(0)))
    asisdes = cent(sum([D(3000) * entero] * (d33m + d65), D(0)))
    asisas = cent(sum([D(3000) / conv] * (a33m + a65), D(0)))
    minperfa = mincon + mindes + minas + disper + asisper + disdes + disas + asisdes + asisas
    # TABLA 1
    col = 2 if numdes >= 2 else numdes
    limit = D(EXEMPT[emp["familySituation"]][col])
    exempt = retrib <= limit
    if exempt:
        cuota = D(0)
    else:
        anualidades = D(emp.get("childMaintenance", "0"))
        if anualidades > 0 and base - anualidades > 0:
            c1, c2 = escala(base - anualidades) + escala(anualidades), escala(minperfa + 1980)
        else:
            c1, c2 = escala(base), escala(minperfa)
        cuota = c1 - c2 if c1 > c2 else D(0)
        if retrib <= D(35200):
            cuota = min(cuota, (retrib - limit) * D("0.43"))
    minopago = trunc2(D("0.02") * retrib) if emp.get("housingLoan") == "true" and retrib < D("33007.20") else D(0)
    ceuta = emp.get("taxTerritory") == "CEUTA_MELILLA_LA_PALMA"
    dif = max((cuota * D("0.40") if ceuta else cuota) - minopago, D(0))
    tipo = trunc2(dif / retrib * 100)
    contract = emp["withholdingContract"]
    special, short = (D("6.00"), D("0.80")) if ceuta else (D("15.00"), D("2.00"))
    if contract == "SPECIAL_RELATIONSHIP" and tipo < special:
        tipo = special
    elif contract == "UNDER_ONE_YEAR" and tipo < short:
        tipo = short
    if details:
        return {"tipo": tipo, "base": base, "minperfa": minperfa, "cuota": cuota, "retrib": retrib, "ceuta": ceuta}
    return tipo


def minimum_rate(emp, tipo):
    """RIRPF art. 86.2 minimums (0.8% / 6% for Ceuta, Melilla and La Palma)."""
    ceuta = emp.get("taxTerritory") == "CEUTA_MELILLA_LA_PALMA"
    special, short = (D("6.00"), D("0.80")) if ceuta else (D("15.00"), D("2.00"))
    if emp["withholdingContract"] == "SPECIAL_RELATIONSHIP" and tipo < special:
        return special
    if emp["withholdingContract"] == "UNDER_ONE_YEAR" and tipo < short:
        return short
    return tipo


def regularization_causes(emp, prior, now):
    """RIRPF art. 87.2 causes as the AEAT service numbers them, from what changed since the determination
    kept in `prior`: 1 base, 2 minimum, 3 spousal pension, 4 child maintenance, 5 situation 2 to 3; 11 otherwise."""
    causes = []
    if now["base"] != prior["withholdingBase"]:
        causes.append(1)
    if now["minperfa"] != prior["personalFamilyMinimum"]:
        causes.append(2)
    spousal, maintenance = D(emp.get("spousalPension", "0")), D(emp.get("childMaintenance", "0"))
    if spousal > 0 and spousal != prior["spousalPensionDetermined"]:
        causes.append(3)
    if maintenance > 0 and maintenance != prior["childMaintenanceDetermined"]:
        causes.append(4)
    if prior["familySituationCode"] == 2 and emp["familySituation"] == "3":
        causes.append(5)
    return causes or [11]


def regularized_rate(emp, birth_year, prior, percibido, retenido):
    """RIRPF art. 87.3 and 87.5 as the AEAT algorithm's REGULARIZACIÓN section computes them, without the
    housing-loan and Ceuta-change branches: (TIPOREG, IMPORTE)."""
    now = withholding_rate(emp, birth_year, details=True)
    retrib, ceuta = now["retrib"], now["ceuta"]
    importereg = now["cuota"] * D("0.40") if ceuta else now["cuota"]
    tiporeg = trunc2(max((importereg - retenido) / (retrib - percibido) * 100, D(0)))
    if ceuta and tiporeg > 19:
        tiporeg = D("19.00")
    elif tiporeg > 47:
        tiporeg = D("47.00")
    else:
        tiporeg = minimum_rate(emp, tiporeg)
    importe = cent((retrib - percibido) * tiporeg / 100 + retenido)
    causes = regularization_causes(emp, prior, now)
    if any(c >= 5 for c in causes):
        return tiporeg, importe
    tipoa, importea = prior["appliedRate"], prior["annualWithholding"]
    diferencia = max(prior["withholdingBase"] - prior["personalFamilyMinimum"], D(0))
    nueva = now["base"] - now["minperfa"]
    revisar = False
    if diferencia >= nueva and tiporeg > tipoa:  # 87.5, first paragraph
        tiporeg, revisar = tipoa, True
    elif diferencia < nueva and importea < importe:  # 87.5, second paragraph
        incremento, incremento_base = importe - importea, nueva - diferencia
        if incremento > incremento_base:
            revisar = True
            importe = importea + incremento_base
            tiporeg = max((importe - retenido) / (retrib - percibido) * 100, D(0))
            tiporeg = trunc2(tiporeg) if tiporeg > 0 else D(0)
            importe = (retrib - percibido) * tiporeg / 100 + retenido
            if importe - importea > incremento_base and tiporeg > 0:
                tiporeg = trunc2((importea + incremento_base - retenido) / (retrib - percibido) * 100)
    if revisar:
        tiporeg = minimum_rate(emp, tiporeg)
        importe = cent((retrib - percibido) * tiporeg / 100 + retenido)
    return tiporeg, importe


def determination_memo(emp, birth_year, rate=None):
    """What the template keeps from a first determination (its remember steps), as #ytd.last keys."""
    d = withholding_rate(emp, birth_year, details=True)
    rate = withholding_rate(emp, birth_year) if rate is None else D(rate)
    memo = {"annualPayDetermined": d["retrib"], "withholdingBase": d["base"], "personalFamilyMinimum": d["minperfa"],
            "withholdingRate": withholding_rate(emp, birth_year), "familySituationCode": D(emp["familySituation"]),
            "spousalPensionDetermined": D(emp.get("spousalPension", "0")), "childMaintenanceDetermined": D(emp.get("childMaintenance", "0")),
            "housingLoanFlag": D(1) if emp.get("housingLoan") == "true" else D(0), "ceutaFlag": D(1) if d["ceuta"] else D(0),
            "automaticRate": rate, "appliedRate": rate, "annualWithholding": cent(d["retrib"] * rate / 100)}
    return {"last:" + k: str(v) for k, v in memo.items()}


def months_paid(emp, birth_year, months, rate=None, monthly=None):
    """Year to date after `months` posted months of annualPay / 12 at the determination's rate, with its memo."""
    memo = determination_memo(emp, birth_year, rate)
    pay = D(monthly) if monthly else cent(D(emp["annualPay"]) / 12)
    r = D(memo["last:appliedRate"])
    return {**memo, "incomeTax.TAX.base": str(pay * months), "incomeTax.TAX.amount": str(cent(pay * r / 100) * months),
            "incomeTax.TAX.periods": str(months), "workAccident.EMPLOYER.periods": str(months)}


def automatic_rate(emp, birth_year, ytd):
    """The rate before any override: the computed one at the first determination, the kept one while
    nothing changes, the regularized one (RIRPF art. 87) once the data change."""
    prior = {k[len("last:"):]: D(v) for k, v in (ytd or {}).items() if k.startswith("last:")}
    fresh = withholding_rate(emp, birth_year)
    if emp.get("withholdingRateOverrideReason") == "REGULARIZED":
        return D(emp["withholdingRateOverride"])
    if not prior:
        return fresh
    now = withholding_rate(emp, birth_year, details=True)
    flags = {"annualPayDetermined": now["retrib"], "withholdingBase": now["base"], "personalFamilyMinimum": now["minperfa"],
             "withholdingRate": fresh, "familySituationCode": D(emp["familySituation"]),
             "spousalPensionDetermined": D(emp.get("spousalPension", "0")), "childMaintenanceDetermined": D(emp.get("childMaintenance", "0")),
             "housingLoanFlag": D(1) if emp.get("housingLoan") == "true" else D(0), "ceutaFlag": D(1) if now["ceuta"] else D(0)}
    if all(prior[k] == v for k, v in flags.items()):
        return prior["automaticRate"]
    bf = emp.get("broughtForwardYear") == "2026"
    percibido = D((ytd or {}).get("incomeTax.TAX.base", "0")) + (D(emp.get("broughtForwardPay", "0")) if bf else 0)
    retenido = D((ytd or {}).get("incomeTax.TAX.amount", "0")) + (D(emp.get("broughtForwardWithheld", "0")) if bf else 0)
    return regularized_rate(emp, birth_year, prior, percibido, retenido)[0]


def applied_rate(emp, birth_year, ytd=None):
    tipo = automatic_rate(emp, birth_year, ytd)
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
    # The month's pay from the earnings subject to social contributions, without the extra payments and overtime
    # (their own rules apply); a component not marked social contributes nothing here.
    social_by = c.get("socialEarningsByComponent", by)
    pay = D(c.get("socialEarnings", gross)) - D(social_by.get("BONUS", "0")) - D(social_by.get("OT", "0"))
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
        if emp.get("trainingContract") == "true":
            # Orden art. 46, first rule: fixed quotas; MEI (third rule) and unemployment (art. 33.2.a.1) on the minimum bases
            add("trainingCommonContingencies", "EMPLOYEE", D("11.51"))
            add("trainingCommonContingencies", "EMPLOYER", D("57.72"))
            add("intergenerationalEquity", "EMPLOYEE", cent(cc * D("0.0015")))
            add("intergenerationalEquity", "EMPLOYER", cent(cc * D("0.0075")))
            add("unemployment", "EMPLOYEE", cent(at * D("0.0155")))
            add("unemployment", "EMPLOYER", cent(at * D("0.055")))
            add("trainingWageGuaranteeFund", "EMPLOYER", D("4.38"))
            add("trainingVocationalTraining", "EMPLOYEE", D("0.28"))
            add("trainingVocationalTraining", "EMPLOYER", D("2.16"))
            add("trainingWorkAccident", "EMPLOYER", D("7.95"))
        elif emp.get("retirementSituation") == "PAST_RETIREMENT_AGE":
            # LGSS art. 152: exempt from common contingencies (but IT), unemployment, FOGASA and training;
            # Orden art. 32.1.a): IT 1.55% (1.30 / 0.25); no MEI without retirement contributions (art. 127 bis)
            add("temporaryIncapacity", "EMPLOYEE", cent(cc * D("0.0025")))
            add("temporaryIncapacity", "EMPLOYER", cent(cc * D("0.013")))
        else:
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
        if emp.get("trainingContract") != "true":
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
        rate = applied_rate(emp, int(c["birthDate"][:4]), c.get("ytd"))
        add("incomeTax", "TAX", cent(gross * rate / 100), keep_zero=True)
    return lines


def case(name, evidence, gross, employee, start="2026-03-01", end="2026-03-31", payment=None, whole=True,
         error=None, by_component=None, birth="1985-06-15", employer=None, ytd=None, pre_tax=None,
         reductions=None, fte=None, social=None, social_by=None, employed=None):
    c = {"name": name, "evidence": evidence, "periodStart": start, "periodEnd": end, "frequency": "MONTHLY"}
    if payment:
        c["paymentDate"] = payment
    c["grossEarnings"] = gross
    if by_component:
        c["earningsByComponent"] = by_component
    if social is not None:
        c["socialEarnings"] = social
        c["socialEarningsByComponent"] = social_by
    if reductions:
        c["reductions"] = reductions
    if pre_tax:
        c["preTaxDeductions"] = pre_tax
    c["fullPeriod"] = whole and fte is None
    if not whole:
        c["employedThroughout"] = False
    if birth:
        c["birthDate"] = birth
    if employed:
        c["employmentStartDate"] = employed
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


def group2_cases(law):
    """Disabled dependants, court-ordered payments, Ceuta / Melilla / La Palma and training contracts."""
    two = dict(familySituation="2", descendants="2", descendantsComputation="WHOLE")
    return [
        case("descendants with a disability, by whole", "LIRPF art. 60.2: one child 33-65% with reduced mobility (3,000 + 3,000), one 65% or more "
             "(9,000 + 3,000); descendant minimum 2,400 + 2,700. " + law, "5000.00",
             std(annualPay="60000.00", annualSocialContributions="3890.00", contributionGroup="1", descendantsDisability33Mobility="1", descendantsDisability65="1", dependantsWithDisability="true", **two)),
        case("a descendant with a disability, by half", "RIRPF art. 84.2: the disability minimum halves with the descendant's. " + law, "2600.00",
             std(annualPay="31200.00", annualSocialContributions="2000.00", familySituation="3", descendants="1", descendantsComputation="HALF", descendantsDisability33="1")),
        case("ascendants with a disability, shared by two", "LIRPF arts. 59-61: a parent of 60 with 33-65% and one of 80 with 65% or more, living with the employee and "
             "a sibling: (1,150 x 2 + 1,400 + 3,000 + 9,000 + 3,000) / 2. " + law, "2400.00",
             std(annualPay="28800.00", annualSocialContributions="1850.00", ascendantsDisabledUnder65="1", ascendants75="1", ascendantsSharedBy="2",
                 ascendantsDisability33="1", ascendantsDisability65="1", dependantsWithDisability="true")),
        case("compensatory pension to the spouse", "RIRPF art. 83.3: 6,000 a year off the base (algorithm CONYUGE). " + law, "3500.00",
             std(annualPay="42000.00", annualSocialContributions="2700.00", spousalPension="6000.00", courtOrderedPayments="true")),
        case("child maintenance below the base", "RIRPF art. 85.2: scale on 7,200 and on the rest separately, minimum + 1,980. " + law, "3500.00",
             std(annualPay="42000.00", annualSocialContributions="2700.00", childMaintenance="7200.00", courtOrderedPayments="true")),
        case("child maintenance not below the base", "RIRPF art. 85.2 applies only when the maintenance is below the base; otherwise the ordinary scale. " + law, "1500.00",
             std(annualPay="18000.00", annualSocialContributions="1170.00", childMaintenance="15000.00", courtOrderedPayments="true")),
        case("Ceuta resident working there", "RIRPF art. 80.2: the rate reduced by 60% (quota x 0.40 in the algorithm). " + law, "3000.00",
             std(annualPay="36000.00", annualSocialContributions="2330.00", taxTerritory="CEUTA_MELILLA_LA_PALMA")),
        case("La Palma, contract under a year: 0.8% minimum", "RIRPF art. 86.2: 0.8% instead of 2% for income with the art. 68.4 LIRPF deduction; DA 57ª LIRPF for La Palma. " + law,
             "1250.00", std(annualPay="15000.00", annualSocialContributions="975.00", taxTerritory="CEUTA_MELILLA_LA_PALMA", withholdingContract="UNDER_ONE_YEAR")),
        case("Melilla, special relationship: 6% minimum", "RIRPF art. 86.2: 6% instead of 15%. " + law, "1800.00",
             std(annualPay="21600.00", annualSocialContributions="1400.00", taxTerritory="CEUTA_MELILLA_LA_PALMA", withholdingContract="SPECIAL_RELATIONSHIP")),
        case("training contract at the minimum base", "Orden art. 46 first rule: 69.23 (57.72 + 11.51) common, 7.95 professional, 4.38 FOGASA, 2.44 (2.16 + 0.28) vocational "
             "training; unemployment (art. 33.2.a.1.º) and MEI (third rule) on 1,424.40. " + law, "1300.00",
             std(annualPay="15600.00", trainingContract="true", withholdingContract="UNDER_ONE_YEAR")),
        case("training contract with the contributions estimated", "RIRPF art. 83.2.a: the expected contributions are the training quotas x 12. " + law, "1424.40",
             std(annualPay="17092.80", trainingContract="true")),
    ]


def month(m):
    return {"start": f"2026-{m:02d}-01", "end": f"2026-{m:02d}-{[31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][m - 1]}"}


def regularization_cases(law):
    """RIRPF art. 87: the determination kept from earlier months (#ytd.last) and what changed since."""
    reg = "RIRPF art. 87.3 as the AEAT algorithm's REGULARIZACIÓN computes it: new annual quota less what was withheld, over the pay still to come; "
    aeat = " Regularized rate matching the AEAT calculation service with the same causes. " + law
    def contrib(pay):
        return str(cent(min(D(pay), D("73372.80")) * D("0.0648")))
    base = std(annualPay="28500.00", annualSocialContributions="1700.00")
    raise_ = dict(base, annualPay="33000.00", annualSocialContributions=contrib("33000.00"))
    two = std(annualPay="30000.00", annualSocialContributions="1944.00", familySituation="2")
    low = std(annualPay="30000.00", annualSocialContributions="1944.00")
    exempt = std(annualPay="15000.00", annualSocialContributions="972.00")
    bf = dict(raise_, broughtForwardYear="2026", broughtForwardMonths="3", broughtForwardPay="7125.00", broughtForwardWithheld="1137.15")
    return [
        case("a rate determined earlier is kept while nothing changes", "RIRPF art. 87: the rate changes only by regularization; the kept 17.50% "
             "applies although the data give 15.96% from scratch. " + law, "2375.00", base, birth="1980-02-01",
             ytd=months_paid(base, 1980, 4, rate="17.50"), employed="2020-01-01", **month(5)),
        case("raise from July: the rate is regularized", reg + "28,500 to 33,000 after six months at 15.96%." + aeat,
             "2750.00", raise_, birth="1980-02-01", ytd=months_paid(base, 1980, 6), employed="2020-01-01", **month(7)),
        case("new child after an earlier rate of 8%: not raised (art. 87.5, first paragraph)", reg + "the change only lowers base minus minimum, "
             "so the regularized 21.81% is limited to the 8% applied before." + aeat, "2500.00",
             dict(two, descendants="1", descendantsComputation="WHOLE"), ytd=months_paid(two, 1985, 6, rate="8.00"), employed="2020-01-01", **month(7)),
        case("raise after an earlier rate of 8%: increase capped (art. 87.5, second paragraph)", reg + "the extra annual withholding is limited "
             "to the increase in base minus minimum (935.20): 2,400 + 935.20 over the remaining pay, 13.34% instead of 25.05%." + aeat, "2583.33",
             dict(low, annualPay="31000.00", annualSocialContributions=contrib("31000.00")), ytd=months_paid(low, 1985, 6, rate="8.00"), employed="2020-01-01", **month(7)),
        case("spouse starts earning: situation 2 to 3 without the limits (cause 5)", reg + "the limits of art. 87.5 don't apply to this cause." + aeat,
             "2500.00", dict(two, familySituation="3"), ytd=months_paid(two, 1985, 5, rate="8.00"), employed="2020-01-01", **month(6)),
        case("contract becomes shorter than a year: the 2% minimum (cause 11)", reg + "nothing withheld at 0%, the minimum of RIRPF art. 86.2 applies." + aeat,
             "1250.00", dict(exempt, withholdingContract="UNDER_ONE_YEAR"), ytd=months_paid(exempt, 1985, 4), employed="2020-01-01", **month(5)),
        case("child maintenance ordered in June (cause 4)", reg + "RIRPF art. 85.2 from the new determination." + aeat, "2500.00",
             dict(low, childMaintenance="6000.00", courtOrderedPayments="true"), ytd=months_paid(low, 1985, 5), employed="2020-01-01", **month(6)),
        case("regularization counting pay before Humano", reg + "three months brought forward (7,125 paid, 1,137.15 withheld) and three posted "
             "months in Humano." + aeat, "2750.00", bf, birth="1980-02-01", ytd=months_paid(base, 1980, 3), employed="2020-01-01", **month(7)),
        case("rate requested higher than the regularized one", "RIRPF art. 88.5: the 25% the employee asked for applies over the regularized rate. " + law,
             "2750.00", dict(raise_, withholdingRateOverride="25.00", withholdingRateOverrideReason="REQUESTED_HIGHER"), birth="1980-02-01",
             ytd=months_paid(base, 1980, 6), employed="2020-01-01", **month(7)),
        case("Ceuta resident's raise: quota x 0.40 in the regularization", reg + "RIRPF art. 80.2 also applies to the new quota (IMPORTEREG = CUOTA x 0.40)." + aeat,
             "2750.00", dict(raise_, taxTerritory="CEUTA_MELILLA_LA_PALMA"), birth="1980-02-01",
             ytd=months_paid(dict(base, taxTerritory="CEUTA_MELILLA_LA_PALMA"), 1980, 6), employed="2020-01-01", **month(7)),
        case("rate requested below the regularized one", "RIRPF art. 88.5: a requested rate applies only when higher, so the regularized 18.64% applies over 17%. " + law,
             "2750.00", dict(raise_, withholdingRateOverride="17.00", withholdingRateOverrideReason="REQUESTED_HIGHER"), birth="1980-02-01",
             ytd=months_paid(base, 1980, 6), employed="2020-01-01", **month(7)),
        case("spouse starts earning and a raise: causes 1 and 5, without the limits", reg + "with cause 5 the limits of art. 87.5 don't apply." + aeat,
             "2750.00", dict(two, familySituation="3", annualPay="33000.00", annualSocialContributions=contrib("33000.00")),
             ytd=months_paid(two, 1985, 6, rate="8.00"), employed="2020-01-01", **month(7)),
        case("a rate regularized by hand carries over", "REGULARIZED: the employer's rate is used as entered. " + law, "2750.00",
             dict(raise_, withholdingRateOverride="18.40", withholdingRateOverrideReason="REGULARIZED"), birth="1980-02-01",
             ytd=months_paid(base, 1980, 6), employed="2020-01-01", **month(7)),
    ]


def regularization_errors():
    def contrib(pay):
        return str(cent(min(D(pay), D("73372.80")) * D("0.0648")))
    loan = std(annualPay="30000.00", annualSocialContributions="1944.00", housingLoan="true")
    base = std(annualPay="28500.00", annualSocialContributions="1700.00")
    return [
        case("regularization with the housing-loan reduction refused", "RIRPF art. 87.3.b): the reduction's own regularization is not modelled", "2700.00",
             dict(loan, annualPay="32400.00", annualSocialContributions=contrib("32400.00"), housingLoan="false"),
             ytd=months_paid(loan, 1985, 6), error="housing-loan", employed="2020-01-01", **month(7)),
        case("moving into the Ceuta regime refused", "RIRPF art. 87.2.9.º not modelled", "2375.00", dict(base, taxTerritory="CEUTA_MELILLA_LA_PALMA"),
             birth="1980-02-01", ytd=months_paid(base, 1980, 6), error="Ceuta", employed="2020-01-01", **month(7)),
        case("regularization with earlier months not in Humano", "RIRPF art. 87.3.b): everything withheld this year", "2750.00",
             dict(base, annualPay="33000.00", annualSocialContributions=contrib("33000.00")), birth="1980-02-01",
             ytd=months_paid(base, 1980, 3), error="brought-forward", employed="2020-01-01", **month(10)),
        case("annual pay not above what was already paid", "algorithm validation 34", "2375.00",
             dict(base, annualPay="14000.00", annualSocialContributions=contrib("14000.00")), birth="1980-02-01",
             ytd=months_paid(base, 1980, 6), error="already paid", employed="2020-01-01", **month(7)),
    ]


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
        case("extra pay on a Bonus component not marked subject to contributions: base unchanged",
             "The extra pay is in the monthly base through annualExtraPay / 12 whatever the tenant's component flag (LGSS art. 147); "
             "the month's ordinary pay is the social earnings without the extra payments. " + law,
             "4000.00", std(**fam), start="2026-06-01", end="2026-06-30", by_component={"BASIC": "2000.00", "BONUS": "2000.00"},
             social="2000.00", social_by={"BASIC": "2000.00"}),
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
        case("past retirement age: only IT and AT/EP",
             "LGSS art. 152: no common contingencies (but IT), unemployment, FOGASA or training; Orden art. 32.1.a): IT 1.30% / 0.25% on 2,000; no MEI (art. 127 bis); AT/EP remains. " + law,
             "2000.00", std(annualPay="24000.00", retirementSituation="PAST_RETIREMENT_AGE"), birth="1958-04-01"),
        case("year to date consistent with the rate, including a second run in a month",
             "Earlier lines at 15.96%: 2,375 x 3 months and a 1,000 extra run: 379.05 x 3 + 159.60 = 1,296.75 on 8,125. " + law,
             "2375.00", std(annualSocialContributions="1700.00"), birth="1980-02-01", start="2026-04-01", end="2026-04-30",
             ytd={"incomeTax.TAX.base": "8125.00", "incomeTax.TAX.amount": "1296.75", "incomeTax.TAX.periods": "4"}),
        # Irregular income (LIRPF art. 18.2; RIRPF arts. 12, 83.3.a): the annual pay includes it, the rate's base is reduced by 30% of it.
        case("payment for termination by mutual agreement in the annual pay",
             "RIRPF art. 12.1.f): notoriously irregular; 30% of 12,000 = 3,600 off the base (algorithm IRREGULAR1). " + law,
             "3000.00", std(annualPay="45000.00", irregularIncome="true", irregularIncomeAmount="12000.00", annualSocialContributions="2100.00")),
        case("bonus generated over more than two years, family situation 2",
             "LIRPF art. 18.2: 30% of 60,000 = 18,000 off the base; two children, one under 3, by whole. " + law,
             "7500.00", std(annualPay="150000.00", contributionGroup="1", irregularIncomeAmount="60000.00", annualSocialContributions="4900.00",
                            familySituation="2", descendants="2", descendantsUnder3="1", descendantsComputation="WHOLE")),
        case("irregular income above 300,000: the reduction stops at 90,000",
             "LIRPF art. 18.2: the income with the reduction is at most 300,000 a year; algorithm validation 19. " + law,
             "25000.00", std(annualPay="600000.00", contributionGroup="1", irregularIncomeAmount="400000.00", annualSocialContributions="4900.00")),
        case("irregular income lowering the base into the work income reduction",
             "Without it RNT = 24,000 - 1,600 = 22,400, above 19,747.50 (no art. 20 reduction); 30% of 9,000 = 2,700 brings RNT to 19,700, "
             "which gets 2,364.34 - 1.14 x (19,700 - 17,673.52) = 54.15. " + law,
             "2000.00", std(annualPay="24000.00", irregularIncomeAmount="9000.00", annualSocialContributions="1600.00")),
        case("irregular income on a contract under a year: the 2% minimum",
             "RIRPF art. 86.2: 3.76% without the irregular income; with 30% of 6,000 off the base the computed rate falls below 2%, so 2% applies. " + law,
             "1450.00", std(annualPay="17400.00", irregularIncomeAmount="6000.00", withholdingContract="UNDER_ONE_YEAR", annualSocialContributions="1127.52")),
    ]
    computed += group2_cases(law) + regularization_cases(law)
    for c in computed:
        c["expectLines"] = expected_lines(c)
    zero = case("no pay, no lines", "nothing is paid, so nothing is due", "0", std())
    zero["expectLines"] = []
    errors = [
        case("annual pay is required", "RIRPF art. 83.2: the rate needs the expected annual pay", "2000.00",
             {k: v for k, v in std().items() if k != "annualPay"}, error="annualPay"),
        case("Basque Country workplace refused", "Concierto Económico art. 7.Uno.a)", "2000.00", std(taxTerritory="BASQUE_COUNTRY"), error="Concierto"),
        case("Navarre workplace refused", "Convenio Económico art. 10.1.a)", "2000.00", std(taxTerritory="NAVARRE"), error="Convenio"),
        case("daily-base group refused", "Orden art. 3: groups 8-11 contribute by day", "2000.00", std(contributionGroup="9"), error="groups 8 to 11"),
        case("overtime without regular pay", "the bases start from the month's ordinary pay", "300.00", std(), by_component={"OT": "300.00"}, error="Overtime"),
        case("descendants without how they count", "RIRPF art. 84.2: by half unless the employee has the exclusive right", "2000.00", std(descendants="1"), error="descendantsComputation"),
        case("part-time without hours", "Orden art. 39: minimum = hours x hourly base", "1000.00", std(), fte="0.5", error="partTimeHours"),
        case("part-time without declared contributions", "the estimate assumes full-time bases", "1000.00", std(partTimeHours="80"), fte="0.5", error="annualSocialContributions"),
        case("partial month refused", "bases are prorated by days", "1000.00", std(), whole=False, error="whole month"),
        case("unpaid leave refused", "Orden art. 7", "1500.00", std(), reductions="500.00", error="Unpaid, sick and birth leave are not supported"),
        dict(case("paid sick leave refused", "Orden art. 6.2: temporary incapacity contributes on the previous month's base", "2000.00", std(),
                  error="Unpaid, sick and birth leave are not supported"), leavePay={"SICK": "400.00"}, leaveDays={"SICK": "7"}, fullPeriod=False),
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
        case("training contract in group 2 refused", "Orden art. 46: 'base mínima' of the regime or of the group is not settled", "1700.00",
             std(trainingContract="true", contributionGroup="2"), error="groups 4 to 7"),
        case("training contract with overtime refused", "ET art. 11.2: no overtime in training contracts but force majeure", "1400.00",
             std(trainingContract="true"), by_component={"BASIC": "1300.00", "OT": "100.00"}, error="without overtime"),
        case("training contract above the minimum base refused", "Orden art. 46 second and third rules", "1600.00",
             std(trainingContract="true", annualPay="19200.00"), error="above the minimum base"),
        case("working pensioner refused", "LGSS art. 153", "2000.00", std(retirementSituation="WORKING_PENSIONER"), birth="1958-04-01", error="art. 153"),
        case("past retirement age above the maximum base refused", "solidarity for exempt employees not settled", "6000.00",
             std(annualPay="72000.00", contributionGroup="1", retirementSituation="PAST_RETIREMENT_AGE"), birth="1958-04-01", error="past retirement age"),
        case("multiple employment refused", "Orden art. 10", "2000.00", std(multipleEmployment="true"), error="multiple employment"),
        case("dependants with a disability without how many", "LIRPF art. 60.2 needs the grade of each", "2000.00",
             std(descendants="1", descendantsComputation="WHOLE", dependantsWithDisability="true"), error="descendantsDisability33"),
        case("more disabled descendants than descendants", "they are among the descendants", "2000.00",
             std(descendants="1", descendantsComputation="WHOLE", descendantsDisability65="2"), error="cannot be more"),
        case("disabled ascendant under 65 without a grade", "LIRPF art. 59.1: under 65 only with a disability", "2000.00",
             std(ascendantsDisabledUnder65="1"), error="disability grade"),
        case("court-ordered payments without the amounts", "RIRPF arts. 83.3, 85.2", "2000.00", std(courtOrderedPayments="true"), error="spousalPension"),
        case("irregular income without its amount", "RIRPF art. 83.3.a) needs the amount with the reduction", "2000.00", std(irregularIncome="true"),
             error="irregularIncomeAmount"),
        case("irregular income larger than the annual pay", "it is part of the annual pay", "2000.00", std(irregularIncomeAmount="30000.00"),
             error="cannot be larger"),
        case("irregular income above 700,000 refused", "LIRPF art. 18.2: termination pay limit not modelled", "60000.00",
             std(annualPay="900000.00", contributionGroup="1", irregularIncomeAmount="750000.00", irregularIncome="true"), error="700,000"),
        case("no birth date", "the minimum depends on the year of birth", "2000.00", std(), birth=None, error="birth date"),
        case("paid in the next year", "RIRPF art. 78: withholding follows the payment, 2027 rules", "2000.00", std(),
             start="2026-12-01", end="2026-12-31", payment="2027-01-05", error="same year"),
        case("pre-tax deductions are refused", "not modelled", "2000.00", std(), pre_tax="50.00", error="Pre-tax deductions are not supported"),
        case("2027 is not covered", "2027 values not yet set", "2000.00", std(), start="2027-01-01", end="2027-01-31", error="a newer template version is needed"),
    ] + regularization_errors()
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
CEUTA = "(" + emp_key("taxTerritory") + " == 'CEUTA_MELILLA_LA_PALMA')"
TRAINING = emp_key("trainingContract")
OV = emp_key("withholdingRateOverrideReason")


def LAST(step):
    """The value a remember step kept in the latest earlier posted period (null at the first)."""
    return "#ytd.last('" + step + "')"


# RIRPF art. 86.2 minimums applied to a rate expression ({0})
MIN_RATE = ("(" + emp_key("withholdingContract") + " == 'UNDER_ONE_YEAR' ? #max({0}, " + CEUTA + " ? #p['minimumRateShortContractCeuta'] : #p['minimumRateShortContract'])"
            " : (" + emp_key("withholdingContract") + " == 'SPECIAL_RELATIONSHIP' ? #max({0}, " + CEUTA + " ? #p['minimumRateSpecialCeuta'] : #p['minimumRateSpecial']) : {0}))")


def main():
    template = {
        "schemaVersion": 1,
        "id": "es-employment",
        "name": "Spain — employment (Régimen General, territorio común)",
        "version": "2026.1.4",
        "status": "LAW_DERIVED",
        "jurisdiction": {"country": "ES"},
        "validity": {"from": FROM, "to": TO},
        "currency": "EUR",
        "payFrequencies": ["MONTHLY"],
        "taxYearStart": "--01-01",
        "dateBasis": "PERIOD",
        "rounding": {"scale": 2, "mode": "HALF_UP"},
        "changelog": [
            {"version": "2026.1.4", "date": "2026-10-25",
             "summary": "Sick and birth leave paid through a leave pay rule are refused like unpaid leave: temporary incapacity contributes on the previous month's base and birth leave on the base of the month before that (Orden PJC/297/2026 art. 6), unpaid days on the minimum base (art. 7); the refusal now names them. Before, a fully paid sick day was calculated as an ordinary month."},
            {"version": "2026.1.3", "date": "2026-10-25",
             "summary": "Descendants and ascendants with a disability (LIRPF art. 60.2), court-ordered spousal pension and child maintenance (RIRPF arts. 83.3, 85.2), residents of Ceuta, Melilla and La Palma working there (RIRPF arts. 80.2, 86.2) and training contracts paid at the minimum base (Orden art. 46 fixed quotas); before, all of them were refused. The withholding rate is regularized automatically when the data change during the year (RIRPF art. 87.3 with the limits of art. 87.5, from the determination kept with each result); before, the run was refused until the regularized rate was entered."},
            {"version": "2026.1.2", "date": "2026-10-25",
             "summary": "Irregular income: the annual pay may include income generated over more than two years or notoriously irregular (irregularIncomeAmount); the withholding rate's base is reduced by 30% of it, up to 300,000 EUR of income (LIRPF art. 18.2, RIRPF art. 83.3.a), as the AEAT algorithm's IRREGULAR1. Before, irregularIncome was refused."},
            {"version": "2026.1.1", "date": "2026-10-25",
             "summary": "The month's ordinary pay for the contribution bases is the social earnings less the part of the extra payments and overtime that is itself in them. Before, a Bonus component not marked subject to social contributions was subtracted twice, which could leave a month with an extra pay without common-contingency contributions."},
            {"version": "2026.1.0", "date": RETRIEVED,
             "summary": "2026: IRPF withholding rate per the AEAT algorithm (RIRPF arts. 81-86) with a year-to-date check that asks for the regularized rate; social security per Orden PJC/297/2026: common contingencies 28.30%, MEI 0.90%, unemployment 7.05% / 8.30%, FOGASA 0.20%, vocational training 0.70%, AT/EP from the tariff, solidarity contribution above 5,101.20, overtime in the AT/EP base with its 28.30% / 14% additional contribution, the LGSS art. 152 exemption past retirement age (IT 1.55% only). Withholding rates checked against the AEAT calculation service."}
        ],
        "sources": SOURCES,
        "inputs": {
            "employer": [
                {"key": "accidentRate", "type": "DECIMAL", "required": True, "min": 0, "max": "0.2",
                 "description": "AT/EP contribution rate (IT + IMS, as a fraction, e.g. 0.015 for 1.50%) from the tariff of the LGSS disposición adicional sexagésima primera for the company's activity (Orden PJC/297/2026 art. 4.b); an employee's own rate overrides it"},
            ],
            "employee": [
                {"key": "taxTerritory", "type": "ENUM", "required": True, "values": ["COMMON", "BASQUE_COUNTRY", "NAVARRE", "CEUTA_MELILLA_LA_PALMA"],
                 "description": "Where the withholding belongs: COMMON for work performed in the common territory; CEUTA_MELILLA_LA_PALMA for an employee resident in Ceuta, Melilla or La Palma whose work income is obtained there, withheld at the rate reduced by 60% (RIRPF art. 80.2; DA 57ª LIRPF for La Palma) — a resident working elsewhere is COMMON. Work performed in the Basque Country or Navarre is withheld under the foral rules (Concierto art. 7.Uno.a; Convenio art. 10.1.a) and is refused"},
                {"key": "annualPay", "type": "DECIMAL", "required": True, "min": "0.01",
                 "description": "Expected gross pay from this employer in 2026 (RETRIB, RIRPF art. 83.2): fixed and foreseeable variable pay, in cash and in kind, without arrears belonging to earlier years. For a contract renewed in the year, include what was paid earlier in the year"},
                {"key": "annualExtraPay", "type": "DECIMAL", "required": False, "defaultValue": "0", "min": 0,
                 "description": "Annual amount of the extra payments and other pay accruing over periods longer than a month (pagas extraordinarias), prorated over 12 months in the social security base (LGSS art. 147; Orden art. 1). Pay them as BONUS"},
                {"key": "annualSocialContributions", "type": "DECIMAL", "required": False, "min": 0,
                 "description": "Expected employee social security contributions for the year (COTIZACIONES, RIRPF art. 83.2.a). Without it the template estimates 12 months of contributions on annualPay / 12; required for part-time employees"},
                {"key": "familySituation", "type": "ENUM", "required": True, "values": ["1", "2", "3"],
                 "description": "Family situation from the employee's modelo 145 (RIRPF art. 81.1): 1 single, widowed, divorced or separated with descendants and the single-parent joint-filing reduction; 2 married with a spouse earning no more than 1,500 EUR a year; 3 any other (and when not stated)"},
                {"key": "descendants", "type": "INTEGER", "required": False, "defaultValue": "0", "min": 0, "max": 16,
                 "description": "Children and other descendants giving right to the minimum (LIRPF art. 58): under 25 at the end of 2026 (born 2002 or later), or of any age with a disability, living with the employee, earning no more than 8,000 EUR and not filing a return with more than 1,800 EUR (LIRPF art. 61.2.ª). Children receiving court-ordered maintenance from the employee do not count (RIRPF art. 85.2)"},
                {"key": "descendantsUnder3", "type": "INTEGER", "required": False, "defaultValue": "0", "min": 0, "max": 16,
                 "description": "Of those descendants, the ones born in 2024 or later, or adopted or fostered in 2024 or later (+2,800 each, LIRPF art. 58.2)"},
                {"key": "descendantsComputation", "type": "ENUM", "required": False, "values": ["WHOLE", "HALF"],
                 "description": "How the descendants count, from modelo 145 (required with descendants): HALF unless the employee alone is entitled to their whole minimum (RIRPF art. 84.2; LIRPF art. 61.1.ª); all of them are taken the same way"},
                {"key": "ascendants65", "type": "INTEGER", "required": False, "defaultValue": "0", "min": 0, "max": 6,
                 "description": "Parents or other ascendants aged 65 to 74 in 2026 (born 1952 to 1961) living with the employee at least half the year, earning no more than 8,000 EUR and not filing a return with more than 1,800 EUR (LIRPF arts. 59, 61.2.ª)"},
                {"key": "ascendants75", "type": "INTEGER", "required": False, "defaultValue": "0", "min": 0, "max": 6,
                 "description": "Ascendants aged 75 or more in 2026 (born 1951 or earlier), same conditions (+1,400 each, LIRPF art. 59.2)"},
                {"key": "ascendantsDisabledUnder65", "type": "INTEGER", "required": False, "defaultValue": "0", "min": 0, "max": 6,
                 "description": "Ascendants under 65 in 2026 with a disability of 33% or more, same conditions; they give right to the ascendant minimum (LIRPF art. 59.1) and are counted again in ascendantsDisability*"},
                {"key": "ascendantsSharedBy", "type": "INTEGER", "required": False, "defaultValue": "1", "min": 1, "max": 10,
                 "description": "Number of descendants (the employee included) the ascendants live with, who share their minimum (LIRPF art. 61.1.ª); the same for all of them"},
                {"key": "descendantsDisability33", "type": "INTEGER", "required": False, "defaultValue": "0", "min": 0, "max": 16,
                 "description": "Of the descendants, those with a disability from 33% to under 65% without reduced mobility or need of help (+3,000 each, LIRPF art. 60.2)"},
                {"key": "descendantsDisability33Mobility", "type": "INTEGER", "required": False, "defaultValue": "0", "min": 0, "max": 16,
                 "description": "Of the descendants, those with a disability from 33% to under 65% with reduced mobility or needing help from another person (+3,000 and +3,000 assistance each)"},
                {"key": "descendantsDisability65", "type": "INTEGER", "required": False, "defaultValue": "0", "min": 0, "max": 16,
                 "description": "Of the descendants, those with a disability of 65% or more (+9,000 and +3,000 assistance each)"},
                {"key": "ascendantsDisability33", "type": "INTEGER", "required": False, "defaultValue": "0", "min": 0, "max": 6,
                 "description": "Of the ascendants counted (65 to 74, 75 or more, disabled under 65), those with a disability from 33% to under 65% without reduced mobility (+3,000 each, shared like their minimum)"},
                {"key": "ascendantsDisability33Mobility", "type": "INTEGER", "required": False, "defaultValue": "0", "min": 0, "max": 6,
                 "description": "Of the ascendants counted, those with a disability from 33% to under 65% with reduced mobility or needing help (+3,000 and +3,000 assistance each, shared)"},
                {"key": "ascendantsDisability65", "type": "INTEGER", "required": False, "defaultValue": "0", "min": 0, "max": 6,
                 "description": "Of the ascendants counted, those with a disability of 65% or more (+9,000 and +3,000 assistance each, shared)"},
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
                 "description": "REGULARIZED: a rate the employer regularized under RIRPF art. 87 (used as entered and carried on; needed only where the template doesn't regularize: housing-loan reduction, moving into or out of Ceuta, Melilla or La Palma, or no kept determination yet); REQUESTED_HIGHER: a higher rate the employee asked for in writing (RIRPF art. 88.5; used when above the computed one)"},
                {"key": "broughtForwardYear", "type": "INTEGER", "required": False, "min": 2000, "max": 2100,
                 "description": "Year the brought-forward figures belong to (pay with you this year before the first payroll in Humano); other years' figures are ignored. Needed to regularize the rate when earlier months are not in Humano"},
                {"key": "broughtForwardMonths", "type": "INTEGER", "required": False, "min": 0, "max": 11,
                 "description": "Months paid by you this year before the first payroll in Humano"},
                {"key": "broughtForwardPay", "type": "DECIMAL", "required": False, "min": 0,
                 "description": "Work income paid by you this year before the first payroll in Humano (retribuciones satisfechas)"},
                {"key": "broughtForwardWithheld", "type": "DECIMAL", "required": False, "min": 0,
                 "description": "IRPF withheld by you on it (retenciones practicadas)"},
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
                {"key": "retirementSituation", "type": "ENUM", "required": False, "defaultValue": "NONE", "values": ["NONE", "PAST_RETIREMENT_AGE", "WORKING_PENSIONER"],
                 "description": "PAST_RETIREMENT_AGE: the employee has reached the ordinary retirement age of LGSS art. 205.1.a) and does not work for a public administration — exempt from common contingencies except IT, unemployment, FOGASA and vocational training (LGSS art. 152); WORKING_PENSIONER: draws a retirement pension compatible with the work (art. 153, 9% solidarity) — not supported"},
                {"key": "trainingContract", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "Training contract (formación en alternancia, or a remaining contrato para la formación y el aprendizaje): fixed monthly quotas of Orden art. 46 while the common-contingencies base does not exceed the minimum base; full-time, groups 4 to 7, no overtime"},
                {"key": "multipleEmployment", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "The employee also works for another company (pluriempleo), sharing the maximum base (Orden art. 10) — not supported"},
                {"key": "dependantsWithDisability", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "A descendant or ascendant has a disability (LIRPF art. 60.2): enter them in descendantsDisability* / ascendantsDisability*"},
                {"key": "courtOrderedPayments", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "The employee pays a court-ordered compensatory pension to a spouse or maintenance to children: enter spousalPension and/or childMaintenance"},
                {"key": "spousalPension", "type": "DECIMAL", "required": False, "defaultValue": "0", "min": 0,
                 "description": "Compensatory pension to the spouse fixed by court order, annual amount (reduces the base, RIRPF art. 83.3; algorithm CONYUGE)"},
                {"key": "childMaintenance", "type": "DECIMAL", "required": False, "defaultValue": "0", "min": 0,
                 "description": "Maintenance for children fixed by court order, annual amount (anualidades por alimentos, RIRPF art. 85.2): when below the base, the scale applies separately to it and to the rest, and the minimum is increased by 1,980; those children do not count as descendants"},
                {"key": "irregularIncome", "type": "BOOLEAN", "required": False, "defaultValue": "false",
                 "description": "The annual pay includes income with the 30% reduction (LIRPF art. 18.2): set irregularIncomeAmount"},
                {"key": "irregularIncomeAmount", "type": "DECIMAL", "required": False, "min": 0,
                 "description": "Part of annualPay with the 30% reduction of LIRPF art. 18.2, imputed in this single year: income generated over more than two years (for termination pay, the years of service), unless you applied the reduction to such income of this employee in the five previous years (RIRPF art. 83.3.a), or income notoriously irregular in time (RIRPF art. 12.1: relocation pay above the exempt amounts, payments for termination by mutual agreement, non-exempt literary, artistic or scientific prizes, ...). Paid in instalments only within RIRPF art. 12.2. At most 700,000"},
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
            {"id": "noUnpaidLeave", "type": "REQUIRE", "source": "orden-pjc-297-2026", "condition": "#reductions == 0 and #leavePay['SICK'] == 0 and #leavePay['MATERNITY'] == 0",
             "message": "Unpaid, sick and birth leave are not supported: days registered without pay contribute on the minimum base (Orden PJC/297/2026 art. 7), and temporary incapacity and birth leave contribute on the base of an earlier month (art. 6)"},
            {"id": "wholeMonth", "type": "REQUIRE", "source": "orden-pjc-297-2026", "condition": "#employedThroughout",
             "message": "The employee must be employed for the whole month: bases for a month started or ended part-way are prorated by days, which this template does not do"},
            {"id": "commonTerritory", "type": "REQUIRE", "condition": emp_key("taxTerritory") + " == 'COMMON' or " + emp_key("taxTerritory") + " == 'CEUTA_MELILLA_LA_PALMA'",
             "message": "Withholding on work performed in the Basque Country or Navarre follows the foral rules (Concierto Económico art. 7.Uno.a; Convenio Económico art. 10.1.a), which this template does not do"},
            {"id": "noDailyManual", "type": "REQUIRE", "source": "rirpf", "condition": emp_key("withholdingContract") + " != 'DAILY_MANUAL'",
             "message": "Manual workers paid by the daily wage (peonadas) are not supported: their annual pay is 100 x the daily wage (RIRPF art. 83.2)"},
            {"id": "noTrainingContract", "type": "REQUIRE", "source": "orden-pjc-297-2026", "when": emp_key("trainingContract"),
             "condition": GROUP + " >= 4 and !(" + PART + ") and #earningsByComponent['OT'] == 0 and " + emp_key("unemploymentRate") + " == 'INDEFINITE'"
                          " and #employee['retirementSituation'] == 'NONE'",
             "message": "Training contracts are supported full-time in contribution groups 4 to 7, without overtime, with unemploymentRate INDEFINITE (Orden PJC/297/2026 arts. 33.2.a.1.º, 46)"},
            {"id": "noWorkingPensioner", "type": "REQUIRE", "source": "lgss", "condition": "#employee['retirementSituation'] != 'WORKING_PENSIONER'",
             "message": "Employees drawing a retirement pension compatible with the work contribute under LGSS art. 153 (IT, AT/EP and a 9% solidarity contribution), which this template does not do"},
            {"id": "retiredWithinMaximum", "type": "REQUIRE", "source": "lgss",
             "when": "#employee['retirementSituation'] == 'PAST_RETIREMENT_AGE'",
             "condition": "#socialEarnings - #socialEarningsByComponent['BONUS'] - #socialEarningsByComponent['OT'] + #divide(#employee['annualExtraPay'], 12, 2, 'HALF_UP') <= #p['maximumBase'] and #earningsByComponent['OT'] == 0",
             "message": "For an employee past retirement age (LGSS art. 152), pay above the maximum base (solidarity contribution) or overtime is not supported"},
            {"id": "noMultipleEmployment", "type": "REQUIRE", "source": "orden-pjc-297-2026", "condition": "!" + emp_key("multipleEmployment"),
             "message": "Employees in multiple employment (pluriempleo) share the maximum base between companies (Orden PJC/297/2026 art. 10), which this template does not do"},
            {"id": "noDisabledDependants", "type": "REQUIRE", "source": "lirpf", "detail": "descendantsDisability33",
             "condition": "!" + emp_key("dependantsWithDisability") + " or " + emp_key("descendantsDisability33") + " + " + emp_key("descendantsDisability33Mobility") + " + " + emp_key("descendantsDisability65")
                          + " + " + emp_key("ascendantsDisability33") + " + " + emp_key("ascendantsDisability33Mobility") + " + " + emp_key("ascendantsDisability65") + " > 0",
             "message": "A descendant or ascendant has a disability: enter how many in descendantsDisability33, descendantsDisability33Mobility, descendantsDisability65 or the ascendant equivalents (LIRPF art. 60.2)"},
            {"id": "disabledDescendantsCounted", "type": "REQUIRE", "source": "lirpf",
             "condition": emp_key("descendantsDisability33") + " + " + emp_key("descendantsDisability33Mobility") + " + " + emp_key("descendantsDisability65") + " <= " + emp_key("descendants"),
             "message": "Descendants with a disability are counted among the descendants, so there cannot be more of them"},
            {"id": "disabledAscendantsCounted", "type": "REQUIRE", "source": "lirpf",
             "condition": emp_key("ascendantsDisabledUnder65") + " <= " + emp_key("ascendantsDisability33") + " + " + emp_key("ascendantsDisability33Mobility") + " + " + emp_key("ascendantsDisability65")
                          + " and " + emp_key("ascendantsDisability33") + " + " + emp_key("ascendantsDisability33Mobility") + " + " + emp_key("ascendantsDisability65")
                          + " <= " + emp_key("ascendants65") + " + " + emp_key("ascendants75") + " + " + emp_key("ascendantsDisabledUnder65"),
             "message": "Ascendants with a disability are counted among the ascendants (65 to 74, 75 or more, disabled under 65), and each disabled one under 65 has a disability grade"},
            {"id": "noCourtOrderedPayments", "type": "REQUIRE", "source": "rirpf", "detail": "spousalPension",
             "condition": "!" + emp_key("courtOrderedPayments") + " or " + emp_key("spousalPension") + " + " + emp_key("childMaintenance") + " > 0",
             "message": "The employee pays court-ordered amounts: enter the annual spousalPension and/or childMaintenance (RIRPF arts. 83.3, 85.2)"},
            {"id": "noIrregularIncome", "type": "REQUIRE", "source": "rirpf",
             "condition": "!" + emp_key("irregularIncome") + " or " + emp_key("irregularIncomeAmount") + " != null",
             "detail": "irregularIncomeAmount",
             "message": "The annual pay includes irregular income: enter irregularIncomeAmount, the part of annualPay with the 30% reduction (LIRPF art. 18.2; RIRPF art. 83.3.a)"},
            {"id": "irregularIncomeWithinPay", "type": "REQUIRE", "source": "rirpf",
             "condition": "#coalesce(" + emp_key("irregularIncomeAmount") + ", 0) <= " + PAY,
             "message": "irregularIncomeAmount is part of annualPay, so it cannot be larger"},
            {"id": "irregularIncomeBelowTerminationCap", "type": "REQUIRE", "source": "lirpf",
             "condition": "#coalesce(" + emp_key("irregularIncomeAmount") + ", 0) <= #p['terminationIrregularThreshold']",
             "message": "Irregular income above 700,000 EUR is not supported: for termination pay the income with the reduction falls from 300,000 EUR to zero at 1,000,000 EUR (LIRPF art. 18.2)"},
            {"id": "monthlyContributionGroup", "type": "REQUIRE", "source": "orden-pjc-297-2026", "condition": GROUP + " <= 7",
             "message": "Contribution groups 8 to 11 contribute on daily bases (Orden PJC/297/2026 art. 3), which this template does not do"},
            {"id": "overtimeWithPay", "type": "REQUIRE", "source": "lgss",
             "condition": "#earningsByComponent['OT'] == 0 or #socialEarnings - #socialEarningsByComponent['BONUS'] - #socialEarningsByComponent['OT'] > 0",
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
             "expr": emp_key("trainingContract") + " ? 12 * (#p['trainingCommonEmployee'] + #p['trainingVocationalEmployee'] + #round(#table.lookup('minimumBases', " + GROUP + ", 'monthly') * #p['equityEmployeeRate'], 2, 'HALF_UP')"
                     " + #round(#p['accidentMinimumBase'] * #p['unemploymentIndefiniteEmployeeRate'], 2, 'HALF_UP'))"
                     " : #employee['retirementSituation'] == 'PAST_RETIREMENT_AGE' ? 12 * #round(#estimateCommonBase * #p['retiredIncapacityEmployeeRate'], 2, 'HALF_UP') : 12 * (#round(#estimateCommonBase * #p['commonEmployeeRate'], 2, 'HALF_UP') + #round(#estimateCommonBase * #p['equityEmployeeRate'], 2, 'HALF_UP')"
                     " + #round(#estimateAccidentBase * (" + emp_key("unemploymentRate") + " == 'FIXED_TERM' ? #p['unemploymentFixedTermEmployeeRate'] : #p['unemploymentIndefiniteEmployeeRate']), 2, 'HALF_UP')"
                     " + #round(#estimateAccidentBase * #p['trainingEmployeeRate'], 2, 'HALF_UP') + #round(#table.bracketTax('solidarity', 'EMPLOYEE', #estimateMonthly), 2, 'HALF_UP'))"},
            {"id": "socialContributions", "type": "VALUE", "source": "rirpf",
             "expr": "#coalesce(" + emp_key("annualSocialContributions") + ", #estimatedContributions)"},
            {"id": "netWorkIncome", "type": "VALUE", "source": "rirpf", "expr": "#pos(" + PAY + " - #socialContributions)"},
            # IRREGULAR1: 30% of the irregular income up to 300,000, cents dropped so it never exceeds 30% of the pay (algorithm validations 19-20)
            {"id": "irregularReduction", "type": "VALUE", "source": "lirpf",
             "expr": "#round(#min(#coalesce(" + emp_key("irregularIncomeAmount") + ", 0), #p['irregularIncomeLimit']) * #p['irregularReductionRate'], 2, 'DOWN')"},
            {"id": "reducedNetWorkIncome", "type": "VALUE", "source": "aeat-algoritmo-2026",
             "expr": "#pos(" + PAY + " - #irregularReduction - #socialContributions)"},
            {"id": "otherExpenses", "type": "VALUE", "source": "rirpf",
             "expr": "#min(#p['generalExpenses'] + (" + emp_key("geographicMobility") + " ? #p['mobilityExpenses'] : 0)"
                     " + (" + emp_key("disability") + " == 'FROM_65' or " + emp_key("disability") + " == 'FROM_33_REDUCED_MOBILITY' ? #p['disabledWorkerExpensesHigh']"
                     " : (" + emp_key("disability") + " == 'FROM_33' ? #p['disabledWorkerExpenses'] : 0)), #netWorkIncome)"},
            {"id": "workIncomeReduction", "type": "VALUE", "source": "lirpf",
             "expr": "#reducedNetWorkIncome <= #p['red20Lower'] ? #p['red20Max'] : (#reducedNetWorkIncome <= #p['red20Middle']"
                     " ? #round(#p['red20Max'] - #p['red20Slope1'] * (#reducedNetWorkIncome - #p['red20Lower']), 2, 'HALF_UP')"
                     " : (#reducedNetWorkIncome < #p['red20Upper'] ? #round(#p['red20MiddleAmount'] - #p['red20Slope2'] * (#reducedNetWorkIncome - #p['red20Middle']), 2, 'HALF_UP') : 0))"},
            {"id": "withholdingBase", "type": "VALUE", "remember": True, "source": "rirpf",
             "expr": "#pos(#pos(#reducedNetWorkIncome - #otherExpenses - #workIncomeReduction) - ((" + emp_key("descendants") + " > 2 ? #p['largeFamilyReduction'] : 0) + " + emp_key("spousalPension") + "))"},
            {"id": "taxpayerAge", "type": "VALUE", "source": "aeat-algoritmo-2026", "expr": "#periodEnd.getYear() - #birthYear"},
            {"id": "descendantsWhole", "type": "VALUE", "source": "lirpf",
             "expr": "(" + emp_key("descendants") + " >= 1 ? #p['descendant1'] : 0) + (" + emp_key("descendants") + " >= 2 ? #p['descendant2'] : 0)"
                     " + (" + emp_key("descendants") + " >= 3 ? #p['descendant3'] : 0) + (" + emp_key("descendants") + " > 3 ? (" + emp_key("descendants") + " - 3) * #p['descendant4'] : 0)"
                     " + " + emp_key("descendantsUnder3") + " * #p['descendantUnder3']"},
            # Disabled descendants (by whole or half like the others) and ascendants (shared like their minimum), LIRPF art. 60.2
            {"id": "descendantsDisabilityWhole", "type": "VALUE", "source": "lirpf",
             "expr": "(" + emp_key("descendantsDisability33") + " + " + emp_key("descendantsDisability33Mobility") + ") * #p['disabilityMinimum'] + " + emp_key("descendantsDisability65") + " * #p['disabilityMinimumHigh']"
                     " + (" + emp_key("descendantsDisability33Mobility") + " + " + emp_key("descendantsDisability65") + ") * #p['assistanceMinimum']"},
            {"id": "dependantsDisabilityMinimum", "type": "VALUE", "source": "lirpf",
             "expr": "(" + E_COMP + " == 'HALF' ? #divide(#descendantsDisabilityWhole, 2, 2, 'HALF_UP') : #descendantsDisabilityWhole)"
                     " + #divide((" + emp_key("ascendantsDisability33") + " + " + emp_key("ascendantsDisability33Mobility") + ") * #p['disabilityMinimum'] + " + emp_key("ascendantsDisability65") + " * #p['disabilityMinimumHigh'], " + emp_key("ascendantsSharedBy") + ", 2, 'HALF_UP')"
                     " + #divide((" + emp_key("ascendantsDisability33Mobility") + " + " + emp_key("ascendantsDisability65") + ") * #p['assistanceMinimum'], " + emp_key("ascendantsSharedBy") + ", 2, 'HALF_UP')"},
            {"id": "personalFamilyMinimum", "type": "VALUE", "remember": True, "source": "lirpf",
             "expr": "#p['personalMinimum'] + (#taxpayerAge > 64 ? #p['age65Minimum'] : 0) + (#taxpayerAge > 74 ? #p['age75Minimum'] : 0)"
                     " + (" + E_COMP + " == 'HALF' ? #divide(#descendantsWhole, 2, 2, 'HALF_UP') : #descendantsWhole)"
                     " + #divide((" + emp_key("ascendants65") + " + " + emp_key("ascendants75") + " + " + emp_key("ascendantsDisabledUnder65") + ") * #p['ascendantMinimum'], " + emp_key("ascendantsSharedBy") + ", 2, 'HALF_UP')"
                     " + #divide(" + emp_key("ascendants75") + " * #p['ascendant75Minimum'], " + emp_key("ascendantsSharedBy") + ", 2, 'HALF_UP')"
                     " + (" + emp_key("disability") + " == 'FROM_65' ? #p['disabilityMinimumHigh'] + #p['assistanceMinimum']"
                     " : (" + emp_key("disability") + " == 'FROM_33_REDUCED_MOBILITY' ? #p['disabilityMinimum'] + #p['assistanceMinimum']"
                     " : (" + emp_key("disability") + " == 'FROM_33' ? #p['disabilityMinimum'] : 0)))"
                     " + #dependantsDisabilityMinimum"},
            {"id": "exemptLimit", "type": "VALUE", "source": "rirpf",
             "expr": "#table.lookup('exemptLimits', " + emp_key("familySituation") + ", " + emp_key("descendants") + " >= 2 ? 'd2' : (" + emp_key("descendants") + " == 1 ? 'd1' : 'd0'))"},
            {"id": "withholdingQuota", "type": "VALUE", "source": "rirpf",
             "expr": emp_key("childMaintenance") + " > 0 and #withholdingBase - " + emp_key("childMaintenance") + " > 0"
                     " ? #pos(#table.bracketTax('withholdingScale', #withholdingBase - " + emp_key("childMaintenance") + ") + #table.bracketTax('withholdingScale', " + emp_key("childMaintenance") + ")"
                     " - #table.bracketTax('withholdingScale', #personalFamilyMinimum + #p['childMaintenanceMinimumIncrease']))"
                     " : #pos(#table.bracketTax('withholdingScale', #withholdingBase) - #table.bracketTax('withholdingScale', #personalFamilyMinimum))"},
            {"id": "cappedQuota", "type": "VALUE", "source": "rirpf",
             "expr": PAY + " <= #p['capIncomeLimit'] ? #pos(#min(#withholdingQuota, (" + PAY + " - #exemptLimit) * #p['capRate'])) : #withholdingQuota"},
            {"id": "housingReduction", "type": "VALUE", "source": "aeat-algoritmo-2026",
             "expr": emp_key("housingLoan") + " ? #round(" + PAY + " * #p['housingReductionRate'], 2, 'DOWN') : 0"},
            {"id": "computedRate", "type": "VALUE", "source": "rirpf",
             "expr": PAY + " <= #exemptLimit ? 0 : #divide(#pos((" + CEUTA + " ? #cappedQuota * #p['ceutaQuotaShare'] : #cappedQuota) - #housingReduction) * 100, " + PAY + ", 2, 'DOWN')"},
            {"id": "withholdingRate", "type": "VALUE", "remember": True, "source": "rirpf",
             "expr": emp_key("withholdingContract") + " == 'UNDER_ONE_YEAR' ? #max(#computedRate, " + CEUTA + " ? #p['minimumRateShortContractCeuta'] : #p['minimumRateShortContract'])"
                     " : (" + emp_key("withholdingContract") + " == 'SPECIAL_RELATIONSHIP' ? #max(#computedRate, " + CEUTA + " ? #p['minimumRateSpecialCeuta'] : #p['minimumRateSpecial']) : #computedRate)"},
            # Regularization (RIRPF art. 87; AEAT algorithm REGULARIZACIÓN): the rate determined earlier this year is kept
            # with each result; when the data it came from change, the new rate spreads the new annual quota less what
            # was withheld over the pay still to come, within the limits of art. 87.5.
            {"id": "annualPayDetermined", "type": "VALUE", "remember": True, "source": "rirpf", "expr": PAY},
            {"id": "familySituationCode", "type": "VALUE", "remember": True, "source": "rirpf",
             "expr": emp_key("familySituation") + " == '1' ? 1 : (" + emp_key("familySituation") + " == '2' ? 2 : 3)"},
            {"id": "spousalPensionDetermined", "type": "VALUE", "remember": True, "source": "rirpf", "expr": emp_key("spousalPension")},
            {"id": "childMaintenanceDetermined", "type": "VALUE", "remember": True, "source": "rirpf", "expr": emp_key("childMaintenance")},
            {"id": "housingLoanFlag", "type": "VALUE", "remember": True, "source": "rirpf", "expr": emp_key("housingLoan") + " ? 1 : 0"},
            {"id": "ceutaFlag", "type": "VALUE", "remember": True, "source": "rirpf", "expr": CEUTA + " ? 1 : 0"},
            {"id": "earlierDetermination", "type": "VALUE", "source": "rirpf", "expr": LAST("appliedRate") + " != null"},
            {"id": "dataChanged", "type": "VALUE", "source": "rirpf",
             "expr": "#earlierDetermination and (" + LAST("annualPayDetermined") + " != #annualPayDetermined or " + LAST("withholdingBase") + " != #withholdingBase"
                     " or " + LAST("personalFamilyMinimum") + " != #personalFamilyMinimum or " + LAST("withholdingRate") + " != #withholdingRate"
                     " or " + LAST("familySituationCode") + " != #familySituationCode or " + LAST("spousalPensionDetermined") + " != #spousalPensionDetermined"
                     " or " + LAST("childMaintenanceDetermined") + " != #childMaintenanceDetermined or " + LAST("housingLoanFlag") + " != #housingLoanFlag"
                     " or " + LAST("ceutaFlag") + " != #ceutaFlag)"},
            {"id": "regularize", "type": "VALUE", "source": "rirpf", "expr": "#dataChanged and " + OV + " != 'REGULARIZED'"},
            {"id": "broughtForward", "type": "VALUE", "expr": emp_key("broughtForwardYear") + " != null and " + emp_key("broughtForwardYear") + " == #taxYear"},
            {"id": "paidSoFar", "type": "VALUE", "source": "rirpf",
             "expr": "#ytd.base('incomeTax', 'TAX') + (#broughtForward ? #coalesce(" + emp_key("broughtForwardPay") + ", 0) : 0)"},
            {"id": "withheldSoFar", "type": "VALUE", "source": "rirpf",
             "expr": "#ytd.amount('incomeTax', 'TAX') + (#broughtForward ? #coalesce(" + emp_key("broughtForwardWithheld") + ", 0) : 0)"},
            {"id": "regularizationWithoutHousingLoan", "type": "REQUIRE", "source": "rirpf", "when": "#regularize",
             "condition": "#housingLoanFlag == 0 and " + LAST("housingLoanFlag") + " == 0",
             "message": "The withholding data changed and the housing-loan reduction is or was applied, whose regularization (RIRPF art. 87.3.b) this template does not do: regularize the rate and enter it as withholdingRateOverride with reason REGULARIZED"},
            {"id": "regularizationSameTerritory", "type": "REQUIRE", "source": "rirpf", "when": "#regularize",
             "condition": "#ceutaFlag == " + LAST("ceutaFlag"),
             "message": "The employee moved into or out of the Ceuta, Melilla and La Palma regime during the year, whose regularization (RIRPF art. 87.2.9.º) this template does not do: regularize the rate and enter it as withholdingRateOverride with reason REGULARIZED"},
            {"id": "regularizationPayRemaining", "type": "REQUIRE", "source": "rirpf", "when": "#regularize",
             "condition": PAY + " > #paidSoFar",
             "message": "annualPay must include what was already paid this year and be larger than it: the regularized rate applies to the pay still to come (RIRPF art. 87.3.c)"},
            {"id": "monthsEarlier", "type": "VALUE",
             "expr": "#taxMonth - 1 - (#employmentStartDate != null and #employmentStartDate > #taxYearStartDate ? #employmentStartDate.getMonthValue() - 1 : 0)"},
            {"id": "monthsRecorded", "type": "VALUE",
             "expr": "#ytd.periods('workAccident', 'EMPLOYER') + #ytd.periods('trainingWorkAccident', 'EMPLOYER') + (#broughtForward ? #coalesce(" + emp_key("broughtForwardMonths") + ", 0) : 0)"},
            {"id": "regularizationEarlierPayKnown", "type": "REQUIRE", "source": "rirpf", "when": "#regularize", "detail": "broughtForwardYear",
             "condition": "#monthsRecorded >= #monthsEarlier",
             "message": "The withholding data changed, and regularizing the rate needs everything paid and withheld this year (RIRPF art. 87.3.b), but not every earlier month with you is in a posted payroll: set broughtForwardYear and the brought-forward pay and withholding for the months before Humano"},
            {"id": "regularizationQuota", "type": "VALUE", "source": "aeat-algoritmo-2026", "when": "#regularize",
             "expr": "(" + PAY + " <= #exemptLimit ? 0 : #cappedQuota) * (" + CEUTA + " ? #p['ceutaQuotaShare'] : 1)"},
            {"id": "regularizationCauses", "type": "VALUE", "source": "aeat-algoritmo-2026", "when": "#regularize",
             # causes 1-4 of the algorithm (base, minimum, spousal pension, child maintenance) are limited by art. 87.5; 5 (situation 2 to 3) and 11 are not
             "expr": "(#withholdingBase != " + LAST("withholdingBase") + " or #personalFamilyMinimum != " + LAST("personalFamilyMinimum") +
                     " or (" + emp_key("spousalPension") + " > 0 and #spousalPensionDetermined != " + LAST("spousalPensionDetermined") + ")"
                     " or (" + emp_key("childMaintenance") + " > 0 and #childMaintenanceDetermined != " + LAST("childMaintenanceDetermined") + "))"
                     " and !(" + LAST("familySituationCode") + " == 2 and #familySituationCode == 3)"},
            {"id": "remainingPay", "type": "VALUE", "when": "#regularize", "expr": PAY + " - #paidSoFar"},
            {"id": "rawRegularizedRate", "type": "VALUE", "source": "rirpf", "when": "#regularize",
             "expr": "#pos(#divide((#regularizationQuota - #withheldSoFar) * 100, #remainingPay, 2, 'DOWN'))"},
            {"id": "boundedRegularizedRate", "type": "VALUE", "source": "rirpf", "when": "#regularize",
             "expr": "#min(#rawRegularizedRate, " + CEUTA + " ? #p['maximumRegularizedRateCeuta'] : #p['maximumRegularizedRate'])"},
            {"id": "minimumRegularizedRate", "type": "VALUE", "source": "rirpf", "when": "#regularize",
             "expr": "#rawRegularizedRate > (" + CEUTA + " ? #p['maximumRegularizedRateCeuta'] : #p['maximumRegularizedRate']) ? #boundedRegularizedRate : " + MIN_RATE.format("#boundedRegularizedRate")},
            {"id": "regularizedAnnualAmount", "type": "VALUE", "source": "aeat-algoritmo-2026", "when": "#regularize",
             "expr": "#round(#divide(#remainingPay * #minimumRegularizedRate, 100, 6, 'HALF_UP') + #withheldSoFar, 2, 'HALF_UP')"},
            # art. 87.5: no higher rate when the change only lowers (base - minimum); no more extra withholding than its increase
            {"id": "earlierDifference", "type": "VALUE", "when": "#regularize", "expr": "#pos(" + LAST("withholdingBase") + " - " + LAST("personalFamilyMinimum") + ")"},
            {"id": "newDifference", "type": "VALUE", "when": "#regularize", "expr": "#withholdingBase - #personalFamilyMinimum"},
            {"id": "rateNotRaised", "type": "VALUE", "source": "rirpf", "when": "#regularize",
             "expr": "#regularizationCauses and #earlierDifference >= #newDifference and #minimumRegularizedRate > " + LAST("appliedRate")},
            {"id": "increaseCapped", "type": "VALUE", "source": "rirpf", "when": "#regularize",
             "expr": "#regularizationCauses and #earlierDifference < #newDifference and " + LAST("annualWithholding") + " < #regularizedAnnualAmount"
                     " and #regularizedAnnualAmount - " + LAST("annualWithholding") + " > #newDifference - #earlierDifference"},
            {"id": "cappedRegularizedRate", "type": "VALUE", "source": "aeat-algoritmo-2026", "when": "#regularize",
             "expr": "#pos(#divide((" + LAST("annualWithholding") + " + #newDifference - #earlierDifference - #withheldSoFar) * 100, #remainingPay, 2, 'DOWN'))"},
            {"id": "regularizedRate", "type": "VALUE", "source": "rirpf", "when": "#regularize",
             "expr": "#rateNotRaised ? " + MIN_RATE.format(LAST("appliedRate")) + " : (#increaseCapped ? " + MIN_RATE.format("#cappedRegularizedRate") + " : #minimumRegularizedRate)"},
            # A rate the employer regularized by hand (REGULARIZED) becomes the determination that carries over.
            {"id": "automaticRate", "type": "VALUE", "remember": True, "source": "rirpf",
             "expr": OV + " == 'REGULARIZED' ? " + emp_key("withholdingRateOverride") + " : (!#earlierDetermination ? #withholdingRate"
                     " : (#regularize ? #regularizedRate : " + LAST("automaticRate") + "))"},
            {"id": "annualWithholding", "type": "VALUE", "remember": True, "source": "aeat-algoritmo-2026",
             "expr": "#earlierDetermination and !#dataChanged and " + OV + " != 'REGULARIZED' ? " + LAST("annualWithholding")
                     + " : #round(#divide((" + PAY + " - #paidSoFar) * #automaticRate, 100, 6, 'HALF_UP') + #withheldSoFar, 2, 'HALF_UP')"},
            {"id": "appliedRate", "type": "VALUE", "remember": True, "source": "rirpf",
             "expr": emp_key("withholdingRateOverrideReason") + " == 'REGULARIZED' ? " + emp_key("withholdingRateOverride")
                     + " : (" + emp_key("withholdingRateOverrideReason") + " == 'REQUESTED_HIGHER' ? #max(#automaticRate, " + emp_key("withholdingRateOverride") + ") : #automaticRate)"},
            {"id": "rateMatchesYearToDate", "type": "REQUIRE", "source": "rirpf",
             "when": "#ytd.periods('incomeTax', 'TAX') > 0 and !#earlierDetermination and " + emp_key("withholdingRateOverrideReason") + " == 'NONE'",
             "condition": "#max(#ytd.amount('incomeTax', 'TAX') - #divide(#ytd.base('incomeTax', 'TAX') * #withholdingRate, 100, 6, 'HALF_UP'),"
                          " #divide(#ytd.base('incomeTax', 'TAX') * #withholdingRate, 100, 6, 'HALF_UP') - #ytd.amount('incomeTax', 'TAX')) * 200 <= #ytd.periods('incomeTax', 'TAX')",
             "message": "The withholding rate computed from the current data differs from the rate applied earlier this year: regularize it under RIRPF art. 87 and enter the regularized rate as withholdingRateOverride with reason REGULARIZED"},
            # Social security (Orden PJC/297/2026)
            {"id": "monthlyPay", "type": "VALUE", "source": "lgss", "expr": "#pos(#socialEarnings - #socialEarningsByComponent['BONUS'] - #socialEarningsByComponent['OT'])"},
            {"id": "contributionPay", "type": "VALUE", "source": "orden-pjc-297-2026",
             "expr": "#monthlyPay > 0 ? #monthlyPay + #divide(" + emp_key("annualExtraPay") + ", 12, 2, 'HALF_UP') : 0"},
            {"id": "commonMinimumBase", "type": "VALUE", "source": "orden-pjc-297-2026",
             "expr": PART + " ? #round(" + emp_key("partTimeHours") + " * #table.lookup('minimumBases', " + GROUP + ", 'hourly'), 2, 'HALF_UP') : #table.lookup('minimumBases', " + GROUP + ", 'monthly')"},
            {"id": "accidentMinimumBase", "type": "VALUE", "source": "orden-pjc-297-2026",
             "expr": PART + " ? #round(" + emp_key("partTimeHours") + " * #p['partTimeAccidentHourlyBase'], 2, 'HALF_UP') : #p['accidentMinimumBase']"},
            {"id": "commonBase", "type": "VALUE", "source": "orden-pjc-297-2026", "expr": "#clamp(#contributionPay, #commonMinimumBase, #p['maximumBase'])"},
            {"id": "accidentBase", "type": "VALUE", "source": "orden-pjc-297-2026", "expr": "#clamp(#contributionPay + #earningsByComponent['OT'], #accidentMinimumBase, #p['maximumBase'])"},
            {"id": "trainingWithinMinimumBase", "type": "REQUIRE", "source": "orden-pjc-297-2026", "when": TRAINING + " and #contributionPay > 0",
             "condition": "#contributionPay <= #commonMinimumBase",
             "message": "Training contract paid above the minimum base: Orden PJC/297/2026 art. 46 adds contributions on the excess, and its third rule leaves open whether the MEI then uses the actual or the minimum base, so this template refuses it"},
            {"id": "trainingCommonContingencies", "type": "CONTRIBUTION", "label": "Common contingencies, training contract (Orden art. 46)", "ledger": "SOCIAL_SECURITY", "source": "orden-pjc-297-2026",
             "when": "#contributionPay > 0 and " + TRAINING, "base": "#commonBase",
             "employee": {"amount": "#p['trainingCommonEmployee']"}, "employer": {"amount": "#p['trainingCommonEmployer']"}},
            {"id": "trainingWorkAccident", "type": "CONTRIBUTION", "label": "Professional contingencies, training contract (Orden art. 46)", "ledger": "WORK_INJURY", "source": "orden-pjc-297-2026",
             "when": "#contributionPay > 0 and " + TRAINING, "base": "#accidentBase", "employer": {"amount": "#p['trainingProfessionalEmployer']"}},
            {"id": "trainingWageGuaranteeFund", "type": "CONTRIBUTION", "label": "Wage Guarantee Fund, training contract (Orden art. 46)", "ledger": "OTHER", "source": "orden-pjc-297-2026",
             "when": "#contributionPay > 0 and " + TRAINING, "base": "#accidentBase", "employer": {"amount": "#p['trainingWageGuaranteeEmployer']"}},
            {"id": "trainingVocationalTraining", "type": "CONTRIBUTION", "label": "Vocational training, training contract (Orden art. 46)", "ledger": "OTHER", "source": "orden-pjc-297-2026",
             "when": "#contributionPay > 0 and " + TRAINING, "base": "#accidentBase",
             "employee": {"amount": "#p['trainingVocationalEmployee']"}, "employer": {"amount": "#p['trainingVocationalEmployer']"}},
            {"id": "commonContingencies", "type": "CONTRIBUTION", "label": "Common contingencies (contingencias comunes)", "ledger": "SOCIAL_SECURITY", "source": "orden-pjc-297-2026",
             "when": "#contributionPay > 0 and !(#employee['retirementSituation'] == 'PAST_RETIREMENT_AGE') and !#employee['trainingContract']", "base": "#commonBase",
             "employee": {"rate": "#p['commonEmployeeRate']"}, "employer": {"rate": "#p['commonEmployerRate']"}},
            {"id": "temporaryIncapacity", "type": "CONTRIBUTION", "label": "Temporary incapacity past retirement age (IT, art. 152 LGSS)", "ledger": "SOCIAL_SECURITY", "source": "orden-pjc-297-2026",
             "when": "#contributionPay > 0 and #employee['retirementSituation'] == 'PAST_RETIREMENT_AGE'", "base": "#commonBase",
             "employee": {"rate": "#p['retiredIncapacityEmployeeRate']"}, "employer": {"rate": "#p['retiredIncapacityEmployerRate']"}},
            {"id": "intergenerationalEquity", "type": "CONTRIBUTION", "label": "Intergenerational equity mechanism (MEI)", "ledger": "PENSION_CONTRIBUTION", "source": "orden-pjc-297-2026",
             "when": "#contributionPay > 0 and !(#employee['retirementSituation'] == 'PAST_RETIREMENT_AGE')", "base": "#commonBase",
             "employee": {"rate": "#p['equityEmployeeRate']"}, "employer": {"rate": "#p['equityEmployerRate']"}},
            {"id": "unemployment", "type": "CONTRIBUTION", "label": "Unemployment (desempleo)", "ledger": "UNEMPLOYMENT", "source": "orden-pjc-297-2026",
             "when": "#contributionPay > 0 and !(#employee['retirementSituation'] == 'PAST_RETIREMENT_AGE')", "base": "#accidentBase",
             "employee": {"rate": emp_key("unemploymentRate") + " == 'FIXED_TERM' ? #p['unemploymentFixedTermEmployeeRate'] : #p['unemploymentIndefiniteEmployeeRate']"},
             "employer": {"rate": emp_key("unemploymentRate") + " == 'FIXED_TERM' ? #p['unemploymentFixedTermEmployerRate'] : #p['unemploymentIndefiniteEmployerRate']"}},
            {"id": "wageGuaranteeFund", "type": "CONTRIBUTION", "label": "Wage Guarantee Fund (FOGASA)", "ledger": "OTHER", "source": "orden-pjc-297-2026",
             "when": "#contributionPay > 0 and !(#employee['retirementSituation'] == 'PAST_RETIREMENT_AGE') and !#employee['trainingContract']", "base": "#accidentBase", "employer": {"rate": "#p['wageGuaranteeRate']"}},
            {"id": "vocationalTraining", "type": "CONTRIBUTION", "label": "Vocational training (formación profesional)", "ledger": "OTHER", "source": "orden-pjc-297-2026",
             "when": "#contributionPay > 0 and !(#employee['retirementSituation'] == 'PAST_RETIREMENT_AGE') and !#employee['trainingContract']", "base": "#accidentBase",
             "employee": {"rate": "#p['trainingEmployeeRate']"}, "employer": {"rate": "#p['trainingEmployerRate']"}},
            {"id": "workAccident", "type": "CONTRIBUTION", "label": "Work accidents and occupational diseases (AT/EP)", "ledger": "WORK_INJURY", "source": "lgss",
             "when": "#contributionPay > 0 and !#employee['trainingContract']", "base": "#accidentBase",
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
            "Common tax territory, Régimen General employees paid monthly in EUR in 2026, for whole months with the payment in the same year. Work performed in the Basque Country or Navarre, contribution groups 8 to 11, multiple employment, part months, unpaid leave and working pensioners (LGSS art. 153) are refused. Employees past the ordinary retirement age contribute only IT (1.30% / 0.25%) and AT/EP (LGSS art. 152; Orden art. 32.1.a), not when paid above the maximum base or overtime. Overtime (OT) enters only the AT/EP base and carries the additional contribution of Orden art. 5 (28.30%, or 14% for force majeure); it must be paid with ordinary pay in the same run.",
            "Residents of Ceuta, Melilla or La Palma whose work income is obtained there (taxTerritory CEUTA_MELILLA_LA_PALMA) are withheld at the rate reduced by 60%, with minimums of 0.8% and 6% (RIRPF arts. 80.2, 86.2; DA 57ª LIRPF); the contribution reductions for Ceuta and Melilla are not applied.",
            "Training contracts (formación en alternancia and remaining contratos para la formación y el aprendizaje) pay the fixed quotas of Orden art. 46 while the common-contingencies base does not exceed the minimum base, full-time in groups 4 to 7 without overtime; above the minimum base they are refused, as the Orden's third rule leaves open which base the MEI uses then.",
            "The withholding rate comes from the annual figures entered (annual pay, contributions, family data from modelo 145) and applies to the whole payment. Each result keeps the determination (rate, annual withholding, base, minimum and the data they came from); while nothing changes the kept rate carries on, and when the data change the rate is regularized (RIRPF art. 87.3) from the pay and withholding so far, within the limits of art. 87.5 for changes in the base, the minimum, a spousal pension or child maintenance (as the AEAT algorithm's causes 1 to 4). Not automated, so refused until the employer enters the rate as withholdingRateOverride REGULARIZED: changes while a housing-loan reduction is or was applied, moving into or out of the Ceuta, Melilla and La Palma regime, and data changed in months posted before version 2026.1.3 (no kept determination). Earlier months not in Humano need the brought-forward pay and withholding. A higher rate requested under art. 88.5 can be entered as REQUESTED_HIGHER. Regularization starts in the first run after the data change; the quarterly option of art. 87.4 is not offered.",
            "Without annualSocialContributions the expected employee contributions are estimated as 12 months on annualPay / 12 at full-time bases.",
            "Dependants with a disability are counted by grade (all descendants by whole or all by half, all ascendants shared the same way); children receiving court-ordered maintenance must not be entered as descendants.",
            "Not modelled: irregular income above 700,000 EUR, pensioners and the unemployed, pay in kind and its ingreso a cuenta, arrears of earlier years (15% fixed rate), fixed rates (directors, courses, authors), the special regime for workers posted to Spain, sick leave and birth leave months (their own bases), contribution reductions and incentives, and the 33.62 EUR charge at the end of fixed-term contracts under 30 days (Orden art. 28).",
            "Extra payments (BONUS) are prorated over the year in the contribution base from annualExtraPay (Orden art. 1, monthly-paid employees); a run holding only BONUS pays withholding but no contributions.",
            "Contributions are rounded to the cent, half up, per line; part-time minimum bases (hours x hourly base) and the extra-pay proration to the cent, half up.",
            "Status LAW_DERIVED: values are cited from the BOE consolidated texts, the Orden PJC/297/2026 and the AEAT 2026 algorithm and help; the cases are derived from them (one reproduces the AEAT help's 15.96% example) and every case's withholding rate, regularized rates included, matches the AEAT calculation service.",
        ],
        "testCases": test_cases(),
    }
    with open(sys.argv[1], "w", encoding="utf-8") as f:
        json.dump(template, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("wrote", sys.argv[1], "with", len(template["testCases"]), "test cases")


if __name__ == "__main__":
    main()
