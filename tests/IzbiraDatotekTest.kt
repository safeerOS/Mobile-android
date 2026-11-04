package com.safeer.mobile.browser

/** Izbira datotek za <input type=file>: accept -> MIME in samo URI-ji izbirnika (lastnik 4. 11. 2026). */

private var padli = 0
private var presteti = 0

private fun preveri(dobljeno: Any?, pricakovano: Any?, opis: String) {
    presteti++
    if (dobljeno != pricakovano) {
        padli++
        println("FAIL $opis: $dobljeno (pricakovano $pricakovano)")
    }
}

private val koncnice = mapOf("pdf" to "application/pdf", "jpg" to "image/jpeg", "png" to "image/png", "docx" to
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
private fun tipi(vararg a: String) = IzbiraDatotek.mimeTipi(arrayOf(*a)) { koncnice[it] }

fun main() {
    // accept -> MIME
    preveri(IzbiraDatotek.mimeTipi(null) { null }, listOf("*/*"), "brez accept")
    preveri(tipi(), listOf("*/*"), "prazen seznam")
    preveri(tipi(""), listOf("*/*"), "prazen niz (WebView ga da brez accept)")
    preveri(tipi("image/*"), listOf("image/*"), "image/*")
    preveri(tipi(".pdf"), listOf("application/pdf"), ".pdf")
    preveri(tipi(".PDF"), listOf("application/pdf"), "velike crke")
    preveri(tipi("image/png,.jpg"), listOf("image/png", "image/jpeg"), "vec v enem nizu")
    preveri(tipi("image/png", " .jpg ", ".png"), listOf("image/png", "image/jpeg"), "brez podvajanja")
    preveri(tipi(".docx"), listOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"), ".docx")
    preveri(tipi(".heic"), listOf("*/*"), "neznana koncnica: vse, da uporabnik vidi datoteko")
    preveri(tipi("image/*", ".heic"), listOf("*/*"), "delno neznano: vse")
    preveri(tipi("*/*"), listOf("*/*"), "*/*")
    preveri(tipi("javascript:alert(1)"), listOf("*/*"), "neveljaven vnos strani")
    preveri(tipi("."), listOf("*/*"), "sama pika")

    // Samo URI-ji izbirnika
    val p = "com.safeer.mobile.browser"
    val doc = "content://com.android.providers.media.documents/document/image%3A12"
    val doc2 = "content://com.android.externalstorage.documents/document/primary%3Aa.pdf"
    preveri(IzbiraDatotek.sprejmi(listOf(doc), p, false), listOf(doc), "content iz izbirnika")
    preveri(IzbiraDatotek.sprejmi(listOf("file:///data/data/$p/shared_prefs/x.xml"), p, false), emptyList<String>(), "file:// nikoli")
    preveri(IzbiraDatotek.sprejmi(listOf("FILE:///sdcard/a.pdf"), p, true), emptyList<String>(), "FILE:// nikoli")
    preveri(IzbiraDatotek.sprejmi(listOf("content://$p.fileprovider/x"), p, false), emptyList<String>(), "lastni ponudnik nikoli")
    preveri(IzbiraDatotek.sprejmi(listOf("content://${p.uppercase()}.FILEPROVIDER/x"), p, false), emptyList<String>(), "lastni ponudnik z velikimi crkami")
    preveri(IzbiraDatotek.sprejmi(listOf("content://evil@$p.fileprovider/x"), p, false), emptyList<String>(), "lastni ponudnik za @")
    preveri(IzbiraDatotek.sprejmi(listOf("https://example.org/a.pdf", "javascript:x", "", null), p, true), emptyList<String>(), "ne-content")
    preveri(IzbiraDatotek.sprejmi(listOf("content:///x"), p, false), emptyList<String>(), "content brez ponudnika")
    preveri(IzbiraDatotek.sprejmi(listOf(doc, doc2, doc), p, true), listOf(doc, doc2), "vec datotek brez podvajanja")
    preveri(IzbiraDatotek.sprejmi(listOf(doc, doc2), p, false), listOf(doc), "en sam, ce stran ne dovoli vec")
    preveri(IzbiraDatotek.sprejmi(emptyList(), p, true), emptyList<String>(), "preklic")

    if (padli > 0) {
        println("IzbiraDatotekTest: $padli od $presteti padlo")
        kotlin.system.exitProcess(1)
    }
    println("IzbiraDatotekTest: vseh $presteti uspesnih")
}
