package com.safeer.mobile.browser

import android.content.Context
import com.safeer.threatfeed.PlainListSource
import com.safeer.threatfeed.ThreatListAgent
import java.io.File

/**
 * 🔄 ThreatFeedsUpdater – agent za sezname nevarnih strani (ThreatFox, URLhaus, Phishing Army).
 *
 * Ob vsakem zagonu brskalnika:
 *  1. takoj v ozadju (nizka prioriteta) naloži sezname, shranjene ob prejšnjem zagonu (preverjeni s SHA-256),
 *     zato je zaščita popolna že nekaj trenutkov po zagonu,
 *  2. približno 12 sekund po zagonu, ko se prva stran že nalaga, preveri, ali so na voljo novi seznami
 *     (pogojni prenos: nespremenjen seznam je ena majhna zahteva), in jih zamenja brez prekinitve,
 *  3. med delovanjem preverja vsakih 6 ur.
 * Zagon in nalaganje strani s tem nista upočasnjena. Ob napaki ostanejo v uporabi obstoječi seznami.
 */
object ThreatFeedsUpdater {

    private val SOURCES = listOf(
        PlainListSource(
            id = "threatfox", name = "abuse.ch ThreatFox IOC", url = "https://threatfox.abuse.ch/downloads/hostfile/",
            category = "Botnet C2 & Malware IOC", marker = "threatfox",
        ),
        PlainListSource(
            id = "urlhaus", name = "abuse.ch URLhaus", url = "https://urlhaus.abuse.ch/downloads/hostfile/",
            category = "Zlonamerna koda (Malware)", marker = "urlhaus",
        ),
        PlainListSource(
            id = "phishing-army", name = "Phishing Army Extended",
            url = "https://phishing.army/download/phishing_army_blocklist_extended.txt",
            category = "Spletno ribarjenje (Phishing)", marker = "phishing",
        ),
        PlainListSource(
            id = "hagezi-tif", name = "HaGeZi Threat Intelligence Feeds (mini)",
            url = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/wildcard/tif.mini-onlydomains.txt",
            category = "Nevarne strani (grožnje, ribarjenje, prevare)", marker = "hagezi",
        ),
        PlainListSource(
            id = "hagezi-fake", name = "HaGeZi Fake (lažne trgovine in prevare)",
            url = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/wildcard/fake-onlydomains.txt",
            category = "Lažne trgovine in prevare", marker = "hagezi",
        ),
        PlainListSource(
            id = "easylist", name = "EasyList (pravila za oglase)", url = "https://easylist.to/easylist/easylist.txt",
            category = "Oglasi (EasyList)", marker = "easylist", minEntries = 1000, raw = true,
        ),
        PlainListSource(
            id = "si-cert", name = "SI-CERT phishing domene (Slovenija)",
            url = "https://www.cert.si/misp/rpz/last.txt",
            category = "Spletno ribarjenje (Phishing) – potrdil SI-CERT", marker = "", headerless = true,
        ),
    )

    @Volatile
    private var agent: ThreatListAgent? = null

    @Volatile
    var ruleCount: Int = 0
        private set

    /** Število prevedenih pravil EasyList v uporabi. */
    @Volatile
    var filterRuleCount: Int = 0
        private set

    /** Drevo grozenj v datoteki (mmap): seme in seznami grozenj, zgrajeno ob spremembi seznamov. */
    private fun kazaloDatoteka(context: Context) = File(File(context.applicationContext.filesDir, "threat-index"), "drevo-grozenj.bin")

