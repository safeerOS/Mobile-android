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
    /** Domaca stran brskalnika; most SafeerBridge sme premikati brskalnik samo njej. */
    const val DOMACA_BRSKALNIKA = "file:///android_asset/brave_home.html"

    /**
     * Natanko domaca stran brskalnika (poizvedba in sidro smeta biti). Prazen naslov, about:blank, safeer:// in druge
     * strani iz aplikacije ne (tedenski pregled 4. 11. 2026, N17: navigate() je prej sprejel tudi prazen naslov - pogled
     * pred prvo stranjo ali okvir about:blank - in vsako file:///android_asset/ ali safeer:// stran).
     */
    fun jeDomacaBrskalnika(url: String?): Boolean {
        val cist = url?.substringBefore('#')?.substringBefore('?') ?: return false
        return cist == DOMACA_BRSKALNIKA
    }

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

    /** Od kod je prisel naslov, ki ga brskalnik nalozi v zavihek (namera druge aplikacije ali sporocilo druge naprave). */
    enum class Vhod { NAMERA, LINK, SPLET }

    /** Najdaljsi naslov od zunaj (WebView ga sicer sprejme do 2 MB; tako dolg naslov ni povezava). */
    const val NAJDALJSI_VHODNI = 8192

    /**
     * Vhodna vrata brskalnika (prenos s TV si.safeer.tv.SpletVarnostPravila, 4. 11. 2026, UXSS): MainActivity je
     * izvozena, zato je tuja aplikacija z namero VIEW (dataString) lahko podtaknila javascript: ali data:, Safeer Link pa
     * v cast.url ali share.screen poljuben naslov. Vsak zunanji naslov gre skozi to eno preverbo:
     * - vedno: http(s) z gostiteljem;
     * - NAMERA: se krajevni PDF za nas pregledovalnik (content:// tuje avtoritete, nas ponudnik samo s podeljenim
     *   branjem; file://...pdf izven assets in izven zasebnih map /data/);
     * - LINK: se natanko nasa domaca stran (Daljinec open_url »home«);
     * - SPLET (deljeno besedilo, ki je povezava): samo http(s).
     * Nikoli javascript:, vbscript:, data:, blob:, intent:, about:. Kontrolni znaki in presledki kjerkoli so zavrnjeni:
     * Chromium iz naslova odstrani tabulator in novo vrstico (»java\tscript:«).
     */
    fun vhodniNaslov(url: String?, vhod: Vhod, dovoljenjeBranja: Boolean = false): Boolean {
        if (url.isNullOrEmpty() || url.length > NAJDALJSI_VHODNI) return false
        if (url.any { it <= ' ' || it == '\u007f' }) return false
        val shema = url.substringBefore(':', "").lowercase()
        if ((shema == "http" || shema == "https") && url.length > shema.length + 3) {
            val ostanek = url.substring(shema.length + 1).replace('\\', '/')
            if (!ostanek.startsWith("//")) return false
            val gostitelj = ostanek.substring(2).substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')
            return gostitelj.isNotEmpty() && !gostitelj.startsWith(":")
        }
        return when (vhod) {
            Vhod.NAMERA -> jeKrajevniPdf(url) && (!url.startsWith("content://") || dovoljenjeBranja || !lastnaAvtoriteta(url))
            Vhod.LINK -> jeDomacaBrskalnika(url)
            Vhod.SPLET -> false
        }
    }

    /**
     * Zadnja vrata v ChromiumEngineView.loadUrl (kot TV, 4. 11. 2026): nasa koda z loadUrl nikoli ne nalozi skripte ali
     * dokumenta data: (preverjeno z grep; svoje strani nalagamo z loadDataWithBaseURL), zato je tak naslov vedno
     * podtaknjen. Shemo beremo kot Chromium: brez vodilnih presledkov in kontrolnih znakov, brez tabulatorjev in vrstic.
     */
    fun skriptniNaslov(url: String?): Boolean {
        if (url == null) return false
        val cist = url.trimStart { it <= ' ' }.filterNot { it == '\t' || it == '\n' || it == '\r' }
        val shema = cist.substringBefore(':', "").lowercase()
        return shema == "javascript" || shema == "vbscript" || shema == "data"
    }

    /**
     * content:// nase druzine (com.safeer.*, si.safeer.*): tuja aplikacija bi z namero VIEW odprla NAS ponudnik
     * (com.safeer.mobile.browser.fileprovider) z nasimi pravicami. Velja samo s FLAG_GRANT_READ_URI_PERMISSION, ki ga
     * Android ne dovoli aplikaciji brez dostopa do ponudnika. Avtoriteta brez »uporabnik@« in vrat; %-kodirana je nasa.
     */
    fun lastnaAvtoriteta(url: String): Boolean {
        val a = url.removePrefix("content://").substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfterLast('@').substringBefore(':').lowercase()
        return a == "com.safeer" || a.startsWith("com.safeer.") || a == "si.safeer" || a.startsWith("si.safeer.") || '%' in a
    }

    /**
     * Mape, iz katerih tuja namera sme odpreti file://...pdf: skupna shramba in USB (prenos s TV 05d2336d, 4. 11. 2026).
     * Zasebne mape (/data/, /data_mirror/, /mnt/expand/), /proc/, /sys/ in assets nikoli - zato seznam dovoljenih, ne
     * prepovedanih. Android od 7.0 file:// med aplikacijami sicer ne dovoli (FileUriExposedException).
     */
    private val DOVOLJENE_PDF_MAPE = listOf("/storage/", "/sdcard/", "/mnt/sdcard/", "/mnt/media_rw/")

    /**
     * Enako kot PdfPregledovalnik.jeLokalni (content:// ali file://...pdf), od zunaj pa file:// samo v DOVOLJENE_PDF_MAPE.
     * ContentResolver.openInputStream bere ODKODIRANO pot (Uri.getPath), zato preverjamo odkodirano: %64ata (= data),
     * %2e%2e, file:////data, file://localhost/..., /./ in \ so zavrnjeni; ostanek % po odkodiranju (dvojno kodiranje)
     * tudi. Kodirana locila poti (%2f, %5c) zavrnemo ze pred odkodiranjem - v imenu PDF jih ni.
     */
    private fun jeKrajevniPdf(url: String): Boolean {
        if (url.startsWith("content://")) return url.length > "content://".length
        if (!url.startsWith("file://") || url.startsWith("file:///android_asset/")) return false
        // Brez gostitelja: file://localhost/... ali file://naprava/... ni nasa pot.
        val surova = url.removePrefix("file://").substringBefore('?').substringBefore('#')
        if (!surova.startsWith("/")) return false
        val malaSurova = surova.lowercase()
        if ("%2f" in malaSurova || "%5c" in malaSurova) return false
        val odkodirana = odkodirajPot(surova) ?: return false
        if ('%' in odkodirana || '\\' in odkodirana || odkodirana.any { it < ' ' || it == '\u007f' }) return false
        // Vec posevnic (file:////data/...) je ista pot kot ena.
        val pot = odkodirana.replace(Regex("/{2,}"), "/")
        if (pot.split('/').any { it == "." || it == ".." }) return false
        val mala = pot.lowercase()
        if (DOVOLJENE_PDF_MAPE.none { mala.startsWith(it) }) return false
        return mala.endsWith(".pdf")
    }

    /** %XX v UTF-8 (kot Uri.decode); napacno kodiranje vrne null. Brez android.net, da je pravilo preizkusljivo brez Androida. */
    internal fun odkodirajPot(s: String): String? {
        if ('%' !in s) return s
        val izhod = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%') {
                if (i + 2 >= s.length) return null
                val hex = s.substring(i + 1, i + 3)
                if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
                izhod.write(hex.toInt(16)); i += 3
            } else {
                izhod.write(c.toString().toByteArray(Charsets.UTF_8)); i++
            }
        }
        return try {
            Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(izhod.toByteArray())).toString()
        } catch (_: java.nio.charset.CharacterCodingException) { null }
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
