scoreboard players operation #x timber.data = @s timber.x
scoreboard players operation #x timber.data *= #gx timber.data
execute store result storage timber:anim xg.transformation.translation[0] float 0.000001 run scoreboard players get #x timber.data
scoreboard players operation #y timber.data = @s timber.y
scoreboard players operation #y timber.data *= #gy timber.data
scoreboard players operation #y timber.data /= #1000 timber.data
scoreboard players operation #y timber.data -= #kk timber.data
scoreboard players operation #z timber.data = @s timber.z
scoreboard players operation #z timber.data *= #gz timber.data
scoreboard players operation #z timber.data /= #1000 timber.data
scoreboard players operation #ny timber.data = #y timber.data
scoreboard players operation #ny timber.data *= #cs timber.data
scoreboard players operation #a timber.data = #z timber.data
scoreboard players operation #a timber.data *= #sn timber.data
scoreboard players operation #ny timber.data -= #a timber.data
scoreboard players operation #ny timber.data += #oy4 timber.data
scoreboard players operation #nz timber.data = #y timber.data
scoreboard players operation #nz timber.data *= #sn timber.data
scoreboard players operation #a timber.data = #z timber.data
scoreboard players operation #a timber.data *= #cs timber.data
scoreboard players operation #nz timber.data += #a timber.data
scoreboard players operation #nz timber.data += #oz4 timber.data
execute store result storage timber:anim xg.transformation.translation[1] float 0.0000001 run scoreboard players get #ny timber.data
execute store result storage timber:anim xg.transformation.translation[2] float 0.0000001 run scoreboard players get #nz timber.data
data modify entity @s {} merge from storage timber:anim xg
