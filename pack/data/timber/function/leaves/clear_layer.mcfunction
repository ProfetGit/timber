$fill ~ ~ ~ ~$(dx) ~ ~$(dz) air replace minecraft:structure_void
scoreboard players add #ly timber.data 1
execute if score #ly timber.data <= #ly1 timber.data positioned ~ ~1 ~ run function timber:leaves/clear_layer with storage timber:op lc
