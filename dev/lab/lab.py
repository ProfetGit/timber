#!/usr/bin/env python3
"""Timber fall lab: film the felling animation in the real client and look at it frame by frame.

Takes are recorded with the pack-showcase recorder (the user's "Fabric 26.3" profile, off-screen, lockstep 60 fps, so
every tick is exactly 3 frames and sub-tick interpolation is visible) on a flat test world with four fixed trees, from
fixed cameras. Each take is aligned on the chop sound, so two builds can be compared frame for frame.

  lab.py rec TAG [--jar PATH] [--shots oak:side,spruce:fp] [--shaders] [-j 2]
      Build the pack (unless --jar), record every shot as <TAG>-<tree>-<cam>, then review it.
  lab.py review TAG [--shots ...]
      Frames, per-tick sheet, onion skin and motion graph for each shot: out/<TAG>-<shot>/.
  lab.py compare A B [--shots ...]
      Side-by-side sheets and MP4s of two tags, aligned on the chop: out/cmp-A-B/.

Trees: oak (6 logs), fancy (branchy oak), birch, spruce (2x2 giant). Cameras: side (perpendicular to the fall),
fp (the player's own view: chop, look up, follow), hero (low 3/4 in front of the fall).
"""
import argparse
import json
import math
import re
import shutil
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
LAB = ROOT / "dev/lab"
OUT = LAB / "out"
SHOWCASE = Path.home() / ".claude/skills/pack-showcase/scripts/showcase.py"
DUMPS = ROOT / "dev/capture/scene/data/tscene/function/tree"
PROBE = LAB / "probe/mod"
FPS = 60
TPF = 3  # frames per game tick at 60 fps

# trunk base (the log the player chops), trunk width, height above the base
TREES = {
    "oak": {"dump": "oak_c", "at": (0, -60, 0), "w": 1, "h": 7, "len": 4.6},
    "fancy": {"dump": "fancy_a", "at": (64, -60, 0), "w": 1, "h": 9, "len": 4.8},
    "birch": {"dump": "birch_a", "at": (0, -60, 64), "w": 1, "h": 8, "len": 4.6},
    "spruce": {"dump": "spruce_b", "at": (64, -60, 64), "w": 2, "h": 26, "len": 6.2},
    # vanilla features placed while the world is built (their shape is fixed once the world is cached)
    "acacia": {"feature": "acacia", "at": (128, -60, 0), "w": 1, "h": 8, "len": 4.8},
    "darkoak": {"feature": "dark_oak", "at": (128, -60, 64), "w": 2, "h": 9, "len": 5.0},
    "cherry": {"feature": "cherry", "at": (0, -60, 128), "w": 1, "h": 9, "len": 5.0},
    "jungle": {"feature": "mega_jungle_tree", "at": (64, -60, 128), "w": 2, "h": 16, "len": 6.0},
}
# (mangroves stand on roots and pale oaks' trunks sit off the feature origin, so the scripted chop misses them; the
# headless suite covers both)
CAMS = ["side", "fp", "hero"]
# the player stands west of the trunk facing east (yaw -90); on open ground Timber tips it 33.75 deg to the right
FALL_YAW = -56.25
D = (-math.sin(math.radians(FALL_YAW)), math.cos(math.radians(FALL_YAW)))  # fall direction (x, z)
P = (D[1], -D[0])  # perpendicular, away from the player's side
CHOP_T = 0.5


def world_spec() -> dict:
    cmds = ["forceload add -32 -32 223 191", "!sleep 12", "gamerule random_tick_speed 0", "gamerule spawn_mobs false",
            "kill @e[type=!player]"]
    for tree in TREES.values():
        x, y, z = tree["at"]
        if "feature" in tree:
            cmds.append(f"place feature minecraft:{tree['feature']} {x} {y} {z}")
            continue
        for line in (DUMPS / f"{tree['dump']}.mcfunction").read_text().splitlines():
            if line.startswith("setblock"):
                cmds.append(f"execute positioned {x} {y} {z} run {line}")
    cmds += ["kill @e[type=item]", "!sleep 2"]
    return {"name": "Timber lab", "seed": "timber-lab", "type": "minecraft:flat", "commands": cmds}


def centre(tree: dict) -> tuple:
    x, y, z = tree["at"]
    return x + tree["w"] / 2, y, z + tree["w"] / 2


