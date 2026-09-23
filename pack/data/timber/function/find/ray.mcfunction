scoreboard players add #ray timber.data 1
execute if score #ray timber.data matches 121.. run return 0
execute if block ~ ~ ~ #timber:hollow align xyz positioned ~0.5 ~0.5 ~0.5 if function timber:find/next_to_log run return run execute as @a[tag=timber.finder,limit=1] run function timber:find/origin
execute unless block ~ ~ ~ #timber:hollow run return 0
execute positioned ^ ^ ^0.05 run function timber:find/ray
