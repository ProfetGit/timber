# Changelog

## 2.1.0 - unreleased

### Added
- **Chop mode (optional, off by default).** Turn it on with `chops` 1: every break of a tree's log with an axe is one chop. The log stays, cracks a little more each time, and the axe wears one point per chop. The chop that reaches the count fells the tree as before. A tree takes about as many chops as HT's TreeChop asks (5 logs 4 chops, 30 logs 15, 100 logs 25); `chop_curve` (1 to 100, default 10) makes it fewer or more. Each height you chop at is its own cut, and the progress is saved with the world.
- **See it from every client.** The crack, wood chips, a few falling leaves and a creak on the chop before the last are made by the server, so players without the mod see them too. Players with the mod no longer see the log vanish between chops; vanilla clients see a copy of it stand in for the blink.
- **Giant trees are cut through.** On a 2x2 trunk (dark oak, giant spruce, giant jungle, pale oak) a chop on any of the four logs at one height counts for that whole layer. With the mod on your client the four shrink together around the trunk's centre; every one cracks. The last chop breaks all four with their drops, and the tree drops one block onto the stump before it falls over.
- **Settings screen.** With Mod Menu, Timber's settings are one click away: Felling, Fall, Axe and Server, each with a hover explanation. In singleplayer they save to `config/timber.json`; on a server, operators change them (the screen runs `/timber config`) and everyone else can look.
- Sneaking, a placed log, a claimed tree, a pickaxe (with `require_axe` 1) or another mod breaking logs still break one log, as before. With `require_axe` 1 an empty hand chops too, slowly.

### Changed
- An axe too worn to fell a tree no longer refuses: the tree falls and the axe breaks, as if you had broken the logs one by one with it and then finished by hand.

## 2.0.0 — 2026-11-05

Timber is now a mod. It replaces the data pack and keeps the same fall. It runs on Minecraft **26.3, 26.2, 1.21.1 and 1.20.1**, with **Fabric** (needs Fabric API), **Quilt**, **NeoForge** and **Forge**. On 1.20.1 one jar serves Forge and NeoForge.

### Added
- **Any tree.** Modded trees that use ordinary logs and leaves fall like vanilla ones, and nether fungi (crimson, warped) and huge mushrooms (red, brown) fall too. A data pack can add more kinds of tree (`data/<namespace>/timber/tree_type/*.json`).
- **Smooth fall on your screen.** With the mod on your client, the tree is drawn on your client, so its motion has no steps, the leaves keep their biome colour while they fall, and shader packs shade it like an entity. Players without the mod still see the whole fall, drawn with display entities, and are never disconnected.
- **Claims and protections.** Every block of a tree is checked against the server's block-break permission before it falls. If any block is denied, only the chopped log breaks and the action bar says part of the tree is protected.
- **Crash safety.** The loot of a fall in progress is saved, and a crash or a stopped server returns it at the stump on the next start. A fall keeps its chunks loaded until it finishes.
- **One fell per player per tick**, so a multi-break mod fells one tree instead of twenty.
- **Shadow on the ground.** The falling tree darkens the ground under it as it comes down, without shaders too.
- **Shader and render mod support.** The mesh casts a shadow and keeps its materials under Iris with Complementary; tested with Sodium, Entity Culling, Continuity, Seamless Displays and Snappy Blocks, and a Flashback replay shows the fall.
- **Auto-replant (optional, off by default).** Turn it on with `replant` 1: when the tree has fallen, a free sapling of its kind goes into the stump, so the forest grows back. A giant 2x2 tree gets four saplings and regrows as a giant. Your inventory and the drops are untouched.
- **Trunk knockback.** The falling trunk and crown knock back every mob they sweep through, along the fall, once each. Hostile mobs without a name tag also take 2 hearts (`trunk_damage`, 0 to 20 half hearts); animals, villagers, pets and named mobs are only pushed. Players are never pushed or hurt. Turn it off with `knockback` 0.
- **Conflict warning.** If FallingTree or HT's TreeChop is installed too, operators are told once and the log says so.
- **`/timber`** for operators: `config`, `reload` and `menu`.
- **Config file** `config/timber.json`, re-read while the game runs, with limits checked and the last good values kept if the file is broken.
- **Java hook** `TimberEvents.CANCEL` for mods that add conditions to felling. The data pack hooks keep working.

### Changed
- Every log in `#minecraft:logs` counts as a trunk (1.3.x knew nine overworld logs), and `#timber:never` excludes logs.
- Crowns made of non-leaf blocks shed dust of their own colour instead of green leaves.
- Settings moved from the scoreboard to `config/timber.json` (`/function timber:settings` is now `/timber menu`).
- The action bar no longer counts the logs of each fell; it only says why a tree wasn't felled.

### Notes for 1.3.x users
- Remove the 1.3.x data pack or jar when you add 2.0. Don't run both.
- The first time a 1.3.x world loads, Timber imports the placed-log protection, your settings (only values the new config file doesn't set) and each player's on/off choice, gives back drops that were mid-fall, removes the old scoreboard entries, and turns off the old data pack. Operators get one chat message.
- The tree falls the same way as in 1.3.x: in tests the 2.0 pose matches 1.3.x on every tree measured.
- A tree bigger than the limit is left alone, and only the chopped log breaks (the action bar says so).
- Creative mode never fells trees, as before.
- Adventure mode needs an axe that may break logs.
- The 1.3.x data pack zip and server plugin stay on 1.3.5 and get no more updates.
- On 1.20.1 the data pack cancel hook `#timber:api/cancel` doesn't run (1.20.1 commands can't call it); the Java hook works. Players without the mod see a steppier fall there than on newer versions.
