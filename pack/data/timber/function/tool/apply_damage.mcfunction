scoreboard players operation #dcur timber.data += #dmg timber.data
execute store result storage timber:op m.dmg int 1 run scoreboard players get #dcur timber.data
function timber:tool/set_damage with storage timber:op m
