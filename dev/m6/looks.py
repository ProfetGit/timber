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
SETS = [("baseline", "26.3-fabric"), ("sodium", "26.3-fabric-sodium"), ("iris + Complementary", "26.3-fabric-iris"),
        ("EntityCulling", "26.3-fabric-ec"), ("Continuity", "26.3-fabric-continuity"),
        ("Seamless Displays", "26.3-fabric-sd"), ("Snappy Blocks", "26.3-fabric-snappy"), ("full stack", "26.3-fabric-stack")]
TILE = (480, 270)


def grey(p):
    return np.asarray(Image.open(p).convert("L").resize((160, 90)), dtype=np.float32)


def pick(d):
    files = sorted(d.glob("f*.png"))
    g = [grey(f) for f in files]
    step = [float(np.abs(g[i + 1] - g[i]).mean()) for i in range(len(g) - 1)]
    peak = max(range(len(step)), key=step.__getitem__)
    thr = 0.25 * step[peak]
    c = peak
    while c > 0 and step[c - 1] > thr:
        c -= 1
    e = peak
    while e < len(step) - 1 and step[e + 1] > thr:
        e += 1
    return files, max(0, c - 1), (c + e) // 2, min(len(files) - 1, e + 1)


def main():
    for tree in ("oak", "birch", "spruce"):
        sheet = Image.new("RGB", (TILE[0] * 3 + 190, TILE[1] * len(SETS)), "black")
        d = ImageDraw.Draw(sheet)
        for r, (label, key) in enumerate(SETS):
            src = WORK / key / "out/frames" / f"mesh_{tree}"
            if not src.exists():
                continue
            files, *idx = pick(src)
            d.text((6, r * TILE[1] + 6), label, fill="white")
            for c, i in enumerate(idx):
                im = Image.open(files[min(i, len(files) - 1)]).convert("RGB").resize(TILE)
                sheet.paste(im, (190 + c * TILE[0], r * TILE[1]))
                d.text((196 + c * TILE[0], r * TILE[1] + 6), f"f{i}", fill="yellow")
        out = MOD / f"dev/m6/sheet-{tree}.png"
        sheet.save(out)
        print(out)


main()
