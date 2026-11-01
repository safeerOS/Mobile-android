package com.safeer.mobile.browser.link

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.safeer.mobile.browser.I18n
import org.json.JSONObject
import com.safeer.mobile.browser.cast.HubPairing
import com.safeer.mobile.browser.cast.HubTls
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Prijava racunalnika s QR kodo: uporabnik s kamero poskenira kodo v prijavnem oknu Safeer OS /
 * Safeer Control. Koda odpre to dejavnost (safeer://link/qr?... ali https://safeer.si/p#...).
 *
 * Ta naprava je ze v Safeer Linku, zato sme dovoliti. Pred vprasanjem preverimo, da je koda za NAS
 * Safeer Link: odtis potrdila, ki ga je videl racunalnik (v kodi), se mora ujemati z odtisom huba,
 * ki mu ta naprava zaupa. Tako koda z drugega omrezja ali od vsiljivca v sredini ne gre skozi.
 * Nic se ne zgodi brez uporabnikovega »Dovoli«.
 */
class QrPrijavaActivity : Activity() {

    companion object {
        private const val TAG = "SafeerQrPrijava"

        /** Koda, ki jo pokaze sredisce (Safeer OS na televizorju): ta naprava se mu pridruzi. */
        data class Pridruzitev(val id: String, val skrivnost: String, val odtis: String, val naslov: String)

        /** Ali je povezava katera koli koda Safeer (prijava racunalnika ali pridruzitev srediscu). */
        fun jeSafeerKoda(uri: Uri?): Boolean = razcleni(uri) != null || razcleniPridruzitev(uri) != null || jeStaraPrijava(uri)

        private fun parametri(uri: Uri?): Map<String, String>? {
            if (uri == null) return null
            return when {
                uri.scheme == "safeer" && uri.host == "link" && uri.path == "/qr" ->
                    uri.queryParameterNames.associateWith { uri.getQueryParameter(it).orEmpty() }
                uri.scheme == "https" && uri.host == "safeer.si" && (uri.path == "/p" || uri.path == "/p/") ->
                    (uri.fragment ?: "").split("&").mapNotNull {
                        val k = it.substringBefore("=", ""); if (k.isEmpty()) null else k to Uri.decode(it.substringAfter("="))
                    }.toMap()
                // Koda sredisca s stranjo spletnega odjemalca: http://<zasebni naslov>:<vrata>/#j=...
                uri.scheme == "http" && jeZasebniNaslov(uri.host) && (uri.path.isNullOrEmpty() || uri.path == "/") && !uri.fragment.isNullOrEmpty() ->
                    (uri.fragment ?: "").split("&").mapNotNull {
                        val k = it.substringBefore("=", ""); if (k.isEmpty()) null else k to Uri.decode(it.substringAfter("="))
                    }.toMap()
                else -> null
            }
        }

        /** Okteti, ne predpona niza (»10.evil.com« se zacne z »10.«) - QrPravila.jeZasebniIpv4. */
        private fun jeZasebniNaslov(h: String?): Boolean = QrPravila.jeZasebniIpv4(h)

        fun razcleniPridruzitev(uri: Uri?): Pridruzitev? {
            val p = parametri(uri) ?: return null
            val id = p["j"].orEmpty()
            val skrivnost = p["s"].orEmpty()
            val odtis = p["f"].orEmpty().lowercase()
            val naslov = p["a"].orEmpty()
            if (!Regex("^[0-9a-f]{8,64}$").matches(id) || skrivnost.length !in 16..128 ||
                !QrPravila.veljavenOdtis(odtis) || !QrPravila.zasebniNaslovSredisca(naslov)) return null
            return Pridruzitev(id, skrivnost, odtis, naslov)
        }

        /**
         * Prijava s QR kodo: qr_id, skrivnost in cel odtis potrdila. Naslov »a« iz kode za prijavo NE uporabimo:
         * zahteve z x-safeer-token gredo samo na pripeti Hub te naprave (odtis + hub_url iz nastavitev).
         */
        data class Prijava(val id: String, val skrivnost: String, val odtis: String, val naslov: String)

        fun razcleni(uri: Uri?): Prijava? = prijavaIz(uri)?.takeIf { QrPravila.veljavenOdtis(it.odtis) }

        /** Koda za prijavo v stari obliki (odtis s 16 znaki): racunalnik je treba posodobiti. */
        fun jeStaraPrijava(uri: Uri?): Boolean = prijavaIz(uri)?.let { QrPravila.starOdtis(it.odtis) } == true

        private fun prijavaIz(uri: Uri?): Prijava? {
            if (uri == null) return null
            val parametri: Map<String, String> = when {
                uri.scheme == "safeer" && uri.host == "link" && uri.path == "/qr" ->
                    uri.queryParameterNames.associateWith { uri.getQueryParameter(it).orEmpty() }
                uri.scheme == "https" && uri.host == "safeer.si" && (uri.path == "/p" || uri.path == "/p/") ->
                    (uri.fragment ?: "").split("&").mapNotNull {
                        val k = it.substringBefore("=", ""); if (k.isEmpty()) null else k to Uri.decode(it.substringAfter("="))
                    }.toMap()
                else -> return null
            }
            val id = parametri["i"].orEmpty()
            val skrivnost = parametri["s"].orEmpty()
            val odtis = parametri["f"].orEmpty().lowercase()
            val naslov = parametri["a"].orEmpty()
            if (!Regex("^[0-9a-f]{8,64}$").matches(id) || skrivnost.length !in 16..128 ||
                !(QrPravila.veljavenOdtis(odtis) || QrPravila.starOdtis(odtis)) ||
                (naslov.isNotEmpty() && !QrPravila.zasebniNaslovSredisca(naslov))) return null
            return Prijava(id, skrivnost, odtis, naslov)
        }
    }

    private val glavna = Handler(Looper.getMainLooper())
    private var qr: Prijava? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        razcleniPridruzitev(intent?.data)?.let { pridruzi(it); return }
        qr = razcleni(intent?.data)
        val koda = qr
        if (koda == null) { sporocilo(b(if (jeStaraPrijava(intent?.data)) "staraKoda" else "neveljavna")); return }
        val pripeti = HubTls.pripetiOdtis(this)
        if (HubPairing.token(this).isNullOrBlank() || naslovHuba().isBlank() || pripeti.isNullOrBlank()) {
            sporocilo(b("niPovezana")); return
        }
        // Koda mora biti za NAS pripeti Hub - sicer zavrnemo, preden karkoli poslje zeton v omrezje.
        if (!QrPravila.prijavaDovoljena(pripeti, koda.odtis)) { sporocilo(b("drugLink")); return }
        Thread {
            val (odgovor, videni) = klic("/cast/pair/qr/info", koda)
            glavna.post {
                if (isFinishing) return@post
                when {
                    odgovor == null -> sporocilo(b("niHuba"))
                    !QrPravila.odtisUstreza(videni, koda.odtis) -> sporocilo(b("drugLink"))
                    odgovor.optString("device_id").isBlank() -> sporocilo(napaka(odgovor))
                    else -> vprasaj(odgovor.optString("name").ifBlank { odgovor.optString("device_id") })
                }
            }
        }.start()
    }

    /**
     * Ta naprava se pridruzi srediscu, ki je pokazalo kodo (televizor). Povezavo safeer://link/qr lahko odpre tudi
     * spletna stran ali druga aplikacija, ne samo skeniranje (pregled 28. 10. 2026), zato vedno vprasamo - z naslovom
     * in odtisom sredisca - in posebej opozorimo, ce bi naprava zapustila Safeer Link, ki mu ze zaupa. Naslov je
     * samo zasebni (QrPravila.zasebniNaslovSredisca); odtis potrdila iz kode potrdi, da je to pravo sredisce.
     */
    private fun pridruzi(p: Pridruzitev) {
        val imaZeton = !HubPairing.token(this).isNullOrBlank()
        val pripeti = HubTls.pripetiOdtis(this)
        if (imaZeton && pripeti.equals(p.odtis, ignoreCase = true)) {
            sporocilo(b("zePovezana")); return
        }
        val besedilo = b("vprasajPridruzi").replace("{naslov}", p.naslov).replace("{odtis}", QrPravila.kratekOdtis(p.odtis)) +
            (if (QrPravila.preselitev(imaZeton, pripeti, p.odtis)) "\n\n" + b("preselitev") else "")
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(b("naslovPridruzi"))
            .setMessage(besedilo)
            .setPositiveButton(b("pridruzi")) { _, _ -> povezi(p) }
            .setNegativeButton(b("ne")) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun povezi(p: Pridruzitev) {
        val cakam = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(b("naslovPridruzi")).setMessage(b("povezujem")).setCancelable(false).show()
        HubPairing.pridruziSQr(this, p.naslov, p.odtis, p.id, p.skrivnost, com.safeer.mobile.browser.cast.HubKrmilnik.lastniId(),
            applicationInfo.loadLabel(packageManager).toString() + " (" + android.os.Build.MODEL + ")") { uspelo, razlog ->
            try { cakam.dismiss() } catch (_: Throwable) { }
            if (isFinishing) return@pridruziSQr
            if (uspelo) {
                try { LinkSprejemnik.zagotovi(this) } catch (e: Throwable) { Log.w(TAG, "LinkSprejemnik se ni zagnal", e) }
                sporocilo(b("pridruzena"), b("naslovPridruzi"))
            } else sporocilo(when (razlog) {
                "qr_ne_obstaja", "prevec_poskusov" -> b("poteklaTv")
                "prevec_naprav" -> b("prevecNaprav")
                else -> b("niHuba")
            }, b("naslovPridruzi"))
        }
    }

    private fun vprasaj(ime: String) {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(b("naslov"))
            .setMessage(b("vprasanje").replace("{ime}", ime))
            .setPositiveButton(b("dovoli")) { _, _ -> dovoli(ime) }
            .setNegativeButton(b("ne")) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun dovoli(ime: String) {
        val koda = qr ?: return finish()
        if (!QrPravila.prijavaDovoljena(HubTls.pripetiOdtis(this), koda.odtis)) { sporocilo(b("drugLink")); return }
        Thread {
            val (odgovor, videni) = klic("/cast/pair/qr/approve", koda)
            glavna.post {
                if (isFinishing) return@post
                when {
                    odgovor == null -> sporocilo(b("niHuba"))
                    !QrPravila.odtisUstreza(videni, koda.odtis) -> sporocilo(b("drugLink"))
                    odgovor.optBoolean("approved") -> sporocilo(b("uspeh").replace("{ime}", ime))
                    else -> sporocilo(napaka(odgovor))
                }
            }
        }.start()
    }

    private fun napaka(o: JSONObject): String = when (o.optString("code")) {
        "prevec_poskusov", "qr_ne_obstaja" -> b("potekla")
        "prevec_naprav" -> b("prevecNaprav")
        "naprava_ni_seznanjena" -> b("niPovezana")
        else -> o.optString("detail").ifBlank { b("niHuba") }
    }

    private fun sporocilo(besedilo: String, naslov: String = b("naslov")) {
        if (isFinishing) return
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(naslov)
            .setMessage(besedilo)
            .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun naslovHuba(): String =
        getSharedPreferences(HubPairing.PREFS_NAME, Context.MODE_PRIVATE).getString("hub_url", "") ?: ""

    /**
     * POST na pripeti Hub te naprave (hub_url iz nastavitev, pripeti odtis potrdila) - nikoli na naslov iz kode,
     * ker zahteva nosi x-safeer-token. Zaupnik zahteva pripeti odtis; koda z drugim odtisom sem sploh ne pride.
     * Vrne (odgovor ali null ob napaki povezave, odtis potrdila, ki smo ga videli).
     */
    private fun klic(pot: String, koda: Prijava): Pair<JSONObject?, String?> {
        var videni: String? = null
        return try {
            val pripeti = HubTls.pripetiOdtis(this)
            if (!QrPravila.prijavaDovoljena(pripeti, koda.odtis)) return null to null
            val osnova = HubPairing.httpBase(naslovHuba())
            if (osnova.isBlank()) return null to null
            val povezava = URL(osnova + pot).openConnection() as HttpURLConnection
            if (povezava !is HttpsURLConnection) { povezava.disconnect(); return null to null }
            val (tovarna, zaupnik) = HubTls.odjemalec(pripeti)
            povezava.sslSocketFactory = tovarna
            povezava.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
            try {
                povezava.requestMethod = "POST"
                povezava.connectTimeout = 5000
                povezava.readTimeout = 8000
                povezava.setRequestProperty("x-safeer-token", HubPairing.token(this).orEmpty())
                povezava.setRequestProperty("Content-Type", "application/json")
                povezava.doOutput = true
                val telo = JSONObject().put("qr_id", koda.id).put("secret", koda.skrivnost).toString()
                povezava.outputStream.use { it.write(telo.toByteArray(Charsets.UTF_8)) }
                val status = povezava.responseCode
                videni = zaupnik.videni
                val tok = if (status >= 400) povezava.errorStream else povezava.inputStream
                val vsebina = tok?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                (try { JSONObject(vsebina) } catch (_: Exception) { JSONObject() }) to videni
            } finally {
                povezava.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Hub se ni odzval: ${e.message}")
            null to videni
        }
    }

    /** Besedila v jeziku Safeerja (I18n.getEffectiveLanguage: sl, en, de, es, fr, it; drugace anglescina). */
    private fun b(kljuc: String): String {
        val jezik = try { I18n.getEffectiveLanguage(this) } catch (_: Throwable) { "en" }
        return BESEDILA[kljuc]?.let { it[jezik] ?: it["en"] } ?: kljuc
    }

    private val BESEDILA: Map<String, Map<String, String>> = mapOf(
        "naslov" to mapOf(
            "sl" to "Prijava v Safeer OS", "en" to "Sign in to Safeer OS", "de" to "Bei Safeer OS anmelden",
            "es" to "Iniciar sesión en Safeer OS", "fr" to "Connexion à Safeer OS", "it" to "Accedi a Safeer OS"),
        "vprasanje" to mapOf(
            "sl" to "Dovoliš računalniku »{ime}« dostop do tvojih naprav v Safeer Linku?",
            "en" to "Allow the computer “{ime}” to access your devices in Safeer Link?",
            "de" to "Dem Computer „{ime}“ Zugriff auf deine Geräte in Safeer Link erlauben?",
            "es" to "¿Permitir que el ordenador «{ime}» acceda a tus dispositivos en Safeer Link?",
            "fr" to "Autoriser l’ordinateur « {ime} » à accéder à tes appareils dans Safeer Link ?",
            "it" to "Consentire al computer «{ime}» di accedere ai tuoi dispositivi in Safeer Link?"),
        "dovoli" to mapOf("sl" to "Dovoli", "en" to "Allow", "de" to "Erlauben", "es" to "Permitir", "fr" to "Autoriser", "it" to "Consenti"),
        "ne" to mapOf("sl" to "Ne", "en" to "No", "de" to "Nein", "es" to "No", "fr" to "Non", "it" to "No"),
        "uspeh" to mapOf(
            "sl" to "Računalnik »{ime}« je prijavljen.", "en" to "The computer “{ime}” is signed in.",
            "de" to "Der Computer „{ime}“ ist angemeldet.", "es" to "El ordenador «{ime}» ha iniciado sesión.",
            "fr" to "L’ordinateur « {ime} » est connecté.", "it" to "Il computer «{ime}» ha eseguito l’accesso."),
        "naslovPridruzi" to mapOf("sl" to "Safeer Link", "en" to "Safeer Link", "de" to "Safeer Link",
            "es" to "Safeer Link", "fr" to "Safeer Link", "it" to "Safeer Link"),
        "vprasajPridruzi" to mapOf(
            "sl" to "Povežem to napravo s Safeer Linkom na {naslov}?\nOdtis: {odtis}\n\nPoveži jo samo, če si to kodo pravkar poskeniral na svoji napravi.",
            "en" to "Connect this device to the Safeer Link at {naslov}?\nFingerprint: {odtis}\n\nOnly connect if you just scanned this code on your own device.",
            "de" to "Dieses Gerät mit dem Safeer Link unter {naslov} verbinden?\nFingerabdruck: {odtis}\n\nVerbinde es nur, wenn du diesen Code gerade auf deinem eigenen Gerät gescannt hast.",
            "es" to "¿Conectar este dispositivo al Safeer Link en {naslov}?\nHuella: {odtis}\n\nConéctalo solo si acabas de escanear este código en tu propio dispositivo.",
            "fr" to "Connecter cet appareil au Safeer Link à l’adresse {naslov} ?\nEmpreinte : {odtis}\n\nNe le connecte que si tu viens de scanner ce code sur ton propre appareil.",
            "it" to "Collegare questo dispositivo al Safeer Link su {naslov}?\nImpronta: {odtis}\n\nCollegalo solo se hai appena scansionato questo codice sul tuo dispositivo."),
        "preselitev" to mapOf(
            "sl" to "Pozor: ta naprava je že v drugem Safeer Linku. Če nadaljuješ, ga zapusti.",
            "en" to "Warning: this device is already in another Safeer Link. Continuing will leave it.",
            "de" to "Achtung: Dieses Gerät ist bereits in einem anderen Safeer Link. Wenn du fortfährst, verlässt es diesen.",
            "es" to "Atención: este dispositivo ya está en otro Safeer Link. Si continúas, lo abandonará.",
            "fr" to "Attention : cet appareil fait déjà partie d’un autre Safeer Link. Si tu continues, il le quittera.",
            "it" to "Attenzione: questo dispositivo fa già parte di un altro Safeer Link. Se continui, lo lascerà."),
        "pridruzi" to mapOf("sl" to "Poveži", "en" to "Connect", "de" to "Verbinden", "es" to "Conectar", "fr" to "Connecter", "it" to "Collega"),
        "povezujem" to mapOf("sl" to "Povezujem …", "en" to "Connecting …", "de" to "Verbinde …",
            "es" to "Conectando…", "fr" to "Connexion…", "it" to "Connessione in corso…"),
        "pridruzena" to mapOf(
            "sl" to "Povezano. Ta naprava je zdaj v tvojem Safeer Linku.", "en" to "Connected. This device is now in your Safeer Link.",
            "de" to "Verbunden. Dieses Gerät ist jetzt in deinem Safeer Link.", "es" to "Conectado. Este dispositivo ya está en tu Safeer Link.",
            "fr" to "Connecté. Cet appareil fait désormais partie de ton Safeer Link.", "it" to "Collegato. Questo dispositivo ora fa parte del tuo Safeer Link."),
        "zePovezana" to mapOf(
            "sl" to "Ta naprava je že povezana s tem Safeer Linkom.", "en" to "This device is already connected to this Safeer Link.",
            "de" to "Dieses Gerät ist bereits mit diesem Safeer Link verbunden.", "es" to "Este dispositivo ya está conectado a este Safeer Link.",
            "fr" to "Cet appareil est déjà connecté à ce Safeer Link.", "it" to "Questo dispositivo è già collegato a questo Safeer Link."),
        "neveljavna" to mapOf(
            "sl" to "Ta koda ni koda za prijavo v Safeer.", "en" to "This is not a Safeer sign-in code.",
            "de" to "Das ist kein Safeer-Anmeldecode.", "es" to "Este no es un código de inicio de sesión de Safeer.",
            "fr" to "Ce n’est pas un code de connexion Safeer.", "it" to "Questo non è un codice di accesso Safeer."),
        "staraKoda" to mapOf(
            "sl" to "To kodo je pokazala starejša različica Safeerja na računalniku. Posodobi Safeer na računalniku in poskeniraj novo kodo.",
            "en" to "This code was shown by an older version of Safeer on the computer. Update Safeer on the computer and scan the new code.",
            "de" to "Dieser Code stammt von einer älteren Safeer-Version auf dem Computer. Aktualisiere Safeer auf dem Computer und scanne den neuen Code.",
            "es" to "Este código lo mostró una versión antigua de Safeer en el ordenador. Actualiza Safeer en el ordenador y escanea el código nuevo.",
            "fr" to "Ce code provient d’une ancienne version de Safeer sur l’ordinateur. Mets à jour Safeer sur l’ordinateur, puis scanne le nouveau code.",
            "it" to "Questo codice proviene da una versione precedente di Safeer sul computer. Aggiorna Safeer sul computer e scansiona il nuovo codice."),
        "niPovezana" to mapOf(
            "sl" to "Ta naprava še ni v Safeer Linku. Najprej jo poveži, nato poskeniraj kodo znova.",
            "en" to "This device is not in Safeer Link yet. Connect it first, then scan the code again.",
            "de" to "Dieses Gerät ist noch nicht in Safeer Link. Verbinde es zuerst und scanne den Code dann erneut.",
            "es" to "Este dispositivo aún no está en Safeer Link. Conéctalo primero y luego vuelve a escanear el código.",
            "fr" to "Cet appareil ne fait pas encore partie de Safeer Link. Connecte-le d’abord, puis scanne à nouveau le code.",
            "it" to "Questo dispositivo non fa ancora parte di Safeer Link. Collegalo prima, poi scansiona di nuovo il codice."),
        "niHuba" to mapOf(
            "sl" to "Safeer Link se ne oglasi. Preveri, da sta napravi v istem domačem omrežju.",
            "en" to "Safeer Link is not answering. Check that both devices are on the same home network.",
            "de" to "Safeer Link antwortet nicht. Prüfe, ob beide Geräte im selben Heimnetz sind.",
            "es" to "Safeer Link no responde. Comprueba que ambos dispositivos estén en la misma red doméstica.",
            "fr" to "Safeer Link ne répond pas. Vérifie que les deux appareils sont sur le même réseau domestique.",
            "it" to "Safeer Link non risponde. Verifica che entrambi i dispositivi siano sulla stessa rete domestica."),
        "drugLink" to mapOf(
            "sl" to "Ta koda ni za tvoj Safeer Link. Prijave nisem dovolil.",
            "en" to "This code is not for your Safeer Link. Sign-in was not allowed.",
            "de" to "Dieser Code ist nicht für deinen Safeer Link. Die Anmeldung wurde nicht erlaubt.",
            "es" to "Este código no es para tu Safeer Link. No se ha permitido el inicio de sesión.",
            "fr" to "Ce code n’est pas destiné à ton Safeer Link. La connexion n’a pas été autorisée.",
            "it" to "Questo codice non è per il tuo Safeer Link. L’accesso non è stato consentito."),
        "potekla" to mapOf(
            "sl" to "Koda je potekla. Na računalniku se je pokazala nova – poskeniraj jo.",
            "en" to "The code has expired. The computer is showing a new one – scan that.",
            "de" to "Der Code ist abgelaufen. Der Computer zeigt einen neuen an – scanne diesen.",
            "es" to "El código ha caducado. El ordenador muestra uno nuevo: escanéalo.",
            "fr" to "Le code a expiré. L’ordinateur en affiche un nouveau – scanne-le.",
            "it" to "Il codice è scaduto. Il computer ne mostra uno nuovo: scansiona quello."),
        "poteklaTv" to mapOf(
            "sl" to "Koda je potekla. Naprava je že pokazala novo – poskeniraj jo.",
            "en" to "The code has expired. The device is already showing a new one – scan that.",
            "de" to "Der Code ist abgelaufen. Das Gerät zeigt bereits einen neuen an – scanne diesen.",
            "es" to "El código ha caducado. El dispositivo ya muestra uno nuevo: escanéalo.",
            "fr" to "Le code a expiré. L’appareil en affiche déjà un nouveau – scanne-le.",
            "it" to "Il codice è scaduto. Il dispositivo ne mostra già uno nuovo: scansiona quello."),
        "prevecNaprav" to mapOf(
            "sl" to "V Safeer Linku je že preveč naprav. Odstrani katero, ki je ne uporabljaš.",
            "en" to "Safeer Link already has too many devices. Remove one you no longer use.",
            "de" to "In Safeer Link sind bereits zu viele Geräte. Entferne eines, das du nicht mehr verwendest.",
            "es" to "Safeer Link ya tiene demasiados dispositivos. Elimina alguno que ya no uses.",
            "fr" to "Safeer Link compte déjà trop d’appareils. Retire-en un que tu n’utilises plus.",
            "it" to "Safeer Link ha già troppi dispositivi. Rimuovine uno che non usi più.")
    )
}
