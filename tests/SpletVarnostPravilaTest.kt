package com.safeer.mobile.browser

import com.safeer.mobile.browser.SpletVarnostPravila as P

/** Namere iz strani in rezervni naslov (tedenski pregled 4. 11. 2026; ista pravila kot na TV). */
fun main() {
    val nas = "com.safeer.mobile.browser"
    check(P.dovoljenaNamera("android.intent.action.VIEW", "market", null, nas))
    check(P.dovoljenaNamera("android.intent.action.VIEW", "mailto", "com.example.posta", nas))
    check(P.dovoljenaNamera(null, "geo", null, nas)) { "intent: brez akcije je VIEW" }
    check(!P.dovoljenaNamera("android.intent.action.VIEW", "https", nas, nas)) { "lasten paket" }
    check(!P.dovoljenaNamera("android.intent.action.MAIN", "market", null, nas)) { "samo VIEW" }
    check(!P.dovoljenaNamera("android.intent.action.SEND", null, null, nas))
    check(!P.dovoljenaNamera("android.intent.action.CALL", "tel", null, nas))
    check(!P.dovoljenaNamera("android.intent.action.VIEW", null, "com.example", nas)) { "intent: brez podatkov" }
    check(!P.dovoljenaNamera(null, "", null, nas)) { "prazna shema" }
    for (s in listOf("content", "file", "javascript", "data", "blob", "about", "intent", "android-app", "chrome", "CONTENT", "JavaScript")) {
        check(!P.dovoljenaNamera("android.intent.action.VIEW", s, null, nas)) { "shema $s" }
    }
    // UXSS: browser_fallback_url samo http(s) z gostiteljem.
    check(P.varenRezervniNaslov("https://primer.si/pot?x=1"))
    check(P.varenRezervniNaslov("http://primer.si"))
    for (u in listOf(null, "", "javascript:alert(1)", "JavaScript:alert(document.cookie)", "file:///android_asset/brave_home.html",
                     "content://com.safeer.mobile.browser.fileprovider/x", "data:text/html,x", "intent://x#Intent;end",
                     "https:///brez-gostitelja", "about:blank", " javascript:alert(1)", "javascript://%0aalert(1)")) {
        check(!P.varenRezervniNaslov(u)) { "rezervni $u" }
    }
    // Zunanjo aplikacijo odpre samo uporabnikovo dejanje (kretnja ali dotik pred najvec 3 s), eno dejanje en zagon.
    check(P.dovoljenZunanjiZagon(kretnja = true, msOdDejanja = Long.MAX_VALUE))
    check(P.dovoljenZunanjiZagon(kretnja = false, msOdDejanja = 1200))
    check(!P.dovoljenZunanjiZagon(kretnja = false, msOdDejanja = P.ZAGON_PO_DEJANJU_MS + 1))
    check(!P.dovoljenZunanjiZagon(kretnja = false, msOdDejanja = Long.MAX_VALUE)) { "oglas ob nalaganju" }
    check(!P.dovoljenZunanjiZagon(kretnja = false, msOdDejanja = -5)) { "ura nazaj ne steje" }
    val d = P.PorabljivoDejanje()
    check(d.msOd(1000) == Long.MAX_VALUE) { "brez dejanja" }
    d.oznaci(1000); check(d.msOd(1500) == 500L)
    d.porabi(); check(d.msOd(1600) == Long.MAX_VALUE) { "porabljeno dejanje ne velja vec" }
    d.oznaci(2000); check(d.msOd(2100) == 100L) { "novo dejanje spet velja" }
    println("SpletVarnostPravilaTest: OK")
}
