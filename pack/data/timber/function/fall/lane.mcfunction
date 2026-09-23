execute unless block ~ ~ ~ #timber:passable unless block ~ ~ ~ #minecraft:leaves run return run scoreboard players set #hit timber.data 1
scoreboard players remove #r timber.data 1
execute if score #r timber.data matches 1.. positioned ^ ^1 ^ run function timber:fall/lane
