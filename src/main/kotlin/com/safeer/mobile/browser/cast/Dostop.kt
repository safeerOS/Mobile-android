package com.safeer.mobile.browser.cast

import android.content.Context
import android.util.Log
import com.safeer.mobile.browser.cast.DostopPravila.Zahteva
import com.safeer.mobile.browser.cast.DostopPravila.Zmoznost
import org.json.JSONObject

/**
 * Kaj sme vsaka druga naprava v Safeer Linku na TEM telefonu (pravila: [DostopPravila], ista kot na TV in racunalniku -
 * core/link_dostop.py). Zapis je samo na tej napravi: o svojih vsebinah odloca vsaka naprava sama, nic iz dohodnega
 * sporocila ne da dostopa.
 *
 * Tedenski pregled 4. 11. 2026: telefon je izvedel vsak control.command katerekoli naprave v Linku (aplikacije,
 * datoteke, ponovni zagon, odpiranje strani), ker preverbe ni imel. Zdaj: naprava brez zapisa nima dostopa; ob prvem
 * zagonu po uvedbi obdrzijo dostop naprave, ki so bile s telefonom v krogu ze pred [MEJA_TELEFONA] (uporabnikove
 * dosedanje naprave delajo naprej brez dotika), pozneje dodane zacnejo brez. Odpre jih uporabnik na strani Safeer Link.
 *
 * Telefon zascite od naprave do naprave (E2e) se nima: odloca oznaka posiljatelja, ki jo vpise sredisce (enako kot
 * TV za napravo, ki zascite se ne zna).
 */
object Dostop {
    private const val TAG = "SafeerDostop"
    private const val PREFS = "safeer_dostop"
    private const val KLJUC_NAPRAVE = "naprave"
    private const val KLJUC_PODEDOVANO = "podedovano_ob"
    /**
     * Neodvisni pregled 4. 11. 2026 09:10: kljuci clanov kroga, kot jih je ta naprava hranila ob prvem zagonu procesa z
     * dovoljenji - zapise jih [zapomniZnane] iz Application.onCreate, preden Link sprejme karkoli. Cas »dodano« v krogu
     * nastavi druga naprava, zato sam ne odloca o podedovanem dostopu.
     */
    private const val KLJUC_ZNANI = "znani_ob_uvedbi"
    /** Krog te naprave na disku (KrogNaprave: safeer_cast_prefs / KrogZaupanja.KLJUC_SHRAMBE). */
    private const val PREFS_KROGA = "safeer_cast_prefs"

    /** 4. 11. 2026 00:00:00 UTC - uvedba dovoljenj na telefonu (TV: DostopPravila.MEJA_PODEDOVANJA, 6. 10. 2026). */
    const val MEJA_TELEFONA = 1_793_750_400.0

    private var zapis: HashMap<String, Set<Zmoznost>>? = null

