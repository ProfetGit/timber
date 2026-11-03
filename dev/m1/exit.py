#!/usr/bin/env python3
"""Timber 2.0 M1 exit (PRD 13): the mp cases m1 (no latency) and m1_200 (both clients 200 ms), then the GO/STOP table.

  python3 mods/Timber/dev/m1/exit.py              build the probe, run both cases (mt.py mp), report
  python3 mods/Timber/dev/m1/exit.py --no-run     report on the last runs' work dirs
  ... --cases m1                                   only these cases

Host = Timber client (MpHost: mesh_<tree>.tsv per drawn frame, trees.json), Guest = vanilla client + dev/probe
(<tree>.frames.tsv, <tree>.displays.tsv, frames/<tree>/). Both stamp each frame at the end of Minecraft.runTick with
the epoch clock, so their slams compare on one clock. The server logs "[perf] fall ..." per fall
(-Dvanilla_timber.perf=true); the last fall (spruce_solo) has only the modded Host in range.

Rule, fixed before any result (STATUS.md Next 1e, PRD 13):
  GO   every tree's |slam drift| <= 1 tick in both cases, `wb img flicker` clean at the chop on both clients' frames
       (PRD 13's words; the window: from the chop until the mesh's drawing time reaches 6, the end of the shiver;
       pops over the whole clip are reported too, as info: a fast thin crown tip crossing a tile in one frame looks
       like a pop to the detector),
       spruce client frame p95 <= 1 ms, server moving tick mean <= 0.2 ms with only modded viewers.
  STOP (switch to Lean, PRD 12) any |drift| > 2 ticks at 200 ms, or flicker that a fix can't remove.
  else FIX and re-run.
Writes dev/m1/exit.json.
"""
import argparse
import json
import math
import re
import statistics
import subprocess
import sys
from pathlib import Path

MOD = Path(__file__).resolve().parents[2]
WS = MOD.parents[1]
WORK = WS / "tools/ModTest/.work/Timber"
sys.path.insert(0, str(WS / "tools/Workbench"))
from wblib import img  # noqa: E402

TREES = ["oak", "birch", "spruce"]
TICK_US = 50_000


def rows(path: Path) -> list[list[str]]:
    lines = path.read_text().splitlines()
    return [l.split("\t") for l in lines[1:] if l.strip()]


def cross(pts: list[tuple[float, float]], v: float) -> float:
    """Time at which the value first reaches v, linear between samples; NaN if never."""
    prev = None
    for t, x in pts:
        if math.isnan(x):
            continue
        if x >= v:
            if prev is None:
                return t
            return prev[0] + (t - prev[0]) * (v - prev[1]) / (x - prev[1])
        prev = (t, x)
    return math.nan


def first_at(pts, v):
    return next((t for t, x in pts if not math.isnan(x) and x >= v), math.nan)


def probe_pitch(out: Path, tree: str) -> list[tuple[float, float]]:
    """(frame wall us, median display pitch) per probe frame; pitch = the display's local y axis from vertical."""
    wall = {int(r[0]): int(r[1]) for r in rows(out / f"{tree}.frames.tsv")}
    per = {}
    for r in rows(out / f"{tree}.displays.tsv"):
        m01, m11, m21 = float(r[5]), float(r[9]), float(r[13])
        n = math.sqrt(m01 * m01 + m11 * m11 + m21 * m21)
        if n > 1e-6:
            per.setdefault(int(r[0]), []).append(math.degrees(math.acos(max(-1.0, min(1.0, m11 / n)))))
    return [(wall[f], statistics.median(per[f]) if f in per else math.nan) for f in sorted(wall)]


def flicker(d: Path, window: tuple[int, int] | None = None) -> dict:
    """Pops in the frames; with a window (first, last frame index) only pops on frames inside it (neighbours from outside count)."""
    paths = sorted(d.glob("*.png"))
    if window:
        lo, hi = window
        paths = paths[max(0, lo - 1):hi + 2]
        off = max(0, lo - 1)
    if len(paths) < 3:
        return {"frames": len(paths), "pops": None}
    pops, n = img.flicker(paths)
    if window:
        for p in pops:
            p["frame"] += off
        pops = [p for p in pops if lo <= p["frame"] <= hi]
        n = hi - lo + 1
    top = [{"frame": p["frame"], "px": p["px"], "jump": p["jump"]} for p in pops[:5]]
    return {"frames": n, "pops": len(pops), "top": top}


