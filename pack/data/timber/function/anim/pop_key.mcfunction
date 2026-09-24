scoreboard players operation #q timber.data = @s timber.x
$scoreboard players operation #q timber.data += #ox$(n) timber.data
execute store result storage timber:anim pf.transformation.translation[0] float 0.001 run scoreboard players get #q timber.data
scoreboard players operation #q timber.data = @s timber.y
$scoreboard players operation #q timber.data += #oy$(n) timber.data
execute store result storage timber:anim pf.transformation.translation[1] float 0.001 run scoreboard players get #q timber.data
scoreboard players operation #q timber.data = @s timber.z
$scoreboard players operation #q timber.data += #oz$(n) timber.data
execute store result storage timber:anim pf.transformation.translation[2] float 0.001 run scoreboard players get #q timber.data
$data modify storage timber:anim pf.transformation.scale set value [$(a),$(b),$(a)]
$data modify storage timber:anim pf.interpolation_duration set value $(d)
data modify entity @s {} merge from storage timber:anim pf
