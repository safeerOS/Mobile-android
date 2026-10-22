package com.safeer.mobile.browser

import android.content.Context
import android.net.http.SslCertificate
import android.os.Build
import android.webkit.WebView
import java.io.ByteArrayInputStream
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal
import okhttp3.OkHttpClient

/**
 * Zaščita pred kompromitiranimi vmesnimi overitelji (Intermediate CAs) eMudhra.
 *
 * Ozadje (oktober 2026):
 * eMudhra je preklicala 8 vmesnih overiteljev (z navzkrižnimi podpisi Sectigo skupaj 16 potrdil),
 * katerih zasebne ključe je mogoče pridobiti, kar napadalcem omogoča ponarejanje veljavnih
 * potrdil za katerokoli spletno mesto. Firefox, Chrome in Apple so jih blokirali v svojih
 * shrambah; Android WebView pa se zanaša na sistemsko shrambo naprav, zato je ranljiv.
 *
 * Ta razred zagotavlja:
 * 1. Popoln seznam vseh 16 kompromitiranih potrdil (Subject DN, SHA-256 certifikatov in SPKI).
 * 2. Preverjanje v WebView: preverba teče po prejemu strani (WebView certifikat pokaže šele, ko je odgovor
 *    že prispel), zato je to le delna zaščita; nato ustavitev nalaganja in opozorilo brez obvoza.
 * 3. Lasten varnostni TrustManager ovoj za HttpsURLConnection in OkHttp klice aplikacije.
 */
object EmudhraRevocations {

    data class CompromisedCa(
        val name: String,
        val usage: String,
        val issuedBy: String,
        val subject: String,
        val issuer: String,
        val sha256: String,
        val spkiSha256: String
    )

