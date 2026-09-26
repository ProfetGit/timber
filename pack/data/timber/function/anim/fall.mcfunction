scoreboard players operation #u timber.data = @s timber.t
scoreboard players operation #u timber.data < @s timber.dur
execute store result storage timber:anim c.t int 1 run scoreboard players get #u timber.data
execute store result storage timber:anim c.d int 1 run scoreboard players get @s timber.dur
function timber:anim/curve with storage timber:anim c
function timber:anim/fall_map
scoreboard players operation @s timber.p = #f timber.data
scoreboard players operation #u timber.data *= #100 timber.data
scoreboard players operation #u timber.data /= @s timber.dur
execute if score #u timber.data matches 55.. unless entity @s[tag=timber.whoosh] run function timber:anim/fx/whoosh
execute if score @s timber.p >= @s timber.hit run function timber:anim/settle
