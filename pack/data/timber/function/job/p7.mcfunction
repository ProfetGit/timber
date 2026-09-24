execute unless data storage timber:meta version run return 0
execute unless score #stage timber.data matches 6 run return 0
scoreboard players set #stage timber.data 7
scoreboard players operation #yaw timber.data = #byaw timber.data
function timber:fall/pivot
execute store result storage timber:op s.x double 0.001 run scoreboard players get #px timber.data
execute store result storage timber:op s.y double 0.001 run scoreboard players get #py timber.data
execute store result storage timber:op s.z double 0.001 run scoreboard players get #pz timber.data
execute store result storage timber:op s.yaw double 0.01 run scoreboard players get #byaw timber.data
function timber:disp/controller with storage timber:op s
execute if score #animation timber.config matches 0 run scoreboard players set #stage timber.data 0
execute if score #animation timber.config matches 0 run return run execute as @e[type=marker,tag=timber.new,limit=1] at @s run function timber:disp/instant
scoreboard players set #hang timber.data 0
execute if score #low timber.data matches 1 if score #lymin timber.data > #oy timber.data run scoreboard players set #hang timber.data 1000
scoreboard players set #km timber.data 0
data modify storage timber:op d set value {Tags:["timber.d","timber.b0","timber.lg"],Rotation:[0f,0f],teleport_duration:0,view_range:1.5f,block_state:{},transformation:{translation:[0f,0f,0f],left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:[1f,1f,1f]}}
execute store result storage timber:op d.Rotation[0] float 0.01 run scoreboard players get #byaw timber.data
execute store result storage timber:op d.transformation.left_rotation[1] float 0.000001 run data get storage timber:op s.qs 1000000
execute store result storage timber:op d.transformation.left_rotation[3] float 0.000001 run data get storage timber:op s.qc 1000000
scoreboard players set #bt timber.data 0
scoreboard players set #cw timber.data 600
scoreboard players set #budget timber.data 600
execute as @e[type=marker,tag=timber.new,limit=1] at @s run function timber:disp/loop
