execute store result score @s timber.x run data get entity @s Pos[0]
execute store result score @s timber.y run data get entity @s Pos[1]
execute store result score @s timber.z run data get entity @s Pos[2]
function timber:placed/dim
function timber:placed/key
scoreboard players set #pl timber.data 0
execute if score #protect timber.config matches 1 run function timber:placed/test
execute if score #pl timber.data matches 1 run function timber:placed/remove with storage timber:op k
execute if score #pl timber.data matches 1 run return run kill @s
scoreboard players set #gate timber.data 0
execute as @a[tag=timber.finder,limit=1] if function timber:find/gates run scoreboard players set #gate timber.data 1
execute if score #gate timber.data matches 0 run return run kill @s
scoreboard players add #jobs timber.data 1
scoreboard players operation @s timber.job = #jobs timber.data
scoreboard players operation @a[tag=timber.finder,limit=1] timber.job = #jobs timber.data
tag @s add timber.job
data modify entity @s data.t set from storage timber:op t
data modify entity @s data.k set from storage timber:op k
