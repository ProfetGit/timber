scoreboard players operation #u timber.data = @s timber.t
scoreboard players operation #u timber.data *= #100 timber.data
scoreboard players operation #u timber.data /= @s timber.dur
execute if score #u timber.data matches 101.. run scoreboard players set #u timber.data 100
execute store result storage timber:anim c.u int 1 run scoreboard players get #u timber.data
function timber:anim/curve with storage timber:anim c
scoreboard players operation #f timber.data *= #9250 timber.data
scoreboard players operation #f timber.data /= #10000 timber.data
scoreboard players remove #f timber.data 250
scoreboard players operation @s timber.p = #f timber.data
execute if score #u timber.data matches 55.. unless entity @s[tag=timber.whoosh] run function timber:anim/fx/whoosh
scoreboard players operation #base timber.data = @s timber.hit
scoreboard players operation #base timber.data -= @s timber.e
execute if score @s timber.p >= #base timber.data if score @s timber.e matches 1.. run return run function timber:anim/tilt
execute if score @s timber.p >= #base timber.data run return run function timber:anim/impact
function timber:anim/apply
