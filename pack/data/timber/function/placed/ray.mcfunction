scoreboard players add #ray timber.data 1
execute if score #ray timber.data matches 161.. run return 0
execute if block ~ ~ ~ #timber:logs align xyz positioned ~0.5 ~0.5 ~0.5 summon marker run return run function timber:placed/mark
execute positioned ^ ^ ^0.05 run function timber:placed/ray
