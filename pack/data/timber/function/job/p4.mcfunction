execute unless data storage timber:meta version run return 0
execute unless score #stage timber.data matches 3 run return 0
scoreboard players set #stage timber.data 4
scoreboard players set #lg timber.data 1
execute as @e[type=marker,tag=timber.ours,tag=!timber.origin,tag=!timber.heart] at @s run function timber:logs/take with storage timber:op t
scoreboard players set #lg timber.data 0
kill @e[type=marker,tag=timber.m]
