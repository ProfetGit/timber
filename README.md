![Timber](https://raw.githubusercontent.com/ProfetGit/timber/main/docs/banner.gif)

**Chop one log and the whole tree comes down, cartoon style.**

Timber is a vanilla data pack for **Minecraft Java 26.2 and 26.3**. It installs as a single zip and runs only on the server, so players join with an unmodified game. It works in singleplayer, over LAN and on dedicated servers.

## Features

- **The whole tree falls.** It leans back and creaks, tips over, slams into the ground, bounces three times, sits still for a beat, then poofs into a cloud and the drops pop out.
- **One tree at a time.** In a crowded forest, every log belongs to the trunk it grows from and leaves go to the tree vanilla says they belong to, so touching neighbours keep their logs and leaves.
- **Never fells builds.** Logs placed by players are remembered and left alone, and log structures without a leafy crown are ignored.
- **Chop anywhere up the trunk.** The part above the cut falls and the stump stays.
- **Falls where it makes sense.** Away from you, or into the most open direction when something is in the way. Only the trunk collides, so it lands on hills, rocks and neighbouring trunks, tips past level onto slopes and cliff edges, and a piece cut high up slides off the stump and drops.
- **Everything attached comes along.** Vines, cocoa, hanging moss, mangrove propagules, resin, creaking hearts and snow go with the tree.
- **Every overworld tree.** Oak, birch, spruce, jungle, acacia, dark oak, mangrove, cherry, pale oak, azalea, the 2×2 giants, and poplar on 26.3.
- **Fair durability.** The axe takes durability for the tree, Unbreaking included. A tree that would break your axe is not felled.
- **Players choose for themselves.** Anyone can turn Timber off or on with `/trigger timber`. No operator is needed.
- **A clickable settings menu.** Operators can change every setting from chat. Settings survive `/reload` and restarts.

## How to use

1. Hold an axe.
2. Chop a log of a natural tree.

Sneak to take a single log instead.

## Settings

Open the menu with `/function timber:settings` and click to change a setting.

| Setting | Default | Options |
|---|---|---|
| Max logs per tree | 256 | Menu: 64, 128, 256, 512. Any value from 16 to 1024 by command. Bigger trees are left alone. |
| Search radius | 12 | Menu: 8, 12, 16, 24. Any value from 4 to 32 by command. How far sideways from the cut a tree may reach. |
| Sneaking | Sneak = one log | Sneak = one log / Sneak to fell / Always |
| Drops | Where it lands | Where it lands / At player |
| Require an axe | ON | OFF means any tool or your hand fells trees |
| Use axe durability | ON | OFF means felling is free |
| Falling animation | ON | OFF means the tree vanishes at once and drops at the stump |
| Protect placed logs | ON | OFF means placed logs can be felled too |
| Action bar message | ON | Shows how many logs were felled |
| Join hint | ON | Explains Timber once to each new player |

Every setting can also be changed by command, for example:
`/scoreboard players set #max_logs timber.config 512`

Setting names: `#max_logs`, `#radius`, `#sneak` (0 sneak = one log, 1 sneak to fell, 2 always), `#drops` (0 where it lands, 1 at player), `#require_axe`, `#durability`, `#animation`, `#protect`, `#feedback`, `#welcome`, and `#max_blocks` (limit on leaves and attached blocks per tree, 100 to 1200, default 900). For on/off settings, 1 is on and 0 is off.

## Installation

**Singleplayer**
- New world: under **More → Data Packs**, drag the `.zip` into the window.
- Existing world: open the world folder, put the `.zip` in `datapacks/`, then run `/reload` or reopen the world.

**Server:** put the `.zip` in `world/datapacks/` and run `/reload`, or restart the server.

Don't unzip the file.

## Compatibility

- One zip supports Minecraft Java **26.2 and 26.3**. 26.3 changed the data pack format, so the zip includes a small 26.3 overlay that the game selects automatically.
- Everything lives in the `timber` namespace. The only vanilla files it touches are the `#minecraft:load` and `#minecraft:tick` function tags, which it adds to, so other data packs are unaffected.
- Only vanilla overworld trees are supported.

## Uninstall

1. Run `/function timber:uninstall`.
2. Remove the `.zip` from the `datapacks` folder, or run `/datapack disable "file/Timber-1.0.0.zip"`.
3. Run `/reload`.

## Support

Timber is free. If it saves you some chopping, a coffee helps fund the next update.

[![Support me on Ko-fi](https://raw.githubusercontent.com/ProfetGit/assets/main/kofi-banner.gif)](https://ko-fi.com/profetgit)

## License

© 2026 Profet. All rights reserved.

- **You can** use Timber on any server, including monetized ones, and include the unmodified zip in any modpack that credits Profet and links here. You can also feature it in videos and modify it for your own world or server.
- **Please don't** re-upload Timber or a modified version of it elsewhere, sell it, or present it as your own.

The full terms are in the `LICENSE` file inside the zip. For anything else, just ask.
