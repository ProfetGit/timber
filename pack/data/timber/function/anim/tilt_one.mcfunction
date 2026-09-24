execute store result storage timber:anim tf.transformation.translation[0] float 0.001 run scoreboard players get @s timber.x
scoreboard players operation #ny timber.data = @s timber.y
scoreboard players operation #ny timber.data *= #tc timber.data
scoreboard players operation #a timber.data = @s timber.z
scoreboard players operation #a timber.data *= #ts timber.data
scoreboard players operation #ny timber.data -= #a timber.data
scoreboard players operation #ny timber.data /= #10000 timber.data
scoreboard players operation #nz timber.data = @s timber.y
scoreboard players operation #nz timber.data *= #ts timber.data
scoreboard players operation #a timber.data = @s timber.z
scoreboard players operation #a timber.data *= #tc timber.data
scoreboard players operation #nz timber.data += #a timber.data
scoreboard players operation #nz timber.data /= #10000 timber.data
execute store result storage timber:anim tf.transformation.translation[1] float 0.001 run scoreboard players get #ny timber.data
execute store result storage timber:anim tf.transformation.translation[2] float 0.001 run scoreboard players get #nz timber.data
data modify entity @s {} merge from storage timber:anim tf
scoreboard players operation @s timber.y = #ny timber.data
scoreboard players operation @s timber.z = #nz timber.data
