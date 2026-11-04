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

OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

"$KOTLINC" -J-Xmx2g "$TEST_DIR/stubs/Log.kt" "$SRC/HubStreznik.kt" "$TEST_DIR/HubStreznikTest.kt" \
    -include-runtime -d "$OUT/hub.jar"
java -cp "$OUT/hub.jar" com.safeer.mobile.browser.cast.HubStreznikTestKt

"$KOTLINC" -J-Xmx2g "$TEST_DIR/stubs/Log.kt" "$SRC/HubStreznik.kt" "$SRC/JsonLahki.kt" "$SRC/HubTokovi.kt" "$SRC/Spake2.kt" "$SRC/KrogZaupanja.kt" "$SRC/HubUsmerjevalnik.kt" \
    "$TEST_DIR/UsmerjevalnikTest.kt" -include-runtime -d "$OUT/usmerjevalnik.jar"
java -cp "$OUT/usmerjevalnik.jar" com.safeer.mobile.browser.cast.UsmerjevalnikTestKt

"$KOTLINC" -J-Xmx2g "$TEST_DIR/stubs/Log.kt" "$SRC/HubStreznik.kt" "$SRC/JsonLahki.kt" "$SRC/HubTokovi.kt" "$SRC/Spake2.kt" "$SRC/KrogZaupanja.kt" "$SRC/HubUsmerjevalnik.kt" \
    "$TEST_DIR/TokoviTest.kt" -include-runtime -d "$OUT/tokovi.jar"
java -cp "$OUT/tokovi.jar" com.safeer.mobile.browser.cast.TokoviTestKt

# Testni vektor RFC 9382 (isti kot za spake2.py v brskalniku za Linux).
"$KOTLINC" -J-Xmx2g "$SRC/Spake2.kt" "$TEST_DIR/Spake2Test.kt" -include-runtime -d "$OUT/spake2.jar"
java -cp "$OUT/spake2.jar" com.safeer.mobile.browser.cast.Spake2TestKt

# Dostop naprav do vsebin telefona (DostopPravila, ista pravila kot TV; tedenski pregled 4. 11. 2026).
"$KOTLINC" -J-Xmx2g "$SRC/DostopPravila.kt" "$TEST_DIR/DostopPravilaTest.kt" -include-runtime -d "$OUT/dostop.jar"
java -cp "$OUT/dostop.jar" com.safeer.mobile.browser.cast.DostopPravilaTestKt
python3 "$TEST_DIR/preveri_dostop.py"
