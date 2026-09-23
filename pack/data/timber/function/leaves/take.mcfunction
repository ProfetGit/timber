scoreboard players add #blocks timber.data 1
execute if score #cy timber.data > #ymax timber.data run scoreboard players operation #ymax timber.data = #cy timber.data
execute if score #cy timber.data < #lymin timber.data run scoreboard players operation #lymin timber.data = #cy timber.data
function timber:rec/leaf with storage timber:op t
function timber:decor/around
execute if block ~ ~1 ~ minecraft:snow positioned ~ ~1 ~ run function timber:decor/s/py
function timber:drops/loot
execute if block ~ ~ ~ #minecraft:leaves[waterlogged=true] run return run setblock ~ ~ ~ water
setblock ~ ~ ~ air
