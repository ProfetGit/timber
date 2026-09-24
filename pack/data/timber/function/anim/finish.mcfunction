function timber:anim/ctx
kill @e[type=block_display,tag=timber.d,distance=..0.01]
execute positioned ~ ~1 ~ run kill @e[type=block_display,tag=timber.d,distance=..0.01]
data modify storage timber:anim q set value []
data modify storage timber:anim q append from storage timber:anim s.drops[].Item
data modify storage timber:anim q append from storage timber:anim s.ldrops[]
scoreboard players set #hop timber.data 1
execute if data storage timber:anim q[0] run summon marker ~ ~0.5 ~ {Tags:["timber.pt"]}
function timber:anim/drops
kill @e[type=marker,tag=timber.pt]
kill @s
