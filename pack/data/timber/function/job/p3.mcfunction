execute unless data storage timber:meta version run return 0
execute unless score #stage timber.data matches 2 run return 0
scoreboard players set #stage timber.data 3
data modify storage timber:op recs set value []
data modify storage timber:op drops set value []
data modify storage timber:op ldrops set value []
scoreboard players set #lg timber.data 0
data modify storage timber:op logs set value []
data modify storage timber:op seen set from storage timber:op t.leaf
scoreboard players set #blocks timber.data 0
scoreboard players operation #ymax timber.data = #by timber.data
scoreboard players set #lymin timber.data 2147483647
execute store result score #loot timber.data run gamerule block_drops
scoreboard players set #lx0 timber.data 2147483647
scoreboard players set #ly0 timber.data 2147483647
scoreboard players set #lz0 timber.data 2147483647
scoreboard players set #lx1 timber.data -2147483648
scoreboard players set #ly1 timber.data -2147483648
scoreboard players set #lz1 timber.data -2147483648
execute as @e[type=marker,tag=timber.ours] at @s run function timber:leaves/seed
function timber:leaves/clear
