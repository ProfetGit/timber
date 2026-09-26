scoreboard players set #k timber.data 30
function timber:anim/xf_ring
execute as @e[type=block_display,tag=timber.d,tag=!timber.pop,distance=..0.01,scores={timber.k=30}] run function timber:anim/xf_one
function timber:anim/xf_step
