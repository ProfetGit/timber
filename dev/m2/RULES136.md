# 1.3.6 rules for M2 waves 3-4 (read from the pack by an Explore agent, link 9, 2026-11-03; code reading, not run)

Paths: `packs/Timber/pack/data/timber/function/...` unless noted; gen = `packs/Timber/dev/gen.py`; tests =
`packs/Timber/dev/test/TimberTest.java`.

## Defaults and limits
- `config/defaults.mcfunction`: max_logs 256, max_blocks 900, radius 12, require_axe 1, sneak 0, durability 1,
  animation 1, drops 0, protect 1, feedback 1, welcome 0, hide_leaves 0. Clamps (`config/validate`): max_logs 16..1024,
  max_blocks 100..1200, radius 4..32. Menu offers max_logs 64/128/256/512.
- max_logs: the flood stops at the cap (`flood/visit:1`, `neighbors:1`); logs past it stay standing (partial fell,
  no abort). The settings text says "Bigger trees are left alone (only the one log breaks)": conflict, no test.
- max_blocks counts logs + leaves + decor; leaves/decor past it stay. radius: horizontal box x/z +- radius around the
  cut, no vertical limit.

## Placed-log protection (protect 1)
- Advancement `placed_log.json` (placed_block in #timber:logs) -> `placed/on_place` (revokes itself). A ray from the
  eyes (0.05 steps, 8 blocks) marks the first #timber:logs block hit (the looked-at log, not necessarily the placed
  one: pack limitation). Storage `timber:placed <dim>."cx,cz"."x,y,z" = 1b`, dim o/n/e/x, cx = x/16 floor.
- Chop on a placed log: key removed, no fell, the log breaks normally (`find/probe:8-9`). Other breaks never remove keys.
- Placed logs inside a tree: the flood walks through them but never claims them; they are put back, not felled (per
  log, not per tree).
- Abort rules (`job/p2:10-23`, `own/crown`): no owned logs; no leaf touching the owned logs (#l1 <= 0: a leafless log
  cabin); #l1 x 8 < #n; crown rule: some owned log at y >= ytop (= highest owned y - 1) must have a non-persistent leaf
  at y+1 or y+2 straight above (side contact doesn't count). Abort = put everything back, only the chopped log breaks.
- Tests (group :941): 5 placed logs keyed (:956); pillar -> only the chopped log, 0 displays (:960); key removed (:962);
  cabin without leaves -> one log (:972); 35-log wall touching a canopy: chopping the wall takes 1 log (:982), felling
  the tree leaves the wall (:986); player platform on a trunk survives 3/3 (:1002).

## Ownership between touching trees
- Flood: 26-neighbourhood, same log type only (axis-matched), within the radius box, capped by max_logs.
- Ground logs (block below not in #timber:not_ground = passable, logs, leaves, cocoa, mangrove_propagule,
  pale_hanging_moss, creaking_heart, shelf_mushroom) seed labels; ground logs within 1.5 join one seed (2x2 base = one).
- BFS by layers from all seeds; a front claims unclaimed, non-placed logs within 1.8 blocks; the first front to reach a
  log owns it (same-layer ties by entity order). The tree = the cut log's label (0 -> fallback label spreading from
  the cut log). Owned logs below the cut layer are dropped (the stump).
- Leaves: walked from owned logs into 6-neighbour non-persistent leaves with distance d = 1, then d + 1 ... up to 7
  (gen `gen_leaves` :100-113); persistent leaves are never taken or walked through. A neighbour's leaf is taken only if
  the chain reaches it from our logs. Detached leaves (already distance 7) stay.
- Tests (group :1006): overlapping canopies: neighbour trunk 6/6 (:1017), neighbour leaf count unchanged in
  cx+3..cx+6 (:1018), felled leaves gone (:1019), no non-persistent leaf at distance 7 in cx+1..cx+7 at tick 20
  (:1023); log bridge: felled trunk gone, neighbour 7/7, bridge split 2-3 of 5 (:1036-1038); dark oak pair at (0,0) and
  (5,2): chopped 2x2 fully felled, neighbour keeps >= 8 logs (:1051-1052). Leaf check helper `leafCheck` :545-567.

## Decor (#timber:decor: vine, cocoa, mangrove_propagule, pale_hanging_moss, resin_clump, creaking_heart, shelf_mushroom)
- Found on the 6 faces of taken logs and leaves (`decor/around`), plus #timber:hanging (vine, pale_hanging_moss)
  straight below; snow only straight above a taken leaf. Counts toward max_blocks. State kept per type (vine faces,
  resin_clump faces, moss tip, cocoa facing/age, propagule hanging/age, snow layers, creaking heart axis/state);
  waterlogged spots become water. Loot as leaf drops (ripe cocoa -> beans). Displayed with their block state; put back
  per type (`put/decor`, `put/vine`).
- Tests (group :1352): jungle tree + cocoa age 2 + 4 vines + snow layers 2: all gone (:1364), 4 vine displays (:1366),
  >= 2 beans (:1369).

## Mangroves, pale oak, giants
- Mangrove roots and moss carpet are in no scan tag: they stay. Propagules are decor. Test (:1319-1349): 12 random
  mangrove/tall_mangrove, base = first log in the column, 0 logs left in radius 14 for >= 8 of 12.
- Pale oak: the creaking heart is decor and flood-walkable (`flood/heart`), never counted as a log or for the crown.
- 2x2 giants: no special scan case (26-flood + ground seeds); footprint width = span + 1, forced to 2 if >= 3
  (`p5:12-18`). Plunge gap: open cells under the trunk columns (#timber:gap_open, cap 16).
- Trees group (:1267): per feature 0 logs left, leaves left <= detached + max(2, 3%), 0 invisible displays, landed >=
  85 deg, > 30 ticks. paleoaks (16 x pale_oak_creaking) and giants (4 x mega_pine, mega_spruce, mega_jungle_tree) run
  the same assert, only with `-Dscn`.
