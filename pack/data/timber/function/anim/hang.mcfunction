execute if score @s timber.t matches 3 positioned ~ ~1 ~ as @e[type=block_display,tag=timber.d,distance=..0.01] positioned ~ ~-0.3 ~ run tp @s ~ ~ ~
execute if score @s timber.t matches 4 positioned ~ ~0.7 ~ as @e[type=block_display,tag=timber.d,distance=..0.01] positioned ~ ~-0.7 ~ run tp @s ~ ~ ~
execute if score @s timber.t matches 4 run function timber:anim/fx/land
execute if score @s timber.t matches 5 run function timber:anim/shape_y {a:350}
execute if score @s timber.t matches 6 run function timber:anim/shape_y {a:150}
execute if score @s timber.t matches 7 run function timber:anim/shape_y {a:-120}
execute if score @s timber.t matches 8 run function timber:anim/shape_y {a:-40}
execute if score @s timber.t matches 9 run function timber:anim/shape_y {a:0}
scoreboard players operation #l timber.data = @s timber.t
scoreboard players remove #l timber.data 5
function timber:anim/lean_back