def off(c: tuple, along: float, up: float, side: float = 0.0) -> list:
    return [round(c[0] + D[0] * along + P[0] * side, 2), round(c[1] + up, 2), round(c[2] + D[1] * along + P[1] * side, 2)]


def scene(tag: str, tname: str, cam: str, jar: Path, shaders: bool) -> dict:
    tree = TREES[tname]
    c = centre(tree)
    h, w = tree["h"], tree["w"]
    x, y, z = tree["at"]
    stand = [round(x - (2.6 if cam == "fp" else 1.25), 2), y, round(z + w / 2, 2)]
    base = [x + 0.02, y + 0.5, round(z + w / 2, 2)]
    length = tree["len"]
    script = {
        "fps": FPS, "length": length, "settle": 24 if shaders else 14, "sway": 0.0,
        "setup": ["gamerule advance_time false", "gamerule advance_weather false", "gamerule spawn_mobs false",
                  "time set 6000", "weather clear", "gamemode survival", "clear @s",
                  "item replace entity @s weapon.mainhand with minecraft:diamond_axe[minecraft:enchantments={\"minecraft:efficiency\":2}]",
                  f"tp @s {stand[0]} {stand[1]} {stand[2]} -90 20", "tag @s add timber.welcomed",
                  "scoreboard players set #feedback timber.config 0"],
        "camera": [{"t": -1, "look": base}],
        "events": [{"t": CHOP_T, "key": "attack", "down": True}, {"t": CHOP_T + 0.4, "key": "attack", "down": False},
                   {"t": CHOP_T + 0.6, "cmd": "execute as @e[type=marker,tag=timber.ctl] run data get entity @s Rotation[0]"}],
    }
    options = {}
    if cam == "side":
        # a long lens (40 deg) from the side: little perspective, so spacing and arcs read like an animator's side view
        options["fov"] = -0.75
        dist = 1.4 * h + 5
        mid = off(c, h * 0.42, h * 0.5)
        pos = off(c, h * 0.42, h * 0.5 + 0.5, dist)
        script["view"] = [{"t": -1, "pos": pos, "look": mid}]
        script["viewSway"] = 0
    elif cam == "hero":
        options["fov"] = -0.5
        pos = off(c, h * 1.05, 1.5, h * 0.6 + 1.5)
        look = off(c, h * 0.42, h * 0.3)
        script["view"] = [{"t": -1, "pos": pos, "look": look}]
        script["viewSway"] = 0
    else:  # the player's own eyes: chop, look up at the crown, follow the fall to where it lands
        script["camera"] += [
            {"t": CHOP_T + 0.55, "look": base},
            {"t": CHOP_T + 1.0, "look": off(c, h * 0.3, h * 0.45), "ease": "smooth"},
            {"t": CHOP_T + 2.0, "look": off(c, h * 0.55, 1.2), "ease": "smooth"},
            {"t": length, "look": off(c, h * 0.6, 0.8), "ease": "out"},
        ]
    return {
        "name": f"{tag}-{tname}-{cam}", "mc": "26.3", "size": [1280, 720],
        "exclude_mods": ["Timber-*.jar", "EnchantedTimber-*.jar"], "mods": [str(jar), str(PROBE / "timberprobe.jar")], "shaders": shaders, "options": options,
        "world": world_spec(), "script": script,
    }


def shots(arg: str) -> list:
    if not arg:
        return [(t, c) for t in TREES for c in CAMS]
    out = []
    for s in arg.split(","):
        t, _, c = s.partition(":")
        for tt in (TREES if t in ("", "*") else [t]):
            for cc in (CAMS if c in ("", "*") else [c]):
                out.append((tt, cc))
    return out


def build() -> Path:
    subprocess.run([sys.executable, str(ROOT / "dev/build.py")], check=True, stdout=subprocess.DEVNULL)
    jars = sorted((ROOT / "dist").glob("Timber-*-fabric.jar"), key=lambda p: p.stat().st_mtime)
    return jars[-1]


