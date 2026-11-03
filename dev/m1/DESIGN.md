# M1 design notes (2026-11-03)

Working notes for the M1 spike, read from the 1.3.6 functions (`packs/Timber/pack/data/timber/function/`) and the 26.3
sources. Durable decisions go to `CLAUDE.md` Decisions when M1 closes; this file can go then.

## Done (step 1)
- `core/Curve`: gen.py `rod_from_rest` tables, byte-equal (CurveTest vs `fixtures/136/curve.json`).
- `core/FallSpec`: the chop's decisions (h, hit, dur, amp, km, fd, ft, dr, rs); `derive` = 1.3.6 p8 formulas.
- `core/Timeline`: tick-exact port of `anim/tick` + phases (plunge 6, lean/hang 0, fall 1, tipping 4, drop 5, bounce 2,
  ripple 7), shiver + spring in `pose`, events per tick. TimelineTest vs `fixtures/136/{oak,birch,spruce}.json`
  (1.3.6 controller scores sampled every tick by `dev/m1/sample136.py`, frozen-tick stepping).
- `core/Rig`: ring-origin chain (shear bend), block corner pose, byte step.

## Done (step 2, commit cbe775e)
- `server/TreeScan` + `server/Tree` + `core/Layout` (yaw table, integer rest math); `server/Falls` hooks
  `PlayerBlockBreakEvents.AFTER` (gates: not creative/spectator, not sneaking, `#minecraft:axes`) and keeps
  `Falls.last` for tests. Tags `data/timber/tags/block/{logs,passable,gap_open}.json` copied from 1.3.6.
- Scene `scan` (`Scenarios.scan`): oak_c, birch_a, spruce_b at x 8/56/104; pivot, bin 27, spec, ring mask equal the
  sampler's start. Not ported yet (M2): ownership split, decor, creaking heart, too-big actionbar.

