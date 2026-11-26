#!/usr/bin/env python3
"""S11 (tedenski pregled 4. 11. 2026): vprasanje o prijavi racunalnika prek QR v vseh 6 jezikih opozori, da naj uporabnik
dovoli samo kodo, ki jo je pravkar poskeniral (safeer://link/qr lahko odpre katerakoli spletna stran)."""
import re, sys
from pathlib import Path
koren = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent
s = (koren / "src/main/kotlin/com/safeer/mobile/browser/link/QrPrijavaActivity.kt").read_text(encoding="utf-8")
for kljuc in ("vprasanje", "vprasajPridruzi"):
    m = re.search(r'"%s" to mapOf\((.*?)\),\n' % kljuc, s, re.S)
    if not m:
        print(f"QR OPOZORILO: ni besedila {kljuc}"); sys.exit(1)
    for jezik in ("sl", "en", "de", "es", "fr", "it"):
        b = re.search(r'"%s" to "([^"]*)"' % jezik, m.group(1))
        if not b or "\\n\\n" not in b.group(1):
            print(f"QR OPOZORILO: {kljuc}/{jezik} brez opozorila"); sys.exit(1)
print("QR OPOZORILO: OK")
