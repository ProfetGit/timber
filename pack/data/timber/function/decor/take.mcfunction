execute if score #blocks timber.data >= #max_blocks timber.config run return 0
scoreboard players add #blocks timber.data 1
function timber:rec/new
data modify storage timber:op recs[-1].l set value 1b
function timber:decor/state
scoreboard players operation #lgs timber.data = #lg timber.data
scoreboard players set #lg timber.data 0
function timber:drops/loot
scoreboard players operation #lg timber.data = #lgs timber.data
execute if block ~ ~ ~ #timber:decor[waterlogged=true] run setblock ~ ~ ~ water strict
execute unless block ~ ~ ~ water run setblock ~ ~ ~ air strict
execute if block ~ ~-1 ~ #timber:hanging positioned ~ ~-1 ~ run function timber:decor/s/ny
