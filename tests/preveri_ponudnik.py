#!/usr/bin/env python3
"""MobileFileProvider: samo branje, nic ne ustvari, poti samo prek PonudnikPravila (tedenski pregled 4. 11. 2026)."""
import sys
from pathlib import Path
koren = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent
s = (koren / "src/main/kotlin/com/safeer/mobile/browser/MobileFileProvider.kt").read_text(encoding="utf-8")
napake = [o for o, pogoj in [
    ("pisanje (MODE_CREATE/WRITE)", "MODE_CREATE" in s or "MODE_WRITE" in s or "MODE_READ_WRITE" in s),
    ("ustvarjanje map (mkdirs)", "mkdirs(" in s),
    ("filesDir brez podmape", "safeChild(ctx.filesDir" in s),
    ("brez PonudnikPravila.razcleni", "PonudnikPravila.razcleni(" not in s),
    ("brez PonudnikPravila.samoBranje", "PonudnikPravila.samoBranje(" not in s),
] if pogoj]
for n in napake:
    print("  NAPAKA", n)
if napake:
    print(f"PONUDNIK: {len(napake)} napak"); sys.exit(1)
print("PONUDNIK: OK")
