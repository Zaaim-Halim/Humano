"""Transpile the BMF/ITZBund XML pseudocode of the Programmablaufplan (PAP) für den maschinellen
Lohnsteuerabzug into a Java class, mechanically and without hand edits.

The XML's EVAL/IF expressions are already Java; the only rewrite is the deprecated
BigDecimal.ROUND_* int constants to the equivalent RoundingMode values (same semantics, same
overloads: divide(BigDecimal, int, RoundingMode), setScale(int, RoundingMode)). The generated file
carries the XML's SHA-256, and the script refuses to write unless the generated code contains
exactly as many methods, assignments, conditions and calls as the XML.

Usage: python3 -I pap2java.py Lohnsteuer2026.xml Lohnsteuer2026 <output .java>
"""
import hashlib
import re
import sys
import xml.etree.ElementTree as ET

SRC, CLASS, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
raw = open(SRC, "rb").read()
sha = hashlib.sha256(raw).hexdigest()
root = ET.fromstring(raw, parser=ET.XMLParser(target=ET.TreeBuilder(insert_comments=True)))
COMMENT = ET.Comment


def default_of(e):
    # The 2026 XML spells one attribute "defaul" (VJAHR); it is a default all the same.
    return e.get("default", e.get("defaul"))


def java_default(type_, value):
    if value is not None:
        return value
    return {"int": "0", "double": "0.0", "BigDecimal": "BigDecimal.ZERO"}[type_]


def expr(text):
    return re.sub(r"BigDecimal\.ROUND_(UP|DOWN|CEILING|FLOOR|HALF_UP|HALF_DOWN|HALF_EVEN|UNNECESSARY)", r"RoundingMode.\1", text.strip())


def comment_lines(text, indent):
    return [indent + "// " + line.strip() for line in text.strip().splitlines() if line.strip()]


counts = {"METHOD": 0, "EVAL": 0, "IF": 0, "EXECUTE": 0}


def body(elem, indent):
    lines = []
    for child in elem:
        if child.tag is COMMENT:
            lines += comment_lines(child.text or "", indent)
        elif child.tag == "EVAL":
            counts["EVAL"] += 1
            lines.append(f"{indent}{expr(child.get('exec'))};")
        elif child.tag == "EXECUTE":
            counts["EXECUTE"] += 1
            lines.append(f"{indent}{child.get('method')}();")
        elif child.tag == "IF":
            counts["IF"] += 1
            lines.append(f"{indent}if ({expr(child.get('expr'))}) {{")
            then, other = child.find("THEN"), child.find("ELSE")
            lines += body(then, indent + "    ") if then is not None else []
            if other is not None:
                lines.append(f"{indent}}} else {{")
                lines += body(other, indent + "    ")
            lines.append(f"{indent}}}")
        else:
            raise SystemExit(f"unexpected element {child.tag}")
    return lines


out = [
    "package com.humano.service.payroll.template.extension.de;",
    "",
    "import java.math.BigDecimal;",
    "import java.math.RoundingMode;",
    "",
    "/**",
    f" * GENERATED — do not edit. Transpiled by tools/payroll-templates/de/pap2java.py from the BMF/ITZBund",
    f" * XML pseudocode {root.get('name')} (version {root.get('version')}), SHA-256 {sha}:",
    " * Programmablaufplan für die maschinelle Berechnung der vom Arbeitslohn einzubehaltenden Lohnsteuer,",
    " * des Solidaritätszuschlags und der Maßstabsteuer für die Kirchenlohnsteuer (BMF-Schreiben vom",
    " * 12. November 2025, IV C 5 - S 2361/00025/016/028, Anlage 1). Names and statements are the PAP's own.",
    " * Not thread-safe: use one instance per calculation.",
    " */",
    "@SuppressWarnings({ \"all\" })",
    f"final class {CLASS} {{",
]


def declare(section, modifier=""):
    lines = []
    for e in root.find(section) if root.find(section) is not None else []:
        if e.tag is COMMENT:
            lines += comment_lines(e.text or "", "    ")
            continue
        t = e.get("type")
        value = e.get("value") if e.tag == "CONSTANT" else java_default(t, default_of(e))
        lines.append(f"    {modifier}{t} {e.get('name')} = {expr(value)};")
    return lines


out += ["", "    // ---- Eingangsparameter"] + declare("VARIABLES/INPUTS")
for outputs in root.findall("VARIABLES/OUTPUTS"):
    out += ["", "    // ---- Ausgangsparameter"]
    for e in outputs:
        if e.tag is COMMENT:
            out += comment_lines(e.text or "", "    ")
        else:
            out.append(f"    {e.get('type')} {e.get('name')} = {expr(java_default(e.get('type'), default_of(e)))};")
out += ["", "    // ---- Interne Felder"] + declare("VARIABLES/INTERNALS")
out += ["", "    // ---- Konstanten"] + declare("CONSTANTS", "static final ")

methods = root.find("METHODS")
for m in methods:
    if m.tag is COMMENT:
        out += [""] + comment_lines(m.text or "", "    ")
        continue
    if m.tag == "MAIN":
        out += ["", "    /** The PAP's main program. */", "    void main() {"] + body(m, "        ") + ["    }"]
    else:
        counts["METHOD"] += 1
        out += ["", f"    private void {m.get('name')}() {{"] + body(m, "        ") + ["    }"]
out.append("}")

xml_counts = {tag: sum(1 for _ in root.iter(tag)) for tag in counts}
assert counts == xml_counts, (counts, xml_counts)
java = "\n".join(out) + "\n"
generated = {"METHOD": len(re.findall(r"^    private void \w+\(\) \{$", java, re.M)),
             "EXECUTE": len(re.findall(r"^\s+\w+\(\);$", java, re.M)),
             "IF": len(re.findall(r"^\s+if \(", java, re.M))}
assert generated == {k: xml_counts[k] for k in generated}, (generated, xml_counts)
open(OUT, "w", encoding="utf-8").write(java)
print("wrote", OUT, "from", SRC, "sha256", sha, "counts", xml_counts)
