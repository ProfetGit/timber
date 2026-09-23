execute store result score @s timber.x run data get entity @s Pos[0]
execute store result score @s timber.y run data get entity @s Pos[1]
execute store result score @s timber.z run data get entity @s Pos[2]
function timber:placed/dim
function timber:placed/key
function timber:placed/add with storage timber:op k
kill @s
