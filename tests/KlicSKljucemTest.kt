package com.safeer.mobile.browser.link

import java.io.File

/**
 * Tedenski pregled 25. 11. 2026 (N3): klic, ki nese zeton Huba (x-safeer-token) prek HttpURLConnection,
 * ne sme slediti preusmeritvam - sicer HttpURLConnection na drug naslov ponovi glavo z zetonom.
 * HubTls je samo za Android (Context, Base64), zato preverjamo vir:
 *  - HubTls.zavaruj izklopi preusmeritve,
 *  - v vsaki datoteki z zetonom ima vsak openConnection svoj HubTls.zavaruj,
 *  - v QR prijavi je zavaruj med openConnection in glavo z zetonom, tovarna vticnic pa pripne odtis iz kode.
 * Uporaba: KlicSKljucemTestKt <src/main/kotlin>
 */
private var napak = 0

private fun preveri(opis: String, pogoj: Boolean) {
    if (pogoj) println("OK   $opis") else { napak++; println("NAPAKA $opis") }
}

/** Telo funkcije od `fun ime(` do zaklepaja na istem zamiku (vir je oblikovan z zamiki po 4). */
private fun telo(vir: String, ime: String): String {
    val i = vir.indexOf("fun $ime(")
    require(i >= 0) { "funkcije $ime ni" }
    val zacetek = vir.lastIndexOf('\n', i) + 1
    val zamik = vir.substring(zacetek, i).takeWhile { it == ' ' }
    val konec = vir.indexOf("\n$zamik}", i)
    require(konec > i) { "konca funkcije $ime ni" }
    return vir.substring(i, konec)
}

fun main(args: Array<String>) {
    val koren = File(args.firstOrNull() ?: error("manjka pot do src/main/kotlin"))
    val brskalnik = File(koren, "com/safeer/mobile/browser")

    val hubTls = File(brskalnik, "cast/HubTls.kt").readText()
    preveri("HubTls.zavaruj izklopi preusmeritve", telo(hubTls, "zavaruj").contains("instanceFollowRedirects = false"))

    val zZetonom = koren.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .filter { it.readText().contains("x-safeer-token", ignoreCase = true) && it.readText().contains("openConnection(") }
        .toList()
    preveri("najdene datoteke z zetonom in openConnection (${zZetonom.size})", zZetonom.size >= 4)
    for (f in zZetonom) {
        val v = f.readText()
        val povezav = Regex("""openConnection\(""").findAll(v).count()
        val zavarovanih = Regex("""HubTls\.zavaruj\(""").findAll(v).count()
        preveri("${f.name}: vsak openConnection ($povezav) ima HubTls.zavaruj ($zavarovanih)", zavarovanih >= povezav)
    }

    val klic = telo(File(brskalnik, "link/QrPrijavaActivity.kt").readText(), "klic")
    val odpri = klic.indexOf("openConnection(")
    val zavaruj = klic.indexOf("HubTls.zavaruj(povezava")
    val zeton = klic.indexOf("x-safeer-token", ignoreCase = true)
    preveri("QR klic: zavaruj po openConnection in pred glavo z zetonom", odpri in 0 until zavaruj && zavaruj < zeton)
    preveri("QR klic: tovarna vticnic pripne odtis iz kode", klic.contains("HubTls.odjemalec(koda.odtis"))
    preveri("QR klic: ne vklopi preusmeritev", !klic.contains("instanceFollowRedirects = true"))

    if (napak > 0) { println("KlicSKljucemTest: $napak napak"); kotlin.system.exitProcess(1) }
    println("KlicSKljucemTest: OK")
}
