scoreboard players set #tier timber.data 0
function timber:tool/identify
execute if score #tier timber.data matches 0 run return fail
execute store result storage timber:op slot.s int 1 run data get entity @s SelectedItemSlot
function timber:tool/fetch with storage timber:op slot
execute store result score #dcur timber.data run data get storage timber:op tool.components."minecraft:damage"
execute if data storage timber:op tool.components."minecraft:max_damage" store result score #maxdur timber.data run data get storage timber:op tool.components."minecraft:max_damage"
execute store result score #unb timber.data run data get storage timber:op tool.components."minecraft:enchantments"."minecraft:unbreaking"
scoreboard players operation #unb1 timber.data = #unb timber.data
scoreboard players add #unb1 timber.data 1
scoreboard players set #wear timber.data 1
execute if data storage timber:op tool.components."minecraft:unbreakable" run scoreboard players set #wear timber.data 0
scoreboard players operation #dur_left timber.data = #maxdur timber.data
scoreboard players operation #dur_left timber.data -= #dcur timber.data
scoreboard players remove #dur_left timber.data 1
