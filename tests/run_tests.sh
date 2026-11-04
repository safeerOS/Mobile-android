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
"$KOTLINC" "$SOURCE_DIR/PdfPreusmeritve.kt" "$TEST_DIR/PdfPreusmeritveTest.kt" -include-runtime -d "$TEST_OUTPUT/pdf-preusmeritve.jar"
java -jar "$TEST_OUTPUT/pdf-preusmeritve.jar"
"$KOTLINC" "$SOURCE_DIR/SpletVarnostPravila.kt" "$TEST_DIR/SpletVarnostPravilaTest.kt" -include-runtime -d "$TEST_OUTPUT/splet-varnost.jar"
java -jar "$TEST_OUTPUT/splet-varnost.jar"
# Vhodna vrata (UXSS, 4. 11. 2026 kot TV): vsi vhodi od zunaj gredo skozi SpletVarnostPravila.vhodniNaslov.
python3 "$TEST_DIR/preveri_vhode_brskalnika.py"
bash "$TEST_DIR/run_install_tests.sh"
"$KOTLINC" "$SOURCE_DIR/PonudnikPravila.kt" "$TEST_DIR/PonudnikPravilaTest.kt" -include-runtime -d "$TEST_OUTPUT/ponudnik.jar"
java -jar "$TEST_OUTPUT/ponudnik.jar"
python3 "$TEST_DIR/preveri_ponudnik.py"
"$KOTLINC" "$SOURCE_DIR/GooglePravila.kt" "$TEST_DIR/GooglePravilaTest.kt" -include-runtime -d "$TEST_OUTPUT/google.jar"
java -jar "$TEST_OUTPUT/google.jar"
"$KOTLINC" "$SOURCE_DIR/link/QrPravila.kt" "$TEST_DIR/QrPravilaTest.kt" -include-runtime -d "$TEST_OUTPUT/qr-pravila.jar"
java -jar "$TEST_OUTPUT/qr-pravila.jar"
"$KOTLINC" "$SOURCE_DIR/UrlSanitizer.kt" "$SOURCE_DIR/AuthenticationPages.kt" "$TEST_DIR/LoginPolicyTest.kt" -include-runtime -d "$TEST_OUTPUT/login.jar"
java -jar "$TEST_OUTPUT/login.jar"
"$KOTLINC" "$SOURCE_DIR/PrijavnaOkna.kt" "$TEST_DIR/PrijavnaOknaTest.kt" -include-runtime -d "$TEST_OUTPUT/prijavna-okna.jar"
java -jar "$TEST_OUTPUT/prijavna-okna.jar"
# Spanje zavihkov (lastnik 4. 11. 2026): 60 s v ozadju, 3 min ko aplikacija ni na zaslonu (2FA); brez largeHeap.
"$KOTLINC" "$SOURCE_DIR/SpanjePravila.kt" "$TEST_DIR/SpanjePravilaTest.kt" -include-runtime -d "$TEST_OUTPUT/spanje.jar"
java -cp "$TEST_OUTPUT/spanje.jar" com.safeer.mobile.browser.SpanjePravilaTestKt
# Izbira datotek (<input type=file>, 4. 11. 2026): accept -> MIME, strani samo content:// iz izbirnika.
"$KOTLINC" "$SOURCE_DIR/IzbiraDatotek.kt" "$TEST_DIR/IzbiraDatotekTest.kt" -include-runtime -d "$TEST_OUTPUT/izbira-datotek.jar"
java -cp "$TEST_OUTPUT/izbira-datotek.jar" com.safeer.mobile.browser.IzbiraDatotekTestKt
if grep -q 'largeHeap' "$PROJECT_DIR/AndroidManifest.xml"; then echo "FAIL AndroidManifest.xml: largeHeap mora biti izklopljen"; exit 1; fi
"$KOTLINC" "$SOURCE_DIR/LocalDnsProxy.kt" "$TEST_DIR/LocalDnsProxyTest.kt" -include-runtime -d "$TEST_OUTPUT/proxy.jar"
java -jar "$TEST_OUTPUT/proxy.jar"
FEED_DIR="$PROJECT_DIR/src/main/kotlin/com/safeer/threatfeed"
"$KOTLINC" -cp "$PROJECT_DIR/libs/bcprov-ed25519-1.78.1.jar" "$TEST_DIR"/stubs/*.kt "$SOURCE_DIR/DomainSuffixTrie.kt" "$SOURCE_DIR/ThreatBlockEngine.kt" "$SOURCE_DIR/ZascitaZagona.kt" "$SOURCE_DIR/AdBlockEngine.kt" "$SOURCE_DIR/SignedThreatIntel.kt" "$FEED_DIR/SignedThreatFeed.kt" "$FEED_DIR/SortedStringTable.kt" "$FEED_DIR/BankGuard.kt" "$FEED_DIR/BankGuardData.kt" "$FEED_DIR/ThreatListAgent.kt" "$FEED_DIR/FilterListEngine.kt" "$TEST_DIR/ThreatPolicyTest.kt" "$TEST_DIR/ZascitaZagonaTest.kt" -include-runtime -d "$TEST_OUTPUT/threat.jar"
java -cp "$TEST_OUTPUT/threat.jar:$PROJECT_DIR/libs/bcprov-ed25519-1.78.1.jar" com.safeer.mobile.browser.ThreatPolicyTestKt
# Zascita od prve zahteve hladnega zagona (prenos s TV 4. 11. 2026): svoj JVM, ker je stanje zapaha enkratno na proces.
java -cp "$TEST_OUTPUT/threat.jar:$PROJECT_DIR/libs/bcprov-ed25519-1.78.1.jar" com.safeer.mobile.browser.ZascitaZagonaTestKt
# Drevo grozenj iz kazala (mmap) odloca natanko kot drevo v kopici (300 000 domen).
"$KOTLINC" "$SOURCE_DIR/DomainSuffixTrie.kt" "$FEED_DIR/SortedStringTable.kt" "$TEST_DIR/DrevoKazaloTest.kt" -include-runtime -d "$TEST_OUTPUT/drevo-kazalo.jar"
java -Xmx1g -cp "$TEST_OUTPUT/drevo-kazalo.jar" com.safeer.mobile.browser.DrevoKazaloTestKt
"$KOTLINC" -cp "$PROJECT_DIR/libs/bcprov-ed25519-1.78.1.jar" "$PROJECT_DIR/src/main/kotlin/com/safeer/threatfeed/SignedThreatFeed.kt" "$FEED_DIR/SortedStringTable.kt" "$TEST_DIR/SignedThreatFeedTest.kt" -include-runtime -d "$TEST_OUTPUT/signed-feed.jar"
java -cp "$TEST_OUTPUT/signed-feed.jar:$PROJECT_DIR/libs/bcprov-ed25519-1.78.1.jar" com.safeer.threatfeed.SignedThreatFeedTestKt "$TEST_DIR/signed-feed-conformance"
"$KOTLINC" "$FEED_DIR/BankGuard.kt" "$FEED_DIR/BankGuardData.kt" "$TEST_DIR/BankGuardTest.kt" -include-runtime -d "$TEST_OUTPUT/bank-guard.jar"
java -cp "$TEST_OUTPUT/bank-guard.jar" com.safeer.threatfeed.BankGuardTestKt "$TEST_DIR/bank-guard-cases.json"
"$KOTLINC" "$FEED_DIR/ThreatListAgent.kt" "$TEST_DIR/ThreatListAgentTest.kt" -include-runtime -d "$TEST_OUTPUT/list-agent.jar"
java -cp "$TEST_OUTPUT/list-agent.jar" com.safeer.threatfeed.ThreatListAgentTestKt
"$KOTLINC" "$FEED_DIR/FilterListEngine.kt" "$TEST_DIR/FilterListEngineTest.kt" -include-runtime -d "$TEST_OUTPUT/filter-list.jar"
java -cp "$TEST_OUTPUT/filter-list.jar" com.safeer.threatfeed.FilterListEngineTestKt
# Ujemalnik ABP brez Pattern: enakost s starim regexom (s SAFEER_EASYLIST na polnem EasyList).
KOTLINC="$KOTLINC" bash "$TEST_DIR/run_abp_vzorec_tests.sh"
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
"$KOTLINC" "$TEST_DIR"/browser-stubs/*.kt "$SOURCE_DIR/PreferencesManager.kt" "$SOURCE_DIR/AuthenticationPages.kt" "$SOURCE_DIR/TabSession.kt" "$SOURCE_DIR/SpanjePravila.kt" "$SOURCE_DIR/TabManager.kt" "$TEST_DIR/BrowserStateTest.kt" -include-runtime -d "$TEST_OUTPUT/browser-state.jar"
java -jar "$TEST_OUTPUT/browser-state.jar"
# Hub, SPAKE2, seznanjanje in dostop naprav (prej samo rocno; tedenski pregled 4. 11. 2026).
KOTLINC="$KOTLINC" bash "$TEST_DIR/run_hub_tests.sh"
bash "$TEST_DIR/run_podpis_tests.sh"
python3 "$TEST_DIR/preveri_qr_opozorilo.py"
python3 "$TEST_DIR/preveri_qr_spredaj.py"
python3 "$TEST_DIR/preveri_google.py"
python3 "$TEST_DIR/preveri_pdfjs.py"
