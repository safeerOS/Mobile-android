package com.safeer.mobile.browser

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import java.util.UUID

class TabModel(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "Začetna stran",
    var url: String = TabSessionCodec.HOME,
    var isDesktop: Boolean = false,
    var favicon: String = "🦁",
    var isPlayingAudio: Boolean = false
) {
    var loadedWebView: ChromiumEngineView? = null
        internal set
    internal lateinit var createView: () -> ChromiumEngineView
    val webView: ChromiumEngineView get() = loadedWebView ?: createView()
    internal var savedState: Bundle? = null
    internal var lastUsed = SystemClock.elapsedRealtime()
    /** Po sesutju izrisovalnika: stran se ne nalozi sama, ceka na uporabnikov gumb. */
    internal var crashed = false
    /** Zavihek je nastal kot pojavno okno (prijava); Nazaj ga zapre, ne pelje na prazno stran. */
    internal var jePojavni = false
    /** Zavihek, ki je to okno odprl - tja se vrnemo, ko ga zapremo. */
    internal var odpiralec: String? = null
}

class TabManager(
    private val container: FrameLayout,
    private val onTabsUpdated: (count: Int, activeTab: TabModel?) -> Unit
) {
    private val tabs = mutableListOf<TabModel>()
    private var activeTabId: String? = null
    private val context = container.context
    private val prefs = context.getSharedPreferences("safeer_tab_session", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private var restoring = false
    private var disposed = false
    private val saveTask = Runnable { saveSession() }
    /** Kdaj (elapsedRealtime) je aplikacija sla z zaslona; null = na zaslonu. */
    private var odsotnaOd: Long? = null
    /** Kdaj (elapsedRealtime) je nacrtovan naslednji pregled spanja; 0 = ni nacrtovan. */
    private var pregledOb = 0L
    private val sleepTask = Runnable {
        pregledOb = 0L
        if (!disposed) suspendInactiveTabs()
    }

    /** Configure callbacks before the first navigation, including restored/background tabs. */
    var onViewCreated: ((TabModel) -> Unit)? = null
    var onAudioStateChanged: ((tab: TabModel, playing: Boolean) -> Unit)? = null

    init { naPregled(SpanjePravila.ZAVIHEK_V_OZADJU_MS) }

    /**
     * Pregled spanja ob prvem roku. Pregled ne tece na slepo vsako minuto: prej je zavihek v ozadju
     * zato zaspal sele 60-120 s po roku. Kasnejsi rok nikoli ne prestavi ze nacrtovanega prejsnjega.
     */
    private fun naPregled(zamikMs: Long) {
        if (disposed) return
        val zamik = zamikMs.coerceAtLeast(500L)
        val ob = SystemClock.elapsedRealtime() + zamik
        if (pregledOb != 0L && pregledOb <= ob) return
        handler.removeCallbacks(sleepTask)
        pregledOb = ob
        handler.postDelayed(sleepTask, zamik)
    }

    /** Aplikacija ni vec na zaslonu (onStop): cez 3 min (2FA v drugi aplikaciji) zaspi tudi aktivni, ce ne predvaja in nima vnosa. */
    fun naOdhod() {
        odsotnaOd = SystemClock.elapsedRealtime()
        naPregled(SpanjePravila.APLIKACIJA_V_OZADJU_MS)
    }

    /** Aplikacija je spet na zaslonu (onStart): zaspan aktivni zavihek zbudimo, naslov in Nazaj ostaneta. */
    fun naPrihod() {
        odsotnaOd = null
        val aktivni = getActiveTab() ?: return
        // Po sesutju ostane gumb za ponovni poskus: o nalaganju odloci uporabnik.
        if (aktivni.loadedWebView == null && !aktivni.crashed && !disposed) switchTab(aktivni.id)
        naPregled(SpanjePravila.ZAVIHEK_V_OZADJU_MS)
    }

    /** Kar zavihek drzi budnega (zvok, video, nedokoncan vnos, prijava, nalaganje). */
    private fun izjema(tab: TabModel, view: ChromiumEngineView): Boolean = SpanjePravila.izjema(
        predvaja = tab.isPlayingAudio || view.isPlayingAudio,
        celozaslonskiVideo = view.isFullscreenVideoActive(),
        // Odprt izbirnik datotek steje kot obrazec: uporabnik je sredi nalaganja.
        obrazec = view.hasEditedForm || view.cakaIzbiroDatotek,
        // Pojavno okno prijave in zavihek, ki ga je odprl, sta povezana (window.opener, postMessage): ce eden
        // zaspi, se prijava OAuth ne konca. Z rokom 60 s bi se to zgodilo ze med vpisom kode 2FA.
        prijava = AuthenticationPages.isAuthenticationPage(tab.url) || tab.jePojavni ||
            tabs.any { it.odpiralec == tab.id && it.loadedWebView != null },
        nalaga = view.progress < 100
    )

    private fun zdajZaspi(tab: TabModel, view: ChromiumEngineView, pritisk: Boolean): Boolean {
        val now = SystemClock.elapsedRealtime()
        return SpanjePravila.zaspi(tab.id == activeTabId, now - tab.lastUsed, odsotnaOd?.let { now - it }, pritisk, izjema(tab, view))
    }

    fun getPlayingTab(): TabModel? {
        fun playing(tab: TabModel) = tab.isPlayingAudio || tab.loadedWebView?.isPlayingAudio == true
        return getActiveTab()?.takeIf { playing(it) } ?: tabs.firstOrNull { playing(it) }
    }

    val count: Int get() = tabs.size

    private fun addModel(saved: SavedTab): TabModel {
        val tab = TabModel(id = saved.id, url = saved.url, title = saved.title, isDesktop = saved.desktop)
        tab.createView = { ensureView(tab) }
        tabs.add(tab)
        return tab
    }

    fun createTab(context: Context, url: String = TabSessionCodec.HOME, makeActive: Boolean = true): TabModel {
        val tab = addModel(SavedTab(UUID.randomUUID().toString(), url, "Nov zavihek", PreferencesManager.isDesktopModeDefault(context)))
        if (makeActive || tabs.size == 1) switchTab(tab.id) else notifyUpdated()
        return tab
    }

    /** Poveze pogled z zavihkom. Loceno, ker pogled lahko nastane tudi pred zavihkom. */
    private fun opremi(tab: TabModel, view: ChromiumEngineView) {
        view.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        view.isDesktopMode = tab.isDesktop
        view.onAudioStateChanged = { playing ->
            if (tab.loadedWebView === view) {
                tab.isPlayingAudio = playing
                if (!playing && activeTabId != tab.id) view.onPause()
                onAudioStateChanged?.invoke(tab, playing)
            }
        }
        view.onRendererGone = { rendererGone(tab, view) }
        onViewCreated?.invoke(tab)
    }

    /**
     * Pogled za pojavno okno, ki (se) ni zavihek. Dokler ne vemo, kam okno pelje, ga med
     * zavihke ne vpisemo - sicer bi ob vsakem oglasnem oknu stevec zavihkov poskocil in
     * se takoj vrnil.
     */
    fun pripraviPojavni(): ChromiumEngineView {
        val view = ChromiumEngineView(context)
        // Pogled, ki ni v oknu, ne izvede naslova, ki mu ga stran nastavi naknadno
        // (window.open('about:blank'), nato location = ...). Zato ga pritrdimo takoj -
        // a skritega in velikosti 1x1, da uporabnik o njem ne ve nicesar.
        view.layoutParams = FrameLayout.LayoutParams(1, 1)
        view.visibility = android.view.View.INVISIBLE
        try { container.addView(view) } catch (_: Exception) {}
        return view
    }

    /** Okno se je izkazalo za pravo (prijava): pogled sprejmemo med zavihke in ga pokazemo. */
    fun posvoji(pogled: ChromiumEngineView, naslov: String, odpiralec: String? = null): TabModel {
        val tab = addModel(SavedTab(UUID.randomUUID().toString(), naslov, "Nov zavihek",
            PreferencesManager.isDesktopModeDefault(context)))
        tab.loadedWebView = pogled
        tab.jePojavni = true
        tab.odpiralec = odpiralec
        pogled.visibility = android.view.View.VISIBLE
        opremi(tab, pogled)
        switchTab(tab.id)
        return tab
    }

    /**
     * Zapre pojavni zavihek in se vrne na zavihek, ki ga je odprl. Pojavno okno nima
     * zgodovine, v katero bi se lahko vrnilo: Nazaj v njem pomeni "zapri to okno".
     */
    fun zapriPojavni(context: Context, tab: TabModel) {
        val nazaj = tab.odpiralec
        closeTab(context, tab.id)
        if (nazaj != null && tabs.any { it.id == nazaj }) switchTab(nazaj)
    }

    /** Okno je bilo oglas: pogled zavrzemo, zavihkov se nismo dotaknili. */
    fun zavrziPojavni(pogled: ChromiumEngineView) {
        try { (pogled.parent as? ViewGroup)?.removeView(pogled) } catch (_: Exception) {}
        try { pogled.destroy() } catch (_: Exception) {}
    }

    private fun ensureView(tab: TabModel): ChromiumEngineView {
        check(!disposed && tab in tabs)
        tab.loadedWebView?.let { return it }
        val view = ChromiumEngineView(context)
        tab.loadedWebView = view
        opremi(tab, view)
        if (activeTabId == tab.id) attach(view)
        val state = tab.savedState
        tab.savedState = null
        val restored = state != null && try { view.restoreState(state) != null } catch (_: Exception) { false }
        // Po sesutju ne nalagamo sami: uporabnik se odloci, kdaj poskusiti znova.
        if (!restored && !tab.crashed && tab.url.isNotEmpty() && tab.url != "about:blank") view.loadUrl(tab.url)
        return view
    }

    private fun attach(view: ChromiumEngineView) {
        container.removeAllViews()
        (view.parent as? ViewGroup)?.removeView(view)
        container.addView(view)
    }

    fun switchTab(tabId: String) {
        val target = tabs.find { it.id == tabId } ?: return
        getActiveTab()?.takeIf { it.id != tabId }?.let { old ->
            old.lastUsed = SystemClock.elapsedRealtime()
            if (!old.isPlayingAudio && old.loadedWebView?.isPlayingAudio != true) old.loadedWebView?.onPause()
        }
        activeTabId = tabId
        target.lastUsed = SystemClock.elapsedRealtime()
        val ponovniPoskus = target.crashed
        target.crashed = false
        val view = ensureView(target)
        if (ponovniPoskus && target.url.isNotEmpty() && target.url != "about:blank") view.loadUrl(target.url)
        attach(view)
        view.onResume()
        notifyUpdated()
        naPregled(SpanjePravila.ZAVIHEK_V_OZADJU_MS)
    }

    /** Save only URLs/titles; background pages remain unloaded until selected. */
    fun restoreSession(): Boolean {
        if (tabs.isNotEmpty()) return true
        val session = TabSessionCodec.decode(prefs.getString("session", null))
        if (session.tabs.isEmpty()) return false
        restoring = true
        try {
            session.tabs.forEach { addModel(it) }
            switchTab(session.activeId ?: tabs.first().id)
        } finally { restoring = false }
        scheduleSave()
        return true
    }

    fun scheduleSave() {
        if (restoring || disposed) return
        handler.removeCallbacks(saveTask)
        handler.postDelayed(saveTask, 400L)
    }

    fun saveSession() {
        if (restoring || disposed || tabs.isEmpty()) return
        handler.removeCallbacks(saveTask)
        val data = TabSession(tabs.map { SavedTab(it.id, it.url, it.title, it.isDesktop) }, activeTabId)
        prefs.edit().putString("session", TabSessionCodec.encode(data)).apply()
    }

    /**
     * Uspava zavihke po SpanjePravila: v ozadju po 60 s, aktivni 3 min po odhodu aplikacije z zaslona. Glasba, video,
     * nedokoncan obrazec, prijava in nalaganje ostanejo budni; saveState ohrani zgodovino Nazaj in polozaj.
     */
    fun suspendInactiveTabs(memoryPressure: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val odOdhoda = odsotnaOd?.let { now - it }
        tabs.toList().forEach { tab ->
            val view = tab.loadedWebView ?: return@forEach
            val aktiven = tab.id == activeTabId
            if (!zdajZaspi(tab, view, memoryPressure)) {
                val preostanek = SpanjePravila.preostanek(aktiven, now - tab.lastUsed, odOdhoda) ?: return@forEach
                // Rok je mimo, a izjema (zvok, vnos ...) ga drzi budnega: pogledamo znova pozneje.
                naPregled(if (preostanek > 0L) preostanek else SpanjePravila.PONOVNI_PREGLED_MS)
                return@forEach
            }
            val candidateUrl = tab.url
            view.canSuspendSafely { safe ->
                // Odgovor JS pride pozneje: uporabnik je morda medtem izbral, premaknil ali zaprl zavihek
                // ali se vrnil v aplikacijo - pravilo zato preverimo znova s svezimi podatki.
                if (disposed || tab !in tabs || tab.loadedWebView !== view || tab.url != candidateUrl) return@canSuspendSafely
                if (!safe) { naPregled(SpanjePravila.PONOVNI_PREGLED_MS); return@canSuspendSafely }
                if (!zdajZaspi(tab, view, memoryPressure)) return@canSuspendSafely
                val state = Bundle()
                if (try { view.saveState(state) == null } catch (_: Exception) { true }) return@canSuspendSafely
                tab.savedState = state
                release(tab, view)
            }
        }
        saveSession()
    }

    private fun release(tab: TabModel, view: ChromiumEngineView) {
        tab.loadedWebView = null
        tab.isPlayingAudio = false
        view.onAudioStateChanged = null
        view.onRendererGone = null
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
    }

    private fun rendererGone(tab: TabModel, view: ChromiumEngineView) {
        if (tab.loadedWebView !== view) {
            android.util.Log.w("SafeerTabs", "renderer gone for a view the tab no longer holds")
            return
        }
        android.util.Log.w("SafeerTabs", "tab " + tab.id + ": view released after renderer crash")
        view.exitFullscreenVideo()
        tab.savedState = null
        tab.crashed = true
        release(tab, view)
        onAudioStateChanged?.invoke(tab, false)
        if (tab.id == activeTabId) {
            // No automatic crash/reload loop. The user decides when to retry.
            container.addView(Button(context).apply {
                setText(R.string.tab_reload_after_crash)
                setOnClickListener { switchTab(tab.id) }
            })
        }
        notifyUpdated()
    }

    fun closeTab(context: Context, tabId: String) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return
        val tab = tabs[index]
        tab.loadedWebView?.let { release(tab, it) }
        tabs.removeAt(index)
        if (tabs.isEmpty()) createTab(context)
        else if (activeTabId == tabId) switchTab(tabs[minOf(index, tabs.lastIndex)].id)
        else notifyUpdated()
        onAudioStateChanged?.invoke(tab, false)
    }

    fun closeAllTabs(context: Context) {
        tabs.forEach { tab -> tab.loadedWebView?.let { release(tab, it) } }
        tabs.clear()
        container.removeAllViews()
        activeTabId = null
        createTab(context)
        onAudioStateChanged?.invoke(getActiveTab()!!, false)
        saveSession()
    }

    fun dispose() {
        saveSession()
        disposed = true
        handler.removeCallbacksAndMessages(null)
        tabs.forEach { tab -> tab.loadedWebView?.let { release(tab, it) } }
        tabs.clear()
    }

    fun getActiveTab(): TabModel? = tabs.find { it.id == activeTabId } ?: tabs.firstOrNull()
    fun getAllTabs(): List<TabModel> = tabs.toList()
    /** Ali je zavihek nastal kot pojavno okno. */
    fun jePojavni(tab: TabModel): Boolean = tab.jePojavni
    fun switchToNextTab() = switchRelative(1)
    fun switchToPrevTab() = switchRelative(-1)
    private fun switchRelative(offset: Int) {
        if (tabs.size <= 1) return
        val index = tabs.indexOfFirst { it.id == activeTabId }
        if (index >= 0) switchTab(tabs[(index + offset + tabs.size) % tabs.size].id)
    }
    private fun notifyUpdated() {
        onTabsUpdated(tabs.size, getActiveTab())
        scheduleSave()
    }
}
