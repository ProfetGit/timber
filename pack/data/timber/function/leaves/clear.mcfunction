# the stand-in structure voids back to air, one layer of the crown's box at a time (fill's volume limit)
execute if score #lx0 timber.data > #lx1 timber.data run return 0
execute store result storage timber:op lc.x int 1 run scoreboard players get #lx0 timber.data
execute store result storage timber:op lc.y int 1 run scoreboard players get #ly0 timber.data
execute store result storage timber:op lc.z int 1 run scoreboard players get #lz0 timber.data
scoreboard players operation #a timber.data = #lx1 timber.data
scoreboard players operation #a timber.data -= #lx0 timber.data
execute store result storage timber:op lc.dx int 1 run scoreboard players get #a timber.data
scoreboard players operation #a timber.data = #lz1 timber.data
scoreboard players operation #a timber.data -= #lz0 timber.data
execute store result storage timber:op lc.dz int 1 run scoreboard players get #a timber.data
scoreboard players operation #ly timber.data = #ly0 timber.data
function timber:leaves/clear_at with storage timber:op lc
