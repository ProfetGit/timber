#!/usr/bin/env python3
"""M6 render sheets: per tree one sheet, rows = render sets (baseline, sodium, iris+Complementary, ...), columns = the
chop, mid-fall and the slam, picked from the mesh scene's frame sequences by motion (frame-to-frame difference).

  python3 mods/Timber/dev/m6/looks.py        -> dev/m6/sheet-<tree>.png
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

MOD = Path(__file__).resolve().parents[2]
WORK = MOD.parents[1] / "tools/ModTest/.work/Timber"
SETS = [("baseline", "26.3-fabric-parity-looks"), ("sodium", "26.3-fabric-parity-looks-sodium"), ("iris + Complementary", "26.3-fabric-parity-looks-iris"),
        ("EntityCulling", "26.3-fabric-parity-looks-ec"), ("Continuity", "26.3-fabric-parity-looks-continuity"),
        ("Seamless Displays", "26.3-fabric-parity-looks-sd"), ("Snappy Blocks", "26.3-fabric-parity-looks-snappy"), ("full stack", "26.3-fabric-parity-looks-stack")]
TILE = (480, 270)


def shots(tree):
    d = WORK
    return sorted((d / "frames").glob(f"looks_{tree}_t*"))


def main():
    for tree in ("oak", "birch", "spruce"):
        sheet = Image.new("RGB", (TILE[0] * 4 + 190, TILE[1] * len(SETS)), "black")
        d = ImageDraw.Draw(sheet)
        for r, (label, key) in enumerate(SETS):
            fr = WORK / key / "out/frames"
            dirs = sorted(fr.glob(f"looks_{tree}_t*"), key=lambda x: int(x.name.rsplit("_t", 1)[1]))
            d.text((6, r * TILE[1] + 6), label, fill="white")
            for c, dd in enumerate(dirs[:4]):
                im = Image.open(dd / "f000.png").convert("RGB").resize(TILE)
                sheet.paste(im, (190 + c * TILE[0], r * TILE[1]))
                d.text((196 + c * TILE[0], r * TILE[1] + 6), dd.name.rsplit("_", 1)[1], fill="yellow")
        out = MOD / f"dev/m6/sheet-{tree}.png"
        sheet.save(out)
        print(out)


main()
