package com.safeer.mobile.browser

import java.io.File

/**
 * Tedenski pregled 25. 11. 2026 (N4): lasten ponudnik datotek.
 *  - jeLastna: naslov nase avtoritete (tudi z `0@`, velikimi crkami) zavrnjen, tuji sprejet;
 *  - koren: deljene so samo mape iz seznama prenosov, ne cel filesDir, cacheDir ali zunanje datoteke;
 *  - smeOdZunaj (MOB-FILE): namera VIEW od zunaj ne odpre file:// (zasebne mape, tudi z .. in /data/user/0)
 *    ne lastnega ponudnika; sprejeti so http(s), tuj content:// in natanko domaca stran;
 *  - vir: namera VIEW in ponudnik res uporabljata ti pravili, filter PDF nima sheme file.
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

    // MOB-FILE: namera VIEW od zunaj (tudi eksplicitna, mimo filtra) ne odpre datoteke z naprave prek file://.
    fun smeUrl(url: String): Boolean {
        val shema = url.substringBefore(':', "").ifEmpty { null }
        val avtoriteta = url.substringAfter("://", "").substringBefore('/').ifEmpty { null }
        return DeljeneDatoteke.smeOdZunaj(shema, avtoriteta, url)
    }
    val paket = "com.safeer.mobile.browser"
    for (url in listOf(
        "file:///data/data/$paket/files/x.pdf",
        "file:///data/user/0/$paket/files/threat-intel/x.pdf",
        "file:///data/user/0/$paket/cache/x.pdf",
        "file:///data/user/0/$paket/no_backup/x.pdf",
        "file:///data/user_de/0/$paket/files/x.pdf",
        "file:///storage/emulated/0/Download/../../../../data/data/$paket/files/x.pdf",
        "file:///android_asset/../../data/data/$paket/files/x.pdf",
        "file:///android_asset/brave_home.html/../../../data/data/$paket/files/x.pdf",
        "FILE:///data/data/$paket/files/x.pdf",
        "file://localhost/data/data/$paket/files/x.pdf",
        "file:///storage/emulated/0/Download/x.pdf",
        "content://$a/prenosi/x.pdf",
        "content://0@$a/prenosi/x.pdf",
        "javascript:alert(1)",
        "data:text/html,x",
        "/data/data/$paket/files/x.pdf",
    )) preveri("od zunaj zavrnjeno: $url", !smeUrl(url))
    for (url in listOf(
        "https://example.org/a.pdf",
        "http://192.168.1.2/",
        "content://com.android.providers.downloads.documents/document/12",
        "file:///android_asset/brave_home.html",
    )) preveri("od zunaj sprejeto: $url", smeUrl(url))
    preveri("brez naslova zavrnjeno", !DeljeneDatoteke.smeOdZunaj(null, null, null))

    val brskalnik = File(repo, "src/main/kotlin/com/safeer/mobile/browser")
    preveri("domaca stran Linka je ista kot dovoljena",
        File(brskalnik, "link/LinkSprejemnik.kt").readText().contains("DOMACA_STRAN = com.safeer.mobile.browser.DeljeneDatoteke.DOMACA_STRAN"))
    val main = File(brskalnik, "MainActivity.kt").readText()
    val view = main.substring(main.indexOf("Intent.ACTION_VIEW ->"), main.indexOf("Intent.ACTION_WEB_SEARCH ->"))
    preveri("namera VIEW preveri naslov od zunaj",
        view.contains("DeljeneDatoteke.smeOdZunaj(intent.data?.scheme, intent.data?.authority, intent.dataString)"))
    val filter = Regex("<intent-filter>(?:(?!</intent-filter>).)*application/pdf(?:(?!</intent-filter>).)*</intent-filter>", RegexOption.DOT_MATCHES_ALL)
        .find(manifest)?.value.orEmpty()
    preveri("filter PDF ima content in nima file", filter.contains("android:scheme=\"content\"") && !filter.contains("android:scheme=\"file\""))
    val ponudnik = File(brskalnik, "MobileFileProvider.kt").readText()
    preveri("ponudnik bere samo deljene mape", ponudnik.contains("DeljeneDatoteke.koren(path)") && !ponudnik.contains("ctx.cacheDir"))

    if (napak > 0) { println("DeljeneDatotekeTest: $napak napak"); kotlin.system.exitProcess(1) }
    println("DeljeneDatotekeTest: OK")
}
