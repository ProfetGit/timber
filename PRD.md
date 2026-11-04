# Profet's Timber 2.0 — PRD

Status: draft (2026-11-03). Mod id `vanilla_timber`, package `io.github.profetgit.vanillatimber`. Tracker: `STATUS.md`.
Replaces the data pack in `packs/Timber` (1.3.6, frozen). That folder's `CLAUDE.md` documents the 1.3.x pipeline this
port follows; `dev/gen.py` there holds the curve formulas.

## 1. Summary
- **Promise:** chop one log and the whole tree comes down, cartoon style, with any tree, on any server, in every
  version modpacks use.
- **Moment:** the 1.3.6 gag unchanged: shiver, drop onto the stump, lean back with a creak, bend, slam, crown splat,
  log ripple. It's smooth on 1.20.1 now, and the leaves keep their biome colour while they fall.
- **Edge:**
  - FallingTree (71.5M) breaks trees instantly; its "fall blocks" mode drops falling-block entities, not a staged fall.
  - Panda's Falling Trees (16.6M) needs the mod on every client plus PandaLib.
  - HT's TreeChop (54.3M) has no fall.
  - Timber works with the mod on the server only (vanilla clients see the fall drawn with displays). The mod on the
    client makes it better: the fall is drawn locally, costs one packet, and has no size cap.
- **Platform:**
  - 26.3 and 26.2: Fabric, NeoForge, Forge;
  - 1.21.1: NeoForge, Fabric;
  - 1.20.1: Forge, Fabric.
  One jar per loader and version, used on either side.

## 3. Decisions
| Topic | Choice | Date |
|---|---|---|
| Variant | Sharpened: Java core, server-only works, client-drawn fall when installed, modded trees, fungi, mushrooms, claims, config | 2026-11-03 |
| Versions on day one | 26.3/26.2 + 1.21.1 + 1.20.1 (the modpack versions) | 2026-11-03 |
| Data pack zip and Paper plugin | Retired at 2.0; they stay on 1.3.6, no more updates | 2026-11-03 |
| Why 2.0 at all | Modpack reach: the 70M-class tree mods live in modpacks; 1.3.6 can't get in (vanilla logs only, no 1.20.1, no config) | 2026-11-03 |
| 2.0 scope | 26.3 Fabric only; other loaders and versions are a backport phase after the release | 2026-11-03 |
| Auto-replant and trunk knockback | In 2.0 (§5.11, §5.12). Sapling always free; hostile mobs take a bonk, other mobs only pushed; players never touched | 2026-11-04 |

My calls, open to veto:
- Mod id `vanilla_timber` and CurseForge project 1709833 are kept, so modpacks update in place.
- New Gradle project in `mods/Timber/` with its own git; `packs/Timber` stays as the 1.3.6 record.
- No new registry entries (blocks, items, entity types, sounds, particles), so vanilla clients can always join.
- Effort mode and lying logs wait for 2.1 (§15). Auto-replant and knockback moved into 2.0 (user, 2026-11-04).
- A tree over the display cap still falls. Vanilla viewers see its logs fall with a leaf burst; modded viewers see
  the whole tree.
- Creative mode doesn't fell trees (as in 1.3.x): instant break is a building tool.
- If the 1.3.x data pack is also enabled, the mod disables it and tells the operators.
- Messages use `translatableWithFallback`, so vanilla clients read English and modded clients read their own
  language.

## 4. Pillars
1. **Same feel.** The 2.0 pose stays within 0.5° pitch and 0.02 blocks of 1.3.6 on every tick of an oak and a giant
   spruce fall. Measured with the lab probe (`packs/Timber/dev/lab/lab.py compare`); the user's verdict at M1.
2. **Works server-only.** A vanilla client on a 2.0 dedicated server sees the full fall and is never disconnected.
   Checked by an `mp` case: displays present on every tick, the join succeeds, 0 kicks.
3. **Cheap.** Giant spruce chop tick ≤ 10 ms (1.3.6: 95). Moving tick ≤ 0.2 ms with only modded viewers and ≤ 3 ms
   with vanilla viewers (1.3.6: 12-17). Client frame ≤ 1 ms for a 2000-block tree. All guesses, measured at M2/M6.
