package com.safeer.mobile.browser

/**
 * Cista varnostna pravila brskalnika - brez Androida, da jih preverimo v JVM (tests/SpletVarnostPravilaTest.kt).
 * Ista pravila kot si.safeer.tv.SpletVarnostPravila (Android-tv, zunanji pregled 8. 10. in 3. 11. 2026).
 *
 * Tedenski pregled 4. 11. 2026: telefon je naslov intent: odprl s komponento strani odstranjeno, a z dodatki, ClipData,
 * zastavicami strani, tudi brez kretnje (oglas ob nalaganju), rezervni naslov browser_fallback_url pa nalozil brez
 * preverbe sheme - javascript: v rezervi je tekel v izvoru trenutne strani (UXSS).
 */
object SpletVarnostPravila {
    /** Zunanjo aplikacijo sme stran odpreti samo tako dolgo po uporabnikovem dotiku (ali s kretnjo). */
    const val ZAGON_PO_DEJANJU_MS = 3000L

    private const val VIEW = "android.intent.action.VIEW"

    /** Sheme, ki jih stran nikoli ne preda drugi aplikaciji: krajevne vsebine, skripte in notranje sheme. */
    private val PREPOVEDANE_SHEME = setOf("content", "file", "javascript", "data", "blob", "about", "intent", "android-app", "chrome")

    /**
     * Ali sme stran odpreti drugo aplikacijo s to namero. Klicatelj je namero ze ocistil: komponenta, izbirnik, ClipData
     * in dodatki so odstranjeni, zastavice so samo FLAG_ACTIVITY_NEW_TASK, kategorija BROWSABLE dodana. Tu odlocimo se:
     * samo VIEW, nikoli nas lasten paket, nikoli krajevne ali skriptne sheme, intent: brez podatkov ni povezava.
     */
    fun dovoljenaNamera(akcija: String?, shemaPodatkov: String?, paket: String?, nasPaket: String): Boolean {
        if ((akcija ?: VIEW) != VIEW) return false
        if (paket != null && paket == nasPaket) return false
        val shema = shemaPodatkov?.lowercase()?.takeIf { it.isNotBlank() } ?: return false
        return shema !in PREPOVEDANE_SHEME
    }

    /** browser_fallback_url iz namere: samo spletni naslov http(s) z gostiteljem (nikoli javascript:, file:, content:). */
    fun varenRezervniNaslov(url: String?): Boolean {
        if (url.isNullOrEmpty() || url != url.trim()) return false
        return try {
            val u = java.net.URI(url)
            (u.scheme.equals("https", ignoreCase = true) || u.scheme.equals("http", ignoreCase = true)) && !u.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    /** Oglas ob nalaganju strani ne sme sam odpreti druge aplikacije: potrebna je kretnja ali nedaven dotik. */
    fun dovoljenZunanjiZagon(kretnja: Boolean, msOdDejanja: Long): Boolean =
        kretnja || msOdDejanja in 0..ZAGON_PO_DEJANJU_MS

    /** Cas zadnjega dotika, ki ga lahko porabi en zunanji zagon: po uspesnem zagonu ne velja vec, dokler ni novega. */
    class PorabljivoDejanje {
        @Volatile private var cas = 0L
        @Volatile private var porabljeno = 0L
        fun oznaci(zdaj: Long) { cas = zdaj }
        fun porabi() { porabljeno = cas }
        fun msOd(zdaj: Long): Long = if (cas == 0L || cas <= porabljeno) Long.MAX_VALUE else zdaj - cas
    }
}
