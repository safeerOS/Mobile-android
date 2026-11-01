package com.safeer.mobile.browser

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL

object PdfPreusmeritve {
    /** Razresevanje imena v naslove; v testih ga zamenjamo (brez omrezja). */
    @Volatile
    var razresi: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() }

    /** Gostitelj brez oglatih oklepajev, z malimi crkami in brez koncne pike. */
    fun normaliziraj(host: String?): String? {
        if (host == null) return null
        var h = host.trim().lowercase()
        if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length - 1)
        h = h.trimEnd('.')
        return h.ifEmpty { null }
    }

    private fun ipv4(h: String): IntArray? {
        val deli = h.split('.')
        if (deli.size != 4) return null
        val o = IntArray(4)
        for ((i, d) in deli.withIndex()) {
            if (d.isEmpty() || d.length > 3 || !d.all { it in '0'..'9' } || (d.length > 1 && d[0] == '0')) return null
            o[i] = d.toInt()
            if (o[i] > 255) return null
        }
        return o
    }

    private fun zasebniIpv4(o: IntArray): Boolean =
        o[0] == 0 || o[0] == 10 || o[0] == 127 || (o[0] == 172 && o[1] in 16..31) || (o[0] == 192 && o[1] == 168) ||
            (o[0] == 169 && o[1] == 254) || (o[0] == 100 && o[1] in 64..127)

    /** Naslov kot okteti (4 ali 16), ne glede na zapis; samo za dobesedne naslove IP (brez DNS). */
    private fun okteti(h: String): ByteArray? {
        ipv4(h)?.let { o -> return ByteArray(4) { o[it].toByte() } }
        if (!h.contains(':') || !h.all { it in '0'..'9' || it in 'a'..'f' || it == ':' || it == '.' }) return null
        return try { InetAddress.getByName(h).address } catch (_: Exception) { null }
    }

    /** Ali so okteti naslova lokalni ali zasebni (IPv4, IPv6 ::1, ::, fe80::/10, fc00::/7, ::ffff:IPv4). */
    fun zasebniOkteti(b: ByteArray): Boolean {
        if (b.size == 4) return zasebniIpv4(IntArray(4) { b[it].toInt() and 0xff })
        if (b.size != 16) return true
        val u = IntArray(16) { b[it].toInt() and 0xff }
        if ((0 until 15).all { u[it] == 0 } && (u[15] == 0 || u[15] == 1)) return true // :: in ::1
        if (u[0] == 0xfe && (u[1] and 0xc0) == 0x80) return true // fe80::/10
        if ((u[0] and 0xfe) == 0xfc) return true // fc00::/7
        if ((0 until 10).all { u[it] == 0 } && u[10] == 0xff && u[11] == 0xff) return zasebniIpv4(intArrayOf(u[12], u[13], u[14], u[15]))
        return false
    }

    /** Cisto preverjanje niza (brez DNS): localhost in dobesedni lokalni/zasebni naslovi IPv4/IPv6. */
    fun jeLokalniAliZasebni(host: String?): Boolean {
        val h = normaliziraj(host) ?: return false
        if (h == "localhost" || h.endsWith(".localhost")) return true
        return okteti(h)?.let { zasebniOkteti(it) } ?: false
    }

    private fun jeZasebenNaslov(a: InetAddress): Boolean =
        a.isLoopbackAddress || a.isSiteLocalAddress || a.isLinkLocalAddress || a.isAnyLocalAddress ||
            ((a is Inet4Address || a is Inet6Address) && zasebniOkteti(a.address))

    /**
     * Ali je gostitelj lokalen/zaseben: niz ali KATERI KOLI od razresenih naslovov. Ce imena ni mogoce
     * razresiti, ga obravnavamo kot zasebnega (zavrnemo).
     */
    fun razreseniZasebni(host: String?): Boolean {
        val h = normaliziraj(host) ?: return true
        if (jeLokalniAliZasebni(h)) return true
        return try {
            val naslovi = razresi(h)
            naslovi.isEmpty() || naslovi.any { jeZasebenNaslov(it) }
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Preveri, ce je preusmeritev dovoljena:
     * - samo https (http samo, ce je bil ze prvotni naslov http in isti gostitelj)
     * - brez zasebnih/lokalnih naslovov za javne strani (ce stari ni bil zaseben, novi ne sme biti; po razresenih naslovih)
     */
    fun dovoljen(stariNaslov: String, noviNaslov: String): Boolean {
        return try {
            val stari = URL(stariNaslov)
            val novi = URL(noviNaslov)

            if (novi.protocol != "https") {
                if (novi.protocol != "http") return false
                if (stari.protocol != "http") return false
                if (normaliziraj(stari.host) != normaliziraj(novi.host)) return false
            }

            if (!razreseniZasebni(stari.host) && razreseniZasebni(novi.host)) {
                return false
            }

            true
        } catch (_: Exception) {
            false
        }
    }
}