4. **Nothing lost, nothing griefed.**
   - Loot equals breaking every block by hand with the same axe (scene `fair`).
   - Claims are never crossed (scene `claims`).
   - A crash in mid-fall gives the drops back at the stump (scene `crash`).

## 5. Features (2.0)
### 5.1 Chop and gates
- A fell starts from the loader's block-break event for a log broken by a player (Fabric `PlayerBlockBreakEvents`,
  NeoForge/Forge `BlockEvent.BreakEvent`). Pistons, explosions, Create saws and `/setblock` never fell.
- Gates, in order:
  1. the player's toggle;
  2. game mode (survival or adventure);
  3. sneak mode (0: sneak takes one log, 1: fell only while sneaking, 2: always);
  4. the axe (`#minecraft:axes` or `#c:tools/axe`) when `require_axe`;
  5. durability;
  6. add-on cancel (§5.7).
- Re-entrancy: blocks Timber removes never start a fell. A player starts at most one fell per tick, so a multi-break
  mod (FTB Ultimine) breaking 20 logs fells one tree, not 20.
- A chop on the stump of a tree that is still falling does nothing.

### 5.2 Tree scan
- Logs are `#minecraft:logs` ∪ `#timber:logs` minus `#timber:never`. The flood covers 26 neighbours within `radius`
  (12, range 4-32), up to `max_logs` (256, range 16-4096).
- Ownership as in 1.3.x: a log belongs to the trunk it grows from, so touching trees keep their own logs
  (`own/*` port, spread 1.8 blocks).
- Crown rule: natural leaves (`persistent=false`) at y+1 or y+2 above the top log, and the count of attached leaves
  must be at least the log count. Anything else is a build and isn't felled.
- Leaves are taken by the vanilla `distance` rule, so a neighbour's leaves stay with the neighbour. A crown is capped
  at `max_blocks` (900, range 100-8000).
- 2×2 giants, mangroves (roots stay), pale oak with the creaking heart, poplar on 26.3: all as in 1.3.6.
- Decor comes along: vines, cocoa, propagules, pale hanging moss, resin clumps, shelf mushrooms (26.3), snow layers.
  The list is `#timber:decor`.
- The scan is time-sliced at 2 ms per tick (guess, tune at M2). A normal tree finishes in the chop tick; a giant
  finishes inside the 5-tick chop shiver, so the player never waits.
- Too big (over a cap): the chopped log breaks alone and the actionbar says "Too big to fell (312 logs, limit 256)".

### 5.3 Tree types
- Built-in types: overworld trees (log + natural leaves, distance rule); nether fungi (crimson/warped stems + wart
  blocks + shroomlight + weeping/twisting vines, connected rule); huge mushrooms (mushroom stem + red/brown mushroom
  blocks, connected rule).
- Data-driven types in `data/<ns>/timber/tree_type/*.json`: `{trunk: tag, crown: tag, rule: distance|connected,
  decor: tag, max_crown_gap: 2}`. Modpack makers add a type with a data pack.
- Generic fallback for modded trees: any log from §5.2 whose crown blocks extend `LeavesBlock` and have
  `persistent=false` uses the overworld rule. That covers most biome mods with no extra data.
- Connected rule: crown blocks touching the trunk or each other, within `radius`. A player-built mushroom house of
  mushroom blocks is only felled if it has a stem (as vanilla grows them); placed-log protection covers stems too.
- Not trees: Dynamic Trees branches, Twilight Forest giant canopy trees over the caps (actionbar), chorus plants.

### 5.4 The fall
- The timeline is a pure function of (tree, tick) in `core/`, shared by server and client. It's ported from `gen.py`:
  - shiver tip offsets 350, −260, 180, −110, 50 (thousandths, ticks 1-5);
  - lean −10° over 7 ticks with smoothstep, then a creak, a crack on tick 7;
  - `rod_from_rest` (θ''=sin(θ+25°), from −10° to 90°) over clamp(h/2+8, 10, 22) ticks, mapped to the landing angle;
  - the bend spring k35 c35 m90, clamped ±0.7;
  - impact squash gx1060 gy1000 gz820, crown splat lx1080 lz380, bounce scaled by 7/h;
  - ripple gaps 4/3/2 shrinking to 1, pops (squash 3, snap 1, vanish 2);
  - plunge (hover 2, dip 0.3, quadratic over 1+gap, cap 8) and hang.
