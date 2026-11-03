#!/usr/bin/env python3
"""Frame-aligned critic sheets for the M1 exit: rows mesh (m1 Host), 1.3.6 displays (m1_ab Host), vanilla client (m1
Guest), columns at fixed times after each tree's chop (the epoch clock both clients stamp), one sheet per tree.

  python3 mods/Timber/dev/m1/sheets.py [ms after the chop ...]     -> dev/m1/frames/aligned-<tree>.png
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw

MOD = Path(__file__).resolve().parents[2]
WORK = MOD.parents[1] / "tools/ModTest/.work/Timber"
OUT = MOD / "dev/m1/frames"
TILE = (480, 270)


def rows(p):
    lines = p.read_text().splitlines()[1:]
    return [(int(r.split("\t")[0]), int(r.split("\t")[1])) for r in lines if r.strip()]


def pick(walls, t):
    return min(walls, key=lambda fw: abs(fw[1] - t))[0]


def main():
    offs = [int(a) for a in sys.argv[1:]] or [0, 150, 300, 600, 900, 1300, 1800, 2600]
    src = [("mesh (m1 Host)", "mp-m1", "out-host", "frames_{t}.tsv", "frames/mp_{t}"),
           ("1.3.6 displays (m1_ab Host)", "mp-m1_ab", "out-host", "frames_{t}.tsv", "frames/mp_{t}"),
           ("vanilla client (m1 Guest)", "mp-m1", "out-guest", "{t}.frames.tsv", "frames/{t}")]
    for t in ("oak", "birch", "spruce"):
        sheet = Image.new("RGB", (TILE[0] * len(offs), TILE[1] * len(src)), "black")
        d = ImageDraw.Draw(sheet)
        for r, (label, case, side, tsv, frames) in enumerate(src):
            chop = json.loads((WORK / case / "out-host/trees.json").read_text())[t]["chop_us"]
            walls = rows(WORK / case / side / tsv.format(t=t))
            for c, ms in enumerate(offs):
                f = pick(walls, chop + ms * 1000)
                im = Image.open(WORK / case / side / frames.format(t=t) / f"f{f:03d}.png").convert("RGB").resize(TILE)
                sheet.paste(im, (c * TILE[0], r * TILE[1]))
                d.text((c * TILE[0] + 6, r * TILE[1] + 4), f"{label} +{ms} ms f{f}", fill="yellow")
        p = OUT / f"aligned-{t}.png"
        sheet.save(p)
        print(p)


if __name__ == "__main__":
    main()
