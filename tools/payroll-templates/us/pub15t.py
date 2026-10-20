"""Parse IRS Publication 15-T (2026) — text extracted with ../bg/pdftext.py — into its tables.

- annual_schedules(): the Annual Percentage Method tables (section 1), keyed (schedule, filing status),
  each a list of (A at least, B less than or None, C tentative amount, D rate).
- wage_brackets(): every row of the Wage Bracket Method tables, section 2 (Forms W-4 2020 or later:
  six tentative amounts per row) and section 3 (2019 or earlier: allowances 0-10 per marital status).

Parsing is strict: a row with an unexpected shape, a gap between brackets, or a page without the
expected headings raises, so a layout change can't silently drop rows.
"""
import re
from decimal import Decimal as D

MONEY = r"\$?([\d,]+(?:\.\d\d)?)"
FREQUENCIES = {"WEEKLY": "WEEKLY", "BIWEEKLY": "BIWEEKLY", "SEMIMONTHLY": "SEMIMONTHLY", "MONTHLY": "MONTHLY", "DAILY": "DAILY"}
SECTION2_COLUMNS = [("MFJ", "STANDARD"), ("MFJ", "CHECKBOX"), ("HOH", "STANDARD"), ("HOH", "CHECKBOX"),
                    ("SINGLE", "STANDARD"), ("SINGLE", "CHECKBOX")]


def money(text):
    return D(text.replace("$", "").replace(",", ""))


def pages(text):
    out = []
    for chunk in re.split(r"^=== page (\d+)$", text, flags=re.M)[1:]:
        out.append(chunk)
    return [(int(out[i]), out[i + 1]) for i in range(0, len(out), 2)]


def annual_schedules(text):
    page = dict(pages(text))[12]
    lines = page.splitlines()
    status_lines = {"Married Filing Jointly Married Filing Jointly": "MFJ",
                    "Single or Married Filing Separately Single or Married Filing Separately": "SINGLE",
                    "Head of Household Head of Household": "HOH"}
    row = re.compile(r"^\$([\d,]+) (?:\$([\d,]+) )?\$([\d,]+\.\d\d) (\d+)% \$([\d,]+)"
                     r" \$([\d,]+) (?:\$([\d,]+) )?\$([\d,]+\.\d\d) (\d+)% \$([\d,]+)$")
    schedules, status = {}, None
    for line in lines:
        line = line.strip()
        if line in status_lines:
            status = status_lines[line]
            schedules[("STANDARD", status)], schedules[("CHECKBOX", status)] = [], []
            continue
        m = row.match(line)
        if m:
            assert status, line
            for offset, schedule in ((0, "STANDARD"), (5, "CHECKBOX")):
                a, b, c, d, e = (m.group(offset + i) for i in range(1, 6))
                assert a == e, ("column E must equal column A", line)
                schedules[(schedule, status)].append((money(a), money(b) if b else None, money(c), D(d) / 100))
    assert len(schedules) == 6, schedules.keys()
    for key, rows in schedules.items():
        assert len(rows) == 8, (key, len(rows))
        assert rows[0][0] == 0 and rows[-1][1] is None, key
        for previous, current in zip(rows, rows[1:]):
            assert previous[1] == current[0], ("gap", key, previous, current)
    return schedules


def wage_brackets(text):
    """Yields dicts: section (2 or 3), frequency, lower, upper, and amounts {column: value}."""
    rows = []
    for number, page in pages(text):
        if "Wage Bracket Method Tables for Manual Payroll Systems" not in page or "Payroll Period" not in page:
            continue
        header = re.search(r"^(WEEKL Y|BIWEEKL Y|SEMIMONTHL Y|MONTHL Y|DAIL Y) Payroll Period", page, flags=re.M)
        if not header or not re.search(r"^2026 Wage Bracket Method Tables", page, flags=re.M):
            continue
        frequency = FREQUENCIES[header.group(1).replace(" ", "")]
        section = 2 if "Forms W-4 From 2020 or Later" in page else 3
        status = None
        if section == 3:
            persons = re.findall(r"^(MARRIED|SINGLE) Persons", page, flags=re.M)
            assert len(persons) == 1, (number, persons)
            status = persons[0]
        width = 6 if section == 2 else 11
        for line in page.splitlines():
            if not line.startswith("$"):
                continue
            values = re.findall(r"\$([\d,]+(?:\.\d\d)?)", line)
            assert line.replace(" ", "") == "".join("$" + v for v in values), (number, line)
            assert len(values) == width + 2, (number, line)
            amounts = [money(v) for v in values]
            columns = SECTION2_COLUMNS if section == 2 else [(status, n) for n in range(11)]
            rows.append({"section": section, "page": number, "frequency": frequency, "status": status,
                         "lower": amounts[0], "upper": amounts[1], "amounts": dict(zip(columns, amounts[2:]))})
    # Contiguity within each table.
    tables = {}
    for r in rows:
        tables.setdefault((r["section"], r["frequency"], r["status"]), []).append(r)
    for key, table in tables.items():
        assert table[0]["lower"] == 0, key
        for previous, current in zip(table, table[1:]):
            assert previous["upper"] == current["lower"], ("gap", key, previous["upper"], current["lower"])
    return rows
