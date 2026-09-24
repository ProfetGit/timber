tag @s remove timber.new
function timber:disp/data
data modify storage timber:anim s set from storage timber:op cd
particle minecraft:small_gust ~ ~1 ~ 0.5 0.6 0.5 0 3
playsound minecraft:block.wood.break block @a ~ ~ ~ 1 0.6
data modify storage timber:anim q set value []
data modify storage timber:anim q append from storage timber:anim s.drops[].Item
data modify storage timber:anim q append from storage timber:anim s.ldrops[]
scoreboard players set #hop timber.data 0
summon marker ~ ~0.5 ~ {Tags:["timber.pt"]}
function timber:anim/drops
kill @e[type=marker,tag=timber.pt]
tag @a remove timber.cutter
kill @s
