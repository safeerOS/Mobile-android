package com.safeer.mobile.browser

/**
 * Kateri naslov je Googlov ali YouTubov - samo po gostitelju, nikoli po delu poti ali poizvedbe. Brez Androida, da jih
 * preverimo v JVM (tests/GooglePravilaTest.kt). Isto kot si.safeer.tv.GooglePravila (del za prepoznavo Googla).
 *
 * Tedenski pregled 4. 11. 2026: UserScriptManager.isGoogleDomain na telefonu je Google prepoznal po nizu (».google.«
 * kjerkoli v gostitelju, »/recaptcha« ali »google.com/search« kjerkoli v naslovu) - taka stran je ostala brez nasih
 * skript (skrivanje oglasov).
 *
 * Zunanji pregled 2. 11. 2026: prej je zadoscal niz kjerkoli v naslovu (»signin/v2«, ».google.«, »gstatic.com«,
 * »youtube.com«) - https://zlo.example/signin/v2 ali https://google.zlo.example/ je veljal za Google (brez filtra
 * oglasov, piskotki tretjih strani, samodejna okna, brez mostu), notyoutube.com pa je bil preusmerjen na youtube.com/tv.
 */
object GooglePravila {
    /** Gostitelj naslova http(s): male crke, brez uporabnika, vrat in koncne pike; "" = ni spletni naslov. */
    fun gostitelj(url: String?): String {
        val n = url?.trim() ?: return ""
        val i = n.indexOf("://")
        if (i <= 0) return ""
        val shema = n.substring(0, i).lowercase()
        if (shema != "http" && shema != "https") return ""
        // Chromium bere »\« kot »/«; oblast se konca pri prvem od teh znakov.
        val konec = n.indexOfAny(charArrayOf('/', '?', '#', '\\'), i + 3).let { if (it < 0) n.length else it }
        var h = n.substring(i + 3, konec).substringAfterLast('@').lowercase()
        h = if (h.startsWith("[")) h.substringBefore(']') + "]" else h.substringBefore(':')
        return h.trimEnd('.')
    }

    /** Pot naslova (brez poizvedbe in sidra), male crke; "" ce je ni. */
    fun pot(url: String?): String {
        val n = url?.trim() ?: return ""
        val i = n.indexOf("://")
        if (i <= 0) return ""
        val z = n.indexOfAny(charArrayOf('/', '\\'), i + 3)
        if (z < 0) return ""
        val ostanek = n.substring(z).replace('\\', '/')
        return ostanek.substringBefore('?').substringBefore('#').lowercase()
    }

    private fun jeVDomeni(h: String, domena: String): Boolean = h == domena || h.endsWith(".$domena")

    /** Pripona za »google.«: com ali drzavna domena (si, de, co.uk, com.br). Druge domene niso Googlove. */
    private val GOOGLE_PRIPONA = Regex("^(com|[a-z]{2}|co\\.[a-z]{2}|com\\.[a-z]{2})$")

    /** google.com, google.si, www.google.co.uk, mail.google.com ... - oznaka »google« pred Googlovo pripono. */
    fun jeGoogleGostitelj(h: String): Boolean {
        val deli = h.split('.')
        for (i in deli.indices) {
            if (deli[i] != "google" || i == deli.size - 1) continue
            if (GOOGLE_PRIPONA.matches(deli.subList(i + 1, deli.size).joinToString("."))) return true
        }
        return false
    }

    /** Gostitelji Googlove prijave (tudi YouTubova, ki je del iste prijave). */
    private fun jePrijavniGostitelj(h: String): Boolean {
        if (h == "accounts.youtube.com") return true
        val prvi = h.substringBefore('.')
        return (prvi == "accounts" || prvi == "myaccount") && h.indexOf('.') > 0 && jeGoogleGostitelj(h.substringAfter('.'))
    }

    /**
     * Googlova stran (iskanje, prijava, reCAPTCHA in njeni viri). YouTube, googlevideo in ytimg niso - tam velja
     * filter oglasov - razen accounts.youtube.com (prijava).
     */
    fun jeGoogle(url: String?): Boolean {
        val h = gostitelj(url)
        if (h.isEmpty()) return false
        if (jePrijavniGostitelj(h)) return true
        return jeGoogleGostitelj(h) || jeVDomeni(h, "gstatic.com") || jeVDomeni(h, "googleapis.com") ||
            jeVDomeni(h, "recaptcha.net")
    }
}
