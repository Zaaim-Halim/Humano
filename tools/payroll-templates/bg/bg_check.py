import json
import sys

d = json.load(open(sys.argv[1], encoding="utf-8"))

# 1. TZPB: unique codes, same code set, and the 2 -> 2A diff equals NOI's published list.
noi_changes = {"10": ("0.7", "0.9"), "19": ("0.9", "1.1"), "59": ("0.4", "0.5"), "71": ("0.7", "0.5"), "82": ("0.4", "0.5"), "93": ("0.5", "0.7"), "99": ("0.5", "0.4")}
t1 = {r["codes"]["include"][0]: r["rate"] for r in d["annex2"]}
t2 = {r["codes"]["include"][0]: r["rate"] for r in d["annex2A"]}
assert all(len(r["codes"]["include"]) == 1 and not r["codes"]["exclude"] for r in d["annex2"] + d["annex2A"]), "TZPB rows must be single codes"
assert len(t1) == len(d["annex2"]) == 87 and len(t2) == len(d["annex2A"]) == 87, "duplicate TZPB codes"
assert set(t1) == set(t2), f"code sets differ: {set(t1) ^ set(t2)}"
diff = {c: (t1[c], t2[c]) for c in t1 if t1[c] != t2[c]}
assert diff == noi_changes, f"2->2A diff {diff} != NOI {noi_changes}"
print("TZPB OK: 87 unique codes; 2->2A diff matches NOI's 7 published changes exactly")


# 2. Minimum insurable income: each TZPB division resolves to exactly one full row (division-level lookup).
def matches(codes, kid):
    if codes is None:
        return False
    inc = any(kid == c or kid.startswith(c + ".") for c in codes["include"])
    exc = any(kid == c or kid.startswith(c + ".") for c in codes["exclude"])
    return inc and not exc


for k in ("annex1", "annex1A"):
    rows = d[k]
    problems = []
    for div in sorted(t1):
        hits = [r for r in rows if matches(r["codes"], div)]
        full = [r for r in hits if "byGroup" in r]
        if len(full) != 1:
            problems.append((div, [(r["seq"], r["rawCodes"], "byGroup" in r) for r in hits]))
    # Class-level codes named in rows (e.g. 01.48) must resolve to the specific row, not the excluding one.
    classes = sorted({c for r in rows if r["codes"] for c in r["codes"]["include"] + r["codes"]["exclude"] if "." in c})
    class_hits = {c: [(r["seq"], r["rawCodes"], "byGroup" in r) for r in rows if matches(r["codes"], c)] for c in classes}
    print(f"{k}: {len(rows)} rows; divisions without exactly one full row: {problems or 'none'}")
    print(f"  class codes: {class_hits}")