def chop_window(walls: dict[int, int], t0: int, t1: int) -> tuple[int, int] | None:
    """First and last captured frame index with its wall clock in [t0, t1]."""
    inside = [i for i, w in walls.items() if t0 <= w <= t1]
    return (min(inside), max(inside)) if inside else None


def perf_lines(log: Path) -> list[dict]:
    out = []
    pat = re.compile(r"\[perf\] fall (\d+): (\d+) logs (\d+) leaves, chop ([\d.]+) ms, frame 0 ([\d.]+) ms, frame 1 ([\d.]+) ms, "
                     r"moving mean ([\d.]+) max ([\d.]+) ms over (\d+) ticks, finish ([\d.]+) ms, displays (\w+), payloads (\d+)")
    for m in pat.finditer(log.read_text(errors="replace")):
        g = m.groups()
        out.append({"id": int(g[0]), "logs": int(g[1]), "leaves": int(g[2]), "chop_ms": float(g[3]), "frame0_ms": float(g[4]),
                    "frame1_ms": float(g[5]), "moving_mean_ms": float(g[6]), "moving_max_ms": float(g[7]), "ticks": int(g[8]),
                    "finish_ms": float(g[9]), "displays": g[10] == "yes", "payloads": int(g[11])})
    return out


def case_report(case: str) -> dict:
    w = WORK / f"mp-{case}"
    host, guest = w / "out-host", w / "out-guest"
    res = json.loads((w / "results.json").read_text()) if (w / "results.json").exists() else {"results": []}
    failed = [r["name"] + ": " + r["detail"][:120] for r in res["results"] if not r["pass"]]
    trees = json.loads((host / "trees.json").read_text()) if (host / "trees.json").exists() else {}
    rep = {"case": case, "mp_failed": failed, "trees": {}}
    for t in TREES + ["spruce_solo"]:
        tr = {}
        info = trees.get(t, {})
        mesh_f = host / f"mesh_{t}.tsv"
        if mesh_f.exists() and info:
            mesh = [(int(r[0]), float(r[2])) for r in rows(mesh_f)]
            ns = [int(r[4]) / 1e6 for r in rows(mesh_f)]
            hit = info["hit"]
            tr.update(hit=hit, quads=info["quads"], bake_ms=info["bake_ms"], anchor_shift=info["anchor_shift"],
                      mesh_frames=len(mesh), frame_ms_p50=round(statistics.median(ns), 3) if ns else None,
                      frame_ms_p95=round(sorted(ns)[min(len(ns) - 1, int(0.95 * (len(ns) - 1) + 0.5))], 3) if ns else None,
                      frame_ms_max=round(max(ns), 3) if ns else None)
            if t != "spruce_solo" and (guest / f"{t}.frames.tsv").exists():
                pr = probe_pitch(guest, t)
                tm, tp = cross(mesh, hit - 0.5), cross(pr, hit - 0.5)
                fm, fp = first_at(mesh, hit - 0.5), first_at(pr, hit - 0.5)
                tr.update(probe_frames=len(pr), drift_ticks=round((tp - tm) / TICK_US, 3) if not math.isnan(tp - tm) else None,
                          drift_first_frame_ticks=round((fp - fm) / TICK_US, 3) if not math.isnan(fp - fm) else None,
                          probe_max_pitch=round(max((x for _, x in pr if not math.isnan(x)), default=math.nan), 2))
        win_h = win_g = None
        if mesh_f.exists() and info.get("chop_us") and (host / f"frames_{t}.tsv").exists():
            end = next((int(r[0]) for r in rows(mesh_f) if float(r[3]) >= 6), None)
            if end:
                t0, t1 = info["chop_us"] - 100_000, end
                tr["chop_window_ms"] = round((t1 - t0) / 1000, 1)
                win_h = chop_window({int(r[0]): int(r[1]) for r in rows(host / f"frames_{t}.tsv")}, t0, t1)
                if (guest / f"{t}.frames.tsv").exists():
                    win_g = chop_window({int(r[0]): int(r[1]) for r in rows(guest / f"{t}.frames.tsv") if int(r[0]) < 240}, t0, t1)
        tr["flicker_host_all"] = flicker(host / "frames" / f"mp_{t}")
        tr["flicker_host"] = flicker(host / "frames" / f"mp_{t}", win_h) if win_h else {"pops": None, "why": "no chop window"}
        if t != "spruce_solo":
            tr["flicker_guest_all"] = flicker(guest / "frames" / t)
            tr["flicker_guest"] = flicker(guest / "frames" / t, win_g) if win_g else {"pops": None, "why": "no chop window"}
        rep["trees"][t] = tr
    log = w / "server/server.log"
    rep["perf"] = perf_lines(log) if log.exists() else []
    return rep


