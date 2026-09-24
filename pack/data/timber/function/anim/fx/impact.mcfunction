playsound minecraft:entity.generic.big_fall block @a ~ ~ ~ 1.6 0.6
playsound minecraft:block.wood.break block @a ~ ~ ~ 1.2 0.5
playsound minecraft:block.azalea_leaves.break block @a ~ ~ ~ 1.4 0.7
playsound minecraft:block.azalea_leaves.break block @a ~ ~ ~ 1.0 1.1
function timber:anim/ctx
function timber:anim/walk_impact with storage timber:anim s
kill @e[type=block_display,tag=timber.lf,distance=..0.01]
data modify storage timber:anim q set from storage timber:anim s.ldrops
scoreboard players set #hop timber.data 0
function timber:anim/drops
kill @e[type=marker,tag=timber.pt]
data modify entity @s data.ldrops set value []
