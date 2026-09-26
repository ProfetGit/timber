execute if score #sq timber.data matches 1 if entity @s[tag=timber.lf] run return run function timber:anim/xf_lf
execute if score #sq timber.data matches 1 run return run function timber:anim/xf_lg
# no squash: the offset from the ring's base (timber.yr) was fixed at the swap
execute store result storage timber:anim xg.transformation.translation[0] float 0.001 run scoreboard players get @s timber.x
scoreboard players operation #ny timber.data = @s timber.yr
scoreboard players operation #ny timber.data *= #cs timber.data
scoreboard players operation #a timber.data = @s timber.z
scoreboard players operation #a timber.data *= #sn timber.data
scoreboard players operation #ny timber.data -= #a timber.data
scoreboard players operation #ny timber.data += #oy4 timber.data
scoreboard players operation #nz timber.data = @s timber.yr
scoreboard players operation #nz timber.data *= #sn timber.data
scoreboard players operation #a timber.data = @s timber.z
scoreboard players operation #a timber.data *= #cs timber.data
scoreboard players operation #nz timber.data += #a timber.data
scoreboard players operation #nz timber.data += #oz4 timber.data
execute store result storage timber:anim xg.transformation.translation[1] float 0.0000001 run scoreboard players get #ny timber.data
execute store result storage timber:anim xg.transformation.translation[2] float 0.0000001 run scoreboard players get #nz timber.data
data modify entity @s {} merge from storage timber:anim xg
