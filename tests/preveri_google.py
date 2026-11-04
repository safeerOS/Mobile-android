#!/usr/bin/env python3
"""isGoogleDomain mora biti GooglePravila.jeGoogle (po gostitelju), ne iskanje niza v naslovu (tedenski pregled 4. 11. 2026)."""
import re, sys
from pathlib import Path
koren = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent
s = (koren / "src/main/kotlin/com/safeer/mobile/browser/UserScriptManager.kt").read_text(encoding="utf-8")
if not re.search(r"fun isGoogleDomain\(url: String\?\): Boolean = GooglePravila\.jeGoogle\(url\)", s):
    print("GOOGLE: isGoogleDomain ne uporablja GooglePravila.jeGoogle"); sys.exit(1)
print("GOOGLE: OK")
