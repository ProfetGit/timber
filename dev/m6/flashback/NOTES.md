# M6 Flashback check (2026-11-04), Timber 0.0.26 + Flashback 0.43.6, MC 26.3

Outcome (a): the replay shows the fall with draw=local (default). draw=server run not needed.

Commands
- scene: media/Showcase/scenes/timber2_oak.json (copy of timber_oak; excludes Timber-1.3.6, EnchantedTimber, EnchantedVeinminer; plain netherite_axe)
- take: wb job run --name timber2-oak-take -- python3 ~/.claude/skills/pack-showcase/scripts/showcase.py scenes/timber2_oak.json (131 s client, replay 8.0 MB, 93 ticks)
- re-shoot: wb job run --name timber2-reshoot --exclusive -- python3 mods/Timber/dev/m6/flashback/reshoot.py timber2_oak (orbit r20 h3, 1080p60, 56 s)

Results
- take_sheet.png: tree falls as mesh, trunk on ground, leaf particles, log items. 2.0 works in the recorder.
- replay_sheet.png (out/timber2_oak/reshoot/orbit.mp4): standing oak, then fall and fallen trunk with log pieces on the ground visible from the orbit camera; leaves go to particles as in the take. Frames 2-3 of the sheet: the orbit camera passes through a neighbouring bush (camera choice, not Timber).
- Not verified: pose/timing against the take frame by frame; whether the replay draws the mesh or displays (looks like mesh, not checked in the log).

Mod changes needed: none found. draw is a JVM property (-Dvanilla_timber.draw=server, TimberClient.java:17), not a config file; PRD 5.5 says "client config / Mod Menu", so the PRD wording and the code differ.