## Found in session 2 (for steps 3-6)
- **Use `mt.py mods/Timber ...`**: the bare name `Timber` resolves to `packs/Timber`.
- **Self-test world is solid** (sandstone) around y -60: `Scenarios.plant` lays grass at -61 and clears air above.
- **Frame clock = `ServerTickEvents.START_LEVEL_TICK`** (Fabric names: START/END_LEVEL_TICK). `ServerLevel.tick`
  runs `chunkSource.tick` (block broadcasts + entity tracker, ServerLevel.java:407) before entities, so: scan at the
  break event; frame 0 at the START of the next tick (spawn displays at rest, remove the anchor block, CHOP, send
  TreeFall with e = 0: it leaves before that tick's anchor removal and spawn packets); frame n at the START of the n-th
  tick after (n = 1 also removes the other blocks). Poses and sounds of frame n then leave in the same tick, as 1.3.6's
  tick functions did. END_LEVEL_TICK would put every pose one tick behind its sound.
- **Modded clock, exact:** c0 = the first client tick after TreeFall is handled (a display is first ticked, so first
  drawn, there); tau = clientTicks + partial - c0 - 1 + e. Frame n's pose: c0 + n + 1. Use a client tick counter
  (END_CLIENT_TICK) + `getGameTimeDeltaPartialTick`, not level game time (synced, jumps).
- **26.3 names:** `EntityTypes.BLOCK_DISPLAY`; `new Display.BlockDisplay(type, level)`; `Entity.entityTags()`,
  `addTag`; `snapTo(x, y, z, yRot, xRot)`; Display private setters at Display.java:231 setTransformation, 283
  setTransformationInterpolationDuration, 291 setTransformationInterpolationDelay (forces resend), 299
  setPosRotInterpolationDuration, 328 setViewRange; BlockDisplay.setBlockState :499. `shouldBeSaved` and
  `broadcastToPlayer` are not overridden in Display (Entity.java:4108, :3569). `Block.getDrops(state, level, pos, be,
  breaker, ItemInstance tool)` :364; `ItemStack.hurtAndBreak(int, LivingEntity, EquipmentSlot)`;
  `level.getGameRules().get(GameRules.BLOCK_DROPS)`; `ServerLevel.sendParticles(opt, x, y, z, count, dx, dy, dz,
  speed)`; `Level.playSound(null, x, y, z, SoundEvent, SoundSource.BLOCKS, v, p)`; SoundEvents WOOD_BREAK, WOOD_FALL,
  GENERIC_BIG_FALL, WOODEN_DOOR_OPEN, ZOMBIE_BREAK_WOODEN_DOOR, PLAYER_ATTACK_SWEEP, AZALEA_LEAVES_BREAK, CHICKEN_EGG;
  `LocalCoordinates.apply(Vec3, Vec2(pitch, yaw))` reproduces `execute positioned ^..` exactly (TreeScan uses it).
- **Display pose per frame** (`anim/xf`, `move`): entity pos (px, spawnY + dy, pz), xRot = `Rig.step(p)`; for every
  live non-popping display whose frame values changed: `Rig.origins(res, bend, gy, h, kn, kt)`, translation
  `Rig.corner(tx/1000, wy/1000, tz/1000, k, sx, gy, sz, res, ..)`, left Rx(res), scale (gx|lx, gy, gz|lz), right
  rotation kept from the spawn, interpolation 1 + delay 0. Spawn: translation ((tx+ox)/1000, (wy-3)/1000,
  (tz+oz)/1000), right (0, qs, 0, qc), scale 1.006, teleport_duration 1, view_range 1.5, Rotation [bin*11.25, 0].
- **Pops** (`pop_tick`, `pop_key`): ring k tagged at n_k (= popTick[k]): keys at n_k (1.25/0.7, duration 3),
  n_k+3 (0.8/1.45, 1), n_k+4 (0/0, 2); discard at n_k+6. Key translation = Rx(res now)((tx, wy, tz) + Ry(yaw)(u, v,
  u)) with (u, v) = (-0.125, 0.15), (0.1, -0.225), (0.5, 0.5) (= (1-a)/2, (1-b)/2); scale (a, b, a); left rotation
  untouched; the entity keeps teleporting with the tree.
- **Places:** ring k lying centre = pivot + (0, dy, 0) + Ry(-yaw) Rx(p)(0, k + 0.5, -hw); crown = same with
  k + 0.5 -> cy = (rs + h) / 2 (shake at the chop uses the rest pose, flutter at the slam the landed one + 0.6 up).
  Particles (`anim/fx`, `ring_pop`): crack = block(log) at pivot + 0.2 up, spread (0.3, 0.1, 0.3), 6; ring pop =
  block(log) spread 0.35 speed 0.12 x22 + small_gust x2; chop shake = block(leaf) at the crown, spread (cr, 0.8, cr)
  speed 0.05 x20 (cr = 0.45 * crown width).

## Done (steps 3 + 5, session 3)
- `server/Fall` (one fall, stepped from `ServerTickEvents.START_LEVEL_TICK` in `Falls`): frame 0 spawns displays at
  rest (logs first, cap 600) and clears the anchor; frame 1 removes the rest with setblock-strict flags (818, water
  for waterlogged); pops (keys u = 1/4/5, discard u = 7, left rotation kept from the tag tick, translation at the last
  sent residual), leaves killed at `leavesGone`, then the frame's events, `move` (snapTo on y or byte-step change),
  `pose` (transformation when any frame value changed). `finish` drops leftovers at pivot + 0.5.
- `server/Fx`: every 1.3.6 sound and particle with its position (`LocalCoordinates` = `execute rotated/positioned ^`).
  The crack uses the tree's own log (1.3.6 hardcoded oak_log). Leaf particle table = `disp/leaf_fx`.
- Drops rolled at the chop (`Block.getDrops` with the axe, `BLOCK_DROPS` rule), logs by ring at RING k (hop), leaves
  at the slam from the even crown rings (burst), leftovers at FINISH. Axe wear = `processDurabilityChange(logs)`, too
  worn = actionbar + no fell. Feedback actionbar "Timber! N logs" at frame 0 (translatableWithFallback).
- `mixin/EntityMixin`: tagged displays (`vanilla_timber.fall`) never saved; hidden from `Viewers.local` players.
  `Viewers` is the Hello set (empty until step 4). Displays are only spawned while a vanilla player tracks the chunk
  (`chunkMap.getPlayers`), lazily at the current pose if one walks in.
- Scene `fall` (oak, birch, spruce): 18/18 on 0.0.3 — displays reach the client, frame 0 all displays + blocks
  standing but the anchor, frame 1 no blocks, end 0 displays at `tl.end`, rolled = dropped = items in the world - 1.
  `wb img flicker` on the 240 captured frames of each: no one-frame pops. The test box is under the solid test world,
  so the frames are dark: light the box (or a sky-open spot) before the critic or flicker review of the mesh.

## Client mesh: 26.3 API found (session 3, for step 4)
- Vanilla block displays draw through `BlockModelFeatureRenderer.putQuad` = `buffer.putBakedQuad(pose, quad,
  QuadInstance)` with only the tint colour: no directional shade at all (1.3.6 displays are flat). The mesh uses
  `Shade.table` (SeamlessDisplays) so its rest pose matches the shaded terrain blocks at the swap.
- `BakedQuad` (26.3 `client/resources/model/geometry/BakedQuad.java`): record of 4 `Vector3fc` positions, 4 packed UVs
  (`UVPair.unpackU/V`), `direction`, `materialInfo` (sprite, `layer` ChunkSectionLayer, `tintIndex`,
  `shadeDirectionOverride`, `lightEmission`). Parts: `getModelManager().getBlockStateModelSet().get(state)
  .collectParts(RandomSource, list)`, `part.getQuads(dir)` for 6 dirs + null (HavingABlast `Blasts.model`).
- Render types: `RenderTypes.solidMovingBlock()/cutoutMovingBlock()/translucentMovingBlock()`; submit with
  `collector.submitCustomGeometry(poseStack, type, (pose, vc) -> vc.addVertex(x, y, z, argb, u, v, overlay, light, nx,
  ny, nz))` from a `LevelRenderer.submitEntities` TAIL mixin (HavingABlast `LevelRendererMixin`, args `PoseStack,
  LevelRenderState, SubmitNodeCollector`; camera = `state.cameraRenderState.pos`). `CardinalLighting` =
  `level.cardinalLighting()`, read through `byFace` (Iris patches it).
- Plan: bake per (ring, leaf|log, render type) arrays of tree-frame vertices p = (tx, wy, tz)/1000 + Ry(yaw) m (from
  `Layout`, the same ints as the server); per frame one pose per group M_k = T(pivot + dy - cam) Ry(-yaw) T(0, oy_k,
  oz_k) Rx(theta) S T(0, -k, 0) with `Rig.origins` at the exact theta; one shade table per frame (Ry(-yaw) Rx(theta)
  Ry(yaw) for all groups); leaf tint = `getBlockColors().getTintSources(state)` `colorInWorld` at the rest cell.
  Check the Ry sign against the displays with a frame where both are visible (`draw: server` on one client).

## Done (step 4, session 4, commit 23fff6b, 0.0.4)
- `net/Hello` (C2S on JOIN, version 1 -> `Viewers.hello`; DISCONNECT -> `leave`), `net/TreeFall` (S2C: id, origin,
  pivot x1000, bin, FallSpec as doubles/varints, ring mask bits, e, palette of `Block.getId` states, 4 ints per block:
  zigzag dx/dy/dz from origin + palette<<1|leaf; air parts left out). `Fall.send()` each frame: every player with the
  chunk loaded (`chunkMap.getPlayers(chunk, false)`) who is `Viewers.local` and `canSend`, once, with e = n (frame 0 at
  the chop, later for walk-ins). Displays still only spawn when a non-hidden viewer tracks.
- `Viewers.test` (self-test only): `VANILLA` = treat everyone as vanilla (scene `fall`), `BOTH` = payload + displays to
  modded viewers (scene `sync`). `Viewers.hidden(p)` feeds the `broadcastToPlayer` mixin.
- Client: `client/TimberClient` (JOIN -> Hello, receiver -> `ClientFalls.onPayload`, `END_LEVEL_TICK` -> clock),
  `ClientFalls` (level-tick counter `ticks`, paused with the game like entity ticks), `MeshFall` (bake + pose),
  `Shading` (ambient-cube face shade via `CardinalLighting.byFace`), `mixin/LevelRendererMixin` (client list,
  `submitEntities` TAIL).
- **Clock (verified):** c0 = `ClientFalls.ticks` when the payload is handled; tau = ticks + partial - c0 - 2 + e
  (`Display.tick` starts an update's interpolation on the first entity tick after it is handled, reached one tick
  later). The mesh vanishes at tau >= end - 1 (= when the finish tick's removals show). Scene `sync` (both drawings in
  one client): slam drift 0.03 (oak), 0.28 (birch), -0.01 (spruce) ticks.
- Bake: per block `getBlockStateModelSet().get(state).collectParts(RandomSource.create(state.getSeed(cell)))`, quads of
  6 dirs + null, grouped by (ring, leaf, layer: SOLID/CUTOUT/TRANSLUCENT -> `RenderTypes.*MovingBlock()`,
  `ModelBlockRenderer.forceOpaque` for leaves). Per vertex: tree-frame pos = rest corner + Ry(yaw) m and offset from
  the block centre (pops scale (a, b, a) and the 1.006 inflate as pos + e * off). Tint = `getTintSources` `colorInWorld`
  at the rest cell. No face culling. Light per block: brightest open neighbour if the cell is solid, eased 0.35 every 2
  level ticks at the block's current centre.
- Scene `mesh` 25/25 (Hello reached the server, payload blocks/e/id, same timeline as the server frame by frame, quads
  baked, 0 displays on server and client, drawn up to hit - 0.5, gone at end - 1). Frame ms (submit + geometry,
  headless under load): oak p50 0.17 p95 1.77 max 8.4; birch p50 0.09 p95 1.12; spruce p50 0.09 p95 0.32 max 0.46
  (2562 quads); bake 1-4 ms. The oak/birch maxima look like one-off hitches at the first frames (bake frame?): check.
- Scene `sync` FAILS its own strict checks (not PRD gates): (1) the "pitch" check reads the first display's
  xRot + left angle, which is wrong for popping displays (left rotation frozen at the tag tick: oak 90.17 vs mesh 93
  during the ripple while positions agree within 0.05): measure pitch from non-popping displays only, or drop it for
  the position gap. (2) block gap 0.2-0.7 blocks about 1 tick after the slam (impact squash/bounce frames: displays lerp
  translation linearly between frames, the mesh recomputes origins at the lerped angle; or the squash axes differ):
  find which blocks and fix or bound it. (3) the retry run had one-frame spikes mid-fall (oak 18.7 deg / 1.55 blocks at
  t+21.5, spruce 8.1 deg / 3.5 at t+27.2): likely a server hitch (displays wait for packets, the mesh runs on its own
  clock), confirm from `.work/Timber/26.3-fabric-sync/out/sync_*.tsv` (cols: T, mesh pitch, display pitch, gap,
  displays, mesh blocks).

## Sync scene fixed (session 5, 0.0.5)
- **Root cause of the 1-tick drift (spruce -0.99 in one run):** the mesh clock was set from the payload's arrival, sent
  at START_LEVEL_TICK of frame 0; the displays' clock is set by frame 1's packets (sent in `chunkSource.tick`). When
  the chop tick runs long (scan + loot + 427 spawns), frame 0 is late and frame 1 on time, so the gap between them is
  under a tick and a client tick boundary can fall either side. Fix: `TreeFall.probe` = a cell the server clears at
  frame 1 (e = 0 only); `MeshFall.anchor` (every client tick, before tau is read) sets c0 = ticks - 2 on the tick that
  first shows it gone (frame-1 block updates and entity data leave together: `ServerChunkCache.tick` runs
  `broadcastChangedChunks` right before `chunkMap.tick`). Until then the mesh holds the rest pose; gives up after 3
  ticks. Hello protocol 2. Measured: the anchor moved the clock -1 on 2 of 3 trees in one run, 0 in the next.
- **The 0.2-0.7 block gaps after the slam are vanilla, not a bug:** `Display.tick` restarts a transformation
  interpolation from `lastProgress` (the last rendered frame's progress, 0.66-0.78 at 60 fps), so a display posed
  every tick trails by (1 - p_last) of the previous tick's motion; the crown splat (lz 380) moves outer leaves ~2
  blocks in one tick. Gap = 0.54 (1 - p) on consecutive frames, exactly. The mesh draws the timeline itself (call:
  don't copy a frame-rate artefact). xRot/position use the interpolation handler and don't trail.
- **Popping rings:** displays keep the tag tick's residual (left rotation frozen), the mesh turns them with the tree:
  up to 2.8 deg on a ring that is squashing to nothing. Kept (mesh is the cleaner one).
- Scene `sync` now checks: slam drift (median display pitch) <= 1 tick; geometry (each display's heading-for pose,
  `rs.transformation().get(1)` + entity pos/rot at pt 1, against `MeshFall.centresAt(n)`, popping rings skipped)
  <= 0.02 blocks; >= 90% of frames head for the mesh's own frame (ties go to it); "as drawn" gap as info.
  0.0.5: 15/15, drift 0.03/0.06/-0.01, geometry 0.0012, 100% same frame, as drawn p95 0.05-0.07.

## Exit harness plan (step 6, worked out in session 4; probe built in session 5)
- Chop on a dedicated server through the real path: the Timber client (Host, op) gets `effect give @s haste infinite 127
  true` + a netherite axe, stands ~3 blocks from the log at yaw -90 and calls `mc.gameMode.startDestroyBlock(pos, dir)`
  (instant at that haste: server `getDestroyProgress >= 1` -> `destroyBlock` -> `PlayerBlockBreakEvents`). No dev command.
- Host driver mode `mp` (selftest): wait for Guest in the tab list + op, plant fixtures with `execute positioned X -60 8
  run <line>` (ops are exempt from command spam kicks; flat world top is grass at -61), tp Guest to the side view,
  `tellraw Guest "[probe] start <tree>"`, chop, log per frame wall ms (`System.currentTimeMillis`), T, mesh pitch, tau,
  frame ns to `mesh_<tree>.tsv`, capture frames, "[probe] stop", next tree; a 4th spruce with Guest >100 blocks away
  for the server's modded-only moving tick; then results.json + quit ("[probe] quit" to Guest).
- `dev/probe` (BUILT, session 5, not run yet: `dev/probe/build.sh` -> `timberprobe.jar`, gitignored): the lab probe +
  `-Dtimberprobe.out=<dir>`, chat cues on `ChatListener.handleSystemMessage` ("[probe] start <name>" / "stop" /
  "quit"), per frame `<name>.frames.tsv` (frame, wall_us epoch, displays), `<name>.displays.tsv` (frame, wall_us,
  partial, id, 3x4 matrix, xrot, progress), `<name>.blocks.tsv`, first 240 frames to `frames/<name>/`, results.json
  ("probe <name>": rows > 0) on quit. Pitch from a non-popping ring-0 display matrix's column 1 (angle from vertical).
- `tools/ModTest/multiplayer.py` additive keys: client `"mod_jar": false` + `"jars": [paths]` (Fabric client without the
  mod jar), `"latency_ms"` (a delaying TCP proxy per client, half each way), case `"server_props"` (-D flags after
  `java`, for `-Dvanilla_timber.perf=1`: Fall logs chop ms and moving-tick mean/max at finish). Update its docstring and
  `tools/ModTest/CLAUDE.md`.
- Cases `m1` (no latency) and `m1_200` (both clients 200 ms); `dev/m1/exit.py` reads both work dirs: slam drift per
  tree (mesh tsv vs probe tsv, first frame >= hit - 0.5, wall ms / 50), `wb img flicker` on both clients' frames,
  frame ms p95 for the spruce, server moving tick for the modded-only fall; prints the GO/STOP table (PRD 13).

## Timing model (why tau = n means "tick n's pose fully drawn")
- Displays get every pose with interpolation 1 (transformation) and teleport_duration 1 (pos/rot). Tick n's packets
  arrive at client time c(n); the pose is reached at c(n) + 1. So drawing time tau = clientTicks - c(0) - 1.
- A display spawned at tick 0 is drawn from c(0) + 1 = tau 0; the blocks (left standing in the chop tick) are removed at
  server tick 1, gone on the client at c(1) = tau 0. Mesh: draw from arrival (tau = -1) at 1.006x, blocks vanish at
  tau 0: no gap (1.3.2 put-back rule without the remove/put dance: Java can scan without removing blocks).
- Entity removal (leaves killed at bounce t=2, popped rings at u=7, finish) shows at c(n) = tau n - 1.
- Pop keys (1.25/0.7 over 3, 0.8/1.45 over 1, 0 over 2) sent at u = 1, 4, 5 of a ring tagged at tick n_k:
  in tau, x = tau - (n_k - 1): 0..3 to squash, 3..4 to stretch, 4..6 to 0 (`Timeline.pop`). They scale about each
  block's centre: offset Ry(yaw)(u, v, u) = C - S C with C = Ry(yaw)(0.5,0.5,0.5).
- Slam fx fire at the tick after `settle` (impact in `tipping`), i.e. when the client shows the tree arriving.
- Modded client clock: tau = e + (client ticks since TreeFall arrived + partial) - 1, e = server ticks elapsed at send.

## Server (vanilla viewers): displays
- One BlockDisplay per block at the pivot (spawn y includes the hang lift `fd`), Rotation [yaw, 0],
  teleport_duration 1, view_range 1.5, rest transformation: translation (tx + ox, wy - 0.003, tz + oz) with
  ox = -0.003 (cos+sin), oz = -0.003 (cos-sin) (1.006 about the block centre), left identity,
  right Ry(yaw) quaternion (0, sin(yaw/2), 0, cos(yaw/2)), scale 1.006.
- (tx, tz) = Ry(yaw)(wx, wz): tx = wx cos + wz sin, tz = wz cos - wx sin (wx = block corner - pivot); ring k = round(wy).
- Each tick: entity xRot = `Rig.step(p)` (only when the step changes; packed byte = exact), transformation from
  residual = p - step: left Rx(residual), scale (gx|lx, gy, gz|lz), translation `Rig.corner(..)` with `Rig.origins`
  computed at the residual angle. Interpolation duration 1 and start delay 0 on every update: the delay setter forces
  the resend (`entityData.set(.., true)`, Display.java:291), which restarts the client's interpolation.
- All Display setters are private (Display.java:231-368, BlockDisplay.setBlockState too): one mixin invoker.
  `Transformation(Vector3fc, Quaternionfc, Vector3fc, Quaternionfc)` (Transformation.java:52).
- Hide from modded viewers: `Entity.broadcastToPlayer(ServerPlayer)` (Entity.java:3569), only called from
  `ChunkMap.TrackedEntity.updatePlayer` (ChunkMap.java:1415) on add / section change / player move, not every tick.
  Hello must arrive before a fall starts (it is sent on join). Mixin returns false for tagged displays + Hello'd player.
- Budget with only modded viewers (<= 0.2 ms/tick): don't spawn displays at all when no vanilla player tracks the
  tree's chunk (`chunkMap.getPlayers(ChunkPos, false)`); spawn lazily when one walks in (they see it from that tick).
- Rotation sync: ServerEntity packs degrees (Mth.packDegrees), sends Rot when the byte changes (ServerEntity.java:133);
  forced position every 60 ticks.

## Scan (oak/birch, M1; generic enough for spruce giants)
- Flood 26 neighbours of `#minecraft:logs` within radius 12 / 256 logs, from the chopped log; drop logs below the cut.
- Crown: natural leaves (persistent=false) at y+1 or y+2 above a top log; leaf count (6-neighbour natural leaves of
  the logs) * 8 >= logs. Leaves by distance rule: from each log, walk leaves whose distance is exactly d+1 (d = 1..7).
- Footprint = logs at the cut level oy (within 1.5 of the chop): fx0..fx1, fz0..fz1; by = oy.
  hang = exactly one log at oy and no leaf at or below oy. gap = open cells (`#timber:gap_open`: replaceable, small
  flowers, saplings) under the cut, min over trunk columns, cap 16 (17+ = 0).
- Pivot: centre (fx0+fx1+1)/2, (fz0+fz1+1)/2; w = min(max extent + 1, 2); hw = w/2 - 0.01; pivot = centre +
  hw (-sin yaw, cos yaw); py = oy + 0.002 - gap; spawn y = py + gap + hang.
- Direction: player yaw rounded to 11.25 deg; try +33.75 then -33.75 (strictly better wins, so + on ties), then (only
  if neither reached 85 deg) 0, +67.5, -67.5, +90, -90. Sweep pitch 6..135 deg step 3: sample h points along the tilted
  centre line (pivot - hw along the look dir, then 0.5, 1.5 .. along the trunk dir); a cell not `#timber:passable` and
  not leaves (and not the tree itself: in Java the blocks are still there) is a hit; best = last free pitch.
  best >= 135: tip to 105 and drop `dr` = min free cells under the lying lanes (cap 16).
- h = ymax - oy + 1; km = max log ring; rs = lymin - oy (- 1 if hang), h if no leaves; amp etc: `FallSpec.derive`.

## Sounds (server, vanilla ids, source BLOCKS, at the pivot unless noted)
CHOP wood.break 1 0.6 (+ azalea_leaves.break 0.7 1.2 at the crown) · LAND wood.fall 1 0.6, generic.big_fall 0.6 1.1 ·
CREAK wooden_door.open 1 0.5 · CRACK wood.break 0.9 0.55, zombie.break_wooden_door 0.3 0.9 · WHOOSH
player.attack.sweep 0.8 0.6 · IMPACT generic.big_fall 1.6 0.6, wood.break 1.2 0.5, azalea_leaves.break 1.4 0.7 and
1.0 1.1 + leaf/decor drops along the trunk · THUD(v, p) wood.fall + generic.big_fall · RING k at the ring's lying
position: wood.break 0.7 pp, chicken.egg 0.6 pp+0.3 (pp = min(0.8 + 0.06k, 1.7)) + ring k's log drops hop out ·
FINISH: leftovers at the pivot + 0.5.

## Drops
Loot rolled at the chop: `Block.getDrops(state, level, pos, be, player, tool)` (Block.java:364); logs keyed by ring,
leaves/decor to the slam. `GameRules.BLOCK_DROPS`. Hop motion: (+-0.06, 0.2..0.26, +-0.06); slam burst (+-0.14,
0.18..0.32, +-0.14). Tool damage: one per log (`ItemStack.hurtAndBreak`), M1 may skip durability gates.

## Client mesh
- Bake at TreeFall: per block its model quads (BlockModelResolver like HavingABlast `Blasts.model`), vertex v =
  rest corner (tx, wy, tz) + Ry(yaw) q_local, block centre stored for pops/inflation; leaf tint = the client's
  `getBlockColors().getTintSources(state)` `colorInWorld` at the rest cell (biome colour; no tint in the payload).
- Groups by (ring, log|leaf); per frame M_k = T(pivot + dy) Ry(-yaw) T(o_k(theta)) Rx(theta) S T(0, -k, 0), exact
  theta (no byte step). Submit with `submitCustomGeometry` from `LevelRenderer.submitEntities` TAIL (HavingABlast
  `LevelRendererMixin`). No face culling (displays draw all faces; culled faces open holes in pops).
- Light: `LightCoordsUtil.getLightCoords(level, pos)` per block along its path every 2 ticks, eased 0.35.
- Shade: SeamlessDisplays `Shade.table` (CardinalLighting) per group normal matrix.

## Measurement plan (exit)
- `mp` case `m1`: dedicated server; client A = Timber (driver: records frames on a chat cue, logs mesh pitch per frame
  with wall ms, frame-time of the mesh), client B = Fabric + `dev/probe` (copy of the lab probe + wall ms + frame
  capture on the same cue + results.json), no Timber. Server chops via a dev command that calls
  `player.gameMode.destroyBlock(pos)` (the real break path, Fabric's PlayerBlockBreakEvents fire).
- multiplayer.py needs (additive keys): client `"mod_jar": false` + `"jars": [...]` (probe only), and `"latency_ms"`
  (a delaying TCP proxy per client) for the 200 ms case.
- drift: slam = first frame with pitch >= hit - 0.5 deg on each client (mesh log vs probe matrices: trunk axis column
  of a ring-0 log), compare wall ms; GO if <= 1 tick, STOP if > 2 ticks at 200 ms.
- flicker: `wb img flicker` on both clients' frames around the chop. Client frame <= 1 ms on a giant spruce
  (nanoTime around submit + geometry). Server <= 0.2 ms per moving tick, vanilla client far away (no displays).

## Exit harness built (session 6, 0.0.6)
- Real chop on a dedicated server: the case's steps op the Host and give haste 127 + a netherite axe; `MpHost` plants
  fixtures with `execute positioned X -60 8 run <line>` chat commands (ops skip `detectRateSpam`; longest line 98
  chars + prefix, under the 256 limit), `tp Host (x-2.5) -60 8.5 -90 -25`, Guest at the side view, then
  `mc.gameMode.startDestroyBlock(pos, WEST)` (instant at that haste; the server's `handleBlockBreakAction` path fires
  `PlayerBlockBreakEvents`). 4th tree: spruce at x 152 with the Guest at x -300 (modded-only moving tick).
- Clocks: Host and probe both stamp each frame with `Instant.now()` epoch us at `Minecraft.runTick` TAIL. exit.py:
  probe pitch = median over displays of acos(m11 / |col1|) (col1 = tsv m01, m11, m21), slam = linear crossing of
  hit - 0.5 on wall time, drift = (probe - mesh) / 50 ms.
- First run: see STATUS.md Next 1 (FIX: oak drift 1.31, host flicker pops near the chop). The server log echoes every
  Host command ("System chat: [Host: ...]"): `gamerule send_command_feedback false` / `log_admin_commands false` in
  the case steps would cut it.

## First exit run read (session 7, 0.0.7)
- **Oak drift 1.31 = the anchor copied the Host's own tick phase.** Per-frame probe vs mesh: through the whole fall the
  Guest's displays drew exactly 1.00 tick behind the mesh (oak, birch: anchor shift -1; spruce shift 0, drift ~0); the
  slam crossing adds ~0.3 on hit-93 trees (xRot clamps at 90, the residual sits in the trailing transformation).
  26.3 handles packets at the start of a frame (`Minecraft.runTick`: `packetProcessor.processQueuedPackets`, then the
  ticks, then the render) and the server sends a tick's packets together at its end (`tickChildren`:
  `suspendFlushing` ... `resumeFlushing`; the break packet is handled at the tick's start, so frame 0 runs in the
  break's tick). A display starts toward an update on the first tick *run* after the frame that handled it: when the
  frame-1 packets land just after a tick boundary but before the frame that runs that tick, that client credits them
  to the earlier tick. Two vanilla clients can so differ by up to 1 tick + 1 frame. The oak's chop tick ran 39 ms
  (scan 26 ms cold + spawns 12.5), so its frame-0 and frame-1 flushes were 13 ms apart and the Host handled both in one
  (38 ms, 15 ms cold bake) frame whose tick boundary had passed 16 ms before they arrived.
- **Fix (`MeshFall.frame`, `LAG`):** the clock comes from the frame that first shows the payload / the frame-1 cell gone:
  arrival = its client time - half its interval; frame 1 fully drawn at arrival + 4/3 tick (the vanilla average at 60
  fps). Within ~0.5 tick of every vanilla client's displays whatever its phase, the same client's included (scene
  `sync`'s "same frame" check became "clock offset": median |displays - mesh| drawing time <= 0.5 tick).
- **The swap darkened the trunk (birch 130 -> 84 brightest bark texels, oak/spruce a little):** the mesh lit each block
  with one light (brightest open neighbour) and no AO; the terrain uses smooth light + AO per vertex. Fix: `RestLight`
  runs vanilla's `ModelBlockRenderer.tesselateBlock` (cull off, FLAT cardinal shade, the tree's cells standing) per
  block at the bake and keeps per-vertex AO x tint and light; each group blends to the old per-block path light as it
  moves 0..4 blocks from where it stood (SeamlessDisplays' fade). `wb img flicker` can't see this (A-B-B, not A-B-A).
- **The Host's pops were not the tree:** oak frames 12-19 = the camera inside the crown leaning back toward it (Host
  3 blocks from the trunk); frame 47 a leaf particle; birch 14-16 the hunger-bar jitter and bark texture sliding 3
  texels/frame; Guest spruce 143-144 the crown tip crossing a tile in one frame near the slam. Harness: Host 4 blocks
  out, HUD hidden and particles minimal on both clients (MpHost, probe `quiet`), frames stamped (`frames_<tree>.tsv`,
  `chop_us`), exit.py counts pops "at the chop" (PRD 13: chop - 0.1 s .. mesh tau 6) and the whole clip as info.
- Not fixed, seen in SeamlessDisplays: the client light engine floods when the tree's blocks vanish (ground under the
  crown brightens at frame 1; 1.3.6 does the same). SeamlessDisplays' `LightPin` is the known fix (M6 candidate).

## Birch Host pops read (session 8, 2026-11-03)
- **Where:** m1 frame 21 (mesh tau 5.157), m1_200 frame 34 (tau 5.167): the first frame after the tick-5 key. Ground
  still (camera fine); the crown widens and drops ~4 px on that frame and comes back on the next (tile shifts 20->21
  (-4, +2), 21->22 (+4, -2), 20->22 (-1, 0)). Tick 5 of a hang tree is three keys reversing at once: landing squash
  gy 1000 -> 957 -> 982 and gx 1000 -> 1021 -> 1009 (`lean()` shapeY 350/150), the shiver's last key bend -2.37 ->
  +1.07 -> +0.50 deg, and the lean-back start. The port's values equal the 1.3.6 fixture on every tick (JUnit).
- **A/B (case `m1_ab`, 2026-11-03 13:23):** the Host with `-Dvanilla_timber.draw=server` (no Hello, so the server shows
  it the displays: 1.3.6's drawing) at the same spot, same window (chop - 0.1 s .. chop + the m1 run's mesh-tau-6
  offset: oak 466, birch 416, spruce 440 ms): chop pops oak 1 (frame 9), **birch 0**, spruce 0; whole clip 8 / 27
  (birch frame 42, vanilla's own) / 2. The mesh in m1: birch 10 at frame 21. Script:
  `python3 dev/m1/ab.py m1_ab m1`.
- **Root cause:** the mesh draws the timeline's keys exactly (call 2026-11-03: no copy of the displays' trail). Vanilla
  `Display.tick` restarts each transformation interpolation from `lastProgress` (Display.java:102, :176, :181,
  :372-381, :428), the last drawn frame's progress (~0.84 at this phase), so a one-tick out-and-back key is rounded:
  gy reaches ~964 instead of 957 and the turn is A-B-B' (fast in, slow out), while the mesh's exact corner gives the
  frame on each side of the key the same pose (A-B-A) from 4 blocks. The earlier call was right for the slam splat
  (invisible there) but makes the mesh visibly differ from 1.3.6 at sharp keys.
- **Fix (next):** the mesh plays the transformation's share of the pose like a display does: per fall keep a "from"
  sample; when tau enters [n, n+1), from = lerp(from, frame n, f_last) with f_last = the last drawn frame's fraction in
  the previous segment, to = frame n+1; draw lerp(from, to, frac(tau)). Apply to what the display carries in its
  transformation (bend, gx gy gz lx lz, ring origins, pop scale, inflate, the pitch residual below the byte step);
  keep what the entity's interpolation handler carries exact (the pitch byte step `Rig.step`, position/dy). Then
  scene `sync`'s "as drawn" gap should fall to ~0 and the vanilla pops of its own (birch frame 42: step vs residual
  split) are not copied. Re-run `exit.py` (m1, m1_200) and `m1_ab` as the baseline.
