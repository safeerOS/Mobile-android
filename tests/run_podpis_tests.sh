#!/usr/bin/env bash
# Tedenski pregled 25. 11. 2026 (S2): podpis v build_mobile_apk.sh brez gesla v ukazni vrstici in brez tihega novega
# kljuca. Skripto pozenemo v zacasni kopiji projekta z laznimi orodji (aapt2, kotlinc, java, jar, keytool, zipalign,
# apksigner, curl), ki svoje argumente zapisejo v dnevnik. Brez pravega kljuca, brez omrezja, brez prave gradnje.
# Uporaba: run_podpis_tests.sh [pot do build_mobile_apk.sh]   (privzeto skripta tega repozitorija)
set -uo pipefail
TEST_DIR="$(cd "$(dirname "$0")" && pwd)"
SKRIPTA="${1:-$TEST_DIR/../build_mobile_apk.sh}"
GESLO="Lazno-GESLO-s2-7f3a"
padci=0
preveri() {   # preveri <opis> <pricakovano> <dobljeno>
    if [ "$2" = "$3" ]; then echo "  OK  $1"; else echo "  PADEL $1 (pricakovano $2, dobljeno $3)"; padci=$((padci + 1)); fi
}

KOREN="$(mktemp -d /tmp/safeer-podpis.XXXXXX)"
trap 'rm -rf "$KOREN"' EXIT

lazno() {   # lazno <pot> <telo>: lazno orodje, ki zapise svoje ime in argumente v $LOG
    mkdir -p "$(dirname "$1")"
    printf '#!/usr/bin/env bash\necho "%s $*" >> "$LOG"\n%s\n' "$1" "$2" > "$1"
    chmod +x "$1"
}

# pripravi <ime>: zacasni projekt s kopijo skripte, laznimi orodji in pripeto (lazno) build-tools 34.0.0
pripravi() {
    P="$KOREN/$1/projekt"; T="$KOREN/$1/orodja"; B="$KOREN/$1/bin"; LOG="$KOREN/$1/ukazi.log"
    mkdir -p "$P/res" "$P/assets" "$P/libs" "$P/src/main/kotlin/x"
    cp "$SKRIPTA" "$P/build_mobile_apk.sh"
    echo '<manifest/>' > "$P/AndroidManifest.xml"
    echo 'package x' > "$P/src/main/kotlin/x/A.kt"
    : > "$LOG"
    mkdir -p "$T"; : > "$T/android.jar"; : > "$T/r8.jar"; : > "$T/uber-apk-signer.jar"
    lazno "$T/aapt2" 'while [ $# -gt 0 ]; do [ "$1" = -o ] && : > "$2"; shift; done; exit 0'
    lazno "$T/kotlinc/bin/kotlinc" ':'
    lazno "$B/jar" ':'
    lazno "$B/curl" 'exit 7'
    lazno "$B/apksigner" 'exit 0'
    lazno "$B/zipalign" 'exit 0'
    # java: D8 ne naredi nicesar; uber-apk-signer (stara skripta) naredi podpisan APK v --out
    lazno "$B/java" 'o=""; while [ $# -gt 0 ]; do [ "$1" = --out ] && o="$2"; shift; done; [ -n "$o" ] && mkdir -p "$o" && : > "$o/a-aligned-signed.apk"; exit 0'
    lazno "$B/keytool" 'while [ $# -gt 0 ]; do [ "$1" = -keystore ] && : > "$2"; shift; done; exit 0'
    local bt="$P/.android-sdk/build-tools/34.0.0"
    lazno "$bt/zipalign" '[ "$1" = -c ] && exit 0; n=$#; cp "${@:n-1:1}" "${@:n:1}"'
    lazno "$bt/apksigner" 'if [ "$1" = sign ]; then o=""; while [ $# -gt 1 ]; do [ "$1" = --out ] && o="$2"; shift; done; cp "$1" "$o"; fi; exit 0'
}

