"""Lohnsteuer, Solidaritätszuschlag and Maßstabsteuer 2026 for a monthly current wage, written from the
statute text — independent of the PAP code:

§ 39b Abs. 2 EStG (Hochrechnung ×12, Frei-/Hinzurechnungsbetrag, Altersentlastungsbetrag, Arbeitnehmer-
and Sonderausgaben-Pauschbetrag, Vorsorgepauschale a-c and e incl. the 1 900 EUR rule, Entlastungsbetrag,
Klassen V/VI, 1/12, Bruchteile eines Cents), § 32a Abs. 1 and 5, § 24a (table), § 24b Abs. 2, § 32 Abs. 6,
§ 39f (Faktor), § 51a Abs. 2a, SolzG §§ 3-4; social-insurance rates and ceilings 2026 for the
Vorsorgepauschale (§ 243 SGB V ermäßigt 14,0 %, BSV 2018 18,6 %, § 341 SGB III 2,6 %, PBAV 2025 3,6 %,
SVBezGrV 2026). The statute leaves two roundings open that the binding PAP fixes (§ 39b Abs. 6 EStG):
Vorsorgepauschale parts truncated to cents and the total rounded up to the euro, and the
Altersentlastungsbetrag rounded up to the euro; they are applied here as the PAP prescribes.
"""
from decimal import ROUND_CEILING, ROUND_FLOOR, Decimal as D

# § 24a table as printed in the statute: (year after completing 64) -> (percent, maximum EUR)
AGE_RELIEF = {
    2005: ("40.0", 1900),
    2006: ("38.4", 1824),
    2007: ("36.8", 1748),
    2008: ("35.2", 1672),
    2009: ("33.6", 1596),
    2010: ("32.0", 1520),
    2011: ("30.4", 1444),
    2012: ("28.8", 1368),
    2013: ("27.2", 1292),
    2014: ("25.6", 1216),
    2015: ("24.0", 1140),
    2016: ("22.4", 1064),
    2017: ("20.8", 988),
    2018: ("19.2", 912),
    2019: ("17.6", 836),
    2020: ("16.0", 760),
    2021: ("15.2", 722),
    2022: ("14.4", 684),
    2023: ("14.0", 665),
    2024: ("13.6", 646),
    2025: ("13.2", 627),
    2026: ("12.8", 608),
    2027: ("12.4", 589),
    2028: ("12.0", 570),
    2029: ("11.6", 551),
    2030: ("11.2", 532),
    2031: ("10.8", 513),
    2032: ("10.4", 494),
    2033: ("10.0", 475),
    2034: ("9.6", 456),
    2035: ("9.2", 437),
    2036: ("8.8", 418),
    2037: ("8.4", 399),
    2038: ("8.0", 380),
    2039: ("7.6", 361),
    2040: ("7.2", 342),
    2041: ("6.8", 323),
    2042: ("6.4", 304),
    2043: ("6.0", 285),
    2044: ("5.6", 266),
    2045: ("5.2", 247),
    2046: ("4.8", 228),
    2047: ("4.4", 209),
    2048: ("4.0", 190),
    2049: ("3.6", 171),
    2050: ("3.2", 152),
    2051: ("2.8", 133),
    2052: ("2.4", 114),
    2053: ("2.0", 95),
    2054: ("1.6", 76),
    2055: ("1.2", 57),
    2056: ("0.8", 38),
    2057: ("0.4", 19),
    2058: ("0.0", 0),
}
AGE_RELIEF = {k: (D(v[0]) / 100, D(v[1])) for k, v in AGE_RELIEF.items()}
assert AGE_RELIEF[2026] == (D("0.128"), D(608)) and AGE_RELIEF[2023] == (D("0.140"), D(665)) and AGE_RELIEF[2058] == (D(0), D(0))


def floor(x, q="1"):
    return x.quantize(D(q), rounding=ROUND_FLOOR)


def ceil(x, q="1"):
    return x.quantize(D(q), rounding=ROUND_CEILING)


def tariff(x):
    x = floor(x)
    if x <= 12348:
        return D(0)
    if x <= 17799:
        y = (x - 12348) / 10000
        return floor((D("914.51") * y + 1400) * y)
    if x <= 69878:
        z = (x - 17799) / 10000
        return floor((D("173.10") * z + 2397) * z + D("1034.87"))
    if x <= 277825:
        return floor(D("0.42") * x - D("11135.63"))
    return floor(D("0.45") * x - D("19470.38"))


def class_5_6(x):
    x = floor(x)
    b = lambda v: max(2 * (tariff(floor(v * D("1.25"))) - tariff(floor(v * D("0.75")))), floor(v * D("0.14")))
    w1, w2, w3 = D(14071), D(34939), D(222260)
    if x <= w1:
        return b(x)
    capped = lambda v: min(b(v), b(w1) + floor((v - w1) * D("0.42")))
    if x <= w2:
        return capped(x)
    s2 = capped(w2)
    if x <= w3:
        return s2 + floor((x - w2) * D("0.42"))
    return s2 + floor((w3 - w2) * D("0.42")) + floor((x - w3) * D("0.45"))


def annual_tax(zve, stkl, factor):
    zve = max(floor(zve), D(0))
    st = 2 * tariff(floor(zve / 2)) if stkl == 3 else class_5_6(zve) if stkl in (5, 6) else tariff(zve)
    return floor(st * factor) if factor is not None else st


def monthly(stkl, wage, kvz, pvs, pvz, pva, zkf=D(0), allowance=D(0), addition=D(0), alter1=0, ajahr=0, factor=None):
    """(Lohnsteuer, Solidaritätszuschlag, Maßstabsteuer BK) for the month, in euros."""
    jlst, jbmg, solz = annual(stkl, wage * 12, kvz, pvs, pvz, pva, zkf, allowance * 12, addition * 12, alter1, ajahr, factor)
    cents = lambda v: floor(v, "0.01")
    return cents(jlst / 12), cents(solz / 12), cents(jbmg / 12)


def annual(stkl, a, kvz, pvs, pvz, pva, zkf=D(0), allowance=D(0), addition=D(0), alter1=0, ajahr=0, factor=None):
    """(Jahreslohnsteuer, Jahres-Maßstabsteuer, Jahres-SolZ) for an annual wage a."""
    x = a - allowance + addition
    if alter1:
        rate, maximum = AGE_RELIEF[max(ajahr, 2005)]
        x -= min(ceil(a * rate), maximum)
    deductions = D(1266) if stkl <= 5 else D(0)
    if stkl == 2:
        deductions += 4260
    cents = lambda v: floor(v, "0.01")
    rv = cents(D("0.093") * min(a, D(101400)))
    kvpv = cents(((D("0.07") + kvz / 200) + (D("0.023") if pvs else D("0.018")) + (D("0.006") if pvz else 0) - D("0.0025") * pva) * min(a, D(69750)))
    vsp = ceil(rv + kvpv)
    if stkl <= 5:
        av = cents(D("0.013") * min(a, D(101400)))
        vsp = max(vsp, ceil(rv + min(cents(av + kvpv), D(1900))))
    zve = x - deductions - vsp
    jlst = annual_tax(zve, stkl, factor)
    kfb = zkf * 9756 if stkl <= 3 else zkf * 4878 if stkl == 4 else D(0)
    jbmg = annual_tax(zve - kfb, stkl, factor) if kfb else jlst
    exempt = D(40700) if stkl == 3 else D(20350)
    solz = floor(min(jbmg * D("0.055"), (jbmg - exempt) * D("0.119")), "0.01") if jbmg > exempt else D(0)
    return jlst, jbmg, solz
