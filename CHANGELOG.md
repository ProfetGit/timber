# Changelog

## 1.3.5 — 2026-10-09
- Fixed: a tree cut above empty space (for example the bottom log was mined by hand and you chop the second) used to drop one block and then tip over in mid-air. It now falls straight down until it stands on the ground, and only then leans back and tips over.
- For pack authors: `timber:meta version_id` is now 10305.

## 1.3.4 — 2026-10-08
- The join hint is now off by default, so new worlds no longer print the controls reminder when you open them. Operators can turn it back on in `/function timber:settings` (Join hint).
- For pack authors: `timber:meta version_id` is now 10304.

## 1.3.3 — 2026-09-30
- Now also runs on Minecraft Java 1.21.1, 1.21.4, 1.21.8 and 1.21.11, as a data pack zip and a Fabric/Quilt mod jar for each of them. Nothing changes on 26.2 and 26.3. (The pale oak, creaking heart and resin bits only exist from 1.21.4 on.)
- For pack authors: `timber:meta version_id` is now 10303.

## 1.3.2 — 2026-09-28
- Fixed a flicker at the chop: the tree vanished for a split second before its falling copy appeared (most visible at high frame rates). The falling tree now appears first and the real blocks are removed a tick later.
- For pack authors: `timber:meta version_id` is now 10302.

## 1.3.1 — 2026-09-27
- Fixed: a tree cut above a gap (for example after some of its lower logs were already mined) slid off, dropped to the ground and then stayed there as a lying tree that never went away. Its logs and saplings dropped as normal, but the tree itself was left behind.
- Trees left behind like that by 1.3.0 now disappear on their own within a few seconds of loading in.

## 1.3.0 — 2026-09-26
- A smoother, weightier fall. The game only sends an entity's tilt in steps of about 1.4°, so the slow moments (the lean-back, the first moment of the fall, the bounces) used to move in small jerks. Every block now carries its exact angle, so the whole tree glides at any frame rate.
- The chop jolts the tree: its top shivers, then it drops onto the stump with a squash and a little stretch.
- It leans back with a creak, then starts falling from rest with a crack and speeds up all the way down.
- The trunk bends as it falls: the top lags behind while the tree speeds up.
- A heavier slam: the trunk squashes flat for an instant, the crown splats into the ground and bursts into leaves, dust kicks up along the trunk, and a few leaves drift down afterwards.
- Big trees bounce less, so a giant spruce no longer see-saws on its stump. The slam's sound and particles now land on the exact frame the tree hits.
- Leaves buried inside the crown get no display entity (nobody can see them): about a third fewer entities on big trees, for both the server and the client.

## 1.2.0 — 2026-09-24
- New felling animation, tuned by watching it in the real game. The chopped log shatters, the tree hangs for a beat, drops onto the stump and leans back with a creak, then tips over and slams down. Its crown bursts into leaves, the trunk bounces, and then it pops apart log by log from the stump to the tip.
- The tree now tips about 34° to one side of straight away from you (whichever side is more open), so you see it fall in first person instead of watching it disappear behind its own stump.
- Drops land where they belong: each log hops out of its own spot as the trunk pops apart, and saplings, sticks and apples burst out of the crown. The loot is exactly the same as before: vanilla loot, rolled with your axe at the moment you chop.
- The lingering smoke cloud is gone. Small gusts of wind replace it, so nothing blocks your view.
- Leaves no longer show in the wrong colour for long: blocks that move can't take their biome's colour, so the crown now bursts into correctly coloured leaves as soon as it lands.
- Fixed: when the way ahead was blocked, trees never tried the directions to your left, only straight ahead and to the right.
- Fixed: after `/function timber:uninstall`, a pack that was still enabled kept creating empty markers every tick.
- `/function timber:uninstall` in the middle of a fall now drops everything that tree still held.
- A tree that was still falling when a world was saved with 1.1.0 drops its loot right away after updating.

## 1.1.0 — 2026-09-23
- Add-on support. Other data packs can stop a tree from being felled, or add a line to the join hint and the settings menu. The first add-on is Enchanted Timber, which makes felling need a Timber enchantment on the axe.
- Without add-ons, Timber works exactly as in 1.0.0.
- For pack authors: the function tags `#timber:api/cancel` and `#timber:api/loaded`, the `timber:meta requires` text list, and the `timber:meta version_id` number (10100 for 1.1.0). See the README.

## 1.0.0 — 2026-09-23
- First release for Minecraft Java 26.2 and 26.3.
- Chop a natural tree with an axe and the whole tree comes down: it leans back and creaks, tips over, slams into the ground, bounces three times, sits still for a beat, then poofs into a cloud and the drops pop out.
- Picks out one tree in a crowded forest. Every log belongs to the trunk it grows from, so touching neighbours keep their logs, and leaves go to the tree vanilla says they belong to, so neighbours keep their leaves.
- Never fells builds: logs placed by players are remembered and left alone, and log structures without a leafy crown are ignored.
- Chop anywhere up the trunk: the part above the cut falls and the stump stays.
- Falls away from you, or into the most open direction when something is in the way. Only the trunk collides, so leaves and branches never hold it up in the air: it lands on hills, rocks and neighbouring trunks, tips past level onto slopes and cliff edges, and a piece cut high up slides off the stump and drops.
- Vines, cocoa, hanging moss, mangrove propagules, resin, creaking hearts and snow go with the tree.
- Works on every overworld tree: oak, birch, spruce, jungle, acacia, dark oak, mangrove, cherry, pale oak, azalea, the 2×2 giants, and poplar on 26.3.
- Axe durability applies (Unbreaking too); a tree that would break your axe is not felled.
- Sneak to take a single log. Players can turn it off with `/trigger timber`. Ops get a clickable settings menu with `/function timber:settings`, and `/function timber:uninstall` removes all data.
