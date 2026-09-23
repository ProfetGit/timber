execute unless score #stage timber.data matches 1 run return 0
scoreboard players set #stage timber.data 2
execute as @e[type=marker,tag=timber.m,tag=timber.g,tag=!timber.p] at @s run function timber:own/seed
function timber:own/layer
scoreboard players set #tree timber.data 0
execute as @e[type=marker,tag=timber.cur] if score @s timber.lab matches 1.. run scoreboard players operation #tree timber.data = @s timber.lab
execute if score #tree timber.data matches 0 run function timber:own/fallback
execute as @e[type=marker,tag=timber.m] if score @s timber.lab = #tree timber.data run tag @s add timber.ours
execute store result score #n timber.data if entity @e[type=marker,tag=timber.ours,tag=!timber.origin,tag=!timber.heart]
execute if score #n timber.data matches 0 run return run function timber:job/abort
scoreboard players set #l1 timber.data 0
execute as @e[type=marker,tag=timber.ours] at @s run function timber:own/leafcount
scoreboard players operation #l4 timber.data = #l1 timber.data
scoreboard players operation #l4 timber.data *= #8 timber.data
execute if score #l1 timber.data matches ..0 run return run function timber:job/abort
execute if score #l4 timber.data < #n timber.data run return run function timber:job/abort
scoreboard players set #ytop timber.data -2147483648
execute as @e[type=marker,tag=timber.ours] run scoreboard players operation #ytop timber.data > @s timber.y
scoreboard players remove #ytop timber.data 1
scoreboard players set #crown timber.data 0
execute as @e[type=marker,tag=timber.ours] if score @s timber.y >= #ytop timber.data at @s run function timber:own/crown
execute if score #crown timber.data matches 0 run return run function timber:job/abort
execute as @e[type=marker,tag=timber.cur] run scoreboard players operation #oy timber.data = @s timber.y
execute as @e[type=marker,tag=timber.ours] if score @s timber.y < #oy timber.data run tag @s remove timber.ours
execute store result score #n timber.data if entity @e[type=marker,tag=timber.ours,tag=!timber.origin,tag=!timber.heart]
execute if score #n timber.data matches 0 run return run function timber:job/abort
scoreboard players set #tool timber.data 1
execute as @a[tag=timber.cutter,limit=1] unless function timber:tool/check run scoreboard players set #tool timber.data 0
execute if score #tool timber.data matches 0 run return run function timber:job/abort
function timber:own/footprint
execute as @e[type=marker,tag=timber.m,tag=!timber.ours] at @s run function timber:job/restore with storage timber:op t
kill @e[type=marker,tag=timber.m,tag=!timber.ours]
