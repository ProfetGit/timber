scoreboard players set #g0 timber.data 4
execute if score @s timber.km matches 9.. run scoreboard players set #g0 timber.data 3
execute if score @s timber.km matches 17.. run scoreboard players set #g0 timber.data 2
scoreboard players operation #g timber.data /= #3 timber.data
scoreboard players operation #g0 timber.data -= #g timber.data
scoreboard players operation #g timber.data = #g0 timber.data
execute if score #g timber.data matches ..0 run scoreboard players set #g timber.data 1