- Fall direction: the player's yaw in 32 bins, trying ±33.75° (the more open side wins), 0, ±67.5° and ±90°; pitch
  is swept from 6° to 135° in 3° steps; if every direction is blocked past 135°, the tree drops first. Same as 1.3.6.
- Exact angles everywhere: the modded client poses from render-state time on its own fall clock (never
  `tickCount + partialTick`, workspace rule), so there are no byte-pitch steps for modded viewers.
  Vanilla viewers get the 1.3.0 residual-in-transformation trick.
- Slam effects, sounds and drops fire on the timeline tick when the client shows the tree arriving (1.3.0 rule).
  Sounds are vanilla only, the same ids, volumes and pitches as 1.3.6 (`packs/Timber/.../anim/fx/*`).
- Animation off (`animation` 0): the tree is removed at once and the drops pop where each block was.

### 5.5 Drawing
- Vanilla viewers: block_display entities, spawned and posed from Java with direct transformation sets (no NBT
  merge), `teleport_duration` 1, the 1.006 spawn scale against z-fighting.
- 1.3.2 put-back: the real blocks are set back for the chop tick and removed the next tick, so the chop never
  flickers. The anchor cell stays empty (it lit the whole tree black).
- Leaves are never culled for vanilla viewers (1.3.6 rule: culled crowns looked hollow).
- Modded viewers: the server sends one `TreeFall` payload (palette, positions relative to the pivot, a tint per leaf,
  start tick, fall parameters), and a mixin on `Entity.broadcastToPlayer` hides the displays from them.
- The modded client bakes one mesh at the payload and draws it in the entity pass, so shader packs shade it like
  entities (HavingABlast `DebrisRenderer` pattern).
- The client draws the mesh at 1.006 scale while the put-back blocks are still there. The blocks leave on the next
  tick and the mesh takes over with nothing in between.
- Light: each block samples block and sky light along its path every 2 ticks and eases toward it (HavingABlast debris
  light smoothing). Cardinal face shading follows SeamlessDisplays `Shade`.
- Leaf colour: the biome tint at each leaf's rest cell, baked into the mesh. Vanilla displays can't be tinted, which
  is why 1.3.x used tinted particles.
- Mixed audiences: vanilla and modded viewers of the same tree get the same timeline from the same start tick; drops
  and sounds come from the server for everyone.
- A player who comes within range mid-fall: a modded client gets the payload with the elapsed ticks; a vanilla client
  sees the displays from that tick on.
- Client preference `draw: local|server` (Mod Menu, client config). With `server`, that player gets displays like a
  vanilla client. This is the escape hatch for any render conflict and for Flashback recording.

### 5.6 Durability and loot
- The axe pays one damage per log, and Unbreaking rolls per log. A tree that would break the axe isn't felled; the
  actionbar says so and only the chopped log breaks. Unbreakable axes pay nothing.
- Loot is vanilla, rolled with the axe at the chop (`Block.getDrops` with the tool), so Fortune, Silk Touch and
  modded loot modifiers apply. The `doTileDrops`/`block_drops` game rule is respected.
- `drops` 0: each log hops out of its own spot as its ring pops, and leaf and decor drops burst from the crown at the
  slam. `drops` 1: everything at the player.
- Drops that would land in lava, fire, the void or below the world go to the stump instead.
- Experience from modded logs that give some (vanilla gives none) is paid at the slam.

### 5.7 Protection
- Placed logs are remembered per dimension, keyed by chunk, recorded on player placement (any loader's place
  event). A tree containing a placed log is not felled (`protect` 1).
- Claims: every block of the tree passes the loader's break-permission check as the chopper. If one is denied, only
  the chopped log breaks, and the actionbar says "Part of this tree is protected".
