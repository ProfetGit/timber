#!/usr/bin/env python3
"""Writes the repetitive functions (tree-type tables, neighbour walks, leaf chains, decor state capture, fall curve).

Run after editing the lists below; the hand-written functions call into these files.
"""
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BASE = ROOT / "pack/data/timber/function"
OVER = ROOT / "pack/overlay_26_3/data/timber/function"

TYPES = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak"]
TYPES_26_3 = ["poplar"]
LEAVES = ["oak", "birch", "spruce", "jungle", "dark_oak", "acacia", "mangrove", "cherry", "azalea",
          "flowering_azalea", "pale_oak"]
LEAVES_26_3 = ["red_poplar", "orange_poplar", "yellow_poplar"]
LEAF_HINT = {"poplar": "yellow_poplar"}
OBJECTIVES = ["off", "config", "data", "job", "x", "y", "z", "lab", "ph", "e", "dr", "y0", "t", "p", "hit", "dur", "h", "b",
              "k", "u", "kr", "ks", "kf", "kx", "km",
              # pose runtime: last byte pitch step and residual sent, trunk bend (tip, centi-degrees) and its spring,
              # squash per group (x1000), lowest and highest ring, crown ring
              "qs", "xl", "bn", "bv", "bl", "pv", "wv", "gx", "gy", "gz", "lx", "lz", "kn", "kt", "yr"]

SIX = {"px": (1, 0, 0), "nx": (-1, 0, 0), "py": (0, 1, 0), "ny": (0, -1, 0), "pz": (0, 0, 1), "nz": (0, 0, -1)}
AXIS_SCORE = {0: "#cx", 1: "#cy", 2: "#cz"}