pozeni() {   # pozeni [argumenti skripte]: gradnja z laznimi orodji in geslom samo v okolju
    env PATH="$B:/usr/bin:/bin" LOG="$LOG" ANDROID_BUILD_TOOLS="$T" RELEASE_KEY_PASS="$GESLO" \
        bash "$P/build_mobile_apk.sh" "$@" > "$KOREN/izpis" 2>&1
    local rc=$?
    if [ -n "${PODPIS_IZPIS:-}" ]; then sed 's/^/    | /' "$KOREN/izpis"; fi   # PODPIS_IZPIS=1: izpis gradnje za razhroscevanje
    return "$rc"
}

echo "Podpis APK (S2):"

# (a) manjkajoc keystore brez zastavice: ustavi, brez keytool -genkeypair
pripravi a; pozeni; rc=$?
grep -q '5/5' "$KOREN/izpis"; preveri "kontrola: lazna gradnja pride do podpisa (5/5)" 0 $?
[ "$rc" -ne 0 ]; preveri "manjkajoc keystore brez --nov-kljuc -> izhod != 0" 0 $?
grep -q -- '-genkeypair' "$LOG"; preveri "manjkajoc keystore: brez keytool -genkeypair" 1 $?
[ -e "$P/keystore/safeer-release.jks" ]; preveri "manjkajoc keystore: nov kljuc ni nastal" 1 $?
grep -q -- '--nov-kljuc' "$KOREN/izpis"; preveri "manjkajoc keystore: sporocilo omeni --nov-kljuc" 0 $?

# (b) obstojec keystore: podpis z apksigner iz pripete build-tools, geslo nikoli v argumentih
pripravi b; mkdir -p "$P/keystore"; : > "$P/keystore/safeer-release.jks"; pozeni; rc=$?
preveri "podpis z obstojecim kljucem -> 0" 0 "$rc"
grep -qF -- "$GESLO" "$LOG"; preveri "geslo ni v argumentih nobenega ukaza" 1 $?
grep -q "^$P/.android-sdk/build-tools/34.0.0/apksigner sign .*--ks-pass env:RELEASE_KEY_PASS .*--key-pass env:RELEASE_KEY_PASS" "$LOG"
preveri "apksigner z absolutne poti, gesli prek env:" 0 $?
grep -q "^$P/.android-sdk/build-tools/34.0.0/apksigner verify" "$LOG"; preveri "podpis preverjen (apksigner verify)" 0 $?
grep -q "^$B/apksigner\|^$B/zipalign\|uber-apk-signer" "$LOG"; preveri "ne apksigner iz PATH ne uber-apk-signer" 1 $?
grep -q "^$B/curl" "$LOG"; preveri "brez omrezja (curl ni klican)" 1 $?
[ -f "$P/Safeer-Mobile.apk" ]; preveri "podpisan APK nastal" 0 $?

# (b2) --nov-kljuc: keytool z geslom iz okolja
pripravi c; pozeni --nov-kljuc; rc=$?
preveri "--nov-kljuc -> 0" 0 "$rc"
grep -q -- "^$B/keytool -genkeypair .*-storepass:env RELEASE_KEY_PASS -keypass:env RELEASE_KEY_PASS" "$LOG"
preveri "--nov-kljuc: keytool -storepass:env/-keypass:env" 0 $?
grep -qF -- "$GESLO" "$LOG"; preveri "--nov-kljuc: geslo ni v argumentih" 1 $?

# (c) skladnja in shellcheck
bash -n "$SKRIPTA"; preveri "bash -n" 0 $?
if command -v shellcheck > /dev/null; then
    shellcheck -S warning "$SKRIPTA"; preveri "shellcheck -S warning" 0 $?
else
    echo "  PADEL shellcheck ni namescen"; padci=$((padci + 1))
fi

if [ "$padci" -ne 0 ]; then echo "Podpis APK (S2): PADEL ($padci)"; exit 1; fi
echo "Podpis APK (S2): OK"
