execute if score #fn timber.data matches 0 run function timber:own/fp_init
scoreboard players add #fn timber.data 1
scoreboard players operation #fx0 timber.data < @s timber.x
scoreboard players operation #fx1 timber.data > @s timber.x
scoreboard players operation #fz0 timber.data < @s timber.z
scoreboard players operation #fz1 timber.data > @s timber.z
scoreboard players operation #by timber.data < @s timber.y
