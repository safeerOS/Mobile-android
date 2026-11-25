package com.safeer.mobile.browser.link

/**
 * Varnostne odlocitve sprejema zvoka - ciste, brez Androida (tests/LinkVarnostPravilaTest.kt). Prenos s TV
 * (si.safeer.tv.link.LinkVarnostPravila, pregled 10. 11. 2026, 2.4) v tedenskem pregledu 25. 11. 2026 (S3): samo
 * funkcije, ki jih uporablja ZvokSprejemnik; pomocniki naslovov so iz TV PretokPravila.
 */
object LinkVarnostPravila {
    // ------------------------------------------------------------------ sprejem zvoka (2.4)

    /** Racunalnik poslje svoje naslove v domacem omrezju; vec kot toliko jih ne poskusamo (vsak poskus caka do 4 s). */
    const val NAJVEC_NASLOVOV_ZVOKA = 6
    /** Glava toka zvoka mora priti v tem casu po povezavi; potem je tisina dovoljena (zvok tece, dokler ga racunalnik ne konca). */
    const val ROK_GLAVE_ZVOKA_MS = 8_000L

    /**
     * Naslovi iz ukaza `audio.play` (`host` najprej, nato `hosts`), na katere se sprejemnik res poveze. Prej je bil
     * seznam brez meje (vsak naslov 4 s cakanja, nit v ozadju) in je sprejel tudi to napravo samo. Zdaj: brez
     * ponovitev, samo veljavna imena in naslovi, brez zanke in povezavno-lokalnih ([gostiteljPrepovedan]), najvec
     * [NAJVEC_NASLOVOV_ZVOKA]. [lastni] so naslovi IP te naprave (iz njenih omreznih vmesnikov, kot bajti); zapis, ki je
     * eden izmed njih (tudi kot preslikani IPv4 v IPv6), odpade. Ime gostitelja ostane (kot na TV) in se razresi ob
     * povezavi - zato meja stevila vnosov.
     */
    fun nasloviZvoka(host: String, hosts: List<String>, lastni: List<ByteArray> = emptyList()): List<String> {
        val izid = ArrayList<String>()
        for (surov in listOf(host) + hosts) {
            if (izid.size >= NAJVEC_NASLOVOV_ZVOKA) break
            val n = surov.trim()
            if (n.isEmpty() || n.length > 253 || n in izid) continue
            if (!n.all { it.isLetterOrDigit() && it.code < 128 || it == '.' || it == '-' || it == ':' || it == '_' }) continue
            if (gostiteljPrepovedan(n)) continue
            val b = bajti(n)
            if (b != null && lastni.any { enakNaslov(b, it) }) continue
            izid.add(n)
        }
        return izid
    }

    /** Ali je rok ([rokMs] od [zacetekMs]) mimo; ura nazaj steje kot mimo. */
    fun rokPotekel(zacetekMs: Long, zdajMs: Long, rokMs: Long): Boolean = zdajMs < zacetekMs || zdajMs - zacetekMs >= rokMs

    // ------------------------------------------------------------------ naslovi (iz TV PretokPravila)

    private val IPV4 = Regex("\\d{1,3}(\\.\\d{1,3}){3}")

    /** Ali je ime gostitelja ta naprava sama (localhost in *.localhost, kot RFC 6761). */
    private fun jeImeZanke(gost: String): Boolean {
        val g = gost.trim().lowercase().trimEnd('.')
        return g == "localhost" || g.endsWith(".localhost")
    }

    /** Bajti naslova IP iz zapisa (brez DNS); null = ni zapis IP. */
    private fun bajti(naslov: String): ByteArray? {
        val n = naslov.trim().removePrefix("[").removeSuffix("]").substringBefore('%')
        if (IPV4.matches(n)) {
            val d = n.split('.').map { it.toInt() }
            if (d.any { it > 255 }) return null
            return ByteArray(4) { d[it].toByte() }
        }
        if (':' !in n || !n.all { it.isLetterOrDigit() || it == ':' || it == '.' }) return null
        return try { java.net.InetAddress.getByName(n).address } catch (_: Exception) { null }
    }

    private fun v4IzV6(b: ByteArray): ByteArray? {
        if (b.size != 16) return null
        val u = IntArray(16) { b[it].toInt() and 0xFF }
        if ((0 until 10).all { u[it] == 0 } && u[10] == 0xFF && u[11] == 0xFF) return b.copyOfRange(12, 16)   // ::ffff:a.b.c.d
        if (u[0] == 0x20 && u[1] == 0x02) return b.copyOfRange(2, 6)                                         // 6to4
        if (u[0] == 0 && u[1] == 0x64 && u[2] == 0xFF && u[3] == 0x9B && (4 until 12).all { u[it] == 0 }) return b.copyOfRange(12, 16) // NAT64
        // Lokalna predpona NAT64 (64:ff9b:1::/48): prevajalnik v omrezju jo lahko preslika na 127.0.0.1 ali 169.254.169.254.
        if (u[0] == 0 && u[1] == 0x64 && u[2] == 0xFF && u[3] == 0x9B && u[4] == 0 && u[5] == 1) return b.copyOfRange(12, 16)
        if ((0 until 12).all { u[it] == 0 }) return b.copyOfRange(12, 16)                                      // ::a.b.c.d, :: in ::1
        return null
    }

    /** Isti naslov IP; preslikani IPv4 v IPv6 (::ffff:a.b.c.d) je enak svojemu IPv4. */
    private fun enakNaslov(a: ByteArray, b: ByteArray): Boolean =
        (v4IzV6(a) ?: a).contentEquals(v4IzV6(b) ?: b)

    /** Prepovedan naslov IP (bajti): zanka, povezavno-lokalni, vecvrstni, nedoloceni, rezervirani. */
    private fun prepovedan(b: ByteArray): Boolean {
        val v4 = if (b.size == 4) b else v4IzV6(b)
        if (v4 != null) {
            val a = v4[0].toInt() and 0xFF; val c = v4[1].toInt() and 0xFF
            return a == 127 || a == 0 || (a == 169 && c == 254) || a >= 224
        }
        val u0 = b[0].toInt() and 0xFF; val u1 = b[1].toInt() and 0xFF
        return u0 == 0xFF || (u0 == 0xFE && (u1 and 0xC0) == 0x80)          // ff00::/8 vecvrstni, fe80::/10 povezavno-lokalni
    }

    /** Gostitelj ni dovoljen ze po zapisu (brez DNS): ime zanke ali prepovedan naslov IP. */
    private fun gostiteljPrepovedan(gost: String): Boolean {
        if (gost.isBlank() || jeImeZanke(gost)) return true
        val b = bajti(gost) ?: return false
        return prepovedan(b)
    }
}
