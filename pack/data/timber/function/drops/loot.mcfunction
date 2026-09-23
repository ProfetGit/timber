execute if score #loot timber.data matches 0 run return 0
execute as @a[tag=timber.cutter,limit=1] run loot spawn ~ ~ ~ mine ~ ~ ~ mainhand
execute as @e[type=item,distance=..0.01] run function timber:drops/stash
