#!/usr/bin/env bash
# ==============================================================================
# 🚀 BUILD SCRIPT: SAFEER MOBILE BROWSER (BOTNET C2 & MALWARE SHIELD)
# ==============================================================================
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Samodejno odkrivanje orodij za gradnjo (Build Tools Discovery)
if [ -n "$ANDROID_BUILD_TOOLS" ] && [ -d "$ANDROID_BUILD_TOOLS" ]; then
    TOOLS_DIR="$ANDROID_BUILD_TOOLS"
elif [ -d "$DIR/.tools" ]; then
    TOOLS_DIR="$DIR/.tools"
elif [ -d "$DIR/../streamN-TV2/android_tv/.tools" ]; then
    TOOLS_DIR="$DIR/../streamN-TV2/android_tv/.tools"
elif [ -d "$HOME/.tools/android" ]; then
    TOOLS_DIR="$HOME/.tools/android"
else
    TOOLS_DIR=""
fi

if [ -z "$TOOLS_DIR" ] || [ ! -d "$TOOLS_DIR" ]; then
    echo "❌ Napaka: Orodja za gradnjo niso bila najdena."
    echo "👉 Nastavite okoljsko spremenljivko: export ANDROID_BUILD_TOOLS=/pot/do/orodij"
    echo "👉 Zahtevana orodja v mapi: kotlinc/, aapt2, r8.jar, android.jar"
    exit 1
fi
# Tedenski pregled 25. 11. 2026 (S2): ta orodja nimajo pripete razlicice ali vsote - izpisemo, od kod so.
echo "Orodja za gradnjo (brez pripete vsote): $TOOLS_DIR"

# Tedenski pregled 25. 11. 2026 (S2): nov podpisni kljuc samo z izrecno zastavico. Nov kljuc pomeni, da se namescena
# aplikacija pri uporabnikih ne more posodobiti (drug podpisnik).
NOV_KLJUC=0
for arg in "$@"; do
    if [ "$arg" = "--nov-kljuc" ]; then NOV_KLJUC=1; fi
done

# 🧪 Testni podpisni ključ Safeer Threat Intelligence se ne sme znajti v objavljeni različici
if grep -rqs '"safeer-test-' "$DIR/src/main/kotlin" --include=SignedThreatIntel.kt && [ "${SAFEER_ALLOW_TEST_KEY:-}" != "1" ]; then
    echo "❌ SignedThreatIntel.kt vsebuje TESTNI ključ (safeer-test-*). To je testna gradnja."
    echo "👉 Za testno gradnjo: SAFEER_ALLOW_TEST_KEY=1 $0   (take različice ne objavljaj)"
    exit 1
fi

# 🔗 Link Core: gostitelj/odjemalec Safeer Linka (cast/*) ima en sam vir v tv-browser-2; tu je kopija.
# Ce je vir na tem racunalniku, mora biti kopija enaka - sicer se protokol med napravami razide.
LINK_CORE_SYNC="$DIR/../tv-browser-2/tools/link-core-sync.sh"
if [ -x "$LINK_CORE_SYNC" ]; then
    if ! bash "$LINK_CORE_SYNC" --preveri "$DIR"; then
        echo "❌ Link Core na telefonu se razlikuje od vira v tv-browser-2."
        echo "👉 Pozeni: bash \"$LINK_CORE_SYNC\" \"$DIR\"   (kopira vir na telefon), nato gradi znova."
        exit 1
    fi
fi

