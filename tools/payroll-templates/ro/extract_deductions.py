"""Extract the basic personal deduction table of Codul fiscal art. 77(4) into deduction-table-art77.csv.

Reads ANAF's consolidated Fiscal Code (https://static.anaf.ro/static/10/Anaf/legislatie/Cod_fiscal_norme_2023.htm,
saved as HTML). The page is updated in place, so it is identified by its "actualizare" marker rather than a
hash. The table's 41 bands (up to the minimum wage, then 50 lei each up to minimum wage + 2,000 lei) are
checked against the structure the article describes before anything is written.

Usage: python3 -I extract_deductions.py <saved Cod_fiscal_norme_2023.htm> deduction-table-art77.csv
"""
import csv
import html
import re
import sys
from decimal import Decimal

EXPECTED_UPDATE = "LEGEA nr. 170/2026"


def text_lines(path):
    with open(path, encoding="utf-8") as f:
        t = f.read()
    t = re.sub(r"(?s)<script.*?</script>|<style.*?</style>", "", t)
    t = html.unescape(re.sub(r"<[^>]+>", "\n", t))
    return [line.strip() for line in t.split("\n") if line.strip()]


def percent(cell):
    # The official text has typos such as "3,50%%" and "00,50%".
    return Decimal(re.sub(r"%+", "", cell).replace(",", ".")) / 100


def main():
    lines = text_lines(sys.argv[1])
    updates = [line for line in lines if line.startswith("ultima actualizare:")]
    if not updates or EXPECTED_UPDATE not in updates[0]:
        sys.exit(f"the Fiscal Code page is at {updates[:1]}, not {EXPECTED_UPDATE}; re-check art. 77 before updating EXPECTED_UPDATE")
    start = next(i for i, line in enumerate(lines) if line.startswith("(4) Pentru contribuabilii care realizează venituri brute lunare din salarii"))
    first = lines.index("salariul minim", start)
    rows = [(0, [percent(v) for v in lines[first + 1:first + 6]])]
    k = first + 6
    while lines[k].startswith("salariul minim +"):
        upper = int(re.search(r"\+ ?(\d+)", lines[k + 1]).group(1))
        rows.append((upper, [percent(v) for v in lines[k + 2:k + 7]]))
        k += 7
    if len(rows) != 41:
        sys.exit(f"expected 41 bands, found {len(rows)}")
    base = rows[0][1]
    for band, (upper, values) in enumerate(rows):
        expected = [max(Decimal(0), b - Decimal("0.005") * band) for b in base]
        if upper != 50 * band or values != expected:
            sys.exit(f"band {band} (up to minimum + {upper}) is {values}, expected {expected}")
    with open(sys.argv[2], "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["band", "upToMinimumPlus", "dependents0", "dependents1", "dependents2", "dependents3", "dependents4OrMore"])
        for band, (upper, values) in enumerate(rows):
            w.writerow([band, upper] + [str(v.normalize()) if v else "0" for v in values])
    print("wrote", sys.argv[2], "with", len(rows), "bands")


main()
