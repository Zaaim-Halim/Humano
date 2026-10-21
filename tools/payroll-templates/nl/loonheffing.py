"""Independent implementation of the Belastingdienst 'Rekenvoorschriften voor de geautomatiseerde
loonadministratie 2026' (uitgave januari 2026, versie 2), chapter 2: white tables, standard situation,
resident of the Netherlands. Parameter values: tables 1a, 1b, 2, 3, 4, 5 and 6 of that document.

check_tables() compares every row and column of the official white tables (xlsx) with it.
"""
from decimal import ROUND_CEILING, ROUND_FLOOR, ROUND_HALF_UP, Decimal as D

LV, LMAX = D(54), D(133110)
FACTORS = {"KWARTAAL": 4, "MAAND": 12, "VIERWEKEN": 13, "WEEK": 52}
# Table 2 per age group: 1 = younger than AOW age, 2 = AOW age, born 1945 or earlier, 3 = AOW age, born 1946 or later.
TARIFF = {
    1: {"a2": D(38883), "a3": D(78426), "b1": D("0.3575"), "b2": D("0.3756"), "b3": D("0.4950"), "c2": D(13900), "c3": D(28752)},
    2: {"a2": D(41123), "a3": D(78426), "b1": D("0.1785"), "b2": D("0.3756"), "b3": D("0.4950"), "c2": D(7340), "c3": D(21351)},
    3: {"a2": D(38883), "a3": D(78426), "b1": D("0.1785"), "b2": D("0.3756"), "b3": D("0.4950"), "c2": D(6940), "c3": D(21792)},
}
# Tables 3-6: younger than AOW age (1) and AOW age and older (2, 3).
AHK = {"young": (D(3115), D("0.06398")), "aow": (D(1556), D("0.03195"))}
AHKG1, AHKG2 = D(29736), D(78426)
OUK = (D(2067), D(46002), D(59782), D("0.15000"))
AOK = D(540)
ARK = {
    "young": {"o1": D("0.08324"), "o2": D("0.31009"), "o3": D("0.01950"), "a1": D("0.06510"), "m1": D(996), "m2": D(5300), "m3": D(5685)},
    "aow": {"o1": D("0.04156"), "o2": D("0.15483"), "o3": D("0.00974"), "a1": D("0.03250"), "m1": D(498), "m2": D(2647), "m3": D(2840)},
}
ARKG = (D(11965), D(25845), D(45592), D(132920))


def r5(x):
    return x.quantize(D("0.00001"), rounding=ROUND_HALF_UP)


def ceil0(x):
    return x.quantize(D(1), rounding=ROUND_CEILING)


def floor0(x):
    return x.quantize(D(1), rounding=ROUND_FLOOR)


def annual_wage(tvl, f):
    raw = tvl * f
    if raw <= 0:
        return D(0)
    if raw <= LMAX:
        return floor0(raw / LV) * LV
    return r5(raw)


def x1(L, group):
    t = TARIFF[group]
    if L <= t["a2"]:
        v = L * t["b1"]
    elif L <= t["a3"]:
        v = (L - t["a2"]) * t["b2"] + t["c2"]
    else:
        v = (L - t["a3"]) * t["b3"] + t["c3"]
    return max(floor0(v), D(0))


def ahk(L, group):
    m, a = AHK["young" if group == 1 else "aow"]
    if L <= AHKG1:
        return m
    if L <= AHKG2:
        return max(ceil0(m - (L - AHKG1) * a), D(0))
    return D(0)


def ouk(L, group):
    if group == 1:
        return D(0)
    m, g1, g2, a = OUK
    if L <= g1:
        return m
    if L <= g2:
        return max(ceil0(m - (L - g1) * a), D(0))
    return D(0)


def ark(L, group):
    p = ARK["young" if group == 1 else "aow"]
    g1, g2, g3, g4 = ARKG
    if L > g4:
        return D(0)
    s1 = min(r5(p["o1"] * L), p["m1"])
    s2 = min(s1 + r5(p["o2"] * max(L - g1, D(0))), p["m2"])
    s3 = min(s2 + r5(p["o3"] * max(L - g2, D(0))), p["m3"])
    return max(ceil0(s3 - r5(p["a1"] * max(L - g3, D(0)))), D(0))


