package com.safeer.mobile.browser

import java.io.File

/**
 * Tedenski pregled 25. 11. 2026 (N4): lasten ponudnik datotek.
 *  - jeLastna: naslov nase avtoritete (tudi z `0@`, velikimi crkami) zavrnjen, tuji sprejet;
 *  - koren: deljene so samo mape iz seznama prenosov, ne cel filesDir, cacheDir ali zunanje datoteke;
 *  - vir: namera VIEW in ponudnik res uporabljata ti pravili.
 * Uporaba: DeljeneDatotekeTestKt <koren repozitorija>
 */
private var napak = 0

private fun preveri(opis: String, pogoj: Boolean) {
    if (pogoj) println("OK   $opis") else { napak++; println("NAPAKA $opis") }
}

fun main(args: Array<String>) {
    val repo = File(args.firstOrNull() ?: error("manjka koren repozitorija"))
    val a = DeljeneDatoteke.AUTHORITY

    // Avtoriteta, kot jo vrne android.net.Uri.getAuthority() (ze dekodirana).
    preveri("lastna avtoriteta zavrnjena", DeljeneDatoteke.jeLastna("content", a))
    preveri("lastna z uporabnikom 0@ zavrnjena", DeljeneDatoteke.jeLastna("content", "0@$a"))
    preveri("lastna z velikimi crkami zavrnjena", DeljeneDatoteke.jeLastna("CONTENT", a.uppercase()))
    preveri("tuja avtoriteta (prenosi) sprejeta", !DeljeneDatoteke.jeLastna("content", "com.android.providers.downloads.documents"))
    preveri("tuja z nasim imenom kot predpono sprejeta", !DeljeneDatoteke.jeLastna("content", "$a.evil"))
    preveri("https z istim imenom gostitelja ni ponudnik", !DeljeneDatoteke.jeLastna("https", a))
    preveri("brez avtoritete sprejeta", !DeljeneDatoteke.jeLastna("content", null))

    // Manifest mora imeti isto avtoriteto, sicer pravilo ne zavrne nicesar.
    val manifest = File(repo, "AndroidManifest.xml").readText()
    preveri("manifest ima avtoriteto $a", manifest.contains("android:authorities=\"$a\""))

    preveri("javna mapa deljena", DeljeneDatoteke.koren("/javno/Download/a.pdf") == (DeljeneDatoteke.Koren.JAVNO to "Download/a.pdf"))
    preveri("zunanji prenosi deljeni", DeljeneDatoteke.koren("/external_files/Download/a.pdf") == (DeljeneDatoteke.Koren.ZUNANJA to "a.pdf"))
    preveri("notranji prenosi deljeni", DeljeneDatoteke.koren("/prenosi/a.pdf") == (DeljeneDatoteke.Koren.NOTRANJA to "a.pdf"))
    preveri("koren filesDir ni deljen", DeljeneDatoteke.koren("/threat-intel/threats.json") == null)
    preveri("cache_files ni deljen", DeljeneDatoteke.koren("/cache_files/safeer-link/x") == null)
    preveri("zunanje datoteke izven Download niso deljene", DeljeneDatoteke.koren("/external_files/drugo/x") == null)
    preveri("mapa z enakim zacetkom ni deljena", DeljeneDatoteke.koren("/prenosi-skrito/x") == null)
    preveri("pot brez mape ni deljena", DeljeneDatoteke.koren("/a.pdf") == null)

    val brskalnik = File(repo, "src/main/kotlin/com/safeer/mobile/browser")
    val main = File(brskalnik, "MainActivity.kt").readText()
    val view = main.substring(main.indexOf("Intent.ACTION_VIEW ->"), main.indexOf("Intent.ACTION_WEB_SEARCH ->"))
    preveri("namera VIEW preveri lastno avtoriteto", view.contains("DeljeneDatoteke.jeLastna(intent.data?.scheme, intent.data?.authority)"))
    val ponudnik = File(brskalnik, "MobileFileProvider.kt").readText()
    preveri("ponudnik bere samo deljene mape", ponudnik.contains("DeljeneDatoteke.koren(path)") && !ponudnik.contains("ctx.cacheDir"))

    if (napak > 0) { println("DeljeneDatotekeTest: $napak napak"); kotlin.system.exitProcess(1) }
    println("DeljeneDatotekeTest: OK")
}
