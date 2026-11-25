package com.safeer.mobile.browser.link

/**
 * Zetoni streznika datotek te naprave (DatotekeStreznik) - cista pravila brez Androida (tests/ZetoniDatotekTest.kt).
 *
 * Tedenski pregled 25. 11. 2026 (V3): zeton za medije prej ni imel roka, ni bil omejen po stevilu in je veljal tudi po
 * izklopu deljenja in po umiku naprave iz kroga (AGENTS.md: umik naprave takoj preklice njene zetone in seje). Pravila
 * so enaka kot na TV (si.safeer.tv.link.DatotekeStreznik, zetonZa in zetonVelja); tam so v objektu z Androidom, tu
 * locena, da jih preizkus pozene. Kljuc je id naprave ali `id#izdan` za prejsnji zeton iste naprave.
 * Cas [zdaj] je monoton (SystemClock.elapsedRealtime), da ga premik ure ne podaljsa.
 */
class ZetoniDatotek(private val nov: () -> String) {
    private class Zeton(val vrednost: String, val izdan: Long, var rabljen: Long) {
        fun zivi(zdaj: Long) = zdaj - rabljen < VELJA_MS && zdaj - izdan < NAJDLJE_MS && zdaj >= izdan
    }
    private val zetoni = HashMap<String, Zeton>()

    /** Stevilo shranjenih zetonov (za preizkus meje). */
    val stevilo: Int @Synchronized get() = zetoni.size

    /** Zeton za napravo [idNaprave]; po polovici roka nov, prejsnji velja do konca svojega roka (film, ki tece, ne pade). */
    @Synchronized
    fun zetonZa(idNaprave: String, zdaj: Long): String {
        val kljuc = idNaprave.ifBlank { "naprava" }
        zetoni.entries.removeIf { !it.value.zivi(zdaj) }
        zetoni[kljuc]?.takeIf { zdaj - it.izdan < VELJA_MS / 2 }?.let { return it.vrednost }
        val z = nov()
        zetoni[kljuc]?.let { zetoni["$kljuc#${it.izdan}"] = it }
        zetoni[kljuc] = Zeton(z, zdaj, zdaj)
        // Meja stevila: najprej odpade najdlje nerabljen, nikoli ravnokar izdani (enak cas rabe ne sme izbrisati novega).
        while (zetoni.size > NAJVEC) zetoni.entries.filter { it.key != kljuc }.minByOrNull { it.value.rabljen }?.let { zetoni.remove(it.key) } ?: break
        return z
    }

    /**
     * Ali [z] velja ob [zdaj]: zivi zeton, deljenje vklopljeno ([deli]) in naprava ni umaknjena iz kroga ([umaknjena],
     * dobi id naprave brez `#`). Ob izklopu ali umiku zeton takoj odpade, ne sele po roku. Napaka pri preverbi = umaknjena.
     */
    @Synchronized
    fun velja(z: String?, zdaj: Long, deli: () -> Boolean, umaknjena: (String) -> Boolean): Boolean {
        if (z.isNullOrBlank()) return false
        val zb = z.toByteArray()
        val (kljuc, najden) = zetoni.entries.firstOrNull { it.value.zivi(zdaj) && java.security.MessageDigest.isEqual(it.value.vrednost.toByteArray(), zb) }
            ?.let { it.key to it.value } ?: return false
        val naprava = kljuc.substringBefore('#')
        val umik = try { umaknjena(naprava) } catch (_: Throwable) { true }
        if (!deli() || umik) {
            zetoni.remove(kljuc)
            return false
        }
        najden.rabljen = zdaj
        return true
    }

    /** Preklic vseh zetonov (izklop deljenja). */
    @Synchronized
    fun pocisti() = zetoni.clear()

    companion object {
        /** Zeton velja toliko od zadnje rabe (tok, ki tece, ga drzi pri zivljenju) ... */
        const val VELJA_MS = 12 * 3600_000L
        /** ... in najvec toliko od izdaje. */
        const val NAJDLJE_MS = 7 * 24 * 3600_000L
        /** Najvec shranjenih zetonov (kot na TV). */
        const val NAJVEC = 64
    }
}
