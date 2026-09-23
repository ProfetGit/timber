execute store result score @s timber.x run data get entity @s Pos[0]
execute store result score @s timber.y run data get entity @s Pos[1]
execute store result score @s timber.z run data get entity @s Pos[2]
scoreboard players set #ok timber.data 1
execute if score @s timber.x < #rx0 timber.data run scoreboard players set #ok timber.data 0
execute if score @s timber.x > #rx1 timber.data run scoreboard players set #ok timber.data 0
execute if score @s timber.z < #rz0 timber.data run scoreboard players set #ok timber.data 0
execute if score @s timber.z > #rz1 timber.data run scoreboard players set #ok timber.data 0
execute if score #ok timber.data matches 0 run kill @s
execute if score #ok timber.data matches 0 run return fail
tag @s add timber.m
execute unless block ~ ~-1 ~ #timber:not_ground run tag @s add timber.g
execute if score #protect timber.config matches 1 run function timber:flood/placed
return 1
