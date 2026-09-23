scoreboard players set #c timber.data 0
execute positioned ~ ~-1 ~ run function timber:fall/drop_count
scoreboard players operation #drop timber.data < #c timber.data
scoreboard players remove #r timber.data 1
execute if score #r timber.data matches 1.. positioned ^ ^1 ^ run function timber:fall/drop_lane
