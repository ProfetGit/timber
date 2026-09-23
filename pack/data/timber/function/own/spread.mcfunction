scoreboard players operation #l timber.data = @s timber.lab
execute as @e[type=marker,tag=timber.m,tag=!timber.claimed,tag=!timber.p,distance=..1.8] run function timber:own/claim