    /**
     * Enkrat, ob prvem zagonu z dovoljenji (Application.onCreate - pred vsako storitvijo in sporocilom Linka): kljuci, ki
     * jih je ta naprava ze imela v krogu. Bere krog z diska v svoj primerek (ne v skupni KrogNaprave), commit = zapis takoj.
     */
    fun zapomniZnane(ctx: Context) {
        try {
            val prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.contains(KLJUC_PODEDOVANO) || prefs.contains(KLJUC_ZNANI)) return
            val json = ctx.applicationContext.getSharedPreferences(PREFS_KROGA, Context.MODE_PRIVATE).getString(KrogZaupanja.KLJUC_SHRAMBE, null)
            val kljuci = if (json.isNullOrBlank()) emptySet()
                else KrogZaupanja().apply { zdruzi(json, obvesti = false) }.clani().map { it.kljuc }.toSet()
            prefs.edit().putStringSet(KLJUC_ZNANI, kljuci).commit()
        } catch (e: Throwable) { Log.w(TAG, "znani kljuci ob uvedbi: ${e.javaClass.simpleName}") }
    }

    private fun lastniKljuc(): String? = try { HubTls.javniKljucB64() } catch (_: Throwable) { null }

    private fun lastnoJedro(): String? = lastniKljuc()?.let { try { KrogZaupanja.idIzKljuca(it) } catch (_: Throwable) { null } }

    @Synchronized
    private fun nalozen(ctx: Context): HashMap<String, Set<Zmoznost>> {
        zapis?.let { return it }
        val prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val m = HashMap<String, Set<Zmoznost>>()
        try {
            val o = JSONObject(prefs.getString(KLJUC_NAPRAVE, "{}") ?: "{}")
            for (k in o.keys()) m[k] = DostopPravila.izNiza(o.optString(k))
        } catch (_: Throwable) { }
        if (!prefs.contains(KLJUC_PODEDOVANO)) {
            val krog = KrogNaprave.krog(ctx)
            val podedovani = try {
                // Brez zapisa [zapomniZnane] (ne bi smelo biti) nihce ne podeduje: »ne vem« ni »da«.
                val znani = prefs.getStringSet(KLJUC_ZNANI, null)?.toSet() ?: emptySet()
                DostopPravila.podedovani(krog.clani().map { DostopPravila.Clan(it.id, it.kljuc, it.dodano) }, lastniKljuc(),
                    KrogZaupanja::idIzKljuca, MEJA_TELEFONA, znani)
            } catch (_: Throwable) { emptySet() }
            for (j in podedovani) if (j !in m) m[j] = DostopPravila.VSE_ZMOZNOSTI
            prefs.edit().putString(KLJUC_NAPRAVE, vJson(m)).putLong(KLJUC_PODEDOVANO, System.currentTimeMillis()).remove(KLJUC_ZNANI).apply()
            Log.i(TAG, "Dovoljenja uvedena: dostop obdrzi ${podedovani.size} dosedanjih naprav; nove zacnejo brez dostopa.")
        }
        zapis = m
        return m
    }

    private fun vJson(m: Map<String, Set<Zmoznost>>): String =
        JSONObject().apply { for ((k, v) in m) put(k, DostopPravila.vNiz(v)) }.toString()

    /** Stalna oznaka naprave (jedro); prazna, ce je pod oznako v krogu drug kljuc - taka oznaka nima nicesar. */
    fun jedro(ctx: Context, id: String): String =
        DostopPravila.jedro(id, { i -> try { KrogNaprave.krog(ctx).clanZaId(i)?.kljuc } catch (_: Throwable) { null } }, KrogZaupanja::idIzKljuca)

    fun jeTaNaprava(ctx: Context, id: String): Boolean {
        if (id.isBlank()) return false
        val nas = lastnoJedro() ?: return false
        return jedro(ctx, id) == nas
    }

    /** Napaka pri branju kroga = umaknjena (»ne vem« ni »da«). */
    private fun umaknjena(ctx: Context, jedro: String): Boolean =
        try { KrogNaprave.krog(ctx).jeUmaknjen(jedro) } catch (_: Throwable) { true }

    fun zmoznosti(ctx: Context, id: String): Set<Zmoznost> {
        if (id.isBlank()) return emptySet()
        val j = jedro(ctx, id)
        if (j.isBlank()) return emptySet()
        if (j == lastnoJedro()) return DostopPravila.VSE_ZMOZNOSTI
        val dano = synchronized(this) { nalozen(ctx)[j] } ?: emptySet()
        return DostopPravila.veljavne(dano, umaknjena(ctx, j))
    }

    private fun clanKroga(ctx: Context, jedro: String): Boolean {
        val krog = try { KrogNaprave.krog(ctx) } catch (_: Throwable) { return false }
        val jedra = HashSet<String>()
        try {
            for (cl in krog.clani()) { jedra.add(cl.id); try { jedra.add(KrogZaupanja.idIzKljuca(cl.kljuc)) } catch (_: Throwable) { } }
        } catch (_: Throwable) { return false }
        return DostopPravila.clanKroga(jedro, { it == lastnoJedro() }, { it in jedra }, { krog.jeUmaknjen(it) })
    }

    /** Ali sme [posiljatelj] izvesti ukaz [dejanje] (control.command). Zavrnitev gre v dnevnik brez vsebine ukaza. */
    fun smeDejanje(ctx: Context, posiljatelj: String, dejanje: String): Boolean {
        val zahteva = DostopPravila.zahtevaDejanja(dejanje)
        val sme = DostopPravila.smeUkaz(dejanje, DostopPravila.sme(zmoznosti(ctx, posiljatelj), zahteva)) {
            clanKroga(ctx, jedro(ctx, posiljatelj))
        }
        if (!sme) Log.i(TAG, "Zavrnjeno: ${posiljatelj.ifBlank { "(neznan posiljatelj)" }} nima dostopa za $dejanje ($zahteva)")
        return sme
    }

    /** Kot [smeDejanje] za sporocila, ki niso ukaz (stran, ki se odpre sama, zaslon, usklajevanje). */
    fun smeSporocilo(ctx: Context, posiljatelj: String, tip: String, dejanje: String = ""): Boolean {
        val zahteva = DostopPravila.zahtevaSporocila(tip, dejanje)
        val sme = DostopPravila.sme(zmoznosti(ctx, posiljatelj), zahteva)
        if (!sme) Log.i(TAG, "Zavrnjeno: ${posiljatelj.ifBlank { "(neznan posiljatelj)" }} nima dostopa za $tip ($zahteva)")
        return sme
    }

    /** Ali je napravi [id] odprt ozji krog (vse). Za stikalo na strani Safeer Link. */
    fun vOzjemKrogu(ctx: Context, id: String): Boolean = DostopPravila.sme(zmoznosti(ctx, id), Zahteva.VSE)

    /**
     * Uporabnik je na TEM telefonu (stran Safeer Link, most v locenem pogledu brez spletnih strani) napravi [id] odprl ali
     * zaprl ozji krog. Prazen nabor = brez dostopa (naprava samo pomaga pri povezavi). Ozji krog vkljucuje vse zmoznosti
     * (lastnik 25. 10. 2026 10:49: ena privolitev, brez locenih stikal).
     */
    fun nastavi(ctx: Context, id: String, dano: Set<Zmoznost>) {
        if (id.isBlank() || jeTaNaprava(ctx, id)) return
        val j = jedro(ctx, id)
        if (j.isBlank()) return
        val novo = DostopPravila.ozjiKrog(dano)
        synchronized(this) {
            val m = nalozen(ctx)
            m[j] = novo
            ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KLJUC_NAPRAVE, vJson(m)).apply()
        }
        Log.i(TAG, "Dostop naprave $j: ${DostopPravila.vNiz(novo).ifEmpty { "brez" }}")
        if (Zmoznost.DATOTEKE !in novo) {
            try { com.safeer.mobile.browser.link.DatotekeStreznik.preklici(id); com.safeer.mobile.browser.link.DatotekeStreznik.preklici(j) } catch (_: Throwable) { }
        }
    }
}
