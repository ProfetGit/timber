$execute if score #any timber.data matches 1 run particle minecraft:block{block_state:"$(log)"} ~ ~ ~ 0.35 0.35 0.35 0.12 22
execute if score #any timber.data matches 1 run particle minecraft:small_gust ~ ~ ~ 0.25 0.25 0.25 0 2
$execute if score #any timber.data matches 1 run playsound minecraft:block.wood.break block @a ~ ~ ~ 0.7 $(pp)
$execute if score #any timber.data matches 1 run playsound minecraft:entity.chicken.egg block @a ~ ~ ~ 0.6 $(pq)
execute unless data storage timber:anim q[0] run return 0
summon marker ~ ~ ~ {Tags:["timber.pt"]}
scoreboard players set #hop timber.data 1
function timber:anim/drops
kill @e[type=marker,tag=timber.pt]
