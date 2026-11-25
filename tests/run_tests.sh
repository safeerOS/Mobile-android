#!/usr/bin/env bash
set -euo pipefail
TEST_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$TEST_DIR")"
SOURCE_DIR="$PROJECT_DIR/src/main/kotlin/com/safeer/mobile/browser"
KOTLINC="${KOTLINC:-$PROJECT_DIR/../streamN-TV2/android_tv/.tools/kotlinc/bin/kotlinc}"
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
JAVA_MAJOR="$(java -XshowSettings:properties -version 2>&1 | awk '/java.specification.version =/ {print $3}')"
if ! [[ "$JAVA_MAJOR" =~ ^[0-9]+$ ]] || [ "$JAVA_MAJOR" -lt 17 ]; then
    echo "NAPAKA: testi zahtevajo JDK 17 ali novejši (Ed25519). Nastavite JAVA_HOME."
    exit 1
fi
TEST_OUTPUT="$(mktemp -d /tmp/safeer-tests.XXXXXX)"
trap 'rm -rf "$TEST_OUTPUT"' EXIT
# Tedenski pregled 25. 11. 2026: namestitvena skripta (V1) in pravila kode QR (V2), kot na TV.
bash "$TEST_DIR/run_install_tests.sh"
"$KOTLINC" "$SOURCE_DIR/link/QrPravila.kt" "$TEST_DIR/QrPravilaTest.kt" -include-runtime -d "$TEST_OUTPUT/qr.jar"
java -cp "$TEST_OUTPUT/qr.jar" com.safeer.mobile.browser.link.QrPravilaTestKt
# Tedenski pregled 25. 11. 2026 (V3): zetoni in meja povezav streznika datotek, kot na TV.
"$KOTLINC" "$SOURCE_DIR/link/ZetoniDatotek.kt" "$TEST_DIR/ZetoniDatotekTest.kt" -include-runtime -d "$TEST_OUTPUT/zetoni.jar"
java -cp "$TEST_OUTPUT/zetoni.jar" com.safeer.mobile.browser.link.ZetoniDatotekTestKt "$PROJECT_DIR/src/main/kotlin"
"$KOTLINC" "$SOURCE_DIR/link/StreznikMeja.kt" "$TEST_DIR/StreznikMejaTest.kt" -include-runtime -d "$TEST_OUTPUT/meja.jar"
java -cp "$TEST_OUTPUT/meja.jar" com.safeer.mobile.browser.link.StreznikMejaTestKt "$PROJECT_DIR/src/main/kotlin"
# Tedenski pregled 25. 11. 2026 (S3): naslovi in rok glave sprejema zvoka, kot na TV.
"$KOTLINC" "$SOURCE_DIR/link/LinkVarnostPravila.kt" "$TEST_DIR/LinkVarnostPravilaTest.kt" -include-runtime -d "$TEST_OUTPUT/link-varnost.jar"
java -cp "$TEST_OUTPUT/link-varnost.jar" com.safeer.mobile.browser.link.LinkVarnostPravilaTestKt "$PROJECT_DIR/src/main/kotlin"
"$KOTLINC" "$SOURCE_DIR/UrlSanitizer.kt" "$SOURCE_DIR/AuthenticationPages.kt" "$TEST_DIR/LoginPolicyTest.kt" -include-runtime -d "$TEST_OUTPUT/login.jar"
java -jar "$TEST_OUTPUT/login.jar"
"$KOTLINC" "$SOURCE_DIR/PrijavnaOkna.kt" "$TEST_DIR/PrijavnaOknaTest.kt" -include-runtime -d "$TEST_OUTPUT/prijavna-okna.jar"
java -jar "$TEST_OUTPUT/prijavna-okna.jar"
"$KOTLINC" "$SOURCE_DIR/LocalDnsProxy.kt" "$TEST_DIR/LocalDnsProxyTest.kt" -include-runtime -d "$TEST_OUTPUT/proxy.jar"
java -jar "$TEST_OUTPUT/proxy.jar"
FEED_DIR="$PROJECT_DIR/src/main/kotlin/com/safeer/threatfeed"
"$KOTLINC" -cp "$PROJECT_DIR/libs/bcprov-ed25519-1.78.1.jar" "$TEST_DIR"/stubs/*.kt "$SOURCE_DIR/DomainSuffixTrie.kt" "$SOURCE_DIR/ThreatBlockEngine.kt" "$SOURCE_DIR/AdBlockEngine.kt" "$SOURCE_DIR/SignedThreatIntel.kt" "$FEED_DIR/SignedThreatFeed.kt" "$FEED_DIR/BankGuard.kt" "$FEED_DIR/BankGuardData.kt" "$FEED_DIR/ThreatListAgent.kt" "$FEED_DIR/FilterListEngine.kt" "$TEST_DIR/ThreatPolicyTest.kt" -include-runtime -d "$TEST_OUTPUT/threat.jar"
java -cp "$TEST_OUTPUT/threat.jar:$PROJECT_DIR/libs/bcprov-ed25519-1.78.1.jar" com.safeer.mobile.browser.ThreatPolicyTestKt
"$KOTLINC" -cp "$PROJECT_DIR/libs/bcprov-ed25519-1.78.1.jar" "$PROJECT_DIR/src/main/kotlin/com/safeer/threatfeed/SignedThreatFeed.kt" "$TEST_DIR/SignedThreatFeedTest.kt" -include-runtime -d "$TEST_OUTPUT/signed-feed.jar"
java -cp "$TEST_OUTPUT/signed-feed.jar:$PROJECT_DIR/libs/bcprov-ed25519-1.78.1.jar" com.safeer.threatfeed.SignedThreatFeedTestKt "$TEST_DIR/signed-feed-conformance"
"$KOTLINC" "$FEED_DIR/BankGuard.kt" "$FEED_DIR/BankGuardData.kt" "$TEST_DIR/BankGuardTest.kt" -include-runtime -d "$TEST_OUTPUT/bank-guard.jar"
java -cp "$TEST_OUTPUT/bank-guard.jar" com.safeer.threatfeed.BankGuardTestKt "$TEST_DIR/bank-guard-cases.json"
"$KOTLINC" "$FEED_DIR/ThreatListAgent.kt" "$TEST_DIR/ThreatListAgentTest.kt" -include-runtime -d "$TEST_OUTPUT/list-agent.jar"
java -cp "$TEST_OUTPUT/list-agent.jar" com.safeer.threatfeed.ThreatListAgentTestKt
"$KOTLINC" "$FEED_DIR/FilterListEngine.kt" "$TEST_DIR/FilterListEngineTest.kt" -include-runtime -d "$TEST_OUTPUT/filter-list.jar"
java -cp "$TEST_OUTPUT/filter-list.jar" com.safeer.threatfeed.FilterListEngineTestKt
"$KOTLINC" "$FEED_DIR/SponsorBlock.kt" "$FEED_DIR/BankGuard.kt" "$FEED_DIR/BankGuardData.kt" "$TEST_DIR/SponsorBlockTest.kt" -include-runtime -d "$TEST_OUTPUT/sponsorblock.jar"
java -cp "$TEST_OUTPUT/sponsorblock.jar" com.safeer.threatfeed.SponsorBlockTestKt
ANDROID_JAR="${ANDROID_JAR:-$PROJECT_DIR/../streamN-TV2/android_tv/.tools/android.jar}"
DNS_CLASSPATH="$ANDROID_JAR"
for lib in "$PROJECT_DIR"/libs/*.jar; do DNS_CLASSPATH="$DNS_CLASSPATH:$lib"; done
"$KOTLINC" -cp "$DNS_CLASSPATH" "$SOURCE_DIR/DoHProxyEngine.kt" "$SOURCE_DIR/LocalDnsProxy.kt" "$SOURCE_DIR/Http2DnsTransport.kt" "$SOURCE_DIR/EmudhraRevocations.kt" "$TEST_DIR"/dns-stubs/*.kt "$TEST_DIR/DoHResolverTest.kt" -include-runtime -d "$TEST_OUTPUT/dns.jar"
java -cp "$TEST_OUTPUT/dns.jar:$DNS_CLASSPATH" com.safeer.mobile.browser.DoHResolverTestKt

"$KOTLINC" -cp "$DNS_CLASSPATH" "$SOURCE_DIR/EmudhraRevocations.kt" "$TEST_DIR/EmudhraRevocationsTest.kt" -include-runtime -d "$TEST_OUTPUT/emudhra.jar"
java -cp "$TEST_OUTPUT/emudhra.jar:$DNS_CLASSPATH" com.safeer.mobile.browser.EmudhraRevocationsTestKt

"$KOTLINC" "$SOURCE_DIR/TabSession.kt" "$TEST_DIR/TabSessionTest.kt" -include-runtime -d "$TEST_OUTPUT/session.jar"
java -jar "$TEST_OUTPUT/session.jar"
"$KOTLINC" "$TEST_DIR"/browser-stubs/*.kt "$SOURCE_DIR/PreferencesManager.kt" "$SOURCE_DIR/AuthenticationPages.kt" "$SOURCE_DIR/TabSession.kt" "$SOURCE_DIR/TabManager.kt" "$TEST_DIR/BrowserStateTest.kt" -include-runtime -d "$TEST_OUTPUT/browser-state.jar"
java -jar "$TEST_OUTPUT/browser-state.jar"
