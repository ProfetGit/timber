# every display's transformation: the residual pitch, the bend (ring by ring up the trunk, a chain so the rings stay
# joined) and each group's squash; sent with a one-tick interpolation so the client lands on it exactly
tag @s remove timber.xf
scoreboard players operation @s timber.xl = #x64 timber.data
scoreboard players operation @s timber.bl = @s timber.bn
scoreboard players operation #xr timber.data = #x64 timber.data
scoreboard players operation #xr timber.data *= #27271 timber.data
scoreboard players operation #xr timber.data /= #10000 timber.data
scoreboard players operation #br timber.data = @s timber.bn
scoreboard players operation #br timber.data *= #17453 timber.data
scoreboard players operation #br timber.data /= #10000 timber.data
scoreboard players operation #gx timber.data = @s timber.gx
scoreboard players operation #gy timber.data = @s timber.gy
scoreboard players operation #gz timber.data = @s timber.gz
scoreboard players operation #lx timber.data = @s timber.lx
scoreboard players operation #lz timber.data = @s timber.lz
scoreboard players operation #hh timber.data = @s timber.h
scoreboard players operation #hh timber.data *= @s timber.h
data modify storage timber:anim xg set value {start_interpolation:0,interpolation_duration:1,transformation:{translation:[0f,0f,0f],left_rotation:[0f,0f,0f,1f],scale:[1f,1f,1f]}}
execute store result storage timber:anim xg.transformation.scale[0] float 0.001 run scoreboard players get #gx timber.data
execute store result storage timber:anim xg.transformation.scale[1] float 0.001 run scoreboard players get #gy timber.data
execute store result storage timber:anim xg.transformation.scale[2] float 0.001 run scoreboard players get #gz timber.data
data modify storage timber:anim xl set from storage timber:anim xg
execute store result storage timber:anim xl.transformation.scale[0] float 0.001 run scoreboard players get #lx timber.data
execute store result storage timber:anim xl.transformation.scale[2] float 0.001 run scoreboard players get #lz timber.data
scoreboard players set #sq timber.data 0
execute unless score #gx timber.data matches 1000 run scoreboard players set #sq timber.data 1
execute unless score #gy timber.data matches 1000 run scoreboard players set #sq timber.data 1
execute unless score #gz timber.data matches 1000 run scoreboard players set #sq timber.data 1
execute unless score #lx timber.data matches 1000 run scoreboard players set #sq timber.data 1
execute unless score #lz timber.data matches 1000 run scoreboard players set #sq timber.data 1
# every ring turns by the same residual (so the rings stay flush); the bend slides each ring forward along the chain
scoreboard players operation #th timber.data = #xr timber.data
function timber:anim/trig
execute store result storage timber:anim xg.transformation.left_rotation[0] float 0.0001 run scoreboard players get #hs timber.data
execute store result storage timber:anim xg.transformation.left_rotation[3] float 0.0001 run scoreboard players get #hc timber.data
execute store result storage timber:anim xl.transformation.left_rotation[0] float 0.0001 run scoreboard players get #hs timber.data
execute store result storage timber:anim xl.transformation.left_rotation[3] float 0.0001 run scoreboard players get #hc timber.data
scoreboard players set #oy timber.data 0
scoreboard players set #oz timber.data 0
# a tree hanging over its chopped log is still a block (then 0.7) above the controller
execute if entity @s[tag=timber.hang] if score @s timber.ph matches 0 if score @s timber.t matches ..2 positioned ~ ~1 ~ run return run function timber:anim/xf/rings
execute if entity @s[tag=timber.hang] if score @s timber.ph matches 0 if score @s timber.t matches 3 positioned ~ ~0.7 ~ run return run function timber:anim/xf/rings
function timber:anim/xf/rings
