#!/usr/bin/env python3
"""Real-time preview GIF from captured frames: gif.py <frames-dir> <out.gif> [first] [count] [width] [crop: x y w h]
One frame per game tick at 50 ms, so it plays at real speed."""
import sys
from pathlib import Path
from PIL import Image

frames = sorted(Path(sys.argv[1]).glob("t*.png"))
out = Path(sys.argv[2])
first = int(sys.argv[3]) if len(sys.argv) > 3 else 0
count = int(sys.argv[4]) if len(sys.argv) > 4 else len(frames)
width = int(sys.argv[5]) if len(sys.argv) > 5 else 640
crop = tuple(int(v) for v in sys.argv[6:10]) if len(sys.argv) > 9 else None
sel = frames[first:first + count]
if not sel:
    sys.exit(f"no frames in {sys.argv[1]}")
ims = []
for f in sel:
    im = Image.open(f).convert("RGB")
    if crop:
        x, y, w, h = crop
        im = im.crop((x, y, x + w, y + h))
    ims.append(im.resize((width, round(im.height * width / im.width)), Image.LANCZOS))
pal = ims[len(ims) // 2].quantize(colors=255, method=Image.Quantize.MEDIANCUT)
q = [im.quantize(palette=pal, dither=Image.Dither.NONE) for im in ims]
q[0].save(out, save_all=True, append_images=q[1:], duration=50, loop=0, optimize=True, disposal=1)
print(f"{out} {len(q)} frames, {out.stat().st_size // 1024} KiB")
