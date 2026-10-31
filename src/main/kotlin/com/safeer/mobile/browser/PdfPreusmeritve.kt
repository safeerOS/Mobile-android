package com.safeer.mobile.browser

import java.net.URL

object PdfPreusmeritve {
    /** Zasebni naslov IPv4 (10/8, 172.16/12, 192.168/16, 127.0.0.1, ::1). */
    fun jeLokalniAliZasebni(host: String?): Boolean {
        if (host == null) return false
        if (host == "localhost" || host == "127.0.0.1" || host == "[::1]") return true
        val deli = host.split('.')
        if (deli.size == 4) {
            val o = IntArray(4)
            for ((i, d) in deli.withIndex()) {
                if (d.isEmpty() || d.length > 3 || !d.all { it in '0'..'9' } || (d.length > 1 && d[0] == '0')) return false
                o[i] = d.toInt()
                if (o[i] > 255) return false
            }
            if (o[0] == 10 || (o[0] == 172 && o[1] in 16..31) || (o[0] == 192 && o[1] == 168) || (o[0] == 169 && o[1] == 254)) return true
        }
        return false
    }

    /**
     * Preveri, ce je preusmeritev dovoljena:
     * - samo https (http samo, ce je bil ze prvotni naslov http in isti gostitelj)
     * - brez zasebnih/lokalnih naslovov za javne strani (ce stari ni bil zaseben, novi ne sme biti)
     */
    fun dovoljen(stariNaslov: String, noviNaslov: String): Boolean {
        return try {
            val stari = URL(stariNaslov)
            val novi = URL(noviNaslov)
            
            if (novi.protocol != "https") {
                if (novi.protocol != "http") return false
                if (stari.protocol != "http") return false
                if (stari.host != novi.host) return false
            }
            
            if (!jeLokalniAliZasebni(stari.host) && jeLokalniAliZasebni(novi.host)) {
                return false
            }
            
            true
        } catch (_: Exception) {
            false
        }
    }
}
