execute unless block ~ ~ ~ #timber:passable unless block ~ ~ ~ #minecraft:leaves run return 0
scoreboard players add #c timber.data 1
execute if score #c timber.data matches 16.. run return 0
execute positioned ~ ~-1 ~ run function timber:fall/drop_count
