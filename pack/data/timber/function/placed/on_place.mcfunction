advancement revoke @s only timber:placed_log
execute unless score #protect timber.config matches 1 run return 0
scoreboard players set #ray timber.data 0
execute anchored eyes positioned ^ ^ ^ run function timber:placed/ray
