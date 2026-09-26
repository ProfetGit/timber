data modify storage timber:op cd.lp set value "minecraft:tinted_leaves{color:[0.282,0.71,0.094,1.0]}"
execute if data storage timber:op {seen:"minecraft:birch_leaves"} run data modify storage timber:op cd.lp set value "minecraft:tinted_leaves{color:[0.502,0.655,0.333,1.0]}"
execute if data storage timber:op {seen:"minecraft:spruce_leaves"} run data modify storage timber:op cd.lp set value "minecraft:tinted_leaves{color:[0.38,0.6,0.38,1.0]}"
execute if data storage timber:op {seen:"minecraft:mangrove_leaves"} run data modify storage timber:op cd.lp set value "minecraft:tinted_leaves{color:[0.573,0.776,0.282,1.0]}"
execute if data storage timber:op {seen:"minecraft:azalea_leaves"} run data modify storage timber:op cd.lp set value "minecraft:tinted_leaves{color:[0.55,0.72,0.28,1.0]}"
execute if data storage timber:op {seen:"minecraft:flowering_azalea_leaves"} run data modify storage timber:op cd.lp set value "minecraft:tinted_leaves{color:[0.6,0.68,0.36,1.0]}"
execute if data storage timber:op {seen:"minecraft:cherry_leaves"} run data modify storage timber:op cd.lp set value "minecraft:cherry_leaves"
execute if data storage timber:op {seen:"minecraft:pale_oak_leaves"} run data modify storage timber:op cd.lp set value "minecraft:pale_oak_leaves"
function timber:compat/leaf_fx
