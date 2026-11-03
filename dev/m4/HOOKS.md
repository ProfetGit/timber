# 1.3.6 add-on API (for M4 `hooks`), read from `packs/Timber/pack` (hand-written, not gen.py), 2026-11-03

## `#timber:api/cancel`
- Runs in `find/gates.mcfunction:6` (`execute if function #timber:api/cancel run return fail`), after the toggle,
  game mode, sneak and axe gates, before shape/durability. As the player (`execute as @a[tag=timber.finder]`), at the
  chopped log's block centre; rotation = the player's (unverified).
- Once per probed candidate (per chop), not per tree.
- Cancel = a hook returns 1 (`execute if function`: the first hook that returns decides; a `return 0`/fail ends the
  chain too, so later hooks don't run). No score or storage involved.
- On cancel: no fell, no action bar line; the chopped log still breaks.
- Sneaking (sneak mode 0, single log) skips the hooks (calls stay 0).

## `storage timber:meta`
- `load.mcfunction:1-3`: `version` "1.3.6" (string), `version_id` 10306 (int), `requires` removed. Every load/`/reload`.
  2.0: `version` "2.0.0", `version_id` 20000, `requires` cleared, then `#timber:api/loaded`.
- `requires`: list of text components add-ons append; Timber only shows them (settings menu `settings.mcfunction:2`,
  the join hint `player/welcome.mcfunction:3-5`). Never refuses.

## `#timber:api/loaded`
- Last line of `load.mcfunction` (47), after meta is set and `requires` cleared: every load and `/reload`, as the
  server. Empty by default (`tags/function/api/{cancel,loaded}.json` values []).

## No other API. Internal (not to port as API): `timber.*` scoreboards, marker data, `storage timber:op`.

## Users
- Enchanted Timber (`packs/EnchantedTimber`): cancel = `execute unless items entity @s weapon.mainhand
  *[minecraft:enchantments~[{enchantments:"enchanted_timber:timber"}]] run return 1`; loaded = creates
  `enchanted_timber.data`, stores `timber:meta version_id` into `#base_version` (check: >= 10100), appends a gray
  "Your axe needs the Timber enchantment." to `requires`. Its tests: `dev/test/EnchantedTimberTest.java`
  ("unenchanted axe chops a single log" :538, "enchanted axe fells the whole tree" :551, "requirement registered
  with Timber once" :530).
- TimberTitan (`packs/TimberTitan`): cancel only (`titan:api/cancel`: `return 0` unless armed, `return 1` within 85
  blocks of a fixed point).

## 1.3.6's own test (`packs/Timber/dev/test/hookpack`, `TimberTest.java` hooks() ~1395-1460)
- cancel = [tbtest:veto_a, tbtest:veto_b] (count calls; veto_a records its position; each returns 1 when @s has tag
  `tbtest.veto_a`/`_b`); loaded = [tbtest:loaded] (#loaded +1, stores version_id, appends "Test requirement.").
- Asserts: loaded once per load and /reload, version_id, requires rebuilt not duplicated; hooks that don't return let
  the fell go (each called once); hook runs as the player at the log centre; veto_a cancels (log breaks, no fell, no
  bar, veto_b not called); veto_b alone cancels; sneaking skips hooks; join hint and menu show the requirement.
- Port as parity group `hooks`: write the hookpack with `testPack()`, then the same asserts, plus Enchanted Timber's
  pack copied into the world (`packs/EnchantedTimber/pack`).
