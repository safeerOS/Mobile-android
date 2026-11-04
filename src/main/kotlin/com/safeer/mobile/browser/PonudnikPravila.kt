package com.safeer.mobile.browser

import java.io.File

/**
 * Cista pravila ponudnika datotek (MobileFileProvider) - brez Androida, da jih preverimo v JVM (tests/PonudnikPravilaTest.kt).
 *
 * Tedenski pregled 4. 11. 2026 (kot TV, zunanji pregled 3. 11.): ponudnik je stregel vsako pot v notranji mapi aplikacije
 * (filesDir) in zunanji mapi aplikacije, dovolil pisanje (w, rw) in ustvaril manjkajoce mape. Zdaj samo branje in samo
 * obstojece datoteke v podrocjih, iz katerih telefon res deli prenose (PrenosiUi.uriZaDatoteko):
 * javne mape (Prenosi, Dokumenti ...), zunanja Download aplikacije, notranja »prenosi« (HubKrmilnik.mapaZaPrejete)
 * in predpomnilnik (cache_files, kot TV).
 */
object PonudnikPravila {
    enum class Podrocje { JAVNO, ZUNANJI_PRENOSI, NOTRANJI_PRENOSI, PREDPOMNILNIK }

    /** Podmapa zunanje mape aplikacije (getExternalFilesDir(DIRECTORY_DOWNLOADS)) in notranje mape za prenose. */
    const val ZUNANJA_PODMAPA = "Download"
    const val NOTRANJA_PODMAPA = "prenosi"

    /** Pot URI-ja -> podrocje in pot znotraj njega; null za vse drugo. */
    fun razcleni(pot: String?): Pair<Podrocje, String>? {
        val p = pot ?: return null
        return when {
            p.startsWith("/javno/") -> Podrocje.JAVNO to p.removePrefix("/javno/")
            p.startsWith("/external_files/$ZUNANJA_PODMAPA/") -> Podrocje.ZUNANJI_PRENOSI to p.removePrefix("/external_files/$ZUNANJA_PODMAPA/")
            p.startsWith("/$NOTRANJA_PODMAPA/") -> Podrocje.NOTRANJI_PRENOSI to p.removePrefix("/$NOTRANJA_PODMAPA/")
            p.startsWith("/cache_files/") -> Podrocje.PREDPOMNILNIK to p.removePrefix("/cache_files/")
            else -> null
        }
    }

    /** Datoteka znotraj [koren] po kanonicni poti (nikoli koren sam, nikoli zunaj njega) ali null. */
    fun datoteka(koren: File?, rel: String?): File? {
        if (koren == null || rel.isNullOrEmpty() || '\u0000' in rel) return null
        return try {
            val k = koren.canonicalFile
            val f = File(k, rel.replace('\\', '/')).canonicalFile
            if (f.path.startsWith(k.path + File.separator)) f else null
        } catch (_: Exception) { null }
    }

    /** Samo branje: »r« (prazen ali null = r). Pisanje in ustvarjanje nikoli. */
    fun samoBranje(nacin: String?): Boolean = nacin.isNullOrEmpty() || nacin == "r"
}
