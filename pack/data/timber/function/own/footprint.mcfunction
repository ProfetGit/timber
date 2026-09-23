scoreboard players set #fn timber.data 0
execute at @e[type=marker,tag=timber.cur,limit=1] as @e[type=marker,tag=timber.ours,distance=..1.5] if score @s timber.y = #oy timber.data run function timber:own/fp_add
