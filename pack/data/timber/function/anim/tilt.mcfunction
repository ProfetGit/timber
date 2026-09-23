scoreboard players set @s timber.p 9000
function timber:anim/apply
function timber:anim/phase {ph:4}
scoreboard players operation #tdur timber.data = @s timber.e
scoreboard players operation #tdur timber.data /= #1500 timber.data
scoreboard players add #tdur timber.data 1
scoreboard players operation @s timber.dur = #tdur timber.data
scoreboard players operation #ti timber.data = @s timber.e
scoreboard players operation #ti timber.data /= #300 timber.data
function timber:anim/tilt_sc
execute store result score #c timber.data run data get entity @s data.qsi
execute store result score #d timber.data run data get entity @s data.qci
data modify storage timber:anim tf set value {start_interpolation:0,interpolation_duration:1,transformation:{translation:[0f,0f,0f],left_rotation:[0f,0f,0f,1f]}}
execute store result storage timber:anim tf.interpolation_duration int 1 run scoreboard players get #tdur timber.data
scoreboard players operation #q timber.data = #ta timber.data
scoreboard players operation #q timber.data *= #d timber.data
execute store result storage timber:anim tf.transformation.left_rotation[0] float 0.00000001 run scoreboard players get #q timber.data
scoreboard players operation #q timber.data = #tb timber.data
scoreboard players operation #q timber.data *= #c timber.data
execute store result storage timber:anim tf.transformation.left_rotation[1] float 0.00000001 run scoreboard players get #q timber.data
scoreboard players operation #q timber.data = #ta timber.data
scoreboard players operation #q timber.data *= #c timber.data
execute store result storage timber:anim tf.transformation.left_rotation[2] float 0.00000001 run scoreboard players get #q timber.data
scoreboard players operation #q timber.data = #tb timber.data
scoreboard players operation #q timber.data *= #d timber.data
execute store result storage timber:anim tf.transformation.left_rotation[3] float 0.00000001 run scoreboard players get #q timber.data
execute as @e[type=block_display,tag=timber.d,distance=..0.01] run function timber:anim/tilt_one