def verdict(reports: dict) -> tuple[str, list[str]]:
    why, stop = [], False
    for case, rep in reports.items():
        for t in TREES:
            d = rep["trees"].get(t, {}).get("drift_ticks")
            if d is None:
                why.append(f"{case} {t}: no drift measured")
            elif abs(d) > 1:
                why.append(f"{case} {t}: drift {d} ticks > 1")
                if case == "m1_200" and abs(d) > 2:
                    stop = True
            for side in ("host", "guest"):
                fl = rep["trees"].get(t, {}).get(f"flicker_{side}", {})
                if fl.get("pops") is None:
                    why.append(f"{case} {t}: no {side} frames")
                elif fl["pops"]:
                    why.append(f"{case} {t}: {fl['pops']} flicker pops at the chop on the {side}")
        sp = rep["trees"].get("spruce", {}).get("frame_ms_p95")
        if sp is None or sp > 1.0:
            why.append(f"{case}: spruce client frame p95 {sp} ms > 1")
        solo = [p for p in rep["perf"] if not p["displays"]]
        if not solo:
            why.append(f"{case}: no modded-only fall in the server log")
        elif solo[-1]["moving_mean_ms"] > 0.2:
            why.append(f"{case}: modded-only moving tick {solo[-1]['moving_mean_ms']} ms > 0.2")
        if rep["mp_failed"]:
            why.append(f"{case}: mp checks failed: {'; '.join(rep['mp_failed'][:3])}")
    if stop:
        return "STOP", why
    return ("GO" if not why else "FIX"), why


def table(reports: dict):
    print(f"{'case':8} {'tree':12} {'drift':>7} {'1st-frm':>7} {'frm p95':>8} {'quads':>6} {'chop H/G':>9} {'all H/G':>9} {'shift':>6}")
    for case, rep in reports.items():
        for t, tr in rep["trees"].items():
            fh = tr.get("flicker_host", {}).get("pops")
            fg = tr.get("flicker_guest", {}).get("pops", "-")
            ah = tr.get("flicker_host_all", {}).get("pops")
            ag = tr.get("flicker_guest_all", {}).get("pops", "-")
            print(f"{case:8} {t:12} {str(tr.get('drift_ticks')):>7} {str(tr.get('drift_first_frame_ticks')):>7} "
                  f"{str(tr.get('frame_ms_p95')):>8} {str(tr.get('quads')):>6} {f'{fh}/{fg}':>9} {f'{ah}/{ag}':>9} {str(tr.get('anchor_shift')):>6}")
        for p in rep["perf"]:
            print(f"{case:8} server fall {p['id']}: {p['logs']} logs, chop {p['chop_ms']} ms, frame0 {p['frame0_ms']}, "
                  f"moving mean {p['moving_mean_ms']} max {p['moving_max_ms']} ms, displays {'yes' if p['displays'] else 'no'}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--no-run", action="store_true")
    ap.add_argument("--no-build", action="store_true")
    ap.add_argument("--cases", default="m1,m1_200")
    a = ap.parse_args()
    cases = a.cases.split(",")
    if not a.no_run:
        subprocess.run(["bash", str(MOD / "dev/probe/build.sh")], check=True)
        cmd = [sys.executable, str(WS / "tools/ModTest/mt.py"), "mods/Timber", "mp", "--case", ",".join(cases), "--no-retry"]
        if a.no_build:
            cmd.append("--no-build")
        subprocess.run(cmd, cwd=WS)
    reports = {c: case_report(c) for c in cases}
    table(reports)
    v, why = verdict(reports)
    print(f"\nM1 exit: {v}")
    for w in why:
        print("  -", w)
    (MOD / "dev/m1/exit.json").write_text(json.dumps({"verdict": v, "why": why, "cases": reports}, indent=1) + "\n")


if __name__ == "__main__":
    main()
