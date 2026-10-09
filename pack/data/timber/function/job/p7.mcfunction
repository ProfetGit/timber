execute unless data storage timber:meta version run return 0
execute unless score #stage timber.data matches 6 run return 0
scoreboard players set #stage timber.data 7
scoreboard players operation #yaw timber.data = #byaw timber.data
function timber:fall/pivot
scoreboard players set #hang timber.data 0
execute if score #low timber.data matches 1 if score #lymin timber.data > #oy timber.data run scoreboard players set #hang timber.data 1000
scoreboard players operation #dd timber.data = #gap timber.data
scoreboard players operation #dd timber.data *= #1000 timber.data
scoreboard players operation #dd timber.data += #hang timber.data
execute if score #animation timber.config matches 0 run scoreboard players set #dd timber.data 0
scoreboard players operation #py timber.data += #dd timber.data
execute store result storage timber:op s.x double 0.001 run scoreboard players get #px timber.data
execute store result storage timber:op s.y double 0.001 run scoreboard players get #py timber.data
execute store result storage timber:op s.z double 0.001 run scoreboard players get #pz timber.data
execute store result storage timber:op s.yaw double 0.01 run scoreboard players get #byaw timber.data
function timber:disp/controller with storage timber:op s
execute if score #animation timber.config matches 0 run scoreboard players set #stage timber.data 0
execute if score #animation timber.config matches 0 run return run execute as @e[type=marker,tag=timber.new,limit=1] at @s run function timber:disp/instant
scoreboard players set #km timber.data 0
scoreboard players set #kn timber.data 1000000
scoreboard players set #kt timber.data -1000000
data modify storage timber:op d set value {Tags:["timber.d","timber.b0","timber.lg"],Rotation:[0f,0f],teleport_duration:1,view_range:1.5f,block_state:{},transformation:{translation:[0f,0f,0f],left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:[1f,1f,1f]}}
execute store result storage timber:op d.Rotation[0] float 0.01 run scoreboard players get #byaw timber.data
execute store result storage timber:op d.transformation.right_rotation[1] float 0.000001 run data get storage timber:op s.qs 1000000
execute store result storage timber:op d.transformation.right_rotation[3] float 0.000001 run data get storage timber:op s.qc 1000000
scoreboard players set #cw timber.data 600
scoreboard players set #budget timber.data 600
data modify storage timber:op d.transformation.scale set value [1.006f,1.006f,1.006f]
data modify storage timber:op put set value []
scoreboard players operation #ox timber.data = #cos timber.data
scoreboard players operation #ox timber.data += #sin timber.data
scoreboard players operation #ox timber.data *= #-3 timber.data
scoreboard players operation #ox timber.data /= #10000 timber.data
scoreboard players operation #oz timber.data = #cos timber.data
scoreboard players operation #oz timber.data -= #sin timber.data
scoreboard players operation #oz timber.data *= #-3 timber.data
scoreboard players operation #oz timber.data /= #10000 timber.data
execute as @e[type=marker,tag=timber.new,limit=1] at @s run function timber:put/anchor
execute as @e[type=marker,tag=timber.new,limit=1] at @s run function timber:disp/loop