    /**
     * Vseh 16 kompromitiranih vmesnih potrdil eMudhra (8 neposrednih eMudhra + 8 navzkrižno podpisanih s Sectigo AAA).
     */
    val VSI_KOMPROMITIRANI: List<CompromisedCa> = listOf(
        // 1. EM DV TLS CA - G3B (eMudhra)
        CompromisedCa(
            name = "EM DV TLS CA - G3B",
            usage = "tls",
            issuedBy = "emSign Root TLS CA - G3",
            subject = "CN=EM DV TLS CA - G3B,O=eMudhra BV,C=NL",
            issuer = "CN=emSign Root TLS CA - G3,O=eMudhra Technologies Limited,C=IN",
            sha256 = "42c4accd12c4f59597925e1a08a7a90102608543809308d50ec427e118ba1493",
            spkiSha256 = "ebed6475c2c2d80fcb199f3fe8dd6e6b3ce60b77eb15d4e42c3dec0c6f5b5105"
        ),
        // 2. EM DV TLS CA - G3B (Sectigo cross-sign)
        CompromisedCa(
            name = "EM DV TLS CA - G3B",
            usage = "tls",
            issuedBy = "AAA Certificate Services",
            subject = "CN=EM DV TLS CA - G3B,O=eMudhra BV,C=NL",
            issuer = "CN=AAA Certificate Services,O=Comodo CA Limited,L=Salford,ST=Greater Manchester,C=GB",
            sha256 = "45a883f349aedfd5f3a649b5498ffb121712ba8b448f3f2a91dbc5a766fcddbe",
            spkiSha256 = "ebed6475c2c2d80fcb199f3fe8dd6e6b3ce60b77eb15d4e42c3dec0c6f5b5105"
        ),
        // 3. EM OV TLS CA - G3B (eMudhra)
        CompromisedCa(
            name = "EM OV TLS CA - G3B",
            usage = "tls",
            issuedBy = "emSign Root TLS CA - G3",
            subject = "CN=EM OV TLS CA - G3B,O=eMudhra BV,C=NL",
            issuer = "CN=emSign Root TLS CA - G3,O=eMudhra Technologies Limited,C=IN",
            sha256 = "98b896625c0c230adeec3eb2cf6aad5001ecf9156a68644e82374ce5833bc25b",
            spkiSha256 = "43bffa19d6ef651451f6841e019931e96f6f2eaeff28ab4b6f1ce4576de2717b"
        ),
        // 4. EM OV TLS CA - G3B (Sectigo cross-sign)
        CompromisedCa(
            name = "EM OV TLS CA - G3B",
            usage = "tls",
            issuedBy = "AAA Certificate Services",
            subject = "CN=EM OV TLS CA - G3B,O=eMudhra BV,C=NL",
            issuer = "CN=AAA Certificate Services,O=Comodo CA Limited,L=Salford,ST=Greater Manchester,C=GB",
            sha256 = "5f1ded4dd590af74631290cf073b122a2aac6a1fa479778a0bd43579bd832699",
            spkiSha256 = "43bffa19d6ef651451f6841e019931e96f6f2eaeff28ab4b6f1ce4576de2717b"
        ),
        // 5. EM EV TLS CA - G3B (eMudhra)
        CompromisedCa(
            name = "EM EV TLS CA - G3B",
            usage = "tls",
            issuedBy = "emSign Root TLS CA - G3",
            subject = "CN=EM EV TLS CA - G3B,O=eMudhra BV,C=NL",
            issuer = "CN=emSign Root TLS CA - G3,O=eMudhra Technologies Limited,C=IN",
            sha256 = "e7568fe6e12331f4fd7de61da4c7559d0f8eb192a218d4741a3ba75184333838",
            spkiSha256 = "e0254c7fcdc265e2f0f8a428dabb75ab4ae039b4872d62f5ee57f0877d5a8a41"
        ),
        // 6. EM EV TLS CA - G3B (Sectigo cross-sign)
        CompromisedCa(
            name = "EM EV TLS CA - G3B",
            usage = "tls",
            issuedBy = "AAA Certificate Services",
            subject = "CN=EM EV TLS CA - G3B,O=eMudhra BV,C=NL",
            issuer = "CN=AAA Certificate Services,O=Comodo CA Limited,L=Salford,ST=Greater Manchester,C=GB",
            sha256 = "579480aea166290bd3c643589e795fc0d0e54e86505d180f1dff99efd3199512",
            spkiSha256 = "e0254c7fcdc265e2f0f8a428dabb75ab4ae039b4872d62f5ee57f0877d5a8a41"
        ),
        // 7. EM DV TLS CA - G3C (eMudhra)
        CompromisedCa(
            name = "EM DV TLS CA - G3C",
            usage = "tls",
            issuedBy = "emSign Root TLS CA - G3",
            subject = "CN=EM DV TLS CA - G3C,O=eMudhra Inc,C=US",
            issuer = "CN=emSign Root TLS CA - G3,O=eMudhra Technologies Limited,C=IN",
            sha256 = "0164d7ceae5bb909c272af151ff44b1177ae9b47d9424b94f1d9558c30934d19",
            spkiSha256 = "49dd353dc2b919a3770645117d0e60c16115e7d1bc259eefe734b00ea434fe5f"
        ),
        // 8. EM DV TLS CA - G3C (Sectigo cross-sign)
        CompromisedCa(
            name = "EM DV TLS CA - G3C",
            usage = "tls",
            issuedBy = "AAA Certificate Services",
            subject = "CN=EM DV TLS CA - G3C,O=eMudhra Inc,C=US",
            issuer = "CN=AAA Certificate Services,O=Comodo CA Limited,L=Salford,ST=Greater Manchester,C=GB",
            sha256 = "62b7f9868263f72f6911fc78ceaf72c1979158b7368bf648683bb2aa39b02e72",
            spkiSha256 = "49dd353dc2b919a3770645117d0e60c16115e7d1bc259eefe734b00ea434fe5f"
        ),
        // 9. EM OV TLS CA - G3C (eMudhra)
        CompromisedCa(
            name = "EM OV TLS CA - G3C",
            usage = "tls",
            issuedBy = "emSign Root TLS CA - G3",
            subject = "CN=EM OV TLS CA - G3C,O=eMudhra Inc,C=US",
            issuer = "CN=emSign Root TLS CA - G3,O=eMudhra Technologies Limited,C=IN",
            sha256 = "cb6e15165ac802e9b3f993f16c25d41f4e81c21ebde71ae1b012ac00bf9c7c3f",
            spkiSha256 = "63ecefee431d49523bc1136c4e72d0206a477086caf606cd5cf025686c109cf8"
        ),
        // 10. EM OV TLS CA - G3C (Sectigo cross-sign)
        CompromisedCa(
            name = "EM OV TLS CA - G3C",
            usage = "tls",
            issuedBy = "AAA Certificate Services",
            subject = "CN=EM OV TLS CA - G3C,O=eMudhra Inc,C=US",
            issuer = "CN=AAA Certificate Services,O=Comodo CA Limited,L=Salford,ST=Greater Manchester,C=GB",
            sha256 = "7485b51c0ed192878fa1d2f948fb529ae1842cc5086c4b67f7be5b2039de9489",
            spkiSha256 = "63ecefee431d49523bc1136c4e72d0206a477086caf606cd5cf025686c109cf8"
        ),
        // 11. EM EV TLS CA - G3C (eMudhra)
        CompromisedCa(
            name = "EM EV TLS CA - G3C",
            usage = "tls",
            issuedBy = "emSign Root TLS CA - G3",
            subject = "CN=EM EV TLS CA - G3C,O=eMudhra Inc,C=US",
            issuer = "CN=emSign Root TLS CA - G3,O=eMudhra Technologies Limited,C=IN",
            sha256 = "432ec25f0d28de9006d97bf752f17bbb2cd41f669933fcb072d9a1148d0261f3",
            spkiSha256 = "5c0660a2b164910d8ff589c18e14552e9e0ec237dcefe2cb736d41a4811f81a0"
        ),
        // 12. EM EV TLS CA - G3C (Sectigo cross-sign)
        CompromisedCa(
            name = "EM EV TLS CA - G3C",
            usage = "tls",
            issuedBy = "AAA Certificate Services",
            subject = "CN=EM EV TLS CA - G3C,O=eMudhra Inc,C=US",
            issuer = "CN=AAA Certificate Services,O=Comodo CA Limited,L=Salford,ST=Greater Manchester,C=GB",
            sha256 = "fd9d5c2dbbd4995ab69b8d50c439d9527b00529199c2fd01b2174eb58f07021f",
            spkiSha256 = "5c0660a2b164910d8ff589c18e14552e9e0ec237dcefe2cb736d41a4811f81a0"
        ),
        // 13. EM OV CS CA - G3B (eMudhra)
        CompromisedCa(
            name = "EM OV CS CA - G3B",
            usage = "code-signing",
            issuedBy = "emSign Root CS CA - G3",
            subject = "CN=EM OV CS CA - G3B,O=eMudhra BV,C=NL",
            issuer = "CN=emSign Root CS CA - G3,O=eMudhra Technologies Limited,C=IN",
            sha256 = "9e39de2881273acc225a149c72ba3e0f51424963669ab633ee1dc7df5db067b4",
            spkiSha256 = "ec4ffa1ad0ac88081314b7aaffef6a91f97cd57291bb5ba686c5415f5eb4f6b6"
        ),
        // 14. EM OV CS CA - G3B (Sectigo cross-sign)
        CompromisedCa(
            name = "EM OV CS CA - G3B",
            usage = "code-signing",
            issuedBy = "AAA Certificate Services",
            subject = "CN=EM OV CS CA - G3B,O=eMudhra BV,C=NL",
            issuer = "CN=AAA Certificate Services,O=Comodo CA Limited,L=Salford,ST=Greater Manchester,C=GB",
            sha256 = "a496e23ac0cab8155fc9b117b97eae368efcbb9041aec8804d5a308d2d01c2d8",
            spkiSha256 = "ec4ffa1ad0ac88081314b7aaffef6a91f97cd57291bb5ba686c5415f5eb4f6b6"
        ),
        // 15. EM OV CS CA - G3C (eMudhra)
        CompromisedCa(
            name = "EM OV CS CA - G3C",
            usage = "code-signing",
            issuedBy = "emSign Root CS CA - G3",
            subject = "CN=EM OV CS CA - G3C,O=eMudhra Inc,C=US",
            issuer = "CN=emSign Root CS CA - G3,O=eMudhra Technologies Limited,C=IN",
            sha256 = "4b2460a466f95fd40113bcdcca2ceb29b3b093921de45474da7df48874c630b1",
            spkiSha256 = "92c063d67e8f14db71d288e11266ffdaf69cbf115f7bab14f3bc2f68d25e0e30"
        ),
        // 16. EM OV CS CA - G3C (Sectigo cross-sign)
        CompromisedCa(
            name = "EM OV CS CA - G3C",
            usage = "code-signing",
            issuedBy = "AAA Certificate Services",
            subject = "CN=EM OV CS CA - G3C,O=eMudhra Inc,C=US",
            issuer = "CN=AAA Certificate Services,O=Comodo CA Limited,L=Salford,ST=Greater Manchester,C=GB",
            sha256 = "84112972b8e59d14560ae7e7119654474aad119990cf3722bfc1e3b91615e1fd",
            spkiSha256 = "92c063d67e8f14db71d288e11266ffdaf69cbf115f7bab14f3bc2f68d25e0e30"
        )
    )

