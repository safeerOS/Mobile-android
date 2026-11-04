#!/usr/bin/env bash
# ==============================================================================
# 🛡️ 1-KLIK NAMESTITEV: SAFEER MOBILE BROWSER (SAMSUNG GALAXY & ANDROID)
# ==============================================================================
set -e

# Zadnja javna izdaja Safeer za Android: naslova APK in kontrolnih vsot preberemo iz GitHuba,
# da skripta deluje tudi po novih izdajah, ko se ime datoteke spremeni.
IZDAJA_API="https://api.github.com/repos/safeerOS/Mobile-android/releases/latest"
# Samo https, tudi po preusmeritvah; -f: napaka strezenika (404, 500) je napaka, ne prenesena stran z napako.
CURL_VARNO=(--proto '=https' --proto-redir '=https')
# Ime paketa aplikacije iz izdaje Mobile-android.
PAKET="com.safeer.mobile.browser"
# Tedenski pregled 4. 11. 2026: skripta je v SHA256SUMS iskala »Safeer-Browser.apk«, izdaja pa navaja
# safeer-browser-android-<verzija>.apk - vrstice ni bilo in preverba se je TIHO preskocila (prav tako brez SHA256SUMS).
# Prevzeto iz Android-tv (popravljeno tam 24. 10. 2026): brez vrstice ali ob neujemanju se namestitev ustavi.
# Pregled Grok 7: datoteke izdaje vzamemo samo z GitHuba (prenosi izdaj Mobile-android) in APK po imenu paketa
# (safeer-browser-android-<verzija>.apk) - ne prvi .apk v izdaji, ce jih je vec (npr. razlicica za preizkus).
PRENOSI_IZDAJE='https://github\.com/safeerOS/Mobile-android/releases/download/[^"/]+'
VZOREC_APK="$PRENOSI_IZDAJE/safeer-browser-android-[0-9]+(\.[0-9]+)*\.apk"
VZOREC_SHA="$PRENOSI_IZDAJE/SHA256SUMS"

# Naslov datoteke izdaje iz JSON odgovora GitHub API ($1) po vzorcu ($2); prazno, ce je ni.
naslov_iz_izdaje() {
    printf '%s' "$1" | grep -oE "\"browser_download_url\": *\"$2\"" | head -n 1 | cut -d'"' -f4
}

# Celovitost: SHA256SUMS izdaje navaja vsoto pod imenom datoteke v izdaji (npr. safeer-browser-android-1.0.37.apk),
# v obliki »<vsota>  ime« ali »<vsota> *ime«. Vrne 0 samo, ce se vsota ujema; sicer izpise razlog in vrne 1.
preveri_sha256() {
    local apk="$1" vsote="$2" ime="$3" pricakovano dejansko
    if [ ! -s "$vsote" ]; then
        echo "❌ Datoteka SHA256SUMS je prazna ali je ni. Prekinjam namestitev."
        return 1
    fi
    pricakovano="$(awk -v ime="$ime" '$2 == ime || $2 == "*" ime { print $1; exit }' "$vsote")"
    if [ -z "$pricakovano" ]; then
        echo "❌ SHA256SUMS izdaje nima vrstice za $ime. Prekinjam namestitev."
        return 1
    fi
    dejansko="$(sha256sum "$apk" | awk '{print $1}')"
    if [ "$pricakovano" != "$dejansko" ]; then
        echo "❌ SHA-256 kontrolna vsota se NE ujema!"
        echo "   Pričakovano: $pricakovano"
        echo "   Dobljeno:    $dejansko"
        echo "🚨 Prekinjam namestitev zaradi varnosti."
        return 1
    fi
    echo "🔒 SHA-256 celovitost potrjena ($dejansko)"
}

# Tedenski pregled 4. 11. 2026 (S5): pred namestitvijo preverimo tudi podpisnika APK - SHA256SUMS je v isti izdaji kot APK,
# zato sama vsota ne loci ponarejene izdaje. Javni odtis SHA-256 potrdila mobilnega kljuca Safeer, izmerjen z
# »apksigner verify --print-certs« na uradnem safeer-browser-android-1.0.37.apk (4. 11. 2026). Ni skrivnost.
MOBILNI_ODTIS="442B4A455551887DDB7BB2F2F62CC1574EE3EF0EEBB56246A2E425C9E9D9CC55"

