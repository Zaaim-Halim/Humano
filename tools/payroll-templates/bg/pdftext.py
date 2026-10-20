import sys
from pypdf import PdfReader
r = PdfReader(sys.argv[1])
for i, p in enumerate(r.pages, 1):
    print(f"=== page {i}")
    print(p.extract_text())
