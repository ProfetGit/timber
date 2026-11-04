# Changelog

## 2.0.0 (unreleased)

Timber is now a mod. It replaces the data pack, keeps the same fall, and runs on Minecraft **26.3 with Fabric** (needs Fabric API). Minecraft 26.2 and older stay on 1.3.6; other loaders and versions come later.

### Added
- **Any tree.** Modded trees that use ordinary logs and leaves fall like vanilla ones, and nether fungi (crimson, warped) and huge mushrooms (red, brown) fall too. A data pack can add more kinds of tree (`data/<namespace>/timber/tree_type/*.json`).
- **Smooth fall on your screen.** With the mod on your client, the tree is drawn on your client, so its motion has no steps, the leaves keep their biome colour while they fall, and shader packs shade it like an entity. Players without the mod still see the whole fall, drawn with display entities, and are never disconnected.
- **Claims and protections.** Every block of a tree is checked against the server's block-break permission before it falls. If any block is denied, only the chopped log breaks and the action bar says part of the tree is protected.
- **Crash safety.** The loot of a fall in progress is saved, and a crash or a stopped server returns it at the stump on the next start. A fall keeps its chunks loaded until it finishes.
- **One fell per player per tick**, so a multi-break mod fells one tree instead of twenty.
- **Shader and render mod support.** The mesh casts a shadow and keeps its materials under Iris with Complementary; tested with Sodium, Entity Culling, Continuity, Seamless Displays and Snappy Blocks, and a Flashback replay shows the fall.
- **Conflict warning.** If FallingTree or HT's TreeChop is installed too, operators are told once and the log says so.
- **`/timber`** for operators: `config`, `reload` and `menu`.
- **Config file** `config/timber.json`, re-read while the game runs, with limits checked and the last good values kept if the file is broken.
- **Java hook** `TimberEvents.CANCEL` for mods that add conditions to felling. The data pack hooks keep working.

### Changed
- Every log in `#minecraft:logs` counts as a trunk (1.3.x knew nine overworld logs), and `#timber:never` excludes logs.
- Crowns made of non-leaf blocks shed dust of their own colour instead of green leaves.
- Settings moved from the scoreboard to `config/timber.json` (`/function timber:settings` is now `/timber menu`).

### Notes for 1.3.x users
- Remove the 1.3.x data pack or jar when you add 2.0. Don't run both.
- The first time a 1.3.x world loads, Timber imports the placed-log protection, your settings (only values the new config file doesn't set) and each player's on/off choice, gives back drops that were mid-fall, removes the old scoreboard entries, and turns off the old data pack. Operators get one chat message.
- The tree falls the same way as in 1.3.6: in tests the 2.0 pose matches 1.3.6 on every tree measured.
- A tree bigger than the limit is left alone, and only the chopped log breaks (the action bar says so).
- Creative mode never fells trees, as before.
- Adventure mode needs an axe that may break logs.
- The 1.3.x data pack zip and server plugin stay on 1.3.6 and get no more updates.
