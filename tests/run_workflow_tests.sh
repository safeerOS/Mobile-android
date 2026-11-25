#!/usr/bin/env bash
# Tedenski pregled 25. 11. 2026 (N1): vsaka akcija v .github/workflows je pripeta na 40-mestni commit
# (oznaka v@ se lahko premakne na drugo kodo) in vsak checkout ima persist-credentials: false
# (sicer zeton GITHUB_TOKEN ostane v .git/config za vse naslednje korake).
# Uporaba: run_workflow_tests.sh [koren repozitorija]
set -euo pipefail
KOREN="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
python3 - "$KOREN/.github/workflows" <<'PY'
import pathlib, re, sys

mapa = pathlib.Path(sys.argv[1])
datoteke = sorted(mapa.glob("*.yml")) + sorted(mapa.glob("*.yaml"))
napake, uses, checkouti = [], 0, 0
for d in datoteke:
    vrstice = d.read_text(encoding="utf-8").splitlines()
    for i, v in enumerate(vrstice):
        m = re.match(r"^(\s*)(?:-\s+)?uses:\s*(\S+)", v)
        if not m:
            continue
        uses += 1
        akcija = m.group(2)
        if akcija.startswith("./"):
            continue  # lokalna akcija iz istega repozitorija
        if not re.fullmatch(r"[\w.-]+/[\w./-]+@[0-9a-f]{40}", akcija):
            napake.append(f"{d.name}:{i + 1}: ni pripeta na commit: {akcija}")
        if akcija.split("@")[0] == "actions/checkout":
            checkouti += 1
            # Blok koraka: vrstice do naslednjega koraka (- na enakem ali manjsem zamiku). Zamik je zamik
            # pomisljaja koraka; ce uses: ni v vrstici s pomisljajem (- name: ... / uses:), ga poiscemo nazaj.
            zamik = len(m.group(1))
            if not re.match(r"^\s*-\s", v):
                for w in reversed(vrstice[:i]):
                    if re.match(r"^\s*-\s", w):
                        zamik = len(w) - len(w.lstrip())
                        break
            blok = []
            for w in vrstice[i + 1:]:
                if w.strip() and (len(w) - len(w.lstrip())) <= zamik:
                    break
                blok.append(w)
            if not any(re.match(r"^\s*persist-credentials:\s*false\s*$", w) for w in blok):
                napake.append(f"{d.name}:{i + 1}: checkout brez persist-credentials: false")

# Preizkus brez predmeta ne sme uspeti (napacna pot ali prazna mapa).
if len(datoteke) < 2 or uses < 5 or checkouti < 2:
    napake.append(f"premalo najdenega: datotek={len(datoteke)} uses={uses} checkout={checkouti}")
for n in napake:
    print("NAPAKA", n)
print(f"workflowi: datotek={len(datoteke)} uses={uses} checkout={checkouti} napak={len(napake)}")
sys.exit(1 if napake else 0)
PY
