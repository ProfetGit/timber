# M2: the 1.3.6 TimberTest groups to port (2026-11-03)

Source: `packs/Timber/dev/test/TimberTest.java` (1783 lines, read by an Explore agent, not re-verified line by line).
Headless server, fake player `TimberTester`, Y = -59 (ground -60), 8 plots on a 64-block grid (`plot()` :611).
Each group becomes a named-assert scene in `selftest/` (assert labels below are 1.3.6's; keep them so the two runs
read side by side). Not in 1.3.6: `perf_spruce` (PRD 13 names it; 1.3.6 only logs the tick cost in `trees`).

## Run order (`run()` :713-758)
oak, swap (+ putBack, hideLeaves), gates, protection, neighbours, direction, stump, landing, modes, durability,
robustness, trees, decor, mangroves, hooks, anim, pose, settings. Only with `-Dscn`: paleoaks (16 pale_oak_creaking),
giants (4 mega trees). Preflight: "pack loaded (config defaults present)", "base pack runs alone", "version_id stored
for add-ons" (10306), "no requirement text without add-ons".

## Groups
- **oak** :760 — customTree(0,0,6,2,oak), iron axe, stand(-2,0,-90). "oak: world blocks replaced by displays";
  "one display per felled block, enclosed leaves included" (d = logs-1 + leaves-hidden); "no stand-in blocks left
  behind" (structure_void 0); "falls away from the player, tipped 33.75 deg" (|yaw-270| = 33.75); "leans back first"
  (min pitch in first 20 ticks in (-14,-7)); "lands flat on open ground" (pitch > 89.9); "two bounces" (2 dips); "lies
  still while the trunk pops apart" (>= 10 still ticks); "whole gag lasts 2.3-3.5 s" (46-70 ticks); "displays and
  controller gone at the end"; "every log dropped" (oak_log items within 24 = logs); "axe used 1 per log".
- **swap** :874 — fancy_oak at yaws -90, -67.5, 45. "swap at yaw X: every display sits exactly on its old block".
- **putBack** :841 (under swap) — "put-back: the chop tick ends with the displays spawned and visible blocks still in
  place, except the anchor cell"; "the next tick removes them, displays stay".
- **hideLeaves** :807 (under swap, `#hide_leaves=1`) — "no display for leaves shut in on all sides"; "only displayed
  leaves are put back"; "no stand-in blocks left behind". (1.3.6 shows all leaves by default.)
- **gates** :917 — birch per case. "gate: sneaking -> single log", "gate: bare hand -> single log", "gate: creative ->
  single log", "gate: toggle off -> single log" (logs-1 left, 0 displays); "require_axe off + shovel -> fells".
- **protection** :941 — (1) 4 placed oak logs + leaves: "placed: exactly the 5 placed logs tracked"; "player pillar ->
  only the chopped log"; "key removed when the log is mined". (2) fill-built cabin, no leaves: "cabin (no leaves): only
  the chopped log". (3) 35-log wall touching a canopy: "wall touching a canopy: chopping the wall takes one log";
  "felling the tree leaves the wall". (4) placed log platform on a trunk: "player logs attached to a trunk survive
  the felling".
- **neighbours** :1006 — overlapping oak canopies: "neighbour's trunk untouched" (6); "neighbour keeps its own
  leaves"; "felled tree's leaves gone"; "neighbour's leaves stay attached (no distance 7)". Log bridge: "felled tree's
  trunk gone"; "neighbour's trunk untouched" (7); "bridge split between the trees" (2-3 of 5). Dark oak pair: "chopped
  trunk fully felled"; "neighbour's trunk still standing".
- **direction** :1056 — "wall in the way: tree falls into the open instead" (yaw != 270, landed > 70); "boxed in: tree
  lands on the wall and bounces there" (landed 20-70).
- **stump** :1086 — oak h=8 cut at y+2: "cut mid-trunk: the stump stays" (2 logs); "the part above the cut falls"; "it
  hangs a block over the cut, then hinges at the cut" (pivot Y+3 then Y+2); "tips past level to rest on the ground"
  (landed 95-125); "stump afterwards: breaks log by log".
- **landing** :1110 — "cliff: tips past level down onto the lower ground" (landed > 110); spruce h=11 cut at 6: "high
  cut: the top slides off the stump and drops" (pivot falls >= 3); "the tree's displays ride the drop down with it and
  are gone when it ends"; orphan sweep: "the sweep kills a stray tree display, keeps the falling tree's and other
  packs' displays", "the sweep runs on its own every 5 s"; "hedge in the way: falls through leaves and lies flat".
  (The mod never saves displays, `EntityMixin`: the sweep asserts become "no Timber display survives a reload".)
- **modes** :1195 — "drops=1: logs appear at the player" (5 within 1.5); "animation off: instant, drops at the stump"
  (0 displays, 6 dropped within 3).