    /**
     * Seme in BankGuard se spremenita samo z novo namestitvijo aplikacije; ta je del kljuca kazala. Razvojne gradnje imajo
     * lahko isto versionCode, zato steje tudi cas namestitve (lastUpdateTime).
     */
    private fun razlicica(context: Context): String = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${context.packageName}:${info.longVersionCode}:${info.lastUpdateTime}"
    } catch (_: Exception) {
        "" // brez razlicice ni kazala (kljucKazala zahteva samo vsote; prazna razlicica bi bila nevarna ob novi aplikaciji)
    }

    /**
     * Zažene agenta (enkrat na proces). Vrne v nekaj ms; vse delo z vsebino seznamov poteka v ozadju.
     *
     * Prenos s TV (4. 11. 2026): drevo grozenj se odpre SINHRONO iz kazala (mmap), ce je zgrajeno iz istih seznamov (kljuc
     * iz SHA-256 v metapodatkih agenta). Takrat agent ob zagonu prebere in prevede samo EasyList; ostali seznami ostanejo na
     * disku. Brez kazala (prvi zagon, nova namestitev, nov seznam) se vse zgradi v ozadju kot prej in kazalo se zapise.
     * Do konca prvega nalaganja zahteve v shouldInterceptRequest omejeno pocakajo (ZascitaZagona).
     */
    @Synchronized
    fun start(context: Context) {
        if (agent != null) return
        ZascitaZagona.zacni()
        val app = context.applicationContext
        val kazalo = kazaloDatoteka(app)
        val razlicica = razlicica(app)
        val listAgent = ThreatListAgent(File(app.filesDir, "threat-lists"), SOURCES,
            firstLoadPriority = Thread.NORM_PRIORITY,
            onFirstLoad = {
                // Tudi ce seznamov ni ali je kaj padlo: zahteve ne cakajo do roka.
                koncaj(ZascitaZagona.Del.DREVO, "seznamov ni ali niso berljivi")
                koncaj(ZascitaZagona.Del.EASYLIST, "seznamov ni ali niso berljivi")
            },
            onPartialLists = { lists ->
                val t = System.nanoTime()
                filterRuleCount = AdBlockEngine.installFilterLists(lists.filter { it.source.raw })
                koncaj(ZascitaZagona.Del.EASYLIST, "$filterRuleCount pravil, prevod ${(System.nanoTime() - t) / 1_000_000} ms")
                // Kazalo je bilo odprto brez branja vsebine; tu (v ozadju) se preverimo CRC celotne datoteke.
                if (!ThreatBlockEngine.preveriKazalo()) {
                    android.util.Log.w("SafeerSecurity", "Kazalo grozenj poskodovano: gradim znova iz seznamov")
                    try { kazalo.delete() } catch (_: Exception) { }
                    agent?.reloadSaved()
                }
            },
        ) { lists ->
            val t = System.nanoTime()
            val grozenj = lists.filter { !it.source.raw }
            ruleCount = ThreatBlockEngine.rebuildFromLists(grozenj, if (razlicica.isEmpty()) null else kazalo, razlicica)
            koncaj(ZascitaZagona.Del.DREVO, "zgrajeno iz ${grozenj.size} seznamov, ${ThreatBlockEngine.steviloDomen} domen, " +
                "${(System.nanoTime() - t) / 1_000_000} ms")
            val te = System.nanoTime()
            filterRuleCount = AdBlockEngine.installFilterLists(lists.filter { it.source.raw })
            koncaj(ZascitaZagona.Del.EASYLIST, "$filterRuleCount pravil, prevod ${(System.nanoTime() - te) / 1_000_000} ms")
            android.util.Log.i("SafeerSecurity", "Seznami v uporabi: ${lists.joinToString { "${it.source.name} (${it.entries.size})" }}; pravila EasyList: $filterRuleCount; pravila za skrivanje: ${AdBlockEngine.cosmeticRuleCount}")
        }
        agent = listAgent

        // Sinhrono: metapodatki (nekaj majhnih datotek) in glava kazala.
        val t = System.nanoTime()
        val shranjeni = try { listAgent.savedInfo() } catch (_: Exception) { emptyList() }
        val grozenj = shranjeni.filter { !it.source.raw }
        val kljuc = if (razlicica.isEmpty()) null else ThreatBlockEngine.kljucKazala(grozenj.map { it.source.id to it.sha256 }, razlicica)
        val odprto = ThreatBlockEngine.odpriKazalo(kazalo, kljuc)
        val ms = (System.nanoTime() - t) / 100_000 / 10.0
        if (odprto) {
            ruleCount = grozenj.sumOf { it.count }
            koncaj(ZascitaZagona.Del.DREVO, "kazalo mmap, ${ThreatBlockEngine.steviloDomen} domen, odprtje $ms ms")
        } else {
            android.util.Log.i("SafeerSecurity", "Kazalo grozenj ni v uporabi (${if (kljuc == null) "ni shranjenih seznamov" else "zgrajeno iz drugih seznamov ali ga ni"}): " +
                "drevo se gradi v ozadju (preverba $ms ms)")
        }
        // Ce je kazalo zgrajeno iz istih seznamov, ob zagonu beremo samo EasyList (raw).
        listAgent.start(if (odprto) SOURCES.filter { it.raw }.map { it.id }.toSet() else null)
    }

    /** Del zascite je pripravljen: dnevnik s casom tega dela (in skupnim, ko je pripravljeno vse). */
    private fun koncaj(del: ZascitaZagona.Del, opis: String) {
        val cas = ZascitaZagona.koncan(del) ?: return
        val ime = when (del) {
            ZascitaZagona.Del.DREVO -> "drevo grozenj pripravljeno"
            ZascitaZagona.Del.PODPISANI -> "podpisani vir pripravljen"
            ZascitaZagona.Del.EASYLIST -> "EasyList pripravljen"
        }
        android.util.Log.i("SafeerSecurity", "Zascita ob zagonu: $ime po $cas ms ($opis)" +
            if (ZascitaZagona.pripravljeno) "; vse pripravljeno po ${ZascitaZagona.pripravljenoPoMs} ms" else "")
    }

    data class UpdateResult(val totalRules: Int, val failedSources: Int, val error: String) {
        val successful: Boolean get() = failedSources == 0 && error.isEmpty()
    }

    /** Reports partial failure while keeping every previously valid list in use. */
    fun updateFeedsAsync(context: Context, onComplete: ((UpdateResult) -> Unit)? = null) {
        start(context)
        val current = agent
        val requested = current?.requestUpdate {
            val failed = current.statuses.count { it.error.isNotEmpty() || it.lastSuccessEpochSeconds == 0L }
            onComplete?.invoke(UpdateResult(ruleCount, failed, current.lastError))
        } ?: false
        if (!requested) onComplete?.invoke(UpdateResult(ruleCount, SOURCES.size, "Preverjanja ni mogoče zagnati"))
    }

    fun statusLine(): String {
        val lists = agent?.lists.orEmpty()
        val formatter = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
        val now = System.currentTimeMillis() / 1000
        val statuses = agent?.statuses.orEmpty().associateBy { it.sourceId }
        val details = SOURCES.joinToString("\n\n") { source ->
            val status = statuses[source.id]
            val success = status?.lastSuccessEpochSeconds ?: 0
            val last = if (success > 0) formatter.format(java.util.Date(success * 1000)) else "še nikoli"
            val state = when {
                !status?.error.isNullOrEmpty() -> "Zadnji poskus ni uspel: ${status?.error}"
                success == 0L -> "Čaka na prvi uspešen prenos"
                now - success > 24 * 3600 -> "Seznam ni bil uspešno preverjen več kot 24 ur"
                else -> "Preverjeno"
            }
            val count = lists.find { it.source.id == source.id }?.count ?: 0
            "${source.name}: $count pravil\nZadnje uspešno preverjanje: $last\n$state"
        }
        val running = if (agent?.isRefreshing == true) "\nPreverjanje poteka …" else ""
        return "Seznami: ${lists.size}/${SOURCES.size} virov; EasyList: $filterRuleCount pravil, ${AdBlockEngine.cosmeticRuleCount} za skrivanje$running\n\n$details"
    }
}
