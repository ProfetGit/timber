# Changelog

## 2.3.1 — unreleased

### Added
- **A new settings screen** with a live preview for each option: a small tree that the screen chops and fells with your current settings. You see the fall style, chop mode, the sneak and tool rules, protected logs, what stays fallen, where the drops go, auto-replant, the trunk knocking a zombie aside and axe wear. Open it from the Profet golem button in the Game Menu or with the config button in Mod Menu, Forge's or NeoForge's mod list. It is the same screen on every Minecraft version and loader Timber supports.
- **Quick setup presets**: Default, Chop mode, Tidy (replant and drops at the player), Stays fallen, Instant.
- **Report a bug from inside the game**: the Report bug button fills in your Minecraft version, loader, Timber's settings and recent errors for you.
- On a server, operators change the server's settings from the screen (as `/timber config`); other players see them, locked.

### Changed
- The settings screen replaces the old one. The `config/timber.json` file is unchanged apart from the two removed options below. The tools list is still set with `/timber config tools`.
- The sneaking option is now **Trees fall when**: Not sneaking, Sneaking or Always.
- **Tree stays fallen** is one smooth motion: after the bounce the part that stays settles into place as blocks, with no snap or flash. (Players without Timber on their own game still see the leaf colour change at that moment.)
- With the falling animation off, the leaves burst into crumbs where the tree stood.

### Removed
- The join hint (`welcome`).
- Hide inner leaves (`hide_leaves`): a falling tree now always keeps all of its leaves. Old config files that still have either key load as before; the keys do nothing.

### Fixed
- Felling a warped or crimson tree, or a huge mushroom, no longer takes the caps of the trees around it, which also left them impossible to fell.

## 2.2.10 — 2026-11-24

### Fixed
- **Fruit and anything else hanging from the tree now falls with it.** Apples and pears from No Man's Land stayed floating in the air after the tree was chopped. Timber now asks every block next to the tree whether it would still stand without it (the same check Minecraft uses to pop a torch or a flower), and takes the ones that would not along for the fall: fruit under its leaves, lanterns under a branch, torches on a log, any mod's vines. No list per mod is needed any more. Their drops come out where the tree lands, as before for vines and cocoa.
- A hanging block whose own check cannot run on the felled tree pops when the tree goes, as it would have in vanilla, instead of floating.
- **Black boxes behind falling vines and fruit** on Forge and NeoForge (1.20.1 and 1.21.1). A mod's plant that marks itself see-through in its own model file, such as Luminous Nether's vines, fell inside a black rectangle. Timber now draws any block whose texture has see-through pixels with cut-out edges.

### Added
- **Block tag `timber:keep`** (empty by default): blocks listed in it are never taken with a tree, for a pack that wants a hanging block to stay.

## 2.2.8 — 2026-11-23

### Fixed
- **Black poof clouds.** A cloud puff that landed inside a block (against a hill, in a dense forest) was lit by that block's own darkness and showed up black. Each puff now takes the brightest light of its cell and the cells around it.

## 2.2.7 — 2026-11-23

### Added
- **Poof fall style.** A new `style` setting (Fall section of the settings screen, or `/timber config style 1`). The tree falls and slams as usual, holds still for a beat, gulps down a little, springs, and vanishes in a single cartoon cloud with a pop. Every item hops out where its log lay. Classic stays the default, and `land_blocks` is ignored in poof style.
- **Hand-drawn cloud sprites.** On Minecraft 26.3 the poof is made of Timber's own 16-frame pixel puffs (bigger ones at the logs, smaller ones in the crown) that pop open and break up in half a second. Other versions use the vanilla poof particle.
- Players without Timber see the same poof fall with the vanilla cloud. A client on an older 2.2 build also sees the fall as displays.

## 2.2.6 — 2026-11-23

### Added
- **Minecraft 26.1.2 on Fabric, Quilt, NeoForge and Forge**, with every 2.2 feature including the settings screen and chop mode's shrinking logs (Fabric and Quilt).

## 2.2.5 — 2026-11-23

### Fixed
- **Vines from other mods no longer stay hanging when the tree falls.** Any mod's vine now goes with the tree: vine and growing-plant blocks, whatever is in `#minecraft:climbable` (short of ladders and scaffolding), and a block with no collision hanging free under the tree's leaves. Luminous Nether's ash vines and driprot vines on its withered trees are listed by name too.

## 2.2.4 — 2026-11-23

