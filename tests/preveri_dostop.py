#!/usr/bin/env python3
"""Tedenski pregled 4. 11. 2026: telefon je izvedel vsak control.command katerekoli naprave v Linku.

Preveri, da gre vsak dohodni ukaz in vsako samodejno odpiranje (stran, zaslon, usklajevanje) skozi cast/Dostop, preden
se kaj izvede. Pravila sama preveri DostopPravilaTest.kt (ista kot na TV).
  python3 tests/preveri_dostop.py [koren_repozitorija]
"""
import re
import sys
from pathlib import Path

koren = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent
SRC = koren / "src/main/kotlin/com/safeer/mobile/browser"
napake = []


def telo(pot, zacetek):
    s = (SRC / pot).read_text(encoding="utf-8")
    i = s.find(zacetek)
    if i < 0:
        napake.append(f"{pot}: ne najdem »{zacetek}«")
        return ""
    return s[i:i + 4000]


def pred(besedilo, prvo, drugo, opis):
    a = besedilo.find(prvo)
    b = besedilo.find(drugo)
    if a < 0 or b < 0 or a > b:
        napake.append(opis)


t = telo("link/LinkSprejemnik.kt", "private fun izvediUkaz(")
pred(t, "Dostop.smeDejanje(", "Daljinec.izvedi(", "LinkSprejemnik.izvediUkaz: Daljinec.izvedi brez Dostop.smeDejanje")
t = telo("link/LinkSprejemnik.kt", "private fun prejmi(")
pred(t, "Dostop.smeSporocilo(", "odpriStran(", "LinkSprejemnik.prejmi: stran/zaslon brez Dostop.smeSporocilo")
t = telo("link/LinkMost.kt", "nov.onControl = {")
pred(t, "Dostop.smeDejanje(", "Daljinec.izvedi(", "LinkMost.onControl: Daljinec.izvedi brez Dostop.smeDejanje")
t = telo("link/LinkMost.kt", "private fun prejmiDeljenje(")
pred(t, "Dostop.smeSporocilo(", "odpriVBrskalniku(", "LinkMost.prejmiDeljenje: stran brez Dostop.smeSporocilo")
t = telo("cast/CastSenderClient.kt", '"sync.data" -> {')
pred(t, 'Dostop.smeSporocilo(', "onSyncData?.invoke(", "CastSenderClient: sync.data brez Dostop.smeSporocilo")

# S9: streznik datotek - izklop ustavi in preklice zetone, vsaka zahteva preveri stikalo in dostop imetnika zetona.
t = telo("link/DatotekeStreznik.kt", "private fun postrezi(")
pred(t, "vklopljeno(ctx)", "contentResolver", "DatotekeStreznik.postrezi: brez preverbe vklopljeno()")
pred(t, "Dostop.smeDejanje(", "contentResolver", "DatotekeStreznik.postrezi: brez preverbe dostopa imetnika zetona")
t = telo("link/DatotekeStreznik.kt", "fun ustavi()")
pred(t, "zetoni.clear()", "tece.set(false)", "DatotekeStreznik.ustavi: zetoni ostanejo")
t = telo("link/DatotekeStreznik.kt", "fun nastavi(context: Context, vklop: Boolean)")
pred(t, "if (!vklop) ustavi()", "}", "DatotekeStreznik.nastavi: izklop ne ustavi streznika")

# Vsak klic Daljinec.izvedi v izvorni kodi mora biti v enem od zgornjih (novega mesta brez preverbe ni).
klici = [str(p.relative_to(SRC)) for p in SRC.rglob("*.kt") if re.search(r"Daljinec\.izvedi\(", p.read_text(encoding="utf-8"))]
for k in klici:
    if k not in ("link/LinkSprejemnik.kt", "link/LinkMost.kt"):
        napake.append(f"{k}: Daljinec.izvedi zunaj preverjenih poti")

if napake:
    for n in napake:
        print("  NAPAKA", n)
    print(f"DOSTOP: {len(napake)} napak")
    sys.exit(1)
print("DOSTOP: OK")