def rec(args) -> None:
    jar = Path(args.jar).resolve() if args.jar else build()
    if not (PROBE / "timberprobe.jar").exists():
        subprocess.run([str(PROBE / "build.sh")], check=True)
    print(f"jar: {jar.name}")
    (LAB / "scenes").mkdir(parents=True, exist_ok=True)
    paths = []
    for tname, cam in shots(args.shots):
        s = scene(args.tag, tname, cam, jar, args.shaders)
        p = LAB / "scenes" / f"{s['name']}.json"
        p.write_text(json.dumps(s, indent=1))
        paths.append(str(p))
    r = subprocess.run([sys.executable, str(SHOWCASE), *paths, "--root", str(LAB), "-j", str(args.jobs), "--keep-going"])
    review(args)
    if r.returncode:
        sys.exit(r.returncode)


# ---- review --------------------------------------------------------------------------------------------------------
def sounds(name: str) -> list:
    f = OUT / name / f"{name}.sounds.tsv"
    if not f.exists():
        return []
    rows = [l.split("\t") for l in f.read_text().splitlines() if l[:1].isdigit()]
    return [{"frame": int(r[0]), "id": r[1], "vol": float(r[4]), "pitch": float(r[5])} for r in rows]


def marks(name: str) -> dict:
    """Frames of the chop (Timber's own start sound) and the slam (the loud big_fall), from the client's sound log."""
    m = {}
    for s in sounds(name):
        if "chop" not in m and s["id"].endswith("block.wood.break") and abs(s["pitch"] - 0.6) < 0.01:
            m["chop"] = s["frame"]
        if "slam" not in m and s["id"].endswith("entity.generic.big_fall") and s["vol"] > 1.5:
            m["slam"] = s["frame"]
    log = LAB / ".work/game" / name / "client.log"
    if log.exists():
        yaws = re.findall(r"has the following entity data: (-?[\d.]+)f", log.read_text(errors="replace"))
        if yaws:
            m["yaw"] = float(yaws[-1])
        errs = [l for l in log.read_text(errors="replace").splitlines()
                if ("Failed to load function" in l or "/ERROR]" in l) and "flite" not in l and "user properties" not in l and "Realms" not in l and "narrator" not in l and "Update Checker" not in l]
        if errs:
            print(f"  {name}: {len(errs)} error line(s) in the client log, first: {errs[0][:200]}")
    return m


def frames(name: str, width: int = 640) -> list:
    d = OUT / name / "frames"
    mp4 = OUT / name / f"{name}.mp4"
    if not mp4.exists():
        return []
    if not d.exists() or d.stat().st_mtime < mp4.stat().st_mtime:
        shutil.rmtree(d, ignore_errors=True)
        d.mkdir()
        subprocess.run(["ffmpeg", "-v", "error", "-i", str(mp4), "-vf", f"scale={width}:-2", str(d / "f%04d.png")], check=True)
    return sorted(d.glob("f*.png"))


