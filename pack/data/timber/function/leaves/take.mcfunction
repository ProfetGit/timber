scoreboard players add #blocks timber.data 1
execute if score #cy timber.data > #ymax timber.data run scoreboard players operation #ymax timber.data = #cy timber.data
execute if score #cy timber.data < #lymin timber.data run scoreboard players operation #lymin timber.data = #cy timber.data
function timber:rec/leaf with storage timber:op t
function timber:decor/around
execute if block ~ ~1 ~ minecraft:snow positioned ~ ~1 ~ run function timber:decor/s/py
function timber:drops/loot
execute if block ~ ~ ~ #minecraft:leaves[waterlogged=true] run return run setblock ~ ~ ~ water
# a leaf with tree on all six sides is never seen, so it gets no display (it still drops its loot); taken leaves
# stand in as structure voids until the crown is done (leaves/clear, same tick), so they count as tree
function timber:leaves/hide
scoreboard players operation #lx0 timber.data < #cx timber.data
scoreboard players operation #lx1 timber.data > #cx timber.data
scoreboard players operation #ly0 timber.data < #cy timber.data
scoreboard players operation #ly1 timber.data > #cy timber.data
scoreboard players operation #lz0 timber.data < #cz timber.data
scoreboard players operation #lz1 timber.data > #cz timber.data
setblock ~ ~ ~ minecraft:structure_void strict
