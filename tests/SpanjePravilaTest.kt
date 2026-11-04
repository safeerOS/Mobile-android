package com.safeer.mobile.browser

/**
 * Roki spanja zavihkov (lastnik 4. 11. 2026): zavihek v ozadju po 60 s, vse po 30 s, ko aplikacija ni na
 * zaslonu; zvok, video, nedokoncan obrazec, prijava in nalaganje nikoli. S starimi roki (10 min, aktivni
 * nikoli) preizkus pade.
 */

private var padli = 0
private var presteti = 0

private fun preveri(dobljeno: Any?, pricakovano: Any?, opis: String) {
    presteti++
    if (dobljeno != pricakovano) {
        padli++
        println("FAIL $opis: $dobljeno (pricakovano $pricakovano)")
    }
}

private fun brezIzjem() = SpanjePravila.izjema(predvaja = false, celozaslonskiVideo = false, obrazec = false, prijava = false, nalaga = false)

fun main() {
    val s = SpanjePravila
    preveri(s.ZAVIHEK_V_OZADJU_MS, 60_000L, "rok zavihka v ozadju")
    preveri(s.APLIKACIJA_V_OZADJU_MS, 30_000L, "rok aplikacije v ozadju")

    // Aplikacija na zaslonu, zavihek v ozadju
    preveri(s.zaspi(false, 59_999L, null, false, brezIzjem()), false, "vrnitev v prvi minuti ohrani stran")
    preveri(s.zaspi(false, 60_000L, null, false, brezIzjem()), true, "zavihek v ozadju zaspi po 60 s")
    preveri(s.zaspi(false, 5 * 60_000L, null, false, brezIzjem()), true, "po 5 min (prej buden do 10 min)")
    preveri(s.preostanek(false, 15_000L, null), 45_000L, "preostanek zavihka")
    // Aktivni zavihek na zaslonu ne zaspi nikoli, tudi ob pritisku
    preveri(s.zaspi(true, 3_600_000L, null, false, brezIzjem()), false, "aktivni na zaslonu")
    preveri(s.zaspi(true, 3_600_000L, null, true, brezIzjem()), false, "aktivni na zaslonu ob pritisku")
    preveri(s.preostanek(true, 3_600_000L, null), null, "aktivni na zaslonu nima roka")

    // Aplikacija ni na zaslonu: vse zaspi po 30 s
    preveri(s.zaspi(true, 0L, 29_999L, false, brezIzjem()), false, "kratek skok v drugo aplikacijo")
    preveri(s.zaspi(true, 0L, 30_000L, false, brezIzjem()), true, "aktivni zaspi 30 s po odhodu")
    preveri(s.zaspi(false, 10_000L, 30_000L, false, brezIzjem()), true, "zavihek v ozadju zaspi 30 s po odhodu")
    preveri(s.preostanek(false, 50_000L, 5_000L), 10_000L, "velja prej dosezeni rok")
    preveri(s.preostanek(true, 0L, 12_000L), 18_000L, "preostanek aktivnega v ozadju")
    preveri(s.zaspi(true, 0L, 1_000L, true, brezIzjem()), true, "ob pritisku v ozadju takoj")

    // Izjeme: nikoli ne zaspi
    val predvaja = s.izjema(predvaja = true, celozaslonskiVideo = false, obrazec = false, prijava = false, nalaga = false)
    val video = s.izjema(predvaja = false, celozaslonskiVideo = true, obrazec = false, prijava = false, nalaga = false)
    val obrazec = s.izjema(predvaja = false, celozaslonskiVideo = false, obrazec = true, prijava = false, nalaga = false)
    val prijava = s.izjema(predvaja = false, celozaslonskiVideo = false, obrazec = false, prijava = true, nalaga = false)
    val nalaga = s.izjema(predvaja = false, celozaslonskiVideo = false, obrazec = false, prijava = false, nalaga = true)
    for ((ime, iz) in listOf("glasba" to predvaja, "video" to video, "obrazec" to obrazec, "prijava" to prijava, "nalaganje" to nalaga)) {
        preveri(iz, true, "$ime je izjema")
        preveri(s.zaspi(true, 3_600_000L, 3_600_000L, false, iz), false, "$ime: aktivni v ozadju ne zaspi")
        preveri(s.zaspi(false, 3_600_000L, null, false, iz), false, "$ime: zavihek v ozadju ne zaspi")
        preveri(s.zaspi(false, 3_600_000L, 3_600_000L, true, iz), false, "$ime: ne zaspi niti ob pritisku")
    }
    preveri(brezIzjem(), false, "brez izjem")

    if (padli > 0) {
        println("SpanjePravilaTest: $padli od $presteti padlo")
        kotlin.system.exitProcess(1)
    }
    println("SpanjePravilaTest: vseh $presteti uspesnih")
}
