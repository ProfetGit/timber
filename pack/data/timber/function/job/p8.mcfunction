execute unless score #stage timber.data matches 7 run return 0
scoreboard players set #stage timber.data 0
scoreboard players set #budget timber.data 900
execute as @e[type=marker,tag=timber.new,limit=1] at @s run function timber:disp/loop
scoreboard players operation #hit timber.data = #best timber.data
execute if score #hit timber.data matches ..599 run scoreboard players set #hit timber.data 600
scoreboard players operation #dur timber.data = #h timber.data
scoreboard players operation #dur timber.data /= #2 timber.data
scoreboard players add #dur timber.data 12
execute if score #dur timber.data matches ..13 run scoreboard players set #dur timber.data 14
execute if score #dur timber.data matches 27.. run scoreboard players set #dur timber.data 26
scoreboard players operation #tilt timber.data = #hit timber.data
scoreboard players remove #tilt timber.data 9000
execute if score #tilt timber.data matches ..-1 run scoreboard players set #tilt timber.data 0
scoreboard players operation #amp timber.data = #hit timber.data
scoreboard players operation #amp timber.data -= #tilt timber.data
scoreboard players operation #amp timber.data *= #12 timber.data
scoreboard players operation #amp timber.data /= #100 timber.data
execute if score #amp timber.data matches ..399 run scoreboard players set #amp timber.data 400
execute if score #amp timber.data matches 1101.. run scoreboard players set #amp timber.data 1100
scoreboard players operation #rs timber.data = #lymin timber.data
scoreboard players operation #rs timber.data -= #by timber.data
execute if score #lymin timber.data matches 2147483647 run scoreboard players operation #rs timber.data = #h timber.data
execute if score #cw timber.data matches 4001.. run scoreboard players set #cw timber.data 4000
execute as @e[type=marker,tag=timber.new,limit=1] at @s run function timber:disp/start
