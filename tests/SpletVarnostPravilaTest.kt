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
    // N17: SafeerBridge.navigate samo z domace strani brskalnika (kot TV).
    check(P.jeDomacaBrskalnika("file:///android_asset/brave_home.html"))
    check(P.jeDomacaBrskalnika("file:///android_asset/brave_home.html?x=1#y"))
    for (u in listOf(null, "", "about:blank", "safeer://security-interstitial", "file:///android_asset/link/index.html",
                     "file:///android_asset/brave_home.html.evil", "https://napadalec.test/file:///android_asset/brave_home.html",
                     "file:///android_asset/../android_asset/brave_home.html", "data:text/html,brave_home")) {
        check(!P.jeDomacaBrskalnika(u)) { "domaca $u" }
    }
    // Vhodna vrata (UXSS, 4. 11. 2026 kot TV): namera VIEW, Safeer Link, deljeno besedilo.
    val N = P.Vhod.NAMERA; val L = P.Vhod.LINK; val W = P.Vhod.SPLET
    for (v in P.Vhod.values()) {
        check(P.vhodniNaslov("https://primer.si/pot?x=1#y", v)) { "https $v" }
        check(P.vhodniNaslov("http://192.168.1.5:8080/cast/screen/1", v)) { "http $v" }
        for (u in listOf(null, "", "javascript:alert(1)", "JavaScript:alert(document.cookie)", "java\tscript:alert(1)",
                         " javascript:alert(1)", "javascript://%0aalert(1)", "vbscript:msgbox", "data:text/html,<script>x</script>",
                         "blob:https://primer.si/1", "intent://x#Intent;end", "about:blank", "chrome://settings",
                         "https:///brez-gostitelja", "https:primer.si", "https://:80/x", "https://primer.si/\nx",
                         "https://" + "a".repeat(P.NAJDALJSI_VHODNI))) {
            check(!P.vhodniNaslov(u, v)) { "vhod $v: $u" }
        }
    }
    // Krajevni PDF samo iz namere; nas ponudnik samo s podeljenim branjem.
    check(P.vhodniNaslov("content://com.android.providers.downloads.documents/document/12", N))
    check(P.vhodniNaslov("file:///storage/emulated/0/Download/racun.pdf", N))
    check(P.vhodniNaslov("file:///sdcard/Download/A.PDF?x#y", N))
    for (u in listOf("content://com.safeer.mobile.browser.fileprovider/cache/x.pdf", "content://COM.Safeer.mobile.browser.fileprovider/x",
                     "content://0@com.safeer.mobile.browser.fileprovider/x", "content://com.safeer.mobile.browser.fileprovider:1/x",
                     "content://com%2Esafeer.mobile.browser.fileprovider/x", "content://com.safeer", "content://si.safeer.trgovina.x/1",
                     "content://", "file:///android_asset/brave_home.html", "file:///android_asset/x.pdf",
                     "file:///data/data/com.safeer.mobile.browser/files/x.pdf", "file:///data/user/0/com.safeer.mobile.browser/x.pdf",
                     "file:////data/data/com.safeer.mobile.browser/x.pdf", "file:///sdcard/../data/data/com.safeer.mobile.browser/x.pdf",
                     "file:///sdcard/./x/../../data/x.pdf", "file:///sdcard/%2e%2e/data/x.pdf", "file:///sdcard/a%2fb.pdf",
                     "file:///proc/self/root/x.pdf", "file://localhost/data/x.pdf", "file:///sdcard/beleznica.txt", "file:///sdcard/x.pdf/..")) {
        check(!P.vhodniNaslov(u, N)) { "namera: $u" }
    }
    check(P.vhodniNaslov("content://com.safeer.mobile.browser.fileprovider/cache/x.pdf", N, dovoljenjeBranja = true)) { "podeljeno branje" }
    check(P.vhodniNaslov("content://com.safeerx.ponudnik/x", N)) { "samo com.safeer.*, ne podobno ime" }
    for (v in listOf(L, W)) {
        check(!P.vhodniNaslov("content://com.android.providers.downloads.documents/document/12", v, dovoljenjeBranja = true)) { "$v nikoli content:" }
        check(!P.vhodniNaslov("file:///sdcard/Download/racun.pdf", v)) { "$v nikoli file:" }
    }
    // Domaca stran samo iz Linka (Daljinec »home«).
    check(P.vhodniNaslov("file:///android_asset/brave_home.html", L))
    check(!P.vhodniNaslov("file:///android_asset/brave_home.html", N)) { "namera ne odpre domace strani" }
    check(!P.vhodniNaslov("file:///android_asset/brave_home.html", W))
    check(!P.vhodniNaslov("file:///android_asset/link/index.html", L))
    // Zadnja vrata v loadUrl.
    for (u in listOf("javascript:alert(1)", "JAVASCRIPT:x", "  javascript:x", "\u0001javascript:x", "java\nscript:x", "jav\ta\rscript:x",
                     "vbscript:x", "data:text/html,x", "DATA:image/png;base64,AA")) {
        check(P.skriptniNaslov(u)) { "skriptni $u" }
    }
    for (u in listOf(null, "", "https://primer.si/javascript:x", "file:///android_asset/brave_home.html", "about:blank",
                     "content://x/y", "https://data.si/")) {
        check(!P.skriptniNaslov(u)) { "ni skriptni $u" }
    }
    println("SpletVarnostPravilaTest: OK")
}
