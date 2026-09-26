data modify entity @s {} merge from storage timber:op d
scoreboard players operation @s timber.x = #tx timber.data
scoreboard players operation @s timber.y = #wy timber.data
scoreboard players operation @s timber.z = #tz timber.data
scoreboard players operation @s timber.k = #k timber.data
scoreboard players operation #a timber.data = #k timber.data
scoreboard players operation #a timber.data *= #1000 timber.data
scoreboard players operation @s timber.yr = #wy timber.data
scoreboard players operation @s timber.yr -= #a timber.data
