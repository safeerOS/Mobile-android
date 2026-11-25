package com.safeer.mobile.browser

/**
 * Tedenski pregled 25. 11. 2026 (N4): pravila za lasten ponudnik datotek (MobileFileProvider), brez Androida.
 *
 * Ponudnik ni izvozen, a druga aplikacija lahko Safeerju poslje namero VIEW z NASIM naslovom
 * content://<nasa avtoriteta>/... - takrat bi Safeer sam (z vsemi pravicami) odprl svojo zasebno
 * datoteko v pregledovalniku PDF. Zato namere od zunaj z naso avtoriteto zavrnemo, ponudnik pa
 * deli samo mape, ki jih seznam prenosov res prikaze.
 */
object DeljeneDatoteke {
    const val AUTHORITY = "com.safeer.mobile.browser.fileprovider"

    /**
     * Ali naslov (shema in ze dekodirana avtoriteta iz android.net.Uri) kaze na nas ponudnik.
     * ContentResolver predpono uporabnika (`0@`) odstrani, zato jo odstranimo tudi tu.
     */
    fun jeLastna(shema: String?, avtoriteta: String?): Boolean {
        if (!shema.equals("content", ignoreCase = true) || avtoriteta == null) return false
        return avtoriteta.substringAfterLast('@').trim().equals(AUTHORITY, ignoreCase = true)
    }

    /** Domaca stran; Safeer Link (Daljinec »home«) jo odpre z eksplicitno namero VIEW na MainActivity. */
    const val DOMACA_STRAN = "file:///android_asset/brave_home.html"

    /**
     * MOB-FILE (25. 11. 2026): ali naslov namere VIEW od zunaj sme v zavihek. MainActivity je izvozena,
     * zato eksplicitna namera obide filtre - pravilo je dovoljeni seznam, ne filter manifesta.
     * file:// je zavrnjen v celoti: tuja aplikacija bi z njim Safeerja pripravila, da odpre svojo
     * zasebno datoteko (/data/data, /data/user/0, `..`), aplikacije s targetSdk >= 24 pa file:// privzeto
     * ne morejo poslati (StrictMode: FileUriExposedException). Izjema je samo natanko domaca stran (brez `..`).
     */
    fun smeOdZunaj(shema: String?, avtoriteta: String?, naslov: String?): Boolean = when (shema?.lowercase()) {
        "http", "https" -> true
        "content" -> !jeLastna(shema, avtoriteta)
        "file" -> naslov == DOMACA_STRAN
        else -> false
    }

    /** Koren, iz katerega ponudnik bere. */
    enum class Koren { JAVNO, ZUNANJA, NOTRANJA }

    /** Mapa prenosov aplikacije (getExternalFilesDir(DIRECTORY_DOWNLOADS)) in nadomestna mapa v filesDir. */
    const val ZUNANJA_MAPA = "Download"
    const val NOTRANJA_MAPA = "prenosi"

    /**
     * Pot URI ponudnika -> (koren, pot znotraj deljene mape) ali null, ce te mape ne delimo.
     * Deljeno je samo to, kar prikaze seznam prenosov (PrenosiUi.zberi, HubKrmilnik.mapaZaPrejete):
     * javne mape (JAVNO: pot se zacne z imenom javne mape), <zunanje datoteke>/Download (ZUNANJA)
     * in <filesDir>/prenosi (NOTRANJA). Izhod iz deljene mape (..) zavrne ponudnik s kanonsko potjo,
     * zato mora biti koren za ZUNANJA in NOTRANJA ze sama deljena mapa, ne njen starsevski imenik.
     */
    fun koren(pot: String): Pair<Koren, String>? = when {
        pot.startsWith("/javno/") -> Koren.JAVNO to pot.removePrefix("/javno/")
        pot.startsWith("/external_files/$ZUNANJA_MAPA/") -> Koren.ZUNANJA to pot.removePrefix("/external_files/$ZUNANJA_MAPA/")
        pot.startsWith("/$NOTRANJA_MAPA/") -> Koren.NOTRANJA to pot.removePrefix("/$NOTRANJA_MAPA/")
        else -> null
    }
}
