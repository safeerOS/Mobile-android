package com.safeer.mobile.browser.cast

/**
 * Ali je naslov strani naslov Huba, ki mu zaupamo s pripetim odtisom (HubTls.jeZaupanjaVredenHub). Cista pravila
 * brez Androida (tests/HubNaslovTest.kt).
 *
 * Pregled 4. 11. 2026: prej a.port == b.port (-1 proti 443) in gostitelj z velikimi crkami - https://hub/ in
 * https://hub:443/ sta bila razlicna, stran Huba na telefonu je ostala v opozorilu o potrdilu. Izpuscena vrata so
 * privzeta vrata sheme; shema in gostitelj brez velikih crk. Drug gostitelj ali druga vrata ostanejo zavrnjeni.
 */
object HubNaslov {
    private val PRIVZETA_VRATA = mapOf("https" to 443, "http" to 80, "wss" to 443, "ws" to 80)

    private fun vrata(u: java.net.URI): Int? =
        if (u.port != -1) u.port else PRIVZETA_VRATA[(u.scheme ?: "").lowercase()]

    fun jeNaslovHuba(url: String, hubHttp: String): Boolean {
        if (hubHttp.isBlank()) return false
        return try {
            val a = java.net.URI(url)
            val b = java.net.URI(hubHttp)
            val gA = a.host ?: return false
            val gB = b.host ?: return false
            val vA = vrata(a) ?: return false
            a.scheme.equals(b.scheme, ignoreCase = true) && gA.equals(gB, ignoreCase = true) && vA == vrata(b)
        } catch (e: Exception) {
            false
        }
    }
}
