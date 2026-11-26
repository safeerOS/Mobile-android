package com.safeer.mobile.browser.cast

// Pripeti odtis Huba velja samo za naslov Huba (pregled 4. 11. 2026: privzeta vrata in velike crke).
private var napak = 0

private fun preveri(kaj: String, pogoj: Boolean) {
    if (pogoj) println("  OK   $kaj") else { println("FAIL   $kaj"); napak++ }
}

fun main() {
    val n = HubNaslov
    println("== naslov Huba za pripeti odtis ==")
    preveri("https://hub/ in https://hub:443/ sta isti Hub", n.jeNaslovHuba("https://hub:443/zaslon", "https://hub"))
    preveri("obratno: Hub z :443, stran brez vrat", n.jeNaslovHuba("https://hub/zaslon", "https://hub:443"))
    preveri("gostitelj in shema brez velikih crk", n.jeNaslovHuba("HTTPS://Hub.Lan/x", "https://hub.lan"))
    preveri("IP in vrata Huba", n.jeNaslovHuba("https://192.168.1.20:8443/cast", "https://192.168.1.20:8443"))
    preveri("NE: drug gostitelj", !n.jeNaslovHuba("https://drug/", "https://hub"))
    preveri("NE: gostitelj, ki se s Hubom samo zacne", !n.jeNaslovHuba("https://hub.napadalec.si/", "https://hub"))
    preveri("NE: druga vrata", !n.jeNaslovHuba("https://hub:8443/", "https://hub"))
    preveri("NE: druga vrata (Hub na 8443, stran brez vrat)", !n.jeNaslovHuba("https://192.168.1.20/", "https://192.168.1.20:8443"))
    preveri("NE: http namesto https", !n.jeNaslovHuba("http://hub:443/", "https://hub"))
    preveri("NE: prazen Hub", !n.jeNaslovHuba("https://hub/", ""))
    preveri("NE: neveljaven naslov", !n.jeNaslovHuba("https://hub:abc/", "https://hub"))
    preveri("NE: uporabnik v naslovu ne spremeni gostitelja", !n.jeNaslovHuba("https://hub@drug/", "https://hub"))
    if (napak > 0) { println("$napak napak"); kotlin.system.exitProcess(1) }
    println("vse OK")
}
