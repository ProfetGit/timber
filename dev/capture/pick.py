#!/usr/bin/env python3
"""Big-frame sheet: pick.py <frames-dir> <out.png> <frame indices...>  (3 columns, centre crop)"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw

fr = sorted(Path(sys.argv[1]).glob("t*.png"))
pick = [int(a) for a in sys.argv[3:]]
tw, th = 520, 292
rows = (len(pick) + 2) // 3
sheet = Image.new("RGB", (3 * tw, rows * th))
d = ImageDraw.Draw(sheet)
for i, k in enumerate(pick):
    im = Image.open(fr[k]).convert("RGB").crop((160, 60, 1120, 600)).resize((tw, th))
    sheet.paste(im, ((i % 3) * tw, (i // 3) * th))
    d.text(((i % 3) * tw + 4, (i // 3) * th + 4), str(k), fill=(255, 255, 0))
sheet.save(sys.argv[2])
