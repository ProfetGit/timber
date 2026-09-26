execute unless entity @e[type=block_display,tag=timber.pop,distance=..0.01,limit=1] run return 0
scoreboard players add @e[type=block_display,tag=timber.pop,distance=..0.01] timber.u 1
# the lying trunk may sit past level (the residual pitch the transformations carry): pop about the turned centres
execute store result score #th timber.data run scoreboard players get @s timber.xl
scoreboard players operation #th timber.data *= #27271 timber.data
scoreboard players operation #th timber.data /= #10000 timber.data
function timber:anim/trig
scoreboard players operation #pcs timber.data = #cs timber.data
scoreboard players operation #psn timber.data = #sn timber.data
execute store result score #pc timber.data run data get entity @s data.pc
execute store result score #mc timber.data run data get entity @s data.mc
function timber:anim/pop_off {u:-125,v:150,n:1}
function timber:anim/pop_off {u:100,v:-225,n:2}
function timber:anim/pop_off {u:500,v:500,n:3}
data modify storage timber:anim pf set value {start_interpolation:0,interpolation_duration:1,transformation:{translation:[0f,0f,0f],scale:[1f,1f,1f]}}
execute as @e[type=block_display,tag=timber.pop,distance=..0.01,scores={timber.u=1}] run function timber:anim/pop_key {n:1,a:1.25f,b:0.7f,d:3}
execute as @e[type=block_display,tag=timber.pop,distance=..0.01,scores={timber.u=4}] run function timber:anim/pop_key {n:2,a:0.8f,b:1.45f,d:1}
execute as @e[type=block_display,tag=timber.pop,distance=..0.01,scores={timber.u=5}] run function timber:anim/pop_key {n:3,a:0f,b:0f,d:2}
kill @e[type=block_display,tag=timber.pop,distance=..0.01,scores={timber.u=7..}]
