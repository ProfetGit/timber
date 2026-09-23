execute store result storage timber:op k.x int 1 run scoreboard players get @s timber.x
execute store result storage timber:op k.y int 1 run scoreboard players get @s timber.y
execute store result storage timber:op k.z int 1 run scoreboard players get @s timber.z
scoreboard players operation #c timber.data = @s timber.x
scoreboard players operation #c timber.data /= #16 timber.data
execute store result storage timber:op k.cx int 1 run scoreboard players get #c timber.data
scoreboard players operation #c timber.data = @s timber.z
scoreboard players operation #c timber.data /= #16 timber.data
execute store result storage timber:op k.cz int 1 run scoreboard players get #c timber.data