KOTLINC="$TOOLS_DIR/kotlinc/bin/kotlinc"
KOTLIN_LIB="$TOOLS_DIR/kotlinc/lib/kotlin-stdlib.jar"
AAPT2="${AAPT2:-$TOOLS_DIR/aapt2}"
LIB_CLASSPATH=""
for lib in "$DIR"/libs/*.jar; do LIB_CLASSPATH="$LIB_CLASSPATH:$lib"; done
BUILD_DIR="$DIR/build"
RELEASE_DIR="$DIR/Release/Artifacts"

echo "=========================================================="
echo "🛡️ GRADIM SAFEER MOBILE BROWSER (KOTLIN APK)"
echo "=========================================================="

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/gen"
mkdir -p "$BUILD_DIR/classes"
mkdir -p "$BUILD_DIR/dex"
mkdir -p "$RELEASE_DIR"

echo "⚙️ 1/5: Prevajam Android XML vire (AAPT2)..."
"$AAPT2" compile --dir "$DIR/res" -o "$BUILD_DIR/compiled_res.zip"
"$AAPT2" link -I "$TOOLS_DIR/android.jar" \
    --manifest "$DIR/AndroidManifest.xml" \
    --rename-manifest-package "com.safeer.mobile.browser" \
    -A "$DIR/assets" \
    --min-sdk-version 28 \
    --target-sdk-version 36 \
    -o "$BUILD_DIR/resources.apk" \
    --java "$BUILD_DIR/gen" \
    "$BUILD_DIR/compiled_res.zip"

# Vse datoteke .kt pod src/main/kotlin -- polje, ker poti vsebujejo presledke.
mapfile -d '' -t KT_VIRI < <(find "$DIR/src/main/kotlin" -name '*.kt' -print0 | sort -z)
if [ "${#KT_VIRI[@]}" -eq 0 ]; then
    echo "❌ Pod src/main/kotlin ni nobene datoteke .kt."
    exit 1
fi

echo "☕ 2/5: Prevajam Kotlin izvorno kodo (kotlinc)..."
# Kotlin je pri rasti kode zmanjkalo kopice (OutOfMemoryError, koda 137). Mejo povemo izrecno,
# da gradnja ni odvisna od tega, koliko je na racunalniku trenutno prostega.
"$KOTLINC" -J-Xmx3g -cp "$TOOLS_DIR/android.jar:$BUILD_DIR/gen$LIB_CLASSPATH" \
    -d "$BUILD_DIR/classes" \
    -jvm-target 1.8 \
    "${KT_VIRI[@]}" \
    "$BUILD_DIR/gen/com/safeer/mobile/browser/R.java"

# Vsi prevedeni razredi, vkljucno s podpaketi (npr. cast/).
mapfile -d '' -t RAZREDI < <(find "$BUILD_DIR/classes" -name '*.class' -print0 | sort -z)

echo "⚡ 3/5: Prevajam v Dalvik Executable (D8)..."
java -cp "$TOOLS_DIR/r8.jar" com.android.tools.r8.D8 \
    --min-api 28 \
    --output "$BUILD_DIR/dex" \
    --lib "$TOOLS_DIR/android.jar" \
    "${RAZREDI[@]}" \
    "$KOTLIN_LIB" "$DIR"/libs/*.jar

echo "📦 4/5: Sestavljam APK paket..."
cp "$BUILD_DIR/resources.apk" "$BUILD_DIR/unaligned.apk"
cd "$BUILD_DIR/dex"
jar -uf "$BUILD_DIR/unaligned.apk" ./*.dex
cd "$DIR"

if [ "${ASSEMBLE_ONLY:-}" = "1" ] || [ "${1:-}" = "--assemble" ] || [ "${1:-}" = ":assemble" ]; then
    echo "=========================================================="
    echo "🎉 ZGRAJEN APK PAKET (SESTAVLJEN / NEPODPISAN)"
    echo "Paket: $BUILD_DIR/unaligned.apk"
    echo "=========================================================="
    ls -lh "$BUILD_DIR/unaligned.apk"
    exit 0
fi

echo "✍️ 5/5: Podpisujem APK paket z namenskim produkcijskim ključem..."
KEYSTORE_DIR="$DIR/keystore"
DEFAULT_KEYSTORE="$KEYSTORE_DIR/safeer-release.jks"
RELEASE_KEYSTORE="${RELEASE_KEYSTORE:-$DEFAULT_KEYSTORE}"
RELEASE_KEY_ALIAS="${RELEASE_KEY_ALIAS:-safeer-browser}"

# Preberi geslo iz okolja ali lokalne zaščitene datoteke (brez privzetega gesla v skripti)
if [ -z "$RELEASE_KEY_PASS" ] && [ -f "$KEYSTORE_DIR/.release_pass" ]; then
    RELEASE_KEY_PASS="$(cat "$KEYSTORE_DIR/.release_pass")"
fi

if [ -z "$RELEASE_KEY_PASS" ]; then
    echo "❌ Napaka: Geslo produkcijskega ključa (RELEASE_KEY_PASS) ni nastavljeno."
    echo "👉 Nastavite okoljsko spremenljivko: export RELEASE_KEY_PASS=\"...\""
    echo "👉 Ali shranite geslo v lokalno datoteko $KEYSTORE_DIR/.release_pass (ki je v .gitignore)"
    exit 1
fi

# Tedenski pregled 25. 11. 2026 (S2): geslo gre orodjem prek okolja (keytool -storepass:env, apksigner env:), ne v
# ukazni vrstici - ta je vidna vsem procesom (ps, /proc/<pid>/cmdline). Kot TV (S6, 4. 11. 2026).
export RELEASE_KEY_PASS

if [ ! -f "$RELEASE_KEYSTORE" ]; then
    if [ "$NOV_KLJUC" != "1" ]; then
        echo "❌ Podpisni kljuc ni najden: $RELEASE_KEYSTORE" >&2
        echo "👉 Obnovi keystore iz varnostne kopije. Nov kljuc ne more posodobiti ze namescene aplikacije." >&2
        echo "👉 Samo za povsem nov kljuc: $0 --nov-kljuc" >&2
        exit 1
    fi
    echo "🔑 Ustvarjam NOV produkcijski keystore ($RELEASE_KEYSTORE) - shrani varnostno kopijo!"
    mkdir -p "$KEYSTORE_DIR"
    keytool -genkeypair -v \
        -keystore "$RELEASE_KEYSTORE" \
        -alias "$RELEASE_KEY_ALIAS" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass:env RELEASE_KEY_PASS \
        -keypass:env RELEASE_KEY_PASS \
        -dname "CN=Safeer Mobile Browser, OU=Safeer Security, O=Safeer, L=Ljubljana, ST=Slovenia, C=SI"
fi

# Tedenski pregled 25. 11. 2026 (S2): namesto uber-apk-signer.jar (brez pripete vsote, gesla v ukazni vrstici)
# zipalign in apksigner z absolutne poti iz build-tools 34.0.0, prenesenih s pripeto SHA-256 (enako kot TV).
SDK_DIR="$DIR/.android-sdk"
BT="$SDK_DIR/build-tools/34.0.0"
BUILD_TOOLS_ZIP_URL="https://dl.google.com/android/repository/build-tools_r34-linux.zip"
BUILD_TOOLS_ZIP_SHA256="e858c4b60069d0431051b225d384413b1643e1289b00a4825aed347f25bd510f"
if [ ! -x "$BT/apksigner" ] || [ ! -x "$BT/zipalign" ]; then
    echo "⬇️  Prenasam build-tools 34.0.0 (pripeta SHA-256)..."
    TMP_BT="$(mktemp -d)"
    curl -fL "$BUILD_TOOLS_ZIP_URL" -o "$TMP_BT/bt.zip"
    IZRACUNANO="$(sha256sum "$TMP_BT/bt.zip" | awk '{print $1}')"
    if [ "$IZRACUNANO" != "$BUILD_TOOLS_ZIP_SHA256" ]; then
        echo "❌ SHA-256 vsota build-tools se ne ujema (pricakovano: $BUILD_TOOLS_ZIP_SHA256, dobljeno: $IZRACUNANO)" >&2
        rm -rf "$TMP_BT"
        exit 1
    fi
    unzip -q "$TMP_BT/bt.zip" -d "$TMP_BT/razpakirano"
    rm -rf "$BT"
    mkdir -p "$SDK_DIR/build-tools"
    mv "$TMP_BT/razpakirano/android-14" "$BT"
    rm -rf "$TMP_BT"
fi

rm -rf "$BUILD_DIR/signed"
mkdir -p "$BUILD_DIR/signed"
# v1 ostane vklopljen (kot pri uber-apk-signer) za starejse preverbe podpisa.
"$BT/zipalign" -p -f 4 "$BUILD_DIR/unaligned.apk" "$BUILD_DIR/signed/aligned.apk"
SIGNED_APK="$BUILD_DIR/signed/safeer-mobile-signed.apk"
"$BT/apksigner" sign \
    --ks "$RELEASE_KEYSTORE" \
    --ks-key-alias "$RELEASE_KEY_ALIAS" \
    --ks-pass env:RELEASE_KEY_PASS \
    --key-pass env:RELEASE_KEY_PASS \
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
    --out "$SIGNED_APK" "$BUILD_DIR/signed/aligned.apk"
"$BT/apksigner" verify "$SIGNED_APK" || { echo "❌ Podpis APK ni veljaven." >&2; exit 1; }

FINAL_APK="$RELEASE_DIR/safeer-mobile-release.apk"
cp "$SIGNED_APK" "$FINAL_APK"
cp "$FINAL_APK" "$RELEASE_DIR/safeer-browser-release.apk"
cp "$FINAL_APK" "$DIR/Safeer-Mobile.apk"
cp "$FINAL_APK" "$DIR/Safeer-Browser.apk"

# Generiranje uradnih SHA256SUMS kontrolnih vsot
cd "$DIR"
sha256sum Safeer-Browser.apk Safeer-Mobile.apk > "$DIR/SHA256SUMS"
(cd "$RELEASE_DIR" && sha256sum safeer-mobile-release.apk safeer-browser-release.apk > "$RELEASE_DIR/SHA256SUMS")

# Sinhronizacija v spletno mapo za prenos (če je nastavljena)
if [ -n "$WEB_MOB_DIR" ] && [ -d "$WEB_MOB_DIR" ]; then
    cp -f "$FINAL_APK" "$WEB_MOB_DIR/Safeer-Mobile.apk"
    cp -f "$FINAL_APK" "$WEB_MOB_DIR/Safeer-Browser.apk"
    cp -f "$DIR/SHA256SUMS" "$WEB_MOB_DIR/SHA256SUMS"
    echo "🌐 Sinhronizirano v $WEB_MOB_DIR"
fi

echo ""
echo "=========================================================="
echo "🎉 ZGRAJEN SIGNED SAFEER MOBILE APK: $DIR/Safeer-Mobile.apk"
echo "Package ID: com.safeer.mobile.browser"
echo "=========================================================="
ls -lh "$DIR/Safeer-Mobile.apk"
