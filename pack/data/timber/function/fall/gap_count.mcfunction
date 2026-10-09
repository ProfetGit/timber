execute unless block ~ ~ ~ #timber:gap_open run return 0
scoreboard players add #c timber.data 1
execute if score #c timber.data matches 17.. run return 0
execute positioned ~ ~-1 ~ run function timber:fall/gap_count
