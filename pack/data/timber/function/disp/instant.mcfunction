tag @s remove timber.new
function timber:disp/data
data modify storage timber:anim s set from storage timber:op cd
particle minecraft:poof ~ ~1 ~ 0.6 0.8 0.6 0.04 20
playsound minecraft:block.wood.break block @a ~ ~ ~ 1 0.6
summon marker ~ ~0.5 ~ {Tags:["timber.pt"]}
function timber:anim/drops
kill @e[type=marker,tag=timber.pt]
tag @a remove timber.cutter
kill @s
