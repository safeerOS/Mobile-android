#!/usr/bin/env bash
# Podpis APK izdaje (tedenski pregled 4. 11. 2026, S6/S13). Vir: build_mobile_apk.sh; preizkus tests/run_podpis_tests.sh.
#
# Prej: uber-apk-signer.jar brez preverbe vsote (kdorkoli z dostopom do mape orodij bi podtaknil program, ki dobi kljuc in
# geslo), geslo v ukazni vrstici (vidno v /proc/<pid>/cmdline vsem uporabnikom) in samodejno nov produkcijski kljuc,
# ce ga ni na pricakovani poti - izdaja s tujim kljucem se na telefonih ne more posodobiti.
# Zdaj: zipalign in apksigner iz Android build-tools (sdkmanager), geslo samo prek okolja (apksigner »env:«), nov kljuc
# samo z izrecno zastavico SAFEER_NOV_KLJUC=1.

# Pot do orodja iz Android build-tools ($1 = apksigner | zipalign); prazno, ce ga ni.
orodje_build_tools() {
    local ime="$1" pot=""
    pot="$(command -v "$ime" 2>/dev/null || true)"
    if [ -z "$pot" ] && [ -n "${ANDROID_HOME:-}" ]; then
        pot="$(ls "$ANDROID_HOME"/build-tools/*/"$ime" 2>/dev/null | sort -V | tail -n 1)"
    fi
    printf '%s' "$pot"
}

# podpisi_apk <nepodpisan.apk> <cilj.apk> <keystore> <alias>; geslo v okolju RELEASE_KEY_PASS (nikoli argument).
podpisi_apk() {
    local vhod="$1" cilj="$2" ks="$3" alias="$4" za zp d
    if [ -z "${RELEASE_KEY_PASS:-}" ]; then
        echo "❌ RELEASE_KEY_PASS ni nastavljen." >&2; return 1
    fi
    if [ ! -f "$ks" ]; then
        if [ "${SAFEER_NOV_KLJUC:-}" != "1" ]; then
            echo "❌ Produkcijskega kljuca ni: $ks" >&2
            echo "👉 Nov kljuc naredi samo, ce ga res nimas (telefoni z izdajo starega kljuca je ne bodo mogli posodobiti):" >&2
            echo "   SAFEER_NOV_KLJUC=1 bash build_mobile_apk.sh" >&2
            return 1
        fi
        mkdir -p "$(dirname "$ks")"
        # keytool sprejme geslo iz okolja (:env), ne v ukazni vrstici.
        keytool -genkeypair -keystore "$ks" -alias "$alias" -keyalg RSA -keysize 4096 -validity 10000 \
            -storepass:env RELEASE_KEY_PASS -keypass:env RELEASE_KEY_PASS \
            -dname "CN=Safeer Mobile Browser, OU=Safeer Security, O=Safeer, L=Ljubljana, ST=Slovenia, C=SI" || return 1
    fi
    za="$(orodje_build_tools zipalign)"; zp="$(orodje_build_tools apksigner)"
    if [ -z "$za" ] || [ -z "$zp" ]; then
        echo "❌ Ni zipalign ali apksigner (Android build-tools). Nastavi ANDROID_HOME." >&2; return 1
    fi
    d="$(mktemp -d)" || return 1
    "$za" -f -p 4 "$vhod" "$d/poravnan.apk" || { rm -rf "$d"; return 1; }
    "$zp" sign --ks "$ks" --ks-key-alias "$alias" --ks-pass env:RELEASE_KEY_PASS --key-pass env:RELEASE_KEY_PASS \
        --out "$cilj" "$d/poravnan.apk" || { rm -rf "$d"; return 1; }
    rm -rf "$d"
    "$zp" verify "$cilj" >/dev/null || return 1
}