- **durability** :1219 — "worn axe: tree not felled, warning shown" (damage 245 start, chat "too worn"); "worn axe:
  survives with the vanilla single-block wear" (246); "unbreaking III: wear reduced" (diamond, mega_spruce, 0 < dmg <
  0.6 logs); "unbreakable: no wear".
- **robustness** :1248 — "two trees animate at once"; "both finish after a /reload mid-animation"; "both trees dropped
  their logs" (6 oak + 6 spruce within 30).
- **trees** :1267 — features oak, fancy_oak, birch, super_birch_bees, spruce, pine, mega_spruce, mega_pine,
  jungle_tree, mega_jungle_tree, acacia, dark_oak, cherry, pale_oak, pale_oak_creaking, azalea_tree, mangrove,
  tall_mangrove, swamp_oak, + red/orange/yellow_poplar on 26.3; netherite axe. "tree <f>: felled and animated": 0 logs
  left, leaves left <= detached + max(2, 3%), displays > 0, no air displays, finish - chop > 30 ticks, landed >= 85.
- **decor** :1352 — jungle tree with cocoa (age 2), vines, snow: "vines, cocoa and snow go with the tree"; "vines ride
  along as displays with their faces" (4 vine displays); "ripe cocoa drops beans" (>= 2).
- **mangroves** :1319 — 12 random mangrove/tall_mangrove: "mangroves: every random mangrove fully felled" (>= 8).
- **hooks** :1395 (fixture pack `dev/test/hookpack`) — "api/loaded runs after version_id is set"; "requires text
  rebuilt on /reload, not duplicated"; "hooks that don't return allow the fell"; "run as the player, at the chopped
  log's centre"; "return 1 cancels the fell, the chopped log still breaks"; "first returning hook ends the chain";
  "cancelled fell shows no action bar"; "a later hook can cancel after an earlier one falls through"; "run only after
  Timber's own checks pass"; "join hint shows add-on requirement"; "menu shows add-on requirement"; "removing the
  add-on clears its requirement and hooks". (PRD puts hooks in M4.)
- **anim** :1598 — "hang: at the chop the controller and tree stand one block up"; "hang: then both drop into the
  gap"; plunge x3 ("1 log mined", "3 logs mined", "2x2 trunk, 2 layers mined"): "controller reaches the ground", "tree
  is on the ground before it leans", "displays ride with the controller and it never rises", "it ends clean and every
  log drops"; "plunge: a cut above a stump doesn't fall through it"; "hang: a 2x2 trunk cut in one corner doesn't
  drop"; "tip: tips 33.75 deg to the open side" (yaw 236.25); "burst: at the slam the crown's leaves burst while the
  trunk still lies there"; "ripple: logs pop one ring at a time from the stump end to the tip"; "ripple: each ring
  throws its log as it pops"; "fair: the ground holds exactly the loot rolled at the chop, plus the chopped log";
  "fair: blocks changed mid-animation don't add or lose drops"; "upgrade: a 1.1.0 tree mid-animation drops its loot
  and ends"; "uninstall mid-fall: nothing left behind, every log still drops". (PRD's `fair` = these two.)
- **pose** :1500 — birch h=9 mid-fall: "pose: mid-fall the trunk sits exactly at its true pitch" (< 0.01, entity on the
  byte step); "pose: the bent trunk's rings stay flush" (gap < 0.15).
- **settings** :1756 — "settings menu renders" (13 lines); "max_logs clamped" (1024); "config survives /reload";
  "/trigger timber toggles off with a message"; "join hint is off by default"; "welcome hint"; "uninstall removes all
  objectives".
- **paleoaks**, **giants** — only with `-Dscn` (see above).

## Harness facts worth copying
- `chop()` = `gameMode.destroyBlock` then 2 ticks. A fall is traced once per server tick until its top display goes or
  a cap (oak 120, hang 160, plunge 200 ticks).
- Reads: block counts in a box (cx +- r, Y-1..Y+40), item entities by id in an AABB, displays by tag, chat/action bar
  from the fake connection. Pitch = entity xRot + transformation residual (`AnimTrace.rot`).
- Expected values: `leafCheck` (vanilla distance rule), `enclosedLeaves`, `lootOf` (loot rolled at the chop).

## Features M2 needs that 0.0.8 lacks
config file + `/timber` + `/trigger timber` (gates: toggle, sneak modes, require_axe, drops, animation, protect,
feedback), placed-log protection (saved data, place event), ownership split between touching trees, the crown rule
for builds, decor (vines/cocoa/snow/...), mangroves (roots stay), 2x2 giants, pale oak + creaking heart, cliff/high cut
landing (plunge already in the timeline?), drops=1 and animation off, /reload mid-fall.
