"""Writes giant sequoia-style fixtures (Arboria grows 80+ block sequoias) for the giants_xl perf group:
trees/sequoia_3x3.mcfunction and trees/sequoia_4x4.mcfunction, relative to the trunk's north-west ground cell."""
import math
import os
from collections import deque

OUT = os.path.join(os.path.dirname(__file__), "trees")


def tree(w, height, crown_from, r_base):
    logs = {(x, y, z) for x in range(w) for z in range(w) for y in range(height)}
    c = (w - 1) / 2.0
    # four short branches every 9 blocks in the crown, like a sequoia's stubby limbs
    for y in range(crown_from, height - 6, 9):
        for i, (dx, dz) in enumerate(((1, 0), (0, 1), (-1, 0), (0, -1))):
            yy = y + i * 2
            for s in range(1, 4):
                x = round(c + dx * (w / 2.0 + s - 0.5))
                z = round(c + dz * (w / 2.0 + s - 0.5))
                logs.add((x, yy, z))
    leaves = set()
    for y in range(crown_from, height + 3):
        f = (y - crown_from) / max(1, height + 2 - crown_from)
        r = max(1.5, r_base * (1 - f) + 1.5 * f) + w / 2.0
        for x in range(-12, w + 12):
            for z in range(-12, w + 12):
                if (x, y, z) in logs:
                    continue
                d = math.hypot(x - c, z - c)
                if d <= r and (d >= r - 2.5 or (x * 7 + y * 3 + z * 5) % 4 != 0):
                    leaves.add((x, y, z))
    # leaf distance: BFS from logs over leaves, keep <= 6
    dist = {}
    q = deque()
    for p in logs:
        q.append((p, 0))
    seen = set(logs)
    while q:
        (x, y, z), d = q.popleft()
        for n in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
            if n in leaves and n not in seen and d + 1 <= 6:
                seen.add(n)
                dist[n] = d + 1
                q.append((n, d + 1))
    return logs, dist, c


def axis(p, w):
    x, y, z = p
    if 0 <= x < w and 0 <= z < w:
        return "y"
    return "x" if x < 0 or x >= w else "z"


def write(name, w, height, crown_from, r_base):
    logs, dist, c = tree(w, height, crown_from, r_base)
    lines = []
    for x in range(-8, w + 8):
        for z in range(-8, w + 8):
            lines.append(f"setblock ~{x} ~-1 ~{z} minecraft:podzol[snowy=false] strict")
    for (x, y, z) in sorted(logs, key=lambda p: (p[1], p[0], p[2])):
        lines.append(f"setblock ~{x} ~{y} ~{z} minecraft:spruce_log[axis={axis((x, y, z), w)}] strict")
    for (x, y, z), d in sorted(dist.items(), key=lambda e: (e[0][1], e[0][0], e[0][2])):
        lines.append(f"setblock ~{x} ~{y} ~{z} minecraft:spruce_leaves[distance={d},persistent=false,waterlogged=false] strict")
    with open(os.path.join(OUT, name + ".mcfunction"), "w") as f:
        f.write("\n".join(lines) + "\n")
    print(name, "logs", len(logs), "leaves", len(dist), "lines", len(lines))


if __name__ == "__main__":
    write("sequoia_3x3", 3, 90, 40, 6)
    write("sequoia_4x4", 4, 90, 40, 7)
