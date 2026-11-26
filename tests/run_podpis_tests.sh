#!/usr/bin/env bash
# Preizkus tools/podpis-apk.sh brez pravega kljuca (tedenski pregled 4. 11. 2026, S6/S13): geslo nikoli v ukazni vrstici,
# brez kljuca in brez SAFEER_NOV_KLJUC=1 se nov kljuc ne naredi. zipalign, apksigner in keytool so nadomestki.
set -uo pipefail
TEST_DIR="$(cd "$(dirname "$0")" && pwd)"
D="$(mktemp -d)"; trap 'rm -rf "$D"' EXIT
napak=0
preveri() { if [ "$2" = "$3" ]; then echo "  OK   $1"; else echo "  NAPAKA $1: pricakovano $2, dobljeno $3"; napak=$((napak + 1)); fi; }
mkdir -p "$D/bin"
for o in zipalign apksigner keytool; do
    cat > "$D/bin/$o" <<EOS
#!/usr/bin/env bash
echo "$o \$*" >> "$D/klici"
case "$o \$1" in
  "zipalign -f") cp "\${@: -2:1}" "\${@: -1}" ;;
  "apksigner sign") while [ \$# -gt 0 ]; do [ "\$1" = "--out" ] && cp /dev/null "\$2"; shift; done ;;
  keytool*) for a in "\$@"; do case "\$a" in -keystore) n=1 ;; *) [ "\${n:-}" = 1 ] && : > "\$a"; n= ;; esac; done ;;
esac
exit 0
EOS
    chmod +x "$D/bin/$o"
done
export PATH="$D/bin:$PATH"
GESLO="Skrivno-geslo-preizkusa-$$"
# shellcheck source=/dev/null
source "$TEST_DIR/../tools/podpis-apk.sh"
printf 'apk' > "$D/vhod.apk"

: > "$D/klici"; ( RELEASE_KEY_PASS="$GESLO"; podpisi_apk "$D/vhod.apk" "$D/izhod.apk" "$D/ni.jks" safeer ) > "$D/izpis" 2>&1
preveri "brez kljuca brez zastavice -> 1" 1 $?
! grep -q keytool "$D/klici"; preveri "brez zastavice ni novega kljuca" 0 $?

: > "$D/klici"; ( RELEASE_KEY_PASS="$GESLO"; SAFEER_NOV_KLJUC=1; podpisi_apk "$D/vhod.apk" "$D/izhod.apk" "$D/nov.jks" safeer ) > "$D/izpis" 2>&1
preveri "nov kljuc z zastavico -> 0" 0 $?
grep -q "keytool -genkeypair" "$D/klici"; preveri "z zastavico keytool -genkeypair" 0 $?
grep -q "apksigner sign .*--ks-pass env:RELEASE_KEY_PASS --key-pass env:RELEASE_KEY_PASS" "$D/klici"; preveri "apksigner geslo iz okolja" 0 $?
grep -q -- "-storepass:env RELEASE_KEY_PASS" "$D/klici"; preveri "keytool geslo iz okolja" 0 $?
! grep -qF "$GESLO" "$D/klici"; preveri "geslo ni v nobeni ukazni vrstici" 0 $?

: > "$D/klici"; ( unset RELEASE_KEY_PASS; podpisi_apk "$D/vhod.apk" "$D/izhod.apk" "$D/nov.jks" safeer ) > "$D/izpis" 2>&1
preveri "brez gesla -> 1" 1 $?
! grep -q "uber-apk-signer" "$TEST_DIR/../build_mobile_apk.sh"; preveri "build_mobile_apk.sh brez uber-apk-signer" 0 $?
! grep -q "genkeypair" "$TEST_DIR/../build_mobile_apk.sh" "$TEST_DIR/../install_mobile.sh"; preveri "nov kljuc samo v podpis-apk.sh" 0 $?

if [ "$napak" -eq 0 ]; then echo "PODPIS TESTI: OK"; else echo "PODPIS TESTI: $napak napak"; exit 1; fi
