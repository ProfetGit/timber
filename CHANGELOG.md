# Changelog

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
