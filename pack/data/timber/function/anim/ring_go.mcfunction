execute if score @s timber.kr > @s timber.km run return 0
execute if score @s timber.t < @s timber.ks run return 0
scoreboard players operation #k timber.data = @s timber.kr
execute as @e[type=block_display,tag=timber.lg,distance=..0.01] if score @s timber.k = #k timber.data run tag @s add timber.pop
scoreboard players add @s timber.kr 1
scoreboard players operation #g timber.data = @s timber.kr
function timber:anim/gap
scoreboard players operation @s timber.ks += #g timber.data
function timber:anim/ring_go
