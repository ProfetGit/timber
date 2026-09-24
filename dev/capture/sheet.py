#!/usr/bin/env python3
"""Contact sheet of captured frames: sheet.py <frames-dir> <out.png> [first] [count] [crop: x y w h] [scale]"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw

frames = sorted(Path(sys.argv[1]).glob("t*.png"))
out = Path(sys.argv[2])
first = int(sys.argv[3]) if len(sys.argv) > 3 else 0
count = int(sys.argv[4]) if len(sys.argv) > 4 else 48
crop = tuple(int(v) for v in sys.argv[5:9]) if len(sys.argv) > 8 else (320, 150, 640, 420)
scale = float(sys.argv[9]) if len(sys.argv) > 9 else 0.5
sel = frames[first:first + count]
if not sel:
    sys.exit(f"no frames in {sys.argv[1]}")
x, y, w, h = crop
tw, th = int(w * scale), int(h * scale)
cols = 6
rows = (len(sel) + cols - 1) // cols
sheet = Image.new("RGB", (cols * tw, rows * (th + 14)), (20, 20, 20))
d = ImageDraw.Draw(sheet)
t0 = int(sel[0].stem[1:])
for i, f in enumerate(sel):
    im = Image.open(f).convert("RGB").crop((x, y, x + w, y + h)).resize((tw, th), Image.NEAREST)
    cx, cy = (i % cols) * tw, (i // cols) * (th + 14)
    sheet.paste(im, (cx, cy + 14))
    d.text((cx + 3, cy + 1), f"{first + i}: +{int(f.stem[1:]) - t0}", fill=(230, 230, 230))
sheet.save(out)
print(f"{out} ({len(sel)} frames, {sheet.size[0]}x{sheet.size[1]})")
