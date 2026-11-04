#!/usr/bin/env python3
"""Vhodna vrata telefona (UXSS, 4. 11. 2026, prenos s TV): vsak vhod, po katerem pride naslov od zunaj v WebView,
gre skozi SpletVarnostPravila.vhodniNaslov (namera VIEW, deljeno besedilo, Safeer Link cast.url, share.screen,
open_url), obe razlicici ChromiumEngineView.loadUrl pa zavrneta javascript:/data:. Pravilo samo preverja
SpletVarnostPravilaTest.kt; ta preverba pazi, da ga klicejo vsi vhodi. Pade, ce kateri vhod preverbo izgubi."""
import pathlib
import re
import sys

KOREN = pathlib.Path(__file__).resolve().parent.parent
B = KOREN / "src/main/kotlin/com/safeer/mobile/browser"
PREVERBA = re.compile(r"varenVhod\(|SpletVarnostPravila\.vhodniNaslov\(")


def telo(besedilo: str, glava: str, vrstic: int) -> str:
    i = besedilo.find(glava)
    if i < 0:
        return ""
    return "\n".join(besedilo[i:].splitlines()[:vrstic])


def main() -> int:
    napake = []
    pravila = [
        # (datoteka, glava, stevilo vrstic, niz, pred katerim mora biti preverba, ali None)
        ("MainActivity.kt", "Intent.ACTION_VIEW -> {", 12, "targetUrl = intent.dataString"),
        ("MainActivity.kt", "Intent.ACTION_SEND -> {", 8, None),
        ("MainActivity.kt", "private fun odpriOdZunaj(", 3, "openUrlInBrowser("),
        ("link/LinkSprejemnik.kt", '"cast.url" -> {', 4, "odpriStran("),
        ("link/LinkSprejemnik.kt", '"share.screen" -> {', 7, "odpriStran("),
        ("link/LinkMost.kt", '"cast.url" -> {', 5, "odpriVBrskalniku("),
        ("link/LinkMost.kt", '"share.screen" -> {', 8, "odpriVBrskalniku("),
        ("link/Daljinec.kt", "private fun odpriUrl(", 6, "odpriStran("),
    ]
    cev = (B / "ChromiumEngineView.kt").read_text(encoding="utf-8")
    for glava in ("override fun loadUrl(url: String) {", "override fun loadUrl(url: String, additionalHttpHeaders"):
        if "SpletVarnostPravila.skriptniNaslov(url)" not in telo(cev, glava, 4):
            napake.append(f"ChromiumEngineView.kt: '{glava}' brez skriptniNaslov")
    for datoteka, glava, vrstic, pred in pravila:
        besedilo = (B / datoteka).read_text(encoding="utf-8")
        del_ = telo(besedilo, glava, vrstic)
        if not del_:
            napake.append(f"{datoteka}: ni najden '{glava}'")
            continue
        m = PREVERBA.search(del_)
        if not m:
            napake.append(f"{datoteka}: '{glava}' brez SpletVarnostPravila.vhodniNaslov")
        elif pred and pred in del_ and del_.find(pred) < m.start():
            napake.append(f"{datoteka}: '{glava}' preveri naslov sele po '{pred}'")
    main_kt = (B / "MainActivity.kt").read_text(encoding="utf-8")
    if re.search(r"targetUrl = intent\.dataString\s*$", main_kt, re.M):
        napake.append("MainActivity.kt: ACTION_VIEW vzame dataString brez preverbe")
    # Povratni klici Linka (naStran, LinkMost odpri) ne smejo naravnost v openUrlInBrowser.
    if re.search(r"naStran = \{ url -> runOnUiThread \{ openUrlInBrowser\(", main_kt):
        napake.append("MainActivity.kt: LinkSprejemnik.naStran brez preverbe")
    if re.search(r"\{ naslov -> openUrlInBrowser\(naslov\) \},\s*\{ cilj -> izberiDatotekoZaLink", main_kt):
        napake.append("MainActivity.kt: odpiranje iz LinkMost brez preverbe")
    for n in napake:
        print("NAPAKA:", n)
    if napake:
        return 1
    print(f"preveri_vhode_brskalnika: OK ({len(pravila)} vhodov + 2 x loadUrl)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
