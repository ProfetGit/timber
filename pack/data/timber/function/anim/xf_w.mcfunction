# bend weight of ring #k: (k/h)^2, x10000
scoreboard players operation #w timber.data = #k timber.data
scoreboard players operation #w timber.data *= #k timber.data
scoreboard players operation #w timber.data *= #10000 timber.data
scoreboard players operation #w timber.data /= #hh timber.data
execute if score #w timber.data matches 10001.. run scoreboard players set #w timber.data 10000
