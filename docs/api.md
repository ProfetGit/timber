# Timber add-on API

Hooks for data packs that build on Timber, for example [Enchanted Timber](https://www.curseforge.com/minecraft/mc-mods/enchanted-timber).

Timber 1.1.0 and newer offer these hooks. They are safe to use when Timber isn't installed, because a tag your pack adds to is simply never called.

- `#timber:api/cancel` (function tag): runs as the player, positioned at the centre of the chopped log, just before Timber looks at the tree. It runs after Timber's own checks (toggle, game mode, sneaking, axe) and before the tree's shape and the axe's durability are checked. Do `return 1` to cancel the fell. The log the player chopped still breaks normally. To allow the fell, **don't return at all**: the first function in the tag that returns decides, so a `return 0` or `return fail` would skip the add-ons after yours.
- `#timber:api/loaded` (function tag): runs at the end of Timber's load function, every load and `/reload`. Use it to check that Timber is present.
- `storage timber:meta requires` (list of text components): cleared on every load, just before `#timber:api/loaded` runs. Append a sentence there, such as `{text:"Your axe needs X. ",color:"gray"}`, and Timber shows it in the join hint and the settings menu.
- `storage timber:meta version_id` (int): `major × 10000 + minor × 100 + patch`, for example `10100` for 1.1.0.


## Timber 2.0 (the mod)

- The data pack hooks above keep working with the 2.0 mod. On Minecraft 1.20.1, `#timber:api/cancel` doesn't run (1.20.1 commands can't read a function's return value); use the Java hook there. `version_id` is `20000` for 2.0.0.
- Mods get a Java hook, `io.github.profetgit.vanillatimber.api.TimberEvents.CANCEL`:

  ```java
  TimberEvents.CANCEL.register((level, player, pos, state) -> null);
  ```

  It is asked once per chop that passed Timber's own checks (toggle, game mode, sneaking, axe), before the tree is scanned, on the server thread. Return `null` to allow the fell, `Component.empty()` to cancel it silently (the chopped log still breaks), or any other text to cancel it and show that text on the action bar. The first listener that returns non-null decides, and the Java listeners run before the `#timber:api/cancel` tag.

The 1.3.x data pack's source is in this repository's history (its last commit is `37451ff`).
