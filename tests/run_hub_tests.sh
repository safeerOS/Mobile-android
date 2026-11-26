#!/usr/bin/env bash
# Preizkus streznika, usmerjevalnika, tokov in seznanjanja SPAKE2 Safeer Huba v navadnem JVM
# (Androidovi razredi so v tests/stubs; HubTls je samo za Android in tu ni vkljucen).
# Ista koda kot na televizorju (tv-browser-2/tests/run_hub_tests.sh, run_usmerjevalnik_tests.sh in
# run_tokovi_tests.sh), prevedena iz TE kopije, da se odkrije vsak odmik med telefonom in televizorjem.
#   KOTLINC=/pot/do/kotlinc tests/run_hub_tests.sh
set -euo pipefail
TEST_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$TEST_DIR")"
SRC="$PROJECT_DIR/src/main/kotlin/com/safeer/mobile/browser/cast"
TOOLS_DIR="${SAFEER_TOOLS_DIR:-$PROJECT_DIR/../streamN-TV2/android_tv/.tools}"
KOTLINC="${KOTLINC:-$TOOLS_DIR/kotlinc/bin/kotlinc}"
command -v "$KOTLINC" >/dev/null 2>&1 || KOTLINC="kotlinc"
# Preizkusi tecejo tudi v CI (tests/run_tests.sh, tedenski pregled 4. 11. 2026).

# HubTokovi uporablja org.json (na Androidu vgrajen); na JVM ga dodamo iz predpomnilnika Gradla.
JSON_JAR="${JSON_JAR:-$(ls "$HOME"/.gradle/caches/modules-2/files-2.1/org.json/json/*/*/json-*.jar 2>/dev/null | grep -v sources | head -1)}"
[ -n "$JSON_JAR" ] || { echo "Manjka org.json (JSON_JAR)" >&2; exit 2; }
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

"$KOTLINC" -J-Xmx2g \
    "$TEST_DIR/stubs/Log.kt" \
    "$SRC/HubStreznik.kt" \
    "$SRC/ViriNaprave.kt" \
    "$SRC/JsonLahki.kt" \
    "$SRC/HubObramba.kt" \
    "$TEST_DIR/HubStreznikTest.kt" \
    -include-runtime -d "$OUT/hub.jar"

java -cp "$OUT/hub.jar" com.safeer.mobile.browser.cast.HubStreznikTestKt

# Obrambni mehanizem sredisca (HubObramba): pravila z lazno uro in pravo sredisce na zanki.
"$KOTLINC" -J-Xmx2g \
    "$TEST_DIR/stubs/Log.kt" \
    "$SRC/HubStreznik.kt" \
    "$SRC/ViriNaprave.kt" \
    "$SRC/JsonLahki.kt" \
    "$SRC/HubObramba.kt" \
    "$TEST_DIR/HubObrambaTest.kt" \
    -include-runtime -d "$OUT/obramba.jar"

java -cp "$OUT/obramba.jar" com.safeer.mobile.browser.cast.HubObrambaTestKt

# Varovalka kode za povezavo (HubVarovalka): skupna omejitev ugibanja 6-mestne kode, pravila z lazno uro.
"$KOTLINC" -J-Xmx2g \
    "$SRC/HubVarovalka.kt" \
    "$TEST_DIR/HubVarovalkaTest.kt" \
    -include-runtime -d "$OUT/varovalka.jar"

java -cp "$OUT/varovalka.jar" com.safeer.mobile.browser.cast.HubVarovalkaTestKt

# Dostop naprav do vsebin (DostopPravila): kaj zahteva katero dejanje in kdo ob uvedbi dovoljenj obdrzi dostop.
"$KOTLINC" -J-Xmx2g "$SRC/DostopPravila.kt" "$TEST_DIR/DostopPravilaTest.kt" -include-runtime -d "$OUT/dostop.jar"
java -cp "$OUT/dostop.jar" com.safeer.mobile.browser.cast.DostopPravilaTestKt

