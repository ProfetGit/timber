#!/usr/bin/env python3
"""Samples the 1.3.6 data pack's fall controller every tick for the core JUnit fixtures.

Runs packs/Timber's headless harness (read-only: SKIP_BUILD, work dir outside the pack) on the fixed lab trees and
writes src/test/resources/fixtures/136/<tree>.json: the fall parameters the pack chose, then per tick the controller's
phase, pitch, bend, squash and height, as the pack's scores hold them at the end of that tick.

  python3 dev/m1/sample136.py [work-dir] [tree ...]

The f_<feature> trees are the per-feature fixtures of dev/fixtures/trees (scene `capture`); they are copied into the
work copy of the scene pack as tscene:tree/f_<feature>. Without tree names every tree is sampled.
"""
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

MOD = Path(__file__).resolve().parents[2]
PACK = MOD.parents[1] / "packs/Timber"
SCENE = PACK / "dev/capture/scene"
OUT = MOD / "src/test/resources/fixtures/136"
TREES = {"oak": "oak_c", "birch": "birch_a", "spruce": "spruce_b"}
FIXTURES = MOD / "dev/fixtures/trees"
for _f in sorted(FIXTURES.glob("f_*.mcfunction")):
    TREES[_f.stem] = _f.stem
START = ["hit", "dur", "h", "b", "km", "kn", "kt", "fd", "ft", "dr"]
TICK = ["t", "ph", "p", "bn", "gx", "gy", "gz", "lx", "lz", "qs", "xl", "kr", "kf"]
TICKS = 140
CTL = "@e[type=marker,tag=timber.ctl,limit=1]"
STEP = ["tick step 1", "!tick 4"]


def script(fn: str) -> str:
    lines = ["forceload add -16 -16 64 64", "gamerule random_tick_speed 0", "!player", "!tick 60",
             f"execute positioned 8 -60 8 run function tscene:tree/{fn}", "!tick 20",
             "item replace entity TimberTester weapon.mainhand with minecraft:netherite_axe",
             "gamemode survival TimberTester", "tag TimberTester add timber.welcomed",
             "tp TimberTester 6.75 -60 8.5 -90 20", "!tick 2", "tick freeze", "!tick 2", "!destroy 8 -60 8"]
    # frozen, so the queries of one sample read one tick: each step runs one tick, then the state holds
    lines += STEP
    lines += [f"scoreboard players get {CTL} timber.{s}" for s in START]
    lines += [f"data get entity {CTL} Pos", f"data get entity {CTL} Rotation[0]", f"data get entity {CTL} data.rs"]
    for _ in range(TICKS):
        lines += STEP
        lines += [f"scoreboard players get {CTL} timber.{s}" for s in TICK]
        lines.append(f"data get entity {CTL} Pos[1] 1000")
    return "\n".join(lines) + "\n"


def parse(log: str) -> dict:
    # every query prints "[EXPLORE] > <cmd>" then its feedback; a missing controller prints an error line instead
    vals = []
    cur = None
    for line in log.splitlines():
        m = re.search(r"\[EXPLORE\] > (.*)", line)
        if m:
            cur = m.group(1)
            vals.append([cur, None])
            continue
        m = re.search(r"\[EXPLORE\]\s{5}(.*)", line)
        if m and vals and vals[-1][1] is None:
            vals[-1][1] = m.group(1)
    def num(text):
        if text is None:
            return None
        m = (re.search(r"has (-?\d+) \[", text) or re.search(r"entity data: (-?[\d.]+)[df]?$", text)
             or re.search(r"scale factor of [\d.]+ is (-?\d+)$", text))
        return float(m.group(1)) if m else None
    first = next(i for i, v in enumerate(vals) if v[0].endswith("timber.hit"))
    it = iter(v for v in vals[first:] if not v[0].startswith("tick "))
    out = {"start": {}, "ticks": []}
    for s in START:
        out["start"][s] = num(next(it)[1])
    pos = next(it)[1]
    out["start"]["pos"] = [float(v) for v in re.findall(r"(-?[\d.]+)d", pos or "")]
    out["start"]["yaw"] = num(next(it)[1])
    out["start"]["rs"] = num(next(it)[1])
    for _ in range(TICKS):
        row = {s: num(next(it)[1]) for s in TICK}
        row["y"] = num(next(it)[1])
        if row["p"] is None:
            break
        out["ticks"].append(row)
    return out


def write_starts():
    """The self-test's scan scene compares 2.0's scan with these starts: they ship in the jar next to the fixtures."""
    for j in sorted(OUT.glob("f_*.json")):
        start = json.loads(j.read_text())["start"]
        (FIXTURES / f"{j.stem}.start.json").write_text(json.dumps(start, indent=1) + "\n")


def main():
    work = Path(sys.argv[1] if len(sys.argv) > 1 else os.environ.get("TMPDIR", "/tmp")) / "timber136"
    only = sys.argv[2:]
    if " " in str(work):
        raise SystemExit("work dir must not contain spaces (run.sh)")
    OUT.mkdir(parents=True, exist_ok=True)
    scene = work / "scene"  # run.sh splits EXTRA_PACKS on spaces: a copy at a path without them
    if not scene.exists():
        shutil.copytree(SCENE, scene)
    for fx in FIXTURES.glob("f_*.mcfunction"):
        shutil.copy(fx, scene / "data/tscene/function/tree" / fx.name)
    for tree, fn in TREES.items():
        if only and tree not in only:
            continue
        f = work / f"{tree}.txt"
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(script(fn))
        env = {**os.environ, "SKIP_BUILD": "1", "EXTRA_PACKS": str(scene)}
        r = subprocess.run(["bash", str(PACK / "dev/test/run.sh"), "26.3", str(work / f"w-{tree}"), "explore", str(f)],
                           env=env, capture_output=True, text=True)
        data = parse(r.stdout)
        if not data["ticks"]:
            print(r.stdout[-3000:])
            raise SystemExit(f"{tree}: no samples")
        (OUT / f"{tree}.json").write_text(json.dumps(data, indent=1))
        print(f"{tree}: {len(data['ticks'])} ticks, start {data['start']}")
    write_starts()


if __name__ == "__main__":
    main()
