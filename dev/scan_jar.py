#!/usr/bin/env python3
"""Scan mod jars for tree species and write dev/modded.json.  Usage: python3 dev/scan_jar.py mods/*.jar
Heuristic: <x>_log + <x>_leaves (or _leaf) blockstates in one namespace form a species; <x>_wood is used if present.
Check the result by eye, then run dev/gen.py and dev/build_pack.py."""
import json
import re
import sys
import zipfile
from pathlib import Path

out = Path(__file__).resolve().parent / "modded.json"
species = json.loads(out.read_text()) if out.exists() else []
have = {e["log"] for e in species}
for jar in sys.argv[1:]:
    states: dict[str, set[str]] = {}
    with zipfile.ZipFile(jar) as z:
        for n in z.namelist():
            m = re.fullmatch(r"assets/([^/]+)/blockstates/([^/]+)\.json", n)
            if m:
                states.setdefault(m.group(1), set()).add(m.group(2))
    for ns, names in sorted(states.items()):
        for n in sorted(names):
            if not n.endswith("_log") or n.startswith("stripped_") or f"{ns}:{n}" in have:
                continue
            base = n[:-4]
            leaf = next((c for c in (f"{base}_leaves", f"{base}_leaf") if c in names), None)
            if not leaf:
                print(f"skip {ns}:{n} (no matching leaves)")
                continue
            e = {"log": f"{ns}:{n}", "leaf": f"{ns}:{leaf}"}
            if f"{base}_wood" in names:
                e["wood"] = f"{ns}:{base}_wood"
            species.append(e)
            print(f"found {e}")
out.write_text(json.dumps(species, indent=2) + "\n")
print(f"{len(species)} species in {out}")
