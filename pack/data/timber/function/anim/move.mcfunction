scoreboard players operation @s timber.qs = #qs timber.data
execute store result entity @s Rotation[1] float 1.40625 run scoreboard players get #qs timber.data
tp @e[type=block_display,tag=timber.d,distance=..0.01] @s