    /** Vseh 16 SHA-256 prstnih odtisov (male črke, hex). */
    val KOMPROMITIRANI_SHA256: Set<String> = VSI_KOMPROMITIRANI.map { it.sha256.lowercase() }.toSet()

    /** 8 edinstvenih SPKI (javni ključi) odtisov kompromitiranih CA. */
    val KOMPROMITIRANI_SPKI_SHA256: Set<String> = VSI_KOMPROMITIRANI.map { it.spkiSha256.lowercase() }.toSet()

    /** 8 X500Principal subjektov kompromitiranih overiteljev. */
    val KOMPROMITIRANI_SUBJECT_PRINCIPALS: Set<X500Principal> = VSI_KOMPROMITIRANI.map { X500Principal(it.subject) }.toSet()

    /** 8 imen (CN) kompromitiranih overiteljev za hitro ujemanje. */
    val KOMPROMITIRANI_CNS: Set<String> = setOf(
        "EM DV TLS CA - G3B",
        "EM OV TLS CA - G3B",
        "EM EV TLS CA - G3B",
        "EM OV CS CA - G3B",
        "EM DV TLS CA - G3C",
        "EM OV TLS CA - G3C",
        "EM EV TLS CA - G3C",
        "EM OV CS CA - G3C"
    )

    // =========================================================================
    // Kriptografski in verifikacijski pomočniki
    // =========================================================================

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun jeKompromitiranSha256(sha256Hex: String): Boolean =
        sha256Hex.lowercase(Locale.ROOT) in KOMPROMITIRANI_SHA256

