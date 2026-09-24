execute unless data storage timber:meta version run return 0
execute unless score #stage timber.data matches 4 run return 0
scoreboard players set #stage timber.data 5
scoreboard players operation #fcx timber.data = #fx0 timber.data
scoreboard players operation #fcx timber.data += #fx1 timber.data
scoreboard players add #fcx timber.data 1
scoreboard players operation #fcx timber.data *= #500 timber.data
scoreboard players operation #fcz timber.data = #fz0 timber.data
scoreboard players operation #fcz timber.data += #fz1 timber.data
scoreboard players add #fcz timber.data 1
scoreboard players operation #fcz timber.data *= #500 timber.data
scoreboard players operation #w timber.data = #fx1 timber.data
scoreboard players operation #w timber.data -= #fx0 timber.data
scoreboard players operation #a timber.data = #fz1 timber.data
scoreboard players operation #a timber.data -= #fz0 timber.data
scoreboard players operation #w timber.data > #a timber.data
scoreboard players add #w timber.data 1
execute if score #w timber.data matches 3.. run scoreboard players set #w timber.data 2
scoreboard players operation #hw timber.data = #w timber.data
scoreboard players operation #hw timber.data *= #500 timber.data
scoreboard players remove #hw timber.data 10
scoreboard players operation #lz timber.data = #hw timber.data
scoreboard players operation #lz timber.data *= #-1 timber.data
scoreboard players operation #py timber.data = #by timber.data
scoreboard players operation #py timber.data *= #1000 timber.data
scoreboard players add #py timber.data 2
scoreboard players operation #h timber.data = #ymax timber.data
scoreboard players operation #h timber.data -= #by timber.data
scoreboard players add #h timber.data 1
execute store result storage timber:op f.fx double 0.001 run scoreboard players get #fcx timber.data
execute store result storage timber:op f.fy double 0.001 run scoreboard players get #py timber.data
execute store result storage timber:op f.fz double 0.001 run scoreboard players get #fcz timber.data
execute store result storage timber:op f.hw double 0.001 run scoreboard players get #hw timber.data
execute store result storage timber:op f.lz double 0.001 run scoreboard players get #lz timber.data
execute store result score #pyaw timber.data run data get entity @a[tag=timber.cutter,limit=1] Rotation[0] 100
scoreboard players add #pyaw timber.data 562
scoreboard players operation #pyaw timber.data %= #36000 timber.data
scoreboard players operation #pyaw timber.data /= #1125 timber.data
scoreboard players operation #pyaw timber.data *= #1125 timber.data
