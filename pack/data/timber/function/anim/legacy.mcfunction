data modify storage timber:anim s set from entity @s data
data modify storage timber:anim q set from storage timber:anim s.drops
scoreboard players set #hop timber.data 0
summon marker ~ ~0.5 ~ {Tags:["timber.pt"]}
function timber:anim/drops
kill @e[type=marker,tag=timber.pt]
kill @e[type=block_display,tag=timber.d,distance=..0.01]
kill @s
