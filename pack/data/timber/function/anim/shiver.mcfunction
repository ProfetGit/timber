# the chop jolts the tree: its top trembles (keyed tip offset in thousandths of a block, as a bend)
execute if score @s timber.t matches 1 run function timber:anim/shiver_at {a:350}
execute if score @s timber.t matches 2 run function timber:anim/shiver_at {a:-260}
execute if score @s timber.t matches 3 run function timber:anim/shiver_at {a:180}
execute if score @s timber.t matches 4 run function timber:anim/shiver_at {a:-110}
execute if score @s timber.t matches 5 run function timber:anim/shiver_at {a:50}
execute if score @s timber.t matches 6.. run function timber:anim/shiver_at {a:0}
execute if score @s timber.t matches 6.. run tag @s remove timber.shiver