    fun jeKompromitiranSpkiSha256(spkiHex: String): Boolean =
        spkiHex.lowercase(Locale.ROOT) in KOMPROMITIRANI_SPKI_SHA256

    fun jeKompromitiranSubject(principal: X500Principal): Boolean =
        principal in KOMPROMITIRANI_SUBJECT_PRINCIPALS

    fun jeKompromitiranIzdajatelj(issuerPrincipal: X500Principal): Boolean =
        issuerPrincipal in KOMPROMITIRANI_SUBJECT_PRINCIPALS

    /**
     * Preveri ime izdajatelja (Common Name in polno DName) za primer, ko potrdilo prihaja
     * iz Android SslCertificate.DName.
     */
    fun jeKompromitiranIzdajatelj(cName: String?, dName: String?): Boolean {
        if (cName != null && cName in KOMPROMITIRANI_CNS) {
            return true
        }
        if (dName != null) {
            try {
                if (X500Principal(dName) in KOMPROMITIRANI_SUBJECT_PRINCIPALS) return true
            } catch (_: Throwable) {}
            for (cn in KOMPROMITIRANI_CNS) {
                if (dName.contains(cn, ignoreCase = true) &&
                    (dName.contains("eMudhra", ignoreCase = true) || dName.contains("eMudhra BV", ignoreCase = true) || dName.contains("eMudhra Inc", ignoreCase = true))
                ) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Preveri posamezno X.509 potrdilo:
     * - ali je njegov lastni odtis med 16 kompromitiranimi
     * - ali je njegov javni ključ (SPKI) med kompromitiranimi
     * - ali je njegov subjekt eden od kompromitiranih CA
     */
    fun jeKompromitiranoPotrdilo(cert: X509Certificate): Boolean {
        try {
            val fp = sha256Hex(cert.encoded)
            if (jeKompromitiranSha256(fp)) return true
        } catch (_: Throwable) {}

        try {
            val spkiFp = sha256Hex(cert.publicKey.encoded)
            if (jeKompromitiranSpkiSha256(spkiFp)) return true
        } catch (_: Throwable) {}

        try {
            if (jeKompromitiranSubject(cert.subjectX500Principal)) return true
        } catch (_: Throwable) {}

        return false
    }

    /**
     * Preveri celotno TLS verigo:
     * Če veriga vsebuje katerokoli kompromitirano potrdilo (po SHA-256 ali SPKI)
     * ali je bil list izdan s strani kompromitiranega overitelja, sproži [CertificateException].
     */
    @Throws(CertificateException::class)
    fun preveriVerigo(chain: Array<out X509Certificate>?) {
        if (chain.isNullOrEmpty()) return

        for (cert in chain) {
            // 1. Preveri, ali je to potrdilo samo kompromitiran vmesni CA
            if (jeKompromitiranoPotrdilo(cert)) {
                throw CertificateException(
                    "Safeer varnostna blokada: potrdilo v verigi (${cert.subjectX500Principal}) je kompromitirano vmesno potrdilo eMudhra."
                )
            }
            // 2. Preveri, ali je bil izdajatelj kateregakoli potrdila v verigi eden od kompromitiranih overiteljev
            if (jeKompromitiranIzdajatelj(cert.issuerX500Principal)) {
                throw CertificateException(
                    "Safeer varnostna blokada: potrdilo (${cert.subjectX500Principal}) je izdal kompromitiran vmesni overitelj eMudhra (${cert.issuerX500Principal})."
                )
            }
        }
    }

    // =========================================================================
    // Android WebView podpora (SslCertificate)
    // =========================================================================

    /**
     * Pretvori Androidov [SslCertificate] v standardni [X509Certificate].
     * Na API 29+ uporablja [SslCertificate.getX509Certificate], na starejših pa [SslCertificate.saveState].
     */
    fun toX509Certificate(cert: SslCertificate): X509Certificate? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                cert.x509Certificate?.let { return it }
            } catch (_: Throwable) {}
        }
        return try {
            val bundle = SslCertificate.saveState(cert)
            val bytes = bundle?.getByteArray("x509-certificate") ?: return null
            val cf = CertificateFactory.getInstance("X.509")
            cf.generateCertificate(ByteArrayInputStream(bytes)) as? X509Certificate
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Preveri, ali je izdajatelj lista v [SslCertificate] eden od kompromitiranih eMudhra overiteljev.
     */
    fun jeKompromitiranSslCertificate(cert: SslCertificate?): Boolean {
        if (cert == null) return false

        // 1. Najbolj natančno: preko X509Certificate (API 29+ ali saveState)
        val x509 = toX509Certificate(cert)
        if (x509 != null) {
            if (jeKompromitiranoPotrdilo(x509)) return true
            if (jeKompromitiranIzdajatelj(x509.issuerX500Principal)) return true
        }

        // 2. Preverba preko SslCertificate.DName
        val issuedBy = cert.issuedBy ?: return false
        return jeKompromitiranIzdajatelj(issuedBy.cName, issuedBy.dName)
    }

    /** Naslov vgrajene strani, ki jo vrne gumb »Nazaj na varno«. */
    const val DOMACA_STRAN = "file:///android_asset/brave_home.html"

    /** URL, ki ga sproži gumb »Nazaj na varno« na opozorilni strani. */
    const val NAZAJ_NA_VARNO_URL = "safeer://nazaj-na-varno"

    private const val OPOZORILNA_STRAN_URL = "safeer://compromised-ca"

    private val blokiraniGostitelji: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet())

    /** Ali je bil v tem zagonu že blokiran kakšen gostitelj (potem predpomnilnik ne sme več streči strani). */
    fun imaBlokiraneGostitelje(): Boolean = blokiraniGostitelji.isNotEmpty()

    fun blokirajGostitelja(host: String) {
        blokiraniGostitelji.add(host.lowercase(Locale.ROOT))
    }

    fun jeBlokiranGostitelj(host: String?): Boolean =
        !host.isNullOrEmpty() && blokiraniGostitelji.contains(host.lowercase(Locale.ROOT))

    fun jeOpozorilnaStran(url: String?): Boolean =
        url?.startsWith(OPOZORILNA_STRAN_URL, ignoreCase = true) == true

    /** Naslov za naslovno vrstico: pri opozorilni strani izvirni URL blokirane strani, sicer nespremenjen [url]. */
    fun prikazniNaslov(url: String, zastavica: ZastavicaOpozorila): String =
        if (jeOpozorilnaStran(url)) zastavica.izvirniUrl ?: url else url

    private fun gostiteljIz(url: String?): String? = try {
        java.net.URI(url ?: "").host?.lowercase(Locale.ROOT)
    } catch (_: Throwable) {
        null
    }

    /**
     * Zastavica ene navigacije v enem zavihku: opozorilo se za isto navigacijo (isti URL in števec)
     * pokaže in naloži le enkrat, čeprav ga sprožijo različni povratni klici WebViewClient.
     */
    class ZastavicaOpozorila {
        private var stevecNavigacije = 0
        private var zadnjiKljuc: String? = null

        /** Izvirni URL blokirane strani: naslovna vrstica kaže njega, ne notranjega naslova opozorilne strani. */
        @Volatile
        var izvirniUrl: String? = null
            private set

        @Synchronized
        fun zabeleziBlokiraniUrl(url: String) {
            izvirniUrl = url
        }

        @Synchronized
        fun novaNavigacija() {
            stevecNavigacije++
        }

        /** Vrne true samo ob prvem klicu za isto navigacijo in isti URL. */
        @Synchronized
        fun prviZaNavigacijo(url: String): Boolean {
            val kljuc = "$stevecNavigacije|$url"
            if (kljuc == zadnjiKljuc) return false
            zadnjiKljuc = kljuc
            return true
        }
    }

    /**
     * Izbere vnos zgodovine za gumb »Nazaj na varno«: najbližji prejšnji vnos, ki ni opozorilna stran
     * in ne pripada blokiranemu gostitelju. Vrne zamik za goBackOrForward ali null (potem domača stran).
     */
    fun zamikNazajNaVarno(urlji: List<String>, trenutni: Int): Int? {
        var i = minOf(trenutni, urlji.size) - 1
        while (i >= 0) {
            val u = urlji[i]
            if (!jeOpozorilnaStran(u) && !jeBlokiranGostitelj(gostiteljIz(u))) return i - trenutni
            i--
        }
        return null
    }

    /** Gumb »Nazaj na varno«: nazaj mimo blokiranih gostiteljev, če jih ni, domača stran. */
    fun nazajNaVarno(view: WebView) {
        val seznam = view.copyBackForwardList()
        val urlji = (0 until seznam.size).map { seznam.getItemAtIndex(it).url ?: "" }
        val zamik = zamikNazajNaVarno(urlji, seznam.currentIndex)
        if (zamik != null) view.goBackOrForward(zamik) else view.loadUrl(DOMACA_STRAN)
    }

    /**
     * Preveri certifikat v WebView (po prejemu strani, torej delna zaščita). Če je kompromitiran:
     * - ustavi nalaganje ([WebView.stopLoading]),
     * - zamenja vsebino z varnostnim opozorilom v jeziku uporabnika (brez možnosti nadaljevanja),
     * - prikaže sistemsko opozorilno okno,
     * - vrne true (kar pomeni, da je bilo nalaganje blokirano).
     * Opozorilo in naložitev opozorilne strani se za isto navigacijo zgodita le enkrat ([zastavica]).
     */
    fun obravnavajKompromitiranCa(view: WebView, url: String?, context: Context, zastavica: ZastavicaOpozorila): Boolean {
        val cert = view.certificate ?: return false
        if (!jeKompromitiranSslCertificate(cert)) return false

        val safeUrl = url ?: ""
        // Prepreči rekurzivno blokiranje lastne opozorilne strani
        if (jeOpozorilnaStran(safeUrl)) return true

        // Isto navigacijo so že obravnavali drugi povratni klici
        if (!zastavica.prviZaNavigacijo(safeUrl)) return true

        zastavica.zabeleziBlokiraniUrl(safeUrl)
        android.util.Log.e("SafeerSecurity", "🛑 BLOKIRANO eMudhra: Kompromitiran vmesni overitelj za $safeUrl")

        // 1. Takoj ustavi vsa mrežna nalaganja
        view.stopLoading()

        val host = try {
            java.net.URI(safeUrl).host?.ifEmpty { null } ?: safeUrl
        } catch (_: Throwable) {
            safeUrl
        }

        // Blokiran gostitelj: do ponovnega zagona brez predpomnilnika
        blokirajGostitelja(host)
        view.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE

        val issuerName = cert.issuedBy?.cName ?: "eMudhra Intermediate CA"

        // 2. Pripravi in naloži varnostno opozorilno stran (brez možnosti nadaljevanja)
        val html = ustvariOpozorilnoStran(context, host, safeUrl, issuerName)
        view.loadDataWithBaseURL(OPOZORILNA_STRAN_URL, html, "text/html", "UTF-8", null)

        // 3. Prikaži modalni dialog
        val act = context as? android.app.Activity
        act?.runOnUiThread {
            try {
                val title = niz(context, "ssl_compromised_ca_title")
                val msg = niz(context, "ssl_compromised_ca_msg", host, issuerName)
                val okText = try {
                    val resId = context.resources.getIdentifier("dialog_ok", "string", context.packageName)
                    if (resId != 0) context.getString(resId) else if (niz(context, "lang") == "sl") "V redu" else "OK"
                } catch (_: Throwable) {
                    "V redu"
                }

                android.app.AlertDialog.Builder(context)
                    .setTitle(title)
                    .setMessage(msg)
                    .setPositiveButton(okText, null)
                    .setCancelable(false)
                    .show()
            } catch (_: Throwable) {}
        }

        return true
    }

    /**
     * Vrne lokalizirano besedilo v slovenščini ali angleščini (glede na nastavitve aplikacije).
     */
    fun niz(context: Context, kljuc: String, vararg params: Any): String {
        val lang = try {
            val prefLang = context.getSharedPreferences("safeer_browser_prefs", 0).getString("pref_language", "auto")
            if (prefLang != null && prefLang != "auto") {
                prefLang.lowercase(Locale.ROOT)
            } else {
                Locale.getDefault().language.lowercase(Locale.ROOT)
            }
        } catch (_: Throwable) {
            Locale.getDefault().language.lowercase(Locale.ROOT)
        }
        val isSl = lang.startsWith("sl")
        val raw = if (isSl) {
            when (kljuc) {
                "ssl_compromised_ca_title" -> "⚠️ Varnostno opozorilo: kompromitiran overitelj (eMudhra)"
                "ssl_compromised_ca_msg" -> "Potrdilo te strani je izdal overitelj, ki je bil preklican zaradi kompromitiranih ključev. Strani zato ne odpremo."
                "ssl_compromised_ca_badge" -> "🔒 Iz varnostnih razlogov te strani ni mogoče odpreti."
                "ssl_compromised_ca_btn_back" -> "Nazaj na varno"
                else -> kljuc
            }
        } else {
            when (kljuc) {
                "ssl_compromised_ca_title" -> "⚠️ Security Warning: Compromised CA (eMudhra)"
                "ssl_compromised_ca_msg" -> "This site's certificate was issued by a certificate authority that has been revoked because of compromised keys. We therefore do not open the site."
                "ssl_compromised_ca_badge" -> "🔒 For security reasons this page cannot be opened."
                "ssl_compromised_ca_btn_back" -> "Back to Safety"
                else -> kljuc
            }
        }
        return if (params.isNotEmpty()) {
            try { String.format(Locale.getDefault(), raw, *params) } catch (_: Throwable) { raw }
        } else raw
    }

    /**
     * Ustvari HTML stran varnostnega opozorila v Safeer temnem dizajnu brez možnosti nadaljevanja.
     */
    fun ustvariOpozorilnoStran(context: Context, host: String, url: String, issuerName: String): String {
        val title = niz(context, "ssl_compromised_ca_title")
        val hostHtml = android.text.TextUtils.htmlEncode(host)
        val urlHtml = android.text.TextUtils.htmlEncode(url)
        val msg = android.text.TextUtils.htmlEncode(niz(context, "ssl_compromised_ca_msg", host, issuerName))
            .replace("\n\n", "<br><br>")
        val badge = niz(context, "ssl_compromised_ca_badge")
        val backBtn = niz(context, "ssl_compromised_ca_btn_back")

        return """
        <!DOCTYPE html>
        <html lang="sl">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <title>$title</title>
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                    background-color: #050508;
                    color: #e5e5e5;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                    min-height: 100vh;
                    display: flex;
                    flex-direction: column;
                    justify-content: center;
                    align-items: center;
                    padding: 24px;
                    text-align: center;
                }
                .card {
                    background: rgba(26, 10, 14, 0.92);
                    border: 1px solid rgba(255, 68, 68, 0.45);
                    border-radius: 20px;
                    padding: 32px 24px;
                    max-width: 500px;
                    width: 100%;
                    box-shadow: 0 10px 40px rgba(255, 0, 0, 0.35);
                    backdrop-filter: blur(12px);
                }
                .icon {
                    width: 72px;
                    height: 72px;
                    margin: 0 auto 20px;
                    background: rgba(255, 68, 68, 0.18);
                    border: 2px solid #ff4444;
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 36px;
                }
                h1 {
                    font-size: 20px;
                    font-weight: 700;
                    color: #ff5555;
                    margin-bottom: 14px;
                    line-height: 1.3;
                }
                .domain-badge {
                    display: inline-block;
                    background: rgba(255, 255, 255, 0.08);
                    padding: 6px 14px;
                    border-radius: 12px;
                    font-family: monospace;
                    font-size: 15px;
                    color: #fff;
                    margin-bottom: 18px;
                    word-break: break-all;
                }
                p {
                    font-size: 14px;
                    line-height: 1.6;
                    color: #bbb;
                    margin-bottom: 24px;
                    text-align: left;
                }
                .zero-bypass {
                    margin-bottom: 24px;
                    padding: 12px 16px;
                    background: rgba(255, 68, 68, 0.12);
                    border: 1px solid rgba(255, 68, 68, 0.35);
                    border-radius: 12px;
                    font-size: 13px;
                    color: #ff8888;
                    text-align: center;
                    line-height: 1.4;
                    font-weight: 600;
                }
                .btn {
                    display: block;
                    width: 100%;
                    padding: 14px;
                    border-radius: 12px;
                    font-size: 15px;
                    font-weight: 600;
                    text-decoration: none;
                    cursor: pointer;
                    transition: all 0.2s ease;
                    border: none;
                    background: #2563eb;
                    color: #ffffff;
                }
                .btn:active {
                    transform: scale(0.98);
                    opacity: 0.9;
                }
            </style>
        </head>
        <body>
            <div class="card">
                <div class="icon">🛑</div>
                <h1>$title</h1>
                <div class="domain-badge">$hostHtml</div>
                <p>$msg</p>
                <div class="zero-bypass">$badge</div>
                <button class="btn" onclick="location.href='$NAZAJ_NA_VARNO_URL'">$backBtn</button>
                <div style="font-size: 11px; color: #777; margin-top: 16px; word-break: break-all;">$urlHtml</div>
            </div>
        </body>
        </html>
        """.trimIndent()
    }

    // =========================================================================
    // TrustManager ovoj za HTTPS klicatelje (OkHttp & HttpsURLConnection)
    // =========================================================================

    /**
     * Vrne privzetega sistemskega zaupnika [X509TrustManager].
     */
    fun defaultTrustManager(): X509TrustManager {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        return tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    /**
     * Safeer TrustManager ovoj:
     * Ob vsakem klicu [checkServerTrusted] preveri, da veriga ne vsebuje nobenega od
     * kompromitiranih eMudhra potrdil (po SHA-256, SPKI ali Subject DN), nato pa
     * preverjanje prepusti sistemskemu zaupniku.
     */
    open class SafeerTrustManager(
        val delegate: X509TrustManager = defaultTrustManager()
    ) : X509ExtendedTrustManager() {

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            delegate.checkClientTrusted(chain, authType)
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
            if (delegate is X509ExtendedTrustManager) {
                delegate.checkClientTrusted(chain, authType, socket)
            } else {
                delegate.checkClientTrusted(chain, authType)
            }
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
            if (delegate is X509ExtendedTrustManager) {
                delegate.checkClientTrusted(chain, authType, engine)
            } else {
                delegate.checkClientTrusted(chain, authType)
            }
        }

        @Throws(CertificateException::class)
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            preveriVerigo(chain)
            delegate.checkServerTrusted(chain, authType)
        }

        @Throws(CertificateException::class)
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
            preveriVerigo(chain)
            if (delegate is X509ExtendedTrustManager) {
                delegate.checkServerTrusted(chain, authType, socket)
            } else {
                delegate.checkServerTrusted(chain, authType)
            }
        }