# Neodvisni pregled 4. 11. 2026 09:10: podpisnika bere SAMO apksigner (sheme v1-v3.1). Prejsnji nadomestek
# keytool -printcert -jarfile vidi le podpis v1 (JAR): APK z ohranjenim Safeer v1 in tujim v2/v3 je sel skozi, Android
# pa ga preveri in namesti po v2/v3. Brez apksignerja namestitev zavrnemo. Pot je absolutna (pripeta build-tools v
# Android SDK ali sistemski paket apksigner), ne iz PATH.
APKSIGNER_RAZLICICE="36.0.0 35.0.0 34.0.0"
najdi_apksigner() {
    local koren r
    for koren in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "${HOME:-}/Android/Sdk"; do
        case "$koren" in /*) ;; *) continue ;; esac
        for r in $APKSIGNER_RAZLICICE; do
            if [ -x "$koren/build-tools/$r/apksigner" ]; then printf '%s' "$koren/build-tools/$r/apksigner"; return 0; fi
        done
    done
    if [ -x /usr/bin/apksigner ]; then printf '%s' /usr/bin/apksigner; return 0; fi
    return 1
}
NI_APKSIGNERJA="Za preverbo podpisa je potreben apksigner (Android SDK build-tools ali paket: sudo apt install apksigner)."

# Odtis SHA-256 podpisnika APK (velike crke brez dvopicij); prazno, ce apksignerja ni, APK ne preveri ali ima vec
# podpisnikov (tudi dodaten podpisnik v3.1 za novejse Androide - ta bi na takem Androidu odlocal).
odtis_apk() {
    local apksigner izpis
    apksigner="$(najdi_apksigner)" || return 0
    izpis="$("$apksigner" verify --print-certs "$1" 2>/dev/null)" || return 0
    [ "$(printf '%s\n' "$izpis" | grep -c 'certificate SHA-256 digest:')" = 1 ] || return 0
    printf '%s\n' "$izpis" | awk '/^Signer #1 certificate SHA-256 digest:/ { print toupper($NF); exit }'
}

# Vrne 0 samo, ce je APK podpisan z mobilnim kljucem Safeer; sicer izpise razlog in vrne 1.
preveri_podpis() {
    local odtis
    odtis="$(odtis_apk "$1")"
    if [ -z "$odtis" ]; then
        if najdi_apksigner >/dev/null; then echo "❌ Podpisa APK ni mogoče preveriti (apksigner ga ne potrdi)."; else echo "❌ $NI_APKSIGNERJA"; fi
        echo "👉 Ali prenesi APK s https://safeer.si/browser/android/"
        return 1
    fi
    if [ "$odtis" != "$MOBILNI_ODTIS" ]; then
        echo "❌ APK NI podpisan s ključem Safeer (odtis: $odtis). Prekinjam namestitev."
        return 1
    fi
    echo "🔏 Podpis Safeer potrjen."
}

glavno() {
    # Zacasne datoteke v svoji mapi (mktemp -d), ne na predvidljivi poti v /tmp: drug uporabnik bi jo lahko vnaprej
    # pripravil ali zamenjal med prenosom in namestitvijo. Mapa izgine ob izhodu. (Preizkus poda svoje poti.)
    if [ -z "${TEMP_APK:-}" ]; then
        D="$(mktemp -d)"
        trap 'rm -rf "$D"' EXIT
        TEMP_APK="$D/Safeer-Browser.apk"
        TEMP_SHA="$D/Safeer-Browser.SHA256SUMS"
    fi

    IZDAJA_JSON="$(curl "${CURL_VARNO[@]}" -fsL "$IZDAJA_API" || true)"
    APK_URL="$(naslov_iz_izdaje "$IZDAJA_JSON" "$VZOREC_APK")"
    SHA_URL="$(naslov_iz_izdaje "$IZDAJA_JSON" "$VZOREC_SHA")"
    if [ -z "$IZDAJA_JSON" ]; then
        echo "❌ Ne najdem zadnje izdaje (GitHub ni odgovoril). Prenesi APK s https://safeer.si/browser/android/"
        exit 1
    fi
    # Neodvisni pregled 9: izdaja je, a brez APK s pricakovanim imenom (npr. preimenovan) - povemo, kaj manjka.
    if [ -z "$APK_URL" ]; then
        echo "❌ Zadnja izdaja nima APK z imenom safeer-browser-android-<verzija>.apk na github.com/safeerOS/Mobile-android."
        echo "👉 Prenesi APK s https://safeer.si/browser/android/"
        exit 1
    fi
    # Brez kontrolnih vsot APK ne namestimo (prej je bilo to samo opozorilo).
    if [ -z "$SHA_URL" ]; then
        echo "❌ Izdaja nima datoteke SHA256SUMS; celovitosti APK ni mogoče preveriti. Prekinjam namestitev."
        exit 1
    fi

    echo "=========================================================="
    echo "🛡️ SAFEER BROWSER: 1-KLIK NAMESTITEV"
    echo "=========================================================="

    echo "📥 Prenašam najnovejšo različico Safeer Browser APK..."
    if ! curl "${CURL_VARNO[@]}" -fsL -o "$TEMP_APK" "$APK_URL" || [ ! -s "$TEMP_APK" ]; then
        echo "❌ Napaka pri prenosu APK paketa."
        exit 1
    fi

    echo "✅ APK uspešno prenesen ($(du -h "$TEMP_APK" | cut -f1))"

    if ! curl "${CURL_VARNO[@]}" -fLs -o "$TEMP_SHA" "$SHA_URL"; then
        echo "❌ Napaka pri prenosu SHA256SUMS. Prekinjam namestitev."
        exit 1
    fi
    preveri_sha256 "$TEMP_APK" "$TEMP_SHA" "${APK_URL##*/}" || exit 1

    # Preveri prisotnost ADB
    if command -v adb >/dev/null 2>&1; then
        DEVICE=$(adb devices | grep -v "List" | grep "device$" | head -n 1 | awk '{print $1}')
        if [ -n "$DEVICE" ]; then
            echo "📱 Zaznana ADB naprava: $DEVICE"
            echo "📲 Nameščam Safeer Browser..."
            preveri_podpis "$TEMP_APK" || exit 1
            adb -s "$DEVICE" install -r "$TEMP_APK"
            echo "🚀 Zaganjam Safeer Browser..."
            adb -s "$DEVICE" shell am start -n "$PAKET/.MainActivity"
            echo "=========================================================="
            echo "🎉 SAFEER BROWSER JE USPEŠNO NAMEŠČEN IN ZAGNAN!"
            echo "=========================================================="
            exit 0
        fi
    fi

    # Če ni zaznane ADB naprave
    cp "$TEMP_APK" "./Safeer-Browser.apk"
    echo "ℹ️ APK paket je shranjen v: $(pwd)/Safeer-Browser.apk"
    echo "👉 Lahko ga ročno pošljete ali namestite na svoj telefon."
}

# Ob »source« (preizkus tests/preveri_install.sh) se namestitev ne zazene - samo funkcije.
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
    glavno "$@"
fi
