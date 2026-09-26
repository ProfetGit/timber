execute if entity @s[tag=timber.hang] run return run function timber:anim/hang
scoreboard players operation #l timber.data = @s timber.t
scoreboard players remove #l timber.data 2
function timber:anim/lean_back