- Add-on cancel: `#timber:api/cancel` runs as the chopper at the log; a return of 1 or more cancels. The mod writes
  `storage timber:meta` {version "2.0.0", version_id 20000, requires []} and runs `#timber:api/loaded` on load and
  `/reload`, so Enchanted Timber and TimberTitan keep working unchanged.
- A Java `TimberEvents.CANCEL` hook is for mod add-ons.

### 5.8 Config and commands
- Server config `config/timber.json`:
  - the 1.3.x keys: max_logs, max_blocks, radius, require_axe, sneak, durability, animation, drops, protect, feedback,
    welcome;
  - new keys: display_cap (1200), slice_ms (2), replant (1), knockback (1), trunk_damage (4);
  - `anim.*` tunables (lean degrees, fall duration terms, spring k/c/m, bounce scale, ripple gaps).
- The server re-reads the file when its mtime changes (SnappyBlocks `Config.java` pattern), clamps every value, writes
  comments, and syncs it to modded clients (HarvestSweep `ConfigPayload`).
- `/timber` (op level 2): `config <key> [value]`, `reload`, `menu` (the clickable chat menu, ported from
  `settings.mcfunction`).
- `/trigger timber` stays: any player toggles Timber for themselves, on vanilla clients too.
- Client config: `draw` (§5.5). A Mod Menu screen on Fabric and the config screen extension on NeoForge/Forge.

