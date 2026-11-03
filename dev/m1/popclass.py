"""Are the mesh's chop pops the drawing's or the detector's? Mesh (m1, m1_200) vs 1.3.6's displays (m1_ab), same spot.

  python3 mods/Timber/dev/m1/popclass.py

1. Pops in the chop window at the detector's floor 12 and at lower floors (a lottery shows up as counts that swap
   between the two drawings as the floor drops).
2. Back-and-forth index per frame: over the tiles that move (min(d_ab, d_bc) > 8), the median d_ac / min(d_ab, d_bc)
   and the share under 0.5. A pose that goes out and back in one frame (a key reversing, a clock step) drives the
   median under 1 on that frame for the whole crown; a lone tile whose leaf texture lines up again two frames later is
   one tile in a few hundred.
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import exit as ex  # noqa: E402
from wblib import img  # noqa: E402

AB_END = {"oak": 466_339, "birch": 416_120, "spruce": 440_326}  # m1's chop -> mesh tau 6, for the displays-only run


def window(case, t):
    host = ex.WORK / f"mp-{case}" / "out-host"
    walls = {int(r[0]): int(r[1]) for r in ex.rows(host / f"frames_{t}.tsv")}
    c = json.loads((host / "trees.json").read_text())[t]["chop_us"]
    mf = host / f"mesh_{t}.tsv"
    end = next((int(r[0]) for r in ex.rows(mf) if float(r[3]) >= 6), None) if mf.exists() else c + AB_END[t]
    return host / "frames" / f"mp_{t}", ex.chop_window(walls, c - 100_000, end)


def tiles(x, t=24):
    hh, ww = (x.shape[0] // t) * t, (x.shape[1] // t) * t
    return np.abs(x[:hh, :ww]).reshape(hh // t, t, ww // t, t).mean(axis=(1, 3))


def main():
    for case in ("m1_ab", "m1", "m1_200"):
        for t in ex.TREES:
            d, (lo, hi) = window(case, t)
            paths = sorted(d.glob("*.png"))[max(0, lo - 1):hi + 2]
            off = max(0, lo - 1)
            counts = []
            for fl in (4, 8, 12):
                pops, _ = img.flicker(paths, floor=fl)
                counts.append(sum(lo <= p["frame"] + off <= hi for p in pops))
            g = [img._gray(p, 2) for p in paths]
            med, share = [], []
            for i in range(1, len(g) - 1):
                ab, bc, ac = tiles(g[i - 1] - g[i]), tiles(g[i] - g[i + 1]), tiles(g[i - 1] - g[i + 1])
                m = np.minimum(ab, bc)
                mv = m > 8
                if mv.sum() >= 50:
                    r = ac[mv] / m[mv]
                    med.append(float(np.median(r)))
                    share.append(float((r < 0.5).mean()))
            print(f"{case:7s} {t:6s} frames {lo}-{hi}  pops floor 4/8/12: {counts[0]}/{counts[1]}/{counts[2]}  "
                  f"moving frames {len(med)}: min median {min(med, default=0):.2f}, max share {max(share, default=0):.2f}")


if __name__ == "__main__":
    main()
