#!/usr/bin/env python3
"""Timber art as lossless WebP, animations at 60 fps, everything collected in release-art/ (generated, not in Git).

Frames come from Blockbench at 60 fps (VM.render(0, 192, 1600, dir, 60) into dev/icon/frames60/ for the icon camera and
dev/icon/banner_frames60/ after VM.camera(0.1)). The crop, background, outline and banner layout are the 25 fps ones
(make_icon / make_banner), measured on dev/icon/frames/ so the 60 fps art sits exactly where the GIF's does.

  python3 dev/make_webp.py          # slow (lossless, 192 frames of 1536x512): run it as a background job
"""
import shutil
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import make_banner as mb  # noqa: E402
import make_icon as mi  # noqa: E402

ROOT = mi.ROOT
OUT = ROOT / "release-art"
FPS = 60
N = 192                      # 3.2 s


def save(frames, path):
    dur = [round((i + 1) * 1000 / FPS) - round(i * 1000 / FPS) for i in range(len(frames))]
    frames[0].save(path, save_all=True, append_images=frames[1:], duration=dur, loop=0, lossless=True, quality=100, method=6, exact=True)
    return path.stat().st_size / 1024


def crop_box():
    raw = [Image.open(f).convert("RGBA") for f in sorted((mi.ICON / "frames").glob("frame_*.png"))]
    boxes = [im.getchannel("A").getbbox() for im in raw]
    lo = lambda v: sorted(v)[int(len(v) * (1 - mi.CROP_PCT))]
    hi = lambda v: sorted(v)[int(len(v) * mi.CROP_PCT) - 1]
    box = (lo([b[0] for b in boxes]), lo([b[1] for b in boxes]), hi([b[2] for b in boxes]), hi([b[3] for b in boxes]))
    for f in mi.EXTRA_FRAMES:
        b = boxes[f]
        box = (min(box[0], b[0]), min(box[1], b[1]), max(box[2], b[2]), max(box[3], b[3]))
    side = int(max(box[2] - box[0], box[3] - box[1]) * mi.CROP_MARGIN)
    cx, cy = (box[0] + box[2]) // 2, (box[1] + box[3]) // 2
    return (cx - side // 2, cy - side // 2, cx - side // 2 + side, cy - side // 2 + side)


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    for g in ("icon", "banner", "promo-tile", "description"):
        (OUT / g).mkdir(parents=True)

    crop, bg = crop_box(), mi.background()
    icon = []
    for i in range(N):
        a = Image.open(mi.ICON / "frames60" / f"frame_{i:03d}.png").convert("RGBA").crop(crop).resize((mi.S, mi.S), Image.NEAREST)
        icon.append(mi.compose(a, bg).convert("RGB").resize((mi.GIF_SIZE, mi.GIF_SIZE), Image.NEAREST).convert("RGBA"))
    print(f"icon-animated-60fps.webp {save(icon, OUT / 'icon/icon-animated-60fps.webp'):.0f} KiB", flush=True)

    sys.path.insert(0, str(ROOT.parents[1] / "tools/Description-Kit/dev"))
    import make_promo as mp
    base, _ = mp.compose_static("timber", mp.TILES["timber"])
    base = base.convert("RGBA")
    pos = (mp.icon_x(mi.GIF_SIZE), (mp.TH * mp.S - mi.GIF_SIZE) // 2)
    tile = []
    for ic in icon:
        fr = base.copy()
        fr.alpha_composite(ic, pos)
        tile.append(fr)
    print(f"promo-tile-60fps.webp {save(tile, OUT / 'promo-tile/promo-tile-60fps.webp'):.0f} KiB", flush=True)

    (bg_name, bg_scale, _), *text_layers = mb.LAYERS
    bbg = mb.sprite(bg_name, bg_scale)
    text = [(mb.sprite(n, s), p) for n, s, p in text_layers]
    mask = np.array(mb.corner_mask()) > 0
    banner = []
    for i in range(N):
        fr = mb.compose(mb.art(mb.ICON / "banner_frames60" / f"frame_{i:03d}.png"), bbg, text)
        arr = np.array(fr.convert("RGBA"))
        arr[~mask] = 0
        banner.append(Image.fromarray(arr, "RGBA"))
    print(f"banner-animated-60fps.webp {save(banner, OUT / 'banner/banner-animated-60fps.webp'):.0f} KiB", flush=True)
    banner_still = Image.open(mi.DIST / "banner.png").convert("RGBA")
    banner_still.save(OUT / "banner/banner.webp", lossless=True, quality=100, method=6, exact=True)

    stills = [(mi.DIST / "icon-512.png", OUT / "icon/icon-512.webp"), (ROOT / "pack/pack.png", OUT / "icon/pack-128.webp")]
    for f in sorted((ROOT / "docs/desc").glob("*.png")):
        stills.append((f, OUT / "description" / (f.stem + ".webp")))
    for src, dst in stills:
        Image.open(src).convert("RGBA").save(dst, lossless=True, quality=100, method=6, exact=True)
    shutil.copy2(banner_still and mi.DIST / "icon-512.png", OUT / "icon/icon-512.png")
    for p in sorted(OUT.rglob("*")):
        if p.is_file():
            print(f"{p.relative_to(OUT)}  {p.stat().st_size / 1024:.0f} KiB")


if __name__ == "__main__":
    main()
