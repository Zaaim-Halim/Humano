"""Parse the annexes of the Bulgarian ZBDOO 2026 (text extracted with pypdf) into JSON.

Annex 1 / 1A: minimum monthly insurable income by KID-2025 activity x 9 NKPD qualification groups.
Annex 2 / 2A: work-accident (TZPB) contribution % by KID-2025 activity.

Strict: any row that doesn't have the expected shape aborts with an error.
"""
import json
import re
import sys

MONEY = re.compile(r"^\d{3,4}\.\d{2}$")
SECTION = re.compile(r"^[A-ZА-Я][,;]?$")
RATE = re.compile(r"^[01][,.]\d$")
CODE_TOKEN = re.compile(r"^\d{1,2}(\.\d{1,2})?[;,]?$")


def load(path):
    return open(path, encoding="utf-8").read().splitlines()


def section(lines, start_marker, end_marker):
    s = next(i for i, l in enumerate(lines) if l.strip() == start_marker)
    e = next(i for i, l in enumerate(lines) if i > s and l.strip() == end_marker)
    return lines[s:e]


def tokens(lines):
    out = []
    for l in lines:
        if l.startswith("=== page"):
            continue
        out.extend(l.split())
    return out


def norm_code(c):
    c = c.rstrip(";,")
    if "." in c:
        a, b = c.split(".")
        return f"{int(a):02d}.{b}"
    return f"{int(c):02d}"


def parse_codes(code_tokens):
    """'01 без 1.48; 03' -> {'include': ['01','03'], 'exclude': ['01.48']}"""
    include, exclude, mode = [], [], "inc"
    for t in code_tokens:
        if t == "без":
            mode = "exc"
            continue
        if t in ("и",):
            continue
        group_end = t.endswith(";")
        (include if mode == "inc" else exclude).append(norm_code(t))
        if group_end:
            mode = "inc"
    if not include:
        raise ValueError(f"no included code in {code_tokens}")
    return {"include": include, "exclude": exclude}


def split_head(head):
    """head tokens: seq, section letter, code tokens..., name tokens..."""
    if not head or not head[0].isdigit():
        raise ValueError(f"row head does not start with a sequence number: {head[:6]}")
    seq = int(head[0])
    i, letters = 1, []
    while i < len(head) and SECTION.match(head[i]):
        letters.append(head[i].rstrip(",;"))
        i += 1
    section_letter = ",".join(letters) or None  # organisation rows (e.g. a cooperative union) have none
    codes = []
    while i < len(head) and (CODE_TOKEN.match(head[i]) or head[i] in ("без", "и") and i + 1 < len(head) and CODE_TOKEN.match(head[i + 1])):
        codes.append(head[i])
        i += 1
    name = "".join(head[i:])  # pypdf splits words at column wraps; joined without spaces is still searchable
    return seq, section_letter, parse_codes(codes) if codes else None, " ".join(codes), name


def parse_min_income(lines):
    toks = tokens(lines)
    # Data starts after the column-number header "1 2 3 ... 9" that follows the group names.
    start = next(i for i in range(len(toks) - 9) if toks[i : i + 9] == [str(n) for n in range(1, 10)])
    toks = toks[start + 9 :]
    rows, head = [], []
    i = 0
    while i < len(toks):
        if MONEY.match(toks[i]):
            n = 0
            while i + n < len(toks) and n < 9 and MONEY.match(toks[i + n]):
                n += 1
            vals = toks[i : i + n]
            expected = str(len(rows) + 1)
            k = next(
                (j for j in range(len(head) - 1) if head[j] == expected and not MONEY.match(head[j + 1])),
                None,
            )
            if k is None:
                raise ValueError(f"row {expected}: no '{expected} <section>' start in {head[:8]}")
            if k > 0:
                if not rows:
                    raise ValueError(f"unexpected text before row 1: {head[:k]}")
                rows[-1]["name"] += "".join(head[:k])
            head = head[k:]
            seq, letter, codes, raw_codes, name = split_head(head)
            row = {"seq": seq, "section": letter, "codes": codes, "rawCodes": raw_codes, "name": name}
            if n == 9:
                row["byGroup"] = vals
            else:
                # Column positions are lost in extraction, so the groups these values belong to are unknown.
                row["partialValues"] = vals
            rows.append(row)
            head = []
            i += n
            continue
        if toks[i] == "Забележки:":
            if head and rows:
                rows[-1]["name"] += "".join(head)
            break
        head.append(toks[i])
        i += 1
    seqs = [r["seq"] for r in rows]
    if seqs != list(range(1, len(rows) + 1)):
        raise ValueError(f"row sequence numbers are not 1..n: {seqs}")
    return rows


def parse_tzpb(lines):
    toks = tokens(lines)
    start = next(i for i in range(len(toks) - 2) if toks[i : i + 3] == ["1", "2", "3"])
    toks = toks[start + 3 :]
    rows, cur = [], None
    for t in toks:
        if t.startswith("Забележка"):
            break
        is_code = CODE_TOKEN.match(t) and not RATE.match(t)
        if is_code and (cur is None or cur["rate"] is not None or not cur["nameParts"]):
            if cur is None or cur["rate"] is not None:
                cur = {"codeTokens": [], "nameParts": [], "rate": None}
                rows.append(cur)
            cur["codeTokens"].append(t)
        elif t in ("без", "и") and cur is not None and cur["rate"] is None and not cur["nameParts"]:
            cur["codeTokens"].append(t)
        elif RATE.match(t) and cur is not None and cur["rate"] is None:
            cur["rate"] = t.replace(",", ".")
        elif cur is not None:
            cur["nameParts"].append(t)
    out = []
    for r in rows:
        if r["rate"] is None:
            raise ValueError(f"TZPB row {r['codeTokens']} has no rate")
        out.append({"codes": parse_codes(r["codeTokens"]), "rawCodes": " ".join(r["codeTokens"]), "name": "".join(r["nameParts"]), "rate": r["rate"]})
    return out


def main():
    lines = load(sys.argv[1])
    out = {
        "annex1": parse_min_income(section(lines, "Приложение № 1", "Приложение № 1А")),
        "annex1A": parse_min_income(section(lines, "Приложение № 1А", "Приложение № 2")),
        "annex2": parse_tzpb(section(lines, "Приложение № 2", "Приложение № 2А")),
        "annex2A": parse_tzpb(section(lines, "Приложение № 2А", "Приложение № 3")),
    }
    for k, v in out.items():
        print(k, len(v), file=sys.stderr)
    json.dump(out, open(sys.argv[2], "w", encoding="utf-8"), ensure_ascii=False, indent=1)


main()
