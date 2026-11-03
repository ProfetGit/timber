# 1.3.6 world state (for M4 `migrate`), read from `packs/Timber/pack/data/timber/function`, 2026-11-03

## `storage timber:placed` (the only storage to import)
- `{o|n|e|x: {"cx,cz": {"x,y,z": 1b}}}`; dim key `placed/dim.mcfunction:1-4` (o overworld, n nether, e end, x every
  other dimension: can't be mapped back, skip). The chunk key is scoreboard `/16` (truncates toward zero, not a real
  chunk coord): ignore it, parse the inner `"x,y,z"` keys.
- Recorded by advancement `placed_log` (placed_block on `#timber:logs`) -> `placed/on_place` -> `placed/add`. Only
  `find/probe:8` removes a key (a player chopping that log); fire/explosions leave stale keys (2.0 drops them lazily).

## `timber.config` scores (fake players; defaults `config/defaults`, clamps `config/validate`)
max_logs 256 (16..1024), max_blocks 900 (100..1200), radius 12 (4..32), require_axe 1, sneak 0 (0 sneak = one log,
1 sneak to fell, 2 always), durability 1, animation 1, drops 0 (0 where it lands, 1 at player), protect 1, feedback 1,
welcome 0, hide_leaves 0; `stage` is internal (skip). Import only keys `timber.json` doesn't set.

## Player toggle
- `timber.off` score per player, 1 = off (`player/toggle.mcfunction:1-5`, trigger 1 toggle, 2 on, 3 off;
  `find/gates.mcfunction:1`). Score holders are player names: map name -> UUID via the server's profile cache.

## Falls in progress
- Controller: `marker` tagged `timber.ctl` at the pivot (= stump/drop point; plunge moves it onto the pivot), rotation
  [yaw,0]. Loot lives in the marker's `data` (`disp/start.mcfunction:35` copies `timber:op cd`; `disp/data.mcfunction:2-4`):
  `drops` (log items, each with a `y`) and `ldrops` (leaf/attached items), plus put, leaf, log, yaw, owner. No item
  entities exist mid-fall (`drops/stash` absorbs and kills them).
- Displays: `block_display` tagged `timber.d` (also `timber.lg`/`timber.lf`), saved with the world. Helper markers
  `timber.pt`, `timber.anc`, `timber.cur/job/m/heart/origin/next/front` are transient but may be saved.
- 1.3.6 has no stop/unload hook: a saved fall resumes from its NBT on the next load (untested), frozen while its chunk
  is unloaded. Entities in chunks not loaded at server start can't be found then: clean them on entity load.

## Objectives, storages, tags to remove
- Objectives (`load/objectives.mcfunction`): `timber` (trigger), `timber.off`, `timber.config`, `timber.data`,
  `timber.job`, `timber.x/y/z`, `timber.lab`, `timber.ph`, `timber.e`, `timber.dr`, `timber.y0`, `timber.t`, `timber.p`,
  `timber.hit`, `timber.dur`, `timber.h`, `timber.b`, `timber.k`, `timber.u`, `timber.kr ks kf kx km qs xl bn bv bl pv wv
  gx gy gz lx lz kn kt yr fd ft`, `timber.m.<type>` (9 mined stats). Rule: every objective whose name starts `timber.`
  or is exactly `timber`. 2.0's own `/trigger timber` objective (`server/Players`) must survive or be re-created.
- Storages: `timber:placed`, `timber:types`, `timber:curve`, `timber:menu`, `timber:op`, `timber:anim`; keep `timber:meta`.
- Player tags: `timber.welcomed` (2.0 uses `vanilla_timber.welcomed`: carry it over), `finder`, `cutter`, `owner`,
  `claimed`, `ours`, `p`, `dropped`, `whoosh`, `g` (all `timber.`-prefixed).

## Detecting the old pack
- Its `#minecraft:tick` has `timber:tick`, `timber:anim/run`, `timber:job/p1..p9`; `#minecraft:load` has `timber:load`.
- As a zip in `datapacks/`: id `file/Timber-1.3.6.zip` (any name). As `Timber-1.3.6-fabric.jar`: mod id
  `vanilla_timber`, the same as 2.0, so the 2.0 jar replaces it and there's no pack to disable.
- Probe used by ModJar smoke: `data get storage timber:meta version`.

## 1.3.6 tests touching persistence (`packs/Timber/dev/test/TimberTest.java`)
- None saves or restarts mid-fall. Nearest: robustness ~1248 (/reload mid-animation), uninstall mid-fall ~1750,
  config survives /reload ~1767, placed keys ~940-962.