def write(path: Path, lines: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n")


def rel(v: int) -> str:
    return "~" if v == 0 else f"~{v}"


def at(d: tuple[int, int, int]) -> str:
    return " ".join(rel(v) for v in d)


def type_lines(types: list[str]) -> dict[str, list[str]]:
    load = [f"scoreboard objectives add timber.m.{t} minecraft.mined:minecraft.{t}_log" for t in types]
    load += [f'data modify storage timber:types {t} set value {{t:"{t}",log:"minecraft:{t}_log",'
             f'wood:"minecraft:{t}_wood",leaf:"minecraft:{LEAF_HINT.get(t, t)}_leaves"}}' for t in types]
    tick = [f'execute as @a[scores={{timber.m.{t}=1..}}] at @s run function timber:mined {{t:"{t}"}}' for t in types]
    reset = [f"scoreboard players reset @s timber.m.{t}" for t in types]
    uninstall = [f"scoreboard objectives remove timber.m.{t}" for t in types]
    uninstall += [f"data remove storage timber:types {t}" for t in types]
    return {"load": load, "tick": tick, "reset": reset, "uninstall": uninstall}


def gen_types() -> None:
    base = type_lines(TYPES)
    write(BASE / "load/types.mcfunction", base["load"])
    write(BASE / "tick/mined.mcfunction", base["tick"])
    write(BASE / "player/reset_stats.mcfunction", base["reset"] + ["function timber:compat/extra_reset"])
    write(BASE / "uninstall/types.mcfunction", base["uninstall"])
    extra = type_lines(TYPES_26_3)
    write(BASE / "compat/extra_load.mcfunction", ["return 0"])
    write(BASE / "compat/extra_tick.mcfunction", ["return 0"])
    write(BASE / "compat/extra_reset.mcfunction", ["return 0"])
    write(BASE / "compat/extra_uninstall.mcfunction", ["return 0"])
    write(OVER / "compat/extra_load.mcfunction", extra["load"])
    write(OVER / "compat/extra_tick.mcfunction", extra["tick"])
    write(OVER / "compat/extra_reset.mcfunction", extra["reset"])
    write(OVER / "compat/extra_uninstall.mcfunction", extra["uninstall"])
    write(BASE / "uninstall/objectives.mcfunction",
          ["scoreboard objectives remove timber"] + [f"scoreboard objectives remove timber.{o}" for o in OBJECTIVES])
    write(BASE / "load/objectives.mcfunction",
          ['scoreboard objectives add timber trigger {text:"Timber"}'] + [f"scoreboard objectives add timber.{o} dummy" for o in OBJECTIVES])


def gen_flood() -> None:
    lines = ["execute if score #logs timber.data >= #max_logs timber.config run return 0"]
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if (dx, dy, dz) == (0, 0, 0):
                    continue
                d = at((dx, dy, dz))
                lines.append(f"$execute positioned {d} if block ~ ~ ~ $(log) run function timber:flood/visit with storage timber:op t")
    # a creaking heart sits inside a pale oak trunk: walk through it so the trunk stays one piece
    for d in SIX.values():
        lines.append(f"execute positioned {at(d)} if block ~ ~ ~ minecraft:creaking_heart run function timber:flood/heart")
    write(BASE / "flood/neighbors.mcfunction", lines)


def step(fn_dir: str, name: str, d: tuple[int, int, int], target: str) -> None:
    axis = next(i for i, v in enumerate(d) if v)
    sc, v = AXIS_SCORE[axis], d[axis]
    add, sub = ("add", "remove") if v > 0 else ("remove", "add")
    write(BASE / fn_dir / f"{name}.mcfunction", [
        f"scoreboard players {add} {sc} timber.data 1",
        f"function {target}",
        f"scoreboard players {sub} {sc} timber.data 1",
    ])


def gen_leaves() -> None:
    # n<d>: from the current cell, walk into neighbouring natural leaves whose distance is exactly d
    for dist in range(1, 8):
        lines = []
        for name, d in SIX.items():
            lines.append(f"execute if block {at(d)} #minecraft:leaves[persistent=false,distance={dist}] "
                         f"positioned {at(d)} run function timber:leaves/s/{name}{dist}")
            step("leaves/s", f"{name}{dist}", d, f"timber:leaves/v{dist}")
        write(BASE / f"leaves/n{dist}.mcfunction", lines)
        v = ["execute if score #blocks timber.data >= #max_blocks timber.config run return 0",
             "function timber:leaves/take"]
        if dist < 7:
            v.append(f"function timber:leaves/n{dist + 1}")
        write(BASE / f"leaves/v{dist}.mcfunction", v)


def gen_decor() -> None:
    lines = []
    for name, d in SIX.items():
        lines.append(f"execute if block {at(d)} #timber:decor positioned {at(d)} run function timber:decor/s/{name}")
        step("decor/s", name, d, "timber:decor/take")
    write(BASE / "decor/around.mcfunction", lines)

    def bools(block: str, props: list[str]) -> list[str]:
        out = [f'data modify storage timber:op recs[-1].n set value "minecraft:{block}"']
        for p in props:
            out.append(f'execute if block ~ ~ ~ minecraft:{block}[{p}=true] run data modify storage timber:op recs[-1].p.{p} set value "true"')
        return out

    def enum(block: str, prop: str, values: list[str]) -> list[str]:
        return [f'execute if block ~ ~ ~ minecraft:{block}[{prop}={v}] run data modify storage timber:op recs[-1].p.{prop} set value "{v}"'
                for v in values]

    faces4 = ["north", "east", "south", "west"]
    states = {
        "vine": bools("vine", faces4 + ["up"]),
        "resin_clump": bools("resin_clump", faces4 + ["up", "down"]),
        "pale_hanging_moss": bools("pale_hanging_moss", ["tip"]),
        "cocoa": ['data modify storage timber:op recs[-1].n set value "minecraft:cocoa"']
                 + enum("cocoa", "facing", faces4) + enum("cocoa", "age", ["0", "1", "2"]),
        "mangrove_propagule": bools("mangrove_propagule", ["hanging"]) + enum("mangrove_propagule", "age", [str(i) for i in range(5)]),
        "snow": ['data modify storage timber:op recs[-1].n set value "minecraft:snow"']
                + enum("snow", "layers", [str(i) for i in range(1, 9)]),
        "creaking_heart": ['data modify storage timber:op recs[-1].n set value "minecraft:creaking_heart"']
                          + enum("creaking_heart", "axis", ["x", "y", "z"])
                          + enum("creaking_heart", "creaking_heart_state", ["uprooted", "dormant", "awake"]),
    }
    chain = []
    for block, body in states.items():
        write(BASE / f"decor/st/{block}.mcfunction", body)
        chain.append(f"execute if block ~ ~ ~ minecraft:{block} run return run function timber:decor/st/{block}")
    chain.append("function timber:compat/decor_state")
    write(BASE / "decor/state.mcfunction", chain)
    write(BASE / "compat/decor_state.mcfunction",
          ['data modify storage timber:op recs[-1].n set value "minecraft:air"'])
    shelf = (['execute unless block ~ ~ ~ minecraft:shelf_mushroom run return run data modify storage timber:op recs[-1].n set value "minecraft:air"',
              'data modify storage timber:op recs[-1].n set value "minecraft:shelf_mushroom"']
             + enum("shelf_mushroom", "facing", faces4) + enum("shelf_mushroom", "age", ["0", "1"]))
    write(OVER / "compat/decor_state.mcfunction", shelf)


def gen_leaf_chain() -> None:
    def chain(names: list[str]) -> list[str]:
        return [f'execute if block ~ ~ ~ minecraft:{n}_leaves run return run data modify storage timber:op recs[-1].n set value "minecraft:{n}_leaves"'
                for n in names] + ['data modify storage timber:op recs[-1].n set value "minecraft:oak_leaves"']
    write(BASE / "rec/leaf_chain.mcfunction", chain(LEAVES))
    write(OVER / "rec/leaf_chain.mcfunction", chain(LEAVES + LEAVES_26_3))


# anticipation: the tree leans back LEAN degrees (eased in and out) over LEAN_T ticks and holds HOLD_T ticks; the fall
# then starts from rest at -LEAN, so the angular velocity is continuous from the chop to the slam
LEAN, LEAN_T, HOLD_T = 10.0, 7, 0
# fall: a rod released at rest, torque ~ sin(angle + FALL_A0), from -LEAN to 90 degrees (the curve is time-normalised)
FALL_A0 = 25.0


def rod_from_rest(a_start: float, a_end: float, a0: float, n: int = 100) -> list[int]:
    """Normalised fall curve 0..10000 over n+1 evenly spaced times: theta'' = sin(theta + a0) from rest at a_start."""
    th, w, t, dt, pts = math.radians(a_start), 0.0, 0.0, 1e-5, []
    while th < math.radians(a_end):
        pts.append((t, th))
        w += math.sin(th + math.radians(a0)) * dt
        th += w * dt
        t += dt
    pts.append((t, math.radians(a_end)))
    total, span, out, j = t, math.radians(a_end - a_start), [], 0
    for i in range(n + 1):
        tt = total * i / n
        while j + 1 < len(pts) and pts[j + 1][0] <= tt:
            j += 1
        out.append(round((pts[j][1] - math.radians(a_start)) / span * 10000))
    out[-1] = 10000
    return out


FALL_DUR = range(10, 27)  # fall lengths in ticks (p8 clamps the tree's to this range)


def gen_curve() -> None:
    # one exact per-tick table per fall length, so every tick's step follows the curve (no rounding of the time axis)
    fine = rod_from_rest(-LEAN, 90.0, FALL_A0, 2000)
    tables = []
    for d in FALL_DUR:
        vals = [fine[round(2000 * t / d)] for t in range(d + 1)]
        tables.append(f"d{d}:[{','.join(str(v) for v in vals)}]")
    write(BASE / "load/curve.mcfunction", [f"data modify storage timber:curve fall set value {{{','.join(tables)}}}"])


def gen_lean() -> None:
    # #l counts ticks since the tree stood still (after the drop in the hang case); keyed pitch x100, eased in and out
    lines = ["execute if score #l timber.data matches ..0 run return 0",
             "execute if score #l timber.data matches 1 run function timber:anim/fx/creak"]
    for k in range(1, LEAN_T + 1):
        x = k / LEAN_T
        e = x * x * (3 - 2 * x)
        lines.append(f"execute if score #l timber.data matches {k} run scoreboard players set @s timber.p {round(-LEAN * 100 * e)}")
    lines.append(f"execute if score #l timber.data matches {LEAN_T + HOLD_T} run function timber:anim/fx/crack")
    lines.append(f"execute if score #l timber.data matches {LEAN_T + HOLD_T}.. run function timber:anim/phase {{ph:1}}")
    write(BASE / "anim/lean_back.mcfunction", lines)
    # the fall maps the curve from -LEAN to 90 degrees (pitch x100)
    # (to the landing angle when that is past level, so a tree tipping over an edge keeps its speed)
    write(BASE / "anim/fall_map.mcfunction", [
        "scoreboard players operation #fr timber.data = @s timber.hit",
        "scoreboard players operation #fr timber.data > #9000 timber.data",
        f"scoreboard players add #fr timber.data {round(LEAN * 100)}",
        "scoreboard players operation #f timber.data *= #fr timber.data",
        "scoreboard players operation #f timber.data /= #10000 timber.data",
        f"scoreboard players remove #f timber.data {round(LEAN * 100)}",
    ])


RINGS = range(-16, 96)  # rings (block heights above the pivot) the pose pass can bend; a ring outside stays rigid


def ring_name(k: int) -> str:
    return f"m{-k}" if k < 0 else str(k)


def gen_rings() -> None:
    # the pose pass walks the rings bottom to top (the bend is a chain: each ring starts where the one below ends);
    # one function per ring with its selector spelled out, so the hot path has no macros
    lines = [f"execute if score @s timber.kn matches ..{k} if score @s timber.kt matches {k}.. run function timber:anim/xf/k_{ring_name(k)}"
             for k in RINGS]
    write(BASE / "anim/xf/rings.mcfunction", lines)
    for k in RINGS:
        body = [f"scoreboard players set #k timber.data {k}", "function timber:anim/xf_ring",
                f"execute as @e[type=block_display,tag=timber.d,tag=!timber.pop,distance=..0.01,scores={{timber.k={k}}}] run function timber:anim/xf_one"]
        if k >= 0:
            body.append("function timber:anim/xf_step")
        write(BASE / f"anim/xf/k_{ring_name(k)}.mcfunction", body)


def gen_sincos() -> None:
    # yaw i*11.25 deg (byte-exact for entity rotation, so the counter-rotation cancels exactly): sin/cos x10000 for the
    # yaw-frame translation, and the Ry(yaw) quaternion (0, qs, 0, qc) that counter-rotates each block so the yawed
    # display still draws it axis-aligned
    lines = []
    for i in range(32):
        a = math.radians(i * 11.25)
        qs, qc = round(math.sin(a / 2), 6), round(math.cos(a / 2), 6)
        lines.append(f"execute if score #yi timber.data matches {i} run return run function timber:fall/sc "
                     f"{{s:{round(math.sin(a) * 10000)},c:{round(math.cos(a) * 10000)},qs:{float(qs)},qc:{float(qc)},"
                     f"qsi:{round(qs * 10000)},qci:{round(qc * 10000)}}}")
    write(BASE / "fall/sincos.mcfunction", lines)


if __name__ == "__main__":
    gen_types()
    gen_flood()
    gen_leaves()
    gen_decor()
    gen_leaf_chain()
    gen_curve()
    gen_lean()
    gen_rings()
    gen_sincos()
    print("generated")
