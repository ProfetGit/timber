execute unless data entity @s data.ky run return run function timber:anim/legacy
scoreboard players add @s timber.t 1
function timber:anim/phase_tick
execute if score @s timber.ph matches 0.. run function timber:anim/pose