def annual(L, group, lhk, aok):
    """(X, verrekende ARK) on an annual wage L <= Lmax."""
    base = x1(L, group)
    if not lhk:
        return base, D(0)
    credits = {"AHK": ahk(L, group), "OUK": ouk(L, group), "ARK": ark(L, group), "AOK": AOK if aok and group != 1 else D(0)}
    total = sum(credits.values())
    if base >= total:
        return floor0(base - total), credits["ARK"]
    # Top the credits off in the order AOK, ARK, OUK, AHK until they equal X1.
    excess = total - base
    for name in ("AOK", "ARK", "OUK", "AHK"):
        cut = min(excess, credits[name])
        credits[name] -= cut
        excess -= cut
    return D(0), credits["ARK"]


def period_tax(tvl, f, group, lhk, aok=False):
    """(loonbelasting/premie volksverzekeringen, verrekende arbeidskorting) for a period wage tvl."""
    L = annual_wage(tvl, f)
    if L <= LMAX:
        X, ark_ = annual(L, group, lhk, aok)
        return (X / f).quantize(D("0.01"), rounding=ROUND_HALF_UP), (ark_ / f).quantize(D("0.01"), rounding=ROUND_HALF_UP)
    # Above the highest table wage: the tax at the highest table wage plus the top rate on the excess over
    # it (as printed, Lmax/F rounded up to cents), rounded down in the employee's favour.
    X, ark_ = annual(LMAX, group, lhk, aok)
    y = (X / f).quantize(D("0.01"), rounding=ROUND_HALF_UP)
    top = (LMAX / f).quantize(D("0.01"), rounding=ROUND_CEILING)
    return y + ((tvl - top) * TARIFF[group]["b3"]).quantize(D("0.01"), rounding=ROUND_FLOOR), (ark_ / f).quantize(D("0.01"), rounding=ROUND_HALF_UP)


COLUMNS = [  # (group, loonheffingskorting, alleenstaande-ouderenkorting, column is verrekende arbeidskorting)
    (1, False, False, False), (1, True, False, False), (1, True, False, True),
    (2, False, False, False), (2, True, False, False), (2, True, True, False), (2, True, True, True),
    (3, False, False, False), (3, True, False, False), (3, True, True, False), (3, True, True, True),
]


def read_table(path):
    import openpyxl
    import warnings
    warnings.filterwarnings("ignore")
    rows = []
    for r in openpyxl.load_workbook(path, data_only=True).active.iter_rows(values_only=True):
        if isinstance(r[0], (int, float)) and all(isinstance(v, (int, float)) for v in r[:12]):
            rows.append([D(str(v)).quantize(D("0.01")) for v in r[:12]])
    return rows


def check_tables(paths):
    """paths: {factor name: xlsx path}. Returns the number of compared amounts; raises on a difference."""
    compared = 0
    for name, path in paths.items():
        f = FACTORS[name]
        rows = read_table(path)
        assert rows[0][0] == (LV / f).quantize(D("0.01"), rounding=ROUND_CEILING), (name, rows[0][0])
        assert rows[-1][0] == (LMAX / f).quantize(D("0.01"), rounding=ROUND_CEILING), (name, rows[-1][0])
        for i, row in enumerate(rows):
            # The row's table wage is ceil(L/F); any wage from it to the next row's gives the row's amounts.
            assert row[0] == (LV * (i + 1) / f).quantize(D("0.01"), rounding=ROUND_CEILING), (name, i, row[0])
            for col, (group, lhk, aok, ark_col) in enumerate(COLUMNS, start=1):
                tax, ark_ = period_tax(row[0], f, group, lhk, aok)
                got = ark_ if ark_col else tax
                assert got == row[col], (name, str(row[0]), col, str(row[col]), str(got))
                compared += 1
    return compared