### 5.9 Migrating from 1.3.x
- On the first load of a world with 1.3.x state (`timber:meta` version_id below 20000, or `timber.*` objectives):
  1. import `storage timber:placed` (dimension keys o/n/e/x) into the placed-log data;
  2. import the `timber.config` scores into `timber.json` (only keys the file doesn't set);
  3. import `timber.off` scores into the player toggles;
  4. spawn the drops still held by `timber.ctl` controllers at their positions;
  5. remove the `timber.*` entities, objectives and storage (except `timber:meta`);
  6. log a summary.
- If the 1.3.x data pack is enabled (it has `timber:tick` in `#minecraft:tick`), the mod runs `datapack disable` on
  it and tells the operators in chat once.

### 5.10 Feedback
- Every refusal has an actionbar line, with `feedback` 1: too big, axe would break, protected, cancelled by an
  add-on (the add-on's own text if it set one).
- The join hint stays off by default (1.3.4 rule).
- No other UI: a player learns Timber by chopping a tree.

### 5.11 Auto-replant (added to 2.0, user 2026-11-04)
- `replant` 1 (default): when a fall finishes (FINISH frame, after every ring has popped and every drop is out), a
  sapling of the tree's kind is planted in each of the tree's ground log cells (the trunk cells the scan found standing
  on ground). A 2x2 giant gets four, so dark oak, mega spruce and big jungle regrow as giants.
- The sapling is free (user's pick): the loot is not touched and nothing comes from the inventory.
- Which sapling, first match wins:
  1. a sapling item in this tree's rolled loot (`#minecraft:saplings`), its block;
  2. the crown block's id: `_leaves` → `_sapling` (modded trees follow this convention);
  3. the trunk block's id: `_log`, `_wood`, `_stem` → `_sapling`;
  4. vanilla exceptions: mangrove → `mangrove_propagule`, azalea leaves → `azalea`, flowering azalea leaves →
     `flowering_azalea`, crimson/warped stem → crimson/warped fungus, red/brown mushroom block → red/brown mushroom;
  5. nothing found: no replant.
  The block must exist and be in `#minecraft:saplings` or be one of the step-4 blocks.
- A cell is planted only if it is air now and the sapling `canSurvive` there (a huge mushroom in daylight on grass,
  a propagule over removed roots: skipped, nothing else happens). Never overwrites a block someone placed mid-fall.
- Planting looks like bonemeal: the sapling's place sound and a few `happy_villager` particles. Vanilla clients see
  it (plain block set and vanilla particles).
- `animation` 0 plants at once; an aborted fall (server stop, chunk unload) plants in its finish. A crash mid-fall
  plants nothing (the pending loot still comes back).

### 5.12 Trunk knockback (added to 2.0, user 2026-11-04)
- `knockback` 1 (default): from the frame the blocks go (frame 1) to the slam (IMPACT), the falling tree knocks back
  every living mob it sweeps through, once per mob per fall.
- Hit test per frame, in the fall's frame (pivot, fall direction by yaw, side axis): a mob is hit when its angle from
  the pivot lies inside the pitch swept since the last frame (with a margin for the mob's own size), its distance
  from the pivot is within the tree's height plus crown, and its side offset is within the trunk half-width (crown
  half-width where the crown is), plus the mob's half-width.
- Knockback along the fall direction with a small lift, strength scaled by how fast the tree moves at that distance,
  clamped (guess: 0.4..1.6, tune by feel). Vanilla `knockback`, so knockback resistance (iron golems, ravagers) counts.
- Damage (user's pick): only hostile mobs (`Enemy`) without a name tag take `trunk_damage` (default 4 = 2 hearts,
  0..20), from a vanilla damage type with the chopper as the cause (so drops and XP count as a player kill). Pets,
  villagers, animals and named mobs are only pushed.
- Players are never touched: no push, no damage (user, hard rule). Armor stands and spectators are skipped.
- A wooden bonk sound and a few crit particles at each hit. Server only, so vanilla and modded viewers see the same.
- Budget: one entity query per moving frame around the tree's reach; ≤ 0.05 ms per frame on a giant spruce (measured
  in the `knock` scene).

## 6. Controls
| Input | Context | Action |
|---|---|---|
| Break a natural log with an axe | survival/adventure, Timber on | the whole tree falls |
| Sneak + break | `sneak` 0 (default) | one log only |
| `/trigger timber` | any player | Timber off/on for me |
| `/timber config\|reload\|menu` | op 2 | server settings |
No keybinds.

## 7. Multiplayer and security
- The server decides everything. The modded client sends one message, `Hello` on join (HarvestSweep pattern), and
  nothing else.
- `TreeFall` goes only to players tracking the tree's chunk. Packets are chunked under the payload limit, with a
  16 KB budget (guess, measured at M1).
- Ping: the client's fall clock starts when `TreeFall` arrives, offset by the ticks already elapsed on the server.
  At 200 ms ping the fall starts 4 ticks late on both kinds of viewer alike; drops follow the same clock.
- Per-player state (handshake, draw preference) is cleared on disconnect; nothing is kept in static fields across
  worlds.
- Claims and `/trigger` as in §5.7 and §5.8.

## 8. Data and persistence
- Per-dimension SavedData:
  - `vanilla_timber_placed`: placed logs as packed positions keyed by chunk;
  - `vanilla_timber_pending`: the loot of each fall in progress, written at the chop and cleared at the last drop.
- Overworld SavedData `vanilla_timber_players`: toggles by UUID.
- Displays are never saved: a mixin on `Entity.shouldBeSaved` returns false for tagged displays.
- On a server stop every fall finishes at once and drops its loot. After a crash, pending loot spawns at the stump
  on load.
- Chunk unload in mid-fall: that fall finishes at once, with its drops at the stump.
- Removing the mod: nothing is left in the world except the ignored SavedData files and `timber:meta`; logs and
  leaves are already real blocks or drops.
- Updates: SavedData carries a `format` int; migrations run on load.

## 9. Compatibility
| Mod | Expected | Checked by | Milestone |
|---|---|---|---|
| Sodium | mesh drawn in the entity pass, unaffected | render sheet | M6 |
| Iris + Complementary | shaded like entities, shadow pass draws the tree once | render sheet, SeamlessDisplays `IrisCompat` pattern | M6 |
| EntityCulling | displays culled only off-screen; mesh not an entity | render sheet | M6 |
| Distant Horizons | LOD updates after the blocks go; no ghost tree near | hands-on | M6 |
| Continuity | connected textures on the mesh match the standing tree | render sheet | M6 |
| Flashback | replay shows the fall (draw=server while recording if payloads aren't replayed) | recorded take | M6 |
| Seamless Displays | its block-display tweaks apply to vanilla-mode displays | render sheet | M6 |
| Snappy Blocks | its break burst on the chopped log only | hands-on | M6 |
| FTB Chunks, Open Parties and Claims | claims respected (§5.7) | scene `claims` + one real claim mod by hand | M4 |
| FTB Ultimine | one fell per tick (§5.1) | scene `multibreak` | M4 |
| FallingTree, HT's TreeChop | both present: Timber logs a warning and tells ops once | scene `conflict` | M4 |
| Enchanted Timber, TimberTitan | API unchanged (§5.7) | scene `hooks` | M4 |
| Biome mods (one, e.g. Biomes O' Plenty if a 26.3 build exists) | modded trees fall via the fallback | hands-on (download needs the user's OK) | M3 |

## 10. Performance
- Server, chop tick (scan + loot + spawn), giant spruce with 427 blocks: ≤ 10 ms. Scene `perf_spruce`, warm, median
  of 5.
- Server, moving tick: ≤ 0.2 ms with only modded viewers, ≤ 3 ms with one vanilla viewer, giant spruce. Same scene.
- Worst case: 10 fake players fell 10 oaks in the same tick, chop tick ≤ 15 ms (time-sliced). Scene `perf_forest`.
- Client: ≤ 1 ms per frame for a 2000-block tree; mesh bake ≤ 4 ms, done once. Probe mod frame timer.
- Network: one `TreeFall` ≤ 16 KB for a giant spruce, versus about 8.5K entity updates per second per viewer in
  1.3.6.
- All are guesses, tuned at M2 (server) and M6 (client).

## 11. Technical design
- **Template:**
  - the Stonecutter multi-loader layout of `mods/BeltLantern` (26.2/26.3 Fabric/NeoForge/Forge, `platform/<loader>/`
    entries);
  - the 1.20.1 Fabric legacy target and the standalone ForgeGradle 6 project from `mods/FarOut`
    (`forge-1.20.1/`);
  - 1.21.1 NeoForge (moddev) is new in the workspace;
  - the studio template (`tools/Studio/new_mod.py`) gives the ModTest selftest driver and the `mp` case.
- **Packages:**
  - `core` (plain Java: scan model, ownership, direction, curves, timeline);
  - `server` (chop, scan on the level, loot, displays, pending drops);
  - `client` (mesh bake, renderer, light, config screen);
  - `net` (Hello, TreeFall, ConfigPayload);
  - `data` (SavedData, migration);
  - `compat`, `platform`, `selftest`.
- **Mixins:**
  - `Entity.broadcastToPlayer` (hide displays from modded viewers; 26.3 `Entity.java:3569`);
  - `Entity.shouldBeSaved` (never save displays);
  - a `Display` accessor for transformation and interpolation.
  No client mixin into terrain rendering.
- **Vanilla paths checked 2026-11-03 (26.3):** `LeavesBlock.DISTANCE` (`LeavesBlock.java:31`), `BlockTags.LOGS`
  (`BlockTags.java:28`), `BlockTags.OVERWORLD_NATURAL_LOGS` (`BlockTags.java:77`).
- **Tests:** the 21 TimberTest groups (`packs/Timber/dev/test/TimberTest.java`) are rewritten as ModTest selftest
  scenes with named asserts; `core` gets JUnit tests for the curves against values sampled from 1.3.6.

## 12. Risks and spikes
| Risk | Spike | Milestone | Fallback |
|---|---|---|---|
| The client mesh drifts from the server's drops and sounds, or flickers at the swap | Client fall in sync, `mp` with 1 modded + 1 vanilla client | M1 | Lean: displays for everyone, driven from Java |
| No workspace mod builds 1.21.1 NeoForge yet | Build it first in M5 | M5 | Ship 1.21.1 Fabric at 2.0, NeoForge in 2.0.1 |
| Fabric has no standard per-block permission event for claims | Check FTB Chunks and OPAC on Fabric | M4 | Compat classes per claim mod |
| Vanilla clients on 1.20.1 still see a stepped fall (no `teleport_duration` there) | — | M5 | Accept and say so; modded clients are smooth |
| Port volume: 756 functions, 21 test groups | Test-first port, group by group | M2 | Ship parity minus hide_leaves (already off) |
| Iris shadow pass or Sodium draws the mesh wrong | SeamlessDisplays and HavingABlast patterns | M6 | `draw: server` per player |

## 13. Milestones
2026-11-03: the user narrowed 2.0 to 26.3 Fabric only. M6 and M7 ship that; M5 (other loaders and versions) is a later backport phase, listed after M7. Work already done there: `STATUS.md` M5.
Each milestone: build green, version bump, installed in the Fabric 26.3 profile, committed.

| | Milestone | Exit check |
|---|---|---|
| M0 | Scaffold | `mods/Timber`, 26.3 Fabric, id `vanilla_timber`; `mt.py Timber quick` boots; smoke scene green |
| M1 | Client fall in sync (spike + ugly core loop: oak and birch scan, full timeline, both drawing paths, server drops and sounds) | `mp` case with 1 modded + 1 vanilla client, both with the probe: **go** if the modded mesh is within 1 tick of the vanilla displays at the slam, `wb img flicker` is clean at the chop on both, the client frame is ≤ 1 ms for a giant spruce, and the server is ≤ 0.2 ms per moving tick with only modded viewers; **stop** (switch to Lean) if the drift is over 2 ticks at 200 ms ping or the flicker can't be removed. Then the feel check below |
| M2 | Parity with 1.3.6 | all ported scene groups green on 26.3 Fabric (`base`, `swap`, `gates`, `protection`, `neighbours`, `direction`, `stump`, `landing`, `modes`, `durability`, `robustness`, `trees`, `decor`, `mangroves`, `anim`, `pose`, `fair`, `paleoaks`, `giants`); pose within 0.5° of 1.3.6 (`lab.py compare`); `perf_spruce` chop ≤ 10 ms |
| M3 | Any tree | scenes `fungi` (crimson, warped), `mushrooms` (red, brown), `custom_type` (a fixture data pack defines a type), `modded_fallback` (a fixture LeavesBlock subclass in the test mod); one biome mod by hand; feel check |
| M4 | Server-grade | scenes `claims`, `multibreak`, `conflict`, `hooks` (with Enchanted Timber), `migrate` (a world saved with the 1.3.6 jar: placed builds still protected, mid-fall drops returned, old pack disabled), `crash` (kill mid-fall, reload: loot at the stump), `config_reload`; `mp` with 2 vanilla + 1 modded client, 0 kicks |
| M6 | Render compatibility and performance (26.3 Fabric only) | render sheet per §9 row passes the critic; `wb img flicker` clean with shaders; every §10 budget measured and met or re-set with the user; user verdict on the named stills |
| M7 | Release prep (26.3 Fabric only) | release tier green on 26.3 Fabric; README and CurseForge description, changelog, 60 fps WebP clips; the user's go |
| M5 | Later backport phase, after the 2.0 release: loaders and versions | `mt.py Timber full` smoke on 26.3/26.2 Fabric, NeoForge and Forge, 1.21.1 NeoForge and Fabric, 1.20.1 Forge and Fabric; scene subset (`base`, `swap`, `fair`, `anim`, `hooks`) green on 26.3 Fabric, 1.21.1 NeoForge and 1.20.1 Forge; feel check on 1.20.1 |

Feel check (M1, ~3 minutes, `studio.py play Timber`, a new world near a forest):
1. Chop an oak, a birch and a giant spruce, then the same in 1.3.6 (`lab.py` clips side by side).
2. Same weight and timing? Does anything feel floatier or snappier than before?
3. Watch the chop up close: any flicker, dark frame or gap when the tree turns into the falling copy?
4. Chop in a swamp and a cherry grove: do the leaves keep their colour while they fall?
5. A second client without the mod joins (`mp`): does it still see the fall, in step with yours?
Tunables: `config/timber.json` `anim.*` (live, no rebuild).

Feel check (M3, ~3 minutes): fell a crimson fungus, a warped fungus, a red and a brown mushroom, and a tree from the
biome mod. Does each fall read as that tree (the wart crown splats like leaves, the mushroom cap pops)? Anything
felled that shouldn't be?

Feel check (M5, ~3 minutes, 1.20.1 Forge play client): chop an oak and a giant spruce with the mod on the client,
then with `draw: server`. Is the jank the user saw in the 1.20.1 data pack gone?

## 14. Release
- Version 2.0.0 on CurseForge 1709833 (same project). One file per loader and version, tagged only with the loaders
  tested at M5. The `release` skill and its single confirmation.
- The Data Packs twin 1712830 stays on 1.3.6 with a note on the page that Timber 2.0 is a mod. The plugin jar is
  retired (it was never on CurseForge).
- The description says server-only works and the client mod is optional but better. Workspace kit: Ko-fi,
  BisectHosting, promo tiles, AI disclosure, 60 fps framed WebP clips.
- Enchanted Timber's page gets a line: it works with Timber 2.0.

## 15. Roadmap
- 2.1: effort mode (bigger trees take more chops, each chop cracks the trunk; off by default); lying logs you chop
  up; Jade/WTHIT tooltips.
- Later, on demand: 1.21.4, 1.21.8, 1.21.11 builds.

## 16. Cut
- Bringing 2.0 features back into the data pack, and further Paper plugin builds: both retired by decision.
- Quilt tag: only if it is tested at M5.
- Hide inner leaves (`hide_leaves`): there's no reason left for it now that the client draws the tree; vanilla
  viewers keep full crowns (1.3.6 default).

## 17. Prior art
Checked on CurseForge, 2026-11-03.

| Mod | Downloads | Versions / loaders | Gap Timber fills |
|---|---|---|---|
| FallingTree (rakambda) | 71.5M | 1.12 to 26.x, Fabric/NeoForge/Forge; config for logs/leaves/tools | no staged fall; its fall mode drops falling blocks |
| HT's TreeChop (hammertater) | 54.3M | 1.19.2-1.21.1; forks for 1.21.11 and 26.3 (Polymer) | chop-by-chop, no fall |
| Panda's Falling Trees | 16.6M | Fabric + NeoForge, Forge dropped; needs the mod on both sides + PandaLib | Timber works server-only |
| Sable physics tree mods (Sable Trees, Falling Trees Physics, Felled) | 0.8K-16K | 26.x, new in Oct-Nov 2026 | physics libs, both sides; Timber keeps the cartoon feel |
| Profet's Timber 1.3.6 | 13.5K in 40 days | data pack + jars, 26.x/1.21.x | this PRD |
The name and slug stay (`profets-timber`).

## 18. Coverage
| Dimension | Answered in |
|---|---|
| Sides | §1 Platform, §5.5, §7 |
| Multiplayer | §5.5 mixed audiences, §7, M1/M4 `mp` |
| Persistence | §8 |
| Data safety | §4 pillar 4, §5.6 lava/void rule, §8 pending loot |
| Uninstall | §8 removing the mod |
| Updates | §5.9 migration, §8 `format` |
| Config | §5.8 |
| Performance | §10 |
| Compatibility | §9 |
| Versions | §1, §3, M5 |
| Controls | §6 |
| Discoverability | §5.10: learned by chopping; refusals explained |
| Accessibility | vanilla sounds carry vanilla subtitles; `animation` 0 is the reduce-motion switch; no colour-only cues |
| Localisation | §3 `translatableWithFallback`; strings in lang files |
| Game modes | §5.1: survival and adventure fell; creative and spectator don't |
| Dimensions | fungi in the Nether (§5.3); the void and lava rule (§5.6); build height: the scan stops at world bounds |
| Command bypass | §5.1: `/setblock`, `/fill` and pistons never fell; logs placed by command aren't recorded as placed (as in 1.3.x) |

## 19. Open questions
- GitHub: Java source in `ProfetGit/timber` (data pack to a `datapack-1.3` branch) or a new repo? Default: the same repo, decided at M7, pushed only with the user's OK.
- Which biome mod for the M3 hands-on? Default: Biomes O' Plenty if it has a 26.3 build; the download needs the user's OK.
- Creative felling as an option? Default: no (§3).