### Fixed
- **Shader packs on 1.21.1, 1.21.11 and 1.20.1** (Iris): a falling tree casts its shadow while it falls and keeps the shadows on its own leaves, instead of the shadow vanishing the moment the trunk is cut. 26.2 and 26.3 already did this.
- **Leaves keep their colour under shader packs on 1.20.1** (Iris 1.7): they no longer change colour and lighting when the tree starts to fall.
- **A falling tree keeps its depth.** The dark pockets between leaf clumps and under the canopy stay for the whole fall, and the log stays lit once the leaves are gone.

## 2.2.1 — 2026-11-22

### Added
- **Minecraft 1.21.11 on Fabric, Quilt, NeoForge and Forge**, with every 2.2 feature including the settings screen (Mod Menu button, `/timber config`, `config/timber.json`).
- **Chop mode's shrinking logs on 1.21.11**: each cut narrows the log, as on 26.2 and 26.3.

## 2.2.0 — 2026-11-18

### Added
- **The tree can stay fallen.** `land_blocks` (0 to 100 %, off by default): that share of the trunk stays on the ground as sideways logs when the fall ends, with the leaves right next to it, for you to chop by hand (vines, cocoa and moss still fall off). The rest pops into drops as before. With it on, trees fall straight along north, south, east or west (the way you face, or to the side if that is blocked), so the trunk lies as one straight row. Logs that land cost no axe durability, are never felled again as a tree, and their leaves decay once you have chopped the logs away.
- **Choose what fells trees.** `tools` lists the items that fell trees while `require_axe` is on: item ids and `#tags`, axes by default (`/timber config tools #minecraft:axes minecraft:stick`, or the text box in the settings screen). `require_axe` 0 still lets any item fell.
- **Settings screen on 1.21.1 and 1.20.1** (Mod Menu on Fabric and Quilt, the mods list on NeoForge and Forge), as on 26.2 and 26.3.

### Changed
- Big trees fall slower: past 28 blocks a fall takes longer the taller the tree, like a real falling trunk (a 56-block tree 1.5 s, a 90-block one 2 s), instead of 1.1 s for every giant. Small trees keep their speed.
- Much bigger trees fall by default (worldgen mods' sequoias and redwoods): the default limits are now 1024 logs (was 256) and 5000 leaves (was 900), and they go up to 4096 logs, 8000 leaves and 64 blocks sideways. A 90-block sequoia with a 3x3 trunk falls whole. Existing config files keep their values: raise `max_logs` and `max_blocks` yourself, or delete the two lines.
- `display_cap` now sets how many displays a fall may spawn for players without the mod (default 600, as before); it had no effect.

## 2.1.5 — 2026-11-13

### Fixed
- Chop mode: faces inside the hollow of a cut trunk (the sides of the narrowed cells, and the logs around them) are lit by the open air around the cut instead of coming out black.
- Chop mode on trunks that are not squares (redwoods, arched or ragged bases, plus shapes, trunks wider than 5): a chop now cuts every upright log of the tree at that height, so the whole base is cut at once and the tree falls when it is gone. Each part of the cut narrows from its own outside, and the tree tips about the whole footprint. Before, wide trunks were cut in 5x5 tiles from one corner, which left scattered cuts and a tree that could not fall properly.

## 2.1.2 — 2026-11-12

### Fixed
- The pop sound of the logs bursting apart at the end of a fall plays again for players who have the mod on their client (it only played for players without it). It rises in pitch up the trunk as before.

## 2.1.1 — 2026-11-12

### Fixed
- In chop mode, the grass (or any tinted block) under a tree no longer turns gray when the bottom log is cut: it keeps its biome colour.

## 2.1.0 — 2026-11-12

### Added
- **Chop mode (optional, off by default).** Turn it on with `chops` 1: every break of a tree's log with an axe is one chop. The log stays, cracks a little more each time, and the axe wears one point per chop. The chop that reaches the count fells the tree as before. A tree takes about as many chops as HT's TreeChop asks (5 logs 4 chops, 30 logs 15, 100 logs 25); `chop_curve` (1 to 100, default 10) makes it fewer or more. Each height you chop at is its own cut, and the progress is saved with the world.
- **See it from every client.** The crack, wood chips, a few falling leaves and a creak on the chop before the last are made by the server, so players without the mod see them too. Players with the mod no longer see the log vanish between chops; vanilla clients see a copy of it stand in for the blink.
- **Giant trees are cut through.** On a 2x2 trunk (dark oak, giant spruce, giant jungle, pale oak) a chop on any of the four logs at one height counts for that whole layer. With the mod on your client the four shrink together around the trunk's centre; every one cracks. The last chop breaks all four with their drops, and the tree drops one block onto the stump before it falls over. Wider modded trunks work the same way: a chop counts for the whole connected trunk layer up to 5x5 (wider trunks in 5x5 sections), and the tree tips over its full base.
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
