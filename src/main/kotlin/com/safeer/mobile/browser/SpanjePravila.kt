package com.safeer.mobile.browser

/**
 * Kdaj zavihek telefona zaspi (WebView sprosti pomnilnik, saveState ohrani zgodovino Nazaj in polozaj).
 * Brez Androida, preizkus v tests/SpanjePravilaTest.kt.
 *
 * Nacelo lastnika (4. 11. 2026): zivi samo zavihek, ki ga gledas; ostalo zaspi. Prej je zavihek v ozadju
 * ostal buden 10 minut, ko aplikacija ni bila na zaslonu, pa aktivni zavihek ni zaspal nikoli.
 * Vzorec je TV (si.safeer.tv.SpanjePravila); telefon ima daljsi rok za odhod z zaslona (2FA, glej spodaj).
 */
object SpanjePravila {
    /** Zavihek v ozadju: preklop nazaj v prvi minuti stran ohrani brez ponovnega nalaganja. */
    const val ZAVIHEK_V_OZADJU_MS = 60_000L
    /**
     * Aplikacija ni na zaslonu. Lastnik 4. 11. 2026: za 2FA uporabnik pogosto odide v drugo aplikacijo (mobilna
     * banka, SMS, avtentikator) in se vrne po 1-2 min; s 30 s (kot TV) bi stran zaspala, se nalozila znova in
     * prijava bi se izgubila. Zato 3 min; ob pomnilniskem pritisku (onTrimMemory) zaspi takoj.
     */
    const val APLIKACIJA_V_OZADJU_MS = 180_000L
    /** Zavihek, ki ga izjema (zvok, obrazec ...) drzi budnega, pogledamo znova cez toliko. */
    const val PONOVNI_PREGLED_MS = 30_000L

    /** Kar zavihek drzi budnega ne glede na rok: uporabnik bi sicer izgubil zvok, video, vnos ali prijavo. */
    fun izjema(predvaja: Boolean, celozaslonskiVideo: Boolean, obrazec: Boolean, prijava: Boolean, nalaga: Boolean): Boolean =
        predvaja || celozaslonskiVideo || obrazec || prijava || nalaga

    /**
     * Cez koliko ms zavihek doseze rok spanja (0 = zdaj); null = ne zaspi (aktivni zavihek na zaslonu).
     * odUporabeMs: od zadnjega pogleda zavihka; odOdhodaMs: od odhoda aplikacije z zaslona (null, ce je na zaslonu).
     */
    fun preostanek(aktiven: Boolean, odUporabeMs: Long, odOdhodaMs: Long?): Long? {
        val zaradiZavihka = if (aktiven) null else ZAVIHEK_V_OZADJU_MS - odUporabeMs
        val zaradiAplikacije = odOdhodaMs?.let { APLIKACIJA_V_OZADJU_MS - it }
        val rok = listOfNotNull(zaradiZavihka, zaradiAplikacije).minOrNull() ?: return null
        return rok.coerceAtLeast(0L)
    }

    /**
     * Ali zavihek zdaj zaspi. Ob pomnilniskem pritisku zaspi takoj vsak zavihek, ki ga uporabnik ne gleda;
     * izjeme veljajo tudi takrat.
     */
    fun zaspi(aktiven: Boolean, odUporabeMs: Long, odOdhodaMs: Long?, pritisk: Boolean, izjema: Boolean): Boolean {
        if (izjema) return false
        if (pritisk && (!aktiven || odOdhodaMs != null)) return true
        return preostanek(aktiven, odUporabeMs, odOdhodaMs) == 0L
    }
}
