scoreboard players add @s timber.t 1
execute if score @s timber.ph matches 0 run return run function timber:anim/lean
execute if score @s timber.ph matches 1 run return run function timber:anim/fall
execute if score @s timber.ph matches 2 run return run function timber:anim/bounce
execute if score @s timber.ph matches 4 run return run function timber:anim/tipping
execute if score @s timber.ph matches 5 run return run function timber:anim/drop
function timber:anim/rest