def sheet(ims: list, labels: list, cols: int, out: Path, scale: float = 0.5) -> None:
    w, h = int(ims[0].width * scale), int(ims[0].height * scale)
    rows = (len(ims) + cols - 1) // cols
    s = Image.new("RGB", (cols * w, rows * (h + 14)), (18, 18, 18))
    d = ImageDraw.Draw(s)
    for i, (im, lab) in enumerate(zip(ims, labels)):
        x, y = (i % cols) * w, (i // cols) * (h + 14)
        s.paste(im.resize((w, h), Image.LANCZOS), (x, y + 14))
        d.text((x + 3, y + 1), lab, fill=(255, 220, 90))
    s.save(out)


def motion(fr: list) -> np.ndarray:
    """Mean absolute change between consecutive frames (grey, 160 px wide): smooth motion gives a smooth curve;
    a freeze is a dip to ~0 inside the motion, a snap is a spike."""
    g = [np.asarray(Image.open(f).convert("L").resize((160, 90)), dtype=np.float32) for f in fr]
    return np.array([0.0] + [float(np.abs(a - b).mean()) for a, b in zip(g, g[1:])])


def plot_motion(series: dict, marks_by: dict, out: Path, title: str) -> None:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    fig, ax = plt.subplots(figsize=(12, 3.2), dpi=100)
    for lab, (x, y) in series.items():
        ax.plot(x, y, lw=1.2, label=lab)
    for lab, m in marks_by.items():
        for k, v in m.items():
            ax.axvline(v, ls=":", lw=0.8, color="grey")
            ax.text(v, ax.get_ylim()[1] * 0.92, f"{lab}:{k}", fontsize=7, rotation=90)
    ax.set_xlabel("ticks after the chop")
    ax.set_ylabel("frame-to-frame change")
    ax.set_title(title, fontsize=9)
    ax.legend(fontsize=8)
    fig.tight_layout()
    fig.savefig(out)
    plt.close(fig)


def review_one(name: str) -> None:
    fr = frames(name)
    if not fr:
        print(f"{name}: no clip")
        return
    m = marks(name)
    yaw = m.pop("yaw", None)
    chop = m.get("chop", int(CHOP_T * FPS))
    out = OUT / name
    # one frame per tick from 3 ticks before the chop
    idx = list(range(max(0, chop - 3 * TPF), len(fr), TPF))
    ims = [Image.open(fr[i]).convert("RGB") for i in idx]
    labels = [f"t{(i - chop) / TPF:+.0f}" + (" SLAM" if "slam" in m and abs(i - m["slam"]) < TPF else "") for i in idx]
    sheet(ims, labels, 8, out / "ticks.png", 0.4)
    # every frame around the slam (sub-tick interpolation, the stop and the bounce)
    if "slam" in m:
        s = m["slam"]
        idx2 = list(range(max(0, s - 12), min(len(fr), s + 30)))
        sheet([Image.open(fr[i]).convert("RGB") for i in idx2], [f"f{i - chop} ({(i - chop) / TPF:.2f}t)" for i in idx2], 6,
              out / "slam.png", 0.5)
    # onion skin of the fall: darkest pixel per position over one frame per tick (the sky is the brightest thing)
    a, b = chop, m.get("slam", chop + 40 * TPF) + 2 * TPF
    stack = [np.asarray(Image.open(fr[i]).convert("RGB"), dtype=np.int16) for i in range(a, min(b, len(fr)), TPF)]
    if stack:
        Image.fromarray(np.min(np.stack(stack), axis=0).astype(np.uint8)).save(out / "onion.png")
    e = motion(fr)
    x = [(i - chop) / TPF for i in range(len(e))]
    plot_motion({name: (x, e)}, {name: {k: (v - chop) / TPF for k, v in m.items()}}, out / "motion.png", name)
    np.save(out / "motion.npy", e)
    c = curves(name)
    if c:
        plot_curves({name: (c, chop, sound_marks(name))}, out / "curves.png", name)
        np.savez(out / "curves.npz", **c)
    print(f"{name}: {len(fr)} frames, marks {m}, fall yaw {yaw}, sheets in {out}")


def load_probe(name: str) -> dict:
    """Per-frame world matrices of every block display (from the probe mod), plus which ones are logs and leaves."""
    d, b = OUT / name / f"{name}.displays.tsv", OUT / name / f"{name}.blocks.tsv"
    if not d.exists():
        return {}
    kind = {}
    for line in b.read_text().splitlines()[1:]:
        i, blk = line.split("\t", 1)
        kind[int(i)] = "log" if ("_log" in blk or "_wood" in blk or "_stem" in blk) else ("leaf" if "leaves" in blk else "other")
    frames, extra = {}, {}
    for line in d.read_text().splitlines()[1:]:
        f = line.split("\t")
        fr, i = int(f[0]), int(f[3])
        frames.setdefault(fr, {})[i] = np.array([float(v) for v in f[4:16]]).reshape(3, 4)
        if len(f) > 17:
            extra.setdefault(fr, {})[i] = (float(f[16]), float(f[17]), int(f[1]), float(f[2]))
    return {"kind": kind, "frames": frames, "extra": extra}


def curves(name: str) -> dict:
    """Tree pose per rendered frame: fall angle (deg from upright, from the logs' own up axis), crown centroid, the
    top log's position and the logs' squash (block y scale along the trunk, z across it)."""
    pr = load_probe(name)
    if not pr:
        return {}
    kind, frames = pr["kind"], pr["frames"]
    logs0 = None
    out = {"frame": [], "angle": [], "tipy": [], "crowny": [], "crownd": [], "sy": [], "sz": [], "leafs": [], "n": [],
           "bend": [], "lgmin": [], "lgmax": [], "lfmin": []}
    fall = np.array([D[0], 0.0, D[1]])
    for fr in sorted(frames):
        ms = frames[fr]
        lg = [i for i in ms if kind.get(i) == "log"]
        lf = [i for i in ms if kind.get(i) == "leaf"]
        if not lg:
            continue
        if logs0 is None:
            logs0 = {i: ms[i][:, 3].copy() for i in lg}
        ups = np.array([ms[i][:, 1] for i in lg])
        up = np.median(ups, axis=0)
        sy = float(np.linalg.norm(up))
        up = up / sy
        ang = math.degrees(math.atan2(float(up @ fall), float(up[1])))
        cents = {i: ms[i] @ np.array([0.5, 0.5, 0.5, 1.0]) for i in ms}
        top = max(lg, key=lambda i: logs0.get(i, np.zeros(3))[1] if i in logs0 else -1e9)
        crown = np.mean([cents[i] for i in lf], axis=0) if lf else None
        out["frame"].append(fr)
        out["angle"].append(ang)
        out["tipy"].append(float(cents[top][1]))
        out["crowny"].append(float(crown[1]) if crown is not None else np.nan)
        out["crownd"].append(float((crown - cents[lg[0]]) @ np.array([fall[0], 0, fall[2]])) if crown is not None else np.nan)
        out["sy"].append(sy)
        out["sz"].append(float(np.median([np.linalg.norm(ms[i][:, 2]) for i in lg])))
        out["leafs"].append(float(np.median([np.linalg.norm(ms[i][:, 2]) for i in lf])) if lf else np.nan)
        out["n"].append(len(ms))
        order = sorted(lg, key=lambda i: logs0.get(i, np.zeros(3))[1] if i in logs0 else 1e9)
        def ang_of(i):
            u = ms[i][:, 1] / np.linalg.norm(ms[i][:, 1])
            return math.degrees(math.atan2(float(u @ fall), float(u[1])))
        # the bend is a shear (rings slide, all keep one orientation): measure the trunk line from the bottom log's
        # centre to the top log's centre against the logs' own axis, in the fall plane
        chord = cents[order[-1]][:3] - cents[order[0]][:3]
        out["bend"].append(math.degrees(math.atan2(float(chord @ fall), float(chord[1]))) - ang_of(order[0]) if len(order) > 1 else 0.0)
        sv = [np.linalg.svd(ms[i][:, :3], compute_uv=False) for i in lg]
        out["lgmin"].append(float(np.median([v.min() for v in sv])))
        out["lgmax"].append(float(np.median([v.max() for v in sv])))
        svl = [np.linalg.svd(ms[i][:, :3], compute_uv=False) for i in lf]
        out["lfmin"].append(float(np.median([v.min() for v in svl])) if svl else np.nan)
    return {k: np.array(v) for k, v in out.items()}


def plot_curves(takes: dict, out: Path, title: str) -> None:
    """takes: label -> (curves, chop frame, marks). Angle, angular speed, tip height and squash against ticks."""
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    fig, axs = plt.subplots(5, 1, figsize=(13, 13), dpi=100, sharex=True)
    for lab, (c, chop, m) in takes.items():
        if not c:
            continue
        t = (c["frame"] - chop) / TPF
        axs[0].plot(t, c["angle"], lw=1.3, label=lab)
        v = np.gradient(c["angle"]) * FPS
        axs[1].plot(t, v, lw=1.0, label=lab)
        axs[2].plot(t, c["tipy"] - c["tipy"][0], lw=1.2, label=f"{lab} top log")
        axs[2].plot(t, c["crowny"] - c["tipy"][0], lw=0.8, ls="--", label=f"{lab} crown")
        axs[3].plot(t, c["lgmin"], lw=1.0, label=f"{lab} log squash (min scale)")
        axs[3].plot(t, c["lgmax"], lw=1.0, ls=":", label=f"{lab} log stretch (max scale)")
        axs[3].plot(t, c["lfmin"], lw=0.8, ls="--", label=f"{lab} leaf squash")
        axs[4].plot(t, c["bend"], lw=1.0, label=f"{lab} trunk bend (top - bottom)")
        for k, f in m.items():
            for ax in axs:
                ax.axvline((f - chop) / TPF, color="grey", lw=0.6, ls=":")
            axs[0].text((f - chop) / TPF, 95, f"{lab}:{k}", fontsize=6, rotation=90, va="top")
    axs[0].set_ylabel("fall angle (deg)")
    axs[1].set_ylabel("angular speed (deg/s)")
    axs[2].set_ylabel("height change (blocks)")
    axs[3].set_ylabel("scale")
    axs[4].set_ylabel("bend (deg)")
    axs[4].set_xlabel("ticks after the chop sound (3 frames each)")
    for ax in axs:
        ax.grid(alpha=0.3)
        ax.legend(fontsize=7, loc="upper left")
    axs[0].set_title(title, fontsize=9)
    fig.tight_layout()
    fig.savefig(out)
    plt.close(fig)


def sound_marks(name: str) -> dict:
    """Frames of Timber's own sounds, for the curve plots."""
    names = {"wooden_door.open": "creak", "break_wooden_door": "crack", "attack.sweep": "whoosh", "chicken.egg": "pop"}
    m = {}
    for s_ in sounds(name):
        for k, v in names.items():
            if s_["id"].endswith(k) and v not in m:
                m[v] = s_["frame"]
    mk = marks(name)
    mk.pop("yaw", None)
    m.update(mk)
    return m


def review(args) -> None:
    for tname, cam in shots(args.shots):
        review_one(f"{args.tag}-{tname}-{cam}")


def compare(args) -> None:
    out = OUT / f"cmp-{args.a}-{args.b}"
    out.mkdir(parents=True, exist_ok=True)
    for tname, cam in shots(args.shots):
        na, nb = f"{args.a}-{tname}-{cam}", f"{args.b}-{tname}-{cam}"
        fa, fb = frames(na), frames(nb)
        if not fa or not fb:
            print(f"{tname}:{cam}: missing a take")
            continue
        ma, mb = marks(na), marks(nb)
        ma.pop("yaw", None), mb.pop("yaw", None)
        ca, cb = ma.get("chop", 30), mb.get("chop", 30)
        n = min(len(fa) - ca, len(fb) - cb)
        ims, labels = [], []
        for k in range(-3 * TPF, n, TPF):
            if ca + k < 0 or cb + k < 0:
                continue
            pa, pb = Image.open(fa[ca + k]).convert("RGB"), Image.open(fb[cb + k]).convert("RGB")
            both = Image.new("RGB", (pa.width * 2 + 4, pa.height))
            both.paste(pa, (0, 0))
            both.paste(pb, (pa.width + 4, 0))
            ims.append(both)
            labels.append(f"t{k // TPF:+d}  {args.a} | {args.b}")
        sheet(ims, labels, 4, out / f"{tname}-{cam}.png", 0.35)
        ea, eb = motion(fa), motion(fb)
        plot_motion({args.a: ([(i - ca) / TPF for i in range(len(ea))], ea), args.b: ([(i - cb) / TPF for i in range(len(eb))], eb)},
                    {args.a: {k: (v - ca) / TPF for k, v in ma.items()}, args.b: {k: (v - cb) / TPF for k, v in mb.items()}},
                    out / f"{tname}-{cam}-motion.png", f"{tname} {cam}: {args.a} vs {args.b}")
        plot_curves({args.a: (curves(na), ca, sound_marks(na)), args.b: (curves(nb), cb, sound_marks(nb))},
                    out / f"{tname}-{cam}-curves.png", f"{tname} {cam}: {args.a} vs {args.b}")
        # side-by-side video, both starting 0.5 s before their chop
        va, vb = OUT / na / f"{na}.mp4", OUT / nb / f"{nb}.mp4"
        sa, sb = max(0, ca / FPS - 0.5), max(0, cb / FPS - 0.5)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", f"{sa:.3f}", "-i", str(va), "-ss", f"{sb:.3f}", "-i", str(vb),
                        "-filter_complex", "[0:v]scale=960:-2[a];[1:v]scale=960:-2[b];[a][b]hstack=inputs=2,format=yuv420p[v]",
                        "-map", "[v]", "-c:v", "libx264", "-crf", "18", "-preset", "fast", "-shortest",
                        str(out / f"{tname}-{cam}.mp4")], check=True)
        print(f"{tname}:{cam}: {out / f'{tname}-{cam}.png'}")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("rec")
    r.add_argument("tag")
    r.add_argument("--jar")
    r.add_argument("--shots", default="")
    r.add_argument("--shaders", action="store_true")
    r.add_argument("-j", "--jobs", type=int, default=2)
    v = sub.add_parser("review")
    v.add_argument("tag")
    v.add_argument("--shots", default="")
    c = sub.add_parser("compare")
    c.add_argument("a")
    c.add_argument("b")
    c.add_argument("--shots", default="")
    args = ap.parse_args()
    {"rec": rec, "review": review, "compare": compare}[args.cmd](args)


if __name__ == "__main__":
    main()