        @Throws(CertificateException::class)
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
            preveriVerigo(chain)
            if (delegate is X509ExtendedTrustManager) {
                delegate.checkServerTrusted(chain, authType, engine)
            } else {
                delegate.checkServerTrusted(chain, authType)
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            delegate.acceptedIssuers ?: emptyArray()
    }

    /**
     * Ustvari nov [SafeerTrustManager] ovoj.
     */
    fun ustvariTrustManager(delegate: X509TrustManager? = null): SafeerTrustManager =
        SafeerTrustManager(delegate ?: defaultTrustManager())

    /**
     * Ustvari [SSLSocketFactory] in pripadajoči zaupnik z zaščito Safeer.
     */
    fun ustvariSslSocketFactory(trustManager: X509TrustManager = ustvariTrustManager()): Pair<SSLSocketFactory, X509TrustManager> {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), SecureRandom())
        return sslContext.socketFactory to trustManager
    }

    /**
     * Zaščiti [okhttp3.OkHttpClient.Builder] pred kompromitiranimi eMudhra potrdili.
     */
    fun zascitiOkHttp(builder: OkHttpClient.Builder, trustManager: X509TrustManager = ustvariTrustManager()): OkHttpClient.Builder {
        val (factory, tm) = ustvariSslSocketFactory(trustManager)
        return builder.sslSocketFactory(factory, tm)
    }

    /**
     * Nastavi [HttpsURLConnection.setDefaultSSLSocketFactory] na varen Safeer SSLSocketFactory,
     * ki ščiti vse privzete HTTPS klice v aplikaciji.
     */
    @Synchronized
    fun vklopiZaHttpsURLConnection() {
        try {
            val (factory, _) = ustvariSslSocketFactory()
            HttpsURLConnection.setDefaultSSLSocketFactory(factory)
            android.util.Log.i("SafeerSecurity", "✅ Safeer eMudhra zaščita za HttpsURLConnection aktivirana.")
        } catch (e: Throwable) {
            android.util.Log.e("SafeerSecurity", "Napaka pri nastavitvi zaščite za HttpsURLConnection: ${e.message}")
        }
    }
}
