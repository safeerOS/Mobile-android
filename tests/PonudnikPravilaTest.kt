package com.safeer.mobile.browser

import com.safeer.mobile.browser.PonudnikPravila as P
import com.safeer.mobile.browser.PonudnikPravila.Podrocje

/** MobileFileProvider (tedenski pregled 4. 11. 2026): samo branje, samo podrocja prenosov, nikoli zunaj korena. */
fun main() {
    check(P.razcleni("/javno/Download/a.pdf") == (Podrocje.JAVNO to "Download/a.pdf"))
    check(P.razcleni("/external_files/Download/x/a.apk") == (Podrocje.ZUNANJI_PRENOSI to "x/a.apk"))
    check(P.razcleni("/prenosi/a.jpg") == (Podrocje.NOTRANJI_PRENOSI to "a.jpg"))
    check(P.razcleni("/cache_files/posodobitve/a.apk") == (Podrocje.PREDPOMNILNIK to "posodobitve/a.apk"))
    for (p in listOf(null, "", "/", "/a.txt", "/sessions/tabs.json", "/external_files/a.txt", "/external_files/Downloadx/a",
                     "/shared_prefs/x.xml", "/../shared_prefs/x.xml", "/external_app_files/a")) {
        check(P.razcleni(p) == null) { "zunaj podrocij: $p" }
    }
    val koren = java.nio.file.Files.createTempDirectory("ponudnik").toFile()
    val zunaj = java.io.File(koren.parentFile, koren.name + "-sosed").apply { mkdirs() }
    try {
        java.io.File(koren, "x").mkdirs(); java.io.File(koren, "x/a.apk").writeText("a")
        java.io.File(zunaj, "s.xml").writeText("s")
        check(P.datoteka(koren, "x/a.apk")?.name == "a.apk")
        check(P.datoteka(koren, "x/../x/a.apk")?.name == "a.apk") { "znotraj korena po kanonicni poti" }
        for (r in listOf("../" + zunaj.name + "/s.xml", "x/../../" + zunaj.name + "/s.xml", "..", "", "x\u0000.apk", "..\\" + zunaj.name + "\\s.xml")) {
            check(P.datoteka(koren, r).let { it == null || it.path.startsWith(koren.canonicalPath + java.io.File.separator) }) { "zunaj korena: $r" }
        }
        check(P.datoteka(koren, "../" + zunaj.name + "/s.xml") == null)
        check(P.datoteka(null, "a") == null)
    } finally { koren.deleteRecursively(); zunaj.deleteRecursively() }
    check(P.samoBranje("r") && P.samoBranje(null) && P.samoBranje(""))
    for (n in listOf("w", "wa", "rw", "rwt", "wt")) check(!P.samoBranje(n)) { "nacin $n" }
    println("PonudnikPravilaTest: OK")
}
