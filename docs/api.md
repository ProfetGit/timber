# Timber add-on API

Hooks for data packs that build on Timber, for example [Enchanted Timber](https://modrinth.com/datapack/enchanted-timber).

Timber 1.1.0 and newer offer these hooks. They are safe to use when Timber isn't installed, because a tag your pack adds to is simply never called.

- `#timber:api/cancel` (function tag): runs as the player, positioned at the centre of the chopped log, just before Timber looks at the tree. It runs after Timber's own checks (toggle, game mode, sneaking, axe) and before the tree's shape and the axe's durability are checked. Do `return 1` to cancel the fell. The log the player chopped still breaks normally. To allow the fell, **don't return at all**: the first function in the tag that returns decides, so a `return 0` or `return fail` would skip the add-ons after yours.
- `#timber:api/loaded` (function tag): runs at the end of Timber's load function, every load and `/reload`. Use it to check that Timber is present.
- `storage timber:meta requires` (list of text components): cleared on every load, just before `#timber:api/loaded` runs. Append a sentence there, such as `{text:"Your axe needs X. ",color:"gray"}`, and Timber shows it in the join hint and the settings menu.
- `storage timber:meta version_id` (int): `major × 10000 + minor × 100 + patch`, for example `10100` for 1.1.0.