# Zeton Huba ne sledi preusmeritvam (S6, pregled 5. 11. 2026, kot na telefonu): pravi OkHttp proti krajevnima streznikoma.
OKHTTP_CP="$PROJECT_DIR/libs/okhttp-4.12.0.jar:$PROJECT_DIR/libs/okio-jvm-3.6.0.jar"
"$KOTLINC" -J-Xmx2g -cp "$OKHTTP_CP" "$SRC/HubPreusmeritve.kt" "$TEST_DIR/HubPreusmeritveTest.kt" -include-runtime -d "$OUT/hub-preusmeritve.jar"
java -cp "$OUT/hub-preusmeritve.jar:$OKHTTP_CP" com.safeer.mobile.browser.cast.HubPreusmeritveTestKt

"$KOTLINC" -J-Xmx2g "$TEST_DIR/stubs/Log.kt" "$SRC/OznakaDnevnik.kt" "$SRC/RegisterNaprav.kt" "$SRC/HubHttp.kt" "$SRC/HubVarovalka.kt" "$SRC/HubStreznik.kt" "$SRC/ViriNaprave.kt" "$SRC/HubObramba.kt" "$SRC/DostopPravila.kt" "$SRC/KrogZaupanja.kt" "$SRC/SafeerLog.kt" "$SRC/SeznamSprememb.kt" "$SRC/JsonLahki.kt" "$SRC/HubTokovi.kt" "$SRC/LinkVticniki.kt" "$SRC/Spake2.kt" "$SRC/HubUsmerjevalnik.kt" \
    "$TEST_DIR/UsmerjevalnikTest.kt" -cp "$JSON_JAR" -include-runtime -d "$OUT/usmerjevalnik.jar"
java -cp "$OUT/usmerjevalnik.jar:$JSON_JAR" com.safeer.mobile.browser.cast.UsmerjevalnikTestKt

"$KOTLINC" -J-Xmx2g "$TEST_DIR/stubs/Log.kt" "$SRC/OznakaDnevnik.kt" "$SRC/RegisterNaprav.kt" "$SRC/HubHttp.kt" "$SRC/HubVarovalka.kt" "$SRC/HubStreznik.kt" "$SRC/ViriNaprave.kt" "$SRC/HubObramba.kt" "$SRC/DostopPravila.kt" "$SRC/KrogZaupanja.kt" "$SRC/SafeerLog.kt" "$SRC/SeznamSprememb.kt" "$SRC/JsonLahki.kt" "$SRC/HubTokovi.kt" "$SRC/LinkVticniki.kt" "$SRC/Spake2.kt" "$SRC/HubUsmerjevalnik.kt" \
    "$TEST_DIR/TokoviTest.kt" -cp "$JSON_JAR" -include-runtime -d "$OUT/tokovi.jar"
java -cp "$OUT/tokovi.jar:$JSON_JAR" com.safeer.mobile.browser.cast.TokoviTestKt

# Testni vektor RFC 9382 (isti kot za spake2.py v brskalniku za Linux).
"$KOTLINC" -J-Xmx2g "$SRC/Spake2.kt" "$TEST_DIR/Spake2Test.kt" -include-runtime -d "$OUT/spake2.jar"
java -cp "$OUT/spake2.jar" com.safeer.mobile.browser.cast.Spake2TestKt

# Dostop naprav do vsebin telefona (DostopPravila, ista pravila kot TV; tedenski pregled 4. 11. 2026).
"$KOTLINC" -J-Xmx2g "$SRC/DostopPravila.kt" "$TEST_DIR/DostopPravilaTest.kt" -include-runtime -d "$OUT/dostop.jar"
java -cp "$OUT/dostop.jar" com.safeer.mobile.browser.cast.DostopPravilaTestKt
python3 "$TEST_DIR/preveri_dostop.py"

# Pripeti odtis Huba velja za https://hub/ in https://hub:443/, ne za drug gostitelj ali vrata (pregled 4. 11. 2026).
"$KOTLINC" -J-Xmx2g "$SRC/HubNaslov.kt" "$TEST_DIR/HubNaslovTest.kt" -include-runtime -d "$OUT/hub-naslov.jar"
java -cp "$OUT/hub-naslov.jar" com.safeer.mobile.browser.cast.HubNaslovTestKt
