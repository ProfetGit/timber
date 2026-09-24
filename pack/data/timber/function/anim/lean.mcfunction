execute if entity @s[tag=timber.hang] run return run function timber:anim/hang
execute if score @s timber.t matches 1 run function timber:anim/tpd {b:"b0",y:0}
execute if score @s timber.t matches 1 run function timber:anim/tpd {b:"b1",y:0}
execute if score @s timber.t matches 2 run function timber:anim/tpd {b:"b2",y:0}
execute if score @s timber.t matches 2 run function timber:anim/tpd {b:"b3",y:0}
scoreboard players operation #l timber.data = @s timber.t
scoreboard players remove #l timber.data 2
function timber:anim/lean_back
