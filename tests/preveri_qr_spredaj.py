#!/usr/bin/env python3
"""Pregled 4. 11. 2026: potrditev prijave in pridruzitve s QR (safeer://link/qr, https://safeer.si/p) velja samo, ko je
Safeer spredaj in vprasanje vidno. Oba gumba za potrditev gresta skozi varnoVprasanje (QrPravila.potrditevVelja,
filterTouchesWhenObscured, fokus okna); noben setPositiveButton s takojsnjim dejanjem."""
import re, sys
from pathlib import Path
koren = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent
s = (koren / "src/main/kotlin/com/safeer/mobile/browser/link/QrPrijavaActivity.kt").read_text(encoding="utf-8")
napake = []
if re.search(r'setPositiveButton\(b\("(dovoli|pridruzi)"\)\)', s):
    napake.append("potrditev brez preverbe, ali je Safeer spredaj")
for dejanje in (r'\{ povezi\(p\) \}', r'\{ dovoli\(ime\) \}'):
    if not re.search(r'varnoVprasanje\(.*?' + dejanje, s, re.S):
        napake.append("dejanje %s ni za varnoVprasanje" % dejanje)
for nujno in ("QrPravila.potrditevVelja(spredaj", "filterTouchesWhenObscured = true", "hasWindowFocus()",
              "override fun onPause()", "spredaj = false"):
    if nujno not in s:
        napake.append("manjka: " + nujno)
if napake:
    print("QR SPREDAJ:", "; ".join(napake)); sys.exit(1)
print("QR SPREDAJ: OK")
