#!/usr/bin/env bash
# ==============================================================================
# 📲 SAFEER BROWSER ZA TELEFON (Samsung Galaxy in drugi Android telefoni)
# ==============================================================================
# Tedenski pregled 4. 11. 2026 (S13, kot na TV 28. 10.): skripta je brez APK sama zagnala build_mobile_apk.sh (in s tem
# lahko nov produkcijski kljuc) in namestila lokalni APK brez preverbe. Uradno izdajo namesti install_safeer.sh
# (preverba SHA-256 in podpisnika). Razvijalec svoj APK namesti z »adb install -r <apk>«.
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec bash "$DIR/install_safeer.sh" "$@"
