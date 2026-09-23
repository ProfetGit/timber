execute unless score #durability timber.config matches 1 run return 1
function timber:tool/read
execute if score #tier timber.data matches 0 run return 1
execute if score #wear timber.data matches 0 run return 1
scoreboard players set #dmg timber.data 0
scoreboard players operation #left timber.data = #n timber.data
function timber:tool/roll
execute if score #dmg timber.data > #dur_left timber.data run title @s actionbar {text:"🪓 Timber: your axe is too worn to fell this tree",color:"red"}
execute if score #dmg timber.data > #dur_left timber.data run return fail
execute if score #dmg timber.data matches 1.. run function timber:tool/apply_damage
return 1
