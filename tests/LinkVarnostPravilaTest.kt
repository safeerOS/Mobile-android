package com.safeer.mobile.browser.link

/**
 * Tedenski pregled 25. 11. 2026 (S3): sprejem zvoka kot na TV (prenesen del si.safeer.tv tests/LinkVarnostPravilaTest.kt,
 * 2.4). Argument: pot do src/main/kotlin (preverba, da ZvokSprejemnik pravila res uporablja).
 */
private var preverb = 0

private fun preveri(opis: String, pogoj: Boolean) {
    check(pogoj) { "NAPAKA: $opis" }
    preverb++
}

private fun vir(izvorna: String, pot: String): String = java.io.File(izvorna, pot).readText()

/** 2.4: naslovi za sprejem zvoka so omejeni, brez te naprave same; glava ima skupen rok. */
private fun sprejemZvoka(izvorna: String) {
    val P = LinkVarnostPravila
    preveri("naslov iz host je prvi, brez ponovitev", P.nasloviZvoka("192.168.1.5", listOf("10.0.0.5", "192.168.1.5")) == listOf("192.168.1.5", "10.0.0.5"))
    val veliko = (1..200).map { "10.0.0.$it" }
    preveri("seznam je omejen", P.nasloviZvoka("", veliko).size == P.NAJVEC_NASLOVOV_ZVOKA && P.NAJVEC_NASLOVOV_ZVOKA <= 8)
    for (n in listOf("127.0.0.1", "localhost", "::1", "[::1]", "::ffff:127.0.0.1", "169.254.1.1", "0.0.0.0", "fe80::1"))
        preveri("zvok ne gre na $n", P.nasloviZvoka(n, emptyList()).isEmpty())
    preveri("neveljavni zapisi odpadejo", P.nasloviZvoka("a b", listOf("x/y", "ime@gost", "", "  ", "a".repeat(300))).isEmpty())
    preveri("ime in IPv6 v domacem omrezju ostaneta", P.nasloviZvoka("racunalnik.lan", listOf("fd00::5")) == listOf("racunalnik.lan", "fd00::5"))
    preveri("prepovedani ne zasedejo mest veljavnim", P.nasloviZvoka("127.0.0.1", listOf("localhost", "192.168.1.9")) == listOf("192.168.1.9"))
    val moj = listOf(byteArrayOf(192.toByte(), 168.toByte(), 1, 77), java.net.InetAddress.getByName("fd00::77").address)
    preveri("lastni naslov IPv4 odpade, drugi ostane", P.nasloviZvoka("192.168.1.77", listOf("192.168.1.5"), moj) == listOf("192.168.1.5"))
    preveri("lastni naslov kot preslikani IPv4 v IPv6 odpade", P.nasloviZvoka("::ffff:192.168.1.77", listOf("192.168.1.5"), moj) == listOf("192.168.1.5"))
    preveri("lastni naslov IPv6 (drug zapis) odpade", P.nasloviZvoka("[fd00:0:0:0:0:0:0:77]", listOf("fd00::5"), moj) == listOf("fd00::5"))
    preveri("lastni naslov ne zaseda mesta", P.nasloviZvoka("192.168.1.77", (1..10).map { "10.0.0.$it" }, moj).size == P.NAJVEC_NASLOVOV_ZVOKA)
    preveri("brez lastnih naslovov deluje kot prej", P.nasloviZvoka("192.168.1.77", emptyList()) == listOf("192.168.1.77"))
    preveri("ime se ne izloci (brez DNS)", P.nasloviZvoka("racunalnik.lan", emptyList(), moj) == listOf("racunalnik.lan"))
    // Neodvisni pregled 25. 11. 2026: stevilcni zapisi, ki niso strogi IPv4, sistem razresi v 127.0.0.1.
    for (n in listOf("127.1", "2130706433", "0177.0.0.1", "0x7f.1", "0x7f000001", "127.0.0.01", "010.0.0.5"))
        preveri("zvok ne gre na stevilcni zapis $n", P.nasloviZvoka(n, emptyList()).isEmpty())
    preveri("strogi IPv4 in ime ostaneta", P.nasloviZvoka("10.0.0.5", listOf("racunalnik.lan", "pc1.lan")) == listOf("10.0.0.5", "racunalnik.lan", "pc1.lan"))
    preveri("rok se ni mimo", !P.rokPotekel(1000L, 1000L + P.ROK_GLAVE_ZVOKA_MS - 1, P.ROK_GLAVE_ZVOKA_MS))
    preveri("rok je mimo", P.rokPotekel(1000L, 1000L + P.ROK_GLAVE_ZVOKA_MS, P.ROK_GLAVE_ZVOKA_MS))
    preveri("ura nazaj steje kot mimo", P.rokPotekel(1000L, 999L, P.ROK_GLAVE_ZVOKA_MS))
    check(izvorna.isNotEmpty()) { "NAPAKA: manjka pot do src/main/kotlin" }
    val zvok = vir(izvorna, "com/safeer/mobile/browser/link/ZvokSprejemnik.kt")
    preveri("ZvokSprejemnik naslove omeji s pravilom", zvok.contains("val naslovi = LinkVarnostPravila.nasloviZvoka(p.optString(\"host\", \"\"), surovi, lastniNaslovi())"))
    preveri("ZvokSprejemnik glavo bere s skupnim rokom", zvok.contains("LinkVarnostPravila.rokPotekel(zacetek,") && zvok.contains("if (potekel()) throw java.net.SocketTimeoutException"))
    preveri("ZvokSprejemnik naslovov ne pise v dnevnik", !zvok.contains("naslovi.joinToString()") && !zvok.contains("\"\$n:\$vrata ni dosegljiv"))
}

fun main(args: Array<String>) {
    sprejemZvoka(args.firstOrNull().orEmpty())
    println("LinkVarnostPravilaTest: OK ($preverb preverb)")
}
