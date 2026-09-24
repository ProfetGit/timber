execute if score @s timber.t matches 1 run function timber:anim/tpd {b:"b0",y:1}
execute if score @s timber.t matches 1 run function timber:anim/tpd {b:"b1",y:1}
execute if score @s timber.t matches 2 run function timber:anim/tpd {b:"b2",y:1}
execute if score @s timber.t matches 2 run function timber:anim/tpd {b:"b3",y:1}
execute if score @s timber.t matches 3 positioned ~ ~1 ~ as @e[type=block_display,tag=timber.d,distance=..0.01] positioned ~ ~-0.3 ~ run tp @s ~ ~ ~
execute if score @s timber.t matches 4 positioned ~ ~0.7 ~ as @e[type=block_display,tag=timber.d,distance=..0.01] positioned ~ ~-0.7 ~ run tp @s ~ ~ ~
execute if score @s timber.t matches 4 run function timber:anim/fx/land
scoreboard players operation #l timber.data = @s timber.t
scoreboard players remove #l timber.data 4
function timber:anim/lean_back
