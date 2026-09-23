#!/usr/bin/env python3
"""Render a felling trace (from the harness `!anim N file.json` directive) as a side-view GIF.

Reproduces the client's display math: vertex = pivot + Ry(-yaw) Rx(pitch) (T + Ry(yaw) p), p in the unit cube,
viewed from the side of the fall direction. Usage: render_fall.py trace.json out.gif
"""
import json
import math
import sys

from PIL import Image, ImageDraw

SCALE, W, H = 14, 640, 420
COLORS = {"leaves": (70, 140, 60), "_log": (120, 85, 50), "vine": (60, 110, 40), "other": (180, 160, 90)}


def color(block: str) -> tuple[int, int, int]:
    name = block.split("[")[0]
    for key, c in COLORS.items():
        if key in name:
            return c
    return COLORS["other"]


def local_to_view(q, pitch):
    x, y, z = q
    c, s = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    return x, y * c - z * s, y * s + z * c


def main() -> None:
    tr = json.load(open(sys.argv[1]))
    yaw = tr["rot"][0][0]
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    corners = [(a, b, c) for a in (0, 1) for b in (0, 1) for c in (0, 1)]
    # the block's own cube is counter-rotated by Ry(yaw) (left_rotation) so it stays world-aligned at pitch 0
    cube = [(a * cy + c * sy, b, -a * sy + c * cy) for a, b, c in corners]
    blocks = [(b["b"], b["t"]) for b in tr["blocks"]]
    # the server applies the past-level tilt at once and holds; the client lerps it over the hold ticks
    rot = [list(r) for r in tr["rot"]]
    for i in range(1, len(rot)):
        if rot[i][1] - rot[i - 1][1] > 20:
            j = i
            while j + 1 < len(rot) and abs(rot[j + 1][1] - rot[i][1]) < 0.01:
                j += 1
            start, end, n = rot[i - 1][1], rot[i][1], j - i + 1
            for k in range(n):
                rot[i + k][1] = start + (end - start) * (k + 1) / n
    tr["rot"] = rot
    frames = []
    for i, (fyaw, pitch, removed) in enumerate(tr["rot"]):
        img = Image.new("RGB", (W, H), (200, 225, 245))
        d = ImageDraw.Draw(img)
        ground = H - 90
        if not removed:
            polys = []
            for name, t in blocks:
                pts = [local_to_view((t[0] + r[0], t[1] + r[1], t[2] + r[2]), pitch) for r in cube]
                depth = sum(p[0] for p in pts) / 8
                xy = [(W * 0.25 + p[2] * SCALE, ground - p[1] * SCALE) for p in pts]
                polys.append((depth, name, xy))
            for depth, name, xy in sorted(polys, key=lambda p: p[0]):
                hull = convex_hull(xy)
                d.polygon(hull, fill=color(name), outline=tuple(max(0, v - 35) for v in color(name)))
        d.rectangle([0, ground, W, H], fill=(95, 160, 70))
        d.text((8, 8), f"tick {i:3d}  pitch {pitch:6.1f}{'  POOF' if removed else ''}", fill=(0, 0, 0))
        frames.append(img)
    frames[0].save(sys.argv[2], save_all=True, append_images=frames[1:] + [frames[-1]] * 10, duration=50, loop=0)
    sheet = Image.new("RGB", (W // 2 * 6, H // 2 * ((len(frames) + 5) // 6)), "white")
    for i, f in enumerate(frames):
        sheet.paste(f.resize((W // 2, H // 2)), ((i % 6) * W // 2, (i // 6) * H // 2))
    sheet.save(sys.argv[2].rsplit(".", 1)[0] + "_sheet.png")


def convex_hull(points):
    pts = sorted(set(points))
    if len(pts) <= 2:
        return pts

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])

    lower, upper = [], []
    for p in pts:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], p) <= 0:
            lower.pop()
        lower.append(p)
    for p in reversed(pts):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], p) <= 0:
            upper.pop()
        upper.append(p)
    return lower[:-1] + upper[:-1]


if __name__ == "__main__":
    main()
