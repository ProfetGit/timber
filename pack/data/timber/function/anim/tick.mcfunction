execute unless data entity @s data.ky run return run function timber:anim/legacy
execute if entity @s[tag=timber.put] run function timber:put/clear
scoreboard players add @s timber.t 1
function timber:anim/phase_tick
execute if score @s timber.ph matches 0.. run function timber:anim/pose
execute if score @s timber.ph matches 6 run function timber:anim/plunge
